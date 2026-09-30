'use strict';
// Transport rings for chambers: lay a circle of slabs, pair two by the console form or as a
// player, fire, read the plugin's answers, take a pair down. Every fact here is from the
// plugin's source (RingPattern, RingTemplate, RingSurvey, RingCommand, RingConsoleCommands,
// RingTransit, RingCycle, RingMessages):
//
//  - ODD circle: 16 slabs round a true centre (rows 3,5,7,7,7,5,3); EVEN: 12 slabs round the
//    low-x, low-z block of a 2x2 centre (rows 2,4,6,6,4,2). One kind of slab, one half: bottom
//    slabs make a floor ring, top slabs a ceiling ring. The middle must be clear.
//  - `ring build <world> x1 y1 z1 x2 y2 z2`, console: each x y z a block inside its circle where
//    a player would stand (the slab layer works for floor and ceiling alike). The pair is PUBLIC
//    with no owner, its slabs are taken up, and the answer names the id and both arrivals.
//  - `ring create`, as a player standing inside a circle, twice: the pair is PRIVATE, owned by
//    that player.
//  - `ring fire <id>` (console) or stepping into a ring (a player) arms it; everything inside
//    both ends swaps in one instant about 154 ticks later, to the other ring's centre at its
//    stack base; then 30 s of cooldown from the end of the cycle.
//  - `ring remove`, `ring list`, `ring edit`, `allow`, `deny`, `owner`, `cancel` are player-only.

const SLABS_ODD = [
  [-1, -3], [0, -3], [1, -3], [-2, -2], [2, -2], [-3, -1], [3, -1], [-3, 0], [3, 0], [-3, 1], [3, 1],
  [-2, 2], [2, 2], [-1, 3], [0, 3], [1, 3],
];
const SLABS_EVEN = [
  [0, -2], [1, -2], [-1, -1], [2, -1], [-2, 0], [3, 0], [-2, 1], [3, 1], [-1, 2], [2, 2], [0, 3], [1, 3],
];

// About how long from arming to the swap with the default timings (RingTransit's timeline).
const SWAP_TICKS = 154;
const CYCLE_TICKS = 228;

function slabsOf(pattern) {
  return pattern === 'EVEN' ? SLABS_EVEN : SLABS_ODD;
}

/** The cells a circle covers (perimeter and inside), for clearing and overlap reasoning. */
function footprint(pattern, anchor) {
  const cells = pattern === 'EVEN' ? { lo: -2, hi: 3 } : { lo: -3, hi: 3 };
  return { x0: anchor.x + cells.lo, x1: anchor.x + cells.hi, z0: anchor.z + cells.lo, z1: anchor.z + cells.hi };
}

class RingKit {
  constructor(srv, probe) {
    this.srv = srv;
    this.probe = probe;
  }

  /**
   * Lays a circle of `slab` (e.g. 'smooth_stone_slab') round `anchor` ({x,y,z}: y is the slab
   * layer), bottom slabs for a floor ring, top for a ceiling ring. `odd` replaces one slab with
   * another kind or half, for the refusal cases.
   */
  async lay(pattern, anchor, slab, { half = 'bottom', odd = null, dim = 'minecraft:overworld' } = {}) {
    const offs = slabsOf(pattern);
    for (const [i, [dx, dz]] of offs.entries()) {
      const block = i === 0 && odd ? odd : `minecraft:${slab}[type=${half}]`;
      await this.srv.run(`execute in ${dim} run setblock ${anchor.x + dx} ${anchor.y} ${anchor.z + dz} ${block}`);
    }
  }

  /** Takes up a circle's slabs (and anything else in its layer). */
  async clear(pattern, anchor, dim = 'minecraft:overworld') {
    const f = footprint(pattern, anchor);
    await this.srv.run(`execute in ${dim} run fill ${f.x0} ${anchor.y} ${f.z0} ${f.x1} ${anchor.y} ${f.z1} minecraft:air`);
  }

  /** Pairs two circles by the console form; returns { id, arrivals: [a, b], text } or { error }. */
  async build(a, b, world = 'world') {
    const r = await this.srv.run(`wormhole ring build ${world} ${a.x} ${a.y} ${a.z} ${b.x} ${b.y} ${b.z}`);
    const text = r.lines.join(' / ');
    const num = /(-?\d+(?:\.\d+)?)/.source;
    const m = new RegExp(`Ring pair (\\w+) is live and public\\. Arrivals at ${num} ${num} ${num} and ${num} ${num} ${num}`).exec(text);
    if (!m) return { error: text || 'no answer', text };
    const n = m.slice(2).map(Number);
    return { id: m[1], arrivals: [{ x: n[0], y: n[1], z: n[2] }, { x: n[3], y: n[4], z: n[5] }], text };
  }

  async fire(id) {
    const r = await this.srv.run(`wormhole ring fire ${id}`);
    return r.lines.join(' / ');
  }

  /**
   * Probe says a player command and collects what it is told for `ms` (or until `until` matches).
   * Chat and action bar alike, as plain text.
   */
  async ask(command, { ms = 2000, until = null, settle = 0 } = {}) {
    const bot = this.probe.bot;
    const heard = [];
    const onChat = (m) => heard.push(m.toString());
    bot.on('message', onChat);
    bot.chat(command);
    const end = Date.now() + ms;
    try {
      while (Date.now() < end && !(until && heard.some((h) => until.test(h)))) {
        await new Promise((resolve) => { setTimeout(resolve, 100); });
      }
      // The rest of a reply sent in one go, after its first line.
      if (settle) await new Promise((resolve) => { setTimeout(resolve, settle); });
    } finally {
      bot.off('message', onChat);
    }
    return heard.filter((h) => !h.startsWith('[Server:')).join(' / ');
  }

  /** Removes a pair as Probe (op: `ring remove` is player-only), laying its slabs back. */
  async remove(id) {
    return this.ask(`/wormhole ring remove ${id}`, { until: /Removed both ends|no ring pair called/ });
  }

  /**
   * The ids Probe's `ring list` shows (an op sees every pair). Waits for the reply itself, not a
   * fixed time: under load a late reply read as "no pairs" leaks a pair into the next cell. The
   * lines are sent in one go, so once the first is in, the rest follow at once.
   */
  async list() {
    const reply = /You have no transport rings\.|, (PUBLIC|PRIVATE), /;
    const text = await this.ask('/wormhole ring list', { ms: 10000, until: reply, settle: 300 });
    if (!reply.test(text)) throw new Error(`ring list was not answered within 10 s (heard: ${text || 'nothing'})`);
    const ids = [...text.matchAll(/\((\w{8})\)|^(\w{8}) —|\/ (\w{8}) —/g)].map((m) => m[1] || m[2] || m[3]);
    return { text, ids };
  }
}

module.exports = { RingKit, SLABS_ODD, SLABS_EVEN, SWAP_TICKS, CYCLE_TICKS, slabsOf, footprint };
