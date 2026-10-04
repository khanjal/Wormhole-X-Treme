'use strict';
// R4, the Build Bench (design 3.2): a creative cell for laying rings by hand. Hands-on first;
// `Run` has Probe lay each faulty circle and read back the plugin's refusal, in its own words
// (RingCommand, RingSurvey), then pair a good one and cancel a half-made one.

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');
const { RingKit } = require('../lib/rings');
const trip = require('../lib/ringtrip');

const def = campus.chamber('r4');
const A = { x: 88, y: 0, z: 14 };
const B = { x: 99, y: 0, z: 14 };
const SLAB = 'smooth_stone_slab';

const CASES = {
  pair: { why: 'two good circles: paired', expect: /Ring pair \w+ is live\. Step into either end\./ },
  cancel: { why: 'one circle, then `ring cancel`', expect: /Forgotten\. The circle you laid is still there/ },
  'mixed slabs': { why: 'one oak slab among stone ones', expect: /That ring is built from more than one kind of slab\./ },
  'mixed halves': { why: 'one top slab among bottom ones', expect: /Some of those slabs rest on the floor and others hang from the ceiling\./ },
  'double slab': { why: 'one double slab: not a slab to the plugin', expect: /No ring of slabs here\./ },
  'filled disc': { why: 'a slab in the middle too', expect: /That circle is filled in\./ },
  'low ceiling': { why: 'a block three above the pad', expect: /There is not enough clear air for the rings\./ },
  'built inside': { why: 'a block standing in the circle', expect: /There is something built inside that ring\./ },
  'hole in floor': { why: 'a hole under the circle', expect: /That ring has a hole in its floor\./ },
};

module.exports = {
  id: 'r4',
  wing: 'rings',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    case: Object.entries(CASES).map(([value, c]) => ({ value, label: value, why: c.why })),
  },
  refuses: () => null,

  async stage(ctx, o) {
    const kit = new RingKit(ctx.server, ctx.probe);
    const obs = ctx.observed;
    obs.chat = [];
    trip.listen(ctx.probe, obs.chat);
    const odd = { 'mixed slabs': 'minecraft:oak_slab[type=bottom]', 'mixed halves': `minecraft:${SLAB}[type=top]`, 'double slab': `minecraft:${SLAB}[type=double]` }[o.case];
    await kit.lay('ODD', A, SLAB, { odd });
    if (o.case === 'pair') await kit.lay('ODD', B, SLAB);
    const s = ctx.server;
    if (o.case === 'filled disc') await s.run(`setblock ${A.x + 1} 0 ${A.z} minecraft:${SLAB}[type=bottom]`);
    if (o.case === 'low ceiling') await s.run(`setblock ${A.x + 1} 3 ${A.z} minecraft:white_concrete`);
    if (o.case === 'built inside') await s.run(`setblock ${A.x + 1} 1 ${A.z} minecraft:white_concrete`);
    if (o.case === 'hole in floor') await s.run(`setblock ${A.x + 1} -1 ${A.z} minecraft:air`);
  },

  async run(ctx, o) {
    const kit = new RingKit(ctx.server, ctx.probe);
    const obs = ctx.observed;
    await ctx.probe.teleport({ x: A.x + 0.5, y: 0, z: A.z + 0.5 });
    obs.said = await kit.ask('/wormhole ring create', { until: /noted|live|No ring|kind of slab|Some of those|filled in|clear air|built inside|hole|close by|overlaps/ });
    if (o.case === 'pair') {
      await ctx.probe.teleport({ x: B.x + 0.5, y: 0, z: B.z + 0.5 });
      obs.said = await kit.ask('/wormhole ring create', { until: /live|apart|No ring|close by|overlaps/ });
    }
    if (o.case === 'cancel') obs.said = await kit.ask('/wormhole ring cancel', { until: /Forgotten|no half-built/ });
  },

  checks(ctx, o) {
    const expect = CASES[o.case].expect;
    return [{ name: `Probe was told: "${expect.source.replace(/\\/g, '')}"`, afterReset: null, test: async () => expect.test(ctx.observed.said || '') }];
  },

  async cleanup(ctx) {
    await trip.cleanup(ctx, new RingKit(ctx.server, ctx.probe));
  },

  reset: 'wx:reset/r4',
  A, B,
};
