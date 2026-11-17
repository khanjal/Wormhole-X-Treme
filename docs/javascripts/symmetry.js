// The designer's symmetry (mirror) drawing mode: which cells answer to a cell you draw on, and copying
// one half or quadrant of a layer over the rest. No page in here; designer.js applies it and the tests run it in node.
// Mirroring is always inside one layer, never between layers.
import { SINGLES } from "./shape-parser.js";
import { cellAt, settle } from "./shape-edit.js";

export const MODES = ["none", "lr", "tb", "both"];
export const MODE_LABELS = {
  none: "None", lr: "Left-right (draw the left half)", tb: "Top-bottom (draw the top half)", both: "Both (draw the top-left quarter)",
};
export const isMode = (mode) => MODES.includes(mode);

/** The place across from `i` in a row or column of `n`; the middle of an odd one is across from itself. */
export const flip = (i, n) => n - 1 - i;

const acrossColumns = (mode) => mode === "lr" || mode === "both";
const acrossRows = (mode) => mode === "tb" || mode === "both";

/** The other cells that mirror a cell at (row, col), never the cell itself and never one twice. */
export function images(mode, row, col, width, height) {
  const rows = acrossRows(mode) ? [row, flip(row, height)] : [row];
  const cols = acrossColumns(mode) ? [col, flip(col, width)] : [col];
  const seen = new Set([`${row},${col}`]);
  const out = [];
  for (const r of rows) {
    for (const c of cols) {
      if (!seen.has(`${r},${c}`)) { seen.add(`${r},${c}`); out.push([r, c]); }
    }
  }
  return out;
}

/** The cell in the source half or quadrant that (row, col) is a copy of; a source cell is its own. */
export function sourceOf(mode, row, col, width, height) {
  return [acrossRows(mode) ? Math.min(row, flip(row, height)) : row, acrossColumns(mode) ? Math.min(col, flip(col, width)) : col];
}

export const isSource = (mode, row, col, width, height) => {
  const [r, c] = sourceOf(mode, row, col, width, height);
  return r === row && c === col;
};

/** The size of the source half or quadrant, in rows and columns; the middle row or column of an odd grid belongs to it. */
export function sourceSize(mode, width, height) {
  return { rows: acrossRows(mode) ? Math.ceil(height / 2) : height, cols: acrossColumns(mode) ? Math.ceil(width / 2) : width };
}

/** The markers that may be mirrored: none today, as every marker is one per gate. */
export const mirrorableMarkers = (markers) => markers.filter((m) => !SINGLES.has(m));

const LOOK = (c) => JSON.stringify([c.block, c.lenientChevron, c.portal, c.light, c.woosh]);
const isEmpty = (c) => c.block === "none" && !c.portal && c.light === null && c.woosh === null;
const isIgnored = (c) => c.block === "none" && !c.portal;

/** A cell that holds a one-per-gate marker: a mirrored change never touches it, as it is the gate's only one and may sit across the axis on purpose. */
export const holdsSingle = (cell) => cell.markers.some((m) => SINGLES.has(m));

/** The marker names a list of cells hold, for the status line ("skipped 2 cells holding :A, :EP"). */
export const singlesHeld = (cells) => [...new Set(cells.flatMap((c) => c.markers.filter((m) => SINGLES.has(m))))].map((m) => `:${m}`);

/**
 * The cells a paint or an order at (row, col) reaches besides that one: its images, less any that hold a one-per-gate marker.
 * @returns {{places: number[][], skipped: object[]}} the places to change, and the cells left alone
 */
export function targets(shape, layer, row, col, mode) {
  const places = [];
  const skipped = [];
  for (const [r, c] of images(mode, row, col, shape.width, shape.height)) {
    const cell = cellAt(shape, layer, r, c);
    if (cell && holdsSingle(cell)) skipped.push(cell); else places.push([r, c]);
  }
  return { places, skipped };
}

/** Gives `to` the look of `from`: its kind, and its light and woosh orders unless `orders` is false; its own one-per-gate markers stay. */
export function copyLook(from, to, orders = true) {
  to.block = from.block;
  to.lenientChevron = from.lenientChevron;
  to.portal = from.portal;
  if (orders) {
    to.light = from.light;
    to.woosh = from.woosh;
  }
  to.markers = [...to.markers, ...mirrorableMarkers(from.markers).filter((m) => !to.markers.includes(m))];
}

/** The problems in `after` that `before` did not have, told apart by their text. */
export function newIssues(before, after) {
  const had = new Set(before.map((i) => i.message));
  return after.filter((i) => !had.has(i.message));
}

/**
 * Copies the source half or quadrant of each given layer over the rest. A cell holding a one-per-gate marker is skipped.
 * Orders are copied only with `copyOrders`: a gate's light numbers often run round the ring, so copying them leaves gaps.
 * A cell that becomes Ignored loses its light number, as the plugin would record an [I:L#n] as a light position (a woosh number is fine there).
 * @returns {{shape: object, changed: number, overwritten: number, skipped: object[], dropped: number}} how many cells differ now, how many of those held something, the cells left alone, and the light numbers dropped
 */
export function mirrorLayers(shape, layers, mode, { copyOrders = false } = {}) {
  if (!isMode(mode) || mode === "none") return { shape, changed: 0, overwritten: 0, skipped: [], dropped: 0 };
  const cells = shape.cells.slice();
  const skipped = [];
  let changed = 0;
  let overwritten = 0;
  let dropped = 0;
  for (const layer of layers) {
    for (let r = 0; r < shape.height; r++) {
      for (let c = 0; c < shape.width; c++) {
        const [sr, sc] = sourceOf(mode, r, c, shape.width, shape.height);
        if (sr === r && sc === c) continue;
        const to = cellAt(shape, layer, r, c);
        const next = { ...to, markers: [...to.markers] };
        copyLook(cellAt(shape, layer, sr, sc), next, copyOrders);
        const lost = isIgnored(next) && !isIgnored(to) && next.light !== null;
        if (lost) next.light = null;
        if (LOOK(next) === LOOK(to)) continue;
        if (holdsSingle(to)) { skipped.push(to); continue; }
        if (lost) dropped++;
        changed++;
        if (!isEmpty(to)) overwritten++;
        cells[((layer - 1) * shape.height + r) * shape.width + c] = next;
      }
    }
  }
  return { shape: changed ? settle({ ...shape, cells }) : shape, changed, overwritten, skipped, dropped };
}
