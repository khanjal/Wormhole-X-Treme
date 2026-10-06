'use strict';
// dashboard.js without its server: which maps it finds in each lab folder, at what address, and
// what it says about the ones missing. Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const net = require('net');
const os = require('os');
const path = require('path');
const maps = require('../lib/maps');
const companions = require('../lib/companions');
const dashboard = require('../dashboard');

/** A .local-server with one lab folder for `version` on `port`, the given maps installed and configured. */
function labs(version, port, installed, fn) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-dash-'));
  const folder = path.join(root, `facility-${version}-${port}`);
  fs.mkdirSync(path.join(folder, 'logs'), { recursive: true });
  fs.mkdirSync(path.join(folder, 'plugins'), { recursive: true });
  for (const name of installed) {
    fs.writeFileSync(path.join(folder, 'plugins', `${name}.jar`), name);
    if (name === 'dynmap') {
      fs.mkdirSync(path.join(folder, 'plugins', 'dynmap'), { recursive: true });
      fs.writeFileSync(path.join(folder, 'plugins', 'dynmap', 'configuration.txt'), `webserver-port: ${maps.webPort(name, port)}\n`);
    } else {
      maps.configure(name, folder, { port: maps.webPort(name, port) });
    }
  }
  fs.writeFileSync(path.join(folder, 'plugins', companions.RECORD), JSON.stringify({ installed: installed.map((name) => ({ name, file: `${name}.jar` })) }));
  return Promise.resolve(fn(root, folder)).finally(() => fs.rmSync(root, { recursive: true, force: true }));
}

test('a 26.1.2 lab with squaremap and Pl3xMap: both at 127.0.0.1 on its own ports, Dynmap missing for want of a build', () => labs('26.1.2', 25610, ['squaremap', 'pl3xmap'], (root) => {
  const [lab] = dashboard.findLabs(root);
  assert.strictEqual(lab.name, '26.1.2 · :25610');
  const by = Object.fromEntries(lab.maps.map((m) => [m.name, m]));
  assert.strictEqual(by.squaremap.url, 'http://127.0.0.1:8420/');
  assert.strictEqual(by.pl3xmap.url, 'http://127.0.0.1:8520/');
  assert.strictEqual(by.dynmap.installed, false);
  assert.match(by.dynmap.why, /no Dynmap build supports 26\.x/);
  assert.match(by.bluemap.why, /-With bluemap/);
  // The page carries them, and names no localhost address.
  const html = dashboard.page([lab]);
  assert.ok(html.includes('http://127.0.0.1:8420/'));
  assert.ok(!/http:\/\/localhost:84/.test(html));
}));

test('a 1.21.11 lab with Dynmap: its address read from Dynmap\'s own settings', () => labs('1.21.11', 25620, ['dynmap'], (root) => {
  const by = Object.fromEntries(dashboard.findLabs(root)[0].maps.map((m) => [m.name, m]));
  assert.strictEqual(by.dynmap.url, 'http://127.0.0.1:8153/');
  assert.strictEqual(by.squaremap.installed, false);
}));

test('a map whose port nobody listens on is said not to answer; one that listens, to answer', () => labs('1.21.11', 25630, ['squaremap', 'pl3xmap'], async (root) => {
  const [lab] = dashboard.findLabs(root);
  const srv = net.createServer().listen(maps.webPort('squaremap', 25630), '127.0.0.1');
  await new Promise((resolve) => { srv.on('listening', resolve); });
  try {
    const by = Object.fromEntries((await dashboard.mapState(lab)).map((m) => [m.name, m]));
    assert.strictEqual(by.squaremap.answers, true);
    assert.strictEqual(by.pl3xmap.answers, false);
    assert.strictEqual(by.dynmap.answers, undefined, 'not installed: not asked');
  } finally {
    srv.close();
  }
}));
