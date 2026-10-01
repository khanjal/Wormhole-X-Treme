'use strict';
// Gates for chambers: build one flush with the floor by the console form, dial, wait for the
// opening, shut an iris at its lever, take it down. Every fact about the plugin used here is
// from its source (see lib/shapes.js for geometry); every message matched is the plugin's own.
//
//   gate build <shape> <name> <world> <x> <y> <z> <facing> [net=] [idc=]   (x y z: the DHD holder)
//   gate dial <from> <to> [idc]     -> "Stargates connected." (sent before the opening is open)
//   gate force <gate>               -> closes, darkens, opens its iris and keeps it open
//   gate remove <gate> -destroy     -> the frame and chevrons become air
//   gate edit <gate> idc <code|-clear>, custom true, portal <MAT>, group <G>, spin <p>
//
// The opening stays AIR on the server: the portal and iris are drawn to each client. So "the
// gate is open" is judged by what Probe's client is shown in the opening cells.

const campus = require('./campus');
const shapes = require('./shapes');
const { ticks } = require('./probe');

const WORLDS = { [campus.OVERWORLD]: 'world', [campus.NETHER]: 'world_nether', [campus.END]: 'world_the_end' };
const FLOORS = { [campus.OVERWORLD]: 'minecraft:smooth_quartz', [campus.NETHER]: 'minecraft:polished_blackstone', [campus.END]: 'minecraft:purpur_block' };

/** Strips section-sign colour codes from a plugin message. */
function plain(line) {
  return line.replace(/§./g, '');
}

class GateKit {
  constructor(srv) {
    this.srv = srv;
  }

  async say(command) {
    const r = await this.srv.run(command);
    return { ...r, text: r.lines.map(plain).join(' / ') };
  }

  /**
   * Where a gate of `shape` goes so that its opening is centred on (cx, cz) in the plane
   * `openingAt` (the opening's coordinate along F) with arrivals' feet on `floorY`.
   */
  place(shape, facing, { cx, openingAt, floorY = 0 }) {
    const g0 = shapes.geometry(shape, { x: 0, y: 0, z: 0 }, facing);
    const y = floorY - g0.feetOffset;
    const along = g0.normal.x !== 0 ? 'x' : 'z';
    const across = along === 'x' ? 'z' : 'x';
    const holder = { x: 0, y, z: 0 };
    holder[across] = Math.round(cx - (g0.centre[across] - 0.5));
    holder[along] = Math.round(openingAt - g0.opening[0][along]);
    return shapes.geometry(shape, holder, facing);
  }

  /**
   * Builds a gate flush with the floor: clears the floor under its grid, builds, then puts the
   * floor back everywhere the gate did not take (the opening of a flat gate stays open).
   * Returns { geom, text } or throws with the plugin's refusal.
   */
  async build(name, geom, { dim = campus.OVERWORLD, net = null, idc = null, floorY = 0 } = {}) {
    await this.clearSite(geom, { dim, floorY });
    const h = geom.holder;
    const opts = [net ? `net=${net}` : '', idc ? `idc=${idc}` : ''].filter(Boolean).join(' ');
    const r = await this.say(`wormhole gate build ${geom.name} ${name} ${WORLDS[dim]} ${h.x} ${h.y} ${h.z} ${geom.facing} ${opts}`.trim());
    await this.restoreSite(geom, { dim, floorY });
    if (!/Built /.test(r.text)) throw new Error(`gate build ${name}: ${r.text || 'no answer'}`);
    if (idc) await this.say(`wormhole gate edit ${name} idc ${idc}`);
    return { geom, text: r.text };
  }

  /** The grid, the DHD button one out in front of it, and any `extra` points, as one box. */
  siteBox(geom, extra = []) {
    const g = geom.bounds;
    const pts = [geom.button, ...extra];
    return {
      x0: Math.min(g.x0, ...pts.map((p) => p.x)), x1: Math.max(g.x1, ...pts.map((p) => p.x)),
      y0: Math.min(g.y0, ...pts.map((p) => p.y)), y1: Math.max(g.y1, ...pts.map((p) => p.y)),
      z0: Math.min(g.z0, ...pts.map((p) => p.z)), z1: Math.max(g.z1, ...pts.map((p) => p.z)),
    };
  }

  /**
   * Makes room for a gate flush with the floor: air through the floor under its grid and button
   * (a gate whose holder sits under the floor has its bottom row there). `extra` points are
   * cleared too (where a builder stands).
   */
  async clearSite(geom, { dim = campus.OVERWORLD, floorY = 0, extra = [], cellsOnly = false } = {}) {
    if (cellsOnly) {
      // Only the gate's own cells (and `extra`): a builder by hand needs the floor round them to
      // place against, as a player building in a trench has.
      // The redstone markers too: the plugin lays its wire and lever only in air.
      const cells = [...geom.frame, ...geom.opening, geom.button, ...(geom.sign ? [geom.sign] : []), ...Object.values(geom.redstone || {}), ...extra];
      for (const c of cells.filter((p) => p.y < floorY)) await this.srv.run(`execute in ${dim} run setblock ${c.x} ${c.y} ${c.z} minecraft:air`);
      return;
    }
    const b = this.siteBox(geom, extra);
    await this.srv.run(`execute in ${dim} run fill ${b.x0} ${Math.min(b.y0, floorY - 1)} ${b.z0} ${b.x1} ${floorY - 1} ${b.z1} minecraft:air`);
  }

  /**
   * Puts the floor back under and around a gate: stone below the top layer, the floor block on
   * top, wherever the gate did not take; a flat gate's opening and a lever under the floor stay
   * open.
   */
  async restoreSite(geom, { dim = campus.OVERWORLD, floorY = 0, extra = [] } = {}) {
    const b = this.siteBox(geom, extra);
    await this.srv.run(`execute in ${dim} run fill ${b.x0} ${Math.min(b.y0, floorY - 2)} ${b.z0} ${b.x1} ${floorY - 2} ${b.z1} minecraft:stone replace minecraft:air`);
    await this.srv.run(`execute in ${dim} run fill ${b.x0} ${floorY - 1} ${b.z0} ${b.x1} ${floorY - 1} ${b.z1} ${FLOORS[dim]} replace minecraft:air`);
    for (const c of geom.opening.filter((p) => p.y < floorY)) await this.srv.run(`execute in ${dim} run setblock ${c.x} ${c.y} ${c.z} minecraft:air`);
    if (geom.lever && geom.lever.y < floorY) await this.srv.run(`execute in ${dim} run setblock ${geom.lever.x} ${geom.lever.y} ${geom.lever.z} minecraft:air`);
  }

  async exists(name) {
    const r = await this.say('wormhole gate list');
    return r.text.split(/[,/:]/).map((s) => s.trim()).includes(name);
  }

  async remove(name) {
    await this.say(`wormhole gate force ${name}`);
    return this.say(`wormhole gate remove ${name} -destroy`);
  }

  async dial(from, to, idc = null) {
    return this.say(`wormhole gate dial ${from} ${to}${idc ? ` ${idc}` : ''}`);
  }

  async force(name) {
    return this.say(`wormhole gate force ${name}`);
  }

  async edit(name, field, value) {
    return this.say(`wormhole gate edit ${name} ${field} ${value}`);
  }

  /**
   * Waits until Probe's client is shown something other than air in every opening cell (the
   * drawn portal), then for the woosh in front to clear, then 25 ticks more: the plugin only
   * follows projectiles launched after its once-a-second "is any gate open" check.
   * Returns the block name drawn at the opening's centre.
   */
  async waitOpen(probe, geom, ms = 15000) {
    const bot = probe.bot;
    const { Vec3 } = require('vec3');
    const shown = (p) => { const b = bot.blockAt(new Vec3(p.x, p.y, p.z)); return b ? b.name : null; };
    const deadline = Date.now() + ms;
    while (!geom.opening.every((c) => { const n = shown(c); return n && n !== 'air' && n !== 'cave_air'; })) {
      if (Date.now() > deadline) {
        const seen = [...new Set(geom.opening.map(shown))].join(', ');
        throw new Error(`the opening of ${geom.name} was never drawn (Probe is shown: ${seen})`);
      }
      await ticks(2);
    }
    const mid = geom.opening[Math.floor(geom.opening.length / 2)];
    const front = { x: mid.x + geom.normal.x, y: mid.y, z: mid.z + geom.normal.z };
    const wooshEnd = Date.now() + 5000;
    while (!['air', 'cave_air', null].includes(shown(front)) && Date.now() < wooshEnd) await ticks(2);
    await ticks(25);
    return shown(mid);
  }

  /**
   * Whether the plugin draws this gate's opening to the probe at all: same world, within its
   * VISUAL_RADIUS (64, StargateBlockSetup), measured as it does, from the feet to the corner of the
   * opening's first portal cell, with a margin. Beyond it an end is never drawn, open or shut, so
   * there is nothing to judge.
   */
  static drawnTo(probe, geom, dim) {
    const c = geom.opening[0];
    return probe.dimension === dim && Math.hypot(probe.position.x - c.x, probe.position.y - c.y, probe.position.z - c.z) <= 60;
  }

  /**
   * Waits (bounded) until Probe's client is shown something other than air in the middle of a
   * gate's opening: drawn open (portal or iris). "Shut" is only judged of a gate seen open first,
   * or an opening never drawn at all would pass. A probe that has just arrived is not shown the
   * opening at once. Null if the opening is not in Probe's view at all.
   */
  async waitDrawn(probe, geom, ms = 8000) {
    const { Vec3 } = require('vec3');
    const mid = geom.opening[Math.floor(geom.opening.length / 2)];
    const shown = () => { const b = probe.bot.blockAt(new Vec3(mid.x, mid.y, mid.z)); return b ? b.name : null; };
    if (shown() === null) return null;
    const deadline = Date.now() + ms;
    for (;;) {
      if (!['air', 'cave_air'].includes(shown())) return true;
      if (Date.now() > deadline) return false;
      await ticks(2);
    }
  }

  /**
   * Waits (bounded) until Probe's client is shown air in every opening cell of a gate: shut, its
   * portal and iris gone. Null if the opening is not in Probe's view at all (another world, or
   * too far), so a caller only judges a gate it can see.
   */
  async waitShut(probe, geom, ms = 5000) {
    const { Vec3 } = require('vec3');
    const shown = (p) => { const b = probe.bot.blockAt(new Vec3(p.x, p.y, p.z)); return b ? b.name : null; };
    if (geom.opening.some((c) => shown(c) === null)) return null;
    const deadline = Date.now() + ms;
    for (;;) {
      if (geom.opening.every((c) => ['air', 'cave_air'].includes(shown(c)))) return true;
      if (Date.now() > deadline) return false;
      await ticks(2);
    }
  }

  /** Right-clicks a gate's iris lever as Probe (who is opped); returns the lever's new state. */
  async toggleIris(probe, geom, dim = campus.OVERWORLD) {
    const l = geom.lever;
    const stand = { x: l.x + geom.normal.x * 2 + 0.5, y: Math.max(l.y + 1, geom.arrival.y), z: l.z + geom.normal.z * 2 + 0.5 };
    await probe.teleport(stand, dim);
    const now = await probe.click(l);
    return now.getProperties().powered;
  }
}

module.exports = { GateKit, WORLDS, FLOORS, plain };
