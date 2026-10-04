'use strict';
// The Wormhole Research Facility launcher. A local pre-release check: it never runs in GitHub.
//
//   node scripts/facility/run-facility.js [version]            build the campus and hold for a tester
//   node scripts/facility/run-facility.js [version] --selftest run the self-test, exit 1 on any FAIL
//   node scripts/facility/run-facility.js --selftest --quick --versions 1.20.4,26.1.2   (in parallel)
//
// Options:
//   --java <path>        java for the server (default: a JDK of the highest major the version and
//                        the newest class file in every plugin jar need, found by
//                        JAVA<major>_HOME or in the usual install folders)
//   --plugin <jar>       use this plugin jar instead of building one
//   --no-build           use target/WormholeXTreme.jar as it is
//   --jdk17 <path>       java for the Maven build (default: a JDK 17 found the same way)
//   --port <n>           server port (default 25590); another port gets its own server folder
//   --keep-world         keep the world from the last run instead of starting fresh
//   --quick              self-test with the short matrix (the cells marked quick): a few minutes
//   --cells <names>      self-test only the matrix cells whose names match (a|b, ^start, end$)
//   --fixed <issues>     the plugin jar carries these fixes (e.g. 491): their known-failure
//                        cells are expected to pass
//   --shards <n>         self-test: split the matrix across n servers of the version, each on its
//                        own port and folder, run at once, with one merged report (lib/shards.js);
//                        with --versions, versions x shards servers
//   --paper-build <n>    use this Paper build instead of the newest stable one (still checked)
//   --with <list>        install companion plugins, pinned in companions.json: viaversion,
//                        viabackwards, dynmap, worldedit, worldguard, luckperms, vault, or the
//                        sets via, regions, permissions (what one needs comes with it). Read from
//                        the plugin cache, else downloaded from an official source and checked;
//                        a run without one takes out the jar an earlier run installed. The
//                        self-test's companion cells run only with --with (lib/companions.js)
//   --plugin-cache <dir> read companion jars from <dir>/<version>/ then <dir>/any/ first
//                        (default: WX_PLUGIN_CACHE, else the nearest .wx-plugins folder beside
//                        the repository or a folder above it)
//   --op <names>         op these players (comma-separated) once the server is up
//   --tied              (set by --versions and --shards for their children) stop when stdin
//                        closes
//   --viewer             serve prismarine-viewer on Probe at http://127.0.0.1:<3007 + port - 25590>/
//                        (orbit) and .../first/ (Probe's eyes); not on 26.x, which it cannot draw
//   --viewer-port <n>    the viewer's web port instead
//   --shots [names|all]  fly Probe to campus.SHOTS's vantage points and save a PNG of each to
//                        .local-server/shots/<version>/<name>.png (needs Chrome or Edge, or
//                        WX_BROWSER); then the self-test with --selftest, the hold with --viewer,
//                        else the end
//   --schematics <dir>   paste the WorldEdit schematics campus.SCHEMATICS and <dir>/placements.json
//                        place, from <dir>, after the build; each box checked by the decoration
//                        guardrail first (lib/schematics.js). Needs --with worldedit; a placement
//                        with a minVersion newer than the version is left out
//   --design             design mode (design/facility/BRIEF.md): 1.21.11 with WorldEdit, the world
//                        in .local-server/design-1.21.11/ kept between sessions, ops in creative, no
//                        tests; the campus and its placeholders are made in the first session only.
//                        An op says check, export or stop in chat (lib/designmode.js)
//   --design-check       design mode's check from here: start the design world, check, stop
//                        (exit 1 if it found a problem); the report is in wx-design/check.txt
//   --design-export      design mode's export from here: start, export, stop; the zip (the
//                        schematics, placements, manifest and check report) is in .local-server/exports/
//   --full               with --design-export: the zip holds the three worlds as well
//   --design-open        design mode listens on every address, not only 127.0.0.1 (online-mode is
//                        off, so anyone who can reach the port can join under any name, an op's too)
//   --design-import <zip> a design export: unpacked into .local-server/imports/<name>/ and pasted as
//                        --schematics with WorldEdit, on 1.21.11 and later (1.20.4 runs without it);
//                        refused if its check.txt reports problems, unless --accept-check-problems
//
// The server folder is .local-server/facility-<version>/. In hold mode, say "stop" in chat or
// press Ctrl+C to shut it down; Ctrl+C again kills the server if it will not stop.

const crypto = require('crypto');
const fs = require('fs');
const net = require('net');
const path = require('path');
const { spawn } = require('child_process');
const server = require('./lib/server');
const campus = require('./lib/campus');
const generate = require('./lib/generate');
const wings = require('./wings');
const { Facility, BOT } = require('./facility');
const { selftest } = require('./selftest');
const shards = require('./lib/shards');
const { Config } = require('./lib/config');
const shapes = require('./lib/shapes');
const companions = require('./lib/companions');

const DEFAULT_VERSION = '26.1.2';
const DEFAULT_PORT = 25590;
const REPO = path.resolve(__dirname, '..', '..');
const LOCAL = path.join(REPO, '.local-server');

/** The RCON port for a game port (only the dashboard uses it), or null past the last port. */
function rconPort(gamePort) {
  const port = gamePort + 10000;
  return port > 65535 ? null : port;
}

/** Whether something on this machine already accepts connections on a port. */
function portAnswers(port) {
  return new Promise((resolve) => {
    const sock = net.connect({ host: '127.0.0.1', port });
    const end = (answered) => { sock.destroy(); resolve(answered); };
    sock.setTimeout(1000, () => end(false));
    sock.on('connect', () => end(true));
    sock.on('error', () => end(false));
  });
}

/**
 * Tells the dashboard whether this lab takes commands: `starting` while the campus is generated,
 * `ready` in the hold, `selftest` while a self-test runs, `no-rcon` in a hold whose RCON is not
 * running (its port was taken, or the game port leaves none), `none` otherwise. The pid tells a
 * launcher still running from one that died; null removes the file if this launcher wrote it, so a
 * second launcher that fails on a running lab's folder does not take the first one's away.
 */
function consoleChannel(folder, state) {
  const file = path.join(folder, 'console-channel.json');
  if (state !== null) { fs.writeFileSync(file, JSON.stringify({ state, pid: process.pid })); return; }
  try {
    if (JSON.parse(fs.readFileSync(file, 'utf8')).pid === process.pid) fs.rmSync(file, { force: true });
  } catch { /* none, or not this launcher's */ }
}

function parseArgs(argv) {
  const a = { port: DEFAULT_PORT, selftest: false, build: true };
  // An option's value, refused if it is missing (the last word, or the next option): a missing
  // --cells or --fixed would otherwise reach every child as "undefined".
  const value = (i) => {
    const got = argv[i + 1];
    if (got === undefined || got.startsWith('--')) throw new Error(`${argv[i]} needs a value`);
    return got;
  };
  const whole = (name, s, min) => {
    const n = Number(s);
    if (!Number.isInteger(n) || n < min) throw new Error(`${name} takes a whole number from ${min}, not ${s}`);
    return n;
  };
  const list = (s) => s.split(',').map((t) => t.trim()).filter(Boolean);
  for (let i = 0; i < argv.length; i++) {
    const x = argv[i];
    if (x === '--java') a.java = value(i++);
    else if (x === '--jdk17') a.jdk17 = value(i++);
    else if (x === '--plugin') { a.plugin = value(i++); a.build = false; }
    else if (x === '--no-build') a.build = false;
    else if (x === '--port') a.port = whole('--port', value(i++), 1);
    else if (x === '--selftest') a.selftest = true;
    else if (x === '--versions') a.versions = list(value(i++));
    else if (x === '--keep-world') a.keepWorld = true;
    else if (x === '--cells') {
      a.cells = value(i++);
      // Refused here, before a server is started and a campus built for nothing.
      try { shards.cellMatcher(a.cells); } catch (e) { throw new Error(`--cells ${a.cells}: ${e.message}`); }
    }
    else if (x === '--quick') a.quick = true;
    else if (x === '--fixed') a.fixed = list(value(i++));
    else if (x === '--shards') a.shards = whole('--shards', value(i++), 1);
    else if (x === '--shard') {
      const m = /^(\d+)\/(\d+)$/.exec(value(i++));
      if (!m || Number(m[1]) < 1 || Number(m[1]) > Number(m[2])) throw new Error(`--shard takes k/n, not ${argv[i]}`);
      a.shard = { index: Number(m[1]) - 1, count: Number(m[2]) };
    }
    else if (x === '--plan') a.plan = value(i++);
    else if (x === '--report') a.report = value(i++);
    else if (x === '--tied') a.tied = true;
    else if (x === '--paper-build') a.paperBuild = whole('--paper-build', value(i++), 1);
    // `--with none` is a run with --with and no companion: the paired cells' run without any.
    else if (x === '--with') {
      a.with = list(value(i++)).filter((s) => s !== 'none');
      companions.expand(a.with); // a name it does not know is refused here, before anything starts
    }
    else if (x === '--plugin-cache') a.pluginCache = value(i++);
    else if (x === '--op') {
      a.op = list(value(i++));
      const bad = a.op.find((p) => !/^\w{1,16}$/.test(p));
      if (bad !== undefined) throw new Error(`--op takes player names, not ${bad}`);
    }
    else if (x === '--viewer') a.viewer = true;
    else if (x === '--viewer-port') a.viewerPort = whole('--viewer-port', value(i++), 1024);
    else if (x === '--shots') {
      // The list is optional: a bare --shots (or one followed by an option or the version) is all.
      const next = argv[i + 1];
      a.shots = next !== undefined && !next.startsWith('--') && !/^\d+\.\d+/.test(next) ? argv[++i] : 'all';
      require('./lib/shots').select(a.shots); // a name it does not know is refused here
    }
    else if (x === '--schematics') a.schematics = path.resolve(value(i++));
    else if (x === '--design') a.design = true;
    else if (x === '--design-check') { a.design = true; a.designCheck = true; }
    else if (x === '--design-export') { a.design = true; a.designExport = true; }
    else if (x === '--design-import') a.designImport = path.resolve(value(i++));
    else if (x === '--full') a.full = true;
    else if (x === '--accept-check-problems') a.acceptCheckProblems = true;
    else if (x === '--design-open') a.designOpen = true;
    else if (!x.startsWith('--') && !a.version) a.version = x;
    else throw new Error(`unknown argument ${x}`);
  }
  if (a.versions && !a.versions.length) throw new Error('--versions names no version');
  if ((a.versions || a.shards) && (a.viewer || a.shots || a.schematics || a.designImport)) throw new Error('--viewer, --shots, --schematics and --design-import take one version and one server');
  if (a.designImport && a.schematics) throw new Error('--design-import is a --schematics of its own: give one of them');
  if (a.design) {
    const design = require('./lib/design');
    if (a.version && a.version !== design.VERSION) throw new Error(`design mode runs Minecraft ${design.VERSION} only, not ${a.version}`);
    const clash = [['--selftest', a.selftest], ['--versions', a.versions], ['--shards', a.shards], ['--viewer', a.viewer], ['--shots', a.shots],
      ['--schematics', a.schematics], ['--design-import', a.designImport], ['--keep-world', a.keepWorld]].filter(([, on]) => on).map(([n]) => n);
    if (clash.length) throw new Error(`design mode runs no tests and keeps its own world: not with ${clash.join(', ')}`);
    if (a.designCheck && a.designExport) throw new Error('--design-check or --design-export, one at a time');
    a.version = design.VERSION;
  }
  if (a.full && !a.designExport) throw new Error('--full goes with --design-export');
  if (a.acceptCheckProblems && !a.designImport) throw new Error('--accept-check-problems goes with --design-import');
  if (a.designOpen && !a.design) throw new Error('--design-open goes with --design');
  a.version = a.version || DEFAULT_VERSION;
  return a;
}

function schematicsLib() {
  return require('./lib/schematics');
}

function pluginJar(args) {
  if (args.plugin) return path.resolve(args.plugin);
  if (!args.build) {
    const jar = path.join(REPO, 'target', 'WormholeXTreme.jar');
    if (!fs.existsSync(jar)) throw new Error(`--no-build, but there is no ${jar}`);
    return jar;
  }
  const jdk = args.jdk17 || server.findJava(17);
  console.log(`building the plugin with Maven on ${jdk || 'the default JDK'}`);
  return server.buildPlugin(REPO, jdk);
}

/**
 * Design mode's plugin jar: --plugin, else the one the design folder already has (a designer
 * passes -Plugin once), else a Maven build; without Maven, says where a jar comes from.
 */
function designPlugin(args, folder) {
  if (args.plugin) return path.resolve(args.plugin);
  const kept = path.join(folder, 'plugins', 'WormholeXTreme.jar');
  if (fs.existsSync(kept)) return kept;
  try {
    return pluginJar(args);
  } catch (e) {
    throw new Error(`${e.message}\n\nDesign mode needs the plugin's jar. Without Maven and a JDK 17, download WormholeXTreme.jar `
      + 'from https://github.com/khanjal/Wormhole-X-Treme/releases and pass it: lab.ps1 -Design -Plugin <jar>, lab.sh -d -p <jar>, '
      + 'or --plugin <jar>. Later sessions keep using it.');
  }
}

/**
 * Unpacks a design export (--design-import) into .local-server/imports/<name>/, fresh, and says
 * what it is; returns that folder for --schematics.
 */
function unpackDesign(zipFile, { acceptCheckProblems = false } = {}) {
  const design = require('./lib/design');
  if (!fs.existsSync(zipFile)) throw new Error(`--design-import ${zipFile} is not there`);
  const name = path.basename(zipFile).replace(/\.zip$/i, '');
  if (!/^[\w.-]+$/.test(name) || /^\.+$/.test(name)) throw new Error(`--design-import ${zipFile}: name the zip with letters, digits, dots, dashes or underscores`);
  const dir = path.join(LOCAL, 'imports', name);
  fs.rmSync(dir, { recursive: true, force: true });
  // Only what a run reads: never the worlds of a full export, nor anything else in it.
  const wanted = (n) => ['placements.json', 'manifest.json', 'check.txt'].includes(n) || /^[\w.-]+\.schem$/.test(n);
  require('./lib/zip').extractZip(zipFile, dir, wanted);
  const manifest = path.join(dir, 'manifest.json');
  if (!fs.existsSync(manifest) || !fs.existsSync(path.join(dir, 'placements.json'))) throw new Error(`${zipFile} is not a design export: no manifest.json and placements.json`);
  // A hand-edited zip could add an unguarded placement, pasted plainly: refused outright.
  const unguarded = schematicsLib().designPlacementProblems(dir);
  if (unguarded) throw new Error(`${zipFile}: ${unguarded}`);
  // The import's own guardrail (--schematics) is the real gate; the export's check report, which a
  // hand can also edit, is a second one the maintainer may override.
  const reported = design.reportProblems(fs.existsSync(path.join(dir, 'check.txt')) ? fs.readFileSync(path.join(dir, 'check.txt'), 'utf8') : '');
  if (reported !== 0 && !acceptCheckProblems) {
    throw new Error(`${zipFile}: its check.txt ${reported === null ? 'says nothing readable' : `reports ${reported} problem(s)`}; send it back to the designer, or pass --accept-check-problems to try it anyway`);
  }
  const m = JSON.parse(fs.readFileSync(manifest, 'utf8'));
  const c = design.clean;
  const commit = m.facility && typeof m.facility === 'object' ? c(m.facility.commit, 60) : '?';
  console.log(`design export by ${c(m.designer, 40)} on ${c(m.date, 40)} (Minecraft ${c(m.minecraft, 20)}, facility ${commit}): `
    + `${Array.isArray(m.areas) ? m.areas.length : '?'} areas, ${c(m.checkProblems, 10)} check problem(s) when exported; unpacked in ${dir}`);
  if (m.minecraft !== design.VERSION) console.log(`  WARNING: made on Minecraft ${c(m.minecraft, 20)}, not ${design.VERSION}, the version design mode runs`);
  const here = require('./lib/designmode').facilityCommit(REPO).replace(/\+changes$/, '');
  if (commit.replace(/\+changes$/, '') !== here) console.log(`  WARNING: made with facility ${commit}, and this checkout is ${here}: a protected volume that moved since is refused below if the design reaches it`);
  return dir;
}

/**
 * Design mode after the server is up: the first session generates the campus, its fixtures and
 * placeholders; then a check or an export from the command line, or a hold for the designer.
 * Returns the exit code.
 */
async function designSession({ args, fac, srv, folder, plugin, hold }) {
  const { DesignMode, facilityCommit, opsOf } = require('./lib/designmode');
  const design = require('./lib/design');
  const dm = new DesignMode({ srv, folder, repo: REPO, local: LOCAL });
  // Probe is opped only while it builds the fixtures; one left over from a session that died then goes now.
  const deopProbe = async () => {
    const r = await srv.run(`deop ${BOT}`);
    const words = r.lines.join(' ');
    if (!/no longer a server operator|Nothing changed/i.test(words)) throw new Error(`deop ${BOT}: ${words || 'no answer'}`);
    if (opsOf(folder).includes(BOT)) throw new Error(`${BOT} is still in ops.json after deop`);
  };
  if (opsOf(folder).includes(BOT)) { await deopProbe(); console.log(`design: ${BOT}'s op from an earlier session taken away`); }
  if (DesignMode.generated(folder)) {
    console.log(`design: the world generated ${dm.state().generated} is kept as it is`);
  } else {
    // The whitelist is on (server.properties) and nobody else is opped until this is done.
    console.log('design: generating the campus (the first session only; nobody can join until it is done)');
    const report = await fac.build();
    const failed = report.filter((r) => !r.ok);
    if (failed.length) throw new Error(`the campus did not build: ${failed.map((r) => `${r.fn}: ${r.detail}`).join('; ')}`);
    await srv.run(`whitelist add ${BOT}`);
    let fixtures;
    try {
      await fac.connectProbe();
      fixtures = await fac.fixtures({ stock: false });
    } finally {
      await fac.dismissProbe();
      await deopProbe();
      await srv.run(`whitelist remove ${BOT}`);
    }
    for (const f of fixtures) console.log(`  ${f.ok ? 'fixture' : 'FIXTURE FAILED'} ${f.id}: ${f.detail}`);
    await dm.finishGeneration({
      commit: facilityCommit(REPO), version: design.VERSION, plugin: path.basename(plugin),
      fixtureProblems: fixtures.filter((f) => !f.ok).map((f) => `${f.id}: ${f.detail}`),
    });
    await srv.run('whitelist off');
  }
  for (const name of args.op || []) {
    const r = await srv.run(`op ${name}`);
    console.log(`  op ${name}: ${r.lines.join(' ') || 'no answer'}`);
  }
  // Who made it: --op, else the server's ops; never a made-up name.
  const designer = (args.op && args.op[0]) || opsOf(folder).filter((n) => n !== BOT).join(', ');
  if (args.designExport && !designer) throw new Error('say who the designer is: --op <name> (lab.ps1 -Op, lab.sh -o)');
  if (args.designCheck) {
    const r = await dm.check('the console');
    console.log(`\n${r.report}\nthe report: ${r.file}`);
    return r.items.some((p) => p.kind !== 'stray') ? 1 : 0;
  }
  if (args.designExport) {
    const r = await dm.export(designer, { plugin: path.basename(plugin), full: Boolean(args.full) });
    console.log(`\nexported ${(r.bytes / 1024 / 1024).toFixed(1)} MB to ${r.file}`);
    for (const l of design.chatSummary(r.items, 20)) console.log(`  ${l}`);
    return 0;
  }
  console.log(`\nready: join localhost:${args.port} with Minecraft ${design.VERSION}${args.op ? ` as ${args.op.join(' or ')}` : ' (op yourself with --op)'}.`);
  console.log('Ops are in creative with WorldEdit. Say check, export (export full for the worlds too) or stop in chat; Ctrl+C here stops it too.');
  if (!args.designOpen) console.log('The server listens on 127.0.0.1 only (--design-open, lab.ps1 -Open, lab.sh -O to let others in).');
  let over = false;
  await new Promise((resolve) => {
    hold(resolve);
    const off = dm.listen({ onStop: (who) => { console.log(`design: ${who} said stop`); off(); resolve(); }, plugin: path.basename(plugin) });
    if (srv.exited !== null) resolve();
    srv.on('exit', (code) => { if (!over) console.error(`facility: the server exited (${code})`); resolve(); });
  });
  over = true;
  // An export or check still running finishes before the server stops.
  while (dm.busy) await new Promise((resolve) => { setTimeout(resolve, 500); });
  return 0;
}

/** The companion jars for a version (lib/companions.js): from the plugin cache, or downloaded once into .local-server/companions/. */
function companionsFor(args, version, names) {
  const cache = args.pluginCache ? path.resolve(args.pluginCache) : companions.defaultCache(REPO);
  if (args.pluginCache && !fs.existsSync(cache)) throw new Error(`--plugin-cache ${cache} is not there`);
  return companions.resolve(names, version, { cache, store: path.join(LOCAL, 'companions') });
}

/**
 * The peak memory (working set) of these processes and their children, and the machine's CPU
 * use, sampled every `everyMs`; returns { stop() -> { peakGb, peakCpu, meanCpu } }.
 */
function sampleResources(pids, everyMs = 10000) {
  const os = require('os');
  const { execFile } = require('child_process');
  let peakGb = 0;
  let peakCpu = 0;
  const cpus = [];
  let last = os.cpus();
  const tick = () => {
    const now = os.cpus();
    let busy = 0;
    let all = 0;
    now.forEach((c, i) => {
      const a = c.times;
      const b = last[i].times;
      const idle = a.idle - b.idle;
      const total = (a.user - b.user) + (a.nice - b.nice) + (a.sys - b.sys) + (a.irq - b.irq) + idle;
      busy += total - idle;
      all += total;
    });
    last = now;
    if (all > 0) { const pct = (100 * busy) / all; cpus.push(pct); peakCpu = Math.max(peakCpu, pct); }
    const list = pids().join(',');
    if (!list) return;
    const ps = process.platform === 'win32'
      ? ['powershell', ['-NoProfile', '-Command', `$ids=@(${list}); $p=Get-CimInstance Win32_Process | Where-Object { $ids -contains $_.ProcessId -or $ids -contains $_.ParentProcessId }; ($p | Measure-Object WorkingSetSize -Sum).Sum`]]
      : ['sh', ['-c', `ps -o rss= -p $(pgrep -d, -P ${list}),${list} | awk '{s+=$1*1024} END {print s}'`]];
    execFile(ps[0], ps[1], { windowsHide: true }, (err, out) => {
      const bytes = Number(String(out || '').trim());
      if (!err && bytes) peakGb = Math.max(peakGb, bytes / 1024 ** 3);
    });
  };
  const timer = setInterval(tick, everyMs);
  return {
    stop() {
      clearInterval(timer);
      const meanCpu = cpus.length ? cpus.reduce((a, b) => a + b, 0) / cpus.length : 0;
      return { peakGb, peakCpu, meanCpu };
    },
  };
}

/**
 * --versions and --shards: one child process per version and shard, all at once, each on its own
 * port (--port, +2, +4, ...) and in its own server folder; every line is prefixed with which it
 * is. Each shard writes its results to a report; they are merged per version into one summary.
 * Returns the exit code: 0 only if every child and every merged check passed.
 */
async function fanOut(args) {
  // Each child stops its own server however it ends, and watches its JVM with a watchdog of its
  // own. It is --tied: it holds a pipe from this launcher and stops when that closes, so a
  // launcher killed outright takes its children with it (on Windows, Node's job object kills
  // them at once, and their watchdogs their JVMs). A signal here closes the pipes, which
  // stops them as a first signal does; a second kills them, leaving their watchdogs to kill
  // their JVMs. A console Ctrl+C reaches the children directly as well. A signal before any
  // child is started (while the Paper jars are fetched) ends the run there.
  const children = [];
  let signals = 0;
  for (const sig of ['SIGINT', 'SIGTERM', 'SIGHUP']) {
    process.on(sig, () => {
      signals++;
      for (const c of children) {
        if (signals === 1) c.stdin.end();
        else c.kill('SIGKILL');
      }
    });
  }
  const jar = pluginJar(args);
  const versions = args.versions || [args.version];
  const n = args.shards || 1;
  // Fetched here, once per version: shards of one version starting at once would each download
  // the same jar over the others.
  for (const v of versions) {
    await server.ensurePaperJar(LOCAL, v, { build: args.paperBuild || null });
    if (signals) return 130;
  }
  // Companions likewise (and a companion a version cannot have is refused here, before any start).
  const withNames = args.with ? companions.expand(args.with) : null;
  for (const v of versions) {
    if (withNames) await companionsFor(args, v, withNames);
    if (signals) return 130;
  }
  fs.mkdirSync(LOCAL, { recursive: true });
  const work = fs.mkdtempSync(path.join(LOCAL, 'run-'));
  try {
    return await fanOutIn(work, { args, jar, versions, n, children, withNames, stopped: () => signals > 0 });
  } finally {
    fs.rmSync(work, { recursive: true, force: true });
  }
}

/** fanOut's children, in the work folder `work` (removed by fanOut however this ends). */
async function fanOutIn(work, { args, jar, versions, n, children, withNames, stopped }) {
  const jobs = [];
  for (const [vi, v] of versions.entries()) {
    let planFile = null;
    if (n > 1) {
      const names = shards.labels({ quick: args.quick, only: args.cells ? shards.cellMatcher(args.cells) : null, companions: withNames });
      const p = shards.plan(names, shards.loadTimes(LOCAL, v), n);
      planFile = path.join(work, `plan-${v}.json`);
      fs.writeFileSync(planFile, JSON.stringify(p));
      console.log(`${v}: ${names.length} matrix cells in ${n} shards, expected ${p.loads.map((x) => `${Math.round(x / 60)} min`).join(' / ')} of tests`);
    }
    for (let si = 0; si < n; si++) {
      jobs.push({ v, si, port: args.port + 2 * (vi * n + si), planFile, report: path.join(work, `report-${v}-${si + 1}.json`) });
    }
  }
  const t0 = Date.now();
  const sampler = sampleResources(() => children.filter((c) => c.exitCode === null).map((c) => c.pid));
  const runs = jobs.map((j) => new Promise((resolve) => {
    if (stopped()) { resolve({ ...j, code: null, report: null, why: 'not started: the launcher was stopped' }); return; }
    const tag = n > 1 ? `${j.v} ${j.si + 1}/${n}` : j.v;
    const child = [__filename, j.v, '--selftest', '--tied', '--plugin', jar, '--port', String(j.port), '--report', j.report];
    if (n > 1) child.push('--shard', `${j.si + 1}/${n}`, '--plan', j.planFile);
    if (args.java) child.push('--java', args.java);
    if (args.cells) child.push('--cells', args.cells);
    if (args.fixed) child.push('--fixed', args.fixed.join(','));
    if (args.quick) child.push('--quick');
    if (args.paperBuild) child.push('--paper-build', String(args.paperBuild));
    if (args.keepWorld) child.push('--keep-world');
    if (withNames) child.push('--with', withNames.join(',') || 'none');
    if (args.pluginCache) child.push('--plugin-cache', args.pluginCache);
    const p = spawn(process.execPath, child, { stdio: ['pipe', 'pipe', 'pipe'] });
    p.stdin.on('error', () => {});
    children.push(p);
    // One buffer per stream: a line split across two reads must not be spliced with the other's.
    const show = (line) => { if (!/DEP0040|trace-deprecation/.test(line)) console.log(`[${tag}] ${line}`); };
    const reader = () => {
      let buf = '';
      const read = (d) => {
        buf += d.toString();
        let k;
        while ((k = buf.indexOf('\n')) >= 0) {
          show(buf.slice(0, k));
          buf = buf.slice(k + 1);
        }
      };
      read.flush = () => { if (buf) show(buf); buf = ''; };
      return read;
    };
    const out = reader();
    const err = reader();
    p.stdout.on('data', out);
    p.stderr.on('data', err);
    let settled = false;
    const finish = (code, why) => {
      if (settled) return;
      settled = true;
      out.flush();
      err.flush();
      let report = null;
      if (fs.existsSync(j.report)) {
        try { report = JSON.parse(fs.readFileSync(j.report, 'utf8')); } catch (e) { why = `its report is not JSON (${e.message})`; }
      }
      resolve({ ...j, code, report, why });
    };
    // 'close', not 'exit': the child's last lines (its summary) are still in its pipes at exit.
    p.on('close', (code) => finish(code));
    p.on('error', (e) => finish(null, `could not be started: ${e.message}`));
  }));
  const done = await Promise.all(runs);
  const use = sampler.stop();
  const wall = (Date.now() - t0) / 1000;
  let allOk = true;
  for (const v of versions) {
    const mine = done.filter((d) => d.v === v);
    const results = mine.flatMap((d) => (d.report ? d.report.results : []));
    const known = mine.flatMap((d) => (d.report ? d.report.known : []));
    const missing = mine.filter((d) => !d.report);
    console.log(`\nfacility self-test on ${v}${n > 1 ? ` (${n} shards)` : ''}:`);
    for (const sec of [...new Set(results.map((r) => r.section))]) {
      const in_ = results.filter((r) => r.section === sec);
      const failed = in_.filter((r) => !r.ok);
      console.log(`  ${failed.length ? 'FAIL' : 'PASS'}  ${sec} (${in_.length - failed.length}/${in_.length})${failed.length ? ` — ${failed.map((f) => `${f.name}: ${f.detail}`).join('; ')}` : ''}`);
    }
    for (const d of missing) console.log(`  FAIL  shard ${d.si + 1} wrote no report (exit ${d.code}${d.why ? `; ${d.why}` : ''})`);
    if (known.length) {
      console.log(`known plugin failures on ${v} (expected, not hidden):`);
      for (const k of known) console.log(`  KNOWN  ${k.label}: ${k.note}`);
    }
    const bad = results.filter((r) => !r.ok).length + missing.length;
    const slowest = Math.max(...mine.map((d) => (d.report ? d.report.testMs : 0))) / 1000;
    console.log(`self-test on ${v}: ${bad ? `${bad} FAIL` : 'all PASS'} of ${results.length} checks (${known.length} known plugin failures); `
      + `slowest shard's self-test ${slowest.toFixed(0)} s`);
    if (bad || mine.some((d) => d.code !== 0)) allOk = false;
    // What this run measured, for the next run's split.
    const times = Object.assign({}, ...mine.map((d) => (d.report ? d.report.times : {})));
    const first = mine.find((d) => d.si === 0 && d.report);
    const once = first ? ['transit', 'plates', 'boards', 'console'].reduce((a, sec) => a + (first.report.sectionMs[sec] || 0), 0) / 1000 : 0;
    if (Object.keys(times).length) shards.saveTimes(LOCAL, v, times, n > 1 ? Math.round(once) : undefined);
  }
  console.log(`\nwall ${(wall / 60).toFixed(1)} min for ${jobs.length} server(s); peak memory ${use.peakGb.toFixed(1)} GB (the servers and their launchers), `
    + `peak CPU ${use.peakCpu.toFixed(0)}%, mean ${use.meanCpu.toFixed(0)}% (the whole machine)`);
  return allOk ? 0 : 1;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.versions || (args.shards > 1 && !args.shard)) return fanOut(args);
  const { version } = args;
  // One folder per version and port, so runs side by side (--versions, or two terminals) never
  // share a world: a fresh run deletes the worlds of the folder it uses. Design mode has its own,
  // which no test run uses, so its placeholders never reach one.
  const folder = path.join(LOCAL, `${args.design ? 'design' : 'facility'}-${version}${args.port === DEFAULT_PORT ? '' : `-${args.port}`}`);
  if (args.design) {
    const { DesignMode, STATE_DIR } = require('./lib/designmode');
    const made = DesignMode.generated(folder);
    if ((args.designCheck || args.designExport) && !made) throw new Error(`there is no design world in ${folder} yet: start design mode first (--design)`);
    // The first session starts from nothing; every later one keeps the designer's world.
    args.keepWorld = made;
    if (!made) {
      // A session that died while generating left its state, ops and whitelist: start them over too.
      fs.rmSync(path.join(folder, STATE_DIR), { recursive: true, force: true });
      for (const f of ['ops.json', 'whitelist.json']) fs.rmSync(path.join(folder, f), { force: true });
    }
    args.designMade = made;
    args.with = [...new Set([...(args.with || []), 'worldedit'])];
  }
  if (args.designImport) {
    args.schematics = unpackDesign(args.designImport, { acceptCheckProblems: Boolean(args.acceptCheckProblems) });
    const wanted = schematicsLib().forVersion(schematicsLib().placements(args.schematics), version).use;
    if (wanted.length) args.with = [...new Set([...(args.with || []), 'worldedit'])];
  }
  const jar = await server.ensurePaperJar(LOCAL, version, { build: args.paperBuild || null });
  const plugin = args.design ? designPlugin(args, folder) : pluginJar(args);
  // Companions before Java: the JDK is the highest any jar here needs, Paper's own included.
  const withNames = args.with ? companions.expand(args.with) : null;
  const extras = withNames ? await companionsFor(args, version, withNames) : [];
  const need = companions.javaNeeded(version, [{ name: 'WormholeXTreme', java: companions.classJava(plugin) }, ...extras]);
  const java = args.java || companions.findJavaAtLeast(need.major);
  if (!java) throw new Error(`no Java ${need.major}+ found (${need.why.join('; ')}); pass --java`);
  const javaMajor = server.checkJava(java, version, need.major, need.why.join('; '));
  // The viewer and the schematics are refused here, before a server is started for nothing.
  const viewer = args.viewer || args.shots ? require('./lib/viewer') : null;
  if (viewer) viewer.assetVersion(version);
  const webPort = viewer ? args.viewerPort || viewer.viewerPort(args.port) : null;
  if (args.shots && !require('./lib/shots').findBrowser()) throw new Error('--shots needs Chrome or Edge installed, or WX_BROWSER naming a Chromium-based browser');
  const schematics = schematicsLib();
  let placed = [];
  if (args.schematics) {
    if (!fs.existsSync(args.schematics)) throw new Error(`--schematics ${args.schematics} is not there`);
    const { use: list, skipped } = schematics.forVersion(schematics.placements(args.schematics), version);
    // A design's blocks may be newer than this version: it runs on the plain campus instead.
    if (skipped.length) console.log(`schematics: ${skipped.length} left out on ${version} (they need ${[...new Set(skipped.map((p) => p.minVersion))].join(', ')} or later): ${skipped.map((p) => p.file).join(', ')}`);
    if (list.length && !(withNames || []).includes('worldedit')) throw new Error('--schematics pastes with WorldEdit: add --with worldedit');
    const checked = await schematics.check(list, version);
    if (checked.problems.length) throw new Error(`the decoration guardrail refuses what --schematics would paste:\n  ${checked.problems.join('\n  ')}`);
    placed = checked.placed;
    console.log(`schematics: ${placed.length} to paste from ${args.schematics}, each clear of the guardrail`);
  }
  // A lab already running here would have its worlds, plugins and RCON password rewritten under it.
  if (await portAnswers(args.port)) throw new Error(`port ${args.port} already answers: is this lab running already? Nothing in ${folder} was touched`);
  // The settings journal describes the plugin's config file, so it goes when that does.
  if (!args.keepWorld) {
    server.freshWorlds(folder, { pluginData: true });
    fs.rmSync(path.join(folder, Config.JOURNAL), { force: true });
  }
  // Survival by default: a tester is put in adventure by the welcome, and the self-test's check
  // of that would pass without it if the server's default were adventure already.
  // Design mode: creative and peaceful, nothing stocked.
  server.prepareFolder(folder, { port: args.port, layers: campus.FLAT_LAYERS, seed: campus.SEED, gamemode: args.design ? 'creative' : 'survival', viewDistance: 10, mobs: !args.design });
  // Every server listens on 127.0.0.1 only, unless --design-open: RCON binds to server-ip, or to
  // every interface when it is empty.
  const extra = [];
  if (!args.designOpen) extra.push('server-ip=127.0.0.1');
  // The launcher's console commands (thousands of fences) are not shown to a designer who is an op.
  // Design mode: nobody joins until the campus is generated.
  if (args.design) extra.push('broadcast-console-to-ops=false', `white-list=${!args.designMade}`, `enforce-whitelist=${!args.designMade}`);
  // The dashboard's command box talks RCON, with a password made for this run: on a hand lab's
  // hold only, never during a self-test (whose fences it would interleave with) or on a design server.
  const hold = !args.design && !args.selftest && !(args.shots && !args.viewer);
  const rcon = hold && rconPort(args.port) !== null;
  if (rcon) extra.push('enable-rcon=true', `rcon.port=${rconPort(args.port)}`, `rcon.password=${crypto.randomBytes(24).toString('hex')}`);
  else extra.push('enable-rcon=false');
  if (hold && !rcon) console.log(`The dashboard cannot send commands here: game port ${args.port} leaves no RCON port (port + 10000).`);
  fs.appendFileSync(path.join(folder, 'server.properties'), `${extra.join('\n')}\n`);
  let channel = 'none';
  if (args.selftest) channel = 'selftest';
  else if (rcon) channel = 'starting';
  else if (hold) channel = 'no-rcon';
  if (path.resolve(plugin) !== path.resolve(folder, 'plugins', 'WormholeXTreme.jar')) server.installPlugin(folder, plugin);
  // Always, with or without --with: a run without a companion takes out what an earlier one put
  // in, jars and the Wormhole settings switched on for them.
  const switches = Object.assign({}, ...extras.map((c) => companions.SWITCHES[c.name] || {}));
  const { removed, unseeded } = companions.install(folder, extras, { fresh: !args.keepWorld, switches });
  if (removed.length) console.log(`companions taken out (installed by an earlier run, not wanted by this one): ${removed.join(', ')}`);
  if (unseeded.length) console.log(`Wormhole settings an earlier --with run switched on, put back: ${unseeded.join(', ')}`);
  const mapPort = extras.some((c) => c.name === 'dynmap') ? companions.dynmapPort(args.port) : null;
  if (mapPort) companions.configureDynmap(folder, extras.find((c) => c.name === 'dynmap').jar, mapPort);
  // The test shape (assets/Lab.shape), read at startup; its diamond frame makes the Diamond group.
  shapes.installTestShapes(folder);
  if (placed.length) schematics.install(folder, placed);
  const manifest = generate.writeFacilityPack(path.join(folder, 'world'), version);
  const chunks = wings.forceloadChunks();

  console.log(`facility: Paper ${version} on Java ${javaMajor} (${need.why.join('; ')}), port ${args.port}, ${folder}`);
  for (const c of extras) console.log(`  companion ${c.plugin} ${c.version}: ${c.file}, SHA-256 ${c.sha256}, Java ${c.java || '?'}, from ${c.from}`);
  if (Object.keys(switches).length) console.log(`  Wormhole settings for them: ${Object.entries(switches).map(([k, v]) => `${k}: ${v}`).join(', ')}`);
  if (mapPort) console.log(`  Dynmap web map: http://localhost:${mapPort}/`);
  const srv = new server.Server({ jar, java, folder, version, memory: '3G' });
  if (server.echoOn(process.env.WX_ECHO)) srv.on('line', (l) => console.log(`  | ${l}`));
  const fac = new Facility({ srv, version, manifest, port: args.port, fixed: args.fixed || [], companions: withNames ? extras : null, mapPort });
  // However the launcher goes, the server goes with it: a signal ends hold mode or the run and
  // stops the server, a second one kills it, and an exit any other way kills a JVM still
  // running, so no server is left holding the port and the world folder.
  let holding = null;
  let stopping = false;
  let web = null;
  const shutDown = async () => {
    stopping = true;
    consoleChannel(folder, null);
    if (web) await web.close().catch(() => {});
    // Bounded: its bossbar removals queue behind whatever run is in flight.
    await Promise.race([fac.close().catch(() => {}), new Promise((resolve) => { setTimeout(resolve, 10000).unref(); })]);
    await srv.stop();
  };
  const stopFor = server.tieToProcess(srv, () => {
    if (holding) { holding(); return; }
    if (stopping) return;
    shutDown().catch(() => {}).then(() => process.exit(130));
  });
  if (args.tied) {
    // A --versions or --shards launcher's child: the end of its pipe is a stop, like a first signal, and a
    // launcher that has gone reads nothing this one writes.
    for (const s of [process.stdout, process.stderr]) s.on('error', () => {});
    process.stdin.on('end', () => { if (!stopping) stopFor('the parent launcher closed the pipe'); });
    process.stdin.on('error', () => { if (!stopping) stopFor('the parent launcher went'); });
    process.stdin.resume();
  }
  let exit = 0;
  // A stray rejection is a bug in the facility: log it, fail the run, and still stop the server.
  const stray = [];
  process.on('unhandledRejection', (e) => { stray.push(e); console.error(`facility: unhandled ${e && e.stack ? e.stack : e}`); });
  try {
    const t0 = Date.now();
    // Paper runs on without RCON when its port is taken, and says so only by not printing this.
    let rconUp = false;
    const rconLine = new RegExp(`RCON running on 127\\.0\\.0\\.1:${rconPort(args.port)}$`);
    const watchRcon = (l) => { if (rconLine.test(l)) rconUp = true; };
    if (rcon) srv.on('line', watchRcon);
    await srv.start();
    srv.off('line', watchRcon);
    if (rcon && !rconUp) {
      channel = 'no-rcon';
      console.error(`facility: RCON did not start on port ${rconPort(args.port)} (taken?), so the dashboard cannot send commands here`);
    }
    // Only once this launcher's server holds the port: a second launcher on a running lab's folder fails before this.
    consoleChannel(folder, channel);
    console.log(`server up in ${((Date.now() - t0) / 1000).toFixed(1)} s`);
    const setup = await fac.prepare();
    // Design mode ops its players once the campus is generated (designSession): an op passes the whitelist.
    for (const name of args.design ? [] : args.op || []) {
      const r = await srv.run(`op ${name}`);
      console.log(`  op ${name}: ${r.lines.join(' ') || 'no answer'}`);
    }
    if (args.design) {
      for (const p of setup) console.log(`  setup problem: ${p}`);
      exit = await designSession({ args, fac, srv, folder, plugin, hold: (resolve) => { holding = resolve; } });
      return exit;
    }
    if (mapPort) {
      const web = await fac.mapWebUp();
      if (!web.ok) setup.push(`Dynmap's web map: ${web.detail}`);
      console.log(`  Dynmap web map: ${web.detail}`);
    }
    for (const p of setup) console.log(`  setup problem: ${p}`);
    const tb = Date.now();
    const report = await fac.build();
    const buildMs = Date.now() - tb;
    const blocks = report.reduce((n, r) => n + r.blocks, 0);
    const total = Object.values(chunks).reduce((a, b) => a + b, 0);
    console.log(`generation: ${report.length} wings, ${blocks} blocks in ${buildMs} ms after ${fac.loadMs} ms of chunk loading; `
      + `forceloaded ${total} chunks (${Object.entries(chunks).map(([d, n]) => `${d.replace('minecraft:', '')} ${n}`).join(', ')})`);
    if (placed.length) {
      for (const r of await schematics.paste(srv, placed)) {
        console.log(`  ${r.ok ? 'pasted' : 'PASTE FAILED'} ${r.file}: ${r.detail}`);
        if (!r.ok) setup.push(`schematic ${r.file}: ${r.detail}`);
      }
    }
    await fac.connectProbe();
    if (viewer) {
      web = await viewer.startViewer(fac.probe.bot, { port: webPort, log: console.log });
      if (args.viewer) console.log(`  viewer (prismarine-viewer ${viewer.PV_VERSION}, ${web.assets} assets): ${web.url} orbits Probe, ${web.url}first/ is through its eyes`);
      // A restart (a Map or Region Desk cell) brings a new Probe: serve that one, on the same port.
      // close() resolves once the port is free, and a listen still finding it held retries.
      fac.afterRestart.push(() => {
        const old = web;
        web = null;
        Promise.resolve(old && old.close())
          .then(() => viewer.startViewer(fac.probe.bot, { port: webPort, log: console.log }))
          .then((w) => { web = w; })
          .catch((e) => console.error(`facility: the viewer did not come back after the restart: ${e.message}`));
      });
    }
    await fac.openConsole();
    const tf = Date.now();
    const fixtures = await fac.fixtures();
    for (const f of fixtures) console.log(`  ${f.ok ? 'fixture' : 'FIXTURE FAILED'} ${f.id}: ${f.detail}`);
    console.log(`fixtures in ${Date.now() - tf} ms`);
    await fac.refreshBoards();
    // Every shot is a check: a bad one fails the run, whether it then stops, self-tests or holds.
    let shotChecks = [];
    if (args.shots) {
      const shotsLib = require('./lib/shots');
      const dir = path.join(LOCAL, 'shots', version);
      const shots = await shotsLib.takeShots(fac, web, shotsLib.select(args.shots), dir);
      shotChecks = shotsLib.asResults(shots);
      const shotsBad = shots.filter((x) => !x.ok).length;
      console.log(`\nshots on ${version}: ${shots.length - shotsBad} of ${shots.length} drawn, in ${dir}`);
      for (const x of shots) if (x.file) console.log(`  ${x.file}`);
    }
    const shotsFailed = shotChecks.some((r) => !r.ok);

    if (args.shots && !args.selftest && !args.viewer) {
      exit = shotsFailed || setup.length || stray.length ? 1 : 0;
    } else if (args.selftest) {
      const ts = Date.now();
      let shard = null;
      if (args.shard) {
        const planned = JSON.parse(fs.readFileSync(args.plan, 'utf8'));
        shard = { ...args.shard, cells: new Set(planned.shards[args.shard.index]) };
        console.log(`shard ${args.shard.index + 1}/${args.shard.count}: ${shard.cells.size} matrix cells`);
      }
      const results = await selftest(fac, {
        buildReport: report, fixtures, only: args.cells ? shards.cellMatcher(args.cells) : null, fixed: args.fixed || [], quick: Boolean(args.quick), shard,
        companions: withNames,
      });
      const testMs = Date.now() - ts;
      if (setup.length) results.push({ section: 'setup', name: 'setup', ok: false, detail: setup.join('; ') });
      results.push(...shotChecks);
      const sections = [...new Set(results.map((r) => r.section))];
      console.log(`\nfacility self-test on ${version}:`);
      for (const s of sections) {
        const mine = results.filter((r) => r.section === s);
        const failed = mine.filter((r) => !r.ok);
        console.log(`  ${failed.length ? 'FAIL' : 'PASS'}  ${s} (${mine.length - failed.length}/${mine.length})${failed.length ? ` — ${failed.map((f) => `${f.name}: ${f.detail}`).join('; ')}` : ''}`);
      }
      const bad = results.filter((r) => !r.ok).length;
      if (results.known.length) {
        console.log(`known plugin failures on ${version} (expected, not hidden):`);
        for (const k of results.known) console.log(`  KNOWN  ${k.label}: ${k.note}`);
      }
      console.log(`self-test on ${version}: ${bad ? `${bad} FAIL` : 'all PASS'} of ${results.length} checks (${results.known.length} known plugin failures); `
        + `self-test ${(testMs / 1000).toFixed(0)} s, generation ${buildMs} ms, ${total} chunks forceloaded`);
      exit = bad || stray.length ? 1 : 0;
      if (args.report) {
        fs.writeFileSync(args.report, JSON.stringify({
          version, shard: args.shard || null, testMs, results: results.map((r) => ({ section: r.section, name: r.name, ok: r.ok, detail: r.detail })),
          known: results.known, times: results.times, sectionMs: results.sectionMs,
        }));
      } else if (!args.cells) {
        shards.saveTimes(LOCAL, version, results.times);
      }
    } else {
      if (channel === 'starting') consoleChannel(folder, 'ready');
      console.log(`\nready: join localhost:${args.port} with Minecraft ${version} under any name.`);
      if (mapPort) console.log(`The Dynmap web map is at http://localhost:${mapPort}/`);
      if (args.viewer && web) console.log(`The viewer is at ${web.url} (orbit) and ${web.url}first/ (Probe's eyes)`);
      console.log('You arrive in the atrium in adventure mode; say ! or click Console. Say "stop" in chat, or press Ctrl+C, to end.');
      await new Promise((resolve) => {
        holding = resolve;
        // Probe again after a restart (a Map or Region Desk cell restarts the server): the old
        // one leaving then is not the end of the hold.
        const listen = (bot) => {
          bot.on('chat', (username, message) => { if (username !== BOT && /^stop[.!]?$/i.test(message.trim())) resolve(); });
          // Without Probe nobody hears "stop": say so and end rather than hang, whether it left
          // now or before the hold began.
          const gone = (reason) => { if (fac.restarting) return; console.error(`facility: ${BOT} left (${reason}); stopping`); resolve(); };
          if (bot.ended) gone(bot.ended); else bot.once('end', gone);
        };
        listen(fac.probe.bot);
        fac.afterRestart.push(() => listen(fac.probe.bot));
        if (srv.exited !== null) resolve();
        srv.on('exit', (code) => { if (srv.restarting) return; console.error(`facility: the server exited (${code})`); resolve(); });
      });
      holding = null;
      if (shotsFailed) console.error('facility: a shot failed (above), so the run fails');
      exit = shotsFailed ? 1 : 0;
    }
  } catch (e) {
    console.error(`facility: ${e.stack || e}`);
    exit = 1;
  } finally {
    await shutDown();
  }
  return exit;
}

main().then((code) => process.exit(code)).catch((e) => { console.error(e); process.exit(2); });
