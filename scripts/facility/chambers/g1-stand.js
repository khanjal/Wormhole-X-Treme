'use strict';
// G1, the Test Stand (design 3.1): the gate under test, built by the console form in the middle
// of the cell facing south, dialled to a far gate (Relay across the hall, Range in the nether,
// Annex in the End), and a traveller sent through. Every check is a fact recorded during the
// run: where Probe is, what its client was shown, what the tick function caught at the far
// exit, what the plugin said. A check never passes because nothing threw.
//
// Also here: the cart-at-a-shut-iris cells for issue #491 (traveller "cart at iris"); the Stand
// built as a player builds one (by preview, or every block by hand: lib/gatebuild.js), which
// makes the sign-dial shapes and the Lab.shape test asset buildable, and their sign and redstone
// dials; and the chevron light order (G8): every dial with chevrons is watched from Probe's
// client, and the chevrons must lock in the order of the shape's :L#n cells.

const campus = require('../lib/campus');
const { GateKit, WORLDS } = require('../lib/gatekit');
const shapes = require('../lib/shapes');
const { cellLayout } = require('../lib/blueprint');
const { ARMOURY, itemNbt, offlineUuidInts, exists, hangingAt } = require('../lib/menagerie');
const { atLeast } = require('../lib/version');
const { ticks } = require('../lib/probe');
const { GateBuilder } = require('../lib/gatebuild');
const relay = require('./relay');

const def = campus.chamber('g1');
const O = campus.OVERWORLD;
const STAND = 'Stand';
const IDC_NEAR = '1111';
const IDC_FAR = '2222';
const PLACE = campus.GATES.stand;

const v = (value, why, label = value) => ({ value, label, why });

const OPTIONS = {
  shape: [
    v('Standard', 'the 7x7 ring'), v('Large', '10x10'), v('Grand', '22x22, three layers deep'), v('Massive', '23x23, the widest'),
    v('Minimal', 'a 1x2 opening'), v('Horizontal', 'flat in the floor: you drop into it'),
    v('StandardSignDial', 'dials by sign; the console cannot build it'), v('MinimalSignDial', 'dials by sign'),
    v('HorizontalSignDial', 'flat, dials by sign'),
    v('custom', 'Lab.shape (scripts/facility/assets): [C], [S:C] and [RS] cells, a diamond frame, dials by sign'),
  ],
  group: [v('default', 'the first group in config.yml (Standard, obsidian)'), v('Atlantis', 'lapis; drawn by `edit group`'),
    v('Universe', 'polished blackstone'), v('MilkyWay', 'deepslate'), v('Diamond', 'the group the plugin derives from Lab.shape\'s diamond frame')],
  chevrons: [v('frame', 'chevrons built of the frame block'), v('lamp', 'chevron cells replaced by redstone lamps'),
    v('copper bulb', 'copper bulbs (1.21+)')],
  built: [v('console', '`gate build ... x y z facing`'), v('preview', 'as a player: `gate build <shape>`, `gate preview place`, `gate complete`'),
    v('hand', 'as a player: every block `gate preview needs` lists, the button, `gate complete`')],
  dial: [v('console', '`gate dial Stand <to>`'), v('dhd', 'press the DHD button, then /dial'),
    v('sign right', 'right-click the dial sign on to the destination, then press the DHD'),
    v('sign left', 'left-click the dial sign back to the destination, then press the DHD'),
    v('redstone', 'set the sign (Lab.shape: by pulses on [RS]), then pull a lever by [RD]')],
  spin: [v('default', 'the group\'s pattern'), ...['top', 'chevron', 'lap', 'fill', 'pegasus', 'chase', 'universe', 'overshoot', 'none'].map((p) => v(p, `\`edit spin ${p}\``))],
  destination: [v('Relay', 'across the hall'), v('Range', 'the nether'), v('Annex', 'the End'),
    v('busy', 'Relay already dialled elsewhere: refused'), v('self', 'dial the Stand itself: refused')],
  traveller: [
    v('walk', 'Probe on foot'), v('minecart', 'an empty cart on rails'), v('minecart ridden', 'Probe in the cart'),
    v('boat', 'an empty boat on blue ice'), v('boat ridden', 'Probe rows it'),
    ...['horse', 'camel', 'pig', 'donkey', 'strider'].map((k) => v(k, `Probe riding a ${k}`)),
    v('llama', 'walks in riderless and is swept through: a rider cannot steer a llama'),
    v('wolf', 'a tamed wolf follows Probe'), v('cat', 'a tamed cat follows'), v('parrot', 'a tamed parrot follows'),
    v('sitting wolf', 'told to sit: must stay'), v('projectile', 'see projectile, launcher and angle'),
    v('dropped item', 'Probe drops a named, enchanted sword into the opening'), v('dispensed item', 'a dispenser drops it in'),
    v('lying item', 'a named, enchanted sword lying still in the opening'),
    v('spilled items', 'a hopper cart broken in the opening (its drops scatter a little)'), v('xp', 'an experience orb'), v('armour stand', 'swept through'),
    v('item frame', 'hung by the opening: must not move'), v('zombie', 'swept through'),
    v('cart at iris', 'issue #491: a cart run at a shut iris must stop flush at its face'),
  ],
  projectile: [
    v('arrow', 'from a bow'), v('spectral arrow', 'glowing'), v('tipped arrow', 'slowness: the effect must survive'),
    v('bolt', 'from a crossbow'), v('piercing bolt', 'crossbow with Piercing IV'), v('firework', 'a rocket from a crossbow'),
    v('trident', 'Loyalty III: must come back'), v('snowball', 'thrown'), v('egg', 'thrown'), v('pearl', 'the thrower goes with it'),
    v('splash potion', 'slowness must survive'), v('lingering potion', 'slowness must survive'),
    v('fireball', 'a fire charge from a dispenser'), v('wind charge', '1.21+'), v('llama spit', 'spawned moving'),
  ],
  launcher: [v('bot', 'Probe shoots'), v('dispenser', 'a dispenser in front, no player'), v('tester', 'you: the run waits for your shot')],
  angle: [v('square', 'straight at the opening'), v('glancing', '30 degrees off its normal'), v('above', 'down into a flat gate'),
    v('below', 'up into a flat gate'), v('point-blank', 'one block from the opening')],
  'far iris': [v('open', 'no code'), v('shut with code', 'dialled with the code: opens'), v('shut no code', 'dial refused'),
    v('shut after dial', 'shut once the wormhole is open: travellers are stopped')],
  'own iris': [v('open', ''), v('shut', 'the Stand\'s own iris shut once open')],
  portal: [v('group', 'the group\'s portal'), v('LAVA', '`edit portal LAVA`'), v('NETHER_PORTAL', ''), v('WATER', '')],
  'iris gate': [v('idle', 'cart at iris: the Stand is idle'), v('incoming', 'cart at iris: the Stand is the far end of an open wormhole from Relay')],
  approach: [v('front', 'cart at iris: from the DHD side'), v('behind', 'cart at iris: from the back')],
  rider: [v('none', 'cart at iris: empty'), v('Probe', 'cart at iris: Probe rides it')],
  vehicle: [v('cart', 'cart at iris: a minecart on rails'), v('boat', 'cart at iris: a boat Probe rows (#491 D)')],
  'cart speed': [v('0.4', 'full speed on rails (blocks per tick)'), v('0.2', 'half speed')],
  'run-up': [v('6', 'rails in front of the face'), v('10', ''), v('16', '')],
};

const MOUNTS = ['horse', 'camel', 'pig', 'donkey', 'strider'];
// How many times a tossed or dispensed item is sent (see sweep()).
const TOSSES = 5;
const PETS = { wolf: 'wolf', cat: 'cat', parrot: 'parrot', 'sitting wolf': 'wolf' };
const SIGN_SHAPES = ['StandardSignDial', 'MinimalSignDial', 'HorizontalSignDial'];
// The shapes a person lays by hand in a minute or two; Large and up take hundreds of blocks.
const TOO_BIG_FOR_HANDS = ['Large', 'Grand', 'Massive'];

/** The plugin's name of the shape an option value stands for: `custom` is the Lab.shape asset. */
function shapeOf(o) {
  return o.shape === 'custom' ? 'Lab' : o.shape;
}

/** Whether the shape dials by sign (a :D block): the three SignDial shapes and Lab.shape. */
function signDial(o) {
  return SIGN_SHAPES.includes(o.shape) || o.shape === 'custom';
}

/** The group a player builds in: the option's, or Lab.shape's own Diamond; null for the default. */
function buildGroup(o) {
  if (o.group !== 'default') return o.group;
  return o.shape === 'custom' ? 'Diamond' : null;
}
const EXPECTED_REFUSAL = {
  self: /Can't dial own gate without solar flare/,
  busy: /Target gate is currently active\./,
  'shut no code': /Remote Iris is active; provide the IDC to unlock\./,
};

/** What a combination cannot be, with the reason; the self-test expects REFUSED:<reason>. */
function refuses(o, version = '26.1.2') {
  const flat = shapes.isFlat(shapeOf(o));
  if (o.built === 'hand' && TOO_BIG_FOR_HANDS.includes(o.shape)) return 'by hand only the small shapes: Large, Grand and Massive take hundreds of blocks';
  if (o.chevrons === 'copper bulb' && !atLeast(version, '1.21')) return 'there are no copper bulbs before 1.21';
  if (['sign right', 'sign left', 'redstone'].includes(o.dial) && !signDial(o)) return `${o.shape} has no dial sign or [RD] block`;
  if (signDial(o) && o.built !== 'console') {
    if (o.dial === 'dhd') return 'a sign-dial gate\'s DHD dials what its sign shows: the sign rows are that';
    if (o.destination === 'self') return 'a dial sign never offers its own gate';
    if (['busy'].includes(o.destination) && o.dial !== 'console') return 'a busy Relay is refused the same by any dial: the console row is it';
    if (o.shape === 'custom' && (o['own iris'] === 'shut' || o.traveller === 'cart at iris')) return 'Lab.shape has no iris lever';
  }
  if (['busy', 'self'].includes(o.destination) && o['far iris'] !== 'open') return 'the dial is refused anyway; nothing for an iris to stop';
  const t = o.traveller;
  if (t === 'cart at iris') {
    if (o.vehicle === 'boat' && o.rider !== 'Probe') return 'an empty boat stops within two blocks of a push; the boat is rowed by Probe';
    // A flat gate's DHD button, name sign and lever lie in the floor in front of it, so its
    // cart comes from behind.
    if (flat && (o.vehicle === 'boat' || o.approach !== 'behind' || o['iris gate'] !== 'idle')) return 'the Horizontal control is a cart from behind at an idle gate';
    if (o['iris gate'] === 'incoming' && o['own iris'] === 'open') return 'the open-iris control is an idle gate';
    return null;
  }
  if (o['own iris'] === 'shut' && !['walk', 'minecart', 'boat'].includes(t)) return 'an own-iris run sends a walker, a cart or a boat';
  if (flat && !['walk', 'projectile', 'dropped item'].includes(t)) return 'into a flat gate only on foot, by dropping, or shooting from above';
  if (t === 'projectile') {
    const p = o.projectile;
    if (p === 'wind charge' && !exists(version, 'wind_charge')) return 'there are no wind charges before 1.21';
    if (o.launcher === 'bot' && p === 'fireball') return 'a player cannot throw a fireball; the dispenser can';
    if (o.launcher === 'bot' && p === 'llama spit') return 'only a llama spits; the dispenser stand-in spawns it moving';
    if (o.launcher === 'dispenser' && ['trident', 'pearl', 'bolt', 'piercing bolt'].includes(p)) return `a dispenser cannot fire a ${p}`;
    if (o.launcher === 'dispenser' && o.angle === 'glancing') return 'a dispenser shoots only along an axis';
    if (o.angle === 'below') return 'there is no room under the floor for a shot from below';
    if (o.angle === 'above' && !flat) return 'a shot from above needs a flat gate';
    if (o.angle !== 'above' && flat) return 'a flat gate is shot into from above';
  }
  return null;
}

// ---- helpers -------------------------------------------------------------------------------

function kitOf(ctx) {
  return new GateKit(ctx.server);
}

function destOf(o) {
  return ['Range', 'Annex'].includes(o.destination) ? o.destination : 'Relay';
}

function farOf(o) {
  return relay.farGates()[destOf(o)];
}

async function run(ctx, command) {
  const r = await ctx.server.run(command);
  if (r.errors.length) throw new Error(`${command}: ${r.errors.join(' ')}`);
  return r;
}

/** The point `d` blocks out in front of the Stand's opening, on the floor, facing it. */
function front(geom, d, side = 0) {
  return {
    x: geom.centre.x + geom.normal.x * d + geom.right.x * side,
    y: PLACE.floorY,
    z: Math.floor(geom.opening[0].z) + 0.5 + geom.normal.z * d + geom.right.z * side,
    yaw: (geom.yaw + 180) % 360,
  };
}

/** The plain text of a sign's front as the server holds it (every version's form: » « survive). */
async function signText(ctx, at) {
  const r = await ctx.server.run(`data get block ${at.x} ${at.y} ${at.z} front_text.messages`);
  return r.lines.join(' ').replace(/§./g, '');
}

/** The destination a dial sign shows (its »Target« line), or null. */
function signShows(text) {
  const m = /»([^«]+)«/.exec(text || '');
  return m ? m[1] : null;
}

/**
 * A spot for a lever by a redstone marker: air on a solid floor within the block round `at` the
 * plugin listens to, off the gate's own cells, and as far as it can be from `awayFrom` (the other
 * marker, whose block round it must not reach).
 */
async function leverSpot(ctx, geom, at, awayFrom = null) {
  const taken = new Set([...geom.frame, ...geom.opening, geom.button, ...(geom.sign ? [geom.sign] : []),
    ...Object.values(geom.redstone)].map((c) => `${c.x},${c.y},${c.z}`));
  const spots = [];
  for (const dx of [-1, 0, 1]) {
    for (const dz of [-1, 0, 1]) {
      for (const dy of [-1, 0, 1]) {
        const p = { x: at.x + dx, y: at.y + dy, z: at.z + dz };
        if (taken.has(`${p.x},${p.y},${p.z}`)) continue;
        if (awayFrom && Math.max(Math.abs(p.x - awayFrom.x), Math.abs(p.y - awayFrom.y), Math.abs(p.z - awayFrom.z)) <= 1) continue;
        // Air on a full block (a lever on the floor needs one: not on dust or a button), as Probe's client sees it.
        const { Vec3 } = require('vec3');
        const here = ctx.probe.bot.blockAt(new Vec3(p.x, p.y, p.z));
        const below = ctx.probe.bot.blockAt(new Vec3(p.x, p.y - 1, p.z));
        if (here && here.name === 'air' && below && below.boundingBox === 'block') spots.push(p);
      }
    }
  }
  if (!spots.length) throw new Error(`no room for a lever by ${at.x} ${at.y} ${at.z}`);
  const far = (p) => (awayFrom ? Math.abs(p.x - awayFrom.x) + Math.abs(p.z - awayFrom.z) : 0);
  return spots.sort((a, b) => far(b) - far(a))[0];
}

/**
 * Watches the Stand's :L cells on Probe's client from now on; `stop()` returns, per light wave
 * (L#n), the time its cells were last shown turning lit, and whether they were lit at the end.
 */
function watchLights(ctx, geom) {
  const { Vec3 } = require('vec3');
  const bot = ctx.probe.bot;
  const base = new Map();
  for (const l of geom.lights) {
    const b = bot.blockAt(new Vec3(l.x, l.y, l.z));
    base.set(`${l.x},${l.y},${l.z}`, b ? b.stateId : null);
  }
  const litAt = new Map();
  const isLit = (key, b) => b && b.stateId !== base.get(key);
  const onUpdate = (old, b) => {
    if (!b) return;
    const key = `${b.position.x},${b.position.y},${b.position.z}`;
    if (!base.has(key)) return;
    const was = old ? isLit(key, old) : false;
    if (isLit(key, b) && !was) litAt.set(key, Date.now());
  };
  bot.on('blockUpdate', onUpdate);
  return {
    stop() {
      bot.off('blockUpdate', onUpdate);
      const waves = {};
      for (const l of geom.lights) {
        const key = `${l.x},${l.y},${l.z}`;
        const b = bot.blockAt(new Vec3(l.x, l.y, l.z));
        const w = waves[l.order] || (waves[l.order] = { order: l.order, at: 0, cells: 0, lit: 0 });
        w.cells++;
        if (isLit(key, b)) w.lit++;
        w.at = Math.max(w.at, litAt.get(key) || 0);
      }
      return Object.values(waves).sort((a, b) => a.order - b.order);
    },
  };
}

/** Is an entity with this selector-body within `r` of a point in `dim`? */
async function near(ctx, dim, at, body, r = 4) {
  const q = await ctx.server.run(`execute in ${dim} positioned ${at.x} ${at.y} ${at.z} if entity @e[${body},distance=..${r}]`);
  return q.lines.some((l) => /Test passed/.test(l));
}

/** Polls (bounded) until `test` is true; returns whether it became true. */
async function until(test, ms, every = 5) {
  const end = Date.now() + ms;
  for (;;) {
    if (await test()) return true;
    if (Date.now() > end) return false;
    await ticks(every);
  }
}

function chatSince(ctx, mark) {
  return ctx.observed.chat.slice(mark).join(' / ');
}

// ---- stage -------------------------------------------------------------------------------

async function stage(ctx, o) {
  const kit = kitOf(ctx);
  const obs = ctx.observed;
  // Everything Probe is told during the run, for the checks on the plugin's messages. One
  // listener for the session, pointed at this run's list.
  obs.chat = [];
  ctx.probe.chatSink = obs.chat;
  if (!ctx.probe.chatListening) {
    // Console feedback broadcast to ops ([Server: ...]) is not the plugin talking to Probe.
    ctx.probe.bot.on('message', (m) => {
      const line = m.toString();
      if (ctx.probe.chatSink && !line.startsWith('[Server:')) ctx.probe.chatSink.push(line);
    });
    ctx.probe.chatListening = true;
  }
  // The far gates are fixtures; make sure they are there, idle, and have open irises.
  const far = relay.farGates();
  for (const g of Object.values(far)) if (!(await kit.exists(g.name))) await relay.fixture(ctx);
  for (const g of Object.values(far)) { await kit.force(g.name); await kit.edit(g.name, 'idc', '-clear'); }
  // A far world's arrival is forceloaded; wait until it answers as loaded all the same, since a
  // trip into a chunk still being brought in is a harness flake, not a plugin failure.
  if (['Range', 'Annex'].includes(o.destination)) {
    const a = farOf(o).geom.arrival;
    await ctx.server.waitLoaded(farOf(o).dim, [[Math.floor(a.x), Math.floor(a.y), Math.floor(a.z)]], 30000);
  }
  if (await kit.exists(STAND)) await kit.remove(STAND);

  const geom = kit.place(shapeOf(o), PLACE.facing, PLACE);
  obs.geom = geom;
  const needsIdc = o['own iris'] === 'shut' || o.traveller === 'cart at iris';
  if (o.built === 'console') {
    try {
      const built = await kit.build(STAND, geom, { dim: O, idc: needsIdc ? IDC_NEAR : null, floorY: PLACE.floorY });
      obs.build = built.text;
    } catch (err) {
      obs.build = err.message;
      obs.buildRefused = true;
      if (signDial(o)) return; // the plugin's own refusal: judged by the checks
      throw err;
    }
    if (o.group !== 'default') obs.group = (await kit.edit(STAND, 'group', o.group)).text;
  } else {
    // As a player: in the group chosen (the preview is dressed in it), so no `edit group`.
    const builder = new GateBuilder(ctx.server, ctx.probe);
    const how = { group: buildGroup(o), idc: needsIdc ? IDC_NEAR : null, dim: O, floorY: PLACE.floorY };
    const r = o.built === 'hand'
      ? await builder.byHand(STAND, geom, { ...how, group: how.group || 'Standard' })
      : await builder.byPreview(STAND, geom, how);
    obs.built = { said: r.said, laid: r.laid || null, needs: r.needs || null, retries: builder.retries || 0 };
    obs.build = r.said.complete || '';
    if (!r.ok) { obs.buildFailed = true; return; }
    if (geom.sign) obs.signAfterBuild = await signText(ctx, geom.sign);
  }
  if (o.spin !== 'default') obs.spin = (await kit.edit(STAND, 'spin', o.spin)).text;
  if (o.portal !== 'group') {
    await kit.edit(STAND, 'custom', 'true');
    obs.portal = (await kit.edit(STAND, 'portal', o.portal)).text;
  }
  if (o.chevrons !== 'frame') {
    const block = o.chevrons === 'lamp' ? 'minecraft:redstone_lamp' : 'minecraft:copper_bulb';
    for (const l of geom.lights) await run(ctx, `setblock ${l.x} ${l.y} ${l.z} ${block}`);
  }
  if (o['far iris'] !== 'open' && !['busy', 'self'].includes(o.destination)) {
    const f = farOf(o);
    obs.farIdc = (await kit.edit(f.name, 'idc', IDC_FAR)).text;
    if (o['far iris'] !== 'shut after dial') obs.farIrisShut = await kit.toggleIris(ctx.probe, f.geom, f.dim);
  }
  await stageTraveller(ctx, o, geom);
}

/** The traveller's fixture: lanes, the animal, the pets, the dispenser. */
async function stageTraveller(ctx, o, geom) {
  const t = o.traveller;
  const start = front(geom, 8);
  if (t === 'minecart' || t === 'minecart ridden') {
    await run(ctx, `fill ${Math.floor(start.x)} 0 ${Math.floor(geom.opening[0].z) + 1} ${Math.floor(start.x)} 0 ${Math.floor(start.z)} minecraft:rail[shape=north_south]`);
  }
  if (t === 'boat' || t === 'boat ridden') {
    await run(ctx, `fill ${Math.floor(start.x)} -1 ${Math.floor(geom.opening[0].z) + 1} ${Math.floor(start.x)} -1 ${Math.floor(start.z) + 1} minecraft:blue_ice`);
  }
  if (t === 'cart at iris') await stageCartRun(ctx, o, geom);
  if (MOUNTS.includes(t)) {
    const at = front(geom, 6);
    await ctx.menagerie.animal(t, at, ctx.tag, { saddled: true });
  }
  if (PETS[t]) {
    const at = front(geom, 7, 1.5);
    // A parrot left free perches on Probe's shoulder (and stops being an entity) if given time,
    // so it sits until the moment Probe sets off.
    await ctx.menagerie.animal(PETS[t], at, ctx.tag, { owner: ctx.probe.name, sitting: t === 'sitting wolf' || t === 'parrot' });
  }
}

/** #491: a straight rail line at right angles into the opening's bottom row, ending just short. */
async function stageCartRun(ctx, o, geom) {
  const n = Number(o['run-up']);
  const face = cartFace(geom, o.approach);
  const lane = [];
  for (let i = 1; i <= n; i++) lane.push(face.firstBlock + face.dir * (i - 1));
  // Straight in along the middle, unless the gate has something of its own in the floor there
  // (a flat gate's name sign and lever sit in front of it): then the nearest clear column.
  let x = Math.floor(geom.centre.x);
  for (const dx of [0, -1, 1, -2, 2]) {
    const cx = Math.floor(geom.centre.x) + dx;
    let clear = true;
    for (const z of lane) {
      const r = await ctx.server.run(`execute unless block ${cx} -1 ${z} minecraft:smooth_quartz unless block ${cx} -1 ${z} minecraft:obsidian`);
      if (r.lines.some((l) => /Test passed/.test(l))) { clear = false; break; }
    }
    if (clear) { x = cx; break; }
  }
  const z0 = Math.min(...lane);
  const z1 = Math.max(...lane);
  if (o.vehicle === 'boat') {
    // Ice under the lane, except where the gate's own frame lies in the floor (Massive's lone
    // :EP and :EM blocks): a rowed boat does not need it, and the gate must stay whole.
    for (const z of lane) {
      if (!geom.frame.some((f) => f.x === x && f.y === -1 && f.z === z)) await run(ctx, `setblock ${x} -1 ${z} minecraft:blue_ice`);
    }
    ctx.observed.cartLane = { x, z0, z1, start: lane[lane.length - 1] };
    return;
  }
  await run(ctx, `fill ${x} 0 ${z0} ${x} 0 ${z1} minecraft:rail[shape=north_south]`);
  // The two farthest rails are powered, on redstone blocks, so the cart leaves at full speed.
  for (const z of lane.slice(-2)) {
    await run(ctx, `setblock ${x} -1 ${z} minecraft:redstone_block`);
    await run(ctx, `setblock ${x} 0 ${z} minecraft:powered_rail[shape=north_south,powered=true]`);
  }
  ctx.observed.cartLane = { x, z0, z1, start: lane[lane.length - 1] };
}

/**
 * The iris face a cart runs at, for an approach: the opening block spans z..z+1 along the
 * gate's axis. From the front (the DHD side, +F) the face is the block's +F side; from behind,
 * its -F side. `dir` is the way the rails run out from the face.
 */
function cartFace(geom, approach) {
  // An upright opening is one block along the axis; a flat one several, and its near edge is
  // the one a cart from the front reaches first.
  const zs = geom.opening.map((c) => c.z);
  const sign = geom.normal.z; // facing south: +1
  const near = sign > 0 ? Math.max(...zs) : Math.min(...zs);
  const far = sign > 0 ? Math.min(...zs) : Math.max(...zs);
  if (approach === 'front') return { face: sign > 0 ? near + 1 : near, firstBlock: near + sign, dir: sign, travel: -sign };
  return { face: sign > 0 ? far : far + 1, firstBlock: far - sign, dir: -sign, travel: sign };
}

// ---- run -----------------------------------------------------------------------------------

async function runTrip(ctx, o) {
  const obs = ctx.observed;
  if (obs.buildRefused) return;
  const kit = kitOf(ctx);
  const geom = obs.geom;
  const probe = ctx.probe;
  const f = farOf(o);
  const start = front(geom, 8);
  await probe.teleport(start, O);

  if (o.traveller === 'cart at iris') return cartAtIris(ctx, o, geom);

  // Dial.
  let target = destOf(o);
  if (o.destination === 'busy') obs.busyDial = (await kit.dial('Relay', 'Range')).text;
  if (o.destination === 'self') target = STAND;
  const idc = o['far iris'] === 'shut with code' ? IDC_FAR : null;
  await ctx.step(`dialling ${target} (${o.dial})`);
  const mark = obs.chat.length;
  // The chevrons, watched while they light (a sign gate opens at once: nothing to order).
  const lights = !signDial(o) ? watchLights(ctx, geom) : null;
  if (signDial(o) && o.dial !== 'console') {
    await dialBySign(ctx, o, geom, target);
  } else if (o.dial === 'dhd') {
    await probe.teleport({ ...front(geom, 0, 0), x: geom.button.x + 0.5 + geom.normal.x * 1.5, z: geom.button.z + 0.5 + geom.normal.z * 1.5 }, O);
    await probe.press(geom.button);
    await until(async () => /Gate successfully activated/.test(chatSince(ctx, mark)), 3000);
    probe.bot.chat(`/dial ${target}${idc ? ` ${idc}` : ''}`);
    await until(async () => /Stargates connected|Target gate|solar flare|Remote Iris is active|Invalid gate/.test(chatSince(ctx, mark)), 5000);
    obs.dial = chatSince(ctx, mark);
    await probe.teleport(start, O);
  } else {
    obs.dial = (await kit.dial(STAND, target, idc)).text;
  }
  if (EXPECTED_REFUSAL[o.destination] || EXPECTED_REFUSAL[o['far iris']]) {
    await ticks(60);
    obs.openingShown = await openingShown(ctx, geom);
    return undefined;
  }
  await ctx.step('waiting for the kawoosh');
  obs.drawn = await kit.waitOpen(probe, geom).catch((e) => { obs.openError = e.message; return null; });
  if (lights) obs.lightWaves = lights.stop();
  if (!obs.drawn) return undefined;
  if (o['far iris'] === 'shut after dial') {
    obs.farIrisShut = await kit.toggleIris(probe, f.geom, f.dim);
    await probe.teleport(start, O);
  }
  if (o['own iris'] === 'shut') {
    obs.ownIrisShut = await kit.toggleIris(probe, geom, O);
    await probe.teleport(start, O);
  }
  await ctx.step(`sending: ${o.traveller}`);
  return sendTraveller(ctx, o, geom, f);
}

/**
 * Dials a sign gate as a player does: the sign turned to the destination (right-clicks on,
 * left-clicks back; on Lab.shape's redstone row, pulses on [RS]), then the DHD pressed, or a
 * lever by [RD] pulled.
 */
async function dialBySign(ctx, o, geom, target) {
  const probe = ctx.probe;
  const obs = ctx.observed;
  const sign = geom.sign;
  const facing = { x: geom.button.x + 0.5 + geom.normal.x * 2, y: PLACE.floorY, z: geom.button.z + 0.5 + geom.normal.z * 2, yaw: (geom.yaw + 180) % 360 };
  await probe.teleport(facing, O);
  obs.signSteps = [];
  const byRs = o.dial === 'redstone' && geom.redstone.RS;
  let lever = null;
  if (byRs) {
    lever = await leverSpot(ctx, geom, geom.redstone.RS, geom.redstone.RD);
    await run(ctx, `setblock ${lever.x} ${lever.y} ${lever.z} minecraft:lever[face=floor]`);
    obs.rsLever = lever;
  }
  for (let i = 0; i < 8 && signShows(await signText(ctx, sign)) !== target; i++) {
    const mark = obs.chat.length;
    if (byRs) {
      // A pulse: on (the rising edge turns the sign), then off again.
      await probe.click(lever);
      await ticks(6);
      await probe.click(lever);
      await ticks(6);
      obs.signSteps.push(signShows(await signText(ctx, sign)));
    } else {
      // The sign's front: a left click is a punch at that face (block_dig's face: 2 north, 3 south, 4 west, 5 east).
      const face = { '0,-1': 2, '0,1': 3, '-1,0': 4, '1,0': 5 }[`${geom.normal.x},${geom.normal.z}`];
      if (o.dial === 'sign left') await probe.punch(sign, face); else await probe.rightClick(sign, { dir: new (require('vec3').Vec3)(geom.normal.x, 0, geom.normal.z) });
      await until(async () => /Dialer set to: |No available target/.test(chatSince(ctx, mark)), 2000, 2);
      obs.signSteps.push((/Dialer set to: (\S+)/.exec(chatSince(ctx, mark)) || [])[1] || chatSince(ctx, mark));
    }
  }
  obs.signShows = signShows(await signText(ctx, sign));
  const mark = obs.chat.length;
  if (o.dial === 'redstone') {
    const rd = await leverSpot(ctx, geom, geom.redstone.RD, geom.redstone.RS || null);
    await run(ctx, `setblock ${rd.x} ${rd.y} ${rd.z} minecraft:lever[face=floor]`);
    obs.rdLever = rd;
    await probe.click(rd);
    obs.dial = 'a lever by [RD] pulled';
  } else {
    await probe.press(geom.button);
    await until(async () => /Stargates connected|Invalid gate target|remotely activated|lack the permissions/.test(chatSince(ctx, mark)), 4000);
    obs.dial = chatSince(ctx, mark);
  }
  await probe.teleport(front(geom, 8), O);
}

async function openingShown(ctx, geom) {
  const { Vec3 } = require('vec3');
  return geom.opening.filter((c) => { const b = ctx.probe.bot.blockAt(new Vec3(c.x, c.y, c.z)); return b && b.name !== 'air'; }).length;
}

async function sendTraveller(ctx, o, geom, f) {
  const t = o.traveller;
  const probe = ctx.probe;
  const obs = ctx.observed;
  const dest = f.geom;
  const beyond = front(geom, -2.5);
  // A summoned traveller is marked as launched, so the tick function catches the first tick it
  // is within 5 of the far exit (a fast cart or boat is carried well past it within a second).
  const launched = async (kind) => {
    for (const tagName of ['wx_launched', 'wx_seen']) await run(ctx, `tag @e[tag=${ctx.tag},tag=wx_kind_${kind}] add ${tagName}`);
  };
  const arrival = async (ms) => {
    await ctx.watch.waitArrival(ms);
    await ticks(5);
    return ctx.watch.arrival();
  };
  if (t === 'walk' || PETS[t]) {
    const mark = obs.chat.length;
    if (PETS[t]) {
      await ctx.watch.arm(O, front(geom, 30), { ...dest.arrival, dim: f.dim }, { freeze: false });
      if (t === 'parrot') await ctx.server.run(`data merge entity @e[tag=${ctx.tag},tag=wx_kind_parrot,limit=1] {Sitting:0b}`);
      await launched(PETS[t]);
    }
    if (geom.flat) {
      // A flat gate is stepped into: Probe is put just above the middle of the opening and falls.
      // (Walking up to it crosses the DHD button, which sits in a hole in the floor.)
      await probe.teleport({ x: geom.centre.x, y: PLACE.floorY + 0.2, z: geom.centre.z }, O).catch(() => {});
    } else {
      await probe.walkTo(beyond, { within: 0.4, ms: 15000, until: () => probe.dimension !== O || probe.distanceTo(dest.arrival) < 3 }).catch(() => {});
    }
    await until(async () => probe.dimension === f.dim && probe.distanceTo(dest.arrival) < 3, 4000);
    await probe.settle();
    obs.probeAt = { dim: probe.dimension, d: probe.distanceTo(dest.arrival), home: probe.distanceTo(geom.arrival), pos: probe.position.floored().toString() };
    obs.walkChat = chatSince(ctx, mark);
    if (PETS[t]) {
      // The plugin sends a pet after its owner 20 ticks later, to the owner's arrival.
      const a = await arrival(5000);
      obs.petNear = a.count > 0 && a.same;
      obs.petStayed = await near(ctx, O, front(geom, 7, 1.5), `tag=${ctx.tag},tag=wx_kind_${PETS[t]}`, 3);
    }
    return undefined;
  }
  if (MOUNTS.includes(t)) {
    if (t === 'pig' || t === 'strider') {
      const stick = t === 'pig' ? 'carrot_on_a_stick' : 'warped_fungus_on_a_stick';
      await run(ctx, `give ${probe.name} minecraft:${stick}`);
      await probe.waitForItem(stick);
      await probe.hold(stick);
    }
    await ctx.watch.arm(O, front(geom, 30), { ...dest.itemArrival, dim: f.dim }, { freeze: false });
    await launched(t);
    // Its AI is on, so it has wandered since it was staged: bring it back to the lane first.
    const lane = front(geom, 6);
    await run(ctx, `execute in ${O} run tp @e[tag=${ctx.tag},tag=wx_kind_${t},limit=1] ${lane.x} ${lane.y} ${lane.z} ${lane.yaw} 0`);
    // Board it from the side, not from behind: a camel is nearly two blocks long.
    await probe.teleport({ x: lane.x + geom.right.x * 1.6, y: 0, z: lane.z + geom.right.z * 1.6, yaw: 90 }, O);
    await probe.mount(ctx.tag, 5000, 3);
    // Back onto the line through the middle of the opening, then straight in.
    await probe.drive(front(geom, 4), { speed: 0.25, ms: 8000 }).catch(() => {});
    await probe.drive(beyond, { speed: 0.25, ms: 15000 }).catch((e) => { obs.driveError = e.message; });
    const a = await arrival(6000);
    await ticks(10);
    obs.mountArrived = a.count > 0 && a.same;
    const where = await probe.entityByTag(ctx.tag);
    obs.mountAt = where ? `${where.x.toFixed(1)} ${where.y.toFixed(1)} ${where.z.toFixed(1)}` : 'gone';
    obs.stillRiding = Boolean(probe.bot.vehicle) && probe.dimension === f.dim && probe.distanceTo(dest.arrival) < 6;
    return undefined;
  }
  if (t === 'minecart' || t === 'minecart ridden' || t === 'boat' || t === 'boat ridden') {
    const kind = t.startsWith('minecart') ? 'minecart' : 'boat';
    // An empty boat on land stops within two blocks of a push, so it starts close; a cart rolls.
    const at = front(geom, t === 'boat' ? 2 : 6);
    await ctx.menagerie.summon(kind, { ...at, y: kind === 'minecart' ? 0.1 : 0 }, ctx.tag);
    const sel = `@e[tag=${ctx.tag},tag=wx_kind_${kind},limit=1]`;
    await ctx.watch.arm(O, front(geom, 30), { ...dest.cartArrival, dim: f.dim }, { freeze: false });
    await launched(kind);
    const mark = obs.chat.length;
    if (t.endsWith('ridden')) {
      await probe.teleport({ x: at.x + geom.right.x * 1.5, y: 0, z: at.z + geom.right.z * 1.5, yaw: 90 }, O);
      await probe.mount(ctx.tag, 5000, 3);
    }
    if (t === 'boat ridden') {
      await probe.drive(beyond, { speed: 0.35, ms: 15000 }).catch((e) => { obs.driveError = e.message; });
    } else {
      await run(ctx, `data merge entity ${sel} {Motion:[0.0d,0.0d,${(-geom.normal.z * (kind === 'boat' ? 1.0 : 0.6)).toFixed(1)}d]}`);
    }
    const a = await arrival(8000);
    await ticks(20);
    obs.vehicleArrived = a.count > 0 && a.same;
    obs.stillRiding = Boolean(probe.bot.vehicle) && probe.dimension === f.dim;
    obs.walkChat = chatSince(ctx, mark);
    if (o['own iris'] === 'shut') {
      // Where it ended, in blocks out in front of the opening's middle (negative: past it), so a
      // vehicle that never moved does not read as one the iris stopped.
      const pos = await ctx.server.run(`execute in ${O} run data get entity ${sel} Pos`);
      const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(pos.lines.join(' '));
      const plane = front(geom, 0);
      obs.vehicleRun = m ? {
        from: (at.x - plane.x) * geom.normal.x + (at.z - plane.z) * geom.normal.z,
        to: (Number(m[1]) - plane.x) * geom.normal.x + (Number(m[3]) - plane.z) * geom.normal.z,
      } : null;
    }
    return undefined;
  }
  if (t === 'projectile') return shoot(ctx, o, geom, f);
  return sweep(ctx, o, geom, f);
}

/** Items, orbs, stands, mobs and the frame: the plugin's scanner moves them (or must not). */
async function sweep(ctx, o, geom, f) {
  const t = o.traveller;
  const probe = ctx.probe;
  const obs = ctx.observed;
  const version = ctx.version;
  const mid = geom.opening[Math.floor(geom.opening.length / 2)];
  const bottom = geom.opening.reduce((a, b) => (b.y < a.y ? b : a));
  const inOpening = { x: geom.centre.x, y: bottom.y, z: Math.floor(geom.opening[0].z) + 0.5 };
  const dest = f.geom;
  const relic = ARMOURY.relic;
  await ctx.watch.arm(O, { x: geom.centre.x, y: bottom.y + 1, z: inOpening.z + geom.normal.z * 1.5 }, { ...dest.itemArrival, dim: f.dim });
  // A toss has a little random spread, so one toss may land in the opening or go through it:
  // TOSSES of them make the outcome a property of the plugin, not of one throw.
  obs.tosses = ['dropped item', 'dispensed item'].includes(t) ? TOSSES : 1;
  if (t === 'dropped item') {
    // Close enough that a tossed stack reaches the opening (beside a flat one), then away, so
    // Probe does not pick it up again before the plugin's sweep finds it.
    const stand = geom.flat
      ? { x: geom.centre.x + geom.right.x * 3.2, y: PLACE.floorY, z: geom.centre.z + geom.right.z * 3.2 }
      : { ...front(geom, 0.85), y: PLACE.floorY };
    obs.tossedOut = 0;
    const holding = () => probe.bot.inventory.items().some((i) => i.name === relic.id);
    for (let i = 0; i < TOSSES; i++) {
      await ctx.menagerie.give(probe.name, 'relic');
      await probe.waitForItem(relic.id);
      await probe.hold(relic.id);
      await probe.teleport(stand, O);
      // Steeply down into the opening's bottom cell.
      await probe.look(geom.flat ? { x: geom.centre.x, y: bottom.y + 0.5, z: geom.centre.z } : { x: inOpening.x, y: bottom.y + 0.2, z: inOpening.z });
      await probe.dropOne();
      // Out of Probe's hand, as the server sees it: the harness's half of "tossed".
      if (await until(async () => !holding(), 1000, 1)) obs.tossedOut++;
      await ticks(3);
      await probe.teleport(front(geom, 8), O);
    }
  } else if (t === 'dispensed item') {
    const d = front(geom, 1);
    const dx = Math.floor(d.x);
    const dz = Math.floor(d.z);
    const stack = Array.from({ length: TOSSES }, (_, i) => itemNbt(version, relic, `,Slot:${i}b`)).join(',');
    await run(ctx, `setblock ${dx} ${bottom.y + 1} ${dz} minecraft:dispenser[facing=north]{Items:[${stack}]}`);
    for (let i = 0; i < TOSSES; i++) {
      await run(ctx, `setblock ${dx} ${bottom.y + 2} ${dz} minecraft:redstone_block`);
      await ticks(4);
      await run(ctx, `setblock ${dx} ${bottom.y + 2} ${dz} minecraft:air`);
      await ticks(4);
    }
    // The harness's half of "dispensed": what the dispenser still holds (none, if it fired five times).
    const left = await ctx.server.run(`data get block ${dx} ${bottom.y + 1} ${dz} Items`);
    obs.dispenserHolds = left.lines.join(' ');
    obs.dispenserLeft = [...obs.dispenserHolds.matchAll(/[Cc]ount: (\d+)/g)].reduce((n, m) => n + Number(m[1]), 0);
  } else if (t === 'spilled items') {
    const items = `Items:[${itemNbt(version, relic, ',Slot:0b')}]`;
    await ctx.menagerie.summon('hopper_minecart', { ...inOpening, y: bottom.y }, ctx.tag, items);
    await run(ctx, `kill @e[tag=${ctx.tag},tag=wx_kind_hopper_minecart]`);
    // Breaking the cart drops the cart too (as a hopper minecart, or a minecart and a hopper);
    // only the spilled contents are the traveller.
    for (const part of ['hopper_minecart', 'minecart', 'hopper']) {
      await ctx.server.run(`kill @e[type=minecraft:item,distance=..4,x=${inOpening.x},y=${bottom.y},z=${inOpening.z},nbt={Item:{id:"minecraft:${part}"}}]`);
    }
  } else if (t === 'lying item') {
    // Still, in the middle of the opening, tagged from the start: what the plugin's sweep is for.
    // Tagged in the summon itself: the plugin's once-a-second sweep may send it before a second
    // command could tag it, and it would then arrive looking like a new entity.
    await ctx.menagerie.summon('item', { ...inOpening, y: bottom.y }, ctx.tag, `Item:${itemNbt(version, relic)},PickupDelay:32767s,Motion:[0.0d,0.0d,0.0d]`, ['wx_launched', 'wx_seen']);
  } else if (t === 'xp') {
    await ctx.menagerie.summon('experience_orb', { ...inOpening, y: bottom.y + 0.2 }, ctx.tag, 'Value:7s', ['wx_launched']);
  } else if (t === 'armour stand') {
    await ctx.menagerie.summon('armor_stand', { ...inOpening, y: bottom.y }, ctx.tag, '', ['wx_launched', 'wx_seen']);
  } else if (t === 'zombie') {
    await ctx.menagerie.summon('zombie', { ...inOpening, y: bottom.y }, ctx.tag, 'NoAI:1b,PersistenceRequired:1b,Silent:1b', ['wx_launched', 'wx_seen']);
  } else if (t === 'llama') {
    await ctx.menagerie.animal('llama', { ...inOpening, y: bottom.y }, ctx.tag, {});
    await run(ctx, `data merge entity @e[tag=${ctx.tag},tag=wx_kind_llama,limit=1] {NoAI:1b}`);
  } else if (t === 'item frame') {
    // Hung on the frame block beside the opening's middle row, facing out of the gate.
    const rowY = Math.round(geom.centre.y - 0.5);
    const openZ = Math.floor(geom.opening[0].z);
    const sides = geom.frame.filter((p) => p.y === rowY && p.z === openZ && !geom.opening.some((q) => q.x === p.x && q.y === p.y && q.z === p.z));
    const side = sides.sort((p, q) => Math.abs(p.x + 0.5 - geom.centre.x) - Math.abs(q.x + 0.5 - geom.centre.x))[0] || { x: mid.x, y: mid.y, z: mid.z };
    // Hung inside the opening, on the frame beside it, facing in: in the portal, where the sweep
    // looks, and still it must not be taken (the plugin excludes hanging entities).
    const inward = Math.sign(geom.centre.x - (side.x + 0.5)) || 0;
    const inwardZ = Math.sign(geom.centre.z - (side.z + 0.5)) || 0;
    const hang = geom.normal.z !== 0 ? { x: side.x + inward, y: side.y, z: side.z } : { x: side.x, y: side.y, z: side.z + inwardZ };
    const facing = geom.normal.z !== 0 ? (inward > 0 ? 5 : 4) : (inwardZ > 0 ? 3 : 2);
    obs.framePlace = hang;
    await ctx.menagerie.summon('item_frame', { x: hang.x, y: hang.y, z: hang.z }, ctx.tag, `${hangingAt(hang.x, hang.y, hang.z)},Facing:${facing}b`);
  }
  const scan = 20; // entity-scan-interval-ticks, the default
  if (['armour stand', 'zombie', 'llama'].includes(t)) {
    // A summoned mob is tagged in its summon, as a lying item is: the sweep may take it before a
    // later command. The llama comes from the pen, so it is tagged after.
    if (t === 'llama') {
      for (const tagName of ['wx_launched', 'wx_seen']) await run(ctx, `tag @e[tag=${ctx.tag},tag=wx_kind_llama] add ${tagName}`);
    }
    await ctx.watch.waitArrival((scan + 60) * 50);
    const a = await ctx.watch.arrival();
    obs.mobArrived = a.count > 0 && a.same;
    return undefined;
  }
  if (t === 'item frame') {
    await ticks(scan * 3);
    obs.frameStayed = await near(ctx, O, obs.framePlace, `tag=${ctx.tag},tag=wx_kind_item_frame`, 1);
    return undefined;
  }
  // Every toss, or the deadline: one sent in the last toss may lie in the opening until the next
  // sweep, a second off.
  await ctx.watch.waitArrival((scan + 60) * 50, obs.tosses);
  await ticks(4);
  obs.arrival = await ctx.watch.arrival();
  obs.leftBehind = await ctx.watch.leftBehind();
  obs.launches = await ctx.watch.launches();
  obs.leftCounts = await ctx.watch.leftBehindCounts();
  const stray = await ctx.server.run('data get entity @e[tag=wx_launched,tag=!wx_arrived,limit=1] Pos');
  obs.strayAt = stray.lines.join(' ');
  obs.items = (await ctx.server.run(`execute positioned ${geom.centre.x} ${geom.centre.y} ${geom.centre.z} if entity @e[type=minecraft:item,distance=..12]`)).lines.join(' ');
  const dump = await ctx.server.run(`execute in ${f.dim} run data get entity @e[tag=wx_arrived,limit=1]`);
  obs.arrivalData = dump.lines.join(' ');
  return undefined;
}

/** Projectiles: aimed at the opening's centre from the launcher; caught at the far exit. */
async function shoot(ctx, o, geom, f) {
  const probe = ctx.probe;
  const obs = ctx.observed;
  const dest = f.geom;
  const p = o.projectile;
  const version = ctx.version;
  // The middle of the opening: of its plane for an upright gate, of its floor for a flat one.
  const aim = geom.flat
    ? { x: geom.centre.x, y: geom.centre.y - 0.5, z: geom.centre.z }
    : { x: geom.centre.x, y: geom.centre.y, z: Math.floor(geom.opening[0].z) + 0.5 };
  let from;
  // From above, but off to the front: straight down, the shot would hit the block Probe stands on.
  if (o.angle === 'above') from = { x: aim.x, y: 6, z: aim.z + geom.normal.z * 4 };
  else if (o.angle === 'point-blank') from = front(geom, 1.6);
  else if (o.angle === 'glancing') from = front(geom, 10 * Math.cos(Math.PI / 6), 10 * Math.sin(Math.PI / 6));
  // A thrown potion is slow and falls short from ten blocks; it is thrown from four.
  else from = front(geom, ['splash potion', 'lingering potion'].includes(p) ? 4 : 10);
  // Arrows, pearls and tridents are left to fly on (a pearl must land, a trident come back).
  const freeze = !['pearl', 'trident'].includes(p);
  const eye = { x: from.x, y: from.y + 1.62, z: from.z };
  // A dispenser's shot leaves its front: at the opening's height, or under it when it fires down.
  const muzzle = { x: from.x, y: o.angle === 'above' ? 5.5 : aim.y, z: from.z };
  await ctx.watch.arm(O, o.launcher === 'dispenser' ? muzzle : eye, { ...dest.itemArrival, dim: f.dim }, { freeze });
  const mark = obs.chat.length;
  if (o.launcher === 'dispenser') {
    const bx = Math.floor(from.x);
    const bz = Math.floor(from.z);
    const by = o.angle === 'above' ? 6 : Math.max(0, Math.floor(aim.y));
    const facing = o.angle === 'above' ? 'down' : 'north';
    if (p === 'llama spit') {
      await ctx.menagerie.summon('llama_spit', { x: bx + 0.5, y: by + 0.5, z: bz - 0.8 }, 'wx_spit', `Motion:[0.0d,0.0d,${(-geom.normal.z * 1.5).toFixed(1)}d]`);
    } else {
      const ammo = { arrow: 'arrow', 'spectral arrow': 'spectral_arrow', 'tipped arrow': 'tipped_arrow', firework: 'firework_rocket', snowball: 'snowball', egg: 'egg',
        'splash potion': 'splash_potion', 'lingering potion': 'lingering_potion', fireball: 'fire_charge', 'wind charge': 'wind_charge' }[p];
      await run(ctx, `setblock ${bx} ${by} ${bz} minecraft:dispenser[facing=${facing}]{Items:[${itemNbt(version, { ...ARMOURY[ammo], count: 1 }, ',Slot:0b')}]}`);
      await run(ctx, `setblock ${bx} ${by + (o.angle === 'above' ? 1 : 1)} ${bz} minecraft:redstone_block`);
    }
  } else {
    if (o.angle === 'above') await run(ctx, `setblock ${Math.floor(from.x)} 5 ${Math.floor(from.z)} minecraft:glass`);
    await probe.teleport({ ...from, yaw: 180, pitch: 0 }, O);
    await ctx.server.run(`clear ${probe.name}`);
    const kit = {
      arrow: ['bow', 'arrow'], 'spectral arrow': ['bow', 'spectral_arrow'], 'tipped arrow': ['bow', 'tipped_arrow'],
      bolt: ['crossbow', 'arrow'], 'piercing bolt': ['piercing', 'arrow'], firework: ['crossbow', 'firework_rocket'],
      trident: ['trident'], snowball: ['snowball'], egg: ['egg'], pearl: ['ender_pearl'], 'splash potion': ['splash_potion'],
      'lingering potion': ['lingering_potion'], 'wind charge': ['wind_charge'],
    }[p];
    const holder = o.launcher === 'tester' ? (await testerName(ctx)) : probe.name;
    if (!holder) throw new Error('launcher "tester" needs a person on the server');
    // A creative player keeps a thrown trident and never picks one back up, so "came back" is
    // only a fact in survival (cleanup puts Probe back in creative).
    if (p === 'trident' && o.launcher === 'bot') await run(ctx, `gamemode survival ${probe.name}`);
    for (const item of kit) await ctx.menagerie.give(holder, item);
    if (o.launcher === 'tester') {
      await ctx.server.run(`tellraw ${holder} {"text":"G1: shoot the ${p} into the Stand's opening now (60 s)","color":"gold"}`);
      await ctx.watch.waitArrival(60000);
    } else {
      for (const item of kit) await probe.waitForItem(ARMOURY[item].id);
      await probe.hold(ARMOURY[kit[0]].id);
      if (p === 'firework') await probe.hold('firework_rocket', 'off-hand');
      await probe.look(aim);
      if (['arrow', 'spectral arrow', 'tipped arrow', 'trident'].includes(p)) await probe.drawAndLoose(25);
      else if (['bolt', 'piercing bolt', 'firework'].includes(p)) await probe.drawAndLoose(30, { crossbow: true });
      else await probe.use();
      if (p === 'trident') obs.tridentThrown = await until(async () => !probe.bot.inventory.items().some((i) => i.name === 'trident'), 2000, 1);
    }
  }
  await ctx.watch.waitArrival(6000);
  await ticks(6);
  obs.arrival = await ctx.watch.arrival();
  obs.leftBehind = await ctx.watch.leftBehind();
  obs.launches = await ctx.watch.launches();
  obs.strayAt = (await ctx.server.run('data get entity @e[tag=wx_launched,tag=!wx_arrived,limit=1] Pos')).lines.join(' ');
  obs.arrivalData = (await ctx.server.run(`execute in ${f.dim} run data get entity @e[tag=wx_arrived,limit=1]`)).lines.join(' ');
  if (p === 'pearl') {
    // It flies on out of the far gate and lands a few blocks on; Probe goes where it lands.
    // It flies on out of the far gate (through Relay's cell door) and lands where it lands; the
    // thrower must be in the far world, out in front of the far gate, on its line.
    const onLine = () => {
      const d = { x: probe.position.x - dest.arrival.x, z: probe.position.z - dest.arrival.z };
      const along = d.x * dest.normal.x + d.z * dest.normal.z;
      const across = Math.abs(d.x * dest.right.x + d.z * dest.right.z);
      return along > -1 && across < 3;
    };
    obs.pearlCarried = await until(async () => probe.dimension === f.dim && onLine(), 5000);
    obs.pearlLanding = `${probe.dimension} ${probe.position.floored()}`;
  }
  if (p === 'trident' && obs.tridentThrown) {
    obs.tridentBack = await until(async () => probe.bot.inventory.items().some((i) => i.name === 'trident'), 10000);
  }
  obs.shotChat = chatSince(ctx, mark);
  return undefined;
}

async function testerName(ctx) {
  const others = Object.keys(ctx.probe.bot.players).filter((n) => n !== ctx.probe.name && n !== 'Tester' && n !== 'Probe2' && !ctx.facility.watching(n));
  return others[0] || null;
}

/** #491: run a cart at a shut iris and track its front edge every tick. */
async function cartAtIris(ctx, o, geom) {
  const obs = ctx.observed;
  const kit = kitOf(ctx);
  const probe = ctx.probe;
  const face = cartFace(geom, o.approach);
  obs.face = face;
  if (o['iris gate'] === 'incoming') {
    obs.dial = (await kit.dial('Relay', STAND)).text;
    obs.drawn = await kit.waitOpen(probe, geom).catch((e) => { obs.openError = e.message; return null; });
  }
  if (o['own iris'] === 'shut') obs.ownIrisShut = await kit.toggleIris(probe, geom, O);
  // What the opening shows now: the iris material when shut (stone by default).
  const lane = obs.cartLane;
  const watchFrom = { x: lane.x + 3.5, y: 0, z: lane.start + 0.5, yaw: 180 };
  await probe.teleport(watchFrom, O);
  await ticks(10);
  const { Vec3 } = require('vec3');
  const mid = geom.opening.find((c) => c.y === Math.min(...geom.opening.map((p) => p.y)) && c.x === lane.x) || geom.opening[0];
  const shown = probe.bot.blockAt(new Vec3(mid.x, mid.y, mid.z));
  obs.irisShown = shown ? shown.name : null;
  const kind = o.vehicle === 'boat' ? 'boat' : 'minecart';
  const at = { x: lane.x + 0.5, y: kind === 'boat' ? 0 : 0.1, z: lane.start + 0.5 };
  await ctx.menagerie.summon(kind, at, ctx.tag);
  const sel = `@e[tag=${ctx.tag},tag=wx_kind_${kind},limit=1]`;
  if (o.rider === 'Probe') {
    await probe.teleport({ x: at.x + 1.5, y: 0, z: at.z, yaw: 90 }, O);
    await probe.mount(ctx.tag, 5000, 3);
  }
  await ctx.watch.track(sel);
  const speed = Number(o['cart speed']);
  if (kind === 'boat') {
    // Rowed by its rider, as a client does: straight at the face and on past it; the plugin must
    // stop it. The drive ends when the server moves the boat itself, or at the target.
    probe.drive({ x: at.x, z: face.face + face.travel * 4 }, { speed, ms: 8000 }).catch(() => {});
  } else {
    await run(ctx, `data merge entity ${sel} {Motion:[0.0d,0.0d,${(face.travel * speed).toFixed(2)}d]}`);
  }
  // Until it has been still for ten ticks (or 10 s).
  let last = null;
  let still = 0;
  const end = Date.now() + 10000;
  while (Date.now() < end && still < 10) {
    await ticks(1);
    const r = await ctx.watch.trackResult();
    if (last && Math.abs(r.z - last.z) < 0.0001 && Math.abs(r.x - last.x) < 0.0001) still++; else still = 0;
    last = r;
  }
  obs.track = await ctx.watch.trackResult();
  await ctx.watch.untrack();
  await ticks(10);
  obs.riderSeated = o.rider === 'Probe' ? Boolean(probe.bot.vehicle) : null;
  return undefined;
}

// ---- checks ----------------------------------------------------------------------------------

function checks(ctx, o) {
  const obs = ctx.observed;
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  const list = [];
  if (signDial(o) && o.built === 'console') {
    list.push(c('the plugin refused a console build of a sign-dial gate', () => obs.buildRefused && /dials by sign/.test(obs.build)));
    return list;
  }
  if (o.built === 'console') list.push(c('the Stand was built', () => /Built Stand/.test(obs.build || '')));
  else list.push(...buildChecks(o, obs));
  if (o.group !== 'default' && o.built === 'console') list.push(c(`the Stand draws the ${o.group} group`, () => new RegExp(`is now on group ${o.group}`).test(obs.group || '')));
  if (o.spin !== 'default') list.push(c(`the Stand dials with ${o.spin}`, () => new RegExp(`now dials with ${o.spin}`).test(obs.spin || '')));
  if (o.portal !== 'group') list.push(c(`the portal is set to ${o.portal}`, () => new RegExp(`portal material set to: ${o.portal}`).test(obs.portal || '')));
  const t = o.traveller;
  if (t === 'cart at iris') return list.concat(cartChecks(o, obs));
  // A far iris row proves nothing unless the iris was shut: the lever moved and the plugin shut it.
  const farIris = o['far iris'] !== 'open' && !['busy', 'self'].includes(o.destination);
  const farShutCheck = c(`${destOf(o)}'s iris was shut at its lever`, () => obs.farIrisShut === true);
  if (farIris && o['far iris'] !== 'shut after dial') list.push(farShutCheck);
  const refusal = EXPECTED_REFUSAL[o.destination] || EXPECTED_REFUSAL[o['far iris']];
  if (refusal) {
    list.push(c(`the dial was refused: "${refusal.source.replace(/\\/g, '')}"`, () => refusal.test(obs.dial || '')));
    list.push(c('the opening stayed air', () => obs.openingShown === 0));
    return list;
  }
  if (o.dial === 'dhd') list.push(c('the DHD button lit the gate', () => /Gate successfully activated/.test(obs.dial || '')));
  if (signDial(o) && o.dial !== 'console') {
    list.push(c(`the dial sign was turned to ${destOf(o)} (»${destOf(o)}«)`, () => obs.signShows === destOf(o)));
    if (o.dial === 'sign right' || o.dial === 'sign left') {
      list.push(c(`each click answered "Dialer set to: <gate>"`, () => obs.signSteps.length > 0 && obs.signSteps.every((s) => /^[A-Za-z]+$/.test(s))));
    }
    if (o.dial === 'redstone' && o.shape === 'custom') list.push(c('pulses on [RS] turned the sign', () => obs.signSteps.length > 0));
    if (o.dial !== 'redstone') list.push(c('the DHD dialled what the sign showed: "Stargates connected!"', () => /Stargates connected!/.test(obs.dial || '')));
  } else {
    list.push(c('the dial connected', () => /Stargates connected/.test(obs.dial || '')));
  }
  list.push(c('the opening was drawn', () => Boolean(obs.drawn)));
  if (!signDial(o) || o.built === 'console') list.push(...lightChecks(o, obs));
  if (o.portal !== 'group') list.push(c(`the opening is drawn as ${o.portal}`, () => (obs.drawn || '').toUpperCase() === o.portal));
  const dest = destOf(o);
  const stopped = o['far iris'] === 'shut after dial';
  if (farIris && stopped) list.push(farShutCheck);
  if (t === 'walk' || PETS[t]) {
    if (o['own iris'] === 'shut') {
      // The shut iris is drawn solid on the client, so a player walks into it and stops; the
      // plugin's "Iris is locked!" is only for a client that gets into the opening regardless.
      list.push(c('the Stand\'s own iris was shut at its lever', () => obs.ownIrisShut === true));
      list.push(c('Probe did not cross', () => obs.probeAt && obs.probeAt.dim === O && obs.probeAt.d > 5));
      list.push(c('Probe was stopped at the iris, in front of the Stand', () => obs.probeAt && obs.probeAt.home < 2));
    } else if (stopped) {
      list.push(c('Probe was told "Remote Iris is locked!"', () => /Remote Iris is locked!/.test(obs.walkChat || '')));
      list.push(c('Probe was put back at the Stand', () => obs.probeAt && obs.probeAt.dim === O && obs.probeAt.home < 3));
    } else {
      list.push(c(`Probe came out of ${dest}`, () => obs.probeAt && obs.probeAt.d < 1.5));
    }
    if (PETS[t] && !stopped && o['own iris'] !== 'shut') {
      if (t === 'sitting wolf') list.push(c('the sitting wolf stayed behind', () => obs.petStayed && !obs.petNear));
      else list.push(c(`the ${t} came along`, () => obs.petNear));
    }
    return list;
  }
  if (MOUNTS.includes(t)) {
    list.push(c(`the same ${t} came out of ${dest}`, () => obs.mountArrived));
    list.push(c(`Probe is still riding it`, () => obs.stillRiding));
    return list;
  }
  if (t.startsWith('minecart') || t.startsWith('boat')) {
    const kind = t.startsWith('minecart') ? 'cart' : 'boat';
    if (o['own iris'] === 'shut') {
      list.push(c('the Stand\'s own iris was shut at its lever', () => obs.ownIrisShut === true));
      // Not only "nothing arrived": it ran at least a block towards the iris and ended in front of
      // the opening's far side, so a vehicle that never moved fails.
      list.push(c(`the ${kind} ran at the iris and ended on the near side`, () => obs.vehicleRun
        && obs.vehicleRun.to <= obs.vehicleRun.from - 1 && obs.vehicleRun.to > -0.5));
      list.push(c(`the ${kind} did not cross`, () => !obs.vehicleArrived));
    } else {
      list.push(c(`the same ${kind} came out of ${dest}`, () => obs.vehicleArrived));
    }
    if (t.endsWith('ridden') && o['own iris'] !== 'shut') list.push(c('Probe is still in it', () => obs.stillRiding));
    return list;
  }
  if (t === 'item frame') return list.concat([c('the item frame stayed where it hung', () => obs.frameStayed)]);
  if (['armour stand', 'zombie', 'llama'].includes(t)) return list.concat([c(`the ${t} came out of ${dest}`, () => obs.mobArrived)]);
  const arrived = () => obs.arrival && obs.arrival.count > 0;
  // Seen leaving the launcher by the tick function, or taken by the plugin before it could be.
  // A lying item needs none: its summon is checked, and it is summoned in the opening.
  const launched = () => obs.launches && obs.launches.tagged + obs.launches.fresh > 0;
  if (t === 'projectile') list.push(c('it was launched (seen leaving the launcher)', launched));
  if (stopped) {
    list.push(c('it was stopped: nothing came out', () => obs.arrival && obs.arrival.count === 0));
    list.push(c('and nothing was left on the near side', () => obs.leftBehind === 0));
    return list;
  }
  if (t === 'projectile') {
    const p = o.projectile;
    const n = farOf(o).geom.normal;
    const along = () => obs.arrival.motion.x * n.x + obs.arrival.motion.z * n.z;
    list.push(c(`it came out of ${dest}`, arrived));
    if (!['pearl', 'trident'].includes(p)) list.push(c('flying on, away from the gate', () => arrived() && along() >= 0.5));
    list.push(c('none was left on the near side', () => obs.leftBehind === 0));
    if (o.launcher === 'bot' && !['pearl'].includes(p)) {
      const ints = offlineUuidInts(ctx.probe.name);
      list.push(c('its owner is still Probe', () => ints.every((i) => (obs.arrivalData || '').includes(String(i)))));
    }
    if (['tipped arrow', 'splash potion', 'lingering potion'].includes(p)) {
      // What left the launcher carried slowness, so a plain arrival is the plugin's doing.
      list.push(c(`the ${p} launched carried slowness`, () => /slowness/.test((obs.launches && obs.launches.first) || '')));
      list.push(c('its slowness survived', () => /slowness/.test(obs.arrivalData || '')));
    }
    if (p === 'pearl') list.push(c(`the pearl carried Probe to ${dest}`, () => obs.pearlCarried));
    if (p === 'trident' && o.launcher === 'bot') {
      list.push(c('the trident left Probe\'s inventory (thrown in survival)', () => obs.tridentThrown));
      list.push(c('the loyal trident came back to Probe', () => obs.tridentBack));
    }
    return list;
  }
  // Items and orbs.
  if (obs.tosses > 1) {
    // The harness's half first: all of them went out, and every one is accounted for (arrived, or
    // lying on this side), so a toss that failed or a stack Probe picked up again is not read as
    // the plugin losing it.
    if (t === 'dropped item') list.push(c(`all ${obs.tosses} left Probe's hand`, () => obs.tossedOut === obs.tosses));
    if (t === 'dispensed item') list.push(c(`the dispenser fired all ${obs.tosses}`, () => obs.dispenserLeft === 0 && /\[/.test(obs.dispenserHolds || '')));
    list.push(c(`all ${obs.tosses} are accounted for: arrived, or lying on this side`, () => obs.arrival
      && obs.arrival.count + (obs.leftCounts || []).reduce((a, b) => a + b, 0) === obs.tosses));
    list.push(c(`every one of the ${obs.tosses} came out of ${dest}`, () => obs.arrival && obs.arrival.count === obs.tosses && obs.leftBehind === 0));
  }
  list.push(c(`it came out of ${dest}`, arrived));
  if (t === 'xp' || t === 'lying item') {
    // Summoned with its tags, so it can be judged as an entity: the plugin moves it, not a copy.
    list.push(c('as itself (the same entity, tags and all)', () => obs.arrival && obs.arrival.same));
  }
  if (t !== 'xp') {
    // A dropped, dispensed or spilled stack is born untagged and is tagged on the next tick; the
    // plugin's sweep can take it in between, so it is judged as the design says, by its stack:
    // the one named, enchanted item (and a lying one by its identity as well, above).
    list.push(c('the same stack: one, still named and enchanted', () => /Relic of the Stand/.test(obs.arrivalData || '')
      && /sharpness/.test(obs.arrivalData || '') && /[Cc]ount: 1[b,}]/.test(obs.arrivalData || '')));
  }
  return list;
}

/**
 * G8: the chevrons locked in the shape's :L#n order. A dial lights waves 1 to 7 (8 into another
 * world); each wave's lock is the last time Probe's client was shown its cells turning lit
 * before the kawoosh (a spin's travelling light passes over the chevrons first), and they must
 * come in order, all lit.
 */
function lightChecks(o, obs) {
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  const last = ['Range', 'Annex'].includes(o.destination) ? 8 : 7;
  const waves = () => (obs.lightWaves || []).filter((w) => w.order <= last);
  return [
    c(`every chevron wave up to ${last} was shown lit`, () => waves().length > 0 && waves().every((w) => w.lit === w.cells)),
    // A Universe ring carries its locked chevrons round and puts them all back when the last
    // locks (StargateAnimator), so their own cells light together and show no order.
    o.spin === 'universe' ? c('Universe: every chevron cell lit together as the last locked', () => {
      const w = waves();
      return w.length > 1 && w.every((x) => x.at > 0) && w[w.length - 1].at - w[0].at < 100;
    })
    // Each wave in a later tick than the one before (at least 1, so a client reading two ticks
    // in one go is allowed for), and the whole order spread over the ticks it takes: waves drawn
    // all at once, in any order, fail.
    : c('the chevrons locked in the order of the shape\'s :L#n cells, one after another', () => {
      const w = waves();
      return w.length > 0 && w.every((x, i) => i === 0 || x.at > w[i - 1].at) && w.every((x) => x.at > 0)
        && w[w.length - 1].at - w[0].at >= (w.length - 1) * 25;
    }),
  ];
}

/** A player's build: by preview it was placed and offered; by hand every block needs listed was laid. */
function buildChecks(o, obs) {
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  const b = obs.built || { said: {} };
  const s = b.said || {};
  const shape = shapeOf(o);
  const group = buildGroup(o) || 'Standard';
  const list = [c(`the preview stood in the group: "Previewing ${shape} in ${group}."`, () => new RegExp(`Previewing ${shape} in ${group}\\.`).test(s.build || ''))];
  if (o.built === 'preview') {
    list.push(c(`\`gate preview place\` laid it: "Placed ${shape}."`, () => new RegExp(`Placed ${shape}\\.`).test(s.place || '')));
  } else {
    const blocks = () => (b.needs || []).filter((n) => !/button|sign/.test(n.material)).reduce((a, n) => a + n.count, 0);
    const laid = () => Object.entries(b.laid || {}).filter(([k]) => !['button', 'sign'].includes(k)).reduce((a, [, n]) => a + n, 0);
    list.push(c('Probe laid every block `needs` listed, one for one', () => blocks() > 0 && blocks() === laid()));
    list.push(c('and the button (and a sign-dial shape\'s sign)', () => (b.laid || {}).button === 1 && (!signDial(o) || (b.laid || {}).sign === 1)));
    list.push(c(`the guide said "${shape} is built! Press its button to check it."`, () => new RegExp(`${shape} is built!`).test(s.built || '')));
  }
  const offer = signDial(o) && o.built === 'hand' ? /Valid Sign Nav Stargate Design!/ : /Valid Stargate Design!/;
  list.push(c(`its button offered it: "${offer.source.replace(/\\/g, '')}"`, () => offer.test(s.offer || '')));
  list.push(c('`gate complete Stand` made it: "Gate successfully constructed."', () => /Gate successfully constructed/.test(s.complete || '')));
  if (signDial(o)) list.push(c('its dial sign is live, showing a destination (»...«)', () => Boolean(signShows(obs.signAfterBuild))));
  return list;
}

/**
 * #491 (from the fix's checklist): the cart's front, its centre plus half its 0.98 width along
 * the run, must end flush with the iris face (within 0.05, never past it at any tick) and at
 * rest; a rider stays seated; with the iris open (the control) the cart passes through.
 */
function cartChecks(o, obs) {
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  // Half the vehicle's length along the run: a minecart is 0.98 wide, a boat 1.375.
  const HALF = o.vehicle === 'boat' ? 0.6875 : 0.49;
  const t = obs.track;
  const face = obs.face;
  // The farthest the front got toward the face, and where it ended.
  const reach = () => (face.travel < 0 ? t.zmin - HALF : t.zmax + HALF);
  const end = () => (face.travel < 0 ? t.z - HALF : t.z + HALF);
  const past = (front) => (face.travel < 0 ? face.face - front : front - face.face); // > 0: over the face
  const list = [c('the cart ran (tracked every tick)', () => t && t.ticks > 5)];
  if (o['iris gate'] === 'incoming') list.push(c('the Stand is the open far end of Relay\'s wormhole', () => /Stargates connected/.test(obs.dial || '') && obs.drawn));
  if (obs.geom && obs.geom.flat) {
    // The control: a Horizontal gate's iris is real blocks in the floor, so the cart rolls over
    // it and must never sink into the opening (and, with it open, drops in).
    if (o['own iris'] === 'shut') list.push(c('the iris is shut', () => obs.ownIrisShut === true));
    if (o['own iris'] === 'open') {
      list.push(c('with the iris open, the cart dropped into the opening', () => t && t.ymin < -0.5));
      return list;
    }
    list.push(c('the cart rolled onto the shut iris', () => t && past(reach()) > 0.5));
    list.push(c('and never sank into the opening', () => t && t.ymin >= -0.01));
    return list;
  }
  if (o['own iris'] === 'open') {
    list.push(c('with the iris open, the cart passed through the opening', () => t && past(reach()) > 1));
    return list;
  }
  list.push(c('the iris is shut and drawn in the opening', () => obs.ownIrisShut === true && obs.irisShown && obs.irisShown !== 'air'));
  list.push(c('the cart\'s front never crossed the iris face', () => t && past(reach()) <= 0.001));
  list.push(c('it stopped flush with the face (within 0.05)', () => t && past(end()) <= 0.001 && past(end()) >= -0.05));
  list.push(c('and at rest', () => t && Math.abs(t.vx) < 0.001 && Math.abs(t.vz) < 0.001));
  if (o.rider === 'Probe') list.push(c('Probe is still seated', () => obs.riderSeated));
  return list;
}

// ---- shut by command ------------------------------------------------------------------------

/**
 * Once the checks are read: a trip that opened a wormhole has both ends closed by `force` (the
 * plugin's own close), rather than left open until timeout-shutdown, and the end state checked:
 * the plugin says so, and every end Probe can see (in its world, within view) is shown shut.
 */
async function shut(ctx, o) {
  const obs = ctx.observed;
  if (!(obs.drawn || /Stargates connected/.test(obs.dial || '')) || !obs.geom) return [];
  const kit = kitOf(ctx);
  const f = farOf(o);
  const ends = [{ name: STAND, dim: O, geom: obs.geom }, { name: f.name, dim: f.dim, geom: f.geom }];
  const probe = ctx.probe;
  // Only an end whose opening Probe's client has in view is judged: another world's, or one too
  // far off to be sent, would pass whatever the plugin did. And it must be seen drawn open before
  // the close, or an opening never drawn to Probe would read as shut.
  const { Vec3 } = require('vec3');
  const inView = (g) => g.opening.every((p) => probe.bot.blockAt(new Vec3(p.x, p.y, p.z)) !== null);
  let judged = ends.filter((e) => GateKit.drawnTo(probe, e.geom, e.dim) && inView(e.geom));
  if (!judged.length) {
    // Neither end in view (a trip that ended out of the Stand's world and away from the far end):
    // go back to the Stand, so one end is always judged rather than none.
    await probe.teleport(front(obs.geom, 8), O).catch(() => {});
    await ticks(20);
    judged = ends.filter((e) => e.name === STAND && GateKit.drawnTo(probe, e.geom, e.dim) && inView(e.geom));
  }
  for (const e of judged) e.seenOpen = (await kit.waitDrawn(probe, e.geom)) === true;
  obs.closed = [];
  for (const e of ends) obs.closed.push((await kit.force(e.name)).text);
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  const list = [c('closed by command: "<gate> has been closed, darkened, ..."', () => obs.closed.every((t) => /has been closed/.test(t)))];
  list.push(c('an end was in view to be judged shut', () => judged.length > 0));
  for (const e of judged) {
    list.push(c(`${e.name} shut on command: drawn open before, and its opening no longer drawn`,
      async () => e.seenOpen && (await kit.waitShut(probe, e.geom)) === true));
  }
  return list;
}

// ---- reset ------------------------------------------------------------------------------------

async function cleanup(ctx) {
  const kit = kitOf(ctx);
  await ctx.probe.dismount().catch(() => {});
  await ctx.watch.clear();
  await ctx.watch.untrack();
  if (await kit.exists(STAND)) await kit.remove(STAND);
  for (const g of Object.values(relay.farGates())) { await kit.force(g.name); await kit.edit(g.name, 'idc', '-clear'); }
  // A preview a failed build left standing (block displays only Probe sees).
  await new GateBuilder(ctx.server, ctx.probe).clearPreviews().catch(() => {});
  await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
  await ctx.server.run('kill @e[tag=wx_spit]');
  await ctx.server.run(`clear ${ctx.probe.name}`);
  await ctx.server.run(`gamemode creative ${ctx.probe.name}`);
  await ctx.server.run('execute in minecraft:overworld run fill -1 5 -135 1 6 -120 minecraft:air replace minecraft:glass');
}

module.exports = {
  id: 'g1',
  wing: 'gates',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: OPTIONS,
  needs: () => ({ config: {} }),
  refuses,
  stage,
  run: runTrip,
  checks,
  shut,
  cleanup,
  reset: 'wx:reset/g1',
  STAND,
  WORLDS,
};
