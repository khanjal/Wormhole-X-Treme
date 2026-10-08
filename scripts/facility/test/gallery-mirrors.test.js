'use strict';
// lib/gallery-mirrors.js without a server: the scenes, where the mirror hangs and where Probe
// stands, what a scene does before it begins and what the run leaves behind. Run with `npm test
// --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const mirrors = require('../lib/gallery-mirrors');
const gallery = require('../lib/gallery');
const campus = require('../lib/campus');
const { STUDIO, EYE } = require('../lib/studio');

test('there is the banner, and each room as a still and a reel', () => {
  assert.deepStrictEqual(mirrors.scenes().map((s) => s.name), ['mirror-idle', 'mirror-nether-open', 'mirror-end-open', 'mirror-nether', 'mirror-end']);
  for (const s of mirrors.scenes()) {
    assert.strictEqual(s.family, 'mirror');
    if (s.kind === 'reel') assert.ok(s.trim > 0 && s.action === 'choose' && s.mirror.to, `${s.name}: a reel begins just before the click`);
    else assert.strictEqual(s.state === 'open', Boolean(s.mirror.to), `${s.name}: a still is open exactly when it names a room`);
  }
});

test('every room is a mirror the campus has, in another world than the studio', () => {
  const known = new Map(campus.ROUTES.mirrors.map((m) => [m.name, m]));
  for (const r of mirrors.ROOMS) {
    assert.ok(known.has(r.to), `${r.to} is not a campus mirror`);
    assert.notStrictEqual(known.get(r.to).dim, mirrors.MIRROR.dim, `${r.to} is in the studio's own world`);
  }
});

test('the gallery lists them last, and a prefix picks them', () => {
  const all = gallery.catalog();
  assert.ok(all.findIndex((s) => s.family === 'mirror') > all.findIndex((s) => s.family === 'ring'));
  assert.deepStrictEqual(gallery.select('mirror-', all).map((s) => s.name), mirrors.scenes().map((s) => s.name));
});

test('the banner hangs on the front of the backdrop, well inside its walls, with solid wall round the opening', () => {
  const m = mirrors.MIRROR;
  assert.strictEqual(m.facing, 'south');
  // The banner is on the south face of the wall at backdropAt: the wall block behind it is the opening's top half.
  assert.strictEqual(m.z, STUDIO.backdropAt + 1);
  assert.ok(Math.abs(m.x) + 2 < STUDIO.sideAt, 'a block of wall each side of the opening');
  assert.ok(m.y >= 1 && m.y + 2 < STUDIO.backdropHigh, 'wall above and the floor below it');
  assert.strictEqual(m.floorY, 0);
});

test('Probe looks at the middle of the banner from a couple of blocks out, feet on the floor, as it will face to click', () => {
  const m = mirrors.MIRROR;
  const cam = mirrors.camera();
  assert.ok(cam.y >= 0);
  assert.ok(Math.abs(cam.z - (m.z + 0.5)) <= 3 && cam.z > m.z + 0.5, `${cam.z - m.z - 0.5} blocks out`);
  assert.ok(Math.abs(cam.yaw) === 180, `facing north, not ${cam.yaw}`);
  // The click faces (x + 0.5, y + 0.6, z + 0.5): the picture must not move when it does.
  const face = { x: m.x + 0.5, y: m.y + 0.6, z: m.z + 0.5 };
  const eyeY = cam.y + EYE;
  const pitch = (Math.atan2(eyeY - face.y, cam.z - face.z) * 180) / Math.PI;
  assert.ok(Math.abs(pitch - cam.pitch) < 0.2, `the camera's pitch ${cam.pitch} is not the click's ${pitch.toFixed(1)}`);
});

test('the place Probe is sent to between scenes is out of the mirror\'s range', () => {
  const m = mirrors.MIRROR;
  assert.ok(Math.hypot(mirrors.AWAY.x - m.x, mirrors.AWAY.z - m.z) > 16 + 8, 'the plugin shows a room within 16 blocks');
  assert.ok(mirrors.AWAY.z < STUDIO.partnerAt && mirrors.AWAY.z > m.z, 'inside the studio');
});

/** A ctx whose server, Probe and mirror kit are stand-ins that record what they are asked. */
function fakeCtx({ list = ['Gallery'], set = "A right-click on 'Gallery' opens onto 'Range' first." } = {}) {
  const log = [];
  const kit = {
    banner: async (m, colour) => log.push(`banner ${m.name} ${colour}`),
    create: async (name) => { log.push(`create ${name}`); return 'Mirror made.'; },
    list: async () => ({ names: list }),
    captured: async (name) => log.push(`captured ${name}`),
    set: async (name, prop, value) => { log.push(`set ${name} ${prop} ${value}`); return set; },
    remove: async (name) => log.push(`remove ${name}`),
  };
  const ctx = {
    srv: { run: async (c) => { log.push(`run ${c}`); return { lines: [], errors: [] }; } },
    probe: { teleport: async (p) => log.push(`teleport ${p.x} ${p.z}`), bot: { on() {}, off() {} } },
    sleep: async () => {},
    mirrorState: { made: false, heard: [], kit },
  };
  return { ctx, log };
}

test('a scene makes the mirror once, waits for its capture, sends Probe away, then says where it opens', async () => {
  const { ctx, log } = fakeCtx();
  const [idle, nether] = [mirrors.scenes()[0], mirrors.scenes()[1]];
  await mirrors.prepare(ctx, idle);
  assert.deepStrictEqual(log.filter((l) => !/^teleport/.test(l)), ['banner Gallery red', 'create Gallery', 'captured Gallery']);
  log.length = 0;
  const prep = await mirrors.prepare(ctx, nether);
  // Made already: not again; away first; then the start.
  assert.deepStrictEqual(log, [`teleport ${mirrors.AWAY.x} ${mirrors.AWAY.z}`, 'set Gallery -start Range']);
  assert.strictEqual(prep.m, mirrors.MIRROR);
  assert.strictEqual(prep.heard, ctx.mirrorState.heard);
});

test('a mirror the plugin did not make, or a start it did not take, is an error rather than a picture of nothing', async () => {
  await assert.rejects(mirrors.prepare(fakeCtx({ list: [] }).ctx, mirrors.scenes()[0]), /mirror was not made/);
  const { ctx } = fakeCtx({ set: "No mirror called 'Range'." });
  ctx.mirrorState.made = true;
  await assert.rejects(mirrors.prepare(ctx, mirrors.scenes()[1]), /mirror set -start Range/);
});

test('the run takes the mirror and its banner away at the end, and does nothing if none was made', async () => {
  const { ctx, log } = fakeCtx();
  await mirrors.finish({ ...ctx, mirrorState: undefined });
  await mirrors.finish(ctx);
  assert.deepStrictEqual(log, []);
  ctx.mirrorState.made = true;
  await mirrors.finish(ctx);
  assert.deepStrictEqual(log, ['remove Gallery', 'run execute in minecraft:overworld run setblock 0 1 375 minecraft:air']);
  assert.strictEqual(ctx.mirrorState.made, false);
});

const { EventEmitter } = require('node:events');

/**
 * A ctx and prep for act(), on a clock sped up a hundredfold: a Probe that right-clicks by saying
 * what the plugin says above the hotbar, and a bot that reports the blocks the plugin sends.
 */
function actStage({ hint = "Gallery -- punch to travel to 'Range'. It opens onto 'Range' (1 of 2).", draw = 'ok' } = {}) {
  const bot = new EventEmitter();
  const m = mirrors.MIRROR;
  const heard = [];
  const clicks = [];
  const probe = {
    bot,
    face: async () => {},
    rightClick: async () => { clicks.push(Date.now()); heard.push({ at: Date.now(), bar: true, text: hint }); },
  };
  const marks = [];
  const ctx = { probe, factor: 0.01, sleep: (ms) => new Promise((r) => { setTimeout(r, ms); }), mark: () => marks.push(Date.now()) };
  const behind = (n) => { for (let i = 0; i < n; i++) bot.emit('blockUpdate', null, { name: 'stone', position: { x: m.x, y: 5, z: m.z - 3 - (i % 20) } }); };
  const bannerGone = () => bot.emit('blockUpdate', null, { name: 'air', position: { x: m.x, y: m.y, z: m.z } });
  if (draw === 'ok') setTimeout(() => { behind(300); bannerGone(); }, 30);
  if (draw === 'blocks only') setTimeout(() => behind(300), 30);
  if (draw === 'few') setTimeout(() => { behind(50); bannerGone(); }, 30);
  if (draw === 'bursts') {
    setTimeout(() => { behind(250); bannerGone(); }, 30);
    setTimeout(() => behind(60), 40);
  }
  return { ctx, prep: { m, heard }, marks, clicks };
}

test('the click is marked as the reel begins, and the reel ends once the room is drawn and has stopped arriving', async () => {
  const { ctx, prep, marks, clicks } = actStage();
  const t0 = Date.now();
  await mirrors.act(ctx, { mirror: { to: 'Range' } }, prep);
  assert.strictEqual(marks.length, 1);
  assert.ok(marks[0] <= clicks[0], 'marked before the click');
  assert.ok(Date.now() - t0 >= 30, 'not before the room arrived');
});

test('a mirror that does not open onto the room asked for is an error naming what it said', async () => {
  const { ctx, prep } = actStage({ hint: "Gallery -- right-click to choose a mirror. It opens onto 'Optics' (2 of 2)." });
  await assert.rejects(mirrors.act(ctx, { mirror: { to: 'Range' } }, prep), /did not open onto Range: .*Optics/);
});

test('a room whose banner never goes, or that never arrives, is an error and not a reel of the wrong thing', async () => {
  const blocks = actStage({ draw: 'blocks only' });
  await assert.rejects(mirrors.act(blocks.ctx, { mirror: { to: 'Range' } }, blocks.prep), /room was not drawn: 300 blocks behind the wall, banner still there/);
  const none = actStage({ draw: 'none' });
  await assert.rejects(mirrors.act(none.ctx, { mirror: { to: 'Range' } }, none.prep), /room was not drawn: 0 blocks/);
});

test('a room is not drawn until more than a handful of blocks have come, and not over while they still arrive', async () => {
  const few = actStage({ draw: 'few' });
  await assert.rejects(mirrors.act(few.ctx, { mirror: { to: 'Range' } }, few.prep), /room was not drawn: 50 blocks/);
  const bursts = actStage({ draw: 'bursts' });
  const t0 = Date.now();
  await mirrors.act(bursts.ctx, { mirror: { to: 'Range' } }, bursts.prep);
  assert.ok(Date.now() - t0 >= 50, `ended at ${Date.now() - t0} ms, while blocks were still coming at 40 ms`);
});
