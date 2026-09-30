'use strict';
// B2, the Dispatch Desk (design 3.3): moving other people, and who may. The console form
// `beam admin send` (to a public destination, to a player, to coordinates in another world, to
// nobody), `admin goto` and `admin set` as an op and as Probe2 (never opped: refused), a
// player's own place against someone else's, a gate by `/wormhole go`, and the per-player
// cooldown (Probe2 waits it out; an op skips it). Destinations are spots on the hall floor by
// the desk; nothing is left behind.

const campus = require('../lib/campus');
const { deskLayout } = require('../lib/blueprint');
const { BeamKit, follow, flat } = require('../lib/beams');
const { ticks } = require('../lib/probe');

const def = campus.chamber('b2');
const O = campus.OVERWORLD;
// On the hall floor south of the Pad Array, west of the desk.
const DOCK = { x: -70, y: 0, z: 16, yaw: 90 };
const DEN = { x: -80, y: 0, z: 16, yaw: 90 };
const STAND = { x: -59.5, y: 0, z: 24.5, yaw: 90 }; // the desk's seat: where travellers start
const NETHER_SPOT = { x: -12, y: 64, z: -8 };
const COOLDOWN = 8;
// When Probe2's last cooldown began: kept here, since a cleanup runs with a fresh context.
let cooldownFrom = 0;

const CASES = {
  'send to public': 'console `beam admin send Probe2 Dock`',
  'send to player': 'console `beam admin send Probe2 Probe`: lands where Probe stands',
  'send to coordinates': 'console `beam admin send Probe2 <x y z> world_nether`: another world',
  'send to nobody': 'console `beam admin send Nobody Dock`: refused, in the plugin\'s words',
  'goto as op': 'Probe `beam admin goto Dock`',
  'goto as player': 'Probe2 `beam admin goto Dock`: refused',
  'set as player': 'Probe2 `beam admin set`: refused, and nothing is added',
  'own place': 'Probe2 saves Den, walks back, `beam to Den`',
  'another\'s place': 'Probe saves Den; Probe2 `beam to Den`: refused',
  'go to a gate as op': 'Probe `/wormhole go Relay`: beamed to the gate',
  'go to a gate as player': 'Probe2 `/wormhole go Relay`: a gate is not a player\'s to go to',
  cooldown: `\`beam-use-cooldown-enabled\`, ${COOLDOWN} s: Probe2's second beam waits`,
  'op skips cooldown': 'the same, as Probe: both beams go',
};

const centre = (p) => ({ x: p.x + 0.5, y: p.y, z: p.z + 0.5 });

module.exports = {
  id: 'b2',
  wing: 'beams',
  title: def.title,
  cell: null,
  seat: deskLayout(def).seat,
  options: {
    action: Object.entries(CASES).map(([value, why]) => ({ value, label: value, why })),
  },
  needs: (o) => ({
    config: ['cooldown', 'op skips cooldown'].includes(o.action)
      ? { 'beam-use-cooldown-enabled': 'true', 'beam-use-cooldown-seconds': String(COOLDOWN) } : {},
  }),
  refuses: () => null,

  async stage(ctx, o) {
    const obs = ctx.observed;
    const kit = new BeamKit(ctx.server);
    const p2 = await ctx.facility.second();
    obs.dock = await kit.save(ctx.probe, 'public', 'Dock', DOCK);
    if (o.action === 'own place') obs.den = await kit.save(p2, 'place', 'Den', DEN);
    if (o.action === 'another\'s place') obs.den = await kit.save(ctx.probe, 'place', 'Den', DEN);
    if (o.action === 'send to player') await ctx.probe.teleport({ ...centre(DEN), yaw: 0 });
    else await ctx.probe.teleport({ x: STAND.x, y: 0, z: STAND.z + 2, yaw: 180 });
    await p2.teleport(STAND);
    await ticks(10);
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    const kit = new BeamKit(ctx.server);
    const p2 = await ctx.facility.second();
    const probe = ctx.probe;
    const a = o.action;
    const say = (who, cmd) => async () => who.bot.chat(cmd);
    if (a === 'send to public') {
      obs.beam = await follow(p2, async () => { obs.console = await kit.send('Probe2', 'Dock'); }, { name: 'Dock' });
    } else if (a === 'send to player') {
      obs.beam = await follow(p2, async () => { obs.console = await kit.send('Probe2', 'Probe'); }, { name: 'Probe' });
    } else if (a === 'send to coordinates') {
      const n = NETHER_SPOT;
      obs.beam = await follow(p2, async () => { obs.console = await kit.send('Probe2', `${n.x} ${n.y} ${n.z} world_nether`); }, { name: '[^.]+' });
    } else if (a === 'send to nobody') {
      obs.console = await kit.send('Nobody', 'Dock');
    } else if (a === 'goto as op') {
      obs.beam = await follow(probe, say(probe, '/wormhole beam admin goto Dock'), { name: 'Dock' });
    } else if (a === 'goto as player') {
      obs.beam = await follow(p2, say(p2, '/wormhole beam admin goto Dock'), { name: 'Dock', refusal: /You lack the permissions to do this\./ });
    } else if (a === 'set as player') {
      obs.said = await kit.ask(p2, '/wormhole beam admin set Dock2', { until: /permissions|set to/ });
      obs.listed = await kit.list(p2);
    } else if (a === 'own place') {
      obs.places = await kit.places(p2);
      obs.beam = await follow(p2, say(p2, '/wormhole beam to Den'), { name: 'Den' });
    } else if (a === 'another\'s place') {
      obs.beam = await follow(p2, say(p2, '/wormhole beam to Den'), { name: 'Den', refusal: /No destination named "Den" among your places or the public list\./ });
    } else if (a === 'go to a gate as op') {
      obs.beam = await follow(probe, say(probe, '/wormhole go Relay'), { name: 'Relay' });
    } else if (a === 'go to a gate as player') {
      obs.beam = await follow(p2, say(p2, '/wormhole go Relay'), { name: 'Relay', refusal: /No gate or beam destination named: Relay/ });
    } else if (a === 'cooldown' || a === 'op skips cooldown') {
      const who = a === 'cooldown' ? p2 : probe;
      obs.first = await follow(who, say(who, '/wormhole beam to Dock'), { name: 'Dock' });
      obs.cooldownFrom = Date.now();
      if (who === p2) cooldownFrom = obs.cooldownFrom;
      await who.teleport(STAND);
      obs.second = await follow(who, say(who, '/wormhole beam to Dock'), { name: 'Dock', refusal: /You must wait longer before beaming again\./ });
      const wait = (obs.second.told.map((t) => /Current Wait \(in seconds\): (\d+)/.exec(t)).find(Boolean) || [])[1];
      obs.wait = wait === undefined ? null : Number(wait);
      if (obs.second.refused) {
        await ticks(Math.ceil(((obs.wait || COOLDOWN) + 1) * 20));
        await who.teleport(STAND);
        obs.third = await follow(who, say(who, '/wormhole beam to Dock'), { name: 'Dock' });
        obs.cooldownFrom = Date.now();
        cooldownFrom = obs.cooldownFrom;
      }
    }
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    const a = o.action;
    const b = (x = obs.beam) => x || {};
    const at = (x, p, r = 1) => x.at && flat(x.at, p) < r && Math.abs(x.at.y - p.y) < 0.6;
    const stayed = (x) => c('and stayed where it was', () => b(x).at && flat(b(x).at, STAND) < 1);
    const list = [c('Dock was saved as a public destination', () => /Public beam destination "Dock" set to your current location\./.test(obs.dock || ''))];
    if (a === 'send to public') {
      list.push(c('the console was told "Beaming Probe2 to Dock."', () => /Beaming Probe2 to Dock\./.test(obs.console || '')));
      list.push(c('Probe2 was beamed to Dock, facing 90° as saved', () => b().done && at(b(), centre(DOCK)) && Math.abs(b().yaw - 90) <= 5));
    } else if (a === 'send to player') {
      list.push(c('the console was told "Beaming Probe2 to Probe."', () => /Beaming Probe2 to Probe\./.test(obs.console || '')));
      list.push(c('Probe2 landed where Probe stands', () => b().done && at(b(), centre(DEN), 1.5)));
    } else if (a === 'send to coordinates') {
      list.push(c('the console was told "Beaming Probe2 to ..."', () => /Beaming Probe2 to /.test(obs.console || '')));
      list.push(c('Probe2 landed at those coordinates in the nether', () => b().done && b().dim === campus.NETHER && at(b(), { x: NETHER_SPOT.x + 0.5, y: NETHER_SPOT.y, z: NETHER_SPOT.z + 0.5 })));
    } else if (a === 'send to nobody') {
      list.push(c('the console was told: No online player named "Nobody".', () => /No online player named "Nobody"\./.test(obs.console || '')));
    } else if (a === 'goto as op') {
      list.push(c('Probe was beamed to Dock', () => b().done && at(b(), centre(DOCK))));
    } else if (a === 'goto as player') {
      list.push(c('Probe2 was told "You lack the permissions to do this."', () => b().refused));
      list.push(stayed());
    } else if (a === 'set as player') {
      list.push(c('Probe2 was told "You lack the permissions to do this."', () => /You lack the permissions to do this\./.test(obs.said || '')));
      list.push(c('and Dock2 is not on the public list', () => /Beam destinations:/.test(obs.listed || '') && !/Dock2/.test(obs.listed || '')));
    } else if (a === 'own place') {
      list.push(c('Den was saved as Probe2\'s place', () => /Place "Den" set to your current location\./.test(obs.den || '')));
      list.push(c('Probe2\'s places list Den', () => /Den/.test(obs.places || '')));
      list.push(c('Probe2 was beamed to Den', () => b().done && at(b(), centre(DEN))));
    } else if (a === 'another\'s place') {
      list.push(c('Den was saved as Probe\'s place', () => /Place "Den" set to your current location\./.test(obs.den || '')));
      list.push(c('Probe2 was refused: No destination named "Den" among your places or the public list.', () => b().refused));
      list.push(stayed());
    } else if (a === 'go to a gate as op') {
      const relay = require('./relay').farGates().Relay.geom.arrival;
      list.push(c('Probe was beamed to the Relay gate\'s arrival', () => b().done && at(b(), relay, 1.5)));
    } else if (a === 'go to a gate as player') {
      list.push(c('Probe2 was told "No gate or beam destination named: Relay"', () => b().refused));
      list.push(stayed());
    } else if (a === 'cooldown') {
      list.push(c('the first beam went', () => b(obs.first).done && at(b(obs.first), centre(DOCK))));
      list.push(c('the second was refused: "You must wait longer before beaming again."', () => b(obs.second).refused));
      list.push(c(`with "Current Wait (in seconds): N", N at most ${COOLDOWN}`, () => obs.wait !== null && obs.wait > 0 && obs.wait <= COOLDOWN));
      list.push(stayed(obs.second));
      list.push(c('once the wait was over, it went', () => b(obs.third).done && at(b(obs.third), centre(DOCK))));
    } else if (a === 'op skips cooldown') {
      list.push(c('the first beam went', () => b(obs.first).done && at(b(obs.first), centre(DOCK))));
      list.push(c('and the second, straight after', () => b(obs.second).done && !b(obs.second).refused && at(b(obs.second), centre(DOCK))));
    }
    return list;
  },

  async cleanup(ctx) {
    const kit = new BeamKit(ctx.server);
    const p2 = ctx.facility.probe2;
    await kit.drop(ctx.probe, 'public', 'Dock');
    await kit.drop(ctx.probe, 'public', 'Dock2');
    await kit.drop(ctx.probe, 'place', 'Den');
    if (p2) await kit.drop(p2, 'place', 'Den');
    // A cooldown left running would refuse the next run's first beam.
    const left = cooldownFrom + (COOLDOWN + 1) * 1000 - Date.now();
    if (left > 0) await ticks(Math.ceil(left / 50));
    await ctx.probe.teleport(campus.TRANSIT.home).catch(() => {});
    if (p2) await p2.teleport(campus.TRANSIT.home).catch(() => {});
  },
  SPOTS: { DOCK, DEN, STAND, NETHER_SPOT },
};
