'use strict';
// Watch mode's vantage points (campus.WATCH, lib/watcher.js) without a server: a watcher held
// within WATCH_LEASH of one is never somewhere the plugin would count a player. Run with
// `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const campus = require('../lib/campus');
const watcher = require('../lib/watcher');
const { GateKit } = require('../lib/gatekit');
const { MATRIX, defaultsOf } = require('../matrix');

const O = campus.OVERWORLD;
const L = campus.WATCH_LEASH;
const chamberOf = (file) => require(`../chambers/${file}`);
const g1 = chamberOf('g1-stand');
const g3 = chamberOf('g3-automation');
const g4 = chamberOf('g4-bench');
const g5 = chamberOf('g5-iris');
const m1 = chamberOf('m1-round');
const map = chamberOf('companion-map');

// The plugin's default (DefaultSettings MIRROR_PROXIMITY_DISTANCE); a test below shows no cell changes it.
const MIRROR_PROXIMITY = 16;
// Half the widest ring (seven slabs across), and how far its volume runs above a floor ring and
// below a ceiling one (ring-max-ceiling-drop is 10).
const RING_HALF = 4;
const RING_UP = 6;
const RING_DOWN = 11;

/** Every mirror banner a cell or the transit routes hang: { what, dim, x, y, z }. */
function banners() {
  const out = campus.ROUTES.mirrors.map((m) => ({ what: `transit ${m.name}`, ...m }));
  out.push({ what: 'M1 Round', ...m1.ROUND }, { what: 'M1 Spare', ...m1.END_SPARE });
  for (const [k, n] of Object.entries(chamberOf('m2-wall').NICHES)) {
    for (const b of [n.banner, n.partner].filter(Boolean)) out.push({ what: `M2 ${k}`, dim: O, x: b[0], y: b[1], z: b[2] });
  }
  out.push({ what: 'M3 Desk', ...chamberOf('m3-capture').DESK }, { what: 'G3 CbMirror', dim: O, ...g3.MIRROR },
    { what: 'Map Desk mirror', ...map.MIRROR });
  return out;
}

/** Every ring end: { what, dim, x, z, y0, y1 } with the height its volume can reach. */
function rings() {
  const floor = (what, p, dim = O) => ({ what, dim, x: p.x, z: p.z, y0: p.y - 1, y1: p.y + RING_UP });
  const out = campus.ROUTES.rings.ends.map((e) => floor(`transit ${e.name}`, e));
  const r1 = chamberOf('r1-pair');
  out.push(floor('R1 A', r1.A));
  for (const d of r1.options.distance) out.push(floor(`R1 B at ${d.value}`, { ...r1.A, x: r1.A.x + Number(d.value) }));
  const r2 = chamberOf('r2-ceiling');
  out.push(floor('R2 floor', r2.FLOOR_RING), { what: 'R2 ceiling', dim: O, x: r2.CEILING_XZ.x, z: r2.CEILING_XZ.z, y0: -1, y1: RING_DOWN });
  const top = chamberOf('r3-shaft').TOP;
  out.push({ what: 'R3 shaft', dim: O, x: top.x, z: top.z, y0: -61, y1: top.y + RING_UP });
  const r4 = chamberOf('r4-bench');
  out.push(floor('R4 A', r4.A), floor('R4 B', r4.B));
  for (const e of chamberOf('r5-edit').ENDS || []) out.push(floor('R5 end', e));
  const r6 = chamberOf('r6-range');
  out.push(floor('tunnel A', r6.A));
  for (const d of r6.options.distance) out.push(floor(`tunnel B at ${d.value}`, { ...r6.A, x: r6.A.x + Number(d.value) }));
  g3.RINGS.forEach((r, i) => out.push(floor(`G3 ring ${i + 1}`, r)));
  map.RING_ENDS.forEach((r, i) => out.push(floor(`Map Desk ring ${i + 1}`, r)));
  return out;
}

/** Every gate a cell or the transit routes stand: { what, dim, box } (each shape a cell may build there). */
function gates() {
  const kit = new GateKit(null);
  const out = [];
  const add = (what, dim, shape, facing, at) => out.push({ what: `${what} (${shape})`, dim, box: kit.place(shape, facing, at).bounds });
  // G1's `custom` is the Lab.shape asset.
  const shapes = g1.options.shape.map((s) => (typeof s === 'object' ? s.value : s)).map((s) => (s === 'custom' ? 'Lab' : s));
  for (const [n, g] of Object.entries(campus.ROUTES.gates)) add(`transit ${n}`, g.dim, g.shape, g.facing, g);
  for (const s of shapes) add('G1 Stand', O, s, campus.GATES.stand.facing, campus.GATES.stand);
  for (const [n, g] of Object.entries(campus.GATES.far)) add(n, g.dim, g.shape, g.facing, g);
  const row = campus.GATES.gallery;
  for (const [s, cx] of row.row) add('G2 gallery', O, s, row.facing, { ...row, cx });
  for (const [n, g] of Object.entries({ BAY: g3.BAY, FAR: g3.FAR, CB_GATE: g3.CB_GATE })) add(`G3 ${n}`, O, g.shape, g.facing, g);
  for (const s of shapes) add('G4 bench', O, s, g4.PLACE.facing, g4.PLACE);
  for (const g of Object.values(g5.GATES)) add(`G5 ${g.name}`, O, 'Standard', g.facing, g);
  for (const [n, geom] of Object.entries(map.GATES)) out.push({ what: `Map Desk ${n}`, dim: O, box: geom.bounds });
  return out;
}

const points = Object.entries(campus.WATCH).map(([id, v]) => ({ id, ...v }));

test('every chamber the matrix runs has a vantage point, facing into it', () => {
  for (const id of Object.keys(MATRIX)) assert.ok(campus.WATCH[id], `no WATCH entry for ${id}`);
  for (const p of points) {
    assert.ok(p.look, `${p.id} says nothing to look for`);
    const f = watcher.facing(p);
    assert.ok(Number.isFinite(f.yaw) && Number.isFinite(f.pitch) && f.pitch > -90 && f.pitch < 90, `${p.id}: yaw ${f.yaw}, pitch ${f.pitch}`);
  }
});

test('facing turns the way Minecraft does: yaw 0 south, 90 west, -90 east, 180 north; pitch down is positive', () => {
  const at = (dx, dz, dy = 1.62) => watcher.facing({ x: 0, y: 0, z: 0, at: { x: dx, y: dy, z: dz } });
  assert.strictEqual(at(0, 5).yaw, 0);
  assert.strictEqual(at(-5, 0).yaw, 90);
  assert.strictEqual(at(5, 0).yaw, -90);
  assert.strictEqual(Math.abs(at(0, -5).yaw), 180);
  assert.ok(at(0, 5, -3).pitch > 0);
});

test(`no vantage point is within ${MIRROR_PROXIMITY} + the leash of any mirror banner, so the plugin never counts a watcher as near one`, () => {
  const all = banners();
  assert.ok(all.length >= 12, `${all.length} banners`);
  for (const p of points) {
    for (const b of all.filter((x) => x.dim === p.dim)) {
      const d = Math.hypot(p.x - (b.x + 0.5), p.y - b.y, p.z - (b.z + 0.5));
      assert.ok(d >= MIRROR_PROXIMITY + L + 1, `${p.id} is ${d.toFixed(1)} from ${b.what}`);
    }
  }
});

test('no matrix cell changes mirror-proximity-distance, which the clearance above assumes', () => {
  for (const [id, cells] of Object.entries(MATRIX)) {
    const ch = require('../chambers').entries().find((e) => e.def.id === id).chamber;
    for (const cell of cells) {
      const config = ch.needs ? (ch.needs({ ...defaultsOf(ch), ...cell.values }) || {}).config || {} : {};
      assert.ok(!('mirror-proximity-distance' in config), `${id} sets it`);
    }
  }
});

test('no vantage point lets a watcher into a ring\'s volume, where the plugin would carry them', () => {
  const all = rings();
  for (const p of points) {
    for (const r of all.filter((x) => x.dim === p.dim)) {
      const across = Math.hypot(p.x - (r.x + 0.5), p.z - (r.z + 0.5));
      const clear = across >= RING_HALF + L || p.y - L > r.y1 || p.y + 2 + L < r.y0;
      assert.ok(clear, `${p.id} is ${across.toFixed(1)} across from ${r.what} (y ${r.y0}..${r.y1})`);
    }
  }
});

test('no vantage point lets a watcher into a gate, where the plugin would send them through', () => {
  for (const p of points) {
    for (const g of gates().filter((x) => x.dim === p.dim)) {
      const b = g.box;
      // From a block box's faces; a player is 1.8 tall from its feet.
      const gap = (lo, hi, a0, a1) => Math.max(lo - a1, 0, a0 - (hi + 1));
      const d = Math.hypot(gap(b.x0, b.x1, p.x, p.x), gap(b.y0, b.y1, p.y, p.y + 1.8), gap(b.z0, b.z1, p.z, p.z));
      assert.ok(d >= L + 1, `${p.id} is ${d.toFixed(1)} from ${g.what}`);
    }
  }
});

test('every vantage point is in a forceloaded chunk, so its leash marker is always there', () => {
  for (const p of points) {
    const chunk = (v) => Math.floor(v / 16);
    const inside = campus.FORCELOAD.some((f) => f.dim === p.dim && chunk(p.x) >= chunk(f.from[0]) && chunk(p.x) <= chunk(f.to[0])
      && chunk(p.z) >= chunk(f.from[1]) && chunk(p.z) <= chunk(f.to[1]));
    assert.ok(inside, `${p.id} at ${p.x} ${p.z}`);
  }
});

test('the leash puts a watcher back on the marker, and only a watcher', () => {
  const tick = watcher.functions().watcher;
  assert.match(tick, new RegExp(`execute as @a\\[tag=${watcher.TAG}\\] at @s unless entity @e\\[type=minecraft:marker,tag=${watcher.MARKER},distance=\\.\\.${L}\\] run tp @s @e\\[type=minecraft:marker,tag=${watcher.MARKER},limit=1\\]`));
});

test('a cell is described by the options it sets away from their defaults, with their reasons', () => {
  const lines = watcher.settingLines(g1, { ...defaultsOf(g1), traveller: 'horse' });
  assert.strictEqual(lines.length, 1);
  assert.match(lines[0], /^traveller horse: /);
  assert.deepStrictEqual(watcher.settingLines(g1, defaultsOf(g1)), []);
});
