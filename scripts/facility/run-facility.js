'use strict';
// The Wormhole Research Facility launcher. Local only: there is no CI job for it.
//
//   node scripts/facility/run-facility.js [version]            build the campus and hold for a tester
//   node scripts/facility/run-facility.js [version] --selftest run the self-test, exit 1 on any FAIL
//   node scripts/facility/run-facility.js --selftest --versions 1.20.4,1.21.11,26.1.2
//
// Options:
//   --java <path>        java for the server (default: a JDK of the version's major, found by
//                        JAVA<major>_HOME or in the usual install folders)
//   --plugin <jar>       use this plugin jar instead of building one
//   --no-build           use target/WormholeXTreme.jar as it is
//   --jdk17 <path>       java for the Maven build (default: a JDK 17 found the same way)
//   --port <n>           server port (default 25590)
//   --keep-world         keep the world from the last run instead of starting fresh
//
// The server folder is .local-server/facility-<version>/. In hold mode, say "stop" in chat or
// press Ctrl+C to shut it down.

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const server = require('./lib/server');
const campus = require('./lib/campus');
const generate = require('./lib/generate');
const wings = require('./wings');
const { Facility, BOT } = require('./facility');
const { selftest } = require('./selftest');

const DEFAULT_VERSION = '26.1.2';
const REPO = path.resolve(__dirname, '..', '..');
const LOCAL = path.join(REPO, '.local-server');

function parseArgs(argv) {
  const a = { port: 25590, selftest: false, build: true };
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

/** --versions: one child process per version, so each gets a clean process; returns the exit code. */
async function acrossVersions(args) {
  const jar = pluginJar(args);
  const summary = [];
  for (const v of args.versions) {
    const child = [__filename, v, '--selftest', '--plugin', jar, '--port', String(args.port)];
    if (args.java) child.push('--java', args.java);
    console.log(`\n=== ${v} ===`);
    const code = await new Promise((resolve) => {
      const p = spawn(process.execPath, child, { stdio: ['ignore', 'pipe', 'inherit'] });
      let tail = '';
      p.stdout.on('data', (d) => { process.stdout.write(d); tail = (tail + d.toString()).slice(-4000); });
      p.on('exit', (c) => { summary.push({ v, code: c, tail }); resolve(c); });
    });
    if (code === null) break;
  }
  console.log('\nfacility self-test across versions:');
  for (const s of summary) {
    const line = /self-test on \S+: (.*)/.exec(s.tail);
    console.log(`  ${s.code === 0 ? 'PASS' : 'FAIL'}  ${s.v}${line ? `  ${line[1]}` : ''}`);
  }
  return summary.every((s) => s.code === 0) && summary.length === args.versions.length ? 0 : 1;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.versions) return acrossVersions(args);
  const { version } = args;
  const java = args.java || server.findJava(server.requiredJava(version));
  if (!java) throw new Error(`no Java ${server.requiredJava(version)} found for ${version}; pass --java`);
  const javaMajor = server.checkJava(java, version);
  const jar = await server.ensurePaperJar(LOCAL, version);
  const plugin = pluginJar(args);
  const folder = path.join(LOCAL, `facility-${version}`);

  if (!args.keepWorld) server.freshWorlds(folder, { pluginData: true });
  server.prepareFolder(folder, { port: args.port, layers: campus.FLAT_LAYERS, gamemode: 'adventure', viewDistance: 10 });
  server.installPlugin(folder, plugin);
  const manifest = generate.writeFacilityPack(path.join(folder, 'world'), version);
  const chunks = wings.forceloadChunks();

  console.log(`facility: Paper ${version} on Java ${javaMajor}, port ${args.port}, ${folder}`);
  const srv = new server.Server({ jar, java, folder, version, memory: '3G' });
  if (process.env.WX_ECHO) srv.on('line', (l) => console.log(`  | ${l}`));
  const fac = new Facility({ srv, version, manifest, port: args.port });
  let exit = 0;
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
    await fac.refreshBoards();

    if (args.selftest) {
      const results = await selftest(fac, { buildReport: report });
      if (setup.length) results.push({ section: 'setup', name: 'setup', ok: false, detail: setup.join('; ') });
      const sections = [...new Set(results.map((r) => r.section))];
      console.log(`\nfacility self-test on ${version}:`);
      for (const s of sections) {
        const mine = results.filter((r) => r.section === s);
        const failed = mine.filter((r) => !r.ok);
        console.log(`  ${failed.length ? 'FAIL' : 'PASS'}  ${s} (${mine.length - failed.length}/${mine.length})${failed.length ? ` — ${failed.map((f) => `${f.name}: ${f.detail}`).join('; ')}` : ''}`);
      }
      const bad = results.filter((r) => !r.ok).length;
      console.log(`self-test on ${version}: ${bad ? `${bad} FAIL` : 'all PASS'} of ${results.length} checks; generation ${buildMs} ms, ${total} chunks forceloaded`);
      exit = bad ? 1 : 0;
    } else {
      console.log(`\nready: join localhost:${args.port} with Minecraft ${version} under any name.`);
      console.log('You arrive in the atrium in adventure mode; say ! or click Console. Say "stop" in chat, or press Ctrl+C, to end.');
      await new Promise((resolve) => {
        fac.probe.bot.on('chat', (username, message) => { if (username !== BOT && /^stop[.!]?$/i.test(message.trim())) resolve(); });
        process.once('SIGINT', resolve);
      });
    }
  } catch (e) {
    console.error(`facility: ${e.stack || e}`);
    exit = 1;
  } finally {
    await fac.close().catch(() => {});
    await srv.stop();
  }
  return exit;
}

main().then((code) => process.exit(code)).catch((e) => { console.error(e); process.exit(2); });
