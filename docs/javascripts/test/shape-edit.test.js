// Run from the repository root with plain `node --test`; the designer's model and its history.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseShape } from "../shape-parser.js";
import {
  fromParsed, newShape, cellAt, editCell, setKind, setMarker, setOrder, kindOf, addLayer, removeLayer, addRow, removeRow,
  addColumn, removeColumn, resize, EditError, History, draftText, readDraft, fileNameFor, customName, nextLightOrder, withSettings,
} from "../shape-edit.js";

const SHAPES = fileURLToPath(new URL("../../../src/main/resources/shapes/gate/", import.meta.url));
const standard = () => fromParsed(parseShape(readFileSync(`${SHAPES}Standard.shape`, "utf8")));

/** Every cell sits at its own index, and its world position follows from the grid. */
function assertTidy(s) {
  assert.equal(s.cells.length, s.width * s.height * s.layers);
  s.cells.forEach((c, i) => {
    assert.equal(i, ((c.layer - 1) * s.height + c.row) * s.width + c.col);
    assert.deepEqual([c.x, c.y], [s.width - 1 - c.col, s.height - 1 - c.row]);
  });
}

test("a shipped shape loads as a full grid with nothing to say", () => {
  const { shape, notes } = standard();
  assert.deepEqual(notes, []);
  assert.deepEqual([shape.width, shape.height, shape.layers], [7, 7, 4]);
  assertTidy(shape);
});

test("a file with a short row, a long row and a skipped layer is padded and trimmed, with a note", () => {
  const text = "Name=Odd\nVersion=2\nGateShape=\nLayer#1=\n[I][I:EP]\n[I]\n\nLayer#3=\n[I][I][S]\n[I][I]\n\n";
  const { shape, notes } = fromParsed(parseShape(text));
  assert.deepEqual([shape.width, shape.height, shape.layers], [2, 2, 3]);
  assertTidy(shape);
  assert.equal(notes.length, 2);
  assert.match(notes[0], /left out/);
  assert.match(notes[1], /dropped/);
});

test("editing a cell leaves the old shape as it was and updates the steps", () => {
  const { shape } = standard();
  const next = editCell(shape, 1, 0, 0, (c) => { setKind(c, "S"); setOrder(c, "light", 9); });
  assert.equal(cellAt(shape, 1, 0, 0).light, null);
  assert.equal(cellAt(next, 1, 0, 0).light, 9);
  assert.equal(next.lightOrders.at(-1), 9);
  assert.equal(nextLightOrder(next), 10);
  assert.equal(cellAt(next, 1, 3, 3), cellAt(shape, 1, 3, 3));
});

test("kinds: portal wins the label, and setting a kind clears the others", () => {
  const { shape } = standard();
  const c = cellAt(shape, 1, 3, 3);
  assert.equal(kindOf(c), "P");
  const copy = { ...c, markers: [] };
  setKind(copy, "C");
  assert.deepEqual([kindOf(copy), copy.portal], ["C", false]);
  setKind(copy, "I");
  assert.deepEqual([copy.block, copy.portal], ["none", false]);
});

test("adding a layer puts a blank one in place and numbers the rest on", () => {
  const { shape } = standard();
  const next = addLayer(shape, 1);
  assertTidy(next);
  assert.equal(next.layers, 5);
  assert.deepEqual(cellAt(next, 2, 0, 3), { ...cellAt(next, 2, 0, 3), block: "none", markers: [], light: null, woosh: null });
  assert.equal(cellAt(next, 3, 1, 2).woosh, 1);
  assert.equal(cellAt(addLayer(shape, 0), 2, 0, 3).block, "frame");
});

test("removing a layer and a row or a column keeps the grid whole", () => {
  const { shape } = standard();
  const noLayer = removeLayer(shape, 2);
  assert.equal(noLayer.layers, 3);
  assert.equal(cellAt(noLayer, 2, 2, 3).woosh, 2);
  assertTidy(noLayer);
  const noRow = removeRow(shape, 0);
  assert.equal(noRow.height, 6);
  assert.equal(cellAt(noRow, 1, 0, 1).block, "frame");
  assertTidy(noRow);
  const noCol = removeColumn(shape, 0);
  assert.equal(noCol.width, 6);
  assertTidy(noCol);
  const wider = addColumn(shape, -1);
  assert.deepEqual([wider.width, cellAt(wider, 1, 0, 0).block, cellAt(wider, 1, 0, 3).block], [8, "none", "frame"]);
  const taller = addRow(shape, 6);
  assert.deepEqual([taller.height, cellAt(taller, 1, 7, 0).block], [8, "none"]);
  assertTidy(taller);
});

test("the last layer, row or column cannot be removed", () => {
  const s = newShape({ width: 1, height: 1, layers: 1 });
  for (const remove of [() => removeLayer(s, 1), () => removeRow(s, 0), () => removeColumn(s, 0)]) {
    assert.throws(remove, EditError);
  }
});

test("resize cuts or pads at the right, the bottom and the far layer, and refuses what is too big", () => {
  const { shape } = standard();
  const small = resize(shape, 3, 2, 2);
  assert.deepEqual([small.width, small.height, small.layers], [3, 2, 2]);
  assertTidy(small);
  assert.equal(cellAt(small, 1, 0, 2).block, "frame");
  const big = resize(shape, 9, 8, 5);
  assertTidy(big);
  assert.equal(cellAt(big, 4, 5, 5).markers[0], "A");
  assert.throws(() => resize(shape, 65, 7, 4), EditError);
  assert.throws(() => resize(shape, 64, 64, 6), /20000/);
  assert.throws(() => addLayer(resize(newShape(), 7, 7, 64), 1), EditError);
});

test("undo and redo walk the history and a new change drops what was redone", () => {
  const h = new History(3);
  h.record("a");
  h.record("b");
  assert.equal(h.undo("c"), "b");
  assert.equal(h.undo("b"), "a");
  assert.equal(h.undo("a"), null);
  assert.equal(h.redo("a"), "b");
  h.record("x");
  assert.equal(h.redo("y"), null);
  for (const v of ["1", "2", "3", "4"]) h.record(v);
  assert.equal(h.past.length, 3);
});

test("a saved draft comes back as it was, and anything else is refused", () => {
  const { shape } = standard();
  const edited = withSettings(editCell(shape, 1, 0, 0, (c) => setMarker(c, "RD", true)), { name: "Mine", lightTicks: 4 });
  assert.deepEqual(readDraft(draftText(edited)), edited);
  const parsed = JSON.parse(draftText(edited));
  for (const bad of [
    "", "{", "null", "[]", '{"v":2}', JSON.stringify({ v: 1, shape: { ...parsed.shape, width: 99 } }),
    JSON.stringify({ v: 1, shape: { ...parsed.shape, cells: parsed.shape.cells.slice(1) } }),
    JSON.stringify({ v: 1, shape: { ...parsed.shape, cells: "no" } }),
  ]) assert.equal(readDraft(bad), null, bad.slice(0, 40));
});

test("a draft keeps only what the designer writes: unknown markers and materials are dropped", () => {
  const parsed = JSON.parse(draftText(standard().shape));
  parsed.shape.cells[0].markers = ["EP", "<img>"];
  parsed.shape.cells[0].light = -3;
  parsed.shape.materials = { PORTAL_MATERIAL: "WATER", EVIL: "x" };
  parsed.shape.extra = ["SOUND_SCALE=2", "Layer#1=", "SOUND_SCALE=1\nName=x"];
  const got = readDraft(JSON.stringify(parsed));
  assert.deepEqual([got.cells[0].markers, got.cells[0].light], [["EP"], null]);
  assert.deepEqual(got.materials, { PORTAL_MATERIAL: "WATER" });
  assert.deepEqual(got.extra, ["SOUND_SCALE=2"]);
});

test("a download is named after the shape, in letters, digits, _ and - only", () => {
  assert.equal(fileNameFor("Standard"), "Standard.shape");
  assert.equal(fileNameFor("My gate/../x"), "My_gate_x.shape");
  assert.equal(fileNameFor("..\\evil.shape"), "evil_shape.shape");
  assert.equal(fileNameFor("  "), "MyGate.shape");
  assert.equal(fileNameFor(null), "MyGate.shape");
});

test("settings that do not change give back the same shape, so no history step is spent", () => {
  const { shape } = standard();
  const same = { name: "Standard", lightTicks: 10, wooshTicks: 3, redstoneActivated: false, materials: { ...shape.materials } };
  assert.equal(withSettings(shape, same), shape);
  assert.notEqual(withSettings(shape, { ...same, lightTicks: 11 }), shape);
  assert.notEqual(withSettings(shape, { ...same, materials: { PORTAL_MATERIAL: "WATER" } }), shape);
});

test("a draft keeps tick settings up to 32 bits and drops the rest", () => {
  const parsed = JSON.parse(draftText(standard().shape));
  parsed.shape.lightTicks = 2 ** 31 - 1;
  parsed.shape.wooshTicks = 1e10;
  const got = readDraft(JSON.stringify(parsed));
  assert.deepEqual([got.lightTicks, got.wooshTicks], [2 ** 31 - 1, null]);
});

test("a shipped shape starts under a name of its own", () => {
  assert.equal(customName("Standard"), "StandardCustom");
  assert.equal(customName(""), "MyCustom");
});
