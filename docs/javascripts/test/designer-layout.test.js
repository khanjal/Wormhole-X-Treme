// Run from the repository root with plain `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { ZOOM, clampZoom, stepZoom, fitZoom, wheelZoom, wheelPixels, clampSplit, splitBounds, parseViewState, viewStateText } from "../designer-layout.js";

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
  // the same box with 30 rows: height limits, (600 - 18 - 2) / 30 = 19.3
  assert.equal(fitZoom({ width: 600, height: 600, columns: 20, rows: 30, gutterX: 20, gutterY: 18 }), 19);
});

test("fit reads the height for the rows and the width for the columns", () => {
  assert.equal(fitZoom({ width: 900, height: 400, columns: 10, rows: 10 }), 39);
  assert.equal(fitZoom({ width: 400, height: 900, columns: 10, rows: 10 }), 39);
});

test("fit never goes above the largest size, nor below the legible size when nothing fits", () => {
  assert.equal(fitZoom({ width: 4000, height: 4000, columns: 3, rows: 3 }), ZOOM.max);
  assert.equal(fitZoom({ width: 300, height: 300, columns: 64, rows: 64 }), ZOOM.legible);
  assert.ok(ZOOM.legible > ZOOM.min, "the smallest sizes stay reachable by hand only");
});

test("fit takes a size above the legible one whenever the layer fits at it", () => {
  assert.equal(fitZoom({ width: 17 * 20 + 2, height: 9999, columns: 20, rows: 1 }), 17);
});

test("fit with an unusable box or an empty grid is the legible size", () => {
  assert.equal(fitZoom({ width: NaN, height: 500, columns: 5, rows: 5 }), ZOOM.legible);
  assert.equal(fitZoom({ width: 500, height: 500, columns: 0, rows: 5 }), ZOOM.legible);
  assert.equal(fitZoom({}), ZOOM.legible);
});

test("one scroll notch zooms by a step or two, in the scroll's direction", () => {
  const up = wheelZoom(40, -100);
  const down = wheelZoom(40, 100);
  assert.ok(up > 40 && up <= 48, String(up));
  assert.ok(down < 40 && down >= 30, String(down));
});

test("a pinch of many tiny events adds up to a modest change, not the end of the range", () => {
  let delta = 0;
  for (let i = 0; i < 60; i++) delta -= 2;
  const size = wheelZoom(24, delta);
  assert.ok(size > 24 && size < ZOOM.max, String(size));
  assert.equal(wheelZoom(40, -1), 40, "one tiny event does not move a whole pixel");
});

test("wheel zoom stays inside the range and ignores a delta that is not a number", () => {
  assert.equal(wheelZoom(40, -100000), ZOOM.max);
  assert.equal(wheelZoom(40, 100000), ZOOM.min);
  assert.equal(wheelZoom(40, NaN), 40);
});

test("a wheel delta is read in pixels whatever its unit, and capped", () => {
  assert.equal(wheelPixels(3, 1), 48);
  assert.equal(wheelPixels(1, 2), 100);
  assert.equal(wheelPixels(5, 0), 5);
  assert.equal(wheelPixels(1e9, 0), 400);
  assert.equal(wheelPixels(undefined), 0);
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
  const good = { zoom: 28, split: 0.45, dockOpen: false, toolsOpen: false };
  assert.deepEqual(parseViewState(viewStateText(good)), good);
  assert.deepEqual(parseViewState('{"zoom":"big","split":7,"dockOpen":"no"}'),
    { zoom: null, split: null, dockOpen: true, toolsOpen: true });
  assert.equal(parseViewState('{"zoom":9999}').zoom, ZOOM.max);
});

test("an expanded window is never restored, even from an old saved state", () => {
  assert.equal("expanded" in parseViewState('{"expanded":true,"zoom":30}'), false);
  assert.equal("expanded" in JSON.parse(viewStateText({ expanded: true, zoom: 30, split: null, dockOpen: true, toolsOpen: true })), false);
});

test("garbage in storage gives the plain defaults", () => {
  const plain = { zoom: null, split: null, dockOpen: true, toolsOpen: true };
  for (const raw of [null, undefined, "", "{", "null", "[]", "7", '"x"', '{"expanded":"true"}', '{"__proto__":{"expanded":true}}']) {
    assert.deepEqual(parseViewState(raw), plain, String(raw));
  }
});
