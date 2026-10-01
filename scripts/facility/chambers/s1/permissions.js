'use strict';
// Permission runs with no permissions plugin, by Probe2 (never an op). With none, the plugin
// is in its simple mode while `permissions-auto-fallback` is on (the default): anyone may use a
// gate, and building, configuring and removing need op. `permissions-auto-fallback false`, which
// it follows at every check, holds a non-op to the nodes and their plugin.yml defaults instead
// (wormhole.use.* false), and `wormhole-use-is-teleport true` then stops one walking through. The companion Permissions Desk
// covers the same with LuckPerms; these refuse a run with it installed.

const { GateKit } = require('../../lib/gatekit');
const campus = require('../../lib/campus');
const { ticks } = require('../../lib/probe');
const { O, GATE, GEOM, NO, v, c, ear, told, atButton, before, buildGate, builtChecks, walkThrough } = require('./common');

const cases = [
  v('fallback', 'no permissions plugin: the plugin falls back to its simple mode, and a non-op may use a gate but not build, configure or remove'),
  v('nodes', '`permissions-auto-fallback false`: a non-op is held to the nodes, and has none of wormhole.use.*, list or compass by default'),
  v('op outranks', 'the same, as an op at a gate it does not own: the DHD answers'),
  v('use is teleport', 'nodes, and `wormhole-use-is-teleport true`: a non-op without wormhole.use cannot walk through an open gate'),
  v('use is not teleport', 'nodes, and `wormhole-use-is-teleport false` (the default): the same non-op walks through'),
];

const FALLBACK = 'enabling simple permission fallback';
const ACTIVATED = /Gate successfully activated\./;
const LISTED = new RegExp(`\\b${GATE}\\b`);
const COMPASS = /Compass set to wormhole|No wormholes to track/;

function needs(o) {
  if (o.case === 'fallback') return {};
  const config = { 'permissions-auto-fallback': 'false' };
  if (o.case === 'use is teleport') config['wormhole-use-is-teleport'] = 'true';
  if (o.case === 'use is not teleport') config['wormhole-use-is-teleport'] = 'false';
  return config;
}

async function stage(ctx) {
  ear(await ctx.facility.second());
  Object.assign(ctx.observed, await buildGate(ctx));
}

async function run(ctx, o) {
  const obs = ctx.observed;
  const fac = ctx.facility;
  const kit = new GateKit(ctx.server);
  const p2 = await fac.second();
  obs.fallbackLogged = ctx.server.log.slice(ctx.server.startIndex || 0).some((l) => l.includes(FALLBACK));
  obs.mode = await ctx.config.get('permissions-auto-fallback');
  obs.told = {};
  if (['fallback', 'nodes', 'op outranks'].includes(o.case)) {
    const who = o.case === 'op outranks' ? fac.probe : p2;
    ear(who);
    // Probe is the owner; for the op's run it is handed to Probe2, so ownership is not the reason.
    if (o.case === 'op outranks') obs.handed = (await kit.edit(GATE, 'owner', p2.name)).text;
    await ctx.step(`${who.name} presses the DHD of a gate it does not own`);
    await who.teleport(atButton(GEOM), O);
    obs.told.dial = await told(who, () => who.press(GEOM.button));
    await kit.force(GATE);
  }
  if (['fallback', 'nodes'].includes(o.case)) {
    await ctx.step('Probe2 tries the commands');
    const trials = {
      list: '/wormhole list',
      compass: '/wormhole compass',
      preview: '/wormhole gate build Standard',
      config: '/wormhole config gate-sound-volume',
      remove: `/wormhole gate remove ${GATE}`,
      go: `/wormhole go ${GATE}`,
    };
    await p2.teleport(before(GEOM, 10), O);
    for (const [k, cmd] of Object.entries(trials)) obs.told[k] = await told(p2, async () => p2.bot.chat(cmd), 2000);
    p2.bot.chat('/wormhole gate preview clear -all');
    obs.stillThere = await kit.exists(GATE);
  }
  if (['use is teleport', 'use is not teleport'].includes(o.case)) {
    await ctx.step('Probe opens the gate; Probe2 walks into it');
    await fac.probe.teleport(before(GEOM, 8), O);
    await ticks(20);
    obs.dial = (await kit.dial(GATE, 'Relay')).text;
    obs.drawn = await kit.waitDrawn(fac.probe, GEOM, 15000);
    obs.toldWalk = await told(p2, async () => { obs.arrived = await walkThrough(p2); }, 1000);
    await kit.force(GATE);
    await kit.force('Relay');
    await p2.teleport(campus.TRANSIT.home);
    await ticks(5);
  }
}

function checks(obs, o) {
  const t = (k) => (obs.told && obs.told[k]) || '';
  const allowed = (k, re) => c(`${k} is allowed`, () => {
    if (re.test(t(k)) && !NO.test(t(k))) return true;
    throw new Error(`told: ${t(k) || 'nothing'}`);
  });
  const refused = (k, re) => c(`${k} is refused: "You lack the permissions to do this."`, () => {
    if (NO.test(t(k)) && !re.test(t(k))) return true;
    throw new Error(`told: ${t(k) || 'nothing'}`);
  });
  // GO is denied a non-op in either mode, and the name is then looked up as a beam destination.
  const noGo = c(`go is no gate to a non-op: "No gate or beam destination named: ${GATE}"`, () => {
    if (t('go').includes(`No gate or beam destination named: ${GATE}`)) return true;
    throw new Error(`told: ${t('go') || 'nothing'}`);
  });
  const list = [
    ...builtChecks(obs),
    c('no permissions plugin: the plugin said it falls back ("enabling simple permission fallback")', () => obs.fallbackLogged === true),
  ];
  if (o.case === 'fallback') {
    list.push(c('permissions-auto-fallback is on (the default)', () => obs.mode === 'true'),
      allowed('dial', ACTIVATED), allowed('list', LISTED), allowed('compass', COMPASS),
      refused('preview', /Previewing/), refused('config', /GATE_SOUND_VOLUME = /), refused('remove', /removed/i), noGo,
      c(`and ${GATE} is still there`, () => obs.stillThere === true));
  } else {
    list.push(c('permissions-auto-fallback is false: held to the nodes', () => obs.mode === 'false'));
    if (o.case === 'nodes') {
      list.push(refused('dial', ACTIVATED), refused('list', LISTED), refused('compass', COMPASS),
        refused('preview', /Previewing/), refused('config', /GATE_SOUND_VOLUME = /), refused('remove', /removed/i), noGo);
    } else if (o.case === 'op outranks') {
      list.push(c(`${GATE} was handed to Probe2`, () => /Now owned by: Probe2\b/.test(obs.handed || '')), allowed('dial', ACTIVATED));
    } else {
      list.push(c(`${GATE} was dialled and drawn open to Relay`, () => /Stargates connected/.test(obs.dial || '') && Boolean(obs.drawn)));
      if (o.case === 'use is teleport') {
        list.push(c('Probe2 was told "You lack the permissions to do this." in the opening', () => NO.test(obs.toldWalk || '')),
          c('and was not carried to Relay', () => obs.arrived === false));
      } else {
        list.push(c('Probe2 walked through to Relay', () => obs.arrived === true));
      }
    }
  }
  return list;
}

/** With a permissions plugin the nodes are its to give: the Permissions Desk runs those. */
function refuses(o, version, fac) {
  return fac && fac.has('luckperms') ? 'a permissions plugin is installed: the Permissions Desk tests that' : null;
}

module.exports = { cases, needs, stage, run, checks, refuses };
