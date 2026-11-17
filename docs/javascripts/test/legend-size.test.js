// Run from the repository root with plain `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { reserveHeight, overlayMaxHeight } from "../legend-size.js";

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

test("the overlay may use the room between where it starts and the lowest line it may reach, and never less than a short list needs to be usable", () => {
  assert.equal(overlayMaxHeight(52, 450), 390);
  assert.equal(overlayMaxHeight(52, 100), 160);
  assert.equal(overlayMaxHeight(undefined, undefined), 160);
});
