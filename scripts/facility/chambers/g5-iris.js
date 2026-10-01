'use strict';
// G5, the Iris Chamber (design 3.1): two Standard gates facing each other twenty apart, IrisA in
// the Atlantis group (a yellow glass iris) and IrisS in the Standard group (stone), both with a
// code, on their own network. Every check is on what a client is sent, packet by packet (G13,
// G14, G15):
//
//  - steps: the iris shut and opened at its lever; a watcher's block-change packets in the
//    opening, grouped by when they came, must be the plugin's steps for the style (lib/iris.js
//    works them out as IrisSweep does), cell for cell, a step every gate-iris-step-ticks, with
//    more than the band limit merged;
//  - layers: the gates dialled to each other and the iris shut on the open one, then a watcher
//    in front, behind or to the side is shown the two layers (iris in the plane and the horizon
//    one behind it, from the front; the horizon in the plane and the iris one further off, from
//    behind; the iris alone, from the side);
//  - arrow: shot at the shut iris, it is taken out (at the open iris, the control, it flies on);
//  - place: a block put into the opening while the iris is shut is refused.

const campus = require('../lib/campus');
const { GateKit } = require('../lib/gatekit');
const { cellLayout } = require('../lib/blueprint');
const iris = require('../lib/iris');
const { ticks } = require('../lib/probe');

const def = campus.chamber('g5');
const O = campus.OVERWORLD;
const NET = 'Iris';
// A crossing's time against (steps - 1) x step ticks: measured 0.78x to 0.99x on 1.21.11 (an
// opening's first step tends to reach the client late); 2 ticks read as 4 is 2x, 4 as 2 is 0.5x.
const PACE_LOW = 0.7;
const PACE_HIGH = 1.35;
// Twenty apart across the cell, facing each other: IrisA's DHD on the east of its ring, IrisS's
// on the west.
const GATES = {
  Atlantis: { name: 'IrisA', group: 'Atlantis', idc: '3333', facing: 'east', cx: -125, openingAt: -60, floorY: 0, iris: 'yellow_stained_glass', horizon: ['packed_ice', 'blue_ice'] },
  Standard: { name: 'IrisS', group: null, idc: '4444', facing: 'west', cx: -125, openingAt: -40, floorY: 0, iris: 'stone', horizon: ['water'] },
};

const v = (value, why) => ({ value, label: value, why });

function geomOf(g) {
  return new GateKit(null).place('Standard', g.facing, g);
}

/** A point `d` out from a gate's opening (in front: toward its DHD; negative: behind), `s` to its side. */
function spot(g, d, s = 0) {
  const geom = geomOf(g);
  const n = geom.normal;
  const r = geom.right;
  const mid = geom.opening[Math.floor(geom.opening.length / 2)];
  const yaw = d >= 0 ? (geom.yaw + 180) % 360 : geom.yaw;
  return { x: mid.x + 0.5 + n.x * d + r.x * s, y: g.floorY, z: mid.z + 0.5 + n.z * d + r.z * s, yaw };
}

/** The block the client is shown at a point. */
function shown(probe, p) {
  const { Vec3 } = require('vec3');
  const b = probe.bot.blockAt(new Vec3(p.x, p.y, p.z));
  return b ? b.name : null;
}

module.exports = {
  id: 'g5',
  wing: 'gates',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    gate: [v('Standard', 'IrisS: a stone iris'), v('Atlantis', 'IrisA: a yellow glass iris, ice behind it')],
    check: [v('steps', 'the packets of a closing and an opening, step by step'), v('layers', 'the two-layer iris on an open gate'),
      v('arrow', 'an arrow at the iris'), v('place', 'a block put into the opening while shut')],
    animation: iris.STYLES.map((s) => v(s, `\`gate edit <gate> iris-animation ${s}\``)),
    'step ticks': [v('2', 'gate-iris-step-ticks 2 (the default)'), v('4', 'gate-iris-step-ticks 4')],
    'max ticks': [v('20', 'gate-iris-sweep-max-ticks 20 (the default: ten steps at 2)'), v('4', '4: at most two bands'), v('0', '0: no limit')],
    watcher: [v('Probe2', 'Probe2 stands by while Probe pulls the lever'), v('puller', 'Probe, who pulled it, watches')],
    side: [v('front', 'layers: from the DHD side'), v('behind', 'layers: from behind the ring'), v('side', 'layers: level with the ring, off to one side')],
    iris: [v('shut', 'arrow: at the shut iris'), v('open', 'arrow: at the open one, the control')],
  },
  needs: (o) => ({ config: o.check === 'steps' ? { 'gate-iris-step-ticks': o['step ticks'], 'gate-iris-sweep-max-ticks': o['max ticks'] } : {} }),
  refuses: (o) => {
    const stepsOnly = o.animation !== 'sweep' || o['step ticks'] !== '2' || o['max ticks'] !== '20' || o.watcher !== 'Probe2';
    if (o.check !== 'steps' && stepsOnly) return 'the animation, pace and watcher rows are the steps check';
    if (o.check !== 'layers' && o.side !== 'front') return 'the side row is the layers check';
    if (o.check !== 'arrow' && o.iris !== 'shut') return 'the iris row is the arrow check';
    return null;
  },

  async stage(ctx, o) {
    const kit = new GateKit(ctx.server);
    // The gates stand in the cell, so the reset before every run took their blocks: build again.
    await module.exports.fixture(ctx);
    const g = GATES[o.gate];
    if (o.check === 'steps') ctx.observed.style = (await kit.edit(g.name, 'iris-animation', o.animation)).text;
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    const kit = new GateKit(ctx.server);
    const g = GATES[o.gate];
    const geom = geomOf(g);
    const probe = ctx.probe;
    if (o.check === 'steps') {
      const watcher = o.watcher === 'Probe2' ? await ctx.facility.second() : probe;
      const lever = geom.lever;
      const pull = { x: lever.x + geom.normal.x * 2 + 0.5, y: g.floorY, z: lever.z + geom.normal.z * 2 + 0.5, yaw: (geom.yaw + 180) % 360 };
      await probe.teleport(pull, O);
      if (watcher !== probe) await watcher.teleport(spot(g, 6, 2), O);
      // A teleport is followed by redraws of every drawn iris for 60 ticks: let them pass.
      await ticks(70);
      const { stepTicks, maxSteps } = iris.pace(Number(o['step ticks']), Number(o['max ticks']));
      const expected = iris.closingOrder(geom.opening, o.animation, maxSteps);
      const crossMs = (expected.length * stepTicks + 20) * 50;
      let rec = iris.recordCells(watcher.bot, geom.opening);
      obs.closeLever = (await probe.click(lever)).getProperties().powered;
      await ticks(crossMs / 50);
      obs.closeEvents = rec.stop();
      obs.shutShown = geom.opening.map((c) => shown(watcher, c));
      await ticks(20);
      rec = iris.recordCells(watcher.bot, geom.opening);
      obs.openLever = (await probe.click(lever)).getProperties().powered;
      await ticks(crossMs / 50);
      obs.openEvents = rec.stop();
      obs.openShown = geom.opening.map((c) => shown(watcher, c));
      obs.expectClose = expected;
      obs.expectOpen = iris.openingOrder(geom.opening, o.animation, maxSteps);
      obs.stepTicks = stepTicks;
      obs.close = iris.stepsOf(obs.closeEvents, { to: g.iris });
      obs.open = iris.stepsOf(obs.openEvents, { to: 'air', first: true });
      return;
    }
    if (o.check === 'layers') {
      const other = Object.values(GATES).find((x) => x !== g);
      obs.dial = (await kit.dial(other.name, g.name)).text;
      await probe.teleport(spot(g, 6), O);
      obs.drawn = await kit.waitOpen(probe, geom).catch((e) => e.message);
      obs.irisShut = await kit.toggleIris(probe, geom, O);
      const p2 = await ctx.facility.second();
      const at = { front: spot(g, 4), behind: spot(g, -4), side: spot(g, 0, 6) }[o.side];
      await p2.teleport(at, O);
      await ticks(40);
      const F = geom.normal;
      obs.ring = geom.opening.map((c) => shown(p2, c));
      obs.before = geom.opening.map((c) => shown(p2, { x: c.x + F.x, y: c.y, z: c.z + F.z }));
      obs.after = geom.opening.map((c) => shown(p2, { x: c.x - F.x, y: c.y, z: c.z - F.z }));
      return;
    }
    if (o.check === 'arrow') {
      if (o.iris === 'shut') obs.irisShut = await kit.toggleIris(probe, geom, O);
      // The tracker notices an iris (open gate or drawn shut iris) once a second.
      await probe.teleport(spot(g, 8), O);
      await ticks(30);
      await ctx.server.run(`give ${probe.name} minecraft:bow`);
      await ctx.server.run(`give ${probe.name} minecraft:arrow 4`);
      await probe.waitForItem('bow');
      await probe.hold('bow');
      const c = geom.centre;
      await probe.face({ x: c.x, y: c.y, z: c.z });
      // The arrow as Probe's client sees it, every tick: how far it got past the ring's plane
      // (along the gate's facing: negative is behind it), and whether it was taken away.
      const F = geom.normal;
      const ring = geom.opening[0];
      const along = (p) => (p.x - (ring.x + 0.5)) * F.x + (p.z - (ring.z + 0.5)) * F.z;
      const seen = { spawned: false, least: Infinity, gone: false, lastAlong: null };
      let arrow = null;
      const onSpawn = (e) => { if (!arrow && e.name === 'arrow') { arrow = e; seen.spawned = true; } };
      const onGone = (e) => { if (arrow && e.id === arrow.id) seen.gone = true; };
      const onTick = () => { if (arrow && !seen.gone) { seen.lastAlong = along(arrow.position); seen.least = Math.min(seen.least, seen.lastAlong); } };
      probe.bot.on('entitySpawn', onSpawn);
      probe.bot.on('entityGone', onGone);
      const clock = setInterval(onTick, 25);
      try {
        await probe.drawAndLoose(20);
        await ticks(60);
      } finally {
        clearInterval(clock);
        probe.bot.off('entitySpawn', onSpawn);
        probe.bot.off('entityGone', onGone);
      }
      obs.arrow = seen;
      return;
    }
    // place: into the middle of the opening, against the frame block under it.
    obs.irisShut = await kit.toggleIris(probe, geom, O);
    await probe.teleport(spot(g, 3), O);
    await ticks(10);
    const mid = geom.opening.filter((c) => c.y === Math.min(...geom.opening.map((q) => q.y))).sort((a, b) => a.x - b.x || a.z - b.z)[1];
    const under = { x: mid.x, y: mid.y - 1, z: mid.z };
    const heard = [];
    const onChat = (m) => heard.push(m.toString());
    probe.bot.on('message', onChat);
    try {
      await probe.wield('stone');
      const { Vec3 } = require('vec3');
      await probe.placeAgainst(mid, { ref: probe.bot.blockAt(new Vec3(under.x, under.y, under.z)), face: new Vec3(0, 1, 0) }, 2000).catch(() => {});
      await ticks(10);
    } finally {
      probe.bot.off('message', onChat);
    }
    obs.placeSaid = heard.join(' / ');
    obs.cellAfter = (await ctx.server.run(`execute if block ${mid.x} ${mid.y} ${mid.z} minecraft:air`)).lines.some((l) => /Test passed/.test(l));
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const g = GATES[o.gate];
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    if (o.check === 'steps') {
      const n = () => obs.expectClose.length;
      // Paced by the server's ticks, read on a client's clock: the whole crossing must take
      // (steps - 1) x step ticks, within PACE_LOW to PACE_HIGH of it and a tick, since one late
      // tick moves a single step but not the crossing. Tight enough that 2 ticks is not 4.
      const ratio = (steps) => ((steps[steps.length - 1].t - steps[0].t) / ((steps.length - 1) * obs.stepTicks * 50));
      const paced = (steps) => {
        const want = (steps.length - 1) * obs.stepTicks * 50;
        const took = steps[steps.length - 1].t - steps[0].t;
        return took >= want * PACE_LOW - 50 && took <= want * PACE_HIGH + 50;
      };
      const pace = (steps) => (steps && steps.length > 1 ? `${ratio(steps).toFixed(2)}x` : '-');
      console.log(`  g5 pace at ${obs.stepTicks} ticks: closing ${pace(obs.close)}, opening ${pace(obs.open)} of (steps - 1) x step ticks`);
      const list = [
        c(`${o.animation}: the iris set as the gate's ("${g.name}'s iris now crosses as ${o.animation}")`, () => new RegExp(`${g.name}'s iris now crosses as ${o.animation}`).test(obs.style || '')),
        c('the lever shut it and opened it again', () => obs.closeLever === true && obs.openLever === false),
        c(`closing was drawn in the plugin's ${o.animation} steps, cell for cell`, () => iris.sameSteps(obs.close, obs.expectClose)),
        c(`and every cell ended ${g.iris}`, () => obs.shutShown.every((x) => x === g.iris)),
        c(`opening was drawn in its steps back, cell for cell`, () => iris.sameSteps(obs.open, obs.expectOpen)),
        c('and every cell ended air', () => obs.openShown.every((x) => x === 'air')),
      ];
      if (o.animation !== 'instant') {
        list.push(c(`a step every ${obs.stepTicks || '?'} ticks, both ways (took ${pace(obs.close)} and ${pace(obs.open)} of it)`, () => n() > 1 && paced(obs.close) && paced(obs.open)));
      }
      return list;
    }
    if (o.check === 'layers') {
      const all = (xs, want) => xs.length > 0 && xs.every((x) => want.includes(x));
      const list = [
        c(`${g.name} was dialled and opened`, () => /Stargates connected/.test(obs.dial || '') && obs.drawn && obs.drawn !== 'air' && !/never drawn/.test(obs.drawn)),
        c('and its iris shut on the open gate', () => obs.irisShut === true),
      ];
      if (o.side === 'front') {
        list.push(c(`from the front: the iris (${g.iris}) in the ring`, () => all(obs.ring, [g.iris])));
        list.push(c(`and the horizon (${g.horizon.join(' / ')}) one behind it`, () => all(obs.after, g.horizon)));
      } else if (o.side === 'behind') {
        list.push(c('from behind: the horizon (water) in the ring', () => all(obs.ring, ['water'])));
        list.push(c(`and the iris (${g.iris}) one further off, on the DHD side`, () => all(obs.before, [g.iris])));
      } else {
        list.push(c(`from the side: the iris (${g.iris}) in the ring`, () => all(obs.ring, [g.iris])));
        list.push(c('and nothing drawn a block off it either way', () => all(obs.before, ['air']) && all(obs.after, ['air'])));
      }
      return list;
    }
    if (o.check === 'arrow') {
      const a = () => obs.arrow || {};
      const list = [c('Probe\'s client saw the arrow leave the bow', () => a().spawned)];
      if (o.iris === 'open') return list.concat([c('the iris open: the arrow flew through the opening and on, past its plane', () => a().least < -1)]);
      return list.concat([
        c('the iris is shut', () => obs.irisShut === true),
        c('the arrow never got past the iris', () => a().least > -0.5),
        // (The plugin takes it the tick after it is loosed, from where it is.)
        c('it was taken away, not left stuck anywhere', () => a().gone),
      ]);
    }
    return [
      c('the iris is shut', () => obs.irisShut === true),
      c(`refused: "You cannot build inside the gate '${g.name}'."`, () => new RegExp(`You cannot build inside the gate '${g.name}'`).test(obs.placeSaid || '')),
      c('and the opening cell is still air', () => obs.cellAfter),
    ];
  },

  /** The gates go back to idle with their irises open and the server's animation. */
  async cleanup(ctx) {
    const kit = new GateKit(ctx.server);
    for (const g of Object.values(GATES)) {
      if (!(await kit.exists(g.name))) continue;
      await kit.force(g.name);
      await kit.edit(g.name, 'iris-animation', 'default');
    }
    await ctx.server.run(`clear ${ctx.probe.name}`);
    await ctx.server.run('execute in minecraft:overworld run kill @e[type=minecraft:arrow,x=-66,y=0,z=-140,dx=33,dy=16,dz=31]');
    if (ctx.facility.probe2) await ctx.facility.probe2.teleport(campus.TRANSIT.home).catch(() => {});
  },

  reset: 'wx:reset/g5',

  /** Builds the two gates, each with its code, on network Iris; IrisA dressed in Atlantis. */
  async fixture(ctx) {
    const kit = new GateKit(ctx.server);
    for (const g of Object.values(GATES)) {
      if (await kit.exists(g.name)) await kit.remove(g.name);
      await kit.build(g.name, geomOf(g), { dim: O, net: NET, idc: g.idc, floorY: g.floorY });
      if (g.group) {
        const r = await kit.edit(g.name, 'group', g.group);
        if (!new RegExp(`is now on group ${g.group}`).test(r.text)) throw new Error(`${g.name} group: ${r.text}`);
      }
    }
    return `built ${Object.values(GATES).map((g) => g.name).join(', ')} on network ${NET}`;
  },

  GATES,
};
