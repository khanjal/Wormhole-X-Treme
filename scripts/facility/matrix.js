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
// A cell may hold `settings` ({ setting: value }) for its run, on top of the chamber's own: an
// ordinary trip under a setting meant to stop it, `because` saying how, fails at the check the
// setting stops ('FAIL:<check name>'), against the same cell without it that passes.

const { normaliseOptions } = require('./lib/console');
const { atLeast } = require('./lib/version');

function defaultsOf(chamber) {
  return Object.fromEntries(normaliseOptions(chamber.options).map((o) => [o.name, o.values[0].value]));
}

function expectation(cell, version, fixed = []) {
  if (cell.regressedBy && fixed.includes(cell.regressedBy)) return cell.regression;
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
  // Cross-world rows (stage 4): travellers other than a walker, into the nether and the End.
  { name: 'g1 horse to the Range', values: { destination: 'Range', traveller: 'horse' }, expect: 'PASS' },
  { name: 'g1 minecart to the Annex', values: { destination: 'Annex', traveller: 'minecart' }, expect: 'PASS' },
  { name: 'g1 wolf to the Range', values: { destination: 'Range', traveller: 'wolf' }, expect: 'PASS' },
  { name: 'g1 arrow to the Annex', values: { destination: 'Annex', ...P('arrow') }, expect: 'PASS' },
  { name: 'g1 zombie to the Range', values: { destination: 'Range', traveller: 'zombie' }, expect: 'PASS' },
  { name: 'g1 lying item to the Annex', values: { destination: 'Annex', traveller: 'lying item' }, expect: 'PASS' },
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
  { name: 'g1 Lab.shape by console', values: { shape: 'custom' }, expect: 'PASS' },
  { name: 'g1 sign dial on Standard', values: { dial: 'sign right' }, expect: 'REFUSED:Standard has no dial sign or [RD] block' },
  // Built as a player builds (stage 5): by preview, and every block by hand.
  { name: 'g1 built by preview', values: { built: 'preview' }, expect: 'PASS' },
  { name: 'g1 built by hand', values: { built: 'hand' }, expect: 'PASS' },
  { name: 'g1 Grand by preview', values: { shape: 'Grand', built: 'preview' }, expect: 'PASS' },
  { name: 'g1 Horizontal by hand', values: { shape: 'Horizontal', built: 'hand' }, expect: 'PASS' },
  { name: 'g1 by preview in Atlantis', values: { built: 'preview', group: 'Atlantis' }, expect: 'PASS' },
  { name: 'g1 by hand in Diamond', values: { built: 'hand', group: 'Diamond' }, expect: 'PASS' },
  { name: 'g1 group Diamond', values: { group: 'Diamond' }, expect: 'PASS' },
  { name: 'g1 Massive by hand', values: { shape: 'Massive', built: 'hand' }, expect: 'REFUSED:by hand only the small shapes: Large, Grand and Massive take hundreds of blocks' },
  // The sign-dial shapes and Lab.shape, built by a player, dialled by their signs and redstone.
  { name: 'g1 StandardSignDial by hand, sign right', values: { shape: 'StandardSignDial', built: 'hand', dial: 'sign right' }, expect: 'PASS' },
  { name: 'g1 StandardSignDial by preview, sign left', values: { shape: 'StandardSignDial', built: 'preview', dial: 'sign left' }, expect: 'PASS' },
  { name: 'g1 MinimalSignDial by preview, redstone', values: { shape: 'MinimalSignDial', built: 'preview', dial: 'redstone' }, expect: 'PASS' },
  { name: 'g1 HorizontalSignDial by hand, console dial', values: { shape: 'HorizontalSignDial', built: 'hand' }, expect: 'PASS' },
  { name: 'g1 Lab.shape by preview, sign right', values: { shape: 'custom', built: 'preview', dial: 'sign right' }, expect: 'PASS' },
  { name: 'g1 Lab.shape by hand, [RS] and [RD]', values: { shape: 'custom', built: 'hand', dial: 'redstone' }, expect: 'PASS' },
  { name: 'g1 Lab.shape to the Range', values: { shape: 'custom', built: 'preview', destination: 'Range' }, expect: 'PASS' },
  { name: 'g1 sign gate\'s DHD and /dial', values: { shape: 'StandardSignDial', built: 'preview', dial: 'dhd' },
    expect: 'REFUSED:a sign-dial gate\'s DHD dials what its sign shows: the sign rows are that' },
  // The chevron light order (G8) is checked on every dial above; these add the spins.
  { name: 'g1 spin none', values: { spin: 'none' }, expect: 'PASS' },
  { name: 'g1 spin universe', values: { spin: 'universe' }, expect: 'PASS' },
  { name: 'g1 chevron order by DHD to the Range', values: { dial: 'dhd', destination: 'Range' }, expect: 'PASS' },
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
  // Known failures (#537: items flying through between two entity sweeps) until main's #543.
  ...['dropped item', 'dispensed item'].map((t) => ({ name: `g1 ${t}`, values: { traveller: t }, expect: 'PASS' })),
  // Every projectile, and the launchers and angles.
  ...['arrow', 'spectral arrow', 'bolt', 'piercing bolt', 'firework', 'snowball', 'egg', 'pearl',
    'splash potion', 'lingering potion'].map((p) => ({ name: `g1 ${p}`, values: P(p), expect: 'PASS' })),
  // Thrown in survival, so the trident is used up. Both were #536's known failures (a plain trident
  // or arrow re-made at the far gate) until main's #542; on Spigot, with no loyalty API, a trident
  // keeps its enchantments but cannot return, so there it would fail again.
  { name: 'g1 trident', values: P('trident'), expect: 'PASS' },
  { name: 'g1 tipped arrow', values: P('tipped arrow'), expect: 'PASS' },
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
  {
    name: '491-D boat rowed at the shut iris',
    values: { traveller: 'cart at iris', shape: 'Massive', 'own iris': 'shut', vehicle: 'boat', rider: 'Probe' },
    expect: "FAIL:the cart's front never crossed the iris face",
    known: '#491 D: a boat its rider rows at a shut drawn iris is let 2.1 blocks past the face (to z -129.1 from a face at -127) before it is put back; unchanged by the fix at 7ebc95f4 and 41aaaabf',
  },
  { name: '491-F control: iris open', values: { traveller: 'cart at iris', shape: 'Massive', 'own iris': 'open' }, expect: 'PASS' },
  {
    name: '491-H control: Horizontal, iris shut',
    values: { traveller: 'cart at iris', shape: 'Horizontal', 'own iris': 'shut', approach: 'behind' },
    expect: 'PASS',
    regressedBy: '491',
    regression: 'FAIL:and never sank into the opening',
    regressionNote: 'the #491 fix (7ebc95f4, 41aaaabf) puts a cart that rolls onto a flat gate\'s shut real-block iris down inside the opening, at y -1 (0.5, -1, -128.5); on main it rolls over',
  },
  { name: '491-H control: Horizontal, iris open', values: { traveller: 'cart at iris', shape: 'Horizontal', 'own iris': 'open', approach: 'behind' }, expect: 'PASS' },
];

const R1 = [
  { name: 'r1 walk ODD by console', values: {}, expect: 'PASS' },
  { name: 'r1 walk EVEN', values: { pattern: 'EVEN' }, expect: 'PASS' },
  ...['oak_slab', 'stone_brick_slab', 'andesite_slab', 'prismarine_slab', 'cut_copper_slab']
    .map((slab) => ({ name: `r1 walk ${slab}`, values: { slab }, expect: 'PASS' })),
  { name: 'r1 walk 40 apart', values: { distance: '40' }, expect: 'PASS' },
  { name: 'r1 built by Probe', values: { built: 'player' }, expect: 'PASS' },
  { name: 'r1 swap', values: { traveller: 'swap' }, expect: 'PASS' },
  { name: 'r1 horse', values: { traveller: 'horse' }, expect: 'PASS' },
  { name: 'r1 zombie', values: { traveller: 'zombie' }, expect: 'PASS' },
  { name: 'r1 item', values: { traveller: 'item' }, expect: 'PASS' },
  { name: 'r1 minecart', values: { traveller: 'minecart' }, expect: 'PASS' },
  { name: 'r1 stranger refused', values: { built: 'player', access: 'stranger' }, expect: 'PASS' },
  { name: 'r1 allowed', values: { built: 'player', access: 'allowed' }, expect: 'PASS' },
  { name: 'r1 slow style', values: { timing: 'slow' }, expect: 'PASS' },
  { name: 'r1 quick countdown', values: { timing: 'quick' }, expect: 'PASS' },
  { name: 'r1 access on a console pair', values: { access: 'stranger' }, expect: 'REFUSED:the access rows are about a private pair, which a player builds' },
];

const B1 = [
  { name: 'b1 N public', values: {}, expect: 'PASS' },
  { name: 'b1 E own place', values: { destination: 'E', kind: 'own place' }, expect: 'PASS' },
  { name: 'b1 S another\'s place', values: { destination: 'S', kind: 'another\'s place' }, expect: 'PASS' },
  { name: 'b1 W by go', values: { destination: 'W', kind: 'go' }, expect: 'PASS' },
  { name: 'b1 Trap', values: { destination: 'Trap' }, expect: 'PASS' },
  { name: 'b1 Blocked', values: { destination: 'Blocked' }, expect: 'PASS' },
  { name: 'b1 Far (nether)', values: { destination: 'Far' }, expect: 'PASS' },
  { name: 'b1 End', values: { destination: 'End' }, expect: 'PASS' },
  { name: 'b1 op', values: { traveller: 'op' }, expect: 'PASS' },
  { name: 'b1 horse', values: { traveller: 'horse' }, expect: 'PASS' },
  { name: 'b1 wolf', values: { destination: 'E', traveller: 'wolf' }, expect: 'PASS' },
  { name: 'b1 long', values: { timing: 'long' }, expect: 'PASS' },
  { name: 'b1 late teleport', values: { timing: 'late teleport' }, expect: 'PASS' },
  { name: 'b1 clamped teleport', values: { timing: 'clamped' }, expect: 'PASS' },
  { name: 'b1 clamped vanish', values: { timing: 'clamped vanish' }, expect: 'PASS' },
  { name: 'b1 another\'s place as op', values: { kind: 'another\'s place', traveller: 'op' }, expect: 'REFUSED:another\'s place is asked for by Probe2 (the player)' },
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
  r1: R1,
  r2: [
    ...['6', '10'].map((drop) => ({ name: `r2 ceiling ${drop} up`, values: { drop }, expect: 'PASS' })),
    { name: 'r2 ceiling 6 up, EVEN', values: { drop: '6', pattern: 'EVEN' }, expect: 'PASS' },
    { name: 'r2 ceiling 11 up refused', values: { drop: '11' }, expect: 'PASS' },
    { name: 'r2 ceiling 2 up refused', values: { drop: '2' }, expect: 'PASS' },
  ],
  r3: [
    ...['20', '40', '60'].map((depth) => ({ name: `r3 shaft ${depth} down`, values: { depth }, expect: 'PASS' })),
    { name: 'r3 20 down, reach 30', values: { depth: '20', 'max height': '30' }, expect: 'PASS' },
    { name: 'r3 40 down, reach 30: refused', values: { depth: '40', 'max height': '30' }, expect: 'PASS' },
  ],
  r4: ['pair', 'cancel', 'mixed slabs', 'mixed halves', 'double slab', 'filled disc', 'low ceiling', 'built inside', 'hole in floor']
    .map((c) => ({ name: `r4 ${c}`, values: { case: c }, expect: 'PASS' })),
  r5: ['name', 'light', 'private', 'allow', 'deny', 'owner'].map((edit) => ({ name: `r5 ${edit}`, values: { edit }, expect: 'PASS' })),
  tunnel: ['64', '128', '250', '257'].map((d) => ({ name: `tunnel ${d} apart${d === '257' ? ': refused' : ''}`, values: { distance: d }, expect: 'PASS' })),
  b1: B1,
  m1: [
    ...['Annex', 'Optics', 'Ops', 'Range'].map((to) => ({ name: `m1 round to ${to}`, values: { to }, expect: 'PASS' })),
    { name: 'm1 -start Range, round to Optics', values: { to: 'Optics', start: 'Range' }, expect: 'PASS' },
    { name: 'm1 an op to the Range', values: { to: 'Range', traveller: 'op' }, expect: 'PASS' },
    { name: 'm1 the three-second hold', values: { to: 'Ops', hold: 'watched' }, expect: 'PASS' },
    { name: 'm1 approach message off', values: { to: 'Annex', approach: 'off' }, expect: 'PASS' },
    { name: 'm1 one mirror per world', values: { limit: 'one per world' }, expect: 'PASS' },
    { name: 'm1 limit row with a trip', values: { limit: 'one per world', to: 'Ops' }, expect: 'REFUSED:the limit row only makes a mirror; nothing travels' },
  ],
  m2: ['solid', 'gap one out', 'gap two out', 'on a post', 'two wide', 'protected', 'remove', 'limit']
    .map((c) => ({ name: `m2 ${c}`, values: { case: c }, expect: 'PASS' })),
  m3: ['capture', 'stamp a look', 'stamp from the room'].map((c) => ({ name: `m3 ${c}`, values: { case: c }, expect: 'PASS' })),
  b2: ['send to public', 'send to player', 'send to coordinates', 'send to nobody', 'goto as op', 'goto as player', 'set as player',
    'own place', 'another\'s place', 'go to a gate as op', 'go to a gate as player', 'cooldown', 'op skips cooldown']
    .map((action) => ({ name: `b2 ${action}`, values: { action }, expect: 'PASS' })),
  // Stage 5: the Automation Bay, the Build Bench (preview actions, building by hand) and the Iris Chamber.
  g3: [
    ...['lever', 'button', 'plate', 'repeater', 'comparator'].map((i) => ({ name: `g3 ${i}`, values: { input: i }, expect: 'PASS' })),
    {
      name: 'g3 detector rail',
      values: { input: 'detector rail' },
      // Paper 1.21.11's detector rail reports its BlockRedstoneEvent with the new current as the old
      // one as well (DetectorRailBlock.checkPressed). Seen there only; 1.20.4 and 26.1.2 report 0
      // then 15 by their source, and no other 1.21.x has been run.
      expect: (v) => (v === '1.21.11' ? 'FAIL:the detector rail dialled Bay: its opening was drawn' : 'PASS'),
      known: 'a detector rail by the DHD never dials on Paper 1.21.11: Paper raises its BlockRedstoneEvent with old current 15 as well as new (DetectorRailBlock.checkPressed passes the new state twice), and the redstone listener takes only a rise from 0 (WormholeXTremeRedstoneListener.isActionableRisingEdge), so the rail the plugin documents as a trigger is dropped there',
    },
    ...['gate build', 'gate dial', 'gate force', 'ring build', 'ring fire', 'mirror create', 'beam send']
      .map((k) => ({ name: `g3 command block: ${k}`, values: { input: 'console', console: k }, expect: 'PASS' })),
    { name: 'g3 timeout 0: open until somebody goes through', values: { input: 'lever', timing: 'timeout 0' }, expect: 'PASS' },
    { name: 'g3 held open by presses, never past max-open', values: { input: 'button', timing: 'hold' }, expect: 'PASS' },
    { name: 'g3 hold with a lever', values: { input: 'lever', timing: 'hold' }, expect: 'REFUSED:the hold is a button pressed again and again' },
  ],
  g4: [
    ...['hand', 'layer', 'chevrons', 'dhd', 'material', 'iris', 'activate', 'share', 'place', 'fill a gap', 'clear']
      .map((k) => ({ name: `g4 ${k}`, values: { case: k }, expect: 'PASS' })),
    {
      name: 'g4 lenient [S:C] chevron in the frame block',
      values: { case: 'lenient' },
      expect: 'FAIL:the guide said "Lab is built! Press its button to check it."',
      known: 'the build guide takes a lenient [S:C] chevron for a strict [C]: `needs` asks for the chevron block only and the guide marks the frame block there wrong (BuildGuide.accepts, CHEVRON), though detection takes either (StargateHelper.cellMatches), so a gate built so completes but is never "built!"',
    },
  ],
  g5: [
    { name: 'g5 sweep steps, IrisS', values: { check: 'steps' }, expect: 'PASS' },
    { name: 'g5 sweep steps, IrisA', values: { check: 'steps', gate: 'Atlantis' }, expect: 'PASS' },
    ...['spiral', 'rows', 'columns', 'instant'].map((a) => ({ name: `g5 ${a} steps`, values: { check: 'steps', animation: a }, expect: 'PASS' })),
    { name: 'g5 steps at 4 ticks', values: { check: 'steps', 'step ticks': '4' }, expect: 'PASS' },
    { name: 'g5 steps merged to two bands', values: { check: 'steps', 'max ticks': '4' }, expect: 'PASS' },
    { name: 'g5 steps with no band limit', values: { check: 'steps', 'max ticks': '0' }, expect: 'PASS' },
    {
      name: 'g5 the puller watches the sweep',
      values: { check: 'steps', watcher: 'puller' },
      expect: "FAIL:closing was drawn in the plugin's sweep steps, cell for cell",
      known: 'the player who pulls the iris lever is shown the iris whole a tick later: their arm swing near a drawn iris redraws it (WormholeXTremePlayerListener.onPlayerAnimation, StargateManager.redrawPortalVisualsSoon, StargateBlockSetup.sendIrisTo), which does not ask whether a sweep is under way (StargateIrisAnimator.isSweeping), so they never see it close step by step',
    },
    ...['front', 'behind', 'side'].map((s) => ({ name: `g5 layers from ${s === 'side' ? 'the side' : s === 'front' ? 'the front' : 'behind'}, IrisS`, values: { check: 'layers', side: s }, expect: 'PASS' })),
    { name: 'g5 layers from the front, IrisA (ice behind glass)', values: { check: 'layers', gate: 'Atlantis' }, expect: 'PASS' },
    { name: 'g5 layers from behind, IrisA', values: { check: 'layers', gate: 'Atlantis', side: 'behind' }, expect: 'PASS' },
    { name: 'g5 arrow at the shut iris, IrisS', values: { check: 'arrow' }, expect: 'PASS' },
    { name: 'g5 arrow at the shut iris, IrisA', values: { check: 'arrow', gate: 'Atlantis' }, expect: 'PASS' },
    { name: 'g5 arrow at the open iris', values: { check: 'arrow', iris: 'open' }, expect: 'PASS' },
    { name: 'g5 block in the shut opening, IrisS', values: { check: 'place' }, expect: 'PASS' },
    { name: 'g5 block in the shut opening, IrisA', values: { check: 'place', gate: 'Atlantis' }, expect: 'PASS' },
    { name: 'g5 side row off the layers check', values: { check: 'arrow', side: 'behind' }, expect: 'REFUSED:the side row is the layers check' },
  ],
  // S1, the Systems console: the settings and permissions audits (chambers/s1-systems.js).
  s1: require('./chambers/s1-systems').CASES.map((c) => ({ name: `s1 ${c.value}`, values: { case: c.value }, expect: 'PASS' })),
};

/**
 * The --quick profile: one of each kind of thing, for a check across versions in a few minutes
 * (text formats, entity ids, boats, the teleport quirk) rather than the whole matrix.
 */
const QUICK = new Set([
  'g1 walk Standard by console', 'g1 minecart', 'g1 horse', 'g1 wolf', 'g1 arrow', 'g1 snowball',
  'g1 arrow from a dispenser', 'g1 dropped item', 'g1 dial itself', '491-A cart from the front',
  'g2 Standard to its neighbour',
  'r1 walk ODD by console', 'r1 swap', 'r2 ceiling 11 up refused', 'r3 shaft 20 down', 'r4 mixed slabs', 'r5 name',
  'tunnel 257 apart: refused', 'b1 N public', 'b1 Trap', 'b2 send to public', 'b2 cooldown',
  'm1 round to Range', 'm1 the three-second hold', 'm2 gap one out', 'm3 capture', 'm3 stamp a look', 'g1 horse to the Range', 'b1 End',
  'g1 built by hand', 'g1 Lab.shape by preview, sign right', 'g3 lever', 'g3 command block: gate build', 'g4 activate', 'g4 share', 'g5 sweep steps, IrisS',
  'g5 layers from the front, IrisA (ice behind glass)', 'g5 arrow at the shut iris, IrisS',
]);
for (const [id, cells] of Object.entries(MATRIX)) {
  for (const cell of cells) if (id === 'c0' || QUICK.has(cell.name)) cell.quick = true;
}

// The companion desks' cells (companion-matrix.js), each marked `with` or `without`.
Object.assign(MATRIX, require('./companion-matrix').CELLS);

/**
 * Whether a cell belongs in this run: one marked `with` only when every companion it names is
 * installed; one marked `without` (the paired run: the plugin must do without that companion)
 * only when --with was given and none it names is. `companions` is the --with list, or null for
 * a run without --with, which runs neither kind: the default self-test is what it always was.
 */
function applies(cell, companions) {
  if (cell.with) return Boolean(companions) && cell.with.every((c) => companions.includes(c));
  if (cell.without) return Boolean(companions) && !cell.without.some((c) => companions.includes(c));
  return true;
}

module.exports = { MATRIX, QUICK, defaultsOf, expectation, applies };
