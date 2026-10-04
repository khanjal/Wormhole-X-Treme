'use strict';
// The facility at run time: one server, one Probe. Prepares the world (gamerules, forceload),
// builds every wing from the datapack, keeps the boards, pylons and fault counter, runs and
// resets chambers through the contract in chambers/index.js, and serves the chat console.

const campus = require('./lib/campus');
const server = require('./lib/server');
const text = require('./lib/text');
const blueprint = require('./lib/blueprint');
const { Boards, RunBar, FaultCounter } = require('./lib/board');
const { Config } = require('./lib/config');
const { Probe, join } = require('./lib/probe');
const { FacilityConsole, normaliseOptions } = require('./lib/console');
const chambers = require('./chambers');
const wings = require('./wings');
const { Menagerie } = require('./lib/menagerie');
const { Watch } = require('./lib/observe');
const { Transit } = require('./lib/transit');
const { Logbook } = require('./lib/logbook');
const { companionFault } = require('./lib/companions');
const { Groups, GROUPS, BASELINE: BASELINE_GROUP } = require('./lib/groups');
const { httpText } = require('./lib/http');
const { TAG: WATCHER_TAG, MARKER: WATCHER_MARKER } = require('./lib/watcher');

const BOT = 'Probe';
/** The Config owner of the facility's baseline settings, held for the whole session. */
const BASELINE_OWNER = 'facility baseline';

function clock() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

class Facility {
  /** `srv` is a started Server; `manifest` is what lib/generate.js wrote. */
  constructor({ srv, version, manifest, port, log = console.log, fixed = [], companions = null, mapPort = null }) {
    Object.assign(this, { srv, version, manifest, port, log, mapPort });
    // The companion plugins installed (--with), or null for a run without --with at all.
    this.companions = companions;
    const plugins = (companions || []).map((c) => c.plugin);
    this.boards = new Boards(srv, version);
    this.config = new Config(srv);
    this.faults = new FaultCounter(srv, { fixed, extra: plugins.length ? (line) => companionFault(line, plugins) : null });
    this.afterRestart = [];
    this.restarting = false;
    this.entries = chambers.entries();
    this.status = {};
    this.last = {};
    this.held = new Set(); // chambers whose Stage left settings applied until their Reset
    this.queue = Promise.resolve();
    this.bars = [];
    this.menagerie = new Menagerie(srv, version);
    this.watch = new Watch(srv);
    this.transit = new Transit(this);
    this.keepRings = new Set(); // the transit pair: permanent, never taken down by a chamber
    this.logbook = new Logbook(this);
    this.fixed = fixed; // the issues this plugin jar carries fixes for (--fixed): the Logbook's KNOWN
  }

  // ---- world -------------------------------------------------------------------------------

  /** Gamerules by version (one name each, never both), then forceload and wait for the chunks. */
  async prepare() {
    await this.srv.prepareFence();
    const problems = [];
    // Paper keeps gamerules per world: the nether and the End need them too (a ghast spawned in
    // the Range, with mob spawning off only in the overworld, fireballed Probe2 on a transit trip).
    for (const dim of [campus.OVERWORLD, campus.NETHER, campus.END]) {
      for (const rule of ['daylight', 'weather', 'mobSpawning', 'patrols', 'traders', 'commandBlockOutput', 'logAdminCommands',
        'fallDamage', 'fireDamage', 'drowningDamage', 'freezeDamage']) {
        const cmd = `execute in ${dim} run gamerule ${server.gameruleName(this.version, rule)} false`;
        const r = await this.srv.run(cmd);
        if (r.errors.length) problems.push(`${cmd}: ${r.errors.join(' ')}`);
      }
    }
    for (const c of ['time set 6000', 'weather clear', `setworldspawn ${Math.floor(campus.TRANSIT.home.x)} 0 ${Math.floor(campus.TRANSIT.home.z)}`]) {
      const r = await this.srv.run(c);
      if (r.errors.length) problems.push(`${c}: ${r.errors.join(' ')}`);
    }
    const t0 = Date.now();
    for (const f of campus.FORCELOAD) {
      const r = await this.srv.run(`execute in ${f.dim} run forceload add ${f.from[0]} ${f.from[1]} ${f.to[0]} ${f.to[1]}`);
      if (r.errors.length) problems.push(`forceload ${f.why}: ${r.errors.join(' ')}`);
    }
    await this.waitForceloaded();
    this.loadMs = Date.now() - t0;
    try {
      const { restored, refused } = await this.config.recover();
      if (restored.length) this.log(`  put back settings a killed run left changed: ${restored.join(', ')}`);
      for (const r of refused) {
        problems.push(`${Config.JOURNAL} held ${r}; dropped it from the journal, so set that setting by hand if it matters`);
      }
    } catch (e) {
      problems.push(`cannot put back the settings in ${Config.JOURNAL}, kept for the next start: ${e.message}`);
    }
    await this.watch.prepare();
    // The facility's baseline settings (campus.BASELINE), journalled like a chamber's and put
    // back at close, so a killed run's baseline is put back by the next --keep-world start.
    for (const [name, value] of Object.entries(campus.BASELINE)) {
      try {
        await this.config.set(name, value, BASELINE_OWNER);
      } catch (e) { problems.push(`baseline ${name}: ${e.message}`); }
    }
    return problems;
  }

  /** Waits until every forceloaded chunk is loaded (forceload is asynchronous, and kept by a restart). */
  async waitForceloaded() {
    for (const f of campus.FORCELOAD) {
      const y = f.dim === campus.OVERWORLD ? 0 : 64;
      // Every chunk of the rectangle, not only its corners: a build writing into a chunk still
      // loading fails silently.
      const points = [];
      for (let cx = Math.floor(f.from[0] / 16); cx <= Math.floor(f.to[0] / 16); cx++) {
        for (let cz = Math.floor(f.from[1] / 16); cz <= Math.floor(f.to[1] / 16); cz++) points.push([cx * 16 + 8, y, cz * 16 + 8]);
      }
      await this.srv.waitLoaded(f.dim, points, 120000);
    }
  }

  /** True if the block at `at` in `dim` is `block`. */
  async isBlock(dim, at, block) {
    const r = await this.srv.run(`execute in ${dim} if block ${at.join(' ')} ${block}`);
    return r.lines.some((l) => /Test passed/.test(l));
  }

  /** Runs one generated function and confirms its sentinel; returns { ok, ms, detail }. */
  async runFunction(f) {
    const t0 = Date.now();
    const r = await this.srv.run(`execute in ${f.dim} run function ${f.id}`, 120000);
    const ms = Date.now() - t0;
    const sentinel = await this.isBlock(f.dim, f.sentinel.at, f.sentinel.block);
    const ok = r.errors.length === 0 && sentinel;
    return { ok, ms, detail: r.errors.join(' ') || (sentinel ? 'sentinel set' : `sentinel missing at ${f.sentinel.at.join(' ')}`) };
  }

  /** Builds every wing in order; returns [{ fn, ok, ms, detail, blocks, commands }]. */
  async build() {
    const out = [];
    for (const f of this.manifest.functions.filter((x) => x.fn.startsWith('build/'))) {
      const r = await this.runFunction(f);
      out.push({ fn: f.fn, blocks: f.blocks, commands: f.commands, ...r });
      this.log(`  ${r.ok ? 'built' : 'FAILED'} ${f.fn}: ${f.commands} commands, ${f.blocks} blocks, ${r.ms} ms${r.ok ? '' : ` — ${r.detail}`}`);
    }
    return out;
  }

  /**
   * Whether a volume is all air: fills its air with structure void and counts, then puts the air
   * back. Exact, works in any dimension, and needs no reference region. Returns { ok, air, volume }.
   */
  async isClear(dim, box) {
    let air = 0;
    let vol = 0;
    for (const p of blueprint.split(box)) {
      const v = blueprint.volume(p);
      vol += v;
      const coords = `${p.x0} ${p.y0} ${p.z0} ${p.x1} ${p.y1} ${p.z1}`;
      const r = await this.srv.run(`execute in ${dim} run fill ${coords} minecraft:structure_void replace minecraft:air`);
      const m = r.lines.map((l) => /filled (\d+) block/i.exec(l)).find(Boolean);
      const n = m ? Number(m[1]) : 0;
      air += n;
      // While the air is still void, name what else is there: something short-lived (fire,
      // flowing liquid) is gone by the time a slower search looks.
      if (n < v) (this.strays || (this.strays = [])).push(...await this.nameStrays(dim, coords));
      if (n) await this.srv.run(`execute in ${dim} run fill ${coords} minecraft:air replace minecraft:structure_void`);
    }
    const out = { ok: air === vol, air, volume: vol };
    if (!out.ok) {
      out.found = [...(this.strays || []), ...await this.locateSolid(dim, box)];
      this.strays = [];
    }
    return out;
  }

  /** Counts of likely stray blocks in a box, by turning each kind into void and counting it. */
  async nameStrays(dim, coords) {
    const kinds = ['cave_air', 'void_air', 'fire', 'soul_fire', 'lava', 'water', 'netherrack', 'gravel', 'soul_sand',
      'basalt', 'blackstone', 'magma_block', 'weeping_vines', 'weeping_vines_plant', 'twisting_vines', 'twisting_vines_plant',
      'crimson_nylium', 'warped_nylium', 'glowstone', 'end_stone', 'chorus_plant', 'chorus_flower', 'glass'];
    const out = [];
    for (const k of kinds) {
      const r = await this.srv.run(`execute in ${dim} run fill ${coords} minecraft:structure_void replace minecraft:${k}`);
      const m = r.lines.map((l) => /filled (\d+) block/i.exec(l)).find(Boolean);
      if (m) out.push(`${k} x${m[1]}`);
    }
    return out;
  }

  /** Up to `max` non-air blocks in a box, by halving it: "x y z name" for the report. */
  async locateSolid(dim, box, max = 3) {
    const found = [];
    const visit = async (b) => {
      if (found.length >= max) return;
      const r = await this.isClearCount(dim, b);
      if (r === blueprint.volume(b)) return;
      if (blueprint.volume(b) === 1) {
        const at = `${b.x0} ${b.y0} ${b.z0}`;
        const names = ['lava', 'fire', 'soul_fire', 'water', 'netherrack', 'magma_block', 'glass', 'structure_void', 'end_stone', 'chorus_plant', 'chorus_flower', 'obsidian'];
        let name = 'unknown';
        for (const n of names) if (await this.isBlock(dim, [b.x0, b.y0, b.z0], `minecraft:${n}`)) { name = n; break; }
        found.push(`${at} ${name}`);
        return;
      }
      const spans = [['x', b.x1 - b.x0], ['y', b.y1 - b.y0], ['z', b.z1 - b.z0]].sort((p, q) => q[1] - p[1]);
      const axis = spans[0][0];
      const mid = Math.floor((b[`${axis}0`] + b[`${axis}1`]) / 2);
      await visit({ ...b, [`${axis}1`]: mid });
      await visit({ ...b, [`${axis}0`]: mid + 1 });
    };
    for (const p of blueprint.split(box)) await visit(p);
    return found;
  }

  async isClearCount(dim, b) {
    const coords = `${b.x0} ${b.y0} ${b.z0} ${b.x1} ${b.y1} ${b.z1}`;
    const r = await this.srv.run(`execute in ${dim} run fill ${coords} minecraft:structure_void replace minecraft:air`);
    const m = r.lines.map((l) => /filled (\d+) block/i.exec(l)).find(Boolean);
    const n = m ? Number(m[1]) : 0;
    if (n) await this.srv.run(`execute in ${dim} run fill ${coords} minecraft:air replace minecraft:structure_void`);
    return n;
  }

  /**
   * After the build: stock the Menagerie, then let each chamber with a `fixture` set up what it
   * keeps for the whole session (the Relay gate, the shape gallery). Returns [{ id, ok, detail }].
   * Design mode builds them unstocked (`stock: false`): no animals in a designer's world.
   */
  async fixtures({ stock = true } = {}) {
    const out = [];
    // Whatever the far worlds spawned before their mob spawning was turned off.
    for (const [dim, types] of [[campus.NETHER, ['ghast', 'blaze', 'magma_cube', 'zombified_piglin', 'piglin', 'piglin_brute', 'hoglin', 'wither_skeleton', 'skeleton', 'enderman', 'strider']],
      [campus.END, ['enderman', 'shulker', 'endermite']]]) {
      for (const t of types) await this.srv.run(`execute in ${dim} run kill @e[type=minecraft:${t}]`);
    }
    // And the animals a plains chunk is generated with, which no gamerule stops (spawn-animals
    // cannot be off: before 1.21 it discards a summoned animal too). Nothing is stocked yet, so
    // every one is a stray that could wander into a lane.
    let strays = 0;
    for (const t of ['sheep', 'pig', 'chicken', 'cow', 'horse', 'donkey', 'rabbit']) {
      const r = await this.srv.run(`execute in ${campus.OVERWORLD} run kill @e[type=minecraft:${t}]`);
      const m = r.lines.map((l) => /Killed (\d+)/.exec(l)).find(Boolean);
      if (m) strays += Number(m[1]);
      else if (r.lines.some((l) => /^Killed /.test(l))) strays++;
    }
    out.push({ id: 'strays', ok: true, detail: `${strays} animals from chunk generation killed` });
    if (stock) {
      try {
        await this.menagerie.stock();
        out.push({ id: 'menagerie', ok: true, detail: 'stocked' });
      } catch (e) { out.push({ id: 'menagerie', ok: false, detail: e.message }); }
    }
    for (const e of this.entries.filter((x) => x.chamber && x.chamber.fixture)) {
      try {
        const detail = await e.chamber.fixture(this.makeCtx(e));
        out.push({ id: e.def.id, ok: true, detail: detail || 'set up' });
      } catch (err) { out.push({ id: e.def.id, ok: false, detail: err.message }); }
    }
    // The transit routes last: the Ops gate's console dials the far gates made above.
    try {
      out.push({ id: 'transit', ok: true, detail: await this.transit.build() });
    } catch (err) { out.push({ id: 'transit', ok: false, detail: err.message }); }
    return out;
  }

  /** Whether a chamber's cell holds a fixture that lives for the whole session. */
  holdsFixture(id) {
    const e = this.entries.find((x) => x.def.id === id);
    return Boolean(e && e.chamber && e.chamber.fixture);
  }

  // ---- people --------------------------------------------------------------------------------

  /**
   * Players in the facility do not go hungry or get hurt: infinite Saturation and Resistance 255,
   * particles hidden (`infinite` is from 1.19.4). Given on join, and every five seconds to anyone
   * missing either (a death or a bucket of milk clears them, and a respawn or a world change
   * would otherwise go unnoticed): only to them, since giving an effect a player already has
   * logs "Unable to apply this effect" (the predicates are the datapack's, lib/generate.js).
   * Mobs are not given it: a cell's mob still takes damage.
   */
  async shield(who = '@a') {
    const sel = who === '@a' ? '@a[' : `@a[name=${who},`;
    for (const [p, effect] of [['saturated', 'saturation'], ['resistant', 'resistance']]) {
      const r = await this.srv.run(`execute as ${sel}predicate=!wx:${p}] run effect give @s minecraft:${effect} infinite 255 true`);
      if (!r.errors.length) continue;
      // Without the facility's predicates (a datapack not loaded), give it to everyone named, as before them.
      const plain = await this.srv.run(`effect give ${who} minecraft:${effect} infinite 255 true`);
      if (plain.errors.length) throw new Error(`the players' shield: ${r.errors.join(' ')} / ${plain.errors.join(' ')}`);
    }
  }

  startShielding() {
    if (this.shieldTimer) return;
    this.shieldTimer = setInterval(() => { this.shield().catch(() => {}); }, 5000);
  }

  async connectProbe() {
    const bot = await join({ port: this.port, version: this.version, username: BOT });
    this.probe = new Probe(bot, this.srv);
    for (const c of [`op ${BOT}`, `gamemode creative ${BOT}`]) await this.srv.run(c);
    await this.shield(BOT);
    this.startShielding();
    await this.probe.teleport(campus.TRANSIT.home);
    return this.probe;
  }

  /** Probe leaves, its shield stopped: design mode, once the fixtures are built (the caller deops it). */
  async dismissProbe() {
    if (this.shieldTimer) { clearInterval(this.shieldTimer); this.shieldTimer = null; }
    if (this.probe) { this.probe.gone = 'dismissed'; this.probe.bot.quit(); }
    this.probe = null;
  }

  /**
   * A second body for the tests that need two (a ring swap, a private pair, a player's beam
   * place, a cooldown an op would skip): Probe2, never opped, in adventure mode. Joined on first
   * use and kept for the session.
   */
  async second() {
    if (this.probe2 && this.probe2.bot.entity && !this.probe2.gone) return this.probe2;
    if (this.probe2 && this.probe2.gone) this.log(`  Probe2 rejoins (it left: ${this.probe2.gone})`);
    const bot = await join({ port: this.port, version: this.version, username: 'Probe2' });
    this.probe2 = new Probe(bot, this.srv);
    const p2 = this.probe2;
    bot.on('kicked', (reason) => { p2.gone = `kicked: ${typeof reason === 'string' ? reason : JSON.stringify(reason)}`; });
    bot.on('end', (reason) => { p2.gone = p2.gone || `disconnected: ${reason}`; });
    await this.srv.run('deop Probe2');
    await this.srv.run('gamemode adventure Probe2');
    // With a permissions plugin, a player holds only the nodes given: Probe2 is a visitor, the
    // player the rest of the facility means (a mirror, for one, needs a gate's use nodes).
    if (this.has('luckperms')) await this.joinGroup('Probe2', BASELINE_GROUP, { quiet: true });
    await this.shield('Probe2');
    await this.probe2.teleport(campus.TRANSIT.home);
    return this.probe2;
  }

  /** Starts the console and greets everyone who joins from now on. */
  async openConsole() {
    const wingList = campus.WINGS;
    this.console = new FacilityConsole({
      srv: this.srv, version: this.version, bot: this.probe.bot, wings: wingList, entries: this.entries,
      status: (id) => this.status[id],
      handlers: {
        run: (player, e, action) => this.request(player, e, action),
        watch: (player, e) => this.seat(player, e),
        go: (player, w) => this.go(player, w),
        transit: (player, action) => this.transitAction(player, action),
        book: (player) => this.giveBook(player),
        group: (player, group) => this.joinGroup(player, group),
      },
      groups: this.has('luckperms') ? [...Object.entries(GROUPS).map(([id, g]) => ({ id, why: g.why })),
        { id: 'default', why: 'none of them: only what every player has' }] : null,
      // A watcher's click would run a chamber or change a menu under the self-test.
      ignores: (player) => this.watching(player),
    });
    await this.console.start();
    // Once: a restart makes a new console (it reads the new Probe's packets) but the log is the same.
    if (this.greeting) return;
    this.greeting = true;
    this.srv.on('line', (line) => {
      const m = /: (\w+) joined the game/.exec(line);
      // A watcher (lib/watcher.js) is made one there instead: no adventure mode, atrium or Logbook.
      if (m && m[1] !== BOT && m[1] !== 'Probe2' && !this.watching(m[1])) this.welcome(m[1]).catch((e) => this.log(`  welcome ${m[1]}: ${e.message}`));
    });
  }

  /** Whether a player is a watcher of a watched self-test (lib/watcher.js). */
  watching(player) {
    return Boolean(this.watcher && this.watcher.is(player));
  }

  /** Puts a player in a tester group (lib/groups.js), making the groups the first time. */
  async joinGroup(player, group, { quiet = false } = {}) {
    const groups = new Groups(this.srv);
    if (!this.groupsReady) { await groups.ensure(); this.groupsReady = true; }
    await groups.put(player, group);
    if (quiet) return;
    const why = GROUPS[group] ? GROUPS[group].why : 'none of the tester groups';
    await this.console.tell(player, [{ text: `You are in ${group} now: `, color: 'white' }, { text: why, color: 'gray' },
      { text: '. An op passes every Wormhole check whatever the group.', color: 'dark_gray' }]);
  }

  async welcome(player) {
    const h = campus.TRANSIT.home;
    await this.srv.run(`gamemode adventure ${player}`);
    // A watched self-test that was killed (lib/watcher.js) leaves its tag in the player's data and its
    // leash marker in the world, either of which would take this tester's plates and pads away.
    await this.srv.run(`tag ${player} remove ${WATCHER_TAG}`);
    if (!this.watcher) await this.srv.run(`kill @e[type=minecraft:marker,tag=${WATCHER_MARKER}]`);
    await this.shield(player);
    await this.srv.run(`execute in ${campus.OVERWORLD} run tp ${player} ${h.x} ${h.y} ${h.z} ${h.yaw} 0`);
    await this.console.greet(player);
    await this.logbook.give(player);
  }

  /** `!book` and the console's Logbook button: a fresh copy, in its slot or a free one. */
  async giveBook(player) {
    const slot = await this.logbook.give(player);
    if (slot !== null) await this.console.tell(player, [{ text: 'Your Logbook is up to date.', color: 'gray' }]);
  }

  /** A Transit tab action for `player`: the console forms the Gate Room's buttons run. */
  async transitAction(player, action) {
    const say = async (cmd) => (await this.srv.run(cmd)).lines.map((l) => l.replace(/§./g, '')).join(' / ') || 'no answer';
    let words;
    if (action.startsWith('dial ')) words = await say(`wormhole gate dial Ops ${action.slice(5)}`);
    else if (action === 'beam lab') words = await say(`wormhole beam admin send ${player} BeamLab`);
    else if (action === 'beam home') words = await say(`wormhole beam admin send ${player} Atrium`);
    else {
      const lines = this.transit.routes().map((r) => {
        const s = this.transit.status[r.id];
        return `${r.id}: ${s ? `${s.ok ? 'PASS' : 'FAIL'} at ${s.time} · ${s.detail}` : 'not walked this session'}`;
      });
      for (const l of lines) await this.console.tell(player, [{ text: `  ${l}`, color: 'gray' }]);
      return;
    }
    await this.console.tell(player, [{ text: `${action}: `, color: 'white' }, { text: words, color: 'gray' }]);
  }

  async go(player, w) {
    const e = w.entrance;
    await this.srv.run(`execute in ${w.dim} run tp ${player} ${e.x} ${e.y} ${e.z} ${e.yaw} 0`);
  }

  /** Takes a player to a chamber's gallery seat (the console's Watch; not `this.watch`, the Watch helper). */
  async seat(player, e) {
    const L = blueprint.layoutOf(e.def);
    const s = L.seat;
    const dim = campus.wing(e.def.wing).dim;
    await this.srv.run(`execute in ${dim} run tp ${player} ${s.x} ${s.y} ${s.z} ${s.yaw} ${s.pitch || 0}`);
  }

  // ---- boards -------------------------------------------------------------------------------

  opsWallSpec() {
    const lines = [{ text: 'OPS WALL', color: 'white', bold: true }];
    let later = 0;
    for (const e of this.entries) {
      if (!e.chamber) { later++; continue; }
      const s = this.status[e.def.id];
      const colour = !s ? 'gray' : { pass: 'green', fail: 'red', running: 'yellow' }[s.state] || 'gray';
      lines.push('\n', { text: `${e.def.id.toUpperCase()} ${e.def.title} · `, color: 'white' },
        { text: s ? `${s.state.toUpperCase()}${s.line ? ` · ${s.line}` : ''}` : 'never run', color: colour });
    }
    lines.push('\n', { text: `${later} more chambers built, awaiting their tests`, color: 'dark_gray' });
    lines.push(...this.transit.wallLines());
    return lines;
  }

  async refreshOpsWall() {
    await this.boards.set('opswall', this.opsWallSpec());
  }

  chamberSpec(e) {
    const w = campus.wing(e.def.wing);
    const s = this.status[e.def.id];
    const colour = !s ? 'gray' : { pass: 'green', fail: 'red', running: 'yellow', refused: 'gold', staged: 'aqua' }[s.state] || 'gray';
    const opts = normaliseOptions(e.chamber ? e.chamber.options : {});
    const settings = opts.map((o) => `${o.name} ${o.values[e.values[o.name] || 0].label}`).join(' · ');
    return [{ text: `${e.def.id.toUpperCase()} `, color: w.text, bold: true }, { text: e.def.title, color: 'white', bold: true },
      '\n', { text: settings, color: 'gray' },
      '\n', { text: s ? `${s.state.toUpperCase()}${s.line ? ` · ${s.line}` : ''} · ${s.time}` : 'idle · never run', color: colour }];
  }

  async refreshBoards() {
    await this.boards.set('opswall', this.opsWallSpec());
    await this.faults.show(this.boards);
    for (const e of this.entries.filter((x) => x.chamber)) await this.boards.set(e.def.id, this.chamberSpec(e));
  }

  async setStatus(e, state, line) {
    this.status[e.def.id] = { state, line, time: clock() };
    const L = blueprint.layoutOf(e.def);
    const pylon = { pass: 'pass', fail: 'fail', running: 'running', refused: 'idle', staged: 'running' }[state] || 'idle';
    await this.boards.pylon(L.pylon, pylon, campus.wing(e.def.wing).dim);
    await this.boards.set(e.def.id, this.chamberSpec(e));
    for (const m of campus.BOARD_MIRRORS[e.def.id] || []) await this.boards.set(m, this.chamberSpec(e)).catch(() => {});
    await this.boards.set('opswall', this.opsWallSpec());
  }

  // ---- chambers -------------------------------------------------------------------------------

  /** Queues a console request so runs never overlap (one Probe, one run at a time). */
  request(player, e, action) {
    const job = this.queue.then(async () => {
      if (action === 'reset') {
        const r = await this.resetChamber(e);
        await this.console.tell(player, [{ text: `${e.def.id.toUpperCase()} reset: `, color: 'white' },
          { text: r.ok ? 'as built' : `NOT CLEAN · ${r.problems[0]}`, color: r.ok ? 'green' : 'red' }]);
        return;
      }
      const values = action === 'again' && this.last[e.def.id] ? this.last[e.def.id] : { ...e.values };
      const r = await this.runChamber(e, { values, mode: action === 'stage' ? 'stage' : 'run', by: player });
      await this.console.tell(player, [{ text: `${e.def.id.toUpperCase()} ${e.def.title}: `, color: 'white' },
        { text: `${r.outcome}${r.reason ? ` · ${r.reason}` : ''}`, color: { PASS: 'green', FAIL: 'red', REFUSED: 'gold', STAGED: 'aqua' }[r.outcome] }]);
    });
    this.queue = job.catch((err) => this.log(`  ${e.def.id} ${action}: ${err.stack || err}`));
    return this.queue;
  }

  /** Option indices to the values a chamber sees: { option: value }. */
  valuesOf(e, indices) {
    const out = {};
    for (const o of normaliseOptions(e.chamber.options)) out[o.name] = o.values[indices[o.name] || 0].value;
    return out;
  }

  makeCtx(e) {
    const def = e.def;
    return {
      server: this.srv, probe: this.probe, config: this.config, board: this.boards, version: this.version,
      tag: `wx_run_${def.id}`, observed: {}, layout: blueprint.layoutOf(def), step: () => {}, owner: def.id,
      menagerie: this.menagerie, watch: this.watch, facility: this,
    };
  }

  /**
   * Runs one chamber: refuse, or reset the cell, apply its settings, stage, (run, check), and
   * put the settings back. `values` are option indices, or option values if `raw`. A Run of a
   * chamber that is staged is refused until its Reset. Returns
   * { outcome: PASS | FAIL | REFUSED | STAGED, reason, checks }.
   */
  async runChamber(e, { values = {}, raw = false, mode = 'run', holdMs = 3000, by = null, settings = {} } = {}) {
    const result = await this.runChamberOnce(e, { values, raw, mode, holdMs, settings });
    // In the Logbook, and every holder's copy replaced with one that has it.
    this.logbook.record(e, raw ? values : this.valuesOf(e, values), result, by);
    await this.logbook.refresh();
    return result;
  }

  async runChamberOnce(e, { values = {}, raw = false, mode = 'run', holdMs = 3000, settings = {} } = {}) {
    const ch = e.chamber;
    // A run would reset the cell and put its settings back under a stage a person is using.
    if (mode !== 'stage' && this.held.has(e.def.id)) {
      return { outcome: 'REFUSED', reason: 'staged for a person: Reset it first', checks: [] };
    }
    const v = raw ? values : this.valuesOf(e, values);
    if (!raw) this.last[e.def.id] = { ...values };
    const refusal = ch.refuses ? ch.refuses(v, this.version, this) : null;
    if (refusal) {
      await this.setStatus(e, 'refused', refusal);
      return { outcome: 'REFUSED', reason: refusal, checks: [] };
    }
    const ctx = this.makeCtx(e);
    // Where a run's time goes, for the report: ms per phase.
    const timing = {};
    let mark = Date.now();
    const lap = (phase) => { timing[phase] = Date.now() - mark; mark = Date.now(); };
    const bar = new RunBar(this.srv, this.version, e.def.id, `${e.def.id.toUpperCase()} ${e.def.title}`, 6);
    this.bars = this.bars.filter((b) => !b.closed);
    this.bars.push(bar);
    ctx.step = (name) => bar.advance(name);
    await bar.open();
    await this.setStatus(e, 'running', mode === 'stage' ? 'staging' : 'running');
    let result;
    let staged = false;
    try {
      await bar.advance('resetting the cell');
      if (ch.cleanup) await ch.cleanup(ctx);
      lap('cleanup');
      const rf = this.resetFunction(e);
      const reset = rf ? await this.runFunction(rf) : { ok: true };
      lap('reset');
      if (!reset.ok) throw Object.assign(new Error(`reset before staging: ${reset.detail}`), { phase: 'fixture' });
      await this.srv.run(`kill @e[tag=${ctx.tag}]`);
      // The reset empties the cell, fixture and all (G2's gallery): put the fixture back, or the
      // run dials gates whose frames and floor are gone.
      if (ch.fixture) {
        try { await ch.fixture(ctx); } catch (err) { err.phase = 'fixture'; throw err; }
        lap('fixture');
      }
      await bar.advance('applying settings');
      const needs = ch.needs ? ch.needs(v) : {};
      // A matrix cell's own `settings` (a setting's effect on an ordinary run) on top of the chamber's.
      await this.config.apply({ ...(needs.config || {}), ...settings }, e.def.id);
      try {
        await ch.stage(ctx, v);
      } catch (err) { err.phase = 'fixture'; throw err; }
      lap('stage');
      if (mode === 'stage') {
        staged = true;
        this.held.add(e.def.id);
        await this.setStatus(e, 'staged', 'yours: Reset when done');
        await bar.finish(true, 'staged', holdMs);
        return { outcome: 'STAGED', reason: null, checks: [] };
      }
      try {
        await ch.run(ctx, v);
      } catch (err) { err.phase = 'trip'; throw err; }
      lap('run');
      await bar.advance('checking');
      const checks = [];
      for (const c of ch.checks(ctx, v)) {
        let ok = false;
        try { ok = Boolean(await c.test()); } catch (err) { ok = false; c.error = err.message; }
        checks.push({ name: c.name, ok, error: c.error });
      }
      // Shut by command: once its checks are read, a chamber whose purpose is not the shutdown
      // timeout closes its gates with the plugin's own command rather than leaving them to time
      // out, and the end state is checked after it (chambers' `shut`, returning more checks).
      if (ch.shut) {
        let after;
        try { after = (await ch.shut(ctx, v)) || []; } catch (err) { after = [{ name: 'its gates were shut by command', test: async () => { throw err; } }]; }
        for (const c of after) {
          let ok = false;
          try { ok = Boolean(await c.test()); } catch (err) { ok = false; c.error = err.message; }
          checks.push({ name: c.name, ok, error: c.error });
        }
      }
      const failed = checks.find((c) => !c.ok);
      result = failed ? { outcome: 'FAIL', reason: failed.name, checks } : { outcome: 'PASS', reason: null, checks };
    } catch (err) {
      result = { outcome: 'FAIL', reason: `${err.phase === 'fixture' ? 'fixture failed' : 'trip failed'}: ${err.message}`, checks: [] };
    } finally {
      // A stage that got as far as being staged holds its settings until its Reset; anything
      // else, a failed stage included, puts them back now.
      if (!staged) await this.config.restore(e.def.id);
    }
    lap('checks');
    result.timing = timing;
    await this.setStatus(e, result.outcome === 'PASS' ? 'pass' : 'fail', result.reason || 'all checks true');
    await bar.finish(result.outcome === 'PASS', result.outcome === 'PASS' ? 'PASS' : `FAIL · ${result.reason}`, holdMs);
    this.lastCtx = { ctx, v, e };
    return result;
  }

  resetFunction(e) {
    return this.manifest.functions.find((f) => f.fn === `reset/${e.def.id}`);
  }

  /**
   * Resets a chamber's cell and proves it: the sentinel, every clear volume is air, and each of
   * the last run's checks now reads its `afterReset` value. Returns { ok, problems }.
   */
  async resetChamber(e) {
    const problems = [];
    const f = this.resetFunction(e);
    if (e.chamber && e.chamber.cleanup) {
      try { await e.chamber.cleanup(this.makeCtx(e)); } catch (err) { problems.push(`cleanup: ${err.message}`); }
    }
    // A desk has no cell and no reset function: its cleanup is all of its reset.
    if (!f) {
      await this.srv.run(`kill @e[tag=wx_run_${e.def.id}]`);
      if (this.held.delete(e.def.id)) await this.config.restore(e.def.id);
      return { ok: problems.length === 0, problems };
    }
    const r = await this.runFunction(f);
    if (!r.ok) problems.push(r.detail);
    await this.srv.run(`kill @e[tag=wx_run_${e.def.id}]`);
    if (this.held.delete(e.def.id)) await this.config.restore(e.def.id);
    if (e.chamber && e.chamber.fixture) {
      // A fixture's cell is not left empty: put the fixture back and let it say it is whole.
      try { await e.chamber.fixture(this.makeCtx(e)); } catch (err) { problems.push(`fixture: ${err.message}`); }
      return { ok: problems.length === 0, problems };
    }
    const build = this.manifest.functions.find((x) => x.clear.some((c) => c.id === e.def.id));
    for (const c of build ? build.clear.filter((x) => x.id === e.def.id) : []) {
      const clear = await this.isClear(f.dim, c.box);
      if (!clear.ok) problems.push(`${clear.volume - clear.air} of ${clear.volume} blocks in the cell are not air`);
    }
    if (e.chamber && this.lastCtx && this.lastCtx.e === e) {
      const { ctx, v } = this.lastCtx;
      for (const c of e.chamber.checks(ctx, v)) {
        const expect = c.afterReset === undefined ? false : c.afterReset;
        if (expect === null) continue;
        let got;
        try { got = Boolean(await c.test()); } catch { got = false; }
        if (got !== expect) problems.push(`after reset, "${c.name}" is ${got}`);
      }
    }
    if (e.chamber) await this.setStatus(e, 'idle', problems.length ? 'reset NOT clean' : 'reset');
    return { ok: problems.length === 0, problems };
  }

  // ---- companions ------------------------------------------------------------------------------

  /** True if the run installed this companion (--with); false in a run without it, or without --with. */
  has(name) {
    return Boolean(this.companions && this.companions.some((c) => c.name === name));
  }

  /**
   * Restarts the server on the same world, for a setting the plugin reads only at enable (a
   * companion's switch, a map layer). Probe and Probe2 leave with it and Probe comes back; the
   * chunks are waited for again and the console listens through the new Probe. The plugin's
   * gates, rings, beams and mirrors are its own saved state, and the facility's settings journal
   * and baseline carry on as they were. Chambers read `ctx.facility.probe` after one, not
   * `ctx.probe`. Returns the index in the server's log where the new start begins.
   */
  async restart(why = 'a restart') {
    this.restarting = true;
    let from;
    try {
      if (this.shieldTimer) { clearInterval(this.shieldTimer); this.shieldTimer = null; }
      for (const p of [this.probe, this.probe2]) {
        if (p) { p.gone = why; p.bot.quit(); }
      }
      this.probe2 = null;
      const t0 = Date.now();
      await this.srv.restart();
      from = this.srv.startIndex;
      await this.srv.prepareFence();
      await this.waitForceloaded();
      await this.connectProbe();
      await this.openConsole();
      if (this.watcher) await this.watcher.afterRestart();
      this.log(`  restarted the server for ${why} in ${((Date.now() - t0) / 1000).toFixed(1)} s`);
    } finally {
      this.restarting = false;
    }
    for (const f of this.afterRestart) f();
    return from;
  }

  /**
   * Whether Dynmap's web map came up on this server's own port (8123 + port - 25590): its log
   * says the web server started there, not that it failed to bind, and it answers. Looks at the
   * log from `from` (a restart's start). Returns { ok, detail }.
   */
  async mapWebUp({ from = 0, ms = 120000 } = {}) {
    const port = this.mapPort;
    // "[dynmap] Web server started on address 0.0.0.0:8193": the port compared as text, not as a pattern.
    const started = { test: (l) => /\[dynmap\] .*[Ww]eb ?server started on /.test(l) && l.trim().endsWith(`:${port}`) };
    const failed = /\[dynmap\].*(Failed to start|Address already in use|BindException|Error starting)/i;
    const seen = () => this.srv.log.slice(from).find((l) => started.test(l) || failed.test(l));
    const deadline = Date.now() + ms;
    let line = seen();
    while (!line && Date.now() < deadline) {
      await new Promise((resolve) => { setTimeout(resolve, 500); });
      line = seen();
    }
    if (!line) return { ok: false, detail: `Dynmap never said its web server started on port ${port}` };
    if (failed.test(line)) return { ok: false, detail: line };
    try {
      const body = await httpText(`http://127.0.0.1:${port}/up/configuration`);
      if (!/"worlds"/.test(body)) return { ok: false, detail: `http://127.0.0.1:${port}/up/configuration answered without Dynmap's configuration` };
    } catch (e) {
      return { ok: false, detail: `http://127.0.0.1:${port}/ does not answer: ${e.message}` };
    }
    return { ok: true, detail: `http://127.0.0.1:${port}/ (${line.replace(/^\[[^\]]*\]: /, '')})` };
  }

  async close() {
    if (this.shieldTimer) clearInterval(this.shieldTimer);
    this.shieldTimer = null;
    await this.config.restore(BASELINE_OWNER).catch(() => {});
    for (const b of this.bars) await b.close().catch(() => {});
    if (this.watcher) this.watcher.close();
    if (this.probe) this.probe.bot.quit();
    if (this.probe2) this.probe2.bot.quit();
  }
}

module.exports = { Facility, BOT };
