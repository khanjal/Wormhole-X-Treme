'use strict';
// Design mode without a server (lib/design.js, lib/zip.js and the guarded placements of
// lib/schematics.js): the placeholders reach every protected volume, the check finds what a
// designer must not do and nothing else, and an export never carries a placeholder into a test
// world. Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const design = require('../lib/design');
const zip = require('../lib/zip');
const sch = require('../lib/schematics');
const bp = require('../lib/blueprint');
const campus = require('../lib/campus');

const PROTECT = design.protectedBoxes('1.21.11');
const SKINS = design.skins();
const O = campus.OVERWORLD;

function scratch() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'wx-design-'));
}

test('the export areas hold every position of every protected volume and skin, so check and export see all of them', () => {
  const areas = design.areas();
  const uncovered = (dim, box) => {
    const g = new design.Grid({ dim, box, palette: ['minecraft:air'], ids: new Uint32Array(bp.volume(box)) });
    return g.maskOf(areas).filter((v) => !v).length;
  };
  assert.deepStrictEqual(PROTECT.filter((k) => uncovered(k.dim, k.box)).map((k) => k.what), []);
  assert.deepStrictEqual(SKINS.filter((s) => uncovered(s.dim, s.outer)).map((s) => s.what), []);
});

test('the placeholder fills cover every position of every protected volume, and only over air', () => {
  const fills = design.placeholderFills(PROTECT);
  for (const f of fills) {
    assert.ok(bp.volume(f.box) <= 32768, 'a fill over the vanilla limit');
    assert.match(design.fillCommand(f), / replace minecraft:air$/);
  }
  // Rasterised per area: every protected position is under some fill.
  for (const a of design.areas()) {
    const g = new design.Grid({ dim: a.dim, box: a.box, palette: ['minecraft:air'], ids: new Uint32Array(bp.volume(a.box)) });
    const want = g.maskOf(PROTECT);
    const got = g.maskOf(fills);
    let missing = 0;
    for (let i = 0; i < want.length; i++) if (want[i] && !got[i]) missing++;
    assert.strictEqual(missing, 0, `${missing} protected positions in ${a.name} get no placeholder`);
    assert.ok(want.some((v) => v), `${a.name} has no protected volume at all`);
  }
  // Test volumes take the volume placeholder, the rest the fixture one.
  const g1 = fills.find((f) => design.contains(campus.chamber('g1').box ? bp.interior(campus.chamber('g1').box) : null, f.box));
  assert.strictEqual(g1.block, design.PLACEHOLDER.volume);
  const pad = PROTECT.find((k) => k.what === 'the beam pad Atrium');
  assert.ok(fills.some((f) => f.block === design.PLACEHOLDER.fixture && design.contains(pad.box, f.box)));
});

// A small area round C0 (x 11..17, z 7..13, y 0..3), made as the campus leaves it: every protected
// position a placeholder except a wall block, and a lever the campus put in C0's skin.
const C0_AREA = { name: 'c0-test', dim: O, box: bp.box3(0, -6, 0, 30, 9, 25) };
const WALL = [10, 1, 10]; // C0's west wall, in its footprint
const CAMPUS_LEVER = [19, 1, 8]; // east of C0, in its skin, outside every protected box
const PLANT = [14, 1, 10]; // inside C0's clear volume
const SKIN_LEVER = [19, 1, 10]; // in C0's skin, outside every protected box

function c0Baseline() {
  const g = design.Grid.make({ box: C0_AREA.box });
  const prot = g.maskOf(PROTECT);
  const ph = g.idOf(design.PLACEHOLDER.volume);
  for (let i = 0; i < prot.length; i++) if (prot[i]) g.ids[i] = ph;
  g.set(...WALL, 'minecraft:white_concrete');
  g.set(...CAMPUS_LEVER, 'minecraft:lever[face=wall,facing=east,powered=false]');
  return g;
}

function copyOf(g) {
  const c = new design.Grid({ dim: g.dim, box: g.box, palette: [...g.palette], ids: Uint32Array.from(g.ids), entities: g.entities.map((e) => ({ ...e })) });
  c.blockEntities = g.blockEntities.map((b) => ({ ...b }));
  return c;
}

test('the test positions round C0 are where the check test needs them', () => {
  const g = design.Grid.make({ box: C0_AREA.box });
  const prot = g.maskOf(PROTECT);
  const skin = g.skinMaskOf(SKINS);
  assert.ok(prot[g.index(...PLANT)] && prot[g.index(...WALL)]);
  assert.ok(!prot[g.index(...SKIN_LEVER)] && skin[g.index(...SKIN_LEVER)]);
  assert.ok(!prot[g.index(...CAMPUS_LEVER)] && skin[g.index(...CAMPUS_LEVER)]);
});

test('check finds a block planted in a test volume and a lever in a skin, and nothing the campus or the placeholders put there', () => {
  const baseline = c0Baseline();
  const current = copyOf(baseline);
  current.set(...PLANT, 'minecraft:stone');
  current.set(...SKIN_LEVER, 'minecraft:lever[face=wall,facing=east,powered=true]');
  // Things that are fine: a placeholder broken to air, decoration well clear, the campus's own lever pulled.
  const ph = PROTECT.find((k) => k.what === 'c0\'s clear volume').box;
  current.set(ph.x1, ph.y1, ph.z1, 'minecraft:air');
  current.set(25, 0, 22, 'minecraft:copper_bulb[lit=true]');
  current.set(25, 1, 22, 'minecraft:lever[face=floor,facing=north,powered=false]');
  current.set(...CAMPUS_LEVER, 'minecraft:lever[face=wall,facing=east,powered=true]');
  const items = design.compareArea({ area: C0_AREA, current, baseline, protect: PROTECT, skinList: SKINS });
  assert.deepStrictEqual(items.map((p) => [p.kind, p.x, p.y, p.z, p.block]), [
    ['inside', ...PLANT, 'minecraft:stone'],
    ['skin', ...SKIN_LEVER, 'minecraft:lever'],
  ]);
  assert.match(items[0].what, /c0's clear volume/);
  assert.strictEqual(items[0].wing, 'ops');
  assert.match(items[1].what, /c0's skin/);
});

test('check: a removed wall block is a problem, the same placeholder put back is not', () => {
  const baseline = c0Baseline();
  const current = copyOf(baseline);
  current.set(...WALL, 'minecraft:air');
  current.set(...PLANT, design.PLACEHOLDER.fixture); // a placeholder of the other colour: still a placeholder
  const items = design.compareArea({ area: C0_AREA, current, baseline, protect: PROTECT, skinList: SKINS });
  assert.deepStrictEqual(items.map((p) => [p.kind, p.x, p.y, p.z, p.was]), [['inside', ...WALL, 'minecraft:white_concrete']]);
});

test('check: a mob is a problem anywhere, an armour stand only in a skin or a volume, the campus\'s own entities never', () => {
  const baseline = c0Baseline();
  baseline.entities = [{ id: 'minecraft:text_display', pos: [20.5, 2.6, 12.5] }];
  const current = copyOf(baseline);
  current.entities.push(
    { id: 'minecraft:pig', pos: [26.5, 0, 20.5] },
    { id: 'minecraft:armor_stand', pos: [26.5, 0, 22.5] },
    { id: 'minecraft:armor_stand', pos: [SKIN_LEVER[0] + 0.5, 0, SKIN_LEVER[2] + 0.5] },
  );
  const items = design.compareArea({ area: C0_AREA, current, baseline, protect: PROTECT, skinList: SKINS });
  assert.deepStrictEqual(items.map((p) => [p.kind, p.block, p.x, p.z]), [
    ['entity', 'minecraft:pig', 26, 20],
    ['entity-skin', 'minecraft:armor_stand', SKIN_LEVER[0], SKIN_LEVER[2]],
  ]);
});

test('check reports a placeholder block outside the volumes as a stray, not a problem', () => {
  const baseline = c0Baseline();
  const current = copyOf(baseline);
  current.set(25, 0, 22, design.PLACEHOLDER.volume);
  const items = design.compareArea({ area: C0_AREA, current, baseline, protect: PROTECT, skinList: SKINS });
  assert.deepStrictEqual(items.map((p) => p.kind), ['stray']);
  assert.match(design.formatReport(items), /No problems/);
});

test('export masks every protected position, strips every placeholder and structure void, and keeps the decoration', async () => {
  const baseline = c0Baseline();
  const current = copyOf(baseline);
  current.set(25, 0, 22, design.PLACEHOLDER.volume); // a stray
  current.set(24, 0, 22, design.PLACEHOLDER.fixture);
  current.set(23, 0, 22, design.MASK);
  current.set(22, 0, 22, 'minecraft:copper_bulb[lit=true]');
  current.entities.push({ id: 'minecraft:armor_stand', pos: [26.5, 0, 22.5] }, { id: 'minecraft:pig', pos: [26.5, 0, 20.5] });
  const out = design.exportArea({ current, baseline, protect: PROTECT, skinList: SKINS });
  const g = out.grid;
  const prot = g.maskOf(PROTECT);
  let notMasked = 0;
  for (let i = 0; i < g.ids.length; i++) {
    const name = g.names[g.ids[i]];
    assert.ok(!design.PLACEHOLDERS.has(name), `a placeholder left at ${g.at(i)}`);
    if (prot[i] && name !== design.MASK) notMasked++;
    if (!prot[i]) assert.notStrictEqual(name, design.MASK, `structure void outside the volumes at ${g.at(i)}`);
  }
  assert.strictEqual(notMasked, 0);
  assert.strictEqual(g.name(22, 0, 22), 'minecraft:copper_bulb');
  assert.strictEqual(g.name(25, 0, 22), 'minecraft:air');
  assert.deepStrictEqual(g.entities.map((e) => e.id), ['minecraft:armor_stand']);
  // Through a file and back: what lib/schematics.js would paste passes its check; the unmasked area does not.
  const d = scratch();
  try {
    design.writeGrid(g, path.join(d, 'a.schem'));
    const back = await design.readGrid(path.join(d, 'a.schem'), { dim: O, at: { x: C0_AREA.box.x0, y: C0_AREA.box.y0, z: C0_AREA.box.z0 } });
    assert.deepStrictEqual(back.box, C0_AREA.box);
    assert.ok(!back.names.some((n) => design.PLACEHOLDERS.has(n)), 'a placeholder in the written palette');
    assert.deepStrictEqual(design.pasteProblems(back, PROTECT, SKINS, 'a.schem'), []);
    assert.deepStrictEqual(back.entities.map((e) => [e.id, ...e.pos]), [['minecraft:armor_stand', 26.5, 0, 22.5]]);
    design.writeGrid(current, path.join(d, 'raw.schem'));
    const raw = await design.readGrid(path.join(d, 'raw.schem'), { dim: O, at: { x: 0, y: -6, z: 0 } });
    const problems = design.pasteProblems(raw, PROTECT, SKINS, 'raw.schem');
    assert.ok(problems.some((p) => /would paste \d+ block\(s\) into protected volumes/.test(p)), problems.join('\n'));
    assert.ok(problems.some((p) => /placeholder/.test(p)), problems.join('\n'));
    assert.ok(problems.some((p) => /pig/.test(p)), problems.join('\n'));
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});

test('an export\'s placements are guarded, unturned, at each area\'s corner, from 1.21.11; --schematics reads them and leaves them out on 1.20.4', async () => {
  const areas = design.areas().map((a) => ({ ...a, file: `${a.name}.schem` }));
  const list = design.placementsFor(areas);
  assert.strictEqual(list.length, areas.length);
  for (const [i, p] of list.entries()) {
    assert.deepStrictEqual(Object.keys(p).sort(), ['at', 'dim', 'file', 'guarded', 'minVersion', 'rotation']);
    assert.deepStrictEqual(p.at, { x: areas[i].box.x0, y: areas[i].box.y0, z: areas[i].box.z0 });
    assert.strictEqual(p.rotation, 0);
    assert.strictEqual(p.guarded, true);
    assert.strictEqual(p.minVersion, '1.21.11');
  }
  const m = design.manifestFor({ commit: 'abc', designer: 'Tester', areaList: areas, problems: 0, date: new Date('2026-10-01T12:00:00Z') });
  for (const k of ['kind', 'format', 'facility', 'minecraft', 'appliesFrom', 'designer', 'date', 'areas', 'checkProblems']) assert.ok(k in m, k);
  assert.strictEqual(m.minecraft, '1.21.11');
  assert.strictEqual(m.designer, 'Tester');
  assert.strictEqual(m.date, '2026-10-01T12:00:00.000Z');
  const d = scratch();
  try {
    fs.writeFileSync(path.join(d, 'placements.json'), JSON.stringify(list));
    for (const a of areas) fs.writeFileSync(path.join(d, a.file), '');
    const read = sch.placements(d);
    assert.ok(read.every((p) => p.guarded && p.minVersion === '1.21.11'));
    assert.strictEqual(sch.forVersion(read, '1.20.4').use.length, 0);
    assert.strictEqual(sch.forVersion(read, '1.21.11').use.length, list.length);
    assert.strictEqual(sch.forVersion(read, '26.1.2').use.length, list.length);
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});

test('--schematics passes a guarded placement over a test volume when it is masked there, and refuses it when it is not', async () => {
  const d = scratch();
  try {
    const baseline = c0Baseline();
    const current = copyOf(baseline);
    current.set(22, 0, 22, 'minecraft:copper_bulb[lit=true]');
    design.writeGrid(design.exportArea({ current, baseline, protect: PROTECT, skinList: SKINS }).grid, path.join(d, 'masked.schem'));
    design.writeGrid(current, path.join(d, 'raw.schem'));
    const at = { x: C0_AREA.box.x0, y: C0_AREA.box.y0, z: C0_AREA.box.z0 };
    fs.writeFileSync(path.join(d, 'placements.json'), JSON.stringify([
      { file: 'masked.schem', at, guarded: true, minVersion: '1.21.11' },
      { file: 'raw.schem', at, guarded: true, minVersion: '1.21.11' },
      { file: 'masked.schem', at },
    ]));
    const { problems } = await sch.check(sch.placements(d), '1.21.11');
    assert.ok(!problems.some((p) => p.startsWith('masked.schem') && /protected volumes|placeholder/.test(p)), problems.join('\n'));
    assert.ok(problems.some((p) => p.startsWith('raw.schem') && /into protected volumes/.test(p)), problems.join('\n'));
    // Unguarded, the same masked box is refused by its box alone, as before.
    assert.ok(problems.some((p) => p.startsWith('masked.schem') && /reaches into c0's clear volume/.test(p)), problems.join('\n'));
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});

test('a guarded placement is pasted with its entities and around structure void; a plain one as before', async () => {
  const EventEmitter = require('events');
  const srv = new EventEmitter();
  srv.sent = [];
  srv.run = async (cmd) => {
    srv.sent.push(cmd);
    if (/world world$/.test(cmd)) return { lines: ['Set the world override to world.'], errors: [] };
    if (/pos1/.test(cmd)) return { lines: ['First position set to (0, 0, 0).'], errors: [] };
    if (/schem load (\S+)/.test(cmd)) return { lines: [`${cmd.split(' ')[2]} loaded. Paste it with //paste`], errors: [] };
    if (/paste/.test(cmd)) return { lines: ['The clipboard has been pasted at (0, 0, 0)'], errors: [] };
    return { lines: [], errors: [] };
  };
  const box = { x0: 0, y0: 0, z0: 0, x1: 0, y1: 0, z1: 0 };
  const out = await sch.paste(srv, [{ file: 'a.schem', at: { x: 0, y: 0, z: 0 }, dim: O, rotation: 0, guarded: true, box }, { file: 'b.schem', at: { x: 0, y: 0, z: 0 }, dim: O, rotation: 0, box }]);
  assert.deepStrictEqual(out.map((r) => r.ok), [true, true]);
  assert.deepStrictEqual(srv.sent.filter((c) => /paste/.test(c)), ['//paste -e -m !minecraft:structure_void', '//paste']);
});

test('a zip written here reads back byte for byte, and one that would write outside its folder is refused', () => {
  const d = scratch();
  try {
    const src = path.join(d, 'src');
    fs.mkdirSync(path.join(src, 'region'), { recursive: true });
    const big = Buffer.alloc(200000, 7);
    const noise = Buffer.from(Array.from({ length: 5000 }, (_, i) => (i * 7919) % 251));
    fs.writeFileSync(path.join(src, 'region', 'r.0.0.mca'), big);
    fs.writeFileSync(path.join(src, 'level.dat'), noise);
    fs.writeFileSync(path.join(src, 'session.lock'), 'x');
    const names = zip.filesUnder(src, (r) => r === 'session.lock');
    assert.deepStrictEqual(names.sort(), ['level.dat', 'region/r.0.0.mca']);
    const file = path.join(d, 'out.zip');
    zip.writeZip(file, [...names.map((n) => ({ name: `worlds/world/${n}`, from: path.join(src, n) })), { name: 'manifest.json', data: Buffer.from('{}') }]);
    const to = path.join(d, 'x');
    assert.deepStrictEqual(zip.extractZip(file, to).sort(), ['manifest.json', 'worlds/world/level.dat', 'worlds/world/region/r.0.0.mca']);
    assert.ok(fs.readFileSync(path.join(to, 'worlds/world/region/r.0.0.mca')).equals(big));
    assert.ok(fs.readFileSync(path.join(to, 'worlds/world/level.dat')).equals(noise));
    // Only what is asked for: an import takes the schematics and leaves the worlds.
    assert.deepStrictEqual(zip.extractZip(file, path.join(d, 'only'), (n) => n === 'manifest.json'), ['manifest.json']);
    assert.ok(!fs.existsSync(path.join(d, 'only', 'worlds')));
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});

test('the chat and join lines design mode listens for, as Paper 1.21.11 logs them', () => {
  const { CHAT, JOINED } = require('../lib/designmode');
  assert.deepStrictEqual(CHAT.exec('[12:00:01 INFO]: [Not Secure] <Builder> check').slice(1), ['Builder', 'check']);
  assert.deepStrictEqual(CHAT.exec('[12:00:01 INFO]: <Builder> export').slice(1), ['Builder', 'export']);
  assert.strictEqual(CHAT.exec('[12:00:01 INFO]: Builder issued server command: /check'), null);
  assert.deepStrictEqual(JOINED.exec('[12:00:01 INFO]: Builder joined the game').slice(1), ['Builder']);
});

test('a save waits for WorldEdit\'s "<name> saved." said later, and takes no other file\'s', async () => {
  const EventEmitter = require('events');
  const make = (later) => {
    const srv = new EventEmitter();
    srv.run = async () => {
      setTimeout(() => { for (const l of later) srv.emit('line', l); }, 5);
      return { lines: [], errors: [] };
    };
    return srv;
  };
  const we = new sch.WorldEdit(make(['[14:30:56 INFO]: CONSOLE saved C:/x/wx_a.schem', '[14:30:56 INFO]: wx_a saved.']));
  assert.match(await we.save('wx_a', 2000), /wx_a saved\./);
  const other = new sch.WorldEdit(make(['[14:30:56 INFO]: xwx_a saved.', '[14:30:56 INFO]: wx_ab saved.']));
  await assert.rejects(other.save('wx_a', 300), /no answer/);
});

/** A zip holding one entry, whose name is then replaced byte for byte by `evil` (the same length). */
function zipNamed(d, evil, data = Buffer.from('no')) {
  const safe = 'a'.repeat(Buffer.byteLength(evil));
  const file = path.join(d, `z${Math.random().toString(36).slice(2)}.zip`);
  zip.writeZip(file, [{ name: safe, data }]);
  const buf = fs.readFileSync(file);
  let k;
  while ((k = buf.indexOf(safe)) >= 0) Buffer.from(evil).copy(buf, k);
  fs.writeFileSync(file, buf);
  return file;
}

test('a zip entry named outside its folder is refused before anything is written: .., absolute, drive letter, backslash', () => {
  const d = scratch();
  try {
    for (const evil of ['../evil.txt', '/tmp/evil.txt', 'C:/evil.txt', 'C:evil.txt', '..\\evil.txt', 'a/../../e.txt', 'a//b.txt']) {
      const f = zipNamed(d, evil);
      assert.throws(() => zip.extractZip(f, path.join(d, 'out')), /not a plain relative name/, evil);
      assert.ok(!fs.existsSync(path.join(d, 'out')), `${evil} wrote something`);
      assert.throws(() => zip.writeZip(path.join(d, 'w.zip'), [{ name: evil, data: Buffer.from('x') }]), /not a name/, evil);
    }
    assert.ok(zip.safeName('worlds/world/region/r.0.0.mca') && zip.safeName('gates.schem'));
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});

test('an entry that does not compress is stored and reads back; one that inflates past its declared size is refused', () => {
  const d = scratch();
  try {
    const noise = require('crypto').randomBytes(4096);
    const file = path.join(d, 'stored.zip');
    zip.writeZip(file, [{ name: 'noise.bin', data: noise }]);
    const [e] = zip.readZip(file);
    assert.strictEqual(e.method, 0, 'random bytes were deflated');
    assert.ok(e.read().equals(noise));
    // A small declared size over a big deflated body: a zip bomb's shape.
    const bomb = path.join(d, 'bomb.zip');
    zip.writeZip(bomb, [{ name: 'big.bin', data: Buffer.alloc(1000000, 0) }]);
    const buf = fs.readFileSync(bomb);
    for (const sig of [0x04034b50, 0x02014b50]) {
      const at = buf.indexOf(Buffer.from([sig & 0xff, (sig >> 8) & 0xff, (sig >> 16) & 0xff, sig >>> 24]));
      buf.writeUInt32LE(100, at + (sig === 0x04034b50 ? 22 : 24));
    }
    fs.writeFileSync(bomb, buf);
    assert.throws(() => zip.extractZip(bomb, path.join(d, 'b')), /does not unpack to the 100 bytes it declares/);
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});

/** A raw block entity tag (prismarine-nbt form) for a sign at x y z of grid g, its front line `json`. */
function signAt(g, [x, y, z], json) {
  const tag = {
    Pos: { type: 'intArray', value: [x - g.box.x0, y - g.box.y0, z - g.box.z0] },
    Id: { type: 'string', value: 'minecraft:sign' },
    Data: { type: 'compound', value: { front_text: { type: 'compound', value: { messages: { type: 'list', value: { type: 'string', value: [json, '""', '""', '""'] } } } } } },
  };
  return { index: g.index(x, y, z), tag, data: require('prismarine-nbt').simplify({ type: 'compound', value: tag }).Data };
}

const CLICKY = '{"text":"press","clickEvent":{"action":"run_command","value":"/op Mallory"}}';
const FAR = [25, 0, 22]; // well clear of C0

test('a command block or spawner of the designer\'s is reported, left out of the export, and refused by --schematics', async () => {
  const baseline = c0Baseline();
  baseline.set(24, 0, 20, 'minecraft:command_block[conditional=false,facing=up]'); // the campus's own
  const current = copyOf(baseline);
  current.set(...FAR, 'minecraft:command_block[conditional=false,facing=up]');
  current.set(23, 0, 22, 'minecraft:spawner');
  const items = design.compareArea({ area: C0_AREA, current, baseline, protect: PROTECT, skinList: SKINS });
  assert.deepStrictEqual(items.map((p) => [p.kind, p.block]).sort(), [['forbidden', 'minecraft:command_block'], ['forbidden', 'minecraft:spawner']]);
  const out = design.exportArea({ current, baseline, protect: PROTECT, skinList: SKINS }).grid;
  assert.strictEqual(out.name(...FAR), 'minecraft:air');
  assert.strictEqual(out.name(23, 0, 22), 'minecraft:air');
  assert.strictEqual(out.name(24, 0, 20), design.MASK, 'the campus\'s command block is left to the campus');
  assert.deepStrictEqual(design.pasteProblems(out, PROTECT, SKINS, 'x'), []);
  const raw = copyOf(current);
  for (let i = 0; i < raw.ids.length; i++) if (out.names[out.ids[i]] === design.MASK) raw.ids[i] = raw.idOf(design.MASK);
  assert.ok(design.pasteProblems(raw, PROTECT, SKINS, 'x').some((p) => /blocks no design may hold \(minecraft:command_block, minecraft:spawner\)/.test(p)));
});

test('a sign whose text runs a command is reported, exported without its click event, and refused by --schematics with it', async () => {
  const baseline = c0Baseline();
  const current = copyOf(baseline);
  current.set(...FAR, 'minecraft:oak_sign[rotation=0,waterlogged=false]');
  current.blockEntities.push(signAt(current, FAR, CLICKY));
  const items = design.compareArea({ area: C0_AREA, current, baseline, protect: PROTECT, skinList: SKINS });
  assert.deepStrictEqual(items.map((p) => [p.kind, p.x, p.y, p.z]), [['click', ...FAR]]);
  const out = design.exportArea({ current, baseline, protect: PROTECT, skinList: SKINS });
  assert.strictEqual(out.scrubbed, 1);
  const d = scratch();
  try {
    design.writeGrid(out.grid, path.join(d, 's.schem'));
    const back = await design.readGrid(path.join(d, 's.schem'), { dim: O, at: { x: 0, y: -6, z: 0 } });
    assert.strictEqual(back.blockEntities.length, 1);
    assert.deepStrictEqual(JSON.parse(back.blockEntities[0].data.front_text.messages[0]), { text: 'press' }, 'the words stay, the command goes');
    assert.deepStrictEqual(design.pasteProblems(back, PROTECT, SKINS, 's'), []);
    const raw = copyOf(current);
    raw.ids = Uint32Array.from(out.grid.ids.map((k) => raw.idOf(out.grid.palette[k])));
    raw.blockEntities = current.blockEntities;
    assert.ok(design.pasteProblems(raw, PROTECT, SKINS, 's').some((p) => /click events/.test(p)));
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});

test('click events come out of JSON text, SNBT-style compounds and nested items alike', () => {
  assert.deepStrictEqual(JSON.parse(design.scrubJson(CLICKY)), { text: 'press' });
  assert.deepStrictEqual(JSON.parse(design.scrubJson('{"text":"","extra":[{"text":"a","click_event":{"action":"run_command","command":"/stop"}}]}')), { text: '', extra: [{ text: 'a' }] });
  assert.strictEqual(design.scrubJson('not json clickEvent'), '');
  const tag = { type: 'compound', value: { Item: { type: 'compound', value: { components: { type: 'compound', value: { pages: { type: 'list', value: { type: 'compound', value: [{ text: { type: 'string', value: 'p' }, click_event: { type: 'compound', value: {} } }] } } } } } } } };
  assert.strictEqual(design.scrubTag(tag), 1);
  assert.ok(!design.hasClick(tag));
});

test('an armour stand keeps only whitelisted data: no riders, no tags, no click events', () => {
  const raw = {
    Id: { type: 'string', value: 'minecraft:armor_stand' },
    Pos: { type: 'list', value: { type: 'double', value: [1.5, 0, 1.5] } },
    Data: { type: 'compound', value: {
      ShowArms: { type: 'byte', value: 1 },
      Tags: { type: 'list', value: { type: 'string', value: ['wx_board'] } },
      Passengers: { type: 'list', value: { type: 'compound', value: [{ id: { type: 'string', value: 'minecraft:command_block_minecart' } }] } },
      CustomName: { type: 'string', value: CLICKY },
    } },
  };
  const out = design.sanitizeEntity(raw);
  assert.deepStrictEqual(Object.keys(out.Data.value).sort(), ['CustomName', 'ShowArms']);
  assert.ok(!design.hasClick(out));
  const nbtLib = require('prismarine-nbt');
  const g = design.Grid.make({ box: C0_AREA.box });
  g.entities = [{ id: 'minecraft:armor_stand', pos: [26.5, 0, 22.5], tag: raw, data: nbtLib.simplify({ type: 'compound', value: raw }).Data }];
  assert.ok(design.pasteProblems(g, PROTECT, SKINS, 'e').some((p) => /Tags, Passengers, a click event/.test(p)), design.pasteProblems(g, PROTECT, SKINS, 'e').join('\n'));
  const items = design.compareArea({ area: C0_AREA, current: Object.assign(copyOf(c0Baseline()), { entities: g.entities }), baseline: c0Baseline(), protect: PROTECT, skinList: SKINS });
  assert.deepStrictEqual(items.map((p) => p.kind), ['entity-data']);
});

test('check: in a skin, a copper bulb, a door, water and fire are active parts; a rotated or retexted campus part and a removed campus block are problems', () => {
  const baseline = c0Baseline();
  baseline.set(19, 2, 8, 'minecraft:oak_wall_sign[facing=east,waterlogged=false]');
  baseline.blockEntities.push(signAt(baseline, [19, 2, 8], '{"text":"C0"}'));
  baseline.set(19, -1, 12, 'minecraft:smooth_quartz');
  const current = copyOf(baseline);
  current.set(19, 0, 10, 'minecraft:copper_bulb[lit=false,powered=false]');
  current.set(19, 0, 11, 'minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]');
  current.set(19, 0, 12, 'minecraft:water[level=0]');
  current.set(19, 0, 13, 'minecraft:fire[age=0,east=false,north=false,south=false,up=false,west=false]');
  current.set(...CAMPUS_LEVER, 'minecraft:lever[face=wall,facing=north,powered=false]'); // turned
  current.blockEntities = [signAt(current, [19, 2, 8], '{"text":"Gift shop"}')]; // retexted
  current.set(19, -1, 12, 'minecraft:air'); // the floor under it taken away
  const items = design.compareArea({ area: C0_AREA, current, baseline, protect: PROTECT, skinList: SKINS });
  const at = items.map((p) => `${p.kind} ${p.x} ${p.y} ${p.z}`).sort();
  assert.deepStrictEqual(at, ['skin 19 -1 12', 'skin 19 0 10', 'skin 19 0 11', 'skin 19 0 12', 'skin 19 0 13', 'skin 19 1 8', 'skin 19 2 8'].sort());
});

test('check and export ignore dropped items and falling blocks', () => {
  const baseline = c0Baseline();
  const current = copyOf(baseline);
  current.entities = [{ id: 'minecraft:item', pos: [26.5, 0, 22.5] }, { id: 'minecraft:falling_block', pos: [SKIN_LEVER[0] + 0.5, 1, SKIN_LEVER[2] + 0.5] }];
  assert.deepStrictEqual(design.compareArea({ area: C0_AREA, current, baseline, protect: PROTECT, skinList: SKINS }), []);
  assert.strictEqual(design.exportArea({ current, baseline, protect: PROTECT, skinList: SKINS }).entities, 0);
});

test('design marks and masks every supported version from 1.21.11 on, the union of their protected boxes', () => {
  assert.deepStrictEqual(design.maskVersions(), require('../lib/version').SUPPORTED.filter((v) => v !== '1.20.4'));
  assert.deepStrictEqual(design.maskVersions(['1.20.4', '1.21.11', '1.21.20', '26.1.2']), ['1.21.11', '1.21.20', '26.1.2']);
  const a = { what: 'a', dim: O, box: bp.box3(0, 0, 0, 1, 1, 1), kind: 'volume' };
  const b = { what: 'b', dim: O, box: bp.box3(5, 0, 5, 6, 1, 6), kind: 'fixture' };
  const boxesOf = (v) => (v === '26.1.2' ? [a, b] : [a]);
  assert.deepStrictEqual(design.protectedFor(['1.21.11', '26.1.2'], boxesOf).map((k) => k.what), ['a', 'b']);
  // What the export masks: b only exists on 26.1.2, and is masked all the same.
  const g = design.Grid.make({ box: bp.box3(0, 0, 0, 7, 1, 7), blocks: [{ x: 5, y: 0, z: 5, block: 'minecraft:stone' }] });
  const out = design.exportArea({ current: g, baseline: g, protect: design.protectedFor(['1.21.11', '26.1.2'], boxesOf), skinList: [] }).grid;
  assert.strictEqual(out.name(5, 0, 5), design.MASK);
  const only = design.exportArea({ current: g, baseline: g, protect: design.protectedFor(['1.21.11'], boxesOf), skinList: [] }).grid;
  assert.strictEqual(only.name(5, 0, 5), 'minecraft:stone');
});

test('a design schematic is refused if its palette repeats or skips an index, or it is Sponge version 2', async () => {
  assert.throws(() => design.readPalette({ 'minecraft:air': 0, 'minecraft:stone': 0 }, 'x.schem'), /missing, repeated or out of range/);
  assert.throws(() => design.readPalette({ 'minecraft:air': 0, 'minecraft:stone': 2 }, 'x.schem'), /missing, repeated or out of range/);
  assert.deepStrictEqual(design.readPalette({ 'minecraft:stone': 1, 'minecraft:air': 0 }, 'x.schem'), ['minecraft:air', 'minecraft:stone']);
  const d = scratch();
  try {
    sch.writeSchem(path.join(d, 'v2.schem'), { size: [1, 1, 1], dataVersion: 3700, blocks: [] });
    await assert.rejects(design.readGrid(path.join(d, 'v2.schem'), { at: { x: 0, y: 0, z: 0 } }), /version 2: a design schematic is version 3/);
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});

test('design chat: only an op is heard, one check at a time, stop ends it, and an op who joins is put in creative', async () => {
  const EventEmitter = require('events');
  const { DesignMode } = require('../lib/designmode');
  const d = scratch();
  try {
    fs.writeFileSync(path.join(d, 'ops.json'), JSON.stringify([{ name: 'Builder', level: 4 }]));
    const srv = new EventEmitter();
    srv.sent = [];
    srv.run = async (c) => { srv.sent.push(c); return { lines: [], errors: [] }; };
    const dm = new DesignMode({ srv, folder: d, repo: d, local: d, log: () => {} });
    let release;
    const ran = [];
    dm.run = (what, who) => { ran.push(`${who} ${what}`); return new Promise((r) => { release = r; }); };
    const stopped = [];
    const off = dm.listen({ onStop: (who) => stopped.push(who) });
    const say = (who, words) => srv.emit('line', `[12:00:00 INFO]: [Not Secure] <${who}> ${words}`);
    say('Visitor', 'check');
    say('Builder', 'hello');
    say('Builder', 'Check!');
    say('Builder', 'export');
    assert.deepStrictEqual(ran, ['Builder check'], 'a non-op was heard, or a second job started while one ran');
    assert.ok(srv.sent.some((c) => /tellraw Builder .*Busy with check/.test(c)));
    release();
    await dm.running;
    say('Builder', 'export  full');
    assert.deepStrictEqual(ran, ['Builder check', 'Builder export full']);
    release();
    await dm.running;
    say('Visitor', 'stop');
    say('Builder', 'stop');
    assert.deepStrictEqual(stopped, ['Builder']);
    srv.emit('line', '[12:00:00 INFO]: Builder joined the game');
    srv.emit('line', '[12:00:00 INFO]: Visitor joined the game');
    assert.ok(srv.sent.includes('gamemode creative Builder') && !srv.sent.includes('gamemode creative Visitor'));
    off();
    assert.strictEqual(srv.listenerCount('line'), 0);
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
});
