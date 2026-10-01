'use strict';
// lib/viewer.js and lib/schematics.js without a server: the versions the viewer refuses, its
// port, the block-state translation into its assets' numbering, and a schematic's placed box
// against the decoration guardrail. Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const mcData = require('minecraft-data');
const viewer = require('../lib/viewer');
const sch = require('../lib/schematics');

test('the viewer draws 1.20.4 and 1.21.11 with its nearest assets and refuses 26.1.2 by name', () => {
  assert.strictEqual(viewer.assetVersion('1.20.4'), '1.20.1');
  assert.strictEqual(viewer.assetVersion('1.21.11'), '1.21.4');
  assert.throws(() => viewer.assetVersion('26.1.2'), /cannot draw Minecraft 26\.1\.2/);
});

test('the viewer port follows the game port like Dynmap\'s and refuses one below 1024', () => {
  assert.strictEqual(viewer.viewerPort(25590), 3007);
  assert.strictEqual(viewer.viewerPort(25780), 3197);
  assert.throws(() => viewer.viewerPort(23000), /would be 417/);
});

test('a block state keeps its name and properties across the translation to older assets', () => {
  const { map } = viewer.stateMap('1.21.11', '1.21.4');
  const src = mcData('1.21.11').blocksByName;
  const dst = mcData('1.21.4').blocksByName;
  // An oak stair facing east, top half, outer_left, waterlogged: four properties, all kept.
  const props = { facing: 'east', half: 'top', shape: 'outer_left', waterlogged: 'true' };
  const from = viewer.encode(src.oak_stairs, props);
  assert.notStrictEqual(from, viewer.encode(dst.oak_stairs, props), 'the two versions number oak stairs alike, so this proves nothing');
  assert.deepStrictEqual(viewer.decode(dst.oak_stairs, map[from]), props);
  // And the numbering is the game's (prismarine-block's), not merely consistent with itself.
  const Block = require('prismarine-block')('1.21.4');
  assert.strictEqual(map[from], Block.fromProperties('oak_stairs', { facing: 'east', half: 'top', shape: 'outer_left', waterlogged: true }, 0).stateId);
  // A block the older assets lack is drawn as stone.
  assert.strictEqual(map[src.acacia_shelf.defaultState], dst.stone.defaultState);
});

test('the same version translates to itself, state for state', () => {
  const { map } = viewer.stateMap('1.21.4', '1.21.4');
  const moved = map.findIndex((v, i) => v !== i);
  assert.strictEqual(moved, -1, `state ${moved} moved`);
});

/** A scratch folder holding an L of three blocks, 3 x 1 x 2, as l.schem; removed after `fn`. */
async function withSchem(fn) {
  const d = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-schem-'));
  try {
    sch.writeSchem(path.join(d, 'l.schem'), {
      size: [3, 1, 2], dataVersion: mcData('1.20.4').version.dataVersion,
      blocks: [{ x: 0, y: 0, z: 0, block: 'minecraft:red_wool' }, { x: 2, y: 0, z: 0, block: 'minecraft:blue_wool' }, { x: 0, y: 0, z: 1, block: 'minecraft:lime_wool' }],
    });
    await fn(d);
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
}

test('a schematic written here reads back with its size and its corner at the origin', () => withSchem(async (d) => {
  const info = await sch.readSchem(path.join(d, 'l.schem'));
  assert.deepStrictEqual(info, { version: 2, size: [3, 1, 2], min: [0, 0, 0] });
}));

test('a placed box turns clockwise with //rotate, as WorldEdit 7.4.5 pasted it', () => withSchem(async (d) => {
  const info = await sch.readSchem(path.join(d, 'l.schem'));
  // In game: //rotate 90 then //paste at 20,0,20 put the block from (2, 0, 0) at (20, 0, 22).
  assert.deepStrictEqual(sch.placedBox(info, { x: 20, y: 0, z: 20 }, 90), { x0: 19, y0: 0, z0: 20, x1: 20, y1: 0, z1: 22 });
  assert.deepStrictEqual(sch.placedBox(info, { x: 20, y: 0, z: 20 }, 0), { x0: 20, y0: 0, z0: 20, x1: 22, y1: 0, z1: 21 });
}));

test('the guardrail refuses a schematic in a cell and passes one on open floor', () => withSchem(async (d) => {
  fs.writeFileSync(path.join(d, 'placements.json'), JSON.stringify([
    { file: 'l.schem', at: { x: 14, y: 0, z: 10 } }, // inside C0, the Calibration Cell
    { file: 'l.schem', at: { x: 55, y: 0, z: -50 }, rotation: 180 }, // the Gate hall's floor
  ]));
  const { placed, problems } = await sch.check(sch.placements(d), '1.21.11');
  assert.strictEqual(placed.length, 2);
  assert.ok(problems.length > 0 && problems.every((p) => p.startsWith('l.schem at 14 0 10..16 0 11 ')), problems.join('\n'));
  assert.ok(problems.some((p) => /c0's clear volume/.test(p)), problems.join('\n'));
}));

test('a placement that is not a plain .schem name, or turns by an odd angle, is refused', () => withSchem(async (d) => {
  for (const [bad, why] of [[{ file: '../l.schem', at: { x: 0, y: 0, z: 0 } }, /plain name/], [{ file: 'l.schem', at: { x: 0, y: 0, z: 0 }, rotation: 45 }, /rotation/]]) {
    fs.writeFileSync(path.join(d, 'placements.json'), JSON.stringify([bad]));
    assert.throws(() => sch.placements(d), why);
  }
}));
