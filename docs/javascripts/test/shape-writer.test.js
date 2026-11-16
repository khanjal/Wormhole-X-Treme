// Run from the repository root with plain `node --test`; it reads the shipped .shape files.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseShape, ShapeError } from "../shape-parser.js";
import { fromParsed, newShape, editCell, setMarker, setKind, setOrder } from "../shape-edit.js";
import { writeShape, cellText } from "../shape-writer.js";

const SHAPES = fileURLToPath(new URL("../../../src/main/resources/shapes/gate/", import.meta.url));
const files = readdirSync(SHAPES).filter((f) => f.endsWith(".shape")).sort();
const read = (file) => readFileSync(SHAPES + file, "utf8");
// The rows as written, each cell's parts in a fixed order: the file may say [S:L#1:EP], the writer [S:EP:L#1].
const rowLines = (text) => text.split(/\r?\n/).filter((l) => l.startsWith("["))
  .map((l) => l.replace(/\[([^\]]*)\]/g, (_, parts) => `[${parts.split(":").sort().join(":")}]`));

test("all nine shipped shapes are round-tripped", () => {
  assert.equal(files.length, 9);
});

for (const file of files) {
  test(`${file}: parse, write, parse gives the same cells, orders and settings`, () => {
    const parsed = parseShape(read(file));
    const written = writeShape(fromParsed(parsed).shape);
    assert.deepEqual(parseShape(written), parsed);
  });

  test(`${file}: every row has the same cells as the shipped file`, () => {
    const written = writeShape(fromParsed(parseShape(read(file))).shape);
    assert.deepEqual(rowLines(written), rowLines(read(file)));
  });
}

test("a new blank shape is written as a file the parser reads back", () => {
  const shape = newShape({ name: "Fresh", width: 5, height: 4, layers: 2 });
  const parsed = parseShape(writeShape(shape));
  assert.equal(parsed.name, "Fresh");
  assert.deepEqual([parsed.width, parsed.height, parsed.layers], [5, 4, 2]);
  assert.equal(parsed.cells.filter((c) => c.markers.includes("EP")).length, 1);
});

test("a shape with no :EP is written but the parser refuses it, as the plugin does", () => {
  const shape = editCell(newShape(), 1, 6, 3, (c) => setMarker(c, "EP", false));
  assert.throws(() => parseShape(writeShape(shape)), ShapeError);
});

test("settings, materials and the lines the page does not draw survive a write", () => {
  const text = read("Standard.shape").replace("REDSTONE_ACTIVATED=FALSE",
    "PORTAL_MATERIAL=WATER\nCHEVRON_MATERIAL = GOLD_BLOCK;\nSOUND_SCALE=1.5\nMATERIAL_GROUPS=Standard\nREDSTONE_ACTIVATED=TRUE");
  const parsed = parseShape(text);
  const again = parseShape(writeShape(fromParsed(parsed).shape));
  assert.deepEqual(again.materials, { PORTAL_MATERIAL: "WATER", CHEVRON_MATERIAL: "GOLD_BLOCK" });
  assert.deepEqual(again.extra, ["SOUND_SCALE=1.5", "MATERIAL_GROUPS=Standard"]);
  assert.equal(again.redstoneActivated, true);
  assert.deepEqual([again.lightTicks, again.wooshTicks], [10, 3]);
});

test("the file starts with Name= and Version=2 under a short header of comments", () => {
  const lines = writeShape(newShape({ name: "Header" })).split("\n");
  assert.ok(lines.slice(0, 2).every((l) => l.startsWith("#")));
  assert.deepEqual(lines.slice(2, 4), ["Name=Header", "Version=2"]);
});

test("a line break in the name or a material cannot start another line of the file", () => {
  const evil = { ...newShape({ name: "A\nLayer#9=" }), materials: { PORTAL_MATERIAL: "WATER\nLayer#8=" } };
  const lines = writeShape(evil).split("\n");
  assert.equal(lines.filter((l) => l.startsWith("Layer#")).length, 3);
  assert.ok(lines.includes("Name=A Layer#9="));
});

test("cells are spelled as the plugin reads them", () => {
  const cell = (fn) => { const c = { block: "none", lenientChevron: false, portal: false, markers: [], light: null, woosh: null }; fn(c); return cellText(c); };
  assert.equal(cell(() => {}), "I");
  assert.equal(cell((c) => setKind(c, "S")), "S");
  assert.equal(cell((c) => { setKind(c, "C"); setOrder(c, "light", 3); }), "C:L#3");
  assert.equal(cell((c) => { setKind(c, "P"); setOrder(c, "woosh", 2); }), "P:W#2");
  assert.equal(cell((c) => { setKind(c, "S"); setMarker(c, "EP", true); }), "S:EP");
  assert.equal(cell((c) => setMarker(c, "EM", true)), "I:EM");
  assert.equal(cell((c) => setMarker(c, "RD", true)), "RD");
  assert.equal(cell((c) => { setMarker(c, "RD", true); setOrder(c, "woosh", 1); }), "I:RD:W#1");
  assert.equal(cell((c) => { setKind(c, "C"); c.lenientChevron = true; }), "S:C");
});
