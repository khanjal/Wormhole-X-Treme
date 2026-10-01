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
//
// A designer's schematic is untrusted input: nothing in it may run a command or spawn anything.
// Command blocks, spawners, vaults, structure and jigsaw blocks are refused; click events are
// stripped from every block entity and entity; entities are item frames and armour stands with
// a whitelist of data keys.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const nbt = require('prismarine-nbt');
const campus = require('./campus');
const bp = require('./blueprint');
const { atLeast, SUPPORTED } = require('./version');

/** The one version design mode runs, and the first a design export is applied on. */
const VERSION = '1.21.11';
const APPLIES_FROM = '1.21.11';

/** Placeholders: test volumes (cells, the shaft, the tunnel) and everything else the guardrail keeps. */
const PLACEHOLDER = { volume: 'minecraft:pink_stained_glass', fixture: 'minecraft:green_stained_glass' };
const PLACEHOLDERS = new Set(Object.values(PLACEHOLDER));
const MASK = 'minecraft:structure_void';
const AIR = 'minecraft:air';

// Rule 3's active parts: what the plugin or the tests react to, everything redstone or a fluid
// moves, and fluids and fire themselves.
const ACTIVE = new RegExp('^minecraft:(redstone_wire|redstone_torch|redstone_wall_torch|redstone_block|repeater|comparator'
  + '|target|daylight_detector|tripwire|tripwire_hook|dispenser|dropper|crafter|sculk_sensor|calibrated_sculk_sensor'
  + '|rail|powered_rail|detector_rail|activator_rail|\\w+_sign|\\w+_wall_sign|\\w+_hanging_sign|\\w+_wall_hanging_sign'
  + '|\\w+_button|lever|\\w+_pressure_plate|\\w+_banner|\\w+_wall_banner|hopper|piston|sticky_piston|piston_head|moving_piston'
  + '|observer|command_block|chain_command_block|repeating_command_block'
  + '|\\w+_door|\\w+_trapdoor|\\w+_fence_gate|redstone_lamp|(\\w+_)?copper_bulb|note_block|bell|(\\w+_)?lightning_rod|tnt'
  + '|jukebox|lectern|water|lava|fire|soul_fire|bubble_column)$');

// Blocks no design may hold anywhere: they run commands, spawn mobs, hand out loot or place structures.
const FORBIDDEN = /^minecraft:(command_block|chain_command_block|repeating_command_block|spawner|trial_spawner|vault|structure_block|jigsaw|test_block|test_instance_block)$/;

/** Entities a designer may place (rule 6), outside every protected volume and skin. */
const ALLOWED_ENTITIES = new Set(['minecraft:item_frame', 'minecraft:glow_item_frame', 'minecraft:armor_stand']);
/** Entities that come and go on their own (a dropped item, a falling block): neither checked nor exported. */
const TRANSIENT_ENTITIES = new Set(['minecraft:item', 'minecraft:falling_block', 'minecraft:experience_orb']);
/** The entity data an exported item frame or armour stand keeps; anything else (Passengers, Tags, UUID) is dropped. */
const ENTITY_KEYS = new Set(['Pos', 'Rotation', 'Motion', 'Facing', 'Fixed', 'Invisible', 'Item', 'ItemRotation', 'ItemDropChance',
  'block_pos', 'TileX', 'TileY', 'TileZ', 'NoBasePlate', 'ShowArms', 'Small', 'Marker', 'Pose', 'ArmorItems', 'HandItems', 'equipment',
  'drop_chances', 'ArmorDropChances', 'HandDropChances', 'DisabledSlots', 'NoGravity', 'Invulnerable', 'CustomName', 'CustomNameVisible',
  'Silent', 'Glowing', 'OnGround', 'Air', 'Fire', 'fall_distance', 'FallDistance', 'HurtTime', 'DeathTime', 'Health', 'AbsorptionAmount',
  'HurtByTimestamp', 'FallFlying', 'PortalCooldown']);
const CLICK_KEYS = new Set(['clickEvent', 'click_event']);
const CLICK_TEXT = /click_?event/i;

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
      const id = m && /"wx_board_([^"]+)"/.exec(m[4]);
      if (!id) continue;
      const [x, y, z] = [m[1], m[2], m[3]].map((n) => Math.floor(Number(n)));
      out.push({ what: `the ${id[1]} board`, dim: f.dim, box: bp.box3(x, y, z, x, y + 1, z), kind: 'fixture', entities: true });
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

/** The versions a design export is pasted on: every supported version from APPLIES_FROM on. */
function maskVersions(supported = SUPPORTED) {
  return supported.filter((v) => atLeast(v, APPLIES_FROM));
}

/**
 * The union of the protected boxes over these versions (`boxesOf(version)`, protectedBoxes by
 * default): what design mode marks, checks and masks, so an export pastes on each of them.
 */
function protectedFor(versions = maskVersions(), boxesOf = protectedBoxes) {
  const seen = new Set();
  const out = [];
  for (const v of versions) {
    for (const k of boxesOf(v)) {
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

// ---- untrusted NBT --------------------------------------------------------------------------

/** A text component given as JSON, with its click events taken out; a string that is not JSON is dropped. */
function scrubJson(s) {
  if (!CLICK_TEXT.test(s)) return s;
  const strip = (v) => {
    if (Array.isArray(v)) return v.map(strip);
    if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).filter(([k]) => !CLICK_KEYS.has(k)).map(([k, x]) => [k, strip(x)]));
    if (typeof v === 'string' && CLICK_TEXT.test(v)) return scrubJson(v);
    return v;
  };
  try { return JSON.stringify(strip(JSON.parse(s))); } catch { return ''; }
}

/** Takes every click event out of a raw NBT value (prismarine-nbt's { type, value }) in place; returns how many. */
function scrubTag(node) {
  let n = 0;
  const compound = (obj) => {
    for (const k of Object.keys(obj)) {
      if (CLICK_KEYS.has(k)) { delete obj[k]; n++; } else n += scrubTag(obj[k]);
    }
  };
  const list = (l) => {
    if (!l || !Array.isArray(l.value)) return;
    if (l.type === 'compound') l.value.forEach(compound);
    else if (l.type === 'list') l.value.forEach(list);
    else if (l.type === 'string') {
      l.value = l.value.map((s) => { const t = scrubJson(s); if (t !== s) n++; return t; });
    }
  };
  if (!node) return 0;
  if (node.type === 'compound') compound(node.value);
  else if (node.type === 'list') list(node.value);
  else if (node.type === 'string') {
    const t = scrubJson(node.value);
    if (t !== node.value) { node.value = t; n++; }
  }
  return n;
}

function hasClick(tag) {
  return CLICK_TEXT.test(JSON.stringify(tag || {}));
}

/** An entity's raw compound with only the whitelisted data, click events out; Passengers and the rest dropped. */
function sanitizeEntity(raw) {
  const out = structuredClone(raw);
  const data = out.Data && out.Data.value;
  if (data) for (const k of Object.keys(data)) if (!ENTITY_KEYS.has(k)) delete data[k];
  scrubTag({ type: 'compound', value: out });
  return out;
}

/** The data keys of an entity that are not whitelisted. */
function entityExtras(e) {
  return Object.keys(e.data || {}).filter((k) => !ENTITY_KEYS.has(k));
}

// ---- schematic grids ------------------------------------------------------------------------

/**
 * A block grid in the world: a Sponge version 3 schematic read with its origin at its minimum
 * corner, or one made from scratch. `palette` is block states, `ids` an index into it per position
 * (x fastest, then z, then y, as Sponge stores them). `blockEntities` are
 * [{ index, tag (raw), data }] and `entities` [{ id, pos (world), tag (raw), data }], positions in
 * the schematic relative to its minimum corner.
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

  /** The block's full state, e.g. minecraft:lever[face=wall,facing=east,powered=false]. */
  state(x, y, z) { return this.palette[this.ids[this.index(x, y, z)]]; }

  /** A block entity's data at index i, as JSON text, or '' (to compare two grids'). */
  blockData(i) {
    if (!this.beIndex) this.beIndex = new Map(this.blockEntities.map((b) => [b.index, JSON.stringify(b.data || {})]));
    return this.beIndex.get(i) || '';
  }

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

function varints(bytes, n, size) {
  const out = new Uint32Array(n);
  let i = 0;
  let k = 0;
  while (k < n) {
    let v = 0;
    let shift = 0;
    let b;
    do {
      if (i >= bytes.length) throw new Error('its block data ends early');
      b = bytes[i++] & 0xff;
      v |= (b & 0x7f) << shift;
      shift += 7;
    } while (b & 0x80 && shift < 35);
    if (v >= size) throw new Error(`its block data names palette entry ${v} of ${size}`);
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

/** A palette tag as a list of states, refused if an index is missing, repeated or out of range. */
function readPalette(pal, file) {
  const entries = Object.entries(pal);
  const palette = new Array(entries.length);
  for (const [state, k] of entries) {
    if (!Number.isInteger(k) || k < 0 || k >= entries.length || palette[k] !== undefined) {
      throw new Error(`${path.basename(file)}: its palette gives ${state} the index ${k}, which is missing, repeated or out of range`);
    }
    palette[k] = state;
  }
  return palette;
}

/**
 * Reads a design schematic (Sponge version 3, origin at its minimum corner, as WorldEdit saves a
 * console copy from pos1 and as writeGrid writes) as a Grid with that corner at `at`. Version 2
 * is refused rather than read without its block entities.
 */
async function readGrid(file, { dim = campus.OVERWORLD, at }) {
  const { parsed } = await nbt.parse(fs.readFileSync(file));
  const top = parsed.value.Schematic ? parsed.value.Schematic.value : parsed.value;
  const v = (name) => (top[name] ? nbt.simplify(top[name]) : undefined);
  const version = v('Version');
  if (version !== 3) throw new Error(`${path.basename(file)} is Sponge schematic version ${version}: a design schematic is version 3 (WorldEdit 7.3 and later)`);
  const [w, h, l] = [v('Width'), v('Height'), v('Length')].map((n) => n & 0xffff);
  const offset = top.Offset ? v('Offset') : [0, 0, 0];
  if (offset.some((n) => n !== 0)) throw new Error(`${path.basename(file)}: a design schematic has its origin at its minimum corner (Offset 0 0 0), not ${offset.join(' ')}`);
  if (!top.Blocks) throw new Error(`${path.basename(file)} has no blocks`);
  const blocks = top.Blocks.value;
  const palette = readPalette(nbt.simplify(blocks.Palette), file);
  const box = bp.box3(at.x, at.y, at.z, at.x + w - 1, at.y + h - 1, at.z + l - 1);
  const ids = varints(blocks.Data.value, w * h * l, palette.length);
  const rawBe = blocks.BlockEntities && blocks.BlockEntities.value && Array.isArray(blocks.BlockEntities.value.value) ? blocks.BlockEntities.value.value : [];
  const corner = [box.x0, box.y0, box.z0];
  const g = new Grid({ dim, box, palette, ids, tree: { parsed }, dataVersion: v('DataVersion') });
  g.blockEntities = rawBe.map((tag) => {
    const s = nbt.simplify({ type: 'compound', value: tag });
    const p = s.Pos || [];
    if (p.length !== 3 || p.some((n, k) => n < 0 || n >= [w, h, l][k])) throw new Error(`${path.basename(file)}: a block entity at ${p.join(' ')} is outside it`);
    return { index: g.index(corner[0] + p[0], corner[1] + p[1], corner[2] + p[2]), tag, data: s.Data || {} };
  });
  const rawEnt = top.Entities && top.Entities.value && Array.isArray(top.Entities.value.value) ? top.Entities.value.value : [];
  g.entities = rawEnt.map((tag) => {
    const s = nbt.simplify({ type: 'compound', value: tag });
    return { id: s.Id, pos: (s.Pos || [0, 0, 0]).map((q, k) => q + corner[k]), tag, data: s.Data || {} };
  });
  return g;
}

/**
 * Writes a Grid as a Sponge version 3 schematic, origin at its minimum corner, with its block
 * entities' and entities' raw tags (or, for an entity made from scratch, its id and position).
 */
function writeGrid(g, file) {
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
  const listOf = (items) => ({ type: 'list', value: { type: items.length ? 'compound' : 'end', value: items } });
  const corner = [g.box.x0, g.box.y0, g.box.z0];
  const entities = g.entities.map((e) => e.tag || {
    Id: { type: 'string', value: e.id },
    Pos: { type: 'list', value: { type: 'double', value: e.pos.map((p, k) => p - corner[k]) } },
  });
  const old = g.tree && g.tree.parsed && g.tree.parsed.value.Schematic ? g.tree.parsed.value.Schematic.value : {};
  const root = {
    type: 'compound',
    name: '',
    value: {
      Schematic: {
        type: 'compound',
        value: {
          ...(old.Metadata ? { Metadata: old.Metadata } : {}),
          Version: { type: 'int', value: 3 },
          DataVersion: { type: 'int', value: g.dataVersion },
          Width: { type: 'short', value: g.w },
          Height: { type: 'short', value: g.h },
          Length: { type: 'short', value: g.l },
          Offset: { type: 'intArray', value: [0, 0, 0] },
          Blocks: {
            type: 'compound',
            value: { Palette: { type: 'compound', value: palette }, Data: { type: 'byteArray', value: toVarints(ids) }, BlockEntities: listOf(g.blockEntities.map((b) => b.tag)) },
          },
          Entities: listOf(entities),
        },
      },
    },
  };
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, zlib.gzipSync(nbt.writeUncompressed(root)));
}

// ---- check and export -----------------------------------------------------------------------

function settled(state) {
  return state.replace(/(?<=[[,])(powered|open|lit|triggered)=\w+,?/g, '').replace(/,\]$/, ']').replace(/\[\]$/, '');
}

function blockKey(e) {
  return `${e.id} ${e.pos.map(Math.floor).join(' ')}`;
}

/**
 * The keep-clear check over one area: `current` and `baseline` grids of the same box (what is
 * there now, and what the campus put there with its placeholders). Returns
 * [{ kind, dim, x, y, z, block, was, what, wing }]:
 *   inside      a block that is not the campus's in a protected volume (a placeholder, or air
 *               where a placeholder was, is not one);
 *   skin        in a chamber's 2-block skin: an active part that is not the campus's own, a
 *               campus active part (or block with data, such as a sign) changed in any way, or a
 *               campus block taken away;
 *   forbidden   a command block, spawner, vault, structure or jigsaw block the campus did not put there;
 *   click       a block entity of the designer's whose text runs a command when clicked;
 *   entity      a new entity of a kind a design may not hold, anywhere;
 *   entity-skin a new item frame or armour stand in a protected volume or a skin;
 *   entity-data an item frame or armour stand carrying riders, tags or click events;
 *   stray       a placeholder block outside the protected volumes, which export turns to air.
 * Dropped items and falling blocks are not looked at.
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
  const bActive = bn.map((n) => ACTIVE.test(n));
  const forbidden = cn.map((n) => FORBIDDEN.test(n));
  const beAt = new Set(baseline.blockEntities.map((b) => b.index));
  for (let i = 0; i < current.ids.length; i++) {
    const c = current.ids[i];
    const b = baseline.ids[i];
    if (prot[i]) {
      if (cn[c] === bn[b] || ph[c] || (cn[c] === AIR && bph[b])) continue;
      const [x, y, z] = current.at(i);
      push('inside', i, { block: cn[c], was: bn[b], what: whatAt(protect, x, y, z) });
    } else if (ph[c]) {
      push('stray', i, { block: cn[c], what: 'outside every protected volume' });
    } else if (forbidden[c] && cn[c] !== bn[b]) {
      push('forbidden', i, { block: cn[c], was: bn[b], what: 'a block no design may hold' });
    } else if (skin[i]) {
      // A campus part's state, less what using it changes (a lever pulled, a door opened, a lamp lit).
      const cs = settled(current.palette[c]);
      const bs = settled(baseline.palette[b]);
      const campusPart = bActive[b] || beAt.has(i);
      let why = null;
      if (active[c] && cs !== bs && !campusPart) why = 'an active part';
      else if (campusPart && (cs !== bs || current.blockData(i) !== baseline.blockData(i))) why = cn[c] === bn[b] ? 'the campus\'s part, changed' : 'the campus\'s part, replaced';
      else if (cn[c] === AIR && bn[b] !== AIR && !bph[b]) why = 'a campus block taken away';
      if (why) {
        const [x, y, z] = current.at(i);
        push('skin', i, { block: cn[c], was: bn[b], what: `${whatAt(skinList, x, y, z)}: ${why}` });
      }
    }
  }
  for (const be of current.blockEntities) {
    if (prot[be.index] || !hasClick(be.data) || current.blockData(be.index) === baseline.blockData(be.index)) continue;
    push('click', be.index, { block: cn[current.ids[be.index]], what: 'text that runs a command when clicked (export removes it)' });
  }
  const before = new Set(baseline.entities.map(blockKey));
  for (const e of current.entities) {
    if (TRANSIENT_ENTITIES.has(e.id) || before.has(blockKey(e))) continue;
    const [x, y, z] = e.pos.map(Math.floor);
    if (!inside(current.box, x, y, z)) continue;
    const i = current.index(x, y, z);
    const at = { dim: current.dim, x, y, z, block: e.id, wing: wingAt(current.dim, x, z, area.name) };
    if (!ALLOWED_ENTITIES.has(e.id)) out.push({ kind: 'entity', ...at, what: 'an entity a design may not hold' });
    else if (prot[i] || skin[i]) out.push({ kind: 'entity-skin', ...at, what: prot[i] ? whatAt(protect, x, y, z) : whatAt(skinList, x, y, z) });
    else if (entityExtras(e).length || hasClick(e.data)) {
      out.push({ kind: 'entity-data', ...at, what: `carries ${[...entityExtras(e), ...(hasClick(e.data) ? ['a click event'] : [])].join(', ')} (export removes it)` });
    }
  }
  return out;
}

/**
 * The export of one area: `current` with structure void at every protected position and at
 * every campus block no design may hold (the campus builds those itself), every placeholder,
 * structure void and designer's forbidden block elsewhere turned to air, the block entities of
 * kept blocks with their click events out, and only the item frames and armour stands the
 * designer added outside the volumes and skins, with whitelisted data. Returns
 * { grid, masked, stripped, scrubbed, entities }.
 */
function exportArea({ current, baseline, protect, skinList }) {
  const prot = current.maskOf(protect);
  const skin = current.skinMaskOf(skinList);
  const ids = new Uint32Array(current.ids.length);
  const g = new Grid({ dim: current.dim, box: current.box, palette: [...current.palette], ids, tree: current.tree, dataVersion: current.dataVersion });
  const mask = g.idOf(MASK);
  const air = g.idOf(AIR);
  const strip = current.names.map((n) => PLACEHOLDERS.has(n) || n === MASK);
  const forbidden = current.names.map((n) => FORBIDDEN.test(n));
  // A campus block with text that runs a command (none today), untouched: left to the campus too.
  const campusClick = new Set(baseline.blockEntities.filter((x) => hasClick(x.data)).map((x) => x.index));
  const untouched = (i) => campusClick.has(i) && current.palette[current.ids[i]] === baseline.palette[baseline.ids[i]] && current.blockData(i) === baseline.blockData(i);
  let masked = 0;
  let stripped = 0;
  for (let i = 0; i < ids.length; i++) {
    const c = current.ids[i];
    if (prot[i] || (forbidden[c] && current.names[c] === baseline.names[baseline.ids[i]]) || untouched(i)) { ids[i] = mask; masked++; } else if (strip[c] || forbidden[c]) { ids[i] = air; stripped++; } else ids[i] = c;
  }
  // Block entities (a chest's contents, a sign's words) only where the block itself was kept.
  let scrubbed = 0;
  g.blockEntities = current.blockEntities.filter((b) => ids[b.index] === current.ids[b.index]).map((b) => {
    const tag = structuredClone(b.tag);
    scrubbed += scrubTag({ type: 'compound', value: tag });
    return { index: b.index, tag, data: nbt.simplify({ type: 'compound', value: tag }).Data || {} };
  });
  const before = new Set(baseline.entities.map(blockKey));
  g.entities = current.entities.filter((e) => {
    if (TRANSIENT_ENTITIES.has(e.id) || before.has(blockKey(e)) || !ALLOWED_ENTITIES.has(e.id)) return false;
    const [x, y, z] = e.pos.map(Math.floor);
    if (!inside(current.box, x, y, z)) return false;
    const i = current.index(x, y, z);
    return !prot[i] && !skin[i];
  }).map((e) => {
    const tag = e.tag ? sanitizeEntity(e.tag) : null;
    return { id: e.id, pos: e.pos, tag, data: tag ? (nbt.simplify({ type: 'compound', value: tag }).Data || {}) : {} };
  });
  return { grid: g, masked, stripped, scrubbed, entities: g.entities.length };
}

/**
 * Whether a design grid may be pasted on a version whose protected boxes are `protect`: structure
 * void at every protected position, no placeholder and no block a design may not hold anywhere,
 * no click event in any block entity or entity, and no entity but an item frame or armour stand
 * with whitelisted data outside the volumes and skins. Returns sentences, none if it may.
 */
function pasteProblems(grid, protect, skinList, label) {
  const problems = [];
  const prot = grid.maskOf(protect);
  const maskIds = new Set();
  grid.names.forEach((n, k) => { if (n === MASK) maskIds.add(k); });
  let bad = 0;
  let first = null;
  for (let i = 0; i < prot.length; i++) {
    if (prot[i] && !maskIds.has(grid.ids[i])) {
      bad++;
      if (!first) first = grid.at(i);
    }
  }
  if (bad) {
    const [x, y, z] = first;
    const k = protect.find((p) => p.dim === grid.dim && inside(p.box, x, y, z));
    problems.push(`${label} would paste ${bad} block(s) into protected volumes, first ${grid.name(x, y, z)} at ${x} ${y} ${z} in ${k ? k.what : '?'}`);
  }
  const usedIds = new Set(grid.ids);
  const usedNames = [...new Set(grid.names.filter((n, k) => usedIds.has(k)))];
  const ph = usedNames.filter((n) => PLACEHOLDERS.has(n));
  if (ph.length) problems.push(`${label} holds design-mode placeholder blocks (${ph.join(', ')})`);
  const fb = usedNames.filter((n) => FORBIDDEN.test(n));
  if (fb.length) problems.push(`${label} holds blocks no design may hold (${fb.join(', ')})`);
  const clicks = grid.blockEntities.filter((b) => hasClick(b.data)).length;
  if (clicks) problems.push(`${label} holds ${clicks} block entit${clicks === 1 ? 'y' : 'ies'} with click events`);
  const skin = grid.skinMaskOf(skinList);
  for (const e of grid.entities) {
    const [x, y, z] = e.pos.map(Math.floor);
    const i = inside(grid.box, x, y, z) ? grid.index(x, y, z) : -1;
    if (!ALLOWED_ENTITIES.has(e.id)) problems.push(`${label} holds a ${e.id} at ${x} ${y} ${z}: a design holds only item frames and armour stands`);
    else if (i < 0 || prot[i] || skin[i]) problems.push(`${label} holds a ${e.id} at ${x} ${y} ${z}, outside it, in a protected volume or in a chamber's skin`);
    else if (entityExtras(e).length || hasClick(e.data)) problems.push(`${label} holds a ${e.id} at ${x} ${y} ${z} with data a design may not carry (${[...entityExtras(e), ...(hasClick(e.data) ? ['a click event'] : [])].join(', ')})`);
  }
  return problems;
}

// ---- reports, placements, manifest ----------------------------------------------------------

const KIND_WORDS = {
  inside: 'inside a protected volume',
  skin: 'in a chamber\'s 2-block skin',
  forbidden: 'a block no design may hold (export removes it)',
  click: 'a click event (export removes it)',
  entity: 'an entity a design may not hold',
  'entity-skin': 'an item frame or armour stand too close to a test',
  'entity-data': 'an item frame or armour stand carrying data a design may not (export removes it)',
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

function manifestFor({ commit, generatedFrom = null, designer, date = new Date(), plugin = null, areaList, problems, worlds = false }) {
  return {
    kind: 'wormhole-facility-design',
    format: 1,
    facility: { commit, generatedFrom },
    minecraft: VERSION,
    appliesFrom: APPLIES_FROM,
    maskedFor: maskVersions(),
    designer,
    date: date.toISOString(),
    plugin,
    placeholders: PLACEHOLDER,
    mask: MASK,
    worlds,
    areas: areaList.map((a) => ({ name: a.name, dim: a.dim, box: a.box, file: a.file })),
    checkProblems: problems,
  };
}

/** A string from an untrusted file, safe to print: no control characters, at most `max` long. */
function clean(s, max = 120) {
  return String(s === undefined || s === null ? '' : s).replace(/[\u0000-\u001f\u007f-\u009f\u2028\u2029\u202a-\u202e\u2066-\u2069]/g, '?').slice(0, max);
}

/** A local yyyy-mm-dd for the export's name. */
function dateStamp(d = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

module.exports = {
  VERSION, APPLIES_FROM, PLACEHOLDER, PLACEHOLDERS, MASK, ACTIVE, FORBIDDEN, ALLOWED_ENTITIES, TRANSIENT_ENTITIES, ENTITY_KEYS, SKIN, MARGIN,
  areas, boardBoxes, protectedBoxes, maskVersions, protectedFor, skins, placeholderFills, fillCommand, grow, contains, inside, clip, wingAt,
  scrubJson, scrubTag, hasClick, sanitizeEntity, readPalette,
  Grid, readGrid, writeGrid, compareArea, exportArea, pasteProblems, formatReport, chatSummary, itemLine,
  placementsFor, manifestFor, clean, dateStamp,
};
