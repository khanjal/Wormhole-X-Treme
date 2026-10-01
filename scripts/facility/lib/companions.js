'use strict';
// Companion plugins (--with): pinned in ../companions.json by version and SHA-256, read from the
// plugin cache first, downloaded only when missing and only from an official release source, and
// installed into a server's plugins/ beside a record of what the launcher put there, so a run
// without a companion takes out the jar an earlier run installed (and never one it did not).
//
// The Java a server needs is the highest of what Paper needs and what every plugin jar was
// compiled for: WorldEdit 7.4.5 is class 69 (Java 25) even on 1.21.11, WorldGuard 7.0.17 needs
// it, and on Java 21 Paper refuses them with no word from Wormhole beyond "not installed".

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const server = require('./server');
const { atLeast } = require('./version');

const MANIFEST = path.join(__dirname, '..', 'companions.json');
const RECORD = '.wx-companions.json';
/** Dynmap's web port for the default game port; every other server is offset by its port. */
const DYNMAP_BASE_PORT = 8123;
const BASE_GAME_PORT = 25590;

function manifest() {
  return JSON.parse(fs.readFileSync(MANIFEST, 'utf8'));
}

/**
 * The companions a --with list names, sets expanded and each one's `requires` added before it:
 * `regions` is worldedit, worldguard; `luckperms` brings vault. Throws on a name it does not know.
 */
function expand(list, m = manifest()) {
  const out = [];
  const add = (name, why) => {
    const key = name.toLowerCase();
    if (m.sets[key]) { for (const n of m.sets[key]) add(n, why); return; }
    const c = m.companions[key];
    if (!c) throw new Error(`--with ${why}: no companion called ${name}; known: ${[...Object.keys(m.companions), ...Object.keys(m.sets)].join(', ')}`);
    for (const r of c.requires || []) add(r, `${why} (${key} needs ${r})`);
    if (!out.includes(key)) out.push(key);
  };
  for (const n of list) add(n, n);
  return out;
}

/** Whether a build's `versions` ("*" or "1.21.11,26.1.2") covers a Minecraft version. */
function covers(versions, version) {
  return versions === '*' || versions.split(',').map((v) => v.trim()).includes(version);
}

/**
 * The pinned build of a companion for a Minecraft version; throws, in plain words, if the
 * companion does not run there or nothing is pinned for it.
 */
function pick(name, version, m = manifest()) {
  const c = m.companions[name];
  const no = (c.unsupported || []).find((u) => atLeast(version, u.from));
  if (no) throw new Error(`${c.plugin} on ${version}: ${no.why}`);
  const b = c.builds.find((x) => covers(x.versions, version));
  if (!b) {
    throw new Error(`no build of ${c.plugin} is pinned for ${version} (pinned: ${c.builds.map((x) => `${x.version} for ${x.versions}`).join('; ')}); `
      + 'add one to scripts/facility/companions.json from an official release source');
  }
  return { name, plugin: c.plugin, ...b };
}

/**
 * The plugin cache to read first: --plugin-cache, else WX_PLUGIN_CACHE, else the nearest
 * `.wx-plugins` folder beside the repository or beside any folder above it. Null if none.
 */
function defaultCache(repo, env = process.env) {
  if (env.WX_PLUGIN_CACHE) return env.WX_PLUGIN_CACHE;
  let dir = path.resolve(repo);
  for (;;) {
    const up = path.dirname(dir);
    const here = path.join(up, '.wx-plugins');
    if (fs.existsSync(here) && fs.statSync(here).isDirectory()) return here;
    if (up === dir) return null;
    dir = up;
  }
}

/** True if `file` is the pinned jar: its size and SHA-256 both. */
function matches(file, b) {
  return fs.existsSync(file) && fs.statSync(file).size === b.size && server.sha256Of(file) === b.sha256;
}

/**
 * Finds or fetches each companion's pinned jar: the cache's <version> folder, then its any/
 * folder, then `store` (what earlier runs downloaded), then the build's official URL. A jar in
 * the cache under the pinned name that is not the pinned build is refused, not used and not
 * replaced: the cache is the user's. Returns [{ name, plugin, version, file, jar, sha256, from }].
 */
async function resolve(names, mcVersion, { cache = null, store, log = console.log } = {}) {
  const out = [];
  for (const name of names) {
    const b = pick(name, mcVersion);
    let jar = null;
    let from = null;
    for (const dir of cache ? [path.join(cache, mcVersion), path.join(cache, 'any')] : []) {
      const f = path.join(dir, b.file);
      if (!fs.existsSync(f)) continue;
      if (!matches(f, b)) {
        throw new Error(`${f} is not the pinned ${b.plugin} ${b.version} (SHA-256 ${server.sha256Of(f)}, `
          + `pinned ${b.sha256}); move it aside or pin it in companions.json`);
      }
      jar = f;
      from = `the plugin cache (${dir})`;
      break;
    }
    const kept = path.join(store, b.file);
    if (!jar && matches(kept, b)) { jar = kept; from = 'an earlier download'; }
    if (!jar) {
      if (!b.url) {
        throw new Error(`${b.plugin} ${b.version} for ${mcVersion} has no official download: ${b.source} `
          + `It is not in ${cache ? `${path.join(cache, mcVersion)} or ${path.join(cache, 'any')}` : 'a plugin cache (--plugin-cache)'} `
          + `as ${b.file} (SHA-256 ${b.sha256}).`);
      }
      fs.mkdirSync(store, { recursive: true });
      log(`downloading ${b.plugin} ${b.version} from ${b.source}`);
      await server.download(b.url, kept, { sha256: b.sha256, size: b.size }, b.source);
      jar = kept;
      from = `${b.source}, downloaded`;
    }
    out.push({ name, plugin: b.plugin, version: b.version, file: b.file, jar, sha256: b.sha256, from, java: classJava(jar) });
  }
  return out;
}

/**
 * The Java major a plugin jar needs: the newest class file version in it (major - 44), over every
 * class but a multi-release jar's META-INF/versions/. Not the main class alone: WorldEdit 7.4.5's
 * main class is Java 21 and most of the rest Java 25, and WorldGuard 7.0.17 is Java 21 throughout
 * but needs WorldEdit. Null if the jar holds no class. A jar this cannot read (cut short, or a
 * Zip64 one) is refused by name.
 */
function classJava(jar) {
  try {
    return newestClass(fs.readFileSync(jar));
  } catch (e) {
    throw new Error(`cannot read the class versions in ${jar}: ${e.message}`);
  }
}

function newestClass(buf) {
  const eocd = buf.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  if (eocd < 0) throw new Error('it is not a jar (no end of central directory)');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  if (count === 0xffff || p === 0xffffffff) throw new Error('it is a Zip64 jar, which this reader does not follow');
  let top = null;
  for (let n = 0; n < count; n++) {
    const method = buf.readUInt16LE(p + 10);
    const size = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const skip = buf.readUInt16LE(p + 30) + buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLen);
    p += 46 + nameLen + skip;
    if (!name.endsWith('.class') || name.startsWith('META-INF/versions/')) continue;
    const start = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const data = buf.subarray(start, start + size);
    let head;
    try {
      // Only the first 8 bytes matter: a partial inflate is enough, and cheaper.
      head = method === 0 ? data : zlib.inflateRawSync(data, { finishFlush: zlib.constants.Z_SYNC_FLUSH });
    } catch {
      continue;
    }
    if (head.length < 8 || head.readUInt32BE(0) !== 0xcafebabe) continue;
    const java = head.readUInt16BE(6) - 44;
    if (top === null || java > top) top = java;
  }
  return top;
}

/** A bare file or folder name: nothing that reaches out of plugins/ ("", ".", "..", "a/b" are not). */
function bareName(x) {
  return typeof x === 'string' && x !== '' && x !== '.' && x !== '..' && path.basename(x) === x && !/[\\/]/.test(x);
}

/**
 * The record of what the launcher installed in a server's plugins/: { files, plugins, seeded }.
 * Its names are deleted by, and it is a file anyone can edit: a name that is not a bare file or
 * folder name is ignored, never followed out of plugins/.
 */
function readRecord(folder) {
  const f = path.join(folder, 'plugins', RECORD);
  try {
    const r = JSON.parse(fs.readFileSync(f, 'utf8'));
    const names = (a) => (Array.isArray(a) ? a.filter(bareName) : []);
    const seeded = r.seeded && typeof r.seeded === 'object' && !Array.isArray(r.seeded) ? r.seeded : {};
    return { files: names(r.files), plugins: names(r.plugins), installed: r.installed || [], seeded };
  } catch {
    return { files: [], plugins: [], installed: [], seeded: {} };
  }
}

/**
 * Installs the resolved companions into plugins/ and takes out any jar an earlier run installed
 * that this one does not want, so the run without a companion really is without it. With
 * `fresh`, a managed companion's data folder goes too (a fresh world must not inherit regions,
 * groups or map tiles from the last one).
 *
 * A jar of the same name already in plugins/ that no earlier run installed, and that is not the
 * pinned build, is somebody's own: refused, never overwritten (and so never deleted later).
 *
 * `switches` are Wormhole settings to turn on for these companions (SWITCHES): written into its
 * config.yml with the value each replaced kept in the record, so a later run without that
 * companion puts it back: a --keep-world run without --with is not left with an integration an
 * earlier run switched on. Returns { removed, unseeded }: the jars taken out, the settings put back.
 */
function install(folder, resolved, { fresh = false, switches = {} } = {}) {
  const dir = path.join(folder, 'plugins');
  fs.mkdirSync(dir, { recursive: true });
  const had = readRecord(folder);
  for (const r of resolved) {
    const to = path.join(dir, r.file);
    if (fs.existsSync(to) && !had.files.includes(r.file) && !matches(to, { size: fs.statSync(r.jar).size, sha256: r.sha256 })) {
      throw new Error(`${to} is already there, is not the pinned ${r.plugin} ${r.version}, and no earlier run installed it: `
        + 'it is left alone; move it aside to run --with it');
    }
  }
  const want = new Set(resolved.map((r) => r.file));
  const removed = [];
  for (const f of had.files) {
    if (!want.has(f) && fs.existsSync(path.join(dir, f))) { fs.rmSync(path.join(dir, f), { force: true }); removed.push(f); }
  }
  if (fresh) {
    for (const p of new Set([...had.plugins, ...resolved.map((r) => r.plugin)])) {
      if (bareName(p)) fs.rmSync(path.join(dir, p), { recursive: true, force: true });
    }
  }
  for (const r of resolved) {
    const to = path.join(dir, r.file);
    if (!matches(to, { size: fs.statSync(r.jar).size, sha256: r.sha256 })) fs.copyFileSync(r.jar, to);
  }
  // Put back what an earlier run switched on and this one does not want (a key it found absent
  // goes back to the plugin's default, false); then switch on this run's, keeping the value from
  // before the first run that switched each on.
  const seeded = {};
  const back = {};
  for (const [k, before] of Object.entries(had.seeded)) {
    if (k in switches) seeded[k] = before;
    else back[k] = before === null || before === undefined ? 'false' : before;
  }
  if (Object.keys(back).length) seedSettings(folder, back);
  const replaced = Object.keys(switches).length ? seedSettings(folder, switches) : {};
  for (const [k, before] of Object.entries(replaced)) if (!(k in seeded)) seeded[k] = before;
  const record = {
    note: 'Written by scripts/facility/run-facility.js --with: the companion jars it installed here, and the Wormhole settings '
      + 'it switched on for them with the values they replaced. A run without them takes the jars out and puts the settings back.',
    files: [...want],
    plugins: resolved.map((r) => r.plugin),
    seeded,
    installed: resolved.map((r) => ({ name: r.name, plugin: r.plugin, version: r.version, file: r.file, sha256: r.sha256, from: r.from, java: r.java })),
  };
  fs.writeFileSync(path.join(dir, RECORD), `${JSON.stringify(record, null, 2)}\n`);
  return { removed, unseeded: Object.entries(back).map(([k, x]) => `${k}: ${x}`) };
}

/** The Java a server needs: Paper's for the version, raised by any plugin jar's class version. */
function javaNeeded(version, jars) {
  let need = server.requiredJava(version);
  const why = [`Paper ${version} needs ${need}`];
  for (const j of jars) {
    if (j.java && j.java > need) { need = j.java; }
    if (j.java && j.java > server.requiredJava(version)) why.push(`${j.name} is compiled for Java ${j.java}`);
  }
  return { major: need, why };
}

/** A JDK's java of at least `major`: that major if installed, else the next one up. */
function findJavaAtLeast(major, highest = 30) {
  for (let m = major; m <= highest; m++) {
    const j = server.findJava(m);
    if (j) return j;
  }
  return null;
}

/** Dynmap's web port for a game port: 8123 on 25590, offset one for one with the game port. */
function dynmapPort(gamePort) {
  return DYNMAP_BASE_PORT + (gamePort - BASE_GAME_PORT);
}

/**
 * Points Dynmap's web server at its own port, on this machine only, before the server starts:
 * configuration.txt from the jar's own template when there is none yet (what Dynmap would
 * write), with webserver-port and webserver-bindaddress set; an existing one has only those
 * lines changed.
 */
function configureDynmap(folder, jar, port) {
  const dir = path.join(folder, 'plugins', 'dynmap');
  const file = path.join(dir, 'configuration.txt');
  let body;
  if (fs.existsSync(file)) {
    body = fs.readFileSync(file, 'utf8');
  } else {
    const template = server.readZipEntry(jar, 'configuration.txt');
    if (!template) throw new Error(`${jar} holds no configuration.txt to start Dynmap's from`);
    body = template.toString('utf8');
  }
  if (!/^webserver-port:.*$/m.test(body)) throw new Error('Dynmap\'s configuration.txt has no webserver-port line to set');
  body = body.replace(/^webserver-port:.*$/m, `webserver-port: ${port}`);
  // The template has it commented out (#webserver-bindaddress: 0.0.0.0, every interface).
  if (/^#?\s*webserver-bindaddress:.*$/m.test(body)) body = body.replace(/^#?\s*webserver-bindaddress:.*$/m, 'webserver-bindaddress: 127.0.0.1');
  else body = body.replace(/^webserver-port:.*$/m, (l) => `webserver-bindaddress: 127.0.0.1\n${l}`);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(file, body);
  return file;
}

/**
 * Sets top-level keys in Wormhole's config.yml (a companion's integration switch, read only at
 * enable), keeping the file's own line endings. A file that is not there yet is written with
 * just these keys: the plugin appends every setting a file does not mention. Returns what each
 * key held before ({ key: value }, null where there was none).
 */
function seedSettings(folder, settings) {
  const dir = path.join(folder, 'plugins', 'WormholeXTreme');
  const file = path.join(dir, 'config.yml');
  fs.mkdirSync(dir, { recursive: true });
  const body = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
  const eol = body.includes('\r\n') ? '\r\n' : '\n';
  const lines = body === '' ? [] : body.split(/\r?\n/);
  if (lines.length && lines[lines.length - 1] === '') lines.pop();
  const before = {};
  for (const [k, v] of Object.entries(settings)) {
    // A top-level key starts its line; matched as text, never as a pattern.
    const at = lines.findIndex((l) => l.startsWith(`${k}:`));
    before[k] = at >= 0 ? lines[at].slice(k.length + 1).trim() : null;
    if (at >= 0) lines[at] = `${k}: ${v}`;
    else lines.push(`${k}: ${v}`);
  }
  fs.writeFileSync(file, `${lines.join(eol)}${eol}`);
  return before;
}

/** Wormhole's switches for its integrations with each companion: set true when it is installed. */
const SWITCHES = { worldguard: { 'worldguard-enabled': 'true' }, dynmap: { 'dynmap-enabled': 'true' } };

/**
 * A companion's line that is a fault in a run that installed it: one that failed to load (a
 * jar for a newer Java, say) or to enable, and Dynmap's web server failing to bind its port.
 * Null for anything else.
 */
function companionFault(line, plugins) {
  // Paper's words for a jar it would not load (a newer class version among them) or enable.
  // ("Could not load plugin 'worldedit-bukkit-7.4.5.jar' in folder 'plugins'", then the cause.)
  if (/Could not load (plugin )?'|Error loading plugin|^Caused by: java\.lang\.UnsupportedClassVersionError/.test(line)) return line.trim();
  if (plugins.some((p) => line.includes(`Error occurred while enabling ${p} `))) return line.trim();
  if (plugins.includes('dynmap') && /\[dynmap\].*(Failed to start|Address already in use|BindException)/i.test(line)) return line.trim();
  return null;
}

module.exports = {
  MANIFEST, RECORD, manifest, expand, covers, pick, defaultCache, resolve, classJava, readRecord, install, javaNeeded,
  findJavaAtLeast, dynmapPort, configureDynmap, seedSettings, SWITCHES, companionFault, DYNMAP_BASE_PORT,
};
