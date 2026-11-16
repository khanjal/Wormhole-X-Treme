// The checks the designer shows live: what Stargate3DShape and `gate shapes validate`
// (ShapeFileValidator) refuse or report, plus the rules in the shipped files' header comment.
// The in-game command is the authority; this is the same list while you edit.
import { LIMITS } from "./shape-parser.js";
import { cellAt } from "./shape-edit.js";

const ONE_EACH = ["N", "EP", "EM", "A", "D", "IA"];
const REDSTONE = ["RD", "RS", "RA"];
const SWITCHES = ["IA", "D", "N", "A"];
const MATERIAL_NAME = /^[A-Za-z0-9_:]+$/;

/** Where a cell is, as a person counts it: from 1, rows from the top, columns from the left. */
export const where = (c) => `layer ${c.layer}, row ${c.row + 1}, column ${c.col + 1}`;
const at = (c) => ({ layer: c.layer, row: c.row, col: c.col });
const isBuilt = (c) => c.block !== "none";

/**
 * @param {object} shape a designer model
 * @returns {Array<{level: "error"|"warning", message: string, cell: ?{layer: number, row: number, col: number}}>}
 *   errors first, each group in file order
 */
export function checkShape(shape) {
  const found = [];
  const error = (message, cell = null) => found.push({ level: "error", message, cell: cell && at(cell) });
  const warn = (message, cell = null) => found.push({ level: "warning", message, cell: cell && at(cell) });
  const has = (c, key) => c.markers.includes(key);
  const withMarker = (key) => shape.cells.filter((c) => has(c, key));

  checkSettings(shape, error, warn);
  checkSize(shape, error);

  // 1 per gate: nothing enforces it, a second silently replaces the first.
  for (const key of [...ONE_EACH, ...REDSTONE]) {
    const list = withMarker(key);
    if (list.length > 1) {
      error(`${list.length} :${key} blocks, but a gate takes only one; the plugin keeps the last and ignores the rest. ` +
        `The others are at ${list.slice(0, -1).map(where).join("; ")}.`, list[list.length - 1]);
    }
  }
  if (!withMarker("EP").length) error("No :EP block, where players teleport in; the plugin refuses a shape without one.");
  if (!withMarker("A").length) error("No :A block, so there is nowhere for the activation switch to go and the gate could never be dialled.");

  for (const c of shape.cells) {
    const clash = SWITCHES.filter((k) => has(c, k));
    if (clash.length > 1) error(`${clash.map((k) => `:${k}`).join(" and ")} are on the same block at ${where(c)}; they each need their own.`, c);
    if (c.woosh !== null && clash.length) error(`:${clash[0]} is on a woosh (:W) block at ${where(c)}; none of :IA, :D, :N or :A can be.`, c);
    checkFront(shape, c, has, error);
    checkRedstoneCell(shape, c, error, warn);
  }

  const dial = withMarker("D").length > 0;
  for (const key of ["RD", "RS"]) {
    for (const c of withMarker(key)) if (!dial) error(`[${key}] needs a :D block to target, and there is none.`, c);
  }

  checkOrders(shape.cells, "light", ":L", warn);
  checkOrders(shape.cells, "woosh", ":W", warn);
  return [...found.filter((f) => f.level === "error"), ...found.filter((f) => f.level === "warning")];
}

function checkSettings(shape, error, warn) {
  const name = shape.name ?? "";
  if (!name.trim()) error("The shape has no name; the plugin refuses a Name= line with nothing after it.");
  else if (name.includes("=")) error("The name cannot contain an = sign.");
  else if (!/^[A-Za-z0-9_-]+$/.test(name)) warn("A name of letters, digits, _ and - only is the safest, as it is also the file name and what you type in commands.");
  for (const [key, value] of [["LIGHT_TICKS", shape.lightTicks], ["WOOSH_TICKS", shape.wooshTicks]]) {
    if (value !== null && (!Number.isInteger(value) || value < 0)) error(`${key} must be a whole number, 0 or more.`);
  }
  for (const [key, value] of Object.entries(shape.materials)) {
    if (value.trim() && !MATERIAL_NAME.test(value.trim())) error(`${key} is not a material name; use capital letters and underscores, like OBSIDIAN.`);
  }
}

function checkSize(shape, error) {
  if (shape.width > LIMITS.side || shape.height > LIMITS.side) error(`The grid is ${shape.width} by ${shape.height}; this page edits and draws at most ${LIMITS.side} each way.`);
  if (shape.layers > LIMITS.layers) error(`${shape.layers} layers; this page edits and draws at most ${LIMITS.layers}.`);
  if (shape.cells.length > LIMITS.cells) error(`${shape.cells.length} cells; this page edits and draws at most ${LIMITS.cells}.`);
  if (shape.lightOrders.length > LIMITS.orders || shape.wooshOrders.length > LIMITS.orders) {
    error(`More than ${LIMITS.orders} light or woosh steps; this page draws at most that many.`);
  }
}

/** :A and :D face the next layer up and need nothing built in front; so does the :N sign. */
function checkFront(shape, c, has, error) {
  const front = cellAt(shape, c.layer + 1, c.row, c.col);
  if (!front) return;
  for (const key of ["A", "D"]) {
    if (has(c, key) && (isBuilt(front) || front.portal)) {
      error(`:${key} at ${where(c)} needs an Ignored block in front of it (layer ${front.layer}), so nothing is in the way of the ${key === "A" ? "switch" : "sign"}.`, front);
    }
  }
  if (has(c, "N") && (isBuilt(front) || front.portal)) {
    error(`:N at ${where(c)} hangs its sign on layer ${front.layer}, where the gate already has a block; move :N to a cell with nothing in front.`, front);
  }
}

/** [RD], [RS] and [RA] sit alone on an empty cell above a frame block (or, as older shapes did, on the frame block itself). */
function checkRedstoneCell(shape, c, error, warn) {
  const marks = c.markers.filter((m) => REDSTONE.includes(m));
  if (!marks.length) return;
  const label = `[${marks.join("][")}]`;
  if (c.markers.length > marks.length || marks.length > 1 || c.portal || c.light !== null || c.woosh !== null || c.block === "chevron") {
    error(`${label} at ${where(c)} must be on a block with nothing else on it.`, c);
    return;
  }
  // The plugin puts the redstone one up from a frame cell; what it lands in must be empty.
  const landing = c.block === "frame" ? cellAt(shape, c.layer, c.row - 1, c.col) : c;
  if (landing && isBuilt(landing)) {
    error(`${label} at ${where(c)} lands on a cell the frame already occupies, so the redstone there could never fire.`, c);
  }
  if (c.block === "none") {
    const below = cellAt(shape, c.layer, c.row + 1, c.col);
    // Under the bottom row is the ground, which the shape cannot say anything about.
    if (below && !isBuilt(below)) warn(`${label} at ${where(c)} should sit on top of a Stargate material block, with one directly below it.`, c);
  }
}

/** Light and woosh steps should run on without a gap; a gap is a tick that draws nothing. */
function checkOrders(cells, key, label, warn) {
  const orders = [...new Set(cells.map((c) => c[key]).filter((n) => n !== null))].sort((a, b) => a - b);
  for (let i = 1; i < orders.length; i++) {
    if (orders[i] === orders[i - 1] + 1) continue;
    const first = orders[i - 1] + 1;
    const missing = orders[i] - 1 === first ? `#${first} is` : `#${first} through #${orders[i] - 1} are`;
    warn(`${label} ${missing} never used, between #${orders[i - 1]} and #${orders[i]}; that tick draws nothing instead of skipping on.`,
      cells.find((c) => c[key] === orders[i]));
  }
}
