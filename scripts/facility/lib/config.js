'use strict';
// Plugin settings through the plugin's own command, `wormhole config <setting> [value]`, run at
// the console. Read: "GATE_SOUND_VOLUME = 1.5" (then a description line). Write:
// "GATE_SOUND_VOLUME is now 0.5." or the refusal. Settings may be typed in either spelling
// (gate-sound-volume or GATE_SOUND_VOLUME); the plugin prints the enum name.
//
// Every change goes on a stack with the value it replaced, and restore() puts them back in
// reverse, so a chamber that declares `needs.config` never leaks a setting into the next run.

class Config {
  constructor(srv) {
    this.srv = srv;
    this.stack = [];
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

  /** Sets a value, remembering what it replaced. */
  async set(name, value) {
    const before = await this.get(name);
    const now = await this.write(name, value);
    this.stack.push({ name, before });
    return now;
  }

  /** Applies a chamber's `needs.config` ({ setting: value }); returns how many were set. */
  async apply(settings = {}) {
    const entries = Object.entries(settings);
    for (const [name, value] of entries) await this.set(name, value);
    return entries.length;
  }

  /** Puts every recorded setting back, newest first; returns what it restored. */
  async restore() {
    const done = [];
    while (this.stack.length) {
      const { name, before } = this.stack.pop();
      await this.write(name, before);
      done.push(`${name}=${before}`);
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
