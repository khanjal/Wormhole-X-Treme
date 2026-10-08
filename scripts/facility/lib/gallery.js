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

const studio = require('./studio');

const { O, STUDIO, forceRect, EYE, lookAt, standAt, buildStudio, isAir, must, slowdown } = studio;

const SUBJECT = 'StudioA';
const PARTNER = 'StudioB';
const NET = 'Studio';

/** The ring patterns, as `gate-dial-spin` takes them (all but `none`, which has no ring light to show). */
const SPINS = ['top', 'chevron', 'lap', 'fill', 'pegasus', 'chase', 'universe', 'overshoot'];
/** The shapes the console can build, in the order the guide shows them. */
const SHAPES = ['Massive', 'Grand', 'Large', 'Standard', 'Minimal', 'Horizontal'];

const CONFIG = path.resolve(__dirname, '..', '..', '..', 'src', 'main', 'resources', 'config.yml');
/** The group a console build is made in, and so the one a gate needs no redressing for. */
const DEFAULT_GROUP = 'Standard';

/** The material groups the shipped config.yml names, each with its keys: { Standard: { structure: 'OBSIDIAN', ... }, ... }. */
function readGroups(config = CONFIG) {
  const out = {};
  let inside = false;
  let group = null;
  for (const line of fs.readFileSync(config, 'utf8').split(/\r?\n/)) {
    if (/^gate-material-groups:/.test(line)) { inside = true; continue; }
    if (!inside) continue;
    const g = /^ {2}([A-Za-z][\w-]*):\s*$/.exec(line);
    const kv = /^ {4}([\w-]+):\s*["']?([^\s"'#]+)["']?\s*(?:#.*)?$/.exec(line);
    if (g) { group = g[1]; out[group] = {}; } else if (kv && group) out[group][kv[1]] = kv[2];
    else if (/^\S/.test(line)) break;
  }
  return out;
}

/** The names of the material groups the shipped config.yml has. */
function groups(config = CONFIG) {
  return Object.keys(readGroups(config));
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
    out.push({ name: `kawoosh-${shape.toLowerCase()}`, kind: 'reel', action: 'kawoosh', trim: 500, subject: gate(shape), view: shapes.isFlat(shape) ? 'high' : 'quarter' });
  }
  const other = (list) => list.flatMap((f) => f.scenes());
  return [...out, ...other(Object.values(families()))];
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
  return standAt(eye, c);
}

// ---- running scenes -----------------------------------------------------------------------

const sleep = shots.sleep;

/** Repaints the floor round a gate and sweeps up what it dropped (see studio.tidyBox). */
async function tidy(kit, geom, dim = O) {
  await studio.tidyBox(kit.srv, kit.siteBox(geom), dim);
}

/**
 * The cells the woosh can be drawn in: the opening and the cells one and two out from it along the
 * gate's normal, and above it (a gate lying flat sends its woosh up). Those that are not air before
 * the dial (the frame, say) are left out by the caller.
 */
function wooshCells(geom) {
  const n = geom.normal;
  const out = [];
  for (const c of geom.opening) {
    out.push(c);
    for (const k of [1, 2]) {
      out.push({ x: c.x + n.x * k, y: c.y, z: c.z + n.z * k });
      out.push({ x: c.x, y: c.y + k, z: c.z });
    }
  }
  return out;
}

/**
 * Dresses a gate built in the default group in another group's frame: a console build always
 * uses the default group's blocks, and `gate edit group` changes only what the gate draws (its
 * light, portal and iris), so the structure and chevron blocks are put in by hand and the gate
 * detected afresh.
 */
async function dress(kit, geom, group, config = readGroups()) {
  const mats = config[group];
  if (!mats) throw new Error(`the shipped config.yml has no material group ${group}`);
  if (!mats.structure) throw new Error(`material group ${group} has no structure block in config.yml`);
  for (const b of geom.blocks) {
    const m = (b.role === 'chevron' && mats.chevron) || mats.structure;
    await kit.srv.run(`execute in ${O} run setblock ${b.x} ${b.y} ${b.z} minecraft:${m.toLowerCase()}`);
  }
  const said = (await kit.say(`wormhole gate regen ${SUBJECT}`)).text;
  // "Re-detected ... from its frame": the other answer keeps the recorded gate, whose frame is not the group's.
  if (!/re-detected/i.test(said)) throw new Error(`the gate was not re-detected in ${group}'s frame: ${said || 'no answer'}`);
  const edit = (await kit.edit(SUBJECT, 'group', group)).text;
  if (/no material group/i.test(edit)) throw new Error(`the server has no material group ${group} (its config.yml lacks it): ${edit}`);
  return `${said} / ${edit}`;
}

/**
 * The scene families other than gates: lib/gallery-rings.js, lib/gallery-mirrors.js. A family is
 * { scenes(), prepare(ctx, scene) -> { camera, ... }, act?(ctx, scene, prepared), cleanup?(ctx,
 * scene, prepared), finish?(ctx) }. `prepare` builds what the scene shows and says where Probe stands; `act` is
 * what happens while a reel is recorded, or before an open still is taken, and calls `ctx.mark()`
 * where a scene with `trim` should begin; `cleanup` runs after the scene, whatever happened, and
 * `finish` once at the end of the run, for what a family keeps standing between its scenes.
 */
function families() {
  return { ring: require('./gallery-rings'), mirror: require('./gallery-mirrors') };
}

/**
 * Takes each scene: { name, file, ok, detail }. `fac` has the server (`srv`), Probe and the
 * version; `viewer` is lib/viewer.js's running viewer; pictures go to `outDir`. `slow` is how many
 * times slower than real time a reel's server runs, 1 to 20 (1 for none; a server before 1.20.3
 * cannot be slowed, and records at full speed).
 */
async function takeScenes(fac, viewer, scenes, outDir, {
  log = console.log, width = 1280, height = 720, reelWidth = 800, reelHeight = 450, slow = 4, settleMs = 60000, launch = null,
} = {}) {
  const canSlow = atLeast(fac.version, '1.20.3');
  const { rate, factor } = slowdown(slow, canSlow);
  const srv = fac.srv;
  const kit = new GateKit(srv);
  const probe = fac.probe;
  const bot = probe.bot;
  const config = readGroups();
  fs.mkdirSync(outDir, { recursive: true });
  const out = [];
  const state = { key: null, geom: null };
  const errors = [];
  let browser = null;
  let flying = false;
  const used = new Set();

  /** Takes the subject gate down and puts the floor back, for a scene of another family or another gate. */
  const clearGate = async () => {
    if (!state.key) return;
    await kit.remove(SUBJECT);
    await kit.restoreSite(state.geom);
    await tidy(kit, state.geom);
    state.key = null;
  };

  /** The subject gate of `s`: rebuilt if its shape or group changed, redressed, set to its ring pattern. */
  const subject = async (s) => {
    const key = `${s.shape}/${s.group}`;
    if (state.key !== key) {
      await clearGate();
      state.geom = kit.place(s.shape, 'south', { cx: 0, openingAt: STUDIO.plane });
      await kit.build(SUBJECT, state.geom, { net: NET });
      await tidy(kit, state.geom);
      if (s.group !== DEFAULT_GROUP) await dress(kit, state.geom, s.group, config);
      state.key = key;
    }
    await kit.edit(SUBJECT, 'spin', s.spin || 'default');
    return state.geom;
  };

  const shut = async () => {
    await kit.force(SUBJECT);
    await kit.force(PARTNER);
    await kit.waitShut(probe, state.geom, 8000);
  };

  /** Puts Probe at `cam` and opens the viewer's page on what it sees; resolves once it has painted. */
  const frame = async (page, cam, token) => {
    await page.goto('about:blank');
    bot.creative.startFlying();
    flying = true;
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

  /**
   * Waits until Probe is shown a block where there was air before the dial, in the opening or one
   * or two blocks out from it (in front, and above for a gate lying flat): the first block of the
   * woosh, which the plugin draws out from the opening before it fills it.
   */
  const firstDrawn = async (geom, ms) => {
    const watched = wooshCells(geom).filter((c) => isAir(bot.blockAt(new Vec3(c.x, c.y, c.z))));
    const end = Date.now() + ms;
    while (Date.now() < end) {
      if (watched.some((c) => !isAir(bot.blockAt(new Vec3(c.x, c.y, c.z))))) return true;
      await sleep(25);
    }
    throw new Error('the woosh never began: nothing was drawn in or in front of the opening');
  };

  const dial = async () => {
    const said = (await kit.dial(SUBJECT, PARTNER)).text;
    if (!/connected/i.test(said)) throw new Error(`the dial was refused: ${said || 'no answer'}`);
  };

  /** What a scene may use: the server, Probe, the clocks, and `mark()` for where a trimmed reel begins. */
  const ctx = {
    srv, probe, bot, kit, log, sleep, factor, canSlow, clearGate, markAt: null, dim: O, home: campus.TRANSIT.home,
    second: () => fac.second(),
    mark() { this.markAt = Date.now(); },
  };

  /** The gate family: the subject gate, and the dial that opens it. */
  const gates = {
    async prepare(_ctx, s) {
      const geom = await subject(s.subject);
      await shut();
      return { camera: camera(geom, s.view), geom };
    },
    async act(_ctx, s, prep) {
      await dial();
      const ms = s.kind === 'reel' ? 60000 * factor : 20000;
      if (s.kind === 'reel') {
        await firstDrawn(prep.geom, ms);
        ctx.mark();
      }
      await kit.waitOpen(probe, prep.geom, ms);
    },
  };

  /** One still: the picture once it holds, judged as a shot is. */
  const still = async (page, s, family, prep, chunks, t0) => {
    if (s.state === 'open' && family.act) await family.act(ctx, s, prep);
    const held = await shots.settle({ shoot: () => page.screenshot({ type: 'png' }), chunks, settleMs: 20000 });
    const file = path.join(outDir, `${s.name}.png`);
    fs.writeFileSync(file, held.png);
    const share = await shots.shareIn(page, held.png);
    const verdict = shots.judge({ still: held.still, chunks: chunks(), chunksBefore: held.chunksBefore, share, errors });
    const detail = `${((Date.now() - t0) / 1000).toFixed(0)} s, ${(100 * share).toFixed(0)}% scene${verdict.ok ? '' : `; ${verdict.why.join('; ')}`}`;
    return { name: s.name, file, ok: verdict.ok, detail, label: verdict.ok ? 'still' : 'STILL PROBLEM' };
  };

  /** One reel: what the family does, the server slowed throughout, from a little before `mark()` if the scene says so. */
  const makeReel = async (page, s, family, prep, size, t0) => {
    const file = path.join(outDir, `${s.name}.gif`);
    const rec = await reel.startReel(page, { width: size.w, height: size.h });
    let frames = [];
    ctx.markAt = null;
    try {
      if (canSlow) await must(srv, `tick rate ${rate}`, /tick rate/i);
      await sleep(500);
      await family.act(ctx, s, prep);
      await sleep(1000);
    } finally {
      // The recording first: if the server has gone, the screencast must not be left running.
      frames = await rec.stop();
      if (canSlow) await srv.run('tick rate 20').catch(() => {});
    }
    if (s.trim && ctx.markAt) frames = reel.from(frames, ctx.markAt - s.trim * factor);
    const written = await reel.writeGif(file, frames, { slow: factor });
    const moved = frames.length > 1 && !frames[0].png.equals(frames[frames.length - 1].png);
    const ok = written.frames >= 5 && moved && errors.length === 0;
    const detail = `${((Date.now() - t0) / 1000).toFixed(0)} s, ${written.frames} frames, ${written.seconds.toFixed(1)} s at real speed, ${(written.bytes / 1048576).toFixed(1)} MB${moved ? '' : '; the first frame and the last are the same'}${errors.length ? `; page errors: ${errors.slice(0, 3).join(' / ')}` : ''}`;
    return { name: s.name, file, ok, detail, label: ok ? 'reel' : 'REEL PROBLEM' };
  };

  try {
    const problems = await buildStudio(srv);
    for (const p of problems) log(`  studio problem: ${p}`);
    for (const name of [SUBJECT, PARTNER]) if (await kit.exists(name)) await kit.remove(name);
    const partner = await kit.build(PARTNER, kit.place('Standard', 'south', { cx: 0, openingAt: STUDIO.partnerAt }), { net: NET });
    await tidy(kit, partner.geom);
    log(`  studio ready; partner ${PARTNER}: ${partner.text}`);
    browser = await shots.launchBrowser(launch);
    const page = await browser.newPage();
    page.on('pageerror', (e) => errors.push(e.message));
    for (const [i, s] of scenes.entries()) {
      const t0 = Date.now();
      const size = s.kind === 'reel' ? { w: reelWidth, h: reelHeight } : { w: width, h: height };
      const family = s.family ? families()[s.family] : gates;
      used.add(family);
      let prep = null;
      try {
        await page.setViewport({ width: size.w, height: size.h });
        if (s.family) await clearGate();
        prep = await family.prepare(ctx, s);
        const token = `g${process.pid}-${i}-${Date.now()}`;
        const chunks = await frame(page, prep.camera, token);
        const held = await shots.settle({ shoot: () => page.screenshot({ type: 'png' }), chunks, settleMs: Math.max(0, settleMs - 3000) });
        if (!held.still) throw new Error('the picture never held still before the scene began');
        const done = s.kind === 'still' ? await still(page, s, family, prep, chunks, t0) : await makeReel(page, s, family, prep, size, t0);
        out.push({ name: done.name, file: done.file, ok: done.ok, detail: done.detail });
        log(`  ${done.label} ${s.name}: ${done.file} (${done.detail})`);
      } catch (e) {
        out.push({ name: s.name, file: null, ok: false, detail: e.message });
        log(`  SCENE FAILED ${s.name}: ${e.message}`);
        if (canSlow) await srv.run('tick rate 20').catch(() => {});
      } finally {
        if (prep && family.cleanup) await family.cleanup(ctx, s, prep).catch((e) => log(`  cleanup after ${s.name}: ${e.message}`));
      }
    }
  } finally {
    if (browser) await browser.close().catch(() => {});
    if (flying) { try { bot.creative.stopFlying(); } catch { /* left */ } }
    if (canSlow) await srv.run('tick rate 20').catch(() => {});
    for (const f of used) if (f.finish) await f.finish(ctx).catch((e) => log(`  finishing a scene family: ${e.message}`));
    for (const name of [SUBJECT, PARTNER]) await kit.remove(name).catch(() => {});
    if (flying) await probe.teleport(campus.TRANSIT.home).catch(() => {});
    // The studio's chunks are not kept loaded for a self-test or a kept world that follows.
    await srv.run(`execute in ${O} run forceload remove ${forceRect().join(' ')}`).catch(() => {});
  }
  return out;
}

/** Scenes as self-test results, section 'gallery'. */
function asResults(done) {
  return done.map((s) => ({ section: 'gallery', name: s.name, ok: s.ok, detail: s.file ? `${s.file} (${s.detail})` : s.detail }));
}

module.exports = { STUDIO, SPINS, SHAPES, DEFAULT_GROUP, readGroups, groups, catalog, select, lookAt, camera, buildStudio, forceRect, wooshCells, dress, must, slowdown, standAt, takeScenes, asResults, EYE };
