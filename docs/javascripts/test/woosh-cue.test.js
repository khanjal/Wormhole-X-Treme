// Run from the repository root with plain `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { TINT, BADGE_MIN_ZOOM, wooshStep, wooshTint, wooshCue } from "../woosh-cue.js";

test("a woosh order is its place among the distinct orders, so gaps do not skip a tint", () => {
  assert.equal(wooshStep(2, [2, 5, 9]), 0);
  assert.equal(wooshStep(9, [2, 5, 9]), 2);
  assert.equal(wooshStep(null, [2, 5, 9]), -1);
  assert.equal(wooshStep(4, [2, 5, 9]), -1);
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

test("stripes are drawn at every size and the number only where it fits", () => {
  assert.deepEqual(wooshCue(12), { stripes: true, number: false });
  assert.equal(wooshCue(BADGE_MIN_ZOOM - 1).number, false);
  assert.equal(wooshCue(BADGE_MIN_ZOOM).number, true);
  assert.equal(wooshCue(48).number, true);
  assert.equal(wooshCue(NaN).number, false);
});
