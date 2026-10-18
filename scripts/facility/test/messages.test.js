'use strict';
// lib/messages.js without a server: what a message is normalised to, and how a run is compared
// with a snapshot within the cells that ran. Run with `npm test --prefix scripts/facility`.

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const messages = require('../lib/messages');

test('only the plugin\'s lines are kept, without colour codes, quotes or line breaks', () => {
  assert.strictEqual(messages.normalise('Welcome to the Wormhole Research Facility.'), null);
  assert.strictEqual(messages.normalise('§a:: §fBuilt Hall'), ':: Built Hall');
  assert.strictEqual(messages.normalise('has the following block data: ":: Built CbGate"'), ':: Built CbGate');
  assert.strictEqual(messages.normalise(':: first\n  second'), ':: first second');
});

test('numbers, ring ids and capture state are replaced, ordinary words are not', () => {
  assert.strictEqual(messages.normalise(':: Built Stand at -12 5 7.5 in world.'), ':: Built Stand at N N N in world.');
  assert.strictEqual(messages.normalise(':: Ring pair 3f2a1b is live.'), ':: Ring pair ID is live.');
  assert.strictEqual(messages.normalise(':: Ring pair 543210 is live.'), ':: Ring pair ID is live.');
  assert.strictEqual(messages.normalise(':: Face2 Dead1 Pad-3'), ':: FaceN DeadN Pad-N');
  assert.strictEqual(messages.normalise(':: capture: 1234 bytes, in memory'), ':: capture: STATE');
  assert.strictEqual(messages.normalise(':: capture: file missing, not in memory'), ':: capture: STATE');
});

test('a run is compared only within the cells and sections it ran', () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-messages-'));
  const said = [];
  const run = (version, ran, lines, update) => report(version, { ran, lines }, out, update, said);
  try {
    // No snapshot yet: told so, and nothing is compared.
    run('0.0.test', ['a', 'b'], ['a\t:: one', 'b\t:: two'], false);
    assert.match(said.pop(), /no snapshot yet/);
    run('0.0.test', ['a', 'b'], ['a\t:: one', 'b\t:: two'], true);
    said.length = 0;
    // The same again, and a run of only `a`: unchanged, `b` is neither removed nor added.
    run('0.0.test', ['a', 'b'], ['a\t:: one', 'b\t:: two'], false);
    assert.match(said.pop(), /unchanged/);
    run('0.0.test', ['a'], ['a\t:: one'], false);
    assert.match(said.pop(), /unchanged/);
    // A reworded line in `a` is one removed and one added; `b` is still left alone.
    run('0.0.test', ['a'], ['a\t:: One'], false);
    assert.match(said[0], /CHANGED, 1 added, 1 removed/);
    assert.deepStrictEqual(said.slice(1), ['  - a | :: one', '  + a | :: One']);
  } finally {
    fs.rmSync(out, { recursive: true, force: true });
    fs.rmSync(path.join(__dirname, '..', 'messages', '0.0.test.snap'), { force: true });
  }
});

function report(version, run, out, update, said) {
  messages.report(version, run, { outFile: path.join(out, `${version}.txt`), update, log: (l) => said.push(l) });
}
