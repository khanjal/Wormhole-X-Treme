'use strict';
// The Map Desk (#236, Wormhole's markers on Dynmap): the #236 checklist, one case a cell, against
// a jar built with the integration and Dynmap installed by run-facility --with dynmap. Every
// marker is read back from Dynmap itself (lib/dynmap.js: its own `dmarker` console commands),
// waiting up to lib/dynmap.js REDRAW_MS after each change (Wormhole redraws on a 100-tick poll).
//
// Where: G1's cell. MapA stands on G1's Stand position and MapB beside it, both Standard, flush;
// two slab circles make a ring pair on the cell's floor; a short stone wall at its north end holds
// a mirror's banner. Probe saves the public beam destination; Probe2 its own place, which must
// never be drawn. A desk has no cell of its own: cleanup takes everything down and runs G1's own
// reset function, so G1 finds its cell as built.
//
// Cases that restart the server: `iris hidden` (map-show-iris-gates false), `layer off`
// (map-show-rings false), `off` (dynmap-enabled false), `restart` (the markers come back once) and
// the paired run `absent` (dynmap-enabled true, Dynmap not installed). A setting's case owes a
// second restart, with the setting put back, which its cleanup pays.
//
// Not automatic: the checklist's `/dynmap reload` (Dynmap 3.7 and 3.8 have no reload subcommand,
// and nothing else reloads Dynmap alone), renaming a gate (`gate edit` has no name field; the
// cell expects that as a known failure), the icons' look at normal zoom and the popup's rendering
// (the tester, at the web map), and the main-thread check (a profiler).

const campus = require('../lib/campus');
const { GateKit } = require('../lib/gatekit');
const { RingKit } = require('../lib/rings');
const { MirrorKit } = require('../lib/mirrors');
const { BeamKit } = require('../lib/beams');
const { MapReader, SETS, LABELS } = require('../lib/dynmap');
const { deskLayout } = require('../lib/blueprint');
const { ticks } = require('../lib/probe');
const relay = require('./relay');
const { httpText } = require('../lib/http');
const server = require('../lib/server');

const DYNMAP_ABSENT = 'dynmap-enabled is set but Dynmap was not found. Nothing is shown on a map.';

const def = campus.chamber('map');
const O = campus.OVERWORLD;
const NET = 'Maps';
const kit0 = new GateKit(null);
const STAND = campus.GATES.stand;
const GATES = {
  MapA: kit0.place('Standard', STAND.facing, STAND),
  MapB: kit0.place('Standard', STAND.facing, { ...STAND, cx: 13.5 }),
};
const RING_ENDS = [{ x: -12, y: 0, z: -112 }, { x: 12, y: 0, z: -112 }];
const RING_SLAB = 'polished_andesite_slab';
// At head height, as every facility mirror is (campus.MIRRORS).
const MIRROR = { dim: O, x: 0, y: 1, z: -136, facing: 'south' };
const WALL = { x0: -3, x1: 3, y0: 0, y1: 4, z: -137 };
// Block positions: BeamKit.save stands a player on the block's centre.
const BEAM_AT = { x: 0, y: 0, z: -111, yaw: 180 };
const PLACE_AT = { x: 4, y: 0, z: -111, yaw: 180 };
const HOME = { x: 0.5, y: 0, z: -118.5, yaw: 180 };
const IDC = '2360';
const RAW = '<b>x</b>';
const ESCAPED = '&lt;b&gt;x&lt;/b&gt;';
const OPEN = 'wormhole_gate_open';
const IDLE = 'wormhole_gate_idle';

const v = (value, why) => ({ value, label: value, why });
const CASES = [
  v('gate', '1: a gate is drawn: its point on the opening, labelled, idle; an area over the opening; the popup has network and owner'),
  v('dial', '2: dialled: both idle while the chevrons lock, both open with a cyan line once the wormhole forms, both idle and no line once shut'),
  v('cross-world', '3: a pair across worlds: both open, no line'),
  v('rename', '4: gate edit <gate> name: the label moves, nothing left under the old name'),
  v('escaped', '5: an owner and a network called <b>x</b> show literally in the popup, in Dynmap and on the web map'),
  v('iris hidden', '6: map-show-iris-gates false and a restart: a gate with an iris code is not drawn, and the gate it dials stays idle with no line'),
  v('rings', '7: a ring pair: a point at each end and a grey line; a name relabels its end; removed, all go'),
  v('beams', '8: a public destination is drawn; a player\'s own place never is; removed, it goes'),
  v('mirrors', '9: a mirror is drawn at its banner, with no line; made again under a new name at the same banner, relabelled'),
  v('remove', '10: gate remove takes its point, area and line'),
  v('restart', '11: a restart: every marker back exactly once, a gate removed before the stop not among them'),
  v('reload', '12: /dynmap reload: all four layers return'),
  v('layer off', '13: map-show-rings false and a restart: that layer is gone entirely, the others stay'),
  v('off', '14: dynmap-enabled false and a restart: no layers, and nothing logged about Dynmap'),
  v('absent', '14 (a run without Dynmap): dynmap-enabled true with Dynmap absent: one startup warning, and gates work'),
];

/** The setting each restarting case holds; each owes a second restart, with it put back. */
const SETTINGS = {
  'iris hidden': { 'map-show-iris-gates': 'false' },
  'layer off': { 'map-show-rings': 'false' },
  off: { 'dynmap-enabled': 'false' },
  absent: { 'dynmap-enabled': 'true' },
};
const state = { restartOwed: null };

function refuses(o, version, fac) {
  if (o.case === 'reload') return 'Dynmap 3.7 and 3.8 have no /dynmap reload (their subcommands stop at render, purge, pause, stats and the like), and nothing else reloads Dynmap alone';
  if (!fac) return null;
  if (o.case === 'absent') return fac.has('dynmap') ? 'the absent case is the paired run: start the facility without Dynmap (--with anything but it)' : null;
  if (!fac.has('dynmap')) return 'needs Dynmap: start the facility --with dynmap (Dynmap has no build for 26.x: run it on 1.21.11)';
  return null;
}

// ---- helpers --------------------------------------------------------------------------------

function near(p, q, within = 0.75) {
  return Math.abs(p.x - q.x) <= within && Math.abs(p.y - q.y) <= within && Math.abs(p.z - q.z) <= within;
}

/** Builds a gate by the console form, flush; its answer goes on `observed.builds` for the checks. */
async function buildGate(ctx, name, geom, { net = NET, idc = null } = {}) {
  const kit = new GateKit(ctx.server);
  if (await kit.exists(name)) await kit.remove(name);
  const text = (await kit.build(name, geom, { dim: O, net, idc, floorY: STAND.floorY })).text;
  (ctx.observed.builds = ctx.observed.builds || []).push(text);
  return text;
}

async function layRings(ctx) {
  const kit = new RingKit(ctx.server, ctx.facility.probe);
  for (const a of RING_ENDS) {
    await kit.clear('ODD', a);
    await kit.lay('ODD', a, RING_SLAB);
  }
  return kit.build(RING_ENDS[0], RING_ENDS[1]);
}

async function hangMirror(ctx, name) {
  const w = WALL;
  await ctx.server.run(`execute in ${O} run fill ${w.x0} ${w.y0} ${w.z} ${w.x1} ${w.y1} ${w.z} minecraft:stone`);
  const kit = new MirrorKit(ctx.server);
  await kit.banner(MIRROR);
  return kit.create(name, MIRROR);
}

/** The icons read every half second while a dial forms, until the opening is drawn to Probe: [{ a, b, drawnA }]. */
async function watchDial(ctx, reader, from, to, geomFrom, ms = 20000) {
  const { Vec3 } = require('vec3');
  const probe = ctx.facility.probe;
  const shown = (c) => { const b = probe.bot.blockAt(new Vec3(c.x, c.y, c.z)); return b && !['air', 'cave_air'].includes(b.name); };
  const samples = [];
  const end = Date.now() + ms;
  let firstDrawn = null;
  while (Date.now() < end) {
    const g = await reader.points(SETS.gates) || {};
    const t = Date.now();
    if (firstDrawn === null && geomFrom.opening.some(shown)) firstDrawn = t;
    samples.push({ t, a: g[from] && g[from].icon, b: g[to] && g[to].icon, drawnA: geomFrom.opening.every(shown) });
    if (samples[samples.length - 1].drawnA) break;
    await ticks(10);
  }
  // The kawoosh and the redraw it asks for reach Probe's client and Dynmap in either order, so
  // only a read more than a second before any of the opening was drawn is one while locking.
  samples.locking = firstDrawn === null ? [] : samples.filter((x) => x.t < firstDrawn - 1000);
  return samples;
}

/** Reads the markers for `ms` more, every half second: what `pick` makes of each read. */
async function hold(reader, pick, ms = 9000) {
  const seen = [];
  const end = Date.now() + ms;
  while (Date.now() < end) {
    seen.push(pick(await reader.snapshot()));
    await ticks(10);
  }
  return seen;
}

// ---- stage, run, checks --------------------------------------------------------------------

async function stage(ctx, o) {
  const obs = ctx.observed;
  obs.case = o.case;
  if (SETTINGS[o.case]) {
    // The setting (needs.config) is in force only after a full restart.
    obs.restartFrom = await ctx.facility.restart(`${Object.entries(SETTINGS[o.case]).map(([k, x]) => `${k} ${x}`).join(', ')} (Map Desk: ${o.case})`);
    state.restartOwed = o.case;
  }
  await ctx.facility.probe.teleport(HOME, O);
}

async function run(ctx, o) {
  const obs = ctx.observed;
  const fac = ctx.facility;
  const reader = new MapReader(ctx.server);
  const kit = new GateKit(ctx.server);
  switch (o.case) {
    case 'gate': {
      await ctx.step('MapA is built');
      obs.build = await buildGate(ctx, 'MapA', GATES.MapA);
      obs.owner = (await kit.edit('MapA', 'owner', fac.probe.name)).text;
      obs.snap = (await reader.until((s) => s.gates && s.gates.mapa && s.gateAreas && s.gateAreas.mapa)).snap;
      // The owner is set after the build: wait for the popup to carry it.
      const end = Date.now() + 9000;
      do {
        obs.desc = await reader.desc(SETS.gates, 'mapa');
        if (/Owner: /.test(obs.desc)) break;
        await ticks(10);
      } while (Date.now() < end);
      obs.areaDesc = await reader.desc(SETS.gates, 'mapa', 'area');
      break;
    }
    case 'dial':
    case 'remove': {
      obs.build = `${await buildGate(ctx, 'MapA', GATES.MapA)} / ${await buildGate(ctx, 'MapB', GATES.MapB)}`;
      obs.before = (await reader.until((s) => s.gates && s.gates.mapa && s.gates.mapb)).snap;
      await ctx.step('MapA dials MapB');
      obs.dial = (await kit.dial('MapA', 'MapB')).text;
      obs.samples = await watchDial(ctx, reader, 'mapa', 'mapb', GATES.MapA);
      obs.open = await reader.until((s) => s.gates && s.gates.mapa && s.gates.mapb && s.gates.mapa.icon === OPEN && s.gates.mapb.icon === OPEN && s.gateLines && s.gateLines['mapa|mapb']);
      if (o.case === 'dial') {
        await ctx.step('MapA is shut');
        obs.shutText = (await kit.force('MapA')).text;
        obs.shut = await reader.until((s) => s.gates && s.gates.mapa && s.gates.mapb && s.gates.mapa.icon === IDLE && s.gates.mapb.icon === IDLE && s.gateLines && !s.gateLines['mapa|mapb']);
      } else {
        await ctx.step('MapA is removed, open');
        obs.removed = (await kit.say('wormhole gate remove MapA -destroy')).text;
        obs.gone = await reader.until((s) => s.gates && !s.gates.mapa && s.gateAreas && !s.gateAreas.mapa && s.gateLines && !Object.keys(s.gateLines).some((id) => id.split('|').includes('mapa')));
      }
      break;
    }
    case 'cross-world': {
      obs.build = await buildGate(ctx, 'MapA', GATES.MapA, { net: null });
      if (!(await kit.exists('Range'))) await relay.fixture(ctx);
      await kit.force('Range');
      await ctx.step('MapA dials the Range, in the nether');
      obs.dial = (await kit.dial('MapA', 'Range')).text;
      obs.drawn = await kit.waitOpen(fac.probe, GATES.MapA).catch((e) => { obs.openError = e.message; return null; });
      obs.open = await reader.until((s) => s.gates && s.gates.mapa && s.gates.range && s.gates.mapa.icon === OPEN && s.gates.range.icon === OPEN);
      // Held for a full redraw after both were drawn open: a line must not appear at any point.
      obs.lines = await hold(reader, (s) => Object.keys(s.gateLines || {}));
      break;
    }
    case 'rename': {
      obs.build = await buildGate(ctx, 'MapA', GATES.MapA);
      await reader.until((s) => s.gates && s.gates.mapa);
      await ctx.step('gate edit MapA name MapZ');
      obs.rename = (await kit.edit('MapA', 'name', 'MapZ')).text;
      obs.after = await reader.until((s) => s.gates && s.gates.mapz && s.gates.mapz.label === 'MapZ' && !s.gates.mapa);
      break;
    }
    case 'escaped': {
      obs.build = await buildGate(ctx, 'MapX', GATES.MapA, { net: RAW });
      obs.owner = (await kit.edit('MapX', 'owner', RAW)).text;
      obs.drawnX = (await reader.until((s) => s.gates && s.gates.mapx)).ok;
      const end = Date.now() + 9000;
      do {
        obs.desc = await reader.desc(SETS.gates, 'mapx');
        if (/Owner: /.test(obs.desc)) break;
        await ticks(10);
      } while (Date.now() < end);
      obs.areaDesc = await reader.desc(SETS.gates, 'mapx', 'area');
      // The web map's own copy, which a browser renders: Dynmap writes it a little after a change.
      const url = `http://127.0.0.1:${fac.mapPort}/tiles/_markers_/marker_world.json`;
      const until = Date.now() + 20000;
      for (;;) {
        try {
          const j = JSON.parse(await httpText(url));
          const m = j.sets && j.sets[SETS.gates] && j.sets[SETS.gates].markers && j.sets[SETS.gates].markers.mapx;
          if (m && /Owner: /.test(m.desc || '')) { obs.webDesc = m.desc; break; }
        } catch (e) { obs.webError = e.message; }
        if (Date.now() > until) break;
        await ticks(20);
      }
      break;
    }
    case 'iris hidden': {
      obs.build = `${await buildGate(ctx, 'MapA', GATES.MapA, { idc: IDC })} / ${await buildGate(ctx, 'MapB', GATES.MapB)}`;
      obs.before = (await reader.until((s) => s.gates && s.gates.mapb)).snap;
      await ctx.step('MapA, hidden, dials MapB');
      obs.dial = (await kit.dial('MapA', 'MapB')).text;
      obs.drawn = await kit.waitOpen(fac.probe, GATES.MapB).catch((e) => { obs.openError = e.message; return null; });
      // Held for a full redraw and more: MapB must stay idle and no line appear, the whole time.
      obs.seen = await hold(reader, (s) => ({
        a: Boolean(s.gates && s.gates.mapa), aArea: Boolean(s.gateAreas && s.gateAreas.mapa),
        b: s.gates && s.gates.mapb && s.gates.mapb.icon, lines: Object.keys(s.gateLines || {}),
      }));
      break;
    }
    case 'rings': {
      const rk = new RingKit(ctx.server, fac.probe);
      await ctx.step('a ring pair is built');
      obs.pair = await layRings(ctx);
      const id = obs.pair.id;
      obs.drawn = await reader.until((s) => s.rings && s.rings[`${id}:a`] && s.rings[`${id}:b`] && s.ringLines && s.ringLines[id]);
      await ctx.step('one end is named');
      await fac.probe.teleport({ x: RING_ENDS[0].x + 0.5, y: 0, z: RING_ENDS[0].z + 0.5, yaw: 180 }, O);
      obs.named = await rk.ask('/wormhole ring edit name MapRing', { until: /This ring is now|Stand in/ });
      obs.relabelled = await reader.until((s) => s.rings && Object.values(s.rings).some((m) => m.label === 'MapRing'));
      await fac.probe.teleport(HOME, O);
      await ctx.step('the pair is removed');
      obs.removedText = await rk.remove(id);
      obs.removed = await reader.until((s) => s.rings && !s.rings[`${id}:a`] && !s.rings[`${id}:b`] && s.ringLines && !s.ringLines[id]);
      break;
    }
    case 'beams': {
      const probe2 = await fac.second();
      const bk = new BeamKit(ctx.server);
      await ctx.step('a public destination and a player\'s own place');
      obs.set = await bk.save(fac.probe, 'public', 'MapBeam', BEAM_AT);
      obs.place = await bk.save(probe2, 'place', 'MapPlace', PLACE_AT);
      obs.drawn = await reader.until((s) => s.beams && s.beams.mapbeam);
      // Held for a full redraw: the place must never appear.
      obs.seen = await hold(reader, (s) => Object.keys(s.beams || {}));
      await ctx.step('the destination is removed');
      obs.removeText = await bk.drop(fac.probe, 'public', 'MapBeam');
      obs.removed = await reader.until((s) => s.beams && !s.beams.mapbeam);
      break;
    }
    case 'mirrors': {
      await ctx.step('a mirror is hung');
      obs.made = await hangMirror(ctx, 'MapMirror');
      obs.drawn = await reader.until((s) => s.mirrors && s.mirrors.mapmirror);
      await ctx.step('made again at the same banner as MapGlass');
      obs.remade = await new MirrorKit(ctx.server).create('MapGlass', MIRROR);
      obs.relabelled = await reader.until((s) => s.mirrors && s.mirrors.mapglass && s.mirrors.mapglass.label === 'MapGlass' && !s.mirrors.mapmirror);
      break;
    }
    case 'restart': {
      await ctx.step('gates, a ring pair, a destination and a mirror');
      obs.build = `${await buildGate(ctx, 'MapA', GATES.MapA)} / ${await buildGate(ctx, 'MapB', GATES.MapB)}`;
      obs.pair = await layRings(ctx);
      obs.set = await new BeamKit(ctx.server).save(fac.probe, 'public', 'MapBeam', BEAM_AT);
      obs.made = await hangMirror(ctx, 'MapMirror');
      obs.before = (await reader.until((s) => s.gates && s.gates.mapb && s.rings && s.beams && s.beams.mapbeam && s.mirrors && s.mirrors.mapmirror)).snap;
      obs.removedB = (await kit.say('wormhole gate remove MapB -destroy')).text;
      await ctx.step('the server restarts');
      obs.restartFrom = await fac.restart('the Map Desk\'s restart case');
      const id = obs.pair && obs.pair.id;
      obs.after = (await reader.until((s) => s.gates && s.gates.mapa && s.rings && s.rings[`${id}:a`] && s.beams && s.beams.mapbeam && s.mirrors && s.mirrors.mapmirror, 20000)).snap;
      obs.held = await held(ctx);
      break;
    }
    case 'layer off':
    case 'off': {
      // Waited out for a full redraw after the restart: a layer is made on the first draw.
      obs.sets = (await reader.until(() => false, 9000)).snap.sets;
      obs.logged = ctx.server.log.slice(obs.restartFrom).filter((l) => /\[WormholeXTreme\].*([Dd]ynmap|web map)/.test(l));
      break;
    }
    case 'absent':
      await runAbsent(ctx, kit);
      break;
    default:
      throw new Error(`no case ${o.case}`);
  }
  return undefined;
}

/** The paired run: dynmap-enabled true, Dynmap not installed: one warning, and gates work. */
async function runAbsent(ctx, kit) {
  const obs = ctx.observed;
  const fac = ctx.facility;
  const lines = ctx.server.log.slice(obs.restartFrom);
  // Wormhole's own warnings since the start, by message. Others may be benign ones of their own
  // (no Vault or LuckPerms: the permission fallback), which server.KNOWN_BENIGN names.
  obs.warnings = lines.map((l) => /^\[\d\d:\d\d:\d\d WARN\]: \[WormholeXTreme\] ?(.*)$/.exec(l)).filter(Boolean).map((m) => m[1].trim());
  obs.enabled = lines.some((l) => /\[WormholeXTreme\].*Enable Completed/.test(l));
  obs.faults = fac.faults.faults.filter((f) => lines.some((l) => l.includes(f))).length;
  await ctx.step('MapA dials MapB and Probe walks through');
  obs.build = `${await buildGate(ctx, 'MapA', GATES.MapA)} / ${await buildGate(ctx, 'MapB', GATES.MapB)}`;
  const probe = fac.probe;
  const g = GATES.MapA;
  await probe.teleport({ x: g.centre.x, y: 0, z: Math.floor(g.opening[0].z) + 0.5 + g.normal.z * 6, yaw: 180 }, O);
  obs.dial = (await kit.dial('MapA', 'MapB')).text;
  obs.drawn = await kit.waitOpen(probe, g).catch((e) => { obs.openError = e.message; return null; });
  if (!obs.drawn) return;
  const to = GATES.MapB;
  await probe.walkTo({ x: g.centre.x, z: Math.floor(g.opening[0].z) + 0.5 - g.normal.z * 2.5 }, { within: 0.4, ms: 15000, until: () => probe.distanceTo(to.arrival) < 3 }).catch(() => {});
  const end = Date.now() + 4000;
  while (Date.now() < end && probe.distanceTo(to.arrival) >= 1.5) await ticks(5);
  obs.landed = probe.distanceTo(to.arrival);
}

/** What the plugin holds, as marker ids: its gates, ring ends, public destinations and mirrors. */
async function held(ctx) {
  const probe = ctx.facility.probe;
  const gates = (await ctx.server.run('wormhole list')).lines.filter((l) => !/Available gates|No gates found/.test(l)).join(',')
    .replace(/§./g, '').split(',').map((x) => x.trim().toLowerCase()).filter(Boolean);
  const rk = new RingKit(ctx.server, probe);
  const rings = (await rk.list()).ids.flatMap((id) => [`${id}:a`, `${id}:b`]);
  const beamText = await rk.ask('/wormhole beam list', { until: /Beam destinations|No public beam/ });
  const beams = ((/Beam destinations: (.*)$/.exec(beamText) || [null, ''])[1]).split(', ').filter(Boolean).map((b) => b.toLowerCase());
  const mirrors = (await new MirrorKit(ctx.server).list()).names.map((n) => n.toLowerCase());
  return { gates, rings, beams, mirrors };
}

const same = (a, b) => Array.isArray(a) && Array.isArray(b) && JSON.stringify([...a].sort()) === JSON.stringify([...b].sort());

function checks(ctx, o) {
  const obs = ctx.observed;
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  const built = c('the gates were built', () => (obs.builds || []).length > 0 && obs.builds.every((t) => /Built /.test(t)));
  switch (o.case) {
    case 'gate': {
      const s = obs.snap || {};
      const m = s.gates && s.gates.mapa;
      const a = s.gateAreas && s.gateAreas.mapa;
      return [built,
        c('the four layers are there, named', () => Object.entries(LABELS).every(([id, label]) => s.sets && s.sets[id] === label)),
        c('a point labelled MapA', () => m && m.label === 'MapA' && m.world === 'world'),
        c('at the centre of its opening', () => m && near(m, GATES.MapA.centre)),
        c('with the idle icon', () => m && m.icon === IDLE),
        c('an area over the opening, in the gate colour', () => a && a.label === 'MapA' && a.color === '37b0d8'),
        c(`the popup names the network: "Network: ${NET}"`, () => (obs.desc || '').includes(`Network: ${NET}`)),
        c('and the owner: "Owner: Probe"', () => /Owner: Probe\b/.test(obs.desc || '')),
        c('the area carries the same popup', () => obs.areaDesc === obs.desc)];
    }
    case 'dial':
    case 'remove': {
      const lockSamples = (obs.samples && obs.samples.locking) || [];
      const line = obs.open && obs.open.snap.gateLines && obs.open.snap.gateLines['mapa|mapb'];
      const list = [built,
        c('both idle before the dial', () => obs.before && obs.before.gates.mapa.icon === IDLE && obs.before.gates.mapb.icon === IDLE),
        c('the dial connected', () => /Stargates connected/.test(obs.dial || '')),
        c('while the chevrons locked (read over a second before the kawoosh), neither was drawn open', () => lockSamples.length > 0 && lockSamples.every((x) => x.a !== OPEN && x.b !== OPEN)),
        c('the kawoosh drew the opening', () => (obs.samples || []).some((x) => x.drawnA)),
        c('then both open', () => obs.open && obs.open.ok),
        c('with a cyan line between them: "MapA to MapB"', () => line && line.label === 'MapA to MapB' && line.color === '37b0d8')];
      if (o.case === 'dial') {
        list.push(c('shut: both idle and the line gone', () => obs.shut && obs.shut.ok));
      } else {
        list.push(c('removed: "Wormhole Removed: MapA"', () => /Wormhole Removed: MapA/.test(obs.removed || '')),
          c('its point, its area and the line are gone', () => obs.gone && obs.gone.ok));
      }
      return list;
    }
    case 'cross-world': {
      const s = obs.open && obs.open.snap;
      return [built,
        c('MapA dialled the Range', () => /Stargates connected/.test(obs.dial || '') && Boolean(obs.drawn)),
        c('both drawn open (MapA in world, Range in world_nether)', () => obs.open && obs.open.ok && s.gates.range.world === 'world_nether'),
        c('and no line in any world, for a full redraw', () => (obs.lines || []).length > 0 && obs.lines.every((ids) => !ids.some((id) => id.split('|').includes('mapa'))))];
    }
    case 'rename':
      return [built,
        c('the gate was renamed', () => !/No such field/.test(obs.rename || '')),
        c('its point is labelled MapZ, and nothing is left under MapA', () => obs.after && obs.after.ok)];
    case 'escaped':
      return [built,
        c('drawn', () => obs.drawnX),
        c(`the popup shows the network literally: "Network: ${ESCAPED}"`, () => (obs.desc || '').includes(`Network: ${ESCAPED}`)),
        c(`and the owner: "Owner: ${ESCAPED}"`, () => (obs.desc || '').includes(`Owner: ${ESCAPED}`)),
        c('with no raw <b>x</b> in it', () => Boolean(obs.desc) && !obs.desc.includes(RAW)),
        c('the area\'s popup likewise', () => (obs.areaDesc || '').includes(`Owner: ${ESCAPED}`) && !obs.areaDesc.includes(RAW)),
        c('and the web map\'s marker file carries it escaped', () => (obs.webDesc || '').includes(`Owner: ${ESCAPED}`) && !obs.webDesc.includes(RAW))];
    case 'iris hidden': {
      const seen = obs.seen || [];
      return [built,
        c('MapB is drawn, MapA (with an iris code) is not', () => obs.before && obs.before.gates.mapb && !obs.before.gates.mapa),
        c('MapA dialled MapB', () => /Stargates connected/.test(obs.dial || '') && Boolean(obs.drawn)),
        c('MapA stayed undrawn, point and area', () => seen.length > 0 && seen.every((x) => !x.a && !x.aArea)),
        c('MapB stayed idle for a full redraw after its opening was drawn', () => {
          const icons = [...new Set(seen.map((x) => x.b))];
          if (seen.length > 0 && icons.length === 1 && icons[0] === IDLE) return true;
          throw new Error(`MapB's icon read ${icons.join(', ')} over ${seen.length} reads in ${((seen.length * 0.5) + 0.5).toFixed(0)} s`);
        }),
        c('and no line', () => seen.length > 0 && seen.every((x) => !x.lines.some((id) => id.split('|').includes('mapa'))))];
    }
    case 'rings': {
      const id = obs.pair && obs.pair.id;
      const s = obs.drawn && obs.drawn.snap;
      return [c('the pair was built', () => Boolean(id)),
        c('a point at each end', () => obs.drawn && obs.drawn.ok),
        c('and a grey line between them', () => s && s.ringLines[id] && s.ringLines[id].color === '9aa5b1'),
        c('an end named MapRing is relabelled', () => /This ring is now/.test(obs.named || '') && obs.relabelled && obs.relabelled.ok),
        c('removed, both points and the line go', () => /Removed both ends/.test(obs.removedText || '') && obs.removed && obs.removed.ok)];
    }
    case 'beams':
      return [c('MapBeam, public, is drawn', () => obs.drawn && obs.drawn.ok),
        c('Probe2\'s own place MapPlace was saved', () => /set to your current location/.test(obs.place || '')),
        c('and is never drawn, for a full redraw', () => (obs.seen || []).length > 0 && obs.seen.every((ids) => !ids.includes('mapplace'))),
        c('removed, MapBeam goes', () => obs.removed && obs.removed.ok)];
    case 'mirrors': {
      const s = obs.drawn && obs.drawn.snap;
      return [c('the mirror was made', () => /MapMirror/.test(obs.made || '') && !/refus|error/i.test(obs.made || '')),
        c('drawn at its banner, labelled MapMirror', () => s && s.mirrors.mapmirror && s.mirrors.mapmirror.label === 'MapMirror'
          && near(s.mirrors.mapmirror, { x: MIRROR.x + 0.5, y: MIRROR.y + 0.5, z: MIRROR.z + 0.5 }, 0.1)),
        c('with no line', () => s && s.mirrorLines && Object.keys(s.mirrorLines).length === 0),
        c('made again at the same banner, relabelled MapGlass, nothing left as MapMirror', () => obs.relabelled && obs.relabelled.ok)];
    }
    case 'restart': {
      const s = obs.after || {};
      const h = obs.held || {};
      return [built,
        c('MapB was removed before the stop', () => /Wormhole Removed: MapB/.test(obs.removedB || '')),
        c('after the restart, every gate the plugin holds is drawn once, and nothing else', () => s.gates && same(Object.keys(s.gates), h.gates)),
        c('MapB is not among them', () => s.gates && !s.gates.mapb),
        c('every ring end likewise', () => s.rings && same(Object.keys(s.rings), h.rings)),
        c('every public destination likewise', () => s.beams && same(Object.keys(s.beams), h.beams)),
        c('every mirror likewise', () => s.mirrors && same(Object.keys(s.mirrors), h.mirrors))];
    }
    case 'layer off':
      return [c('the Transport rings layer is gone entirely', () => obs.sets && !obs.sets[SETS.rings]),
        c('the other three are there', () => obs.sets && [SETS.gates, SETS.beams, SETS.mirrors].every((id) => obs.sets[id]))];
    case 'off':
      // An empty list proves nothing if Dynmap itself is down: its own "markers" set shows it is up.
      return [c('Dynmap is up: its own markers set is there', () => obs.sets && Boolean(obs.sets.markers)),
        c('no Wormhole layer', () => obs.sets && !Object.keys(obs.sets).some((id) => id.startsWith('wormhole.'))),
        c('and nothing logged by Wormhole about Dynmap since the restart', () => Array.isArray(obs.logged) && obs.logged.length === 0)];
    case 'absent':
      return [
        c('one Wormhole warning at startup: "dynmap-enabled is set but Dynmap was not found. Nothing is shown on a map."',
          () => (obs.warnings || []).filter((w) => w === DYNMAP_ABSENT).length === 1),
        c('and any other Wormhole warning is a known-benign one', () => {
          const other = (obs.warnings || []).filter((w) => w !== DYNMAP_ABSENT && !server.KNOWN_BENIGN.includes(w));
          if (!other.length) return true;
          throw new Error(other.join(' | '));
        }),
        c('it enabled, with no Wormhole fault since the restart', () => obs.enabled && obs.faults === 0),
        built,
        c('a gate works: MapA dialled MapB', () => /Stargates connected/.test(obs.dial || '') && Boolean(obs.drawn)),
        c('and Probe came out of MapB', () => obs.landed !== undefined && obs.landed < 1.5)];
    default:
      return [c(`a case called ${o.case}`, () => false)];
  }
}

async function cleanup(ctx) {
  const fac = ctx.facility;
  if (state.restartOwed) {
    const owed = state.restartOwed;
    state.restartOwed = null;
    await fac.restart(`the setting put back after the Map Desk's ${owed} case`);
  }
  const kit = new GateKit(ctx.server);
  for (const name of ['MapA', 'MapB', 'MapX', 'MapZ']) if (await kit.exists(name)) await kit.remove(name);
  if (await kit.exists('Range')) await kit.force('Range');
  const rk = new RingKit(ctx.server, fac.probe);
  for (const id of (await rk.list()).ids) if (!fac.keepRings.has(id)) await rk.remove(id);
  const bk = new BeamKit(ctx.server);
  await bk.drop(fac.probe, 'public', 'MapBeam');
  if (fac.probe2) await bk.drop(fac.probe2, 'place', 'MapPlace');
  const mk = new MirrorKit(ctx.server);
  for (const name of ['MapMirror', 'MapGlass']) if ((await mk.list()).names.includes(name)) await mk.remove(name);
  await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
  const reset = fac.manifest.functions.find((f) => f.fn === 'reset/g1');
  if (reset) {
    const r = await fac.runFunction(reset);
    if (!r.ok) throw new Error(`G1's reset: ${r.detail}`);
  }
}

module.exports = {
  id: 'map',
  wing: 'systems',
  title: def.title,
  seat: deskLayout(def).seat,
  options: { case: CASES },
  needs: (o) => ({ config: SETTINGS[o.case] || {} }),
  refuses,
  stage,
  run,
  checks,
  cleanup,
  GATES, RING_ENDS, MIRROR,
};
