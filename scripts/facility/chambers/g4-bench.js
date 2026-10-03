'use strict';
// G4, the Build Bench (design 3.1, G4/G5 of the inventory): building a gate as a player does,
// and every preview action on the way. Probe (an op, in creative) stands where `gate build`
// puts a Standard gate's preview on the bench, and works through one action a run; what it is
// told, what `needs` lists, and what its client is shown (the preview is block displays only its
// owner, and whoever it is shared with, can see) are the checks. Probe2 is who a preview is
// shared with. Facts from the plugin's source: see lib/gatebuild.js.
//
// Cases: by hand (every block `needs` lists, the guide's "is built!", the button, complete),
// layer, chevrons, dhd, material (a group, and a role's block), iris, activate (the preview's own
// wormhole, drawn to Probe's client), share (to Probe2 and back), place, fill a gap (a preview
// over a standing gate with a block knocked out), clear; and Lab.shape's lenient [S:C] chevron
// built in the frame block, which detection takes.

const campus = require('../lib/campus');
const { GateKit } = require('../lib/gatekit');
const { GateBuilder, parseNeeds } = require('../lib/gatebuild');
const { cellLayout } = require('../lib/blueprint');
const { ticks } = require('../lib/probe');

const def = campus.chamber('g4');
const O = campus.OVERWORLD;
const NAME = 'Bench';
// In the middle of the cell, facing south (its DHD toward the gallery side).
const PLACE = { cx: -53, openingAt: -80, floorY: 0, facing: 'south' };

const CASES = {
  hand: 'every block `needs` lists, laid by hand; the guide says built; the button; complete',
  layer: '`layer`, then `layer -all`: fewer blocks shown, then all of them',
  chevrons: '`chevrons`: the chevron cells drawn (and counted) as redstone lamps',
  dhd: '`dhd`: the DHD hidden, and shown again',
  material: '`material Atlantis`, then `material -frame gold_block`: which no group can be found by',
  iris: '`iris`: the preview\'s iris shut, and open again',
  activate: '`activate`: the preview dials; Probe\'s client is shown its water, then shut down',
  share: '`share Probe2`: Probe2 is told and shown it; again, and it is gone from Probe2',
  place: '`place`: laid and offered; complete',
  'fill a gap': 'a gate with a block knocked out: a preview on its DHD, `place` fills it in',
  clear: '`clear -all`: the preview gone',
  lenient: 'Lab.shape by hand with one [S:C] chevron in diamond, which detection takes',
};

const v = (value, why) => ({ value, label: value, why });

function geomOf(shape = 'Standard') {
  return new GateKit(null).place(shape, PLACE.facing, PLACE);
}

/** How many block displays (a preview's blocks) a probe's client has in the bench. */
function displays(probe) {
  const b = def.box;
  return Object.values(probe.bot.entities).filter((e) => e.name === 'block_display'
    && e.position.x >= b.x0 - 1 && e.position.x <= b.x1 + 2 && e.position.z >= b.z0 - 1 && e.position.z <= b.z1 + 2).length;
}

/** Waits (bounded) until `test` holds; returns whether it did. */
async function until(test, ms) {
  const end = Date.now() + ms;
  for (;;) {
    if (await test()) return true;
    if (Date.now() > end) return false;
    await ticks(2);
  }
}

module.exports = {
  id: 'g4',
  wing: 'gates',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: { case: Object.entries(CASES).map(([k, why]) => v(k, why)) },
  refuses: () => null,

  async stage(ctx, o) {
    const kit = new GateKit(ctx.server);
    if (await kit.exists(NAME)) await kit.remove(NAME);
    if (o.case === 'fill a gap') {
      const geom = geomOf();
      await kit.build(NAME, geom, { dim: O, floorY: PLACE.floorY });
      // The top chevron knocked out, as a careless builder or WorldEdit might.
      const top = geom.blocks.filter((b) => b.lit).sort((a, b) => b.y - a.y)[0];
      await ctx.server.run(`setblock ${top.x} ${top.y} ${top.z} minecraft:air`);
      ctx.observed.gap = top;
    }
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    const b = new GateBuilder(ctx.server, ctx.probe);
    const probe = ctx.probe;
    if (o.case === 'hand' || o.case === 'lenient') {
      const shape = o.case === 'lenient' ? 'Lab' : 'Standard';
      const r = await b.byHand(NAME, geomOf(shape), { group: o.case === 'lenient' ? 'Diamond' : 'Standard', dim: O, floorY: PLACE.floorY, lenient: o.case === 'lenient' });
      obs.built = r;
      obs.listed = await new GateKit(ctx.server).exists(NAME);
      return;
    }
    if (o.case === 'place') {
      const r = await b.byPreview(NAME, geomOf(), { dim: O, floorY: PLACE.floorY });
      obs.built = r;
      obs.listed = await new GateKit(ctx.server).exists(NAME);
      return;
    }
    const geom = geomOf();
    b.listen();
    try {
      if (o.case === 'fill a gap') {
        // Looking at the standing gate's DHD button, `gate build` stands the preview on it.
        await probe.teleport(b.pressPoint(geom, PLACE.floorY), O);
        // At the button's plate, against the DHD block: a ray through the middle of its cell misses it.
        const k = geom.button;
        await probe.face({ x: k.x + 0.5 - geom.normal.x * 0.4, y: k.y + 0.5, z: k.z + 0.5 - geom.normal.z * 0.4 });
        await ticks(5);
        obs.build = await b.ask('/wormhole gate build Standard', { until: /Previewing|error/ });
        obs.place = await b.ask('/wormhole gate preview place', { ms: 5000, until: /Filled in|Nothing placed|error ::/ });
        const g = obs.gap;
        obs.filled = await until(async () => (await ctx.server.run(`execute if block ${g.x} ${g.y} ${g.z} minecraft:obsidian`)).lines.some((l) => /Test passed/.test(l)), 3000);
        return;
      }
      const site = b.standCells(geom);
      await b.kit.clearSite(geom, { dim: O, floorY: PLACE.floorY, extra: site, cellsOnly: true });
      obs.build = await b.preview(geom, null, { dim: O });
      await until(async () => displays(probe) > 0, 3000);
      obs.shown = displays(probe);
      const say = async (cmd, until = /::/) => b.ask(`/wormhole gate preview ${cmd}`, { until, ms: 3000 });
      if (o.case === 'layer') {
        obs.first = await say('layer', /Showing|error/);
        await ticks(10);
        obs.firstShown = displays(probe);
        obs.all = await say('layer -all', /Showing|error/);
        await ticks(10);
        obs.allShown = displays(probe);
      } else if (o.case === 'chevrons') {
        obs.before = await say('needs', /button or lever/);
        obs.toggled = await say('chevrons', /Chevrons/);
        obs.after = await say('needs', /button or lever/);
      } else if (o.case === 'dhd') {
        obs.hidden = await say('dhd', /DHD/);
        await ticks(10);
        obs.hiddenShown = displays(probe);
        obs.again = await say('dhd', /DHD/);
        await ticks(10);
        obs.againShown = displays(probe);
      } else if (o.case === 'material') {
        obs.group = await say('material Atlantis', /Materials|error/);
        obs.groupNeeds = await say('needs', /button or lever/);
        obs.role = await say('material -frame gold_block', /Materials|error/);
        obs.roleNeeds = await say('needs', /would not be found|button or lever/);
        await ticks(10);
        obs.roleNeeds += ` / ${b.heard.slice(-2).join(' / ')}`;
        obs.place = await say('place', /Nothing placed|Placed|error/);
      } else if (o.case === 'iris') {
        obs.shut = await say('iris', /Iris/);
        await ticks(10);
        obs.shutShown = displays(probe);
        obs.open = await say('iris', /Iris/);
        await ticks(10);
        obs.openShown = displays(probe);
      } else if (o.case === 'activate') {
        const { Vec3 } = require('vec3');
        const water = () => geom.opening.every((c) => { const x = probe.bot.blockAt(new Vec3(c.x, c.y, c.z)); return x && x.name === 'water'; });
        const air = () => geom.opening.every((c) => { const x = probe.bot.blockAt(new Vec3(c.x, c.y, c.z)); return x && x.name === 'air'; });
        obs.dialling = await say('activate', /Dialling|Shut down|error/);
        obs.water = await until(async () => water(), 15000);
        obs.down = await say('activate', /Dialling|Shut down|error/);
        obs.air = await until(async () => air(), 5000);
      } else if (o.case === 'share') {
        const p2 = await ctx.facility.second();
        await p2.teleport({ x: PLACE.cx + 0.5, y: 0, z: PLACE.openingAt + 8.5, yaw: 180 }, O);
        await ticks(20);
        obs.p2Before = displays(p2);
        const told = [];
        const onChat = (m) => told.push(m.toString());
        p2.bot.on('message', onChat);
        try {
          obs.shared = await say('share Probe2', /Showing it to|error/);
          await until(async () => displays(p2) > 0, 3000);
          obs.p2Shown = displays(p2);
          obs.unshared = await say('share Probe2', /Stopped showing|error/);
          await until(async () => displays(p2) === 0, 3000);
          obs.p2After = displays(p2);
        } finally {
          p2.bot.off('message', onChat);
        }
        obs.p2Told = told.join(' / ');
      } else if (o.case === 'clear') {
        obs.cleared = await say('clear -all', /Cleared|error/);
        await until(async () => displays(probe) === 0, 3000);
        obs.clearShown = displays(probe);
      }
    } finally {
      b.stop();
    }
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    if (o.case === 'hand' || o.case === 'lenient') {
      const r = obs.built || { said: {} };
      const shape = o.case === 'lenient' ? 'Lab' : 'Standard';
      const blocks = () => (r.needs || []).filter((n) => !/button|sign/.test(n.material)).reduce((a, n) => a + n.count, 0);
      const laid = () => Object.entries(r.laid || {}).filter(([k]) => !['button', 'sign'].includes(k)).reduce((a, [, n]) => a + n, 0);
      const offer = o.case === 'lenient' ? /Valid Sign Nav Stargate Design!/ : /Valid Stargate Design!/;
      return [
        c(`\`needs\` listed ${shape}'s blocks`, () => blocks() > 0),
        c('Probe laid as many blocks as it listed', () => blocks() === laid()),
        c(`its button offered it: "${offer.source.replace(/\\/g, '')}"`, () => offer.test(r.said.offer || '')),
        c('`gate complete Bench` made it, and it is listed', () => /Gate successfully constructed/.test(r.said.complete || '') && obs.listed),
        c(`the guide said "${shape} is built! Press its button to check it."`, () => new RegExp(`${shape} is built!`).test(r.said.built || '')),
      ];
    }
    if (o.case === 'place') {
      const r = obs.built || { said: {} };
      return [
        c('`gate preview place`: "Placed Standard."', () => /Placed Standard\./.test(r.said.place || '')),
        c('offered: "Valid Stargate Design!"', () => /Valid Stargate Design!/.test(r.said.offer || '')),
        c('`gate complete Bench` made it, and it is listed', () => /Gate successfully constructed/.test(r.said.complete || '') && obs.listed),
      ];
    }
    if (o.case === 'fill a gap') {
      return [
        c('`gate build` looking at the DHD: "Previewing Standard in Standard on your DHD."', () => /Previewing Standard in Standard on your DHD\./.test(obs.build || '')),
        c('`place`: "Filled in Bench\'s missing blocks. Regenerating it."', () => /Filled in Bench's missing blocks\. Regenerating it\./.test(obs.place || '')),
        c('and the knocked-out chevron is obsidian again', () => obs.filled),
      ];
    }
    const list = [
      c('the preview stood: "Previewing Standard in Standard."', () => /Previewing Standard in Standard\./.test(obs.build || '')),
      c('and Probe\'s client is shown its blocks', () => obs.shown > 0),
    ];
    const needs = (said, material) => (parseNeeds(said || '').find((n) => n.material === material) || {}).count;
    switch (o.case) {
      case 'layer':
        // Standard's blocks stand in two of its four layers, the ring's and the DHD's: those are its layers here.
        return list.concat([
          c('`layer`: "Showing layer 1 of 2."', () => /Showing layer 1 of 2\./.test(obs.first || '')),
          c('and fewer blocks shown than the whole', () => obs.firstShown > 0 && obs.firstShown < obs.shown),
          c('`layer -all`: "Showing all 2 layers.", and all of them shown', () => /Showing all 2 layers\./.test(obs.all || '') && obs.allShown === obs.shown),
        ]);
      case 'chevrons':
        return list.concat([
          c('`needs` first: 18 obsidian', () => needs(obs.before, 'obsidian') === 18),
          c('`chevrons`: "Chevrons shown."', () => /Chevrons shown\./.test(obs.toggled || '')),
          c('then 10 obsidian and 8 "redstone_lamp or obsidian"', () => needs(obs.after, 'obsidian') === 10 && needs(obs.after, 'redstone_lamp or obsidian') === 8),
        ]);
      case 'dhd':
        return list.concat([
          c('`dhd`: "DHD hidden."', () => /DHD hidden\./.test(obs.hidden || '')),
          c('and its blocks are not shown', () => obs.hiddenShown > 0 && obs.hiddenShown < obs.shown),
          c('`dhd` again: "DHD shown.", all of them back', () => /DHD shown\./.test(obs.again || '') && obs.againShown === obs.shown),
        ]);
      case 'material':
        return list.concat([
          c('`material Atlantis`: "Materials changed."', () => /Materials changed\./.test(obs.group || '')),
          c('and `needs` lists 18 lapis_block', () => needs(obs.groupNeeds, 'lapis_block') === 18),
          c('`material -frame gold_block`: 18 gold_block', () => /Materials changed\./.test(obs.role || '') && needs(obs.roleNeeds, 'gold_block') === 18),
          c('with "No material group uses gold_block for a frame, so this gate would not be found."', () => /No material group uses gold_block for a frame, so this gate would not be found\./.test(obs.roleNeeds || '')),
          c('and `place` refused: "... would not be found. Nothing placed."', () => /No material group uses that frame block, so the gate would not be found\. Nothing placed\./.test(obs.place || '')),
        ]);
      case 'iris':
        return list.concat([
          c('`iris`: "Iris closed."', () => /Iris closed\./.test(obs.shut || '')),
          c('and the opening shown shut (more blocks shown)', () => obs.shutShown > obs.shown),
          c('`iris` again: "Iris open.", the opening shown open', () => /Iris open\./.test(obs.open || '') && obs.openShown === obs.shown),
        ]);
      case 'activate':
        return list.concat([
          c('`activate`: "Dialling."', () => /Dialling\./.test(obs.dialling || '')),
          c('Probe\'s client is shown water in the opening', () => obs.water),
          c('`activate` again: "Shut down.", and the opening shown empty', () => /Shut down\./.test(obs.down || '') && obs.air),
        ]);
      case 'share':
        return list.concat([
          c('Probe2 is shown nothing of it at first', () => obs.p2Before === 0),
          c('`share Probe2`: "Showing it to Probe2."', () => /Showing it to Probe2\./.test(obs.shared || '')),
          c('Probe2 is told "Probe is showing you a gate preview." and shown it', () => /Probe is showing you a gate preview\./.test(obs.p2Told || '') && obs.p2Shown > 0),
          c('`share Probe2` again: "Stopped showing it to Probe2.", and it is gone for Probe2', () => /Stopped showing it to Probe2\./.test(obs.unshared || '') && obs.p2After === 0),
        ]);
      case 'clear':
        return list.concat([c('`clear -all`: "Cleared 1 preview."', () => /Cleared 1 preview\./.test(obs.cleared || '')), c('and its blocks are gone', () => obs.clearShown === 0)]);
      default:
        return list;
    }
  },

  async cleanup(ctx) {
    const kit = new GateKit(ctx.server);
    await new GateBuilder(ctx.server, ctx.probe).clearPreviews().catch(() => {});
    if (await kit.exists(NAME)) await kit.remove(NAME);
    await ctx.server.run(`clear ${ctx.probe.name}`);
    if (ctx.facility.probe2) await ctx.facility.probe2.teleport(campus.TRANSIT.home).catch(() => {});
  },

  reset: 'wx:reset/g4',
  PLACE,
};
