'use strict';
// lib/reel.js without a browser: the delays a reel's frames get, which frames a kawoosh keeps, and
// a GIF written from real PNGs. Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { PNG } = require('pngjs');
const reel = require('../lib/reel');

test('a frame is held for the gap to the next, slowed server time put back, in hundredths of a second', () => {
  // Four frames 0.2 s apart on the browser's clock, a server four times slow: 50 ms apiece.
  const t = reel.retime([10, 10.2, 10.4, 10.6], { slow: 4, minGap: 40, hold: 1000 });
  assert.deepStrictEqual(t, [{ index: 0, delay: 50 }, { index: 1, delay: 50 }, { index: 2, delay: 50 }, { index: 3, delay: 1000 }]);
});

test('a frame that follows too soon is dropped and its time goes to the one before, so the length is kept', () => {
  const t = reel.retime([0, 0.01, 0.1, 0.11, 0.3], { slow: 1, minGap: 40, hold: 500 });
  assert.deepStrictEqual(t.map((k) => k.index), [0, 2, 4]);
  assert.strictEqual(t[0].delay + t[1].delay, 300);
});

test('a delay is never under 20 ms, and a reel of nothing has none', () => {
  assert.deepStrictEqual(reel.retime([5], { hold: 0 }), [{ index: 0, delay: 20 }]);
  assert.deepStrictEqual(reel.retime([]), []);
});

test('a cut starts at the frame before the first received at its time, made to begin at the cut', () => {
  // Painted at 10 s, 30 s (a page that did not change for twenty), 30.2 s and 30.4 s; received as they were.
  const f = [{ t: 10, recv: 1000 }, { t: 30, recv: 21000 }, { t: 30.2, recv: 21200 }, { t: 30.4, recv: 21400 }];
  const cut = reel.from(f, 21100);
  assert.deepStrictEqual(cut.map((x) => x.recv), [21000, 21200, 21400]);
  assert.ok(Math.abs(cut[0].t - 30.1) < 1e-9, `the lead frame begins at ${cut[0].t}, 0.1 s before the next`);
  assert.deepStrictEqual(reel.from(f, 50), f);
  assert.deepStrictEqual(reel.from(f, 9e9), [f[3]]);
});

test('a cut never moves the lead frame earlier than it was painted', () => {
  const f = [{ t: 5, recv: 100 }, { t: 5.1, recv: 200 }];
  assert.strictEqual(reel.from(f, 50)[0], f[0]);
  assert.ok(Math.abs(reel.from(f, 150)[0].t - 5.05) < 1e-9);
  // A cut so far before the next frame that its time would be before the lead's own: the lead keeps its own.
  const slow = [{ t: 5, recv: 100 }, { t: 5.1, recv: 5000 }];
  assert.strictEqual(reel.from(slow, 150)[0].t, 5);
});

test('a frame stores only the pixels that moved: the rest are the transparent index', () => {
  const before = Uint8Array.from([1, 2, 3, 4]);
  assert.deepStrictEqual([...reel.changed(Uint8Array.from([1, 9, 3, 8]), before, 200)], [200, 9, 200, 8]);
  const first = Uint8Array.from([1, 2]);
  assert.strictEqual(reel.changed(first, null, 200), first);
});

/** A w x h PNG of one colour, with a square of another at `box` ({x, y, size}). */
function png(w, h, [r, g, b], box = null, [br, bg, bb] = [0, 0, 0]) {
  const img = new PNG({ width: w, height: h });
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const inBox = box && x >= box.x && x < box.x + box.size && y >= box.y && y < box.y + box.size;
      img.data.set(inBox ? [br, bg, bb, 255] : [r, g, b, 255], (y * w + x) * 4);
    }
  }
  return PNG.sync.write(img);
}

test('frames are written as a looping GIF, one image per kept frame, at the delays retime gave', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-reel-'));
  try {
    const frames = [0, 1, 2].map((i) => ({ t: i * 0.1, recv: i, png: png(32, 24, [200, 200, 200], { x: i * 8, y: 4, size: 6 }, [20, 40, 160]) }));
    const out = await reel.writeGif(path.join(dir, 'a', 'x.gif'), frames, { slow: 1, minGap: 40, hold: 500 });
    assert.strictEqual(out.frames, 3);
    assert.ok(Math.abs(out.seconds - 0.7) < 0.011, `${out.seconds} s`);
    const gif = fs.readFileSync(out.file);
    assert.strictEqual(gif.subarray(0, 6).toString('latin1'), 'GIF89a');
    assert.ok(gif.includes(Buffer.from('NETSCAPE2.0')), 'loops');
    assert.strictEqual(reel.decode(frames[0].png).width, 32);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

/** A frame of `n` pixels, all `rgb`, with `dark` of them near black. */
function pixels(n, rgb, dark = 0) {
  const d = new Uint8Array(n * 4);
  for (let i = 0; i < n; i++) d.set(i < dark ? [4, 4, 6, 255] : [...rgb, 255], i * 4);
  return { data: d };
}

test('no pixel is ever given the transparent index, near black ones included, whatever the frames sampled', async () => {
  const gif = (await import('gifenc')).default;
  // Near black only in the odd frames, which a one-in-two sample leaves out.
  const frames = Array.from({ length: 16 }, (_, i) => pixels(400, [200, 180, 90], i % 2 ? 40 : 0));
  const pal = reel.paletteOf(16, (i) => frames[i], gif);
  assert.ok(pal.colours.length <= 255);
  assert.strictEqual(pal.transparent, pal.colours.length);
  assert.strictEqual(pal.table.length, pal.colours.length + 1);
  for (const f of frames) {
    const index = gif.applyPalette(f.data, pal.colours);
    assert.ok(index.every((v) => v < pal.transparent), 'a pixel took the transparent index');
  }
});

test('the transparent index is inside the colour table even for a flat picture of a few colours', async () => {
  const gif = (await import('gifenc')).default;
  const frames = [pixels(64, [255, 255, 255]), pixels(64, [255, 255, 255], 8)];
  const pal = reel.paletteOf(2, (i) => frames[i], gif);
  assert.ok(pal.transparent < pal.table.length);
  assert.ok(pal.colours.length < 255, 'the test wants a palette short of full');
});

test('pixels are handed to gifenc in a buffer of their own, however the decoder allocated them', () => {
  const pool = new Uint8Array(32).fill(7);
  const slice = pool.subarray(8, 24);
  const own = reel.own(slice);
  assert.strictEqual(own.byteOffset, 0);
  assert.strictEqual(own.buffer.byteLength, own.length);
  assert.deepStrictEqual([...own], new Array(16).fill(7));
});

test('a frame of another size is left out and its time goes to its neighbour', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-reel-'));
  try {
    const odd = { t: 0.1, recv: 1, png: png(16, 12, [10, 10, 10]) };
    const frames = [
      { t: 0, recv: 0, png: png(32, 24, [200, 200, 200]) }, odd,
      { t: 0.2, recv: 2, png: png(32, 24, [200, 200, 200], { x: 4, y: 4, size: 6 }, [20, 40, 160]) },
    ];
    const out = await reel.writeGif(path.join(dir, 'x.gif'), frames, { slow: 1, minGap: 40, hold: 500 });
    assert.strictEqual(out.frames, 2);
    assert.ok(Math.abs(out.seconds - 0.7) < 0.011, `${out.seconds} s: the odd frame's time is not lost`);
    assert.strictEqual(fs.readFileSync(out.file).readUInt16LE(6), 32);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('a reel with no frames is refused rather than written empty', async () => {
  await assert.rejects(reel.writeGif(path.join(os.tmpdir(), 'wx-none.gif'), []), /no frames/);
});
