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
  const everything = gallery.catalog();
  assert.strictEqual(new Set(everything.map((s) => s.name)).size, everything.length);
  const all = everything.filter((s) => !s.family);
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
  const flat = gallery.catalog().filter((x) => !x.family && shapes.isFlat(x.subject.shape));
  assert.ok(flat.length > 0);
  for (const s of flat) assert.strictEqual(s.view, 'high', s.name);
});

test("the ring patterns are the plugin\'s, bar none, and the shapes are the ones the console builds", () => {
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

test("Probe stands on the floor for every view, however low the gate's middle is", () => {
  for (const shape of gallery.SHAPES) {
    const geom = shapes.geometry(shape, { x: 0, y: -1, z: 400 }, 'south');
    for (const kind of ['front', 'quarter', 'high']) {
      const cam = gallery.camera(geom, kind);
      assert.ok(cam.y >= 0, `${shape} ${kind}: feet at ${cam.y}, under the floor`);
      assert.ok(aim(cam, geom) > 0.999, `${shape} ${kind}: not aimed at the gate once clamped`);
    }
  }
});

test('the groups carry their materials, and the default group is one of them', () => {
  const all = gallery.readGroups();
  assert.ok(gallery.DEFAULT_GROUP in all, 'the default group is not in config.yml');
  for (const [name, g] of Object.entries(all)) assert.ok(g.structure, `${name} has no structure block`);
  assert.strictEqual(all.Standard.structure, 'OBSIDIAN');
  assert.strictEqual(all.Atlantis.structure, 'LAPIS_BLOCK');
  assert.deepStrictEqual(gallery.groups(), Object.keys(all));
});

/** A kit that records what it is told, and answers `regen` and `edit` as the plugin does. */
function fakeKit({ edit = 'Gate edited.', regen = 'Re-detected StudioA from its frame.' } = {}) {
  const ran = [];
  return {
    ran,
    srv: { run: async (c) => { ran.push(c); return { lines: [], errors: [] }; } },
    say: async (c) => { ran.push(c); return { text: regen }; },
    edit: async (name, field, value) => { ran.push(`edit ${name} ${field} ${value}`); return { text: edit }; },
  };
}

test("a gate is dressed in another group's frame: every block laid in its structure block, then the gate found afresh", async () => {
  const geom = shapes.geometry('Standard', { x: 0, y: 0, z: 400 }, 'south');
  const kit = fakeKit();
  await gallery.dress(kit, geom, 'Atlantis');
  const sets = kit.ran.filter((c) => /setblock/.test(c));
  assert.strictEqual(sets.length, geom.blocks.length);
  assert.ok(sets.every((c) => c.endsWith('minecraft:lapis_block')), sets.join(', '));
  assert.ok(kit.ran.indexOf(kit.ran.find((c) => /gate regen/.test(c))) > kit.ran.lastIndexOf(sets[sets.length - 1]), 'regen comes after the blocks');
  assert.ok(kit.ran.some((c) => /^edit StudioA group Atlantis/.test(c)));
});

test('a group with its own chevron block gets it in the chevron cells, the structure block elsewhere', async () => {
  const geom = { blocks: [{ x: 1, y: 2, z: 3, role: 'chevron' }, { x: 4, y: 5, z: 6, role: 'frame' }, { x: 7, y: 8, z: 9, role: 'either' }] };
  const kit = fakeKit();
  await gallery.dress(kit, geom, 'X', { X: { structure: 'STONE', chevron: 'REDSTONE_LAMP' } });
  const sets = kit.ran.filter((c) => /setblock/.test(c)).map((c) => c.replace(/^.*setblock /, ''));
  assert.deepStrictEqual(sets, ['1 2 3 minecraft:redstone_lamp', '4 5 6 minecraft:stone', '7 8 9 minecraft:stone']);
  // A group with no chevron block of its own has the structure block in them too.
  const plain = fakeKit();
  await gallery.dress(plain, geom, 'Y', { Y: { structure: 'DEEPSLATE' } });
  assert.ok(plain.ran.filter((c) => /setblock/.test(c)).every((c) => c.endsWith('minecraft:deepslate')));
});

test('a group the repo knows and the server does not is an error, not a picture of the wrong palette', async () => {
  const geom = shapes.geometry('Standard', { x: 0, y: 0, z: 400 }, 'south');
  await assert.rejects(gallery.dress(fakeKit({ edit: 'No material group called Atlantis.' }), geom, 'Atlantis'), /server has no material group Atlantis/);
  await assert.rejects(gallery.dress(fakeKit(), geom, 'Nope'), /no material group Nope/);
});

test('a reel is retimed by the speed the server ran at: 20 over the tick rate, whatever slowdown was asked for', () => {
  assert.deepStrictEqual(gallery.slowdown(4, true), { rate: 5, factor: 4 });
  const odd = gallery.slowdown(3, true);
  assert.strictEqual(odd.rate, 6.667);
  assert.strictEqual(odd.factor, 20 / 6.667);
  assert.ok(Math.abs(odd.factor - 3) < 0.001);
  assert.deepStrictEqual(gallery.slowdown(20, true), { rate: 1, factor: 20 });
  assert.deepStrictEqual(gallery.slowdown(1, true), { rate: 20, factor: 1 });
  // A server that cannot be slowed records at full speed whatever was asked.
  assert.deepStrictEqual(gallery.slowdown(8, false), { rate: 20, factor: 1 });
});

test('a slowdown outside 1 to 20 is refused, before anything is built', async () => {
  for (const bad of [0, 0.5, 21, 30, NaN, -4]) assert.throws(() => gallery.slowdown(bad, true), /from 1 to 20/);
  await assert.rejects(gallery.takeScenes({}, {}, [], os.tmpdir(), { slow: 30 }), /from 1 to 20/);
});

test('a gate that is not found again in its new frame is an error: the picture would show the old gate drawn in the new group', async () => {
  const geom = shapes.geometry('Standard', { x: 0, y: 0, z: 400 }, 'south');
  const kept = "No shape matches all of StudioA's frame, so its recorded geometry is kept.";
  await assert.rejects(gallery.dress(fakeKit({ regen: kept }), geom, 'Atlantis'), /not re-detected in Atlantis/);
  await assert.rejects(gallery.dress(fakeKit(), geom, 'X', { X: { chevron: 'STONE' } }), /no structure block/);
});

test('a group key with a comment or quotes is still read', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-groups-'));
  try {
    const file = path.join(dir, 'config.yml');
    fs.writeFileSync(file, ['gate-material-groups:', '  A:', '    structure: OBSIDIAN  # the frame', '    light: "SEA_LANTERN"', "    chevron: 'REDSTONE_LAMP'", '    dial-spin: pegasus', ''].join('\n'));
    assert.deepStrictEqual(gallery.readGroups(file), { A: { structure: 'OBSIDIAN', light: 'SEA_LANTERN', chevron: 'REDSTONE_LAMP', 'dial-spin': 'pegasus' } });
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('the woosh is watched for in the opening and in the cells in front of it and above it, a block or two out', () => {
  const geom = shapes.geometry('Standard', { x: 0, y: 0, z: 400 }, 'south');
  const key = (c) => `${c.x},${c.y},${c.z}`;
  const cells = new Set(gallery.wooshCells(geom).map(key));
  for (const c of geom.opening) {
    assert.ok(cells.has(key(c)), 'the opening itself');
    for (const k of [1, 2]) {
      assert.ok(cells.has(key({ x: c.x + geom.normal.x * k, y: c.y, z: c.z + geom.normal.z * k })), `${k} out in front`);
      assert.ok(cells.has(key({ x: c.x, y: c.y + k, z: c.z })), `${k} above`);
    }
  }
  // Not the cells behind the gate, where the woosh is never drawn.
  const c = geom.opening[0];
  assert.ok(!cells.has(key({ x: c.x - geom.normal.x * 3, y: c.y, z: c.z - geom.normal.z * 3 })));
});

test("the studio's forceload covers its floor and fits one forceload command", () => {
  const [x0, z0, x1, z1] = gallery.forceRect();
  const s = gallery.STUDIO;
  // The floor to its edges, the backdrop and the side walls (which run to partnerAt + 30), and room beyond them.
  assert.ok(x0 <= -s.half - 16 && x1 >= s.half + 16 && z0 <= s.backdropAt - 16 && z1 >= s.partnerAt + 30);
  assert.ok((Math.floor(x1 / 16) - Math.floor(x0 / 16) + 1) * (Math.floor(z1 / 16) - Math.floor(z0 / 16) + 1) <= 256, 'a forceload command takes at most 256 chunks');
});

test('a command is judged by its own answer, not by a warning from something else that landed with it', async () => {
  const srv = (lines, errors) => ({ run: async () => ({ lines, errors }) });
  assert.deepStrictEqual(await gallery.must(srv(['The game is running at 5.0 ticks per second'], ['[WARN] unrelated']), 'tick rate 5', /ticks per second/i), ['The game is running at 5.0 ticks per second']);
  await assert.rejects(gallery.must(srv([], ['Unknown or incomplete command']), 'tick rate 5', /ticks per second/), /Unknown or incomplete command/);
  await assert.rejects(gallery.must(srv([], []), 'tick rate 5', /ticks/), /no answer/);
});
