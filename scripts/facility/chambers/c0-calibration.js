'use strict';
// C0, the Calibration Cell in Ops: the trivial chamber that exercises the whole contract. Stage
// hangs a lamp in the back wall and puts a block in front of it with a lever or a button on it;
// Run has Probe walk in through the door and press it; the checks read the world back (and,
// for a button, which springs back, what Probe's client was sent while it was down).

const campus = require('../lib/campus');
const { cellLayout } = require('../lib/blueprint');

const def = campus.chamber('c0');
const layout = cellLayout(def);
const b = def.box;
const row = Math.floor((b.z0 + b.z1) / 2); // the door's row, straight in from it
const LAMP = { x: b.x1 + 1, y: b.y0 + 1, z: row }; // in the east wall
const TARGET = { x: b.x1, y: b.y0 + 1, z: row };
const CONTROL = { x: b.x1 - 1, y: b.y0 + 1, z: row };
const at = (p) => `${p.x} ${p.y} ${p.z}`;

const SETTING = 'gate-sound-volume';

async function test(ctx, command) {
  const r = await ctx.server.run(command);
  return r.lines.some((l) => /Test passed/.test(l));
}

module.exports = {
  id: 'c0',
  wing: 'ops',
  title: def.title,
  cell: def.box,
  seat: layout.seat,
  options: {
    target: [
      { value: 'gold_block', label: 'gold', why: 'a solid block: a lever or button hangs on it' },
      { value: 'glass', label: 'glass', why: 'not solid enough to hold anything: refused' },
    ],
    control: [
      { value: 'lever', label: 'lever', why: 'stays down: the lamp stays lit' },
      { value: 'button', label: 'button', why: 'springs back after a second: Probe must see the lamp while it is down' },
    ],
  },
  needs: () => ({ config: { [SETTING]: '0.5' } }),
  refuses: (v) => (v.target === 'glass' ? 'a lever or button needs a solid block to hang on' : null),

  async stage(ctx, v) {
    await ctx.step('hanging the lamp');
    for (const cmd of [
      `setblock ${at(LAMP)} minecraft:redstone_lamp`,
      `setblock ${at(TARGET)} minecraft:${v.target}`,
      `setblock ${at(CONTROL)} minecraft:${v.control === 'button' ? 'stone_button' : 'lever'}[face=wall,facing=west]`,
    ]) {
      const r = await ctx.server.run(cmd);
      if (r.errors.length) throw new Error(`${cmd}: ${r.errors.join(' ')}`);
    }
  },

  async run(ctx) {
    await ctx.step('walking in');
    const out = layout.door.outside;
    const inside = layout.door.inside;
    await ctx.probe.teleport({ x: out.x + 0.5, y: b.y0, z: out.z + 0.5, yaw: -90 });
    await ctx.probe.walk([{ x: inside.x + 0.5, z: inside.z + 0.5 }, { x: CONTROL.x - 0.5, z: CONTROL.z + 0.5 }]);
    await ctx.step('pressing it');
    const lit = new Promise((resolve) => {
      const on = (_old, blk) => {
        if (blk && blk.position.x === LAMP.x && blk.position.y === LAMP.y && blk.position.z === LAMP.z
          && blk.getProperties().lit === true) { ctx.probe.bot.off('blockUpdate', on); resolve(true); }
      };
      ctx.probe.bot.on('blockUpdate', on);
      setTimeout(() => { ctx.probe.bot.off('blockUpdate', on); resolve(false); }, 4000);
    });
    await ctx.probe.click(CONTROL);
    ctx.observed.lampLit = await lit;
  },

  checks(ctx, v) {
    const list = [
      { name: 'target stands where staged', test: () => test(ctx, `execute if block ${at(TARGET)} minecraft:${v.target}`) },
      { name: 'the setting it needs is in force', afterReset: null, test: async () => (await ctx.config.get(SETTING)) === '0.5' },
      // Or the check above, and the self-test's "restored", would hold whether it was set or not.
      { name: 'and it replaced a different value', afterReset: null, test: async () => {
        const was = ctx.config.replaced(ctx.owner, SETTING);
        return was !== undefined && was !== '0.5';
      } },
      { name: 'Probe saw the lamp light', afterReset: null, test: async () => ctx.observed.lampLit === true },
    ];
    if (v.control === 'lever') {
      list.push({ name: 'the lever is down', test: () => test(ctx, `execute if block ${at(CONTROL)} minecraft:lever[powered=true]`) });
      list.push({ name: 'the lamp is lit', test: () => test(ctx, `execute if block ${at(LAMP)} minecraft:redstone_lamp[lit=true]`) });
    } else {
      list.push({ name: 'the button is there', test: () => test(ctx, `execute if block ${at(CONTROL)} minecraft:stone_button`) });
    }
    return list;
  },

  reset: 'wx:reset/c0',
  // Exposed for the self-test's own checks.
  points: { LAMP, TARGET, CONTROL },
};
