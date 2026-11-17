// The gate designer's model: the parsed shape from shape-parser.js, kept as a full grid (every
// layer has every row and column) so a cell is found by its place, and changed by copying, never
// in place, so the undo history can simply hold the earlier shapes.
import { LIMITS, SINGLES, MATERIAL_KEYS } from "./shape-parser.js";

/** A change the designer refuses, worded for the visitor. */
export class EditError extends Error {}

export const KIND_NAMES = { I: "Ignored", S: "Stargate material", P: "Portal", C: "Chevron material" };

export const blankCell = (layer, row, col) => ({
  layer, row, col, y: 0, x: 0, block: "none", lenientChevron: false, portal: false, markers: [], light: null, woosh: null,
});

const distinct = (cells, key) => [...new Set(cells.map((c) => c[key]).filter((n) => n !== null))].sort((a, b) => a - b);

/** Recomputes what follows from the cells: world positions and the light and woosh steps. */
export function settle(shape) {
  shape.layers = Math.max(1, shape.layers);
  for (const c of shape.cells) {
    c.y = shape.height - 1 - c.row;
    c.x = shape.width - 1 - c.col;
  }
  shape.lightOrders = distinct(shape.cells, "light");
  shape.wooshOrders = distinct(shape.cells, "woosh").filter((n) => n >= 1); // order 0 is never played (StargateHelper.recordWooshWaves)
  return shape;
}

const cloneCell = (c) => ({ ...c, markers: [...c.markers] });

export function cellAt(shape, layer, row, col) {
  if (layer < 1 || layer > shape.layers || row < 0 || row >= shape.height || col < 0 || col >= shape.width) return undefined;
  return shape.cells[((layer - 1) * shape.height + row) * shape.width + col];
}

/** Lays cells out as grids[layer][row][col] (null where there is none) and back again. */
function toGrids(shape) {
  const grids = [];
  for (let l = 1; l <= shape.layers; l++) {
    const rows = [];
    for (let r = 0; r < shape.height; r++) {
      rows.push(Array.from({ length: shape.width }, (_, c) => cellAt(shape, l, r, c) ?? null));
    }
    grids.push(rows);
  }
  return grids;
}

function assemble(base, grids, width, height) {
  if (width < 1 || height < 1 || grids.length < 1) throw new EditError("A shape needs at least one row, one column and one layer.");
  if (width > LIMITS.side || height > LIMITS.side) throw new EditError(`A grid can be at most ${LIMITS.side} by ${LIMITS.side} here.`);
  if (grids.length > LIMITS.layers) throw new EditError(`A shape can have at most ${LIMITS.layers} layers here.`);
  if (width * height * grids.length > LIMITS.cells) throw new EditError(`That is more than ${LIMITS.cells} cells, too many to edit and draw.`);
  const shape = { ...base, materials: { ...base.materials }, extra: [...base.extra], width, height, layers: grids.length, cells: [] };
  grids.forEach((rows, li) => {
    for (let r = 0; r < height; r++) {
      for (let c = 0; c < width; c++) {
        const old = rows[r]?.[c];
        const cell = old ? cloneCell(old) : blankCell(0, 0, 0);
        Object.assign(cell, { layer: li + 1, row: r, col: c });
        shape.cells.push(cell);
      }
    }
  });
  return settle(shape);
}

const rebuild = (shape, grids, width = shape.width, height = shape.height) => assemble(shape, grids, width, height);

/** A new shape: every cell Ignored except the one player entry, so the file is one the plugin can load. */
export function newShape({ name = "MyGate", width = 7, height = 7, layers = 3 } = {}) {
  const base = {
    name, width, height, layers, cells: [], lightTicks: null, wooshTicks: null, redstoneActivated: false,
    materials: {}, extra: [],
  };
  const grids = Array.from({ length: layers }, () => Array.from({ length: height }, () => new Array(width).fill(null)));
  const shape = assemble(base, grids, width, height);
  cellAt(shape, 1, height - 1, Math.floor(width / 2)).markers.push("EP");
  return shape;
}

/**
 * A parsed shape as a full grid. A file the plugin reads can have a skipped layer or a row longer
 * or shorter than the first; those are padded or trimmed here, and said in the notes.
 * @returns {{shape: object, notes: string[]}}
 */
export function fromParsed(parsed) {
  const grids = Array.from({ length: parsed.layers }, () =>
    Array.from({ length: parsed.height }, () => new Array(parsed.width).fill(null)));
  let dropped = 0;
  for (const c of parsed.cells) {
    if (c.row < parsed.height && c.col < parsed.width) grids[c.layer - 1][c.row][c.col] = c;
    else dropped++;
  }
  const filled = grids.flat(2).filter(Boolean).length;
  const base = {
    name: parsed.name ?? "", lightTicks: parsed.lightTicks, wooshTicks: parsed.wooshTicks,
    redstoneActivated: parsed.redstoneActivated, materials: { ...parsed.materials }, extra: [...(parsed.extra ?? [])],
  };
  const shape = assemble(base, grids, parsed.width, parsed.height);
  const notes = [];
  const gaps = parsed.width * parsed.height * parsed.layers - filled;
  if (gaps > 0) notes.push(`${gaps} cells the file left out (a short row or a skipped layer) are now Ignored.`);
  if (dropped > 0) notes.push(`${dropped} cells past the end of the first row's width were dropped, as the plugin ignores them.`);
  return { shape, notes };
}

// --- structure ------------------------------------------------------------------------------

/** Inserts a blank layer after layer `after` (0 for before the first). */
export function addLayer(shape, after) {
  const grids = toGrids(shape);
  grids.splice(after, 0, Array.from({ length: shape.height }, () => new Array(shape.width).fill(null)));
  return rebuild(shape, grids);
}

export function removeLayer(shape, layer) {
  if (shape.layers <= 1) throw new EditError("A shape needs at least one layer.");
  const grids = toGrids(shape);
  grids.splice(layer - 1, 1);
  return rebuild(shape, grids);
}

/** Inserts a blank row after row index `after` (-1 for before the first), in every layer. */
export function addRow(shape, after) {
  const grids = toGrids(shape);
  for (const rows of grids) rows.splice(after + 1, 0, new Array(shape.width).fill(null));
  return rebuild(shape, grids, shape.width, shape.height + 1);
}

export function removeRow(shape, row) {
  if (shape.height <= 1) throw new EditError("A shape needs at least one row.");
  const grids = toGrids(shape);
  for (const rows of grids) rows.splice(row, 1);
  return rebuild(shape, grids, shape.width, shape.height - 1);
}

export function addColumn(shape, after) {
  const grids = toGrids(shape);
  for (const rows of grids) for (const row of rows) row.splice(after + 1, 0, null);
  return rebuild(shape, grids, shape.width + 1, shape.height);
}

export function removeColumn(shape, col) {
  if (shape.width <= 1) throw new EditError("A shape needs at least one column.");
  const grids = toGrids(shape);
  for (const rows of grids) for (const row of rows) row.splice(col, 1);
  return rebuild(shape, grids, shape.width - 1, shape.height);
}

/** Cuts or pads each layer at the right and the bottom, and the stack at the far end. */
export function resize(shape, width, height, layers) {
  const grids = toGrids(shape).slice(0, layers);
  while (grids.length < layers) grids.push([]);
  const fitted = grids.map((rows) => Array.from({ length: height }, (_, r) => Array.from({ length: width }, (_, c) => rows[r]?.[c] ?? null)));
  return rebuild(shape, fitted, width, height);
}

// --- cells ----------------------------------------------------------------------------------

const cellFields = (c) => [c.block, c.lenientChevron, c.portal, c.markers, c.light, c.woosh];

/** `fn` changes a copy of one cell; the rest of the shape is shared with the old one. */
export function editCell(shape, layer, row, col, fn) {
  const at = cellAt(shape, layer, row, col);
  if (!at) throw new EditError("There is no such cell.");
  const next = cloneCell(at);
  fn(next);
  if (JSON.stringify(cellFields(next)) === JSON.stringify(cellFields(at))) return shape; // nothing changed
  const cells = shape.cells.slice();
  cells[cells.indexOf(at)] = next;
  return settle({ ...shape, cells });
}

/** `fn` changes a copy of each of the cells at `places` ([row, col] pairs, one layer) as one change; the very same shape when none differs. */
export function editCells(shape, layer, places, fn) {
  const cells = shape.cells.slice();
  let changed = false;
  for (const [row, col] of places) {
    const at = cellAt(shape, layer, row, col);
    if (!at) throw new EditError("There is no such cell.");
    const next = cloneCell(at);
    fn(next);
    if (JSON.stringify(cellFields(next)) === JSON.stringify(cellFields(at))) continue;
    cells[cells.indexOf(at)] = next;
    changed = true;
  }
  return changed ? settle({ ...shape, cells }) : shape;
}

/** The palette kind a cell shows as: a portal cell is P whatever else it is. */
export function kindOf(cell) {
  if (cell.portal) return "P";
  if (cell.block === "chevron") return "C";
  return cell.block === "frame" ? "S" : "I";
}

/** Painting a chevron over a chevron keeps an [S:C] cell as it was; any other kind drops it. There is no control to make one: it comes from a file. */
export function setKind(cell, kind) {
  const keep = kind === "C" && cell.block === "chevron" && cell.lenientChevron;
  cell.block = kind === "S" ? "frame" : kind === "C" ? "chevron" : "none";
  cell.portal = kind === "P";
  cell.lenientChevron = keep;
}

export function setMarker(cell, key, on) {
  cell.markers = cell.markers.filter((m) => m !== key);
  if (on) cell.markers.push(key);
}

/** Orders are whole numbers from 0; null takes the step off the cell. */
export function setOrder(cell, which, order) {
  if (order !== null && (!Number.isInteger(order) || order < 0 || order > LIMITS.order)) {
    throw new EditError(`An order number is a whole number from 0 to ${LIMITS.order}.`);
  }
  cell[which] = order;
}

/** The next light step to offer: one past the highest in use. */
export const nextLightOrder = (shape) => (shape.lightOrders.length ? shape.lightOrders[shape.lightOrders.length - 1] + 1 : 1);
/** Woosh cells share steps, so a new one joins the highest step in use. */
export const currentWooshOrder = (shape) => (shape.wooshOrders.length ? shape.wooshOrders[shape.wooshOrders.length - 1] : 1);

/** A short description of one cell, for its screen-reader label and its tags. */
export function describeCell(cell) {
  const tags = cell.markers.map((m) => `:${m}`);
  if (cell.light !== null) tags.push(`:L#${cell.light}`);
  if (cell.woosh !== null) tags.push(`:W#${cell.woosh}`);
  const kind = kindOf(cell);
  const base = KIND_NAMES[kind] + (cell.lenientChevron ? " (frame material also accepted)" : "");
  return { kind, tags, text: tags.length ? `${base}, ${tags.join(" ")}` : base };
}

/** The shape with `patch` applied; the very same shape when nothing in it differs, so no history step is spent. */
export function withSettings(shape, patch) {
  const same = Object.entries(patch).every(([key, value]) => (key === "materials"
    ? JSON.stringify(Object.entries(value).sort()) === JSON.stringify(Object.entries(shape.materials).sort())
    : shape[key] === value));
  if (same) return shape;
  return { ...shape, ...patch, materials: patch.materials ? { ...patch.materials } : { ...shape.materials }, extra: [...shape.extra] };
}

// --- history --------------------------------------------------------------------------------

/** Undo and redo over whole shapes; a shape is never changed in place, so holding it is enough. */
export class History {
  constructor(limit = 100) {
    this.limit = limit;
    this.past = [];
    this.future = [];
  }

  /** Remembers `current` as the state before a change. */
  record(current) {
    this.past.push(current);
    if (this.past.length > this.limit) this.past.shift();
    this.future = [];
  }

  undo(current) {
    if (!this.past.length) return null;
    this.future.push(current);
    return this.past.pop();
  }

  redo(current) {
    if (!this.future.length) return null;
    this.past.push(current);
    return this.future.pop();
  }

  clear() {
    this.past = [];
    this.future = [];
  }
}

// --- the saved draft ------------------------------------------------------------------------

const DRAFT_VERSION = 1;
const EXTRA = /^(MATERIAL_GROUPS|SOUND_SCALE|USE_COST)=[^\r\n]{1,200}$/;
const text = (v, max) => (typeof v === "string" ? v.slice(0, max) : "");
/** A light or woosh order, or null for none; anything else makes the whole draft unreadable rather than quietly losing it. */
const orderOf = (v) => {
  if (v === null || v === undefined) return null;
  if (Number.isInteger(v) && v >= 0 && v <= LIMITS.order) return v;
  throw new Error("order");
};
const ticks = (v) => (Number.isInteger(v) && v >= 0 && v <= 2 ** 31 - 1 ? v : null);

export const draftText = (shape) => JSON.stringify({ v: DRAFT_VERSION, shape });

/** The saved draft back as a shape, or null for anything that is not one made by draftText. */
export function readDraft(raw) {
  try {
    const { v, shape: d } = JSON.parse(raw);
    if (v !== DRAFT_VERSION || !d || !Array.isArray(d.cells)) return null;
    const { width, height, layers } = d;
    if (![width, height, layers].every(Number.isInteger) || width < 1 || height < 1 || layers < 1
      || width > LIMITS.side || height > LIMITS.side || layers > LIMITS.layers) return null;
    if (width * height * layers > LIMITS.cells || d.cells.length !== width * height * layers) return null;
    const cells = d.cells.map((c, i) => {
      const cell = blankCell(Math.floor(i / (width * height)) + 1, Math.floor(i / width) % height, i % width);
      if (!c || typeof c !== "object") return cell;
      cell.block = ["frame", "chevron"].includes(c.block) ? c.block : "none";
      cell.lenientChevron = c.lenientChevron === true && cell.block === "chevron";
      cell.portal = c.portal === true;
      cell.markers = Array.isArray(c.markers) ? c.markers.filter((m) => SINGLES.has(m)).slice(0, 12) : [];
      cell.light = orderOf(c.light);
      cell.woosh = orderOf(c.woosh);
      return cell;
    });
    const materials = {};
    for (const key of MATERIAL_KEYS) if (typeof d.materials?.[key] === "string") materials[key] = text(d.materials[key], 100);
    const extra = (Array.isArray(d.extra) ? d.extra : []).filter((l) => typeof l === "string" && EXTRA.test(l)).slice(0, 20);
    return settle({
      name: text(d.name, 100), width, height, layers, cells, lightTicks: ticks(d.lightTicks), wooshTicks: ticks(d.wooshTicks),
      redstoneActivated: d.redstoneActivated === true, materials, extra,
    });
  } catch {
    return null;
  }
}

/**
 * Keeps a draft the designer could not read, under a key of its own, so saving a new one does not destroy it.
 * @returns {?string} the key it went under, or null when storage refused
 */
export function backUpUnreadable(storage, key, raw, now = Date.now()) {
  const backup = `${key}-unreadable-${now}`;
  try {
    storage.setItem(backup, raw);
    return backup;
  } catch {
    return null;
  }
}

/** The name a loaded shipped shape starts with in the editor, so saving it does not replace the stock one. */
export const customName = (name) => `${name || "My"}Custom`;

/** A file name from a shape name: letters, digits, underscore and hyphen only. */
export function fileNameFor(name) {
  const safe = String(name ?? "").replace(/[^A-Za-z0-9_-]+/g, "_").replace(/^_+|_+$/g, "");
  return `${safe || "MyGate"}.shape`;
}
