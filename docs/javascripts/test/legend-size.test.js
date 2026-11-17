// Run from the repository root with plain `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { reserveHeight, overlayPlacement } from "../legend-size.js";

test("the reserve only grows, so a shorter legend never shrinks the stage", () => {
  let r = 0;
  r = reserveHeight(r, 310.2);
  assert.equal(r, 311);
  r = reserveHeight(r, 180);
  assert.equal(r, 311);
  r = reserveHeight(r, 420);
  assert.equal(r, 420);
});

test("a hidden legend measures zero and leaves the reserve alone", () => {
  assert.equal(reserveHeight(250, 0), 250);
  assert.equal(reserveHeight(250, NaN), 250);
  assert.equal(reserveHeight(250, -5), 250);
});

test("an overlay that fits below where it starts stays put, with no height limit", () => {
  assert.deepEqual(overlayPlacement(50, 300, 800), { shift: 0, maxHeight: null });
});

test("an overlay that would run past the window is lifted just enough, never above the window's top", () => {
  assert.deepEqual(overlayPlacement(300, 432, 667), { shift: 73, maxHeight: null });
});

test("only a window shorter than the list leaves a height limit, and never below a usable minimum", () => {
  assert.deepEqual(overlayPlacement(300, 600, 500), { shift: 292, maxHeight: 484 });
  assert.deepEqual(overlayPlacement(20, 600, 150), { shift: 12, maxHeight: 160 });
  assert.deepEqual(overlayPlacement(undefined, 300, 800), { shift: 0, maxHeight: null });
});
