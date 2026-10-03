'use strict';
// Watch mode (run-facility --selftest --watch): a person stands in the lab and watches the
// self-test. The run waits for them; then before each chamber's cells they are moved to its
// vantage point (campus.WATCH), told which cell is running and what to look for, and told its
// PASS, FAIL or KNOWN after.
//
// A watcher must change no result, so every person who joins while it runs is one: in spectator
// mode, tagged wx_watcher (which the facility's own @p selectors leave out), never welcomed,
// given no Logbook, and refused by the console. The plugin counts any player near a mirror, in a
// ring or in a gate's opening, so a watcher is held within campus.WATCH_LEASH of a vantage point
// chosen clear of all of them: a tick function (`wx:watcher`) puts them back on a marker there.
// One who leaves is let go; the run carries on, and takes them back if they return.

const campus = require('./campus');
const text = require('./text');
const { normaliseOptions } = require('./console');

const TAG = 'wx_watcher';
const MARKER = 'wx_vantage';
/** The facility's own players: never a watcher. */
const BOTS = new Set(['Probe', 'Probe2', 'Tester']);
/** The stand-in's name (--watch-bot). */
const STAND_IN = 'Watcher';
const sleep = (ms) => new Promise((resolve) => { setTimeout(resolve, ms); });

/** The leash, as a datapack tick function (lib/generate.js); it does nothing while nobody is a watcher. */
function functions() {
  return {
    watcher: [
      '# wx:watcher: holds a watcher near the vantage marker; see scripts/facility/lib/watcher.js',
      `execute as @a[tag=${TAG}] at @s unless entity @e[type=minecraft:marker,tag=${MARKER},distance=..${campus.WATCH_LEASH}] run tp @s @e[type=minecraft:marker,tag=${MARKER},limit=1]`,
      '',
    ].join('\n'),
  };
}

/** A vantage point with the yaw and pitch that face its `at` from a standing eye. */
function facing(v) {
  const dx = v.at.x - v.x;
  const dz = v.at.z - v.z;
  const dy = v.at.y - (v.y + 1.62);
  const yaw = (-Math.atan2(dx, dz) * 180) / Math.PI;
  const pitch = (-Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
  // `|| 0`: no -0 in a command.
  return { ...v, yaw: Math.round(yaw * 10) / 10 || 0, pitch: Math.round(pitch * 10) / 10 || 0 };
}

/** The vantage for a chamber id, or the sections' one. */
function vantage(id) {
  return facing(campus.WATCH[id] || campus.WATCH.sections);
}

/** "traveller horse: Probe rides a horse in" for each option a cell sets away from its default. */
function settingLines(chamber, values) {
  const out = [];
  for (const o of normaliseOptions(chamber.options)) {
    const v = o.values.find((x) => x.value === values[o.name]);
    if (!v || v === o.values[0]) continue;
    out.push(`${o.name} ${v.label}${v.why ? `: ${v.why}` : ''}`);
  }
  return out;
}

class Watcher {
  /** `name`: the one to wait for (null: whoever joins first); `standIn`: join a bot as them (--watch-bot). */
  constructor(fac, { name = null, standIn = false, log = console.log } = {}) {
    Object.assign(this, { fac, srv: fac.srv, version: fac.version, wanted: standIn ? STAND_IN : name, standIn, log });
    this.present = new Set();
    this.current = null;
    this.moves = 0;
    this.misses = 0;
    this.srv.on('line', (line) => {
      const j = /: (\w+) joined the game/.exec(line);
      if (j && this.claims(j[1])) this.adopt(j[1]).catch((e) => this.log(`  watcher ${j[1]}: ${e.message}`));
      const l = /: (\w+) left the game/.exec(line);
      if (l && this.present.delete(l[1])) this.log(`  watcher ${l[1]} left; the self-test carries on`);
    });
  }

  /** Every person who joins while the self-test runs is a watcher, the facility's bots never. */
  claims(name) {
    return !BOTS.has(name);
  }

  /** Whether `name` is a watcher: the console ignores them. */
  is(name) {
    return this.claims(name);
  }

  /** Waits until the watcher (or, unnamed, anyone) has joined and been made a watcher; false if the server went first. */
  async arrive() {
    const who = this.wanted || 'any name';
    this.log(`\nwatch: join localhost:${this.fac.port} with Minecraft ${this.version} as ${who}; the self-test starts when you are in.`);
    this.log('  You are put in spectator mode and moved to each chamber in turn; the cells take no notice of you.');
    if (this.standIn) await this.joinStandIn();
    // Minecraft names are not case-sensitive: --watch yourname waits for YourName too.
    const wanted = this.wanted && this.wanted.toLowerCase();
    const ready = () => (wanted ? [...this.present].some((n) => n.toLowerCase() === wanted) : this.present.size > 0);
    while (!ready()) {
      if (this.srv.exited !== null && this.srv.exited !== undefined) return false;
      await sleep(250);
    }
    return true;
  }

  /** The stand-in: a client that joins as a person would and stays where it is put. */
  async joinStandIn() {
    const { join } = require('./probe');
    let first = true;
    this.bot = await join({
      port: this.fac.port, version: this.version, username: STAND_IN,
      // No physics, from the start: a person's movement is their own business, and a falling bot is not one standing still.
      onCreate: (b) => {
        b.physicsEnabled = false;
        // The first cell line it is told, as its client shows it: proof the text arrives whole on this version.
        b.on('message', (m) => { const t = m.toString(); if (first && t.startsWith('▶')) { first = false; this.log(`  ${STAND_IN} was told: ${t}`); } });
      },
    });
    this.bot.physicsEnabled = false;
  }

  /** Makes `name` a watcher: spectator, tagged, night vision for the dark cells, and held at a vantage point at once. */
  async adopt(name) {
    // The facility's welcome (adventure, the atrium, the Logbook) runs from the same log line and is
    // skipped for a watcher (Facility.openConsole).
    for (const c of [`gamemode spectator ${name}`, `tag ${name} add ${TAG}`, `effect give ${name} minecraft:night_vision infinite 0 true`]) {
      await this.srv.run(c);
    }
    this.present.add(name);
    this.log(`  watcher ${name} joined: a spectator, tagged ${TAG}`);
    await this.tell(name, [{ text: 'You are watching the facility self-test. ', color: 'aqua' },
      { text: 'You are a spectator the cells take no notice of, held near each chamber\'s vantage point while its cells run. ', color: 'gray' },
      { text: 'Look, but do not click Probe or Probe2: spectating one carries you into the cell with it.', color: 'gold' }]);
    // One who joins before the self-test starts is held over the atrium, not left at the world spawn.
    if (this.current) await this.move(name, this.current);
    else await this.goTo(vantage('sections'));
  }

  async tell(name, spec) {
    await this.srv.run(`tellraw ${name} ${text.command(this.version, spec)}`);
  }

  async tellAll(spec) {
    for (const n of this.present) await this.tell(n, spec);
  }

  async move(name, v) {
    await this.srv.run(`execute in ${v.dim} run tp ${name} ${v.x} ${v.y} ${v.z} ${v.yaw} ${v.pitch}`);
  }

  /** Puts the leash marker at a vantage point and every watcher there. */
  async goTo(v) {
    if (this.current && this.current.dim === v.dim && this.current.x === v.x && this.current.y === v.y && this.current.z === v.z) return;
    this.current = v;
    await this.srv.run(`kill @e[type=minecraft:marker,tag=${MARKER}]`);
    await this.srv.run(`execute in ${v.dim} run summon minecraft:marker ${v.x} ${v.y} ${v.z} {Tags:["${MARKER}"],Rotation:[${v.yaw}f,${v.pitch}f]}`);
    for (const n of this.present) await this.move(n, v);
    if (this.bot && this.present.has(STAND_IN)) await this.standInThere(v);
  }

  /** The stand-in's own client must see itself a spectator at the vantage point; says so if not. */
  async standInThere(v) {
    const bot = this.bot;
    const there = () => bot.entity && bot.game.gameMode === 'spectator' && bot.entity.position.distanceTo({ x: v.x, y: v.y, z: v.z }) < 0.5;
    for (let i = 0; i < 40 && !there(); i++) await sleep(50);
    this.moves++;
    if (there()) return;
    this.misses++;
    const p = bot.entity ? bot.entity.position : null;
    this.log(`  WATCHER NOT AT ITS VANTAGE: ${STAND_IN} is ${bot.game.gameMode} at ${p ? `${p.x.toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)}` : 'nowhere'}, not ${v.x} ${v.y} ${v.z}`);
  }

  /**
   * Runs a watcher step so that it can never change the self-test: a failure (a console command
   * timing out, the server going) is logged and the run goes on as an unwatched one would.
   */
  async quietly(what, fn) {
    try { await fn(); } catch (e) { this.log(`  watcher: ${what} failed (${e.message}); the self-test goes on`); }
  }

  /** A self-test section begins. */
  async section(name, what) {
    await this.quietly(`the ${name} section`, async () => {
      await this.goTo(vantage('sections'));
      await this.tellAll([{ text: `── ${name} ──`, color: 'yellow', bold: true }, { text: ` ${what}`, color: 'gray' }]);
    });
  }

  /** A matrix cell is about to run: where to look, and what for. */
  async cell(e, { label, values, want, index, total }) {
    await this.quietly(label, async () => {
      const v = vantage(e.def.id);
      await this.goTo(v);
      const lines = [
        [{ text: `▶ ${index}/${total} `, color: 'gold', bold: true }, { text: `${e.def.id.toUpperCase()} ${e.def.title}`, color: 'white', bold: true },
          { text: ` · ${label}`, color: 'white' }, { text: ` (expects ${want})`, color: 'dark_gray' }],
        [{ text: '  look for: ', color: 'aqua' }, { text: v.look || 'the cell in front of you', color: 'gray' }],
        ...settingLines(e.chamber, values).map((s) => [{ text: `  ${s}`, color: 'dark_aqua' }]),
      ];
      for (const l of lines) await this.tellAll(l);
    });
  }

  /** A cell's result: PASS, FAIL or KNOWN (a known plugin failure, as expected). */
  async result(label, { ok, known, detail }) {
    const word = !ok ? 'FAIL' : known ? 'KNOWN' : 'PASS';
    const colour = { PASS: 'green', FAIL: 'red', KNOWN: 'gold' }[word];
    await this.quietly(label, () => this.tellAll([{ text: `  ${word} `, color: colour, bold: true }, { text: label, color: 'white' }, { text: ` · ${detail}`, color: 'gray' }]));
  }

  /** The end of the run: the summary, and a few seconds for a person to read it before the server stops. */
  async finish(summary) {
    await this.quietly('the summary', async () => {
      await this.tellAll([{ text: 'Self-test over: ', color: 'aqua' }, { text: summary, color: 'white' },
        { text: ' The server stops in 10 seconds.', color: 'gray' }]);
      await this.srv.run(`kill @e[type=minecraft:marker,tag=${MARKER}]`);
    });
    this.current = null;
    if (this.bot) this.log(`watch: ${STAND_IN} saw itself a spectator at its vantage point after ${this.moves - this.misses} of ${this.moves} moves`);
    if ([...this.present].some((n) => n !== STAND_IN)) await sleep(10000);
  }

  /** A restart (a companion cell) put everyone out: a person rejoins by hand, the stand-in by itself. */
  async afterRestart() {
    // `present` is kept by the log's join and leave lines, which a rejoin during the restart has already updated.
    const v = this.current;
    this.current = null;
    if (v) await this.goTo(v);
    if (this.standIn) await this.joinStandIn();
  }

  close() {
    if (this.bot) this.bot.quit();
  }
}

module.exports = { Watcher, functions, facing, vantage, settingLines, TAG, MARKER, BOTS, STAND_IN };
