'use strict';
// lib/extras.js without a server: what the plugins-extra copy may add to, overwrite in and take
// out of plugins/. Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const extras = require('../lib/extras');

/** A scratch server folder and a plugins-extra folder; removed after `fn`. */
function scratch(fn) {
  const d = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-extras-'));
  try {
    const folder = path.join(d, 'srv');
    const from = path.join(d, 'extra');
    fs.mkdirSync(from, { recursive: true });
    fn({ folder, from, plugins: path.join(folder, 'plugins') });
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
}

const recorded = (plugins) => Object.keys(JSON.parse(fs.readFileSync(path.join(plugins, extras.RECORD), 'utf8')).files);

test('a jar in plugins-extra is copied in and recorded', () => scratch(({ folder, from, plugins }) => {
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'eco');
  const r = extras.install(folder, from);
  assert.deepStrictEqual(r.copied, ['Eco.jar']);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Eco.jar'), 'utf8'), 'eco');
  assert.deepStrictEqual(recorded(plugins), ['Eco.jar']);
}));

test('a jar gone from plugins-extra is taken out by the next run', () => scratch(({ folder, from, plugins }) => {
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'eco');
  extras.install(folder, from);
  fs.rmSync(path.join(from, 'Eco.jar'));
  const r = extras.install(folder, from);
  assert.deepStrictEqual(r.removed, ['Eco.jar']);
  assert.strictEqual(fs.existsSync(path.join(plugins, 'Eco.jar')), false);
}));

test('a jar no run copied is never taken out', () => scratch(({ folder, from, plugins }) => {
  fs.mkdirSync(plugins, { recursive: true });
  fs.writeFileSync(path.join(plugins, 'Mine.jar'), 'mine');
  extras.install(folder, from);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Mine.jar'), 'utf8'), 'mine');
}));

test('somebody\'s own jar of the same name is refused, not overwritten', () => scratch(({ folder, from, plugins }) => {
  fs.mkdirSync(plugins, { recursive: true });
  fs.writeFileSync(path.join(plugins, 'Eco.jar'), 'theirs');
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'ours');
  assert.throws(() => extras.install(folder, from), /is already there/);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Eco.jar'), 'utf8'), 'theirs');
}));

test('somebody\'s own jar of the same bytes is not adopted, so a later run never takes it out', () => scratch(({ folder, from, plugins }) => {
  fs.mkdirSync(plugins, { recursive: true });
  fs.writeFileSync(path.join(plugins, 'Eco.jar'), 'eco');
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'eco');
  extras.install(folder, from);
  assert.deepStrictEqual(recorded(plugins), []);
  fs.rmSync(path.join(from, 'Eco.jar'));
  extras.install(folder, from);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Eco.jar'), 'utf8'), 'eco');
}));

test('a jar of ours somebody has replaced since is neither overwritten nor taken out', () => scratch(({ folder, from, plugins }) => {
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'v1');
  extras.install(folder, from);
  fs.writeFileSync(path.join(plugins, 'Eco.jar'), 'theirs now');
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'v2');
  assert.throws(() => extras.install(folder, from), /replaced since/);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Eco.jar'), 'utf8'), 'theirs now');
  fs.rmSync(path.join(from, 'Eco.jar'));
  const r = extras.install(folder, from);
  assert.deepStrictEqual(r.kept, ['Eco.jar']);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Eco.jar'), 'utf8'), 'theirs now');
}));

test('a changed jar is copied over the one an earlier run put there', () => scratch(({ folder, from, plugins }) => {
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'v1');
  extras.install(folder, from);
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'v2');
  extras.install(folder, from);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Eco.jar'), 'utf8'), 'v2');
}));

test('a jar named as a --with companion is refused', () => scratch(({ folder, from, plugins }) => {
  fs.mkdirSync(plugins, { recursive: true });
  fs.writeFileSync(path.join(plugins, 'Vault.jar'), 'pinned');
  fs.writeFileSync(path.join(plugins, '.wx-companions.json'), JSON.stringify({ files: ['Vault.jar'] }));
  fs.writeFileSync(path.join(from, 'Vault.jar'), 'other');
  assert.throws(() => extras.install(folder, from), /--with companion/);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Vault.jar'), 'utf8'), 'pinned');
}));

test('a folder named like a jar in plugins-extra is refused in words', () => scratch(({ folder, from }) => {
  fs.mkdirSync(path.join(from, 'Odd.jar'));
  assert.throws(() => extras.install(folder, from), /is a folder, not a jar/);
}));

test('a link to nothing named like a jar is refused in words', (t) => scratch(({ folder, from, d }) => {
  try {
    fs.symlinkSync(path.join(from, 'gone.jar'), path.join(from, 'Link.jar'));
  } catch {
    t.skip('this system will not make a symbolic link here');
    return;
  }
  assert.throws(() => extras.install(folder, from), /is a link to nothing/);
}));

test('with no drop folder and nothing recorded, no record is written', () => scratch(({ folder, plugins }) => {
  extras.install(folder, null);
  assert.strictEqual(fs.existsSync(path.join(plugins, extras.RECORD)), false);
}));

test('a record naming a path outside plugins/ is not acted on', () => scratch(({ folder, from, plugins }) => {
  fs.mkdirSync(plugins, { recursive: true });
  fs.writeFileSync(path.join(folder, 'precious.jar'), 'keep');
  fs.writeFileSync(path.join(plugins, extras.RECORD), JSON.stringify({ files: { '../precious.jar': 'x' } }));
  extras.install(folder, from);
  assert.strictEqual(fs.existsSync(path.join(folder, 'precious.jar')), true);
}));
