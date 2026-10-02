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
//             followed by its reset, which must leave the cell as built; then a Run of a
//             staged chamber, which is refused
//   resets    every chamber's reset function, then its cell must be clear
//   console   a non-op Tester clicks through the menus, chooses an option, runs and resets
//   players   a non-op Tester is not hurt or hungry; a mob still is
//   logbook   Tester is given the Logbook on joining; its contents and Go links are intact in
//             what the client holds; a Go moves Tester; after a run its copy is replaced in the
//             same slot, not added to, with the run in it
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
const { MATRIX, defaultsOf, expectation, applies } = require('./matrix');

async function selftest(fac, {
  buildReport, fixtures = [], only = null, fixed = [], quick = false, log = console.log, shard = null, companions = null,
}) {
  const results = [];
  const known = [];
  // Seconds per matrix cell (its run and its reset) and per section, for sharding the next run.
  const times = {};
  const sectionMs = {};
  const check = (section, name, ok, detail) => {
    results.push({ section, name, ok: Boolean(ok), detail });
    log(`  ${ok ? 'ok  ' : 'FAIL'} ${section} · ${name}${detail ? ` — ${detail}` : ''}`);
  };
  const guard = async (section, fn) => {
    const t0 = Date.now();
    try { await fn(); } catch (e) { check(section, 'threw', false, e.stack || String(e)); }
    sectionMs[section] = (sectionMs[section] || 0) + (Date.now() - t0);
  };
  // With --shards, each shard (lib/shards.js) runs its share of the matrix. The sections that
  // check its own world and state (world, fixtures, resets, empty, settings, faults) run on every
  // shard; the ones about the campus as a whole (transit, plates, boards, console) on the first.
  const first = !shard || shard.index === 0;
  const inShard = (label) => !shard || shard.cells.has(label);
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

  if (first) await guard('transit', async () => {
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
    const held = await new (require('./lib/mirrors').MirrorKit)(srv).list();
    check('transit', 'mirror list shows Ops, Optics, Range and Annex', ['Annex', 'Ops', 'Optics', 'Range'].every((n) => held.names.includes(n)), held.text);
  });

  if (first) await guard('plates', async () => {
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

  if (first) await guard('boards', async () => {
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
      const defaults = defaultsOf(e.chamber);
      for (const cell of cells) {
        // Only the cells this run takes: a companion cell's settings may not exist in a jar tested
        // without its companion, and a cell --cells, --quick or the shard leaves out reads nothing.
        if (!applies(cell, companions)) continue;
        const label = cell.name || `${id} ${Object.entries(cell.values).map(([k, x]) => `${k}=${x}`).join(' ')}`;
        if (only && !only.test(label)) continue;
        if (quick && !cell.quick) continue;
        if (!inShard(label)) continue;
        // Every setting a cell needs is read before the first run that needs it, and checked at the end.
        const needs = e.chamber.needs ? Object.keys((e.chamber.needs({ ...defaults, ...cell.values }) || {}).config || {}) : [];
        for (const n of needs) { settings.add(n); if (!(n in before)) before[n] = await fac.config.get(n); }
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
        times[label] = Math.round((Date.now() - t0) / 100) / 10;
      }
    }
    // A Run of a staged chamber is refused, and the stage's setting stays until its Reset.
    const c0 = fac.entries.find((x) => x.def.id === 'c0');
    const cell = MATRIX.c0[0].values;
    const [name, value] = Object.entries(c0.chamber.needs(cell).config)[0];
    const staged = await fac.runChamber(c0, { values: cell, raw: true, mode: 'stage', holdMs: 0 });
    const run = await fac.runChamber(c0, { values: cell, raw: true, holdMs: 0 });
    const held = await fac.config.get(name);
    check('matrix', 'c0 Run while staged is refused, the stage kept', staged.outcome === 'STAGED' && run.outcome === 'REFUSED' && held === value,
      `${staged.outcome}, then ${run.outcome}${run.reason ? ` (${run.reason})` : ''}; ${name} ${held}`);
    const reset = await fac.resetChamber(c0);
    check('matrix', 'c0 reset after a stage leaves the cell as built', reset.ok, reset.problems.join('; ') || 'clean');
  });

  // A person's path through the console, played by Tester (never opped): every step clicks the
  // word the previous menu sent, by running the command in its click event, as a client does.
  if (first) await guard('console', async () => {
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
      const transitTab = await click(tabs, '⇄ Transit', (m) => clickOn(m, '[Routes]'), 'the Transit tab');
      check('console', 'the Transit tab offers dialling, beaming and the routes', /Dial Hall.*Beam to lab.*Routes/.test(transitTab.toString()), transitTab.toString().trim());
      const routes = await click(transitTab, '[Routes]', (m) => /gate Ops→Hall: /.test(m.toString()), 'the routes’ results');
      check('console', 'Routes prints each route’s last result', /gate Ops→Hall: (PASS|FAIL|not walked)/.test(routes.toString()), routes.toString().trim());
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

  // Players are not hurt and do not go hungry (Facility.shield and the damage gamerules), and a
  // mob still is: a non-op Tester dropped fourteen blocks and shot (the hit is seen by its own
  // client as a hurt, so it did land), and a pig dropped ten (fourteen would kill it), which must
  // come down hurt.
  if (first) await guard('players', async () => {
    const tester = await join({ port: fac.port, version: fac.version, username: 'Tester' });
    try {
      await waitEvent(tester, 'message', (m) => /Welcome to the Wormhole Research Facility/.test(m.toString()), 20000, 'the welcome');
      await new Promise((resolve) => { setTimeout(resolve, 1000); });
      const at = { x: -10.5, z: 10.5 };
      const mob = 'wx_players_mob';
      await srv.run(`kill @e[tag=${mob}]`);
      await srv.run(`summon minecraft:pig ${at.x + 4} 10 ${at.z} {PersistenceRequired:1b,Silent:1b,Tags:["${mob}"]}`);
      // The lowest health the client is told of, not just the last: Saturation heals a hurt
      // player back to full within a couple of seconds, which would hide a hit.
      let lowest = tester.health;
      const onHealth = () => { lowest = Math.min(lowest, tester.health); };
      tester.on('health', onHealth);
      await srv.run(`tp Tester ${at.x} 14 ${at.z}`);
      await new Promise((resolve) => { setTimeout(resolve, 3000); });
      let hurt = 0;
      const onHurt = (e) => { if (e === tester.entity) hurt++; };
      tester.on('entityHurt', onHurt);
      const t = tester.entity.position;
      await srv.run(`summon minecraft:arrow ${t.x.toFixed(2)} ${(t.y + 3).toFixed(2)} ${t.z.toFixed(2)} {Motion:[0.0,-1.5,0.0]}`);
      await new Promise((resolve) => { setTimeout(resolve, 2000); });
      tester.off('entityHurt', onHurt);
      tester.off('health', onHealth);
      const mobHealth = (await srv.run(`data get entity @e[tag=${mob},limit=1] Health`)).lines.join(' ');
      const hp = Number((/(-?[\d.]+)f?\s*$/.exec(mobHealth) || [])[1]);
      check('players', 'Tester (not an op) never dropped below full health through a fourteen-block fall and an arrow', lowest === 20, `lowest ${lowest}, now ${tester.health}`);
      check('players', 'and the arrow did hit it (its client saw the hit)', hurt > 0, `hits ${hurt}`);
      check('players', 'and is not hungry', tester.food === 20, `food ${tester.food}`);
      check('players', 'a pig dropped ten blocks still takes damage', Number.isFinite(hp) && hp < 10, mobHealth);
      await srv.run(`kill @e[tag=${mob}]`);
      // The shield alone keeps Tester whole, so the gamerules are read back: off in every world.
      const { gameruleName } = require('./lib/server');
      const on = [];
      for (const dim of [campus.OVERWORLD, campus.NETHER, campus.END]) {
        for (const rule of ['fallDamage', 'fireDamage', 'drowningDamage', 'freezeDamage']) {
          const r = await srv.run(`execute in ${dim} run gamerule ${gameruleName(fac.version, rule)}`);
          if (!r.lines.some((l) => /(set to|is currently)[^\n]*\bfalse\b/i.test(l))) on.push(`${rule} in ${dim.replace('minecraft:', '')}: ${r.lines.join(' ') || r.errors.join(' ')}`);
        }
      }
      check('players', 'the player damage gamerules are off in all three worlds', on.length === 0, on.join('; ') || '12 of 12 read back false');
    } finally {
      tester.quit();
    }
  });

  // The Logbook (lib/logbook.js), judged by what Tester's client holds: the book, its pages'
  // click events as they arrived, a Go that moves it (a non-op, by /trigger), and a copy
  // replaced after a run, in the same slot.
  if (first) await guard('logbook', async () => {
    const { clientPages, clicksIn, rowsOf, PAGE_LINES } = require('./lib/logbook');
    const blueprint = require('./lib/blueprint');
    const { Vec3 } = require('vec3');
    const tester = await join({ port: fac.port, version: fac.version, username: 'Tester' });
    const until = async (test, ms) => {
      const end = Date.now() + ms;
      for (;;) {
        if (test()) return true;
        if (Date.now() > end) return false;
        await new Promise((resolve) => { setTimeout(resolve, 100); });
      }
    };
    const books = () => tester.inventory.slots.map((it, i) => ({ it, i })).filter((x) => x.it && x.it.name === 'written_book');
    const pageOf = (pages, name) => {
      const entry = clicksIn(pages[0] || {}).find((x) => x.text === name);
      return entry ? { entry, page: pages[Number(entry.target) - 1] } : { entry: null, page: null };
    };
    try {
      await waitEvent(tester, 'message', (m) => /Welcome to the Wormhole Research Facility/.test(m.toString()), 20000, 'the welcome');
      await until(() => books().length > 0, 8000);
      const given = books();
      check('logbook', 'Tester is given the Logbook on joining', given.length === 1, `${given.length} written book(s)`);
      const pages = clientPages(given[0] && given[0].it);
      const yours = pageOf(pages, 'Your runs');
      check('logbook', 'a contents entry turns to its page: change_page as the client received it', yours.entry && yours.entry.action === 'change_page'
        && /YOUR RUNS/.test(text.plain(yours.page || {})), yours.entry ? `${yours.entry.action} ${yours.entry.target}: ${text.plain(yours.page || {}).slice(0, 60)}` : `contents: ${text.plain(pages[0] || {}).slice(0, 120)}`);
      check('logbook', 'Your runs lists the run Tester made from the console', /C0 (PASS|FAIL|KNOWN)/.test(text.plain(yours.page || {})), text.plain(yours.page || {}).replace(/\n/g, ' / ').slice(0, 160));
      // A page that runs past the book's lines is cut off on the client, Go links and all.
      const rows = pages.map((p) => rowsOf(text.plain(p)));
      const over = rows.map((r, i) => ({ r, i })).filter((x) => x.r > PAGE_LINES);
      check('logbook', `no page runs past ${PAGE_LINES} lines (wrapped as a book wraps them)`, pages.length > 2 && over.length === 0,
        over.length ? over.map((x) => `page ${x.i + 1}: ${x.r} lines`).join(', ') : `${pages.length} pages, the longest ${Math.max(...rows)} lines`);
      const wing = campus.wing('gates');
      const gates = pageOf(pages, wing.title);
      const go = clicksIn(gates.page || {}).find((x) => x.text === '[Go]');
      check('logbook', `${wing.title}'s Go is intact: run_command /trigger wx set <code>`, go && go.action === 'run_command' && /^\/trigger wx set \d+$/.test(go.target), go ? `${go.action} ${go.target}` : 'no Go on the page');
      if (go) {
        tester.chat(go.target);
        const e = wing.entrance;
        const moved = await until(() => tester.entity.position.distanceTo(new Vec3(e.x, e.y, e.z)) < 2, 8000);
        check('logbook', `clicked by Tester (not an op), the Go moves it to ${wing.title}`, moved, `at ${tester.entity.position}`);
      }
      // A chamber's own Go: the console's Watch code, to its gallery seat.
      const seatGo = clicksIn(gates.page || {}).map((x) => ({ ...x, code: Number((/^\/trigger wx set (\d+)$/.exec(x.target) || [])[1]) })).find((x) => x.code >= 10000);
      const target = seatGo ? fac.entries.find((x) => x.number === Math.floor(seatGo.code / 10000)) : null;
      if (target) {
        tester.chat(seatGo.target);
        const s = blueprint.layoutOf(target.def).seat;
        const seated = await until(() => tester.entity.position.distanceTo(new Vec3(s.x, s.y, s.z)) < 2, 8000);
        check('logbook', `a chamber's Go moves Tester to ${target.def.id.toUpperCase()}'s gallery seat`, seated, `at ${tester.entity.position}, the seat at ${s.x} ${s.y} ${s.z}`);
      } else {
        check('logbook', 'a chamber\'s Go moves Tester to its gallery seat', false, `no chamber Go on ${wing.title}'s page`);
      }
      // A run by the bot: the copy is replaced where it is, not added to, and now has the run:
      // one more on Bot runs' count, and first on its list.
      const slot = given[0] ? given[0].i : null;
      const latest = () => {
        const b = books();
        const p = b.length ? pageOf(clientPages(b[0].it), 'Bot runs').page : null;
        const lines = p ? text.plain(p).split('\n').filter(Boolean) : [];
        return { b, count: Number((/BOT RUNS · (\d+)/.exec(lines[0] || '') || [])[1]), first: lines[1] || '' };
      };
      const before = latest();
      const c0 = fac.entries.find((x) => x.def.id === 'c0');
      await fac.runChamber(c0, { values: MATRIX.c0[0].values, raw: true, holdMs: 0 });
      await fac.resetChamber(c0);
      await until(() => latest().count === before.count + 1, 8000);
      const after = latest();
      check('logbook', 'after a run its copy is replaced in the same slot, not added to, with the run first on Bot runs',
        after.b.length === 1 && after.b[0].i === slot && after.count === before.count + 1 && /C0 PASS/.test(after.first),
        `${after.b.length} book(s), slot ${after.b.length ? after.b[0].i : '-'} (was ${slot}); Bot runs ${before.count} → ${after.count}, first "${after.first}"`);
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
    // Only the session's fixtures: the far gates, the gallery, the transit gates and the Iris
    // Chamber's pair. A gate a run built and its reset left behind would show here.
    const gates = await srv.run('wormhole list');
    const listed = gates.lines.filter((l) => !/Available gates|No gates found/.test(l)).join(',')
      .replace(/§./g, '').split(',').map((x) => x.trim()).filter(Boolean).sort();
    const fixed = [...Object.keys(require('./chambers/relay').farGates()), ...require('./chambers/g2-gallery').gallery().map((g) => g.name),
      ...Object.keys(campus.ROUTES.gates), ...Object.values(require('./chambers/g5-iris').GATES).map((g) => g.name)].sort();
    check('empty', 'only the fixture gates', JSON.stringify(listed) === JSON.stringify(fixed), `listed ${listed.join(', ') || 'none'}; fixtures ${fixed.join(', ')}`);
    const listing = await transitListing(fac);
    const keep = [...fac.keepRings];
    check('empty', 'only the transit ring pair', JSON.stringify(listing.rings) === JSON.stringify(keep), `listed ${listing.ringText || 'none'}; kept ${keep.join(', ') || 'none'}`);
    const beams = campus.ROUTES.beams.map((b) => b.name).sort();
    const listedBeams = (/Beam destinations: ([^/]*?)(?: \/ |$)/.exec(listing.beams) || [null, ''])[1].split(', ').filter(Boolean).sort();
    check('empty', 'only the transit beam destinations', JSON.stringify(listedBeams) === JSON.stringify(beams), listing.beams);
    const { BeamKit } = require('./lib/beams');
    const bk = new BeamKit(srv);
    for (const who of [fac.probe, await fac.second()]) {
      const places = await bk.places(who);
      check('empty', `${who.name} has no beam places`, /You have no places set\./.test(places), places);
    }
    const { MirrorKit } = require('./lib/mirrors');
    const held = await new MirrorKit(srv).list();
    const expected = campus.ROUTES.mirrors.map((m) => m.name).sort();
    check('empty', 'only the transit mirrors', JSON.stringify(held.names) === JSON.stringify(expected), `listed ${held.names.join(', ') || 'none'}; transit ${expected.join(', ')}`);
  });

  await guard('settings', async () => {
    for (const n of settings) {
      const now = await fac.config.get(n);
      check('settings', `${n} restored`, now === before[n], `before ${before[n]}, after ${now}`);
    }
  });

  // (A known fault whose fix this jar carries, --fixed <issue>, is counted as a fault by FaultCounter.)
  await fac.faults.settled();
  check('faults', 'plugin log has no faults', fac.faults.count === 0,
    fac.faults.faults.slice(0, 3).join(' | ') || (fac.faults.known.length ? `0 faults; KNOWN PLUGIN FAULT: ${fac.faults.known[0].note} (${fac.faults.known.length}x)` : '0 faults'));
  for (const k of fac.faults.known) known.push({ label: `plugin log: ${k.line}`, note: k.note, version: fac.version });
  results.known = known;
  results.times = times;
  results.sectionMs = sectionMs;
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
