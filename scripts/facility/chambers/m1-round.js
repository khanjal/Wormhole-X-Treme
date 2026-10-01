'use strict';
// M1, the Mirror Round (design 3.4, inventory M2-M4, M8): a mirror of its own, `Round`, on the
// cell's north wall, and the other mirrors of the network in their three worlds (the transit
// mirrors Ops and Optics, and Range and Annex at the far sites). A traveller walks up, is told
// the mirror's name above the hotbar, right-clicks through the others by name (its -start
// first) until it opens onto `to`, sees the banner give way to the far room, punches, and lands
// at `to` facing out. With someone else standing at the mirror a choice holds three seconds.
// Also: the default one-mirror-per-world limit, refused in the End (which holds only Annex).

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');
const { atLeast } = require('../lib/version');
const trip = require('../lib/ringtrip');
const mirrors = require('../lib/mirrors');
const { ticks } = require('../lib/probe');

const def = campus.chamber('m1');
const O = campus.OVERWORLD;
// On the inside of the cell's north wall (z 49), well clear of its door (x 0..1).
const ROUND = { name: 'Round', dim: O, x: -20, y: 1, z: 50, facing: 'south', floorY: 0 };
// Where the one-per-world refusal is tried: the Annex pier's other face.
const END_SPARE = { name: 'Spare', dim: campus.END, x: 1012, y: 61, z: 1018, facing: 'south', floorY: 60 };
// Player.sendBlockUpdate, which puts a banner back in front of a view, arrived in 1.20.1.
const SEND_BLOCK_UPDATE = '1.20.1';

const v = (value, why) => ({ value, label: value, why });

function network() {
  return [...campus.ROUTES.mirrors.map((m) => m.name), ROUND.name].sort();
}

/** The names a right-click walks from Round, in order: its start, then the rest by name. */
function order(start) {
  const others = network().filter((n) => n !== ROUND.name).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
  if (start && others.includes(start)) return [start, ...others.filter((n) => n !== start)];
  return others;
}

module.exports = {
  id: 'm1',
  wing: 'mirrors',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    to: [v('Annex', 'the End'), v('Optics', 'the gallery lobby'), v('Ops', 'the atrium'), v('Range', 'the nether')],
    start: [v('none', 'the round in name order'), v('Range', '`-start Range`: the first right-click opens onto the Range')],
    traveller: [v('player', 'Probe2, never opped'), v('op', 'Probe')],
    hold: [v('alone', 'clicks 0.4 s apart each move the choice on'), v('watched', 'the other probe stands by: a second click inside 3 s is held')],
    approach: [v('on', '`mirror-approach-message` on: the name above the hotbar'), v('off', 'off: no name, the clicks still answer')],
    limit: [v('baseline', 'the facility\'s limit (6)'), v('one per world', '`mirror-per-world-limit 1`: a second mirror in the End is refused')],
  },
  needs: (o) => {
    const config = {};
    if (o.approach === 'off') config['mirror-approach-message'] = 'false';
    if (o.limit === 'one per world') config['mirror-per-world-limit'] = '1';
    return { config };
  },
  refuses: (o) => {
    if (o.limit === 'one per world' && (o.start !== 'none' || o.hold !== 'alone' || o.approach !== 'on' || o.traveller !== 'player' || o.to !== 'Annex')) {
      return 'the limit row only makes a mirror; nothing travels';
    }
    return null;
  },

  async stage(ctx, o) {
    const obs = ctx.observed;
    const kit = new mirrors.MirrorKit(ctx.server);
    if (o.limit === 'one per world') {
      await kit.banner(END_SPARE);
      obs.refused = await kit.create(END_SPARE.name, END_SPARE);
      return;
    }
    await kit.banner(ROUND);
    obs.made = await kit.create(ROUND.name, ROUND);
    if (o.start !== 'none') obs.started = await kit.set(ROUND.name, '-start', o.start);
    obs.captured = await kit.captured(ROUND.name).catch((e) => e.message);
    const p2 = await ctx.facility.second();
    const traveller = o.traveller === 'player' ? p2 : ctx.probe;
    const other = traveller === p2 ? ctx.probe : p2;
    obs.traveller = traveller.name;
    if (o.hold === 'watched') await other.teleport(mirrors.frontOf(ROUND, 4));
    else await other.teleport(campus.TRANSIT.home);
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    if (o.limit === 'one per world') return;
    const p2 = await ctx.facility.second();
    const traveller = obs.traveller === p2.name ? p2 : ctx.probe;
    const heard = [];
    trip.listen(traveller, heard);
    const seen = mirrors.watchWindow(traveller, ROUND);
    try {
      obs.approach = await mirrors.approach(traveller, ROUND, heard, 3, 4000);
      // Where it stood while it listened: "nothing was named" means something only from there.
      obs.stood = traveller.distanceTo(mirrors.frontOf(ROUND, 3));
      obs.expected = order(o.start === 'none' ? null : o.start);
      const index = obs.expected.indexOf(o.to);
      obs.clicks = [];
      const gap = o.hold === 'watched' ? 3300 : 400;
      for (let i = 0; i <= index; i++) {
        const r = await mirrors.chooseUntil(traveller, ROUND, obs.expected[i], heard, { max: 1, gap: 0 });
        obs.clicks.push(r.hints[0]);
        if (o.hold === 'watched' && i === 0) {
          // A second click at once, with somebody else at the mirror: held.
          await new Promise((resolve) => { setTimeout(resolve, 400); });
          const again = await mirrors.chooseUntil(traveller, ROUND, '(held)', heard, { max: 1, gap: 0 });
          obs.held = again.hints[0];
        }
        if (i < index) await new Promise((resolve) => { setTimeout(resolve, gap); });
      }
      await trip.until(async () => seen.bannerAir && seen.openingBarrier > 0, 5000);
      obs.bannerAir = seen.bannerAir;
      obs.openingBarrier = seen.openingBarrier;
      obs.drawn = seen.count;
      // The approach line, now naming where it goes (held off for 3 s by the last click's hint).
      if (o.approach === 'on') {
        const t0 = Date.now() + 2500;
        await trip.until(async () => heard.some((h) => h.at >= t0 && h.bar && /punch to travel to/.test(h.text)), 6000);
        const l = heard.find((h) => h.at >= t0 && h.bar && /punch to travel to/.test(h.text));
        obs.pointing = l ? l.text : null;
      }
      const to = campus.ROUTES.mirrors.find((m) => m.name === o.to);
      obs.arrival = mirrors.arrivalOf(to);
      obs.landed = await mirrors.punchThrough(traveller, ROUND, obs.arrival, to.dim);
    } finally {
      seen.stop();
    }
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    if (o.limit === 'one per world') {
      return [c('refused in the plugin\'s words: "world_the_end already has 1 mirror, which is as many as mirror-per-world-limit allows."',
        () => /world_the_end already has 1 mirror, which is as many as mirror-per-world-limit allows\./.test(obs.refused || ''))];
    }
    const list = [
      c('Round was made: "Mirror \'Round\' is this banner."', () => /Mirror 'Round' is this banner\./.test(obs.made || '')),
      c('its room was captured', () => /capture: [^,]+, in memory/.test(obs.captured || '')),
    ];
    if (o.start !== 'none') list.push(c(`"A right-click on 'Round' opens onto '${o.start}' first."`, () => new RegExp(`opens onto '${o.start}' first`).test(obs.started || '')));
    if (o.approach === 'on') {
      list.push(c('walking up, it was named above the hotbar: "Round -- right-click to choose a mirror."', () => /^:: Round -- right-click to choose a mirror\.$/.test(obs.approach || '')));
      list.push(c(`once chosen, the line names it: "Round -- punch to travel to ${o.to}, ..."`, () => new RegExp(`^:: Round -- punch to travel to ${o.to}, right-click for another\\.$`).test(obs.pointing || '')));
    } else {
      // Silence is only a fact from a traveller standing at the mirror whose bar was heard: the
      // same place, where its right-clicks were answered above the hotbar.
      list.push(c('with the approach message off, nothing was named above the hotbar on walking up (standing at it, where its clicks were answered)',
        () => obs.approach === null && obs.stood < 1 && (obs.clicks || []).some((h) => /opens onto/.test(h))));
    }
    const n = network().length - 1;
    list.push(c(`the right-clicks walked the round by name: ${(obs.expected || []).slice(0, (obs.expected || []).indexOf(o.to) + 1).join(', ')}`,
      () => obs.clicks && obs.clicks.length && obs.clicks.every((h, i) => h === `:: 'Round' opens onto '${obs.expected[i]}' (${i + 1} of ${n}).`)));
    if (o.hold === 'watched') list.push(c('with someone else there, a second click inside 3 s was held: "Somebody else is at this mirror..."', () => /Somebody else is at this mirror\. Give them a moment before changing it\./.test(obs.held || '')));
    if (atLeast(ctx.version, SEND_BLOCK_UPDATE)) {
      list.push(c('the banner gave way (air) and the opening was sent as barrier', () => obs.bannerAir && obs.openingBarrier > 0));
    } else {
      list.push(c(`(skipped below ${SEND_BLOCK_UPDATE}: no Player.sendBlockUpdate, the banner stays in front of the view)`, () => true));
    }
    list.push(c('the room behind the wall was drawn to the traveller (over a hundred blocks)', () => obs.drawn > 100));
    list.push(c(`the punch put ${obs.traveller} down at ${o.to}, in its world`, () => obs.landed && obs.landed.ok));
    list.push(c(`facing out of ${o.to} (within 15°)`, () => obs.landed && obs.landed.ok && mirrors.yawOff(obs.landed.yaw, obs.arrival.yaw) <= 15));
    return list;
  },

  async cleanup(ctx) {
    const kit = new mirrors.MirrorKit(ctx.server);
    await kit.remove(ROUND.name);
    await kit.remove(END_SPARE.name);
    await ctx.server.run(`execute in ${END_SPARE.dim} run setblock ${END_SPARE.x} ${END_SPARE.y} ${END_SPARE.z} minecraft:air`);
    // A mirror carries you: two seconds before any mirror will take you again.
    await ticks(45);
    await ctx.probe.teleport(campus.TRANSIT.home).catch(() => {});
    if (ctx.facility.probe2) await ctx.facility.probe2.teleport(campus.TRANSIT.home).catch(() => {});
  },

  reset: 'wx:reset/m1',
  ROUND,
  END_SPARE,
};
