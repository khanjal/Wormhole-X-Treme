'use strict';
// The bot's body. Walks in straight lines between waypoints (the facility is flat and its
// doors are in line, so no pathfinder), looks, right-clicks blocks, stands on plates. Every
// wait is for an observed state with a deadline, and names what did not happen.
// Entities are found by tag through the server (tags are not sent to clients), never by id.

const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const text = require('./text');

/** Resolves with the first `event` whose arguments satisfy `test`; rejects at the deadline. */
function waitEvent(emitter, event, test, ms, what) {
  return new Promise((resolve, reject) => {
    const on = (...a) => { if (test(...a)) { done(); resolve(a); } };
    const timer = setTimeout(() => { done(); reject(new Error(`no ${what} within ${ms} ms`)); }, ms);
    const done = () => { clearTimeout(timer); emitter.off(event, on); };
    emitter.on(event, on);
  });
}

/** Like waitEvent but resolves null at the deadline instead of rejecting. */
function maybeEvent(emitter, event, test, ms) {
  return waitEvent(emitter, event, test, ms, event).catch(() => null);
}

function join({ host = '127.0.0.1', port, version, username }) {
  const bot = mineflayer.createBot({ host, port, username, version, auth: 'offline' });
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${username} did not spawn within 60 s`)), 60000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(bot); });
    bot.once('kicked', (reason) => { clearTimeout(timer); reject(new Error(`${username} was kicked: ${JSON.stringify(reason)}`)); });
    bot.once('error', (e) => { clearTimeout(timer); reject(e); });
  });
}

const DIMENSIONS = { overworld: 'minecraft:overworld', the_nether: 'minecraft:the_nether', the_end: 'minecraft:the_end' };

class Probe {
  constructor(bot, srv) {
    this.bot = bot;
    this.srv = srv;
  }

  get name() { return this.bot.username; }

  get position() { return this.bot.entity.position; }

  /** The dimension the client is in, as a namespaced id. */
  get dimension() {
    const d = String(this.bot.game.dimension || '');
    return DIMENSIONS[d.replace(/^minecraft:/, '')] || d;
  }

  distanceTo(p) {
    return this.position.distanceTo(new Vec3(p.x, p.y, p.z));
  }

  /** Teleports by console and waits until the client has been moved there. */
  async teleport(p, dim = 'minecraft:overworld') {
    const moved = maybeEvent(this.bot, 'forcedMove', () => this.distanceTo(p) < 1.5, 10000);
    const r = await this.srv.run(`execute in ${dim} run tp ${this.name} ${p.x} ${p.y} ${p.z} ${p.yaw || 0} ${p.pitch || 0}`);
    if (r.errors.length) throw new Error(`tp ${this.name}: ${r.errors.join(' ')}`);
    await moved;
    if (this.distanceTo(p) >= 1.5) throw new Error(`${this.name} was not moved to ${p.x} ${p.y} ${p.z} (at ${this.position})`);
  }

  /** Walks straight to each waypoint in turn; fails if it stops closing in for two seconds. */
  async walk(points, { within = 0.6, ms = 30000 } = {}) {
    for (const p of points) await this.walkTo(p, { within, ms });
  }

  /** Walks straight to a point; `until` ends the walk early (and successfully) when it turns true. */
  walkTo(p, { within = 0.6, ms = 30000, until = null } = {}) {
    const target = new Vec3(p.x, this.position.y, p.z);
    const bot = this.bot;
    return new Promise((resolve, reject) => {
      const started = Date.now();
      let best = Infinity;
      let bestAt = Date.now();
      const stop = (err) => {
        bot.off('physicsTick', tick);
        bot.setControlState('forward', false);
        bot.setControlState('sprint', false);
        if (err) reject(err); else resolve();
      };
      const tick = () => {
        if (until && until()) return stop();
        const here = bot.entity.position;
        const flat = new Vec3(here.x, target.y, here.z).distanceTo(target);
        if (flat <= within) return stop();
        if (flat < best - 0.05) { best = flat; bestAt = Date.now(); }
        if (Date.now() - bestAt > 2000) return stop(new Error(`${bot.username} stuck ${flat.toFixed(1)} from ${p.x} ${p.z} (at ${here.floored()})`));
        if (Date.now() - started > ms) return stop(new Error(`${bot.username} did not reach ${p.x} ${p.z} within ${ms} ms`));
        bot.lookAt(new Vec3(target.x, here.y + bot.entity.height * 0.9, target.z), true);
        bot.setControlState('forward', true);
        return undefined;
      };
      bot.on('physicsTick', tick);
    });
  }

  /** Looks at the centre of a block, or a point. */
  async look(p) {
    await this.bot.lookAt(new Vec3(p.x, p.y, p.z), true);
  }

  /**
   * Right-clicks a block, as a player pressing a button or pulling a lever does, and resolves
   * with the block as the client sees it once its state has changed (or rejects).
   */
  async click({ x, y, z }, ms = 5000) {
    const at = new Vec3(x, y, z);
    const block = this.bot.blockAt(at);
    if (!block) throw new Error(`${this.name} cannot see a block at ${x} ${y} ${z}`);
    const before = block.stateId;
    const changed = waitEvent(this.bot, 'blockUpdate', (_old, b) => b && b.position.equals(at) && b.stateId !== before, ms,
      `a change to ${block.name} at ${x} ${y} ${z} after the click`);
    await this.look({ x: x + 0.5, y: y + 0.5, z: z + 0.5 });
    await this.bot.activateBlock(block);
    const [, now] = await changed;
    return now;
  }

  /** Walks onto a plate and waits to be teleported off it; returns where it landed. */
  async standOn({ x, y, z }, ms = 10000) {
    let teleported = false;
    const moved = waitEvent(this.bot, 'forcedMove', () => { teleported = true; return true; }, ms, `a teleport from the plate at ${x} ${y} ${z}`);
    await this.walkTo({ x: x + 0.5, y, z: z + 0.5 }, { within: 0.3, ms, until: () => teleported }).catch(() => {});
    await moved;
    return { position: this.position.clone(), dimension: this.dimension };
  }

  /** Where the server has an entity with this tag, or null: tags are server-side only. */
  async entityByTag(tag) {
    const r = await this.srv.run(`data get entity @e[tag=${tag},limit=1] Pos`);
    const line = r.lines.find((l) => l.includes('has the following entity data: '));
    if (!line) return null;
    const pos = text.parseSnbt(line.slice(line.indexOf('has the following entity data: ') + 31));
    return { x: pos[0], y: pos[1], z: pos[2] };
  }

  /** The plain text of every text display the client knows of within `range` of a point. */
  displaysNear(p, range = 3) {
    const out = [];
    for (const e of Object.values(this.bot.entities)) {
      if (e.name !== 'text_display' || e.position.distanceTo(new Vec3(p.x, p.y, p.z)) > range) continue;
      out.push(...shownText(e));
    }
    return out;
  }
}

/** The plain text of each text component in an entity's metadata, as the client would draw it. */
function shownText(entity) {
  const nbt = require('prismarine-nbt');
  const out = [];
  for (const v of Object.values(entity.metadata || {})) {
    if (!v || typeof v !== 'object' || typeof v.type !== 'string' || !('value' in v)) continue;
    if (v.type !== 'compound' && v.type !== 'string' && v.type !== 'list') continue;
    try { out.push(text.plain(nbt.simplify(v))); } catch { /* not a component */ }
  }
  return out;
}

module.exports = { Probe, join, waitEvent, maybeEvent, shownText };
