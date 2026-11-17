// Run from the repository root with plain `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { TINT, wooshStep, wooshTint, wooshCue } from "../woosh-cue.js";

test("the step is the place among the distinct orders: gaps are collapsed for tinting, though the Java plays a gap as an empty tick", () => {
  assert.equal(wooshStep(2, [2, 5, 9]), 0);
  assert.equal(wooshStep(5, [2, 5, 9]), 1);
  assert.equal(wooshStep(9, [2, 5, 9]), 2);
  assert.equal(wooshStep(null, [2, 5, 9]), -1);
  assert.equal(wooshStep(4, [2, 5, 9]), -1);
});

test("the corner badge shows step + 1, so :W#2, :W#5 and :W#9 read 1, 2 and 3", () => {
  const orders = [2, 5, 9];
  assert.deepEqual([2, 5, 9].map((n) => wooshStep(n, orders) + 1), [1, 2, 3]);
});

test("order 0 is never played, so it is no step even when it is listed", () => {
  assert.equal(wooshStep(0, [0, 1, 2]), -1);
  assert.equal(wooshStep(1, [0, 1, 2]), 1); // listed first, but the list a shape gives never holds 0
  assert.equal(wooshStep(0, []), -1);
});

test("the tint grows with each step, from the lightest to the darkest", () => {
  assert.equal(wooshTint(0, 4), TINT.min);
  assert.equal(wooshTint(3, 4), TINT.max);
  assert.ok(wooshTint(1, 4) > wooshTint(0, 4) && wooshTint(2, 4) > wooshTint(1, 4));
});

test("a lone step is the middle tint, and a bad step is the lightest", () => {
  assert.equal(wooshTint(0, 1), (TINT.min + TINT.max) / 2);
  for (const [s, n] of [[-1, 3], [3, 3], [0, 0], [NaN, 3], [1.5, 3]]) assert.equal(wooshTint(s, n), TINT.min);
});

test("stripes are drawn at every size and the number from 40 pixels, where two digits clear the letter", () => {
  assert.deepEqual(wooshCue(12), { stripes: true, number: false });
  assert.equal(wooshCue(39).number, false);
  assert.equal(wooshCue(40).number, true);
  assert.equal(wooshCue(48).number, true);
  assert.equal(wooshCue(NaN).number, false);
});
