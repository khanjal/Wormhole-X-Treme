'use strict';
// Far scenery (issue #526, a research spike, off unless WX_FAR_SCENERY is set): a viewing room
// with a Standard-gate-sized opening (3 by 5) looking south over a procedural landscape, whose
// far part is either a TextDisplay panorama at 120, a wall of coloured blocks at 64 or 96, or,
// as the control, real blocks all the way to 160. A person stands at the seat and judges
// "distance or painting" walking up to the opening, past it and sideways. Data: lib/farscenery.js.

const campus = require('../lib/campus');
const { deskLayout } = require('../lib/blueprint');
const text = require('../lib/text');
const fs = require('../lib/farscenery');

const def = campus.chamber('far');
const { SITE, NEAR } = fs;
// The panorama stops at 120: a display is sent only within entity-tracking-range.display (128 on
// a default spigot.yml), and the facility has no hook to raise it.
const PANORAMA_AT = 120;
const WALLS = [64, 96];
const ROOM = { x0: SITE.x - 10, x1: SITE.x + 10, z0: SITE.z - 7, z1: SITE.z - 2, y0: 0, y1: 6 };
const TOWER_NEAR = fs.TOWERS[0];
const TOWER_BEHIND = fs.TOWERS[1];

const v = (value, why) => ({ value, label: value, why });
const at = (p) => `${p.x} ${p.y} ${p.z}`;
const passed = (r) => r.lines.some((l) => /Test passed/.test(l));

// A block already as asked is refused by setblock and fill; for a rebuild that is not an error.
const UNCHANGED = /Could not set the block|No blocks were filled/;

async function runAll(ctx, cmds) {
  const errors = [];
  for (const c of cmds) {
    const r = await ctx.server.run(c);
    const real = r.errors.filter((e) => !UNCHANGED.test(e));
    if (real.length) errors.push(`${c.slice(0, 80)}: ${real.join(' ')}`);
  }
  return errors;
}

function fillCmd(f) {
  const a = fs.world(f.x0, f.y0, f.d0);
  const b = fs.world(f.x1, f.y1, f.d1);
  return `fill ${at(a)} ${at(b)} ${f.block}`;
}

function wallOf(o) {
  const m = /^wall (\d+)$/.exec(o.scenery);
  return m ? Number(m[1]) : null;
}

/** How far real blocks are built for a variant. */
function builtTo(o) {
  if (o.scenery === 'panorama 120') return PANORAMA_AT;
  return wallOf(o) || NEAR.depth + 1;
}

function panoramaCommand(version, tag, gaps) {
  const p = fs.panorama(PANORAMA_AT);
  const pos = fs.world(0, 0, PANORAMA_AT);
  const nbt = {
    billboard: '"fixed"',
    Rotation: '[180f,0f]',
    line_width: String(p.lineWidth),
    background: String(gaps === 'transparent' ? 0 : fs.argb(p.mean)),
    view_range: '3.0f',
    brightness: '{sky:15,block:15}',
    transformation: `{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[${p.scale.x.toFixed(4)}f,${p.scale.y.toFixed(4)}f,1f]}`,
  };
  return { p, cmd: text.summonDisplay(version, { x: pos.x + 0.5, y: 0, z: pos.z, tags: [tag, 'wx_far_pano'], spec: p.spec, nbt }) };
}

module.exports = {
  id: 'far',
  wing: def.wing,
  title: def.title,
  cell: null,
  seat: deskLayout(def).seat,
  options: {
    scenery: [
      v('panorama 120', 'near land real to 120, then one TextDisplay painted with the land beyond it'),
      v('wall 64', 'near land real to 64, then a wall of coloured blocks painted with the land beyond it'),
      v('wall 96', 'the same wall at 96'),
      v('off', 'the control: every block real to 160, nothing painted'),
    ],
    gaps: [
      v('filled', "the panorama's background is its mean colour, hiding the gaps between glyphs"),
      v('transparent', 'background 0: the world behind shows through the gaps'),
    ],
  },
  refuses: () => null,

  async fixture(ctx) {
    const r = ROOM;
    const hole = { x0: SITE.x - 1, x1: SITE.x + 1, z: SITE.z - 1 };
    const errors = await runAll(ctx, [
      `fill ${r.x0 - 1} ${r.y0 - 1} ${r.z0 - 1} ${r.x1 + 1} ${r.y1 + 1} ${r.z1 + 1} minecraft:white_concrete outline`,
      `fill ${hole.x0 - 1} ${r.y0 - 1} ${hole.z} ${hole.x1 + 1} ${r.y0 + 5} ${hole.z} minecraft:polished_deepslate`,
      `fill ${hole.x0} ${r.y0} ${hole.z} ${hole.x1} ${r.y0 + 4} ${hole.z} minecraft:air`,
      ...[r.x0 + 2, SITE.x, r.x1 - 2].map((x) => `setblock ${x} ${r.y1} ${r.z0 + 2} minecraft:light[level=15]`),
    ]);
    if (errors.length) throw new Error(errors[0]);
    return 'the viewing room';
  },

  async stage(ctx, o) {
    const obs = ctx.observed;
    obs.errors = [];
    await ctx.step('building the near land');
    obs.errors.push(...await runAll(ctx, fs.terrainFills(builtTo(o)).map(fillCmd)));
    const w = wallOf(o);
    if (w) {
      await ctx.step(`painting the wall at ${w}`);
      obs.wall = fs.wall(w);
      obs.errors.push(...await runAll(ctx, fs.wallFills(obs.wall).map(fillCmd)));
    }
    if (o.scenery === 'panorama 120') {
      await ctx.step('hanging the panorama');
      const { p, cmd } = panoramaCommand(ctx.version, ctx.tag, o.gaps);
      obs.glyphs = p.spec.filter((s) => typeof s !== 'string').reduce((n, s) => n + [...s.text].length, 0);
      obs.expectGlyphs = p.cols * p.rows;
      obs.lineWidth = p.lineWidth;
      obs.commandLength = cmd.length;
      obs.errors.push(...await runAll(ctx, [cmd]));
    }
  },

  async run() {
    // Nothing moves: the stage is the experiment, and a person judges it from the seat.
  },

  checks(ctx, o) {
    const obs = ctx.observed;
    const s = ctx.server;
    const c = (name, test) => ({ name, afterReset: null, test: async () => Boolean(await test()) });
    const block = async (x, y, d, b) => passed(await s.run(`execute if block ${at(fs.world(x, y, d))} ${b}`));
    const list = [
      c('every build command was taken', () => obs.errors && obs.errors.length === 0),
      c('the near tower stands (real blocks)', () => block(TOWER_NEAR.x, TOWER_NEAR.h - 1, TOWER_NEAR.d, 'minecraft:red_concrete')),
    ];
    if (o.scenery === 'off') {
      list.push(c('the tower at 130 is real blocks', () => block(TOWER_BEHIND.x, TOWER_BEHIND.h - 1, TOWER_BEHIND.d, 'minecraft:red_concrete')));
      return list;
    }
    list.push(c('the tower at 130 is not built (it is painted)', () => block(TOWER_BEHIND.x, TOWER_BEHIND.h - 1, TOWER_BEHIND.d, 'minecraft:air')));
    if (o.scenery === 'panorama 120') {
      const sel = `@e[type=minecraft:text_display,tag=${ctx.tag},tag=wx_far_pano]`;
      list.push(c('one panorama display', async () => {
        const r = await s.run(`execute if entity ${sel}`);
        return passed(r) && !/count: (?!1\b)\d+/.test(r.lines.join(' '));
      }));
      list.push(c(`its text has ${fs.PANORAMA.cols} x ${fs.PANORAMA.rows} glyphs`, () => obs.glyphs === obs.expectGlyphs));
      list.push(c('its line width is set', async () => (await s.run(`data get entity ${sel.replace(/\]$/,',limit=1]')} line_width`)).lines.join(' ').includes(String(obs.lineWidth))));
      return list;
    }
    const w = wallOf(o);
    list.push(c(`the wall at ${w} has the projected blocks at its corners and middle`, async () => {
      const wl = obs.wall;
      for (const [x, y] of [[wl.x0, wl.y1], [wl.x1, wl.y1], [wl.x0, 0], [wl.x1, 0], [0, 2], [0, Math.floor(wl.y1 / 2)]]) {
        if (!(await block(x, y, w, wl.block(x, y)))) return false;
      }
      return true;
    }));
    return list;
  },

  async cleanup(ctx) {
    const out = [];
    const x0 = SITE.x - NEAR.half;
    const x1 = SITE.x + NEAR.half;
    const rows = Math.floor(32768 / ((x1 - x0 + 1) * (NEAR.maxY + 1)));
    for (let d = 0; d <= NEAR.depth; d += rows) {
      out.push(`fill ${x0} 0 ${SITE.z + d} ${x1} ${NEAR.maxY} ${SITE.z + Math.min(d + rows - 1, NEAR.depth)} minecraft:air`);
    }
    for (const w of WALLS) out.push(`fill ${SITE.x - 80} 0 ${SITE.z + w} ${SITE.x + 80} 160 ${SITE.z + w} minecraft:air`);
    const mid = Math.floor(NEAR.depth / 2);
    out.push(`fill ${x0} -1 ${SITE.z} ${x1} -1 ${SITE.z + mid} ${campus.PALETTE.floor}`);
    out.push(`fill ${x0} -1 ${SITE.z + mid + 1} ${x1} -1 ${SITE.z + NEAR.depth} ${campus.PALETTE.floor}`);
    const errors = await runAll(ctx, out);
    if (errors.length) throw new Error(errors[0]);
  },

  PANORAMA_AT,
  CELLS: {
    far: [
      { name: 'far panorama 120', values: { scenery: 'panorama 120' }, expect: 'PASS' },
      { name: 'far wall 64', values: { scenery: 'wall 64' }, expect: 'PASS' },
      { name: 'far wall 96', values: { scenery: 'wall 96' }, expect: 'PASS' },
      { name: 'far off', values: { scenery: 'off' }, expect: 'PASS' },
    ],
  },
};
