'use strict';
// Design mode's session (run-facility --design; design/facility/BRIEF.md, "Run it yourself"):
// Minecraft 1.21.11 with WorldEdit, a world kept between sessions, ops in creative, no tests.
// The first session generates the campus, builds the session fixtures, fills every protected
// volume's air with a placeholder (lib/design.js) and saves a baseline of each export area; after
// that the world is the designer's. `check` and `export` in an op's chat (or --design-check and
// --design-export from the command line) compare the areas with the baseline and write the export.
//
// Every area is read through WorldEdit's console: //copy -e and /schem save, then read here. While
// it is read, every player is a spectator and the world is saved first, so what is checked and
// exported is one moment. Nothing in the world is changed by a check or an export, so the
// placeholders stay where the designer can see them and leave this folder only in its world.

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const design = require('./design');
const bp = require('./blueprint');
const text = require('./text');
const zip = require('./zip');
const { WorldEdit, WORLDS } = require('./schematics');

const STATE_DIR = 'wx-design';
const CHAT = /^\[\d\d:\d\d:\d\d INFO\]: (?:\[Not Secure\] )?<(\w{1,16})> (.+)$/;
const JOINED = /^\[\d\d:\d\d:\d\d INFO\]: (\w{1,16}) joined the game$/;
const COMMANDS = ['check', 'export', 'export full', 'stop'];
const MODES = ['creative', 'survival', 'adventure'];

/** The facility's commit, with "+changes" if scripts/facility differs from it; "unknown" without git. */
function facilityCommit(repo) {
  const head = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: repo, encoding: 'utf8' });
  if (head.status !== 0) return 'unknown';
  const dirty = spawnSync('git', ['status', '--porcelain', '--', 'scripts/facility'], { cwd: repo, encoding: 'utf8' });
  return `${head.stdout.trim()}${dirty.status === 0 && dirty.stdout.trim() ? '+changes' : ''}`;
}

/** The ops in a server folder's ops.json, by name; [] if it has none. */
function opsOf(folder) {
  try {
    const ops = JSON.parse(fs.readFileSync(path.join(folder, 'ops.json'), 'utf8'));
    return Array.isArray(ops) ? ops.map((o) => o && o.name).filter((n) => typeof n === 'string') : [];
  } catch { return []; }
}

class DesignMode {
  constructor({ srv, folder, version = design.VERSION, repo, local, log = console.log }) {
    Object.assign(this, { srv, folder, version, repo, local, log });
    this.dir = path.join(folder, STATE_DIR);
    this.areas = design.areas();
    // Every version an export is pasted on: what is marked, checked and masked is the same union.
    this.protect = design.protectedFor(design.maskVersions());
    this.skinList = design.skins();
    this.busy = null;
  }

  /** Whether this folder holds a design world that was generated to the end. */
  static generated(folder) {
    return fs.existsSync(path.join(folder, STATE_DIR, 'state.json')) && fs.existsSync(path.join(folder, 'world'));
  }

  state() {
    try { return JSON.parse(fs.readFileSync(path.join(this.dir, 'state.json'), 'utf8')); } catch { return {}; }
  }

  // ---- generation --------------------------------------------------------------------------

  /** Fills the air of every protected volume with its placeholder; returns the blocks filled. */
  async fillPlaceholders() {
    let n = 0;
    for (const f of design.placeholderFills(this.protect)) {
      const r = await this.srv.run(design.fillCommand(f), 60000);
      if (r.errors.length && !r.errors.every((e) => /No blocks were filled/.test(e))) throw new Error(`placeholders: ${design.fillCommand(f)}: ${r.errors.join(' ')}`);
      const m = r.lines.map((l) => /filled (\d+) block/i.exec(l)).find(Boolean);
      if (m) n += Number(m[1]);
    }
    return n;
  }

  /**
   * Copies an area with its entities and saves it as plugins/WorldEdit/schematics/<name>.schem,
   * its origin at the area's minimum corner (the console's //copy takes pos1 as the origin).
   */
  async copyArea(we, area, name) {
    const b = area.box;
    await we.world(WORLDS[area.dim]);
    await we.say(`${we.we}pos1 ${b.x0},${b.y0},${b.z0}`, /First position set/i);
    await we.say(`${we.we}pos2 ${b.x1},${b.y1},${b.z1}`, /Second position set/i);
    await we.say(`${we.we}copy -e`, /blocks? (affected|copied)/i, 300000);
    await we.save(name);
    const file = path.join(this.folder, 'plugins', 'WorldEdit', 'schematics', `${name}.schem`);
    if (!fs.existsSync(file)) throw new Error(`WorldEdit said it saved ${name}, but there is no ${file}`);
    return file;
  }

  /** Each area as the campus built it, with its placeholders: what `check` compares with. */
  async saveBaseline() {
    const to = path.join(this.dir, 'baseline');
    fs.rmSync(to, { recursive: true, force: true });
    fs.mkdirSync(to, { recursive: true });
    const we = new WorldEdit(this.srv);
    try {
      for (const a of this.areas) {
        const file = await this.copyArea(we, a, `wx_design_base_${a.name}`);
        fs.renameSync(file, path.join(to, `${a.name}.schem`));
      }
    } finally {
      await we.reset();
    }
  }

  markGenerated(info) {
    fs.mkdirSync(this.dir, { recursive: true });
    fs.writeFileSync(path.join(this.dir, 'state.json'), `${JSON.stringify({ ...info, generated: new Date().toISOString() }, null, 2)}\n`);
  }

  /**
   * The first session's work after the build and the fixtures: the placeholders, then the
   * baseline, then the mark that says it is done (a session killed before it starts over).
   */
  async finishGeneration(info) {
    const n = await this.fillPlaceholders();
    this.log(`  design: ${n} placeholder blocks in ${this.protect.length} protected volumes (${design.PLACEHOLDER.volume} in test volumes, ${design.PLACEHOLDER.fixture} round fixtures)`);
    const t0 = Date.now();
    await this.saveBaseline();
    this.log(`  design: baseline of ${this.areas.length} areas saved in ${((Date.now() - t0) / 1000).toFixed(0)} s`);
    this.markGenerated({ ...info, placeholders: n, maskedFor: design.maskVersions() });
  }

  // ---- check and export --------------------------------------------------------------------

  /**
   * Runs `fn` with the world still: saved to disk, and every player a spectator (put back to the
   * game mode they had afterwards), so nobody changes an area between two copies.
   */
  async still(fn) {
    const run = (c) => this.srv.run(c).catch(() => {});
    try {
      for (const m of MODES) await run(`tag @a[gamemode=${m}] add wx_design_${m}`);
      await run('gamemode spectator @a');
      await this.srv.run('save-all flush', 300000);
      return await fn();
    } finally {
      for (const m of MODES) {
        await run(`gamemode ${m} @a[tag=wx_design_${m}]`);
        await run(`tag @a remove wx_design_${m}`);
      }
    }
  }

  /** The area that carries an entity at x y z in `dim`: the first that holds it, so overlapping areas never both do. */
  owner(dim, x, y, z) {
    return this.areas.find((a) => a.dim === dim && design.inside(a.box, x, y, z));
  }

  /**
   * Reads every area as it is now and compares it with the baseline; with `stage`, also writes
   * each area's export schematic there. Returns { items, files: [{ ...area, file }] }.
   */
  async scan({ stage = null } = {}) {
    const items = [];
    const files = [];
    const seen = new Set();
    const we = new WorldEdit(this.srv);
    try {
      for (const a of this.areas) {
        const at = { x: a.box.x0, y: a.box.y0, z: a.box.z0 };
        const base = path.join(this.dir, 'baseline', `${a.name}.schem`);
        if (!fs.existsSync(base)) throw new Error(`there is no baseline for ${a.name} (${base}); the design world was not generated to the end`);
        const now = await this.copyArea(we, a, `wx_design_now_${a.name}`);
        try {
          const current = await design.readGrid(now, { dim: a.dim, at });
          const baseline = await design.readGrid(base, { dim: a.dim, at });
          if (current.volume !== bp.volume(a.box) || current.box.x1 !== a.box.x1 || current.box.y1 !== a.box.y1 || current.box.z1 !== a.box.z1) {
            throw new Error(`WorldEdit saved ${a.name} as ${JSON.stringify(current.box)}, not its area ${JSON.stringify(a.box)}`);
          }
          // Areas overlap at their edges: a position two of them hold is reported once.
          for (const p of design.compareArea({ area: a, current, baseline, protect: this.protect, skinList: this.skinList })) {
            const key = `${p.kind} ${p.dim} ${p.x} ${p.y} ${p.z}`;
            if (!seen.has(key)) { seen.add(key); items.push(p); }
          }
          if (stage) {
            const out = design.exportArea({ current, baseline, protect: this.protect, skinList: this.skinList, owns: (x, y, z) => this.owner(a.dim, x, y, z) === a });
            const file = `${a.name}.schem`;
            design.writeGrid(out.grid, path.join(stage, file));
            files.push({ ...a, file, masked: out.masked, stripped: out.stripped, scrubbed: out.scrubbed, entities: out.entities });
          }
        } finally {
          fs.rmSync(now, { force: true });
        }
      }
    } finally {
      await we.reset();
    }
    return { items, files };
  }

  /** The check: { items, report, file }; the full report is written to wx-design/check.txt. */
  async check(who = null) {
    const { items } = await this.still(() => this.scan());
    const report = design.formatReport(items, { who });
    fs.mkdirSync(this.dir, { recursive: true });
    const file = path.join(this.dir, 'check.txt');
    fs.writeFileSync(file, report);
    return { items, report, file };
  }

  /**
   * The export: the areas' schematics, placements.json, manifest.json and check.txt (and, `full`,
   * the three worlds) in .local-server/exports/facility-design-<date>.zip. Returns
   * { file, bytes, items, files }.
   */
  async export(designer, { plugin = null, full = false } = {}) {
    const exports = path.join(this.local, 'exports');
    fs.mkdirSync(exports, { recursive: true });
    const stage = fs.mkdtempSync(path.join(exports, '.stage-'));
    try {
      const { items, files } = await this.still(() => this.scan({ stage }));
      const problems = items.filter((p) => p.kind !== 'stray').length;
      fs.writeFileSync(path.join(stage, 'check.txt'), design.formatReport(items, { who: designer }));
      fs.writeFileSync(path.join(stage, 'placements.json'), `${JSON.stringify(design.placementsFor(files), null, 2)}\n`);
      const manifest = design.manifestFor({
        commit: facilityCommit(this.repo), generatedFrom: this.state().commit || null, designer, plugin, areaList: files, problems, worlds: full,
      });
      fs.writeFileSync(path.join(stage, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
      let name = `facility-design-${design.dateStamp()}${full ? '-full' : ''}.zip`;
      for (let k = 2; fs.existsSync(path.join(exports, name)); k++) name = `facility-design-${design.dateStamp()}${full ? '-full' : ''}-${k}.zip`;
      const file = path.join(exports, name);
      const entries = zip.filesUnder(stage).map((f) => ({ name: f, from: path.join(stage, f) }));
      if (!full) return { file, ...zip.writeZip(file, entries), items, files };
      // Saving is off while the worlds are read, so no region file changes under the zip.
      await this.srv.run('save-off');
      try {
        await this.srv.run('save-all flush', 300000);
        for (const w of Object.values(WORLDS)) {
          const dir = path.join(this.folder, w);
          if (!fs.existsSync(dir)) continue;
          for (const f of zip.filesUnder(dir, (r) => r === 'session.lock')) entries.push({ name: `worlds/${w}/${f}`, from: path.join(dir, f) });
        }
        return { file, ...zip.writeZip(file, entries), items, files };
      } finally {
        await this.srv.run('save-on').catch(() => {});
      }
    } finally {
      fs.rmSync(stage, { recursive: true, force: true });
    }
  }

  // ---- chat --------------------------------------------------------------------------------

  isOp(name) {
    return opsOf(this.folder).some((o) => o.toLowerCase() === name.toLowerCase());
  }

  async tell(who, words, color = 'white') {
    const spec = [{ text: '[design] ', color: 'light_purple' }, { text: words, color }];
    await this.srv.run(`tellraw ${who} ${text.command(this.version, spec)}`).catch(() => {});
  }

  /**
   * Listens for ops' chat: `check`, `export`, `export full` and `stop` (`onStop` is called); ops
   * are put in creative when they join. One check or export at a time. Returns a function that
   * stops listening.
   */
  listen({ onStop, plugin = null }) {
    const on = (line) => {
      const j = JOINED.exec(line);
      if (j && this.busy) {
        // Mid-scan, anyone who joins watches until it is done, then gets creative like the rest.
        this.srv.run(`tag ${j[1]} add wx_design_creative`).catch(() => {});
        this.srv.run(`gamemode spectator ${j[1]}`).catch(() => {});
        this.tell(j[1], `Design mode is busy with ${this.busy}; you are a spectator until it is done.`, 'gray');
        return;
      }
      if (j && this.isOp(j[1])) {
        this.srv.run(`gamemode creative ${j[1]}`).catch(() => {});
        this.tell(j[1], 'Design mode: say check, export (export full for your worlds too) or stop in chat.', 'gray');
        return;
      }
      const m = CHAT.exec(line);
      if (!m) return;
      const [who, said] = [m[1], m[2].trim().toLowerCase().replace(/[.!]$/, '').replace(/\s+/g, ' ')];
      if (!COMMANDS.includes(said) || !this.isOp(who)) return;
      if (said === 'stop') { onStop(who); return; }
      if (this.busy) { this.tell(who, `Busy with ${this.busy}; try again when it is done.`, 'yellow'); return; }
      this.busy = said;
      this.running = this.run(said, who, plugin).catch((e) => this.tell(who, `${said} failed: ${e.message}`, 'red')).finally(() => { this.busy = null; });
    };
    this.srv.on('line', on);
    return () => this.srv.off('line', on);
  }

  async run(what, who, plugin) {
    await this.tell(who, what === 'check' ? 'Checking every area (a minute or so); you are a spectator until it is done.' : 'Exporting (a few minutes); you are a spectator until it is done.', 'gray');
    if (what === 'check') {
      const r = await this.check(who);
      const lines = design.chatSummary(r.items);
      for (const [i, l] of lines.entries()) await this.tell(who, l, i === 0 ? (r.items.some((p) => p.kind !== 'stray') ? 'red' : 'green') : 'white');
      await this.tell(who, `Full report: ${r.file}`, 'gray');
      this.log(`  design check by ${who}: ${lines[0]}; ${r.file}`);
    } else {
      const r = await this.export(who, { plugin, full: what === 'export full' });
      const lines = design.chatSummary(r.items, 3);
      await this.tell(who, `Exported ${(r.bytes / 1024 / 1024).toFixed(1)} MB to ${r.file}`, 'green');
      for (const l of lines) await this.tell(who, l);
      this.log(`  design export by ${who}: ${r.file} (${r.bytes} bytes); ${lines[0]}`);
    }
  }
}

module.exports = { DesignMode, facilityCommit, opsOf, STATE_DIR, CHAT, JOINED, COMMANDS };
