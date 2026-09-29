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
//   empty     the plugin holds nothing the facility did not make: no gates, no mirrors
//   settings  every setting a chamber needed is back to what it was
//   faults    the plugin log has no fault in it (server.js KNOWN_BENIGN aside)

const campus = require('./lib/campus');
const { chamber } = require('./lib/campus');

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
      await probe.teleport({ x: home.x, y: 0, z: home.z - 8, yaw: 180 });
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
