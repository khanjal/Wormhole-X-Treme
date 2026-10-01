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
  // Things that are fine: a placeholder broken to air, decoration well clear, the campus's own lever flipped.
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
    zip.writeZip(path.join(d, 'evil.zip'), [{ name: '../evil.txt', data: Buffer.from('no') }]);
    assert.throws(() => zip.extractZip(path.join(d, 'evil.zip'), path.join(d, 'y')), /outside/);
    assert.ok(!fs.existsSync(path.join(d, 'evil.txt')));
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
