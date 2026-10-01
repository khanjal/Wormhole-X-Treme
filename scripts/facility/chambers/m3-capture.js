'use strict';
// M3, the Capture Desk (design 3.4, inventory M6, M7): a mirror's view is a snapshot of its room,
// not a live one. `Desk` hangs on the cell's north wall; a gold block is put in its room after
// the capture was taken, and walking up the viewer is still sent the room without it; after
// `mirror set Desk -capture` the view has it. Also its looks: `-stamp <look>` (a shipped preset)
// and `-stamp` alone (read from the far side).

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');
const trip = require('../lib/ringtrip');
const mirrors = require('../lib/mirrors');
const { ticks } = require('../lib/probe');
const { atLeast } = require('../lib/version');

const def = campus.chamber('m3');
const O = campus.OVERWORLD;
// On the inside of the north wall (z 75), east of the door (x 22..23).
const DESK = { name: 'Desk', dim: O, x: 30, y: 1, z: 76, facing: 'south', floorY: 0 };
// In its room, eight in front: seen through the opening, flipped behind the wall.
const MARK = { x: 30, y: 0, z: 84 };

const v = (value, why) => ({ value, label: value, why });

/** Walks up to the mirror, stands a while, and returns the kinds of block its view was drawn in. */
async function look(probe, heard) {
  const seen = mirrors.watchWindow(probe, DESK);
  try {
    await probe.teleport({ x: 2.5, y: 0, z: 82.5 }); // out of range first, so the view is sent afresh
    await ticks(30);
    await mirrors.approach(probe, DESK, heard, 3);
    await trip.until(async () => seen.count > 200, 8000);
    await ticks(40);
    return { kinds: [...seen.kinds], count: seen.count };
  } finally {
    seen.stop();
  }
}

module.exports = {
  id: 'm3',
  wing: 'mirrors',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {
    case: [v('capture', 'a gold block after the capture: not seen until `-capture`'),
      v('stamp a look', '`-stamp portal`: a shipped look'), v('stamp from the room', '`-stamp` alone: read from the far side')],
  },
  refuses: () => null,

  async stage(ctx) {
    const obs = ctx.observed;
    const kit = new mirrors.MirrorKit(ctx.server);
    await kit.banner(DESK);
    obs.made = await kit.create(DESK.name, DESK);
    obs.captured = await kit.captured(DESK.name).catch((e) => e.message);
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    const kit = new mirrors.MirrorKit(ctx.server);
    const probe = ctx.probe;
    const heard = [];
    trip.listen(probe, heard);
    if (o.case === 'capture') {
      obs.before = await look(probe, heard);
      await ctx.server.run(`setblock ${MARK.x} ${MARK.y} ${MARK.z} minecraft:gold_block`);
      obs.stale = await look(probe, heard);
      obs.retake = await kit.set(DESK.name, '-capture');
      obs.recaptured = await kit.captured(DESK.name).catch((e) => e.message);
      obs.after = await look(probe, heard);
      return;
    }
    // A banner's block entity keeps them as `Patterns` before 1.20.5 and `patterns` from it.
    const key = atLeast(ctx.version, '1.20.5') ? 'patterns' : 'Patterns';
    const patterns = async () => (await ctx.server.run(`data get block ${DESK.x} ${DESK.y} ${DESK.z} ${key}`)).lines.join(' ');
    obs.patternsBefore = await patterns();
    obs.stamped = o.case === 'stamp a look' ? await kit.set(DESK.name, '-stamp', 'portal') : await kit.set(DESK.name, '-stamp');
    obs.patternsAfter = await patterns();
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    const list = [c('Desk was made', () => /Mirror 'Desk' is this banner\./.test(obs.made || '')), c('and its room captured', () => /capture: [^,]+, in memory/.test(obs.captured || ''))];
    if (o.case === 'capture') {
      const has = (r) => r && r.kinds.includes('gold_block');
      list.push(c('walking up, the view was drawn', () => obs.before && obs.before.count > 200));
      list.push(c('a gold block put in the room after the capture is not in the view (a snapshot)', () => obs.stale && obs.stale.count > 200 && !has(obs.stale)));
      list.push(c('"Capturing \'Desk\'s room again; ..."', () => /Capturing 'Desk''s room again/.test(obs.retake || '')));
      list.push(c('after -capture the view has the gold block', () => has(obs.after)));
      return list;
    }
    if (o.case === 'stamp a look') {
      list.push(c('"\'Desk\' looks like portal now."', () => /'Desk' looks like portal now\./.test(obs.stamped || '')));
    } else {
      list.push(c('"\'Desk\' now shows ..."', () => /'Desk' now shows /.test(obs.stamped || '')));
    }
    list.push(c('the banner\'s patterns changed', () => obs.patternsAfter && obs.patternsAfter !== obs.patternsBefore && /\{/.test(obs.patternsAfter)));
    return list;
  },

  async cleanup(ctx) {
    await new mirrors.MirrorKit(ctx.server).remove(DESK.name);
    await ctx.probe.teleport(campus.TRANSIT.home).catch(() => {});
  },

  reset: 'wx:reset/m3',
};
