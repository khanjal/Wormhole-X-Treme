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

/** Waits n game ticks by the clock: Mineflayer stops its physics ticks while riding. */
function ticks(n) {
  return new Promise((resolve) => { setTimeout(resolve, n * 50); });
}

/** Like waitEvent but resolves null at the deadline instead of rejecting. */
function maybeEvent(emitter, event, test, ms) {
  return waitEvent(emitter, event, test, ms, event).catch(() => null);
}

/** Connects a bot and resolves once it has spawned; `onCreate(bot)` runs before it connects. */
function join({ host = '127.0.0.1', port, version, username, onCreate = null }) {
  const bot = mineflayer.createBot({ host, port, username, version, auth: 'offline' });
  // An 'error' with no listener throws out of the event loop and takes the launcher (and the
  // finally that stops the server) with it; keep the last one for whoever asks.
  bot.on('error', (e) => { bot.lastError = e; });
  bot.on('end', (reason) => { bot.ended = reason || 'ended'; });
  if (onCreate) onCreate(bot);
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
    // Mineflayer 4.39 notices a dismount only from a passenger packet naming the rider with no
    // vehicle; a server empties the vehicle's passenger list instead, so bot.vehicle would stay
    // set for ever. Watch the vehicle's own list and let go of it here.
    bot._client.on('set_passengers', ({ entityId, passengers }) => {
      const v = bot.vehicle;
      if (!v || v.id !== entityId || passengers.includes(bot.entity.id)) return;
      v.passengers = v.passengers.filter((e) => e !== bot.entity);
      bot.vehicle = null;
      bot.entity.vehicle = null;
      bot.emit('dismount', v);
    });
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
    const errors = r.errors.filter((l) => !this.isMoveCheck(l));
    if (errors.length) throw new Error(`tp ${this.name}: ${errors.join(' ')}`);
    await moved;
    await this.settle();
    if (this.distanceTo(p) >= 1.5) throw new Error(`${this.name} was not moved to ${p.x} ${p.y} ${p.z} (at ${this.position})`);
  }

  /**
   * The server's movement check on this bot. On 1.20.4, Paper judges the move Mineflayer sends
   * straight after confirming a teleport against where the bot stood when the tick began, so a
   * bot on localhost (answering inside the same tick) "moved too quickly" and is teleported
   * again, to where it already is. Harmless; settle() waits out that second teleport.
   */
  isMoveCheck(line) {
    return new RegExp(`^${this.name} moved (too quickly|wrongly)!`).test(line);
  }

  /** Waits until no teleport has arrived for five ticks (at most three seconds). */
  async settle(quietTicks = 5, maxMs = 3000) {
    let quiet = 0;
    const reset = () => { quiet = 0; };
    this.bot.on('forcedMove', reset);
    const end = Date.now() + maxMs;
    try {
      while (quiet < quietTicks && Date.now() < end) {
        await ticks(1);
        quiet++;
      }
    } finally {
      this.bot.off('forcedMove', reset);
    }
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

  /** Right-clicks a block without waiting for it to change (a button the plugin may consume). */
  async press({ x, y, z }) {
    const block = this.bot.blockAt(new Vec3(x, y, z));
    if (!block) throw new Error(`${this.name} cannot see a block at ${x} ${y} ${z}`);
    await this.look({ x: x + 0.5, y: y + 0.5, z: z + 0.5 });
    await this.bot.activateBlock(block);
  }

  /** Walks onto a plate and waits to be teleported off it; returns where it landed. */
  async standOn({ x, y, z }, ms = 10000) {
    let teleported = false;
    // Observed from the start, so a timeout during the walk is reported rather than unhandled.
    const moved = waitEvent(this.bot, 'forcedMove', () => { teleported = true; return true; }, ms, `teleport from the plate at ${x} ${y} ${z}`)
      .then(() => null, (e) => e);
    await this.walkTo({ x: x + 0.5, y, z: z + 0.5 }, { within: 0.3, ms, until: () => teleported }).catch(() => {});
    const failed = await moved;
    if (failed) throw failed;
    await this.settle();
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

  // ---- hands --------------------------------------------------------------------------------

  /** Puts the first stack of an item id in the hand (or the off hand); throws if none is held. */
  async hold(id, hand = 'hand') {
    const name = id.replace(/^minecraft:/, '');
    const item = this.bot.inventory.items().find((i) => i.name === name);
    if (!item) throw new Error(`${this.name} has no ${name} to hold`);
    await this.bot.equip(item, hand);
  }

  /** Waits (bounded) until the inventory holds an item, e.g. after a console `give`. */
  async waitForItem(id, ms = 5000) {
    const name = id.replace(/^minecraft:/, '');
    const deadline = Date.now() + ms;
    while (!this.bot.inventory.items().some((i) => i.name === name)) {
      if (Date.now() > deadline) throw new Error(`${this.name} was never given ${name}`);
      await ticks(1);
    }
  }

  /** Right-clicks with what is in hand once: throws a snowball, egg, pearl, potion or charge. */
  async use() {
    this.bot.activateItem();
    await ticks(1);
  }

  /**
   * Draws and looses: a bow (or trident) held for `hold` ticks, then released. A crossbow is loaded
   * the same way and then fired with a second use.
   */
  async drawAndLoose(hold = 25, { crossbow = false } = {}) {
    this.bot.activateItem();
    await ticks(hold);
    this.bot.deactivateItem();
    if (crossbow) {
      await ticks(2);
      this.bot.activateItem();
      await ticks(1);
      this.bot.deactivateItem();
    }
  }

  /** Drops one of the held stack (Q), as a player drops an item into a gate. */
  async dropOne() {
    await this.bot.toss(this.bot.heldItem.type, null, 1);
  }

  // ---- saddles --------------------------------------------------------------------------------

  /** The client's copy of the entity nearest a server-tagged entity (tags stay server-side). */
  async clientEntity(tag, within = 2) {
    const p = await this.entityByTag(tag);
    if (!p) return null;
    let best = null;
    for (const e of Object.values(this.bot.entities)) {
      if (e === this.bot.entity) continue;
      const d = e.position.distanceTo(new Vec3(p.x, p.y, p.z));
      if (d <= within && (!best || d < best.d)) best = { e, d };
    }
    return best ? best.e : null;
  }

  /** Right-clicks an entity to get on it, as a player mounts; resolves once seated. An animal with
   * its AI on can step away between the look and the click, so a few tries are allowed. */
  async mount(tag, ms = 5000, tries = 1) {
    for (let i = 1; ; i++) {
      try {
        return await this.mountOnce(tag, ms);
      } catch (err) {
        if (i >= tries) throw err;
        await ticks(10);
      }
    }
  }

  async mountOnce(tag, ms) {
    const target = await this.clientEntity(tag);
    if (!target) {
      const p = await this.entityByTag(tag);
      const seen = Object.values(this.bot.entities).filter((e) => e !== this.bot.entity && p && e.position.distanceTo(new Vec3(p.x, p.y, p.z)) < 8)
        .map((e) => `${e.name}@${e.position.distanceTo(new Vec3(p.x, p.y, p.z)).toFixed(1)}`);
      throw new Error(`${this.name} cannot see ${tag} to mount (server has it at ${p ? `${p.x.toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)}` : 'nowhere'}; near it: ${seen.join(', ') || 'nothing'})`);
    }
    const seated = waitEvent(this.bot, 'mount', () => true, ms, `a seat on ${target.name}`);
    await this.look({ x: target.position.x, y: target.position.y + 1, z: target.position.z });
    this.bot.mount(target);
    await seated;
    return target;
  }

  /** Gets off by console (`ride ... dismount`, 1.19.4+): Mineflayer's own dismount jumps from 1.21.2. */
  async dismount() {
    if (!this.bot.vehicle) return;
    const off = maybeEvent(this.bot, 'dismount', () => true, 5000);
    const r = await this.srv.run(`ride ${this.name} dismount`);
    if (r.errors.length) throw new Error(`ride ${this.name} dismount: ${r.errors.join(' ')}`);
    await off;
  }

  /**
   * Steers the ridden entity or boat in a straight line, as a client does: a vehicle_move each
   * tick, `speed` blocks per tick, until within 0.3 of the point, or until the server moves the
   * vehicle itself (a gate), which ends the drive. Returns 'arrived' or 'moved'.
   */
  async drive(point, { speed = 0.25, ms = 20000 } = {}) {
    const bot = this.bot;
    const vehicle = bot.vehicle;
    if (!vehicle) throw new Error(`${this.name} is not riding anything`);
    const pos = vehicle.position.clone();
    const target = new Vec3(point.x, pos.y, point.z);
    const yaw = Math.atan2(-(target.x - pos.x), target.z - pos.z) * (180 / Math.PI);
    let moved = false;
    const onServerMove = () => { moved = true; };
    bot._client.on('vehicle_move', onServerMove);
    const onEntityMove = (e) => { if (e === vehicle && e.position.distanceTo(pos) > 2) moved = true; };
    bot.on('entityMoved', onEntityMove);
    const wasPhysics = bot.physicsEnabled;
    bot.physicsEnabled = false;
    const end = Date.now() + ms;
    try {
      while (!moved && Date.now() < end) {
        const d = target.minus(pos);
        const flat = Math.hypot(d.x, d.z);
        if (flat <= 0.3) return 'arrived';
        const step = Math.min(speed, flat);
        pos.x += (d.x / flat) * step;
        pos.z += (d.z / flat) * step;
        const packet = { x: pos.x, y: pos.y, z: pos.z, yaw, pitch: 0, onGround: true };
        bot._client.write('vehicle_move', packet);
        bot._client.write('look', { yaw, pitch: 0, onGround: false, flags: { onGround: false } });
        // Physics is off while driving, so ticks are counted by the clock (one packet per 50 ms).
        await new Promise((resolve) => { setTimeout(resolve, 50); });
      }
      if (!moved) throw new Error(`${this.name} did not reach ${point.x} ${point.z} riding ${vehicle.name}`);
      return 'moved';
    } finally {
      bot._client.off('vehicle_move', onServerMove);
      bot.off('entityMoved', onEntityMove);
      bot.physicsEnabled = wasPhysics;
    }
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

module.exports = { Probe, join, waitEvent, maybeEvent, shownText, ticks };
