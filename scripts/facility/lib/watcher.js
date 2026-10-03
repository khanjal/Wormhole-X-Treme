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
    this.joined = null;
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

  /** Waits until the watcher (or, unnamed, anyone) has joined and been made a watcher. */
  async arrive() {
    const who = this.wanted || 'any name';
    this.log(`\nwatch: join localhost:${this.fac.port} with Minecraft ${this.version} as ${who}; the self-test starts when you are in.`);
    this.log('  You are put in spectator mode and moved to each chamber in turn; the cells take no notice of you.');
    if (this.standIn) await this.joinStandIn();
    await new Promise((resolve) => {
      const ready = () => (this.wanted ? this.present.has(this.wanted) : this.present.size > 0);
      if (ready()) { resolve(); return; }
      const timer = setInterval(() => { if (ready()) { clearInterval(timer); resolve(); } }, 250);
    });
  }

  /** The stand-in: a client that joins as a person would and stays where it is put. */
  async joinStandIn() {
    const { join } = require('./probe');
    this.bot = await join({ port: this.fac.port, version: this.version, username: STAND_IN });
    // No physics: a client's own movement is a person's business, and a falling bot is not one standing still.
    this.bot.physicsEnabled = false;
  }

  /** Makes `name` a watcher: spectator, tagged, night vision for the dark cells, and put at the current vantage. */
  async adopt(name) {
    // The facility's welcome (adventure, the atrium, the Logbook) runs from the same log line; this
    // runs after it is skipped, as Facility.openConsole asks.
    for (const c of [`gamemode spectator ${name}`, `tag ${name} add ${TAG}`, `effect give ${name} minecraft:night_vision infinite 0 true`]) {
      await this.srv.run(c);
    }
    this.present.add(name);
    this.log(`  watcher ${name} joined: a spectator, tagged ${TAG}`);
    await this.tell(name, [{ text: 'You are watching the facility self-test. ', color: 'aqua' },
      { text: 'You are a spectator the cells take no notice of, held near each chamber\'s vantage point while its cells run.', color: 'gray' }]);
    if (this.current) await this.move(name, this.current);
    this.joined = this.joined || name;
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
    if (this.bot) await this.standInThere(v);
  }

  /** The stand-in's own client must see itself a spectator at the vantage point; says so if not. */
  async standInThere(v) {
    const bot = this.bot;
    const there = () => bot.entity && bot.game.gameMode === 'spectator' && bot.entity.position.distanceTo({ x: v.x, y: v.y, z: v.z }) < 0.5;
    for (let i = 0; i < 40 && !there(); i++) await new Promise((resolve) => { setTimeout(resolve, 50); });
    this.moves = (this.moves || 0) + 1;
    if (there()) return;
    this.misses = (this.misses || 0) + 1;
    const p = bot.entity ? bot.entity.position : null;
    this.log(`  WATCHER NOT AT ITS VANTAGE: ${STAND_IN} is ${bot.game.gameMode} at ${p ? `${p.x.toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)}` : 'nowhere'}, not ${v.x} ${v.y} ${v.z}`);
  }

  /** A self-test section begins. */
  async section(name, what) {
    const v = vantage('sections');
    await this.goTo(v);
    await this.tellAll([{ text: `── ${name} ──`, color: 'yellow', bold: true }, { text: ` ${what}`, color: 'gray' }]);
  }

  /** A matrix cell is about to run: where to look, and what for. */
  async cell(e, { label, values, want, index, total }) {
    const v = vantage(e.def.id);
    await this.goTo(v);
    const lines = [
      [{ text: `▶ ${index}/${total} `, color: 'gold', bold: true }, { text: `${e.def.id.toUpperCase()} ${e.def.title}`, color: 'white', bold: true },
        { text: ` · ${label}`, color: 'white' }, { text: ` (expects ${want})`, color: 'dark_gray' }],
      [{ text: '  look for: ', color: 'aqua' }, { text: v.look || 'the cell in front of you', color: 'gray' }],
      ...settingLines(e.chamber, values).map((s) => [{ text: `  ${s}`, color: 'dark_aqua' }]),
    ];
    for (const l of lines) await this.tellAll(l);
  }

  /** A cell's result: PASS, FAIL or KNOWN (a known plugin failure, as expected). */
  async result(label, { ok, known, detail }) {
    const word = !ok ? 'FAIL' : known ? 'KNOWN' : 'PASS';
    const colour = { PASS: 'green', FAIL: 'red', KNOWN: 'gold' }[word];
    await this.tellAll([{ text: `  ${word} `, color: colour, bold: true }, { text: label, color: 'white' }, { text: ` · ${detail}`, color: 'gray' }]);
  }

  /** The end of the run. */
  async finish(summary) {
    await this.tellAll([{ text: 'Self-test over: ', color: 'aqua' }, { text: summary, color: 'white' }]);
    await this.srv.run(`kill @e[type=minecraft:marker,tag=${MARKER}]`);
    this.current = null;
    if (this.bot) this.log(`watch: ${STAND_IN} saw itself a spectator at its vantage point after ${(this.moves || 0) - (this.misses || 0)} of ${this.moves || 0} moves`);
  }

  /** A restart (a companion cell) put everyone out: a person rejoins by hand, the stand-in by itself. */
  async afterRestart() {
    this.present.clear();
    // The marker was saved with the world, but say where it is again for the rejoin.
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
