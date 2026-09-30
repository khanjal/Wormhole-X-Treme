'use strict';
// The range tunnel (design 3.2, R1's distance rows): a pair laid inside the tunnel east of the
// ring lab, far apart, for the link limit: `ring-max-link-distance` is 256 blocks on the ground,
// so 250 pairs and 257 is refused with the plugin's own words.

const campus = require('../lib/campus');
const { RingKit } = require('../lib/rings');
const trip = require('../lib/ringtrip');

const def = campus.chamber('tunnel');
const A = { x: 116, y: 0, z: 0 };
// Along the tunnel, from the west: a ring fills the tunnel's width.
const FROM = { dx: -5, dz: 0 };

const v = (value, why) => ({ value, label: value, why });

module.exports = {
  id: 'tunnel',
  wing: 'rings',
  title: def.title,
  cell: def.box,
  seat: { x: A.x - 3.5, y: 0, z: 0.5, yaw: -90, pitch: 0 },
  options: {
    distance: [v('64', ''), v('128', ''), v('250', 'just inside the 256-block limit'), v('257', 'one past it: refused')],
  },
  refuses: () => null,

  async stage(ctx, o) {
    const B = { ...A, x: A.x + Number(o.distance) };
    await trip.stagePair(ctx, { pattern: 'ODD', built: 'console' }, A, B, { from: FROM });
  },

  async run(ctx) {
    const [a, b] = ctx.observed.ends;
    await trip.send(ctx, 'walk', a, b);
  },

  checks(ctx, o) {
    if (o.distance === '257') {
      return trip.checks(ctx, o, {
        refusal: /Those two rings are 257 blocks apart on the ground, and rings reach 256\./,
        label: 'Those two rings are 257 blocks apart on the ground, and rings reach 256.',
      });
    }
    return trip.checks(ctx, o);
  },

  async cleanup(ctx) {
    await trip.cleanup(ctx, new RingKit(ctx.server, ctx.probe));
  },

  reset: 'wx:reset/tunnel',
  A,
};
