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
// One seed, so the nether and End round the far sites are the same terrain every run.
const SEED = 20110609;

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
  // Stage 3.6 (creative pass 1.2); every one of them is in 1.20.0.
  operated: 'minecraft:waxed_cut_copper',
  deckFloor: 'minecraft:deepslate_tiles',
  light: 'minecraft:light[level=15]',
};

/** Boards shown twice: the G1 board is mirrored in the control room over its gallery. */
const BOARD_MIRRORS = { g1: ['g1_control'] };

/**
 * The wings. `room` is the main hall; `entrance` is where a tp plate or `!go` puts you, inside
 * the wing's door, facing in. `colour` is the concrete/text colour of the department.
 * `homePlate`, where given, is the plate back to Ops: two blocks into the wing's corridor, out
 * of its lobby (the rest keep theirs beside the entrance).
 */
const WINGS = [
  {
    id: 'ops', title: 'Operations', nick: 'the Gate Room and the Atrium', colour: 'white', text: 'white', dim: OVERWORLD,
    room: { x0: -20, x1: 20, z0: -20, z1: 20, y0: 0, h: 16 },
    entrance: { x: -2.5, y: 0, z: 12.5, yaw: 180 },
  },
  {
    id: 'gates', title: 'Gate Dynamics', nick: 'the Hangar', colour: 'cyan', text: 'dark_aqua', dim: OVERWORLD,
    room: { x0: -70, x1: 70, z0: -170, z1: -40, y0: 0, h: 36 },
    entrance: { x: 0.5, y: 0, z: -43.5, yaw: 180 },
    homePlate: { x: 2, y: 0, z: -37 },
  },
  {
    id: 'rings', title: 'Ring Transit', nick: 'the Concourse', colour: 'lime', text: 'green', dim: OVERWORLD,
    room: { x0: 40, x1: 110, z0: -30, z1: 30, y0: 0, h: 14 },
    entrance: { x: 43.5, y: 0, z: 0.5, yaw: -90 },
    homePlate: { x: 36, y: 0, z: 2 },
  },
  {
    id: 'beams', title: 'Beam Physics', nick: 'the Transporter Bay', colour: 'light_blue', text: 'aqua', dim: OVERWORLD,
    room: { x0: -110, x1: -40, z0: -30, z1: 30, y0: 0, h: 12 },
    entrance: { x: -42.5, y: 0, z: 0.5, yaw: 90 },
    homePlate: { x: -36, y: 0, z: 2 },
  },
  {
    id: 'mirrors', title: 'Mirror Optics', nick: 'the Looking-Glass Gallery', colour: 'magenta', text: 'light_purple', dim: OVERWORLD,
    room: { x0: -40, x1: 40, z0: 40, z1: 90, y0: 0, h: 12 },
    entrance: { x: 0.5, y: 0, z: 43.5, yaw: 0 },
    homePlate: { x: 2, y: 0, z: 36 },
  },
  {
    id: 'menagerie', title: 'Menagerie and Motor Pool', nick: 'the Yard', colour: 'orange', text: 'gold', dim: OVERWORLD,
    room: { x0: 80, x1: 130, z0: -120, z1: -60, y0: 0, h: 10 },
    entrance: { x: 81.5, y: 0, z: -95.5, yaw: -90 },
  },
  {
    id: 'systems', title: 'Systems', nick: 'the Briefing Room', colour: 'yellow', text: 'yellow', dim: OVERWORLD,
    // The mezzanine over Ops, not a room of its own: a gallery 6 above the atrium floor.
    room: null,
    entrance: { x: 0.5, y: 6, z: -16.5, yaw: 0 },
  },
  {
    id: 'range', title: 'The Range', nick: 'Forward Base', colour: 'red', text: 'red', dim: NETHER,
    room: { x0: -30, x1: 30, z0: -30, z1: 30, y0: 64, h: 20 }, shell: 'dome',
    entrance: { x: 0.5, y: 64, z: 8.5, yaw: 180 },
  },
  {
    id: 'annex', title: 'The Annex', nick: 'the Observatory', colour: 'purple', text: 'dark_purple', dim: END,
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
  { id: 's1', wing: 'systems', title: 'Systems Console', kind: 'desk', stage: 6, logic: 's1-systems', at: { x: 0, y: 6, z: -19 } },
  // The companion desks (run-facility --with): each refuses a run without its companion plugins.
  { id: 'map', wing: 'systems', title: 'Map Desk', kind: 'desk', stage: 6, logic: 'companion-map', at: { x: 6, y: 6, z: -19 } },
  { id: 'regions', wing: 'systems', title: 'Region Desk', kind: 'desk', stage: 6, logic: 'companion-regions', at: { x: -8, y: 6, z: -19 } },
  { id: 'perms', wing: 'systems', title: 'Permissions Desk', kind: 'desk', stage: 6, logic: 'companion-perms', at: { x: -15, y: 6, z: -19 } },
  // Gate Dynamics
  { id: 'g1', wing: 'gates', title: 'Test Stand', kind: 'cell', stage: 2, logic: 'g1-stand',
    box: { x0: -20, x1: 20, z0: -140, z1: -104, y0: 0, h: 30 }, door: 's', gallery: 'w' },
  { id: 'relay', wing: 'gates', title: 'Relay Gate', kind: 'cell', stage: 2, logic: 'relay',
    box: { x0: 30, x1: 50, z0: -140, z1: -120, y0: 0, h: 24 }, door: 's', gallery: 'e' },
  { id: 'g2', wing: 'gates', title: 'Shape Gallery', kind: 'cell', stage: 2, logic: 'g2-gallery',
    box: { x0: -66, x1: 66, z0: -168, z1: -150, y0: 0, h: 30 }, door: 'w', gallery: 's' },
  { id: 'g3', wing: 'gates', title: 'Automation Bay', kind: 'cell', stage: 5, logic: 'g3-automation',
    box: { x0: 40, x1: 66, z0: -90, z1: -60, y0: 0, h: 16 }, door: 'w', gallery: 's' },
  { id: 'g4', wing: 'gates', title: 'Build Bench', kind: 'cell', stage: 5, creative: true, logic: 'g4-bench',
    box: { x0: -66, x1: -40, z0: -90, z1: -60, y0: 0, h: 16 }, door: 'e', gallery: 's' },
  { id: 'g5', wing: 'gates', title: 'Iris Chamber', kind: 'cell', stage: 5, logic: 'g5-iris',
    box: { x0: -66, x1: -34, z0: -140, z1: -110, y0: 0, h: 16 }, door: 'e', gallery: 's' },
  // Ring Transit
  { id: 'r1', wing: 'rings', title: 'Pair Stand', kind: 'cell', stage: 3, logic: 'r1-pair',
    box: { x0: 44, x1: 106, z0: -26, z1: -10, y0: 0, h: 12 }, door: 's', gallery: 'n' },
  { id: 'r2', wing: 'rings', title: 'Ceiling Room', kind: 'cell', stage: 3, logic: 'r2-ceiling',
    box: { x0: 44, x1: 58, z0: 6, z1: 22, y0: 0, h: 12 }, door: 'n', gallery: 'e' },
  // R3's gallery is glass over a light well (wings/decor/rings.js): the Shaft Window.
  { id: 'r3', wing: 'rings', title: 'Shaft', kind: 'cell', stage: 3, shaft: 60, logic: 'r3-shaft', galleryFloor: 'minecraft:glass',
    box: { x0: 66, x1: 72, z0: 8, z1: 14, y0: 0, h: 12 }, door: 'n', gallery: 's' },
  { id: 'r4', wing: 'rings', title: 'Build Bench', kind: 'cell', stage: 3, creative: true, logic: 'r4-bench',
    box: { x0: 82, x1: 104, z0: 6, z1: 22, y0: 0, h: 12 }, door: 'n', gallery: 'w' },
  { id: 'r5', wing: 'rings', title: 'Edit Desk', kind: 'desk', stage: 3, logic: 'r5-edit', at: { x: 100, y: 0, z: -4 } },
  // Nine wide, so a seven-wide ring fits; long enough for a pair 257 apart (one over the limit).
  { id: 'tunnel', wing: 'rings', title: 'Range Tunnel', kind: 'tunnel', stage: 3, logic: 'r6-range',
    box: { x0: 112, x1: 380, z0: -4, z1: 4, y0: 0, h: 6 } },
  // Beam Physics
  { id: 'b1', wing: 'beams', title: 'Pad Array', kind: 'cell', stage: 3, logic: 'b1-pads',
    box: { x0: -104, x1: -52, z0: -26, z1: 8, y0: 0, h: 10 }, door: 'e', gallery: 's' },
  { id: 'b2', wing: 'beams', title: 'Dispatch Desk', kind: 'desk', stage: 3, logic: 'b2-dispatch', at: { x: -60, y: 0, z: 22 } },
  // Mirror Optics
  { id: 'm1', wing: 'mirrors', title: 'Mirror Round', kind: 'cell', stage: 4, logic: 'm1-round',
    box: { x0: -36, x1: 36, z0: 50, z1: 68, y0: 0, h: 10 }, door: 'n', gallery: 's' },
  { id: 'm2', wing: 'mirrors', title: 'Wall Bench', kind: 'cell', stage: 4, creative: true, logic: 'm2-wall',
    box: { x0: -36, x1: -8, z0: 76, z1: 88, y0: 0, h: 8 }, door: 'n', gallery: 'e' },
  { id: 'm3', wing: 'mirrors', title: 'Capture Desk', kind: 'cell', stage: 4, logic: 'm3-capture',
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

/**
 * The tp plates: the fallback that works with the plugin down, in the service corridor under
 * the mezzanine (x -20..20, z -20..-15), four apart so `@p[distance=..2]` never picks the wrong
 * one. `home` is where a plate home and a welcome put you.
 */
const TRANSIT = {
  centre: { x: 0, z: -18 },
  plates: [
    { dir: 'annex', dx: -16, dz: 0, to: 'annex' },
    { dir: 'range', dx: -12, dz: 0, to: 'range' },
    { dir: 'beams', dx: -8, dz: 0, to: 'beams' },
    { dir: 'mirrors', dx: -4, dz: 0, to: 'mirrors' },
    { dir: 'gates', dx: 4, dz: 0, to: 'gates' },
    { dir: 'rings', dx: 8, dz: 0, to: 'rings' },
    { dir: 'menagerie', dx: 12, dz: 0, to: 'menagerie' },
    { dir: 'systems', dx: 16, dz: 0, to: 'systems' },
  ],
  // Where a walk onto a plate starts: south of the corridor's line, walking north onto it.
  approachZ: -15.5,
  home: { x: -2.5, y: 0, z: 12.5, yaw: 180 },
};

/**
 * The transit routes: each wing reached from Ops by the feature it tests, built as session
 * fixtures the way a server's own staff would (console forms, and Probe where the plugin wants a
 * player), and walked by the self-test every run.
 *
 *  - gates: `Ops` in the atrium and `Hall` in the Gate hall's lobby, Standard, facing south, on
 *    the default network (a non-op may use an ownerless gate). A dial console of command-block
 *    buttons by each (the console form of `gate dial`), and the DHD for `/dial`.
 *  - rings: a public pair, `Ops` in the atrium and `Lab` in the Ring lab, off the door lines;
 *    the slabs are consumed when paired, so a rim in the floor marks each pad.
 *  - beams: public destinations `Atrium` and `BeamLab`, each on a pad; a button by each prompts
 *    whoever is nearest with a click that runs `/wormhole beam to <the other>` as them. A command
 *    block cannot beam the presser itself: `beam to` refuses a non-player sender (a command block
 *    running `execute as @p` included), and `beam admin send` takes a player's exact name.
 *  - mirrors (stage 4): the spots are kept free.
 */
const ROUTES = {
  gates: {
    Ops: { wing: 'ops', dim: OVERWORLD, shape: 'Standard', facing: 'south', cx: 0, openingAt: -13, floorY: 0,
      runway: { x0: -1, x1: 1, z0: -8, z1: -3 }, console: { x: -5, z: -8, dial: ['Hall', 'Range', 'Annex'] } },
    Hall: { wing: 'gates', dim: OVERWORLD, shape: 'Standard', facing: 'south', cx: 0, openingAt: -53, floorY: 0,
      runway: { x0: -1, x1: 1, z0: -48, z1: -45 }, console: { x: -3, z: -48, dial: ['Ops'] } },
  },
  rings: {
    pattern: 'ODD', slab: 'polished_deepslate_slab',
    ends: [{ name: 'Ops', wing: 'ops', x: 13, y: 0, z: -6 }, { name: 'Lab', wing: 'rings', x: 52, y: 0, z: -4 }],
  },
  beams: [
    { name: 'Atrium', wing: 'ops', x: -13, y: 0, z: -6, yaw: 90, button: { x: -17, z: -6 }, to: 'BeamLab' },
    { name: 'BeamLab', wing: 'beams', x: -46, y: 0, z: 0, yaw: 90, button: { x: -46, z: -4 }, to: 'Atrium' },
  ],
  // One in each world beside Ops and the gallery (so the default per-world limit of 1 is kept in
  // the nether and the End). `wall: 'room'` hangs on a room's wall, `'pier'` on a free-standing
  // pier the decoration builds; `floorY` is where a traveller's feet land. A banner hangs at
  // floorY + 1, where a standing player's head is, as a player builds one.
  mirrors: [
    { name: 'Ops', wing: 'ops', dim: OVERWORLD, x: -10, y: 1, z: 20, facing: 'north', floorY: 0, wall: 'room', start: 'Optics', look: 'indoors' },
    { name: 'Optics', wing: 'mirrors', dim: OVERWORLD, x: -10, y: 1, z: 40, facing: 'south', floorY: 0, wall: 'room', start: 'Ops', look: 'library' },
    { name: 'Range', wing: 'range', dim: NETHER, x: -15, y: 65, z: -24, facing: 'south', floorY: 64, wall: 'pier', start: 'Ops', look: 'nether' },
    { name: 'Annex', wing: 'annex', dim: END, x: 1012, y: 61, z: 1016, facing: 'north', floorY: 60, wall: 'pier', start: 'Ops', look: 'end' },
  ],
};

/** The facility's baseline: two mirrors in the overworld, room for the chambers' own. Restored at close. */
const BASELINE = { 'mirror-per-world-limit': '6' };

/** Ops boards: the Ops wall (one line per chamber), the fault counter and the welcome board. */
/**
 * Where gates stand: each opening's centre across it (cx), its plane along the facing
 * (openingAt), and the floor its arrivals stand on. The Stand is G1's gate under test; the far
 * gates are fixtures built once a session (chambers/relay.js); the gallery is G2's.
 */
const GATES = {
  stand: { dim: OVERWORLD, facing: 'south', cx: 0, openingAt: -128, floorY: 0 },
  far: {
    Relay: { dim: OVERWORLD, shape: 'Standard', facing: 'south', cx: 40, openingAt: -131, floorY: 0 },
    Range: { dim: NETHER, shape: 'Standard', facing: 'south', cx: 0, openingAt: -20, floorY: 64 },
    Annex: { dim: END, shape: 'Standard', facing: 'south', cx: 1000, openingAt: 1000, floorY: 60 },
  },
  // G2: the six console-buildable shapes in a row along the back wall, west to east.
  gallery: {
    dim: OVERWORLD, facing: 'south', openingAt: -166, floorY: 0, net: 'Gallery',
    row: [['Massive', -52], ['Grand', -22], ['Large', 2], ['Standard', 18], ['Minimal', 31], ['Horizontal', 46]],
  },
};

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
  { dim: OVERWORLD, from: [112, -5], to: [382, 5], why: 'the range tunnel' },
  { dim: NETHER, from: [-31, -31], to: [31, 31], why: 'The Range' },
  { dim: END, from: [978, 978], to: [1022, 1022], why: 'The Annex' },
];

/**
 * The viewer's vantage points (run-facility --shots): where Probe stands (feet; it flies, so a
 * point may be in the air), facing `yaw` (0 south, -90 east, 90 west, 180 north) and `pitch`
 * (down is positive), for a picture of each wing. A cell's tinted glass is all a seat beside it
 * shows, so B1 and M1 are seen from inside, high by the gallery wall.
 */
const SHOTS = [
  { name: 'gate-room', wing: 'ops', dim: OVERWORLD, x: 0.5, y: 2, z: 12.5, yaw: 180, pitch: 5 },
  { name: 'atrium', wing: 'ops', dim: OVERWORLD, x: 0.5, y: 6, z: -15.5, yaw: 0, pitch: 20 },
  { name: 'systems-mezzanine', wing: 'systems', dim: OVERWORLD, x: -18.5, y: 6, z: -16.5, yaw: -90, pitch: 5 },
  { name: 'gate-hall', wing: 'gates', dim: OVERWORLD, x: 0.5, y: 12, z: -44.5, yaw: 180, pitch: 20 },
  { name: 'gate-hall-deck', wing: 'gates', dim: OVERWORLD, x: -25.5, y: 7, z: -139.5, yaw: -60, pitch: 15 },
  { name: 'g1-control', wing: 'gates', dim: OVERWORLD, x: -28.5, y: 9, z: -105.5, yaw: -100, pitch: 20 },
  { name: 'ring-concourse', wing: 'rings', dim: OVERWORLD, x: 42.5, y: 5, z: 0.5, yaw: -90, pitch: 10 },
  { name: 'shaft-window', wing: 'rings', dim: OVERWORLD, x: 69.5, y: 1, z: 17.5, yaw: 180, pitch: 75 },
  { name: 'beam-lab', wing: 'beams', dim: OVERWORLD, x: -44.5, y: 5, z: 20.5, yaw: 120, pitch: 15 },
  { name: 'b1-pads', wing: 'beams', dim: OVERWORLD, x: -77.5, y: 7, z: 6.5, yaw: 180, pitch: 25 },
  { name: 'mirror-hall', wing: 'mirrors', dim: OVERWORLD, x: 0.5, y: 4, z: 41.5, yaw: 0, pitch: 15 },
  { name: 'mirror-optics', wing: 'mirrors', dim: OVERWORLD, x: -9.5, y: 1, z: 46.5, yaw: 180, pitch: 0 },
  { name: 'm1-round', wing: 'mirrors', dim: OVERWORLD, x: 0.5, y: 6, z: 66.5, yaw: 180, pitch: 25 },
  { name: 'menagerie', wing: 'menagerie', dim: OVERWORLD, x: 83.5, y: 6, z: -62.5, yaw: -135, pitch: 20 },
  { name: 'range', wing: 'range', dim: NETHER, x: 0.5, y: 68, z: 22.5, yaw: 180, pitch: 10 },
  { name: 'annex', wing: 'annex', dim: END, x: 1000.5, y: 64, z: 1018.5, yaw: 180, pitch: 10 },
];

/**
 * Watch mode's vantage points (run-facility --selftest --watch, lib/watcher.js): where a watcher
 * is put before a chamber's cells, facing `at`, and held within WATCH_LEASH of while they run;
 * `sections` is for the rest of the self-test. A watcher is a player to the plugin, so each point
 * is kept out of reach of every mirror banner, ring and gate opening the cells use
 * (test/watcher.test.js). `look` is what the watcher is told to look for.
 */
const WATCH_LEASH = 3;
const WATCH = {
  sections: { dim: OVERWORLD, x: 16.5, y: 9, z: 16.5, at: { x: 0, y: 2, z: -8 },
    look: 'the atrium: Probe and Probe2 cross it between wings; the Ops wall and the pylons show each result' },
  c0: { dim: OVERWORLD, x: 14.5, y: 2, z: 17.5, at: { x: 14, y: 1, z: 10 },
    look: 'Probe works the lever or button in the cell, and the lamp answers' },
  g1: { dim: OVERWORLD, x: -17.5, y: 12, z: -106.5, at: { x: 0, y: 3, z: -128 },
    look: 'the Stand dials a far gate (Relay, east past the wall, or the Range or the Annex); the traveller goes into the opening and does not come back out' },
  g2: { dim: OVERWORLD, x: 0.5, y: 14, z: -151.5, at: { x: 0, y: 6, z: -166 },
    look: 'one of the six shapes along the back wall dials another, and Probe goes through it' },
  g3: { dim: OVERWORLD, x: 53.5, y: 11, z: -61.5, at: { x: 53, y: 2, z: -76 },
    look: 'Bay, the gate on the left, opens from a lever, button, plate, rail or redstone, and the lamp by its lever lights while it is open' },
  g4: { dim: OVERWORLD, x: -52.5, y: 10, z: -61.5, at: { x: -53, y: 2, z: -80 },
    look: 'a gate preview on the bench: Probe works the preview action, or builds the gate by hand' },
  g5: { dim: OVERWORLD, x: -49.5, y: 10, z: -112.5, at: { x: -50, y: 3, z: -125 },
    look: 'IrisA (west) and IrisS (east): the iris sweeps shut or open in steps, and what is thrown at a shut one stops' },
  r1: { dim: OVERWORLD, x: 70.5, y: 9, z: -11.5, at: { x: 70, y: 1, z: -18 },
    look: 'two slab circles pair, the rings rise, and whatever stands in one is swapped with the other end' },
  r2: { dim: OVERWORLD, x: 57.5, y: 10, z: 21.5, at: { x: 51, y: 4, z: 13 },
    look: 'a ring hung from the ceiling pairs with the floor ring, or is refused by height' },
  r3: { dim: OVERWORLD, x: 69.5, y: 2, z: 18.5, at: { x: 69, y: -10, z: 11 },
    look: 'down the shaft through the glass: the top ring pairs with one 20, 40 or 60 down' },
  r4: { dim: OVERWORLD, x: 93.5, y: 9, z: 21.5, at: { x: 93, y: 1, z: 14 },
    look: 'a faulty circle of slabs is laid and `ring create` refuses it' },
  r5: { dim: OVERWORLD, x: 80.5, y: 9, z: -6.5, at: { x: 80, y: 1, z: 0 },
    look: 'the pair in the concourse is edited (name, light, access, owner) and then used' },
  tunnel: { dim: OVERWORLD, x: 107.5, y: 4, z: 0.5, at: { x: 130, y: 1, z: 0 },
    look: 'down the range tunnel: rings 64 to 257 apart pair, or are refused past 256' },
  b1: { dim: OVERWORLD, x: -77.5, y: 7, z: 6.5, at: { x: -80, y: 1, z: -15 },
    look: 'the traveller is enveloped on the start pad, rises, and comes down on the named pad facing its letter' },
  b2: { dim: OVERWORLD, x: -70.5, y: 7, z: 27.5, at: { x: -70, y: 1, z: 18 },
    look: 'the dispatch desk: a beam sent by `admin send` or `admin goto`, or refused in chat' },
  m1: { dim: OVERWORLD, x: 10.5, y: 7, z: 66.5, at: { x: -15, y: 2, z: 55 },
    look: 'Round, on the far wall: the traveller right-clicks through the round and punches, and is gone. From here the banner stays a banner: a mirror opens only for whoever is within 16 blocks' },
  m2: { dim: OVERWORLD, x: 5.5, y: 6, z: 82.5, at: { x: -20, y: 1, z: 80 },
    look: 'the niches down the bench (west, through the glass): `mirror create` at each, and the answer in chat' },
  m3: { dim: OVERWORLD, x: 9.5, y: 6, z: 85.5, at: { x: 30, y: 1, z: 80 },
    look: 'Desk, on the north wall, and the gold block put in its room after the capture. Its view is drawn only within 16 blocks, so here it stays a banner' },
  // The companion desks' cells all run at the G1 stand (run-facility --with).
  map: { dim: OVERWORLD, x: -17.5, y: 12, z: -106.5, at: { x: 0, y: 3, z: -125 },
    look: 'the Stand: gates, rings, beams and a mirror made and used while the web map draws them' },
  regions: { dim: OVERWORLD, x: -17.5, y: 12, z: -106.5, at: { x: 0, y: 3, z: -125 },
    look: 'the Stand: building and using in and out of a WorldGuard region' },
  perms: { dim: OVERWORLD, x: -17.5, y: 12, z: -106.5, at: { x: 0, y: 3, z: -125 },
    look: 'the Stand: Probe2 tries what its LuckPerms group allows and is refused the rest' },
  // The Systems desk has no cell: its gate cases build at the Stand, so a watcher is put there too.
  s1: { dim: OVERWORLD, x: -17.5, y: 12, z: -106.5, at: { x: 0, y: 3, z: -125 },
    look: 'the Stand: Probe changes one plugin setting at a time and the gate, ring, sound or message answers it' },
};

/**
 * WorldEdit schematics placed during generation with --schematics <folder> (lib/schematics.js):
 * [{ file, at: { x, y, z }, rotation, dim }], each checked by the decoration guardrail first.
 */
const SCHEMATICS = [];

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
  OVERWORLD, NETHER, END, SEED, FLAT_LAYERS, PALETTE, WINGS, CORRIDORS, LANES, CHAMBERS, MENAGERIE,
  TRANSIT, ROUTES, BASELINE, OPS, GATES, FORCELOAD, BOARD_MIRRORS, SHOTS, WATCH, WATCH_LEASH, SCHEMATICS, wing, chamber,
};
