// Run from the repository root with plain `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { BANDS_MAX, wooshStep, wooshBands, wooshBand, wooshTint, wooshCue, partitionCells, withoutWooshTag, parseRgb } from "../woosh-cue.js";

test("the step is the place among the distinct orders: gaps are collapsed for tinting (the checks refuse a gap anyway)", () => {
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
  assert.equal(wooshStep(1, [0, 1, 2]), 1);
  assert.equal(wooshStep(0, []), -1);
});

test("a woosh of up to five steps has a band for each step", () => {
  for (let steps = 1; steps <= 5; steps++) {
    assert.equal(wooshBands(steps), steps);
    assert.deepEqual(Array.from({ length: steps }, (_, i) => wooshBand(i, steps)), Array.from({ length: steps }, (_, i) => i));
  }
});

test("a longer woosh shares its steps out over five bands, in order, the first band lightest and the last the strongest", () => {
  for (const steps of [6, 9, 13, 30]) {
    assert.equal(wooshBands(steps), BANDS_MAX);
    const bands = Array.from({ length: steps }, (_, i) => wooshBand(i, steps));
    assert.equal(bands[0], 0);
    assert.equal(bands[steps - 1], BANDS_MAX - 1);
    assert.deepEqual(bands, [...bands].sort((a, b) => a - b));
    assert.equal(new Set(bands).size, BANDS_MAX);
  }
});

test("the tint runs from 0 to 1 over the bands, a lone band is the middle and a bad step the lightest", () => {
  assert.equal(wooshTint(0, 13), 0);
  assert.equal(wooshTint(12, 13), 1);
  assert.equal(wooshTint(0, 1), 0.5);
  assert.deepEqual([0, 1, 2].map((i) => wooshTint(i, 3)), [0, 0.5, 1]);
  for (const [s, n] of [[-1, 3], [3, 3], [0, 0], [NaN, 3], [1.5, 3]]) assert.equal(wooshTint(s, n), 0);
});

test("neighbouring steps of a nine-step woosh differ in tint wherever the band changes, and the whole range is used", () => {
  const tints = Array.from({ length: 9 }, (_, i) => wooshTint(i, 9));
  assert.equal(Math.min(...tints), 0);
  assert.equal(Math.max(...tints), 1);
  assert.equal(new Set(tints).size, 5);
});

test("stripes are drawn at every size and the number from 40 pixels, where two digits clear the letter", () => {
  assert.deepEqual(wooshCue(12), { stripes: true, number: false });
  assert.equal(wooshCue(39).number, false);
  assert.equal(wooshCue(40).number, true);
  assert.equal(wooshCue(48).number, true);
  assert.equal(wooshCue(NaN).number, false);
});

const cell = (over) => ({ portal: false, woosh: null, ...over });

test("a [P:W#0] cell is an ordinary portal cell: it never wooshes, but the plugin still fills it", () => {
  const zero = cell({ portal: true, woosh: 0 });
  const { portal, wave, stay } = partitionCells([zero, cell({ portal: true }), cell({ portal: true, woosh: 1 }), cell({ woosh: 1 })]);
  assert.equal(portal.length, 2);
  assert.ok(portal.includes(zero));
  assert.equal(stay.length, 1);
  assert.equal(wave.length, 1);
});

test("an [I:W#0] cell is in no mesh, and the wave and stay lists come out in step order", () => {
  const cells = [cell({ woosh: 0 }), cell({ woosh: 3 }), cell({ woosh: 1 }), cell({ woosh: 2, portal: true }), cell({ woosh: 1, portal: true })];
  const { portal, wave, stay } = partitionCells(cells);
  assert.equal(portal.length, 0);
  assert.deepEqual(wave.map((c) => c.woosh), [1, 3]);
  assert.deepEqual(stay.map((c) => c.woosh), [1, 2]);
});

test("the woosh tag is taken out of a description wherever it stands among the tags", () => {
  assert.equal(withoutWooshTag("Ignored, :W#1"), "Ignored");
  assert.equal(withoutWooshTag("Ignored, :L#2 :W#3"), "Ignored, :L#2");
  assert.equal(withoutWooshTag("Ignored, :W#3 :EP"), "Ignored, :EP");
  assert.equal(withoutWooshTag("Portal, :EP :L#2 :W"), "Portal, :EP :L#2");
  assert.equal(withoutWooshTag("Stargate material"), "Stargate material");
  assert.equal(withoutWooshTag("Ignored, :WX"), "Ignored, :WX");
});

test("a computed colour is read in rgb() and rgba(), comma or space syntax, and anything else is the fallback", () => {
  const f = [1, 2, 3];
  assert.deepEqual(parseRgb("rgb(245, 245, 245)", f), [245, 245, 245]);
  assert.deepEqual(parseRgb("rgba(39, 42, 53, 0.5)", f), [39, 42, 53]);
  assert.deepEqual(parseRgb("rgb(39 42 53)", f), [39, 42, 53]);
  assert.deepEqual(parseRgb("rgb(39 42 53 / 50%)", f), [39, 42, 53]);
  for (const bad of ["", "transparent", "#fff", "rgb(1, 2)", "rgb(300, 0, 0)", undefined, "color(srgb 1 0 0)"]) assert.deepEqual(parseRgb(bad, f), f);
});
