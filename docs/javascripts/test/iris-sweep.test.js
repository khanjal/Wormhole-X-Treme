// Run from the repository root with plain `node --test`; it reads the shipped .shape files.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseShape } from "../shape-parser.js";
import {
  STYLES, STEP_CAP, irisCells, openingOrder, closingOrder, irisStateAt, closingArm,
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
  assert.deepEqual(irisStateAt(5, 0), { phase: "closing", step: 1, covered: 1, arriving: true, total: 46 });
  assert.equal(irisStateAt(5, 3).covered, 2);
  assert.equal(irisStateAt(5, 8).phase, "closed");
  assert.equal(irisStateAt(5, 20).phase, "closed");
  // 8 ticks to shut, 20 held, then the first opening step uncovers the last closing step.
  assert.deepEqual(irisStateAt(5, 28), { phase: "opening", step: 1, covered: 4, arriving: false, total: 46 });
  assert.equal(irisStateAt(5, 36).phase, "open");
  assert.equal(irisStateAt(5, 36).covered, 0);
});

test("a one-step iris closes at tick 0 and opens at once after the hold", () => {
  assert.equal(irisStateAt(1, 0).phase, "closed");
  assert.equal(irisStateAt(1, 20).phase, "open");
});

test("closing, the spiral arm turns clockwise seen from the switch: below the middle, left, above, then right", () => {
  const cells = irisCells(load("Standard"));
  const order = closingArm(cells).map((i) => `${cells[i].col},${cells[i].y}`);
  const at = (col, y) => order.indexOf(`${col},${y}`);
  // The four cells a block from the middle (3,3), in the order the arm passes them, read round from
  // whichever it reaches first; columns count up towards the viewer's right.
  const ring = [at(3, 2), at(2, 3), at(3, 4), at(4, 3)];
  const first = ring.indexOf(Math.min(...ring));
  const round = [...ring.slice(first), ...ring.slice(0, first)];
  assert.ok(round.every((n, k) => k === 0 || n > round[k - 1]), order.join(" "));
});

// The plugin's own spiral on a built gate, in shape coordinates, written by IrisSpiralGoldenTest.
const GOLDEN = fileURLToPath(new URL("../../../src/test/resources/iris/spiral-golden.txt", import.meta.url));

test("the spiral is the plugin's, step for step, on every golden shape", () => {
  const lines = readFileSync(GOLDEN, "utf8").split(/\r?\n/).filter((l) => l && !l.startsWith("#"));
  const names = [...new Set(lines.map((l) => l.split(" ")[0]))];
  assert.deepEqual(names, ["Standard", "Large", "Massive", "Grand"]);
  for (const name of names) {
    const cells = irisCells(load(name));
    const ours = closingOrder(cells, "spiral").map((step, n) => {
      const at = step.map((i) => cells[i]).sort((a, b) => a.y - b.y || a.x - b.x);
      return `${name} ${n + 1}: ${at.map((c) => `${c.x},${c.y}`).join(" ")}`;
    });
    assert.deepEqual(ours, lines.filter((l) => l.startsWith(`${name} `)), name);
  }
});

test("across a wide arm the outer cell is covered first, as IrisSweepTest pins it", () => {
  // A disc of radius 12 has a three-block arm; straight up, 12 and 11 out sit at one place along it.
  const disc = [];
  for (let col = -12; col <= 12; col++) {
    for (let y = -12; y <= 12; y++) if (col * col + y * y <= 144) disc.push({ col, y, layer: 1 });
  }
  const arm = closingArm(disc);
  const at = (col, y) => arm.findIndex((i) => disc[i].col === col && disc[i].y === y);
  assert.equal(at(0, 11), at(0, 12) + 1);
});

test("the closing arm starts on the rim and ends in the middle", () => {
  const cells = irisCells(load("Standard"));
  const arm = closingArm(cells);
  const far = (i) => Math.hypot(cells[i].col - 3, cells[i].y - 3);
  assert.equal(far(arm[0]), Math.max(...arm.map(far)), "the first cell covered is one of the outermost");
  assert.deepEqual([cells[arm.at(-1)].col, cells[arm.at(-1)].y], [3, 3]);
});

// How many times the arm comes back round past straight up.
const turnsOf = (cells, arm) => {
  const xs = cells.map((c) => c.col);
  const ys = cells.map((c) => c.y);
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2;
  const cy = (Math.min(...ys) + Math.max(...ys)) / 2;
  const angle = (i) => (Math.atan2(cells[i].col - cx, cells[i].y - cy) + 2 * Math.PI) % (2 * Math.PI);
  return arm.filter((i, n) => n > 0 && angle(i) < angle(arm[n - 1])).length;
};

test("the arm is a block wide on Standard and wider on Grand, so both wind in a few turns", () => {
  const standard = irisCells(load("Standard"));
  const small = turnsOf(standard, closingArm(standard));
  assert.ok(small >= 2, `Standard goes round more than once: ${small}`);
  const grand = irisCells(load("Grand"));
  const big = turnsOf(grand, closingArm(grand));
  assert.ok(big >= 3 && big <= 6, `Grand winds in about five turns, not ten and not one: ${big}`);
});
