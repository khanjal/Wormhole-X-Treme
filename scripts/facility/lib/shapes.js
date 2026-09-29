'use strict';
// Gate shapes, read from the plugin's own shape files (src/main/resources/shapes/gate), and
// where each part of a gate lands in the world for a console build from a holder and a facing.
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

const FACINGS = {
  north: { f: [0, -1], r: [1, 0], yaw: 180 },
  east: { f: [1, 0], r: [0, 1], yaw: -90 },
  south: { f: [0, 1], r: [-1, 0], yaw: 0 },
  west: { f: [-1, 0], r: [0, -1], yaw: 90 },
};

/** Parses a shape file into cells: [{ layer, row, col, codes: ['S','L#3',...] }] and the header. */
function parse(name) {
  const text = fs.readFileSync(path.join(SHAPE_DIR, `${name}.shape`), 'utf8').replace(/\r/g, '');
  const layers = [];
  let current = null;
  let width = 0;
  for (const line of text.split('\n')) {
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
  return { name, width, height, layers: layers.length, cells };
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

function isFlat(name) {
  return shape(name).cells.filter((c) => c.codes[0] !== 'I').every((c) => c.row === 0);
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
  const frame = s.cells.filter((c) => c.codes[0] !== 'I' && c.codes[0] !== 'P').map((c) => world(off(c)));
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
  return {
    name, facing, holder: h, yaw: F.yaw, normal: { x: F.f[0], z: F.f[1] }, right: { x: F.r[0], z: F.r[1] },
    opening, frame, lights, bounds, centre, arrival, itemArrival, cartArrival,
    button: world({ F: 1, R: 0, U: 0 }),
    lever: ia ? world({ ...off(ia), F: off(ia).F + 1 }) : null,
    flat: isFlat(name),
    // How high the holder must sit for a player to arrive with feet on y = 0.
    feetOffset: ep.U + 1,
  };
}

/** The holder y that puts the gate's arrival feet on the floor (y = 0) for a flush build. */
function flushHolderY(name, floorFeetY = 0) {
  return floorFeetY - geometry(name, { x: 0, y: 0, z: 0 }, 'south').feetOffset;
}

module.exports = { SHAPE_DIR, FACINGS, parse, shape, consoleBuildable, isFlat, geometry, flushHolderY };
