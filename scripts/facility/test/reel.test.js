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

test('a kawoosh starts one frame before the first received at its time, or at the last frame if all came earlier', () => {
  const f = [{ recv: 100 }, { recv: 200 }, { recv: 300 }, { recv: 400 }];
  assert.deepStrictEqual(reel.from(f, 250), [f[1], f[2], f[3]]);
  assert.deepStrictEqual(reel.from(f, 50), f);
  assert.deepStrictEqual(reel.from(f, 900), [f[3]]);
});

test('a frame stores only the pixels that moved: the rest are the transparent index', () => {
  const before = Uint8Array.from([1, 2, 3, 4]);
  assert.deepStrictEqual([...reel.changed(Uint8Array.from([1, 9, 3, 8]), before)], [reel.TRANSPARENT, 9, reel.TRANSPARENT, 8]);
  const first = Uint8Array.from([1, 2]);
  assert.strictEqual(reel.changed(first, null), first);
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

test('a reel with no frames is refused rather than written empty', async () => {
  await assert.rejects(reel.writeGif(path.join(os.tmpdir(), 'wx-none.gif'), []), /no frames/);
});
