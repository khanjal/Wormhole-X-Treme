'use strict';
// The gallery (--gallery): documentation pictures and animations of the plugin at work, taken by
// Probe in a clean white studio under open sky, drawn by the viewer (lib/viewer.js) and written
// to PNG and GIF (lib/reel.js). The campus is for testing; this is for showing: nothing in the
// frame but the thing being shown, the same light every time, the same framing for every shape.
//
// A scene says what to build, how to look at it and what to do while the camera runs:
//   still, idle    the gate as built
//   still, open    the gate dialled, its wormhole open and the woosh gone
//   reel, dial     from the dial command until the wormhole has opened and settled (the ring
//                  pattern and the chevrons locking)
//   reel, kawoosh  the woosh going out and back, from just before the wormhole is drawn
// A reel is made with the server slowed (`/tick rate`, 1.20.3 and later), so a software renderer
// that paints a few frames a second still catches every tick of the plugin's animation; the GIF's
// delays put the time back (reel.retime).

const fs = require('fs');
const path = require('path');
const campus = require('./campus');
const shapes = require('./shapes');
const shots = require('./shots');
const reel = require('./reel');
const { Vec3 } = require('vec3');
const { GateKit } = require('./gatekit');
const { atLeast } = require('./version');

const O = campus.OVERWORLD;

// The studio: a floor, a backdrop and two side walls far from the campus (it ends at z 92), its own
// forceload. The walls are why a view from a corner never looks out past the end of the backdrop;
// the floor is flat white concrete, whose texture is plain, so it does not shimmer from frame to frame.
// The subject's opening is in the plane z = PLANE, facing south; the camera stands to the south
// of it looking north, the partner gate sixty blocks behind the camera.
const STUDIO = {
  plane: 400, half: 70, floor: 'minecraft:white_concrete', backdrop: 'minecraft:white_concrete',
  backdropAt: 374, backdropHigh: 48, partnerAt: 470, sideAt: 46,
};
const SUBJECT = 'StudioA';
const PARTNER = 'StudioB';
const NET = 'Studio';
// What a gate build lays back in the floor (gatekit.FLOORS), to be painted over with the studio's.
const FLOOR_AFTER_BUILD = require('./gatekit').FLOORS[O];

/** The ring patterns, as `gate-dial-spin` takes them (all but `none`, which has no ring light to show). */
const SPINS = ['top', 'chevron', 'lap', 'fill', 'pegasus', 'chase', 'universe', 'overshoot'];
/** The shapes the console can build, in the order the guide shows them. */
const SHAPES = ['Massive', 'Grand', 'Large', 'Standard', 'Minimal', 'Horizontal'];

/** The material groups the shipped config.yml names. */
function groups(config = path.resolve(__dirname, '..', '..', '..', 'src', 'main', 'resources', 'config.yml')) {
  const out = [];
  let inside = false;
  for (const line of fs.readFileSync(config, 'utf8').split(/\r?\n/)) {
    if (/^gate-material-groups:/.test(line)) { inside = true; continue; }
    if (!inside) continue;
    const g = /^ {2}([A-Za-z][\w-]*):\s*$/.exec(line);
    if (g) out.push(g[1]);
    else if (/^\S/.test(line)) break;
  }
  return out;
}

// ---- the scenes ---------------------------------------------------------------------------

/** Every scene, in the order they are built (a run rebuilds the gate only when its shape changes). */
function catalog(groupNames = groups()) {
  const out = [];
  const gate = (shape, group = 'Standard', spin = null) => ({ shape, group, spin });
  for (const shape of SHAPES) {
    const view = shapes.isFlat(shape) ? 'high' : 'front';
    out.push({ name: `gate-${shape.toLowerCase()}-idle`, kind: 'still', state: 'idle', subject: gate(shape), view });
    out.push({ name: `gate-${shape.toLowerCase()}-open`, kind: 'still', state: 'open', subject: gate(shape), view });
  }
  for (const group of groupNames) {
    out.push({ name: `palette-${group.toLowerCase()}-idle`, kind: 'still', state: 'idle', subject: gate('Standard', group), view: 'quarter' });
    out.push({ name: `palette-${group.toLowerCase()}-open`, kind: 'still', state: 'open', subject: gate('Standard', group), view: 'quarter' });
  }
  for (const spin of SPINS) out.push({ name: `dial-${spin}`, kind: 'reel', action: 'dial', subject: gate('Standard', 'Standard', spin), view: 'front' });
  for (const shape of SHAPES) {
    out.push({ name: `kawoosh-${shape.toLowerCase()}`, kind: 'reel', action: 'kawoosh', subject: gate(shape), view: shapes.isFlat(shape) ? 'high' : 'quarter' });
  }
  return out;
}

/**
 * The scenes `spec` names: 'all', or a comma-separated list where each is a scene's name, or a
 * prefix ending in a dash (`dial-`, `kawoosh-`); throws on one that matches none.
 */
function select(spec, all = catalog()) {
  if (!spec || spec === 'all') return all;
  const chosen = new Set();
  const bad = [];
  for (const token of spec.split(',').map((s) => s.trim()).filter(Boolean)) {
    const hit = all.filter((s) => s.name === token || (token.endsWith('-') && s.name.startsWith(token)));
    if (!hit.length) bad.push(token);
    for (const s of hit) chosen.add(s);
  }
  if (bad.length) throw new Error(`no gallery scene named ${bad.join(', ')} (there are: ${all.map((s) => s.name).join(', ')})`);
  return all.filter((s) => chosen.has(s));
}

// ---- the camera ---------------------------------------------------------------------------

/** Yaw and pitch (Minecraft's: 0 faces south, -90 east, pitch down positive) for looking from `from` at `to`. */
function lookAt(from, to) {
  const dx = to.x - from.x;
  const dz = to.z - from.z;
  return {
    yaw: Math.round((Math.atan2(-dx, dz) * 180) / Math.PI * 10) / 10 || 0,
    pitch: Math.round((Math.atan2(from.y - to.y, Math.hypot(dx, dz)) * 180) / Math.PI * 10) / 10 || 0,
  };
}

/** The camera's eye height above Probe's feet. */
const EYE = 1.62;

/**
 * Where Probe stands (feet, the form `Probe.teleport` takes) to see a gate of `geom` as `kind`:
 * `front` square on at its middle's height, `quarter` from a corner a little above, `high` from
 * the front and well above (a gate lying in the floor). The distance grows with the gate's size.
 */
function camera(geom, kind) {
  const b = geom.bounds;
  const width = Math.abs(geom.right.x) * (b.x1 - b.x0 + 1) + Math.abs(geom.right.z) * (b.z1 - b.z0 + 1);
  const height = b.y1 - b.y0 + 1;
  const size = Math.max(width, height);
  const d = Math.min(40, Math.max(6, size * 0.95 + 2));
  const c = geom.centre;
  const n = geom.normal;
  const r = geom.right;
  let eye;
  if (kind === 'quarter') {
    const e = { x: n.x * 0.8 + r.x * 0.6, z: n.z * 0.8 + r.z * 0.6 };
    eye = { x: c.x + e.x * d * 1.05, y: c.y + height * 0.12, z: c.z + e.z * d * 1.05 };
  } else if (kind === 'high') {
    eye = { x: c.x + n.x * d * 0.9, y: c.y + d * 0.75, z: c.z + n.z * d * 0.9 };
  } else {
    eye = { x: c.x + n.x * d, y: c.y, z: c.z + n.z * d };
  }
  return { x: Math.round(eye.x * 10) / 10, y: Math.round((eye.y - EYE) * 10) / 10, z: Math.round(eye.z * 10) / 10, ...lookAt(eye, c) };
}

// ---- the studio ---------------------------------------------------------------------------

/** Forceloads the studio's chunks and lays its floor and backdrop. Returns the problems it met. */
async function buildStudio(srv, dim = O) {
  const s = STUDIO;
  const problems = [];
  const run = async (cmd) => {
    const r = await srv.run(`execute in ${dim} run ${cmd}`);
    if (r.errors.length) problems.push(`${cmd}: ${r.errors.join(' ')}`);
  };
  const z0 = s.backdropAt - 16;
  const z1 = s.partnerAt + 40;
  await run(`forceload add ${-s.half - 16} ${z0} ${s.half + 16} ${z1}`);
  const points = [];
  for (let cx = Math.floor((-s.half - 16) / 16); cx <= Math.floor((s.half + 16) / 16); cx++) {
    for (let cz = Math.floor(z0 / 16); cz <= Math.floor(z1 / 16); cz++) points.push([cx * 16 + 8, 0, cz * 16 + 8]);
  }
  await srv.waitLoaded(dim, points, 120000);
  await run(`fill ${-s.half} -1 ${z0} ${s.half} -1 ${z1} ${s.floor}`);
  await run(`fill ${-s.sideAt} 0 ${s.backdropAt} ${s.sideAt} ${s.backdropHigh} ${s.backdropAt} ${s.backdrop}`);
  for (const x of [-s.sideAt, s.sideAt]) await run(`fill ${x} 0 ${s.backdropAt} ${x} ${s.backdropHigh} ${s.partnerAt + 30} ${s.backdrop}`);
  return problems;
}

// ---- running scenes -----------------------------------------------------------------------

const sleep = shots.sleep;

/**
 * Repaints the floor round a gate and sweeps up what it dropped: a build puts the campus's floor block back where it made room,
 * and a flush gate taken down leaves its trench, so the studio floor would show patches and pits.
 */
async function tidy(kit, geom, dim = O) {
  const b = kit.siteBox(geom);
  await kit.srv.run(`execute in ${dim} run fill ${b.x0} -1 ${b.z0} ${b.x1} -1 ${b.z1} ${STUDIO.floor} replace ${FLOOR_AFTER_BUILD}`);
  // What a removed gate drops: an item is an entity the viewer cannot draw, and shows as a magenta square.
  await kit.srv.run(`execute in ${dim} run kill @e[type=minecraft:item,x=0,y=0,z=${STUDIO.plane},distance=..150]`);
}

/**
 * Takes each scene: { name, file, ok, detail }. `fac` has the server (`srv`), Probe and the
 * version; `viewer` is lib/viewer.js's running viewer; pictures go to `outDir`. `slow` is how many
 * times slower than real time a reel's server runs (1 for none; a server before 1.20.3 cannot).
 */
async function takeScenes(fac, viewer, scenes, outDir, {
  log = console.log, width = 1280, height = 720, reelWidth = 800, reelHeight = 450, slow = 4, settleMs = 60000, launch = null,
} = {}) {
  const srv = fac.srv;
  const kit = new GateKit(srv);
  const probe = fac.probe;
  const bot = probe.bot;
  const canSlow = atLeast(fac.version, '1.20.3');
  const factor = canSlow ? slow : 1;
  fs.mkdirSync(outDir, { recursive: true });
  const problems = await buildStudio(srv);
  for (const p of problems) log(`  studio problem: ${p}`);
  for (const name of [SUBJECT, PARTNER]) if (await kit.exists(name)) await kit.remove(name);
  const partner = await kit.build(PARTNER, kit.place('Standard', 'south', { cx: 0, openingAt: STUDIO.partnerAt }), { net: NET });
  await tidy(kit, partner.geom);
  log(`  studio ready; partner ${PARTNER}: ${partner.text}`);
  const browser = await shots.launchBrowser(launch);
  const out = [];
  const state = { shape: null, geom: null };
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));

  /** The subject gate of `s`: rebuilt if its shape changed, redressed in its group and ring pattern. */
  const subject = async (s) => {
    if (state.shape !== s.shape) {
      if (state.shape) {
        await kit.remove(SUBJECT);
        await kit.restoreSite(state.geom);
        await tidy(kit, state.geom);
      }
      state.geom = kit.place(s.shape, 'south', { cx: 0, openingAt: STUDIO.plane });
      await kit.build(SUBJECT, state.geom, { net: NET });
      await tidy(kit, state.geom);
      state.shape = s.shape;
    }
    await kit.edit(SUBJECT, 'group', s.group);
    await kit.edit(SUBJECT, 'spin', s.spin || 'default');
    return state.geom;
  };

  const shut = async () => {
    await kit.force(SUBJECT);
    await kit.force(PARTNER);
    await kit.waitShut(probe, state.geom, 8000);
  };

  /** Puts Probe at the camera and opens the viewer's page on what it sees; resolves once it has painted. */
  const frame = async (geom, view, token) => {
    await page.goto('about:blank');
    const cam = camera(geom, view);
    bot.creative.startFlying();
    await probe.teleport(cam, O);
    bot.creative.startFlying();
    await bot.waitForChunksToLoad();
    errors.length = 0;
    const chunks = () => viewer.chunks(token);
    await page.goto(`${viewer.url}first/?shot=${token}`, { waitUntil: 'load' });
    await page.waitForSelector('canvas', { timeout: 15000 });
    await sleep(3000);
    return chunks;
  };

  /** Waits until Probe is shown anything in the opening: the first block of the woosh or the portal. */
  const firstDrawn = async (geom, ms) => {
    const end = Date.now() + ms;
    const drawn = (c) => {
      const b = bot.blockAt(new Vec3(c.x, c.y, c.z));
      return b && b.name !== 'air' && b.name !== 'cave_air';
    };
    while (Date.now() < end) {
      if (geom.opening.some(drawn)) return true;
      await sleep(25);
    }
    throw new Error('the opening was never drawn');
  };

  const dial = async () => {
    const said = (await kit.dial(SUBJECT, PARTNER)).text;
    if (!/connected/i.test(said)) throw new Error(`the dial was refused: ${said || 'no answer'}`);
  };

  try {
    for (const [i, s] of scenes.entries()) {
      const t0 = Date.now();
      const view = s.kind === 'reel' ? { w: reelWidth, h: reelHeight } : { w: width, h: height };
      try {
        await page.setViewport({ width: view.w, height: view.h });
        const geom = await subject(s.subject);
        await shut();
        const token = `g${process.pid}-${i}-${Date.now()}`;
        const chunks = await frame(geom, s.view, token);
        const held = await shots.settle({ shoot: () => page.screenshot({ type: 'png' }), chunks, settleMs: Math.max(0, settleMs - 3000) });
        if (!held.still) throw new Error('the picture never held still before the scene began');
        if (s.kind === 'still') {
          if (s.state === 'open') {
            await dial();
            await kit.waitOpen(probe, geom, 20000);
          }
          const png = (await shots.settle({ shoot: () => page.screenshot({ type: 'png' }), chunks, settleMs: 20000 })).png;
          const file = path.join(outDir, `${s.name}.png`);
          fs.writeFileSync(file, png);
          const share = await shots.shareIn(page, png);
          const verdict = shots.judge({ still: true, chunks: chunks(), chunksBefore: chunks(), share, errors });
          const detail = `${((Date.now() - t0) / 1000).toFixed(0)} s, ${(100 * share).toFixed(0)}% scene${verdict.ok ? '' : `; ${verdict.why.join('; ')}`}`;
          out.push({ name: s.name, file, ok: verdict.ok, detail });
          log(`  ${verdict.ok ? 'still' : 'STILL PROBLEM'} ${s.name}: ${file} (${detail})`);
        } else {
          const file = path.join(outDir, `${s.name}.gif`);
          const rec = await reel.startReel(page, { width: view.w, height: view.h });
          let frames;
          let openedAt = Date.now();
          try {
            if (canSlow) await srv.run(`tick rate ${Math.max(1, Math.round(20 / factor))}`);
            await sleep(500);
            await dial();
            await firstDrawn(geom, 60000 * factor);
            openedAt = Date.now();
            await kit.waitOpen(probe, geom, 60000 * factor);
            await sleep(1000);
          } finally {
            if (canSlow) await srv.run('tick rate 20');
            frames = await rec.stop();
          }
          if (s.action === 'kawoosh') frames = reel.from(frames, openedAt - 500 * factor);
          const written = await reel.writeGif(file, frames, { slow: factor });
          const ok = written.frames >= 5 && errors.length === 0;
          const detail = `${((Date.now() - t0) / 1000).toFixed(0)} s, ${written.frames} frames, ${written.seconds.toFixed(1)} s at real speed, ${(written.bytes / 1048576).toFixed(1)} MB${errors.length ? `; page errors: ${errors.slice(0, 3).join(' / ')}` : ''}`;
          out.push({ name: s.name, file, ok, detail });
          log(`  ${ok ? 'reel' : 'REEL PROBLEM'} ${s.name}: ${file} (${detail})`);
        }
      } catch (e) {
        out.push({ name: s.name, file: null, ok: false, detail: e.message });
        log(`  SCENE FAILED ${s.name}: ${e.message}`);
        if (canSlow) await srv.run('tick rate 20').catch(() => {});
      }
    }
  } finally {
    await browser.close().catch(() => {});
    try { bot.creative.stopFlying(); } catch { /* left */ }
    if (canSlow) await srv.run('tick rate 20').catch(() => {});
    for (const name of [SUBJECT, PARTNER]) await kit.remove(name).catch(() => {});
    await probe.teleport(campus.TRANSIT.home).catch(() => {});
  }
  return out;
}

/** Scenes as self-test results, section 'gallery'. */
function asResults(done) {
  return done.map((s) => ({ section: 'gallery', name: s.name, ok: s.ok, detail: s.file ? `${s.file} (${s.detail})` : s.detail }));
}

module.exports = { STUDIO, SPINS, SHAPES, groups, catalog, select, lookAt, camera, buildStudio, takeScenes, asResults, EYE };
