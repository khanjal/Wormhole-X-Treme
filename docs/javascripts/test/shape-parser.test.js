// Run from the repository root with plain `node --test`; it reads the shipped .shape files.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseShape, describeShape } from "../shape-parser.js";

const SHAPES = fileURLToPath(new URL("../../../src/main/resources/shapes/gate/", import.meta.url));
const files = readdirSync(SHAPES).filter((f) => f.endsWith(".shape")).sort();
const load = (file) => parseShape(readFileSync(SHAPES + file, "utf8"));

// What each shipped shape holds, counted by hand from the file and the Markers table in docs/GATES.md.
const EXPECTED = {
  Grand: { width: 22, height: 22, layers: 11, chevrons: 112, lightSteps: 8, wooshSteps: 9, lightTicks: 15 },
  Horizontal: { width: 7, height: 4, layers: 7, chevrons: 8, lightSteps: 8, wooshSteps: 3, lightTicks: 10 },
  HorizontalSignDial: { width: 7, height: 4, layers: 7, chevrons: 8, lightSteps: 8, wooshSteps: 3, lightTicks: 10 },
  Large: { width: 10, height: 10, layers: 5, chevrons: 16, lightSteps: 8, wooshSteps: 4, lightTicks: 12 },
  Massive: { width: 23, height: 23, layers: 15, chevrons: 144, lightSteps: 8, wooshSteps: 13, lightTicks: 15 },
  Minimal: { width: 2, height: 4, layers: 2, chevrons: 1, lightSteps: 1, wooshSteps: 3, lightTicks: 6 },
  MinimalSignDial: { width: 3, height: 4, layers: 2, chevrons: 1, lightSteps: 1, wooshSteps: 3, lightTicks: 6 },
  Standard: { width: 7, height: 7, layers: 4, chevrons: 8, lightSteps: 8, wooshSteps: 3, lightTicks: 10 },
  StandardSignDial: { width: 7, height: 7, layers: 4, chevrons: 8, lightSteps: 8, wooshSteps: 3, lightTicks: 10 },
};

const markerCount = (shape, m) => shape.cells.filter((c) => c.markers.includes(m)).length;

test("all nine shipped shape files are covered", () => {
  assert.deepEqual(files.map((f) => f.replace(".shape", "")), Object.keys(EXPECTED).sort());
});

for (const file of files) {
  const name = file.replace(".shape", "");
  const want = EXPECTED[name];

  test(`${name}: name, grid, layers, chevrons and woosh steps match the file`, () => {
    const shape = load(file);
    const d = describeShape(shape);
    assert.equal(shape.name, name);
    assert.deepEqual(
      { width: d.width, height: d.height, layers: d.layers, chevrons: d.chevrons,
        lightSteps: d.lightSteps, wooshSteps: d.wooshSteps, lightTicks: shape.lightTicks },
      want,
    );
    assert.equal(shape.wooshTicks, 3);
  });

  test(`${name}: every layer holds a full grid of cells`, () => {
    const shape = load(file);
    assert.equal(shape.cells.length, shape.layers * shape.width * shape.height);
  });

  test(`${name}: exactly one activation switch and one player entry point`, () => {
    const shape = load(file);
    assert.equal(markerCount(shape, "A"), 1);
    assert.equal(markerCount(shape, "EP"), 1);
    for (const m of ["N", "D", "IA", "EM"]) assert.ok(markerCount(shape, m) <= 1, m);
  });

  test(`${name}: light order runs 1..n with no gap, and so does woosh`, () => {
    const shape = load(file);
    const run = (orders) => orders.map((_, i) => i + 1);
    assert.deepEqual(shape.lightOrders, run(shape.lightOrders));
    assert.deepEqual(shape.wooshOrders, run(shape.wooshOrders));
    assert.ok(shape.lightOrders.length >= 1);
  });

  test(`${name}: reads the same from LF and CRLF text`, () => {
    const text = readFileSync(SHAPES + file, "utf8").replace(/\r\n/g, "\n");
    assert.deepEqual(parseShape(text.replace(/\n/g, "\r\n")), parseShape(text));
  });
}

test("a sign dial has a D block and a redstone dial block; its plain twin has neither", () => {
  for (const base of ["Standard", "Minimal", "Horizontal"]) {
    assert.equal(describeShape(load(`${base}.shape`)).dialSign, false);
    const twin = describeShape(load(`${base}SignDial.shape`));
    assert.equal(twin.dialSign, true);
    assert.ok(twin.redstone.includes("RD"));
  }
});

test("Horizontal's ring lies in one row of every layer, so it is flat", () => {
  const shape = load("Horizontal.shape");
  const ring = shape.cells.filter((c) => c.block === "frame" || c.portal);
  assert.deepEqual([...new Set(ring.map((c) => c.y))], [0]);
  assert.equal(new Set(ring.map((c) => c.layer)).size, 7);
});

// Parameters combine as StargateShapeLayer.recordMarker combines them.
const tiny = (cells) =>
  parseShape(`Name=T\nVersion=2\nGateShape=\n\nLayer#1=\n${cells}\n\nLIGHT_TICKS = 4;\n`);

test("[S:L#3] is a frame cell that lights third; a bare :L lights first", () => {
  const [a, b] = tiny("[S:L#3][S:L][I:EP]").cells;
  assert.equal(a.block, "frame");
  assert.equal(a.light, 3);
  assert.equal(b.light, 1);
});

test("[P:W#2] is a portal cell that wooshes second", () => {
  const [a] = tiny("[P:W#2][I:EP]").cells;
  assert.equal(a.portal, true);
  assert.equal(a.woosh, 2);
});

test("[S:C] is a chevron cell, not a frame cell as well", () => {
  const [a, b] = tiny("[S:C][C][I:EP]").cells;
  assert.equal(a.block, "chevron");
  assert.equal(a.lenientChevron, true);
  assert.equal(b.block, "chevron");
  assert.equal(b.lenientChevron, false);
});

test("the first column of the file is the highest x, and the first row the highest y", () => {
  const cells = parseShape(
    "Name=T\nVersion=2\nGateShape=\n\nLayer#1=\n[S][I][I:EP]\n[I][I][S:A]\n\n").cells;
  const frame = cells.find((c) => c.block === "frame");
  assert.deepEqual([frame.x, frame.y], [2, 1]);
  const a = cells.find((c) => c.markers.includes("A"));
  assert.deepEqual([a.x, a.y], [0, 0]);
});

test("settings written 'KEY = value;' are read, and an unknown key is ignored", () => {
  const shape = tiny("[I:EP]");
  assert.equal(shape.lightTicks, 4);
  const odd = parseShape("Name=T\nVersion=2\nGateShape=\n\nLayer#1=\n[I:EP]\n\nFUTURE_KEY = 9;\nCHEVRON_MATERIAL=GOLD_BLOCK\n");
  assert.equal(odd.materials.CHEVRON_MATERIAL, "GOLD_BLOCK");
});
