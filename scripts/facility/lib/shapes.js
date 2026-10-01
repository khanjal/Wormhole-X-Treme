'use strict';
// Gate shapes, read from the plugin's own shape files (src/main/resources/shapes/gate) and the
// facility's test shape (assets/Lab.shape), and where each part of a gate lands in the world for
// a build from a holder and a facing.
//
// From the plugin's source (GateGrid, StargateShapeLayer, StargateHelper, WorldUtils):
//  - the console `gate build ... x y z facing` coordinate is the `:A` block the DHD button
//    hangs on (the holder); the button is at holder + F, and F is the way it faces;
//  - layer n sits (n - A's layer) blocks along F from the holder; row 0 is the bottom line of a
//    layer; column = width - 1 - index, and R (right, facing F) runs with the column;
//  - a player arrives at the far gate's :EP cell + 1F, feet one above the cell, facing F;
//    mounts, items, mobs and projectiles one further out and one higher (+1F +1U from that);
//    carts and boats at :EM (or :EP) + 1F + 0.5 up.

const fs = require('fs');
const path = require('path');

const SHAPE_DIR = path.resolve(__dirname, '..', '..', '..', 'src', 'main', 'resources', 'shapes', 'gate');
// Test shapes: never shipped, installed into a test server's shapes/gate by the launcher.
const ASSET_DIR = path.resolve(__dirname, '..', 'assets');
const TEST_SHAPES = ['Lab'];

/** The file a shape is read from: the plugin's, or the facility's test asset. */
function shapeFile(name) {
  const own = path.join(SHAPE_DIR, `${name}.shape`);
  return fs.existsSync(own) ? own : path.join(ASSET_DIR, `${name}.shape`);
}

/** Copies the test shapes into a server folder's plugins/WormholeXTreme/shapes/gate. */
function installTestShapes(folder) {
  const dir = path.join(folder, 'plugins', 'WormholeXTreme', 'shapes', 'gate');
  fs.mkdirSync(dir, { recursive: true });
  for (const name of TEST_SHAPES) fs.copyFileSync(path.join(ASSET_DIR, `${name}.shape`), path.join(dir, `${name}.shape`));
}

const FACINGS = {
  north: { f: [0, -1], r: [1, 0], yaw: 180 },
  east: { f: [1, 0], r: [0, 1], yaw: -90 },
  south: { f: [0, 1], r: [-1, 0], yaw: 0 },
  west: { f: [-1, 0], r: [0, -1], yaw: 90 },
};

/**
 * Parses a shape file into cells: [{ layer, row, col, codes: ['S','L#3',...] }], and its
 * material keys (STARGATE_MATERIAL, CHEVRON_MATERIAL, ...) as `keys`.
 */
function parse(name) {
  const text = fs.readFileSync(shapeFile(name), 'utf8').replace(/\r/g, '');
  const layers = [];
  const keys = {};
  let current = null;
  let width = 0;
  for (const line of text.split('\n')) {
    const key = /^([A-Z_]+)\s*=\s*([^;#]*?)\s*;?\s*$/.exec(line);
    if (key && !line.startsWith('Layer')) keys[key[1]] = key[2];
    const head = /^Layer#(\d+)=\s*$/.exec(line);
    if (head) { current = { n: Number(head[1]), lines: [] }; layers.push(current); continue; }
    if (current && /^\[/.test(line.trim())) current.lines.push(line.trim());
    else if (current && line.trim() === '') current = current.lines.length ? null : current;
  }
  const height = layers[0].lines.length;
  const cells = [];
  for (const layer of layers) {
    layer.lines.forEach((l, i) => {
      const codes = [...l.matchAll(/\[([^\]]*)\]/g)].map((m) => m[1].split(':'));
      width = Math.max(width, codes.length);
      codes.forEach((c, j) => cells.push({ layer: layer.n, row: height - 1 - i, col: codes.length - 1 - j, codes: c }));
    });
  }
  return { name, width, height, layers: layers.length, cells, keys };
}

const cache = {};
function shape(name) {
  if (!cache[name]) cache[name] = parse(name);
  return cache[name];
}

/** Shapes the console can build: every shipped one with a DHD button and no dial sign. */
function consoleBuildable(name) {
  const s = shape(name);
  return s.cells.some((c) => c.codes.includes('A')) && !s.cells.some((c) => c.codes.includes('D'));
}

/** A gate lying in the floor: every block and opening cell in the bottom row (redstone may stand on it). */
function isFlat(name) {
  return shape(name).cells.filter((c) => ['S', 'C', 'P'].includes(c.codes[0])).every((c) => c.row === 0);
}

/**
 * Where everything is for a gate of `name` built with its holder at `h` ({x,y,z}) facing `facing`.
 * Offsets are relative to the holder; world positions are block coordinates.
 */
function geometry(name, h, facing) {
  const s = shape(name);
  const F = FACINGS[facing];
  const a = s.cells.find((c) => c.codes.includes('A'));
  const off = (c) => ({ F: c.layer - a.layer, R: c.col - a.col, U: c.row - a.row });
  const world = ({ F: df, R: dr, U: du }) => ({
    x: h.x + df * F.f[0] + dr * F.r[0],
    y: h.y + du,
    z: h.z + df * F.f[1] + dr * F.r[1],
  });
  const find = (code) => s.cells.find((c) => c.codes.includes(code));
  const ep = off(find('EP'));
  const emCell = find('EM');
  const em = emCell ? off(emCell) : ep;
  const opening = s.cells.filter((c) => c.codes[0] === 'P').map((c) => world(off(c)));
  // The blocks a builder lays: frame ([S...]) and chevron ([C...]) cells. A bare [RD], [RA] or
  // [RS] is not one (redstone goes in that cell), nor is [I] or [P].
  const isBlock = (c) => c.codes[0] === 'S' || c.codes[0] === 'C';
  const roleOf = (c) => {
    if (c.codes[0] === 'C') return 'chevron';
    if (c.codes.includes('C')) return 'either';
    return 'frame';
  };
  const blocks = s.cells.filter(isBlock).map((c) => ({
    ...world(off(c)), role: roleOf(c), lit: c.codes.some((x) => /^L(#\d+)?$/.test(x)),
    marks: c.codes.slice(1).filter((x) => !/^[LC](#\d+)?$/.test(x) && x !== 'S'),
  }));
  const frame = blocks.map(({ x, y, z }) => ({ x, y, z }));
  // Redstone markers: a bare cell holds the redstone itself; [S:RD] puts it on top of the block.
  const redstone = {};
  for (const code of ['RD', 'RS', 'RA']) {
    const c = s.cells.find((x) => x.codes.includes(code));
    if (c) { const p = world(off(c)); redstone[code] = c.codes[0] === code ? p : { ...p, y: p.y + 1 }; }
  }
  const lights = s.cells.filter((c) => c.codes.some((x) => /^L(#\d+)?$/.test(x)))
    .map((c) => ({ ...world(off(c)), order: Number((c.codes.find((x) => /^L/.test(x)).split('#')[1]) || 1) }))
    .sort((p, q) => p.order - q.order);
  const all = s.cells.map((c) => world(off(c)));
  const bounds = {
    x0: Math.min(...all.map((p) => p.x)), x1: Math.max(...all.map((p) => p.x)),
    y0: Math.min(...all.map((p) => p.y)), y1: Math.max(...all.map((p) => p.y)),
    z0: Math.min(...all.map((p) => p.z)), z1: Math.max(...all.map((p) => p.z)),
  };
  const centre = {
    x: opening.reduce((n, p) => n + p.x, 0) / opening.length + 0.5,
    y: opening.reduce((n, p) => n + p.y, 0) / opening.length + 0.5,
    z: opening.reduce((n, p) => n + p.z, 0) / opening.length + 0.5,
  };
  const epW = world({ ...ep, F: ep.F + 1 });
  const arrival = { x: epW.x + 0.5, y: epW.y + 1, z: epW.z + 0.5, yaw: F.yaw };
  const itemW = world({ ...ep, F: ep.F + 2 });
  const itemArrival = { x: itemW.x + 0.5, y: itemW.y + 2, z: itemW.z + 0.5 };
  const emW = world({ ...em, F: em.F + 1 });
  const cartArrival = { x: emW.x + 0.5, y: emW.y + 0.5, z: emW.z + 0.5 };
  const ia = find('IA');
  const d = find('D');
  return {
    name, facing, holder: h, yaw: F.yaw, normal: { x: F.f[0], z: F.f[1] }, right: { x: F.r[0], z: F.r[1] },
    opening, frame, blocks, redstone, lights, bounds, centre, arrival, itemArrival, cartArrival,
    button: world({ F: 1, R: 0, U: 0 }),
    lever: ia ? world({ ...off(ia), F: off(ia).F + 1 }) : null,
    // A dial-sign shape's sign hangs on the front of its :D block.
    sign: d ? world({ ...off(d), F: off(d).F + 1 }) : null,
    materials: s.keys,
    flat: isFlat(name),
    // How high the holder must sit for a player to arrive with feet on y = 0.
    feetOffset: ep.U + 1,
  };
}

/** The holder y that puts the gate's arrival feet on the floor (y = 0) for a flush build. */
function flushHolderY(name, floorFeetY = 0) {
  return floorFeetY - geometry(name, { x: 0, y: 0, z: 0 }, 'south').feetOffset;
}

module.exports = {
  SHAPE_DIR, ASSET_DIR, TEST_SHAPES, FACINGS, shapeFile, installTestShapes, parse, shape, consoleBuildable, isFlat, geometry, flushHolderY,
};
