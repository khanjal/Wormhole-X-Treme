'use strict';
// A permission run with no permissions plugin, by Probe2 (never an op). With none, the plugin is
// in its simple mode, which it no longer has a setting to turn off: anyone may use a gate, and
// building, configuring and removing need op. The nodes are a permissions plugin's to give: the
// companion Permissions Desk covers them with LuckPerms, and this refuses a run with it installed.
// `wormhole-use-is-teleport` needs the nodes, so nothing here exercises it any more.

const { GateKit } = require('../../lib/gatekit');
const { O, GATE, GEOM, NO, v, c, ear, told, atButton, before, buildGate, builtChecks } = require('./common');

const cases = [
  v('fallback', 'no permissions plugin: the plugin falls back to its simple mode, and a non-op may use a gate but not build, configure or remove'),
];

const FALLBACK = 'enabling simple permission fallback';
const ACTIVATED = /Gate successfully activated\./;
const LISTED = new RegExp(`\\b${GATE}\\b`);
const COMPASS = /Compass set to wormhole|No wormholes to track/;

function needs() {
  return {};
}

async function stage(ctx) {
  ear(await ctx.facility.second());
  Object.assign(ctx.observed, await buildGate(ctx));
}

async function run(ctx) {
  const obs = ctx.observed;
  const fac = ctx.facility;
  const kit = new GateKit(ctx.server);
  const p2 = await fac.second();
  obs.fallbackLogged = ctx.server.log.slice(ctx.server.startIndex || 0).some((l) => l.includes(FALLBACK));
  obs.told = {};
  ear(p2);
  await ctx.step(`${p2.name} presses the DHD of a gate it does not own`);
  await p2.teleport(atButton(GEOM), O);
  obs.told.dial = await told(p2, () => p2.press(GEOM.button));
  await kit.force(GATE);
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

function checks(obs) {
  const t = (k) => (obs.told && obs.told[k]) || '';
  const allowed = (k, re) => c(`${k} is allowed`, () => {
    if (re.test(t(k)) && !NO.test(t(k))) return true;
    throw new Error(`told: ${t(k) || 'nothing'}`);
  });
  const refused = (k, re) => c(`${k} is refused: "You lack the permissions to do this."`, () => {
    if (NO.test(t(k)) && !re.test(t(k))) return true;
    throw new Error(`told: ${t(k) || 'nothing'}`);
  });
  // GO is denied a non-op, and the name is then looked up as a beam destination.
  const noGo = c(`go is no gate to a non-op: "No gate or beam destination called ${GATE}."`, () => {
    if (t('go').includes(`No gate or beam destination called ${GATE}.`)) return true;
    throw new Error(`told: ${t('go') || 'nothing'}`);
  });
  return [
    ...builtChecks(obs),
    c('no permissions plugin: the plugin said it falls back ("enabling simple permission fallback")', () => obs.fallbackLogged === true),
    allowed('dial', ACTIVATED), allowed('list', LISTED), allowed('compass', COMPASS),
    refused('preview', /Previewing/), refused('config', /GATE_SOUND_VOLUME = /), refused('remove', /removed/i), noGo,
    c(`and ${GATE} is still there`, () => obs.stillThere === true),
  ];
}

/** With a permissions plugin the nodes are its to give: the Permissions Desk runs those. */
function refuses(o, version, fac) {
  return fac && fac.has('luckperms') ? 'a permissions plugin is installed: the Permissions Desk tests that' : null;
}

module.exports = { cases, needs, stage, run, checks, refuses };
