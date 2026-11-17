'use strict';
// lib/farscenery.js without a server: the projection that paints the far scenery spike's wall
// and panorama (#526). Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('../lib/farscenery');
const text = require('../lib/text');

test('a ray aimed at a column of known height and colour lands on that colour', () => {
  // The tower at 130 stands 30 high: aimed at its middle from the plane at 120, its body is hit.
  const t = fs.TOWERS[1];
  const s120 = 120 - fs.EYE.d;
  const sMid = t.d + 0.5 - fs.EYE.d;
  assert.strictEqual(fs.project(fs.EYE.x + (t.x - fs.EYE.x) * s120 / sMid, fs.EYE.y + (t.h / 2 - fs.EYE.y) * s120 / sMid, 120),
    fs.COLOURS['minecraft:stone_bricks']);
  // Aimed at its red top from the wall plane at 96, the top's colour comes back.
  const s96 = 96 - fs.EYE.d;
  const sAt = t.d + 0.5 - fs.EYE.d;
  const yTop = fs.EYE.y + (t.h - 0.5 - fs.EYE.y) * s96 / sAt;
  const xTop = fs.EYE.x + (t.x - fs.EYE.x) * s96 / sAt;
  assert.strictEqual(fs.project(xTop, yTop, 96), fs.COLOURS['minecraft:red_concrete']);
});

test('a ray that passes over everything is sky, paler at the horizon than overhead', () => {
  const up = fs.project(0, 400, 10);
  assert.strictEqual(up, fs.skyColour(Math.atan2((400 - fs.EYE.y) / (10 - fs.EYE.d), 1)));
  assert.notStrictEqual(fs.skyColour(0), fs.skyColour(1));
  assert.strictEqual(fs.skyColour(0), fs.SKY.horizon);
});

test('the wall cell in line with the tower at 130 is painted with the nearest palette block to the tower', () => {
  const w = fs.wall(96);
  const t = fs.TOWERS[1];
  const s96 = 96 - fs.EYE.d;
  const sAt = t.d + 0.5 - fs.EYE.d;
  const y = Math.floor(fs.EYE.y + (t.h / 2 - fs.EYE.y) * s96 / sAt);
  const x = Math.round(fs.EYE.x + (t.x - fs.EYE.x) * s96 / sAt);
  assert.strictEqual(w.block(x, y), fs.nearest(fs.COLOURS['minecraft:stone_bricks']));
  assert.ok(fs.PALETTE[w.block(x, y)], 'a palette block');
});

test('the wall is opaque over the whole frustum from the seat, with margin', () => {
  for (const at of [64, 96]) {
    const w = fs.wall(at);
    const f = fs.frustum(at);
    assert.ok(w.x0 <= -f.half && w.x1 >= f.half && w.y1 >= f.top, `${at}: ${w.x0}..${w.x1} up to ${w.y1}, frustum ±${f.half} up to ${f.top}`);
    assert.ok(w.cells.every((row) => row.every((b) => fs.PALETTE[b])), `${at}: a cell is not a palette block`);
    const filled = fs.wallFills(w).reduce((n, r) => n + (r.x1 - r.x0 + 1) * (r.y1 - r.y0 + 1), 0);
    assert.strictEqual(filled, (w.x1 - w.x0 + 1) * (w.y1 + 1), `${at}: the fills cover the wall exactly`);
  }
});

test('the panorama is exactly cols x rows glyphs in rows of cols, and fits an NBT string on 1.20.4', () => {
  const p = fs.panorama(120);
  const lines = p.spec.map((s) => (typeof s === 'string' ? s : s.text)).join('').split('\n');
  assert.strictEqual(lines.length, fs.PANORAMA.rows);
  assert.ok(lines.every((l) => [...l].length === fs.PANORAMA.cols));
  assert.ok(p.spec.filter((s) => typeof s !== 'string').every((s) => /^#[0-9a-f]{6}$/.test(s.color)));
  assert.ok(Buffer.byteLength(text.displayNbt('1.20.4', p.spec)) < 65535, 'an NBT string holds 65535 bytes at most');
  // The scale makes the glyph grid as wide as the picture: cols glyphs at their advance.
  assert.ok(Math.abs(p.scale.x * fs.GLYPH.width / fs.GLYPH.perBlock * p.cols - p.width) < 1e-9);
});

test('rectangles merge a grid into fills that cover each block once', () => {
  const grid = ['aab', 'aab', 'cc.'];
  const rects = fs.rectangles(0, 2, 0, 2, (x, y) => (grid[y][x] === '.' ? null : grid[y][x]));
  const seen = new Map();
  for (const r of rects) for (let y = r.b0; y <= r.b1; y++) for (let x = r.a0; x <= r.a1; x++) {
    assert.ok(!seen.has(`${x},${y}`));
    seen.set(`${x},${y}`, r.block);
  }
  assert.strictEqual(seen.size, 8);
  assert.strictEqual(rects.length, 3);
});

test('the near land built by the fills is block for block the landscape, and each fill is within the limit', () => {
  const until = 60;
  const got = new Map();
  for (const f of fs.terrainFills(until)) {
    assert.ok((f.x1 - f.x0 + 1) * (f.y1 - f.y0 + 1) * (f.d1 - f.d0 + 1) <= 32768);
    for (let x = f.x0; x <= f.x1; x++) for (let y = f.y0; y <= f.y1; y++) for (let d = f.d0; d <= f.d1; d++) got.set(`${x},${y},${d}`, f.block);
  }
  let wrong = 0;
  for (let d = 0; d < until; d++) {
    for (let x = -fs.NEAR.half; x <= fs.NEAR.half; x++) {
      for (let y = -1; y <= fs.NEAR.maxY; y++) if ((got.get(`${x},${y},${d}`) || null) !== fs.blockAt(x, d, y)) wrong++;
    }
  }
  assert.strictEqual(wrong, 0);
});

test('the far scenery is off unless WX_FAR_SCENERY says on', () => {
  assert.strictEqual(fs.enabled({}), false);
  assert.strictEqual(fs.enabled({ WX_FAR_SCENERY: '0' }), false);
  assert.strictEqual(fs.enabled({ WX_FAR_SCENERY: '1' }), true);
});
