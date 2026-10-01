'use strict';
// lib/schematics.js without a server: where WorldEdit's own saved schematics land, the
// guardrail's inclusive edges on negative coordinates, and paste() against a fake console. Run
// with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const EventEmitter = require('events');
const mcData = require('minecraft-data');
const sch = require('../lib/schematics');
const guard = require('../wings/decor/guard');
const { allBlueprints } = require('../wings');

const FIXTURES = path.join(__dirname, 'fixtures');

// Saved in game: four wool marks copied from pos1 12,2,11 to pos2 10,0,10, so the console's
// //copy took its origin from pos1, the far corner. Then pasted plain at 30,5,30, turned 90 at
// 40,5,40 and turned 270 at 50,5,50; the .json is where each mark was found afterwards. Red is
// the near corner of the box and yellow the far one.
for (const [file, version, format] of [['we745-origin-at-max', '7.4.5', 3], ['we7220-origin-at-max', '7.2.20', 2]]) {
  test(`a schematic WorldEdit ${version} saved (format ${format}) lands where WorldEdit pasted it, plain and turned`, async () => {
    const info = await sch.readSchem(path.join(FIXTURES, `${file}.schem`));
    assert.strictEqual(info.version, format);
    assert.deepStrictEqual(info.min, [-2, -2, -1], 'the origin is the far corner, so the near one is behind it');
    const found = JSON.parse(fs.readFileSync(path.join(FIXTURES, `${file}.json`), 'utf8'));
    for (const [label, at, rot] of [['plain', { x: 30, y: 5, z: 30 }, 0], ['turned', { x: 40, y: 5, z: 40 }, 90], ['back', { x: 50, y: 5, z: 50 }, 270]]) {
      const marks = Object.values(found[label]);
      const pasted = {
        x0: Math.min(...marks.map((m) => m[0])), y0: Math.min(...marks.map((m) => m[1])), z0: Math.min(...marks.map((m) => m[2])),
        x1: Math.max(...marks.map((m) => m[0])), y1: Math.max(...marks.map((m) => m[1])), z1: Math.max(...marks.map((m) => m[2])),
      };
      assert.deepStrictEqual(sch.placedBox(info, at, rot), pasted, label);
    }
  });
}

/** A scratch folder with l.schem (3 x 1 x 2, origin at its near corner) and these placements; removed after `fn`. */
async function withPlacements(list, fn) {
  const d = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-schem-'));
  try {
    sch.writeSchem(path.join(d, 'l.schem'), {
      size: [3, 1, 2], dataVersion: mcData('1.20.4').version.dataVersion, blocks: [{ x: 0, y: 0, z: 0, block: 'minecraft:red_wool' }],
    });
    fs.writeFileSync(path.join(d, 'placements.json'), JSON.stringify(list));
    await fn(d);
  } finally {
    fs.rmSync(d, { recursive: true, force: true });
  }
}

test('a box touching a guarded volume by one block on its edge is refused, one block short is not (negative coordinates)', async () => {
  // The Atrium beam pad, west of the gate room: x -15..-11 on the floor.
  const pad = guard.forbidden(allBlueprints('1.21.11').builds).find((k) => k.what === 'the beam pad Atrium');
  assert.ok(pad, 'no Atrium pad in the guardrail');
  assert.ok(pad.box.x1 < 0 && pad.box.z0 < 0);
  const z = pad.box.z0;
  await withPlacements([
    { file: 'l.schem', at: { x: pad.box.x1, y: 0, z } }, // its first block is the pad's last
    { file: 'l.schem', at: { x: pad.box.x1 + 1, y: 0, z } },
    { file: 'l.schem', at: { x: pad.box.x0 - 2, y: 0, z } }, // west, three wide: its last block is the pad's first
    { file: 'l.schem', at: { x: pad.box.x0 - 3, y: 0, z } },
  ], async (d) => {
    const { placed, problems } = await sch.check(sch.placements(d), '1.21.11');
    const hits = (p) => problems.some((x) => x.startsWith(`l.schem at ${p.box.x0} ${p.box.y0} ${p.box.z0}..`) && x.includes('the beam pad Atrium'));
    assert.deepStrictEqual(placed.map(hits), [true, false, true, false]);
  });
});

/**
 * A console that answers each command from `script` (first matching pattern; its lines, and any
 * `later` lines logged a tick after) and keeps what was sent.
 */
function fakeServer(script) {
  const srv = new EventEmitter();
  srv.sent = [];
  srv.run = async (cmd) => {
    srv.sent.push(cmd);
    const hit = script.find(([re]) => re.test(cmd));
    const answer = hit ? hit[1](cmd) : { lines: [] };
    if (answer.later) setTimeout(() => { for (const l of answer.later) srv.emit('line', l); }, 5);
    return { lines: answer.lines || [], errors: [] };
  };
  return srv;
}

const place = (file, x) => ({ file, at: { x, y: 0, z: 0 }, rotation: 0, dim: 'minecraft:overworld', box: { x0: x, y0: 0, z0: 0, x1: x, y1: 0, z1: 0 } });
const OK = [
  [/^\/world world$/, () => ({ lines: ['Set the world override to world. (Use //world to go back to default)'] })],
  [/^\/\/world /, () => ({ lines: ['Unknown command. Type "/help" for help.'] })],
  [/^\/pos1 /, () => ({ lines: ['First position set to (0, 0, 0).'] })],
  [/^\/paste$/, () => ({ lines: ['The clipboard has been pasted at (0, 0, 0)'] })],
  [/^\/world$/, () => ({ lines: ['Removed world override.'] })],
];

test('paste falls back from //world to /world, keeps /, and puts the world override back', async () => {
  const srv = fakeServer([[/^\/schem load (\S+)/, (c) => ({ lines: [`${c.split(' ')[2]} loaded. Paste it with //paste`] })], ...OK]);
  const out = await sch.paste(srv, [place('a.schem', 1), place('b.schem', 2)]);
  assert.deepStrictEqual(out.map((r) => r.ok), [true, true], JSON.stringify(out));
  assert.deepStrictEqual(srv.sent.filter((c) => /world/.test(c)), ['//world world', '/world world', '/world world', '/world']);
  assert.ok(srv.sent.includes('/pos1 2,0,0') && !srv.sent.some((c) => c.startsWith('//pos1')));
  // The load was said with the command itself: the wait for it later is called off, not left to time out.
  assert.strictEqual(srv.listenerCount('line'), 0);
});

test('paste waits for a load said later, and leaves nothing listening once it has it', async () => {
  const srv = fakeServer([[/^\/schem load (\S+)/, (c) => ({ lines: [], later: [`[INFO]: ${c.split(' ')[2]} loaded. Paste it with //paste`] })], ...OK]);
  const out = await sch.paste(srv, [place('a.schem', 1)]);
  assert.ok(out[0].ok, out[0].detail);
  assert.strictEqual(srv.listenerCount('line'), 0);
});

test('paste does not take another file\'s "loaded" for its own, and says it had no answer', async () => {
  const srv = fakeServer([[/^\/schem load/, () => ({ lines: [], later: ['[INFO]: wx_other.schem loaded. Paste it with //paste'] })], ...OK]);
  const out = await sch.paste(srv, [place('a.schem', 1)], { loadMs: 200 });
  assert.strictEqual(out[0].ok, false);
  assert.match(out[0].detail, /no answer from WorldEdit to \/schem load wx_a\.schem/);
  assert.ok(!srv.sent.includes('/paste'), 'pasted the clipboard it did not load');
});

test('paste reports each placement that fails and carries on with the next', async () => {
  const srv = fakeServer([
    [/^\/schem load wx_missing/, () => ({ lines: [], later: ['Schematic wx_missing.schem does not exist!'] })],
    [/^\/schem load (\S+)/, (c) => ({ lines: [`${c.split(' ')[2]} loaded. Paste it with //paste`] })],
    ...OK,
  ]);
  const out = await sch.paste(srv, [place('missing.schem', 1), place('b.schem', 2)]);
  assert.strictEqual(out[0].ok, false);
  assert.match(out[0].detail, /does not exist/);
  assert.strictEqual(out[1].ok, true);
  assert.strictEqual(srv.sent[srv.sent.length - 1], '/world');
});

// WorldEdit 7.2.20 given a format-3 file: it finds the file, then says so without naming it.
for (const refusal of ['Unknown schematic format: sponge.3.', 'This schematic version is currently not supported. Version: 3.']) {
  test(`paste takes "${refusal.slice(0, 40)}..." as the pending load's answer, not a silence`, async () => {
    const srv = fakeServer([[/^\/schem load/, () => ({ lines: [], later: [`[09:00:00 INFO]: ${refusal}`] })], ...OK]);
    const t0 = Date.now();
    const out = await sch.paste(srv, [place('new.schem', 1)], { loadMs: 5000 });
    assert.strictEqual(out[0].ok, false);
    assert.ok(out[0].detail.includes(refusal), out[0].detail);
    assert.ok(Date.now() - t0 < 2000, 'it waited for the deadline instead');
    assert.ok(!srv.sent.includes('/paste'));
  });
}
