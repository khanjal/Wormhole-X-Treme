'use strict';
// G2, the Shape Gallery (design 3.1): the six console-buildable shapes in a row along the back
// wall, on network Gallery, built once a session and kept. It is the manual playground: dial any
// to any. Its tests of upkeep (validate, regen), sign colours, protection and one-way travel
// arrive in stage 5; this stage has the one trip that proves the row works: dial a gate to its
// neighbour and walk through.
//
// (StandardSignDial, which the design wants here for its five-neighbour dial sign, cannot be
// built by the console: the plugin refuses a sign-dial shape from coordinates. Stage 5.)

const campus = require('../lib/campus');
const { GateKit } = require('../lib/gatekit');
const { cellLayout } = require('../lib/blueprint');
const { ticks } = require('../lib/probe');

const def = campus.chamber('g2');
const G = campus.GATES.gallery;
const O = campus.OVERWORLD;

function gallery() {
  const kit = new GateKit(null);
  return G.row.map(([shape, cx]) => ({ name: shape, geom: kit.place(shape, G.facing, { ...G, cx }) }));
}

function neighbour(name) {
  const row = G.row.map(([n]) => n);
  return row[(row.indexOf(name) + 1) % row.length];
}

const STAGE5 = 'arrives in stage 5 with the upkeep and protection tests';

module.exports = {
  id: 'g2',
  wing: 'gates',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    gate: G.row.map(([n]) => ({ value: n, label: n, why: `dial ${n} to ${neighbour(n)} and walk through` })),
    check: [
      { value: 'walk', label: 'dial and walk', why: 'the gate opens and takes Probe to its neighbour' },
      { value: 'validate', label: 'validate', why: `a frame block knocked out, then validate (${STAGE5})` },
      { value: 'regen', label: 'regen', why: `regen -fill / -shape / -water (${STAGE5})` },
      { value: 'signs', label: 'sign colours', why: `sign-color-* read back (${STAGE5})` },
      { value: 'protection', label: 'protection', why: `break the frame or build in the opening, de-opped (${STAGE5})` },
      { value: 'one way', label: 'one way', why: `walk into the destination ring (${STAGE5})` },
    ],
  },
  refuses: (o) => (o.check !== 'walk' ? `${o.check} ${STAGE5}` : null),

  async stage(ctx, o) {
    const kit = new GateKit(ctx.server);
    for (const g of gallery()) await kit.force(g.name);
    ctx.observed.from = gallery().find((g) => g.name === o.gate);
    ctx.observed.to = gallery().find((g) => g.name === neighbour(o.gate));
    // Both gates stand whole as the trip starts: the cell's reset wipes the gallery, and dialling
    // gates whose frames are gone proves little.
    ctx.observed.gaps = [];
    for (const g of [ctx.observed.from, ctx.observed.to]) {
      // Forty spread over the frame is plenty: a wipe takes every block, and Massive has 460.
      const step = Math.ceil(g.geom.frame.length / 40);
      for (const p of g.geom.frame.filter((_, i) => i % step === 0)) {
        const r = await ctx.server.run(`execute if block ${p.x} ${p.y} ${p.z} minecraft:air`);
        if (r.lines.some((l) => /Test passed/.test(l))) ctx.observed.gaps.push(`${g.name} ${p.x} ${p.y} ${p.z}`);
      }
    }
  },

  async run(ctx) {
    const kit = new GateKit(ctx.server);
    const { from, to } = ctx.observed;
    const probe = ctx.probe;
    const g = from.geom;
    const start = { x: g.centre.x, y: 0, z: Math.floor(g.opening[0].z) + 0.5 + g.normal.z * 6, yaw: 180 };
    await probe.teleport(start, O);
    ctx.observed.dial = (await kit.dial(from.name, to.name)).text;
    ctx.observed.drawn = await kit.waitOpen(probe, g).catch((e) => { ctx.observed.openError = e.message; return null; });
    if (!ctx.observed.drawn) return;
    if (g.flat) {
      // A flat gate is stepped into, as G1 does it: Probe is put just above the middle of the
      // opening and falls. (Walked to, it stops at the rim of the hole; it only ever passed on a
      // gallery the pre-run reset had floored over.)
      await probe.teleport({ x: g.centre.x, y: G.floorY + 0.2, z: g.centre.z }, O).catch(() => {});
    } else {
      const target = { x: g.centre.x, z: Math.floor(g.opening[0].z) + 0.5 - g.normal.z * 2.5 };
      await probe.walkTo(target, { within: 0.4, ms: 15000, until: () => probe.distanceTo(to.geom.arrival) < 3 }).catch(() => {});
    }
    const end = Date.now() + 4000;
    while (Date.now() < end && probe.distanceTo(to.geom.arrival) >= 1.5) await ticks(5);
    await probe.settle();
    ctx.observed.landed = probe.distanceTo(to.geom.arrival);
  },

  checks(ctx) {
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    return [
      c(`${obs.from ? obs.from.name : 'the gate'} and ${obs.to ? obs.to.name : 'its neighbour'} stood whole (no frame block missing)`, () => obs.gaps && obs.gaps.length === 0),
      c('the dial connected', () => /Stargates connected/.test(obs.dial || '')),
      c('the opening was drawn', () => Boolean(obs.drawn)),
      c(`Probe came out of ${obs.to ? obs.to.name : 'the neighbour'}`, () => obs.landed !== undefined && obs.landed < 1.5),
    ];
  },

  /** Shut by command (see facility.runChamber): both ends closed with `force`, then seen shut. */
  async shut(ctx) {
    const obs = ctx.observed;
    if (!/Stargates connected/.test(obs.dial || '')) return [];
    const kit = new GateKit(ctx.server);
    // Each end the plugin draws to Probe (within 64: Horizontal's neighbour Massive is 99 off), seen
    // drawn open before the close, or an opening never drawn would read as shut.
    const judged = [obs.from, obs.to].filter((g) => GateKit.drawnTo(ctx.probe, g.geom, O));
    const seen = [];
    for (const g of judged) seen.push((await kit.waitDrawn(ctx.probe, g.geom)) === true);
    obs.closed = [];
    for (const g of [obs.from, obs.to]) obs.closed.push((await kit.force(g.name)).text);
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    return [
      c('closed by command: "<gate> has been closed, ..."', () => obs.closed.every((t) => /has been closed/.test(t))),
      ...judged.map((g, i) => c(`${g.name} shut on command: drawn open before, and its opening no longer drawn`,
        async () => seen[i] && (await kit.waitShut(ctx.probe, g.geom)) === true)),
    ];
  },

  async cleanup(ctx) {
    const kit = new GateKit(ctx.server);
    for (const g of gallery()) await kit.force(g.name);
  },

  reset: 'wx:reset/g2',

  /** Builds the six gates (again, if they are there). */
  async fixture(ctx) {
    const kit = new GateKit(ctx.server);
    for (const g of gallery()) {
      if (await kit.exists(g.name)) await kit.remove(g.name);
      await kit.build(g.name, g.geom, { dim: O, net: G.net, floorY: G.floorY });
    }
    return `built ${gallery().map((g) => g.name).join(', ')} on network ${G.net}`;
  },

  gallery,
};
