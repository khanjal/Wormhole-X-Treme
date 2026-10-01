'use strict';
// The rest: the mirror settings, the integrations a server without their plugins can still see,
// and the settings read only when the plugin starts, which a restart puts in force (and which a
// change at run time leaves as they were, though the guide says no restart is needed).

const { Vec3 } = require('vec3');
const { GateKit } = require('../../lib/gatekit');
const mirrors = require('../../lib/mirrors');
const sounds = require('../../lib/sounds');
const campus = require('../../lib/campus');
const { ticks } = require('../../lib/probe');
const { O, GATE, GEOM, v, c, until, ear, toldSince, before, logMark, logSince, buildGate, builtChecks } = require('./common');
const { GATE_SOUNDS } = require('./sounds');

const OPS = campus.ROUTES.mirrors.find((m) => m.name === 'Ops');
const FAR = { x: OPS.x + 0.5, y: 0, z: OPS.z - 30.5, yaw: 0 };
const PLACEHOLDERS = 'Placeholders enabled in config but PlaceholderAPI was not found. Placeholders disabled.';
const NO_VAULT = 'Vault not found. Economy features disabled.';
const NODES_AT_START = 'No Vault/LuckPerms provider detected; permission checks will rely on server built-in permission handling (player.hasPermission()).';
const FALLBACK = 'enabling simple permission fallback';
const START_SETTINGS = {
  'placeholders-enabled': 'true', 'economy-enabled': 'true', 'entity-scan-interval-ticks': '40', 'gate-sound-ambient-ticks': '20', 'permissions-auto-fallback': 'false',
};

const cases = [
  v('mirror proximity', '`mirror-proximity-distance 4`: eight blocks from a mirror nothing is drawn, three blocks from it the room is; the default 16 draws it at eight'),
  v('mirror view depth', '`mirror-view-depth 4`: the room behind a mirror is drawn no deeper than four blocks, where the default goes far further'),
  v('mirror fog', '`mirror-fog-at-depth true` with a shallow view: `mirror debug` says the view distance was pulled in; off by default'),
  v('metrics', '`metrics-enabled false` stops bStats at once, and true starts it again, each said in the log'),
  v('coreprotect', '`coreprotect-enabled true` with no CoreProtect: building a gate logs that nothing is logged to it'),
  v('read at start', 'placeholders and economy on, entity scan 40, ambient 20, auto-fallback off, then a restart: each in force, said in the log or heard'),
  {
    ...v('ambient ticks at run time', '`gate-sound-ambient-ticks 20` by `wormhole config`, no restart: the hum should repeat every second'),
    expect: 'FAIL:the hum repeated every 20 ticks: four or more in five seconds',
    known: 'gate-sound-ambient-ticks changed by /wormhole config does nothing until a restart: the hum\'s timer is scheduled once, at enable, with the period read then (WormholeXTreme.onEnable, runTaskTimer), though `wormhole config` says it is now 20 and the guide says a change needs no reload and no restart (docs/guide/SERVER.md); after a restart it holds (s1 read at start)',
  },
];

function needs(o) {
  return {
    'mirror fog': { 'mirror-view-depth': '4' },
    coreprotect: { 'coreprotect-enabled': 'true' },
    'read at start': START_SETTINGS,
    'ambient ticks at run time': { 'gate-sound-ambient-ticks': '20' },
  }[o.case] || {};
}

const state = { restartOwed: false };

async function stage(ctx, o) {
  const obs = ctx.observed;
  if (o.case === 'read at start') {
    obs.restartFrom = await ctx.facility.restart('the settings read at start (S1)');
    state.restartOwed = true;
  }
  if (['read at start', 'ambient ticks at run time'].includes(o.case)) Object.assign(obs, await buildGate(ctx));
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
  } else if (o.case === 'read at start') {
    const since = ctx.server.log.slice(obs.restartFrom || 0);
    obs.lines = {
      placeholders: since.some((l) => l.includes(PLACEHOLDERS)),
      economy: since.some((l) => l.includes(NO_VAULT)),
      scan: since.some((l) => l.includes('Non-player entity gate scan interval: 40 ticks')),
      nodes: since.some((l) => l.includes(NODES_AT_START)),
      fallback: since.some((l) => l.includes(FALLBACK)),
    };
    const before = ctx.server.log.slice(0, obs.restartFrom || 0);
    obs.firstScan = before.some((l) => l.includes('Non-player entity gate scan interval: 20 ticks'));
    // With the integrations off, a start says neither; so KNOWN_BENIGN, which lets them pass for
    // this case, hides nothing on any other start.
    obs.firstQuiet = !before.some((l) => l.includes(PLACEHOLDERS) || l.includes(NO_VAULT));
    obs.mode = await ctx.config.get('permissions-support-disable');
    obs.hum = await hum(ctx);
  } else if (o.case === 'ambient ticks at run time') {
    obs.hum = await hum(ctx);
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
  if (o.case === 'read at start') {
    const l = (k) => obs.lines && obs.lines[k];
    return [
      c('the first start logged a scan interval of 20 ticks', () => obs.firstScan === true),
      c('and said nothing of PlaceholderAPI or Vault, with both off', () => obs.firstQuiet === true),
      c(`after the restart: "${PLACEHOLDERS}"`, () => l('placeholders')),
      c(`"${NO_VAULT}"`, () => l('economy')),
      c('"Non-player entity gate scan interval: 40 ticks"', () => l('scan')),
      c(`auto-fallback off: "${NODES_AT_START}", and no fallback`, () => l('nodes') && !l('fallback')),
      c('so permissions-support-disable stays false', () => obs.mode === 'false'),
      ...builtChecks(obs), opened, humCheck(20),
    ];
  }
  if (o.case === 'ambient ticks at run time') return [...builtChecks(obs), opened, humCheck(20)];
  return [];
}

/** The restart a start-time case owes once its settings are put back; owed until one succeeds. */
async function payRestart(ctx) {
  if (!state.restartOwed) return;
  await ctx.facility.restart('the settings read at start put back (S1)');
  state.restartOwed = false;
}

module.exports = { cases, needs, stage, run, checks, cleanup: payRestart, afterRestore: payRestart };
