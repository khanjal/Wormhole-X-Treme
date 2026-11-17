// Run from the repository root with plain `node --test`; the designer's live checks.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseShape } from "../shape-parser.js";
import { fromParsed, newShape, editCell, setMarker, setKind, setOrder, withSettings } from "../shape-edit.js";
import { checkShape } from "../shape-check.js";

const SHAPES = fileURLToPath(new URL("../../../src/main/resources/shapes/gate/", import.meta.url));

for (const file of readdirSync(SHAPES).filter((f) => f.endsWith(".shape"))) {
  test(`${file}: a shipped shape has no errors and no warnings`, () => {
    const { shape } = fromParsed(parseShape(readFileSync(SHAPES + file, "utf8")));
    assert.deepEqual(checkShape(shape), []);
  });
}

/** A shape that passes: a frame cell with the player entry, and the switch on layer 2. */
function good() {
  let s = newShape({ width: 5, height: 5, layers: 3 });
  s = put(s, 1, 4, 2, (c) => { setKind(c, "S"); });
  return put(s, 2, 2, 1, (c) => { setKind(c, "S"); setMarker(c, "A", true); });
}
const put = (shape, layer, row, col, fn) => editCell(shape, layer, row, col, fn);
const messages = (shape, level) => checkShape(shape).filter((i) => i.level === level);
const errorsAbout = (shape, text) => checkShape(shape).filter((i) => i.message.includes(text));

test("the starting point passes", () => {
  assert.deepEqual(checkShape(good()), []);
});

test("a second :EP is an error that points at the last one", () => {
  const s = put(good(), 1, 0, 0, (c) => setMarker(c, "EP", true));
  const [hit] = errorsAbout(s, "2 :EP");
  assert.deepEqual(hit.cell, { layer: 1, row: 4, col: 2 });
});

test("two of each one-per-gate marker and each redstone block are errors", () => {
  for (const key of ["N", "EM", "A", "D", "IA", "RD", "RS", "RA"]) {
    let s = good();
    s = put(s, 1, 0, 0, (c) => setMarker(c, key, true));
    s = put(s, 1, 0, 1, (c) => setMarker(c, key, true));
    assert.equal(errorsAbout(s, `:${key} blocks`).length, 1, key);
  }
});

test("no :EP and no :A are each an error", () => {
  const noEp = put(good(), 1, 4, 2, (c) => setMarker(c, "EP", false));
  assert.equal(errorsAbout(noEp, "No :EP").length, 1);
  const noA = put(good(), 2, 2, 1, (c) => setMarker(c, "A", false));
  assert.equal(errorsAbout(noA, "No :A").length, 1);
});

test(":IA, :D, :N and :A cannot share a block, and none can be on a woosh block", () => {
  let s = put(good(), 2, 2, 1, (c) => setMarker(c, "D", true));
  assert.equal(errorsAbout(s, "same block").length, 1);
  s = put(good(), 2, 2, 1, (c) => setOrder(c, "woosh", 1));
  assert.equal(errorsAbout(s, "woosh (:W) block").length, 1);
  s = put(good(), 2, 0, 0, (c) => { setMarker(c, "N", true); setOrder(c, "woosh", 1); });
  assert.equal(errorsAbout(s, ":N is on a woosh").length, 1);
});

test(":A and :D need an Ignored block in front, in the next layer up", () => {
  for (const key of ["A", "D"]) {
    let s = put(newShape({ width: 3, height: 3, layers: 3 }), 2, 1, 1, (c) => setMarker(c, key, true));
    s = put(s, 1, 2, 1, (c) => setKind(c, "S"));
    const blocked = put(s, 3, 1, 1, (c) => setKind(c, "S"));
    const hit = errorsAbout(blocked, `:${key} at layer 2`);
    assert.equal(hit.length, 1, key);
    assert.deepEqual(hit[0].cell, { layer: 3, row: 1, col: 1 });
    // A portal cell in front is in the way too; an empty one, or no layer at all, is not.
    assert.equal(errorsAbout(put(s, 3, 1, 1, (c) => setKind(c, "P")), `:${key} at layer 2`).length, 1, key);
    assert.equal(errorsAbout(s, `:${key} at layer 2`).length, 0, key);
    assert.equal(errorsAbout(put(newShape({ width: 3, height: 3, layers: 2 }), 2, 1, 1, (c) => setMarker(c, key, true)), "in front").length, 0, key);
  }
});

test(":N hangs its sign on the next layer up, which must be empty of frame and portal", () => {
  let s = put(newShape({ width: 3, height: 3, layers: 3 }), 1, 1, 1, (c) => setMarker(c, "N", true));
  assert.equal(errorsAbout(s, ":N at").length, 0);
  s = put(s, 2, 1, 1, (c) => setKind(c, "C"));
  assert.equal(errorsAbout(s, ":N at").length, 1);
});

test("[RD] and [RS] need a :D; [RA] does not", () => {
  const base = newShape({ width: 3, height: 3, layers: 2 });
  for (const [key, count] of [["RD", 1], ["RS", 1], ["RA", 0]]) {
    const s = put(put(base, 1, 1, 1, (c) => setMarker(c, key, true)), 1, 2, 1, (c) => setKind(c, "S"));
    assert.equal(errorsAbout(s, "needs a :D").length, count, key);
  }
});

test("a redstone block on its own empty cell above a frame block is fine; otherwise it is flagged", () => {
  const base = put(newShape({ width: 3, height: 4, layers: 2 }), 1, 2, 1, (c) => setKind(c, "S"));
  const ok = put(base, 1, 1, 1, (c) => setMarker(c, "RA", true));
  assert.deepEqual(checkShape(ok).filter((i) => i.message.includes("[RA]")), []);
  // Nothing under it: only a warning, since the plugin does not insist.
  const floating = put(base, 1, 0, 1, (c) => setMarker(c, "RA", true));
  assert.equal(messages(floating, "warning").filter((i) => i.message.includes("on top of")).length, 1);
  // Shared with another marker or a woosh step, or a chevron block: an error.
  for (const fn of [(c) => setMarker(c, "EM", true), (c) => setOrder(c, "woosh", 1), (c) => setKind(c, "P")]) {
    assert.equal(errorsAbout(put(ok, 1, 1, 1, fn), "nothing else on it").length, 1);
  }
  // A chevron cell counts as frame to the plugin, so that one is its error.
  assert.equal(errorsAbout(put(ok, 1, 1, 1, (c) => setKind(c, "C")), "lands on")[0].level, "error");
  // The older [S:RA] form is the frame block itself; its landing cell, one up, must be empty.
  const legacy = put(base, 1, 2, 1, (c) => setMarker(c, "RA", true));
  assert.equal(errorsAbout(legacy, "nothing else").length, 0);
  assert.equal(errorsAbout(legacy, "lands on").length, 0);
  assert.equal(errorsAbout(put(legacy, 1, 1, 1, (c) => setKind(c, "S")), "lands on").length, 1);
});

test("a gap in the light or woosh steps is an error, as reload refuses it, and points at the step after it", () => {
  let s = put(good(), 1, 0, 0, (c) => setOrder(c, "light", 1));
  s = put(s, 1, 0, 1, (c) => setOrder(c, "light", 4));
  s = put(s, 1, 0, 2, (c) => setOrder(c, "woosh", 2));
  s = put(s, 1, 0, 3, (c) => setOrder(c, "woosh", 3));
  const [gap, ...rest] = messages(s, "error");
  assert.match(gap.message, /:L #2 through #3 are never used/);
  assert.match(gap.message, /reload refuses/);
  assert.deepEqual(gap.cell, { layer: 1, row: 0, col: 1 });
  assert.deepEqual(rest, []);
});

test("the name, ticks and materials are checked", () => {
  const named = (name) => withSettings(good(), { name });
  assert.equal(errorsAbout(named("  "), "no name").length, 1);
  assert.equal(errorsAbout(named("a=b"), "= sign").length, 1);
  assert.equal(messages(named("My Gate"), "warning").length, 1);
  assert.equal(messages(named("My_Gate-2"), "warning").length, 0);
  assert.equal(errorsAbout(withSettings(good(), { lightTicks: -1 }), "LIGHT_TICKS").length, 1);
  assert.equal(errorsAbout(withSettings(good(), { wooshTicks: 2.5 }), "WOOSH_TICKS").length, 1);
  assert.equal(errorsAbout(withSettings(good(), { materials: { PORTAL_MATERIAL: "WA TER" } }), "PORTAL_MATERIAL").length, 1);
  assert.equal(errorsAbout(withSettings(good(), { materials: { PORTAL_MATERIAL: "WATER" } }), "PORTAL_MATERIAL").length, 0);
});

test("errors come before warnings", () => {
  const s = withSettings(put(good(), 1, 4, 2, (c) => setMarker(c, "EP", false)), { name: "bad name" });
  assert.deepEqual(checkShape(s).map((i) => i.level), ["error", "warning"]);
});

test("what ShapeFileValidator rejects is an error; the header's stricter rules are warnings that say so", () => {
  const level = (shape, text) => errorsAbout(shape, text).map((i) => i.level);
  assert.deepEqual(level(put(good(), 1, 0, 0, (c) => setMarker(c, "EP", true)), ":EP blocks"), ["error"]);
  assert.deepEqual(level(put(put(good(), 1, 0, 0, (c) => setMarker(c, "RA", true)), 1, 0, 1, (c) => setMarker(c, "RA", true)), ":RA blocks"), ["warning"]);
  const blocked = put(good(), 3, 2, 1, (c) => setKind(c, "S"));
  const [front] = errorsAbout(blocked, "in front of it");
  assert.equal(front.level, "warning");
  assert.match(front.message, /validator does not reject this/);
  assert.deepEqual(level(put(good(), 2, 2, 1, (c) => setMarker(c, "D", true)), "same block"), ["warning"]);
});

test("a repeated token in one cell counts twice, as the plugin counts it", () => {
  const s = put(good(), 1, 4, 2, (c) => c.markers.push("EP"));
  assert.equal(errorsAbout(s, "2 :EP blocks").length, 1);
});

test("tick settings past 32 bits or fractional are errors; a negative one only a warning", () => {
  for (const bad of [1e10, 2 ** 31, 1.5]) {
    assert.equal(errorsAbout(withSettings(good(), { lightTicks: bad }), "LIGHT_TICKS must").length, 1, String(bad));
  }
  assert.equal(errorsAbout(withSettings(good(), { wooshTicks: 2 ** 31 - 1 }), "WOOSH_TICKS").length, 0);
  assert.equal(errorsAbout(withSettings(good(), { wooshTicks: -2 }), "negative")[0].level, "warning");
});

test("a name equal to a shipped shape's, in any case, is a warning to rename", () => {
  const shipped = ["Standard", "Grand"];
  const named = (name) => checkShape(withSettings(good(), { name }), { shipped });
  assert.equal(named("Standard").length, 1);
  assert.match(named("standard")[0].message, /shipped shape/);
  assert.equal(named("StandardCustom").length, 0);
  assert.equal(checkShape(withSettings(good(), { name: "Standard" })).length, 0);
});

test("an order number over the cap is an error", () => {
  const s = put(good(), 1, 0, 0, (c) => { c.light = 5000; });
  assert.equal(errorsAbout(s, "over 1000").length, 1);
});

test("a name that sanitises to a shipped file name clashes too", () => {
  const shipped = ["Standard"];
  const issues = checkShape(withSettings(good(), { name: "Standard!" }), { shipped });
  assert.equal(issues.filter((i) => i.message.includes("shipped shape Standard")).length, 1);
  assert.equal(checkShape(withSettings(good(), { name: "sTANDARD ." }), { shipped }).filter((i) => i.message.includes("shipped shape")).length, 1);
});

test("woosh order 0 is a warning, because the plugin starts the woosh at 1 and never plays it", () => {
  const s = put(good(), 1, 0, 0, (c) => setOrder(c, "woosh", 0));
  const found = messages(s, "warning").filter((i) => i.message.includes("Woosh order 0 is never played"));
  assert.equal(found.length, 1);
  assert.deepEqual(found[0].cell, { layer: 1, row: 0, col: 0 });
  assert.deepEqual(messages(s, "error"), []);
  assert.equal(messages(put(good(), 1, 0, 0, (c) => setOrder(c, "woosh", 1)), "warning").length, 0);
});

test("a woosh that starts above #1 is a warning: the plugin plays the empty steps before it", () => {
  const s = put(put(good(), 1, 0, 0, (c) => setOrder(c, "woosh", 3)), 1, 0, 1, (c) => setOrder(c, "woosh", 4));
  const found = messages(s, "warning").filter((i) => i.message.includes("The woosh starts at #3"));
  assert.equal(found.length, 1);
  assert.match(found[0].message, /#1 to #2 are empty/);
  assert.deepEqual(found[0].cell, { layer: 1, row: 0, col: 0 });
  assert.equal(messages(put(good(), 1, 0, 0, (c) => setOrder(c, "woosh", 1)), "warning").filter((i) => i.message.includes("starts at")).length, 0);
});

test("an :L on an Ignored cell is a warning, but a :W there is not, and neither is an :L on a built cell", () => {
  const light = put(good(), 1, 0, 0, (c) => setOrder(c, "light", 1));
  const [hit] = errorsAbout(light, "Ignored cell");
  assert.equal(hit.level, "warning");
  assert.deepEqual(hit.cell, { layer: 1, row: 0, col: 0 });
  const woosh = put(good(), 1, 0, 0, (c) => setOrder(c, "woosh", 1));
  assert.deepEqual(errorsAbout(woosh, "Ignored cell"), []);
  const built = put(good(), 1, 0, 0, (c) => { setKind(c, "S"); setOrder(c, "light", 1); });
  assert.deepEqual(errorsAbout(built, "Ignored cell"), []);
});
