'use strict';
// WorldEdit schematics placed during generation (--schematics <folder>): set pieces built by
// hand in game and exported with //schem save, for the restyle. Each placement is
// { file, at: { x, y, z }, rotation (0, 90, 180 or 270, as //rotate), dim }; campus.SCHEMATICS
// lists the design's, and <folder>/placements.json may add more. Every placement's box is
// checked against the decoration guardrail (wings/decor/guard.js) before the server starts,
// and refused if it reaches into a cell, a footprint, a pad, a lane or anything else it guards.
// WorldEdit pastes them from the console: //world, //pos1 x,y,z, /schem load, //rotate,
// //paste. No WorldEdit API, no player.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const nbt = require('prismarine-nbt');
const campus = require('./campus');
const bp = require('./blueprint');

const ROTATIONS = [0, 90, 180, 270];
const WORLDS = { [campus.OVERWORLD]: 'world', [campus.NETHER]: 'world_nether', [campus.END]: 'world_the_end' };

/**
 * A Sponge schematic's size and where its minimum corner lies from its origin (the point a paste
 * puts at the placement): { version, size: [w, h, l], min: [x, y, z] }. Versions 2 and 3.
 */
async function readSchem(file) {
  const { parsed } = await nbt.parse(fs.readFileSync(file));
  let root = nbt.simplify(parsed);
  if (root.Schematic) root = root.Schematic; // version 3 nests it
  const version = root.Version;
  if (![1, 2, 3].includes(version) || root.Width === undefined) throw new Error(`${path.basename(file)} is not a Sponge schematic (version 1 to 3)`);
  const size = [root.Width, root.Height, root.Length].map((n) => n & 0xffff);
  let min = [0, 0, 0];
  if (version === 3) {
    // Version 3: Offset is the minimum corner relative to the origin.
    if (root.Offset) min = [...root.Offset];
  } else {
    // Versions 1 and 2: WorldEdit's origin is Offset less its WEOffset, so the corner is WEOffset.
    const m = root.Metadata || {};
    if (m.WEOffsetX !== undefined) min = [m.WEOffsetX, m.WEOffsetY, m.WEOffsetZ];
  }
  return { version, size, min };
}

/** A point turned by //rotate `deg` about the origin: clockwise seen from above. */
function turn([x, y, z], deg) {
  switch (deg) {
    case 90: return [-z, y, x];
    case 180: return [-x, y, -z];
    case 270: return [z, y, -x];
    default: return [x, y, z];
  }
}

/** The world box a schematic fills when pasted at `at` turned by `rotation`. */
function placedBox(info, at, rotation = 0) {
  const lo = info.min;
  const hi = info.min.map((v, i) => v + info.size[i] - 1);
  const a = turn(lo, rotation);
  const b = turn(hi, rotation);
  return bp.box3(at.x + a[0], at.y + a[1], at.z + a[2], at.x + b[0], at.y + b[1], at.z + b[2]);
}

/** The placements: campus.SCHEMATICS, then `<folder>/placements.json`'s; each checked for shape. */
function placements(folder) {
  const out = [...(campus.SCHEMATICS || [])];
  const extra = path.join(folder, 'placements.json');
  if (fs.existsSync(extra)) {
    const list = JSON.parse(fs.readFileSync(extra, 'utf8'));
    if (!Array.isArray(list)) throw new Error(`${extra} is not a list of placements`);
    out.push(...list);
  }
  return out.map((p, i) => {
    const what = `schematic placement ${i + 1} (${p && p.file})`;
    if (!p || typeof p.file !== 'string' || !/^[\w.-]+\.schem$/.test(p.file)) throw new Error(`${what}: file must be a plain name ending .schem`);
    if (!p.at || ![p.at.x, p.at.y, p.at.z].every(Number.isInteger)) throw new Error(`${what}: at needs whole x, y and z`);
    const rotation = p.rotation === undefined ? 0 : p.rotation;
    if (!ROTATIONS.includes(rotation)) throw new Error(`${what}: rotation is 0, 90, 180 or 270, not ${rotation}`);
    const dim = p.dim || campus.OVERWORLD;
    if (!WORLDS[dim]) throw new Error(`${what}: no dimension ${dim}`);
    const file = path.join(folder, p.file);
    if (!fs.existsSync(file)) throw new Error(`${what}: there is no ${file}`);
    return { file: p.file, path: file, at: p.at, rotation, dim };
  });
}

/**
 * Reads each placement's schematic and checks its box against the guardrail; returns
 * { placed: [{ ...placement, box }], problems: [sentences] }.
 */
async function check(list, version) {
  const guard = require('../wings/decor/guard');
  const { allBlueprints } = require('../wings');
  const bad = guard.forbidden(allBlueprints(version).builds);
  const placed = [];
  const problems = [];
  for (const p of list) {
    const box = placedBox(await readSchem(p.path), p.at, p.rotation);
    placed.push({ ...p, box });
    for (const k of bad) {
      if (k.dim === p.dim && bp.overlaps(box, k.box)) problems.push(`${p.file} at ${fmt(box)} reaches into ${k.what} (${fmt(k.box)})`);
    }
  }
  return { placed, problems: [...new Set(problems)] };
}

function fmt(b) {
  return `${b.x0} ${b.y0} ${b.z0}..${b.x1} ${b.y1} ${b.z1}`;
}

/** Copies the schematics into WorldEdit's folder on the server, under a wx_ prefix. */
function install(serverFolder, list) {
  const dir = path.join(serverFolder, 'plugins', 'WorldEdit', 'schematics');
  fs.mkdirSync(dir, { recursive: true });
  for (const f of fs.readdirSync(dir).filter((n) => n.startsWith('wx_'))) fs.rmSync(path.join(dir, f), { force: true });
  for (const p of list) fs.copyFileSync(p.path, path.join(dir, `wx_${p.file}`));
}

/**
 * Pastes each placement by WorldEdit's console commands; returns [{ file, ok, detail }]. A
 * command WorldEdit answers with anything but its own success line is a failure, with its words.
 * The console has no position, so //paste puts the clipboard's origin at pos #1 (WorldEdit
 * 7.4.5's //toggleplace refuses the console: "Cannot toggle placing in this context").
 */
async function paste(srv, placed) {
  const out = [];
  const say = async (cmd, ok) => {
    const r = await srv.run(cmd, 60000);
    const words = r.lines.join(' ');
    if (!ok.test(words)) throw new Error(`${cmd}: ${words || 'no answer'}`);
    return words;
  };
  // Paper 1.21.11's console drops one leading slash and 1.20.4's does not, so WorldEdit's //world
  // is typed //world on one and /world on the other: whichever it knows is used from then on.
  let we = null;
  const world = async (w) => {
    for (const prefix of we ? [we] : ['//', '/']) {
      const r = await srv.run(`${prefix}world ${w}`, 60000);
      const words = r.lines.join(' ');
      if (/world override/i.test(words)) { we = prefix; return; }
      if (!/Unknown command/i.test(words)) throw new Error(`${prefix}world ${w}: ${words || 'no answer'}`);
    }
    throw new Error(`WorldEdit's world command is not there (${we || '// or /'}world ${w}: unknown)`);
  };
  try {
    for (const p of placed) {
      try {
        const name = `wx_${p.file}`;
        await world(WORLDS[p.dim]);
        await say(`${we}pos1 ${p.at.x},${p.at.y},${p.at.z}`, /First position set/i);
        // /schem load reads the file off the main thread and says so later: wait for that line.
        const esc = name.replace(/[.]/g, '\\.');
        const loaded = srv.waitFor(new RegExp(`${esc} loaded\\. Paste it|${esc}.*(could not|not supported|unknown)|does not exist`, 'i'), 120000, `WorldEdit to load ${name}`);
        loaded.catch(() => {});
        const r = await srv.run(`/schem load ${name}`, 60000);
        const now = r.lines.join(' ');
        // Refused at once (a bad name), or answered later.
        if (now.trim() && !/loading|loaded\. Paste it/i.test(now)) throw new Error(`/schem load ${name}: ${now}`);
        const line = /loaded\. Paste it/i.test(now) ? now : await loaded;
        if (!/loaded\. Paste it/i.test(line)) throw new Error(`/schem load ${name}: ${line}`);
        if (p.rotation) await say(`${we}rotate ${p.rotation}`, /rotated/i);
        const words = await say(`${we}paste`, /pasted/i);
        out.push({ file: p.file, ok: true, detail: `${fmt(p.box)} (${words})` });
      } catch (e) {
        out.push({ file: p.file, ok: false, detail: e.message });
      }
    }
  } finally {
    if (we) await srv.run(`${we}world`, 15000).catch(() => {}); // the console's world override, back to none
  }
  return out;
}

/**
 * Writes a Sponge version 2 schematic: `blocks` is [{ x, y, z, block }] inside `size`, the
 * rest air, origin at the minimum corner. For tests and hand-made fixtures.
 */
function writeSchem(file, { size, blocks, dataVersion }) {
  const [w, h, l] = size;
  const palette = { 'minecraft:air': 0 };
  const ids = new Array(w * h * l).fill(0);
  for (const b of blocks) {
    if (palette[b.block] === undefined) palette[b.block] = Object.keys(palette).length;
    ids[(b.y * l + b.z) * w + b.x] = palette[b.block];
  }
  const varints = [];
  for (let v of ids) {
    while (v > 0x7f) { varints.push((v & 0x7f) | 0x80); v >>>= 7; }
    varints.push(v);
  }
  const int = (value) => ({ type: 'int', value });
  const short = (value) => ({ type: 'short', value });
  const root = {
    type: 'compound',
    name: 'Schematic',
    value: {
      Version: int(2),
      DataVersion: int(dataVersion),
      Width: short(w),
      Height: short(h),
      Length: short(l),
      Offset: { type: 'intArray', value: [0, 0, 0] },
      Metadata: { type: 'compound', value: { WEOffsetX: int(0), WEOffsetY: int(0), WEOffsetZ: int(0) } },
      PaletteMax: int(Object.keys(palette).length),
      Palette: { type: 'compound', value: Object.fromEntries(Object.entries(palette).map(([k, v]) => [k, int(v)])) },
      BlockData: { type: 'byteArray', value: varints.map((b) => (b > 127 ? b - 256 : b)) },
    },
  };
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, zlib.gzipSync(nbt.writeUncompressed(root)));
}

module.exports = { readSchem, placedBox, placements, check, install, paste, writeSchem, turn, WORLDS };
