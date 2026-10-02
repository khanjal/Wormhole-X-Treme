'use strict';
// Building a gate as a player does, for G1 (how built: preview, hand) and G4 (the Build Bench).
// Every fact is from the plugin's source (Build, GatePreviews, GateBlueprint, PreviewPlacer,
// GateInteractionHandler, Complete, StargateHelper):
//
//  - `gate build <shape> [group]` (a player's form: fewer than four words) remembers the shape and
//    stands a preview: in front of the player (the nearest cardinal of their yaw), its DHD block
//    straight ahead, its button in the player's own column, its bottom row level with their feet
//    (GateBlueprint.inFrontOf). The preview is block displays only that player sees; nothing in
//    the world changes. "Previewing <Shape> in <Group>. Build inside it, then press a real button
//    on its DHD."
//  - `gate preview <action>` works on the preview the player looks at (or stands inside):
//    `needs` lists what to lay, "<n> <material> - <n left | done>"; `guide` marks it and says
//    "<Shape> is built! Press its button to check it." once every cell but a dial sign is right;
//    `place` lays it ("Placed <Shape>.") and offers the gate as a button press would.
//  - A frame is recognised only when a button on its DHD is pressed: "Valid Stargate Design!"
//    (or "Valid Sign Nav Stargate Design!"), then `gate complete <name> [idc=] [net=]` makes it
//    ("Gate successfully constructed."). A dial sign is any wall sign on the front of the :D
//    block, and may be hung after the press but before `complete`.
//
// The site is cleared through the floor first (lib/gatekit.js clearSite), so a gate stands flush
// with the floor exactly where the console form would have put it, and put back after.

const { Vec3 } = require('vec3');
const campus = require('./campus');
const { GateKit } = require('./gatekit');
const { ticks } = require('./probe');

/** Each group's frame and chevron blocks (config.yml, and the Diamond group Lab.shape makes). */
const GROUPS = {
  Standard: { frame: 'obsidian', chevron: 'redstone_lamp' },
  Atlantis: { frame: 'lapis_block', chevron: null },
  Universe: { frame: 'polished_blackstone', chevron: null },
  MilkyWay: { frame: 'deepslate', chevron: null },
  Diamond: { frame: 'diamond_block', chevron: 'redstone_lamp' },
};

const SCAFFOLD = 'white_wool';

function plain(line) {
  return line.replace(/§./g, '');
}

/** Parses `gate preview needs`: [{ count, material, left }] (left: 0 when "done"). */
function parseNeeds(said) {
  return [...said.matchAll(/(\d+) ([a-z_]+(?: or [a-z_]+)?) - (?:(\d+) left|done)/g)]
    .map((m) => ({ count: Number(m[1]), material: m[2], left: m[3] === undefined ? 0 : Number(m[3]) }));
}

class GateBuilder {
  /** `probe` is the builder (an op, in creative); everything it is told is kept in `heard`. */
  constructor(srv, probe) {
    this.srv = srv;
    this.probe = probe;
    this.kit = new GateKit(srv);
    this.heard = [];
    this.listener = (m) => { const s = plain(m.toString()); if (!s.startsWith('[Server:')) this.heard.push(s); };
  }

  listen() {
    this.probe.bot.on('message', this.listener);
  }

  stop() {
    this.probe.bot.off('message', this.listener);
  }

  /** Probe says a player command; returns what it was told until `until` matched (or `ms` passed). */
  async ask(command, { ms = 3000, until = null } = {}) {
    const mark = this.heard.length;
    this.probe.bot.chat(command);
    const end = Date.now() + ms;
    while (Date.now() < end && !(until && this.heard.slice(mark).some((h) => until.test(h)))) await ticks(2);
    return this.heard.slice(mark).join(' / ');
  }

  /** Waits (bounded) for a line matching `re` from `mark` on; returns it or null. */
  async hear(re, mark, ms) {
    const end = Date.now() + ms;
    for (;;) {
      const h = this.heard.slice(mark).find((x) => re.test(x));
      if (h || Date.now() > end) return h || null;
      await ticks(2);
    }
  }

  /**
   * Where a builder stands for `gate build` to stand its preview on `geom`: GateBlueprint's
   * inFrontOf run backwards. The DHD block is `ahead` blocks in front of them (one, or more
   * when the shape has blocks nearer than the DHD's layer), the bottom row level with their feet.
   */
  standPoint(geom) {
    const h = geom.holder;
    const F = geom.normal;
    const along = (p) => (p.x - h.x) * F.x + (p.z - h.z) * F.z;
    const ahead = 1 + Math.max(0, ...geom.blocks.map(along));
    return { x: h.x + F.x * ahead + 0.5, y: geom.bounds.y0, z: h.z + F.z * ahead + 0.5, yaw: (geom.yaw + 180) % 360 };
  }

  /** The builder's column (feet and head), cleared with the site so it can stand level with row 0. */
  standCells(geom) {
    const s = this.standPoint(geom);
    return [0, 1].map((dy) => ({ x: Math.floor(s.x), y: s.y + dy, z: Math.floor(s.z) }));
  }

  /** `gate build <shape> [group]` from the stand point; returns what was said. */
  async preview(geom, group, { dim = campus.OVERWORLD } = {}) {
    // Any preview left from before would be the one a later action finds first.
    await this.ask('/wormhole gate preview clear -all', { until: /Cleared|error/ });
    await this.probe.teleport(this.standPoint(geom), dim);
    await ticks(5);
    const said = await this.ask(`/wormhole gate build ${geom.name}${group ? ` ${group}` : ''}`, { until: /Previewing|error|No shape|has no group/ });
    // An action works on the preview looked at: a flat one lies below the builder's eye.
    await this.probe.face(geom.centre);
    return said;
  }

  /**
   * By preview: stand, `gate build`, `gate preview place`, hang a dial sign if the shape has one,
   * `gate complete`. Returns { said: {...}, ok }.
   */
  async byPreview(name, geom, { group = null, idc = null, net = null, dim = campus.OVERWORLD, floorY = 0 } = {}) {
    const said = {};
    const extra = this.standCells(geom);
    await this.kit.clearSite(geom, { dim, floorY, extra, cellsOnly: true });
    this.listen();
    try {
      said.build = await this.preview(geom, group, { dim });
      const mark = this.heard.length;
      said.place = await this.ask('/wormhole gate preview place', { ms: 5000, until: /Nothing placed|no gate was found|error ::|Type '\/wormhole complete/ });
      said.offer = await this.hear(/Valid (Sign Nav )?Stargate Design/, mark, 3000);
      if (geom.sign) said.sign = await this.hangSign(geom, dim);
      said.complete = await this.complete(name, geom, { idc, net, dim, floorY });
    } catch (e) {
      e.said = said;
      throw e;
    } finally {
      this.stop();
      await this.kit.restoreSite(geom, { dim, floorY, extra });
    }
    if (idc) await this.kit.edit(name, 'idc', idc);
    return { said, ok: /successfully constructed/.test(said.complete || '') };
  }

  /**
   * By hand: stand, `gate build`, read `needs`, turn the guide on, lay every block of the frame as
   * a player (below first; a wool scaffold where a block has nothing to be placed against, taken
   * away after), the button on the DHD, and the dial sign; wait for the guide's "is built!", press
   * the button, `gate complete`. Returns { said, laid: { material: n }, needs, ok }.
   */
  async byHand(name, geom, { group = 'Standard', idc = null, net = null, dim = campus.OVERWORLD, floorY = 0, lenient = false } = {}) {
    const said = {};
    const laid = {};
    const extra = this.standCells(geom);
    const mats = GROUPS[group] || GROUPS.Standard;
    await this.kit.clearSite(geom, { dim, floorY, extra, cellsOnly: true });
    this.listen();
    try {
      said.build = await this.preview(geom, group, { dim });
      said.needs = await this.ask('/wormhole gate preview needs', { ms: 1500 });
      said.guide = await this.ask('/wormhole gate preview guide', { until: /Guide on|Guide off|error/ });
      const mark = this.heard.length;
      // What goes where, as `needs` lists it: frame cells in the frame block, chevron cells ([C] and
      // [S:C]) in the chevron block. `lenient` lays the first [S:C] in the frame block instead,
      // which detection takes too.
      let first = lenient;
      const plan = geom.blocks.map((b) => {
        let block = mats.frame;
        if (b.role === 'chevron') block = mats.chevron || mats.frame;
        if (b.role === 'either') {
          block = (first || !mats.chevron) ? mats.frame : mats.chevron;
          first = false;
        }
        return { ...b, block };
      });
      const cells = new Set(geom.blocks.map((b) => `${b.x},${b.y},${b.z}`));
      const opening = new Set(geom.opening.map((b) => `${b.x},${b.y},${b.z}`));
      const scaffolds = [];
      const todo = [...plan].sort((a, b) => a.y - b.y);
      while (todo.length) {
        let i = todo.findIndex((b) => this.probe.supportOf(b));
        if (i < 0) {
          // Nothing left can be placed against anything (a top row over the opening): a scaffold
          // beside the lowest block that has room for one, in a cell the gate does not use,
          // standing on something.
          const free = (p) => !cells.has(`${p.x},${p.y},${p.z}`) && !opening.has(`${p.x},${p.y},${p.z}`) && this.probe.supportOf(p);
          let spot = null;
          for (const [k, b] of todo.entries()) {
            spot = [[0, -1, 0], [1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, 1, 0]].map(([dx, dy, dz]) => ({ x: b.x + dx, y: b.y + dy, z: b.z + dz })).find(free);
            if (spot) { i = k; break; }
          }
          if (!spot) throw new Error(`nowhere to put a scaffold for the blocks left: ${todo.map((b) => `${b.x} ${b.y} ${b.z}`).join(', ')}`);
          await this.lay(spot, SCAFFOLD, geom, dim, floorY);
          scaffolds.push(spot);
        }
        const b = todo.splice(i, 1)[0];
        await this.lay(b, b.block, geom, dim, floorY);
        laid[b.block] = (laid[b.block] || 0) + 1;
      }
      for (const s of scaffolds) await this.srv.run(`execute in ${dim} run setblock ${s.x} ${s.y} ${s.z} minecraft:air`);
      // The button on the DHD's front, and the dial sign on the :D block's, from in front of them.
      await this.probe.teleport(this.pressPoint(geom, floorY), dim);
      await this.lay(geom.button, 'stone_button', geom, dim, floorY, { against: geom.holder });
      laid.button = 1;
      if (geom.sign) {
        said.sign = await this.hangSign(geom, dim);
        laid.sign = 1;
      }
      said.built = await this.hear(/is built! Press its button/, mark, 8000);
      await this.probe.teleport(this.pressPoint(geom, floorY), dim);
      const pressMark = this.heard.length;
      await this.probe.press(geom.button);
      said.offer = await this.hear(/Valid (Sign Nav )?Stargate Design/, pressMark, 4000);
      said.complete = await this.complete(name, geom, { idc, net, dim, floorY });
    } catch (e) {
      e.said = said;
      throw e;
    } finally {
      this.stop();
      await this.kit.restoreSite(geom, { dim, floorY, extra });
    }
    if (idc) await this.kit.edit(name, 'idc', idc);
    return { said, laid, needs: parseNeeds(said.needs || ''), ok: /successfully constructed/.test(said.complete || '') };
  }

  /** A point on the floor in front of the DHD, two out, facing it: where a builder presses the button. */
  pressPoint(geom, floorY = 0) {
    const k = geom.button;
    return { x: k.x + 0.5 + geom.normal.x * 2, y: floorY, z: k.z + 0.5 + geom.normal.z * 2, yaw: (geom.yaw + 180) % 360 };
  }

  /**
   * Lays one block as Probe: within reach (a spot two out in front of it along the gate's facing,
   * on the floor), holding it, placed against its support (or `against`, for a button).
   */
  async lay(p, block, geom, dim, floorY, { against = null } = {}) {
    const eye = () => this.probe.position.offset(0, 1.62, 0);
    const centre = new Vec3(p.x + 0.5, p.y + 0.5, p.z + 0.5);
    const here = this.probe.position.floored();
    const standingIn = (q) => here.x === q.x && here.z === q.z && q.y >= here.y && q.y <= here.y + 1;
    // Straight in front of the block's column: a placement clicked from more than a block to the
    // side of it was dropped by the server now and then.
    const across = Math.abs((here.x - p.x) * geom.right.x + (here.z - p.z) * geom.right.z);
    if (eye().distanceTo(centre) > 4.2 || standingIn(p) || across > 1) await this.probe.teleport(this.spotFor(p, geom, floorY), dim);
    await this.probe.wield(block);
    const support = against
      ? { ref: this.probe.bot.blockAt(new Vec3(against.x, against.y, against.z)), face: new Vec3(p.x - against.x, p.y - against.y, p.z - against.z) }
      : this.probe.supportOf(p);
    if (!support || !support.ref) throw new Error(`nothing to place ${block} against at ${p.x} ${p.y} ${p.z}`);
    if (process.env.WX_BUILD_TRACE) console.log(`lay ${block} at ${p.x} ${p.y} ${p.z} against ${support.ref.name} ${support.ref.position} face ${support.face} from ${this.probe.position} holding ${this.probe.bot.heldItem ? this.probe.bot.heldItem.name : 'nothing'}`);
    try {
      return await this.probe.placeAgainst(p, support);
    } catch (e) {
      // Once more, from the next spot along: counted, so a run that needed it says so.
      this.retries = (this.retries || 0) + 1;
      if (process.env.WX_BUILD_TRACE) console.log(`  failed (${e.message}); again from another spot`);
      await this.probe.teleport(this.spotFor(p, geom, floorY, 1), dim);
      return this.probe.placeAgainst(p, against ? support : this.probe.supportOf(p));
    }
  }

  /**
   * Where a builder stands to lay the block at `p`: on the floor in front of it along the gate's
   * facing (two out, then three, one, four), or a column either side, never in or on a cell of
   * the gate, and within reach.
   */
  spotFor(p, geom, floorY, skip = 0) {
    const taken = new Set([...geom.frame, ...geom.opening, geom.button, ...(geom.sign ? [geom.sign] : [])].map((c) => `${c.x},${c.y},${c.z}`));
    const F = geom.normal;
    const R = geom.right;
    let skipped = 0;
    // A flat gate lies in the floor along its facing: its middle is reached from beside or behind.
    for (const d of [2, 3, 1, 4, -1, -2, 0, -3]) {
      for (const s of [0, 1, -1, 2, -2, 3, -3, 4, -4]) {
        const x = p.x + F.x * d + R.x * s;
        const z = p.z + F.z * d + R.z * s;
        if ([floorY - 1, floorY, floorY + 1].some((y) => taken.has(`${x},${y},${z}`))) continue;
        const spot = { x: x + 0.5, y: floorY, z: z + 0.5, yaw: (geom.yaw + 180) % 360 };
        if (new Vec3(spot.x, spot.y + 1.62, spot.z).distanceTo(new Vec3(p.x + 0.5, p.y + 0.5, p.z + 0.5)) > 4.5) continue;
        if (skipped++ >= skip) return spot;
      }
    }
    throw new Error(`nowhere within reach to stand to lay the block at ${p.x} ${p.y} ${p.z}`);
  }

  /** Hangs a wall sign on the front of the shape's :D block, as a player places one. */
  async hangSign(geom, dim) {
    const d = { x: geom.sign.x - geom.normal.x, y: geom.sign.y, z: geom.sign.z - geom.normal.z };
    await this.lay(geom.sign, 'oak_sign', geom, dim, geom.sign.y, { against: d });
    return this.probe.bot.blockAt(new Vec3(geom.sign.x, geom.sign.y, geom.sign.z)).name;
  }

  /** `gate complete <name> [idc=] [net=]`, from the floor in front of the DHD. */
  async complete(name, geom, { idc = null, net = null, dim = campus.OVERWORLD, floorY = 0 } = {}) {
    await this.probe.teleport(this.pressPoint(geom, floorY), dim);
    const opts = [idc ? `idc=${idc}` : '', net ? `net=${net}` : ''].filter(Boolean).join(' ');
    return this.ask(`/wormhole gate complete ${name}${opts ? ` ${opts}` : ''}`, { ms: 4000, until: /successfully constructed|error ::|Please click/ });
  }

  /** Clears every preview Probe has (a run's cleanup). */
  async clearPreviews() {
    this.listen();
    try {
      return await this.ask('/wormhole gate preview clear -all', { until: /Cleared|error/ });
    } finally {
      this.stop();
    }
  }
}

module.exports = { GateBuilder, GROUPS, parseNeeds };
