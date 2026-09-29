'use strict';
// Stage 0 of the Wormhole Research Facility: proves on one server version that the four
// vanilla mechanisms the facility rests on work there, and says exactly what failed if not.
//
//   node scripts/facility/spike.js <version> --java <path to java> [--port 25590] [--hold]
//
// --hold keeps the server up after the checks so a person can join and click the menu: the one
// thing a bot cannot do. Say "stop" in chat, or press Ctrl+C, to end it.
//
// Mechanisms: a datapack function that builds a box; a /trigger console whose menu reaches a
// non-op player and whose code reaches the bot; a text display that reads back as written;
// a bossbar created, updated and removed. Exits 1 on any FAIL.

const fs = require('fs');
const path = require('path');
const { Vec3 } = require('vec3');
const server = require('./lib/server');
const text = require('./lib/text');
const datapack = require('./lib/datapack');
const wxConsole = require('./lib/console');
const { atLeast } = require('./lib/version');
const { join, maybeEvent, shownText } = require('./lib/probe');

const REPO = path.resolve(__dirname, '..', '..');
const LOCAL = path.join(REPO, '.local-server');

function parseArgs(argv) {
  const args = { port: 25590 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--java') args.java = argv[++i];
    else if (a === '--port') args.port = Number(argv[++i]);
    else if (a === '--hold') args.hold = true;
    else if (!a.startsWith('--') && !args.version) args.version = a;
    else throw new Error(`unknown argument ${a}`);
  }
  if (!args.version) throw new Error('usage: node scripts/facility/spike.js <version> --java <path> [--port N]');
  args.java = args.java || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java');
  return args;
}

// ---- results --------------------------------------------------------------------------------

const results = [];
const notes = [];

function check(mechanism, name, ok, detail) {
  results.push({ mechanism, name, ok: Boolean(ok), detail });
  console.log(`  ${ok ? 'ok  ' : 'FAIL'} ${mechanism} · ${name}${detail ? ` — ${detail}` : ''}`);
}

function note(mechanism, detail) {
  notes.push({ mechanism, detail });
  console.log(`  info ${mechanism} · ${detail}`);
}

// Resolves with the first matching event's arguments, or null at the deadline.
const nextEvent = maybeEvent;

const { clickCommands } = text;

function joinBot(port, version, username) {
  return join({ port, version, username });
}

// ---- geometry --------------------------------------------------------------------------------

// A flat world's surface is y = -60 from 1.18 on; everything sits a few blocks from the bots.
const STAND = { x: 0.5, y: -60, z: 0.5 };
const BOX = { from: [4, -60, 4], to: [10, -55, 10], block: 'minecraft:white_concrete' };
const SENTINEL = { at: [7, -54, 7], block: 'minecraft:lodestone' };
const BOARD = { x: 2, y: -58, z: -3 };

// ---- mechanisms -----------------------------------------------------------------------------

async function checkDatapack(srv, version, folder, probe) {
  const M = 'datapack';
  const info = server.vanillaVersionInfo(folder, version);
  if (info) {
    const pv = info.pack_version;
    const actual = pv.data !== undefined ? pv.data : [pv.data_major, pv.data_minor];
    const table = datapack.packFormat(version);
    check(M, 'format table matches the server', JSON.stringify(actual) === JSON.stringify(table),
      `table ${JSON.stringify(table)}, vanilla jar ${JSON.stringify(actual)}`);
  } else note(M, 'no cache/mojang jar to cross-check the format table against');

  // No log check for the pack itself: a pack whose format is out of range loads silently on
  // 1.21.11 (tried with pack_format 15 alone), so the table cross-check above is the guard.
  const list = await srv.run('datapack list enabled');
  check(M, 'wx is enabled', list.lines.some((l) => l.includes('[file/wx')), list.lines.join(' '));

  const sentinelTest = `execute if block ${SENTINEL.at.join(' ')} ${SENTINEL.block}`;
  const before = await srv.run(sentinelTest);
  check(M, 'sentinel absent before the build', before.lines.some((l) => /Test failed/.test(l)), before.lines.join(' '));

  const sentinelSeen = nextEvent(probe, 'blockUpdate', (_old, b) => b && b.position.equals(new Vec3(...SENTINEL.at)) && b.name === 'lodestone', 10000);
  const built = await srv.run('function wx:build/box');
  check(M, 'function ran', built.errors.length === 0 && built.lines.some((l) => /function 'wx:build\/box'|wx:build\/box/.test(l)),
    built.lines.join(' '));
  const after = await srv.run(sentinelTest);
  check(M, 'sentinel set last', after.lines.some((l) => /Test passed/.test(l)), after.lines.join(' '));
  const wall = await srv.run(`execute if block ${BOX.from.join(' ')} ${BOX.block}`);
  const hollow = await srv.run('execute if block 7 -57 7 minecraft:air');
  check(M, 'box is a hollow shell', wall.lines.some((l) => /Test passed/.test(l)) && hollow.lines.some((l) => /Test passed/.test(l)),
    `corner: ${wall.lines.join(' ')}; centre: ${hollow.lines.join(' ')}`);
  const seen = await sentinelSeen;
  check(M, 'Probe was sent the sentinel block', seen !== null, seen ? `${seen[1].name} at ${seen[1].position}` : 'no block update within 10 s');
}

async function checkConsole(srv, version, probe, tester) {
  const M = 'console';
  const events = wxConsole.listen(probe);
  let libraryEvents = 0;
  probe.on('scoreUpdated', (sb) => { if (sb.name === wxConsole.OBJECTIVE) libraryEvents++; });

  // Control: the objective exists and the player is enabled, but it is in no display slot.
  const [add, , enable] = wxConsole.setupCommands();
  for (const c of [add, enable]) {
    const r = await srv.run(c);
    if (r.errors.length) check(M, `setup: ${c}`, false, r.errors.join(' '));
  }
  const unseen = nextEvent(events, 'code', () => true, 4000);
  tester.chat(wxConsole.triggerCommand(1101));
  const got = await unseen;
  const held = await srv.run(`scoreboard players get ${tester.username} ${wxConsole.OBJECTIVE}`);
  note(M, `without a display slot the server set the score (${held.lines.join(' ')}) and Probe ${got ? 'DID' : 'did not'} receive it`);
  for (const c of wxConsole.rearmCommands(tester.username)) await srv.run(c);

  // The real setup: a display slot nobody sees makes the server send the scores.
  const shown = await srv.run(wxConsole.setupCommands()[1]);
  check(M, 'objective tracked through a hidden display slot', shown.errors.length === 0, shown.lines.join(' '));

  // The menu: accepted by the server, and delivered to a non-op player with its click intact.
  const code = wxConsole.encode({ chamber: 1, option: 2, value: 3 });
  const received = nextEvent(tester, 'message', (msg) => clickCommands(msg.json).length > 0, 10000);
  const menu = wxConsole.menu(version, 'G1 shape', [
    { label: 'Standard', code: 1200, current: true, why: 'the default 5x5 ring' },
    { label: 'Grand', code, why: 'a 7x7 ring · tests large frames' },
  ]);
  const sent = await srv.run(menu);
  check(M, 'tellraw accepted', sent.errors.length === 0, sent.errors.join(' | ') || 'no error in the log');
  const msg = await received;
  const clicks = msg ? clickCommands(msg[0].json) : [];
  const expectedKey = atLeast(version, text.SNAKE_CASE_EVENTS) ? 'click_event' : 'clickEvent';
  const match = clicks.find((c) => c.command === wxConsole.triggerCommand(code));
  check(M, 'menu reached Tester with its click commands', match && match.key === expectedKey,
    msg ? `${clicks.map((c) => `${c.key}=${c.command}`).join(', ')}; text "${msg[0].toString()}"` : 'no message within 10 s');

  // Control: the other side of 1.21.5's key names, to show the switch is load-bearing.
  const other = atLeast(version, text.SNAKE_CASE_EVENTS) ? '1.21.4' : '1.21.5';
  const otherMsg = nextEvent(tester, 'message', (m) => m.toString().includes('control'), 5000);
  const otherSent = await srv.run(`tellraw @a ${text.command(other, { text: 'control', click: { run: wxConsole.triggerCommand(9999) } })}`);
  const om = await otherMsg;
  note(M, `the ${other} click keys on ${version}: ${otherSent.errors.join(' ') || 'accepted'}; `
    + `click commands delivered: ${om ? JSON.stringify(clickCommands(om[0].json)) : 'no message'}`);

  // What the click runs, run by Tester (a bot cannot click chat), read by Probe.
  const first = nextEvent(events, 'code', (e) => e.player === tester.username && e.code === code, 10000);
  tester.chat(match ? match.command : wxConsole.triggerCommand(code));
  const a = await first;
  const decoded = wxConsole.decode(code);
  check(M, 'Probe read who set which code', a !== null && decoded.chamber === 1 && decoded.option === 2 && decoded.value === 3,
    a ? `${a[0].player} set ${a[0].code} = chamber ${decoded.chamber} option ${decoded.option} value ${decoded.value}` : 'no score packet within 10 s');
  note(M, `Tester was never opped; Mineflayer's own scoreUpdated fired ${libraryEvents} time(s)`);

  // Rearm, then a second code must arrive too.
  const rearm = [];
  for (const c of wxConsole.rearmCommands(tester.username)) rearm.push(...(await srv.run(c)).errors);
  const secondCode = 2105;
  const second = nextEvent(events, 'code', (e) => e.player === tester.username && e.code === secondCode, 10000);
  tester.chat(wxConsole.triggerCommand(secondCode));
  const b = await second;
  check(M, 'reset and re-enable let the player trigger again', rearm.length === 0 && b !== null,
    rearm.join(' ') || (b ? `${b[0].player} set ${b[0].code}` : 'no second score packet within 10 s'));

  // Without a re-enable the trigger is refused, so the re-enable above is what made it work.
  const refused = nextEvent(tester, 'message', (m) => /cannot trigger|not enabled|can't trigger|only trigger/i.test(m.toString()), 5000);
  tester.chat(wxConsole.triggerCommand(3101));
  const r = await refused;
  const still = await srv.run(`scoreboard players get ${tester.username} ${wxConsole.OBJECTIVE}`);
  check(M, 'a trigger without re-enable is refused', still.lines.some((l) => l.includes(String(secondCode))),
    `${r ? `Tester was told "${r[0].toString()}"; ` : ''}${still.lines.join(' ')}`);

  // The typed form.
  const typed = nextEvent(events, 'typed', (e) => e.player === tester.username, 10000);
  tester.chat('!g1 shape Grand');
  const t = await typed;
  check(M, 'typed ! line reached Probe', t !== null && t[0].line === 'g1 shape Grand',
    t ? `${t[0].player}: ${t[0].line}` : 'no chat within 10 s');
}

async function checkDisplay(srv, version, probe) {
  const M = 'display';
  const spec = [{ text: 'WX spike board', color: 'gold', bold: true }, { text: ' · idle', color: 'aqua' }];
  const selector = '@e[type=minecraft:text_display,tag=wx_spike,limit=1]';
  const readBack = async () => {
    const r = await srv.run(`data get entity ${selector} text`);
    const line = r.lines.find((l) => l.includes('has the following entity data: '));
    if (!line) return { error: r.lines.join(' ') };
    const printed = line.slice(line.indexOf('has the following entity data: ') + 31);
    try { return { printed, component: text.readDisplayText(version, printed) }; } catch (e) { return { printed, error: e.message }; }
  };
  const expect = (s) => text.toComponent(version, s);
  const agree = (got, s) => got.component && text.plain(got.component) === text.plain(expect(s))
    && text.sameComponent(got.component, expect(s));

  const spawned = nextEvent(probe, 'entitySpawn', (e) => e.name === 'text_display', 10000);
  const summon = await srv.run(text.summonDisplay(version, { ...BOARD, tags: ['wx_spike'], spec, nbt: { billboard: '"center"' } }));
  check(M, 'summoned', summon.errors.length === 0 && summon.lines.some((l) => /Summoned/.test(l)), summon.lines.join(' '));
  const got = await readBack();
  check(M, 'text reads back as written', agree(got, spec), `read ${got.printed || got.error}`);

  // What a player would see: the plain text of the component the client was sent. This does
  // not go through text.js, so a wrong format (JSON left as a literal string) shows up here.
  const want = text.plain(expect(spec));
  const seen = await spawned;
  const board = seen && seen[0];
  if (board && !shownText(board).includes(want)) {
    await nextEvent(probe, 'entityUpdate', (e) => e === board && shownText(board).includes(want), 5000);
  }
  check(M, 'Probe was shown the text', board && shownText(board).includes(want),
    board ? `shown ${JSON.stringify(shownText(board))}` : 'no text_display spawned within 10 s');

  const spec2 = [{ text: 'WX spike board', color: 'green', bold: true }, { text: ' · PASS 14:02', color: 'white' }];
  const merged = await srv.run(`data merge entity ${selector} {text:${text.displayNbt(version, spec2)}}`);
  const want2 = text.plain(expect(spec2));
  const shown2 = board && (shownText(board).includes(want2)
    || await nextEvent(probe, 'entityUpdate', (e) => e === board && shownText(board).includes(want2), 5000));
  const got2 = await readBack();
  check(M, 'data merge rewrites it', merged.errors.length === 0 && agree(got2, spec2) && Boolean(shown2),
    `read ${got2.printed || got2.error}; shown ${board ? JSON.stringify(shownText(board)) : 'nothing'}`);

  // Control: the other side of 1.21.5's format, to show the switch is load-bearing.
  const other = atLeast(version, text.SNAKE_CASE_EVENTS) ? '1.21.4' : '1.21.5';
  const wrong = await srv.run(text.summonDisplay(other, { x: BOARD.x + 2, y: BOARD.y, z: BOARD.z, tags: ['wx_spike_other'], spec }));
  const r = await srv.run('data get entity @e[type=minecraft:text_display,tag=wx_spike_other,limit=1] text');
  note(M, `the ${other} form on ${version}: ${[...wrong.lines, ...r.lines].join(' ')}`);
  await srv.run('kill @e[type=minecraft:text_display,tag=wx_spike_other]');
}

async function checkBossbar(srv, version, probe) {
  const M = 'bossbar';
  const id = 'wx:spike';
  const title1 = [{ text: 'G1 · 1/7 · ', color: 'yellow' }, { text: 'staging', color: 'white' }];
  const title2 = [{ text: 'G1 · 4/7 · ', color: 'yellow' }, { text: 'walking in', color: 'white' }];

  const created = nextEvent(probe, 'bossBarCreated', () => true, 10000);
  const cmds = [
    `bossbar add ${id} ${text.command(version, title1)}`,
    `bossbar set ${id} color yellow`,
    `bossbar set ${id} max 7`,
    `bossbar set ${id} value 1`,
    `bossbar set ${id} players @a`,
  ];
  const errs = [];
  for (const c of cmds) errs.push(...(await srv.run(c)).errors);
  check(M, 'created', errs.length === 0, errs.join(' | ') || 'no error in the log');
  const c = await created;
  const plain1 = text.plain(text.toComponent(version, title1));
  check(M, 'Probe sees the bar and its name', c && c[0].title.toString() === plain1,
    c ? `"${c[0].title.toString()}" ${c[0].color}, ${c[0].health.toFixed(3)}` : 'no bossbar within 10 s');

  const plain2 = text.plain(text.toComponent(version, title2));
  const updated = nextEvent(probe, 'bossBarUpdated', (bar) => bar.title.toString() === plain2 && bar.color === 'green'
    && Math.abs(bar.health - 4 / 7) < 0.01, 10000);
  for (const cmd of [`bossbar set ${id} name ${text.command(version, title2)}`, `bossbar set ${id} value 4`, `bossbar set ${id} color green`]) {
    errs.push(...(await srv.run(cmd)).errors);
  }
  const u = await updated;
  check(M, 'renamed, advanced and recoloured', errs.length === 0 && u !== null,
    errs.join(' | ') || (u ? `"${u[0].title.toString()}" ${u[0].color}, ${u[0].health.toFixed(3)}` : 'no matching update within 10 s'));

  const deleted = nextEvent(probe, 'bossBarDeleted', () => true, 10000);
  const removed = await srv.run(`bossbar remove ${id}`);
  const d = await deleted;
  check(M, 'removed', removed.errors.length === 0 && d !== null, removed.lines.join(' ') + (d ? '' : '; Probe saw no removal'));
}

/** Keeps the server up for a person to click the menu; prints every code Probe reads. */
async function hold(srv, version, probe, port) {
  const events = wxConsole.listen(probe);
  const choices = [
    { label: 'Standard', code: 1200, current: true, why: 'the default 5x5 ring' },
    { label: 'Grand', code: 1203, why: 'a 7x7 ring' },
    { label: 'Massive', code: 1204, why: 'a 9x9 ring' },
  ];
  const sendMenu = async (player) => {
    await srv.run(`scoreboard players enable ${player} ${wxConsole.OBJECTIVE}`);
    await srv.run(wxConsole.menu(version, 'G1 shape', choices).replace('tellraw @a', `tellraw ${player}`));
  };
  events.on('code', async ({ player, code }) => {
    if (player === 'Tester' || player === 'Probe' || !code) return;
    const d = wxConsole.decode(code);
    console.log(`  CLICK  ${player} set ${code} (chamber ${d.chamber} option ${d.option} value ${d.value})`);
    for (const c of wxConsole.rearmCommands(player)) await srv.run(c);
    await srv.run(`tellraw ${player} ${text.command(version, [{ text: `Probe read ${code}. `, color: 'green' }, { text: 'Click another, or say stop.', color: 'gray' }])}`);
  });
  probe.on('playerJoined', (p) => {
    if (p.username === 'Probe' || p.username === 'Tester') return;
    console.log(`  ${p.username} joined; sending the menu`);
    setTimeout(() => sendMenu(p.username).catch((e) => console.log(`  menu: ${e.message}`)), 2000);
  });
  console.log(`\nholding: join localhost:${port} with Minecraft ${version} (not opped) and click a word in the menu.`);
  console.log('Say "stop" in chat, or press Ctrl+C, to end it.');
  await new Promise((resolve) => {
    probe.on('chat', (username, message) => { if (username !== 'Probe' && /^stop[.!]?$/i.test(message.trim())) resolve(); });
    process.once('SIGINT', resolve);
  });
}

// ---- the run ------------------------------------------------------------------------------

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const { version } = args;
  const javaMajor = server.checkJava(args.java, version);
  const folder = path.join(LOCAL, `facility-${version}`);
  const jar = await server.ensurePaperJar(LOCAL, version);

  // A fresh world every run, so nothing a check looks for can be left over from the last one.
  for (const d of fs.existsSync(folder) ? fs.readdirSync(folder) : []) {
    if (/^world/.test(d)) fs.rmSync(path.join(folder, d), { recursive: true, force: true });
  }
  server.prepareFolder(folder, { port: args.port });
  datapack.writePack(path.join(folder, 'world'), version, {
    'build/box': datapack.boxFunction({ ...BOX, sentinel: SENTINEL }),
  });

  console.log(`facility spike: Paper ${version} on Java ${javaMajor}, port ${args.port}, ${folder}`);
  const srv = new server.Server({ jar, java: args.java, folder, version });
  if (process.env.WX_ECHO) srv.on('line', (l) => console.log(`  | ${l}`));
  const bots = [];
  try {
    const t0 = Date.now();
    await srv.start();
    console.log(`server up in ${((Date.now() - t0) / 1000).toFixed(1)} s`);
    await srv.prepareFence();

    const rules = [];
    for (const rule of ['daylight', 'weather', 'mobSpawning', 'commandBlockOutput']) {
      const cmd = `gamerule ${server.gameruleName(version, rule)} false`;
      const r = await srv.run(cmd);
      if (r.errors.length) rules.push(`${cmd}: ${r.errors.join(' ')}`);
    }
    check('setup', 'gamerules by version', rules.length === 0, rules.join(' | ') || `${server.gameruleName(version, 'daylight')} etc. accepted`);

    const probe = await joinBot(args.port, version, 'Probe');
    bots.push(probe);
    const tester = await joinBot(args.port, version, 'Tester');
    bots.push(tester);
    const at = `${STAND.x} ${STAND.y} ${STAND.z}`;
    for (const c of ['op Probe', `tp Probe ${at}`, `tp Tester ${at}`]) await srv.run(c);
    await nextEvent(tester, 'forcedMove', () => tester.entity.position.distanceTo(new Vec3(STAND.x, STAND.y, STAND.z)) < 1, 5000);
    console.log(`bots joined: Probe (op) and Tester (not op), Mineflayer ${require('mineflayer/package.json').version} speaking ${probe.version}`);

    const stages = [
      ['datapack', () => checkDatapack(srv, version, folder, probe)],
      ['console', () => checkConsole(srv, version, probe, tester)],
      ['display', () => checkDisplay(srv, version, probe)],
      ['bossbar', () => checkBossbar(srv, version, probe)],
    ];
    for (const [name, fn] of stages) {
      try { await fn(); } catch (e) { check(name, 'threw', false, e.stack || String(e)); }
    }
    if (args.hold) await hold(srv, version, probe, args.port);
  } catch (e) {
    check('setup', 'launch', false, e.message);
  } finally {
    for (const b of bots) b.quit();
    await srv.stop();
  }

  console.log(`\nfacility spike on ${version}:`);
  for (const m of ['setup', 'datapack', 'console', 'display', 'bossbar']) {
    const mine = results.filter((r) => r.mechanism === m);
    const failed = mine.filter((r) => !r.ok);
    const verdict = mine.length === 0 ? 'NOT RUN' : failed.length ? 'FAIL' : 'PASS';
    console.log(`  ${verdict.padEnd(7)} ${m}${failed.length ? ` — ${failed.map((f) => `${f.name}: ${f.detail}`).join('; ')}` : ` (${mine.length} checks)`}`);
  }
  const bad = results.filter((r) => !r.ok).length;
  const missing = ['datapack', 'console', 'display', 'bossbar'].some((m) => !results.some((r) => r.mechanism === m));
  process.exit(bad || missing ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(2); });
