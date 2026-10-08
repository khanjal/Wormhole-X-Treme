'use strict';
// lib/gallery-rings.js without a server: the scenes, where each end and the camera are, who
// stands where. Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const rings = require('../lib/gallery-rings');
const gallery = require('../lib/gallery');
const { footprint } = require('../lib/rings');
const { STUDIO, EYE } = require('../lib/studio');

test('there is a still of each circle and a reel of each pattern in each style, and one with a ceiling ring', () => {
  const names = rings.scenes().map((s) => s.name);
  assert.deepStrictEqual(names, ['ring-odd-slabs', 'ring-even-slabs', 'ring-odd-fast', 'ring-odd-slow', 'ring-even-fast', 'ring-even-slow', 'ring-ceiling']);
  for (const s of rings.scenes()) {
    assert.strictEqual(s.family, 'ring');
    assert.ok(rings.PATTERNS.includes(s.ring.pattern) && rings.STYLES.includes(s.ring.style), s.name);
    if (s.kind === 'reel') assert.ok(s.trim > 0 && s.action === 'fire', `${s.name}: a reel begins just before the first ring is drawn`);
    else assert.strictEqual(s.state, 'idle', `${s.name}: a still is of the circles as laid`);
  }
  assert.deepStrictEqual(rings.scenes().filter((s) => s.ring.ceiling).map((s) => s.name), ['ring-ceiling']);
});

test('the gallery lists them after the gates, and a prefix picks them', () => {
  const all = gallery.catalog();
  assert.ok(all.findIndex((s) => s.family === 'ring') > all.findIndex((s) => s.name === 'kawoosh-horizontal'));
  assert.deepStrictEqual(gallery.select('ring-', all).map((s) => s.name), rings.scenes().map((s) => s.name));
});

test('a floor pair is two circles on the floor, a ceiling pair has the second the drop above it', () => {
  const [a, b] = rings.ends({ ceiling: false });
  assert.deepStrictEqual([a.y, b.y], [0, 0]);
  const [, c] = rings.ends({ ceiling: true });
  assert.strictEqual(c.y, rings.SITE.drop);
  // Far enough apart that their circles (7 across) do not touch, and in front of the backdrop.
  assert.ok(b.x - a.x >= 7 + 7);
  assert.ok(a.z > STUDIO.backdropAt + 10 && a.z < STUDIO.plane);
});

test('the circles are inside the studio walls, the roof is over the ceiling ring and wider than it', () => {
  for (const pattern of rings.PATTERNS) {
    for (const ceiling of [false, true]) {
      for (const p of rings.ends({ ceiling })) {
        const f = footprint(pattern, p);
        assert.ok(f.x0 > -STUDIO.sideAt && f.x1 < STUDIO.sideAt, `${pattern} at ${p.x}`);
      }
    }
    const b = rings.ends({ ceiling: true })[1];
    const r = rings.roof(pattern, b);
    const f = footprint(pattern, b);
    assert.strictEqual(r.y, b.y + 1);
    assert.ok(r.x0 < f.x0 && r.x1 > f.x1 && r.z0 < f.z0 && r.z1 > f.z1);
  }
});

test('somebody stands in the middle of a circle: a block centre for ODD, the corner of the 2 x 2 for EVEN', () => {
  const p = { x: 10, y: 0, z: 20 };
  assert.deepStrictEqual(rings.middle('ODD', p), { x: 10.5, y: 0, z: 20.5 });
  assert.deepStrictEqual(rings.middle('EVEN', p), { x: 11, y: 0, z: 21 });
  // The same cell the plugin's own slab list is round: ODD's slabs are symmetric about the anchor, EVEN's about anchor + 0.5.
  const { SLABS_ODD, SLABS_EVEN } = require('../lib/rings');
  const mean = (list, k) => list.reduce((n, s) => n + s[k], 0) / list.length;
  assert.strictEqual(mean(SLABS_ODD, 0), 0);
  assert.strictEqual(mean(SLABS_EVEN, 0), 0.5);
});

test('the camera is in front of both ends, on the floor, looking at the pair', () => {
  for (const ceiling of [false, true]) {
    const cam = rings.camera({ ceiling });
    const [a, b] = rings.ends({ ceiling });
    assert.ok(cam.y >= 0, 'feet on the floor');
    assert.ok(cam.z > a.z + 6 && cam.z < STUDIO.plane + 40, `z ${cam.z}`);
    // Both ends are inside the horizontal view (about 107 degrees across, so 53 either side of the way it looks).
    for (const end of [a, b]) {
      const bearing = (Math.atan2(-(end.x - cam.x), end.z - cam.z) * 180) / Math.PI;
      let off = Math.abs(bearing - cam.yaw) % 360;
      if (off > 180) off = 360 - off;
      assert.ok(off < 45, `end at x ${end.x} is ${off} degrees off the centre of the view`);
    }
    assert.ok(cam.pitch > 0 && cam.pitch < 30, `pitch ${cam.pitch}`);
  }
  assert.ok(EYE > 0);
});

const { EventEmitter } = require('node:events');

/** A ctx and prep for act(), on a clock sped up a hundredfold, whose bot reports block updates and whose traveller can be moved. */
function stage(pattern = 'ODD') {
  const bot = new EventEmitter();
  const [a, b] = rings.ends({ ceiling: false });
  const traveller = { position: { x: a.x + 0.5, y: 0, z: a.z + 0.5 } };
  const marks = [];
  const ctx = { bot, factor: 0.01, sleep: (ms) => new Promise((r) => { setTimeout(r, ms); }), mark: () => marks.push(Date.now()) };
  const prep = { a, b, pattern, traveller, kit: { fire: async () => 'is counting down.' }, id: 'abc' };
  const draw = (at) => bot.emit('blockUpdate', null, { position: { x: at.x, y: 1, z: at.z } });
  const land = () => { traveller.position = { x: b.x + 0.5, y: 0, z: b.z + 0.5 }; };
  return { ctx, prep, draw, land, a, b, marks };
}

const after = (ms, f) => setTimeout(f, ms);

test('the cycle is over once the traveller has landed and the rings have gone quiet, not before either', async () => {
  const { ctx, prep, draw, land, a, b, marks } = stage();
  const t0 = Date.now();
  after(10, () => draw(a));
  after(40, () => draw(b));
  // Quiet from 40 ms on, but nobody has landed: the stacks pause while they swap.
  after(150, land);
  after(160, () => draw(b));
  await rings.act(ctx, { kind: 'reel' }, prep);
  const took = Date.now() - t0;
  assert.ok(took >= 160, `ended at ${took} ms, before the last ring change at 160 ms`);
  assert.strictEqual(marks.length, 1, 'the first block drawn marks the start, once');
  assert.ok(marks[0] - t0 < 40, 'the mark is the first block, not a later one');
});

test('a pair that never carries the traveller is an error, and one that draws nothing says so', async () => {
  const lost = stage();
  after(10, () => lost.draw(lost.a));
  await assert.rejects(rings.act(lost.ctx, { kind: 'reel' }, lost.prep), /never reached the far ring/);
  const dead = stage();
  await assert.rejects(rings.act(dead.ctx, { kind: 'reel' }, dead.prep), /no ring was ever drawn/);
});

test('blocks outside the circles are not the cycle', async () => {
  const { ctx, prep, draw } = stage();
  after(5, () => draw({ x: 40, z: 380 }));
  await assert.rejects(rings.act(ctx, { kind: 'reel' }, prep), /no ring was ever drawn/);
});

test('a fire the plugin refuses is an error, with what it said', async () => {
  const { ctx, prep } = stage();
  prep.kit.fire = async () => 'Ring pair abc did not fire: error, an end is blocked';
  await assert.rejects(rings.act(ctx, { kind: 'reel' }, prep), /ring fire: .*blocked/);
});
