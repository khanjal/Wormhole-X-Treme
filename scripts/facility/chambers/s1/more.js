'use strict';
// The rest: the mirror settings, the integrations a server without their plugins can still see,
// and the settings that once took effect only at the next start, which now apply as soon as
// `wormhole config` changes them, as the guide says (no reload, no restart).

const { GateKit } = require('../../lib/gatekit');
const mirrors = require('../../lib/mirrors');
const sounds = require('../../lib/sounds');
const campus = require('../../lib/campus');
const { ticks } = require('../../lib/probe');
const { O, GATE, GEOM, RELAY, v, c, until, ear, toldSince, before, logMark, logSince, buildGate, builtChecks } = require('./common');
const { itemNbt } = require('../../lib/menagerie');
const { GATE_SOUNDS } = require('./sounds');

const OPS = campus.ROUTES.mirrors.find((m) => m.name === 'Ops');
const FAR = { x: OPS.x + 0.5, y: 0, z: OPS.z - 30.5, yaw: 0 };
const PLACEHOLDERS = 'Placeholders enabled in config but PlaceholderAPI was not found. Placeholders disabled.';
const NO_VAULT = 'Vault not found. Economy features disabled.';
const RELAY_GEOM = new GateKit(null).place('Standard', RELAY.facing, RELAY);

const cases = [
  v('mirror proximity', '`mirror-proximity-distance 4`: eight blocks from a mirror nothing is drawn, three blocks from it the room is; the default 16 draws it at eight'),
  v('mirror view depth', '`mirror-view-depth 4`: the room behind a mirror is drawn no deeper than four blocks, where the default goes far further'),
  v('mirror fog', '`mirror-fog-at-depth true` with a shallow view: `mirror debug` says the view distance was pulled in; off by default'),
  v('metrics', '`metrics-enabled false` stops bStats at once, and true starts it again, each said in the log'),
  v('coreprotect', '`coreprotect-enabled true` with no CoreProtect: building a gate logs that nothing is logged to it'),
  v('hum at once', '`gate-sound-ambient-ticks 20`, no restart: the hum repeats every second at once'),
  v('scan at once', '`entity-scan-interval-ticks 200`, no restart: an item lying in an open gate waits for the next sweep, ten seconds off, where by default it is sent within a second or two'),
  v('integrations at once', '`placeholders-enabled true` and `economy-enabled true`, no restart: each says at once that its plugin is not there; a start with both off says neither'),
];

function needs(o) {
  return {
    'mirror fog': { 'mirror-view-depth': '4' },
    coreprotect: { 'coreprotect-enabled': 'true' },
    'hum at once': { 'gate-sound-ambient-ticks': '20' },
  }[o.case] || {};
}

async function stage(ctx, o) {
  if (['hum at once', 'scan at once'].includes(o.case)) Object.assign(ctx.observed, await buildGate(ctx));
}

/**
 * Opens Sys to the Relay; `run({ holdMs, ms })` then lays an item in the bottom of its opening and
 * says how long until it lies at the Relay's arrival (null if not within `ms`), and whether it was
 * still in the opening at `holdMs`.
 */
async function lyingItem(ctx) {
  const kit = new GateKit(ctx.server);
  const probe = ctx.facility.probe;
  await probe.teleport(before(GEOM, 8), O);
  await ticks(20);
  const dial = (await kit.dial(GATE, 'Relay')).text;
  const drawn = await kit.waitDrawn(probe, GEOM, 15000);
  await ticks(60);
  const bottom = GEOM.opening.reduce((a, b) => (b.y < a.y || (b.y === a.y && Math.abs(b.x + 0.5 - GEOM.centre.x) < Math.abs(a.x + 0.5 - GEOM.centre.x)) ? b : a));
  const at = { x: bottom.x + 0.5, y: bottom.y + 0.1, z: bottom.z + 0.5 };
  const sel = `tag=${ctx.tag},tag=wx_kind_item`;
  const near = async (p, r) => (await ctx.server.run(`execute in ${O} positioned ${p.x} ${p.y} ${p.z} if entity @e[${sel},distance=..${r}]`)).lines.some((l) => /Test passed/.test(l));
  return { dial, drawn, run: async ({ holdMs = 0, ms = 15000 } = {}) => {
    // Laid in front first and left to settle, so the plugin's item tracker (which follows an item
    // from its spawn until it lies still) is done with it; then moved into the opening, where only
    // the entity sweep sends it.
    const out = before(GEOM, 2);
    await ctx.menagerie.summon('item', { x: out.x, y: bottom.y, z: out.z }, ctx.tag, `Item:${itemNbt(ctx.version, { id: 'compass' })},PickupDelay:32767s,Motion:[0.0d,0.0d,0.0d]`);
    await ticks(40);
    const t0 = Date.now();
    await ctx.server.run(`execute in ${O} run tp @e[${sel},limit=1] ${at.x} ${at.y} ${at.z}`);
    let held = null;
    if (holdMs) { await new Promise((r) => { setTimeout(r, holdMs); }); held = await near(at, 1.5); }
    const a = RELAY_GEOM.itemArrival || RELAY_GEOM.arrival;
    const arrived = await until(async () => near(a, 3), ms, 4);
    const after = arrived ? Date.now() - t0 : null;
    await ctx.server.run(`kill @e[${sel}]`);
    return { held, after };
  } };
}

/**
 * Watches what a probe is drawn behind a mirror's wall: how many blocks, and how deep the
 * deepest is (blocks back from the wall).
 */
function depthWatch(probe, m) {
  const [fx, fz] = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[m.facing];
  const seen = { count: 0, deepest: 0 };
  const on = (_old, b) => {
    if (!b || Math.abs(b.position.x - m.x) > 170 || Math.abs(b.position.z - m.z) > 170) return;
    const along = (b.position.x - (m.x - fx)) * fx + (b.position.z - (m.z - fz)) * fz;
    if (along < 0) { seen.count++; seen.deepest = Math.max(seen.deepest, -along); }
  };
  probe.bot.on('blockUpdate', on);
  seen.stop = () => probe.bot.off('blockUpdate', on);
  return seen;
}

/** From far off, to `d` in front of the Ops mirror, facing it; returns what was drawn in 3 s. */
async function lookFrom(probe, d) {
  await probe.teleport(FAR, O);
  await ticks(40);
  const seen = depthWatch(probe, OPS);
  try {
    await probe.teleport(mirrors.frontOf(OPS, d), O);
    await probe.face({ x: OPS.x + 0.5, y: OPS.y + 0.6, z: OPS.z + 0.5 });
    await ticks(60);
  } finally {
    seen.stop();
  }
  return { count: seen.count, deepest: seen.deepest };
}

/** Counts the hum's packets from Sys over five seconds of it standing open. */
async function hum(ctx) {
  const probe = ctx.facility.probe;
  const kit = new GateKit(ctx.server);
  await probe.teleport(before(GEOM, 8), O);
  await ticks(20);
  const dial = (await kit.dial(GATE, 'Relay')).text;
  const drawn = await kit.waitDrawn(probe, GEOM, 15000);
  await ticks(20);
  const rec = sounds.record(probe.bot);
  await ticks(100);
  const n = sounds.named(rec.stop(), GATE_SOUNDS.ambient).length;
  await kit.force(GATE);
  await kit.force('Relay');
  return { dial, drawn, n };
}

async function run(ctx, o) {
  const obs = ctx.observed;
  const probe = ctx.facility.probe;
  ear(probe);
  if (o.case === 'mirror proximity') {
    obs.byDefault = await lookFrom(probe, 8);
    // Keep this at or under the default 16: test/watcher.test.js clears watch-mode vantage points of banners assuming it.
    await ctx.config.set('mirror-proximity-distance', '4', 's1');
    obs.atEight = await lookFrom(probe, 8);
    obs.atThree = await lookFrom(probe, 3);
  } else if (o.case === 'mirror view depth') {
    obs.byDefault = await lookFrom(probe, 3);
    await ctx.config.set('mirror-view-depth', '4', 's1');
    obs.shallow = await lookFrom(probe, 3);
  } else if (o.case === 'mirror fog') {
    const debug = async () => {
      await lookFrom(probe, 3);
      const t0 = Date.now();
      probe.bot.chat(`/wormhole mirror debug ${OPS.name} -all`);
      await until(async () => /fog: /.test(toldSince(probe, t0)), 3000);
      await ticks(10);
      const line = toldSince(probe, t0).split(' / ').find((l) => /fog: /.test(l));
      return line || toldSince(probe, t0).slice(0, 300);
    };
    obs.off = await debug();
    await ctx.config.set('mirror-fog-at-depth', 'true', 's1');
    obs.on = await debug();
    await probe.teleport(campus.TRANSIT.home);
  } else if (o.case === 'metrics') {
    let mark = logMark(ctx);
    await ctx.config.set('metrics-enabled', 'false', 's1');
    await ticks(10);
    obs.stopped = logSince(ctx, mark).some((l) => l.includes('Stopped sending usage counts to bStats.'));
    mark = logMark(ctx);
    await ctx.config.set('metrics-enabled', 'true', 's1');
    await ticks(10);
    obs.started = logSince(ctx, mark).some((l) => l.includes('Sending anonymous usage counts to bStats; metrics-enabled: false stops it.'));
  } else if (o.case === 'coreprotect') {
    const mark = logMark(ctx);
    Object.assign(obs, await buildGate(ctx));
    await ticks(10);
    obs.logged = logSince(ctx, mark).some((l) => l.includes('coreprotect-enabled is set, but CoreProtect is not running; nothing is logged to it.'));
  } else if (o.case === 'hum at once') {
    obs.hum = await hum(ctx);
  } else if (o.case === 'scan at once') {
    const g = await lyingItem(ctx);
    Object.assign(obs, { dial: g.dial, drawn: g.drawn });
    obs.byDefault = await g.run();
    // Rescheduled at the change, its next sweep a whole period (ten seconds) off.
    await ctx.config.set('entity-scan-interval-ticks', '200', 's1');
    obs.slow = await g.run({ holdMs: 5000, ms: 12000 });
    await new GateKit(ctx.server).force(GATE);
    await new GateKit(ctx.server).force('Relay');
  } else if (o.case === 'integrations at once') {
    // With the integrations off, no start says either line; so KNOWN_BENIGN, which lets them pass
    // for this case, hides nothing on any other start.
    obs.quietStart = !ctx.server.log.slice(ctx.server.startIndex || 0).some((l) => l.includes(PLACEHOLDERS) || l.includes(NO_VAULT));
    let mark = logMark(ctx);
    await ctx.config.set('placeholders-enabled', 'true', 's1');
    await ticks(10);
    obs.placeholders = logSince(ctx, mark).some((l) => l.includes(PLACEHOLDERS));
    mark = logMark(ctx);
    await ctx.config.set('economy-enabled', 'true', 's1');
    await ticks(10);
    obs.economy = logSince(ctx, mark).some((l) => l.includes(NO_VAULT));
  }
}

function checks(obs, o) {
  if (o.case === 'mirror proximity') {
    return [
      c('by default, eight blocks off, the room behind the Ops mirror was drawn', () => obs.byDefault && obs.byDefault.count > 100),
      c('at 4, eight blocks off, nothing was', () => {
        if (obs.atEight && obs.atEight.count === 0) return true;
        throw new Error(`${obs.atEight && obs.atEight.count} blocks`);
      }),
      c('and three blocks off, it was', () => obs.atThree && obs.atThree.count > 100),
    ];
  }
  if (o.case === 'mirror view depth') {
    return [
      c('by default the room was drawn more than ten blocks deep', () => obs.byDefault && obs.byDefault.deepest > 10),
      c('at 4, no deeper than five', () => {
        if (obs.shallow && obs.shallow.count > 0 && obs.shallow.deepest <= 5) return true;
        throw new Error(`${obs.shallow && obs.shallow.count} blocks, ${obs.shallow && obs.shallow.deepest} deep`);
      }),
    ];
  }
  if (o.case === 'mirror fog') {
    return [
      c('by default mirror debug says "fog: off (mirror-fog-at-depth)"', () => {
        if (/fog: off \(mirror-fog-at-depth\)/.test(obs.off || '')) return true;
        throw new Error(`said: ${obs.off}`);
      }),
      c('on, with the view four deep: "fog: pulled in to N chunk(s), from M"', () => {
        if (/fog: pulled in to \d+ chunk\(s\), from \d+/.test(obs.on || '')) return true;
        throw new Error(`said: ${obs.on}`);
      }),
    ];
  }
  if (o.case === 'metrics') {
    return [
      c('false: "Stopped sending usage counts to bStats." at once', () => obs.stopped === true),
      c('true again: "Sending anonymous usage counts to bStats; ..."', () => obs.started === true),
    ];
  }
  if (o.case === 'coreprotect') {
    return [...builtChecks(obs),
      c('"coreprotect-enabled is set, but CoreProtect is not running; nothing is logged to it." logged', () => obs.logged === true)];
  }
  const humCheck = (n) => c(`the hum repeated every ${n} ticks: four or more in five seconds`, () => {
    if (obs.hum && obs.hum.n >= 4) return true;
    throw new Error(`${obs.hum && obs.hum.n} in five seconds`);
  });
  const opened = c(`${GATE} dialled Relay and opened`, () => obs.hum && /Stargates connected/.test(obs.hum.dial || '') && obs.hum.drawn === true);
  if (o.case === 'hum at once') return [...builtChecks(obs), opened, humCheck(20)];
  if (o.case === 'scan at once') {
    return [...builtChecks(obs),
      c(`${GATE} dialled Relay and opened`, () => /Stargates connected/.test(obs.dial || '') && obs.drawn === true),
      c('by default an item laid in the opening was sent within two seconds', () => {
        if (obs.byDefault && obs.byDefault.after !== null && obs.byDefault.after <= 2000) return true;
        throw new Error(`after ${obs.byDefault && obs.byDefault.after} ms`);
      }),
      c('at 200, set just before it was laid, the next one was still there five seconds on', () => {
        if (obs.slow && obs.slow.held === true) return true;
        throw new Error(`held ${obs.slow && obs.slow.held}, sent after ${obs.slow && obs.slow.after} ms (by default ${obs.byDefault && obs.byDefault.after} ms)`);
      }),
      c('and sent by the sweep ten seconds after the change', () => {
        if (obs.slow && obs.slow.after !== null && obs.slow.after >= 5000) return true;
        throw new Error(`after ${obs.slow && obs.slow.after} ms`);
      })];
  }
  if (o.case === 'integrations at once') {
    return [
      c('this start said nothing of PlaceholderAPI or Vault, with both off', () => obs.quietStart === true),
      c(`placeholders-enabled true, at once: "${PLACEHOLDERS}"`, () => obs.placeholders === true),
      c(`economy-enabled true, at once: "${NO_VAULT}"`, () => obs.economy === true),
    ];
  }
  return [];
}

module.exports = { cases, needs, stage, run, checks };
