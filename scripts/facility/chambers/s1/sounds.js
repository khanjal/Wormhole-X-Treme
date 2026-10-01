'use strict';
// The plugin's sounds, as the packets Probe's client is sent (lib/sounds.js): a gate's dial,
// iris, hum and shutdown; a ring trip; a beam. Each is counted at its source, with its volume and
// pitch, and each `*-sounds-enabled false` must leave the same trip silent. Mirrors make no sound
// of their own (no mirror class plays one), so they have no case.

const { GateKit } = require('../../lib/gatekit');
const { RingKit } = require('../../lib/rings');
const trip = require('../../lib/ringtrip');
const sounds = require('../../lib/sounds');
const campus = require('../../lib/campus');
const { ticks } = require('../../lib/probe');
const { O, GATE, GEOM, v, c, before, buildGate, builtChecks, until } = require('./common');

const GATE_SOUNDS = {
  activate: 'block.conduit.activate',
  chevron: 'block.iron_trapdoor.close',
  lock: 'block.beacon.power_select',
  kawoosh: 'entity.player.splash.high_speed',
  close: 'block.conduit.deactivate',
  irisClose: 'block.iron_door.close',
  irisOpen: 'block.iron_door.open',
  ambient: 'ambient.underwater.loop',
};
const RING_SOUNDS = { open: 'block.beacon.activate', ring: 'block.piston.extend', flash: 'block.beacon.power_select', close: 'block.beacon.deactivate' };
const BEAM_SOUNDS = { charge: 'block.respawn_anchor.charge', depart: 'entity.enderman.teleport', arrive: 'entity.shulker.teleport' };

// Two ends of a pair on the Concourse floor, clear of the transit pair and R5's.
const RING_A = { x: 64, y: 0, z: -4 };
const RING_B = { x: 80, y: 0, z: -4 };
const BEAM_FROM = { x: 6.5, y: 0, z: 10.5, yaw: 0 };

const cases = [
  v('gate sounds', 'a dial, the iris shut and opened, the hum while open, and the shutdown: each sound as many times as the plugin plays it, at gate-sound-volume'),
  v('gate sounds off', '`gate-sounds-enabled false`: the same, silent'),
  v('gate sound volume', '`gate-sound-volume 0.5`: every gate sound at 0.5, the hum at 0.4 of it'),
  v('gate sound renamed', '`gate-sound-kawoosh block.bell.use` and `gate-sound-chevron none`: a bell at the kawoosh, no chevrons'),
  v('ring sounds', 'a ring trip: open, four rings each way, the flashes and the close, at each end, at ring-sound-volume'),
  v('ring sounds off', '`ring-sounds-enabled false`: the same trip, silent'),
  v('beam sounds', 'a beam: charge, depart and arrive, once each, at beam-sound-volume, depart and arrive 20 ticks apart'),
  v('beam sounds off', '`beam-sounds-enabled false`: the same beam, silent'),
];

function needs(o) {
  return {
    'gate sounds off': { 'gate-sounds-enabled': 'false' },
    'gate sound volume': { 'gate-sound-volume': '0.5' },
    'gate sound renamed': { 'gate-sound-kawoosh': 'block.bell.use', 'gate-sound-chevron': 'none' },
    'ring sounds off': { 'ring-sounds-enabled': 'false' },
    'beam sounds off': { 'beam-sounds-enabled': 'false' },
  }[o.case] || {};
}

const isGate = (o) => o.case.startsWith('gate');
const isRing = (o) => o.case.startsWith('ring');

async function stage(ctx, o) {
  const obs = ctx.observed;
  if (isGate(o)) Object.assign(obs, await buildGate(ctx, { idc: '3333' }));
  if (isRing(o)) {
    const kit = new RingKit(ctx.server, ctx.probe);
    for (const id of (await kit.list()).ids.filter((x) => !(ctx.facility.keepRings || new Set()).has(x))) await kit.remove(id);
    await trip.stagePair(ctx, { pattern: 'ODD', built: 'console' }, RING_A, RING_B);
  }
}

async function runGate(ctx) {
  const obs = ctx.observed;
  const probe = ctx.facility.probe;
  const kit = new GateKit(ctx.server);
  obs.volume = Number(await ctx.config.get('gate-sound-volume'));
  await probe.teleport(before(GEOM, 8), O);
  await ticks(20);
  const rec = sounds.record(probe.bot);
  obs.dial = (await kit.dial(GATE, 'Relay')).text;
  obs.drawn = await kit.waitDrawn(probe, GEOM, 15000);
  // The hum repeats every gate-sound-ambient-ticks (70): five seconds hears at least one.
  await ticks(100);
  obs.irisShut = await kit.toggleIris(probe, GEOM);
  await ticks(30);
  obs.irisOpened = (await kit.toggleIris(probe, GEOM)) === false;
  await ticks(30);
  obs.closed = (await kit.force(GATE)).text;
  await kit.force('Relay');
  await ticks(40);
  obs.heard = rec.stop();
}

async function runRing(ctx) {
  const obs = ctx.observed;
  const probe = ctx.probe;
  obs.volume = Number(await ctx.config.get('ring-sound-volume'));
  if (!obs.id) return;
  const rec = sounds.record(probe.bot);
  await trip.stepIn(probe, RING_A);
  const got = await trip.arrival(probe, obs.arrivals[1], 20000);
  obs.carried = got.ok;
  // The rings go back down and the pads close (hold 20, eleven frames, linger 20) after arrival.
  await until(async () => sounds.named(rec.heard, RING_SOUNDS.close).length >= 2, 8000);
  await ticks(20);
  obs.heard = rec.stop();
}

async function runBeam(ctx) {
  const obs = ctx.observed;
  const probe = ctx.facility.probe;
  obs.volume = Number(await ctx.config.get('beam-sound-volume'));
  await probe.teleport(BEAM_FROM, O);
  await ticks(10);
  const dest = campus.ROUTES.beams.find((b) => b.name === 'BeamLab');
  const rec = sounds.record(probe.bot);
  probe.bot.chat('/wormhole beam to BeamLab');
  obs.beamed = await until(async () => probe.distanceTo({ x: dest.x + 0.5, y: dest.y, z: dest.z + 0.5 }) < 3, 10000);
  await ticks(40);
  obs.heard = rec.stop();
  await probe.teleport(campus.TRANSIT.home);
}

async function run(ctx, o) {
  if (isGate(o)) return runGate(ctx);
  if (isRing(o)) return runRing(ctx);
  return runBeam(ctx);
}

// ---- checks ----------------------------------------------------------------------------------

const near = (s, p, r = 3) => Math.hypot(s.x - p.x, s.y - p.y, s.z - p.z) <= r;
const r2 = (x) => Math.round(x * 100) / 100;

function gateChecks(obs, o) {
  const at = { x: GEOM.arrival ? GEOM.arrival.x : GEOM.centre.x, y: GEOM.arrival ? GEOM.arrival.y : 1, z: GEOM.arrival ? GEOM.arrival.z : GEOM.centre.z };
  const mine = () => (obs.heard || []).filter((s) => near(s, at, 4));
  const of = (key) => sounds.named(mine(), GATE_SOUNDS[key]);
  const ours = () => mine().filter((s) => Object.values(GATE_SOUNDS).includes(s.name) || s.name === 'block.bell.use');
  const list = [
    ...builtChecks(obs),
    c(`${GATE} dialled Relay and opened`, () => /Stargates connected/.test(obs.dial || '') && obs.drawn === true),
    c('its iris shut and opened again at the lever', () => obs.irisShut === true && obs.irisOpened === true),
    c(`and it was shut down: "${GATE} has been closed"`, () => /has been closed/.test(obs.closed || '')),
  ];
  if (o.case === 'gate sounds off') {
    list.push(c('not one gate sound was heard from it', () => {
      if (ours().length === 0) return true;
      throw new Error(`heard ${sounds.tally(ours())}`);
    }));
    return list;
  }
  const vol = () => r2(obs.volume);
  const count = (key, n, pitch = null) => c(`${GATE_SOUNDS[key]} x${n}${pitch !== null ? ` at pitch ${pitch}` : ''}, at volume ${vol()}`, () => {
    const s = of(key);
    if (s.length === n && s.every((x) => r2(x.volume) === vol() && (pitch === null || r2(x.pitch) === pitch))) return true;
    throw new Error(`heard ${s.length}: ${s.map((x) => `${r2(x.volume)}/${r2(x.pitch)}`).join(', ') || 'none'} (all: ${sounds.tally(mine())})`);
  });
  list.push(count('activate', 1, 1));
  if (o.case === 'gate sound renamed') {
    list.push(c('gate-sound-chevron none: no chevron sound', () => of('chevron').length === 0));
    list.push(c('gate-sound-kawoosh block.bell.use: a bell once, at the kawoosh\'s pitch 0.7, and no splash', () => {
      const bells = sounds.named(mine(), 'block.bell.use');
      if (bells.length === 1 && r2(bells[0].pitch) === 0.7 && of('kawoosh').length === 0) return true;
      throw new Error(`bells ${bells.map((x) => r2(x.pitch)).join(', ') || 'none'}, splashes ${of('kawoosh').length}`);
    }));
  } else {
    // Seven chevrons on a same-world dial, climbing 0.8 to 1.5; the lock with the last.
    list.push(c(`${GATE_SOUNDS.chevron} x7, climbing in pitch from 0.8 to 1.5, at volume ${vol()}`, () => {
      const s = of('chevron');
      const p = s.map((x) => r2(x.pitch));
      if (s.length === 7 && p[0] === 0.8 && p[6] === 1.5 && p.every((x, i) => i === 0 || x > p[i - 1]) && s.every((x) => r2(x.volume) === vol())) return true;
      throw new Error(`heard ${s.length}: pitches ${p.join(', ')}`);
    }));
    list.push(count('kawoosh', 1, 0.7));
  }
  list.push(count('lock', 1, 0.8));
  list.push(c('the lock with the last chevron, after the activation', () => {
    const lock = of('lock')[0];
    const act = of('activate')[0];
    const chev = of('chevron');
    return lock && act && act.t <= lock.t && (chev.length === 0 || Math.abs(chev[chev.length - 1].t - lock.t) < 150);
  }));
  list.push(count('irisClose', 1, 0.8), count('irisOpen', 1, 1), count('close', 1, 1));
  list.push(c(`the hum (${GATE_SOUNDS.ambient}) while open, at 0.4 of the volume: ${r2(obs.volume * 0.4)}`, () => {
    const s = of('ambient');
    if (s.length >= 1 && s.every((x) => r2(x.volume) === r2(obs.volume * 0.4))) return true;
    throw new Error(`heard ${s.length}: ${s.map((x) => r2(x.volume)).join(', ')}`);
  }));
  return list;
}

function ringChecks(obs, o) {
  const ends = [RING_A, RING_B].map((e) => ({ x: e.x + 0.5, y: e.y, z: e.z + 0.5 }));
  const at = (s, e) => Math.hypot(s.x - e.x, s.z - e.z) <= 1.5;
  const per = (name, e) => sounds.named(obs.heard || [], name).filter((s) => at(s, e));
  const list = [
    c('the pair was built', () => Boolean(obs.id)),
    c('Probe was carried from one end to the other', () => obs.carried === true),
  ];
  if (o.case === 'ring sounds off') {
    list.push(c('not one ring sound was heard', () => {
      const s = (obs.heard || []).filter((x) => Object.values(RING_SOUNDS).includes(x.name));
      if (s.length === 0) return true;
      throw new Error(`heard ${sounds.tally(s)}`);
    }));
    return list;
  }
  // Each end plays its own; the traveller, sixteen blocks from the other end (a sound at volume 1
  // carries sixteen), hears the one it stands in: the departure at A, the arrival at B.
  const vol = r2(obs.volume || 0);
  const shape = (e) => {
    const s = (obs.heard || []).filter((x) => Object.values(RING_SOUNDS).includes(x.name) && at(x, e));
    const key = Object.fromEntries(Object.entries(RING_SOUNDS).map(([k, n]) => [n, k]));
    return { s, seq: s.map((x) => `${key[x.name]} ${r2(x.pitch)}`).join(', ') };
  };
  const [a, b] = ends.map(shape);
  list.push(c(`at A, as Probe left: open, four rings climbing 0.8 to 1.4, the flash out at 1.4, at volume ${vol}`, () => {
    if (a.seq === 'open 1, ring 0.8, ring 1, ring 1.2, ring 1.4, flash 1.4' && a.s.every((x) => r2(x.volume) === vol)) return true;
    throw new Error(`heard: ${a.seq || 'nothing'}`);
  }));
  list.push(c(`at B, as Probe arrived: the flash in at 1.0, four rings back down 1.4 to 0.8, close, at volume ${vol}`, () => {
    if (b.seq === 'flash 1, ring 1.4, ring 1.2, ring 1, ring 0.8, close 1' && b.s.every((x) => r2(x.volume) === vol)) return true;
    throw new Error(`heard: ${b.seq || 'nothing'}`);
  }));
  return list;
}

function beamChecks(obs, o) {
  const of = (key) => sounds.named(obs.heard || [], BEAM_SOUNDS[key]);
  const list = [c('Probe was beamed to BeamLab', () => obs.beamed === true)];
  if (o.case === 'beam sounds off') {
    list.push(c('not one beam sound was heard', () => {
      const s = (obs.heard || []).filter((x) => Object.values(BEAM_SOUNDS).includes(x.name));
      if (s.length === 0) return true;
      throw new Error(`heard ${sounds.tally(s)}`);
    }));
    return list;
  }
  const vol = r2(obs.volume || 0);
  for (const key of ['charge', 'depart', 'arrive']) {
    list.push(c(`${BEAM_SOUNDS[key]} x1, at volume ${vol}`, () => of(key).length === 1 && r2(of(key)[0].volume) === vol));
  }
  // depart at envelop + teleport-at (24 ticks), arrive at + descend (44): 20 ticks apart.
  list.push(c('in order, charge, depart, arrive, with about 20 ticks from depart to arrive', () => {
    const [ch, de, ar] = ['charge', 'depart', 'arrive'].map((k) => of(k)[0]);
    return ch && de && ar && ch.t < de.t && de.t < ar.t && ar.t - de.t >= 700 && ar.t - de.t <= 1500;
  }));
  return list;
}

function checks(obs, o) {
  if (isGate(o)) return gateChecks(obs, o);
  if (isRing(o)) return ringChecks(obs, o);
  return beamChecks(obs, o);
}

async function cleanup(ctx) {
  const kit = new RingKit(ctx.server, ctx.facility.probe);
  const keep = ctx.facility.keepRings || new Set();
  const { ids } = await kit.list().catch(() => ({ ids: [] }));
  for (const id of ids.filter((x) => !keep.has(x))) await kit.remove(id).catch(() => {});
  for (const e of [RING_A, RING_B]) await kit.clear('ODD', e);
}

module.exports = { cases, needs, stage, run, checks, cleanup, GATE_SOUNDS, RING_SOUNDS, BEAM_SOUNDS };
