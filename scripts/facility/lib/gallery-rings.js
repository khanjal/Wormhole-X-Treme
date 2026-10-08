'use strict';
// The gallery's transport rings (lib/gallery.js, family 'ring'): two circles of slabs on the
// studio floor, paired by the console, then fired with Probe2 standing in one (a pair with nobody
// inside either end stands down after its countdown and draws nothing). The rings are drawn to the client as blocks (the
// plugin shows them, never places them), so the viewer has them: a stack of rings climbs out of
// each circle, the circles light, the two ends swap, and the stacks go back down.
//
//   still, idle   the circle of slabs as a player lays it, before it is paired
//   reel, fire    the pair fired, from just before the first ring is drawn until the cycle is quiet
//
// A pair takes its slabs up when it is built, so the stills are of the circles laid and the reels
// of what comes after. The camera stands in front of both ends, high enough to see the stacks.

const { RingKit, footprint, CYCLE_TICKS } = require('./rings');
const { standAt } = require('./studio');

const SITE = { z: 396, ax: -8, bx: 8, drop: 6 };
const SLAB = 'smooth_stone_slab';
const PATTERNS = ['ODD', 'EVEN'];
const STYLES = ['fast', 'slow'];

/** Every ring scene: the circles of each pattern, and each pattern fired in each style, and a pair with a ceiling ring. */
function scenes() {
  const out = [];
  const ring = (pattern, style = 'fast', ceiling = false) => ({ pattern, style, ceiling });
  for (const pattern of PATTERNS) {
    out.push({ name: `ring-${pattern.toLowerCase()}-slabs`, family: 'ring', kind: 'still', state: 'idle', ring: ring(pattern) });
  }
  for (const pattern of PATTERNS) {
    for (const style of STYLES) {
      out.push({ name: `ring-${pattern.toLowerCase()}-${style}`, family: 'ring', kind: 'reel', action: 'fire', trim: 400, ring: ring(pattern, style) });
    }
  }
  out.push({ name: 'ring-ceiling', family: 'ring', kind: 'reel', action: 'fire', trim: 400, ring: ring('ODD', 'fast', true) });
  return out;
}

/** The slab layer of each end: the floor ring on the floor, the ceiling ring `drop` blocks up. */
function ends(ring) {
  return [{ x: SITE.ax, y: 0, z: SITE.z }, { x: SITE.bx, y: ring.ceiling ? SITE.drop : 0, z: SITE.z }];
}

/** Where Probe stands for a pair: in front of both ends, up enough to see the stacks climb. */
function camera(ring) {
  const eye = { x: 0.5, y: ring.ceiling ? 4.5 : 3.6, z: SITE.z + (ring.ceiling ? 13 : 11) };
  return standAt(eye, { x: 0.5, y: ring.ceiling ? 3.2 : 2.2, z: SITE.z + 0.5 });
}

/** Where somebody stands to be inside a circle: its middle (an EVEN circle's is the corner of its 2 x 2 centre). */
function middle(pattern, p) {
  const o = pattern === 'EVEN' ? 1 : 0.5;
  return { x: p.x + o, y: p.y, z: p.z + o };
}

/** The roof over a ceiling ring: one block above its slabs, a block wider than the circle all round. */
function roof(pattern, b) {
  const f = footprint(pattern, b);
  return { x0: f.x0 - 1, x1: f.x1 + 1, z0: f.z0 - 1, z1: f.z1 + 1, y: b.y + 1 };
}

async function prepare(ctx, s) {
  const kit = new RingKit(ctx.srv, ctx.probe);
  const { pattern, style, ceiling } = s.ring;
  const [a, b] = ends(s.ring);
  await kit.lay(pattern, a, SLAB, { half: 'bottom' });
  await kit.lay(pattern, b, SLAB, { half: ceiling ? 'top' : 'bottom' });
  if (ceiling) {
    const r = roof(pattern, b);
    await ctx.srv.run(`execute in ${ctx.dim} run fill ${r.x0} ${r.y} ${r.z0} ${r.x1} ${r.y} ${r.z1} minecraft:white_concrete`);
  }
  let id = null;
  let traveller = null;
  if (s.kind === 'reel') {
    // In before the pair exists, so that nothing arms it: it is fired by the console, on the camera's say.
    traveller = await ctx.second();
    await traveller.teleport(middle(pattern, a));
    const built = await kit.build(a, b);
    if (built.error) throw new Error(`ring build: ${built.error}`);
    id = built.id;
    if (style === 'slow') {
      const said = await kit.ask(`/wormhole ring edit ${id} style slow`, { until: /Style set to|error|cannot|not allowed/i });
      if (!/Style set to/.test(said)) throw new Error(`ring edit style slow: ${said || 'no answer'}`);
    }
  }
  return { camera: camera(s.ring), kit, id, a, b, pattern, ceiling, traveller };
}

/**
 * Fires the pair and waits out the cycle: the first block of a ring drawn marks where a trimmed
 * reel begins, and the cycle is over once the traveller has been put down at the far end and
 * nothing in either circle has changed for a second and a half after (the rings going back down).
 * Quiet alone would not do: the stacks pause while they swap, and a slow pair waits between rings.
 */
async function act(ctx, s, prep) {
  const { a, b, pattern } = prep;
  const regions = [a, b].map((p) => footprint(pattern, p));
  const inside = (pos) => pos.y >= 0 && pos.y <= SITE.drop + 3 && regions.some((f) => pos.x >= f.x0 && pos.x <= f.x1 && pos.z >= f.z0 && pos.z <= f.z1);
  let first = null;
  let last = null;
  const onUpdate = (_old, block) => {
    if (!block || !inside(block.position)) return;
    last = Date.now();
    if (!first) {
      first = last;
      ctx.mark();
    }
  };
  ctx.bot.on('blockUpdate', onUpdate);
  try {
    const said = await prep.kit.fire(prep.id);
    if (/no ring|not found|error/i.test(said)) throw new Error(`ring fire: ${said}`);
    const wall = (ticks) => ticks * 50 * ctx.factor;
    const give = Date.now() + wall(CYCLE_TICKS) * 2;
    const far = middle(pattern, { x: b.x, y: 0, z: b.z });
    const arrived = () => Math.hypot(prep.traveller.position.x - far.x, prep.traveller.position.z - far.z) < 2.5 && prep.traveller.position.y < 1.5;
    let landed = null;
    // A hundred ms at the speeds a reel runs at, less for a faster clock.
    const poll = Math.max(5, Math.min(100, 25 * ctx.factor));
    while (Date.now() < give) {
      if (!landed && arrived()) landed = Date.now();
      if (landed && Date.now() - Math.max(last || 0, landed) > 1500 * ctx.factor) return;
      await ctx.sleep(poll);
    }
    throw new Error(first ? 'the traveller never reached the far ring' : 'no ring was ever drawn: the pair did not run');
  } finally {
    ctx.bot.off('blockUpdate', onUpdate);
  }
}

/** Takes the pair down if there is one and the slabs and the roof away, so the next scene finds a bare floor. */
async function cleanup(ctx, s, prep) {
  const { kit, a, b, pattern } = prep;
  if (prep.traveller) await prep.traveller.teleport(ctx.home).catch(() => {});
  if (prep.id) await kit.remove(prep.id);
  await kit.clear(pattern, a);
  await kit.clear(pattern, b);
  if (prep.ceiling) {
    const r = roof(pattern, b);
    await ctx.srv.run(`execute in ${ctx.dim} run fill ${r.x0} ${r.y} ${r.z0} ${r.x1} ${r.y} ${r.z1} minecraft:air`);
  }
}

module.exports = { scenes, prepare, act, cleanup, camera, ends, middle, roof, SITE, PATTERNS, STYLES };
