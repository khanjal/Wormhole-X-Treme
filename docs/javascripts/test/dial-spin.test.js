// Run from the repository root with plain `node --test`; it reads the shipped .shape files.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseShape } from "../shape-parser.js";
import {
  PATTERNS, DialSpin, ringCells, dialSchedule, dialStateAt, wavesShowing, LAST_CHEVRON_PAUSE_TICKS,
} from "../dial-spin.js";

const SHAPES = fileURLToPath(new URL("../../../src/main/resources/shapes/gate/", import.meta.url));
const load = (name) => parseShape(readFileSync(`${SHAPES}${name}.shape`, "utf8"));
const spinOf = (shape) => DialSpin.of(ringCells(shape));
const locks = (shape, pattern, other = false) =>
  dialSchedule(shape, pattern === "none" ? null : spinOf(shape), pattern, other).glyphs.map((g) => g.lockAt);
const waveOrder = (spin) => spin.ring.filter((c) => c.wave > 0).map((c) => c.wave).join("");

test("Standard's ring is its sixteen front frame cells, chevrons in clockwise order from the top", () => {
  const spin = spinOf(load("Standard"));
  assert.equal(spin.size, 16);
  // :L#7 at the top, then 1, 2, 3 down the right, 8 at the bottom, 4, 5, 6 up the left, from Standard.shape.
  assert.equal(waveOrder(spin), "71238456");
  assert.equal(spin.ring[0].wave, 7);
});

test("a shape with no top chevron has no ring to turn", () => {
  assert.equal(spinOf(load("Minimal")), null);
});

test("none locks a chevron every light interval, the first at once, then holds 40 ticks before the woosh", () => {
  const shape = load("Standard");
  const schedule = dialSchedule(shape, null, "none", false);
  assert.deepEqual(locks(shape, "none"), [0, 10, 20, 30, 40, 50, 60]);
  assert.equal(schedule.wooshStart, 60 + LAST_CHEVRON_PAUSE_TICKS);
  // Three woosh steps out and back is five more steps of three ticks to the settle.
  assert.equal(schedule.settleAt, 100 + 5 * 3);
});

test("pegasus turns a chevron's interval and locks a tick after the light lands", () => {
  assert.deepEqual(locks(load("Standard"), "pegasus"), [10, 21, 32, 43, 54, 65, 76]);
});

test("top rests half a second after every chevron but the last", () => {
  assert.deepEqual(locks(load("Standard"), "top"), [10, 31, 52, 73, 94, 115, 136]);
});

test("universe takes about a second more than the others on Standard and no more on Grand or Massive", () => {
  const std = load("Standard");
  const gap = (shape) => locks(shape, "universe")[6] - locks(shape, "pegasus")[6];
  assert.ok(gap(std) >= 20 && gap(std) <= 30, `Standard ${gap(std)}`);
  assert.ok(gap(load("Grand")) <= 1, `Grand ${gap(load("Grand"))}`);
  assert.ok(gap(load("Massive")) <= 1, `Massive ${gap(load("Massive"))}`);
});

test("a dial to another world locks an eighth chevron; Minimal only ever has its one", () => {
  assert.equal(locks(load("Standard"), "pegasus", true).length, 8);
  assert.equal(locks(load("Standard"), "pegasus").length, 7);
  assert.equal(locks(load("Minimal"), "pegasus", true).length, 1);
});

test("every pattern locks the chevrons in order, one after another", () => {
  for (const name of ["Standard", "Large", "Grand", "Massive"]) {
    const shape = load(name);
    for (const pattern of PATTERNS) {
      const at = locks(shape, pattern, true);
      assert.equal(at.length, 8, `${name} ${pattern}`);
      assert.ok(at.every((t, i) => i === 0 || t > at[i - 1]), `${name} ${pattern}: ${at.join()}`);
    }
  }
});

test("each pattern's light for a glyph ends on that glyph's chevron", () => {
  const spin = spinOf(load("Standard"));
  for (const pattern of ["lap", "chevron", "chase", "pegasus", "fill", "overshoot"]) {
    for (let glyph = 1; glyph <= 8; glyph++) {
      const landed = spin.lit(pattern, glyph, 9, 10);
      const waves = [...landed].map((i) => spin.ring[i].wave);
      assert.ok(waves.includes(glyph), `${pattern} glyph ${glyph}: ${waves.join()}`);
    }
  }
});

test("top always lands on the top chevron, alternating direction each glyph", () => {
  const spin = spinOf(load("Standard"));
  for (let glyph = 1; glyph <= 7; glyph++) {
    const path = spin.path("top", glyph);
    assert.equal(spin.ring[path[path.length - 1]].wave, 7);
    assert.ok(path.length > 1 && path[1] === (path[0] + (glyph % 2 === 1 ? 1 : spin.size - 1)) % spin.size, `glyph ${glyph}`);
  }
});

test("lap is a whole turn: every ring cell, then the chevron", () => {
  const spin = spinOf(load("Standard"));
  assert.equal(spin.path("lap", 3).length, spin.size);
});

test("when the top chevron locks under universe, every chevron is back in its own place", () => {
  const spin = spinOf(load("Standard"));
  const own = new Set(spin.ring.map((c, i) => (c.wave >= 1 && c.wave <= 7 ? i : -1)).filter((i) => i >= 0));
  assert.deepEqual([...spin.rest("universe", 7, 7)].sort((a, b) => a - b), [...own].sort((a, b) => a - b));
});

test("the dial's state counts the chevrons locked, lights them in place and opens at the settle", () => {
  const shape = load("Standard");
  const spin = spinOf(shape);
  const schedule = dialSchedule(shape, spin, "pegasus", false);
  assert.equal(dialStateAt(schedule, spin, "pegasus", 5).locked, 0);
  assert.equal(dialStateAt(schedule, spin, "pegasus", 5).phase, "turning");
  assert.equal(dialStateAt(schedule, spin, "pegasus", 10).locked, 1);
  assert.equal(dialStateAt(schedule, spin, "pegasus", 75).locked, 6);
  const last = dialStateAt(schedule, spin, "pegasus", schedule.lastLock);
  assert.deepEqual([last.locked, last.litInPlace], [7, 7]);
  assert.equal(dialStateAt(schedule, spin, "pegasus", schedule.lastLock + 1).phase, "holding");
  assert.equal(dialStateAt(schedule, spin, "pegasus", schedule.wooshStart).phase, "woosh");
  assert.equal(dialStateAt(schedule, spin, "pegasus", schedule.settleAt).open, true);
});

test("under universe the locked chevrons ride the ring and are lit in place only when the last locks", () => {
  const shape = load("Standard");
  const spin = spinOf(shape);
  const schedule = dialSchedule(shape, spin, "universe", false);
  const mid = dialStateAt(schedule, spin, "universe", schedule.glyphs[3].lockAt);
  assert.deepEqual([mid.locked, mid.litInPlace, mid.ridesTheRing], [4, 0, true]);
  assert.equal(dialStateAt(schedule, spin, "universe", schedule.lastLock).litInPlace, 7);
});

test("the woosh goes out a wave at a time and back in reverse, ending clear", () => {
  assert.deepEqual([0, 1, 2, 3, 4, 5].map((s) => wavesShowing(s, 3)), [1, 2, 3, 2, 1, 0]);
});

test("a gate with no woosh steps settles as soon as the pause is over", () => {
  const shape = { ...load("Standard"), wooshOrders: [] };
  const schedule = dialSchedule(shape, null, "none", false);
  assert.equal(schedule.settleAt, schedule.wooshStart);
});

// Standard's ring by index, clockwise from the top: 0 is :L#7, 2 is :L#1, 4 is :L#2, 6 is :L#3, 8 is the
// eighth at the bottom, 10 is :L#4, 12 is :L#5 and 14 is :L#6; the odd indexes are plain frame. Each
// expected route below is read off the pattern's rule in docs/guide/GATES.md and DialSpin.path.
const ROUTES = {
  // Half the ring to the top chevron: clockwise on odd glyphs, anticlockwise on even ones.
  top: { 1: [8, 9, 10, 11, 12, 13, 14, 15, 0], 2: [8, 7, 6, 5, 4, 3, 2, 1, 0], 3: [8, 9, 10, 11, 12, 13, 14, 15, 0] },
  // A whole turn clockwise, ending on the chevron.
  lap: { 1: [3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2], 2: [5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4] },
  // From the top anticlockwise to chevron 1, then chevron to chevron, the other way each time.
  chevron: { 1: [0, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2], 2: [2, 3, 4], 3: [4, 3, 2, 1, 0, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6] },
  // A whole turn anticlockwise from chevron 1 back to it, then chevron to chevron, clockwise first.
  chase: { 1: [1, 0, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2], 2: [2, 3, 4], 3: [4, 3, 2, 1, 0, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6] },
  // Half the ring, from the cell opposite the chevron, turning the other way each glyph.
  pegasus: { 1: [10, 11, 12, 13, 14, 15, 0, 1, 2], 2: [12, 11, 10, 9, 8, 7, 6, 5, 4], 3: [14, 15, 0, 1, 2, 3, 4, 5, 6] },
  fill: { 1: [10, 11, 12, 13, 14, 15, 0, 1, 2], 2: [12, 11, 10, 9, 8, 7, 6, 5, 4] },
  // Pegasus, two cells past the chevron in the direction of travel, then back onto it.
  overshoot: { 1: [10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 3, 2], 2: [12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 3, 4] },
};

for (const [pattern, glyphs] of Object.entries(ROUTES)) {
  test(`${pattern}: the route each glyph's light takes round Standard's ring`, () => {
    const spin = spinOf(load("Standard"));
    for (const [glyph, route] of Object.entries(glyphs)) {
      assert.deepEqual(spin.path(pattern, Number(glyph)), route, `${pattern} glyph ${glyph}`);
    }
  });
}

const frames = (spin, pattern, glyph, ticks) =>
  Array.from({ length: ticks }, (_, t) => [...spin.lit(pattern, glyph, t, ticks)]);

test("fill lights the ring behind the light, where pegasus shows only a short run", () => {
  const spin = spinOf(load("Standard"));
  assert.deepEqual(frames(spin, "fill", 2, 10)[9], [12, 11, 10, 9, 8, 7, 6, 5, 4]);
  assert.deepEqual(frames(spin, "fill", 2, 10).slice(0, 3), [[12], [12, 11], [12, 11, 10]]);
  // Pegasus on the same route keeps a tail of two: the run is never longer.
  assert.deepEqual(frames(spin, "pegasus", 2, 10)[9], [5, 4]);
  assert.deepEqual(frames(spin, "pegasus", 2, 10)[0], [12]);
});

test("chevron hops over the frame a cell at a time and lands as the chevron alone", () => {
  const spin = spinOf(load("Standard"));
  assert.deepEqual(frames(spin, "chevron", 3, 10), [[3], [1], [1], [15], [13], [11], [11], [9], [7], [6]]);
});

test("chase slides from one chevron to the next with a short run behind it", () => {
  const spin = spinOf(load("Standard"));
  assert.deepEqual(frames(spin, "chase", 2, 10), [[2], [2], [2], [2, 3], [2, 3], [2, 3], [2, 3], [3, 4], [3, 4], [3, 4]]);
});

test("overshoot's light runs past the chevron and the last tick lands back on it", () => {
  const spin = spinOf(load("Standard"));
  const run = frames(spin, "overshoot", 1, 13);
  assert.ok(run.slice(0, 11).some((cells) => cells.includes(4)), "never ran past the chevron");
  assert.deepEqual(run[12], [3, 2]);
});

test("universe turns the ring the other way each glyph, carrying the top chevron round with it", () => {
  const spin = spinOf(load("Standard"));
  assert.equal(spin.turn(1), 14);
  assert.equal(spin.turn(2), -18);
  assert.deepEqual([0, 3, 6, 9].map((t) => [...spin.lit("universe", 1, t, 10)]), [[0], [5], [9], [14]]);
});

test("top leaves its light on the top chevron when a chevron locks, except after the last; the others leave none", () => {
  const spin = spinOf(load("Standard"));
  assert.deepEqual([...spin.rest("top", 3, 7)], [0]);
  assert.deepEqual([...spin.rest("top", 7, 7)], []);
  for (const pattern of ["lap", "chevron", "chase", "pegasus", "fill", "overshoot"]) {
    assert.deepEqual([...spin.rest(pattern, 3, 7)], [], pattern);
  }
  assert.deepEqual([...spin.rest("universe", 3, 7)].sort((a, b) => a - b), [0, 10, 12, 14]);
});

// Standard with its timing lines taken out, to see what the plugin assumes for them.
const withoutTimings = () => parseShape(readFileSync(`${SHAPES}Standard.shape`, "utf8")
  .split(/\r?\n/).filter((l) => !l.includes("LIGHT_TICKS") && !l.includes("WOOSH_TICKS")).join("\n"));

test("a shape that omits LIGHT_TICKS and WOOSH_TICKS gets three ticks of each, as StargateShape defaults them", () => {
  const shape = withoutTimings();
  assert.equal(shape.lightTicks, null);
  const schedule = dialSchedule(shape, null, "none", false);
  assert.deepEqual(schedule.glyphs.map((g) => g.lockAt), [0, 3, 6, 9, 12, 15, 18]);
  assert.equal(schedule.wooshTicks, 3);
  assert.equal(schedule.settleAt, 18 + LAST_CHEVRON_PAUSE_TICKS + 5 * 3);
});

test("a shape with no :W cells has no woosh to play: WOOSH_DEPTH is overwritten when it is read, so it opens at once", () => {
  const text = readFileSync(`${SHAPES}Standard.shape`, "utf8").replace(/:W#\d+/g, "");
  const shape = parseShape(text);
  assert.deepEqual(shape.wooshOrders, []);
  const schedule = dialSchedule(shape, null, "none", false);
  assert.equal(schedule.settleAt, schedule.wooshStart);
});
