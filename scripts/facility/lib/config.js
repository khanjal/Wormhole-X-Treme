'use strict';
// Plugin settings through the plugin's own command, `wormhole config <setting> [value]`, run at
// the console. Read: "GATE_SOUND_VOLUME = 1.5" (then a description line). Write:
// "GATE_SOUND_VOLUME is now 0.5." or the refusal. Settings may be typed in either spelling
// (gate-sound-volume or GATE_SOUND_VOLUME); the plugin prints the enum name.
//
// Every change goes on its owner's stack (a chamber's id) with the value it replaced, and
// restore(owner) puts that owner's back in reverse, so a chamber that declares `needs.config`
// never leaks a setting into the next run, and one chamber's restore never undoes another's
// held stage.
//
// The plugin saves every change to its config file, so a launcher killed before its restore
// would leave it there for the next --keep-world run. Each setting's value from before the
// facility first changed it is kept in a journal in the server folder until it is put back, and
// recover() puts back whatever a killed run left in it.

const fs = require('fs');
const path = require('path');

const JOURNAL = 'facility-settings.json';

class Config {
  /** `journal` is the file to keep outstanding changes in; null keeps none. */
  constructor(srv, { journal = srv.folder ? path.join(srv.folder, JOURNAL) : null } = {}) {
    this.srv = srv;
    this.stacks = new Map();
    this.journal = journal;
  }

  /** The journal's { setting: value before the facility changed it }; {} if there is none. */
  readJournal() {
    if (!this.journal || !fs.existsSync(this.journal)) return {};
    try {
      const j = JSON.parse(fs.readFileSync(this.journal, 'utf8'));
      return j && typeof j === 'object' && !Array.isArray(j) ? j : {};
    } catch {
      return {};
    }
  }

  writeJournal(j) {
    if (!this.journal) return;
    if (!Object.keys(j).length) { fs.rmSync(this.journal, { force: true }); return; }
    const tmp = `${this.journal}.tmp`;
    fs.writeFileSync(tmp, `${JSON.stringify(j, null, 2)}\n`);
    fs.renameSync(tmp, this.journal);
  }

  /** True if any owner's stack still holds a change of `name`. */
  holds(name) {
    return [...this.stacks.values()].some((s) => s.some((x) => x.name === name));
  }

  /** Drops `name` from the journal once no owner holds a change of it. */
  settle(name) {
    if (this.holds(name)) return;
    const j = this.readJournal();
    if (name in j) { delete j[name]; this.writeJournal(j); }
  }

  /**
   * Puts back every setting a run killed before its restore left changed, from the journal.
   * Returns { restored, refused }: an entry the plugin refuses (a setting renamed or retyped
   * between builds) is dropped too and named in `refused`, so it fails one start, not every one.
   */
  async recover() {
    const j = this.readJournal();
    const restored = [];
    const refused = [];
    for (const [name, before] of Object.entries(j)) {
      try {
        await this.write(name, before);
        restored.push(`${name}=${before}`);
      } catch (e) {
        if (!e.refused) throw e; // a server that did not answer: keep the entry for next time
        refused.push(`${name}=${before} (${e.message})`);
      }
      delete j[name];
      this.writeJournal(j);
    }
    return { restored, refused };
  }

  /** The setting's current value as the plugin prints it. Throws for an unknown setting. */
  async get(name) {
    const r = await this.srv.run(`wormhole config ${name}`);
    const m = r.lines.map((l) => /^([A-Z0-9_]+) = (.*)$/.exec(l.trim())).find(Boolean);
    if (!m) throw new Error(`wormhole config ${name}: ${r.lines.join(' | ') || 'no answer'}`);
    return m[2];
  }

  /** Sets a value without recording it; returns the value the plugin accepted, or throws its refusal. */
  async write(name, value) {
    const r = await this.srv.run(`wormhole config ${name} ${value}`);
    const m = r.lines.map((l) => /^([A-Z0-9_]+) is now (.*)\.$/.exec(l.trim())).find(Boolean);
    if (!m) throw Object.assign(new Error(`wormhole config ${name} ${value} was refused: ${r.lines.join(' | ') || 'no answer'}`), { refused: true });
    return m[2];
  }

  /** Sets a value for `owner`, remembering what it replaced. */
  async set(name, value, owner = '') {
    const before = await this.get(name);
    // Journalled before the write, so no moment exists when the change is saved and the value
    // it replaced is not.
    const j = this.readJournal();
    if (!(name in j)) { j[name] = before; this.writeJournal(j); }
    let now;
    try {
      now = await this.write(name, value);
    } catch (e) {
      this.settle(name);
      throw e;
    }
    if (!this.stacks.has(owner)) this.stacks.set(owner, []);
    this.stacks.get(owner).push({ name, before });
    return now;
  }

  /** Applies a chamber's `needs.config` ({ setting: value }) for `owner`; returns how many were set. */
  async apply(settings = {}, owner = '') {
    const entries = Object.entries(settings);
    for (const [name, value] of entries) await this.set(name, value, owner);
    return entries.length;
  }

  /** What `owner`'s change of a setting replaced, or undefined if it has not changed it. */
  replaced(owner, name) {
    const hit = (this.stacks.get(owner) || []).find((x) => x.name === name);
    return hit ? hit.before : undefined;
  }

  /**
   * Puts `owner`'s recorded settings back, newest first (every owner's when none is given);
   * returns what it restored. A record leaves its stack only once its write is accepted, so a
   * refused restore throws with the setting still recorded, to be restored again.
   */
  async restore(owner) {
    const done = [];
    const owners = owner === undefined ? [...this.stacks.keys()] : [owner];
    for (const o of owners) {
      const stack = this.stacks.get(o) || [];
      while (stack.length) {
        const { name, before } = stack[stack.length - 1];
        await this.write(name, before);
        stack.pop();
        this.settle(name);
        done.push(`${name}=${before}`);
      }
      this.stacks.delete(o);
    }
    return done;
  }

  /** The values of several settings, for comparing before and after a run. */
  async snapshot(names) {
    const out = {};
    for (const n of names) out[n] = await this.get(n);
    return out;
  }
}

Config.JOURNAL = JOURNAL;

module.exports = { Config };
