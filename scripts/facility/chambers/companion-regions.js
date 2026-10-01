'use strict';
// The Region Desk (#240, WorldGuard region flags wormhole-build and wormhole-use): the #240
// checklist, one case a cell, against a jar built with the integration and WorldGuard (with
// WorldEdit) and LuckPerms (with Vault) installed by run-facility --with. Probe2 is the player
// under test: never an op (an op passes WorldGuard's region bypass), put in the tester group
// `builder` (lib/groups.js) for the nodes a gate needs.
//
// Where: G1's cell, whose Stand position holds `Guarded` (the checklist's G1), inside region
// `gatetest` round it and its arrival point; the Relay across the hall is the checklist's G2,
// with no region. Region `buildtest`, empty, is the east end of the same cell, where Probe2
// lays a Standard frame by hand (the blocks set from the console, the DHD pressed by Probe2),
// stands a preview, and where a frame just west of it reaches in by one column. A desk has no
// cell of its own: cleanup takes the gates down and runs G1's own reset function, so G1 finds
// its cell as built.
//
// Regions are written to WorldGuard's region file and loaded (`rg define` needs a player's
// WorldEdit selection); flags and members then change by `rg flag` and `rg addmember`, the
// console forms a server's staff would use.
//
// Two cells restart the server: `switched off` (worldguard-enabled false: the flags are not
// registered, `rg flag ... wormhole-use` is an unknown flag and nothing is refused) and the
// paired run `absent` (worldguard-enabled true with no WorldGuard: the plugin loads clean and
// gates work). Their cleanup restarts again with the setting put back.

const fs = require('fs');
const path = require('path');
const campus = require('../lib/campus');
const { GateKit } = require('../lib/gatekit');
const { Groups, BASELINE } = require('../lib/groups');
const { deskLayout } = require('../lib/blueprint');
const { ticks } = require('../lib/probe');
const relay = require('./relay');

const def = campus.chamber('regions');
const O = campus.OVERWORLD;
const GUARDED = 'Guarded';
const FAR = 'Relay';
const IDC = '2400';
const kit0 = new GateKit(null);
/** The checklist's G1: Standard on G1's Stand position, flush with the floor, with an iris lever. */
const GUARD = kit0.place('Standard', campus.GATES.stand.facing, campus.GATES.stand);
/** Hand-built frames (bottom row on the floor): inside buildtest, reaching in by a column, and in no region. */
const HAND = {
  inside: kit0.place('Standard', 'south', { cx: 13.5, openingAt: -112, floorY: 1 }),
  straddling: kit0.place('Standard', 'south', { cx: 5.5, openingAt: -112, floorY: 1 }),
  open: kit0.place('Standard', 'south', { cx: -12.5, openingAt: -112, floorY: 1 }),
};
const REGIONS = {
  gatetest: { x0: -5, y0: -3, z0: -130, x1: 5, y1: 8, z1: -121 },
  buildtest: { x0: 9, y0: -2, z0: -118, x1: 19, y1: 10, z1: -104 },
};
const USE_REFUSED = /This region does not allow using gates\./;
const BUILD_REFUSED = /This region does not allow building gates\./;
const NO_PERMISSION = /You lack the permissions to do this\./;
const ACTIVATED = /Gate successfully activated\./;
const VALID_DESIGN = /Valid Stargate Design!/;

const v = (value, why) => ({ value, label: value, why });
const CASES = [
  v('dial refused', '1: Probe2 presses Guarded\'s DHD in gatetest (wormhole-use deny): refused in the region\'s words, nothing lit'),
  v('walk-in refused', '2: Relay dialled to Guarded; Probe2 walks into Relay: it opened, no trip, told at most once in 2 s'),
  v('cart refused', '3: as 2, with Probe2 in a minecart run into Relay: the cart does not go through'),
  v('shut allowed', '4: an op opens Guarded from outside; Probe2 presses its DHD to shut it: allowed'),
  v('iris allowed', '5: Probe2 toggles Guarded\'s iris lever: allowed'),
  v('hand build refused', '6: Probe2 lays a Standard frame in buildtest (wormhole-build deny) and presses its DHD: refused, no completion prompt'),
  v('preview refused', '7: Probe2 stands a preview in buildtest and places it: refused, "Nothing placed.", no block placed'),
  v('straddling refused', '8: a frame outside buildtest with one column inside: refused'),
  v('hand build allowed', '9: a frame in no region: the completion prompt'),
  v('member refused', '10: Probe2 a member of gatetest under a plain deny: still refused'),
  v('nonmembers flag', '11: wormhole-use -g nonmembers deny: Probe2 as a member is allowed, and refused once removed'),
  v('owner refused', '12: Guarded owned by Probe2, plain deny: refused (owners get no pass)'),
  v('op allowed', '13: Probe, an op, presses the DHD: allowed (WorldGuard\'s bypass)'),
  v('no nodes', '14: Probe2 with no Wormhole nodes, on a gate Probe owns: the no-permission message, not the region\'s'),
  v('flag cleared', '15: the flag cleared: allowed again'),
  v('switched off', 'P1: worldguard-enabled false and a restart: wormhole-use is an unknown flag and nothing is refused'),
  v('absent', 'P2 (a run without WorldGuard): worldguard-enabled true with WorldGuard absent: loads clean, gates work'),
];

/** Whether a case restarts the server (and its cleanup restarts it again, the setting put back). */
const RESTARTS = { 'switched off': 'false', absent: 'true' };
/** Module state across runs: a restart owed by the last run's cleanup. */
const state = { restartOwed: null };

function refuses(o, version, fac) {
  if (!fac) return null;
  if (o.case === 'absent') {
    return fac.has('worldguard') ? 'the absent case is the paired run: start the facility without WorldGuard (--with anything but it)' : null;
  }
  if (!fac.has('worldguard')) return 'needs WorldGuard: start the facility --with regions,permissions';
  if (!fac.has('luckperms')) return 'needs LuckPerms, for Probe2\'s nodes: start the facility --with regions,permissions';
  return null;
}

// ---- helpers --------------------------------------------------------------------------------

/** Everything a probe is told, from the first time it is asked for, as plain text. */
function ear(probe) {
  // Its own properties: another desk's ear on the same Probe2 keeps no times.
  if (!probe.regionHeard) {
    probe.regionHeard = [];
    probe.regionHeardAt = [];
    probe.bot.on('message', (m) => { probe.regionHeard.push(m.toString().replace(/§./g, '')); probe.regionHeardAt.push(Date.now()); });
  }
  return probe.regionHeard;
}

/** Waits (bounded) for a line matching `re` told to `probe` from index `since`; returns it or null. */
async function heard(probe, re, since, ms = 4000) {
  const lines = ear(probe);
  const end = Date.now() + ms;
  for (;;) {
    const hit = lines.slice(since).find((l) => re.test(l));
    if (hit || Date.now() > end) return hit || null;
    await ticks(2);
  }
}

function regionYaml(flags) {
  const block = (id) => {
    const b = REGIONS[id];
    const f = flags[id] || {};
    return [`    ${id}:`,
      `        min: {x: ${b.x0}.0, y: ${b.y0}.0, z: ${b.z0}.0}`,
      `        max: {x: ${b.x1}.0, y: ${b.y1}.0, z: ${b.z1}.0}`,
      '        members: {}',
      `        flags: {${Object.entries(f.flags || {}).map(([k, x]) => `${k}: ${x}`).join(', ')}}`,
      '        owners: {}',
      '        type: cuboid',
      '        priority: 0'].join('\n');
  };
  const ids = Object.keys(flags);
  return ids.length ? `regions:\n${ids.map(block).join('\n')}\n` : 'regions: {}\n';
}

/**
 * Writes WorldGuard's region file for the overworld with these regions and flags, and has it
 * loaded (`rg load`, whose answer comes from another thread: waited for in the log).
 */
async function setRegions(ctx, flags) {
  const srv = ctx.server;
  const file = path.join(srv.folder, 'plugins', 'WorldGuard', 'worlds', 'world', 'regions.yml');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, regionYaml(flags));
  const loaded = srv.waitFor(/Loaded region data for 'world'/, 15000, 'WorldGuard to load the region file');
  await srv.run('rg load -w world');
  await loaded;
}

/** A console region command whose answer comes from another thread; resolves with the answer line. */
async function rgAsync(ctx, command, answer) {
  const said = ctx.server.waitFor(answer, 15000, `WorldGuard to answer ${command}`);
  await ctx.server.run(command);
  return said;
}

/** Lays a Standard frame in obsidian (the Standard group's frame) and its DHD button, as a builder would. */
async function layFrame(ctx, geom) {
  for (const p of geom.frame) await ctx.server.run(`execute in ${O} run setblock ${p.x} ${p.y} ${p.z} minecraft:obsidian`);
  const b = geom.button;
  await ctx.server.run(`execute in ${O} run setblock ${b.x} ${b.y} ${b.z} minecraft:stone_button[face=wall,facing=${geom.facing}]`);
}

/** Where a player stands to press a gate's DHD button: a block and a half out in front of it. */
function atButton(geom) {
  const b = geom.button;
  return { x: b.x + 0.5 + geom.normal.x * 1.5, y: Math.max(0, b.y - 1), z: b.z + 0.5 + geom.normal.z * 1.5, yaw: geom.yaw + 180 };
}

/** Presses a gate's DHD as `probe` and returns what it was told in the next `ms`. */
async function pressDhd(probe, geom, ms = 3000) {
  await probe.teleport(atButton(geom), O);
  const since = ear(probe).length;
  await probe.press(geom.button);
  const end = Date.now() + ms;
  while (Date.now() < end) await ticks(4);
  return ear(probe).slice(since).join(' / ');
}

/** True if nothing but air stands in a box (a frame block placed by a preview would show). */
async function clear(ctx, box) {
  const r = await ctx.facility.isClear(O, { x0: box.x0, x1: box.x1, y0: box.y0, y1: box.y1, z0: box.z0, z1: box.z1 });
  return r.ok;
}

// ---- stage, run, checks --------------------------------------------------------------------

async function stage(ctx, o) {
  const obs = ctx.observed;
  obs.case = o.case;
  const kit = new GateKit(ctx.server);
  if (RESTARTS[o.case]) {
    // The setting (needs.config) is in force only after a full restart.
    obs.restartFrom = await ctx.facility.restart(`worldguard-enabled ${RESTARTS[o.case]} (Region Desk: ${o.case})`);
    state.restartOwed = o.case;
  }
  const far = relay.farGates();
  if (!(await kit.exists(FAR))) await relay.fixture(ctx);
  await kit.force(FAR);
  if (await kit.exists(GUARDED)) await kit.remove(GUARDED);
  obs.build = (await kit.build(GUARDED, GUARD, { dim: O, idc: IDC, floorY: campus.GATES.stand.floorY })).text;
  obs.far = far[FAR].geom;
  if (o.case === 'absent') return;
  const probe2 = await ctx.facility.second();
  ear(probe2);
  const groups = new Groups(ctx.server);
  if (!ctx.facility.groupsReady) { await groups.ensure(); ctx.facility.groupsReady = true; }
  await groups.put(probe2.name, o.case === 'no nodes' ? 'default' : 'builder');
  // gatetest refuses use, buildtest building; the cases that change that do so in run().
  const flags = {
    gatetest: { flags: { 'wormhole-use': 'deny' } },
    buildtest: { flags: { 'wormhole-build': 'deny' } },
  };
  if (o.case !== 'switched off') await setRegions(ctx, flags);
  else await setRegions(ctx, { gatetest: {}, buildtest: {} });
  if (o.case === 'member refused') await rgAsync(ctx, `rg addmember -w world gatetest ${probe2.name}`, /Region 'gatetest' updated with new members/);
  if (o.case === 'owner refused') obs.owner = (await kit.edit(GUARDED, 'owner', probe2.name)).text;
  if (o.case === 'no nodes') obs.owner = (await kit.edit(GUARDED, 'owner', ctx.facility.probe.name)).text;
}

async function run(ctx, o) {
  const obs = ctx.observed;
  const fac = ctx.facility;
  const kit = new GateKit(ctx.server);
  const probe = fac.probe;
  if (o.case === 'absent') return runAbsent(ctx, kit);
  const probe2 = await fac.second();
  switch (o.case) {
    case 'dial refused':
    case 'member refused':
    case 'owner refused':
    case 'no nodes':
      await ctx.step('Probe2 presses Guarded\'s DHD');
      obs.told = await pressDhd(probe2, GUARD);
      break;
    case 'op allowed':
      await ctx.step('Probe (an op) presses Guarded\'s DHD');
      obs.told = await pressDhd(probe, GUARD);
      break;
    case 'flag cleared':
      obs.cleared = (await ctx.server.run('rg flag -w world gatetest wormhole-use')).lines.join(' / ');
      await ctx.step('Probe2 presses Guarded\'s DHD');
      obs.told = await pressDhd(probe2, GUARD);
      break;
    case 'nonmembers flag': {
      await rgAsync(ctx, `rg addmember -w world gatetest ${probe2.name}`, /Region 'gatetest' updated with new members/);
      obs.flagged = (await ctx.server.run('rg flag -w world gatetest -g nonmembers wormhole-use deny')).lines.join(' / ');
      await ctx.step('Probe2, a member, presses the DHD');
      obs.told = await pressDhd(probe2, GUARD);
      // Put it back to rest (shutting is always allowed), then drop the membership and try again.
      obs.rest = await pressDhd(probe2, GUARD, 1500);
      await rgAsync(ctx, `rg removemember -w world gatetest ${probe2.name}`, /Region 'gatetest' updated with members removed/);
      await ctx.step('Probe2, no longer a member, presses it');
      obs.toldAfter = await pressDhd(probe2, GUARD);
      break;
    }
    case 'switched off':
      // WorldGuard answers an unknown flag from another thread.
      obs.unknownFlag = await rgAsync(ctx, 'rg flag -w world gatetest wormhole-use deny', /Unknown flag specified: wormhole-use|Region flag wormhole-use set on/);
      obs.registered = ctx.server.log.slice(obs.restartFrom).filter((l) => /Registered WorldGuard flags/.test(l)).length;
      await ctx.step('Probe2 presses Guarded\'s DHD');
      obs.told = await pressDhd(probe2, GUARD);
      break;
    case 'walk-in refused':
    case 'cart refused':
      await walkIn(ctx, kit, probe2, o.case === 'cart refused');
      break;
    case 'shut allowed': {
      await ctx.step('Probe (an op) opens Guarded to Relay');
      const opened = await pressDhd(probe, GUARD, 1500);
      const since = ear(probe).length;
      probe.bot.chat(`/dial ${FAR}`);
      obs.opened = `${opened} / ${await heard(probe, /Stargates connected|error/i, since, 5000)}`;
      obs.drawn = await kit.waitOpen(probe2, GUARD).catch((e) => { obs.openError = e.message; return null; });
      await ctx.step('Probe2 presses its DHD to shut it');
      obs.told = await pressDhd(probe2, GUARD, 1500);
      obs.shut = await kit.waitShut(probe2, GUARD, 8000);
      break;
    }
    case 'iris allowed': {
      await ctx.step('Probe2 pulls Guarded\'s iris lever');
      const l = GUARD.lever;
      await probe2.teleport({ x: l.x + 0.5 + GUARD.normal.x * 2, y: 0, z: l.z + 0.5 + GUARD.normal.z * 2, yaw: GUARD.yaw + 180 }, O);
      const since = ear(probe2).length;
      obs.lever = await probe2.click(l).then((b) => b.getProperties().powered).catch((e) => `no change: ${e.message}`);
      await ticks(40);
      obs.told = ear(probe2).slice(since).join(' / ');
      obs.irisDrawn = irisShown(probe2);
      break;
    }
    case 'hand build refused':
    case 'straddling refused':
    case 'hand build allowed': {
      const geom = HAND[{ 'hand build refused': 'inside', 'straddling refused': 'straddling', 'hand build allowed': 'open' }[o.case]];
      await ctx.step('the frame is laid; Probe2 presses its DHD');
      await layFrame(ctx, geom);
      obs.told = await pressDhd(probe2, geom);
      break;
    }
    case 'preview refused': {
      await ctx.step('Probe2 stands a preview in buildtest and places it');
      await probe2.teleport({ x: 14.5, y: 0, z: -105.5, yaw: 180 }, O);
      await probe2.face({ x: 14.5, y: 1.6, z: -115 });
      const since = ear(probe2).length;
      probe2.bot.chat('/wormhole gate build Standard');
      obs.preview = await heard(probe2, /./, since, 5000);
      await ticks(20);
      const at = ear(probe2).length;
      probe2.bot.chat('/wormhole gate preview place');
      await ticks(60);
      obs.told = ear(probe2).slice(at).join(' / ');
      obs.previewTold = ear(probe2).slice(since, at).join(' / ');
      obs.nothingPlaced = await clear(ctx, { ...REGIONS.buildtest, y0: 0 });
      probe2.bot.chat('/wormhole gate preview clear -all');
      await ticks(10);
      break;
    }
    default:
      throw new Error(`no case ${o.case}`);
  }
  return undefined;
}

/** Whether Probe2's client is shown the iris (not air) in Guarded's opening. */
function irisShown(probe) {
  const { Vec3 } = require('vec3');
  return GUARD.opening.every((c) => { const b = probe.bot.blockAt(new Vec3(c.x, c.y, c.z)); return b && !['air', 'cave_air'].includes(b.name); });
}

/** Cases 2 and 3: Relay dialled to Guarded; Probe2 into Relay on foot or in a cart. */
async function walkIn(ctx, kit, probe2, cart) {
  const obs = ctx.observed;
  const g = obs.far;
  const lane = Math.floor(g.centre.x);
  const start = { x: lane + 0.5, y: 0, z: Math.floor(g.opening[0].z) + 0.5 + g.normal.z * 7, yaw: 180 };
  await probe2.teleport(start, O);
  await ctx.step('Relay dials Guarded');
  obs.dial = (await kit.dial(FAR, GUARDED)).text;
  obs.drawn = await kit.waitOpen(probe2, g).catch((e) => { obs.openError = e.message; return null; });
  if (!obs.drawn) return;
  const since = ear(probe2).length;
  const t0 = Date.now();
  const behind = { x: lane + 0.5, z: Math.floor(g.opening[0].z) + 0.5 - g.normal.z * 2.5 };
  const front = { x: lane + 0.5, z: start.z };
  if (cart) {
    await ctx.step('Probe2 in a cart, run into Relay');
    const railZ0 = Math.floor(g.opening[0].z) + 1;
    await ctx.server.run(`execute in ${O} run fill ${lane} 0 ${railZ0} ${lane} 0 ${Math.floor(start.z) + 1} minecraft:rail[shape=north_south]`);
    await ctx.server.run(`execute in ${O} run summon minecraft:minecart ${lane + 0.5} 0.1 ${start.z} {Tags:["${ctx.tag}"]}`);
    await ctx.server.run(`ride ${probe2.name} mount @e[tag=${ctx.tag},limit=1]`);
    await ticks(10);
    await ctx.server.run(`data merge entity @e[tag=${ctx.tag},limit=1] {Motion:[0.0d,0.0d,${(-g.normal.z * 0.6).toFixed(1)}d]}`);
    await ticks(120);
    const cartAt = await probe2.entityByTag(ctx.tag);
    obs.cartAt = cartAt;
    await probe2.dismount().catch(() => {});
  } else {
    await ctx.step('Probe2 walks into Relay, three times over six seconds');
    for (let i = 0; i < 3; i++) {
      await probe2.walkTo(behind, { within: 0.5, ms: 6000 }).catch(() => {});
      await probe2.walkTo(front, { within: 0.5, ms: 6000 }).catch(() => {});
    }
  }
  obs.window = (Date.now() - t0) / 1000;
  const at = ear(probe2).map((l, i) => (i >= since && USE_REFUSED.test(l) ? probe2.regionHeardAt[i] : null)).filter((t) => t !== null);
  obs.refusals = at.length;
  // The gaps between one refusal and the next: the reminder's own spacing, whatever the walks took.
  obs.gaps = at.slice(1).map((t, i) => t - at[i]);
  obs.nearGuarded = probe2.distanceTo(GUARD.arrival);
  obs.dimension = probe2.dimension;
}

/** P2: worldguard-enabled true, WorldGuard absent: the plugin says so, loads clean, and a gate works. */
async function runAbsent(ctx, kit) {
  const obs = ctx.observed;
  const lines = ctx.server.log.slice(obs.restartFrom);
  obs.notInstalled = lines.filter((l) => /worldguard-enabled is set, but WorldGuard is not installed/.test(l)).length;
  obs.enabled = lines.some((l) => /\[WormholeXTreme\].*Enable Completed/.test(l));
  obs.wormholeFaults = ctx.facility.faults.faults.filter((f) => lines.some((l) => l.includes(f))).length;
  obs.wgErrors = lines.filter((l) => /WorldGuard|worldguard/.test(l) && /WARN|ERROR|Exception/.test(l)).length;
  const probe = ctx.facility.probe;
  await ctx.step('Probe walks from Guarded to Relay');
  const g = GUARD;
  const start = { x: g.centre.x, y: 0, z: Math.floor(g.opening[0].z) + 0.5 + g.normal.z * 6, yaw: 180 };
  await probe.teleport(start, O);
  obs.dial = (await kit.dial(GUARDED, FAR)).text;
  obs.drawn = await kit.waitOpen(probe, g).catch((e) => { obs.openError = e.message; return null; });
  if (!obs.drawn) return;
  const far = obs.far;
  await probe.walkTo({ x: g.centre.x, z: Math.floor(g.opening[0].z) + 0.5 - g.normal.z * 2.5 }, { within: 0.4, ms: 15000, until: () => probe.distanceTo(far.arrival) < 3 }).catch(() => {});
  const end = Date.now() + 4000;
  while (Date.now() < end && probe.distanceTo(far.arrival) >= 1.5) await ticks(5);
  obs.landed = probe.distanceTo(far.arrival);
}

function checks(ctx, o) {
  const obs = ctx.observed;
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  const told = (re) => re.test(obs.told || '');
  const built = c('Guarded was built in gatetest', () => /Built /.test(obs.build || ''));
  switch (o.case) {
    case 'dial refused':
    case 'member refused':
    case 'owner refused':
      return [built,
        ...(o.case === 'owner refused' ? [c('Guarded is owned by Probe2', () => /Now owned by: Probe2/.test(obs.owner || ''))] : []),
        c('refused in the region\'s words: "This region does not allow using gates."', () => told(USE_REFUSED)),
        c('and nothing lit: no "Gate successfully activated."', () => !told(ACTIVATED))];
    case 'op allowed':
    case 'flag cleared':
    case 'switched off':
      return [built,
        ...(o.case === 'flag cleared' ? [c('the flag was cleared', () => /Region flag wormhole-use removed/.test(obs.cleared || ''))] : []),
        ...(o.case === 'switched off' ? [
          c('worldguard-enabled false: the flags were not registered this start', () => obs.registered === 0),
          c('so WorldGuard says "Unknown flag specified: wormhole-use"', () => /Unknown flag specified: wormhole-use/.test(obs.unknownFlag || '')),
        ] : []),
        c('the DHD lit: "Gate successfully activated."', () => told(ACTIVATED)),
        c('and no region refusal', () => !told(USE_REFUSED))];
    case 'no nodes':
      return [built,
        c('Guarded is owned by Probe', () => /Now owned by: Probe\b/.test(obs.owner || '')),
        c('Probe2 is told the no-permission message: "You lack the permissions to do this."', () => told(NO_PERMISSION)),
        c('not the region\'s', () => !told(USE_REFUSED)),
        c('and nothing lit', () => !told(ACTIVATED))];
    case 'nonmembers flag':
      return [built,
        c('the flag is set for non-members', () => /Region group flag for 'wormhole-use' set/.test(obs.flagged || '')),
        c('Probe2, a member, lit the DHD', () => told(ACTIVATED) && !told(USE_REFUSED)),
        c('removed from the members, Probe2 is refused', () => USE_REFUSED.test(obs.toldAfter || '') && !ACTIVATED.test(obs.toldAfter || ''))];
    case 'walk-in refused':
    case 'cart refused':
      return [built,
        c('Relay dialled Guarded: "Stargates connected."', () => /Stargates connected/.test(obs.dial || '')),
        c('Relay opened (drawn to Probe2)', () => Boolean(obs.drawn)),
        c(`Probe2 did not travel: still in the overworld, not at Guarded's arrival`, () => obs.dimension === O && obs.nearGuarded > 8),
        ...(o.case === 'cart refused' ? [c('the cart did not travel either', () => obs.cartAt && Math.hypot(obs.cartAt.x - GUARD.arrival.x, obs.cartAt.z - GUARD.arrival.z) > 8)] : []),
        c('Probe2 was told why: "This region does not allow using gates."', () => obs.refusals >= 1),
        c(o.case === 'walk-in refused' ? 'told again while it kept walking in, but never within two seconds of the last time'
          : 'never told twice within two seconds', () => {
          // 1900: the client's clock, not the server's, times each message. A cart goes through
          // once, so only the walker, three times over six seconds, must have been told again.
          const again = o.case !== 'walk-in refused' || (obs.gaps && obs.gaps.length > 0);
          if (again && (obs.gaps || []).every((g) => g >= 1900)) return true;
          throw new Error(`gaps between refusals: ${(obs.gaps || []).join(', ') || 'none (told once)'} ms`);
        })];
    case 'shut allowed':
      return [built,
        c('the op opened Guarded to Relay', () => /Stargates connected/.test(obs.opened || '') && Boolean(obs.drawn)),
        c('Probe2 was not refused', () => !told(USE_REFUSED)),
        c('Probe2\'s press shut it: its opening is drawn as air again', () => obs.shut === true)];
    case 'iris allowed':
      return [built,
        c('the lever moved', () => obs.lever === true),
        c('Probe2 was not refused', () => !told(USE_REFUSED)),
        c('the iris is drawn shut over the opening', () => obs.irisDrawn === true)];
    case 'hand build refused':
    case 'straddling refused':
      return [c('refused in the region\'s words: "This region does not allow building gates."', () => told(BUILD_REFUSED)),
        c('and no completion prompt', () => !told(VALID_DESIGN))];
    case 'hand build allowed':
      return [c('the completion prompt: "Valid Stargate Design!"', () => told(VALID_DESIGN)),
        c('and no region refusal', () => !told(BUILD_REFUSED))];
    case 'preview refused':
      return [c('the preview stood: "Previewing Standard. Build inside it, ..."', () => /Previewing Standard( in \w+)?\. Build inside it, then press a real button on its DHD\./.test(obs.previewTold || '')),
        c('placing it was refused: "This region does not allow building gates. Nothing placed."', () => /This region does not allow building gates\. Nothing placed\./.test(obs.told || '')),
        c('and no block was placed in buildtest', () => obs.nothingPlaced === true)];
    case 'absent':
      return [
        c('the plugin said once that WorldGuard is not installed', () => obs.notInstalled === 1),
        c('and enabled', () => obs.enabled),
        c('with no Wormhole fault and no WorldGuard error since the restart', () => obs.wormholeFaults === 0 && obs.wgErrors === 0),
        built,
        c('a gate still works: Guarded dialled Relay', () => /Stargates connected/.test(obs.dial || '') && Boolean(obs.drawn)),
        c('and Probe came out of Relay', () => obs.landed !== undefined && obs.landed < 1.5)];
    default:
      return [c(`a case called ${o.case}`, () => false)];
  }
}

async function cleanup(ctx) {
  const fac = ctx.facility;
  const kit = new GateKit(ctx.server);
  if (state.restartOwed) {
    const owed = state.restartOwed;
    state.restartOwed = null;
    await fac.restart(`the setting put back after the Region Desk's ${owed} case`);
  }
  for (const p of [fac.probe, fac.probe2]) if (p && p.bot.vehicle) await p.dismount().catch(() => {});
  await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
  if (fac.probe2) {
    fac.probe2.bot.chat('/wormhole gate preview clear -all');
    if (fac.has('luckperms')) await new Groups(ctx.server).put(fac.probe2.name, BASELINE).catch(() => {});
  }
  if (await kit.exists(GUARDED)) await kit.remove(GUARDED);
  if (await kit.exists(FAR)) await kit.force(FAR);
  if (fac.has('worldguard')) await setRegions(ctx, {});
  // The Relay's lane (case 3), and G1's cell as built: frames laid by hand, the gate's pit.
  const g = relay.farGates()[FAR].geom;
  const lane = Math.floor(g.centre.x);
  await ctx.server.run(`execute in ${O} run fill ${lane} 0 ${Math.floor(g.opening[0].z) + 1} ${lane} 0 ${Math.floor(g.opening[0].z) + 9} minecraft:air replace minecraft:rail`);
  const reset = fac.manifest.functions.find((f) => f.fn === 'reset/g1');
  if (reset) {
    const r = await fac.runFunction(reset);
    if (!r.ok) throw new Error(`G1's reset: ${r.detail}`);
  }
}

module.exports = {
  id: 'regions',
  wing: 'systems',
  title: def.title,
  seat: deskLayout(def).seat,
  options: { case: CASES },
  needs: (o) => (RESTARTS[o.case] ? { config: { 'worldguard-enabled': RESTARTS[o.case] } } : { config: {} }),
  refuses,
  stage,
  run,
  checks,
  cleanup,
  GUARDED,
  REGIONS,
};
