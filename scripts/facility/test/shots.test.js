'use strict';
// lib/shots.js without a browser: what counts as a picture. Run with `npm test --prefix
// scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const shots = require('../lib/shots');

/** An RGBA frame of `n` pixels, the first `scene` of them grey and the rest the page's sky. */
function frame(n, scene) {
  const px = Buffer.alloc(n * 4);
  for (let i = 0; i < n; i++) px.set(i < scene ? [120, 120, 120, 255] : [...shots.SKY, 255], i * 4);
  return px;
}

test('the share of scene counts every pixel that is not the empty sky', () => {
  assert.strictEqual(shots.sceneShare(frame(100, 0)), 0);
  assert.strictEqual(shots.sceneShare(frame(100, 30)), 0.3);
  // Sky a shade off, as a compressed or blended frame may be, is still sky.
  assert.strictEqual(shots.sceneShare(Buffer.from([shots.SKY[0] + 4, shots.SKY[1] - 4, shots.SKY[2] + 4, 255])), 0);
});

const GOOD = { still: true, chunks: 120, chunksBefore: 120, share: 0.6, errors: [] };

test('a still frame of the facility, with chunks sent and none arriving, is a picture', () => {
  assert.deepStrictEqual(shots.judge(GOOD), { ok: true, why: [] });
});

test('two blank frames alike are not a picture: no chunks, or nothing but sky', () => {
  assert.deepStrictEqual(shots.judge({ ...GOOD, chunks: 0, chunksBefore: 0, share: 0 }).why,
    ['the page was sent no chunks', 'blank: 0.0% of it is not sky']);
  assert.deepStrictEqual(shots.judge({ ...GOOD, share: shots.MIN_SCENE / 2 }).why, ['blank: 2.5% of it is not sky']);
  assert.strictEqual(shots.judge({ ...GOOD, share: shots.MIN_SCENE }).ok, true);
});

test('a frame taken while chunks still arrive, still changing, or with page errors is not a picture', () => {
  assert.deepStrictEqual(shots.judge({ ...GOOD, chunksBefore: 100 }).why, ['chunks still arriving']);
  assert.deepStrictEqual(shots.judge({ ...GOOD, still: false }).why, ['still changing']);
  assert.deepStrictEqual(shots.judge({ ...GOOD, errors: ['boom'] }).why, ['page errors: boom']);
});

test('the browser is started without a Windows compatibility layer, whatever its case', () => {
  const env = shots.browserEnv({ PATH: 'x', __COMPAT_LAYER: 'DetectorsAppHealth', __compat_layer: 'y' });
  assert.deepStrictEqual(env, { PATH: 'x' });
});

/** settle() over a scripted run of frames ('A', 'B', ...) and chunk counts, one frame a tick, no real waiting. */
async function settleOver(frames, chunkCounts = frames.map(() => 10), settleMs = frames.length * 100) {
  let i = -1;
  let clock = 0;
  const shots_ = frames.map((f) => Buffer.from(f));
  const held = await shots.settle({
    shoot: async () => shots_[Math.min(i, frames.length - 1)],
    chunks: () => { i++; return chunkCounts[Math.min(i, chunkCounts.length - 1)]; },
    wait: async (ms) => { clock += ms; },
    now: () => clock,
    settleMs,
    everyMs: 100,
  });
  return { ...held, frame: held.png && held.png.toString(), taken: i + 1 };
}

test('a picture is held only after three like frames in a row, so one pause mid-mesh is not the end', async () => {
  const paused = await settleOver(['A', 'B', 'B', 'C', 'C', 'C']);
  assert.deepStrictEqual([paused.still, paused.frame, paused.taken], [true, 'C', 6]);
  const quick = await settleOver(['A', 'A', 'A']);
  assert.deepStrictEqual([quick.still, quick.taken], [true, 3]);
});

test('like frames with a chunk sent between them do not count as held', async () => {
  const held = await settleOver(['A', 'A', 'A', 'A'], [5, 6, 7, 7]);
  // Only the last two frames share a count: one pair, not two.
  assert.strictEqual(held.still, false);
  const later = await settleOver(['A', 'A', 'A', 'A', 'A'], [5, 6, 7, 7, 7]);
  assert.deepStrictEqual([later.still, later.chunksBefore], [true, 7]);
});

test('a picture that never holds is given up at the deadline, not held', async () => {
  const held = await settleOver(['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L'], undefined, 500);
  assert.strictEqual(held.taken, 5);
  assert.strictEqual(held.still, false);
});

test('each shot is a self-test check in a shots section, a bad one failing', () => {
  assert.deepStrictEqual(shots.asResults([{ name: 'a', ok: true, file: 'a.png', detail: '5 s' }, { name: 'b', ok: false, file: null, detail: 'boom' }]), [
    { section: 'shots', name: 'a', ok: true, detail: 'a.png (5 s)' },
    { section: 'shots', name: 'b', ok: false, detail: 'boom' },
  ]);
});

test('takeShots starts the browser without the compatibility layer and judges each page by its own chunks', async () => {
  const saved = { compat: process.env.__COMPAT_LAYER, browser: process.env.WX_BROWSER };
  process.env.__COMPAT_LAYER = 'DetectorsAppHealth';
  process.env.WX_BROWSER = process.execPath;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-shots-'));
  try {
    let launched = null;
    let url = '';
    const grey = Buffer.alloc(16 * 4, 120);
    const page = {
      setViewport: async () => {}, on: () => {}, waitForSelector: async () => {},
      goto: async (u) => { url = u; },
      screenshot: async () => Buffer.from('frame'),
      evaluate: async () => grey.toString('base64'),
    };
    const launch = async (options) => { launched = options; return { newPage: async () => page, close: async () => {} }; };
    const bot = { creative: { startFlying: () => {}, stopFlying: () => {} }, waitForChunksToLoad: async () => {} };
    const fac = { probe: { bot, teleport: async () => {} } };
    // Another page has many chunks; this shot's own page has some only under its own token.
    const viewer = {
      url: 'http://127.0.0.1:1/',
      chunks: (token) => (token && url.endsWith(`?shot=${token}`) ? 40 : (token === undefined ? 999 : 0)),
    };
    const out = await shots.takeShots(fac, viewer, [{ name: 'here', x: 0, y: 0, z: 0, dim: 'minecraft:overworld' }], dir, { launch, log: () => {} });
    assert.ok(launched, 'the browser was not started');
    assert.ok(!('__COMPAT_LAYER' in launched.env), 'the browser was started under the compatibility layer');
    assert.ok(/\/first\/\?shot=[\w-]+$/.test(url), url);
    assert.strictEqual(out[0].ok, true, out[0].detail);
    assert.match(out[0].detail, /, 40 chunks,/);
  } finally {
    for (const [k, v] of [['__COMPAT_LAYER', saved.compat], ['WX_BROWSER', saved.browser]]) {
      if (v === undefined) delete process.env[k]; else process.env[k] = v;
    }
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
