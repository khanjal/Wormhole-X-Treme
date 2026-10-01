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

test('a jar in plugins-extra is copied in and recorded', () => scratch(({ folder, from, plugins }) => {
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'eco');
  const r = extras.install(folder, from);
  assert.deepStrictEqual(r.copied, ['Eco.jar']);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Eco.jar'), 'utf8'), 'eco');
  assert.deepStrictEqual(JSON.parse(fs.readFileSync(path.join(plugins, extras.RECORD), 'utf8')).files, ['Eco.jar']);
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

test('a changed jar is copied over the one an earlier run put there', () => scratch(({ folder, from, plugins }) => {
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'v1');
  extras.install(folder, from);
  fs.writeFileSync(path.join(from, 'Eco.jar'), 'v2');
  extras.install(folder, from);
  assert.strictEqual(fs.readFileSync(path.join(plugins, 'Eco.jar'), 'utf8'), 'v2');
}));

test('a record naming a path outside plugins/ is not acted on', () => scratch(({ folder, from, plugins }) => {
  fs.mkdirSync(plugins, { recursive: true });
  fs.writeFileSync(path.join(folder, 'precious.jar'), 'keep');
  fs.writeFileSync(path.join(plugins, extras.RECORD), JSON.stringify({ files: ['../precious.jar'] }));
  extras.install(folder, from);
  assert.strictEqual(fs.existsSync(path.join(folder, 'precious.jar')), true);
}));
