'use strict';
// The self-test: the facility proving itself on one server version. Each section is a list of
// named checks with a PASS/FAIL each; any FAIL fails the run.
//
//   world     the flat world's layers, every wing's sentinel and anchor blocks, and every cell
//             volume clear air, so later chambers start from a known state
//   plates    every tp plate sends Probe to the right place in the right world
//   boards    Probe is shown the Ops boards' text (judged by what the client receives)
//   matrix    each chamber's cells with an expected outcome (PASS, REFUSED:<reason>), each run
//             followed by its reset, which must leave the cell as built
//   resets    every chamber's reset function, then its cell must be clear
//   console   a non-op Tester clicks through the menus, chooses an option, runs and resets
//   empty     the plugin holds nothing the facility did not make: no gates, no mirrors
//   settings  every setting a chamber needed is back to what it was
//   faults    the plugin log has no fault in it (server.js KNOWN_BENIGN aside)

const campus = require('./lib/campus');
const { chamber } = require('./lib/campus');
const text = require('./lib/text');
const { join, waitEvent } = require('./lib/probe');

/**
 * The matrix: per chamber, the cells to run and what each must come to. A refusal is a
 * result like any other: `REFUSED:<the chamber's reason>`.
 */
const MATRIX = {
  c0: [
    { values: { target: 'gold_block', control: 'lever' }, expect: 'PASS' },
    { values: { target: 'gold_block', control: 'button' }, expect: 'PASS' },
    { values: { target: 'glass', control: 'lever' }, expect: 'REFUSED:a lever or button needs a solid block to hang on' },
    { values: { target: 'glass', control: 'button' }, expect: 'REFUSED:a lever or button needs a solid block to hang on' },
  ],
};

async function selftest(fac, { buildReport, log = console.log }) {
  const results = [];
  const check = (section, name, ok, detail) => {
    results.push({ section, name, ok: Boolean(ok), detail });
    log(`  ${ok ? 'ok  ' : 'FAIL'} ${section} · ${name}${detail ? ` — ${detail}` : ''}`);
  };
  const guard = async (section, fn) => {
    try { await fn(); } catch (e) { check(section, 'threw', false, e.stack || String(e)); }
  };
  const srv = fac.srv;

  await guard('world', async () => {
    // A spot of bare atrium floor (the centre has the transit ring's sea lantern).
    const floor = await fac.isBlock(campus.OVERWORLD, [-10, -1, 10], 'minecraft:smooth_quartz');
    const under = await fac.isBlock(campus.OVERWORLD, [-10, -2, 10], 'minecraft:stone');
    check('world', 'flat layers: quartz at y -1 over stone', floor && under, `quartz ${floor}, stone ${under}`);
    for (const b of buildReport) check('world', `${b.fn} built`, b.ok, `${b.detail}, ${b.ms} ms`);
    for (const f of fac.manifest.functions.filter((x) => x.fn.startsWith('build/'))) {
      const missing = [];
      for (const a of f.anchors) {
        if (!(await fac.isBlock(f.dim, a.at, a.block))) missing.push(`${a.what} (${a.at.join(' ')})`);
      }
      check('world', `${f.fn} anchors`, missing.length === 0, missing.length ? `missing: ${missing.join('; ')}` : `${f.anchors.length} in place`);
      for (const c of f.clear) {
        const r = await fac.isClear(f.dim, c.box);
        check('world', `${c.id} volume clear`, r.ok, `${r.air} of ${r.volume} air${r.found ? `; not air: ${r.found.join(', ')}` : ''}`);
      }
    }
  });

  await guard('plates', async () => {
    const probe = fac.probe;
    const home = campus.TRANSIT.home;
    const close = (dest, dim) => probe.dimension === dim && probe.distanceTo(dest) < 1.6;
    for (const p of campus.TRANSIT.plates) {
      const w = campus.wing(p.to);
      // From the ring's centre, the straight line to any plate crosses no other plate.
      const c = campus.TRANSIT.centre;
      await probe.teleport({ x: c.x + 0.5, y: 0, z: c.z + 0.5, yaw: 180 });
      let landed = null;
      try { landed = await probe.standOn({ x: campus.TRANSIT.centre.x + p.dx, y: 0, z: campus.TRANSIT.centre.z + p.dz }); } catch (e) { landed = { error: e.message }; }
      check('plates', `${p.dir} plate to ${w.title}`, !landed.error && close(w.entrance, w.dim),
        landed.error || `at ${probe.position.floored()} in ${probe.dimension}`);
      if (landed.error) continue;
      // And the plate home from there.
      const hp = p.to === 'systems' ? { x: 3, y: 6, z: -17 } : require('./wings').homePlate(w);
      let back = null;
      try { back = await probe.standOn(hp); } catch (e) { back = { error: e.message }; }
      check('plates', `home plate from ${w.title}`, !back.error && close(home, campus.OVERWORLD),
        back.error || `at ${probe.position.floored()} in ${probe.dimension}`);
    }
    await probe.teleport(home);
  });

  await guard('boards', async () => {
    await fac.probe.teleport({ x: 0.5, y: 0, z: -8.5, yaw: 180 });
    const near = (at, words) => fac.probe.displaysNear(at, 2).some((t) => t.includes(words));
    const wall = await waitShown(fac, () => near(campus.OPS.wall, 'OPS WALL') && near(campus.OPS.wall, 'Calibration Cell'));
    check('boards', 'Probe is shown the Ops wall', wall, fac.probe.displaysNear(campus.OPS.wall, 2).join(' / ') || 'nothing');
    const faults = await waitShown(fac, () => near(campus.OPS.faults, 'Plugin log: 0 faults'));
    check('boards', 'Probe is shown the fault counter at zero', faults, fac.probe.displaysNear(campus.OPS.faults, 2).join(' / ') || 'nothing');
  });

  const settings = new Set();
  const before = {};
  await guard('matrix', async () => {
    for (const [id, cells] of Object.entries(MATRIX)) {
      const e = fac.entries.find((x) => x.def.id === id);
      const needs = e.chamber.needs ? Object.keys((e.chamber.needs({}) || {}).config || {}) : [];
      for (const n of needs) { settings.add(n); if (!(n in before)) before[n] = await fac.config.get(n); }
      for (const cell of cells) {
        const label = `${id} ${Object.values(cell.values).join(' ')}`;
        const r = await fac.runChamber(e, { values: cell.values, raw: true, holdMs: 0 });
        const got = r.outcome === 'REFUSED' ? `REFUSED:${r.reason}` : r.outcome;
        check('matrix', `${label} → ${cell.expect}`, got === cell.expect,
          got === cell.expect ? (r.checks.length ? `${r.checks.length} checks true` : r.reason || '') : `got ${got}${r.reason && r.outcome !== 'REFUSED' ? ` (${r.reason})` : ''}`);
        if (r.outcome !== 'REFUSED') {
          const reset = await fac.resetChamber(e);
          check('matrix', `${label} reset leaves the cell as built`, reset.ok, reset.problems.join('; ') || 'clean');
        }
      }
    }
  });

  // A person's path through the console, played by Tester (never opped): every step clicks the
  // word the previous menu sent, by running the command in its click event, as a client does.
  await guard('console', async () => {
    // Kept from the moment the bot exists: the welcome can arrive before spawn resolves.
    const inbox = [];
    const tester = await join({ port: fac.port, version: fac.version, username: 'Tester', onCreate: (b) => b.on('message', (m) => inbox.push(m)) });
    try {
      const said = (test, ms, what) => {
        const early = inbox.splice(0).find((m) => test(m));
        return early ? Promise.resolve(early) : waitEvent(tester, 'message', (m) => test(m), ms, what).then(([m]) => m);
      };
      const clickOn = (msg, words) => text.clickCommands(msg.json).find((c) => String(c.text || '').includes(words));
      const click = async (msg, words, expect, what) => {
        const c = clickOn(msg, words);
        if (!c) throw new Error(`no "${words}" to click in "${msg.toString()}"`);
        const next = said(expect, 10000, what);
        tester.chat(c.command);
        return next;
      };
      const welcome = await said((m) => clickOn(m, '[Console]'), 20000, 'the welcome line');
      check('console', 'Tester is greeted with a Console link', /^Welcome to the Wormhole Research Facility\. \[Console\]/.test(welcome.toString()), welcome.toString());
      // The server's own default is survival (run-facility), so adventure here is the welcome's doing.
      check('console', 'Tester arrives in adventure mode', tester.game.gameMode === 'adventure', tester.game.gameMode);
      const tabs = await click(welcome, '[Console]', (m) => clickOn(m, 'Operations'), 'the wing tabs');
      const missing = campus.WINGS.filter((w) => !clickOn(tabs, w.title)).map((w) => w.title);
      check('console', 'Console shows a tab for every wing', missing.length === 0, missing.length ? `no tab for ${missing.join(', ')}: ${tabs.toString().trim()}` : tabs.toString().trim());
      const listing = await click(tabs, 'Operations', (m) => clickOn(m, 'Calibration Cell'), 'the Ops chambers');
      check('console', 'the Ops tab lists the calibration cell with its state', /C0 Calibration Cell\s+(idle · never run|pass|fail|refused|running|staged|idle)\b/.test(listing.toString()), listing.toString().trim());
      const opts = await click(listing, 'Calibration Cell', (m) => clickOn(m, 'button'), 'the calibration menu');
      check('console', 'the chamber menu offers its options, the first current', /control\s+\[lever\] button/.test(opts.toString()), opts.toString().trim());
      const chosen = await click(opts, 'button', (m) => /\[button\]/.test(m.toString()), 'the menu with button chosen');
      check('console', 'choosing an option marks it current, and only it', /control\s+lever \[button\]/.test(chosen.toString()), chosen.toString().trim());
      const ran = said((m) => /C0 Calibration Cell: (PASS|FAIL)/.test(m.toString()), 60000, 'the run result');
      tester.chat('!run c0');
      const result = await ran;
      check('console', 'typed !run c0 passes', /: PASS/.test(result.toString()), result.toString());
      const board = text.plain(await fac.boards.read('c0'));
      check('console', 'the board shows the option chosen from the menu', board.includes('control button'), board.split('\n').join(' / '));
      const reset = said((m) => /C0 reset:/.test(m.toString()), 30000, 'the reset result');
      tester.chat('!reset c0');
      const r = await reset;
      check('console', 'typed !reset c0 puts the cell back', /as built/.test(r.toString()), r.toString());
    } finally {
      tester.quit();
    }
  });

  await guard('resets', async () => {
    for (const f of fac.manifest.functions.filter((x) => x.fn.startsWith('reset/'))) {
      const e = fac.entries.find((x) => x.def.id === f.chamber) || { def: chamber(f.chamber), chamber: null };
      const r = await fac.resetChamber(e);
      check('resets', f.fn, r.ok, r.problems.join('; ') || 'sentinel set, cell clear');
    }
  });

  await guard('empty', async () => {
    const gates = await srv.run('wormhole list');
    check('empty', 'no gates', gates.lines.some((l) => /No gates found/.test(l)), gates.lines.join(' | '));
    const mirrors = await srv.run('wormhole mirror list');
    check('empty', 'no mirrors', mirrors.lines.some((l) => /No mirrors yet/.test(l)), mirrors.lines.join(' | '));
  });

  await guard('settings', async () => {
    for (const n of settings) {
      const now = await fac.config.get(n);
      check('settings', `${n} restored`, now === before[n], `before ${before[n]}, after ${now}`);
    }
  });

  check('faults', 'plugin log has no faults', fac.faults.count === 0, fac.faults.faults.slice(0, 3).join(' | ') || '0 faults');
  return results;
}

/** Waits (bounded) until a client-side condition holds; metadata can trail the spawn packet. */
async function waitShown(fac, test, ms = 5000) {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    if (test()) return true;
    await new Promise((resolve) => { fac.probe.bot.once('entityUpdate', resolve); setTimeout(resolve, 250); });
  }
  return test();
}

module.exports = { selftest, MATRIX };
