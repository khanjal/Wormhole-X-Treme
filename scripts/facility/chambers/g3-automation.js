'use strict';
// G3, the Automation Bay (design 3.1, G16 and G6 of the inventory): the gate driven by redstone and
// by command blocks. Bay is a StandardSignDial gate built by preview as a player (the console
// cannot build a sign-dial gate), on network Bay with one peer, BayFar, so its sign always shows
// »BayFar«. Facts from the plugin's source (WormholeXTremeRedstoneListener, StargateBlockSetup,
// StargateDialManager, Coordinates and the console commands):
//
//  - Redstone reaches a gate only as a BlockRedstoneEvent, on a rising edge, at its [RD] block or
//    a redstone source in the block round it (or round its DHD button): a lever, a button, a
//    pressure plate, a detector rail, a repeater, a comparator. A sign gate idle dials what its
//    sign shows; open, it has its shutdown put back (redstone-extend-open-time), never past
//    max-open-seconds; a falling edge does nothing. One trigger per gate per 250 ms.
//  - [RA]: a lever the plugin places at completion, on while the wormhole is open, so a lamp by
//    it lights while open and goes out at shutdown.
//  - Command blocks: `gate build ... ~ ~ ~` (from the command block's own block), `gate dial`,
//    `gate force`, `ring build <world> ~ ~ ~ ~ ~ ~`, `ring fire <world> ~ ~ ~`,
//    `mirror create <name> <world> ~ ~ ~`, `beam admin send <player> <place>` (no `~` there). A
//    command block's answer is its LastOutput.

const campus = require('../lib/campus');
const text = require('../lib/text');
const { GateKit } = require('../lib/gatekit');
const { GateBuilder } = require('../lib/gatebuild');
const { RingKit } = require('../lib/rings');
const mirrors = require('../lib/mirrors');
const { cellLayout } = require('../lib/blueprint');
const { ticks } = require('../lib/probe');

const def = campus.chamber('g3');
const O = campus.OVERWORLD;
const NET = 'Bay';
const BAY = { name: 'Bay', shape: 'StandardSignDial', facing: 'north', cx: 47, openingAt: -70, floorY: 0 };
const FAR = { name: 'BayFar', shape: 'Standard', facing: 'north', cx: 58, openingAt: -84, floorY: 0 };
// The console: one command block, a button on top, by the gallery.
const CB = { x: 44, y: 0, z: -62 };
const CB_GATE = { name: 'CbGate', shape: 'Minimal', facing: 'north', cx: 62, openingAt: -70, floorY: 0 };
const RINGS = [{ x: 47, y: 0, z: -82 }, { x: 62, y: 0, z: -64 }];
const MIRROR = { name: 'CbMirror', x: 42, y: 1, z: -88, facing: 'south' };

const geomOf = (g) => new GateKit(null).place(g.shape, g.facing, g);
// The ring pair a console run made, kept here since a cleanup runs with a fresh context.
let ringsMade = false;

const INPUTS = {
  lever: 'a lever by the DHD, pulled',
  button: 'a stone button by the DHD, pressed',
  plate: 'a pressure plate by the DHD, stood on',
  'detector rail': 'a cart rolled along a line of rails onto a detector rail by the DHD',
  repeater: 'a lever four repeaters down a line from the DHD, pulled',
  comparator: 'a comparator by the DHD, fed by a lever',
  console: 'a command block: see the console row',
};
const CONSOLE = {
  'gate build': '`gate build Minimal CbGate world ~ ~ ~ north net=Cb`',
  'gate dial': '`gate dial Bay BayFar`',
  'gate force': '`gate force Bay` on an open Bay',
  'ring build': '`ring build world ~ ~ ~ ~ ~ ~` between two slab circles',
  'ring fire': '`ring fire world ~ ~ ~` into a pair\'s circle',
  'mirror create': '`mirror create CbMirror world ~ ~ ~` at a banner',
  'beam send': '`beam admin send Probe2 BeamLab`',
};
const TIMING = {
  default: 'the default timings: judged open, then shut by command',
  'timeout 0': '`timeout-shutdown 0`, no cap: open until somebody goes through, a dropped signal changing nothing',
  hold: '`timeout-shutdown 5`, `max-open-seconds 14`: a press every 2 s holds it open past 5 s, and never past 14',
};

const v = (value, why) => ({ value, label: value, why });

/** The block the redstone inputs stand round: in front of the DHD and one along its right. */
function inputCell(geom) {
  const A = geom.holder;
  return { x: A.x + geom.normal.x + geom.right.x, y: A.y, z: A.z + geom.normal.z + geom.right.z };
}

/** A step of `k` along the gate's right from a point. */
const along = (geom, p, k) => ({ x: p.x + geom.right.x * k, y: p.y, z: p.z + geom.right.z * k });

/**
 * The lamp [RA]'s lever lights: in the floor beside it, toward the ring (the block the lever,
 * placed with the default facing, hangs on), where nothing an input powers touches it.
 */
function lampOf(geom) {
  const ra = geom.redstone.RA;
  return { x: ra.x - geom.normal.x, y: ra.y, z: ra.z - geom.normal.z };
}

/** The facing a repeater or comparator has to take its input from the gate's right. */
function fromRight(geom) {
  return { '1,0': 'east', '-1,0': 'west', '0,1': 'south', '0,-1': 'north' }[`${geom.right.x},${geom.right.z}`];
}

const rel = (p) => `~${p.x - CB.x} ~${p.y - CB.y} ~${p.z - CB.z}`;

async function test(ctx, cmd) {
  return (await ctx.server.run(cmd)).lines.some((l) => /Test passed/.test(l));
}

function openNow(probe, geom) {
  const { Vec3 } = require('vec3');
  return geom.opening.every((c) => { const b = probe.bot.blockAt(new Vec3(c.x, c.y, c.z)); return b && !['air', 'cave_air'].includes(b.name); });
}

async function lampLit(ctx, geom) {
  const l = lampOf(geom);
  return test(ctx, `execute if block ${l.x} ${l.y} ${l.z} minecraft:redstone_lamp[lit=true]`);
}

module.exports = {
  id: 'g3',
  wing: 'gates',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    input: Object.entries(INPUTS).map(([k, why]) => v(k, why)),
    console: Object.entries(CONSOLE).map(([k, why]) => v(k, why)),
    timing: Object.entries(TIMING).map(([k, why]) => v(k, why)),
  },
  needs: (o) => {
    if (o.timing === 'timeout 0') return { config: { 'timeout-shutdown': '0', 'max-open-seconds': '0' } };
    if (o.timing === 'hold') return { config: { 'timeout-shutdown': '5', 'max-open-seconds': '14', 'redstone-extend-open-time': 'true' } };
    return { config: {} };
  },
  refuses: (o) => {
    if (o.input !== 'console' && o.console !== 'gate build') return 'the console row is the console input';
    if (o.input === 'console' && o.timing !== 'default') return 'the timing rows are dialled by redstone';
    if (o.timing === 'hold' && o.input !== 'button') return 'the hold is a button pressed again and again';
    return null;
  },

  async stage(ctx, o) {
    const obs = ctx.observed;
    const kit = new GateKit(ctx.server);
    // The gates stand in the cell, so the reset before every run took their blocks: build again.
    for (const name of [BAY.name, FAR.name, CB_GATE.name]) if (await kit.exists(name)) await kit.remove(name);
    await kit.build(FAR.name, geomOf(FAR), { dim: O, net: NET, floorY: FAR.floorY });
    const geom = geomOf(BAY);
    const r = await new GateBuilder(ctx.server, ctx.probe).byPreview(BAY.name, geom, { net: NET, dim: O, floorY: BAY.floorY });
    obs.built = r.said;
    if (!r.ok) throw new Error(`Bay was not built: ${r.said.complete || r.said.place || 'no answer'}`);
    obs.sign = (await ctx.server.run(`data get block ${geom.sign.x} ${geom.sign.y} ${geom.sign.z} front_text.messages`)).lines.join(' ').replace(/§./g, '');
    // The lamp by [RA]'s lever.
    const lamp = lampOf(geom);
    await ctx.server.run(`setblock ${lamp.x} ${lamp.y} ${lamp.z} minecraft:redstone_lamp`);
    if (o.input !== 'console') await this.stageInput(ctx, o, geom);
    else await this.stageConsole(ctx, o);
  },

  async stageInput(ctx, o, geom) {
    const s = ctx.server;
    const at = inputCell(geom);
    const face = fromRight(geom);
    const put = (p, block) => s.run(`setblock ${p.x} ${p.y} ${p.z} ${block}`);
    if (o.input === 'lever') await put(at, 'minecraft:lever[face=floor]');
    if (o.input === 'button') await put(at, 'minecraft:stone_button[face=floor]');
    if (o.input === 'plate') await put(at, 'minecraft:stone_pressure_plate');
    if (o.input === 'detector rail') {
      const railShape = geom.right.x !== 0 ? 'east_west' : 'north_south';
      await put(at, `minecraft:detector_rail[shape=${railShape}]`);
      for (let k = 1; k <= 5; k++) await put(along(geom, at, k), `minecraft:rail[shape=${railShape}]`);
    }
    if (o.input === 'repeater') {
      for (let k = 0; k < 4; k++) await put(along(geom, at, k), `minecraft:repeater[facing=${face}]`);
      await put(along(geom, at, 4), 'minecraft:lever[face=floor]');
    }
    if (o.input === 'comparator') {
      await put(at, `minecraft:comparator[facing=${face}]`);
      await put(along(geom, at, 1), 'minecraft:lever[face=floor]');
    }
  },

  async stageConsole(ctx, o) {
    const s = ctx.server;
    const commands = {
      'gate build': () => {
        const h = geomOf(CB_GATE).holder;
        return `wormhole gate build ${CB_GATE.shape} ${CB_GATE.name} world ${rel(h)} ${CB_GATE.facing} net=Cb`;
      },
      'gate dial': () => `wormhole gate dial ${BAY.name} ${FAR.name}`,
      'gate force': () => `wormhole gate force ${BAY.name}`,
      'ring build': () => `wormhole ring build world ${rel(RINGS[0])} ${rel(RINGS[1])}`,
      'ring fire': () => `wormhole ring fire world ${rel(RINGS[0])}`,
      'mirror create': () => `wormhole mirror create ${MIRROR.name} world ${rel(MIRROR)}`,
      'beam send': () => 'wormhole beam admin send Probe2 BeamLab',
    };
    if (o.console === 'gate build') await new GateKit(s).clearSite(geomOf(CB_GATE), { dim: O, floorY: CB_GATE.floorY });
    if (o.console.startsWith('ring')) {
      ringsMade = true;
      const rk = new RingKit(s, ctx.probe);
      for (const r of RINGS) await rk.lay('ODD', r, 'smooth_stone_slab');
      if (o.console === 'ring fire') {
        const pair = await rk.build(RINGS[0], RINGS[1]);
        if (pair.error) throw new Error(`ring pair for the fire: ${pair.error}`);
        ctx.observed.pair = pair.id;
      }
    }
    if (o.console === 'mirror create') {
      // Solid two blocks round the opening (the banner's block and the one under it): nothing to
      // warn of. The cell's floor is the row below; the wall stays inside the cell, so its reset clears it.
      await s.run(`fill ${MIRROR.x - 2} 0 ${MIRROR.z - 1} ${MIRROR.x + 2} 3 ${MIRROR.z - 1} minecraft:polished_deepslate`);
      await new mirrors.MirrorKit(s).banner({ ...MIRROR, dim: O });
    }
    if (o.console === 'gate force') ctx.observed.dial = (await new GateKit(s).dial(BAY.name, FAR.name)).text;
    if (o.console === 'beam send') await ctx.facility.second();
    const cmd = commands[o.console]();
    ctx.observed.command = cmd;
    await s.run(`setblock ${CB.x} ${CB.y} ${CB.z} minecraft:command_block{Command:${text.quoteSingle(cmd)},TrackOutput:1b}`);
    await s.run(`setblock ${CB.x} ${CB.y + 1} ${CB.z} minecraft:stone_button[face=floor]`);
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    const probe = ctx.probe;
    const geom = geomOf(BAY);
    const kit = new GateKit(ctx.server);
    if (o.input === 'console') {
      await probe.teleport({ x: CB.x + 0.5, y: 0, z: CB.z - 1.5, yaw: 0 }, O);
      await ticks(5);
      await probe.press({ x: CB.x, y: CB.y + 1, z: CB.z });
      await ticks(20);
      obs.output = (await ctx.server.run(`data get block ${CB.x} ${CB.y} ${CB.z} LastOutput`)).lines.join(' ').replace(/§./g, '');
      if (o.console === 'gate build') obs.listed = await kit.exists(CB_GATE.name);
      if (o.console === 'gate dial' || o.console === 'gate force') {
        await probe.teleport({ x: BAY.cx + 0.5, y: 0, z: BAY.openingAt - 9.5, yaw: 0 }, O);
        await ticks(20);
        obs.openAfter = openNow(probe, geom);
      }
      if (o.console === 'ring build') obs.pair = (/Ring pair (\w+) is live/.exec(obs.output) || [])[1] || null;
      if (o.console === 'ring fire') await ticks(240); // the cycle, so the pair can be taken down after
      if (o.console === 'mirror create') obs.mirrors = (await new mirrors.MirrorKit(ctx.server).list()).names;
      if (o.console === 'beam send') {
        const p2 = ctx.facility.probe2;
        const dest = campus.ROUTES.beams.find((b) => b.name === 'BeamLab');
        const end = Date.now() + 15000;
        while (Date.now() < end && !(p2.position.distanceTo(new (require('vec3').Vec3)(dest.x + 0.5, dest.y, dest.z + 0.5)) < 3)) await ticks(5);
        obs.p2At = p2.position.clone();
        obs.p2Near = p2.position.distanceTo(new (require('vec3').Vec3)(dest.x + 0.5, dest.y, dest.z + 0.5)) < 3;
      }
      return;
    }
    // Watch from in front of Bay, the input to one side.
    const at = inputCell(geom);
    const watch = { x: BAY.cx + 0.5, y: 0, z: BAY.openingAt - 9.5, yaw: 0 };
    await probe.teleport(watch, O);
    await ticks(10);
    obs.lampBefore = await lampLit(ctx, geom);
    const t0 = Date.now();
    const trigger = async () => {
      if (o.input === 'plate') {
        await probe.teleport({ x: at.x + 0.5, y: at.y, z: at.z + 0.5, yaw: 0 }, O);
      } else if (o.input === 'detector rail') {
        const start = along(geom, at, 5);
        const dir = { x: -geom.right.x * 0.4, z: -geom.right.z * 0.4 };
        await ctx.menagerie.summon('minecart', { x: start.x + 0.5, y: start.y + 0.1, z: start.z + 0.5 }, ctx.tag, `Motion:[${dir.x.toFixed(1)}d,0.0d,${dir.z.toFixed(1)}d]`);
      } else if (o.input === 'repeater') {
        await probe.click(along(geom, at, 4));
      } else if (o.input === 'comparator') {
        await probe.click(along(geom, at, 1));
      } else if (o.input === 'button') {
        await probe.press(at);
      } else {
        await probe.click(at);
      }
    };
    // Beside the input, so it is in reach (the opening is in view from anywhere in the bay).
    const reach = o.input === 'repeater' ? along(geom, at, 4) : o.input === 'comparator' ? along(geom, at, 1) : at;
    if (!['plate', 'detector rail'].includes(o.input)) await probe.teleport({ x: reach.x + 0.5, y: 0, z: reach.z + 0.5 - geom.normal.z * 2, yaw: 0 }, O);
    await trigger();
    // A detector rail is judged pressed by the cart first, so a cart that never got there fails as that.
    const pressed = async () => o.input === 'detector rail' && !obs.railPressed
      && (await ctx.server.run(`execute if block ${at.x} ${at.y} ${at.z} minecraft:detector_rail[powered=true]`)).lines.some((l) => /Test passed/.test(l));
    obs.opened = await (async () => {
      const end = Date.now() + 10000;
      while (Date.now() < end) {
        if (await pressed()) obs.railPressed = true;
        if (openNow(probe, geom)) return true;
        await ticks(2);
      }
      return false;
    })();
    const openedAt = Date.now();
    obs.openedMs = openedAt - t0;
    await ticks(20);
    obs.lampOpen = await lampLit(ctx, geom);
    if (o.timing === 'timeout 0') {
      // Open until somebody goes through (the default shutdown is 38 s, so a wait here would prove
      // nothing): the lever back off changes nothing, and the trip shuts it.
      if (o.input === 'lever') { await probe.click(at); await ticks(60); obs.openAfterDrop = openNow(probe, geom); }
      // Then Probe goes through: a timeout-0 gate shuts once somebody has travelled.
      await probe.teleport(watch, O);
      const beyond = { x: BAY.cx + 0.5, y: 0, z: BAY.openingAt + 2.5 };
      await probe.walkTo(beyond, { within: 0.5, ms: 15000, until: () => probe.distanceTo(geomOf(FAR).arrival) < 3 }).catch(() => {});
      await ticks(20);
      obs.traveled = probe.distanceTo(geomOf(FAR).arrival) < 3;
      obs.shutAfterTrip = await kit.waitShut(probe, geom, 6000);
    }
    if (o.timing === 'hold') {
      // A press every 2 s from the moment it opened until 13 s (the default shutdown here is 5 s,
      // the cap 14 s), then only watched: a press on a shut sign gate would dial it again. Without
      // the cap the last press, at 12 s, would hold it open until 17 s.
      const since = () => (Date.now() - openedAt) / 1000;
      obs.samples = [];
      let pressed = 0;
      while (since() < 17) {
        if (since() < 13 && since() - pressed >= 2) { await probe.press(at); pressed = since(); }
        await ticks(10);
        obs.samples.push({ t: since(), open: openNow(probe, geom) });
      }
    }
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const c = (name, t) => ({ name, afterReset: null, test: async () => Boolean(await t()) });
    const list = [
      c('Bay was built by preview and completed', () => /Gate successfully constructed/.test((obs.built || {}).complete || '')),
      c('its dial sign shows its one peer: »BayFar«', () => /»BayFar«/.test(obs.sign || '')),
    ];
    if (o.input === 'console') {
      const out = () => obs.output || '';
      switch (o.console) {
        case 'gate build':
          return list.concat([c('LastOutput: "Built CbGate at ..."', () => /Built CbGate at /.test(out())), c('and CbGate is listed', () => obs.listed)]);
        case 'gate dial':
          return list.concat([c('LastOutput: "Stargates connected."', () => /Stargates connected\./.test(out())), c('and Bay is open', () => obs.openAfter)]);
        case 'gate force':
          return list.concat([
            c('Bay was open first', () => /Stargates connected/.test(obs.dial || '')),
            c('LastOutput: "Bay has been closed, darkened, ..."', () => /Bay has been closed, darkened/.test(out())),
            c('and Bay is shut', () => obs.openAfter === false),
          ]);
        case 'ring build':
          return list.concat([c('LastOutput: "Ring pair <id> is live and public."', () => Boolean(obs.pair))]);
        case 'ring fire':
          return list.concat([c('LastOutput: "Ring pair ... is counting down."', () => /is counting down\./.test(out()))]);
        case 'mirror create':
          return list.concat([
            // A command block keeps only the last line it is told, and after "is this banner" come
            // the lines about the room it sees, so the made mirror is judged by the list.
            c('LastOutput is the plugin\'s, and no error', () => /:: /.test(out()) && !/error ::/.test(out())),
            c('and CbMirror is listed', () => (obs.mirrors || []).includes('CbMirror')),
          ]);
        case 'beam send':
          return list.concat([
            c('LastOutput: "Beaming Probe2 to BeamLab."', () => /Beaming Probe2 to BeamLab\./.test(out())),
            c('and Probe2 arrived at the BeamLab pad', () => obs.p2Near),
          ]);
        default:
          return list;
      }
    }
    list.push(c('the lamp by [RA] was dark before', () => obs.lampBefore === false));
    if (o.input === 'detector rail') list.push(c('the cart rolled onto the detector rail and pressed it', () => obs.railPressed === true));
    list.push(c(`the ${o.input} dialled Bay: its opening was drawn`, () => obs.opened));
    list.push(c('and the lamp by [RA] lit while it was open', () => obs.lampOpen));
    if (o.timing === 'timeout 0') {
      if (o.input === 'lever') list.push(c('the lever back off (a falling edge) left it open', () => obs.openAfterDrop));
      list.push(c('Probe went through to BayFar', () => obs.traveled));
      list.push(c('and then Bay shut', () => obs.shutAfterTrip === true));
    }
    if (o.timing === 'hold') {
      const s = () => obs.samples || [];
      list.push(c('pressed again and again it stayed open 12 s (well past the 5 s shutdown)', () => s().some((x) => x.t > 11.5 && x.t < 12.5) && s().filter((x) => x.t < 12.5).every((x) => x.open)));
      list.push(c('and it shut at 14 s all the same, pressed at 12 s (max-open-seconds)', () => s().filter((x) => x.t > 15).length > 0 && s().filter((x) => x.t > 15).every((x) => !x.open)));
    }
    return list;
  },

  /** Every trip that opened Bay has it closed by command, and the lamp by [RA] must go out. */
  async shut(ctx, o) {
    const obs = ctx.observed;
    if (o.input === 'console') return [];
    const kit = new GateKit(ctx.server);
    obs.closed = (await kit.force(BAY.name)).text;
    await kit.force(FAR.name);
    await ticks(20);
    const geom = geomOf(BAY);
    const c = (name, t) => ({ name, afterReset: null, test: async () => Boolean(await t()) });
    return [
      c('closed by command: "Bay has been closed, ..."', () => /Bay has been closed/.test(obs.closed || '')),
      c('and the lamp by [RA] went out', async () => !(await lampLit(ctx, geom))),
    ];
  },

  async cleanup(ctx) {
    // The bay's gates stand in its cell, which the reset clears: they go with it.
    const kit = new GateKit(ctx.server);
    for (const name of [BAY.name, FAR.name, CB_GATE.name]) if (await kit.exists(name)) await kit.remove(name);
    await new GateBuilder(ctx.server, ctx.probe).clearPreviews().catch(() => {});
    // Any ring pair a console run made, by what is there rather than what LastOutput named (player-
    // only to take down: as Probe); never the transit pair.
    if (ringsMade) {
      const rk = new RingKit(ctx.server, ctx.probe);
      const keep = ctx.facility.keepRings || new Set();
      const { ids } = await rk.list().catch(() => ({ ids: [] }));
      for (const id of ids.filter((x) => !keep.has(x))) await rk.remove(id).catch(() => {});
      ringsMade = false;
    }
    await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
    await ctx.server.run(`wormhole mirror remove ${MIRROR.name}`);
    if (ctx.facility.probe2) await ctx.facility.probe2.teleport(campus.TRANSIT.home).catch(() => {});
  },

  reset: 'wx:reset/g3',

  GATES: { BAY, FAR, CB_GATE },
};
