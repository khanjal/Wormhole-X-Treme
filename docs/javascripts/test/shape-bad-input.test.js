// What the plugin's loader refuses or does oddly, and the caps for a shape that is not ours.
import { test } from "node:test";
import assert from "node:assert/strict";
import { parseShape, ShapeError, LIMITS } from "../shape-parser.js";

const head = "Name=T\nVersion=2\nGateShape=\n\n";
const shape = (layers) => head + layers;
const refused = (text, pattern) => assert.throws(() => parseShape(text), (e) => e instanceof ShapeError && pattern.test(e.message));

test("a shape with no :EP is refused, as Stargate3DShape refuses it", () => {
  refused(shape("Layer#1=\n[S][S]\n\n"), /:EP/);
});

test("a layer with more rows than the grid is refused", () => {
  refused(shape("Layer#1=\n[S:EP]\n\nLayer#2=\n[S]\n[S]\n\n"), /more rows/);
});

test("a layer with fewer rows than the grid is refused", () => {
  refused(shape("Layer#1=\n[S:EP]\n[S]\n\nLayer#2=\n[S]\n\n"), /fewer rows/);
});

test("a non-numeric layer number, light order or woosh order is refused", () => {
  refused(shape("Layer#x=\n[S:EP]\n\n"), /layer number/);
  refused(shape("Layer#1=\n[S:EP:L#a]\n\n"), /light order/);
  refused(shape("Layer#1=\n[S:EP:W#b]\n\n"), /woosh order/);
});

test("layers before any GateShape= are refused", () => {
  refused("Name=T\nVersion=2\nLayer#1=\n[S:EP]\n\n", /GateShape/);
});

test("a comment inside a layer replaces the row above it", () => {
  // The comment takes the slot of the row above it, so layer 2's first row is lost.
  const s = parseShape(shape("Layer#1=\n[S:EP]\n[S]\n\nLayer#2=\n[S]\n# note\n[I]\n\n"));
  const layer2 = s.cells.filter((c) => c.layer === 2);
  assert.equal(layer2.length, 1);
  assert.equal(layer2[0].block, "none");
});

test("a layer named twice is replaced, not added", () => {
  const s = parseShape(shape("Layer#1=\n[S:EP]\n\nLayer#1=\n[I:EP]\n\n"));
  assert.equal(s.cells.filter((c) => c.layer === 1).length, 1);
  assert.equal(s.cells[0].block, "none");
});

test("a file over the byte cap is refused", () => {
  refused("#".repeat(LIMITS.bytes + 1), /KB/);
});

test("a grid wider or taller than the cap, or a layer past it, is refused", () => {
  refused(shape(`Layer#1=\n${"[S]".repeat(LIMITS.side + 1)}\n\n`), /grid/);
  refused(shape(`Layer#1=\n${"[S]\n".repeat(LIMITS.side + 1)}\n`), /grid/);
  refused(shape(`Layer#${LIMITS.layers + 1}=\n[S:EP]\n\n`), /layers are numbered/);
});

test("more cells than the cap is refused", () => {
  const row = "[S]".repeat(LIMITS.side);
  const layer = (n) => `Layer#${n}=\n${(row + "\n").repeat(LIMITS.side)}\n`;
  let text = head;
  for (let n = 1; n <= 5; n++) text += layer(n);
  refused(text, /too many cells|More than \d+ cells/);
});

test("a last layer row on the final line of the file is refused, as the loader would crash on it", () => {
  refused("Name=T\nVersion=2\nGateShape=\n\nLayer#1=\n[S:EP]", /end of the file/);
  refused("Name=T\nVersion=2\nGateShape=\n\nLayer#1=\n[S:EP]\n", /end of the file/);
  assert.ok(parseShape("Name=T\nVersion=2\nGateShape=\n\nLayer#1=\n[S:EP]\n\n"));
});

test("a negative light or woosh order is refused", () => {
  refused(shape("Layer#1=\n[S:EP:L#-1]\n\n"), /negative/);
  refused(shape("Layer#1=\n[S:EP:W#-2]\n\n"), /negative/);
});

test("numbers are read as Integer.parseInt reads them: no spaces, 32 bits", () => {
  refused(shape("Layer# 1 =\n[S:EP]\n\n"), /layer number/);
  refused(shape("Layer#1=\n[S:EP:L# 2]\n\n"), /light order/);
  refused(shape("Layer#1=\n[S:EP]\n\nLIGHT_TICKS=99999999999999999999\n"), /LIGHT_TICKS/);
  refused(shape("Layer#1=\n[S:EP]\n\nWOOSH_TICKS=2147483648\n"), /WOOSH_TICKS/);
  assert.equal(parseShape(shape("Layer#1=\n[S:EP:L#+3]\n\n")).cells[0].light, 3);
});

test("a file with no Version=2 line is refused, even if it has layers", () => {
  refused("Name=T\nVersion=1\nGateShape=\n\nLayer#1=\n[S:EP]\n\n", /Version=2/);
  refused("Name=T\nGateShape=\n\nLayer#1=\n[S:EP]\n\n", /Version=2/);
});

test("Name= with nothing after it is refused", () => {
  refused("Name=\nVersion=2\nGateShape=\n\nLayer#1=\n[S:EP]\n\n", /Name=/);
});

test("the grid width counts [] cells, as measureGrid does", () => {
  const s = parseShape(shape("Layer#1=\n[][S:EP]\n\n"));
  assert.equal(s.width, 2);
});

test("a very long row is refused rather than scanned", () => {
  refused(shape(`Layer#1=\n[S:EP]\n\nLayer#2=\n${"[".repeat(LIMITS.row + 1)}\n\n`), /characters/);
});

test("a *_MATERIAL key the plugin does not know is ignored, as its loader ignores it", () => {
  const parsed = parseShape("Name=T\nVersion=2\nFOO_MATERIAL=STONE\nCHEVRON_MATERIAL=GOLD_BLOCK\nGateShape=\n\nLayer#1=\n[S:EP]\n\n");
  assert.deepEqual(Object.keys(parsed.materials), ["CHEVRON_MATERIAL"]);
});
