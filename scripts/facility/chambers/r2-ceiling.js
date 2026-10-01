'use strict';
// R2, the Ceiling Room (design 3.2): a floor ring paired with a ceiling ring (top slabs) hung
// `drop` blocks above the floor. The plugin's rules (RingSurvey): the drop must be 3 to
// `ring-max-ceiling-drop` (10); the rings fall to the floor and stack up from there. Probe steps
// into the floor ring and must come out under the ceiling ring, standing on the floor, with the
// travelling stack drawn on the floor there.

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');
const { RingKit, footprint } = require('../lib/rings');
const trip = require('../lib/ringtrip');

const def = campus.chamber('r2');
const FLOOR_RING = { x: 51, y: 0, z: 18 };
const CEILING_XZ = { x: 51, z: 9 };
// Into the floor ring from the east, inside the cell: from the north the walk would pass under
// the ceiling ring, and stepping under it arms it too.
const FROM = { dx: 6, dz: 0 };

const v = (value, why) => ({ value, label: value, why });

const REFUSALS = {
  2: /That ring has no room between it and the floor\./,
  11: /That ring is more than 10 blocks above its floor\./,
};

module.exports = {
  id: 'r2',
  wing: 'rings',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    drop: [v('6', 'the ceiling ring six above the floor'), v('10', 'the most (ring-max-ceiling-drop)'),
      v('11', 'one too high: refused'), v('2', 'too low for the rings to fall through: refused')],
    pattern: [v('ODD', ''), v('EVEN', '')],
  },
  refuses: () => null,

  async stage(ctx, o) {
    const ceiling = { ...CEILING_XZ, y: Number(o.drop) };
    ctx.observed.drawnOnFloor = [];
    await trip.stagePair(ctx, { pattern: o.pattern, built: 'console' }, FLOOR_RING, ceiling, { halfB: 'top', from: FROM });
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    // What the client is drawn under the ceiling ring during the cycle: the stack, on the floor.
    const f = footprint(o.pattern, CEILING_XZ);
    const onUpdate = (_old, b) => {
      if (b && /slab/.test(b.name) && b.position.y >= 0 && b.position.y <= 3
        && b.position.x >= f.x0 && b.position.x <= f.x1 && b.position.z >= f.z0 && b.position.z <= f.z1 && !obs.drawnOnFloor.includes(b.position.y)) obs.drawnOnFloor.push(b.position.y);
    };
    ctx.probe.bot.on('blockUpdate', onUpdate);
    try {
      await trip.send(ctx, 'walk', FLOOR_RING, { ...CEILING_XZ, y: Number(o.drop) });
    } finally {
      ctx.probe.bot.off('blockUpdate', onUpdate);
    }
  },

  checks(ctx, o) {
    const refusal = REFUSALS[o.drop];
    if (refusal) return trip.checks(ctx, o, { refusal, label: refusal.source.replace(/\\/g, '') });
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    return [
      ...trip.checks(ctx, { ...o, timing: 'default' }).filter((x) => !/recharging|ring list/.test(x.name)),
      c('the arrival is on the floor, not at the ceiling', () => obs.arrivals && obs.arrivals[1].y === 0),
      c('the rings stood on the floor under the ceiling ring (drawn to Probe)', () => obs.drawnOnFloor.includes(0)),
    ];
  },

  async cleanup(ctx) {
    await trip.cleanup(ctx, new RingKit(ctx.server, ctx.probe));
  },

  reset: 'wx:reset/r2',
};
