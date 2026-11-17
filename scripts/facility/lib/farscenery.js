'use strict';
// Far scenery (issue #526, a research spike): a deterministic procedural landscape seen through a
// gate-sized opening, and the two stand-ins for its far part, a TextDisplay panorama and a wall of
// coloured blocks, both drawn by projecting the landscape from where a viewer stands. Off unless
// WX_FAR_SCENERY is set (chambers/far-scenery.js); no server is needed for anything here.
//
// Local frame: x across (block index, 0 is the opening's middle column, +x is world east, which
// is the viewer's left when facing south), y is world y (feet on 0, the flat world's top at -1),
// d along the view (world z minus SITE.z; block row k spans d k..k+1). The opening is blocks
// x -1..1, y 0..4 in the wall row d -1, so its far face is d 0.

const SITE = { x: -400, z: 0 };
const EYE = { x: 0, y: 1.62, d: -2.5 };
const HOLE = { half: 1.5, y0: 0, y1: 5 };
const NEAR = { half: 112, depth: 160, maxY: 40 }; // what may be built: x -112..112, d 0..160, y up to 40
const FAR = 400; // how far a projection looks
const MARGIN = { across: 1.3, up: 1.1 }; // a picture's size past the frustum from the seat
const PANORAMA = { cols: 128, rows: 72 };
// Text display metrics: 1/40 block per pixel at scale 1; the glyph advance and line height of
// '█' are assumptions, not read from the client's font.
const GLYPH = { char: '█', width: 9, line: 10, perBlock: 40 };

// Average colours, approximate (map colours and texture averages, by eye).
const COLOURS = {
  'minecraft:grass_block': '#6a9f3c', 'minecraft:oak_leaves': '#3f6e22', 'minecraft:oak_log': '#6b5430',
  'minecraft:water': '#3f6fd0', 'minecraft:sand': '#dbd3a0', 'minecraft:stone': '#7d7d7d',
  'minecraft:snow_block': '#f9fefe', 'minecraft:stone_bricks': '#7a7979', 'minecraft:red_concrete': '#8e2121',
  'minecraft:quartz_block': '#ece6df', 'minecraft:coarse_dirt': '#77553b',
};

// The wall's palette: opaque, full blocks a 1.20.4 server knows.
const PALETTE = {
  'minecraft:white_concrete': '#cfd5d6', 'minecraft:light_gray_concrete': '#7d7d73', 'minecraft:gray_concrete': '#36393d',
  'minecraft:black_concrete': '#080a0f', 'minecraft:brown_concrete': '#603c20', 'minecraft:red_concrete': '#8e2121',
  'minecraft:orange_concrete': '#e06101', 'minecraft:yellow_concrete': '#f1af15', 'minecraft:lime_concrete': '#5ea918',
  'minecraft:green_concrete': '#495b24', 'minecraft:cyan_concrete': '#157788', 'minecraft:light_blue_concrete': '#2389c7',
  'minecraft:blue_concrete': '#2d2f8f', 'minecraft:white_terracotta': '#d1b2a1', 'minecraft:light_gray_terracotta': '#876a61',
  'minecraft:gray_terracotta': '#392a23', 'minecraft:green_terracotta': '#4c532a', 'minecraft:lime_terracotta': '#677535',
  'minecraft:cyan_terracotta': '#565b5b', 'minecraft:light_blue_terracotta': '#716c8a', 'minecraft:brown_terracotta': '#4d3323',
  'minecraft:terracotta': '#985e43', 'minecraft:white_wool': '#eaecec', 'minecraft:light_gray_wool': '#8e8e86',
  'minecraft:light_blue_wool': '#3aafd9', 'minecraft:lime_wool': '#70b919', 'minecraft:green_wool': '#546d1b',
  'minecraft:blue_wool': '#35399d', 'minecraft:snow_block': '#f9fefe', 'minecraft:stone': '#7d7d7d',
  'minecraft:blue_ice': '#74a8fd', 'minecraft:packed_ice': '#8db4fe', 'minecraft:calcite': '#dfe0dc',
  'minecraft:clay': '#a0a6b3', 'minecraft:moss_block': '#596d2d',
};

const SKY = { horizon: '#c3d8f5', zenith: '#78a7ff' };

// Three towers: two in the near strip (one in front of every far plane, one behind them), one far.
const TOWERS = [
  { x: -45, d: 50, h: 18 },
  { x: 12, d: 130, h: 30 },
  { x: -30, d: 300, h: 60 },
];

const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const hex = (c) => `#${c.map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('')}`;
const mix = (a, b, t) => hex(rgb(a).map((v, i) => v + (rgb(b)[i] - v) * t));

/** A deterministic value in 0..1 for an integer pair. */
function hash(a, b) {
  let h = (Math.imul(a | 0, 374761393) + Math.imul(b | 0, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

/** Where the sea starts across (east of this is water), for a row d. */
function coastX(d) {
  return 34 + 16 * Math.sin(d / 23) + 6 * Math.sin(d / 7);
}

function towerAt(x, d) {
  return TOWERS.find((t) => Math.abs(x - t.x) <= 1 && Math.abs(d - t.d) <= 1) || null;
}

/** Land height on 4x4 tiles near (so a build merges), smooth ridges far; -1 is bare ground. */
function landHeight(x, d) {
  if (d >= 170) {
    const ridge = (d - 170) * 0.45 + 34 * Math.abs(Math.sin(x / 41 + d / 97)) + 12 * Math.sin(x / 17) + 8 * Math.cos(d / 13);
    return Math.max(-1, Math.min(110, Math.floor(ridge)));
  }
  const tx = Math.floor(x / 4);
  const td = Math.floor(d / 4);
  const rolling = Math.floor(2.4 * (Math.sin(tx / 3.1) + Math.cos(td / 2.3)) - 1.5);
  const foot = d >= 112 ? Math.floor((td * 4 - 112) / 4) : -1;
  return Math.max(-1, Math.min(14, Math.max(rolling, foot)));
}

function treeAt(x, d) {
  const tx = Math.floor(x / 4);
  const td = Math.floor(d / 4);
  const x0 = tx * 4 + 1;
  const d0 = td * 4 + 1;
  if (x0 < -90 || x0 > -12 || d0 < 16 || d0 > 96 || hash(tx, td) < 0.6) return null;
  if (TOWERS.some((t) => Math.abs(t.x - x0) <= 2 && Math.abs(t.d - d0) <= 2)) return null;
  return { x: x0, d: d0, base: landHeight(x0, d0) + 1 };
}

/** The block at (x, d, y) for y >= -1, or null for air. */
function blockAt(x, d, y) {
  if (d < 0) return y < 0 ? 'minecraft:stone' : null;
  const sea = x > coastX(d);
  const beach = !sea && x > coastX(d) - 3;
  const t = towerAt(x, d);
  if (t && !sea) {
    if (y < 0) return 'minecraft:stone_bricks';
    if (y < t.h - 2) return 'minecraft:stone_bricks';
    if (y < t.h) return 'minecraft:red_concrete';
    return null;
  }
  if (sea) return y < 0 ? 'minecraft:water' : null;
  const h = beach ? -1 : landHeight(x, d);
  if (y <= h || y < 0) {
    if (beach) return 'minecraft:sand';
    if (d >= 170) return y >= 70 ? 'minecraft:snow_block' : (y >= 26 ? 'minecraft:stone' : 'minecraft:grass_block');
    return h >= 8 ? 'minecraft:coarse_dirt' : 'minecraft:grass_block';
  }
  const tree = treeAt(x, d);
  if (tree) {
    const dy = y - tree.base;
    if (x === tree.x && d === tree.d && dy >= 0 && dy < 4) return 'minecraft:oak_log';
    if (Math.abs(x - tree.x) <= 1 && Math.abs(d - tree.d) <= 1 && dy >= 3 && dy <= 4) return 'minecraft:oak_leaves';
    if (x === tree.x && d === tree.d && dy === 5) return 'minecraft:oak_leaves';
  }
  return null;
}

/** The top y of the land or a tower in the column (x, d), trees left out; -1 for bare ground. */
function groundHeight(x, d) {
  if (d < 0) return -1;
  const t = towerAt(x, d);
  if (t && x <= coastX(d)) return t.h - 1;
  return x > coastX(d) - 3 ? -1 : landHeight(x, d);
}

/** The top y of anything in the column (x, d), -1 for bare ground. */
function heightAt(x, d) {
  const h = groundHeight(x, d);
  if (d < 0) return h;
  const tree = treeAt(x, d);
  if (tree && Math.abs(x - tree.x) <= 1 && Math.abs(d - tree.d) <= 1) return Math.max(h, tree.base + (x === tree.x && d === tree.d ? 5 : 4));
  return h;
}

function skyColour(elevation) {
  return mix(SKY.horizon, SKY.zenith, Math.max(0, Math.min(1, elevation / (Math.PI / 3))));
}

/**
 * The colour seen from the eye through (px, py, pd), looking only at columns from row `from` on:
 * the first column the ray passes under the top of (the nearest wins), else the sky.
 */
function project(px, py, pd, from = Math.floor(pd)) {
  const len = pd - EYE.d;
  const sx = (px - EYE.x) / len;
  const sy = (py - EYE.y) / len;
  for (let d = Math.max(from, 0); d < FAR; d += 0.25) {
    const s = d - EYE.d;
    const x = Math.round(EYE.x + sx * s);
    const y = EYE.y + sy * s;
    const row = Math.floor(d);
    if (y < heightAt(x, row) + 1) {
      const b = blockAt(x, row, Math.max(-1, Math.min(heightAt(x, row), Math.floor(y))));
      return COLOURS[b] || COLOURS['minecraft:grass_block'];
    }
  }
  return skyColour(Math.atan2(sy, 1));
}

/** The frustum through the opening seen from the seat, at plane d: { half, top } (top is a y). */
function frustum(d) {
  const s = d - EYE.d;
  const near = 0 - EYE.d;
  return { half: HOLE.half * s / near, top: EYE.y + (HOLE.y1 - EYE.y) * s / near };
}

function nearest(colour) {
  const c = rgb(colour);
  let best = null;
  let bestD = Infinity;
  for (const [name, h] of Object.entries(PALETTE)) {
    const p = rgb(h);
    // Weighted RGB distance: green counts most, as the eye does.
    const dd = 2 * (c[0] - p[0]) ** 2 + 4 * (c[1] - p[1]) ** 2 + 3 * (c[2] - p[2]) ** 2;
    if (dd < bestD) { bestD = dd; best = name; }
  }
  return best;
}

/** The wall at row `at`: its extent and every block, [y][x] top row first, x from x0. */
function wall(at) {
  const f = frustum(at);
  const half = Math.ceil(f.half * MARGIN.across);
  const top = Math.ceil(f.top * MARGIN.up);
  const x0 = -half;
  const cells = [];
  for (let y = top; y >= 0; y--) {
    const row = [];
    for (let x = x0; x <= half; x++) row.push(nearest(project(x, y + 0.5, at, at + 1)));
    cells.push(row);
  }
  return { at, x0, x1: half, y0: 0, y1: top, cells, block: (x, y) => cells[top - y][x - x0] };
}

/**
 * The panorama at plane d `at`: rows x cols colours, the picture's size in blocks, and the text
 * display spec (one component per run of a colour, rows joined by newlines) with its scale.
 */
function panorama(at, { cols = PANORAMA.cols, rows = PANORAMA.rows } = {}) {
  const f = frustum(at);
  const width = 2 * f.half * MARGIN.across;
  const height = f.top * MARGIN.up;
  const cw = width / cols;
  const ch = height / rows;
  const grid = [];
  for (let r = 0; r < rows; r++) {
    const row = [];
    // The first glyph of a line is on the viewer's left, which is +x facing south.
    for (let c = 0; c < cols; c++) row.push(project(width / 2 - (c + 0.5) * cw, height - (r + 0.5) * ch, at, at));
    grid.push(row);
  }
  const spec = [];
  grid.forEach((row, r) => {
    if (r > 0) spec.push('\n');
    let run = null;
    for (const colour of row) {
      if (run && run.color === colour) run.text += GLYPH.char;
      else { run = { text: GLYPH.char, color: colour }; spec.push(run); }
    }
  });
  const mean = [0, 0, 0];
  for (const row of grid) for (const c of row) rgb(c).forEach((v, i) => { mean[i] += v / (rows * cols); });
  return {
    at, cols, rows, width, height, grid, spec, mean: hex(mean),
    scale: { x: cw / (GLYPH.width / GLYPH.perBlock), y: ch / (GLYPH.line / GLYPH.perBlock) },
    lineWidth: cols * GLYPH.width + 64,
  };
}

/** An ARGB int (signed, as NBT holds it) for an opaque colour. */
function argb(colour) {
  const [r, g, b] = rgb(colour);
  return ((0xff << 24) | (r << 16) | (g << 8) | b) | 0;
}

/**
 * Merges a grid into filled rectangles: runs along x in each row, then a run grows across the
 * next rows while they hold the same run. `cell(a, b)` is the block or null; returns
 * [{ a0, a1, b0, b1, block }].
 */
function rectangles(a0, a1, b0, b1, cell) {
  const open = new Map();
  const out = [];
  for (let b = b0; b <= b1 + 1; b++) {
    const runs = [];
    if (b <= b1) {
      let cur = null;
      for (let a = a0; a <= a1; a++) {
        const k = cell(a, b);
        if (cur && k === cur.block) cur.a1 = a;
        else { if (cur && cur.block) runs.push(cur); cur = { a0: a, a1: a, block: k }; }
      }
      if (cur && cur.block) runs.push(cur);
    }
    const next = new Map();
    for (const r of runs) {
      const key = `${r.a0},${r.a1},${r.block}`;
      const o = open.get(key);
      if (o) { o.b1 = b; next.set(key, o); open.delete(key); } else next.set(key, { ...r, b0: b, b1: b });
    }
    for (const o of open.values()) out.push(o);
    open.clear();
    for (const [k, v] of next) open.set(k, v);
  }
  return out;
}

/**
 * The near landscape's blocks for rows d 0..until-1, as fills in local coordinates, under the
 * 32768 limit: the ground, each column of one block as one fill from 0 up, the towers' columns
 * level by level, then three fills a tree.
 */
function terrainFills(until) {
  const out = [];
  const last = Math.min(until, NEAR.depth + 1) - 1;
  const w = 2 * NEAR.half + 1;
  const kinds = new Array(w * (last + 1));
  for (let d = 0; d <= last; d++) {
    for (let x = -NEAR.half; x <= NEAR.half; x++) {
      const h = groundHeight(x, d);
      let k = null;
      if (h >= 0) {
        const b = blockAt(x, d, 0);
        k = { block: b, h };
        for (let y = 1; y <= h && k; y++) if (blockAt(x, d, y) !== b) k = false;
      }
      kinds[d * w + x + NEAR.half] = k;
    }
  }
  const kind = (x, d) => kinds[d * w + x + NEAR.half];
  const push = (r, y0, y1, block) => out.push(...splitFill({ x0: r.a0, x1: r.a1, y0, y1, d0: r.b0, d1: r.b1, block }));
  for (const r of rectangles(-NEAR.half, NEAR.half, 0, last, (x, d) => blockAt(x, d, -1))) push(r, -1, -1, r.block);
  for (const r of rectangles(-NEAR.half, NEAR.half, 0, last, (x, d) => (kind(x, d) ? `${kind(x, d).block} ${kind(x, d).h}` : null))) {
    const [block, h] = r.block.split(' ');
    push(r, 0, Number(h), block);
  }
  for (let y = 0; y <= NEAR.maxY; y++) {
    for (const r of rectangles(-NEAR.half, NEAR.half, 0, last, (x, d) => (kind(x, d) === false && y <= groundHeight(x, d) ? blockAt(x, d, y) : null))) push(r, y, y, r.block);
  }
  const seen = new Set();
  for (let d = 0; d <= last; d++) {
    for (let x = -NEAR.half; x <= NEAR.half; x++) {
      const t = treeAt(x, d);
      if (!t || seen.has(`${t.x},${t.d}`) || t.d + 1 > last || Math.abs(t.x) + 1 > NEAR.half) continue;
      seen.add(`${t.x},${t.d}`);
      const at = (x0, x1, y0, y1, d0, d1, block) => out.push({ x0, x1, y0, y1, d0, d1, block });
      at(t.x - 1, t.x + 1, t.base + 3, t.base + 4, t.d - 1, t.d + 1, 'minecraft:oak_leaves');
      at(t.x, t.x, t.base + 5, t.base + 5, t.d, t.d, 'minecraft:oak_leaves');
      at(t.x, t.x, t.base, t.base + 3, t.d, t.d, 'minecraft:oak_log');
    }
  }
  return out;
}

/** A wall's fills, in local coordinates. */
function wallFills(w) {
  return rectangles(w.x0, w.x1, w.y0, w.y1, (x, y) => w.block(x, y))
    .flatMap((r) => splitFill({ x0: r.a0, x1: r.a1, y0: r.b0, y1: r.b1, d0: w.at, d1: w.at, block: r.block }));
}

function splitFill(f) {
  const vol = (f.x1 - f.x0 + 1) * (f.y1 - f.y0 + 1) * (f.d1 - f.d0 + 1);
  if (vol <= 32768) return [f];
  const mid = Math.floor((f.d0 + f.d1) / 2);
  if (f.d1 > f.d0) return [...splitFill({ ...f, d1: mid }), ...splitFill({ ...f, d0: mid + 1 })];
  const my = Math.floor((f.y0 + f.y1) / 2);
  return [...splitFill({ ...f, y1: my }), ...splitFill({ ...f, y0: my + 1 })];
}

/** Local to world. */
function world(x, y, d) {
  return { x: SITE.x + x, y, z: SITE.z + d };
}

function enabled(env = process.env) {
  return ['1', 'true', 'yes', 'on'].includes(String(env.WX_FAR_SCENERY || '').toLowerCase());
}

module.exports = {
  SITE, EYE, HOLE, NEAR, FAR, MARGIN, PANORAMA, GLYPH, COLOURS, PALETTE, SKY, TOWERS,
  blockAt, heightAt, project, frustum, nearest, wall, panorama, argb, rectangles, terrainFills, wallFills, world, enabled, skyColour,
};
