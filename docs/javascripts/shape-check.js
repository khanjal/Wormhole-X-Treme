// The checks the designer shows live: what Stargate3DShape and `gate shapes validate`
// (ShapeFileValidator) refuse or report, plus the rules in the shipped files' header comment.
// The in-game command is the authority; this is the same list while you edit.
import { LIMITS } from "./shape-parser.js";
import { cellAt, fileNameFor } from "./shape-edit.js";

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
const INT32 = 2 ** 31 - 1;
const STRICT = " The plugin's validator does not reject this, but the shape file's header asks for it.";

/**
 * @param {object} shape a designer model
 * @param {{shipped?: string[]}} [options] the names of the shipped shapes, to warn about a clash
 */
export function checkShape(shape, { shipped = [] } = {}) {
  const found = [];
  const error = (message, cell = null) => found.push({ level: "error", message, cell: cell && at(cell) });
  const warn = (message, cell = null) => found.push({ level: "warning", message, cell: cell && at(cell) });
  // A rule from the file header that ShapeFileValidator does not enforce: shown, but not as an error.
  const rule = (message, cell = null) => warn(message + STRICT, cell);
  const has = (c, key) => c.markers.includes(key);
  const withMarker = (key) => shape.cells.filter((c) => has(c, key));

  checkSettings(shape, error, warn, shipped);
  checkSize(shape, error);

  // 1 per gate: nothing enforces it, a second silently replaces the first.
  // Counted by token, as the plugin does: [S:EP:EP] is two.
  for (const key of [...ONE_EACH, ...REDSTONE]) {
    const list = shape.cells.flatMap((c) => c.markers.filter((m) => m === key).map(() => c));
    if (list.length > 1) {
      const report = ONE_EACH.includes(key) ? error : rule;
      report(`${list.length} :${key} blocks, but a gate takes only one; the plugin keeps the last and ignores the rest. ` +
        `The others are at ${list.slice(0, -1).map(where).join("; ")}.`, list[list.length - 1]);
    }
  }
  if (!withMarker("EP").length) error("No :EP block, where players teleport in; the plugin refuses a shape without one.");
  if (!withMarker("A").length) error("No :A block, so there is nowhere for the activation switch to go and the gate could never be dialled.");

  for (const c of shape.cells) {
    const clash = SWITCHES.filter((k) => has(c, k));
    if (clash.length > 1) rule(`${clash.map((k) => `:${k}`).join(" and ")} are on the same block at ${where(c)}; they each need their own.`, c);
    if (c.woosh !== null && clash.length) rule(`:${clash[0]} is on a woosh (:W) block at ${where(c)}; none of :IA, :D, :N or :A can be.`, c);
    checkFront(shape, c, has, error, rule);
    checkRedstoneCell(shape, c, error, rule);
  }

  const dial = withMarker("D").length > 0;
  for (const key of ["RD", "RS"]) {
    const report = key === "RD" ? error : rule;
    for (const c of withMarker(key)) if (!dial) report(`[${key}] needs a :D block to target, and there is none.`, c);
  }

  // ShapeFileValidator.checkNoGap files a gap as a problem, and reload refuses a file with any problem.
  checkOrders(shape.cells, "light", ":L", error);
  checkOrders(shape.cells, "woosh", ":W", error);
  const never = shape.cells.find((c) => c.woosh === 0);
  if (never) warn(`Woosh order 0 is never played: the woosh starts at #1 (a :W with no number is #1), so this cell at ${where(never)} never wooshes.`, never);
  return [...found.filter((f) => f.level === "error"), ...found.filter((f) => f.level === "warning")];
}

function checkSettings(shape, error, warn, shipped) {
  const name = shape.name ?? "";
  if (!name.trim()) error("The shape has no name; the plugin refuses a Name= line with nothing after it.");
  else if (name.includes("=")) error("The name cannot contain an = sign.");
  else if (!/^[A-Za-z0-9_-]+$/.test(name)) warn("A name of letters, digits, _ and - only is the safest, as it is also the file name and what you type in commands.");
  // The registry is keyed by Name and the file by its sanitised name: either one can land on a stock shape.
  const file = fileNameFor(name).slice(0, -".shape".length).toLowerCase();
  const clash = shipped.find((s) => s.toLowerCase() === name.trim().toLowerCase() || s.toLowerCase() === file);
  if (clash) {
    warn(`"${name}" is the name or file name of the shipped shape ${clash}. A file with that Name= clashes with the stock one, and a file called ${clash}.shape replaces it. Give yours a name of its own.`);
  }
  // Integer.valueOf reads these: a fraction or anything past 32 bits fails the load; a negative reads but means nothing.
  for (const [key, value] of [["LIGHT_TICKS", shape.lightTicks], ["WOOSH_TICKS", shape.wooshTicks]]) {
    if (value === null) continue;
    if (!Number.isInteger(value) || value > INT32) error(`${key} must be a whole number, at most ${INT32}; the plugin cannot read ${value}.`);
    else if (value < 0) warn(`${key} is negative; the plugin reads it but a negative wait means nothing.`);
  }
  for (const [key, value] of Object.entries(shape.materials)) {
    if (value.trim() && !MATERIAL_NAME.test(value.trim())) error(`${key} is not a material name; use capital letters and underscores, like OBSIDIAN.`);
  }
}

function checkSize(shape, error) {
  if (shape.width > LIMITS.side || shape.height > LIMITS.side) error(`The grid is ${shape.width} by ${shape.height}; this page edits and draws at most ${LIMITS.side} each way.`);
  if (shape.layers > LIMITS.layers) error(`${shape.layers} layers; this page edits and draws at most ${LIMITS.layers}.`);
  if (shape.cells.length > LIMITS.cells) error(`${shape.cells.length} cells; this page edits and draws at most ${LIMITS.cells}.`);
  for (const c of shape.cells) {
    for (const [key, label] of [["light", ":L"], ["woosh", ":W"]]) {
      if (c[key] !== null && c[key] > LIMITS.order) {
        error(`${label}#${c[key]} is over ${LIMITS.order}; the plugin builds a list that long, so keep order numbers small.`, c);
      }
    }
  }
  if (shape.lightOrders.length > LIMITS.orders || shape.wooshOrders.length > LIMITS.orders) {
    error(`More than ${LIMITS.orders} light or woosh steps; this page draws at most that many.`);
  }
}

/** :A and :D face the next layer up and need nothing built in front; so does the :N sign. */
function checkFront(shape, c, has, error, rule) {
  const front = cellAt(shape, c.layer + 1, c.row, c.col);
  if (!front) return;
  for (const key of ["A", "D"]) {
    if (has(c, key) && (isBuilt(front) || front.portal)) {
      rule(`:${key} at ${where(c)} needs an Ignored block in front of it (layer ${front.layer}), so nothing is in the way of the ${key === "A" ? "switch" : "sign"}.`, front);
    }
  }
  if (has(c, "N") && (isBuilt(front) || front.portal)) {
    error(`:N at ${where(c)} hangs its sign on layer ${front.layer}, where the gate already has a block; move :N to a cell with nothing in front.`, front);
  }
}

/** [RD], [RS] and [RA] sit alone on an empty cell above a frame block (or, as older shapes did, on the frame block itself). */
function checkRedstoneCell(shape, c, error, rule) {
  const marks = c.markers.filter((m) => REDSTONE.includes(m));
  if (!marks.length) return;
  const label = `[${marks.join("][")}]`;
  // A chevron cell is the plugin's own error below (the frame check counts it); the rest is the header's rule.
  if (c.markers.length > marks.length || marks.length > 1 || c.portal || c.light !== null || c.woosh !== null) {
    rule(`${label} at ${where(c)} must be on a block with nothing else on it.`, c);
  }
  // The plugin puts the redstone one up from a frame cell; what it lands in must be empty.
  const landing = c.block === "frame" ? cellAt(shape, c.layer, c.row - 1, c.col) : c;
  if (landing && isBuilt(landing)) {
    error(`${label} at ${where(c)} lands on a cell the frame already occupies, so the redstone there could never fire.`, c);
  }
  if (c.block === "none") {
    const below = cellAt(shape, c.layer, c.row + 1, c.col);
    // Under the bottom row is the ground, which the shape cannot say anything about.
    if (below && !isBuilt(below)) rule(`${label} at ${where(c)} should sit on top of a Stargate material block, with one directly below it.`, c);
  }
}

/** Light and woosh steps should run on without a gap; a gap is a tick that draws nothing. */
function checkOrders(cells, key, label, report) {
  const orders = [...new Set(cells.map((c) => c[key]).filter((n) => n !== null))].sort((a, b) => a - b);
  for (let i = 1; i < orders.length; i++) {
    if (orders[i] === orders[i - 1] + 1) continue;
    const first = orders[i - 1] + 1;
    const missing = orders[i] - 1 === first ? `#${first} is` : `#${first} through #${orders[i] - 1} are`;
    report(`${label} ${missing} never used, between #${orders[i - 1]} and #${orders[i]}; that tick draws nothing. /wormhole gate shapes reload refuses a shape with this, though a restart still loads it.`,
      cells.find((c) => c[key] === orders[i]));
  }
}
