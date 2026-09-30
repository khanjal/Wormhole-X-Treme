'use strict';
// A Paper server as a child process: console commands go in on stdin, the log comes back on
// stdout, and every wait is a wait for a named line with a deadline.

const { spawn, spawnSync } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const https = require('https');
const path = require('path');
const zlib = require('zlib');
const { EventEmitter } = require('events');
const { atLeast } = require('./version');

const USER_AGENT = 'wx-facility (https://github.com/khanjal/Wormhole-X-Treme)';

// Lines a console command prints when the server refused or could not parse it.
const COMMAND_ERROR = /<--\[HERE\]|Unknown or incomplete command|Incorrect argument|Invalid |Expected |Unknown |Unterminated|Malformed|Failed to|Could not|Can't |No entity was found|No player was found|That position is not loaded|Only one|Too many|An unexpected error/;

// A player's chat as Paper logs it: "<name> text", marked [Not Secure] on an offline server.
const CHAT = /^\[\d\d:\d\d:\d\d INFO\]: (\[Not Secure\] )?<[^>]+> /;

// Paper's own log header, and whether a line is a WARN or ERROR at all.
const LOG_LINE = /^\[(\d\d:\d\d:\d\d) (INFO|WARN|ERROR|DEBUG)\]: ?(.*)$/;

// Lines another thread may print into any command's output: Paper's update banner arrives
// asynchronously a few seconds after start, and the tick loop's lag warning whenever a build
// has just taken a few seconds. They are not the command's, so run() drops them.
const ASYNC_NOISE = /^\*+$|You are running the latest build|release\(s\) behind|recommended that you update|papermc\.io\/downloads|You are running a development version|Download the new version|Can't keep up! Is the server overloaded\?/;

/** Gamerule names: camelCase before 1.21.11, snake_case from it. Only one form is ever sent. */
const GAMERULES = {
  daylight: ['doDaylightCycle', 'advance_time'],
  weather: ['doWeatherCycle', 'advance_weather'],
  mobSpawning: ['doMobSpawning', 'spawn_mobs'],
  commandBlockOutput: ['commandBlockOutput', 'command_block_output'],
  logAdminCommands: ['logAdminCommands', 'log_admin_commands'],
};

function gameruleName(version, rule) {
  const names = GAMERULES[rule];
  if (!names) throw new Error(`no gamerule named ${rule}`);
  return atLeast(version, '1.21.11') ? names[1] : names[0];
}

/** The Java major a Paper version needs at least. */
function requiredJava(version) {
  if (atLeast(version, '26.1')) return 25;
  if (atLeast(version, '1.20.5')) return 21;
  return 17;
}

/** Runs `<java> -version` and returns its major, or refuses with what it found. */
function checkJava(java, version) {
  const r = spawnSync(java, ['-version'], { encoding: 'utf8' });
  if (r.error) throw new Error(`cannot run ${java}: ${r.error.message}`);
  const out = `${r.stdout}${r.stderr}`; // -version writes to stderr
  const m = /version "(\d+)(?:\.(\d+))?/.exec(out);
  if (!m) throw new Error(`cannot read the Java version from ${java}: ${out.trim()}`);
  const major = m[1] === '1' ? Number(m[2]) : Number(m[1]);
  const need = requiredJava(version);
  if (major < need) {
    throw new Error(`Paper ${version} needs Java ${need}+, but ${java} is Java ${major}; pass --java`);
  }
  return major;
}

const MAX_REDIRECTS = 5;

/** GETs `url`, following at most MAX_REDIRECTS redirects (relative ones too); resolves with the response. */
function get(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { headers: { 'User-Agent': USER_AGENT } }, (res) => {
      res.on('error', reject);
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        if (redirects >= MAX_REDIRECTS) return reject(new Error(`${url}: more than ${MAX_REDIRECTS} redirects`));
        return resolve(get(new URL(res.headers.location, url).toString(), redirects + 1));
      }
      return resolve(res);
    });
    req.on('error', reject);
    req.setTimeout(60000, () => req.destroy(new Error(`${url}: no answer in 60 s`)));
  });
}

async function getJson(url) {
  const res = await get(url);
  const body = await new Promise((resolve, reject) => {
    let b = '';
    res.on('data', (d) => { b += d; });
    res.on('end', () => resolve(b));
    res.on('error', reject);
  });
  if (res.statusCode !== 200) throw new Error(`${url}: HTTP ${res.statusCode} ${body.slice(0, 200)}`);
  return JSON.parse(body);
}

function sha256Of(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

/** Downloads to `file`, refusing a body whose size or SHA-256 is not the one PaperMC published. */
async function download(url, file, { sha256, size }) {
  const res = await get(url);
  if (res.statusCode !== 200) { res.resume(); throw new Error(`${url}: HTTP ${res.statusCode}`); }
  const tmp = `${file}.part`;
  await new Promise((resolve, reject) => {
    const out = fs.createWriteStream(tmp);
    res.on('error', (e) => { out.destroy(); reject(e); });
    out.on('error', reject);
    out.on('finish', resolve);
    res.pipe(out);
  });
  const got = { size: fs.statSync(tmp).size, sha256: sha256Of(tmp) };
  if (got.size !== size || got.sha256 !== sha256) {
    fs.rmSync(tmp, { force: true });
    throw new Error(`${url}: got ${got.size} bytes with SHA-256 ${got.sha256}; PaperMC published ${size} bytes, ${sha256}`);
  }
  fs.renameSync(tmp, file);
  return file;
}

/** Writes a file whole or not at all: a crash mid-write leaves the old one, never half of it. */
function writeAtomic(file, data) {
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, data);
  fs.renameSync(tmp, file);
}

/** The build record beside a cached jar, or null if there is none or it is not one. */
function readJarRecord(metaFile) {
  if (!fs.existsSync(metaFile)) return null;
  try {
    const m = JSON.parse(fs.readFileSync(metaFile, 'utf8'));
    return m && Number.isInteger(m.id) && Number.isInteger(m.size) && /^[0-9a-f]{64}$/.test(m.sha256) ? m : null;
  } catch {
    return null;
  }
}

/** True if `file` is the jar `record` describes: its size and SHA-256 both. */
function jarMatches(file, record) {
  return Boolean(record) && fs.existsSync(file) && fs.statSync(file).size === record.size && sha256Of(file) === record.sha256;
}

/**
 * The Paper jar for a version under `dir`: the newest STABLE build PaperMC's fill API lists (or
 * build `build`, when named), downloaded when the cached jar is not that build, and checked
 * against the published size and SHA-256. The build, size and checksum are recorded beside the
 * jar, so a cached jar is checked before it is used when PaperMC cannot be asked, or lists no
 * stable build. The facility's jar has a name of its own: scripts/watch-local.ps1 keeps
 * paper-<version>.jar in the same folder and runs a server from it.
 */
async function ensurePaperJar(dir, version, { build: pinned = null } = {}) {
  const file = path.join(dir, `paper-${version}-facility.jar`);
  const metaFile = path.join(dir, `paper-${version}-facility.json`);
  const meta = readJarRecord(metaFile);
  fs.mkdirSync(dir, { recursive: true });
  const cached = (why) => {
    if (pinned !== null && (!meta || meta.id !== pinned)) throw new Error(`${why}, and build ${pinned} of Paper ${version} is not the one cached`);
    if (!fs.existsSync(file)) throw new Error(`${why}, and no Paper ${version} is cached`);
    if (!jarMatches(file, meta)) throw new Error(`${why}, and ${file} is not the build recorded for it; delete it and run online`);
    console.log(`${why}; using the cached Paper ${version} build ${meta.id}`);
    return file;
  };
  let builds;
  try {
    builds = await getJson(`https://fill.papermc.io/v3/projects/paper/versions/${version}/builds`);
  } catch (e) {
    return cached(`PaperMC unreachable (${e.message})`);
  }
  let build;
  if (pinned !== null) {
    build = builds.find((b) => b.id === pinned);
    if (!build) throw new Error(`PaperMC lists no build ${pinned} of Paper ${version}`);
  } else {
    build = builds.filter((b) => b.channel === 'STABLE').sort((a, b) => b.id - a.id)[0];
    if (!build) return cached(`PaperMC lists no STABLE build of ${version} (--paper-build <n> picks one)`);
  }
  const d = build.downloads && build.downloads['server:default'];
  if (!d || !d.url || !d.checksums || !d.checksums.sha256 || !Number.isInteger(d.size)) {
    throw new Error(`PaperMC lists no checked download (URL, size and SHA-256) for ${version} build ${build.id}`);
  }
  const want = { id: build.id, sha256: d.checksums.sha256, size: d.size };
  if (jarMatches(file, want)) {
    // The jar is the published one; a record lost or cut short is written again.
    if (!meta || meta.id !== want.id) writeAtomic(metaFile, `${JSON.stringify(want, null, 2)}\n`);
    return file;
  }
  console.log(`downloading Paper ${version} build ${build.id} (${build.channel ? build.channel.toLowerCase() : 'unknown channel'})`);
  // The old record goes first: a crash before the new one is written leaves a jar with no
  // record, which is checked again online and refused offline, never trusted.
  fs.rmSync(metaFile, { force: true });
  await download(d.url, file, want);
  writeAtomic(metaFile, `${JSON.stringify(want, null, 2)}\n`);
  return file;
}

/** One stored or deflated entry of a zip file, found through its central directory. */
function readZipEntry(file, name) {
  const buf = fs.readFileSync(file);
  const eocd = buf.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  if (eocd < 0) throw new Error(`${file} is not a zip`);
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  for (let n = 0; n < count; n++) {
    const method = buf.readUInt16LE(p + 10);
    const size = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const skip = buf.readUInt16LE(p + 30) + buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    if (buf.toString('utf8', p + 46, p + 46 + nameLen) === name) {
      const start = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
      const data = buf.subarray(start, start + size);
      return method === 0 ? data : zlib.inflateRawSync(data);
    }
    p += 46 + nameLen + skip;
  }
  return null;
}

/** version.json of the vanilla jar Paper unpacked into cache/, or null before the first start. */
function vanillaVersionInfo(folder, version) {
  const jar = path.join(folder, 'cache', `mojang_${version}.jar`);
  if (!fs.existsSync(jar)) return null;
  const entry = readZipEntry(jar, 'version.json');
  return entry ? JSON.parse(entry.toString('utf8')) : null;
}

/**
 * Writes eula.txt and server.properties for an offline, flat, quiet test server. `layers`
 * sets the flat world's layers (bottom up); without it the server's default flat is used.
 */
function prepareFolder(folder, { port, levelName = 'world', layers = null, gamemode = 'creative', viewDistance = 6, seed = null }) {
  fs.mkdirSync(folder, { recursive: true });
  fs.writeFileSync(path.join(folder, 'eula.txt'), 'eula=true\n');
  const props = {
    'online-mode': 'false',
    'enforce-secure-profile': 'false',
    'server-port': String(port),
    'level-name': levelName,
    'level-type': 'minecraft\\:flat',
    'generate-structures': 'false',
    'spawn-protection': '0',
    'spawn-monsters': 'false',
    'spawn-animals': 'false',
    difficulty: 'peaceful',
    gamemode,
    'allow-flight': 'true',
    'view-distance': String(viewDistance),
    'simulation-distance': '6',
    'max-players': '8',
    motd: 'Wormhole Research Facility',
    'enable-command-block': 'true',
  };
  if (layers) props['generator-settings'] = JSON.stringify({ layers, biome: 'minecraft:plains', structure_overrides: [] });
  if (seed !== null) props['level-seed'] = String(seed);
  const text = Object.entries(props).map(([k, v]) => `${k}=${v}`).join('\n');
  fs.writeFileSync(path.join(folder, 'server.properties'), `${text}\n`);
}

/** Deletes the worlds (and, with `pluginData`, the plugin's folder) so a run starts clean. */
function freshWorlds(folder, { pluginData = false } = {}) {
  if (!fs.existsSync(folder)) return;
  for (const d of fs.readdirSync(folder)) {
    if (/^world/.test(d)) fs.rmSync(path.join(folder, d), { recursive: true, force: true });
  }
  if (pluginData) fs.rmSync(path.join(folder, 'plugins', 'WormholeXTreme'), { recursive: true, force: true });
}

/** Copies the plugin jar into plugins/ under its stable name. */
function installPlugin(folder, jar) {
  const dir = path.join(folder, 'plugins');
  fs.mkdirSync(dir, { recursive: true });
  fs.copyFileSync(jar, path.join(dir, 'WormholeXTreme.jar'));
}

/**
 * The version numbers in a JDK folder's name, major first: jdk-17.0.17.10-hotspot is [17, 0, 17,
 * 10], java-21-openjdk-amd64 is [21], jdk1.8.0_202 is [8, 0, 202]. Null if it names none.
 */
function jdkVersion(name) {
  const m = /(?:jdk|java|temurin|openjdk|zulu|corretto)[^0-9]*(\d+(?:[._+]\d+)*)/i.exec(name);
  if (!m) return null;
  const v = m[1].split(/[._+]/).map(Number);
  return v[0] === 1 && v.length > 1 ? v.slice(1) : v;
}

function compareVersions(a, b) {
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const d = (a[i] || 0) - (b[i] || 0);
    if (d) return d;
  }
  return 0;
}

/**
 * A JDK's java for a major version: JAVA<major>_HOME if set, else the usual install folders
 * (Adoptium, Oracle and Microsoft on Windows, /usr/lib/jvm on Linux). Null if none is found.
 */
function findJava(major) {
  const exe = process.platform === 'win32' ? 'java.exe' : 'java';
  const env = process.env[`JAVA${major}_HOME`];
  if (env && fs.existsSync(path.join(env, 'bin', exe))) return path.join(env, 'bin', exe);
  const roots = process.platform === 'win32'
    ? ['C:/Program Files/Eclipse Adoptium', 'C:/Program Files/Java', 'C:/Program Files/Microsoft']
    : ['/usr/lib/jvm', '/Library/Java/JavaVirtualMachines'];
  for (const root of roots) {
    if (!fs.existsSync(root)) continue;
    const hit = fs.readdirSync(root).map((d) => ({ d, v: jdkVersion(d) })).filter((x) => x.v && x.v[0] === major)
      .sort((a, b) => compareVersions(b.v, a.v))[0];
    if (!hit) continue;
    for (const bin of [path.join(root, hit.d, 'bin', exe), path.join(root, hit.d, 'Contents', 'Home', 'bin', exe)]) {
      if (fs.existsSync(bin)) return bin;
    }
  }
  return null;
}

/** Builds the plugin jar from the repository with Maven, offline, tests skipped; returns its path. */
function buildPlugin(repo, javaExe) {
  const env = { ...process.env };
  if (javaExe) env.JAVA_HOME = path.dirname(path.dirname(javaExe));
  const r = spawnSync('mvn', ['-o', '-q', '-DskipTests', 'package'], {
    cwd: repo, env, encoding: 'utf8', shell: process.platform === 'win32',
  });
  if (r.status !== 0) throw new Error(`mvn package failed (${r.status}):\n${`${r.stdout}${r.stderr}`.slice(-2000)}`);
  const jar = path.join(repo, 'target', 'WormholeXTreme.jar');
  if (!fs.existsSync(jar)) throw new Error(`mvn package made no ${jar}`);
  return jar;
}

/**
 * Plugin log lines that are expected on a test server and are not faults, matched exactly
 * against the message after "[WormholeXTreme] ". The one list, used by the fault counter and
 * the self-test alike; add a line here only with the reason it is benign.
 */
const KNOWN_BENIGN = [
  // No Vault or LuckPerms on the test server, by design: the plugin says so once at enable.
  'No Vault/LuckPerms provider detected; enabling simple permission fallback. Players may use gates; '
    + 'advanced actions require OP. Install Vault/LuckPerms to restore node-based permissions or set '
    + 'PERMISSIONS_AUTO_FALLBACK=false.',
];

/**
 * A log line's fault message if it is a plugin fault, else null: a WARN or ERROR from
 * WormholeXTreme, a stack frame in its package, or a failure to load, enable or pass an event
 * to it. Messages on KNOWN_BENIGN are not faults.
 */
function pluginFault(line) {
  const m = LOG_LINE.exec(line);
  if (m && (m[2] === 'WARN' || m[2] === 'ERROR')) {
    const own = /^\[WormholeXTreme\] ?(.*)$/.exec(m[3]);
    if (own) return KNOWN_BENIGN.includes(own[1].trim()) ? null : m[3];
    return /WormholeXTreme|wormhole_xtreme/.test(m[3]) ? m[3] : null;
  }
  if (/^\s+at com\.wormhole_xtreme\./.test(line)) return line.trim();
  const failure = /Error occurred while (enabling|disabling) WormholeXTreme|Could not load 'plugins[\\/]WormholeXTreme|Could not pass event \S+ to WormholeXTreme/;
  return failure.test(line) ? line.trim() : null;
}

/**
 * A running server. `lines` of its log arrive as 'line' events; `run` sends a console command
 * and returns the lines that command printed, fenced by a scoreboard write whose echo is unique.
 */
class Server extends EventEmitter {
  constructor({ jar, java, folder, version, memory = '2G' }) {
    super();
    Object.assign(this, { jar, java, folder, version, memory });
    this.log = [];
    this.fenceCount = 0;
    this.exited = null;
    // Every console command goes through one queue: run() owns the log between its command and
    // its fence, so two at once would read each other's lines.
    this.queue = Promise.resolve();
  }

  start() {
    // UTF-8 both ways: stdin already is on Java 18+, but stdout follows the Windows code page
    // unless told, and a '·' in a readback then arrives as U+FFFD.
    const args = [`-Xmx${this.memory}`, '-Dterminal.jline=false', '-Dterminal.ansi=false',
      '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
      '-jar', path.resolve(this.jar), '--nogui'];
    // Its own process group on Windows, so a Ctrl+C in the console reaches the launcher, which
    // stops the server, rather than the JVM directly while the launcher is still writing to it.
    this.proc = spawn(this.java, args, {
      cwd: this.folder, stdio: ['pipe', 'pipe', 'pipe'], detached: process.platform === 'win32', windowsHide: true,
    });
    this.proc.stdin.setDefaultEncoding('utf8');
    // A write to a server that is dying is not a crash of the launcher: it is noticed as the exit.
    this.proc.stdin.on('error', (e) => { this.stdinError = e; });
    // One buffer per stream: a line split across two reads must not be spliced with the other's.
    const reader = () => {
      let buf = '';
      return (d) => {
        buf += d.toString('utf8');
        let i;
        while ((i = buf.indexOf('\n')) >= 0) {
          const raw = buf.slice(0, i).replace(/\r$/, '');
          buf = buf.slice(i + 1);
          const line = raw.replace(/\x1b\[[0-9;]*[A-Za-z]/g, ''); // eslint-disable-line no-control-regex
          this.log.push(line);
          this.emit('line', line);
        }
      };
    };
    this.proc.stdout.on('data', reader());
    this.proc.stderr.on('data', reader());
    // A JVM killed by a signal exits with no code; it has exited all the same.
    this.proc.on('exit', (code, signal) => { this.exited = code === null ? (signal || 'signal') : code; this.emit('exit', this.exited); });
    return this.waitFor(/Done \([\d.,]+s\)!/, 600000, 'the server to finish starting');
  }

  /** Resolves with the first log line (from now on) matching `re`, or rejects naming `what`. */
  waitFor(re, ms, what) {
    return new Promise((resolve, reject) => {
      const onLine = (line) => { if (re.test(line)) { done(); resolve(line); } };
      const onExit = (code) => { done(); reject(new Error(`server exited (${code}) while waiting for ${what}`)); };
      const timer = setTimeout(() => { done(); reject(new Error(`timed out after ${ms} ms waiting for ${what}`)); }, ms);
      const done = () => { clearTimeout(timer); this.off('line', onLine); this.off('exit', onExit); };
      this.on('line', onLine);
      this.on('exit', onExit);
    });
  }

  send(command) {
    if (this.exited !== null) throw new Error(`server has exited (${this.exited}); cannot send ${command}`);
    if (this.stdinError || !this.proc.stdin.writable) throw new Error(`the server's console is closed; cannot send ${command}`);
    this.proc.stdin.write(`${command}\n`);
  }

  /** Kills the JVM at once, if it is still running: for a launcher on its way out. */
  kill() {
    if (this.proc && this.exited === null) {
      try { this.proc.kill(); } catch { /* already gone */ }
    }
  }

  /**
   * Sends one console command and collects what it printed. A console command's output is
   * written synchronously on the main thread, so everything between the command and the
   * fence's echo belongs to it. Returns { lines, errors }.
   */
  run(command, ms = 15000) {
    const job = this.queue.then(() => this.runNow(command, ms));
    this.queue = job.catch(() => {});
    return job;
  }

  async runNow(command, ms) {
    const n = ++this.fenceCount;
    const holder = `#fence${n}`;
    const lines = [];
    const collect = (line) => { lines.push(line); };
    this.on('line', collect);
    let fence;
    try {
      fence = this.waitFor(new RegExp(`Set \\[?wxfence\\]? for ${holder} to ${n}`), ms, `the echo of: ${command}`);
      this.send(command);
      this.send(`scoreboard players set ${holder} wxfence ${n}`);
      await fence;
    } catch (e) {
      if (fence) fence.catch(() => {});
      throw e;
    } finally {
      this.off('line', collect);
    }
    // A player's chat line can land between a command and its fence; it is not the command's.
    const own = lines.filter((l) => !l.includes(holder) && !CHAT.test(l)).map((l) => {
      const m = LOG_LINE.exec(l);
      return m ? { level: m[2], text: m[3] } : { level: 'INFO', text: l };
    }).filter((l) => !ASYNC_NOISE.test(l.text));
    const errors = own.filter((l) => l.level === 'WARN' || l.level === 'ERROR' || COMMAND_ERROR.test(l.text));
    return { lines: own.map((l) => l.text), errors: errors.map((l) => l.text) };
  }

  /** Creates the dummy objective `run` fences on. Must be the first thing after start. */
  async prepareFence() {
    this.send('scoreboard objectives add wxfence dummy');
    await this.waitFor(/Created new objective \[?wxfence\]?|An objective already exists by that name/, 15000, 'the fence objective');
  }

  /**
   * Runs `command` until its output matches `re`, every `interval` ms, for at most `ms`.
   * A wait for something observable, never a bare sleep: it names `what` when it gives up.
   */
  async until(command, re, ms, what, interval = 250) {
    const deadline = Date.now() + ms;
    let last = [];
    for (;;) {
      const r = await this.run(command);
      if (r.lines.some((l) => re.test(l))) return r;
      last = r.lines;
      if (Date.now() > deadline) throw new Error(`timed out after ${ms} ms waiting for ${what}; last: ${last.join(' | ')}`);
      await new Promise((resolve) => { setTimeout(resolve, interval); });
    }
  }

  /** Waits until every point is in a loaded chunk of `dim` (forceload is asynchronous). */
  async waitLoaded(dim, points, ms = 60000) {
    for (const [x, y, z] of points) {
      await this.until(`execute in ${dim} if loaded ${x} ${y} ${z}`, /Test passed/, ms, `${dim} ${x} ${z} to load`);
    }
  }

  async stop(ms = 60000) {
    if (!this.proc || this.exited !== null) return this.exited;
    const exit = new Promise((resolve) => this.once('exit', resolve));
    try {
      this.send('stop');
    } catch {
      this.kill();
    }
    const timer = setTimeout(() => this.kill(), ms);
    const code = await exit;
    clearTimeout(timer);
    return code;
  }
}

/** WX_ECHO=1 (or true, yes, on) echoes the server's log; unset, 0, false, no or off does not. */
function echoOn(value) {
  return Boolean(value) && !/^(0|false|no|off)$/i.test(String(value).trim());
}

module.exports = {
  echoOn, jdkVersion, Server, gameruleName, requiredJava, checkJava, ensurePaperJar, prepareFolder, freshWorlds, installPlugin,
  findJava, buildPlugin, vanillaVersionInfo, pluginFault, KNOWN_BENIGN, COMMAND_ERROR, ASYNC_NOISE,
};
