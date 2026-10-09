'use strict';
// Quantum mirrors for chambers and the transit routes. Every fact here is from the plugin's
// source (MirrorCommand, MirrorPlacement, MirrorNetwork, MirrorInteraction, MirrorSignpost,
// Windows, MirrorPackets):
//
//  - A mirror is a wall banner. `mirror create <name> <world> <x> <y> <z>` (console form) makes
//    one; a standing banner is refused ("A mirror hangs on a wall..."), and so is a wall that is
//    not solid a block out round the 1x2 opening (the banner and the block under it). Two out is
//    a warning, not a refusal. `mirror-per-world-limit` (1 by default, 0 = none) is checked first.
//  - Its room is the banner's own column, feet at the opening's bottom, facing out; a traveller
//    lands there (corrected by the safe-spot search) facing the way the banner faces.
//  - A right-click walks the other mirrors by name, its `-start` first: "'A' opens onto 'B'
//    (k of n)." With somebody else near, a choice holds three seconds ("Somebody else is at this
//    mirror..."); clicks under 250 ms apart are ignored. A punch goes through.
//  - Near a mirror (mirror-proximity-distance, 16), a viewer's client is sent its room: its own
//    room flipped while nobody has chosen, and once a choice is made the banner as air, the
//    opening as barrier and the chosen room behind the wall (the banner's return needs
//    Player.sendBlockUpdate, from 1.20.1). Looking at it, the approach line names it above the
//    hotbar: "<A> -- right-click to choose a mirror." or "<A> -- punch to travel to <B>, ...".
//  - The first capture of a room takes a while (seconds, at view depth 160); `mirror debug`
//    says when it is in memory.

const { Vec3 } = require('vec3');
const { ticks } = require('./probe');
const trip = require('./ringtrip');

const WORLDS = { 'minecraft:overworld': 'world', 'minecraft:the_nether': 'world_nether', 'minecraft:the_end': 'world_the_end' };
const FRONT = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] };
const YAW = { north: 180, south: 0, east: 270, west: 90 };

function plain(line) {
  return line.replace(/§./g, '');
}

/** Where a traveller lands at mirror `m` ({ x, y, z, facing, floorY }): its banner's column. */
function arrivalOf(m) {
  return { x: m.x + 0.5, y: m.floorY, z: m.z + 0.5, yaw: YAW[m.facing] };
}

/** A point `d` blocks in front of a mirror, on its floor, facing it. */
function frontOf(m, d = 3) {
  const [fx, fz] = FRONT[m.facing];
  return { x: m.x + 0.5 + fx * d, y: m.floorY, z: m.z + 0.5 + fz * d, yaw: (YAW[m.facing] + 180) % 360 };
}

/** The opening's two wall blocks behind the banner. */
function openingOf(m) {
  const [fx, fz] = FRONT[m.facing];
  return [0, -1].map((dy) => ({ x: m.x - fx, y: m.y + dy, z: m.z - fz }));
}

class MirrorKit {
  constructor(srv) {
    this.srv = srv;
  }

  async say(command) {
    const r = await this.srv.run(command);
    return r.lines.map(plain).join(' / ');
  }

  async banner(m, colour = 'white') {
    await this.srv.run(`execute in ${m.dim} run setblock ${m.x} ${m.y} ${m.z} minecraft:${colour}_wall_banner[facing=${m.facing}]`);
  }

  create(name, m) {
    return this.say(`wormhole mirror create ${name} ${WORLDS[m.dim]} ${m.x} ${m.y} ${m.z}`);
  }

  set(name, property, value = '') {
    return this.say(`wormhole mirror set ${name} ${property} ${value}`.trim());
  }

  remove(name) {
    return this.say(`wormhole mirror remove ${name}`);
  }

  /** The mirrors the plugin holds, by name. */
  async list() {
    const text = await this.say('wormhole mirror list');
    return { text, names: [...text.matchAll(/(?:^|\/)\s+(\S+) -- /g)].map((x) => x[1]).sort() };
  }

  /** Waits (bounded) until a mirror's capture is in memory; returns what debug said. */
  async captured(name, ms = 90000) {
    const end = Date.now() + ms;
    let said = '';
    while (Date.now() < end) {
      said = await this.say(`wormhole mirror debug ${name}`);
      // In memory is what a window draws from. The file is written after, off the main thread, and
      // is not waited for: when it cannot be written the plugin logs it and the fault counter says so.
      if (/capture: [^,]+, in memory/.test(said) && !/being taken now/.test(said)) return said;
      await ticks(20);
    }
    throw new Error(`the capture of ${name} was not taken within ${ms / 1000} s: ${said}`);
  }
}

/**
 * Watches what a probe's client is sent around a mirror: every block change behind its wall
 * within `r` (where its view is drawn: nothing real changes there), and the banner's and
 * opening's state. Call stop() when done.
 */
function watchWindow(probe, m, r = 40) {
  const seen = { kinds: new Set(), count: 0, bannerAir: false, openingBarrier: 0, from: Date.now() };
  const opening = openingOf(m);
  const [fx, fz] = FRONT[m.facing];
  const on = (_old, b) => {
    if (!b || Math.abs(b.position.x - m.x) > r || Math.abs(b.position.z - m.z) > r) return;
    // Behind the wall plane (the wall is one block back from the banner).
    const along = (b.position.x - (m.x - fx)) * fx + (b.position.z - (m.z - fz)) * fz;
    if (along < 0) {
      seen.count++;
      seen.kinds.add(b.name);
    }
    if (b.position.x === m.x && b.position.y === m.y && b.position.z === m.z && b.name === 'air') seen.bannerAir = true;
    if (b.name === 'barrier' && opening.some((o) => o.x === b.position.x && o.y === b.position.y && o.z === b.position.z)) seen.openingBarrier++;
  };
  probe.bot.on('blockUpdate', on);
  seen.stop = () => probe.bot.off('blockUpdate', on);
  seen.reset = () => { seen.kinds = new Set(); seen.count = 0; seen.bannerAir = false; seen.openingBarrier = 0; };
  return seen;
}

/** Walks a probe up to a mirror and faces its banner; returns the approach line it was shown. */
async function approach(probe, m, heard, d = 3, ms = 4000) {
  await probe.teleport(frontOf(m, d), m.dim);
  const [fx, fz] = FRONT[m.facing];
  await probe.face({ x: m.x + 0.5 - fx * 0.4, y: m.y + 0.6, z: m.z + 0.5 - fz * 0.4 });
  const t0 = Date.now();
  await trip.until(async () => heard.some((x) => x.at >= t0 && x.bar && new RegExp(`^:: ${m.name} -- `).test(x.text)), ms);
  const line = [...heard].reverse().find((x) => x.at >= t0 && x.bar && new RegExp(`^:: ${m.name} -- `).test(x.text));
  return line ? line.text : null;
}

/**
 * Right-clicks a mirror until its choice is `target` (at most `max` clicks, `gap` ms apart);
 * returns the hints it was given, in order, and whether it got there.
 */
async function chooseUntil(probe, m, target, heard, { max = 8, gap = 400 } = {}) {
  const hints = [];
  for (let i = 0; i < max; i++) {
    const t0 = Date.now();
    const [fx, fz] = FRONT[m.facing];
    await probe.rightClick({ x: m.x, y: m.y, z: m.z }, { dir: new Vec3(fx, 0, fz), cursor: new Vec3(0.5 - fx * 0.4, 0.6, 0.5 - fz * 0.4) });
    await trip.until(async () => heard.some((x) => x.at >= t0 && x.bar && /opens onto|Somebody else|No other mirrors/.test(x.text)), 2000, 2);
    const h = heard.find((x) => x.at >= t0 && x.bar && /opens onto|Somebody else|No other mirrors/.test(x.text));
    hints.push(h ? h.text : '(nothing)');
    if (h && new RegExp(`opens onto '${target}'`).test(h.text)) return { hints, reached: true };
    await new Promise((resolve) => { setTimeout(resolve, gap); });
  }
  return { hints, reached: false };
}

/** Punches a mirror and waits (bounded) to be put down at `at` in `dim`; returns where it is. */
async function punchThrough(probe, m, at, dim, ms = 8000) {
  await probe.punch({ x: m.x, y: m.y, z: m.z });
  const ok = await trip.until(async () => probe.dimension === dim && Math.hypot(probe.position.x - at.x, probe.position.z - at.z) < 1.5
    && Math.abs(probe.position.y - at.y) < 1.2, ms);
  await probe.settle();
  const deg = (probe.bot.entity.yaw * 180) / Math.PI;
  return { ok, at: { x: probe.position.x, y: probe.position.y, z: probe.position.z }, dim: probe.dimension, yaw: (((180 - deg) % 360) + 360) % 360 };
}

function yawOff(a, b) {
  const d = Math.abs(a - b) % 360;
  return d > 180 ? 360 - d : d;
}

module.exports = { MirrorKit, WORLDS, YAW, arrivalOf, frontOf, openingOf, watchWindow, approach, chooseUntil, punchThrough, yawOff, plain };
