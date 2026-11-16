// How a gate dials: the chevron locks, the ring light that runs before each, the pause and the woosh.
// A port of DialSpin, DialSpinPattern, StargateAnimator's lighting loop and WooshSequence: keep it in
// step with them. A cell is {col, y, layer, wave, part, dhd}, as ringCells() builds it from a shape;
// the ring's lit sets are Sets of indexes into DialSpin.ring.

export const LOCAL_CHEVRONS = 7; // the top chevron, the last of a dial within the world
export const OTHER_WORLD_CHEVRON = 8; // the bottom one, locked only on a dial to another world
export const LAST_CHEVRON_PAUSE_TICKS = 40;
// A shape that omits LIGHT_TICKS or WOOSH_TICKS gets 3 of each (StargateShape's own defaults).
export const DEFAULT_LIGHT_TICKS = 3;
const TOP_HOLD_TICKS = 10;
const UNIVERSE_PACE = 12;
const GLYPHS = 36;
const CHEVRON_GAP = 2;
const TWO_PI = 2 * Math.PI;

export const PATTERNS = ["none", "top", "chevron", "lap", "fill", "pegasus", "chase", "universe", "overshoot"];

const floorMod = (a, n) => ((a % n) + n) % n;
const alternating = (glyph) => (glyph % 2 === 1 ? 1 : -1);

/**
 * The cells GateBlueprint lays for a shape's ring: per layer the frame cells, then the chevron
 * cells, in file order. `dhd` marks a layer with no lights and no opening that does not edge one,
 * which is the one the DHD is built in.
 */
export function ringCells(shape) {
  const cells = [];
  const layerNumbers = [...new Set(shape.cells.map((c) => c.layer))].sort((a, b) => a - b);
  for (const layer of layerNumbers) {
    const here = shape.cells.filter((c) => c.layer === layer);
    const before = shape.cells.filter((c) => c.layer === layer - 1);
    const opening = new Set(before.filter((c) => c.portal).map((c) => `${c.row},${c.col}`));
    const built = here.filter((c) => c.block !== "none");
    const edges = built.some((c) => opening.has(`${c.row},${c.col}`));
    const dhd = !here.some((c) => c.light !== null) && !here.some((c) => c.portal) && !edges;
    for (const part of ["frame", "chevron"]) {
      for (const c of built.filter((b) => b.block === part)) {
        cells.push({ col: c.col, y: c.y, layer, wave: c.light ?? 0, part, dhd });
      }
    }
  }
  return cells;
}

const axisOf = (c, axis) => [c.col, c.y, c.layer][axis];

/** The axis (0 x, 1 y, 2 z) the cells spread least along. */
function narrowestAxis(cells) {
  let best = 0;
  let bestSpread = Infinity;
  for (let axis = 0; axis < 3; axis++) {
    const values = cells.map((c) => axisOf(c, axis));
    const spread = values.length ? Math.max(...values) - Math.min(...values) : 0;
    if (spread < bestSpread) { bestSpread = spread; best = axis; }
  }
  return best;
}

const centroid = (cells) => {
  const n = Math.max(1, cells.length);
  return [0, 1, 2].map((a) => cells.reduce((s, c) => s + axisOf(c, a), 0) / n);
};
const minus = (a, b) => a.map((v, i) => v - b[i]);
const dot = (a, b) => a.reduce((s, v, i) => s + v * b[i], 0);

export class DialSpin {
  /** @returns {DialSpin|null} null for a gate with no top chevron, which has nothing to turn to. */
  static of(cells) {
    const top = cells.filter((c) => c.wave === LOCAL_CHEVRONS);
    if (top.length === 0) return null;
    const lit = cells.filter((c) => c.wave > 0 && c.wave <= LOCAL_CHEVRONS);
    const normal = narrowestAxis(lit);
    // A gate lit on several layers turns on the front one, nearest the DHD: the highest layer.
    const front = lit.reduce((a, b) => (b.layer > a.layer ? b : a), lit[0]);
    const level = axisOf(front, normal);
    const ring = cells.filter((c) => !c.dhd && axisOf(c, normal) === level && (c.part === "frame" || c.part === "chevron"));
    const centre = centroid(ring);
    const delta = minus(centroid(top), centre);
    const length = Math.sqrt(dot(delta, delta));
    const up = length === 0 ? [0, 1, 0] : delta.map((v) => v / length);
    // Seen from the DHD, as the page's camera is: columns count up towards the viewer's right.
    const right = [1, 0, 0];
    const angle = (c) => {
      const v = minus([c.col, c.y, c.layer], centre);
      const a = Math.atan2(dot(v, right), dot(v, up));
      return a < 0 ? a + TWO_PI : a;
    };
    const ordered = [...ring].sort((a, b) => angle(a) - angle(b));
    return new DialSpin(ordered, ordered.map(angle));
  }

  constructor(ring, angles) {
    this.ring = ring;
    this.angles = angles;
    // Each chevron's angle round the ring: the mean taken round the circle, so the top's two sides agree.
    const sin = new Array(OTHER_WORLD_CHEVRON + 1).fill(0);
    const cos = new Array(OTHER_WORLD_CHEVRON + 1).fill(0);
    ring.forEach((c, i) => {
      if (c.wave > 0 && c.wave < sin.length) { sin[c.wave] += Math.sin(angles[i]); cos[c.wave] += Math.cos(angles[i]); }
    });
    this.chevrons = sin.map((s, w) => (s === 0 && cos[w] === 0 ? NaN : Math.atan2(s, cos[w])));
  }

  get size() { return this.ring.length; }

  chevronAngle(glyph) {
    return glyph > 0 && glyph < this.chevrons.length && !Number.isNaN(this.chevrons[glyph]) ? this.chevrons[glyph] : 0;
  }

  /** The ring index whose angle is nearest the given one, going round. */
  nearest(target) {
    let best = 0;
    let bestGap = Infinity;
    this.angles.forEach((a, i) => {
      const raw = Math.abs(a - target) % TWO_PI;
      const gap = Math.min(raw, TWO_PI - raw);
      if (gap < bestGap) { bestGap = gap; best = i; }
    });
    return best;
  }

  /** The ring indexes from one to another, stepping one way round. */
  route(start, end, step) {
    const path = [];
    let i = start;
    for (;;) {
      path.push(i);
      if (i === end || path.length >= this.size) return path;
      i = floorMod(i + step, this.size);
    }
  }

  /** One chevron's ring indexes, empty for one not on the ring. */
  chevron(wave) {
    const out = new Set();
    this.ring.forEach((c, i) => { if (c.wave === wave) out.add(i); });
    return out;
  }

  topChevron() {
    const top = this.chevron(LOCAL_CHEVRONS);
    return top.size ? top : new Set([this.nearest(0)]);
  }

  tail() { return Math.max(2, Math.floor(this.size / 16)); }

  /** The path of a glyph's light under a pattern, its last cell where the light lands. */
  path(pattern, glyph) {
    const end = this.nearest(this.chevronAngle(glyph));
    const half = () => this.route(this.nearest(this.chevronAngle(glyph) + Math.PI), end, alternating(glyph));
    switch (pattern) {
      case "top": return this.route(this.nearest(Math.PI), this.nearest(0), alternating(glyph));
      case "lap": return this.route(floorMod(end + 1, this.size), end, 1);
      case "chevron":
        return this.route(this.nearest(glyph <= 1 ? 0 : this.chevronAngle(glyph - 1)), end, -alternating(glyph));
      case "chase":
        return glyph <= 1 ? this.route(floorMod(end - 1, this.size), end, -1)
          : this.route(this.nearest(this.chevronAngle(glyph - 1)), end, -alternating(glyph));
      case "overshoot": return this.overshoot(half(), alternating(glyph));
      default: return half();
    }
  }

  /** A path run on past its end by a comet's length, then back onto it. */
  overshoot(path, step) {
    const end = path[path.length - 1];
    const past = Math.min(this.tail(), Math.floor(this.size / 4));
    const out = [...path];
    for (let i = 1; i <= past; i++) out.push(floorMod(end + step * i, this.size));
    for (let i = past - 1; i >= 0; i--) out.push(floorMod(end + step * i, this.size));
    return out;
  }

  /** How many cells, signed, the ring turns for a glyph under UNIVERSE. */
  turn(glyph) {
    const n = this.size;
    const ahead = floorMod(this.turned(glyph) - this.turned(glyph - 1), n);
    let cells = alternating(glyph) > 0 ? ahead : n - ahead;
    if (cells === 0) cells = n;
    else if (2 * cells < n) cells += n;
    return alternating(glyph) * cells;
  }

  /** How far round the ring stands once a glyph has locked, under UNIVERSE. */
  turned(glyph) {
    if (glyph <= 0 || glyph >= LOCAL_CHEVRONS) return 0;
    return this.nearest(0) - this.nearest(this.chevronAngle(glyph));
  }

  hold(pattern, glyph) {
    return pattern === "top" && glyph > 1 ? TOP_HOLD_TICKS : 0;
  }

  /** Ticks a glyph's light travels: the chevron's interval, or what UNIVERSE's pace needs. */
  travel(pattern, glyph, interval) {
    const ticks = Math.max(1, interval);
    if (pattern !== "universe") return ticks;
    const paced = Math.ceil((Math.abs(this.turn(glyph)) * UNIVERSE_PACE) / this.size) + 1;
    return Math.max(ticks, paced);
  }

  /** How many ticks a glyph's turn takes before its chevron locks: any rest, then the travel. */
  frames(pattern, glyph, interval) {
    return this.hold(pattern, glyph) + this.travel(pattern, glyph, interval);
  }

  /** The ring cells lit at one frame of a glyph's turn. */
  frame(pattern, glyph, frame, interval) {
    const hold = this.hold(pattern, glyph);
    return frame < hold ? this.topChevron() : this.lit(pattern, glyph, frame - hold, this.travel(pattern, glyph, interval));
  }

  /** The cells that stay lit as a glyph's chevron locks, until the next glyph's turn takes them back. */
  rest(pattern, glyph, last) {
    if (pattern === "universe") return this.universe(glyph, 1, 1, true);
    return pattern === "top" && glyph < last ? this.topChevron() : new Set();
  }

  /** The cells lit at one tick of a glyph's spin; the light reaches its path's end on the last tick. */
  lit(pattern, glyph, tick, ticks) {
    if (pattern === "universe") return this.universe(glyph, tick, ticks, false);
    const path = this.path(pattern, glyph);
    const last = path.length - 1;
    const head = ticks <= 1 ? last : Math.round((tick * last) / (ticks - 1));
    if (pattern === "chevron") return this.hopStep(path, glyph, head);
    const length = pattern === "fill" ? head + 1 : this.tail();
    const lit = new Set();
    for (let i = Math.max(0, head - length + 1); i <= head; i++) lit.add(path[i]);
    return lit;
  }

  /** Destiny's ring part way through a glyph's turn: the top chevron and each locked one ride round. */
  universe(glyph, tick, ticks, landed) {
    const n = this.size;
    const progress = ticks <= 1 ? 1 : tick / (ticks - 1);
    const now = this.turned(glyph - 1) + Math.round(this.turn(glyph) * progress);
    const riding = new Set(this.topChevron());
    for (let k = 1; k <= (landed ? glyph : glyph - 1); k++) for (const i of this.chevron(k)) riding.add(i);
    return new Set([...riding].map((i) => floorMod(i + now, n)));
  }

  /** A CHEVRON step: the light hops a glyph's width at a time and lands as the chevron alone. */
  hopStep(path, glyph, head) {
    const last = path.length - 1;
    const chevron = this.chevron(glyph);
    const landed = chevron.size ? chevron : new Set([path[last]]);
    const hops = this.hops(path, chevron);
    if (head >= last || hops.length === 0) return landed;
    return hops[Math.floor((head * hops.length) / last)];
  }

  hops(path, chevron) {
    const width = Math.max(1, Math.round(this.size / GLYPHS));
    const hops = [];
    let hop = new Set();
    for (let i = 0; i < path.length - 1 && !chevron.has(path[i]); i++) {
      const frame = this.ring[path[i]].wave === 0 && !this.withinAChevron(path[i]);
      if ((!frame || hop.size === width) && hop.size) { hops.push(hop); hop = new Set(); }
      if (frame) hop.add(path[i]);
    }
    if (hop.size) hops.push(hop);
    return hops;
  }

  /** Whether a frame cell sits between cells of one chevron, as Grand's do, rather than between two. */
  withinAChevron(at) {
    const before = this.chevronBeside(at, -1);
    return before > 0 && before === this.chevronBeside(at, 1);
  }

  chevronBeside(at, step) {
    for (let i = 1; i <= CHEVRON_GAP; i++) {
      const wave = this.ring[floorMod(at + step * i, this.size)].wave;
      if (wave > 0) return wave;
    }
    return 0;
  }
}

/**
 * When everything of a dial happens, in ticks from the first: each chevron's lock, the ring turn
 * before it, then 40 ticks of pause and the woosh. `spin` is null for the pattern `none`, or for a
 * shape with no top chevron; those lock a chevron every `lightTicks`, the first at once.
 *
 * @param {object} shape a parsed shape
 * @param {DialSpin|null} spin
 * @param {string} pattern one of PATTERNS
 * @param {boolean} otherWorld true for a dial to another world, which locks an eighth chevron
 */
export function dialSchedule(shape, spin, pattern, otherWorld) {
  const highest = shape.lightOrders.length ? Math.max(...shape.lightOrders) : 0;
  const last = Math.min(highest, otherWorld ? OTHER_WORLD_CHEVRON : LOCAL_CHEVRONS);
  const interval = Math.max(1, shape.lightTicks ?? DEFAULT_LIGHT_TICKS);
  const turns = spin !== null && pattern !== "none";
  const glyphs = [];
  let t = 0;
  for (let glyph = 1; glyph <= last; glyph++) {
    const frames = turns ? spin.frames(pattern, glyph, interval) : 0;
    glyphs.push({ glyph, start: t, frames, lockAt: t + frames });
    t += turns ? frames + 1 : interval;
  }
  const lastLock = glyphs.length ? glyphs[glyphs.length - 1].lockAt : 0;
  const wooshTicks = shape.wooshTicks ?? DEFAULT_LIGHT_TICKS;
  const steps = shape.wooshOrders.length;
  const wooshStart = lastLock + LAST_CHEVRON_PAUSE_TICKS;
  // Stage s draws wave s, then takes them back in reverse; the last stage settles into the open portal.
  const settleAt = wooshStart + Math.max(0, 2 * steps - 1) * wooshTicks;
  return { last, turns, interval, glyphs, lastLock, wooshStart, wooshTicks, wooshSteps: steps, settleAt, total: settleAt };
}

/** How many woosh waves show at a woosh stage: out one at a time, then back in reverse. */
export function wavesShowing(stage, steps) {
  if (stage < 0) return 0;
  return stage < steps ? stage + 1 : Math.max(0, 2 * steps - 1 - stage);
}

/**
 * What a dial shows at a tick: chevrons locked, the ring cells the light is on, and the woosh.
 * Chevrons under UNIVERSE are not lit in place until the last locks; they ride the ring instead.
 */
export function dialStateAt(schedule, spin, pattern, tick) {
  const { glyphs, last, interval, turns } = schedule;
  let locked = 0;
  let ring = new Set();
  let phase = "waiting";
  let glyph = 0;
  for (const g of glyphs) {
    if (tick < g.start) break;
    glyph = g.glyph;
    if (tick >= g.lockAt) {
      locked = g.glyph;
      phase = "locked";
      ring = turns ? spin.rest(pattern, g.glyph, last) : new Set();
    } else {
      phase = "turning";
      ring = spin.frame(pattern, g.glyph, tick - g.start, interval);
    }
  }
  let woosh = 0;
  let open = false;
  if (tick >= schedule.settleAt && schedule.wooshStart <= tick) {
    open = true;
    phase = "open";
  } else if (tick >= schedule.wooshStart) {
    const stage = Math.floor((tick - schedule.wooshStart) / schedule.wooshTicks);
    woosh = wavesShowing(stage, schedule.wooshSteps);
    phase = "woosh";
  } else if (locked === last && last > 0) {
    phase = "holding";
  }
  const ridesTheRing = turns && pattern === "universe";
  return { glyph, locked, ring, phase, woosh, open, ridesTheRing, litInPlace: ridesTheRing ? (locked === last ? last : 0) : locked };
}
