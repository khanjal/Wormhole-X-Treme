// Run from the repository root with plain `node --test`; it reads the shipped .shape files.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseShape } from "../shape-parser.js";
import {
  STYLES, STEP_CAP, irisCells, openingOrder, closingOrder, irisStateAt,
} from "../iris-sweep.js";

const SHAPES = fileURLToPath(new URL("../../../src/main/resources/shapes/gate/", import.meta.url));
const load = (name) => parseShape(readFileSync(`${SHAPES}${name}.shape`, "utf8"));
// The upright gates the iris page shows: an opening of one layer.
const upright = readdirSync(SHAPES).filter((f) => f.endsWith(".shape")).map((f) => f.replace(".shape", ""))
  .filter((name) => new Set(irisCells(load(name)).map((c) => c.layer)).size === 1);

const sizes = (steps) => steps.map((s) => s.length);
const where = (cells, steps, n) => steps[n].map((i) => [cells[i].col, cells[i].y]);

test("the upright shipped shapes are the ones with a one-layer opening, and the flat Horizontal pair is not", () => {
  assert.deepEqual(upright, ["Grand", "Large", "Massive", "Minimal", "MinimalSignDial", "Standard", "StandardSignDial"]);
});

for (const name of upright) {
  const cells = irisCells(load(name));
  for (const style of STYLES) {
    test(`${name}, ${style}: every cell is covered exactly once, in at most ${STEP_CAP} steps`, () => {
      const steps = closingOrder(cells, style);
      assert.deepEqual(steps.flat().sort((a, b) => a - b), cells.map((_, i) => i));
      assert.ok(steps.length >= 1 && steps.length <= STEP_CAP, `${steps.length} steps`);
      assert.ok(steps.every((s) => s.length > 0), "a step with nothing in it");
    });

    test(`${name}, ${style}: opening is the closing order run backwards`, () => {
      assert.deepEqual(openingOrder(cells, style), closingOrder(cells, style).reverse());
    });
  }
}

test("Minimal is one step in every style", () => {
  const cells = irisCells(load("Minimal"));
  assert.equal(cells.length, 2);
  for (const style of STYLES) assert.deepEqual(sizes(closingOrder(cells, style)), [2], style);
});

test("Standard's sweep is five rings, rim first, with the middle cell last", () => {
  const cells = irisCells(load("Standard"));
  const steps = closingOrder(cells, "sweep");
  assert.deepEqual(sizes(steps), [8, 4, 4, 4, 1]);
  assert.deepEqual(where(cells, steps, 4), [[3, 3]]);
  // The rim ring is every cell furthest from the middle: none of its cells is nearer than the next ring's.
  const far = (i) => (cells[i].col - 3) ** 2 + (cells[i].y - 3) ** 2;
  assert.ok(Math.min(...steps[0].map(far)) > Math.max(...steps[1].map(far)));
});

test("Standard's rows close in from the top and bottom at once, and its columns from both sides", () => {
  const cells = irisCells(load("Standard"));
  const rows = closingOrder(cells, "rows");
  assert.deepEqual(sizes(rows), [6, 10, 5]);
  assert.deepEqual([...new Set(rows[0].map((i) => cells[i].y))].sort(), [1, 5]);
  assert.deepEqual([...new Set(rows[2].map((i) => cells[i].y))], [3]);
  const columns = closingOrder(cells, "columns");
  assert.deepEqual(sizes(columns), [6, 10, 5]);
  assert.deepEqual([...new Set(columns[0].map((i) => cells[i].col))].sort(), [1, 5]);
  assert.deepEqual([...new Set(columns[2].map((i) => cells[i].col))], [3]);
});

test("instant is the whole opening in one step", () => {
  const cells = irisCells(load("Standard"));
  assert.deepEqual(sizes(closingOrder(cells, "instant")), [21]);
});

test("Standard has five rings and Grand sixty-one, as the guide says; Grand merges into bands", () => {
  assert.equal(openingOrder(irisCells(load("Standard")), "sweep", 0).length, 5);
  const grand = irisCells(load("Grand"));
  assert.equal(openingOrder(grand, "sweep", 0).length, 61);
  assert.equal(closingOrder(grand, "sweep").length, STEP_CAP);
});

test("merging joins neighbouring rings, cut by position on the opening order, then reversed", () => {
  const cells = irisCells(load("Standard"));
  // Opening is 1, 4, 4, 4, 8 cells; cut into three it is [1], [4, 4], [4, 8].
  assert.deepEqual(sizes(closingOrder(cells, "sweep", 3)), [12, 8, 1]);
  assert.deepEqual(sizes(closingOrder(cells, "sweep", 5)), [8, 4, 4, 4, 1]);
});

test("the spiral takes as many steps as the sweep and splits the cells evenly", () => {
  const cells = irisCells(load("Standard"));
  const spiral = sizes(openingOrder(cells, "spiral"));
  assert.equal(spiral.length, openingOrder(cells, "sweep").length);
  assert.ok(Math.max(...spiral) - Math.min(...spiral) <= 1, spiral.join());
});

test("the spiral opens the middle first: the centre cell is in its first step", () => {
  const cells = irisCells(load("Standard"));
  const first = openingOrder(cells, "spiral")[0].map((i) => [cells[i].col, cells[i].y].join());
  assert.ok(first.includes("3,3"), first.join(" "));
});

test("an opening with no cells has no steps", () => {
  for (const style of STYLES) assert.deepEqual(closingOrder([], style), []);
});

test("the page's clock draws step one at tick 0 and a step every two ticks, closing then opening", () => {
  assert.deepEqual(irisStateAt(5, 0), { phase: "closing", step: 1, covered: 1, total: 46 });
  assert.equal(irisStateAt(5, 3).covered, 2);
  assert.equal(irisStateAt(5, 8).phase, "closed");
  assert.equal(irisStateAt(5, 20).phase, "closed");
  // 8 ticks to shut, 20 held, then the first opening step uncovers the last closing step.
  assert.deepEqual(irisStateAt(5, 28), { phase: "opening", step: 1, covered: 4, total: 46 });
  assert.equal(irisStateAt(5, 36).phase, "open");
  assert.equal(irisStateAt(5, 36).covered, 0);
});

test("a one-step iris closes at tick 0 and opens at once after the hold", () => {
  assert.equal(irisStateAt(1, 0).phase, "closed");
  assert.equal(irisStateAt(1, 20).phase, "open");
});
