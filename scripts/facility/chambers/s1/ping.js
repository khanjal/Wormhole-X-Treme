'use strict';
// The experimental locator bar ping (#16): `/wormhole ping Sys` as Probe, and what the server can
// show of it. A bot has no locator bar, so whether a client draws the marker is never checked
// here: only that the marker entity stands at the gate, invisible and unsaved, carrying the
// transmit range the plugin set, that the server's own waypoint list has it, and that it is gone
// after ping-seconds. Each run prints that split, proved and not, in the log.

const { atLeast } = require('../../lib/version');
const { ticks } = require('../../lib/probe');
const { O, GATE, v, c, said, ear, told, until, atButton, buildGate, builtChecks } = require('./common');

const LOCATOR_BAR = '1.21.6';
const SECONDS = 3;
const MARKER = '@e[type=minecraft:armor_stand,tag=wormhole_ping]';

const cases = [
  {
    ...v('ping', `\`ping-enabled true\`, \`ping-seconds ${SECONDS}\`: Probe pings Sys; an invisible marker armour stand stands at the gate with waypoint_transmit_range 6.0E7, is on the server's waypoint list, and is gone ${SECONDS} s later`),
    expect: (version) => (atLeast(version, LOCATOR_BAR) ? 'PASS' : 'REFUSED:the locator bar arrived in Minecraft 1.21.6'),
  },
  {
    ...v('ping unsupported', '`ping-enabled true` on a server with no locator bar: Probe is pointed at the compass, and nothing is spawned'),
    expect: (version) => (atLeast(version, LOCATOR_BAR) ? 'REFUSED:this server has a locator bar' : 'PASS'),
  },
  v('ping off', 'as shipped, ping-enabled false: Probe is told pinging is off, and nothing is spawned'),
];

function needs(o) {
  return o.case === 'ping off' ? {} : { 'ping-enabled': 'true', 'ping-seconds': String(SECONDS), 'ping-cooldown-seconds': '0' };
}

function refuses(o, version) {
  if (o.case === 'ping' && !atLeast(version, LOCATOR_BAR)) return 'the locator bar arrived in Minecraft 1.21.6';
  if (o.case === 'ping unsupported' && atLeast(version, LOCATOR_BAR)) return 'this server has a locator bar';
  return null;
}

async function stage(ctx) {
  Object.assign(ctx.observed, await buildGate(ctx));
}

/** How many ping markers stand now, by the console: "Test passed" is one, "Test passed, count: N" more. */
async function markers(ctx) {
  const lines = await said(ctx, `execute if entity ${MARKER}`);
  const m = lines.map((l) => /^Test passed(?:, count: (\d+))?/.exec(l)).find(Boolean);
  if (!m) return 0;
  return m[1] ? Number(m[1]) : 1;
}

/** How many waypoints `waypoint list` counts in its first line ("3 waypoint(s) in ..."), or -1. */
function waypointCount(text) {
  const m = /(\d+) waypoint\(s\)/.exec(text || '');
  return m ? Number(m[1]) : -1;
}

async function run(ctx, o) {
  const obs = ctx.observed;
  const probe = ctx.facility.probe;
  ear(probe);
  await probe.teleport(atButton(), O);
  if (o.case !== 'ping') {
    obs.told = await told(probe, () => probe.bot.chat(`/wormhole ping ${GATE}`));
    obs.stood = await markers(ctx);
    return;
  }
  const waypointsBefore = (await said(ctx, 'waypoint list')).join(' / ');
  const sent = Date.now();
  obs.told = await told(probe, () => probe.bot.chat(`/wormhole ping ${GATE}`), 1000);
  obs.stood = await markers(ctx);
  obs.range = (await said(ctx, `attribute ${MARKER.replace(/\]$/, ',limit=1]')} minecraft:waypoint_transmit_range get`)).join(' / ');
  obs.data = (await said(ctx, `data get entity ${MARKER.replace(/\]$/, ',limit=1]')}`)).join(' ');
  obs.waypoints = (await said(ctx, 'waypoint list')).join(' / ');
  obs.waypointsBefore = waypointsBefore;
  // Gone within ping-seconds and a second's grace, polled rather than slept.
  obs.gone = await until(async () => (await markers(ctx)) === 0, (SECONDS + 3) * 1000, 4);
  obs.goneAfterMs = Date.now() - sent;
  await ticks(2);
  console.log(`  s1 ping on ${ctx.version}: told "${obs.told}"; ${obs.stood} marker(s); ${obs.range}; waypoint list before "${obs.waypointsBefore}", during "${obs.waypoints}"; gone ${obs.gone} after ${obs.goneAfterMs} ms`);
  console.log('  s1 ping: proved server-side: the marker entity, its flags, its transmit range read back, the waypoint list, its removal.'
    + ' Not proved: that a client draws it on the locator bar (a bot has no locator bar; the owner checks that in game).');
}

function checks(obs, o) {
  if (o.case === 'ping off') {
    return [
      c('Probe is told pinging is off', () => /Pinging gates is turned off on this server\./.test(obs.told || '')),
      c('and no marker stands', () => obs.stood === 0),
    ];
  }
  if (o.case === 'ping unsupported') {
    return [
      ...builtChecks(obs),
      c('Probe is pointed at the compass', () => /needs Minecraft 1\.21\.6 or later\. Try \/wormhole compass instead\./.test(obs.told || '')),
      c('and no marker stands', () => obs.stood === 0),
    ];
  }
  return [
    ...builtChecks(obs),
    c('Probe is told Sys is on the locator bar', () => /Sys is on your locator bar/.test(obs.told || '')),
    c('one marker stands', () => obs.stood === 1),
    c('it transmits 6.0E7, read back by the server', () => /6\.0E7|60000000(\.0)?\b/.test(obs.range || '')),
    c('it is an invisible, tagged marker that is never saved', () => ['Invisible: 1b', 'Marker: 1b', '"wormhole_ping"', 'Bukkit.persist: 0b']
      .every((x) => (obs.data || '').includes(x))),
    // -16711681 is 0xFF00FFFF: Color.AQUA, set through setWaypointColor.
    c('its locator bar icon is aqua', () => /locator_bar_icon: \{color: -16711681/.test(obs.data || '')),
    c('the server lists it as one more waypoint', () => waypointCount(obs.waypoints) === waypointCount(obs.waypointsBefore) + 1
      && waypointCount(obs.waypointsBefore) >= 0 && /Armor Stand/.test(obs.waypoints || '')),
    c(`it is gone within ${SECONDS} s and a little`, () => obs.gone === true && obs.goneAfterMs >= (SECONDS - 1) * 1000),
  ];
}

async function cleanup(ctx) {
  await ctx.server.run(`kill ${MARKER}`);
}

module.exports = { cases, needs, refuses, stage, run, checks, cleanup };
