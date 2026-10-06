'use strict';
// lib/maps.js without a server: each web map's port, the settings written before it starts (on a
// fresh folder and over the files the plugins themselves wrote on a real run, test/fixtures/maps),
// how its log is read for its web server, and what the dashboard is told about each map in a lab.
// Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const maps = require('../lib/maps');
const companions = require('../lib/companions');

const FIX = path.join(__dirname, 'fixtures', 'maps');

function scratch(fn) {
  const d = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-maps-'));
  try { return fn(d); } finally { fs.rmSync(d, { recursive: true, force: true }); }
}

test('every map\'s web port follows the game port, and no two labs or maps side by side share one', () => {
  assert.deepStrictEqual(maps.NAMES.map((n) => maps.webPort(n, 25590)), [8123, 8300, 8400, 8500]);
  assert.strictEqual(maps.webPort('squaremap', 25620), 8430);
  // Every lab port a run uses (25590 to 25689) with every map: all different.
  const seen = new Map();
  for (let p = 25590; p < 25590 + maps.SPAN; p++) {
    for (const n of maps.NAMES) {
      const w = maps.webPort(n, p);
      assert.ok(!seen.has(w), `${n} on ${p} and ${seen.get(w)} both want ${w}`);
      seen.set(w, `${n} on ${p}`);
    }
  }
  assert.throws(() => maps.webPort('bluemap', 25590 + maps.SPAN), /in the next map's range/);
  assert.throws(() => maps.webPort('pl3xmap', 17000), /would be -/);
});

test('a fresh folder gets each map on 127.0.0.1 at its port, and the port reads back', () => scratch((d) => {
  maps.configure('squaremap', d, { port: 8400 });
  maps.configure('pl3xmap', d, { port: 8500 });
  maps.configure('bluemap', d, { port: 8300 });
  const sq = fs.readFileSync(path.join(d, 'plugins', 'squaremap', 'config.yml'), 'utf8');
  assert.strictEqual(maps.readYaml(sq, ['settings', 'internal-webserver', 'bind']), '127.0.0.1');
  assert.strictEqual(maps.readYaml(sq, ['settings', 'internal-webserver', 'flush-json-immediately']), 'true');
  const pl = fs.readFileSync(path.join(d, 'plugins', 'Pl3xMap', 'config.yml'), 'utf8');
  assert.strictEqual(maps.readYaml(pl, ['settings', 'internal-webserver', 'bind']), '127.0.0.1');
  const web = fs.readFileSync(path.join(d, 'plugins', 'BlueMap', 'webserver.conf'), 'utf8');
  assert.match(web, /^ip: "127\.0\.0\.1"$/m);
  assert.match(fs.readFileSync(path.join(d, 'plugins', 'BlueMap', 'plugin.conf'), 'utf8'), /^write-markers-interval: 5$/m);
  assert.deepStrictEqual(['squaremap', 'pl3xmap', 'bluemap'].map((n) => maps.readPort(n, d)), [8400, 8500, 8300]);
}));

test('the files squaremap and Pl3xMap wrote on a real run keep everything but the keys set', () => scratch((d) => {
  for (const [name, dir, file] of [['squaremap', 'squaremap', 'squaremap-1.21.11-config.yml'], ['pl3xmap', 'Pl3xMap', 'pl3xmap-1.21.11-config.yml']]) {
    const to = path.join(d, 'plugins', dir, 'config.yml');
    fs.mkdirSync(path.dirname(to), { recursive: true });
    // As the plugin wrote it, with Windows line endings, and bound everywhere.
    const before = fs.readFileSync(path.join(FIX, file), 'utf8').replace(/\r?\n/g, '\r\n').replace('bind: 127.0.0.1', 'bind: 0.0.0.0');
    fs.writeFileSync(to, before);
    maps.configure(name, d, { port: 8642 });
    const after = fs.readFileSync(to, 'utf8');
    assert.strictEqual(maps.readPort(name, d), 8642, name);
    assert.strictEqual(maps.readYaml(after, ['settings', 'internal-webserver', 'bind']), '127.0.0.1', name);
    assert.ok(!/[^\r]\n/.test(after), `${name}: its CRLF line endings kept`);
    // Only the port, bind (and Pl3xMap's web-address) lines differ; nothing is added or lost.
    const a = before.split('\r\n');
    const b = after.split('\r\n');
    assert.strictEqual(b.length, a.length, name);
    const changed = a.map((l, i) => (l === b[i] ? null : b[i].trim())).filter(Boolean);
    assert.deepStrictEqual(changed, name === 'squaremap'
      ? ['bind: 127.0.0.1', 'port: 8642']
      : ['bind: 127.0.0.1', 'port: 8642', 'web-address: http://127.0.0.1:8642'], name);
  }
}));

test('BlueMap\'s accept-download is never switched on unless asked, and a user\'s yes is kept', () => scratch((d) => {
  const core = path.join(d, 'plugins', 'BlueMap', 'core.conf');
  maps.configure('bluemap', d, { port: 8300 });
  assert.match(fs.readFileSync(core, 'utf8'), /^accept-download: false$/m);
  fs.writeFileSync(core, 'accept-download: true\nmetrics: true\n');
  maps.configure('bluemap', d, { port: 8300 });
  assert.match(fs.readFileSync(core, 'utf8'), /^accept-download: true$/m);
  assert.match(fs.readFileSync(core, 'utf8'), /^metrics: false$/m);
  fs.writeFileSync(core, 'accept-download: false\n');
  maps.configure('bluemap', d, { port: 8300, acceptDownload: true });
  assert.match(fs.readFileSync(core, 'utf8'), /^accept-download: true$/m);
}));

test('a commented-out BlueMap key is set in place, not added twice', () => {
  const body = '# How often\n#write-markers-interval: 10\nother: 1\n';
  assert.strictEqual(maps.setConf(body, 'write-markers-interval', '5'), '# How often\nwrite-markers-interval: 5\nother: 1\n');
});

test('a map\'s log says where its web server bound: 127.0.0.1 at its port, elsewhere, or not at all', () => {
  const sq = '[08:51:29 INFO]: [squaremap] Internal webserver running on 127.0.0.1:8470';
  assert.strictEqual(maps.webLine('squaremap', [sq], 8470).state, 'started');
  assert.strictEqual(maps.webLine('squaremap', [sq], 8471).state, 'elsewhere');
  assert.strictEqual(maps.webLine('squaremap', [sq.replace('127.0.0.1', '0.0.0.0')], 8470).state, 'elsewhere');
  assert.strictEqual(maps.webLine('pl3xmap', ['[08:51:34 INFO]: [Pl3xMap] [INFO] Internal webserver running on 127.0.0.1:8570'], 8570).state, 'started');
  assert.strictEqual(maps.webLine('dynmap', ['[08:51:33 INFO]: [dynmap] Web server started on address 127.0.0.1:8193'], 8193).state, 'started');
  assert.strictEqual(maps.webLine('bluemap', ['[08:51:36 WARN]: [BlueMap] You must accept the required file download in order for BlueMap to work!'], 8300).state, 'waiting');
  assert.strictEqual(maps.webLine('squaremap', ['[x] [Pl3xMap] [INFO] Internal webserver running on 127.0.0.1:8470'], 8470), null);
});

test('the dashboard is told which maps a lab has, each one\'s address, and why the others are missing', () => scratch((d) => {
  const plugins = path.join(d, 'plugins');
  fs.mkdirSync(plugins, { recursive: true });
  fs.writeFileSync(path.join(plugins, 'squaremap.jar'), 'x');
  fs.writeFileSync(path.join(plugins, 'bluemap.jar'), 'x');
  fs.writeFileSync(path.join(plugins, companions.RECORD), JSON.stringify({ installed: [
    { name: 'squaremap', file: 'squaremap.jar' }, { name: 'bluemap', file: 'bluemap.jar' }, { name: 'pl3xmap', file: 'gone.jar' },
  ] }));
  maps.configure('squaremap', d, { port: 8432 });
  maps.configure('bluemap', d, { port: 8332 });
  maps.writeStatus(d, 'squaremap', 'rendering', 'world, since 09:00');
  const got = Object.fromEntries(maps.labMaps(d, '26.1.2', { pick: companions.pick }).map((m) => [m.name, m]));
  assert.strictEqual(got.squaremap.url, 'http://127.0.0.1:8432/');
  assert.strictEqual(got.squaremap.state, 'rendering');
  assert.strictEqual(got.bluemap.installed, true);
  assert.strictEqual(got.bluemap.state, 'waiting', 'BlueMap without accept-download says so');
  assert.strictEqual(got.pl3xmap.installed, false, 'a recorded jar that is gone is not installed');
  assert.match(got.pl3xmap.why, /-With pl3xmap/);
  assert.match(got.dynmap.why, /^no Dynmap build supports 26\.x/);
}));
