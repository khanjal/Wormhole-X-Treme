'use strict';
// The self-test matrix: per chamber, the cells to run and what each must come to. Values are
// overrides on the chamber's defaults (the first value of each option). An expectation is
//   'PASS'                  every check true
//   'REFUSED:<reason>'      the chamber's own refusal, word for word
//   'FAIL:<check name>'     a known plugin failure: the first false check, by name, with `known`
//                           saying what the plugin does wrong. It is reported, never hidden, and
//                           if the plugin is fixed the cell fails until its expectation is updated.
// `expect` may be a function of the server version. A known failure with `fixedBy: '<issue>'` is
// expected to PASS when run-facility is told `--fixed <issue>` (a plugin jar with the fix).

const { normaliseOptions } = require('./lib/console');
const { atLeast } = require('./lib/version');

function defaultsOf(chamber) {
  return Object.fromEntries(normaliseOptions(chamber.options).map((o) => [o.name, o.values[0].value]));
}

function expectation(cell, version, fixed = []) {
  if (cell.fixedBy && fixed.includes(cell.fixedBy)) return 'PASS';
  return typeof cell.expect === 'function' ? cell.expect(version) : cell.expect;
}

const CART_491 = {
  expect: "FAIL:the cart's front never crossed the iris face",
  known: "#491: the plugin checks only the block the cart's centre is in, so a cart is stopped half inside a shut drawn iris",
  fixedBy: '491',
};

const P = (projectile, extra = {}) => ({ traveller: 'projectile', projectile, ...extra });

const G1 = [
  // Shapes, dial methods and destinations, on foot.
  { name: 'g1 walk Standard by console', values: { traveller: 'walk' }, expect: 'PASS' },
  { name: 'g1 walk by DHD and /dial', values: { traveller: 'walk', dial: 'dhd' }, expect: 'PASS' },
  { name: 'g1 walk Large', values: { shape: 'Large' }, expect: 'PASS' },
  { name: 'g1 walk Grand', values: { shape: 'Grand' }, expect: 'PASS' },
  { name: 'g1 walk Massive', values: { shape: 'Massive' }, expect: 'PASS' },
  { name: 'g1 walk Minimal', values: { shape: 'Minimal' }, expect: 'PASS' },
  { name: 'g1 walk into Horizontal', values: { shape: 'Horizontal' }, expect: 'PASS' },
  { name: 'g1 walk to the Range', values: { destination: 'Range' }, expect: 'PASS' },
  { name: 'g1 walk to the Annex', values: { destination: 'Annex' }, expect: 'PASS' },
  { name: 'g1 group Atlantis', values: { group: 'Atlantis' }, expect: 'PASS' },
  { name: 'g1 spin pegasus', values: { spin: 'pegasus' }, expect: 'PASS' },
  { name: 'g1 portal LAVA', values: { portal: 'LAVA' }, expect: 'PASS' },
  { name: 'g1 chevrons lamp', values: { chevrons: 'lamp' }, expect: 'PASS' },
  // Refusals, the plugin's and the chamber's.
  { name: 'g1 dial itself', values: { destination: 'self' }, expect: 'PASS' },
  { name: 'g1 dial a busy Relay', values: { destination: 'busy' }, expect: 'PASS' },
  { name: 'g1 far iris shut, no code', values: { 'far iris': 'shut no code' }, expect: 'PASS' },
  { name: 'g1 far iris shut, dialled with code', values: { 'far iris': 'shut with code' }, expect: 'PASS' },
  { name: 'g1 far iris shut after dial: walker', values: { 'far iris': 'shut after dial' }, expect: 'PASS' },
  { name: 'g1 own iris shut: walker', values: { 'own iris': 'shut' }, expect: 'PASS' },
  { name: 'g1 StandardSignDial by console', values: { shape: 'StandardSignDial' }, expect: 'PASS' },
  { name: 'g1 custom shape', values: { shape: 'custom' }, expect: 'REFUSED:the custom Lab.shape arrives in stage 5' },
  { name: 'g1 built by preview', values: { built: 'preview' }, expect: 'REFUSED:building by preview or by hand arrives in stage 5' },
  { name: 'g1 sign dial on Standard', values: { dial: 'sign right' }, expect: 'REFUSED:Standard has no dial sign or [RD] block' },
  { name: 'g1 bot throws a fireball', values: P('fireball'), expect: 'REFUSED:a player cannot throw a fireball; the dispenser can' },
  // Every traveller.
  { name: 'g1 minecart', values: { traveller: 'minecart' }, expect: 'PASS' },
  { name: 'g1 minecart ridden', values: { traveller: 'minecart ridden' }, expect: 'PASS' },
  { name: 'g1 boat', values: { traveller: 'boat' }, expect: 'PASS' },
  { name: 'g1 boat ridden', values: { traveller: 'boat ridden' }, expect: 'PASS' },
  ...['horse', 'camel', 'pig', 'donkey', 'llama', 'strider'].map((t) => ({ name: `g1 ${t}`, values: { traveller: t }, expect: 'PASS' })),
  ...['wolf', 'cat', 'parrot', 'sitting wolf'].map((t) => ({ name: `g1 ${t}`, values: { traveller: t }, expect: 'PASS' })),
  // (Spilled items are left out: a broken cart's drops scatter, and whether one stays in the
  // one-block opening until the next sweep is chance. The lying item is the deterministic case.)
  ...['lying item', 'xp', 'armour stand', 'item frame', 'zombie'].map((t) => ({ name: `g1 ${t}`, values: { traveller: t }, expect: 'PASS' })),
  ...['dropped item', 'dispensed item'].map((t) => ({
    name: `g1 ${t}`,
    values: { traveller: t },
    expect: 'FAIL:every one of the 5 came out of Relay',
    known: 'of five items tossed (or dispensed) into the opening, some fly through its one block between two entity sweeps (every 20 ticks) and land behind the gate: only an item lying in the opening when a sweep runs is sent',
  })),
  // Every projectile, and the launchers and angles.
  ...['arrow', 'spectral arrow', 'bolt', 'piercing bolt', 'firework', 'trident', 'snowball', 'egg', 'pearl',
    'splash potion', 'lingering potion'].map((p) => ({ name: `g1 ${p}`, values: P(p), expect: 'PASS' })),
  {
    name: 'g1 tipped arrow',
    values: P('tipped arrow'),
    expect: 'FAIL:its slowness survived',
    known: 'the plugin re-makes an arrow at the far gate as a plain one: a tipped arrow comes out as item minecraft:arrow, without its potion',
  },
  { name: 'g1 wind charge', values: P('wind charge'), expect: (v) => (atLeast(v, '1.21') ? 'PASS' : 'REFUSED:there are no wind charges before 1.21') },
  { name: 'g1 fireball from a dispenser', values: P('fireball', { launcher: 'dispenser' }), expect: 'PASS' },
  { name: 'g1 llama spit', values: P('llama spit', { launcher: 'dispenser' }), expect: 'PASS' },
  { name: 'g1 arrow from a dispenser', values: P('arrow', { launcher: 'dispenser' }), expect: 'PASS' },
  { name: 'g1 arrow glancing', values: P('arrow', { angle: 'glancing' }), expect: 'PASS' },
  { name: 'g1 arrow point-blank', values: P('arrow', { angle: 'point-blank' }), expect: 'PASS' },
  { name: 'g1 arrow down into Horizontal', values: P('arrow', { shape: 'Horizontal', angle: 'above' }), expect: 'PASS' },
  { name: 'g1 far iris shut after dial: arrow', values: P('arrow', { 'far iris': 'shut after dial' }), expect: 'PASS' },
  { name: 'g1 far iris shut after dial: item', values: { traveller: 'lying item', 'far iris': 'shut after dial' }, expect: 'PASS' },
  // Issue #491: a cart run at a shut iris stops flush with its face (checklist in g1-stand.js).
  { name: '491-A cart from the front', values: { traveller: 'cart at iris', shape: 'Massive', 'own iris': 'shut' }, ...CART_491 },
  { name: '491-B cart with a rider', values: { traveller: 'cart at iris', shape: 'Massive', 'own iris': 'shut', rider: 'Probe' }, ...CART_491 },
  { name: '491-C cart from behind', values: { traveller: 'cart at iris', shape: 'Massive', 'own iris': 'shut', approach: 'behind' }, ...CART_491 },
  { name: '491-E cart at the shut far end', values: { traveller: 'cart at iris', shape: 'Massive', 'own iris': 'shut', 'iris gate': 'incoming' }, ...CART_491 },
  { name: '491-F control: iris open', values: { traveller: 'cart at iris', shape: 'Massive', 'own iris': 'open' }, expect: 'PASS' },
];

const MATRIX = {
  c0: [
    { values: { target: 'gold_block', control: 'lever' }, expect: 'PASS' },
    { values: { target: 'gold_block', control: 'button' }, expect: 'PASS' },
    { values: { target: 'glass', control: 'lever' }, expect: 'REFUSED:a lever or button needs a solid block to hang on' },
    { values: { target: 'glass', control: 'button' }, expect: 'REFUSED:a lever or button needs a solid block to hang on' },
  ],
  g1: G1,
  g2: [
    ...['Massive', 'Grand', 'Large', 'Standard', 'Minimal', 'Horizontal'].map((g) => ({ name: `g2 ${g} to its neighbour`, values: { gate: g }, expect: 'PASS' })),
    { name: 'g2 validate', values: { check: 'validate' }, expect: 'REFUSED:validate arrives in stage 5 with the upkeep and protection tests' },
  ],
};

/**
 * The --quick profile: one of each kind of thing, for a check across versions in a few minutes
 * (text formats, entity ids, boats, the teleport quirk) rather than the whole matrix.
 */
const QUICK = new Set([
  'g1 walk Standard by console', 'g1 minecart', 'g1 horse', 'g1 wolf', 'g1 arrow', 'g1 snowball',
  'g1 arrow from a dispenser', 'g1 dropped item', 'g1 dial itself', '491-A cart from the front',
  'g2 Standard to its neighbour',
]);
for (const [id, cells] of Object.entries(MATRIX)) {
  for (const cell of cells) if (id === 'c0' || QUICK.has(cell.name)) cell.quick = true;
}

module.exports = { MATRIX, QUICK, defaultsOf, expectation };
