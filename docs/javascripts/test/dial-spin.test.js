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
