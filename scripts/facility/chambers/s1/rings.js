'use strict';
// Ring settings: what a new pair takes from the defaults, the refusals each limit gives (and
// the same pair made once the limit is raised), the outline a refused entry is shown, and the
// transport's timings, read off the sounds each end plays.

const { RingKit } = require('../../lib/rings');
const trip = require('../../lib/ringtrip');
const sounds = require('../../lib/sounds');
const campus = require('../../lib/campus');
const { ticks } = require('../../lib/probe');
const { v, c, until, ear, toldSince } = require('./common');
const { RING_SOUNDS } = require('./sounds');

// The Concourse floor, clear of the transit pair (52, -4) and R5's (72 and 88, 0).
const A = { x: 64, y: 0, z: -4 };
const B = { x: 80, y: 0, z: -4 };
const C = { x: 72, y: 0, z: -4 };
const D = { x: 96, y: 0, z: -4 };
// R2's floor ring and the column above it; the tunnel's west end.
const R2_FLOOR = { x: 51, y: 0, z: 18 };
const R2_CEILING = { x: 51, y: 11, z: 9 };
const TUNNEL_A = { x: 116, y: 0, z: 0 };
const TUNNEL_B = { x: 373, y: 0, z: 0 };

const SLAB = 'smooth_stone_slab';

const cases = [
  v('ring defaults', 'ring-default-access PUBLIC, -style SEQUENTIAL, -light SEA_LANTERN, -flash GOLD_BLOCK: a pair Probe builds takes all four'),
  v('ring separation', '`ring-min-separation 12`: a second pair eight from the first is refused, where the default 8 lets it be'),
  v('ring max pairs', '`ring-max-pairs-per-player 1`: a second pair cannot be handed to Probe2 once it owns one'),
  v('ring ceiling drop', '`ring-max-ceiling-drop 12`: a ceiling ring 11 up is paired, where the default 10 refuses it'),
  v('ring link distance', '`ring-max-link-distance 300`: rings 257 apart are paired, where the default 256 refuses them'),
  v('ring outline', 'stepping back into a recharging pair shows a barrier outline under it, for ring-outline-ticks (100 here)'),
  v('ring outline off', '`ring-outline-on-refusal false`: the same, recharging, and no outline'),
  v('ring timings', 'deploy 4, settle 30, flash 8, hold 40, linger 60 ticks: each interval between the sounds and lights is as long'),
];

function needs(o) {
  return {
    'ring defaults': { 'ring-default-access': 'PUBLIC', 'ring-default-style': 'SEQUENTIAL', 'ring-default-light': 'SEA_LANTERN', 'ring-default-flash': 'GOLD_BLOCK' },
    'ring outline': { 'ring-outline-ticks': '100' },
    'ring outline off': { 'ring-outline-on-refusal': 'false' },
    'ring timings': { 'ring-deploy-ticks': '4', 'ring-settle-ticks': '30', 'ring-flash-ticks': '8', 'ring-hold-ticks': '40', 'ring-lights-linger-ticks': '60' },
  }[o.case] || {};
}

async function clearAll(ctx) {
  const kit = new RingKit(ctx.server, ctx.facility.probe);
  const keep = ctx.facility.keepRings || new Set();
  const { ids } = await kit.list().catch(() => ({ ids: [] }));
  for (const id of ids.filter((x) => !keep.has(x))) await kit.remove(id).catch(() => {});
  await kit.ask('/wormhole ring cancel', { ms: 600 }).catch(() => {});
}

async function lay(ctx, ends, half = []) {
  const kit = new RingKit(ctx.server, ctx.facility.probe);
  for (const [i, e] of ends.entries()) await kit.lay('ODD', e, SLAB, { half: half[i] || 'bottom' });
}

async function stage(ctx, o) {
  await clearAll(ctx);
  if (o.case === 'ring defaults') await trip.stagePair(ctx, { pattern: 'ODD', built: 'player' }, A, B);
  if (['ring outline', 'ring outline off', 'ring timings'].includes(o.case)) await trip.stagePair(ctx, { pattern: 'ODD', built: 'console' }, A, B);
}

/** Probe walks into A and is carried to B; returns whether it came out there. */
async function ride(ctx) {
  const obs = ctx.observed;
  await trip.stepIn(ctx.probe, A);
  return (await trip.arrival(ctx.probe, obs.arrivals[1], 30000)).ok;
}

async function run(ctx, o) {
  const obs = ctx.observed;
  const probe = ctx.probe;
  const kit = new RingKit(ctx.server, probe);
  ear(probe);
  if (o.case === 'ring defaults') {
    if (!obs.id) return;
    obs.listed = (await kit.list()).text;
    obs.reset = await kit.ask(`/wormhole ring edit ${obs.id} reset`, { until: /Reset to/, settle: 300 });
  } else if (o.case === 'ring separation') {
    await lay(ctx, [A, B]);
    obs.first = await kit.build(A, B);
    await lay(ctx, [C, D]);
    obs.atEight = await kit.build(C, D);
    if (obs.atEight.id) await kit.remove(obs.atEight.id);
    await lay(ctx, [C, D]);
    await ctx.config.set('ring-min-separation', '12', 's1');
    obs.atTwelve = await kit.build(C, D);
  } else if (o.case === 'ring max pairs') {
    await ctx.facility.second();
    await lay(ctx, [A, B, C, D]);
    obs.first = await kit.build(A, B);
    obs.second = await kit.build(C, D);
    await ctx.config.set('ring-max-pairs-per-player', '1', 's1');
    obs.handFirst = await kit.ask(`/wormhole ring owner Probe2 ${obs.first.id}`, { until: /Handed|already has|limit|not your/ });
    obs.handSecond = await kit.ask(`/wormhole ring owner Probe2 ${obs.second.id}`, { until: /Handed|already has|limit|not your/ });
  } else if (o.case === 'ring ceiling drop') {
    state.borrowed.push('reset/r2');
    const once = async () => {
      await lay(ctx, [R2_FLOOR, R2_CEILING], ['bottom', 'top']);
      const r = await kit.build(R2_FLOOR, R2_CEILING);
      if (r.id) await kit.remove(r.id);
      return r;
    };
    obs.byDefault = await once();
    await ctx.config.set('ring-max-ceiling-drop', '12', 's1');
    obs.raised = await once();
  } else if (o.case === 'ring link distance') {
    state.borrowed.push('reset/tunnel');
    const once = async () => {
      await lay(ctx, [TUNNEL_A, TUNNEL_B]);
      const r = await kit.build(TUNNEL_A, TUNNEL_B);
      if (r.id) await kit.remove(r.id);
      return r;
    };
    obs.byDefault = await once();
    await ctx.config.set('ring-max-link-distance', '300', 's1');
    obs.raised = await once();
  } else if (o.case === 'ring outline' || o.case === 'ring outline off') {
    if (!obs.id) return;
    obs.carried = await ride(ctx);
    // After the cycle (hold, retract, linger), the pair recharges for ring-cooldown-ticks (600).
    await ticks(140);
    const foot = { x0: B.x - 2, x1: B.x + 2, z0: B.z - 2, z1: B.z + 2 };
    const barriers = { at: null, gone: null };
    const onUpdate = (_old, b) => {
      if (!b || b.position.y !== B.y - 1 || b.position.x < foot.x0 || b.position.x > foot.x1 || b.position.z < foot.z0 || b.position.z > foot.z1) return;
      if (b.name === 'barrier' && barriers.at === null) barriers.at = Date.now();
      else if (b.name !== 'barrier' && barriers.at !== null && barriers.gone === null) barriers.gone = Date.now();
    };
    probe.bot.on('blockUpdate', onUpdate);
    const t0 = Date.now();
    try {
      await probe.walkTo({ x: B.x + 0.5, z: B.z + 5.5 }, { within: 0.5, ms: 6000 }).catch(() => {});
      await probe.walkTo({ x: B.x + 0.5, z: B.z + 0.5 }, { within: 0.3, ms: 6000 }).catch(() => {});
      await until(async () => /Rings recharging/.test(toldSince(probe, t0)), 3000);
      await until(async () => barriers.gone !== null, o.case === 'ring outline' ? 9000 : 3000);
    } finally {
      probe.bot.off('blockUpdate', onUpdate);
    }
    obs.told = toldSince(probe, t0);
    obs.barriers = barriers;
  } else if (o.case === 'ring timings') {
    if (!obs.id) return;
    const rec = sounds.record(probe.bot);
    const lights = { last: null };
    const onUpdate = (_old, b) => {
      if (b && b.position.y === B.y - 2 && Math.abs(b.position.x - B.x) <= 2 && Math.abs(b.position.z - B.z) <= 2) lights.last = Date.now();
    };
    probe.bot.on('blockUpdate', onUpdate);
    try {
      obs.carried = await ride(ctx);
      await until(async () => sounds.named(rec.heard, RING_SOUNDS.close).length >= 1, 15000);
      await ticks(100);
    } finally {
      probe.bot.off('blockUpdate', onUpdate);
    }
    obs.heard = rec.stop();
    obs.lightsLastAt = lights.last;
    obs.recStart = rec.t0;
  }
}

function checks(obs, o) {
  const pairText = (r) => (r ? (r.id ? `pair ${r.id}` : r.error) : 'nothing');
  if (o.case === 'ring defaults') {
    return [
      c('Probe built the pair with ring create at each end', () => Boolean(obs.id)),
      c('and was told "It is PUBLIC."', () => /It is PUBLIC\./.test(obs.createB || '')),
      c('ring list: PUBLIC, SEQUENTIAL/SEQUENTIAL', () => new RegExp(`${obs.id} — world, PUBLIC, SEQUENTIAL/SEQUENTIAL`).test(obs.listed || '')),
      c('ring edit reset takes the defaults: "SEA_LANTERN pad, GOLD_BLOCK flash, SEQUENTIAL deploy"', () => {
        if (/SEA_LANTERN pad, GOLD_BLOCK flash, SEQUENTIAL deploy for both ends\./.test(obs.reset || '')) return true;
        throw new Error(`told: ${obs.reset}`);
      }),
    ];
  }
  if (o.case === 'ring separation') {
    return [
      c('the first pair was made', () => Boolean(obs.first && obs.first.id)),
      c('at the default 8, a second pair eight from it was made too', () => Boolean(obs.atEight && obs.atEight.id)),
      c('at 12: "There is another ring close by. Move this one further away and try again."', () => {
        if (obs.atTwelve && !obs.atTwelve.id && /There is another ring close by\. Move this one further away and try again\./.test(obs.atTwelve.error)) return true;
        throw new Error(pairText(obs.atTwelve));
      }),
    ];
  }
  if (o.case === 'ring max pairs') {
    return [
      c('two pairs were made', () => Boolean(obs.first && obs.first.id && obs.second && obs.second.id)),
      c('the first was handed to Probe2', () => /Handed/.test(obs.handFirst || '')),
      c('the second refused: "Probe2 already has 1 ring pairs, which is the limit."', () => {
        if (/Probe2 already has 1 ring pairs, which is the limit\./.test(obs.handSecond || '')) return true;
        throw new Error(`told: ${obs.handSecond}`);
      }),
    ];
  }
  if (o.case === 'ring ceiling drop') {
    return [
      c('at the default 10: "That ring is more than 10 blocks above its floor."', () => obs.byDefault && !obs.byDefault.id && /That ring is more than 10 blocks above its floor\./.test(obs.byDefault.error)),
      c('at 12 the same pair is made', () => {
        if (obs.raised && obs.raised.id) return true;
        throw new Error(pairText(obs.raised));
      }),
    ];
  }
  if (o.case === 'ring link distance') {
    return [
      c('at the default 256: "Those two rings are 257 blocks apart on the ground, and rings reach 256."', () => obs.byDefault && !obs.byDefault.id && /Those two rings are 257 blocks apart on the ground, and rings reach 256\./.test(obs.byDefault.error)),
      c('at 300 the same pair is made', () => {
        if (obs.raised && obs.raised.id) return true;
        throw new Error(pairText(obs.raised));
      }),
    ];
  }
  if (o.case === 'ring outline' || o.case === 'ring outline off') {
    const list = [
      c('the pair was made and Probe carried', () => Boolean(obs.id) && obs.carried === true),
      c('stepping back in: "Rings recharging"', () => /Rings recharging/.test(obs.told || '')),
    ];
    if (o.case === 'ring outline') {
      list.push(c('a barrier outline was drawn under the pad', () => Boolean(obs.barriers && obs.barriers.at)),
        c('and taken back about five seconds later (ring-outline-ticks 100)', () => {
          const ms = obs.barriers && obs.barriers.gone ? obs.barriers.gone - obs.barriers.at : null;
          if (ms !== null && ms >= 4000 && ms <= 6500) return true;
          throw new Error(`after ${ms} ms`);
        }));
    } else {
      list.push(c('and no outline was drawn', () => obs.barriers && obs.barriers.at === null));
    }
    return list;
  }
  if (o.case === 'ring timings') {
    const heard = obs.heard || [];
    const at = (e) => (s) => Math.hypot(s.x - (e.x + 0.5), s.z - (e.z + 0.5)) <= 1.5;
    const ringsA = sounds.named(heard, RING_SOUNDS.ring).filter(at(A));
    const ringsB = sounds.named(heard, RING_SOUNDS.ring).filter(at(B));
    const flashA = sounds.named(heard, RING_SOUNDS.flash).filter(at(A))[0];
    const flashB = sounds.named(heard, RING_SOUNDS.flash).filter(at(B))[0];
    const closeB = sounds.named(heard, RING_SOUNDS.close).filter(at(B))[0];
    const about = (ms, ticksWanted) => ms >= ticksWanted * 50 * 0.8 - 100 && ms <= ticksWanted * 50 * 1.2 + 150;
    const span = (name, ms, t) => c(`${name}: ${t} ticks`, () => {
      if (ms !== null && about(ms, t)) return true;
      throw new Error(`${ms} ms`);
    });
    return [
      c('the pair was made and Probe carried', () => Boolean(obs.id) && obs.carried === true),
      c('four rings up at A and four down at B, the flashes and the close heard', () => ringsA.length === 4 && ringsB.length === 4 && flashA && flashB && closeB),
      span('a ring every 3 deploy frames, 4 ticks each', ringsA.length === 4 ? (ringsA[3].t - ringsA[0].t) / 3 : null, 12),
      span('the last ring up to the flash out: two frames and the settle', flashA && ringsA[3] ? flashA.t - ringsA[3].t : null, 2 * 4 + 30),
      span('the flash out to the flash in: four rings lit, 8 ticks each', flashA && flashB ? flashB.t - flashA.t : null, 4 * 8),
      span('the flash in to the first ring down: the sweep, the hold and a frame', flashB && ringsB[0] ? ringsB[0].t - flashB.t : null, 4 * 8 + 40 + 4),
      span('the close to the pad lights gone: the linger', closeB && obs.lightsLastAt ? obs.lightsLastAt - (obs.recStart + closeB.t) : null, 60),
    ];
  }
  return [];
}

// Whether a case used R2's or the tunnel's floor, which their own resets put back.
const state = { borrowed: [] };

async function cleanup(ctx) {
  await clearAll(ctx);
  const kit = new RingKit(ctx.server, ctx.facility.probe);
  for (const e of [A, B, C, D]) await kit.clear('ODD', e);
  await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
  for (const fn of state.borrowed.splice(0)) {
    const f = ctx.facility.manifest.functions.find((x) => x.fn === fn);
    if (f) await ctx.facility.runFunction(f);
  }
}

module.exports = { cases, needs, stage, run, checks, cleanup };
