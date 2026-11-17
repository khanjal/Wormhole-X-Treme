// Run from the repository root with plain `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { reserveHeight, collapsedHeight, detailsName, overlayPlacement } from "../legend-size.js";

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

const at = (o) => ({ toggleTop: 200, toggleBottom: 230, natural: 300, viewport: 800, minTop: 48, ...o });

test("an overlay that fits below its button goes there", () => {
  assert.deepEqual(overlayPlacement(at({})), { top: 234, maxHeight: null });
});

test("one that does not fit below goes above the button, never up under the page header", () => {
  assert.deepEqual(overlayPlacement(at({ toggleTop: 400, toggleBottom: 430, viewport: 667 })), { top: 96, maxHeight: null });
});

test("with room neither side it sits against the window's bottom, below the page header", () => {
  assert.deepEqual(overlayPlacement(at({ toggleTop: 200, toggleBottom: 230, natural: 480, viewport: 600 })), { top: 112, maxHeight: null });
  assert.deepEqual(overlayPlacement(at({ toggleTop: 80, toggleBottom: 110, natural: 300, viewport: 420 })), { top: 112, maxHeight: null });
});

test("only a window shorter than the list leaves a height limit, below the header and never under a usable minimum", () => {
  assert.deepEqual(overlayPlacement(at({ natural: 700, viewport: 500 })), { top: 48, maxHeight: 444 });
  assert.deepEqual(overlayPlacement(at({ natural: 700, viewport: 150 })), { top: 48, maxHeight: 160 });
  assert.deepEqual(overlayPlacement({ natural: 300 }), { top: 0, maxHeight: null });
});

test("explanations a reader has opened do not count toward the legend's height", () => {
  assert.equal(collapsedHeight(520, [60, 45]), 415);
  assert.equal(collapsedHeight(520, []), 520);
  assert.equal(collapsedHeight(100, [300]), 0);
  assert.equal(collapsedHeight(520, [NaN, 20]), 500);
});

test("a button's name is the entry's words, without the glyph that leads the text", () => {
  assert.equal(detailsName(" \u2591 Woosh cell"), "Details: Woosh cell");
  assert.equal(detailsName("\u254E Dashed line: symmetry axis"), "Details: Dashed line: symmetry axis");
  assert.equal(detailsName("Underlined letter: extras"), "Details: Underlined letter: extras");
});
