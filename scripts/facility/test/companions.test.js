'use strict';
// lib/companions.js without a server: what the launcher may delete or overwrite in plugins/,
// the integration switches it turns on and puts back, and a jar it cannot read. Run with
// `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const c = require('../lib/companions');

const sha = (s) => crypto.createHash('sha256').update(s).digest('hex');

/** A scratch server folder with a config.yml, and a pinned "jar" of `bytes`; removed after `fn`. */
function scratch(fn, config = 'dynmap-enabled: false\nother: 1\n') {
  const d = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-companions-'));
  try {
    const folder = path.join(d, 'srv');
    fs.mkdirSync(path.join(folder, 'plugins', 'WormholeXTreme'), { recursive: true });
    fs.writeFileSync(path.join(folder, 'plugins', 'WormholeXTreme', 'config.yml'), config);
    const jar = (file, plugin, bytes) => {
      const src = path.join(d, `${file}.src`);
      fs.writeFileSync(src, bytes);
      return { name: plugin.toLowerCase(), plugin, version: '1', file, jar: src, sha256: sha(bytes) };
    };
    fn({ d, folder, plugins: path.join(folder, 'plugins'), jar });
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
}

const config = (folder) => fs.readFileSync(path.join(folder, 'plugins', 'WormholeXTreme', 'config.yml'), 'utf8');
const journal = (folder) => { try { return JSON.parse(fs.readFileSync(path.join(folder, 'facility-settings.json'), 'utf8')); } catch { return {}; } };

/** What Config.set does for a cell's needs, then the kill: the replaced value journalled, the new one saved. */
function killedAfterSetting(folder, before, now) {
  fs.writeFileSync(path.join(folder, 'facility-settings.json'), JSON.stringify({ 'dynmap-enabled': before, 'map-show-rings': 'true' }));
  c.seedSettings(folder, { 'dynmap-enabled': now });
}

const ON = { 'dynmap-enabled': 'true' };

test('a record naming "", ".." or a path deletes nothing outside plugins/', () => scratch(({ folder, plugins }) => {
  fs.writeFileSync(path.join(folder, 'keep.txt'), 'x');
  fs.writeFileSync(path.join(plugins, c.RECORD), JSON.stringify({ files: ['', '..', '../keep.txt'], plugins: ['', '..', '../srv', '.'] }));
  c.install(folder, [], { fresh: true });
  assert.ok(fs.existsSync(path.join(folder, 'keep.txt')));
  assert.ok(fs.existsSync(plugins));
}));

test('a same-named jar no run installed is refused and left as it was', () => scratch(({ plugins, folder, jar }) => {
  const wg = jar('wg.jar', 'WorldGuard', 'pinned');
  fs.writeFileSync(path.join(plugins, 'wg.jar'), 'somebody else\'s');
  assert.throws(() => c.install(folder, [wg]), /already there, is not the pinned WorldGuard/);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'wg.jar'), 'utf8'), 'somebody else\'s');
}));

test('a same-named jar that is the pinned build is adopted, and taken out by a run without it', () => scratch(({ plugins, folder, jar }) => {
  const wg = jar('wg.jar', 'WorldGuard', 'pinned');
  fs.writeFileSync(path.join(plugins, 'wg.jar'), 'pinned');
  c.install(folder, [wg]);
  assert.deepStrictEqual(c.install(folder, []).removed, ['wg.jar']);
}));

test('a switch is turned on with the file\'s line endings, and put back by a run without its companion', () => scratch(({ folder, jar }) => {
  const wg = jar('wg.jar', 'WorldGuard', 'pinned');
  c.install(folder, [wg], { switches: { 'worldguard-enabled': 'true' } });
  assert.strictEqual(config(folder), 'a: 1\r\nworldguard-enabled: true\r\nb: 2\r\n');
  const r = c.install(folder, []);
  assert.strictEqual(config(folder), 'a: 1\r\nworldguard-enabled: false\r\nb: 2\r\n');
  assert.deepStrictEqual(r, { removed: ['wg.jar'], unseeded: ['worldguard-enabled: false'] });
}, 'a: 1\r\nworldguard-enabled: false\r\nb: 2\r\n'));

test('killed in `map off`: a run without Dynmap has the switch off, and the journal nothing to turn it on', () => scratch(({ folder, jar }) => {
  c.install(folder, [jar('Dynmap.jar', 'dynmap', 'dyn')], { switches: ON });
  killedAfterSetting(folder, 'true', 'false');
  c.install(folder, []);
  assert.match(config(folder), /dynmap-enabled: false/);
  assert.deepStrictEqual(journal(folder), { 'map-show-rings': 'true' }); // the rest is recover()'s, as before
}));

test('killed in `map absent`: --with dynmap keeps the switch on, and the journal cannot turn it off', () => scratch(({ folder, jar }) => {
  c.install(folder, []);
  killedAfterSetting(folder, 'false', 'true');
  c.install(folder, [jar('Dynmap.jar', 'dynmap', 'dyn')], { switches: ON });
  assert.match(config(folder), /dynmap-enabled: true/);
  assert.ok(!('dynmap-enabled' in journal(folder)));
  c.install(folder, []);
  assert.match(config(folder), /dynmap-enabled: false/, 'and a run without Dynmap after that puts back the value from before the kill');
}));

test('killed in `map absent`, then another run without Dynmap: the switch goes back off', () => scratch(({ folder }) => {
  c.install(folder, []);
  killedAfterSetting(folder, 'false', 'true');
  c.install(folder, []);
  assert.match(config(folder), /dynmap-enabled: false/);
  assert.ok(!('dynmap-enabled' in journal(folder)));
}));

test('a jar cut short is refused by name', () => scratch(({ d }) => {
  const cut = path.join(d, 'cut.jar');
  fs.writeFileSync(cut, Buffer.concat([Buffer.from([0x50, 0x4b, 0x05, 0x06]), Buffer.alloc(10)]));
  assert.throws(() => c.classJava(cut), /cut\.jar/);
}));

test('Dynmap\'s web port follows the game port, and a port that would be out of range is refused', () => {
  assert.strictEqual(c.dynmapPort(25590), 8123);
  assert.strictEqual(c.dynmapPort(25660), 8193);
  assert.throws(() => c.dynmapPort(17000), /would be -467/);
});

test('every pinned build comes from an official source, never SpigotMC, with a full SHA-256 and size', () => {
  const m = c.manifest();
  for (const [name, comp] of Object.entries(m.companions)) {
    for (const b of comp.builds) {
      assert.match(b.sha256, /^[0-9a-f]{64}$/, `${name} ${b.version}`);
      assert.ok(Number.isInteger(b.size) && b.size > 0, `${name} ${b.version}`);
      assert.ok(!/spigotmc/i.test(b.url), `${name} ${b.version} is from SpigotMC`);
      assert.match(b.url, /^https:\/\/(cdn\.modrinth\.com|github\.com|dynmap\.us)\//, `${name} ${b.version}`);
    }
  }
});

test('BlueMap, squaremap and Pl3xMap each have a build for every version the facility runs, Dynmap none on 26.x', () => {
  const pinned = (name, v) => c.pick(name, v).file;
  assert.deepStrictEqual(['1.20.4', '1.21.11', '26.1.2'].map((v) => pinned('bluemap', v)), ['bluemap-5.16-spigot.jar', 'bluemap-5.16-spigot.jar', 'bluemap-5.28-spigot.jar']);
  assert.deepStrictEqual(['1.20.4', '1.21.11', '26.1.2'].map((v) => pinned('squaremap', v)),
    ['squaremap-paper-mc1.20.4-1.2.3.jar', 'squaremap-paper-mc1.21.11-1.3.12.jar', 'squaremap-paper-mc26.1.2-1.3.13.2.jar']);
  assert.deepStrictEqual(['1.20.4', '1.21.11', '26.1.2'].map((v) => pinned('pl3xmap', v)), ['Pl3xMap-1.20.4-492.jar', 'Pl3xMap-1.21.11-544.jar', 'Pl3xMap-26.1.2-550.jar']);
  // The hashes checked against the jars downloaded from Modrinth on 2026-10-06.
  assert.strictEqual(c.pick('bluemap', '26.1.2').sha256, '5601908281fdf5e0923760b4d54f328c1cccf2427b5f149c93030076a2d3ccdd');
  assert.strictEqual(c.pick('squaremap', '1.21.11').sha256, '0e4befd7ca97b300860803ad87fb449332251e3b7fa08d7c7d8ff07a02f0f612');
  assert.strictEqual(c.pick('pl3xmap', '1.20.4').sha256, 'ac65b23d2b9947e1e1fb306b031b53099ab40dbf731b9f013ef95d013f59c0f2');
  assert.throws(() => c.pick('dynmap', '26.1.2'), /no Dynmap build supports 26\.x/);
  assert.throws(() => c.pick('squaremap', '1.21.4'), /no build of squaremap is pinned for 1\.21\.4/);
});

test('--with maps on 26.1.2 leaves Dynmap out and says why; Dynmap named on its own is still refused there', () => {
  const r = c.forVersion(['maps'], '26.1.2');
  assert.deepStrictEqual(r.names, ['bluemap', 'squaremap', 'pl3xmap']);
  assert.deepStrictEqual(r.skipped.map((s) => s.name), ['dynmap']);
  assert.match(r.skipped[0].why, /no Dynmap build supports 26\.x/);
  assert.deepStrictEqual(c.forVersion(['maps'], '1.21.11').names, ['dynmap', 'bluemap', 'squaremap', 'pl3xmap']);
  assert.deepStrictEqual(c.forVersion(['dynmap', 'regions'], '26.1.2').names, ['dynmap', 'worldedit', 'worldguard']);
});

test('a map web server that fails to bind is a companion fault; another plugin\'s line is not', () => {
  assert.ok(c.companionFault('[09:00:00 ERROR]: [squaremap] Failed to start internal webserver', ['squaremap']));
  assert.ok(c.companionFault('[09:00:00 WARN]: [Pl3xMap] java.net.BindException: Address already in use', ['Pl3xMap']));
  assert.strictEqual(c.companionFault('[09:00:00 ERROR]: [squaremap] Failed to start internal webserver', ['Pl3xMap']), null);
  assert.strictEqual(c.companionFault('[09:00:00 INFO]: [squaremap] Internal webserver running on 127.0.0.1:8400', ['squaremap']), null);
});
