'use strict';
// Reels: short animations of the viewer's first-person page (gallery.js). Chrome's screencast
// hands over a frame whenever the page repaints, with the time it was painted; the frames are
// kept, retimed to what the server's clock was (a slowed server paints slow, a reel plays at the
// speed the plugin meant), and written as an animated GIF. Nothing here needs ffmpeg, so a clone
// with Chrome or Edge and `npm ci` can make one.

const fs = require('fs');
const path = require('path');

/**
 * Starts a screencast of `page`: frames as they are painted, { t: seconds on the browser's clock,
 * recv: ms on this process's, png: Buffer }.
 * `stop()` ends it and returns them in order. Chrome sends no frame for a page that does not
 * change, so a quiet stretch is one long frame, which is what the delays are for.
 */
async function startReel(page, { width = 960, height = 540 } = {}) {
  const client = await page.createCDPSession();
  const frames = [];
  client.on('Page.screencastFrame', (e) => {
    frames.push({ t: e.metadata.timestamp, recv: Date.now(), png: Buffer.from(e.data, 'base64') });
    client.send('Page.screencastFrameAck', { sessionId: e.sessionId }).catch(() => {});
  });
  await client.send('Page.startScreencast', { format: 'png', everyNthFrame: 1, maxWidth: width, maxHeight: height });
  return {
    async stop() {
      await client.send('Page.stopScreencast').catch(() => {});
      await client.detach().catch(() => {});
      return frames;
    },
  };
}

/**
 * Delays for a GIF from frames' paint times: [{ index, delay }] in milliseconds, one per frame
 * kept. `slow` is how many times slower than real time the server ran while it was recorded, so
 * each gap is divided by it; a frame less than `minGap` after the last kept one (after that
 * division) is dropped and its time goes to the frame before, so the length is the same. The last
 * frame holds for `hold` ms, then the loop restarts. GIF delays are whole hundredths of a second.
 */
function retime(times, { slow = 1, minGap = 40, hold = 1500 } = {}) {
  if (!times.length) return [];
  const kept = [{ index: 0, at: 0 }];
  for (let i = 1; i < times.length; i++) {
    const at = ((times[i] - times[0]) * 1000) / slow;
    if (at - kept[kept.length - 1].at >= minGap) kept.push({ index: i, at });
  }
  const end = ((times[times.length - 1] - times[0]) * 1000) / slow;
  return kept.map((k, i) => {
    const next = i + 1 < kept.length ? kept[i + 1].at : Math.max(end, k.at) + hold;
    return { index: k.index, delay: Math.max(20, Math.round((next - k.at) / 10) * 10) };
  });
}

/** Decodes a PNG to { width, height, data: RGBA }. */
function decode(png) {
  const { PNG } = require('pngjs');
  const img = PNG.sync.read(png);
  return { width: img.width, height: img.height, data: img.data };
}

/**
 * One palette for the whole reel, from a spread of its frames, so the colours do not shimmer from
 * frame to frame the way a palette per frame does. 255 colours: the last index is the transparent one.
 */
function sharedPalette(images, { quantize }) {
  const step = Math.max(1, Math.floor(images.length / 8));
  const sample = images.filter((_, i) => i % step === 0);
  const px = Buffer.concat(sample.map((s) => Buffer.from(s.data.buffer, s.data.byteOffset, s.data.length)));
  return [...quantize(px, 255), [0, 0, 0]];
}

const TRANSPARENT = 255;

/**
 * `index` with every pixel the same as in `before` made transparent, so a frame stores only what
 * moved (a gate against an empty studio is mostly still, and a GIF stores a whole frame otherwise).
 */
function changed(index, before) {
  if (!before) return index;
  const out = Uint8Array.from(index);
  for (let i = 0; i < out.length; i++) if (out[i] === before[i]) out[i] = TRANSPARENT;
  return out;
}

/** The frames from the first one received at or after `wallMs` (process time); all of them if none was before. */
function from(frames, wallMs) {
  const i = frames.findIndex((f) => f.recv >= wallMs);
  return i < 0 ? frames.slice(-1) : frames.slice(Math.max(0, i - 1));
}

/**
 * Writes `frames` ({ t, png } from a reel) to `file` as a looping GIF. Returns { file, frames,
 * seconds, bytes }. `slow` and `hold` as for retime.
 */
async function writeGif(file, frames, { slow = 1, hold = 1500, minGap = 40 } = {}) {
  if (!frames.length) throw new Error('no frames were recorded');
  const gif = (await import('gifenc')).default;
  const timing = retime(frames.map((f) => f.t), { slow, hold, minGap });
  const images = timing.map((k) => decode(frames[k.index].png));
  const { width, height } = images[0];
  const palette = sharedPalette(images, gif);
  const enc = gif.GIFEncoder();
  let before = null;
  let written = 0;
  images.forEach((img, i) => {
    if (img.width !== width || img.height !== height) return; // a resize mid-reel: skip the odd frame
    const index = gif.applyPalette(img.data, palette);
    enc.writeFrame(changed(index, before), width, height, {
      palette: written === 0 ? palette : undefined, delay: timing[i].delay, repeat: 0, transparent: written > 0, transparentIndex: TRANSPARENT, dispose: 1,
    });
    before = index;
    written++;
  });
  enc.finish();
  const bytes = Buffer.from(enc.bytes());
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, bytes);
  return { file, frames: written, seconds: timing.reduce((n, k) => n + k.delay, 0) / 1000, bytes: bytes.length };
}

module.exports = { startReel, retime, writeGif, decode, from, changed, TRANSPARENT };
