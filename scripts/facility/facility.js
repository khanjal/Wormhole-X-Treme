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

const BOT = 'Probe';

function clock() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

class Facility {
  /** `srv` is a started Server; `manifest` is what lib/generate.js wrote. */
  constructor({ srv, version, manifest, port, log = console.log }) {
    Object.assign(this, { srv, version, manifest, port, log });
    this.boards = new Boards(srv, version);
    this.config = new Config(srv);
    this.faults = new FaultCounter(srv);
    this.entries = chambers.entries();
    this.status = {};
    this.last = {};
    this.held = new Set(); // chambers whose Stage left settings applied until their Reset
    this.queue = Promise.resolve();
    this.bars = [];
  }

  // ---- world -------------------------------------------------------------------------------

  /** Gamerules by version (one name each, never both), then forceload and wait for the chunks. */
  async prepare() {
    await this.srv.prepareFence();
    const problems = [];
    for (const rule of ['daylight', 'weather', 'mobSpawning', 'commandBlockOutput', 'logAdminCommands']) {
      const cmd = `gamerule ${server.gameruleName(this.version, rule)} false`;
      const r = await this.srv.run(cmd);
      if (r.errors.length) problems.push(`${cmd}: ${r.errors.join(' ')}`);
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
    for (const f of campus.FORCELOAD) {
      const y = f.dim === campus.OVERWORLD ? 0 : 64;
      await this.srv.waitLoaded(f.dim, [[f.from[0], y, f.from[1]], [f.to[0], y, f.to[1]], [f.from[0], y, f.to[1]], [f.to[0], y, f.from[1]]], 120000);
    }
    this.loadMs = Date.now() - t0;
    return problems;
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
      if (n) await this.srv.run(`execute in ${dim} run fill ${coords} minecraft:air replace minecraft:structure_void`);
    }
    return { ok: air === vol, air, volume: vol };
  }

  // ---- people --------------------------------------------------------------------------------

  async connectProbe() {
    const bot = await join({ port: this.port, version: this.version, username: BOT });
    this.probe = new Probe(bot, this.srv);
    for (const c of [`op ${BOT}`, `gamemode creative ${BOT}`]) await this.srv.run(c);
    await this.probe.teleport(campus.TRANSIT.home);
    return this.probe;
  }

  /** Starts the console and greets everyone who joins from now on. */
  async openConsole() {
    const wingList = campus.WINGS;
    this.console = new FacilityConsole({
      srv: this.srv, version: this.version, bot: this.probe.bot, wings: wingList, entries: this.entries,
      status: (id) => this.status[id],
      handlers: {
        run: (player, e, action) => this.request(player, e, action),
        watch: (player, e) => this.watch(player, e),
        go: (player, w) => this.go(player, w),
      },
    });
    await this.console.start();
    this.srv.on('line', (line) => {
      const m = /: (\w+) joined the game/.exec(line);
      if (m && m[1] !== BOT) this.welcome(m[1]).catch((e) => this.log(`  welcome ${m[1]}: ${e.message}`));
    });
  }

  async welcome(player) {
    const h = campus.TRANSIT.home;
    await this.srv.run(`gamemode adventure ${player}`);
    await this.srv.run(`execute in ${campus.OVERWORLD} run tp ${player} ${h.x} ${h.y} ${h.z} ${h.yaw} 0`);
    await this.console.greet(player);
  }

  async go(player, w) {
    const e = w.entrance;
    await this.srv.run(`execute in ${w.dim} run tp ${player} ${e.x} ${e.y} ${e.z} ${e.yaw} 0`);
  }

  async watch(player, e) {
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
    return lines;
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
      const r = await this.runChamber(e, { values, mode: action === 'stage' ? 'stage' : 'run' });
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
      tag: `wx_run_${def.id}`, observed: {}, layout: blueprint.layoutOf(def), step: () => {},
    };
  }

  /**
   * Runs one chamber: refuse, or reset the cell, apply its settings, stage, (run, check), and
   * put the settings back. `values` are option indices, or option values if `raw`. Returns
   * { outcome: PASS | FAIL | REFUSED | STAGED, reason, checks }.
   */
  async runChamber(e, { values = {}, raw = false, mode = 'run', holdMs = 3000 } = {}) {
    const ch = e.chamber;
    const v = raw ? values : this.valuesOf(e, values);
    if (!raw) this.last[e.def.id] = { ...values };
    const refusal = ch.refuses ? ch.refuses(v) : null;
    if (refusal) {
      await this.setStatus(e, 'refused', refusal);
      return { outcome: 'REFUSED', reason: refusal, checks: [] };
    }
    const ctx = this.makeCtx(e);
    const bar = new RunBar(this.srv, this.version, e.def.id, `${e.def.id.toUpperCase()} ${e.def.title}`, 6);
    this.bars.push(bar);
    ctx.step = (name) => bar.advance(name);
    await bar.open();
    await this.setStatus(e, 'running', mode === 'stage' ? 'staging' : 'running');
    let result;
    try {
      await bar.advance('resetting the cell');
      const reset = await this.runFunction(this.resetFunction(e));
      if (!reset.ok) throw Object.assign(new Error(`reset before staging: ${reset.detail}`), { phase: 'fixture' });
      await this.srv.run(`kill @e[tag=${ctx.tag}]`);
      await bar.advance('applying settings');
      const needs = ch.needs ? ch.needs(v) : {};
      await this.config.apply(needs.config || {});
      try {
        await ch.stage(ctx, v);
      } catch (err) { err.phase = 'fixture'; throw err; }
      if (mode === 'stage') {
        this.held.add(e.def.id);
        await this.setStatus(e, 'staged', 'yours: Reset when done');
        await bar.finish(true, 'staged', holdMs);
        return { outcome: 'STAGED', reason: null, checks: [] };
      }
      try {
        await ch.run(ctx, v);
      } catch (err) { err.phase = 'trip'; throw err; }
      await bar.advance('checking');
      const checks = [];
      for (const c of ch.checks(ctx, v)) {
        let ok = false;
        try { ok = Boolean(await c.test()); } catch (err) { ok = false; c.error = err.message; }
        checks.push({ name: c.name, ok, error: c.error });
      }
      const failed = checks.find((c) => !c.ok);
      result = failed ? { outcome: 'FAIL', reason: failed.name, checks } : { outcome: 'PASS', reason: null, checks };
    } catch (err) {
      result = { outcome: 'FAIL', reason: `${err.phase === 'fixture' ? 'fixture failed' : 'trip failed'}: ${err.message}`, checks: [] };
    } finally {
      if (mode !== 'stage') await this.config.restore();
    }
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
    if (!f) return { ok: true, problems };
    const r = await this.runFunction(f);
    if (!r.ok) problems.push(r.detail);
    await this.srv.run(`kill @e[tag=wx_run_${e.def.id}]`);
    if (this.held.delete(e.def.id)) await this.config.restore();
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

  async close() {
    for (const b of this.bars) await b.close().catch(() => {});
    if (this.probe) this.probe.bot.quit();
  }
}

module.exports = { Facility, BOT };
