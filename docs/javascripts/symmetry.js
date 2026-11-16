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

/** Gives `to` the look of `from` (kind and orders), keeping every one-per-gate marker `to` already has. */
export function copyLook(from, to) {
  to.block = from.block;
  to.lenientChevron = from.lenientChevron;
  to.portal = from.portal;
  to.light = from.light;
  to.woosh = from.woosh;
  to.markers = [...to.markers, ...mirrorableMarkers(from.markers).filter((m) => !to.markers.includes(m))];
}

/**
 * Copies the source half or quadrant of each given layer over the rest.
 * @returns {{shape: object, changed: number, overwritten: number}} how many cells differ now, and how many of those held something
 */
export function mirrorLayers(shape, layers, mode) {
  if (!isMode(mode) || mode === "none") return { shape, changed: 0, overwritten: 0 };
  const cells = shape.cells.slice();
  let changed = 0;
  let overwritten = 0;
  for (const layer of layers) {
    for (let r = 0; r < shape.height; r++) {
      for (let c = 0; c < shape.width; c++) {
        const [sr, sc] = sourceOf(mode, r, c, shape.width, shape.height);
        if (sr === r && sc === c) continue;
        const to = cellAt(shape, layer, r, c);
        const next = { ...to, markers: [...to.markers] };
        copyLook(cellAt(shape, layer, sr, sc), next);
        if (LOOK(next) === LOOK(to)) continue;
        changed++;
        if (!isEmpty(to)) overwritten++;
        cells[((layer - 1) * shape.height + r) * shape.width + c] = next;
      }
    }
  }
  return { shape: changed ? settle({ ...shape, cells }) : shape, changed, overwritten };
}
