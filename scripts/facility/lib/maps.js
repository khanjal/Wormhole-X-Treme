'use strict';
// The web-map companions (Dynmap, BlueMap, squaremap, Pl3xMap): each one's web port for a game
// port, its settings written before the server starts so it listens on 127.0.0.1 only and on a
// port no other lab uses, how to tell its web server came up, and how to make it render.
//
// Ports: each map has its own base, offset one for one with the game port like Dynmap's 8123,
// so two labs side by side, and two maps in one lab, never ask for the same port. BlueMap's own
// default (8100), squaremap's and Pl3xMap's (8080) would collide across labs, so none is used.
// The bases are 100 apart: a game port more than 99 above 25590 would reach the next map's range,
// and is refused (DESIGN.md: at most about five servers run side by side anyway).
//
// Settings: a map writes its config files on its first start, so a file that is not there yet is
// written with only the keys set here (each plugin fills in its own defaults for the rest), and
// one that is there has only those keys changed, keeping the file's line endings.

const fs = require('fs');
const path = require('path');

const BASE_GAME_PORT = 25590;

/**
 * The maps: their plugin name (as Paper loads it, and its data folder under plugins/), a label
 * for people, and their web port's base.
 */
const MAPS = {
  dynmap: { plugin: 'dynmap', label: 'Dynmap', base: 8123 },
  bluemap: { plugin: 'BlueMap', label: 'BlueMap', base: 8300 },
  squaremap: { plugin: 'squaremap', label: 'squaremap', base: 8400 },
  pl3xmap: { plugin: 'Pl3xMap', label: 'Pl3xMap', base: 8500 },
};
const NAMES = Object.keys(MAPS);
/** How far above the default game port a map's port may be offset before it reaches the next map's range. */
const SPAN = 100;

/** A map's web port for a game port: its base, offset one for one with the game port. */
function webPort(name, gamePort) {
  const m = MAPS[name];
  if (!m) throw new Error(`no map called ${name}`);
  const offset = gamePort - BASE_GAME_PORT;
  const port = m.base + offset;
  if (port < 1024) throw new Error(`${m.label}'s web port for game port ${gamePort} would be ${port}: run it on a game port from ${BASE_GAME_PORT - m.base + 1024}`);
  if (name !== 'dynmap' && offset >= SPAN) {
    throw new Error(`${m.label}'s web port for game port ${gamePort} would be ${port}, in the next map's range: run it on a game port below ${BASE_GAME_PORT + SPAN}`);
  }
  return port;
}

/** The end of line a file already uses, else \n. */
const eolOf = (body) => (body.includes('\r\n') ? '\r\n' : '\n');

/**
 * Sets a top-level `key: value` line in a HOCON file (BlueMap's .conf), uncommenting one that is
 * there as a comment (`#write-markers-interval: 10`), else adding it at the end.
 */
function setConf(body, key, value) {
  const line = `${key}: ${value}`;
  const live = new RegExp(`^${key}\\s*[:=].*$`, 'm');
  if (live.test(body)) return body.replace(live, line);
  const commented = new RegExp(`^#\\s*${key}\\s*[:=].*$`, 'm');
  if (commented.test(body)) return body.replace(commented, line);
  const eol = eolOf(body);
  return `${body}${body === '' || body.endsWith('\n') ? '' : eol}${line}${eol}`;
}

/**
 * Sets a nested key in a block-style YAML file (squaremap's and Pl3xMap's config.yml), by its
 * path: ['settings', 'internal-webserver', 'bind']. Each level is found by its indentation under
 * the one before; a level that is not there is added at the end of its parent's block. Only for
 * maps of maps, as these files are: a list under the path is not followed.
 */
function setYaml(body, keys, value) {
  const eol = eolOf(body);
  const lines = body === '' ? [] : body.split(/\r?\n/);
  if (lines.length && lines[lines.length - 1] === '') lines.pop();
  const indentOf = (l) => /^ */.exec(l)[0].length;
  const isKey = (l, k, indent) => indentOf(l) === indent && new RegExp(`^ *["']?${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}["']?\\s*:`).test(l);
  let from = 0;
  let to = lines.length;
  let indent = 0;
  for (let d = 0; d < keys.length; d++) {
    const k = keys[d];
    const last = d === keys.length - 1;
    let at = -1;
    for (let i = from; i < to; i++) {
      if (/^\s*(#|$)/.test(lines[i])) continue;
      if (isKey(lines[i], k, indent)) { at = i; break; }
    }
    if (at < 0) {
      // Added at the end of the parent's block, after any of its comments and blank lines.
      const add = keys.slice(d).map((x, j) => `${' '.repeat(indent + 2 * j)}${x}:${j === keys.length - d - 1 ? ` ${value}` : ''}`);
      let end = to;
      while (end > from && /^\s*$/.test(lines[end - 1])) end--;
      lines.splice(end, 0, ...add);
      return `${lines.join(eol)}${eol}`;
    }
    if (last) {
      // A trailing comment on the line goes; the value is the whole rest of it.
      lines[at] = `${' '.repeat(indent)}${/^ *(["']?[^:]+["']?)\s*:/.exec(lines[at])[1]}: ${value}`;
      return `${lines.join(eol)}${eol}`;
    }
    // The child block: the lines after `at` indented deeper than it, up to the next one that is not.
    from = at + 1;
    let end = from;
    while (end < to && (/^\s*(#|$)/.test(lines[end]) || indentOf(lines[end]) > indent)) end++;
    to = end;
    const child = lines.slice(from, to).find((l) => !/^\s*(#|$)/.test(l));
    indent = child ? indentOf(child) : indent + 2;
  }
  return `${lines.join(eol)}${eol}`;
}

/** Reads a file, or '' when it is not there yet. */
function readOr(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
}

function write(file, body) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, body);
  return file;
}

/**
 * Writes a map's settings into a server folder before it starts: its web server on 127.0.0.1 at
 * `port`. BlueMap's markers written to its storage every 5 s (its default is 10), and its
 * accept-download left as it is unless `acceptDownload` says to switch it on: that setting is
 * the user's acceptance of Mojang's EULA for the client jar BlueMap downloads, never the
 * launcher's. squaremap's marker files written as soon as they change. Dynmap: see
 * companions.configureDynmap, which reads its template from the jar. Returns the files written.
 */
function configure(name, folder, { port, acceptDownload = false } = {}) {
  const dir = path.join(folder, 'plugins', MAPS[name].plugin);
  switch (name) {
    case 'bluemap': {
      const web = path.join(dir, 'webserver.conf');
      const core = path.join(dir, 'core.conf');
      const plugin = path.join(dir, 'plugin.conf');
      let c = setConf(readOr(core), 'metrics', 'false');
      // Only ever switched on here; a user who accepted in the file keeps it.
      if (acceptDownload) c = setConf(c, 'accept-download', 'true');
      else if (!/^accept-download\s*[:=]/m.test(c)) c = setConf(c, 'accept-download', 'false');
      return [
        write(web, setConf(setConf(readOr(web), 'ip', '"127.0.0.1"'), 'port', String(port))),
        write(core, c),
        write(plugin, setConf(readOr(plugin), 'write-markers-interval', '5')),
      ];
    }
    case 'squaremap': {
      const file = path.join(dir, 'config.yml');
      let body = readOr(file);
      body = setYaml(body, ['settings', 'internal-webserver', 'enabled'], 'true');
      body = setYaml(body, ['settings', 'internal-webserver', 'bind'], '127.0.0.1');
      body = setYaml(body, ['settings', 'internal-webserver', 'port'], String(port));
      // 1.3 and later: marker files written at once, not on squaremap's own 5 s timer.
      body = setYaml(body, ['settings', 'internal-webserver', 'flush-json-immediately'], 'true');
      return [write(file, body)];
    }
    case 'pl3xmap': {
      const file = path.join(dir, 'config.yml');
      let body = readOr(file);
      body = setYaml(body, ['settings', 'internal-webserver', 'enabled'], 'true');
      body = setYaml(body, ['settings', 'internal-webserver', 'bind'], '127.0.0.1');
      body = setYaml(body, ['settings', 'internal-webserver', 'port'], String(port));
      body = setYaml(body, ['settings', 'web-address'], `http://127.0.0.1:${port}`);
      return [write(file, body)];
    }
    default:
      throw new Error(`configure: ${name} is not written here`);
  }
}

/** The value of a nested key in a block-style YAML file, as text, or null. */
function readYaml(body, keys) {
  const lines = body.split(/\r?\n/);
  let indent = 0;
  let from = 0;
  for (let d = 0; d < keys.length; d++) {
    const re = new RegExp(`^ {${indent}}["']?${keys[d].replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}["']?\\s*:(.*)$`);
    let at = -1;
    for (let i = from; i < lines.length; i++) {
      if (/^\s*(#|$)/.test(lines[i])) continue;
      if (/^ */.exec(lines[i])[0].length < indent) break;
      const m = re.exec(lines[i]);
      if (m) { at = i; if (d === keys.length - 1) return m[1].replace(/\s+#.*$/, '').trim().replace(/^(["'])(.*)\1$/, '$2'); break; }
    }
    if (at < 0) return null;
    from = at + 1;
    const child = lines.slice(from).find((l) => !/^\s*(#|$)/.test(l));
    if (!child) return null;
    indent = /^ */.exec(child)[0].length;
  }
  return null;
}

/**
 * The web address a map serves in a server folder, from its own settings there (whatever wrote
 * them), always as 127.0.0.1: every map here binds that address only, and a browser can take
 * localhost to ::1 and be refused. Null when the map has no settings there, or no port in them.
 */
function readPort(name, folder) {
  const dir = path.join(folder, 'plugins', MAPS[name].plugin);
  try {
    switch (name) {
      case 'dynmap': {
        const m = /^webserver-port:\s*(\d+)/m.exec(fs.readFileSync(path.join(dir, 'configuration.txt'), 'utf8'));
        return m ? Number(m[1]) : null;
      }
      case 'bluemap': {
        const m = /^port\s*[:=]\s*(\d+)/m.exec(fs.readFileSync(path.join(dir, 'webserver.conf'), 'utf8'));
        return m ? Number(m[1]) : null;
      }
      case 'squaremap':
      case 'pl3xmap': {
        const v = readYaml(fs.readFileSync(path.join(dir, 'config.yml'), 'utf8'), ['settings', 'internal-webserver', 'port']);
        return v && /^\d+$/.test(v) ? Number(v) : null;
      }
      default:
        return null;
    }
  } catch {
    return null;
  }
}

// ---- web server up ---------------------------------------------------------------------------

/**
 * How each map says its web server started (the line names the address and port it bound), how
 * it says it did not, and a page only that map serves. BlueMap's started line was never seen
 * (it stops before its web server until accept-download is on), so its page alone decides.
 */
const WEB = {
  dynmap: { started: /\[dynmap\] .*[Ww]eb ?server started on (?:address )?(\S+)$/, failed: /\[dynmap\].*(Failed to start|Address already in use|BindException|Error starting)/i, page: '/up/configuration', expect: /"worlds"/ },
  bluemap: { started: null, failed: /\[BlueMap\].*(BindException|Address already in use|Failed to start)/i, page: '/settings.json', expect: /"maps"/ },
  squaremap: { started: /\[squaremap\] Internal webserver running on (\S+)$/, failed: /\[squaremap\].*(Failed to start|BindException|Address already in use)/i, page: '/tiles/settings.json', expect: /"worlds"/ },
  pl3xmap: { started: /\[Pl3xMap\].*Internal webserver running on (\S+)$/, failed: /\[Pl3xMap\].*(Failed to start|BindException|Address already in use)/i, page: '/tiles/settings.json', expect: /"worldSettings"/ },
};
/** BlueMap's words when its accept-download is off: it loads nothing, its web server included. */
const BLUEMAP_WAITS = /\[BlueMap\].*You must accept the required file download/;

/**
 * What a server's log (from `from`) says about a map's web server on `port`: { state, detail },
 * state 'started' (bound to 127.0.0.1:<port>), 'failed', 'elsewhere' (bound somewhere else),
 * 'waiting' (BlueMap without accept-download), or null when it has said nothing yet.
 */
function webLine(name, lines, port) {
  const w = WEB[name];
  for (const l of lines) {
    if (name === 'bluemap' && BLUEMAP_WAITS.test(l)) {
      return { state: 'waiting', detail: 'BlueMap waits for accept-download: true in plugins/BlueMap/core.conf (Mojang\'s EULA, for the client jar it downloads), which is yours to switch on' };
    }
    if (w.failed.test(l)) return { state: 'failed', detail: l.trim() };
    const m = w.started && w.started.exec(l.trim());
    if (m) return m[1] === `127.0.0.1:${port}` ? { state: 'started', detail: m[1] } : { state: 'elsewhere', detail: `bound ${m[1]}, not 127.0.0.1:${port}` };
  }
  return null;
}

// ---- rendering --------------------------------------------------------------------------------

/**
 * How each map is asked to render the worlds the facility uses, after `save-all flush`, and how
 * it says it has finished. squaremap names a world by its dimension key, Pl3xMap by its Bukkit
 * name. Both claim /map, and Pl3xMap gets it (seen on all three versions), so each is called by
 * its own name. Dynmap renders as chunks change and is not asked.
 *
 *   squaremap  "[squaremap] Finished rendering map for minecraft:overworld" in the log (seen)
 *   Pl3xMap    "pl3xmap status" says "Renderers are idle" (seen) after "Full render starting"
 *   BlueMap    "bluemap" lists its render tasks; none left is inferred from its help, not seen
 */
const RENDER = {
  squaremap: { worlds: ['minecraft:overworld', 'minecraft:the_nether'], command: (w) => `squaremap fullrender ${w}`, done: (w) => new RegExp(`\\[squaremap\\] Finished rendering map for ${w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) },
  pl3xmap: { worlds: ['world', 'world_nether'], command: (w) => `pl3xmap fullrender ${w}`, started: /\[Pl3xMap\].*Full render starting/, status: 'pl3xmap status', idle: /Renderers are idle/ },
  bluemap: { worlds: ['world', 'world_nether'], command: (w) => `bluemap update ${w}`, status: 'bluemap', idle: /no render-?tasks|render-?threads are idle|0 tasks? (queued|pending)/i },
};

// ---- status, for the dashboard ----------------------------------------------------------------

const STATUS = 'map-status.json';

/** Records a map's state in a server folder ({ <map>: { state, detail, at } }), for the dashboard. */
function writeStatus(folder, name, state, detail = '') {
  const file = path.join(folder, STATUS);
  const all = readStatus(folder);
  all[name] = { state, detail, at: new Date().toISOString() };
  try {
    fs.writeFileSync(`${file}.tmp`, `${JSON.stringify(all, null, 2)}\n`);
    fs.renameSync(`${file}.tmp`, file);
  } catch { /* the dashboard's view only: a run never fails for it */ }
}

/** The map states a launcher recorded in a server folder, or {}. */
function readStatus(folder) {
  try {
    const s = JSON.parse(fs.readFileSync(path.join(folder, STATUS), 'utf8'));
    return s && typeof s === 'object' && !Array.isArray(s) ? s : {};
  } catch {
    return {};
  }
}

/**
 * Every map for a lab folder, for the dashboard: [{ name, label, installed, url, why, state,
 * detail }]. Installed means the launcher's install record lists it and its jar is in plugins/;
 * its URL comes from its own settings there. A map not installed says why: the version has no
 * build of it (`pick` throws, with the reason), or the lab was started without it.
 */
function labMaps(folder, version, { pick = null } = {}) {
  let record = {};
  try { record = JSON.parse(fs.readFileSync(path.join(folder, 'plugins', '.wx-companions.json'), 'utf8')); } catch { /* none */ }
  const installed = new Map((Array.isArray(record.installed) ? record.installed : []).filter((r) => r && MAPS[r.name]).map((r) => [r.name, r]));
  const status = readStatus(folder);
  return NAMES.map((name) => {
    const m = MAPS[name];
    const r = installed.get(name);
    const has = Boolean(r && typeof r.file === 'string' && fs.existsSync(path.join(folder, 'plugins', path.basename(r.file))));
    const out = { name, label: m.label, installed: has, url: null, why: null, state: null, detail: null };
    if (!has) {
      if (version && pick) {
        try { pick(name, version); } catch (e) { out.why = e.message.replace(/^\S+ on \S+: /, ''); }
      }
      if (!out.why) out.why = `not installed in this lab: start it with -With ${name} (or -With maps)`;
      return out;
    }
    const port = readPort(name, folder);
    out.url = port ? `http://127.0.0.1:${port}/` : null;
    if (!port) out.why = `installed, but ${m.label} has no web port in its settings yet`;
    const s = status[name];
    if (s) { out.state = s.state; out.detail = s.detail; }
    if (name === 'bluemap' && !/^accept-download\s*[:=]\s*true/m.test(readOr(path.join(folder, 'plugins', 'BlueMap', 'core.conf')))) {
      out.state = 'waiting';
      out.detail = 'BlueMap draws nothing until accept-download: true is set in plugins/BlueMap/core.conf (Mojang\'s EULA, for the client jar it downloads); that is yours to switch on';
    }
    return out;
  });
}

module.exports = {
  MAPS, NAMES, SPAN, BASE_GAME_PORT, webPort, setConf, setYaml, readYaml, configure, readPort,
  WEB, webLine, RENDER, STATUS, writeStatus, readStatus, labMaps,
};
