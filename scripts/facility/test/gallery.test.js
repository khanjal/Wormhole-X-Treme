'use strict';
// lib/gallery.js without a server: the scenes, the camera, the studio's place. Run with `npm test
// --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const gallery = require('../lib/gallery');
const shapes = require('../lib/shapes');

test('Minecraft faces south at yaw 0, east at -90, west at 90 and north at 180', () => {
  assert.deepStrictEqual(gallery.lookAt({ x: 0, y: 5, z: 10 }, { x: 0, y: 5, z: 0 }), { yaw: -180, pitch: 0 });
  assert.deepStrictEqual(gallery.lookAt({ x: 0, y: 5, z: 0 }, { x: 0, y: 5, z: 10 }), { yaw: 0, pitch: 0 });
  assert.strictEqual(gallery.lookAt({ x: 0, y: 5, z: 0 }, { x: 10, y: 5, z: 0 }).yaw, -90);
  assert.strictEqual(gallery.lookAt({ x: 0, y: 5, z: 0 }, { x: -10, y: 5, z: 0 }).yaw, 90);
});

test('a camera above its subject looks down, one level with it looks straight', () => {
  assert.strictEqual(gallery.lookAt({ x: 0, y: 10, z: 10 }, { x: 0, y: 0, z: 0 }).pitch > 0, true);
  assert.strictEqual(gallery.lookAt({ x: 0, y: 0, z: 10 }, { x: 0, y: 0, z: 0 }).pitch, 0);
});

/** How closely a camera's look points at the middle of the gate: 1 is dead on. */
function aim(cam, geom) {
  const eye = { x: cam.x, y: cam.y + gallery.EYE, z: cam.z };
  const to = { x: geom.centre.x - eye.x, y: geom.centre.y - eye.y, z: geom.centre.z - eye.z };
  const len = Math.hypot(to.x, to.y, to.z);
  const yaw = (cam.yaw * Math.PI) / 180;
  const pitch = (cam.pitch * Math.PI) / 180;
  const look = { x: -Math.sin(yaw) * Math.cos(pitch), y: -Math.sin(pitch), z: Math.cos(yaw) * Math.cos(pitch) };
  return (to.x * look.x + to.y * look.y + to.z * look.z) / len;
}

test('every view of every shape looks at the middle of the gate, from the side its opening faces', () => {
  for (const shape of gallery.SHAPES) {
    const geom = shapes.geometry(shape, { x: 0, y: 0, z: 400 }, 'south');
    for (const kind of ['front', 'quarter', 'high']) {
      const cam = gallery.camera(geom, kind);
      assert.ok(aim(cam, geom) > 0.999, `${shape} ${kind}: the camera is not aimed at the gate (${aim(cam, geom)})`);
      assert.ok(cam.z > geom.centre.z - 0.5, `${shape} ${kind}: the camera is behind the gate, not in front of it`);
    }
  }
});

test('a bigger gate is seen from further away, a small one is never closer than six blocks', () => {
  const dist = (shape) => {
    const geom = shapes.geometry(shape, { x: 0, y: 0, z: 400 }, 'south');
    return gallery.camera(geom, 'front').z - geom.centre.z;
  };
  assert.ok(dist('Massive') > dist('Standard'));
  assert.ok(dist('Standard') >= 6 && dist('Minimal') >= 6);
});

test('every camera stands inside the studio: between its walls, and in front of the backdrop and behind the partner gate', () => {
  const { sideAt, backdropAt, partnerAt, plane } = gallery.STUDIO;
  for (const shape of gallery.SHAPES) {
    const geom = shapes.geometry(shape, { x: 0, y: 0, z: plane }, 'south');
    for (const kind of ['front', 'quarter', 'high']) {
      const cam = gallery.camera(geom, kind);
      assert.ok(Math.abs(cam.x) < sideAt - 2, `${shape} ${kind}: x ${cam.x}`);
      assert.ok(cam.z > backdropAt && cam.z < partnerAt - 10, `${shape} ${kind}: z ${cam.z}`);
    }
  }
});

test('every scene has its own name, a shape the console builds, a known group and ring pattern, and a view', () => {
  const all = gallery.catalog();
  assert.strictEqual(new Set(all.map((s) => s.name)).size, all.length);
  const groups = gallery.groups();
  for (const s of all) {
    assert.ok(shapes.consoleBuildable(s.subject.shape), `${s.name}: ${s.subject.shape}`);
    assert.ok(groups.includes(s.subject.group), `${s.name}: group ${s.subject.group}`);
    assert.ok(s.subject.spin === null || gallery.SPINS.includes(s.subject.spin), `${s.name}: spin ${s.subject.spin}`);
    assert.ok(['front', 'quarter', 'high'].includes(s.view), `${s.name}: view ${s.view}`);
    assert.ok(['still', 'reel'].includes(s.kind));
    assert.ok(s.kind === 'still' ? ['idle', 'open'].includes(s.state) : ['dial', 'kawoosh'].includes(s.action), s.name);
  }
});

test('a gate lying in the floor is seen from above, never square on', () => {
  const flat = gallery.catalog().filter((x) => shapes.isFlat(x.subject.shape));
  assert.ok(flat.length > 0);
  for (const s of flat) assert.strictEqual(s.view, 'high', s.name);
});

test('the ring patterns are the plugin\'s, bar none, and the shapes are the ones the console builds', () => {
  const src = fs.readFileSync(path.resolve(__dirname, '..', '..', '..', 'src', 'main', 'java', 'com', 'wormhole_xtreme', 'wormhole', 'logic', 'DialSpinPattern.java'), 'utf8');
  const named = [...src.matchAll(/^\s{4}([A-Z_]+)[,;(]/gm)].map((m) => m[1].toLowerCase()).filter((n) => n !== 'none');
  assert.deepStrictEqual([...gallery.SPINS].sort(), named.sort());
  const built = fs.readdirSync(shapes.SHAPE_DIR).map((f) => f.replace(/\.shape$/, '')).filter(shapes.consoleBuildable);
  assert.deepStrictEqual([...gallery.SHAPES].sort(), built.sort());
});

test('select takes names, dash prefixes and all, and refuses what it does not know', () => {
  const all = gallery.catalog();
  assert.strictEqual(gallery.select('all', all).length, all.length);
  assert.strictEqual(gallery.select(undefined, all).length, all.length);
  assert.deepStrictEqual(gallery.select('dial-lap', all).map((s) => s.name), ['dial-lap']);
  assert.deepStrictEqual(gallery.select('kawoosh-', all).map((s) => s.name), gallery.SHAPES.map((n) => `kawoosh-${n.toLowerCase()}`));
  // In catalog order and once each, whatever order and repeats it was asked in.
  assert.deepStrictEqual(gallery.select('dial-lap,dial-top,dial-', all).map((s) => s.name), gallery.SPINS.map((n) => `dial-${n}`));
  assert.throws(() => gallery.select('dial-lap,dail-top', all), /no gallery scene named dail-top/);
  assert.throws(() => gallery.select('dial', all), /no gallery scene named dial /);
});

test('the groups are read from the config the plugin ships, from its gate-material-groups block only', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-groups-'));
  try {
    const file = path.join(dir, 'config.yml');
    fs.writeFileSync(file, ['other:', '  NotAGroup:', '    x: 1', 'gate-material-groups:', '  Standard:', '    structure: OBSIDIAN', '  Atlantis:', '    structure: LAPIS_BLOCK', 'after:', '  Nope:', '    y: 2', ''].join('\r\n'));
    assert.deepStrictEqual(gallery.groups(file), ['Standard', 'Atlantis']);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('the studio is well clear of the campus, whose forceloaded land ends at z 92', () => {
  const campus = require('../lib/campus');
  const south = Math.max(...campus.FORCELOAD.filter((f) => f.dim === campus.OVERWORLD).flatMap((f) => [f.from[1], f.to[1]]));
  assert.ok(gallery.STUDIO.backdropAt - 16 > south + 50, `the studio starts at ${gallery.STUDIO.backdropAt - 16}, the campus ends at ${south}`);
});
