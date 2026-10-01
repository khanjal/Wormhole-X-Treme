'use strict';
// What the Systems console's cases share: the gate it builds on G1's Stand position, where to
// stand by it, a probe's ear, and the server log since a mark.

const fs = require('fs');
const path = require('path');
const { Vec3 } = require('vec3');
const campus = require('../../lib/campus');
const { GateKit } = require('../../lib/gatekit');
const { ticks } = require('../../lib/probe');

const O = campus.OVERWORLD;
const GATE = 'Sys';
const STAND = campus.GATES.stand;
const GEOM = new GateKit(null).place('Standard', STAND.facing, STAND);
const RELAY = campus.GATES.far.Relay;
const NO = /You lack the permissions to do this\./;

const v = (value, why) => ({ value, label: value, why });

/** A check: its name, and a test that may throw to say what it saw instead. */
const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });

/** Every line a console command answered, as plain text. */
async function said(ctx, command) {
  const r = await ctx.server.run(command);
  return r.lines.map((l) => l.replace(/§./g, '').trim()).filter(Boolean);
}

/** Everything a probe is told from now on (chat and action bar), as plain text with when. */
function ear(probe) {
  if (!probe.s1heard) {
    probe.s1heard = [];
    probe.bot.on('message', (m, position) => { probe.s1heard.push({ at: Date.now(), text: m.toString().replace(/§./g, ''), bar: position === 'game_info' }); });
  }
  return probe.s1heard;
}

/** Runs `act` and returns what `probe` was told in the next `ms`, joined. */
async function told(probe, act, ms = 2500) {
  const since = ear(probe).length;
  await act();
  const end = Date.now() + ms;
  while (Date.now() < end) await ticks(4);
  return ear(probe).slice(since).map((x) => x.text).join(' / ');
}

/** What `probe` has been told since `t`, joined. */
function toldSince(probe, t) {
  return ear(probe).filter((x) => x.at >= t).map((x) => x.text).join(' / ');
}

/** Polls (bounded) until `test` is true; returns whether it became true. */
async function until(test, ms, every = 4) {
  const end = Date.now() + ms;
  for (;;) {
    if (await test()) return true;
    if (Date.now() > end) return false;
    await ticks(every);
  }
}

/** Where a probe stands to press a gate's DHD button. */
function atButton(geom = GEOM) {
  const b = geom.button;
  return { x: b.x + 0.5 + geom.normal.x * 1.5, y: STAND.floorY, z: b.z + 0.5 + geom.normal.z * 1.5, yaw: (geom.yaw + 180) % 360 };
}

/** A point `d` out in front of a gate's opening, facing it. */
function before(geom, d) {
  return { x: geom.centre.x + geom.normal.x * d, y: STAND.floorY, z: Math.floor(geom.opening[0].z) + 0.5 + geom.normal.z * d, yaw: (geom.yaw + 180) % 360 };
}

/** Where a traveller comes out of the Relay. */
function relayArrival() {
  return { x: RELAY.cx + 0.5, y: RELAY.floorY, z: RELAY.openingAt + 2 };
}

/** The server's config.yml for Wormhole, as text. */
function configFile(ctx) {
  const file = path.join(ctx.server.folder, 'plugins', 'WormholeXTreme', 'config.yml');
  return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
}

/** The server's log lines from now on: mark() then since(mark). */
function logMark(ctx) {
  return ctx.server.log.length;
}
function logSince(ctx, mark) {
  return ctx.server.log.slice(mark);
}

/**
 * Builds Sys on the Stand, owned by Probe (so a non-op's use is never the owner's). An iris code
 * (`idc`) gives it its iris lever.
 */
async function buildGate(ctx, { idc = null, net = null } = {}) {
  const kit = new GateKit(ctx.server);
  if (await kit.exists(GATE)) await kit.remove(GATE);
  const built = (await kit.build(GATE, GEOM, { dim: O, floorY: STAND.floorY, idc, net })).text;
  const owner = (await kit.edit(GATE, 'owner', ctx.facility.probe.name)).text;
  return { built, owner };
}

/** The checks that Sys was built and is Probe's. */
function builtChecks(obs) {
  return [c(`${GATE} was built and is Probe's`, () => /Built /.test(obs.built || '') && /Now owned by: Probe\b/.test(obs.owner || ''))];
}

/** Walks `probe` into Sys's opening, towards the Relay; returns whether it came out there. */
async function walkThrough(probe, geom = GEOM, arrival = relayArrival()) {
  await probe.teleport(before(geom, 4), O);
  await ticks(10);
  await probe.walkTo({ x: geom.centre.x, y: STAND.floorY, z: geom.opening[0].z - geom.normal.z * 2 }, { within: 0.5, ms: 8000, until: () => probe.distanceTo(arrival) < 4 }).catch(() => {});
  await ticks(10);
  return probe.distanceTo(arrival) < 4;
}

/**
 * Watches a gate's chevron cells on a probe's client: `stop()` returns, per light wave, the last
 * time its cells were shown turning lit (a spin's travelling light passes over them before they
 * lock), as ms from the start.
 */
function watchLights(probe, geom = GEOM) {
  const bot = probe.bot;
  const base = new Map();
  for (const l of geom.lights) {
    const b = bot.blockAt(new Vec3(l.x, l.y, l.z));
    base.set(`${l.x},${l.y},${l.z}`, b ? b.stateId : null);
  }
  const t0 = Date.now();
  const litAt = new Map();
  const outAt = new Map();
  const on = (_old, b) => {
    if (!b) return;
    const key = `${b.position.x},${b.position.y},${b.position.z}`;
    if (!base.has(key)) return;
    const was = _old ? _old.stateId !== base.get(key) : false;
    if (b.stateId !== base.get(key)) { if (!was) litAt.set(key, Date.now() - t0); } else if (litAt.has(key)) outAt.set(key, Date.now() - t0);
  };
  bot.on('blockUpdate', on);
  return {
    stop() {
      bot.off('blockUpdate', on);
      const waves = {};
      for (const l of geom.lights) {
        const key = `${l.x},${l.y},${l.z}`;
        const w = waves[l.order] || (waves[l.order] = { order: l.order, at: null, out: null });
        const t = litAt.get(key);
        if (t !== undefined && (w.at === null || t > w.at)) w.at = t;
        const o = outAt.get(key);
        if (o !== undefined && (w.out === null || o > w.out)) w.out = o;
      }
      return Object.values(waves).sort((a, b) => a.order - b.order);
    },
    /** How many chevron cells the client shows lit now. */
    litNow() {
      return geom.lights.filter((l) => { const b = bot.blockAt(new Vec3(l.x, l.y, l.z)); return b && b.stateId !== base.get(`${l.x},${l.y},${l.z}`); }).length;
    },
  };
}

module.exports = {
  O, GATE, STAND, GEOM, RELAY, NO, v, c, said, ear, told, toldSince, until, atButton, before, relayArrival,
  configFile, logMark, logSince, buildGate, builtChecks, walkThrough, watchLights,
};
