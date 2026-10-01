'use strict';
// R5, the Edit Desk (design 3.2): a pair Probe builds on the ring lab's floor beside the desk,
// edited from Probe's side (`ring edit`, `allow`, `deny`, `owner`: player commands) and ridden
// to see the edit hold: the name announced on arrival, the pad light drawn, and who may use a
// private pair (Probe2, never opped).

const campus = require('../lib/campus');
const { deskLayout } = require('../lib/blueprint');
const { RingKit, footprint } = require('../lib/rings');
const trip = require('../lib/ringtrip');

const def = campus.chamber('r5');
// On the hall floor between the cells, west of the desk and clear of its stand.
const A = { x: 72, y: 0, z: 0 };
const B = { x: 88, y: 0, z: 0 };
const FROM = { dx: -5.5, dz: 0 };

const v = (value, why) => ({ value, label: value, why });

module.exports = {
  id: 'r5',
  wing: 'rings',
  title: def.title,
  cell: null,
  seat: deskLayout(def).seat,
  options: {
    edit: [
      v('name', '`ring edit name Alpha` standing in one end: arriving there says so'),
      v('light', '`ring edit <id> light SEA_LANTERN`: the pad is lit with it'),
      v('private', '`ring edit <id> access private`: Probe2 is turned away'),
      v('allow', 'private, then `ring allow Probe2`: Probe2 is carried'),
      v('deny', 'allowed, then `ring deny Probe2`: turned away again'),
      v('owner', '`ring owner Probe2 <id>`: handed over'),
    ],
  },
  // A name is only announced on arrival, and the trip back only follows at once with no cooldown.
  needs: (o) => ({ config: o.edit === 'name' ? { 'ring-cooldown-ticks': '0' } : {} }),
  refuses: () => null,

  async stage(ctx, o) {
    const kit = new RingKit(ctx.server, ctx.probe);
    const obs = ctx.observed;
    await trip.stagePair(ctx, { pattern: 'ODD', built: 'console' }, A, B, { from: FROM });
    if (!obs.id) return;
    if (o.edit === 'light') obs.edit = await kit.ask(`/wormhole ring edit ${obs.id} light SEA_LANTERN`, { until: /Pad light set to|not a block/ });
    if (['private', 'allow', 'deny'].includes(o.edit)) obs.edit = await kit.ask(`/wormhole ring edit ${obs.id} access private`, { until: /Access set to/ });
    // The plugin knows a player only once they have been on the server: Probe2 joins first
    // (in a shard, no earlier cell may have brought it).
    if (o.edit === 'allow' || o.edit === 'deny') await ctx.facility.second();
    if (o.edit === 'allow' || o.edit === 'deny') obs.allow = await kit.ask(`/wormhole ring allow Probe2 ${obs.id}`, { until: /may now use|could already/ });
    if (o.edit === 'deny') obs.deny = await kit.ask(`/wormhole ring deny Probe2 ${obs.id}`, { until: /may no longer use|was not on the list/ });
    if (o.edit === 'owner') {
      // Probe2's own list before the hand-over, so "it now finds it" is a change, not a pair a
      // non-op is shown anyway.
      obs.listedBefore = await new RingKit(ctx.server, await ctx.facility.second()).list();
      obs.edit = await kit.ask(`/wormhole ring owner Probe2 ${obs.id}`, { until: /Handed|already owns|not your ring pair|limit/ });
    }
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    if (!obs.id) return;
    if (o.edit === 'name') {
      // A name belongs to one end and is given standing in it, which arms the pair: Probe names
      // A on the way out, lands in B unnamed, steps out and back in (no cooldown: a standing
      // player sends no moves, and only a move arms a ring) and is told the name at A.
      const kit = new RingKit(ctx.server, ctx.probe);
      await trip.stepIn(ctx.probe, A, campus.OVERWORLD, FROM);
      obs.edit = await kit.ask('/wormhole ring edit name Alpha', { until: /This ring is now|Stand in/ });
      obs.outward = await trip.arrival(ctx.probe, obs.arrivals[1]);
      const back = Date.now();
      await trip.stepIn(ctx.probe, B, campus.OVERWORLD, FROM);
      obs.probeArrived = await trip.arrival(ctx.probe, obs.arrivals[0]);
      obs.plainAtB = trip.said(obs.chat, /Transport complete\./, 0);
      obs.announced = await trip.until(async () => trip.said(obs.chat, /Arrived at Alpha\./, back), 2000);
      return;
    }
    if (o.edit === 'light') {
      const f = footprint('ODD', A);
      obs.lit = [];
      const onUpdate = (_old, b) => {
        if (b && b.name === 'sea_lantern' && b.position.x >= f.x0 && b.position.x <= f.x1 && b.position.z >= f.z0 && b.position.z <= f.z1 && !obs.lit.includes(b.position.y)) obs.lit.push(b.position.y);
      };
      ctx.probe.bot.on('blockUpdate', onUpdate);
      try {
        await trip.send(ctx, 'walk', A, B);
      } finally {
        ctx.probe.bot.off('blockUpdate', onUpdate);
      }
      return;
    }
    if (o.edit === 'owner') {
      obs.listed = await new RingKit(ctx.server, await ctx.facility.second()).list();
      return;
    }
    await trip.walkAs(ctx, await ctx.facility.second(), A, B);
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    const list = [c('the pair was made', () => Boolean(obs.id))];
    const arrived = (r, at) => r && r.ok && Math.hypot(r.at.x - at.x, r.at.z - at.z) < 1;
    if (o.edit === 'name') {
      list.push(c('the end was named: "This ring is now Alpha."', () => /This ring is now Alpha\./.test(obs.edit || '')));
      list.push(c('Probe went out to the unnamed end: "Transport complete."', () => obs.arrivals && arrived(obs.outward, obs.arrivals[1]) && obs.plainAtB));
      list.push(c('came back to Alpha', () => obs.arrivals && arrived(obs.probeArrived, obs.arrivals[0])));
      list.push(c('and was told "Arrived at Alpha."', () => obs.announced));
    } else if (o.edit === 'light') {
      list.push(c('"Pad light set to SEA_LANTERN."', () => /Pad light set to SEA_LANTERN\./.test(obs.edit || '')));
      list.push(c('the pad was lit with sea lanterns (drawn to Probe)', () => obs.lit && obs.lit.length > 0));
      list.push(c('Probe came out at the other end', () => obs.arrivals && arrived(obs.probeArrived, obs.arrivals[1])));
    } else if (o.edit === 'owner') {
      list.push(c('"Handed <id> to Probe2."', () => new RegExp(`Handed ${obs.id} to Probe2\\.`).test(obs.edit || '')));
      list.push(c('Probe2 (not an op) did not find it in its own ring list before', () => obs.listedBefore && /You have no transport rings|—/.test(obs.listedBefore.text)
        && !obs.listedBefore.text.includes(obs.id)));
      list.push(c('Probe2 (not an op) now finds it in its own ring list', () => obs.listed && obs.listed.text.includes(obs.id)));
    } else {
      list.push(c('"Access set to PRIVATE."', () => /Access set to PRIVATE\./.test(obs.edit || '')));
      if (o.edit !== 'private') list.push(c('"Probe2 may now use <id>."', () => /Probe2 may now use/.test(obs.allow || '')));
      if (o.edit === 'deny') list.push(c('"Probe2 may no longer use <id>."', () => /Probe2 may no longer use/.test(obs.deny || '')));
      if (o.edit === 'allow') {
        list.push(c('Probe2 came out at the other end', () => obs.arrivals && arrived(obs.probe2Arrived, obs.arrivals[1])));
      } else {
        list.push(c('Probe2 was told the rings are private', () => obs.told));
        list.push(c('and stayed where it was', () => !(obs.probe2Arrived && obs.probe2Arrived.ok)));
      }
    }
    return list;
  },

  async cleanup(ctx) {
    await ctx.probe.teleport(campus.TRANSIT.home).catch(() => {});
    await trip.cleanup(ctx, new RingKit(ctx.server, ctx.probe));
    for (const end of [A, B]) await new RingKit(ctx.server, ctx.probe).clear('ODD', end);
  },
  ENDS: [A, B],
};
