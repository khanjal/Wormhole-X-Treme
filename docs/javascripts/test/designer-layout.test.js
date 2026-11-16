// Run from the repository root with plain `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { ZOOM, clampZoom, stepZoom, fitZoom, clampSplit, splitBounds, parseViewState, viewStateText } from "../designer-layout.js";

test("a cell size is held between the smallest and largest, in whole pixels", () => {
  assert.equal(clampZoom(5), ZOOM.min);
  assert.equal(clampZoom(500), ZOOM.max);
  assert.equal(clampZoom(23.6), 24);
  assert.equal(clampZoom(ZOOM.min), ZOOM.min);
  assert.equal(clampZoom(ZOOM.max), ZOOM.max);
});

test("a cell size that is not a number is the start size", () => {
  for (const bad of [NaN, Infinity, undefined, null, "40", {}]) assert.equal(clampZoom(bad), ZOOM.start);
});

test("stepping goes one step each way and stops at the ends", () => {
  assert.equal(stepZoom(40, 1), 44);
  assert.equal(stepZoom(40, -1), 36);
  assert.equal(stepZoom(ZOOM.max, 1), ZOOM.max);
  assert.equal(stepZoom(ZOOM.min, -1), ZOOM.min);
});

test("fit picks the largest size whose whole layer fits, limited by the tighter side", () => {
  // 20 columns in 600 px wide: width limits, (600 - 20 header - 2) / 20 = 28.9
  assert.equal(fitZoom({ width: 600, height: 600, columns: 20, rows: 10, gutterX: 20, gutterY: 18 }), 28);
  // the same box with 40 rows: height limits, (600 - 18 - 2) / 40 = 14.5
  assert.equal(fitZoom({ width: 600, height: 600, columns: 20, rows: 40, gutterX: 20, gutterY: 18 }), 14);
});

test("fit reads the height for the rows and the width for the columns", () => {
  assert.equal(fitZoom({ width: 900, height: 400, columns: 10, rows: 10 }), 39);
  assert.equal(fitZoom({ width: 400, height: 900, columns: 10, rows: 10 }), 39);
});

test("fit never goes above the largest size, nor below the smallest when nothing fits", () => {
  assert.equal(fitZoom({ width: 4000, height: 4000, columns: 3, rows: 3 }), ZOOM.max);
  assert.equal(fitZoom({ width: 300, height: 300, columns: 64, rows: 64 }), ZOOM.min);
});

test("fit with an unusable box or an empty grid is the smallest size", () => {
  assert.equal(fitZoom({ width: NaN, height: 500, columns: 5, rows: 5 }), ZOOM.min);
  assert.equal(fitZoom({ width: 500, height: 500, columns: 0, rows: 5 }), ZOOM.min);
  assert.equal(fitZoom({}), ZOOM.min);
});

test("the split keeps both sides at their smallest size", () => {
  assert.equal(clampSplit(0.01, 1000, 200, 300), 0.2);
  assert.equal(clampSplit(0.99, 1000, 200, 300), 0.7);
  assert.equal(clampSplit(0.5, 1000, 200, 300), 0.5);
  assert.equal(clampSplit(NaN, 1000, 200, 300), 0.6);
});

test("a split with no room for both sides sits in the middle", () => {
  assert.deepEqual(splitBounds(300, 200, 200), { lo: 0.5, hi: 0.5 });
  assert.equal(clampSplit(0.9, 300, 200, 200), 0.5);
});

test("a stored view state is read, and a damaged one falls back part by part", () => {
  const good = { expanded: true, zoom: 28, split: 0.45, dockOpen: false, toolsOpen: false };
  assert.deepEqual(parseViewState(viewStateText(good)), good);
  assert.deepEqual(parseViewState('{"expanded":true,"zoom":"big","split":7,"dockOpen":"no"}'),
    { expanded: true, zoom: null, split: null, dockOpen: true, toolsOpen: true });
  assert.equal(parseViewState('{"zoom":9999}').zoom, ZOOM.max);
});

test("garbage in storage gives the plain defaults", () => {
  const plain = { expanded: false, zoom: null, split: null, dockOpen: true, toolsOpen: true };
  for (const raw of [null, undefined, "", "{", "null", "[]", "7", '"x"', '{"expanded":"true"}', '{"__proto__":{"expanded":true}}']) {
    assert.deepEqual(parseViewState(raw), plain, String(raw));
  }
});
