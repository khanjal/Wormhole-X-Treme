'use strict';
// M2, the Wall Bench (design 3.4, inventory M1, M2, M5), laid out as the creative pass's mirror
// hall: niches down the cell's west side, each a wall with a banner on it, and a plaque that
// turns green or red with the plugin's own answer when `create` is tried there. The cases:
// a solid wall (made, no warning), a gap one block out (refused, naming the block), a gap two
// out (made, with the warning naming it), a banner on a post (refused), two banners side by side
// (one mirror two wide), the banner and its wall surviving a punch and a break, `remove`, and the
// per-world limit. The niches are the stage: `reset/m2` clears them with the cell.

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');
const text = require('../lib/text');
const mirrors = require('../lib/mirrors');
const { ticks } = require('../lib/probe');

const def = campus.chamber('m2');
const O = campus.OVERWORLD;
const WALL = 'minecraft:polished_deepslate';

// Walls stand in two rows (x -34 and x -24), banners on their east faces.
const NICHES = {
  solid: { x: -34, z0: 76, z1: 80, y1: 4, banner: [-33, 2, 78] },
  'gap one out': { x: -34, z0: 82, z1: 84, y1: 3, banner: [-33, 2, 83], hole: [-34, 3, 84] },
  'gap two out': { x: -24, z0: 76, z1: 80, y1: 4, banner: [-23, 2, 78], hole: [-24, 2, 80] },
  'on a post': { post: [-24, 0, 84], banner: [-24, 1, 84], standing: true },
  'two wide': { x: -16, z0: 76, z1: 81, y1: 4, banner: [-15, 2, 78], partner: [-15, 2, 79] },
};

const CASES = {
  solid: 'a wall solid two blocks round: made, with no warning',
  'gap one out': 'one block missing a block out: refused, naming it',
  'gap two out': 'one block missing two out: made, with the warning naming it',
  'on a post': 'a standing banner: refused',
  'two wide': 'two banners side by side: one mirror two wide',
  protected: 'punched and broken by an op in creative: banner and wall stay',
  remove: '`mirror remove`: an ordinary banner again, and gone from the list',
  limit: '`mirror-per-world-limit` at the overworld\'s count: refused',
};

const v = (value, why) => ({ value, label: value, why });

function nicheOf(o) {
  return ['protected', 'remove', 'limit'].includes(o.case) ? NICHES.solid : NICHES[o.case];
}

function mirrorOf(n) {
  const [x, y, z] = n.banner;
  return { dim: O, x, y, z, facing: 'east', floorY: 0 };
}

module.exports = {
  id: 'm2',
  wing: 'mirrors',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: { case: Object.entries(CASES).map(([k, why]) => v(k, why)) },
  needs: (o) => ({ config: o.case === 'limit' ? { 'mirror-per-world-limit': String(campus.ROUTES.mirrors.filter((m) => m.dim === O).length) } : {} }),
  refuses: () => null,

  async stage(ctx) {
    const s = ctx.server;
    // The whole hall, every run: the exhibition, and a wall for every case.
    for (const [name, n] of Object.entries(NICHES)) {
      if (n.post) {
        await s.run(`setblock ${n.post.join(' ')} minecraft:polished_deepslate_wall`);
        await s.run(`setblock ${n.banner.join(' ')} minecraft:white_banner[rotation=12]`);
      } else {
        await s.run(`fill ${n.x} 0 ${n.z0} ${n.x} ${n.y1} ${n.z1} ${WALL}`);
        if (n.hole) await s.run(`setblock ${n.hole.join(' ')} minecraft:air`);
        await s.run(`setblock ${n.banner.join(' ')} minecraft:white_wall_banner[facing=east]`);
        if (n.partner) await s.run(`setblock ${n.partner.join(' ')} minecraft:white_wall_banner[facing=east]`);
      }
      const [x, y, z] = n.banner;
      await s.run(`summon minecraft:text_display ${x + 0.5} ${y + 1.6} ${z + 0.5} {Tags:["${ctx.tag}","wx_m2_${name.replace(/ /g, '_')}"],billboard:"center",text:${text.displayNbt(ctx.version, [{ text: name, color: 'gray' }])}}`);
    }
  },

  async run(ctx, o) {
    const obs = ctx.observed;
    const kit = new mirrors.MirrorKit(ctx.server);
    const n = nicheOf(o);
    const name = `Wall-${o.case.replace(/ /g, '-')}`;
    obs.name = name;
    obs.made = await kit.create(name, mirrorOf(n));
    if (o.case === 'two wide') obs.again = await kit.create(name, { ...mirrorOf(n), x: n.partner[0], z: n.partner[2] });
    if (o.case === 'protected') {
      // The control: the same block, on no mirror, where the same punch reaches. It must break,
      // or a punch that never landed would read as protection.
      const control = [n.x + 3, 1, n.z1 + 1];
      await ctx.server.run(`setblock ${control.join(' ')} ${WALL}`);
      await ctx.probe.teleport({ x: n.banner[0] + 2.5, y: 0, z: n.banner[2] + 0.5, yaw: 90 });
      await ticks(10);
      const still = async (at, block) => (await ctx.server.run(`execute if block ${at.join(' ')} ${block}`)).lines.some((l) => /Test passed/.test(l));
      await ctx.probe.punch({ x: control[0], y: control[1], z: control[2] }, 2);
      await ticks(10);
      obs.controlBroke = !(await still(control, WALL));
      for (const at of [n.banner, [n.x, 3, n.banner[2] - 1]]) {
        await ctx.probe.punch({ x: at[0], y: at[1], z: at[2] }, 5);
        await ticks(10);
      }
      await ticks(20);
      obs.bannerStays = await still(n.banner, '#minecraft:banners');
      obs.wallStays = await still([n.x, 3, n.banner[2] - 1], WALL);
    }
    if (o.case === 'remove') {
      obs.removed = await kit.remove(name);
      obs.listed = (await kit.list()).names;
    }
    // The plaque says what the plugin said.
    const ok = /is this banner/.test(obs.made || '');
    const tag = `wx_m2_${(o.case in NICHES ? o.case : 'solid').replace(/ /g, '_')}`;
    const words = (obs.made || '').replace(/^:: /, '').split(' / :: ')[0].slice(0, 90);
    await ctx.server.run(`data merge entity @e[tag=${tag},limit=1] {text:${text.displayNbt(ctx.version, [{ text: words, color: ok ? 'green' : 'red' }])}}`);
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    const made = () => new RegExp(`Mirror '${obs.name}' is this banner\\.`).test(obs.made || '');
    const warned = /Its wall is solid a block out, which is enough; two blocks out hides the room's edges better from an angle, and the block at (-?\d+ -?\d+ -?\d+) is not solid\./;
    switch (o.case) {
      case 'solid':
        return [c('made', made), c('and no thin-wall warning', () => made() && !warned.test(obs.made))];
      case 'gap one out':
        return [c('refused: "A mirror needs solid wall a block out on every side of its opening, and the block at -34 3 84 is not."',
          () => /A mirror needs solid wall a block out on every side of its opening, and the block at -34 3 84 is not\./.test(obs.made || '')), c('and not made', () => !made())];
      case 'gap two out':
        return [c('made', made), c('with the warning naming the block two out: -24 2 80', () => (warned.exec(obs.made || '') || [])[1] === '-24 2 80')];
      case 'on a post':
        return [c('refused: "A mirror hangs on a wall. On a post in the open its world shows past its edges."',
          () => /A mirror hangs on a wall\. On a post in the open its world shows past its edges\./.test(obs.made || ''))];
      case 'two wide':
        return [c('made', made), c(`the second banner is the same mirror: "Mirror '${obs.name}' is already this banner. Nothing to do."`,
          () => new RegExp(`Mirror '${obs.name}' is already this banner\\. Nothing to do\\.`).test(obs.again || ''))];
      case 'protected':
        return [c('made', made), c('the same punch broke the same block on no mirror (the control)', () => obs.controlBroke),
          c('its banner survived a punch from an op in creative', () => obs.bannerStays), c('and so did its wall', () => obs.wallStays)];
      case 'remove':
        return [c('made', made), c(`"'${obs.name}' is an ordinary banner again."`, () => new RegExp(`'${obs.name}' is an ordinary banner again\\.`).test(obs.removed || '')),
          c('and the list has only the transit mirrors', () => obs.listed && !obs.listed.includes(obs.name) && obs.listed.includes('Ops'))];
      case 'limit': {
        const count = campus.ROUTES.mirrors.filter((m) => m.dim === O).length;
        return [c(`refused: "world already has ${count} mirrors, which is as many as mirror-per-world-limit allows."`,
          () => new RegExp(`world already has ${count} mirrors, which is as many as mirror-per-world-limit allows\\.`).test(obs.made || ''))];
      }
      default:
        return [];
    }
  },

  async cleanup(ctx) {
    const kit = new mirrors.MirrorKit(ctx.server);
    for (const k of Object.keys(CASES)) await kit.remove(`Wall-${k.replace(/ /g, '-')}`);
    await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
    await ctx.probe.teleport(campus.TRANSIT.home).catch(() => {});
  },

  reset: 'wx:reset/m2',
};
