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

class Config {
  constructor(srv) {
    this.srv = srv;
    this.stacks = new Map();
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
    if (!m) throw new Error(`wormhole config ${name} ${value} was refused: ${r.lines.join(' | ') || 'no answer'}`);
    return m[2];
  }

  /** Sets a value for `owner`, remembering what it replaced. */
  async set(name, value, owner = '') {
    const before = await this.get(name);
    const now = await this.write(name, value);
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
   * returns what it restored.
   */
  async restore(owner) {
    const done = [];
    const owners = owner === undefined ? [...this.stacks.keys()] : [owner];
    for (const o of owners) {
      const stack = this.stacks.get(o) || [];
      while (stack.length) {
        const { name, before } = stack.pop();
        await this.write(name, before);
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

module.exports = { Config };
