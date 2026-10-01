'use strict';
// R1, the Pair Stand (design 3.2): two circles of slabs on the ring lab's floor, paired by the
// console form (public) or by Probe as a player (private, Probe's), and a traveller sent across:
// Probe on foot, on a horse, a zombie, an item, a cart, or Probe and Probe2 swapping ends. The
// access rows use Probe2, who is never opped (an op is admin, and admin is not carried by a
// private pair it is not on, but may arm it).

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');
const { RingKit } = require('../lib/rings');
const trip = require('../lib/ringtrip');

const def = campus.chamber('r1');
const O = campus.OVERWORLD;
const A = { x: 50, y: 0, z: -18 };

const v = (value, why, label = value) => ({ value, label, why });

const OPTIONS = {
  pattern: [v('ODD', '16 slabs round a centre block'), v('EVEN', '12 slabs round a 2x2 centre')],
  slab: ['smooth_stone_slab', 'oak_slab', 'stone_brick_slab', 'andesite_slab', 'prismarine_slab', 'cut_copper_slab']
    .map((s) => v(s, 'the ring material: any one kind of slab')),
  distance: [v('12', 'across the stand'), v('40', 'the length of the stand')],
  built: [v('console', '`ring build`: public, no owner'), v('player', 'Probe runs `ring create` in each circle: private, Probe\'s')],
  traveller: [v('walk', 'Probe walks in'), v('swap', 'Probe at one end, Probe2 at the other: both must cross in one instant'),
    v('horse', 'Probe rides a horse in'), v('zombie', 'fired from the console with a zombie inside'),
    v('item', 'fired with an item inside'), v('minecart', 'fired with a cart inside')],
  access: [v('as built', 'console pairs are public, player pairs private'), v('stranger', 'Probe2 walks into Probe\'s private pair: refused'),
    v('allowed', '`ring allow Probe2`: Probe2 is carried')],
  timing: [v('default', ''), v('slow', '`edit style slow`: the rings climb one at a time'),
    v('quick', '`ring-countdown-ticks 30`, the shortest countdown')],
};

function refuses(o) {
  if (o.access !== 'as built' && o.built !== 'player') return 'the access rows are about a private pair, which a player builds';
  if (o.access !== 'as built' && o.traveller !== 'walk') return 'the access rows send Probe2 on foot';
  return null;
}

module.exports = {
  id: 'r1',
  wing: 'rings',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: OPTIONS,
  needs: (o) => ({ config: o.timing === 'quick' ? { 'ring-countdown-ticks': '30' } : {} }),
  refuses,

  async stage(ctx, o) {
    const B = { ...A, x: A.x + Number(o.distance) };
    ctx.observed.ends = [A, B];
    await trip.stagePair(ctx, o, A, B, { dim: O });
  },

  async run(ctx, o) {
    const [a, b] = ctx.observed.ends;
    if (o.access === 'stranger' || o.access === 'allowed') return trip.walkAs(ctx, await ctx.facility.second(), a, b);
    return trip.send(ctx, o.traveller, a, b);
  },

  checks(ctx, o) {
    return trip.checks(ctx, o);
  },

  async cleanup(ctx) {
    await trip.cleanup(ctx, new RingKit(ctx.server, ctx.probe));
  },

  reset: 'wx:reset/r1',
};
