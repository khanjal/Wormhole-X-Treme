// Run from the repository root with plain `node --test`; the designer's symmetry (mirror) mode.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseShape, SINGLES } from "../shape-parser.js";
import { fromParsed, newShape, cellAt, editCells, setKind, setOrder, setMarker, kindOf } from "../shape-edit.js";
import { MODES, flip, images, sourceOf, isSource, sourceSize, mirrorableMarkers, mirrorLayers } from "../symmetry.js";

const SHAPES = fileURLToPath(new URL("../../../src/main/resources/shapes/gate/", import.meta.url));
const shipped = (file) => fromParsed(parseShape(readFileSync(`${SHAPES}${file}.shape`, "utf8"))).shape;
const sorted = (pairs) => pairs.map(String).sort();

/** A blank grid of this size with no marker on it, so a test sees only what it painted. */
const blank = (width, height, layers = 1) => {
  const s = newShape({ width, height, layers });
  return editCells(s, 1, [[height - 1, Math.floor(width / 2)]], (c) => setMarker(c, "EP", false));
};
const paint = (shape, layer, row, col, kind, mode = "none") =>
  editCells(shape, layer, [[row, col], ...images(mode, row, col, shape.width, shape.height)], (c) => setKind(c, kind));

test("a place is across from its mirror, and the middle of an odd side is across from itself", () => {
  assert.equal(flip(0, 7), 6);
  assert.equal(flip(3, 7), 3);
  assert.equal(flip(0, 4), 3);
  assert.equal(flip(1, 4), 2);
  assert.equal(flip(0, 1), 0);
});

test("a cell's images follow the mode, for even and odd sides, and leave out the cell itself", () => {
  assert.deepEqual(images("none", 1, 2, 7, 5), []);
  assert.deepEqual(images("lr", 1, 2, 7, 5), [[1, 4]]);
  assert.deepEqual(images("tb", 1, 2, 7, 5), [[3, 2]]);
  assert.deepEqual(sorted(images("both", 1, 2, 7, 5)), sorted([[1, 4], [3, 2], [3, 4]]));
  assert.deepEqual(images("lr", 1, 1, 4, 4), [[1, 2]]);
  assert.deepEqual(images("lr", 1, 3, 7, 5), [], "the centre column of an odd grid maps to itself");
  assert.deepEqual(images("both", 2, 3, 7, 5), [], "the centre cell has no image at all");
  assert.deepEqual(images("both", 2, 1, 7, 5), [[2, 5]], "a cell on the middle row has only its left-right image");
  assert.deepEqual(images("both", 0, 3, 7, 5), [[4, 3]], "and one on the middle column only its top-bottom image");
});

test("the source is the left half, the top half or the top-left quarter, with the middle row and column of an odd grid in it", () => {
  assert.deepEqual(sourceOf("lr", 4, 6, 7, 5), [4, 0]);
  assert.deepEqual(sourceOf("lr", 4, 3, 7, 5), [4, 3]);
  assert.deepEqual(sourceOf("tb", 4, 6, 7, 5), [0, 6]);
  assert.deepEqual(sourceOf("both", 4, 6, 7, 5), [0, 0]);
  assert.deepEqual(sourceOf("both", 3, 4, 7, 5), [1, 2]);
  assert.deepEqual(sourceOf("none", 3, 4, 7, 5), [3, 4]);
  assert.deepEqual(sourceSize("both", 7, 5), { rows: 3, cols: 4 });
  assert.deepEqual(sourceSize("both", 6, 4), { rows: 2, cols: 3 });
  assert.deepEqual(sourceSize("lr", 7, 5), { rows: 5, cols: 4 });
  assert.equal(isSource("lr", 0, 3, 7, 5), true);
  assert.equal(isSource("lr", 0, 4, 7, 5), false);
});

test("every cell has exactly the images the source says, in every mode and size", () => {
  for (const mode of MODES) {
    for (const [w, h] of [[1, 1], [2, 4], [3, 4], [7, 7], [10, 5], [23, 22]]) {
      for (let r = 0; r < h; r++) {
        for (let c = 0; c < w; c++) {
          for (const [ir, ic] of images(mode, r, c, w, h)) {
            assert.deepEqual(sourceOf(mode, ir, ic, w, h), sourceOf(mode, r, c, w, h), `${mode} ${w}x${h} (${r},${c}) -> (${ir},${ic})`);
          }
        }
      }
    }
  }
});

test("painting through the images fills the other side, and the centre column of an odd grid is painted once", () => {
  let s = blank(7, 5);
  s = paint(s, 1, 1, 1, "S", "both");
  const painted = [];
  s.cells.forEach((c) => { if (kindOf(c) === "S") painted.push([c.row, c.col]); });
  assert.deepEqual(sorted(painted), sorted([[1, 1], [1, 5], [3, 1], [3, 5]]));
  s = paint(s, 1, 2, 3, "C", "both");
  assert.equal(kindOf(cellAt(s, 1, 2, 3)), "C", "the middle cell takes it");
  assert.equal(s.cells.filter((c) => kindOf(c) === "C").length, 1);
});

test("light and woosh orders set on a cell and on its images carry the same number", () => {
  let s = blank(7, 5);
  const places = [[1, 1], ...images("lr", 1, 1, 7, 5)];
  s = editCells(s, 1, places, (c) => setOrder(c, "light", 3));
  assert.equal(cellAt(s, 1, 1, 1).light, 3);
  assert.equal(cellAt(s, 1, 1, 5).light, 3);
  assert.deepEqual(s.lightOrders, [3]);
  s = editCells(s, 1, places, (c) => setOrder(c, "light", null));
  assert.equal(cellAt(s, 1, 1, 5).light, null);
});

test("the one-per-gate markers are never mirrored, and every marker the plugin reads is one", () => {
  for (const m of ["N", "EP", "EM", "A", "D", "IA", "RD", "RS", "RA"]) {
    assert.deepEqual(mirrorableMarkers([m]), [], m);
    assert.ok(SINGLES.has(m), m);
  }
  assert.deepEqual(mirrorableMarkers(["EP", "X"]), ["X"], "a marker that were not one per gate would be copied");
});

test("Mirror now copies the left half over the right and leaves a one-per-gate marker where it is", () => {
  let s = blank(7, 5, 2);
  s = paint(s, 1, 0, 1, "S");
  s = paint(s, 1, 2, 0, "P");
  s = editCells(s, 1, [[2, 0]], (c) => { setOrder(c, "woosh", 2); setMarker(c, "A", true); });
  s = editCells(s, 1, [[2, 6]], (c) => setMarker(c, "EP", true));
  const { shape, changed } = mirrorLayers(s, [1], "lr");
  assert.equal(changed, 2);
  assert.equal(kindOf(cellAt(shape, 1, 0, 5)), "S");
  const right = cellAt(shape, 1, 2, 6);
  assert.equal(kindOf(right), "P");
  assert.equal(right.woosh, 2, "the order is copied, so the two light together");
  assert.deepEqual(right.markers, ["EP"], "the marker it had stays, and :A is not copied onto it");
  assert.deepEqual(cellAt(shape, 1, 2, 0).markers, ["A"]);
  assert.equal(kindOf(cellAt(shape, 2, 0, 5)), "I", "another layer is left alone");
});

test("Mirror now says how many cells it changed and how many of them held something", () => {
  let s = blank(4, 4);
  s = paint(s, 1, 0, 0, "S");
  s = paint(s, 1, 0, 3, "P");
  const r = mirrorLayers(s, [1], "lr");
  assert.deepEqual([r.changed, r.overwritten], [1, 1]);
  const again = mirrorLayers(r.shape, [1], "lr");
  assert.equal(again.shape, r.shape, "already mirrored: the very same shape, so no history step");
  assert.equal(again.changed, 0);
  assert.equal(mirrorLayers(s, [1], "none").shape, s);
});

test("Mirror now in both directions copies the top-left quarter into the other three", () => {
  let s = blank(6, 4);
  s = paint(s, 1, 0, 1, "C");
  const { shape, changed, overwritten } = mirrorLayers(s, [1], "both");
  assert.equal(changed, 3);
  assert.equal(overwritten, 0);
  const at = shape.cells.filter((c) => kindOf(c) === "C").map((c) => [c.row, c.col]);
  assert.deepEqual(sorted(at), sorted([[0, 1], [0, 4], [3, 1], [3, 4]]));
});

/** The layers of a shipped shape whose kinds are the same on both sides of the axes of this mode. */
function symmetricLayers(shape, mode) {
  const out = [];
  for (let l = 1; l <= shape.layers; l++) {
    let same = true;
    for (let r = 0; r < shape.height; r++) {
      for (let c = 0; c < shape.width; c++) {
        const [sr, sc] = sourceOf(mode, r, c, shape.width, shape.height);
        if (kindOf(cellAt(shape, l, r, c)) !== kindOf(cellAt(shape, l, sr, sc))) same = false;
      }
    }
    if (same) out.push(l);
  }
  return out;
}

/** The shape's kinds as text, layer by layer, for comparing two shapes. */
const kindsOf = (shape, layers) => layers.map((l) => shape.cells.filter((c) => c.layer === l).map(kindOf).join("")).join("/");

for (const [file, mode, minLayers] of [["Standard", "both", 3], ["Standard", "lr", 3], ["Standard", "tb", 3], ["StandardSignDial", "both", 3], ["Horizontal", "lr", 7]]) {
  test(`${file} drawn in its ${mode} source, painted with the mirror on, is the shipped shape on the layers that are symmetric`, () => {
    const real = shipped(file);
    const layers = symmetricLayers(real, mode);
    assert.ok(layers.length >= minLayers, `${file} should have ${minLayers} symmetric layers in ${mode}, found ${layers.join(",")}`);
    let drawn = blank(real.width, real.height, real.layers);
    for (const l of layers) {
      for (let r = 0; r < real.height; r++) {
        for (let c = 0; c < real.width; c++) {
          if (isSource(mode, r, c, real.width, real.height)) drawn = paint(drawn, l, r, c, kindOf(cellAt(real, l, r, c)), mode);
        }
      }
    }
    assert.equal(kindsOf(drawn, layers), kindsOf(real, layers));
    // And the Mirror now button gives the same from a shape with only the source drawn.
    let half = blank(real.width, real.height, real.layers);
    for (const l of layers) {
      for (let r = 0; r < real.height; r++) {
        for (let c = 0; c < real.width; c++) {
          if (isSource(mode, r, c, real.width, real.height)) half = paint(half, l, r, c, kindOf(cellAt(real, l, r, c)));
        }
      }
    }
    assert.equal(kindsOf(mirrorLayers(half, layers, mode).shape, layers), kindsOf(real, layers));
  });
}

test("the shipped shapes that are not symmetric are left out, so the check above cannot pass by accident", () => {
  assert.equal(symmetricLayers(shipped("Grand"), "both").length < shipped("Grand").layers, true);
  assert.equal(symmetricLayers(shipped("Standard"), "lr").includes(4), false, "Standard's switch layer sits to one side");
  assert.equal(readdirSync(SHAPES).filter((f) => f.endsWith(".shape")).length >= 9, true);
});
