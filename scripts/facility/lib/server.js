'use strict';
// A Paper server as a child process: console commands go in on stdin, the log comes back on
// stdout, and every wait is a wait for a named line with a deadline.

const { spawn, spawnSync } = require('child_process');
const fs = require('fs');
const https = require('https');
const path = require('path');
const zlib = require('zlib');
const { EventEmitter } = require('events');
const { atLeast } = require('./version');

const USER_AGENT = 'wx-facility (https://github.com/khanjal/Wormhole-X-Treme)';

// Lines a console command prints when the server refused or could not parse it.
const COMMAND_ERROR = /<--\[HERE\]|Unknown or incomplete command|Incorrect argument|Invalid |Expected |Unknown |Unterminated|Malformed|Failed to|Could not|Can't |No entity was found|No player was found|That position is not loaded|Only one|Too many|An unexpected error/;

// Paper's own log header, and whether a line is a WARN or ERROR at all.
const LOG_LINE = /^\[(\d\d:\d\d:\d\d) (INFO|WARN|ERROR|DEBUG)\]: ?(.*)$/;

// Lines another thread may print into any command's output: Paper's update banner arrives
// asynchronously a few seconds after start. They are not the command's, so run() drops them.
const ASYNC_NOISE = /^\*+$|You are running the latest build|release\(s\) behind|recommended that you update|papermc\.io\/downloads|You are running a development version|Download the new version/;

/** Gamerule names: camelCase before 1.21.11, snake_case from it. Only one form is ever sent. */
const GAMERULES = {
  daylight: ['doDaylightCycle', 'advance_time'],
  weather: ['doWeatherCycle', 'advance_weather'],
  mobSpawning: ['doMobSpawning', 'spawn_mobs'],
  commandBlockOutput: ['commandBlockOutput', 'command_block_output'],
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

function getJson(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': USER_AGENT } }, (res) => {
      let body = '';
      res.on('data', (d) => { body += d; });
      res.on('end', () => {
        if (res.statusCode !== 200) return reject(new Error(`${url}: HTTP ${res.statusCode} ${body.slice(0, 200)}`));
        try { resolve(JSON.parse(body)); } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
}

function download(url, file) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': USER_AGENT } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        return resolve(download(res.headers.location, file));
      }
      if (res.statusCode !== 200) return reject(new Error(`${url}: HTTP ${res.statusCode}`));
      const tmp = `${file}.part`;
      const out = fs.createWriteStream(tmp);
      res.pipe(out);
      out.on('finish', () => out.close(() => { fs.renameSync(tmp, file); resolve(file); }));
      out.on('error', reject);
    }).on('error', reject);
  });
}

/** The Paper jar for a version under `dir`, downloaded from PaperMC's fill API if absent. */
async function ensurePaperJar(dir, version) {
  const file = path.join(dir, `paper-${version}.jar`);
  if (fs.existsSync(file)) return file;
  fs.mkdirSync(dir, { recursive: true });
  const build = await getJson(`https://fill.papermc.io/v3/projects/paper/versions/${version}/builds/latest`);
  const url = build.downloads && build.downloads['server:default'] && build.downloads['server:default'].url;
  if (!url) throw new Error(`PaperMC lists no download for ${version}`);
  console.log(`downloading Paper ${version} build ${build.id}`);
  return download(url, file);
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

/** Writes eula.txt and server.properties for an offline, flat, quiet test server. */
function prepareFolder(folder, { port, levelName = 'world' }) {
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
    difficulty: 'peaceful',
    gamemode: 'creative',
    'view-distance': '6',
    'simulation-distance': '6',
    'max-players': '8',
    motd: 'Wormhole Research Facility',
    'enable-command-block': 'true',
  };
  const text = Object.entries(props).map(([k, v]) => `${k}=${v}`).join('\n');
  fs.writeFileSync(path.join(folder, 'server.properties'), `${text}\n`);
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
  }

  start() {
    // UTF-8 both ways: stdin already is on Java 18+, but stdout follows the Windows code page
    // unless told, and a '·' in a readback then arrives as U+FFFD.
    const args = [`-Xmx${this.memory}`, '-Dterminal.jline=false', '-Dterminal.ansi=false',
      '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
      '-jar', path.resolve(this.jar), '--nogui'];
    this.proc = spawn(this.java, args, { cwd: this.folder, stdio: ['pipe', 'pipe', 'pipe'] });
    this.proc.stdin.setDefaultEncoding('utf8');
    let buf = '';
    const onData = (d) => {
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
    this.proc.stdout.on('data', onData);
    this.proc.stderr.on('data', onData);
    this.proc.on('exit', (code) => { this.exited = code; this.emit('exit', code); });
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
    if (this.exited !== null) throw new Error(`server has exited; cannot send ${command}`);
    this.proc.stdin.write(`${command}\n`);
  }

  /**
   * Sends one console command and collects what it printed. A console command's output is
   * written synchronously on the main thread, so everything between the command and the
   * fence's echo belongs to it. Returns { lines, errors }.
   */
  async run(command, ms = 15000) {
    const n = ++this.fenceCount;
    const holder = `#fence${n}`;
    const lines = [];
    const collect = (line) => { lines.push(line); };
    this.on('line', collect);
    const fence = this.waitFor(new RegExp(`Set \\[?wxfence\\]? for ${holder} to ${n}`), ms, `the echo of: ${command}`);
    this.send(command);
    this.send(`scoreboard players set ${holder} wxfence ${n}`);
    try {
      await fence;
    } finally {
      this.off('line', collect);
    }
    const own = lines.filter((l) => !l.includes(holder)).map((l) => {
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

  async stop(ms = 60000) {
    if (this.exited !== null) return this.exited;
    const exit = new Promise((resolve) => this.once('exit', resolve));
    this.send('stop');
    const timer = setTimeout(() => this.proc.kill(), ms);
    const code = await exit;
    clearTimeout(timer);
    return code;
  }
}

module.exports = {
  Server, gameruleName, requiredJava, checkJava, ensurePaperJar, prepareFolder, vanillaVersionInfo, COMMAND_ERROR,
};
