'use strict';
// lib/mapreaders.js without a server: BlueMap's, squaremap's and Pl3xMap's marker files read into
// one snapshot. The squaremap and Pl3xMap files under test/fixtures/maps are what those plugins
// wrote on a real run (26.1.2, their own spawn and world-border layers); the Wormhole-shaped
// markers below are built in the same shapes, since no provider drew any yet, and BlueMap's in
// the shape its API serializes (BlueMap draws nothing until accept-download is on).
// Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const r = require('../lib/mapreaders');

const FIX = path.join(__dirname, 'fixtures', 'maps');
const fixture = (f) => JSON.parse(fs.readFileSync(path.join(FIX, f), 'utf8'));

function scratch(fn) {
  const d = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-mapreaders-'));
  return Promise.resolve(fn(d)).finally(() => fs.rmSync(d, { recursive: true, force: true }));
}

const write = (file, json) => {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(json));
};

// A gate MapA at (0.5, 2.5, -110.5): its point, its opening's area and a line to MapB, per map.
const SQUAREMAP_GATES = {
  id: 'wormhole_gates', name: 'Stargates', control: true, hide: false, order: 10, z_index: 10,
  markers: [
    { type: 'icon', point: { x: 0.5, z: -110.5 }, icon: 'wormhole_gate_idle', tooltip: 'MapA', popup: 'Network: Maps<br/>Owner: Probe' },
    { type: 'rectangle', points: [{ x: -1, z: -111 }, { x: 2, z: -110 }], color: '#37b0d8', tooltip: 'MapA', popup: 'Network: Maps<br/>Owner: Probe' },
    { type: 'polyline', points: [{ x: 0.5, z: -110.5 }, { x: 13.5, z: -110.5 }], color: '#37b0d8', tooltip: 'MapA to MapB' },
  ],
};

test('squaremap\'s own file from a real run: its spawn icon and world border, in the overworld', () => {
  const snap = r.parseSquaremap(fixture('squaremap-26.1.2-markers.json'), 'world');
  assert.deepStrictEqual(snap.layers, { 'squaremap-spawn_icon': 'Spawn', 'squaremap-worldborder': 'World Border' });
  const spawn = r.inLayer(snap, 'Spawn', 'points', 'Spawn')[0];
  assert.ok(r.at(spawn, { x: 0, y: 64, z: 0 }), 'flat: any y');
  assert.strictEqual(spawn.icon, 'squaremap-spawn_icon');
  const border = r.inLayer(snap, 'World Border', 'lines')[0];
  assert.strictEqual(border.color, 'ff0000');
  assert.strictEqual(border.bounds.maxX, 29999984);
});

test('Pl3xMap\'s own files from a real run: its layer index and each layer\'s markers', () => {
  const layers = { pl3xmap_spawn: 'pl3xmap-26.1.2-pl3xmap_spawn.json', pl3xmap_worldborder: 'pl3xmap-26.1.2-pl3xmap_worldborder.json', pl3xmap_players: 'pl3xmap-26.1.2-pl3xmap_players.json' };
  const snap = r.parsePl3xmap(fixture('pl3xmap-26.1.2-index.json'), (key) => fixture(layers[key]), 'world');
  assert.deepStrictEqual(snap.layers, { pl3xmap_spawn: 'Spawn', pl3xmap_players: 'Players', pl3xmap_worldborder: 'World Border' });
  const spawn = r.inLayer(snap, 'Spawn', 'points', 'Spawn')[0];
  assert.strictEqual(spawn.id, 'pl3xmap_spawn');
  assert.strictEqual(spawn.icon, 'spawn');
  assert.strictEqual(spawn.world, 'world');
  // Its stroke colour is an ARGB int: -65536 is opaque red.
  assert.strictEqual(r.inLayer(snap, 'World Border', 'lines')[0].color, 'ff0000');
});

test('a gate drawn by squaremap is found by its label and place: point, area over the opening, and line', () => {
  const snap = r.parseSquaremap([SQUAREMAP_GATES], 'world');
  const [point] = r.inLayer(snap, 'Stargates', 'points', 'MapA');
  assert.ok(r.at(point, { x: 0.5, y: 2.5, z: -110.5 }));
  assert.ok(!r.at(point, { x: 13.5, y: 2.5, z: -110.5 }), 'not where MapB stands');
  assert.match(point.icon, /idle/);
  assert.match(point.desc, /Owner: Probe/);
  const [area] = r.inLayer(snap, 'Stargates', 'areas', 'MapA');
  assert.ok(r.covers(area, { x: 0.5, z: -110.5 }));
  assert.ok(!r.covers(area, { x: 13.5, z: -110.5 }));
  assert.strictEqual(area.color, '37b0d8');
  assert.strictEqual(r.inLayer(snap, 'Stargates', 'lines', 'MapA to MapB').length, 1);
});

test('a gate drawn by Pl3xMap: an icon with its key, a polygon over the opening, a polyline between', () => {
  const index = [{ key: 'wormhole_gates', label: 'Stargates' }];
  const markers = [
    { type: 'icon', data: { key: 'mapa', point: { x: 0.5, z: -110.5 }, image: 'wormhole_gate_open' }, options: { tooltip: { content: 'MapA' }, popup: { content: 'Owner: Probe' } } },
    { type: 'polygon', data: { key: 'mapa_area', polylines: [{ key: 'p', points: [{ x: -1, z: -111 }, { x: 2, z: -111 }, { x: 2, z: -110 }, { x: -1, z: -110 }] }] }, options: { stroke: { color: 0xff37b0d8 | 0 }, tooltip: { content: 'MapA' } } },
    { type: 'polyline', data: { key: 'mapa|mapb', lines: [{ points: [{ x: 0.5, z: -110.5 }, { x: 13.5, z: -110.5 }] }] }, options: { stroke: { color: 0xff37b0d8 | 0 }, tooltip: { content: 'MapA to MapB' } } },
  ];
  const snap = r.parsePl3xmap(index, () => markers, 'world_nether');
  const [point] = r.inLayer(snap, 'Stargates', 'points', 'MapA');
  assert.strictEqual(point.id, 'mapa');
  assert.strictEqual(point.world, 'world_nether');
  assert.match(point.icon, /open/);
  assert.ok(r.covers(r.inLayer(snap, 'Stargates', 'areas', 'MapA')[0], { x: 0.5, z: -110.5 }));
  const [line] = r.inLayer(snap, 'Stargates', 'lines', 'MapA to MapB');
  assert.strictEqual(line.id, 'mapa|mapb');
  assert.strictEqual(line.color, '37b0d8');
  assert.strictEqual(line.points.length, 2);
});

test('a gate drawn by BlueMap: a poi with its height, a shape, a line, colours as { r, g, b, a }', () => {
  const json = {
    'wormhole.gates': {
      label: 'Stargates', toggleable: true, defaultHidden: false, sorting: 0,
      markers: {
        mapa: { type: 'poi', label: 'MapA', position: { x: 0.5, y: 2.5, z: -110.5 }, detail: 'Owner: Probe', icon: 'assets/wormhole_gate_idle.png', anchor: { x: 8, y: 8 } },
        'mapa.area': { type: 'shape', label: 'MapA', position: { x: 0.5, y: 2.5, z: -110.5 }, shape: [{ x: -1, z: -111 }, { x: 2, z: -111 }, { x: 2, z: -110 }], shapeY: 1, lineColor: { r: 55, g: 176, b: 216, a: 1 } },
        'mapa|mapb': { type: 'line', label: 'MapA to MapB', position: { x: 0.5, y: 2.5, z: -110.5 }, line: [{ x: 0.5, y: 2.5, z: -110.5 }, { x: 13.5, y: 2.5, z: -110.5 }], lineColor: { r: 55, g: 176, b: 216, a: 1 } },
      },
    },
  };
  const snap = r.parseBluemap(json, 'world');
  const [point] = r.inLayer(snap, 'Stargates', 'points', 'MapA');
  assert.strictEqual(point.icon, 'wormhole_gate_idle');
  assert.ok(r.at(point, { x: 0.5, y: 2.5, z: -110.5 }));
  assert.ok(!r.at(point, { x: 0.5, y: 9, z: -110.5 }), 'BlueMap has a height, and it is checked');
  assert.strictEqual(r.inLayer(snap, 'Stargates', 'areas', 'MapA')[0].color, '37b0d8');
  assert.strictEqual(r.inLayer(snap, 'Stargates', 'lines', 'MapA to MapB')[0].id, 'mapa|mapb');
});

test('a reader finds each map\'s files in a server folder, every world, by the Bukkit world name', () => scratch(async (d) => {
  // squaremap: the dimension key with ':' as '_'.
  write(path.join(d, 'plugins', 'squaremap', 'web', 'tiles', 'minecraft_the_nether', 'markers.json'), [SQUAREMAP_GATES]);
  write(path.join(d, 'plugins', 'squaremap', 'web', 'tiles', 'minecraft_overworld', 'markers.json'), fixture('squaremap-26.1.2-markers.json'));
  const sq = await new r.JsonMapReader('squaremap', d).snapshot();
  assert.strictEqual(r.inLayer(sq, 'Stargates', 'points', 'MapA')[0].world, 'world_nether');
  assert.strictEqual(r.inLayer(sq, 'Spawn', 'points', 'Spawn')[0].world, 'world');
  // Pl3xMap: the Bukkit world name, and each layer in markers/<key>.json.
  write(path.join(d, 'plugins', 'Pl3xMap', 'web', 'tiles', 'world', 'markers.json'), fixture('pl3xmap-26.1.2-index.json'));
  write(path.join(d, 'plugins', 'Pl3xMap', 'web', 'tiles', 'world', 'markers', 'pl3xmap_spawn.json'), fixture('pl3xmap-26.1.2-pl3xmap_spawn.json'));
  const pl = await new r.JsonMapReader('pl3xmap', d).snapshot();
  assert.strictEqual(r.inLayer(pl, 'Spawn', 'points', 'Spawn')[0].world, 'world');
  // BlueMap: on 26.x its map called world is the nether (its configs, from a real run), so the
  // world comes from each map's dimension, never its id; its port unset, the files on disk.
  for (const f of fs.readdirSync(path.join(FIX, 'bluemap-26.1.2-maps'))) {
    fs.mkdirSync(path.join(d, 'plugins', 'BlueMap', 'maps'), { recursive: true });
    fs.copyFileSync(path.join(FIX, 'bluemap-26.1.2-maps', f), path.join(d, 'plugins', 'BlueMap', 'maps', f));
  }
  assert.deepStrictEqual(r.bluemapMaps(d).sort((a, b) => a.id.localeCompare(b.id)),
    [{ id: 'overworld', world: 'world' }, { id: 'world', world: 'world_nether' }, { id: 'world_the_end', world: 'world_the_end' }]);
  write(path.join(d, 'bluemap', 'web', 'maps', 'world', 'live', 'markers.json'), { 'wormhole.gates': { label: 'Stargates', markers: { mapa: { type: 'poi', label: 'MapA', position: { x: 1, y: 2, z: 3 } } } } });
  const bm = await new r.JsonMapReader('bluemap', d).snapshot();
  assert.strictEqual(r.inLayer(bm, 'Stargates', 'points', 'MapA')[0].world, 'world_nether');
}));

test('a reader with no files yet reads an empty map, not an error', () => scratch(async (d) => {
  for (const name of ['bluemap', 'squaremap', 'pl3xmap']) {
    assert.deepStrictEqual(await new r.JsonMapReader(name, d).snapshot(), { layers: {}, points: [], areas: [], lines: [] }, name);
  }
}));
