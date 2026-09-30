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
 * but needs WorldEdit. Null if the jar holds no class.
 */
function classJava(jar) {
  const buf = fs.readFileSync(jar);
  const eocd = buf.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  if (eocd < 0) throw new Error(`${jar} is not a jar`);
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
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

/** The record of what the launcher installed in a server's plugins/: { files: [...], plugins: [...] }. */
function readRecord(folder) {
  const f = path.join(folder, 'plugins', RECORD);
  try {
    const r = JSON.parse(fs.readFileSync(f, 'utf8'));
    return { files: Array.isArray(r.files) ? r.files : [], plugins: Array.isArray(r.plugins) ? r.plugins : [], installed: r.installed || [] };
  } catch {
    return { files: [], plugins: [], installed: [] };
  }
}

/**
 * Installs the resolved companions into plugins/ and takes out any jar an earlier run installed
 * that this one does not want, so the run without a companion really is without it. With
 * `fresh`, a managed companion's data folder goes too (a fresh world must not inherit regions,
 * groups or map tiles from the last one). Returns the names of the jars taken out.
 */
function install(folder, resolved, { fresh = false } = {}) {
  const dir = path.join(folder, 'plugins');
  fs.mkdirSync(dir, { recursive: true });
  const had = readRecord(folder);
  const want = new Set(resolved.map((r) => r.file));
  const removed = [];
  for (const f of had.files) {
    if (!want.has(f) && fs.existsSync(path.join(dir, f))) { fs.rmSync(path.join(dir, f), { force: true }); removed.push(f); }
  }
  if (fresh) {
    for (const p of new Set([...had.plugins, ...resolved.map((r) => r.plugin)])) fs.rmSync(path.join(dir, p), { recursive: true, force: true });
  }
  for (const r of resolved) {
    const to = path.join(dir, r.file);
    if (!matches(to, { size: fs.statSync(r.jar).size, sha256: r.sha256 })) fs.copyFileSync(r.jar, to);
  }
  const record = {
    note: 'Written by scripts/facility/run-facility.js --with: the companion jars it installed here. It removes them again from a run without them.',
    files: [...want],
    plugins: resolved.map((r) => r.plugin),
    installed: resolved.map((r) => ({ name: r.name, plugin: r.plugin, version: r.version, file: r.file, sha256: r.sha256, from: r.from, java: r.java })),
  };
  fs.writeFileSync(path.join(dir, RECORD), `${JSON.stringify(record, null, 2)}\n`);
  return removed;
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
 * Points Dynmap's web server at its own port before the server starts: configuration.txt from
 * the jar's own template when there is none yet (what Dynmap would write), with
 * webserver-port set; an existing one has only that line changed.
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
  if (!/^webserver-port:.*$/m.test(body)) throw new Error(`Dynmap's configuration.txt has no webserver-port line to set`);
  body = body.replace(/^webserver-port:.*$/m, `webserver-port: ${port}`);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(file, body);
  return file;
}

/**
 * Sets top-level keys in Wormhole's config.yml before the server starts (a companion's
 * integration switch, read only at enable). A file that is not there yet is written with just
 * these keys: the plugin appends every setting a file does not mention.
 */
function seedSettings(folder, settings) {
  const dir = path.join(folder, 'plugins', 'WormholeXTreme');
  const file = path.join(dir, 'config.yml');
  fs.mkdirSync(dir, { recursive: true });
  let body = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
  for (const [k, v] of Object.entries(settings)) {
    const line = new RegExp(`^${k.replace(/[-]/g, '\\-')}:.*$`, 'm');
    body = line.test(body) ? body.replace(line, `${k}: ${v}`) : `${body}${body && !body.endsWith('\n') ? '\n' : ''}${k}: ${v}\n`;
  }
  fs.writeFileSync(file, body);
}

/** Wormhole's switches for its integrations with each companion: set true when it is installed. */
const SWITCHES = { worldguard: { 'worldguard-enabled': 'true' }, dynmap: { 'dynmap-enabled': 'true' } };

/**
 * A companion's line that is a fault in a run that installed it: one that failed to load (a
 * jar for a newer Java, say) or to enable, and Dynmap's web server failing to bind its port.
 * Null for anything else.
 */
function companionFault(line, plugins) {
  for (const p of plugins) {
    if (new RegExp(`Could not load '?plugins[\\\\/][^']*' in folder|Error occurred while enabling ${p} `).test(line)
      && (line.includes(p) || /Could not load/.test(line))) return line.trim();
  }
  if (plugins.includes('dynmap') && /\[dynmap\].*(Failed to start WebServer|Address already in use|BindException)/i.test(line)) return line.trim();
  return null;
}

module.exports = {
  MANIFEST, RECORD, manifest, expand, covers, pick, defaultCache, resolve, classJava, readRecord, install, javaNeeded,
  findJavaAtLeast, dynmapPort, configureDynmap, seedSettings, SWITCHES, companionFault, DYNMAP_BASE_PORT,
};
