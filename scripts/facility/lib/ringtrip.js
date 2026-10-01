'use strict';
// A ring trip, shared by the ring chambers (R1 pair stand, R2 ceiling room, R3 shaft, the range
// tunnel): stage a pair, send a traveller, record what happened, judge it, take it down.
// Every check reads what was recorded: where Probe (or Probe2) is, what the plugin told them
// (chat and action bar, as plain text), where a tagged entity is on the server.

const { RingKit, SWAP_TICKS } = require('./rings');
const { ticks } = require('./probe');
const campus = require('./campus');
const { itemNbt } = require('./menagerie');

const O = campus.OVERWORLD;

/** Every chat line and action bar a probe is sent, with when, into `sink` (one listener each). */
function listen(probe, sink) {
  probe.ringSink = sink;
  if (probe.ringListening) return;
  probe.bot.on('message', (m, position) => {
    const text = m.toString();
    if (probe.ringSink && !text.startsWith('[Server:')) probe.ringSink.push({ at: Date.now(), text, bar: position === 'game_info' });
  });
  probe.ringListening = true;
}

function said(sink, re, since = 0) {
  return sink.some((m) => m.at >= since && re.test(m.text));
}

async function until(test, ms, every = 4) {
  const end = Date.now() + ms;
  for (;;) {
    if (await test()) return true;
    if (Date.now() > end) return false;
    await ticks(every);
  }
}

/** Is an entity with this selector body within `r` of a point? */
async function near(ctx, at, body, r = 2, dim = O) {
  const q = await ctx.server.run(`execute in ${dim} positioned ${at.x} ${at.y} ${at.z} if entity @e[${body},distance=..${r}]`);
  return q.lines.some((l) => /Test passed/.test(l));
}

function flat(p, q) {
  return Math.hypot(p.x - q.x, p.z - q.z);
}

/**
 * Lays both circles and pairs them: by the console form, or as Probe (`ring create` in each).
 * opts: { dim, world, halfA, halfB } (a ceiling end has top slabs). Records the id and arrivals,
 * or the plugin's refusal in `pairError`.
 */
async function stagePair(ctx, o, a, b, opts = {}) {
  const obs = ctx.observed;
  const kit = new RingKit(ctx.server, ctx.probe);
  obs.chat = [];
  listen(ctx.probe, obs.chat);
  const pattern = o.pattern || 'ODD';
  const slab = o.slab || 'smooth_stone_slab';
  obs.pattern = pattern;
  obs.ends = [a, b];
  obs.from = opts.from;
  await kit.lay(pattern, a, slab, { half: opts.halfA || 'bottom', odd: opts.oddA, dim: opts.dim });
  await kit.lay(pattern, b, slab, { half: opts.halfB || 'bottom', dim: opts.dim });
  if (o.built === 'player') {
    await ctx.probe.teleport({ x: a.x + 0.5, y: opts.standA || a.y, z: a.z + 0.5 }, opts.dim || O);
    obs.createA = await kit.ask('/wormhole ring create', { until: /First ring noted|error|No ring|close by|overlaps|filled|room|headroom|hole|built inside|more than|Some of those|more than one kind/ });
    await ctx.probe.teleport({ x: b.x + 0.5, y: opts.standB || b.y, z: b.z + 0.5 }, opts.dim || O);
    obs.createB = await kit.ask('/wormhole ring create', { until: /Ring pair \w+ is live|apart|error|No ring|close by|overlaps/ });
    const m = /Ring pair (\w+) is live/.exec(obs.createB);
    if (!m) { obs.pairError = `${obs.createA} / ${obs.createB}`; return; }
    obs.id = m[1];
    obs.arrivals = [a, b].map((p, i) => ({ x: p.x + 0.5, y: (opts.bases || [a.y, b.y])[i], z: p.z + 0.5 }));
  } else {
    const r = await kit.build(a, b, opts.world || 'world');
    if (r.error) { obs.pairError = r.error; return; }
    obs.id = r.id;
    obs.arrivals = r.arrivals;
    obs.built = r.text;
  }
  if (o.timing === 'slow') obs.style = await kit.ask(`/wormhole ring edit ${obs.id} style slow`, { until: /Style set to/ });
  // The plugin knows a player only once they have been on the server: Probe2 joins first.
  if (o.access === 'allowed') await ctx.facility.second();
  if (o.access === 'allowed') obs.allow = await kit.ask(`/wormhole ring allow Probe2 ${obs.id}`, { until: /may now use|could already/ });
}

/**
 * Walks a probe from just outside a ring to its centre, as a player steps in; `from` is where
 * outside ({ dx, dz } from the centre; south by default, along a tunnel for the range).
 */
async function stepIn(probe, end, dim = O, from = { dx: 0, dz: 5.5 }) {
  await probe.teleport({ x: end.x + 0.5 + from.dx, y: end.y, z: end.z + 0.5 + from.dz, yaw: 180 }, dim);
  await probe.walkTo({ x: end.x + 0.5, z: end.z + 0.5 }, { within: 0.3, ms: 8000 }).catch(() => {});
}

/** Notes when the server first puts a probe down near a point, from now on; stop() when done. */
function arrivalWatch(probe, at) {
  const w = { when: null };
  const onMove = () => { if (w.when === null && probe.distanceTo(at) < 2) w.when = Date.now(); };
  probe.bot.on('forcedMove', onMove);
  w.stop = () => probe.bot.off('forcedMove', onMove);
  return w;
}

/**
 * Waits (bounded) for a probe to be put down near a point; returns { ok, at, when }. `watch`, an
 * arrivalWatch started earlier, times an arrival that may come before this is called.
 */
async function arrival(probe, at, ms = 15000, dim = O, watch = null) {
  const w = watch || arrivalWatch(probe, at);
  try {
    const ok = await until(async () => probe.dimension === dim && flat(probe.position, at) < 1.5 && Math.abs(probe.position.y - at.y) < 1.5, ms);
    await probe.settle();
    return { ok, at: { x: probe.position.x, y: probe.position.y, z: probe.position.z }, when: w.when };
  } finally {
    w.stop();
  }
}

/** Sends one traveller from end a to end b, recording what happened. */
async function send(ctx, traveller, a, b, dim = O) {
  const obs = ctx.observed;
  obs.traveller = traveller;
  if (!obs.id) return;
  const kit = new RingKit(ctx.server, ctx.probe);
  const [arrA, arrB] = obs.arrivals;
  const probe = ctx.probe;
  const t0 = Date.now();
  if (traveller === 'walk' || traveller === 'swap') {
    let p2 = null;
    // Timed from before either steps in: the swap may come while the second is still walking.
    const w1 = arrivalWatch(probe, arrB);
    let w2 = null;
    try {
      if (traveller === 'swap') {
        p2 = await ctx.facility.second();
        w2 = arrivalWatch(p2, arrA);
        obs.chat2 = [];
        listen(p2, obs.chat2);
        await stepIn(p2, b, dim, obs.from);
      }
      await stepIn(probe, a, dim, obs.from);
    } catch (e) {
      w1.stop();
      if (w2) w2.stop();
      throw e;
    }
    const [r1, r2] = await Promise.all([arrival(probe, arrB, 15000, dim, w1), p2 ? arrival(p2, arrA, 15000, dim, w2) : null]);
    obs.probeArrived = r1;
    obs.probe2Arrived = r2;
    obs.countdown = said(obs.chat, /Transport in \d+ seconds?/, t0);
    if (traveller === 'walk' && r1.ok) {
      // Straight back in: the pair is cooling down now, and says for how long.
      const back = Date.now();
      await ticks(Math.max(0, 230 - SWAP_TICKS));
      const out = obs.from || { dx: 0, dz: 5.5 };
      await probe.walkTo({ x: b.x + 0.5 + out.dx, z: b.z + 0.5 + out.dz }, { within: 0.5, ms: 6000 }).catch(() => {});
      await probe.walkTo({ x: b.x + 0.5, z: b.z + 0.5 }, { within: 0.3, ms: 6000 }).catch(() => {});
      obs.cooldown = await until(async () => said(obs.chat, /Rings recharging\. Ready in \d+ seconds?/, back), 3000);
      obs.listed = await kit.list();
    }
    return;
  }
  if (traveller === 'horse') {
    const at = { x: a.x + 0.5, y: a.y, z: a.z + 6.5, yaw: 180 };
    await ctx.menagerie.animal('horse', at, ctx.tag, { saddled: true });
    await probe.teleport({ x: at.x + 1.6, y: a.y, z: at.z, yaw: 90 }, dim);
    await probe.mount(ctx.tag, 5000, 3);
    await probe.drive({ x: a.x + 0.5, z: a.z + 0.5 }, { speed: 0.2, ms: 8000 }).catch(() => {});
    // A rider moves by vehicle packets; if that did not arm the ring, the console fires it.
    const armed = await until(async () => said(obs.chat, /Transport in|Transport rings engaging/, t0), 1500);
    obs.firedBy = armed ? 'rider' : 'console';
    if (!armed) obs.fire = await kit.fire(obs.id);
    obs.mountArrived = await until(() => near(ctx, arrB, `tag=${ctx.tag},tag=wx_kind_horse`, 2.5, dim), 15000);
    await ticks(10);
    obs.stillRiding = Boolean(probe.bot.vehicle);
    return;
  }
  // Things that cannot arm a ring themselves: in the middle of end a, fired from the console.
  const kind = { zombie: 'zombie', item: 'item', minecart: 'minecart' }[traveller];
  const centre = { x: a.x + 0.5, y: a.y, z: a.z + 0.5 };
  const nbt = kind === 'zombie' ? 'NoAI:1b,PersistenceRequired:1b,Silent:1b'
    : kind === 'item' ? `Item:${itemNbt(ctx.version, { id: 'compass' })},PickupDelay:32767s` : '';
  await ctx.menagerie.summon(kind, centre, ctx.tag, nbt);
  obs.fire = await kit.fire(obs.id);
  obs.entityArrived = await until(() => near(ctx, arrB, `tag=${ctx.tag},tag=wx_kind_${kind}`, 2, dim), 15000);
  obs.entityLeft = await near(ctx, centre, `tag=${ctx.tag},tag=wx_kind_${kind}`, 2, dim);
}

/** Probe2 walks into end a of a private pair of Probe's: refused, or carried if allowed. */
async function walkAs(ctx, p2, a, b, dim = O) {
  const obs = ctx.observed;
  obs.traveller = 'probe2';
  if (!obs.id) return;
  obs.chat2 = [];
  listen(p2, obs.chat2);
  const t0 = Date.now();
  await stepIn(p2, a, dim, obs.from);
  obs.told = await until(async () => said(obs.chat2, /These transport rings are private\./, t0), 3000);
  obs.probe2Arrived = await arrival(p2, obs.arrivals[1], 12000, dim);
}

/** The checks for whatever was sent; `expect` is { refusal: RegExp } for a pair that must not be made. */
function checks(ctx, o, expect = {}) {
  const obs = ctx.observed;
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  if (expect.refusal) {
    return [
      c(`the plugin refused the pair: "${expect.label || expect.refusal.source}"`, () => expect.refusal.test(obs.pairError || '')),
      c('no pair was made', () => !obs.id),
    ];
  }
  const list = [c('the pair was made', () => Boolean(obs.id))];
  if (o.timing === 'slow') list.push(c('its style is slow (SEQUENTIAL)', () => /Style set to SEQUENTIAL/.test(obs.style || '')));
  const t = obs.traveller;
  const near1 = (r, at) => r && r.ok && Math.abs(r.at.y - at.y) < 0.6 && flat(r.at, at) < 1;
  if (t === 'walk' || t === 'swap') {
    list.push(c('the countdown was shown', () => obs.countdown));
    list.push(c('Probe came out at the other end', () => obs.arrivals && near1(obs.probeArrived, obs.arrivals[1])));
    if (t === 'swap') {
      list.push(c('Probe2 came out at the first end', () => obs.arrivals && near1(obs.probe2Arrived, obs.arrivals[0])));
      list.push(c('both crossed in the same instant', () => obs.probeArrived.when && obs.probe2Arrived.when
        && Math.abs(obs.probeArrived.when - obs.probe2Arrived.when) <= 150));
    } else {
      list.push(c('stepping straight back in, it is recharging', () => obs.cooldown));
      list.push(c('Probe\'s ring list shows the pair', () => obs.listed && obs.listed.text.includes(obs.id)));
    }
    return list;
  }
  if (t === 'horse') {
    list.push(c('the same horse came out at the other end', () => obs.mountArrived));
    list.push(c('Probe is still riding it', () => obs.stillRiding));
    return list;
  }
  if (t === 'probe2') {
    if (o.access === 'stranger') {
      list.push(c('Probe2 was told the rings are private', () => obs.told));
      list.push(c('and stayed where it was', () => !(obs.probe2Arrived && obs.probe2Arrived.ok)));
    } else {
      list.push(c('Probe2, allowed, came out at the other end', () => obs.arrivals && near1(obs.probe2Arrived, obs.arrivals[1])));
    }
    return list;
  }
  list.push(c(`the ${t} came out at the other end`, () => obs.entityArrived));
  list.push(c(`and nothing of it was left at the first`, () => !obs.entityLeft));
  return list;
}

/** Takes every pair a run made down again (as Probe), and anything half-made; never the transit pair. */
async function cleanup(ctx, kit) {
  await ctx.probe.dismount().catch(() => {});
  const { ids } = await kit.list();
  const keep = ctx.facility.keepRings || new Set();
  for (const id of ids.filter((x) => !keep.has(x))) await kit.remove(id);
  await kit.ask('/wormhole ring cancel', { ms: 600 });
  await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
  if (ctx.facility.probe2) await ctx.facility.probe2.teleport(campus.TRANSIT.home).catch(() => {});
}

module.exports = { listen, said, until, stagePair, stepIn, arrival, send, walkAs, checks, cleanup, near };
