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
const RENAMED = { short_grass: 'grass' };

/** State ids of `from` mapped to those of `to`: { map: Int32Array, unknown: [names drawn as stone] }. */
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
  return { map, unknown };
}

/** The height the page can draw: y 0 to 255. */
const DRAWN = 256;

/** A copy of a chunk column in the assets' version and numbering, raised so its floor is at 0. */
function translator(from, to) {
  const { map, unknown } = stateMap(from, to);
  const Column = fromViewer('prismarine-chunk')(to);
  const at = new Vec3(0, 0, 0);
  const column = (col) => {
    const out = new Column({ minY: 0, worldHeight: DRAWN });
    const to = new Vec3(0, 0, 0);
    col.sections.forEach((s, i) => {
      if (!s || s.solidBlockCount === 0 || 16 * i >= DRAWN) return;
      const y0 = col.minY + 16 * i;
      for (let y = y0; y < y0 + 16; y++) {
        for (let z = 0; z < 16; z++) {
          for (let x = 0; x < 16; x++) {
            at.set(x, y, z);
            const id = col.getBlockStateId(at);
            to.set(x, y - col.minY, z);
            if (id) out.setBlockStateId(to, map[id] === undefined ? map[1] : map[id]);
          }
        }
      }
    });
    return out;
  };
  return { column, state: (id) => (map[id] === undefined ? 0 : map[id]), unknown };
}

/**
 * Serves the viewer for `bot` on 127.0.0.1:`port`. Resolves once listening with { url, close() };
 * `url` is the orbit page, `url + 'first/'` the first-person one.
 */
function startViewer(bot, { port, viewDistance = 8, log = () => {} }) {
  const assets = assetVersion(bot.version);
  const tr = translator(bot.version, assets);
  if (tr.unknown.length) log(`  viewer: ${tr.unknown.length} blocks of ${bot.version} are not in its ${assets} assets and show as stone`);
  const express = fromViewer('express');
  const socketIo = fromViewer('socket.io');
  const app = express();
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
  // How far the page's world is raised: the current dimension's depth below 0.
  const lift = () => -(bot.game.minY || 0);
  const up = (p) => (p ? { x: p.x, y: p.y + lift(), z: p.z } : p);
  const serve = (io, firstPerson) => io.on('connection', (socket) => {
    // A block update's state id is translated on its way out, like the chunks, and it and an
    // entity raised with them.
    const out = {
      on: (...a) => socket.on(...a),
      emit: (evt, data) => {
        if (evt === 'blockUpdate') socket.emit(evt, { pos: up(data.pos), stateId: tr.state(data.stateId) });
        else if (evt === 'entity') socket.emit(evt, data.pos ? { ...data, pos: up(data.pos) } : data);
        else socket.emit(evt, data);
      },
    };
    socket.emit('version', assets);
    const view = new WorldView(world, viewDistance, bot.entity.position, out);
    const position = () => {
      const p = { pos: up(bot.entity.position), yaw: bot.entity.yaw, addMesh: true };
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
    socketIo(srv, { path: '/socket.io', destroyUpgrade: false }),
    socketIo(srv, { path: '/first/socket.io', destroyUpgrade: false }),
  ];
  serve(ios[0], false);
  serve(ios[1], true);
  return new Promise((resolve, reject) => {
    srv.once('error', (e) => reject(new Error(`the viewer cannot listen on 127.0.0.1:${port}: ${e.message}`)));
    srv.listen(port, '127.0.0.1', () => {
      const url = `http://127.0.0.1:${port}/`;
      resolve({
        url,
        assets,
        close: () => new Promise((done) => {
          for (const io of ios) io.close();
          srv.close(() => done());
          setTimeout(done, 2000).unref();
        }),
      });
    });
  });
}

module.exports = { viewerPort, assetVersion, stateMap, encode, decode, startViewer, PV_VERSION };
