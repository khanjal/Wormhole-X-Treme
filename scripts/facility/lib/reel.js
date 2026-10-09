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
 * recv: ms on this process's, png: Buffer }. `stop()` ends it and returns them in order. Chrome
 * sends no frame for a page that does not change, so a quiet stretch is one long frame, which is
 * what the delays are for.
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

/** The pixels in a Uint8Array that owns its whole buffer: gifenc reads `.buffer` as if it began at the first pixel. */
function own(data) {
  if (data.byteOffset === 0 && data.buffer.byteLength === data.length) return new Uint8Array(data.buffer, 0, data.length);
  return Uint8Array.from(data);
}

/** Decodes a PNG to { width, height, data: RGBA }, the data in an array buffer of its own. */
function decode(png) {
  const { PNG } = require('pngjs');
  const img = PNG.sync.read(png);
  return { width: img.width, height: img.height, data: own(img.data) };
}

/** A PNG's size, from its header, without decoding it. */
function sizeOf(png) {
  return { width: png.readUInt32BE(16), height: png.readUInt32BE(20) };
}

/**
 * The colours one GIF's frames are drawn in: at most 255, from a spread of the frames (`decoded(i)`
 * gives frame i's pixels), so the colours do not shimmer from frame to frame the way a palette per
 * frame does. The transparent entry is one past them, and so is never a colour a pixel can be given.
 */
function paletteOf(count, decoded, { quantize }) {
  const step = Math.max(1, Math.floor(count / 8));
  const sample = [];
  for (let i = 0; i < count; i += step) sample.push(decoded(i).data);
  const px = new Uint8Array(sample.reduce((n, d) => n + d.length, 0));
  let at = 0;
  for (const d of sample) { px.set(d, at); at += d.length; }
  const colours = quantize(px, 255);
  return { colours, transparent: colours.length, table: [...colours, [0, 0, 0]] };
}

/**
 * `index` with every pixel the same as in `before` made `transparent`, so a frame stores only what
 * moved (a gate against an empty studio is mostly still, and a GIF stores a whole frame otherwise).
 */
function changed(index, before, transparent) {
  if (!before) return index;
  const out = Uint8Array.from(index);
  for (let i = 0; i < out.length; i++) if (out[i] === before[i]) out[i] = transparent;
  return out;
}

/**
 * The frames from the first received at or after `wallMs` (process time), the one before it
 * included but made to begin at `wallMs`: Chrome sends nothing while the page is still, so that
 * frame may have been painted long before the cut. All of them if none was received before.
 */
function from(frames, wallMs) {
  const i = frames.findIndex((f) => f.recv >= wallMs);
  if (i < 0) return frames.slice(-1);
  if (i === 0) return frames;
  const lead = { ...frames[i - 1], t: Math.max(frames[i - 1].t, frames[i].t - (frames[i].recv - wallMs) / 1000) };
  return [lead, ...frames.slice(i)];
}

/**
 * Writes `frames` ({ t, png } from a reel) to `file` as a looping GIF. Returns { file, frames,
 * seconds, bytes }. `slow`, `minGap` and `hold` as for retime. A frame whose size is not the
 * commonest is left out before anything is timed, so its time goes to its neighbour; frames are
 * decoded one at a time, so a long reel is not all in memory as pixels at once.
 */
async function writeGif(file, frames, { slow = 1, hold = 1500, minGap = 40 } = {}) {
  if (!frames.length) throw new Error('no frames were recorded');
  const gif = (await import('gifenc')).default;
  const sizes = new Map();
  for (const f of frames) {
    const s = sizeOf(f.png);
    const k = `${s.width}x${s.height}`;
    sizes.set(k, { ...s, n: (sizes.get(k) || { n: 0 }).n + 1 });
  }
  const { width, height } = [...sizes.values()].sort((a, b) => b.n - a.n)[0];
  const same = frames.filter((f) => { const s = sizeOf(f.png); return s.width === width && s.height === height; });
  const timing = retime(same.map((f) => f.t), { slow, hold, minGap });
  const decoded = (i) => decode(same[timing[i].index].png);
  const pal = paletteOf(timing.length, decoded, gif);
  const enc = gif.GIFEncoder();
  let before = null;
  timing.forEach((k, i) => {
    const index = gif.applyPalette(decoded(i).data, pal.colours);
    enc.writeFrame(changed(index, before, pal.transparent), width, height, {
      palette: i === 0 ? pal.table : undefined, delay: k.delay, repeat: 0, transparent: i > 0, transparentIndex: pal.transparent, dispose: 1,
    });
    before = index;
  });
  enc.finish();
  const bytes = Buffer.from(enc.bytes());
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, bytes);
  return { file, frames: timing.length, seconds: timing.reduce((n, k) => n + k.delay, 0) / 1000, bytes: bytes.length };
}

module.exports = { startReel, retime, writeGif, decode, own, sizeOf, paletteOf, changed, from };
