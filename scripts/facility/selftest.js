'use strict';
// The self-test: the facility proving itself on one server version. Each section is a list of
// named checks with a PASS/FAIL each; any FAIL fails the run.
//
//   world     the flat world's layers, every wing's sentinel and anchor blocks, and every cell
//             volume clear air, so later chambers start from a known state
//   transit   Probe2 (never opped) walks each transit route: the gate from the Ops console and
//             back by the DHD and /dial, the ring pair both ways, the beam by its button and back
//             typed; the routes' fixtures are listed where the plugin lists them
//   plates    every tp plate sends Probe to the right place in the right world
//   boards    Probe is shown the Ops boards' text (judged by what the client receives)
//   matrix    each chamber's cells with an expected outcome (PASS, REFUSED:<reason>), each run
//             followed by its reset, which must leave the cell as built
//   resets    every chamber's reset function, then its cell must be clear
//   console   a non-op Tester clicks through the menus, chooses an option, runs and resets
//   empty     the plugin holds nothing the facility did not make: its fixture gates, the transit
//             ring pair and beam destinations, no mirrors
//   settings  every setting a chamber needed is back to what it was
//   faults    the plugin log has no fault in it (server.js KNOWN_BENIGN aside)

const campus = require('./lib/campus');
const { chamber } = require('./lib/campus');
const text = require('./lib/text');
const { join, waitEvent } = require('./lib/probe');
const { RingKit } = require('./lib/rings');

/**
 * The matrix: per chamber, the cells to run and what each must come to. A refusal is a
 * result like any other: `REFUSED:<the chamber's reason>`.
 */
const { MATRIX, defaultsOf, expectation } = require('./matrix');

async function selftest(fac, {
  buildReport, fixtures = [], only = null, fixed = [], quick = false, log = console.log,
}) {
  const results = [];
  const known = [];
  const check = (section, name, ok, detail) => {
    results.push({ section, name, ok: Boolean(ok), detail });
    log(`  ${ok ? 'ok  ' : 'FAIL'} ${section} · ${name}${detail ? ` — ${detail}` : ''}`);
  };
  const guard = async (section, fn) => {
    try { await fn(); } catch (e) { check(section, 'threw', false, e.stack || String(e)); }
  };
  const srv = fac.srv;

  await guard('world', async () => {
    // A spot of bare atrium floor, clear of every inlay (runway, ring rim, beam pad).
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
        // A cell that holds a session fixture (the Relay, the gallery) is judged by its fixture.
        if (fac.holdsFixture(c.id)) continue;
        const r = await fac.isClear(f.dim, c.box);
        check('world', `${c.id} volume clear`, r.ok, `${r.air} of ${r.volume} air${r.found ? `; not air: ${r.found.join(', ')}` : ''}`);
      }
    }
  });

  await guard('fixtures', async () => {
    for (const f of fixtures) check('fixtures', f.id, f.ok, f.detail);
  });

  await guard('transit', async () => {
    const fixture = fixtures.find((f) => f.id === 'transit');
    if (!fixture || !fixture.ok) {
      check('transit', 'the routes were made', false, fixture ? fixture.detail : 'no transit fixture');
      return;
    }
    for (const r of await fac.transit.walk({ quick })) check('transit', r.id, r.ok, r.detail);
    const listing = await transitListing(fac);
    check('transit', 'gate list shows Ops and Hall', listing.gates.includes('Ops') && listing.gates.includes('Hall'), listing.gates.join(', '));
    check('transit', 'ring list shows the transit pair', listing.rings.includes(fac.transit.ringId), `${fac.transit.ringId} in: ${listing.ringText}`);
    check('transit', 'beam list shows Atrium and BeamLab', /Atrium/.test(listing.beams) && /BeamLab/.test(listing.beams), listing.beams);
  });

  await guard('plates', async () => {
    const probe = fac.probe;
    const home = campus.TRANSIT.home;
    const close = (dest, dim) => probe.dimension === dim && probe.distanceTo(dest) < 1.6;
    const c = campus.TRANSIT.centre;
    for (const p of campus.TRANSIT.plates) {
      const w = campus.wing(p.to);
      // From just south of the service corridor's row, north onto the plate: no other plate is
      // on that line.
      const at = { x: c.x + p.dx, y: 0, z: c.z + p.dz };
      await probe.teleport({ x: at.x + 0.5, y: 0, z: campus.TRANSIT.approachZ, yaw: 180 });
      let landed = null;
      try { landed = await probe.standOn(at); } catch (e) { landed = { error: e.message }; }
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
      const defaults = defaultsOf(e.chamber);
      for (const cell of cells) {
        const label = cell.name || `${id} ${Object.entries(cell.values).map(([k, x]) => `${k}=${x}`).join(' ')}`;
        if (only && !only.test(label)) continue;
        if (quick && !cell.quick) continue;
        const want = expectation(cell, fac.version, fixed);
        const t0 = Date.now();
        const r = await fac.runChamber(e, { values: { ...defaults, ...cell.values }, raw: true, holdMs: 0 });
        const got = r.outcome === 'FAIL' ? `FAIL:${r.reason}` : r.outcome === 'REFUSED' ? `REFUSED:${r.reason}` : r.outcome;
        const ok = got === want;
        // A known failure whose fix this jar carries (--fixed) is expected to pass, and is not one.
        const note = cell.regressedBy && fixed.includes(cell.regressedBy) ? cell.regressionNote : cell.known;
        const knownNow = Boolean(note) && want !== 'PASS';
        const secs = ((Date.now() - t0) / 1000).toFixed(1);
        const detail = ok
          ? `${knownNow ? `KNOWN PLUGIN FAILURE: ${note}; ` : ''}${cell.fixedBy && !knownNow ? `fixed by #${cell.fixedBy} in this jar; ` : ''}${r.checks.length ? `${r.checks.filter((x) => x.ok).length}/${r.checks.length} checks true` : r.reason || ''}, ${secs} s`
          : `got ${got}${knownNow ? ` (expected the known plugin failure: ${note})` : ''}, ${secs} s`;
        check('matrix', `${label} → ${want}`, ok, detail);
        if (ok && knownNow) known.push({ label, note, version: fac.version });
        if (r.outcome !== 'REFUSED') {
          const reset = await fac.resetChamber(e);
          check('matrix', `${label}: reset leaves the cell as built`, reset.ok, reset.problems.join('; ') || 'clean');
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
      check('console', 'Tester is greeted with a Console link', true, welcome.toString());
      check('console', 'Tester arrives in adventure mode', tester.game.gameMode === 'adventure', tester.game.gameMode);
      const tabs = await click(welcome, '[Console]', (m) => clickOn(m, 'Operations'), 'the wing tabs');
      check('console', 'Console shows the wing tabs', true, tabs.toString().trim());
      const listing = await click(tabs, 'Operations', (m) => clickOn(m, 'Calibration Cell'), 'the Ops chambers');
      check('console', 'the Ops tab lists the calibration cell', true, listing.toString().trim());
      const opts = await click(listing, 'Calibration Cell', (m) => clickOn(m, 'button'), 'the calibration menu');
      check('console', 'the chamber menu offers its options', true, opts.toString().trim());
      const chosen = await click(opts, 'button', (m) => /\[button\]/.test(m.toString()), 'the menu with button chosen');
      check('console', 'choosing an option marks it current', true, chosen.toString().trim());
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
    // Only the session's fixtures: the far gates and the gallery. A gate a run built and its
    // reset left behind would show here.
    const gates = await srv.run('wormhole list');
    const listed = gates.lines.filter((l) => !/Available gates|No gates found/.test(l)).join(',')
      .replace(/§./g, '').split(',').map((x) => x.trim()).filter(Boolean).sort();
    const fixed = [...Object.keys(require('./chambers/relay').farGates()), ...require('./chambers/g2-gallery').gallery().map((g) => g.name),
      ...Object.keys(campus.ROUTES.gates)].sort();
    check('empty', 'only the fixture gates', JSON.stringify(listed) === JSON.stringify(fixed), `listed ${listed.join(', ') || 'none'}; fixtures ${fixed.join(', ')}`);
    const listing = await transitListing(fac);
    const keep = [...fac.keepRings];
    check('empty', 'only the transit ring pair', JSON.stringify(listing.rings) === JSON.stringify(keep), `listed ${listing.ringText || 'none'}; kept ${keep.join(', ') || 'none'}`);
    const beams = campus.ROUTES.beams.map((b) => b.name).sort();
    const listedBeams = (/Beam destinations: (.*)$/.exec(listing.beams) || [null, ''])[1].split(', ').filter(Boolean).sort();
    check('empty', 'only the transit beam destinations', JSON.stringify(listedBeams) === JSON.stringify(beams), listing.beams);
    const { BeamKit } = require('./lib/beams');
    const bk = new BeamKit(srv);
    for (const who of [fac.probe, await fac.second()]) {
      const places = await bk.places(who);
      check('empty', `${who.name} has no beam places`, /You have no places set\./.test(places), places);
    }
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
  results.known = known;
  return results;
}

/** What the plugin lists: gates (console), ring pairs and public beam destinations (as Probe: player-only). */
async function transitListing(fac) {
  const g = await fac.srv.run('wormhole list');
  const gates = g.lines.filter((l) => !/Available gates|No gates found/.test(l)).join(',')
    .replace(/§./g, '').split(',').map((x) => x.trim()).filter(Boolean);
  const kit = new RingKit(fac.srv, fac.probe);
  const rings = await kit.list();
  const beams = await kit.ask('/wormhole beam list', { until: /Beam destinations|No public beam/ });
  return { gates, rings: rings.ids.sort(), ringText: rings.text, beams };
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
