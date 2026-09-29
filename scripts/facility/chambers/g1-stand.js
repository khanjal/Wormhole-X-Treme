'use strict';
// G1, the Test Stand (design 3.1): the gate under test, built by the console form in the middle
// of the cell facing south, dialled to a far gate (Relay across the hall, Range in the nether,
// Annex in the End), and a traveller sent through. Every check is a fact recorded during the
// run: where Probe is, what its client was shown, what the tick function caught at the far
// exit, what the plugin said. A check never passes because nothing threw.
//
// Also here: the cart-at-a-shut-iris cells for issue #491 (traveller "cart at iris").

const campus = require('../lib/campus');
const { GateKit, WORLDS } = require('../lib/gatekit');
const shapes = require('../lib/shapes');
const { cellLayout } = require('../lib/blueprint');
const { ARMOURY, itemNbt, offlineUuidInts, exists } = require('../lib/menagerie');
const { atLeast } = require('../lib/version');
const { ticks } = require('../lib/probe');
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
    v('HorizontalSignDial', 'flat, dials by sign'), v('custom', 'Lab.shape with [C], [S:C] and [RS] cells (stage 5)'),
  ],
  group: [v('default', 'the first group in config.yml (Standard, obsidian)'), v('Atlantis', 'lapis; drawn by `edit group`'),
    v('Universe', 'polished blackstone'), v('MilkyWay', 'deepslate'), v('Diamond', 'autodiscovered from Lab.shape (stage 5)')],
  chevrons: [v('frame', 'chevrons built of the frame block'), v('lamp', 'chevron cells replaced by redstone lamps'),
    v('copper bulb', 'copper bulbs (1.21+)')],
  built: [v('console', '`gate build ... x y z facing`'), v('preview', 'preview place as a player (stage 5)'), v('hand', 'every block by hand (stage 5)')],
  dial: [v('console', '`gate dial Stand <to>`'), v('dhd', 'press the DHD button, then /dial'), v('sign right', 'sign dial forward'),
    v('sign left', 'sign dial back'), v('redstone', 'a lever on [RD]')],
  spin: [v('default', 'the group\'s pattern'), ...['top', 'chevron', 'lap', 'fill', 'pegasus', 'chase', 'universe', 'overshoot', 'none'].map((p) => v(p, `\`edit spin ${p}\``))],
  destination: [v('Relay', 'across the hall'), v('Range', 'the nether'), v('Annex', 'the End'),
    v('busy', 'Relay already dialled elsewhere: refused'), v('self', 'dial the Stand itself: refused')],
  traveller: [
    v('walk', 'Probe on foot'), v('minecart', 'an empty cart on rails'), v('minecart ridden', 'Probe in the cart'),
    v('boat', 'an empty boat on blue ice'), v('boat ridden', 'Probe rows it'),
    ...['horse', 'camel', 'pig', 'donkey', 'llama', 'strider'].map((k) => v(k, `Probe riding a ${k}`)),
    v('wolf', 'a tamed wolf follows Probe'), v('cat', 'a tamed cat follows'), v('parrot', 'a tamed parrot follows'),
    v('sitting wolf', 'told to sit: must stay'), v('projectile', 'see projectile, launcher and angle'),
    v('dropped item', 'Probe drops a named, enchanted sword into the opening'), v('dispensed item', 'a dispenser drops it in'),
    v('spilled items', 'a hopper cart broken in the opening'), v('xp', 'an experience orb'), v('armour stand', 'swept through'),
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
  'cart speed': [v('0.4', 'full speed on rails (blocks per tick)'), v('0.2', 'half speed')],
  'run-up': [v('6', 'rails in front of the face'), v('10', ''), v('16', '')],
};

const MOUNTS = ['horse', 'camel', 'pig', 'donkey', 'llama', 'strider'];
const PETS = { wolf: 'wolf', cat: 'cat', parrot: 'parrot', 'sitting wolf': 'wolf' };
const SIGN_SHAPES = ['StandardSignDial', 'MinimalSignDial', 'HorizontalSignDial'];
const EXPECTED_REFUSAL = {
  self: /Can't dial own gate without solar flare/,
  busy: /Target gate is currently active\./,
  'shut no code': /Remote Iris is active; provide the IDC to unlock\./,
};

/** What a combination cannot be, with the reason; the self-test expects REFUSED:<reason>. */
function refuses(o, version = '26.1.2') {
  const flat = !['custom'].includes(o.shape) && shapes.isFlat(o.shape);
  if (o.shape === 'custom') return 'the custom Lab.shape arrives in stage 5';
  if (o.group === 'Diamond') return 'the Diamond group is autodiscovered from Lab.shape (stage 5)';
  if (o.built !== 'console') return 'building by preview or by hand arrives in stage 5';
  if (o.chevrons === 'copper bulb' && !atLeast(version, '1.21')) return 'there are no copper bulbs before 1.21';
  if (['sign right', 'sign left', 'redstone'].includes(o.dial) && !SIGN_SHAPES.includes(o.shape)) return `${o.shape} has no dial sign or [RD] block`;
  if (['busy', 'self'].includes(o.destination) && o['far iris'] !== 'open') return 'the dial is refused anyway; nothing for an iris to stop';
  const t = o.traveller;
  if (t === 'cart at iris') {
    if (flat) return 'the Horizontal control for #491 is not built yet';
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
  if (await kit.exists(STAND)) await kit.remove(STAND);

  const geom = kit.place(o.shape, PLACE.facing, PLACE);
  obs.geom = geom;
  const needsIdc = o['own iris'] === 'shut' || o.traveller === 'cart at iris';
  try {
    const built = await kit.build(STAND, geom, { dim: O, idc: needsIdc ? IDC_NEAR : null, floorY: PLACE.floorY });
    obs.build = built.text;
  } catch (err) {
    obs.build = err.message;
    obs.buildRefused = true;
    if (SIGN_SHAPES.includes(o.shape)) return; // the plugin's own refusal: judged by the checks
    throw err;
  }
  if (o.group !== 'default') obs.group = (await kit.edit(STAND, 'group', o.group)).text;
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
    const at = t === 'llama' ? front(geom, 2.5) : front(geom, 6);
    await ctx.menagerie.animal(t, at, ctx.tag, { saddled: true });
  }
  if (PETS[t]) {
    const at = front(geom, 7, 1.5);
    await ctx.menagerie.animal(PETS[t], at, ctx.tag, { owner: ctx.probe.name, sitting: t === 'sitting wolf' });
  }
}

/** #491: a straight rail line at right angles into the opening's bottom row, ending just short. */
async function stageCartRun(ctx, o, geom) {
  const n = Number(o['run-up']);
  const face = cartFace(geom, o.approach);
  const x = Math.floor(geom.centre.x);
  const lane = [];
  for (let i = 1; i <= n; i++) lane.push(face.firstBlock + face.dir * (i - 1));
  const z0 = Math.min(...lane);
  const z1 = Math.max(...lane);
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
  const z = Math.floor(geom.opening[0].z);
  const sign = geom.normal.z; // facing south: +1
  if (approach === 'front') return { face: sign > 0 ? z + 1 : z, firstBlock: z + sign, dir: sign, travel: -sign };
  return { face: sign > 0 ? z : z + 1, firstBlock: z - sign, dir: -sign, travel: sign };
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
  if (o.dial === 'dhd') {
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
  if (t === 'walk' || PETS[t]) {
    const mark = obs.chat.length;
    const target = geom.flat ? { x: geom.centre.x, z: geom.centre.z } : beyond;
    await probe.walkTo(target, { within: 0.4, ms: 15000, until: () => probe.dimension !== O || probe.distanceTo(dest.arrival) < 3 }).catch(() => {});
    await until(async () => probe.dimension === f.dim && probe.distanceTo(dest.arrival) < 3, 4000);
    await probe.settle();
    obs.probeAt = { dim: probe.dimension, d: probe.distanceTo(dest.arrival), home: probe.distanceTo(geom.arrival), pos: probe.position.floored().toString() };
    obs.walkChat = chatSince(ctx, mark);
    if (PETS[t]) {
      obs.petNear = await until(() => near(ctx, f.dim, probe.position, `tag=${ctx.tag},tag=wx_kind_${PETS[t]}`, 6), 5000);
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
    await probe.teleport(front(geom, t === 'llama' ? 4 : 7.5), O);
    await probe.mount(ctx.tag);
    if (t === 'llama') {
      // A llama cannot be steered; it is pushed, as a lead would pull it.
      await run(ctx, `data merge entity @e[tag=${ctx.tag},tag=wx_kind_llama,limit=1] {Motion:[0.0d,0.0d,${(-geom.normal.z * 1.2).toFixed(1)}d]}`);
    } else {
      await probe.drive(beyond, { speed: 0.25, ms: 15000 }).catch((e) => { obs.driveError = e.message; });
    }
    await until(() => near(ctx, f.dim, dest.itemArrival, `tag=${ctx.tag},tag=wx_kind_${t}`, 4), 6000);
    await ticks(10);
    obs.mountArrived = await near(ctx, f.dim, dest.itemArrival, `tag=${ctx.tag},tag=wx_kind_${t}`, 4);
    obs.stillRiding = Boolean(probe.bot.vehicle) && probe.dimension === f.dim && probe.distanceTo(dest.arrival) < 5;
    return undefined;
  }
  if (t === 'minecart' || t === 'minecart ridden' || t === 'boat' || t === 'boat ridden') {
    const kind = t.startsWith('minecart') ? 'minecart' : 'boat';
    const at = front(geom, 6);
    await ctx.menagerie.summon(kind, { ...at, y: kind === 'minecart' ? 0.1 : 0 }, ctx.tag);
    const sel = `@e[tag=${ctx.tag},tag=wx_kind_${kind},limit=1]`;
    const mark = obs.chat.length;
    if (t.endsWith('ridden')) {
      await probe.teleport(front(geom, 7.5), O);
      await probe.mount(ctx.tag);
    }
    if (t === 'boat ridden') {
      await probe.drive(beyond, { speed: 0.35, ms: 15000 }).catch((e) => { obs.driveError = e.message; });
    } else {
      await run(ctx, `data merge entity ${sel} {Motion:[0.0d,0.0d,${(-geom.normal.z * 0.6).toFixed(1)}d]}`);
    }
    const target = kind === 'minecart' ? dest.cartArrival : dest.cartArrival;
    await until(() => near(ctx, f.dim, target, `tag=${ctx.tag},tag=wx_kind_${kind}`, 5), 8000);
    await ticks(20);
    obs.vehicleArrived = await near(ctx, f.dim, target, `tag=${ctx.tag},tag=wx_kind_${kind}`, 6);
    obs.stillRiding = Boolean(probe.bot.vehicle) && probe.dimension === f.dim;
    obs.walkChat = chatSince(ctx, mark);
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
  if (t === 'dropped item') {
    await ctx.menagerie.give(probe.name, 'relic');
    await probe.waitForItem(relic.id);
    await probe.hold(relic.id);
    await probe.teleport({ ...front(geom, geom.flat ? 0 : 1.3), y: PLACE.floorY }, O);
    await probe.look({ x: inOpening.x, y: bottom.y + 0.5, z: inOpening.z });
    await probe.dropOne();
  } else if (t === 'dispensed item') {
    const d = front(geom, 2);
    const dz = Math.floor(d.z);
    await run(ctx, `setblock ${Math.floor(d.x)} ${bottom.y + 1} ${dz} minecraft:dispenser[facing=north]{Items:[${itemNbt(version, relic, ',Slot:0b')}]}`);
    await run(ctx, `setblock ${Math.floor(d.x)} ${bottom.y + 2} ${dz} minecraft:redstone_block`);
  } else if (t === 'spilled items') {
    const items = `Items:[${itemNbt(version, relic, ',Slot:0b')}]`;
    await ctx.menagerie.summon('hopper_minecart', { ...inOpening, y: bottom.y }, ctx.tag, items);
    await run(ctx, `kill @e[tag=${ctx.tag},tag=wx_kind_hopper_minecart]`);
  } else if (t === 'xp') {
    await ctx.menagerie.summon('experience_orb', { ...inOpening, y: bottom.y + 0.2 }, ctx.tag, 'Value:7s');
    await run(ctx, `tag @e[tag=${ctx.tag},tag=wx_kind_experience_orb] add wx_launched`);
  } else if (t === 'armour stand') {
    await ctx.menagerie.summon('armor_stand', { ...inOpening, y: bottom.y }, ctx.tag);
  } else if (t === 'zombie') {
    await ctx.menagerie.summon('zombie', { ...inOpening, y: bottom.y }, ctx.tag, 'NoAI:1b,PersistenceRequired:1b,Silent:1b');
  } else if (t === 'item frame') {
    // Hung on the frame block beside the opening's middle row, facing out of the gate.
    const side = { x: mid.x + 3 * geom.right.x, y: mid.y, z: mid.z };
    const hang = { x: side.x + geom.normal.x, y: side.y, z: side.z + geom.normal.z };
    obs.framePlace = hang;
    await ctx.menagerie.summon('item_frame', { x: hang.x, y: hang.y, z: hang.z }, ctx.tag, `Facing:${geom.normal.z > 0 ? 3 : 2}b`);
  }
  const scan = 20; // entity-scan-interval-ticks, the default
  if (['armour stand', 'zombie'].includes(t)) {
    const kind = t === 'zombie' ? 'zombie' : 'armor_stand';
    obs.mobArrived = await until(() => near(ctx, f.dim, dest.itemArrival, `tag=${ctx.tag},tag=wx_kind_${kind}`, 5), (scan + 60) * 50);
    return undefined;
  }
  if (t === 'item frame') {
    await ticks(scan * 3);
    obs.frameStayed = await near(ctx, O, obs.framePlace, `tag=${ctx.tag},tag=wx_kind_item_frame`, 1);
    return undefined;
  }
  await ctx.watch.waitArrival((scan + 60) * 50);
  await ticks(4);
  obs.arrival = await ctx.watch.arrival();
  obs.leftBehind = await ctx.watch.leftBehind();
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
  const aim = { x: geom.centre.x, y: geom.flat ? geom.centre.y - 0.5 : geom.centre.y, z: Math.floor(geom.opening[0].z) + 0.5 };
  let from;
  if (o.angle === 'above') from = { x: aim.x, y: 6, z: aim.z };
  else if (o.angle === 'point-blank') from = front(geom, 1.6);
  else if (o.angle === 'glancing') from = front(geom, 10 * Math.cos(Math.PI / 6), 10 * Math.sin(Math.PI / 6));
  else from = front(geom, 10);
  // Arrows, pearls and tridents are left to fly on (a pearl must land, a trident come back).
  const freeze = !['pearl', 'trident'].includes(p);
  const eye = { x: from.x, y: from.y + 1.62, z: from.z };
  await ctx.watch.arm(O, o.launcher === 'dispenser' ? { x: from.x, y: aim.y, z: from.z } : eye, { ...dest.itemArrival, dim: f.dim }, { freeze });
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
    }
  }
  obs.launched = true;
  await ctx.watch.waitArrival(6000);
  await ticks(6);
  obs.arrival = await ctx.watch.arrival();
  obs.leftBehind = await ctx.watch.leftBehind();
  obs.arrivalData = (await ctx.server.run(`execute in ${f.dim} run data get entity @e[tag=wx_arrived,limit=1]`)).lines.join(' ');
  if (p === 'pearl') {
    obs.pearlCarried = await until(async () => probe.dimension === f.dim && probe.distanceTo(dest.arrival) < 10, 5000);
  }
  if (p === 'trident') {
    obs.tridentBack = await until(async () => probe.bot.inventory.items().some((i) => i.name === 'trident'), 10000);
  }
  obs.shotChat = chatSince(ctx, mark);
  return undefined;
}

async function testerName(ctx) {
  const others = Object.keys(ctx.probe.bot.players).filter((n) => n !== ctx.probe.name && n !== 'Tester');
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
  const at = { x: lane.x + 0.5, y: 0.1, z: lane.start + 0.5 };
  await ctx.menagerie.summon('minecart', at, ctx.tag);
  const sel = `@e[tag=${ctx.tag},tag=wx_kind_minecart,limit=1]`;
  if (o.rider === 'Probe') {
    await probe.teleport({ x: at.x + face.dir * 1.2, y: 0, z: at.z + face.dir * 1.2 }, O);
    await probe.mount(ctx.tag);
  }
  await ctx.watch.track(sel);
  const speed = Number(o['cart speed']);
  await run(ctx, `data merge entity ${sel} {Motion:[0.0d,0.0d,${(face.travel * speed).toFixed(2)}d]}`);
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
  if (SIGN_SHAPES.includes(o.shape)) {
    list.push(c('the plugin refused a console build of a sign-dial gate', () => obs.buildRefused && /dials by sign/.test(obs.build)));
    return list;
  }
  list.push(c('the Stand was built', () => /Built Stand/.test(obs.build || '')));
  if (o.group !== 'default') list.push(c(`the Stand draws the ${o.group} group`, () => new RegExp(`is now on group ${o.group}`).test(obs.group || '')));
  if (o.spin !== 'default') list.push(c(`the Stand dials with ${o.spin}`, () => new RegExp(`now dials with ${o.spin}`).test(obs.spin || '')));
  if (o.portal !== 'group') list.push(c(`the portal is set to ${o.portal}`, () => new RegExp(`portal material set to: ${o.portal}`).test(obs.portal || '')));
  const t = o.traveller;
  if (t === 'cart at iris') return list.concat(cartChecks(o, obs));
  const refusal = EXPECTED_REFUSAL[o.destination] || EXPECTED_REFUSAL[o['far iris']];
  if (refusal) {
    list.push(c(`the dial was refused: "${refusal.source.replace(/\\/g, '')}"`, () => refusal.test(obs.dial || '')));
    list.push(c('the opening stayed air', () => obs.openingShown === 0));
    return list;
  }
  if (o.dial === 'dhd') list.push(c('the DHD button lit the gate', () => /Gate successfully activated/.test(obs.dial || '')));
  list.push(c('the dial connected', () => /Stargates connected/.test(obs.dial || '')));
  list.push(c('the opening was drawn', () => Boolean(obs.drawn)));
  if (o.portal !== 'group') list.push(c(`the opening is drawn as ${o.portal}`, () => (obs.drawn || '').toUpperCase() === o.portal));
  const dest = destOf(o);
  const stopped = o['far iris'] === 'shut after dial';
  if (t === 'walk' || PETS[t]) {
    if (o['own iris'] === 'shut') {
      list.push(c('Probe was told "Iris is locked!"', () => /Iris is locked!/.test(obs.walkChat || '')));
      list.push(c('Probe did not cross', () => obs.probeAt && obs.probeAt.dim === O && obs.probeAt.d > 5));
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
    if (o['own iris'] === 'shut') list.push(c(`the ${kind} did not cross`, () => !obs.vehicleArrived));
    else list.push(c(`the same ${kind} came out of ${dest}`, () => obs.vehicleArrived));
    if (t.endsWith('ridden') && o['own iris'] !== 'shut') list.push(c('Probe is still in it', () => obs.stillRiding));
    return list;
  }
  if (t === 'item frame') return list.concat([c('the item frame stayed where it hung', () => obs.frameStayed)]);
  if (t === 'armour stand' || t === 'zombie') return list.concat([c(`the ${t} came out of ${dest}`, () => obs.mobArrived)]);
  const arrived = () => obs.arrival && obs.arrival.count > 0;
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
    if (['tipped arrow', 'splash potion', 'lingering potion'].includes(p)) list.push(c('its slowness survived', () => /slowness/.test(obs.arrivalData || '')));
    if (p === 'pearl') list.push(c(`the pearl carried Probe to ${dest}`, () => obs.pearlCarried));
    if (p === 'trident') list.push(c('the loyal trident came back to Probe', () => obs.tridentBack));
    return list;
  }
  // Items and orbs.
  list.push(c(`it came out of ${dest}`, arrived));
  list.push(c('as itself (the same entity, tags and all)', () => obs.arrival && obs.arrival.same));
  if (t !== 'xp') list.push(c('still named and enchanted', () => /Relic of the Stand/.test(obs.arrivalData || '') && /sharpness/.test(obs.arrivalData || '')));
  return list;
}

/**
 * #491 (from the fix's checklist): the cart's front, its centre plus half its 0.98 width along
 * the run, must end flush with the iris face (within 0.05, never past it at any tick) and at
 * rest; a rider stays seated; with the iris open (the control) the cart passes through.
 */
function cartChecks(o, obs) {
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  const HALF = 0.49;
  const t = obs.track;
  const face = obs.face;
  // The farthest the front got toward the face, and where it ended.
  const reach = () => (face.travel < 0 ? t.zmin - HALF : t.zmax + HALF);
  const end = () => (face.travel < 0 ? t.z - HALF : t.z + HALF);
  const past = (front) => (face.travel < 0 ? face.face - front : front - face.face); // > 0: over the face
  const list = [c('the cart ran (tracked every tick)', () => t && t.ticks > 5)];
  if (o['iris gate'] === 'incoming') list.push(c('the Stand is the open far end of Relay\'s wormhole', () => /Stargates connected/.test(obs.dial || '') && obs.drawn));
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

// ---- reset ------------------------------------------------------------------------------------

async function cleanup(ctx) {
  const kit = kitOf(ctx);
  await ctx.probe.dismount().catch(() => {});
  await ctx.watch.clear();
  await ctx.watch.untrack();
  if (await kit.exists(STAND)) await kit.remove(STAND);
  for (const g of Object.values(relay.farGates())) { await kit.force(g.name); await kit.edit(g.name, 'idc', '-clear'); }
  await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
  await ctx.server.run('kill @e[tag=wx_spit]');
  await ctx.server.run(`clear ${ctx.probe.name}`);
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
  cleanup,
  reset: 'wx:reset/g1',
  STAND,
  WORLDS,
};
