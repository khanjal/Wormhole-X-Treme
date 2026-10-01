'use strict';
// lib/shots.js without a browser: what counts as a picture. Run with `npm test --prefix
// scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
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
