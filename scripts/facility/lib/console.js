'use strict';
// The chat console: a tellraw menu whose every word runs `/trigger wx set <code>` as the
// player who clicked, and a typed `!` form of the same thing.
//
// Two things the design assumed and the spike showed otherwise (see docs/DEVELOPMENT.md):
//  - a server only sends score packets for an objective that sits in a display slot, so `wx`
//    is put in the sidebar of a team colour nobody is on: every client is sent the scores,
//    no client draws them;
//  - Mineflayer 4.39's `scoreUpdated` never fires from 1.20.3 (it tests a packet field that
//    no longer exists, and ignores reset_score), so the bot reads the raw packets.

const { EventEmitter } = require('events');
const text = require('./text');

const OBJECTIVE = 'wx';
// A team colour no one is put on: the display slot only exists so the server tracks `wx`.
const HIDDEN_SLOT = 'sidebar.team.dark_gray';

/**
 * Codes are chamber * 10000 + option * 100 + value; this one table maps both ways. (The design
 * had chamber * 1000, which allows nine options; G1 has sixteen.)
 */
function encode({ chamber, option, value }) {
  // Option ACTION_OPTION (99) is the actions' slot, so a chamber has options 0..98.
  if (option < 0 || option >= ACTION_OPTION || value < 0 || value > 99) throw new Error(`code out of range: ${chamber}/${option}/${value}`);
  return chamber * 10000 + option * 100 + value;
}

function decode(code) {
  return { chamber: Math.floor(code / 10000), option: Math.floor((code % 10000) / 100), value: code % 100 };
}

/** Console commands that create the trigger objective and let everyone online use it. */
function setupCommands() {
  return [
    `scoreboard objectives add ${OBJECTIVE} trigger`,
    `scoreboard objectives setdisplay ${HIDDEN_SLOT} ${OBJECTIVE}`,
    `scoreboard players enable @a ${OBJECTIVE}`,
  ];
}

/** After a code is read: clear it and let the player trigger again. */
function rearmCommands(player) {
  return [
    `scoreboard players reset ${player} ${OBJECTIVE}`,
    `scoreboard players enable ${player} ${OBJECTIVE}`,
  ];
}

function triggerCommand(code) {
  return `/trigger ${OBJECTIVE} set ${code}`;
}

/**
 * A menu line: a title then one clickable word per choice; the current one is bracketed.
 * choices: [{ label, code, why, current }]
 */
function menu(version, title, choices) {
  const parts = [{ text: `${title} `, color: 'gray' }];
  for (const c of choices) {
    parts.push({
      text: c.current ? `[${c.label}]` : c.label,
      color: c.current ? 'gold' : 'aqua',
      click: { run: triggerCommand(c.code) },
      hover: c.why || `code ${c.code}`,
    }, ' ');
  }
  return `tellraw @a ${text.command(version, parts)}`;
}

/**
 * Listens on a Mineflayer bot for console input. Emits
 *   'code'  { player, code }  when a player's wx score is set;
 *   'typed' { player, line }  when a player says a line starting with `!`.
 */
function listen(bot) {
  const events = new EventEmitter();
  bot._client.on('scoreboard_score', (packet) => {
    if (packet.scoreName === OBJECTIVE) events.emit('code', { player: packet.itemName, code: packet.value });
  });
  bot.on('chat', (username, message) => {
    if (username !== bot.username && message.startsWith('!')) events.emit('typed', { player: username, line: message.slice(1).trim() });
  });
  return events;
}

// ---- the facility console --------------------------------------------------------------------
//
// Code space (a trigger score is one integer; 0 is what `enable` writes, so it means nothing):
//   1            the console home
//   2            the Transit tab;  3 + t: transit action t (TRANSIT_ACTIONS)
//   9            a fresh Logbook (lib/logbook.js)
//   10 + w       wing tab w (index into the wing list)
//   30 + w       go to wing w
//   50 + g       put me in tester group g (only with LuckPerms: `groups`)
//   N*10000 + o*100 + v    chamber N (1-based): option o (0..98) set to its value v,
//   N*10000 + 9900 + a     or action a on it (ACTIONS below)

const HOME = 1;
const TRANSIT_TAB = 2;
const TRANSIT_ACTION = 3;
const LOGBOOK = 9;
// The Transit tab: each runs the console form for the player who clicked (what the buttons in the
// Gate Room and by the pads run), or prints the smoke test's last word on each route.
const TRANSIT_ACTIONS = [
  { id: 'dial Hall', word: 'Dial Hall', why: 'the Ops gate dials Hall, in the Gate hall\'s lobby' },
  { id: 'dial Range', word: 'Dial Range', why: 'the Ops gate dials the Range, in the nether' },
  { id: 'dial Annex', word: 'Dial Annex', why: 'the Ops gate dials the Annex, in the End' },
  { id: 'beam lab', word: 'Beam to lab', why: 'beam me to the BeamLab pad' },
  { id: 'beam home', word: 'Beam home', why: 'beam me to the Atrium pad' },
  { id: 'routes', word: 'Routes', why: 'the last smoke-test result on each route' },
];
const TAB = 10;
const GO = 30;
// 50 + g: put me in tester group g (with LuckPerms installed: lib/groups.js).
const GROUP = 50;
const ACTION_OPTION = 99;
// The fixed codes must not overlap: the Transit actions end before the Logbook's 9.
if (TRANSIT_ACTION + TRANSIT_ACTIONS.length > LOGBOOK || LOGBOOK >= TAB) throw new Error('console codes overlap: Transit actions, Logbook, wing tabs');
const ACTIONS = ['show', 'run', 'stage', 'reset', 'watch', 'again'];
const ACTION_WORDS = { run: '▶ Run', stage: '◇ Stage', reset: '↺ Reset', watch: '⌖ Watch', again: '⟳ Again' };
const ACTION_WHY = {
  run: 'stage the fixture, make the trip, check the world',
  stage: 'build the fixture and stop, so you can use it yourself',
  reset: 'put the cell back as built',
  watch: 'go to the gallery seat',
  again: 'run with the same settings as last time',
};

/** A chamber's options as [{ name, values: [{ value, label, why }] }], from the contract's form. */
function normaliseOptions(options = {}) {
  return Object.entries(options).map(([name, values]) => ({
    name,
    values: values.map((v) => (typeof v === 'object' ? { label: String(v.value), why: '', ...v } : { value: v, label: String(v), why: '' })),
  }));
}

/**
 * The console, as one object per server: it owns the trigger objective, sends menus, reads
 * clicks and typed lines, and calls the facility back with what was asked. It holds no test
 * logic; `handlers` does the work:
 *   run(player, entry, action)       action is run | stage | reset | again
 *   watch(player, entry)             to the gallery seat
 *   go(player, wing)                 to a wing's entrance
 *   group(player, group)             into a tester group (with `groups`: [{ id, why }], or null)
 * `entries` are the chambers in console order: { number, def (campus entry), chamber (module or
 * null), values: { option: index } }.
 */
class FacilityConsole {
  constructor({ srv, version, bot, wings, entries, handlers, status, groups = null }) {
    Object.assign(this, { srv, version, bot, wings, entries, handlers, status, groups });
    // Wing tabs, wing Go and tester groups each have a band of codes; a list that outgrows it would
    // answer with another band's action.
    if (wings.length > GO - TAB || wings.length > GROUP - GO || (groups || []).length > 10000 - GROUP) throw new Error('console codes overlap: too many wings or groups');
    this.events = listen(bot);
  }

  async start() {
    for (const c of setupCommands()) await this.srv.run(c);
    this.events.on('code', (e) => { this.onCode(e).catch((err) => this.tell(e.player, [{ text: `console: ${err.message}`, color: 'red' }])); });
    this.events.on('typed', (e) => { this.onTyped(e).catch((err) => this.tell(e.player, [{ text: `console: ${err.message}`, color: 'red' }])); });
  }

  /** A player arrived: let them trigger, and say hello with a Console link. */
  async greet(player) {
    await this.srv.run(`scoreboard players enable ${player} ${OBJECTIVE}`);
    await this.tell(player, [
      { text: 'Welcome to the Wormhole Research Facility. ', color: 'aqua' },
      { text: '[Console]', color: 'gold', bold: true, click: { run: triggerCommand(HOME) }, hover: 'every chamber, every option; or say !' },
      { text: '  Gate north, ring pad east, beam pad west; tp plates under the balcony.', color: 'gray' },
    ]);
  }

  async tell(player, spec) {
    await this.srv.run(`tellraw ${player} ${text.command(this.version, spec)}`);
  }

  tabsLine(current) {
    const parts = [{ text: ' ' }];
    this.wings.forEach((w, i) => {
      const here = w.id === current;
      parts.push({ text: here ? `[${w.title}]` : w.title, color: here ? 'gold' : w.text, click: { run: triggerCommand(TAB + i) }, hover: `the ${w.title} tab` }, ' ');
    });
    const here = current === 'transit';
    parts.push({ text: here ? '[⇄ Transit]' : '⇄ Transit', color: here ? 'gold' : 'white', click: { run: triggerCommand(TRANSIT_TAB) }, hover: 'dial, beam, and the routes\' last results' });
    return parts;
  }

  async transitTab(player) {
    await this.tell(player, [{ text: '── TRANSIT ── ', color: 'white', bold: true }, { text: 'the ways between wings, by the plugin', color: 'gray' }]);
    const acts = [{ text: '  ' }];
    TRANSIT_ACTIONS.forEach((a, i) => acts.push({ text: `[${a.word}]`, color: a.id === 'routes' ? 'gold' : 'aqua', click: { run: triggerCommand(TRANSIT_ACTION + i) }, hover: a.why }, ' '));
    await this.tell(player, acts);
    await this.tell(player, this.tabsLine('transit'));
  }

  async home(player) {
    await this.tell(player, [{ text: '── WORMHOLE RESEARCH FACILITY ── ', color: 'aqua', bold: true }, { text: 'pick a wing', color: 'gray' },
      { text: '  [Logbook]', color: 'gold', click: { run: triggerCommand(LOGBOOK) }, hover: 'a fresh Logbook: your runs, the bot\'s, every wing with a Go' }]);
    await this.tell(player, this.tabsLine(null));
  }

  entriesOf(wingId) {
    return this.entries.filter((e) => e.def.wing === wingId || (wingId === 'ops' && e.def.wing === 'systems'));
  }

  async tab(player, wingId) {
    const i = this.wings.findIndex((w) => w.id === wingId);
    const w = this.wings[i];
    await this.tell(player, [{ text: `── ${w.title.toUpperCase()} ── `, color: w.text, bold: true },
      { text: '[Go]', color: 'gold', click: { run: triggerCommand(GO + i) }, hover: `take me to ${w.title}` }]);
    const list = this.entriesOf(wingId);
    if (!list.length) await this.tell(player, [{ text: '  no chambers here', color: 'gray' }]);
    for (const e of list) {
      await this.tell(player, [
        { text: `  ${e.def.id.toUpperCase()} `, color: w.text, bold: true },
        { text: e.def.title, color: 'white', click: { run: triggerCommand(e.number * 10000 + ACTION_OPTION * 100) }, hover: 'open its console' },
        { text: `  ${this.statusWords(e)}`, color: 'gray' },
      ]);
    }
    if (wingId === 'ops' && this.groups) await this.tell(player, this.groupsLine());
    await this.tell(player, this.tabsLine(wingId));
  }

  /** The Operations tab's line that puts whoever clicks in a tester group (LuckPerms installed). */
  groupsLine() {
    const parts = [{ text: '  Your tester group: ', color: 'gray' }];
    this.groups.forEach((g, i) => parts.push({ text: `[${g.id}]`, color: 'aqua', click: { run: triggerCommand(GROUP + i) }, hover: g.why }, ' '));
    return parts;
  }

  statusWords(e) {
    if (!e.chamber) return `not built yet · stage ${e.def.stage}`;
    const s = this.status(e.def.id);
    return s ? `${s.state}${s.line ? ` · ${s.line}` : ''}` : 'idle · never run';
  }

  async chamberMenu(player, e) {
    const w = this.wings.find((x) => x.id === e.def.wing) || this.wings[0];
    await this.tell(player, [{ text: `── ${w.title.toUpperCase()} · ${e.def.id.toUpperCase()} ${e.def.title} ── `, color: w.text, bold: true },
      { text: this.statusWords(e), color: 'gray' }]);
    if (!e.chamber) {
      await this.tell(player, [{ text: `  Its cell is built; its tests arrive in stage ${e.def.stage}. `, color: 'gray' },
        { text: '⌖ Watch', color: 'aqua', click: { run: triggerCommand(e.number * 10000 + ACTION_OPTION * 100 + ACTIONS.indexOf('watch')) }, hover: ACTION_WHY.watch }]);
    } else {
      const opts = normaliseOptions(e.chamber.options);
      for (const [oi, o] of opts.entries()) {
        const parts = [{ text: `  ${o.name.padEnd(9)} `, color: 'gray' }];
        o.values.forEach((v, vi) => {
          const current = (e.values[o.name] || 0) === vi;
          parts.push({ text: current ? `[${v.label}]` : v.label, color: current ? 'gold' : 'aqua',
            click: { run: triggerCommand(encode({ chamber: e.number, option: oi, value: vi })) }, hover: v.why || v.label }, ' ');
        });
        await this.tell(player, parts);
      }
      const acts = [{ text: '  ' }];
      for (const a of ['run', 'stage', 'reset', 'watch', 'again']) {
        acts.push({ text: ACTION_WORDS[a], color: a === 'run' ? 'green' : 'aqua', click: { run: triggerCommand(e.number * 10000 + ACTION_OPTION * 100 + ACTIONS.indexOf(a)) }, hover: ACTION_WHY[a] }, '   ');
      }
      await this.tell(player, acts);
    }
    await this.tell(player, this.tabsLine(e.def.wing === 'systems' ? 'ops' : e.def.wing));
  }

  async onCode({ player, code }) {
    if (!code || player === this.bot.username) return;
    for (const c of rearmCommands(player)) await this.srv.run(c);
    if (code === HOME) return this.home(player);
    if (code === LOGBOOK) return this.handlers.book(player);
    if (code === TRANSIT_TAB) return this.transitTab(player);
    if (code >= TRANSIT_ACTION && code < TRANSIT_ACTION + TRANSIT_ACTIONS.length) return this.handlers.transit(player, TRANSIT_ACTIONS[code - TRANSIT_ACTION].id);
    if (code >= TAB && code < TAB + this.wings.length) return this.tab(player, this.wings[code - TAB].id);
    if (code >= GO && code < GO + this.wings.length) return this.handlers.go(player, this.wings[code - GO]);
    if (this.groups && code >= GROUP && code < GROUP + this.groups.length) return this.handlers.group(player, this.groups[code - GROUP].id);
    const d = decode(code);
    const e = this.entries.find((x) => x.number === d.chamber);
    if (!e) return this.tell(player, [{ text: `unknown code ${code}`, color: 'red' }]);
    if (d.option === ACTION_OPTION) return this.act(player, e, ACTIONS[d.value]);
    return this.choose(player, e, d.option, d.value);
  }

  async act(player, e, action) {
    if (action === 'show' || !action) return this.chamberMenu(player, e);
    if (action === 'watch') return this.handlers.watch(player, e);
    if (!e.chamber) return this.tell(player, [{ text: `${e.def.title} has no tests yet (stage ${e.def.stage}).`, color: 'gray' }]);
    return this.handlers.run(player, e, action);
  }

  async choose(player, e, optionIndex, valueIndex) {
    const opts = normaliseOptions(e.chamber ? e.chamber.options : {});
    const o = opts[optionIndex];
    if (!o || !o.values[valueIndex]) return this.tell(player, [{ text: 'no such option', color: 'red' }]);
    e.values[o.name] = valueIndex;
    return this.chamberMenu(player, e);
  }

  /**
   * The typed form: `!` (home), `!<wing>`, `!go <wing>`, `!<chamber>`, `!<chamber> <option>
   * <value>`, `!run|stage|reset|watch|again <chamber>`, and `!group <visitor|builder|operator|default>`.
   */
  async onTyped({ player, line }) {
    const words = line.split(/\s+/).filter(Boolean).map((w) => w.toLowerCase());
    if (!words.length || words[0] === 'console') return this.home(player);
    const wingOf = (word) => this.wings.find((w) => w.id === word || w.title.toLowerCase().startsWith(word));
    const entryOf = (word) => this.entries.find((x) => x.def.id === word);
    const [first, second, third] = words;
    if (first === 'transit') return this.transitTab(player);
    if (first === 'book' || first === 'logbook') return this.handlers.book(player);
    if (first === 'group' && this.groups) {
      const g = this.groups.find((x) => x.id === second);
      return g ? this.handlers.group(player, g.id) : this.tell(player, this.groupsLine());
    }
    if (first === 'go' && second) {
      const w = wingOf(second);
      return w ? this.handlers.go(player, w) : this.tell(player, [{ text: `no wing ${second}`, color: 'red' }]);
    }
    if (ACTIONS.includes(first) && second) {
      const e = entryOf(second);
      return e ? this.act(player, e, first) : this.tell(player, [{ text: `no chamber ${second}`, color: 'red' }]);
    }
    const e = entryOf(first);
    if (e && second && third) {
      const opts = normaliseOptions(e.chamber ? e.chamber.options : {});
      const oi = opts.findIndex((o) => o.name.toLowerCase() === second);
      const vi = oi < 0 ? -1 : opts[oi].values.findIndex((v) => v.label.toLowerCase() === third || String(v.value).toLowerCase() === third);
      if (vi < 0) return this.tell(player, [{ text: `no ${second} ${third} on ${e.def.id}`, color: 'red' }]);
      return this.choose(player, e, oi, vi);
    }
    if (e) return this.chamberMenu(player, e);
    const w = wingOf(first);
    if (w) return this.tab(player, w.id);
    return this.tell(player, [{ text: `say ! for the console; "${line}" is not a console line`, color: 'gray' }]);
  }
}

module.exports = {
  OBJECTIVE, HIDDEN_SLOT, encode, decode, setupCommands, rearmCommands, triggerCommand, menu, listen,
  FacilityConsole, normaliseOptions, ACTIONS, HOME, TAB, GO, GROUP, ACTION_OPTION, TRANSIT_TAB, TRANSIT_ACTION, TRANSIT_ACTIONS, LOGBOOK,
};
