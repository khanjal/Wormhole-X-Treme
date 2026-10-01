'use strict';
// Design mode's pure parts (design/facility/BRIEF.md, "Run it yourself"): what a designer may not
// build in, the placeholder blocks that show it, the 2-block skin of every chamber, the export
// areas, and the schematic grids that `check` compares and `export` masks. No server here: the
// session that drives WorldEdit is lib/designmode.js.
//
// The export's one rule: a design schematic carries structure void at every protected position,
// and is pasted with WorldEdit's source mask `-m !minecraft:structure_void`, so a paste leaves
// what the campus built there (a cell's air, a gate, a pad, a wall the tests read) as it is, and
// replaces everything else in the box. lib/schematics.js refuses a design placement that is not
// structure void at every position the guardrail protects on the version it is pasted on.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const nbt = require('prismarine-nbt');
const campus = require('./campus');
const bp = require('./blueprint');

/** The one version design mode runs, and the first a design export is applied on. */
const VERSION = '1.21.11';
const APPLIES_FROM = '1.21.11';

/** Placeholders: test volumes (cells, the shaft, the tunnel) and everything else the guardrail keeps. */
const PLACEHOLDER = { volume: 'minecraft:pink_stained_glass', fixture: 'minecraft:green_stained_glass' };
const PLACEHOLDERS = new Set(Object.values(PLACEHOLDER));
const MASK = 'minecraft:structure_void';
const AIR = 'minecraft:air';

// What the brief's rule 3 calls active parts, and the rest of the redstone family.
const ACTIVE = new RegExp('^minecraft:(redstone_wire|redstone_torch|redstone_wall_torch|redstone_block|repeater|comparator'
  + '|target|daylight_detector|tripwire|tripwire_hook|dispenser|dropper|crafter|sculk_sensor|calibrated_sculk_sensor'
  + '|rail|powered_rail|detector_rail|activator_rail|\\w+_sign|\\w+_wall_sign|\\w+_hanging_sign|\\w+_wall_hanging_sign'
  + '|\\w+_button|lever|\\w+_pressure_plate|\\w+_banner|\\w+_wall_banner|hopper|piston|sticky_piston|piston_head|moving_piston'
  + '|observer|command_block|chain_command_block|repeating_command_block)$');

/** Entities a designer may place (rule 6), outside every protected volume and skin. */
const ALLOWED_ENTITIES = new Set(['minecraft:item_frame', 'minecraft:glow_item_frame', 'minecraft:armor_stand']);

const SKIN = 2;

// The export areas: each forceloaded rectangle (campus.FORCELOAD) grown by MARGIN for exteriors,
// over the height a designer builds in, per dimension.
const MARGIN = 16;
const HEIGHT = { [campus.OVERWORLD]: [-63, 95], [campus.NETHER]: [32, 120], [campus.END]: [30, 120] };
const AREA_NAMES = {
  'Beam Physics': 'beams',
  'Gate Dynamics': 'gates',
  'Ops, Systems, Mirror Optics': 'ops',
  'Ring Transit': 'rings',
  'Menagerie and Motor Pool': 'menagerie',
  'the range tunnel': 'tunnel',
  'The Range': 'range',
  'The Annex': 'annex',
};

/** [{ name, dim, box }]: one per wing area and far site. */
function areas() {
  return campus.FORCELOAD.map((f) => {
    const name = AREA_NAMES[f.why];
    if (!name) throw new Error(`design: no export area name for the forceloaded "${f.why}"`);
    const [y0, y1] = HEIGHT[f.dim];
    return { name, dim: f.dim, box: bp.box3(f.from[0] - MARGIN, y0, f.from[1] - MARGIN, f.to[0] + MARGIN, y1, f.to[1] + MARGIN) };
  });
}

/** The world-space block a board (a text display tagged wx_board) stands in, from a build's commands. */
function boardBoxes(builds) {
  const out = [];
  const re = /^summon minecraft:text_display (\S+) (\S+) (\S+) \{Tags:\[([^\]]*)\]/;
  for (const f of builds) {
    for (const op of f.bp.ops.filter((o) => o.kind === 'cmd')) {
      const m = re.exec(op.line);
      if (!m || !/"wx_board_([^"]+)"/.test(m[4])) continue;
      const [x, y, z] = [m[1], m[2], m[3]].map((n) => Math.floor(Number(n)));
      const id = /"wx_board_([^"]+)"/.exec(m[4])[1];
      out.push({ what: `the ${id} board`, dim: f.dim, box: bp.box3(x, y, z, x, y + 1, z), kind: 'fixture', entities: true });
    }
  }
  return out;
}

/**
 * Everything a designer may not build in on `version`: the decoration guardrail's boxes
 * (wings/decor/guard.js), each with the placeholder kind that marks it, and the boards'.
 */
function protectedBoxes(version = VERSION) {
  const guard = require('../wings/decor/guard');
  const { allBlueprints } = require('../wings');
  const builds = allBlueprints(version).builds;
  const list = guard.forbidden(builds).map((k) => ({ ...k, kind: /clear volume/.test(k.what) ? 'volume' : 'fixture' }));
  const boards = boardBoxes(builds).filter((b) => !list.some((k) => k.dim === b.dim && contains(k.box, b.box)));
  return [...list, ...boards];
}

/** The union of protectedBoxes over these versions, so an export pastes on each of them. */
function protectedFor(versions) {
  const seen = new Set();
  const out = [];
  for (const v of versions) {
    for (const k of protectedBoxes(v)) {
      const key = `${k.dim} ${k.box.x0} ${k.box.y0} ${k.box.z0} ${k.box.x1} ${k.box.y1} ${k.box.z1}`;
      if (!seen.has(key)) { seen.add(key); out.push(k); }
    }
  }
  return out;
}

/** Every chamber's test volumes, each with its 2-block skin box: [{ what, dim, inner, outer }]. */
function skins() {
  const out = [];
  for (const ch of campus.CHAMBERS) {
    if (ch.kind === 'desk') continue;
    const dim = campus.wing(ch.wing).dim;
    const inners = ch.kind === 'cell' ? bp.cellClear(ch) : [bp.interior(ch.box)];
    for (const b of inners) out.push({ what: `${ch.id}'s skin`, dim, inner: b, outer: grow(b, SKIN) });
  }
  return out;
}

/**
 * The fills that put the placeholders in, in order: test volumes first, then the rest, each only
 * over air, so a wall, a pad or the canal's water stays. [{ dim, box, block }], each within the
 * vanilla fill limit.
 */
function placeholderFills(list) {
  const out = [];
  for (const kind of ['volume', 'fixture']) {
    for (const k of list.filter((x) => x.kind === kind)) {
      for (const b of bp.split(k.box)) out.push({ dim: k.dim, box: b, block: PLACEHOLDER[kind] });
    }
  }
  return out;
}

function fillCommand(f) {
  const b = f.box;
  return `execute in ${f.dim} run fill ${b.x0} ${b.y0} ${b.z0} ${b.x1} ${b.y1} ${b.z1} ${f.block} replace ${AIR}`;
}

// ---- geometry -------------------------------------------------------------------------------

function grow(b, n) {
  return bp.box3(b.x0 - n, b.y0 - n, b.z0 - n, b.x1 + n, b.y1 + n, b.z1 + n);
}

function contains(outer, inner) {
  return outer.x0 <= inner.x0 && outer.y0 <= inner.y0 && outer.z0 <= inner.z0
    && outer.x1 >= inner.x1 && outer.y1 >= inner.y1 && outer.z1 >= inner.z1;
}

function inside(b, x, y, z) {
  return x >= b.x0 && x <= b.x1 && y >= b.y0 && y <= b.y1 && z >= b.z0 && z <= b.z1;
}

/** The overlap of two boxes, or null. */
function clip(a, b) {
  if (!bp.overlaps(a, b)) return null;
  return { x0: Math.max(a.x0, b.x0), y0: Math.max(a.y0, b.y0), z0: Math.max(a.z0, b.z0), x1: Math.min(a.x1, b.x1), y1: Math.min(a.y1, b.y1), z1: Math.min(a.z1, b.z1) };
}

/** The wing a position belongs to: whose room (and a margin) holds it, else the area's name. */
function wingAt(dim, x, z, fallback = '?') {
  const w = campus.WINGS.find((v) => v.room && v.dim === dim && x >= v.room.x0 - 6 && x <= v.room.x1 + 6 && z >= v.room.z0 - 6 && z <= v.room.z1 + 6);
  return w ? w.id : fallback;
}

// ---- schematic grids ------------------------------------------------------------------------

/**
 * A block grid in the world: a Sponge schematic (version 2 or 3) read with its minimum corner at
 * `box`'s, or one made from scratch. `palette` is block states, `ids` an index into it per
 * position (x fastest, then z, then y, as Sponge stores them).
 */
class Grid {
  constructor({ dim, box, palette, ids, blockEntities = [], entities = [], tree = null, dataVersion = 4671 }) {
    Object.assign(this, { dim, box, palette, ids, blockEntities, entities, tree, dataVersion });
    this.w = box.x1 - box.x0 + 1;
    this.h = box.y1 - box.y0 + 1;
    this.l = box.z1 - box.z0 + 1;
    this.names = palette.map((s) => s.replace(/\[.*$/, ''));
  }

  static make({ dim = campus.OVERWORLD, box, fill = AIR, blocks = [], entities = [] }) {
    const palette = [fill];
    const ids = new Uint32Array(bp.volume(box));
    const g = new Grid({ dim, box, palette, ids, entities });
    for (const b of blocks) g.set(b.x, b.y, b.z, b.block);
    return g;
  }

  get volume() { return this.ids.length; }

  index(x, y, z) {
    return ((y - this.box.y0) * this.l + (z - this.box.z0)) * this.w + (x - this.box.x0);
  }

  /** The world position of index i. */
  at(i) {
    const x = i % this.w;
    const z = Math.floor(i / this.w) % this.l;
    const y = Math.floor(i / (this.w * this.l));
    return [this.box.x0 + x, this.box.y0 + y, this.box.z0 + z];
  }

  idOf(state) {
    let k = this.palette.indexOf(state);
    if (k < 0) {
      k = this.palette.length;
      this.palette.push(state);
      this.names.push(state.replace(/\[.*$/, ''));
    }
    return k;
  }

  set(x, y, z, state) { this.ids[this.index(x, y, z)] = this.idOf(state); }

  /** The block's name without its state, e.g. minecraft:oak_stairs. */
  name(x, y, z) { return this.names[this.ids[this.index(x, y, z)]]; }

  /** A mask over this grid: 1 where a box of `list` in this grid's dimension covers it. */
  maskOf(list) {
    const m = new Uint8Array(this.volume);
    for (const k of list) {
      if (k.dim !== this.dim) continue;
      const c = clip(this.box, k.box);
      if (!c) continue;
      for (let y = c.y0; y <= c.y1; y++) {
        for (let z = c.z0; z <= c.z1; z++) {
          const row = this.index(c.x0, y, z);
          m.fill(1, row, row + (c.x1 - c.x0) + 1);
        }
      }
    }
    return m;
  }

  /** The skin mask: 1 in a skin's outer box but not in its own test volume. */
  skinMaskOf(list) {
    const outer = this.maskOf(list.map((s) => ({ dim: s.dim, box: s.outer })));
    const inner = this.maskOf(list.map((s) => ({ dim: s.dim, box: s.inner })));
    for (let i = 0; i < outer.length; i++) if (inner[i]) outer[i] = 0;
    return outer;
  }
}

function varints(bytes, n) {
  const out = new Uint32Array(n);
  let i = 0;
  let k = 0;
  while (k < n) {
    let v = 0;
    let shift = 0;
    let b;
    do {
      b = bytes[i++] & 0xff;
      v |= (b & 0x7f) << shift;
      shift += 7;
    } while (b & 0x80);
    out[k++] = v;
  }
  return out;
}

function toVarints(ids) {
  const out = [];
  for (let v of ids) {
    while (v > 0x7f) { out.push((v & 0x7f) | 0x80); v >>>= 7; }
    out.push(v);
  }
  return out.map((b) => (b > 127 ? b - 256 : b));
}

/**
 * Reads a Sponge schematic as a Grid, its origin pasted at `at` (so its box is at + its minimum
 * corner's offset); entities' positions come back in the world too.
 */
async function readGrid(file, { dim = campus.OVERWORLD, at }) {
  const { parsed } = await nbt.parse(fs.readFileSync(file));
  const top = parsed.value.Schematic ? parsed.value.Schematic.value : parsed.value;
  const v = (name) => (top[name] ? nbt.simplify(top[name]) : undefined);
  const version = v('Version');
  const [w, h, l] = [v('Width'), v('Height'), v('Length')].map((n) => n & 0xffff);
  let min = [0, 0, 0];
  let blocks;
  let paletteTag;
  let entitiesTag = top.Entities;
  if (version === 3) {
    if (top.Offset) min = v('Offset');
    blocks = top.Blocks.value;
    paletteTag = blocks.Palette;
  } else {
    const m = v('Metadata') || {};
    if (m.WEOffsetX !== undefined) min = [m.WEOffsetX, m.WEOffsetY, m.WEOffsetZ];
    blocks = { Data: top.BlockData, BlockEntities: top.BlockEntities };
    paletteTag = top.Palette;
  }
  const pal = nbt.simplify(paletteTag);
  const palette = [];
  for (const [state, k] of Object.entries(pal)) palette[k] = state;
  const box = bp.box3(at.x + min[0], at.y + min[1], at.z + min[2], at.x + min[0] + w - 1, at.y + min[1] + h - 1, at.z + min[2] + l - 1);
  const ids = varints(blocks.Data.value, w * h * l);
  const blockEntities = blocks.BlockEntities ? nbt.simplify(blocks.BlockEntities) : [];
  const corner = [box.x0, box.y0, box.z0];
  const entities = (entitiesTag ? nbt.simplify(entitiesTag) : []).map((e) => ({
    id: e.Id, pos: e.Pos.map((p, i) => p + [at.x, at.y, at.z][i]),
  }));
  const g = new Grid({ dim, box, palette, ids, blockEntities, entities, tree: { parsed, version, entitiesTag }, dataVersion: v('DataVersion') });
  g.corner = corner;
  return g;
}

/**
 * Writes a Grid as a Sponge schematic, origin at its minimum corner. A grid read from a file
 * keeps that file's tags (DataVersion, metadata), with its blocks, block entities and entities
 * replaced by the grid's own; `keepEntities` (indices into the file's Entities) picks those kept.
 */
function writeGrid(g, file, { keepEntities = null } = {}) {
  // Only the palette entries still used, renumbered: a stripped placeholder leaves no trace.
  const used = new Map();
  const ids = new Uint32Array(g.ids.length);
  for (let i = 0; i < g.ids.length; i++) {
    let k = used.get(g.ids[i]);
    if (k === undefined) { k = used.size; used.set(g.ids[i], k); }
    ids[i] = k;
  }
  const palette = {};
  for (const [old, k] of used) palette[g.palette[old]] = { type: 'int', value: k };
  const data = { type: 'byteArray', value: toVarints(ids) };
  const listOf = (items) => ({ type: 'list', value: { type: items.length ? 'compound' : 'end', value: items } });
  let root;
  if (g.tree && g.tree.version === 3) {
    root = g.tree.parsed;
    const s = root.value.Schematic.value;
    s.Offset = { type: 'intArray', value: [0, 0, 0] };
    s.Blocks.value.Palette = { type: 'compound', value: palette };
    s.Blocks.value.Data = data;
    s.Blocks.value.BlockEntities = listOf(g.blockEntitiesTags || []);
    s.Entities = listOf(keepEntities && g.tree.entitiesTag ? keepEntities.map((i) => g.tree.entitiesTag.value.value[i]) : []);
  } else {
    root = {
      type: 'compound',
      name: '',
      value: {
        Schematic: {
          type: 'compound',
          value: {
            Version: { type: 'int', value: 3 },
            DataVersion: { type: 'int', value: g.dataVersion },
            Width: { type: 'short', value: g.w },
            Height: { type: 'short', value: g.h },
            Length: { type: 'short', value: g.l },
            Offset: { type: 'intArray', value: [0, 0, 0] },
            Blocks: { type: 'compound', value: { Palette: { type: 'compound', value: palette }, Data: data, BlockEntities: listOf([]) } },
            Entities: listOf((g.entities || []).map((e) => ({
              Id: { type: 'string', value: e.id },
              Pos: { type: 'list', value: { type: 'double', value: e.pos.map((p, i) => p - [g.box.x0, g.box.y0, g.box.z0][i]) } },
            }))),
          },
        },
      },
    };
  }
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, zlib.gzipSync(nbt.writeUncompressed(root)));
}

// ---- check and export -----------------------------------------------------------------------

function blockKey(e) {
  return `${e.id} ${e.pos.map(Math.floor).join(' ')}`;
}

/**
 * The keep-clear check over one area: `current` and `baseline` grids of the same box (what is
 * there now, and what the campus put there with its placeholders). Returns
 * [{ kind, dim, x, y, z, block, was, what, wing }]:
 *   inside      a block that is not the campus's in a protected volume (a placeholder, or air
 *               where a placeholder was, is not one);
 *   skin        an active part in a chamber's 2-block skin that the campus did not put there;
 *   entity      a new entity of a kind a designer may not place, anywhere;
 *   entity-skin a new item frame or armour stand in a protected volume or a skin;
 *   stray       a placeholder block outside the protected volumes, which export turns to air.
 */
function compareArea({ area, current, baseline, protect, skinList }) {
  if (current.volume !== baseline.volume || current.box.x0 !== baseline.box.x0 || current.box.y0 !== baseline.box.y0 || current.box.z0 !== baseline.box.z0) {
    throw new Error(`design: ${area.name}'s grid is not its baseline's box`);
  }
  const out = [];
  const prot = current.maskOf(protect);
  const skin = current.skinMaskOf(skinList);
  // The smallest box that holds the position names it best: a cell's clear volume, not its walls.
  const whatAt = (list, x, y, z) => {
    const hits = list.filter((k) => k.dim === current.dim && inside(k.box || k.outer, x, y, z));
    hits.sort((p, q) => bp.volume(p.box || p.outer) - bp.volume(q.box || q.outer));
    return hits.length ? hits[0].what : '?';
  };
  const push = (kind, i, extra) => {
    const [x, y, z] = current.at(i);
    out.push({ kind, dim: current.dim, x, y, z, wing: wingAt(current.dim, x, z, area.name), ...extra });
  };
  const cn = current.names;
  const bn = baseline.names;
  const ph = cn.map((n) => PLACEHOLDERS.has(n));
  const bph = bn.map((n) => PLACEHOLDERS.has(n));
  const active = cn.map((n) => ACTIVE.test(n));
  for (let i = 0; i < current.ids.length; i++) {
    const c = current.ids[i];
    const b = baseline.ids[i];
    if (prot[i]) {
      if (cn[c] === bn[b] || ph[c] || (cn[c] === AIR && bph[b])) continue;
      const [x, y, z] = current.at(i);
      push('inside', i, { block: cn[c], was: bn[b], what: whatAt(protect, x, y, z) });
    } else if (ph[c]) {
      push('stray', i, { block: cn[c], what: 'outside every protected volume' });
    } else if (skin[i] && active[c] && cn[c] !== bn[b]) {
      const [x, y, z] = current.at(i);
      push('skin', i, { block: cn[c], was: bn[b], what: whatAt(skinList, x, y, z) });
    }
  }
  const before = new Set(baseline.entities.map(blockKey));
  for (const e of current.entities) {
    if (before.has(blockKey(e))) continue;
    const [x, y, z] = e.pos.map(Math.floor);
    if (!inside(current.box, x, y, z)) continue;
    const i = current.index(x, y, z);
    if (!ALLOWED_ENTITIES.has(e.id)) {
      out.push({ kind: 'entity', dim: current.dim, x, y, z, block: e.id, what: 'an entity a design may not hold', wing: wingAt(current.dim, x, z, area.name) });
    } else if (prot[i] || skin[i]) {
      out.push({ kind: 'entity-skin', dim: current.dim, x, y, z, block: e.id, what: prot[i] ? whatAt(protect, x, y, z) : whatAt(skinList, x, y, z), wing: wingAt(current.dim, x, z, area.name) });
    }
  }
  return out;
}

/**
 * The export of one area: `current` with structure void at every protected position, every
 * placeholder and structure void elsewhere turned to air, the block entities of positions kept
 * as they were, and only the entities a designer added that may stay (compareArea's rules).
 * Returns { grid, masked, stripped, keepEntities } (keepEntities: indices into current.entities).
 */
function exportArea({ current, baseline, protect, skinList }) {
  const prot = current.maskOf(protect);
  const skin = current.skinMaskOf(skinList);
  const ids = new Uint32Array(current.ids.length);
  const g = new Grid({ ...current, ids, palette: [...current.palette] });
  g.tree = current.tree;
  const mask = g.idOf(MASK);
  const air = g.idOf(AIR);
  const strip = current.names.map((n) => PLACEHOLDERS.has(n) || n === MASK);
  let masked = 0;
  let stripped = 0;
  for (let i = 0; i < ids.length; i++) {
    const c = current.ids[i];
    if (prot[i]) { ids[i] = mask; masked++; } else if (strip[c]) { ids[i] = air; stripped++; } else ids[i] = c;
  }
  // Block entities (a chest's contents, a sign's words) only where the block itself was kept.
  const corner = [current.box.x0, current.box.y0, current.box.z0];
  const raw = current.tree && current.tree.version === 3 ? current.tree.parsed.value.Schematic.value.Blocks.value.BlockEntities : null;
  const rawList = raw && raw.value && Array.isArray(raw.value.value) ? raw.value.value : [];
  g.blockEntitiesTags = rawList.filter((t, k) => {
    const p = current.blockEntities[k] && current.blockEntities[k].Pos;
    if (!p) return false;
    const i = current.index(corner[0] + p[0], corner[1] + p[1], corner[2] + p[2]);
    return !prot[i] && !strip[current.ids[i]];
  });
  const before = new Set(baseline.entities.map(blockKey));
  const keepEntities = [];
  current.entities.forEach((e, k) => {
    if (before.has(blockKey(e)) || !ALLOWED_ENTITIES.has(e.id)) return;
    const [x, y, z] = e.pos.map(Math.floor);
    if (!inside(current.box, x, y, z)) return;
    const i = current.index(x, y, z);
    if (!prot[i] && !skin[i]) keepEntities.push(k);
  });
  g.entities = keepEntities.map((k) => current.entities[k]);
  return { grid: g, masked, stripped, keepEntities };
}

/**
 * Whether a design grid may be pasted on a version whose protected boxes are `protect`: structure
 * void at every protected position, no placeholder anywhere, and no entity of a kind or in a
 * place the design may not have. Returns sentences, none if it may.
 */
function pasteProblems(grid, protect, skinList, label) {
  const problems = [];
  const prot = grid.maskOf(protect);
  const mask = grid.names.indexOf(MASK) >= 0 ? new Set(grid.names.map((n, k) => (n === MASK ? k : -1))) : new Set();
  let bad = 0;
  let first = null;
  for (let i = 0; i < prot.length; i++) {
    if (prot[i] && !mask.has(grid.ids[i])) {
      bad++;
      if (!first) first = grid.at(i);
    }
  }
  if (bad) {
    const [x, y, z] = first;
    const k = protect.find((p) => p.dim === grid.dim && inside(p.box, x, y, z));
    problems.push(`${label} would paste ${bad} block(s) into protected volumes, first ${grid.name(x, y, z)} at ${x} ${y} ${z} in ${k ? k.what : '?'}`);
  }
  const ph = grid.names.filter((n, k) => PLACEHOLDERS.has(n) && grid.ids.includes(k));
  if (ph.length) problems.push(`${label} holds design-mode placeholder blocks (${[...new Set(ph)].join(', ')})`);
  const skin = grid.skinMaskOf(skinList);
  for (const e of grid.entities) {
    const [x, y, z] = e.pos.map(Math.floor);
    const i = inside(grid.box, x, y, z) ? grid.index(x, y, z) : -1;
    if (!ALLOWED_ENTITIES.has(e.id)) problems.push(`${label} holds a ${e.id} at ${x} ${y} ${z}: a design holds only item frames and armour stands`);
    else if (i >= 0 && (prot[i] || skin[i])) problems.push(`${label} holds a ${e.id} at ${x} ${y} ${z}, in a protected volume or a chamber's skin`);
  }
  return problems;
}

// ---- reports, placements, manifest ----------------------------------------------------------

const KIND_WORDS = {
  inside: 'inside a protected volume',
  skin: 'an active part in a chamber\'s 2-block skin',
  entity: 'an entity a design may not hold',
  'entity-skin': 'an item frame or armour stand too close to a test',
  stray: 'a placeholder block outside the test volumes (export turns it to air)',
};

function itemLine(p) {
  const where = p.dim === campus.OVERWORLD ? '' : ` (${p.dim.replace('minecraft:', '')})`;
  const was = p.was && p.was !== p.block ? `, where the campus has ${p.was.replace('minecraft:', '')}` : '';
  return `${p.x} ${p.y} ${p.z}${where}  ${p.block.replace('minecraft:', '')}  ${p.what} [${p.wing}]${was}`;
}

/** The check's report: a summary line, then every item by kind. */
function formatReport(items, { when = new Date(), who = null } = {}) {
  const lines = [`Facility design check, ${when.toISOString()}${who ? `, by ${who}` : ''}`];
  const problems = items.filter((p) => p.kind !== 'stray');
  lines.push(problems.length ? `${problems.length} problem(s) to fix` : 'No problems: every protected volume and skin is as the campus built it.');
  const strays = items.length - problems.length;
  if (strays) lines.push(`${strays} placeholder block(s) outside the protected volumes, which export turns to air`);
  for (const kind of Object.keys(KIND_WORDS)) {
    const mine = items.filter((p) => p.kind === kind);
    if (!mine.length) continue;
    lines.push('', `${KIND_WORDS[kind]}: ${mine.length}`);
    for (const p of mine) lines.push(`  ${itemLine(p)}`);
  }
  return `${lines.join('\n')}\n`;
}

/** The chat summary: a line, then the first `n` problems. */
function chatSummary(items, n = 8) {
  const problems = items.filter((p) => p.kind !== 'stray');
  const strays = items.length - problems.length;
  const head = problems.length ? `check: ${problems.length} problem(s)` : 'check: no problems';
  return [`${head}${strays ? `; ${strays} stray placeholder block(s), which export removes` : ''}`, ...problems.slice(0, n).map(itemLine),
    ...(problems.length > n ? [`... and ${problems.length - n} more in the report`] : [])];
}

/** placements.json for an export: each area's file, pasted at its own corner, guarded. */
function placementsFor(list) {
  return list.map((a) => ({ file: a.file, at: { x: a.box.x0, y: a.box.y0, z: a.box.z0 }, rotation: 0, dim: a.dim, guarded: true, minVersion: APPLIES_FROM }));
}

function manifestFor({ commit, generatedFrom = null, designer, date = new Date(), plugin = null, areaList, problems }) {
  return {
    kind: 'wormhole-facility-design',
    format: 1,
    facility: { commit, generatedFrom },
    minecraft: VERSION,
    appliesFrom: APPLIES_FROM,
    designer,
    date: date.toISOString(),
    plugin,
    placeholders: PLACEHOLDER,
    mask: MASK,
    areas: areaList.map((a) => ({ name: a.name, dim: a.dim, box: a.box, file: a.file })),
    checkProblems: problems,
  };
}

/** A local yyyy-mm-dd for the export's name. */
function dateStamp(d = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

module.exports = {
  VERSION, APPLIES_FROM, PLACEHOLDER, PLACEHOLDERS, MASK, ACTIVE, ALLOWED_ENTITIES, SKIN, MARGIN,
  areas, boardBoxes, protectedBoxes, protectedFor, skins, placeholderFills, fillCommand, grow, contains, inside, clip, wingAt,
  Grid, readGrid, writeGrid, compareArea, exportArea, pasteProblems, formatReport, chatSummary, itemLine,
  placementsFor, manifestFor, dateStamp,
};
