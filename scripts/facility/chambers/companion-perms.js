'use strict';
// The Permissions Desk: the tester groups (lib/groups.js: visitor, builder, operator, each the one
// before and more) in LuckPerms, found by Wormhole through Vault, as run-facility --with
// permissions installs them. Probe2, never an op, is put in one group a cell (or `default`, in
// none) and tries four things, each behind its own node; the cell passes when each is allowed or
// refused exactly as the group says:
//
//   dial      press the DHD of a gate Probe owns        wormhole.use.dialer     visitor and up
//   preview   /wormhole gate build Standard             wormhole.build.preview  builder and up
//   config    /wormhole config gate-sound-volume        wormhole.config         operator
//   admin     /wormhole beam admin set PermDock         wormhole.beam.admin     operator
//
// (A gate nobody owns is anybody's to use, so the dial is at one Probe owns.) The `console` cell
// is the Systems console's own group switch: Probe2 says `!group builder` and must be told so and
// hold builder's nodes. A desk has no cell: the gate stands on G1's Stand position and cleanup
// runs G1's own reset function.

const campus = require('../lib/campus');
const { GateKit } = require('../lib/gatekit');
const { Groups, BASELINE, nodesOf } = require('../lib/groups');
const { BeamKit } = require('../lib/beams');
const { deskLayout } = require('../lib/blueprint');
const { ticks } = require('../lib/probe');

const def = campus.chamber('perms');
const O = campus.OVERWORLD;
const GATE = 'Perm';
const STAND = campus.GATES.stand;
const GEOM = new GateKit(null).place('Standard', STAND.facing, STAND);
const PREVIEW_AT = { x: -9.5, y: 0, z: -108.5, yaw: 180 };
const DOCK_AT = { x: -12, y: 0, z: -110, yaw: 180 };
const NO = /You lack the permissions to do this\./;
const VAULT_FOUND = '[WormholeXTreme] Vault provider detected; permission checks will use Vault/Bukkit provider.';
const FALLBACK = 'enabling simple permission fallback';

/** What each trial needs, and the words that say it was allowed. */
const TRIALS = {
  dial: { node: 'wormhole.use.dialer', allowed: /Gate successfully activated\./ },
  preview: { node: 'wormhole.build.preview', allowed: /Previewing Standard( in \w+)?\. Build inside it, then press a real button on its DHD\./ },
  config: { node: 'wormhole.config', allowed: /GATE_SOUND_VOLUME = / },
  admin: { node: 'wormhole.beam.admin', allowed: /Public beam destination "PermDock" set to your current location\./ },
};

const v = (value, why) => ({ value, label: value, why });
const CASES = [
  v('default', 'Probe2 in no tester group: none of the four'),
  v('visitor', 'Probe2 a visitor: dials, and nothing else'),
  v('builder', 'Probe2 a builder: dials and stands a preview; no config, no beam admin'),
  v('operator', 'Probe2 an operator (not a server op): all four'),
  v('console', 'the Systems console\'s group switch: Probe2 says !group builder'),
];

function refuses(o, version, fac) {
  if (fac && !fac.has('luckperms')) return 'needs LuckPerms and Vault: start the facility --with permissions';
  return null;
}

/** Everything a probe is told from now on, as plain text. */
function ear(probe) {
  if (!probe.heard) {
    probe.heard = [];
    probe.bot.on('message', (m) => { probe.heard.push(m.toString().replace(/§./g, '')); });
  }
  return probe.heard;
}

/** Runs `act` and returns what `probe` was told in the next `ms`. */
async function told(probe, act, ms = 2500) {
  const since = ear(probe).length;
  await act();
  const end = Date.now() + ms;
  while (Date.now() < end) await ticks(4);
  return ear(probe).slice(since).join(' / ');
}

async function stage(ctx, o) {
  const obs = ctx.observed;
  const fac = ctx.facility;
  const kit = new GateKit(ctx.server);
  const probe2 = await fac.second();
  ear(probe2);
  const groups = new Groups(ctx.server);
  if (!fac.groupsReady) { await groups.ensure(); fac.groupsReady = true; }
  await groups.put(probe2.name, 'default');
  if (o.case === 'console') return;
  await groups.put(probe2.name, o.case);
  if (await kit.exists(GATE)) await kit.remove(GATE);
  obs.build = (await kit.build(GATE, GEOM, { dim: O, floorY: STAND.floorY })).text;
  obs.owner = (await kit.edit(GATE, 'owner', fac.probe.name)).text;
}

async function run(ctx, o) {
  const obs = ctx.observed;
  const fac = ctx.facility;
  // Whether Wormhole found LuckPerms through Vault this start: under its own fallback (no
  // provider) a visitor's run would look the same.
  const since = ctx.server.log.slice(ctx.server.startIndex || 0);
  obs.provider = since.some((l) => l.includes(VAULT_FOUND));
  obs.fallback = since.some((l) => l.includes(FALLBACK));
  const probe2 = await fac.second();
  if (o.case === 'console') {
    await ctx.step('Probe2 says !group builder');
    obs.said = await told(probe2, async () => probe2.bot.chat('!group builder'), 4000);
    const groups = new Groups(ctx.server);
    obs.holds = {};
    for (const node of ['wormhole.build', 'wormhole.config']) obs.holds[node] = await groups.holds(probe2.name, node);
    return;
  }
  obs.told = {};
  await ctx.step('Probe2 presses the DHD of a gate Probe owns');
  const b = GEOM.button;
  await probe2.teleport({ x: b.x + 0.5 + GEOM.normal.x * 1.5, y: 0, z: b.z + 0.5 + GEOM.normal.z * 1.5, yaw: GEOM.yaw + 180 }, O);
  obs.told.dial = await told(probe2, () => probe2.press(b));
  if (TRIALS.dial.allowed.test(obs.told.dial)) await new GateKit(ctx.server).force(GATE);
  await ctx.step('Probe2 stands a preview');
  await probe2.teleport(PREVIEW_AT, O);
  obs.told.preview = await told(probe2, async () => probe2.bot.chat('/wormhole gate build Standard'));
  probe2.bot.chat('/wormhole gate preview clear -all');
  await ctx.step('Probe2 reads a setting');
  obs.told.config = await told(probe2, async () => probe2.bot.chat('/wormhole config gate-sound-volume'));
  await ctx.step('Probe2 saves a public beam destination');
  await probe2.teleport({ x: DOCK_AT.x + 0.5, y: 0, z: DOCK_AT.z + 0.5, yaw: 180 }, O);
  obs.told.admin = await told(probe2, async () => probe2.bot.chat('/wormhole beam admin set PermDock'));
}

function checks(ctx, o) {
  const obs = ctx.observed;
  const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
  const hooked = c('Wormhole found LuckPerms through Vault ("Vault provider detected"), not its own fallback', () => obs.provider && !obs.fallback);
  if (o.case === 'console') {
    return [hooked, c('Probe2 is told "You are in builder now"', () => /You are in builder now/.test(obs.said || '')),
      c('and holds builder\'s wormhole.build', () => obs.holds && obs.holds['wormhole.build'] === true),
      c('but not an operator\'s wormhole.config', () => obs.holds && obs.holds['wormhole.config'] === false)];
  }
  const holds = new Set(o.case === 'default' ? [] : nodesOf(o.case));
  const list = [hooked, c(`${GATE} was built and is Probe's`, () => /Built /.test(obs.build || '') && /Now owned by: Probe\b/.test(obs.owner || ''))];
  for (const [trial, t] of Object.entries(TRIALS)) {
    const said = () => (obs.told && obs.told[trial]) || '';
    if (holds.has(t.node)) {
      list.push(c(`${trial} (${t.node}) is allowed`, () => {
        if (t.allowed.test(said()) && !NO.test(said())) return true;
        throw new Error(`told: ${said() || 'nothing'}`);
      }));
    } else {
      list.push(c(`${trial} (${t.node}) is refused: "You lack the permissions to do this."`, () => NO.test(said()) && !t.allowed.test(said())));
    }
  }
  return list;
}

async function cleanup(ctx) {
  const fac = ctx.facility;
  const kit = new GateKit(ctx.server);
  if (fac.probe2) {
    fac.probe2.bot.chat('/wormhole gate preview clear -all');
    if (fac.has('luckperms')) await new Groups(ctx.server).put(fac.probe2.name, BASELINE).catch(() => {});
  }
  await new BeamKit(ctx.server).drop(fac.probe, 'public', 'PermDock');
  if (await kit.exists(GATE)) await kit.remove(GATE);
  const reset = fac.manifest.functions.find((f) => f.fn === 'reset/g1');
  if (reset) {
    const r = await fac.runFunction(reset);
    if (!r.ok) throw new Error(`G1's reset: ${r.detail}`);
  }
}

module.exports = {
  id: 'perms',
  wing: 'systems',
  title: def.title,
  seat: deskLayout(def).seat,
  options: { case: CASES },
  needs: () => ({ config: {} }),
  refuses,
  stage,
  run,
  checks,
  cleanup,
  TRIALS,
};
