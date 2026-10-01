'use strict';
// The world viewer (--viewer, --shots): prismarine-viewer's renderer on Probe, served on this
// machine only. Two pages: / orbits round Probe (third person), /first/ looks out of its eyes.
//
// prismarine-viewer 1.33.0 carries textures and block models up to 1.21.4 and draws a newer
// 1.x.y with the newest of its 1.x; it has nothing for 26.x, which is refused by name. A server
// newer than the assets numbers its block states differently (1.21.5 onwards inserted blocks),
// so every chunk and block update is translated, block by block, by name and properties, into
// the assets' numbering before it is sent; a block the assets do not have is drawn as stone.
// Its renderer draws y 0 to 255 only (the world before 1.18), so the page is shown the world
// raised by the dimension's depth below 0: the overworld's -64 is drawn at 0, and everything
// sent (chunks, block updates, Probe, entities) is moved up with it.

const path = require('path');
const http = require('http');
const { Vec3 } = require('vec3');

const PV_DIR = path.dirname(require.resolve('prismarine-viewer/package.json'));
const PV_VERSION = require('prismarine-viewer/package.json').version;
const { getVersion, supportedVersions } = require('prismarine-viewer/viewer/lib/version');
// worldView alone: the package's index loads the renderer, which needs the native canvas.
const { WorldView } = require('prismarine-viewer/viewer/lib/worldView');
const { setupRoutes } = require('prismarine-viewer/lib/common');
// prismarine-viewer's own copies, the ones its page was built against.
const fromViewer = (name) => require(require.resolve(name, { paths: [PV_DIR] }));

/** The viewer's web port for a game port: 3007 on 25590, offset one for one with the game port. */
const BASE_GAME_PORT = 25590;
const BASE_VIEWER_PORT = 3007;

function viewerPort(gamePort) {
  const port = BASE_VIEWER_PORT + (gamePort - BASE_GAME_PORT);
  if (port < 1024) throw new Error(`the viewer's web port for game port ${gamePort} would be ${port}: use --viewer-port, or a game port from ${BASE_GAME_PORT - BASE_VIEWER_PORT + 1024}`);
  return port;
}

/** The assets prismarine-viewer draws `version` with; throws, naming the version, if it has none. */
function assetVersion(version) {
  const v = getVersion(version);
  if (!v) {
    throw new Error(`prismarine-viewer ${PV_VERSION} cannot draw Minecraft ${version}: it has assets for `
      + `${supportedVersions[0]} to ${supportedVersions[supportedVersions.length - 1]} only`);
  }
  return v;
}

/** The values of a block state property, in minecraft-data's order (a bool is true, false). */
function valuesOf(s) {
  if (s.values) return s.values;
  if (s.type === 'bool') return ['true', 'false'];
  return Array.from({ length: s.num_values }, (_, i) => String(i));
}

/** A block's value index per property, for a state id of it: the last property varies fastest. */
function indices(block, stateId) {
  const out = [];
  let rest = stateId - block.minStateId;
  for (let i = block.states.length - 1; i >= 0; i--) {
    out[i] = rest % block.states[i].num_values;
    rest = Math.floor(rest / block.states[i].num_values);
  }
  return out;
}

/** The properties of a state id of `block` (a minecraft-data block), as strings. */
function decode(block, stateId) {
  const k = indices(block, stateId);
  return Object.fromEntries(block.states.map((s, i) => [s.name, valuesOf(s)[k[i]]]));
}

/** The state id of `block` with these properties; any it lacks, or values it lacks, take the default's. */
function encode(block, props) {
  if (!block.states || !block.states.length) return block.defaultState;
  const def = indices(block, block.defaultState);
  let index = 0;
  block.states.forEach((s, i) => {
    let k = props[s.name] === undefined ? -1 : valuesOf(s).indexOf(String(props[s.name]));
    if (k < 0) k = def[i];
    index = index * s.num_values + k;
  });
  return block.minStateId + index;
}

// Blocks renamed between the assets' version and the server's: the server's name, the assets'.
// The only two between 1.20.1 and 1.20.4 and between 1.21.4 and 1.21.11.
const RENAMED = { short_grass: 'grass', iron_chain: 'chain' };

/**
 * State ids of `from` mapped to those of `to`: { map: Int32Array, unknown: [names drawn as
 * stone], stone: the stone state id of `to` }.
 */
function stateMap(from, to) {
  const mcData = require('minecraft-data');
  const src = mcData(from);
  const dst = mcData(to);
  const stone = dst.blocksByName.stone.defaultState;
  const max = src.blocksArray.reduce((m, b) => Math.max(m, b.maxStateId), 0);
  const map = new Int32Array(max + 1).fill(stone);
  const unknown = [];
  for (const b of src.blocksArray) {
    const d = dst.blocksByName[b.name] || dst.blocksByName[RENAMED[b.name]];
    if (!d) { unknown.push(b.name); continue; }
    for (let id = b.minStateId; id <= b.maxStateId; id++) {
      map[id] = b.states && b.states.length ? encode(d, decode(b, id)) : d.defaultState;
    }
  }
  return { map, unknown, stone };
}

/** The height the page can draw: y 0 to 255. */
const DRAWN = 256;

/**
 * Copies of chunk columns in the assets' version and numbering, raised so the column's floor is
 * at 0. The renderer draws 256 blocks of height, so an overworld column (-64 to 319) loses what
 * is from y 192 up; `onCut(y)` is called with the lowest such section's world y each time one
 * with blocks in it is left out.
 */
function translator(from, to, { onCut = () => {}, Column = fromViewer('prismarine-chunk')(to) } = {}) {
  const { map, unknown, stone } = stateMap(from, to);
  const state = (id) => (id >= 0 && id < map.length ? map[id] : stone);
  const at = new Vec3(0, 0, 0);
  const into = new Vec3(0, 0, 0);
  const column = (col) => {
    const out = new Column({ minY: 0, worldHeight: DRAWN });
    col.sections.forEach((s, i) => {
      if (!s || s.solidBlockCount === 0) return; // air throughout: nothing to copy
      if (16 * i >= DRAWN) { onCut(col.minY + 16 * i); return; }
      const y0 = col.minY + 16 * i;
      for (let y = y0; y < y0 + 16; y++) {
        for (let z = 0; z < 16; z++) {
          for (let x = 0; x < 16; x++) {
            at.set(x, y, z);
            const id = col.getBlockStateId(at);
            into.set(x, y - col.minY, z);
            if (id) out.setBlockStateId(into, state(id));
          }
        }
      }
    });
    return out;
  };
  return { column, state, unknown };
}

/**
 * What WorldView sends a page, on its way out: a block update translated and raised by `lift()`,
 * an entity raised, a loaded chunk counted in `count`; the rest as it is.
 */
function relay(socket, tr, lift, count = { chunks: 0 }) {
  const up = (p) => (p ? { x: p.x, y: p.y + lift(), z: p.z } : p);
  return {
    up,
    count,
    on: (...a) => socket.on(...a),
    emit: (evt, data) => {
      if (evt === 'blockUpdate') socket.emit(evt, { pos: up(data.pos), stateId: tr.state(data.stateId) });
      else if (evt === 'entity') socket.emit(evt, data.pos ? { ...data, pos: up(data.pos) } : data);
      else {
        if (evt === 'loadChunk') count.chunks++;
        socket.emit(evt, data);
      }
    },
  };
}

/** The shot token a page was opened with (?shot=...), from its socket's Referer; null if none. */
function shotToken(referer) {
  const m = /[?&]shot=([\w-]+)/.exec(referer || '');
  return m ? m[1] : null;
}

/** True for a host name (Host or Origin) of this machine: a page elsewhere may not drive the viewer. */
function localHost(value) {
  if (!value) return false;
  let host = value;
  try { if (/^[a-z]+:\/\//i.test(value)) host = new URL(value).host; } catch { return false; }
  const name = host.replace(/:\d+$/, '').replace(/^\[|\]$/g, '');
  return name === '127.0.0.1' || name === 'localhost' || name === '::1';
}

/** Socket.io's allowRequest: Host must be this machine, and so must Origin when a browser sends one. */
function allowLocal(req, callback) {
  const h = req.headers || {};
  callback(null, localHost(h.host) && (h.origin === undefined || localHost(h.origin)));
}

/** Listens on 127.0.0.1:`port`, retrying for a few seconds while the port is still held (a restart's old viewer). */
async function listen(srv, port, tries = 10) {
  for (let i = 1; ; i++) {
    try {
      await new Promise((resolve, reject) => {
        srv.once('error', reject);
        srv.listen(port, '127.0.0.1', () => { srv.off('error', reject); resolve(); });
      });
      return;
    } catch (e) {
      if (e.code !== 'EADDRINUSE' || i >= tries) throw new Error(`the viewer cannot listen on 127.0.0.1:${port}: ${e.message}`);
      await new Promise((resolve) => { setTimeout(resolve, 500); });
    }
  }
}

/**
 * Serves the viewer for `bot` on 127.0.0.1:`port`. Resolves once listening with { url, assets,
 * chunks(token), close() }; `url` is the orbit page, `url + 'first/'` the first-person one, and
 * chunks(token) is how many chunks the page opened with ?shot=<token> has been sent (0 before
 * it connects), whatever other pages are open.
 */
async function startViewer(bot, { port, viewDistance = 8, log = () => {} }) {
  const assets = assetVersion(bot.version);
  let cut = false;
  const tr = translator(bot.version, assets, {
    onCut: (y) => {
      if (cut) return;
      cut = true;
      log(`  viewer: the renderer draws 256 blocks of height, so blocks from y ${y} up are left out (this is said once)`);
    },
  });
  if (tr.unknown.length) log(`  viewer: ${tr.unknown.length} blocks of ${bot.version} are not in its ${assets} assets and show as stone: ${tr.unknown.join(', ')}`);
  const express = fromViewer('express');
  const socketIo = fromViewer('socket.io');
  const app = express();
  app.use((req, res, next) => (localHost(req.headers.host) ? next() : res.status(403).end()));
  // Exactly /first: Express matches /first/ to a /first route as well, which would loop.
  app.get(/^\/first$/, (req, res) => res.redirect('/first/'));
  setupRoutes(app, '/first');
  setupRoutes(app, '');
  const srv = http.createServer(app);
  // The world as WorldView reads it: Probe's current world (a dimension change replaces it),
  // each column translated into the assets' numbering.
  const world = {
    getColumnAt: async (pos) => {
      const col = bot.world.getColumnAt(pos);
      return col ? tr.column(col) : null;
    },
    raycast: (...a) => bot.world.raycast(...a),
  };
  const views = new Set();
  const counts = new Map(); // shot token -> its page's chunk count
  // How far the page's world is raised: the current dimension's depth below 0.
  const lift = () => -(bot.game.minY || 0);
  const serve = (io, firstPerson) => io.on('connection', (socket) => {
    const out = relay(socket, tr, lift);
    const token = shotToken(socket.handshake.headers.referer);
    if (token) counts.set(token, out.count);
    socket.emit('version', assets);
    const view = new WorldView(world, viewDistance, bot.entity.position, out);
    const position = () => {
      const p = { pos: out.up(bot.entity.position), yaw: bot.entity.yaw, addMesh: true };
      if (firstPerson) p.pitch = bot.entity.pitch;
      socket.emit('position', p);
      view.updatePosition(bot.entity.position);
    };
    // Another dimension: drop every chunk the page holds and load the new world's round Probe.
    const respawn = () => {
      for (const k of Object.keys(view.loadedChunks)) {
        const [x, z] = k.split(',').map(Number);
        view.unloadChunk(new Vec3(x, 0, z));
      }
      setTimeout(() => { if (views.has(entry)) view.init(bot.entity.position).catch(() => {}); position(); }, 1000);
    };
    const entry = { socket, view, position, respawn };
    views.add(entry);
    view.init(bot.entity.position).catch(() => {});
    view.listenToBot(bot);
    bot.on('move', position);
    bot.on('forcedMove', position);
    bot.on('respawn', respawn);
    position();
    socket.on('disconnect', () => {
      views.delete(entry);
      bot.off('move', position);
      bot.off('forcedMove', position);
      bot.off('respawn', respawn);
      view.removeListenersFromBot(bot);
    });
  });
  // Two socket.io servers on one HTTP server: neither may close the other's upgrades.
  const ios = [
    socketIo(srv, { path: '/socket.io', destroyUpgrade: false, allowRequest: allowLocal }),
    socketIo(srv, { path: '/first/socket.io', destroyUpgrade: false, allowRequest: allowLocal }),
  ];
  serve(ios[0], false);
  serve(ios[1], true);
  await listen(srv, port);
  const url = `http://127.0.0.1:${port}/`;
  return {
    url,
    assets,
    chunks: (token) => (counts.get(token) || { chunks: 0 }).chunks,
    // Resolves once the port is free again: the pages are dropped, then the HTTP server closed.
    close: () => new Promise((done) => {
      for (const io of ios) {
        io.disconnectSockets(true);
        io.engine.close();
      }
      if (srv.closeAllConnections) srv.closeAllConnections();
      srv.close(() => done());
    }),
  };
}

module.exports = {
  viewerPort, assetVersion, stateMap, encode, decode, translator, relay, shotToken, localHost, allowLocal, startViewer, PV_VERSION, RENAMED,
};
