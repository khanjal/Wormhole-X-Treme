'use strict';
// Every coordinate of the facility, in one place. Move a wing by changing its room here and
// regenerating; the wing builders in ../wings/ and the chambers derive everything from this.
//
// Conventions (design section 2.2): x runs east, z runs south, y is feet level. The overworld is
// a flat world whose top layer, smooth quartz, is at y = -1, so a room's floor is the world's.
// Boxes are inclusive interiors: { x0, x1, z0, z1, y0, h } is the air a person walks in, from
// y0 to y0 + h - 1; walls, floor and roof are one block outside it.

const OVERWORLD = 'minecraft:overworld';
const NETHER = 'minecraft:the_nether';
const END = 'minecraft:the_end';

// The flat world's layers, bottom up; the quartz layer ends at y = -1.
const FLAT_LAYERS = [
  { block: 'minecraft:bedrock', height: 1 },
  { block: 'minecraft:deepslate', height: 31 },
  { block: 'minecraft:stone', height: 31 },
  { block: 'minecraft:smooth_quartz', height: 1 },
];

const PALETTE = {
  wall: 'minecraft:white_concrete',
  trim: 'minecraft:light_gray_concrete',
  skirting: 'minecraft:polished_deepslate',
  floor: 'minecraft:smooth_quartz',
  roof: 'minecraft:glass',
  window: 'minecraft:tinted_glass',
  cellWall: 'minecraft:white_concrete',
  cellGlass: 'minecraft:tinted_glass',
  gallery: 'minecraft:polished_deepslate',
  rail: 'minecraft:iron_bars',
  guide: 'minecraft:sea_lantern',
  console: 'minecraft:waxed_copper_block',
  hazardA: 'minecraft:yellow_concrete',
  hazardB: 'minecraft:black_concrete',
  pylonBase: 'minecraft:polished_deepslate',
  sentinel: 'minecraft:lodestone',
};

/**
 * The wings. `room` is the main hall; `entrance` is where a tp plate or `!go` puts you, inside
 * the wing's door, facing in. `colour` is the concrete/text colour of the department.
 */
const WINGS = [
  {
    id: 'ops', title: 'Operations', colour: 'white', text: 'white', dim: OVERWORLD,
    room: { x0: -20, x1: 20, z0: -20, z1: 20, y0: 0, h: 16 },
    entrance: { x: 0.5, y: 0, z: 10.5, yaw: 180 },
  },
  {
    id: 'gates', title: 'Gate Dynamics', colour: 'cyan', text: 'dark_aqua', dim: OVERWORLD,
    room: { x0: -70, x1: 70, z0: -170, z1: -40, y0: 0, h: 36 },
    entrance: { x: 0.5, y: 0, z: -43.5, yaw: 180 },
  },
  {
    id: 'rings', title: 'Ring Transit', colour: 'lime', text: 'green', dim: OVERWORLD,
    room: { x0: 40, x1: 110, z0: -30, z1: 30, y0: 0, h: 14 },
    entrance: { x: 43.5, y: 0, z: 0.5, yaw: -90 },
  },
  {
    id: 'beams', title: 'Beam Physics', colour: 'light_blue', text: 'aqua', dim: OVERWORLD,
    room: { x0: -110, x1: -40, z0: -30, z1: 30, y0: 0, h: 12 },
    entrance: { x: -42.5, y: 0, z: 0.5, yaw: 90 },
  },
  {
    id: 'mirrors', title: 'Mirror Optics', colour: 'magenta', text: 'light_purple', dim: OVERWORLD,
    room: { x0: -40, x1: 40, z0: 40, z1: 90, y0: 0, h: 12 },
    entrance: { x: 0.5, y: 0, z: 43.5, yaw: 0 },
  },
  {
    id: 'menagerie', title: 'Menagerie and Motor Pool', colour: 'orange', text: 'gold', dim: OVERWORLD,
    room: { x0: 80, x1: 130, z0: -120, z1: -60, y0: 0, h: 10 },
    entrance: { x: 81.5, y: 0, z: -95.5, yaw: -90 },
  },
  {
    id: 'systems', title: 'Systems', colour: 'yellow', text: 'yellow', dim: OVERWORLD,
    // The mezzanine over Ops, not a room of its own: a gallery 6 above the atrium floor.
    room: null,
    entrance: { x: 0.5, y: 6, z: -16.5, yaw: 0 },
  },
  {
    id: 'range', title: 'The Range', colour: 'red', text: 'red', dim: NETHER,
    room: { x0: -30, x1: 30, z0: -30, z1: 30, y0: 64, h: 20 }, shell: 'dome',
    entrance: { x: 0.5, y: 64, z: 8.5, yaw: 180 },
  },
  {
    id: 'annex', title: 'The Annex', colour: 'purple', text: 'dark_purple', dim: END,
    room: { x0: 980, x1: 1020, z0: 980, z1: 1020, y0: 60, h: 20 }, shell: 'open',
    entrance: { x: 1000.5, y: 60, z: 1008.5, yaw: 180 },
  },
];

/** Corridors between rooms: a 9-wide passage, 5 high, with a doorway through each end wall. */
const CORRIDORS = [
  { id: 'ops-gates', box: { x0: -4, x1: 4, z0: -38, z1: -22, y0: 0, h: 5 }, axis: 'z' },
  { id: 'ops-rings', box: { x0: 22, x1: 38, z0: -4, z1: 4, y0: 0, h: 5 }, axis: 'x' },
  { id: 'ops-beams', box: { x0: -38, x1: -22, z0: -4, z1: 4, y0: 0, h: 5 }, axis: 'x' },
  { id: 'ops-mirrors', box: { x0: -4, x1: 4, z0: 22, z1: 38, y0: 0, h: 5 }, axis: 'z' },
  // Wide enough for the three lanes (canal, rails, run-up) that run from the Motor Pool to G1.
  { id: 'gates-menagerie', box: { x0: 72, x1: 78, z0: -100, z1: -92, y0: 0, h: 5 }, axis: 'x', doorHalf: 4 },
];

/**
 * Lanes from the Motor Pool to the G1 stand, as structure only: a canal one wide and one deep,
 * a rail line, and a striped run-up. `x0..x1` at a fixed z.
 */
const LANES = [
  { id: 'canal', kind: 'water', z: -99, x0: 0, x1: 118 },
  { id: 'rails', kind: 'rail', z: -97, x0: 0, x1: 118 },
  { id: 'run-up', kind: 'stripe', z0: -95, z1: -93, x0: 0, x1: 78 },
];

/**
 * The chambers, in console order. A `cell` has a box (its interior), the side its door is on
 * and the side its gallery looks in from; board, pylon, door and seat positions are derived
 * from those (see lib/blueprint.js cellLayout). A `desk` is a console with no cell: a board and
 * a pylon at `at`. `logic` names the module in ../chambers/ once a chamber has one; the rest
 * show "not built yet" and the stage that builds them.
 */
const CHAMBERS = [
  // Ops
  { id: 'c0', wing: 'ops', title: 'Calibration Cell', kind: 'cell', logic: 'c0-calibration',
    box: { x0: 11, x1: 17, z0: 7, z1: 13, y0: 0, h: 4 }, door: 'w', gallery: 's' },
  { id: 's1', wing: 'systems', title: 'Systems Console', kind: 'desk', stage: 6, at: { x: 0, y: 6, z: -19 } },
  // Gate Dynamics
  { id: 'g1', wing: 'gates', title: 'Test Stand', kind: 'cell', stage: 2,
    box: { x0: -20, x1: 20, z0: -140, z1: -104, y0: 0, h: 30 }, door: 's', gallery: 'w' },
  { id: 'relay', wing: 'gates', title: 'Relay Gate', kind: 'cell', stage: 2,
    box: { x0: 30, x1: 50, z0: -140, z1: -120, y0: 0, h: 24 }, door: 's', gallery: 'e' },
  { id: 'g2', wing: 'gates', title: 'Shape Gallery', kind: 'cell', stage: 2,
    box: { x0: -66, x1: 66, z0: -168, z1: -150, y0: 0, h: 30 }, door: 'w', gallery: 's' },
  { id: 'g3', wing: 'gates', title: 'Automation Bay', kind: 'cell', stage: 5,
    box: { x0: 40, x1: 66, z0: -90, z1: -60, y0: 0, h: 16 }, door: 'w', gallery: 's' },
  { id: 'g4', wing: 'gates', title: 'Build Bench', kind: 'cell', stage: 5, creative: true,
    box: { x0: -66, x1: -40, z0: -90, z1: -60, y0: 0, h: 16 }, door: 'e', gallery: 's' },
  { id: 'g5', wing: 'gates', title: 'Iris Chamber', kind: 'cell', stage: 5,
    box: { x0: -66, x1: -34, z0: -140, z1: -110, y0: 0, h: 16 }, door: 'e', gallery: 's' },
  // Ring Transit
  { id: 'r1', wing: 'rings', title: 'Pair Stand', kind: 'cell', stage: 3,
    box: { x0: 44, x1: 106, z0: -26, z1: -10, y0: 0, h: 12 }, door: 's', gallery: 'n' },
  { id: 'r2', wing: 'rings', title: 'Ceiling Room', kind: 'cell', stage: 3,
    box: { x0: 44, x1: 58, z0: 6, z1: 22, y0: 0, h: 12 }, door: 'n', gallery: 'e' },
  { id: 'r3', wing: 'rings', title: 'Shaft', kind: 'cell', stage: 3, shaft: 60,
    box: { x0: 66, x1: 72, z0: 8, z1: 14, y0: 0, h: 12 }, door: 'n', gallery: 's' },
  { id: 'r4', wing: 'rings', title: 'Build Bench', kind: 'cell', stage: 3, creative: true,
    box: { x0: 82, x1: 104, z0: 6, z1: 22, y0: 0, h: 12 }, door: 'n', gallery: 'w' },
  { id: 'r5', wing: 'rings', title: 'Edit Desk', kind: 'desk', stage: 3, at: { x: 100, y: 0, z: -4 } },
  { id: 'tunnel', wing: 'rings', title: 'Range Tunnel', kind: 'tunnel', stage: 3,
    box: { x0: 112, x1: 320, z0: -2, z1: 2, y0: 0, h: 5 } },
  // Beam Physics
  { id: 'b1', wing: 'beams', title: 'Pad Array', kind: 'cell', stage: 3,
    box: { x0: -104, x1: -52, z0: -26, z1: 8, y0: 0, h: 10 }, door: 'e', gallery: 's' },
  { id: 'b2', wing: 'beams', title: 'Dispatch Desk', kind: 'desk', stage: 3, at: { x: -60, y: 0, z: 22 } },
  // Mirror Optics
  { id: 'm1', wing: 'mirrors', title: 'Mirror Round', kind: 'cell', stage: 4,
    box: { x0: -36, x1: 36, z0: 50, z1: 68, y0: 0, h: 10 }, door: 'n', gallery: 's' },
  { id: 'm2', wing: 'mirrors', title: 'Wall Bench', kind: 'cell', stage: 4, creative: true,
    box: { x0: -36, x1: -8, z0: 76, z1: 88, y0: 0, h: 8 }, door: 'n', gallery: 'e' },
  { id: 'm3', wing: 'mirrors', title: 'Capture Desk', kind: 'cell', stage: 4,
    box: { x0: 8, x1: 36, z0: 76, z1: 88, y0: 0, h: 8 }, door: 'n', gallery: 'w' },
  // The far sites: fixtures come in stage 4, the places for them now.
  { id: 'range-site', wing: 'range', title: 'Range Fixtures', kind: 'desk', stage: 4, at: { x: 0, y: 64, z: -8 } },
  { id: 'annex-site', wing: 'annex', title: 'Annex Fixtures', kind: 'desk', stage: 4, at: { x: 1000, y: 60, z: 992 } },
];

/**
 * The Menagerie's structures: enclosures, a lava trough, a boathouse pool, a rail loop, an
 * armoury alcove and an arrow range. Structure only this stage: no animals, carts or contents.
 */
const MENAGERIE = {
  pens: [
    { id: 'horse', box: { x0: 83, x1: 88, z0: -118, z1: -113 } },
    { id: 'camel', box: { x0: 90, x1: 95, z0: -118, z1: -113 } },
    { id: 'donkey', box: { x0: 97, x1: 102, z0: -118, z1: -113 } },
    { id: 'llama', box: { x0: 104, x1: 109, z0: -118, z1: -113 } },
    { id: 'pig', box: { x0: 111, x1: 116, z0: -118, z1: -113 } },
    { id: 'kennel', box: { x0: 118, x1: 128, z0: -118, z1: -110 } },
  ],
  lavaTrough: { x0: 83, x1: 109, z: -109 },
  boathouse: { x0: 119, x1: 128, z0: -104, z1: -99 },
  railLoop: { x0: 84, x1: 112, z0: -86, z1: -76 },
  armoury: { x0: 118, x1: 128, z0: -86, z1: -76, h: 4 },
  arrowRange: { x0: 84, x1: 128, z0: -70, z1: -64, target: { x: 128, y: 1, z: -67 } },
};

/** The transit ring in the atrium: eight plates round the centre, each a command-block tp. */
const TRANSIT = {
  centre: { x: 0, z: 0 },
  plates: [
    { dir: 'N', dx: 0, dz: -6, to: 'gates' },
    { dir: 'NE', dx: 4, dz: -4, to: 'menagerie' },
    { dir: 'E', dx: 6, dz: 0, to: 'rings' },
    { dir: 'SE', dx: 4, dz: 4, to: 'systems' },
    { dir: 'S', dx: 0, dz: 6, to: 'mirrors' },
    { dir: 'SW', dx: -4, dz: 4, to: 'range' },
    { dir: 'W', dx: -6, dz: 0, to: 'beams' },
    { dir: 'NW', dx: -4, dz: -4, to: 'annex' },
  ],
  // A plate at each far site, and one in each wing by its entrance, that brings you back to Ops.
  home: { x: 0.5, y: 0, z: 10.5, yaw: 180 },
};

/** Ops boards: the Ops wall (one line per chamber), the fault counter and the welcome board. */
const OPS = {
  mezzanine: { x0: -20, x1: 20, z0: -20, z1: -15, floorY: 5 },
  stair: { x: -19, z0: -14, z1: -9 },
  wall: { x: -6, y: 1.5, z: -13.5 },
  faults: { x: 6, y: 1.5, z: -13.5 },
  welcome: { x: 0.5, y: 2.2, z: 0.5 },
};

/**
 * Chunk rectangles to forceload, per dimension, each under vanilla's 256-chunk limit per
 * command. Only what is built: the campus rooms and corridors, the tunnel strip, the two sites.
 */
const FORCELOAD = [
  { dim: OVERWORLD, from: [-112, -32], to: [-39, 32], why: 'Beam Physics' },
  { dim: OVERWORLD, from: [-72, -172], to: [72, -39], why: 'Gate Dynamics' },
  { dim: OVERWORLD, from: [-42, -38], to: [42, 92], why: 'Ops, Systems, Mirror Optics' },
  { dim: OVERWORLD, from: [38, -32], to: [112, 32], why: 'Ring Transit' },
  { dim: OVERWORLD, from: [71, -122], to: [132, -58], why: 'Menagerie and Motor Pool' },
  { dim: OVERWORLD, from: [112, -3], to: [322, 3], why: 'the range tunnel' },
  { dim: NETHER, from: [-31, -31], to: [31, 31], why: 'The Range' },
  { dim: END, from: [978, 978], to: [1022, 1022], why: 'The Annex' },
];

function wing(id) {
  const w = WINGS.find((x) => x.id === id);
  if (!w) throw new Error(`no wing ${id}`);
  return w;
}

function chamber(id) {
  const c = CHAMBERS.find((x) => x.id === id);
  if (!c) throw new Error(`no chamber ${id}`);
  return c;
}

module.exports = {
  OVERWORLD, NETHER, END, FLAT_LAYERS, PALETTE, WINGS, CORRIDORS, LANES, CHAMBERS, MENAGERIE,
  TRANSIT, OPS, FORCELOAD, wing, chamber,
};
