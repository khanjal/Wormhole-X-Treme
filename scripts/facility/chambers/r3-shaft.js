'use strict';
// R3, the Shaft (design 3.2): a ring on the glass cap at the top of the 60-deep shaft and one at
// the bottom (on a platform at 20 or 40 down, or the shaft's floor at 60). Rings are for going
// straight down; `ring-max-link-height` (384) limits how far, so at 30 the deeper pairs are
// refused in the plugin's words.

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');
const { RingKit } = require('../lib/rings');
const trip = require('../lib/ringtrip');

const def = campus.chamber('r3');
const TOP = { x: 69, y: 0, z: 11 };
// Through the door on the north side, across the cap.
const FROM = { dx: 0, dz: -5.5 };

const v = (value, why) => ({ value, label: value, why });

module.exports = {
  id: 'r3',
  wing: 'rings',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    depth: [v('20', 'a platform twenty down'), v('40', 'forty down'), v('60', 'the shaft floor')],
    'max height': [v('384', 'ring-max-link-height, the default'), v('30', 'ring-max-link-height 30: the deeper pairs are refused')],
  },
  needs: (o) => ({ config: o['max height'] === '30' ? { 'ring-max-link-height': '30' } : {} }),
  refuses: () => null,

  async stage(ctx, o) {
    const depth = Number(o.depth);
    const b = def.box;
    // A floor for the bottom ring: the shaft's own at 60, a platform above that otherwise.
    if (depth < 60) await ctx.server.run(`fill ${b.x0} ${-depth - 1} ${b.z0} ${b.x1} ${-depth - 1} ${b.z1} minecraft:white_concrete`);
    await trip.stagePair(ctx, { pattern: 'ODD', built: 'console' }, TOP, { x: TOP.x, y: -depth, z: TOP.z }, { from: FROM });
  },

  async run(ctx) {
    const [a, b] = ctx.observed.ends;
    await trip.send(ctx, 'walk', a, b);
  },

  checks(ctx, o) {
    const depth = Number(o.depth);
    if (o['max height'] === '30' && depth > 30) {
      const text = `Those two rings are ${depth} blocks apart in height, and rings reach 30.`;
      return trip.checks(ctx, o, { refusal: new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), label: text });
    }
    return trip.checks(ctx, { ...o, timing: 'default' }).filter((x) => !/recharging|ring list/.test(x.name));
  },

  async cleanup(ctx) {
    await trip.cleanup(ctx, new RingKit(ctx.server, ctx.probe));
    await ctx.probe.teleport(campus.TRANSIT.home).catch(() => {});
  },

  reset: 'wx:reset/r3',
};
