// Reads a gate .shape file (Version=2) the way Stargate3DShape and StargateShapeLayer do.
// Coordinates are the plugin's own: layer = depth, y counts up from the bottom row, x is the
// file's column mirrored (width - 1 - column). Keep this in step with those two classes.

const CELL = /\[(.+?)\]/g;
const SINGLES = new Set(["N", "EP", "EM", "A", "D", "IA", "RA", "RD", "RS"]);

/** Caps, because a shape from an upload is untrusted; no shipped shape is near any of them. */
export const LIMITS = { bytes: 64 * 1024, side: 64, layers: 64, cells: 20000, orders: 256 };

/** A shape file the plugin would refuse to load, or one too big to draw. */
export class ShapeError extends Error {}

/** Rows and columns of the first block grid after "GateShape=", as measureGrid counts them. */
function measureGrid(lines, from) {
  let index = from;
  while (index < lines.length && !lines[index].startsWith("[")) index++;
  let height = 0;
  let width = 0;
  while (index < lines.length && lines[index].startsWith("[")) {
    if (width <= 0) width = (lines[index].match(CELL) || []).length;
    height++;
    index++;
  }
  if (height <= 0 || width <= 0) throw new ShapeError("No block grid follows GateShape=.");
  if (height > LIMITS.side || width > LIMITS.side) {
    throw new ShapeError(`The grid is ${width} by ${height}; at most ${LIMITS.side} each way is drawn.`);
  }
  return { height, width };
}

/**
 * The rows of one layer, as readLayerLines collects them: a comment line is stored in the slot
 * of the row before it and replaces that row, so the row above a comment is lost.
 */
function readLayerRows(lines, start, height, layer) {
  const rows = new Array(height).fill(null);
  let i = start;
  let slot = 0;
  while (i < lines.length && (lines[i].startsWith("[") || lines[i].startsWith("#"))) {
    if (slot >= height) throw new ShapeError(`Layer#${layer} has more rows than the ${height}-row grid.`);
    rows[slot] = lines[i];
    i++;
    if (i >= lines.length || !lines[i].startsWith("#")) slot++;
  }
  if (rows.includes(null)) throw new ShapeError(`Layer#${layer} has fewer rows than the ${height}-row grid.`);
  // Past the line that ended the layer, as the plugin does.
  return { rows, next: i + 1 };
}

/** parseInt that refuses what Integer.parseInt refuses. */
function integer(text, what) {
  if (!/^[+-]?\d+$/.test(text.trim())) throw new ShapeError(`${what} is not a number: "${text}".`);
  return parseInt(text, 10);
}

function parseCell(mods, geometry) {
  const upper = mods.map((m) => m.toUpperCase());
  const frame = upper.includes("S");
  const chevron = upper.includes("C");
  const cell = {
    ...geometry,
    // S with C is the lenient chevron: a chevron cell, not a frame cell as well.
    block: chevron ? "chevron" : frame ? "frame" : "none",
    lenientChevron: frame && chevron,
    portal: upper.includes("P"),
    markers: [],
    light: null,
    woosh: null,
  };
  for (const m of upper) {
    if (m === "S" || m === "P" || m === "C") continue;
    if (SINGLES.has(m)) cell.markers.push(m);
    else if (m.includes("L")) cell.light = m.includes("#") ? integer(m.split("#")[1], "A light order") : 1;
    else if (m.includes("W")) cell.woosh = m.includes("#") ? integer(m.split("#")[1], "A woosh order") : 1;
  }
  return cell;
}

/** "KEY = value;" to "KEY=value", as normaliseSetting does. */
function normaliseSetting(raw) {
  let s = raw.trim();
  if (s.endsWith(";")) s = s.slice(0, -1).trim();
  return s.replace(/\s*=\s*/, "=");
}

function applySetting(shape, rawLine) {
  const line = normaliseSetting(rawLine);
  const parts = line.split("=");
  if (parts.length <= 1) return;
  const value = parts[1];
  if (/^[A-Z]+_MATERIAL=/.test(line)) shape.materials[parts[0]] = value;
  else if (line.includes("LIGHT_TICKS=")) shape.lightTicks = integer(value, "LIGHT_TICKS");
  else if (line.includes("WOOSH_TICKS=")) shape.wooshTicks = integer(value, "WOOSH_TICKS");
  else if (line.startsWith("REDSTONE_ACTIVATED=")) shape.redstoneActivated = value.toLowerCase() === "true";
}

function distinctSorted(cells, key) {
  return [...new Set(cells.map((c) => c[key]).filter((n) => n !== null))].sort((a, b) => a - b);
}

/**
 * Parses shape file text into its cells, orders and settings.
 * @throws {ShapeError} for what Stargate3DShape refuses (no grid, a ragged layer, a bad number,
 *   no :EP) and for a file over LIMITS.
 */
export function parseShape(text) {
  if (text.length > LIMITS.bytes) {
    throw new ShapeError(`The file is over ${LIMITS.bytes / 1024} KB.`);
  }
  const lines = text.split(/\r\n|\n|\r/);
  const shape = {
    name: null, width: 0, height: 0, layers: 0, cells: [], lightTicks: null, wooshTicks: null,
    redstoneActivated: false, materials: {},
  };
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    i++;
    if (line.startsWith("#")) continue;
    if (line.includes("Name=")) {
      shape.name = line.split("=")[1];
    } else if (line === "GateShape=") {
      const grid = measureGrid(lines, i - 1);
      shape.height = grid.height;
      shape.width = grid.width;
    } else if (line.startsWith("Layer")) {
      if (shape.height === 0) throw new ShapeError("A Layer comes before any GateShape= line.");
      const layer = integer(line.trim().split(/[#=]/)[1] ?? "", "A layer number");
      if (layer < 1 || layer > LIMITS.layers) {
        throw new ShapeError(`Layer#${layer}: layers are numbered 1 to ${LIMITS.layers}.`);
      }
      const { rows, next } = readLayerRows(lines, i, shape.height, layer);
      i = next;
      // A layer named twice replaces the first, as recordLayer's list slot does.
      shape.cells = shape.cells.filter((c) => c.layer !== layer);
      rows.forEach((rowText, row) => {
        let col = 0;
        for (const m of rowText.matchAll(CELL)) {
          const geometry = { layer, row, col, y: shape.height - 1 - row, x: shape.width - 1 - col };
          shape.cells.push(parseCell(m[1].split(":"), geometry));
          col++;
        }
        if (shape.cells.length > LIMITS.cells) {
          throw new ShapeError(`More than ${LIMITS.cells} cells; that is too many to draw.`);
        }
      });
      shape.layers = Math.max(shape.layers, layer);
    } else {
      applySetting(shape, line);
    }
  }
  if (!shape.cells.some((c) => c.markers.includes("EP"))) {
    throw new ShapeError("No :EP block, where players teleport in; the plugin refuses a shape without one.");
  }
  shape.lightOrders = distinctSorted(shape.cells, "light");
  shape.wooshOrders = distinctSorted(shape.cells, "woosh");
  if (shape.lightOrders.length > LIMITS.orders || shape.wooshOrders.length > LIMITS.orders) {
    throw new ShapeError(`More than ${LIMITS.orders} light or woosh steps.`);
  }
  return shape;
}

/** The counts the viewer prints beside the picture. */
export function describeShape(shape) {
  const count = (test) => shape.cells.filter(test).length;
  const has = (m) => shape.cells.some((c) => c.markers.includes(m));
  return {
    name: shape.name,
    width: shape.width,
    height: shape.height,
    layers: shape.layers,
    // Frame blocks that do not light; the lit ones are counted as chevrons.
    frame: count((c) => c.block === "frame" && c.light === null),
    chevrons: count((c) => c.light !== null),
    lightSteps: shape.lightOrders.length,
    portal: count((c) => c.portal),
    wooshCells: count((c) => c.woosh !== null),
    wooshSteps: shape.wooshOrders.length,
    dialSign: has("D"),
    redstone: ["RD", "RS", "RA"].filter(has),
  };
}
