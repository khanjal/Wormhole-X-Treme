'use strict';
// The facility's state surfaces: boards (text displays), pylons, a bossbar per running chamber,
// and the fault counter. Everything here writes through server.run, and boards are found by
// tag (wx_board_<id>), never by entity id.

const campus = require('./campus');
const text = require('./text');
const server = require('./server');

const PYLON = { idle: 'gray', pass: 'lime', running: 'yellow', fail: 'red' };

class Boards {
  constructor(srv, version) {
    this.srv = srv;
    this.version = version;
  }

  selector(id) {
    return `@e[type=minecraft:text_display,tag=wx_board_${id},limit=1]`;
  }

  /** Rewrites a board's text; throws if the server refused (no such board, bad text). */
  async set(id, spec) {
    const r = await this.srv.run(`data merge entity ${this.selector(id)} {text:${text.displayNbt(this.version, spec)}}`);
    if (r.errors.length) throw new Error(`board ${id}: ${r.errors.join(' ')}`);
  }

  /** The component a board holds, as the server reads it back. */
  async read(id) {
    const r = await this.srv.run(`data get entity ${this.selector(id)} text`);
    const line = r.lines.find((l) => l.includes('has the following entity data: '));
    if (!line) return null;
    return text.readDisplayText(this.version, line.slice(line.indexOf('has the following entity data: ') + 31));
  }

  /** Sets a pylon's top block: idle (grey), running (yellow), pass (lime) or fail (red). */
  async pylon(at, state, dim = campus.OVERWORLD) {
    const colour = PYLON[state];
    if (!colour || !at) return;
    await this.srv.run(`execute in ${dim} run setblock ${at.x} ${at.y + 2} ${at.z} minecraft:${colour}_concrete`);
  }
}

/**
 * A bossbar for one chamber's run: named for the step, progress = step / steps, yellow while
 * running, green or red for three seconds at the end, then removed.
 */
class RunBar {
  constructor(srv, version, id, title, steps) {
    // Its own id each run: a run inside the last one's three seconds must not be removed by
    // the last one's timer.
    RunBar.count = (RunBar.count || 0) + 1;
    Object.assign(this, { srv, version, id: `wx:run_${id}_${RunBar.count}`, title, steps, step: 0 });
    this.timer = null;
    this.closed = false;
  }

  async open() {
    await this.srv.run(`bossbar remove ${this.id}`);
    await this.srv.run(`bossbar add ${this.id} ${text.command(this.version, this.title)}`);
    await this.srv.run(`bossbar set ${this.id} max ${this.steps}`);
    await this.srv.run(`bossbar set ${this.id} color yellow`);
    await this.srv.run(`bossbar set ${this.id} players @a`);
  }

  async advance(name) {
    this.step = Math.min(this.step + 1, this.steps);
    await this.srv.run(`bossbar set ${this.id} name ${text.command(this.version, `${this.title} · ${this.step}/${this.steps} · ${name}`)}`);
    await this.srv.run(`bossbar set ${this.id} value ${this.step}`);
  }

  /** Shows the result, then removes the bar after `holdMs` (0 removes it now). */
  async finish(passed, line, holdMs = 3000) {
    await this.srv.run(`bossbar set ${this.id} name ${text.command(this.version, `${this.title} · ${line}`)}`);
    await this.srv.run(`bossbar set ${this.id} value ${this.steps}`);
    await this.srv.run(`bossbar set ${this.id} color ${passed ? 'green' : 'red'}`);
    if (holdMs <= 0) return this.close();
    this.timer = setTimeout(() => { this.close().catch(() => {}); }, holdMs);
    return undefined;
  }

  async close() {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    if (this.closed) return;
    this.closed = true;
    if (this.srv.exited === null) await this.srv.run(`bossbar remove ${this.id}`);
  }
}

/**
 * Counts plugin faults in the server's output (server.pluginFault decides what is one) from the
 * moment it is attached, and shows the count on the Ops fault board: green at zero, red with
 * the first fault's line after that. Attach it before the server starts to see enable faults.
 */
class FaultCounter {
  /** `fixed`: issues the plugin jar carries the fix for (--fixed); their known faults are faults. */
  constructor(srv, { fixed = [] } = {}) {
    this.srv = srv;
    this.fixed = fixed;
    this.faults = [];
    // Known plugin bugs (server.KNOWN_FAULTS): reported, not counted as new faults. A line that
    // reads like one is held with the stack trace printed after it, and is known only if the
    // trace shows the bug's cause; anything else with the same words is a fault like any other.
    this.known = [];
    this.pending = null;
    this.onChange = null;
    srv.on('line', (line) => {
      const logLine = /^\[\d\d:\d\d:\d\d /.test(line);
      if (this.pending && !logLine) { this.pending.trace.push(line); return; }
      if (logLine) this.settle();
      const f = server.pluginFault(line);
      if (!f) return;
      const k = server.knownFault(f);
      if (k && !(k.issue && fixed.includes(k.issue))) this.pending = { line: f, k, trace: [] };
      else this.faults.push(f);
      if (this.onChange) this.onChange(this);
    });
  }

  /** Decides a held line: known if its trace shows the known cause, else a fault with its frames. */
  settle() {
    const p = this.pending;
    if (!p) return;
    this.pending = null;
    if (p.k.cause(p.trace, this.srv.folder)) {
      this.known.push({ line: p.line, note: p.k.note, issue: p.k.issue });
    } else {
      const cause = p.trace.find((l) => /Exception|Error/.test(l));
      this.faults.push(`${p.line}${cause ? ` (${cause.trim()})` : ''}`, ...p.trace.map((l) => server.pluginFault(l)).filter(Boolean));
    }
    if (this.onChange) this.onChange(this);
  }

  get count() {
    this.settle();
    return this.faults.length;
  }

  spec() {
    const known = this.known.length ? [{ text: ` (${this.known.length} known)`, color: 'gold' }] : [];
    if (!this.faults.length) return [{ text: 'Plugin log: ', color: 'white' }, { text: '0 faults', color: 'green', bold: true }, ...known];
    const first = this.faults[0];
    return [{ text: 'Plugin log: ', color: 'white' }, { text: `${this.count} fault${this.count > 1 ? 's' : ''}`, color: 'red', bold: true },
      '\n', { text: first.length > 80 ? `${first.slice(0, 77)}...` : first, color: 'red' }];
  }

  /** Keeps the fault board current from now on. */
  show(boards, id = 'faults') {
    const update = () => boards.set(id, this.spec()).catch(() => {});
    this.onChange = update;
    return update();
  }
}

module.exports = { Boards, RunBar, FaultCounter, PYLON };
