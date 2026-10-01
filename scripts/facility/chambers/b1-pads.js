'use strict';
// B1, the Pad Array (design 3.3): named beam destinations on pads, each saved facing its letter,
// and a traveller beamed to one from the start pad. What the plugin must do (lib/beams.js): put
// the traveller down on the pad's centre facing the saved way, find it a safe spot when the pad
// is a trap (no floor) or blocked, hide it from a watcher while it travels, carry its mount or
// its pet with it, and keep to the timings the settings give.

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');
const { BeamKit, follow, beamTicks, flat, yawOff } = require('../lib/beams');
const trip = require('../lib/ringtrip');
const { ticks } = require('../lib/probe');

const def = campus.chamber('b1');
const O = campus.OVERWORLD;
const START = { x: -60, y: 0, z: -8, yaw: 90 };
// Where the watcher stands: in sight of the start pad and of every pad in the cell.
const WATCH = { x: -59.5, y: 0, z: 2.5, yaw: 90 };

/** The pads: block coords of the centre, the yaw saved, the landing's height if not the pad's. */
const PADS = {
  N: { x: -68, y: 0, z: -20, yaw: 180, dim: O },
  E: { x: -78, y: 0, z: -20, yaw: 270, dim: O },
  S: { x: -88, y: 0, z: -20, yaw: 0, dim: O },
  W: { x: -98, y: 0, z: -20, yaw: 90, dim: O },
  // No floor under the pad: nowhere to stand at the pad's height or 1..3 up, so 1 down.
  Trap: { x: -68, y: 0, z: 2, yaw: 90, dim: O, landY: -1 },
  // A block where the feet go: the first standable spot is 1 up.
  Blocked: { x: -78, y: 0, z: 2, yaw: 90, dim: O, landY: 1 },
  Far: { x: -12, y: 64, z: 12, yaw: 90, dim: campus.NETHER },
  End: { x: 1012, y: 60, z: 1012, yaw: 90, dim: campus.END },
};

const TIMING = {
  default: {},
  long: { 'beam-descend-ticks': '60' },
  'late teleport': { 'beam-rise-ticks': '60', 'beam-teleport-at-step': '50' },
  clamped: { 'beam-teleport-at-step': '99' },
  'clamped vanish': { 'beam-vanish-at-step': '99' },
};

const v = (value, why) => ({ value, label: value, why });

function padName(o) {
  return `Pad-${o.destination}`;
}

module.exports = {
  id: 'b1',
  wing: 'beams',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    destination: [v('N', 'saved facing north'), v('E', 'facing east'), v('S', 'facing south'), v('W', 'facing west'),
      v('Trap', 'no floor under it: the safe spot is one down'), v('Blocked', 'a block where the feet go: one up'),
      v('Far', 'in the Range (the nether)'), v('End', 'in the Annex (the End)')],
    kind: [v('public', '`beam admin set` by an op, `beam to` by anyone'), v('own place', 'the traveller\'s own `beam place`'),
      v('another\'s place', 'Probe\'s place, asked for by Probe2: refused'), v('go', '`/wormhole go <name>` to a public one')],
    traveller: [v('player', 'Probe2, never opped, watched by Probe'), v('op', 'Probe, watched by Probe2'),
      v('horse', 'Probe riding a saddled horse'), v('wolf', 'Probe2 with its tame wolf standing by')],
    timing: [v('default', '52 ticks from "Beaming" to "Beamed"'), v('long', '`beam-descend-ticks 60`: 92 ticks'),
      v('late teleport', '`beam-rise-ticks 60`, `beam-teleport-at-step 50`: 90 ticks'),
      v('clamped', '`beam-teleport-at-step 99`: clamped inside the rise, 57 ticks'),
      v('clamped vanish', '`beam-vanish-at-step 99`: clamped inside the envelope; still lands, not frozen')],
  },
  needs: (o) => ({ config: TIMING[o.timing] || {} }),
  refuses: (o) => {
    if (o.kind === 'another\'s place' && o.traveller !== 'player') return 'another\'s place is asked for by Probe2 (the player)';
    if (['horse', 'wolf'].includes(o.traveller) && ['Far', 'End'].includes(o.destination)) return 'animals go along only within this world here';
    return null;
  },

  async stage(ctx, o) {
    const obs = ctx.observed;
    const kit = new BeamKit(ctx.server);
    const pad = PADS[o.destination];
    const name = padName(o);
    const srv = ctx.server;
    obs.name = name;
    const p2 = await ctx.facility.second();
    // The pad, marked on the cell floor; a trap has no floor under its centre, a blocked pad a
    // block where the feet go.
    if (pad.dim === O) {
      await srv.run(`fill ${pad.x - 1} -1 ${pad.z - 1} ${pad.x + 1} -1 ${pad.z + 1} minecraft:light_blue_concrete`);
      if (o.destination === 'Trap') await srv.run(`setblock ${pad.x} -1 ${pad.z} minecraft:air`);
      if (o.destination === 'Blocked') await srv.run(`setblock ${pad.x} 0 ${pad.z} minecraft:white_concrete`);
    }
    if (pad.dim !== O) await srv.waitLoaded(pad.dim, [[pad.x, pad.y, pad.z]], 30000);
    const traveller = ['player', 'wolf'].includes(o.traveller) ? p2 : ctx.probe;
    obs.travellerName = traveller.name;
    // Who saves it: an op for a public one, the traveller for its own place, Probe for another's.
    // A trap is saved standing on it before its floor goes (a player cannot stand in the air).
    const saver = o.kind === 'own place' ? traveller : ctx.probe;
    const kind = ['public', 'go'].includes(o.kind) ? 'public' : 'place';
    if (o.destination === 'Trap') await srv.run(`setblock ${pad.x} -1 ${pad.z} minecraft:light_blue_concrete`);
    if (o.destination === 'Blocked') await srv.run(`setblock ${pad.x} 0 ${pad.z} minecraft:air`);
    obs.saved = await kit.save(saver, kind, name, pad, pad.dim);
    obs.saver = saver.name;
    obs.savedKind = kind;
    if (o.destination === 'Trap') await srv.run(`setblock ${pad.x} -1 ${pad.z} minecraft:air`);
    if (o.destination === 'Blocked') await srv.run(`setblock ${pad.x} 0 ${pad.z} minecraft:white_concrete`);
    obs.listed = kind === 'public' ? await kit.list(traveller) : await kit.places(saver);

    // Everyone to the start: the traveller on the start pad, the watcher to one side.
    const start = { x: START.x + 0.5, y: 0, z: START.z + 0.5, yaw: START.yaw };
    if (o.traveller === 'horse') {
      const at = { ...start };
      await ctx.menagerie.animal('horse', at, ctx.tag, { saddled: true });
      await ctx.probe.teleport({ x: at.x, y: 0, z: at.z + 1.6, yaw: 180 });
      await ctx.probe.mount(ctx.tag, 5000, 3);
      obs.riding = Boolean(ctx.probe.bot.vehicle);
    } else {
      await traveller.teleport(start);
    }
    if (o.traveller === 'wolf') {
      await ctx.menagerie.animal('wolf', { x: start.x, y: 0, z: start.z + 2 }, ctx.tag, { owner: p2.name, sitting: false });
    }
    obs.watcher = o.traveller === 'player' || o.traveller === 'wolf' ? ctx.probe.name : o.traveller === 'op' ? p2.name : null;
    if (obs.watcher) await (obs.watcher === p2.name ? p2 : ctx.probe).teleport(WATCH);
    await ticks(10);
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    const p2 = await ctx.facility.second();
    const traveller = obs.travellerName === p2.name ? p2 : ctx.probe;
    const watcher = obs.watcher ? (obs.watcher === p2.name ? p2 : ctx.probe) : null;
    const name = obs.name;
    const command = o.kind === 'go' ? `/wormhole go ${name}` : `/wormhole beam to ${name}`;
    const refusal = o.kind === 'another\'s place' ? /No destination named "[^"]+" among your places or the public list\./ : null;
    obs.beam = await follow(traveller, async () => traveller.bot.chat(command), { name, watcher, refusal });
    if (o.traveller === 'horse') {
      const pad = PADS[o.destination];
      obs.horseCame = await trip.near(ctx, { x: pad.x + 0.5, y: pad.y, z: pad.z + 0.5 }, `tag=${ctx.tag},tag=wx_kind_horse`, 2.5, pad.dim);
      obs.stillRiding = Boolean(ctx.probe.bot.vehicle);
    }
    if (o.traveller === 'wolf') {
      const pad = PADS[o.destination];
      // A wolf with its AI on is put beside its owner a second after the landing, then potters
      // about: seen 1 to 6 blocks from the pad in seven runs, so within 8 is "came along" (left
      // behind, it would be at the start, 23 blocks away).
      obs.wolfCame = await trip.until(() => trip.near(ctx, { x: pad.x + 0.5, y: pad.y, z: pad.z + 0.5 }, `tag=${ctx.tag},tag=wx_kind_wolf`, 8, pad.dim), 4000);
      const at = await ctx.server.run(`data get entity @e[tag=${ctx.tag},tag=wx_kind_wolf,limit=1] Pos`);
      obs.wolfAt = at.lines.join(' ').replace(/.*data: /, '');
    }
    if (o.timing === 'clamped vanish' && obs.beam.done) {
      // Not left frozen: two blocks' walk.
      const from = { ...traveller.position };
      await traveller.walkTo({ x: from.x - 2, z: from.z }, { within: 0.4, ms: 4000 }).catch(() => {});
      obs.walked = flat(traveller.position, from);
    }
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    const pad = PADS[o.destination];
    const name = padName(o);
    const b = () => obs.beam || {};
    const list = [c(`${name} was saved: "${obs.savedKind === 'public' ? 'Public beam destination' : 'Place'} "${name}" set to your current location."`,
      () => new RegExp(`"${name}" set to your current location\\.`).test(obs.saved || ''))];
    if (o.kind === 'another\'s place') {
      list.push(c(`Probe2 was refused: "No destination named "${name}" among your places or the public list."`, () => b().refused));
      list.push(c('and stayed on the start pad', () => b().at && flat(b().at, { x: START.x + 0.5, z: START.z + 0.5 }) < 1));
      return list;
    }
    list.push(c(obs.savedKind === 'public' ? `${name} is on the public list` : `${name} is in its owner's places`,
      () => (obs.listed || '').includes(name)));
    list.push(c(`told "Beaming to ${name}..." then "Beamed to ${name}."`, () => b().begun && b().done));
    const landY = pad.landY !== undefined ? pad.landY : pad.y;
    // A rider sits on its mount: the horse's own arrival is checked below.
    const seat = o.traveller === 'horse' ? 1.5 : 0.3;
    list.push(c(`landed on the pad's centre${pad.landY !== undefined ? ` at y ${landY}` : ''}`, () => b().at && b().dim === pad.dim
      && flat(b().at, { x: pad.x + 0.5, z: pad.z + 0.5 }) < 1 && b().at.y - landY > -0.3 && b().at.y - landY < seat));
    if (o.traveller !== 'horse') list.push(c(`facing ${pad.yaw}° as saved (within 5°)`, () => b().at && yawOff(b().yaw, pad.yaw) <= 5));
    // Across worlds the watcher loses sight of anyone who leaves, hidden or not: no check there.
    if (obs.watcher && pad.dim === O) {
      list.push(c(`${obs.watcher} lost sight of ${obs.travellerName} while it travelled, and saw it again`,
        () => b().gone && b().shown && b().shown > b().gone));
    }
    // Timed by the traveller's own chat, on localhost: 52 ticks came in at 2591-2598 ms.
    const expect = beamTicks(TIMING[o.timing]) * 50;
    list.push(c(`the beam took ${beamTicks(TIMING[o.timing])} ticks (${(expect / 1000).toFixed(2)} s, within 0.25 s)`, () => b().ms && Math.abs(b().ms - expect) <= 250));
    if (o.timing === 'clamped vanish') list.push(c('and it could walk away afterwards (not left frozen)', () => obs.walked > 1.5));
    if (o.traveller === 'horse') {
      list.push(c('Probe was riding the horse', () => obs.riding));
      list.push(c('the same horse came along', () => obs.horseCame));
      list.push(c('and Probe is still riding it', () => obs.stillRiding));
    }
    if (o.traveller === 'wolf') list.push(c('its wolf came along', () => obs.wolfCame));
    return list;
  },

  async cleanup(ctx) {
    const obs = ctx.observed;
    const kit = new BeamKit(ctx.server);
    await ctx.probe.dismount().catch(() => {});
    const p2 = ctx.facility.probe2;
    // Probe (an op) may send commands as fast as it likes; Probe2 is kicked for spam past about
    // ten in a burst, so it lists its places once and drops only the pads among them. (Cleanup
    // runs with a fresh context, before the next run or at a reset: nothing the run recorded.)
    for (const d of Object.keys(PADS)) {
      const name = `Pad-${d}`;
      await kit.drop(ctx.probe, 'public', name);
      await kit.drop(ctx.probe, 'place', name);
    }
    if (p2 && !p2.gone) {
      // Up to the next message: what Probe2 was told is joined with " / ", and a name has no "/".
      const listed = (/Your places: ([^/]*?)(?: \/ |$)/.exec(await kit.places(p2)) || [null, ''])[1];
      for (const name of listed.split(', ').filter((n) => /^Pad-/.test(n))) await kit.drop(p2, 'place', name);
    }
    await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
    await ctx.probe.teleport(campus.TRANSIT.home).catch(() => {});
    if (p2) await p2.teleport(campus.TRANSIT.home).catch(() => {});
    obs.cleaned = true;
  },

  reset: 'wx:reset/b1',
  PADS,
};
