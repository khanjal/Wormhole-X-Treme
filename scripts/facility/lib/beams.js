'use strict';
// Beams for chambers: save a destination (public, by an op; a place, by its owner), beam a
// traveller by a player command or the console form, and record what happened: what the
// traveller was told and when, where it landed and facing which way, and whether a watcher's
// client lost sight of it while it travelled (the plugin hides a traveller with hideEntity).
// Every fact is from the plugin's source (BeamCommand, BeamTravel, BeamAnimation, BeamTiming,
// BeamCooldown, WorldUtils.findSafePlayerLocation):
//
//  - `beam admin set|remove <name>` (op, player-only) saves or drops a public destination where
//    the player stands, facing as they face; `beam place set|remove|list` is a player's own.
//  - `beam to <name>` (and `go <name>`) looks in the traveller's own places, then the public list.
//  - `beam admin send <player> <player|dest>|<x> <y> <z> [world]` works from the console;
//    `beam admin goto` is player-only; both need wormhole.beam.admin.teleport (op by default).
//  - "Beaming to <name>..." at tick 0, the teleport at envelop + teleport-at-step (24 ticks),
//    "Beamed to <name>." at the teleport + descend + fade (52 ticks by default). The rise's
//    length only paces the column: the descent starts at the teleport, not after the rise.
//  - The landing is the stored spot if a player can stand there, else the first standable
//    spot 1..3 up, else 1..3 down, at the block's centre with the stored yaw.
//  - Ops (beam.admin) skip the cooldown; a player's cooldown starts when the beam departs.

const { ticks } = require('./probe');
const trip = require('./ringtrip');

const WORLDS = { 'minecraft:overworld': 'world', 'minecraft:the_nether': 'world_nether', 'minecraft:the_end': 'world_the_end' };

/** The ticks from "Beaming to" to "Beamed to" for these settings (the defaults if absent), clamped as BeamTiming does. */
function beamTicks(s = {}) {
  const n = (k, d) => (s[k] !== undefined ? Number(s[k]) : d);
  const rise = Math.max(2, n('beam-rise-ticks', 18));
  const teleportAt = Math.min(Math.max(n('beam-teleport-at-step', 12), 1), rise - 1);
  return Math.max(2, n('beam-envelop-ticks', 12)) + teleportAt + Math.max(1, n('beam-descend-ticks', 20)) + Math.max(1, n('beam-fade-ticks', 8));
}

function flat(p, q) {
  return Math.hypot(p.x - q.x, p.z - q.z);
}

function mcYaw(probe) {
  const deg = (probe.bot.entity.yaw * 180) / Math.PI;
  return (((180 - deg) % 360) + 360) % 360;
}

function yawOff(a, b) {
  const d = Math.abs(a - b) % 360;
  return d > 180 ? 360 - d : d;
}

class BeamKit {
  constructor(srv) {
    this.srv = srv;
  }

  /**
   * `who` says a command and collects what it is told for up to `ms` (or until `until`).
   */
  async ask(who, command, { ms = 2000, until = null } = {}) {
    const bot = who.bot;
    const heard = [];
    const on = (m) => heard.push(m.toString());
    bot.on('message', on);
    bot.chat(command);
    const end = Date.now() + ms;
    try {
      while (Date.now() < end && !(until && heard.some((h) => until.test(h)))) await ticks(2);
    } finally {
      bot.off('message', on);
    }
    return heard.filter((h) => !h.startsWith('[Server:')).join(' / ');
  }

  /** Stands `who` on `at` (block coords, yaw) in `dim`, facing its yaw, and saves it. */
  async save(who, kind, name, at, dim = 'minecraft:overworld') {
    await who.teleport({ x: at.x + 0.5, y: at.y, z: at.z + 0.5, yaw: at.yaw || 0 }, dim);
    const cmd = kind === 'public' ? `/wormhole beam admin set ${name}` : `/wormhole beam place set ${name}`;
    return this.ask(who, cmd, { until: /set to your current location|error/ });
  }

  async drop(who, kind, name) {
    const cmd = kind === 'public' ? `/wormhole beam admin remove ${name}` : `/wormhole beam place remove ${name}`;
    return this.ask(who, cmd, { ms: 1200, until: /Removed|No public|no place/ });
  }

  async list(who) {
    return this.ask(who, '/wormhole beam list', { until: /Beam destinations|No public beam/ });
  }

  async places(who) {
    return this.ask(who, '/wormhole beam place list', { until: /Your places|no places/ });
  }

  async send(target, dest) {
    const r = await this.srv.run(`wormhole beam admin send ${target} ${dest}`);
    return r.lines.map((l) => l.replace(/§./g, '')).join(' / ');
  }
}

/**
 * Follows one beam of `who`: from the command (sent by `start`, a function) to "Beamed to"
 * or a refusal. `watcher` (another probe) records whether its client lost and regained sight
 * of `who`. Returns { told, begun, done, ms, refused, gone, shown, at, dim, yaw }.
 */
async function follow(who, start, { name, watcher = null, ms = 15000, refusal = null } = {}) {
  const heard = [];
  trip.listen(who, heard);
  const seen = { gone: null, shown: null };
  let onGone = null;
  let onSpawn = null;
  if (watcher) {
    onGone = (e) => { if (e.username === who.name && seen.gone === null) seen.gone = Date.now(); };
    onSpawn = (e) => { if (e.username === who.name && seen.gone !== null && seen.shown === null) seen.shown = Date.now(); };
    watcher.bot.on('entityGone', onGone);
    watcher.bot.on('entitySpawn', onSpawn);
  }
  const t0 = Date.now();
  try {
    await start();
    const begun = await trip.until(async () => trip.said(heard, new RegExp(`Beaming to ${name}\\.\\.\\.`), t0)
      || (refusal && trip.said(heard, refusal, t0)), 5000, 2);
    const refused = refusal ? trip.said(heard, refusal, t0) : false;
    let done = false;
    if (begun && !refused) done = await trip.until(async () => trip.said(heard, new RegExp(`Beamed to ${name}\\.`), t0), ms, 1);
    const at = (re) => { const m = heard.find((x) => x.at >= t0 && re.test(x.text)); return m ? m.at : null; };
    const tBegun = at(new RegExp(`Beaming to ${name}\\.\\.\\.`));
    const tDone = at(new RegExp(`Beamed to ${name}\\.`));
    await who.settle();
    return {
      told: heard.filter((x) => x.at >= t0).map((x) => x.text),
      begun: Boolean(tBegun), done, refused,
      ms: tBegun && tDone ? tDone - tBegun : null,
      gone: seen.gone, shown: seen.shown,
      at: { x: who.position.x, y: who.position.y, z: who.position.z }, dim: who.dimension, yaw: mcYaw(who),
    };
  } finally {
    if (watcher) {
      watcher.bot.off('entityGone', onGone);
      watcher.bot.off('entitySpawn', onSpawn);
    }
  }
}

module.exports = { BeamKit, follow, beamTicks, flat, mcYaw, yawOff, WORLDS };
