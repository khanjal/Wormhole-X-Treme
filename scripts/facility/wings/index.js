'use strict';
// Compiles the campus (lib/campus.js) into one blueprint per wing and one per chamber reset.
// Everything common to every wing is here: its room, the corridors that reach it, its cells,
// desks, boards, and a plate home to Ops. A wing's own structures are in ./<wing>.js.

const campus = require('../lib/campus');
const bp = require('../lib/blueprint');
const transit = require('./transit');
const guard = require('./decor/guard');

// Each wing's decoration (stage 3.6), run inside `decorate` so the guardrail sees every block.
const DECOR = {
  ops: require('./decor/ops'),
  gates: require('./decor/gates'),
  rings: require('./decor/rings'),
  beams: require('./decor/beams'),
  mirrors: require('./decor/mirrors'),
  menagerie: require('./decor/menagerie'),
  range: require('./decor/range'),
  annex: require('./decor/annex'),
};

const EXTRAS = {
  ops: require('./ops'),
  menagerie: require('./menagerie'),
  range: require('./range'),
  annex: require('./annex'),
};

// Built in this order: a corridor is built by the wing it leads to, after the wing it leaves.
const ORDER = ['ops', 'gates', 'rings', 'beams', 'mirrors', 'menagerie', 'range', 'annex'];

function corridorsInto(wingId) {
  return campus.CORRIDORS.filter((c) => c.id.split('-')[1] === wingId);
}

/** The four wall slabs of a room, for finding which doorways cut through it. */
function roomWalls(r) {
  const top = r.y0 + r.h - 1;
  return [
    bp.box3(r.x0 - 1, r.y0, r.z0 - 1, r.x1 + 1, top, r.z0 - 1),
    bp.box3(r.x0 - 1, r.y0, r.z1 + 1, r.x1 + 1, top, r.z1 + 1),
    bp.box3(r.x0 - 1, r.y0, r.z0, r.x0 - 1, top, r.z1),
    bp.box3(r.x1 + 1, r.y0, r.z0, r.x1 + 1, top, r.z1),
  ];
}

/** Where the plate home to Ops sits in a wing: its own, or two blocks to the right of its entrance. */
function homePlate(w) {
  if (w.homePlate) return w.homePlate;
  const e = w.entrance;
  const facingZ = Math.abs(Math.sin((e.yaw * Math.PI) / 180)) < 0.5;
  const x = Math.floor(e.x) + (facingZ ? 2 : 0);
  const z = Math.floor(e.z) + (facingZ ? 0 : 2);
  return { x, y: e.y, z };
}

function chambersOf(wingId) {
  return campus.CHAMBERS.filter((c) => c.wing === wingId);
}

/**
 * The blueprint for `build/<wing>`. `version` picks the text form for boards. The decoration is
 * always built: some of it carries tests (the far sites' mirror piers, their Dial Ops consoles).
 */
function buildWing(wingId, version) {
  const w = campus.wing(wingId);
  const out = new bp.Blueprint(`wx:build/${wingId}`, w.dim);
  out.cmd(`kill @e[type=minecraft:text_display,tag=wx_wing_${wingId}]`);
  const extra = EXTRAS[wingId] || {};

  if (w.room && !w.shell) bp.room(out, w.room);
  if (extra.shell) extra.shell(out, w);

  // Corridors into this wing, then every doorway (any corridor's) through this wing's walls.
  for (const c of corridorsInto(wingId)) bp.corridor(out, c, w.colour);
  if (w.room && !w.shell) {
    const walls = roomWalls(w.room);
    for (const c of campus.CORRIDORS) {
      for (const d of bp.corridorDoorways(c)) {
        if (walls.some((wall) => bp.overlaps(wall, d))) out.fill(d, 'minecraft:air');
      }
    }
  }
  if (extra.structures) extra.structures(out, w, version);
  transit.structures(out, wingId, version);

  // Systems is the mezzanine over Ops, so Ops builds its desk.
  const chambers = wingId === 'ops' ? [...chambersOf('ops'), ...chambersOf('systems')] : chambersOf(wingId);
  for (const ch of chambers) {
    if (ch.kind === 'cell') {
      bp.cellShell(out, ch, w.colour);
      bp.cellSurrounds(out, ch);
      for (const b of bp.cellClear(ch)) out.mustBeClear(ch.id, b);
    } else if (ch.kind === 'desk') {
      bp.desk(out, ch);
    } else if (ch.kind === 'tunnel') {
      bp.room(out, ch.box, { wall: 'minecraft:glass', roof: 'minecraft:glass', skirting: null });
      const b = ch.box;
      const mz = Math.floor((b.z0 + b.z1) / 2);
      out.fill(bp.box3(b.x0 - 1, b.y0, b.z0, b.x0 - 1, b.y0 + b.h - 2, b.z1), 'minecraft:air');
      out.fill(bp.box3(b.x0, b.y0 - 1, mz, b.x1, b.y0 - 1, mz), campus.PALETTE.guide);
      out.mustBeClear(ch.id, bp.interior(b));
    }
    const L = bp.layoutOf(ch);
    out.cmd(bp.summonBoard(version, { id: ch.id, wing: wingId, at: L.board, spec: bp.chamberBoardSpec(ch) }));
  }

  if (wingId !== 'ops') {
    const p = homePlate(w);
    bp.plate(out, p.x, p.y, p.z, { wing: 'ops', to: campus.TRANSIT.home });
    out.cmd(bp.summonBoard(version, {
      id: `home_${wingId}`, wing: wingId, at: { x: p.x + 0.5, y: p.y + 1.4, z: p.z + 0.5 },
      spec: [{ text: 'Operations', color: 'white', bold: true }, '\n', { text: 'plate: back to the atrium', color: 'gray' }],
    }));
    const e = w.entrance;
    out.cmd(bp.summonBoard(version, {
      id: `sign_${wingId}`, wing: wingId, at: { x: e.x, y: e.y + 3.2, z: e.z }, scale: 1.5,
      spec: [{ text: w.title.toUpperCase(), color: w.text, bold: true }, ...(w.nick ? ['\n', { text: w.nick, color: 'gray' }] : [])],
    }));
  }
  if (DECOR[wingId]) out.decorate(() => DECOR[wingId].decorate(out, w, version));
  return out;
}

/** The blueprint for `reset/<chamber>`: clear the cell and put its enclosure back as built. */
function resetChamber(ch) {
  const w = campus.wing(ch.wing);
  const out = new bp.Blueprint(`wx:reset/${ch.id}`, w.dim);
  out.cmd(`kill @e[tag=wx_run_${ch.id}]`);
  if (ch.kind === 'cell') {
    for (const b of bp.cellClear(ch)) out.fill(b, 'minecraft:air');
    if (w.dim === campus.OVERWORLD && !ch.shaft) {
      // A run may dig into the floor (a gate built flush, a lane of ice): lay it back, the
      // flat world's stone under its quartz, four deep.
      const b = ch.box;
      out.fill(bp.box3(b.x0, b.y0 - 4, b.z0, b.x1, b.y0 - 2, b.z1), 'minecraft:stone');
      out.fill(bp.box3(b.x0, b.y0 - 1, b.z0, b.x1, b.y0 - 1, b.z1), campus.PALETTE.floor);
    }
    bp.cellShell(out, ch, w.colour);
  } else if (ch.kind === 'tunnel') {
    out.fill(bp.interior(ch.box), 'minecraft:air');
  }
  return out;
}

/** Every build and reset, in build order. */
function allBlueprints(version) {
  const builds = ORDER.map((id) => ({ fn: `build/${id}`, wing: id, dim: campus.wing(id).dim, bp: buildWing(id, version) }));
  const resets = campus.CHAMBERS.filter((c) => c.kind !== 'desk')
    .map((c) => ({ fn: `reset/${c.id}`, chamber: c.id, dim: campus.wing(c.wing).dim, bp: resetChamber(c) }));
  return { builds, resets };
}

/**
 * Layout clashes, as sentences: cells, desks, lanes and plates that overlap, a cell outside its
 * wing, and any block a build writes outside the forceloaded chunks (a fill there fails, and a
 * failed command inside a function is silent). An empty list means the campus is consistent.
 */
function validateLayout(version = '1.21.11') {
  const problems = [];
  const items = [];
  for (const ch of campus.CHAMBERS) {
    const dim = campus.wing(ch.wing).dim;
    for (const b of bp.layoutOf(ch).footprint) items.push({ what: `chamber ${ch.id}`, dim, box: b });
    if (ch.kind === 'cell') {
      const home = ch.wing === 'systems' ? campus.wing('ops') : campus.wing(ch.wing);
      const r = home.room;
      const inside = bp.box3(r.x0, r.y0 - (ch.shaft || 0) - 2, r.z0, r.x1, r.y0 + r.h - 1, r.z1);
      for (const b of bp.layoutOf(ch).footprint) {
        const within = b.x0 >= inside.x0 && b.x1 <= inside.x1 && b.z0 >= inside.z0 && b.z1 <= inside.z1 && b.y1 <= inside.y1;
        if (!within) problems.push(`${ch.id} reaches outside the ${home.title} room: ${JSON.stringify(b)}`);
      }
    }
  }
  for (const lane of campus.LANES) {
    const z0 = lane.z0 !== undefined ? lane.z0 : lane.z;
    const z1 = lane.z1 !== undefined ? lane.z1 : lane.z;
    items.push({ what: `lane ${lane.id}`, dim: campus.OVERWORLD, box: bp.box3(lane.x0, -1, z0, lane.x1, 0, z1) });
  }
  const c = campus.TRANSIT.centre;
  for (const p of campus.TRANSIT.plates) {
    items.push({ what: `plate ${p.dir}`, dim: campus.OVERWORLD, box: bp.box3(c.x + p.dx, -1, c.z + p.dz, c.x + p.dx, 1, c.z + p.dz) });
  }
  for (const w of campus.WINGS) {
    if (w.id === 'ops') continue;
    const p = w.id === 'systems' ? { x: 3, y: 6, z: -17 } : homePlate(w);
    items.push({ what: `plate home from ${w.id}`, dim: w.dim, box: bp.box3(p.x, p.y - 1, p.z, p.x, p.y + 1, p.z) });
  }
  for (const t of transit.footprints()) items.push({ what: t.what, dim: campus.wing(t.wing).dim, box: t.box });
  for (let i = 0; i < items.length; i++) {
    for (let j = i + 1; j < items.length; j++) {
      const a = items[i];
      const b = items[j];
      if (a.what === b.what || a.dim !== b.dim) continue;
      if (a.what.startsWith('lane') && b.what.startsWith('lane')) continue;
      if (bp.overlaps(a.box, b.box)) problems.push(`${a.what} overlaps ${b.what}`);
    }
  }
  const chunk = (v) => Math.floor(v / 16);
  const loaded = (dim, box) => campus.FORCELOAD.some((f) => f.dim === dim
    && chunk(box.x0) >= chunk(f.from[0]) && chunk(box.x1) <= chunk(f.to[0])
    && chunk(box.z0) >= chunk(f.from[1]) && chunk(box.z1) <= chunk(f.to[1]));
  const { builds, resets } = allBlueprints(version);
  problems.push(...guard.check(builds));
  // Every block is one 1.20.4 knows (the facility's floor): a newer name is refused here, not
  // by a function that fails to load on the older server.
  const known = require('minecraft-data')('1.20.4').blocksByName;
  for (const f of [...builds, ...resets]) {
    for (const o of f.bp.ops.filter((x) => x.kind !== 'cmd')) {
      const name = o.block.replace(/^minecraft:/, '').replace(/[[{].*$/, '');
      if (!known[name]) problems.push(`${f.fn}: ${o.block.replace(/\{.*$/, '')} is not a block on 1.20.4`);
    }
  }
  for (const f of [...builds, ...resets]) {
    for (const b of f.bp.boxes()) {
      // A box may span two forceload rectangles; check it piece by piece along x.
      const pieces = bp.split(b, 16 * 16 * 400);
      if (!pieces.every((p) => loaded(f.dim, p) || splitByChunk(p).every((q) => loaded(f.dim, q)))) {
        problems.push(`${f.fn} writes outside the forceloaded chunks: ${JSON.stringify(b)}`);
        break;
      }
    }
  }
  return problems;
}

/** A box cut at chunk borders, so each piece lies in one chunk column. */
function splitByChunk(b) {
  const out = [];
  for (let cx = Math.floor(b.x0 / 16); cx <= Math.floor(b.x1 / 16); cx++) {
    for (let cz = Math.floor(b.z0 / 16); cz <= Math.floor(b.z1 / 16); cz++) {
      out.push({ ...b, x0: Math.max(b.x0, cx * 16), x1: Math.min(b.x1, cx * 16 + 15), z0: Math.max(b.z0, cz * 16), z1: Math.min(b.z1, cz * 16 + 15) });
    }
  }
  return out;
}

/** The distinct chunks the campus forceloads, per dimension. */
function forceloadChunks() {
  const out = {};
  for (const f of campus.FORCELOAD) {
    const set = out[f.dim] || (out[f.dim] = new Set());
    for (let cx = Math.floor(f.from[0] / 16); cx <= Math.floor(f.to[0] / 16); cx++) {
      for (let cz = Math.floor(f.from[1] / 16); cz <= Math.floor(f.to[1] / 16); cz++) set.add(`${cx},${cz}`);
    }
  }
  return Object.fromEntries(Object.entries(out).map(([k, v]) => [k, v.size]));
}

module.exports = {
  ORDER, buildWing, resetChamber, allBlueprints, homePlate, chambersOf, roomWalls, validateLayout, forceloadChunks,
};
