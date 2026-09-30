'use strict';
// The Wormhole Research Facility launcher. A local pre-release check: it never runs in GitHub.
//
//   node scripts/facility/run-facility.js [version]            build the campus and hold for a tester
//   node scripts/facility/run-facility.js [version] --selftest run the self-test, exit 1 on any FAIL
//   node scripts/facility/run-facility.js --selftest --quick --versions 1.20.4,26.1.2   (in parallel)
//
// Options:
//   --java <path>        java for the server (default: a JDK of the version's major, found by
//                        JAVA<major>_HOME or in the usual install folders)
//   --plugin <jar>       use this plugin jar instead of building one
//   --no-build           use target/WormholeXTreme.jar as it is
//   --jdk17 <path>       java for the Maven build (default: a JDK 17 found the same way)
//   --port <n>           server port (default 25590); another port gets its own server folder
//   --keep-world         keep the world from the last run instead of starting fresh
//   --quick              self-test with the short matrix (the cells marked quick): a few minutes
//   --cells <regex>      self-test only the matrix cells whose names match
//   --fixed <issues>     the plugin jar carries these fixes (e.g. 491): their known-failure
//                        cells are expected to pass
//   --paper-build <n>    use this Paper build instead of the newest stable one (still checked)
//   --tied               (set by --versions for its children) stop when stdin closes
//
// The server folder is .local-server/facility-<version>/. In hold mode, say "stop" in chat or
// press Ctrl+C to shut it down; Ctrl+C again kills the server if it will not stop.

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const server = require('./lib/server');
const campus = require('./lib/campus');
const generate = require('./lib/generate');
const wings = require('./wings');
const { Facility, BOT } = require('./facility');
const { selftest } = require('./selftest');
const { Config } = require('./lib/config');

const DEFAULT_VERSION = '26.1.2';
const DEFAULT_PORT = 25590;
const REPO = path.resolve(__dirname, '..', '..');
const LOCAL = path.join(REPO, '.local-server');

function parseArgs(argv) {
  const a = { port: DEFAULT_PORT, selftest: false, build: true };
  for (let i = 0; i < argv.length; i++) {
    const x = argv[i];
    if (x === '--java') a.java = argv[++i];
    else if (x === '--jdk17') a.jdk17 = argv[++i];
    else if (x === '--plugin') { a.plugin = argv[++i]; a.build = false; }
    else if (x === '--no-build') a.build = false;
    else if (x === '--port') a.port = Number(argv[++i]);
    else if (x === '--selftest') a.selftest = true;
    else if (x === '--versions') a.versions = argv[++i].split(',').map((s) => s.trim()).filter(Boolean);
    else if (x === '--keep-world') a.keepWorld = true;
    else if (x === '--cells') a.cells = argv[++i];
    else if (x === '--quick') a.quick = true;
    else if (x === '--fixed') a.fixed = argv[++i].split(',').map((s) => s.trim()).filter(Boolean);
    else if (x === '--tied') a.tied = true;
    else if (x === '--paper-build') {
      a.paperBuild = Number(argv[++i]);
      if (!Number.isInteger(a.paperBuild) || a.paperBuild <= 0) throw new Error(`--paper-build takes a build number, not ${argv[i]}`);
    }
    else if (!x.startsWith('--') && !a.version) a.version = x;
    else throw new Error(`unknown argument ${x}`);
  }
  a.version = a.version || DEFAULT_VERSION;
  return a;
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
 * --versions: one child process per version, all at once, each on its own port (--port, +2,
 * +4, ...) and in its own server folder; every line is prefixed with its version. Returns the
 * exit code: 0 only if every version passed.
 */
async function acrossVersions(args) {
  const jar = pluginJar(args);
  // Each child stops its own server however it ends, and watches its JVM with a watchdog of its
  // own. It is --tied: it holds a pipe from this launcher and stops when that closes, so a
  // launcher killed outright takes its children with it (on Windows, Node's job object kills
  // them at once, and their watchdogs their JVMs). A signal here closes the pipes, which
  // stops them as a first signal does; a second kills them, leaving their watchdogs to kill
  // their JVMs. A console Ctrl+C reaches the children directly as well.
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
  const runs = args.versions.map((v, i) => new Promise((resolve) => {
    const child = [__filename, v, '--selftest', '--tied', '--plugin', jar, '--port', String(args.port + 2 * i)];
    if (args.java) child.push('--java', args.java);
    if (args.cells) child.push('--cells', args.cells);
    if (args.fixed) child.push('--fixed', args.fixed.join(','));
    if (args.quick) child.push('--quick');
    if (args.paperBuild) child.push('--paper-build', String(args.paperBuild));
    const p = spawn(process.execPath, child, { stdio: ['pipe', 'pipe', 'pipe'] });
    p.stdin.on('error', () => {});
    children.push(p);
    let tail = '';
    // One buffer per stream: a line split across two reads must not be spliced with the other's.
    const reader = () => {
      let buf = '';
      return (d) => {
        buf += d.toString();
        let n;
        while ((n = buf.indexOf('\n')) >= 0) {
          const line = buf.slice(0, n);
          buf = buf.slice(n + 1);
          if (!/DEP0040|trace-deprecation/.test(line)) console.log(`[${v}] ${line}`);
          tail = (tail + line + '\n').slice(-4000);
        }
      };
    };
    p.stdout.on('data', reader());
    p.stderr.on('data', reader());
    p.on('exit', (c) => resolve({ v, code: c, tail }));
  }));
  const summary = await Promise.all(runs);
  console.log('\nfacility self-test across versions:');
  for (const s of summary) {
    const line = /self-test on \S+: (.*)/.exec(s.tail);
    console.log(`  ${s.code === 0 ? 'PASS' : 'FAIL'}  ${s.v}${line ? `  ${line[1]}` : ''}`);
  }
  return summary.every((s) => s.code === 0) ? 0 : 1;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.versions) return acrossVersions(args);
  const { version } = args;
  const java = args.java || server.findJava(server.requiredJava(version));
  if (!java) throw new Error(`no Java ${server.requiredJava(version)} found for ${version}; pass --java`);
  const javaMajor = server.checkJava(java, version);
  const jar = await server.ensurePaperJar(LOCAL, version, { build: args.paperBuild || null });
  const plugin = pluginJar(args);
  // One folder per version and port, so runs side by side (--versions, or two terminals) never
  // share a world: a fresh run deletes the worlds of the folder it uses.
  const folder = path.join(LOCAL, args.port === DEFAULT_PORT ? `facility-${version}` : `facility-${version}-${args.port}`);

  // The settings journal describes the plugin's config file, so it goes when that does.
  if (!args.keepWorld) {
    server.freshWorlds(folder, { pluginData: true });
    fs.rmSync(path.join(folder, Config.JOURNAL), { force: true });
  }
  // Survival by default: a tester is put in adventure by the welcome, and the self-test's check
  // of that would pass without it if the server's default were adventure already.
  server.prepareFolder(folder, { port: args.port, layers: campus.FLAT_LAYERS, seed: campus.SEED, gamemode: 'survival', viewDistance: 10, mobs: true });
  server.installPlugin(folder, plugin);
  const manifest = generate.writeFacilityPack(path.join(folder, 'world'), version);
  const chunks = wings.forceloadChunks();

  console.log(`facility: Paper ${version} on Java ${javaMajor}, port ${args.port}, ${folder}`);
  const srv = new server.Server({ jar, java, folder, version, memory: '3G' });
  if (server.echoOn(process.env.WX_ECHO)) srv.on('line', (l) => console.log(`  | ${l}`));
  const fac = new Facility({ srv, version, manifest, port: args.port });
  // However the launcher goes, the server goes with it: a signal ends hold mode or the run and
  // stops the server, a second one kills it, and an exit any other way kills a JVM still
  // running, so no server is left holding the port and the world folder.
  let holding = null;
  let stopping = false;
  const shutDown = async () => {
    stopping = true;
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
    // A --versions launcher's child: the end of its pipe is a stop, like a first signal, and a
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
    await srv.start();
    console.log(`server up in ${((Date.now() - t0) / 1000).toFixed(1)} s`);
    const setup = await fac.prepare();
    for (const p of setup) console.log(`  setup problem: ${p}`);
    const tb = Date.now();
    const report = await fac.build();
    const buildMs = Date.now() - tb;
    const blocks = report.reduce((n, r) => n + r.blocks, 0);
    const total = Object.values(chunks).reduce((a, b) => a + b, 0);
    console.log(`generation: ${report.length} wings, ${blocks} blocks in ${buildMs} ms after ${fac.loadMs} ms of chunk loading; `
      + `forceloaded ${total} chunks (${Object.entries(chunks).map(([d, n]) => `${d.replace('minecraft:', '')} ${n}`).join(', ')})`);
    await fac.connectProbe();
    await fac.openConsole();
    const tf = Date.now();
    const fixtures = await fac.fixtures();
    for (const f of fixtures) console.log(`  ${f.ok ? 'fixture' : 'FIXTURE FAILED'} ${f.id}: ${f.detail}`);
    console.log(`fixtures in ${Date.now() - tf} ms`);
    await fac.refreshBoards();

    if (args.selftest) {
      const ts = Date.now();
      const results = await selftest(fac, {
        buildReport: report, fixtures, only: args.cells ? new RegExp(args.cells) : null, fixed: args.fixed || [], quick: Boolean(args.quick),
      });
      const testMs = Date.now() - ts;
      if (setup.length) results.push({ section: 'setup', name: 'setup', ok: false, detail: setup.join('; ') });
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
    } else {
      console.log(`\nready: join localhost:${args.port} with Minecraft ${version} under any name.`);
      console.log('You arrive in the atrium in adventure mode; say ! or click Console. Say "stop" in chat, or press Ctrl+C, to end.');
      await new Promise((resolve) => {
        holding = resolve;
        const bot = fac.probe.bot;
        bot.on('chat', (username, message) => { if (username !== BOT && /^stop[.!]?$/i.test(message.trim())) resolve(); });
        // Without Probe nobody hears "stop": say so and end rather than hang, whether it left
        // now or before the hold began.
        const gone = (reason) => { console.error(`facility: ${BOT} left (${reason}); stopping`); resolve(); };
        if (bot.ended) gone(bot.ended); else bot.once('end', gone);
        if (srv.exited !== null) resolve();
        srv.once('exit', (code) => { console.error(`facility: the server exited (${code})`); resolve(); });
      });
      holding = null;
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
