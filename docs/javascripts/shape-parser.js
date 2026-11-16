// Reads a gate .shape file (Version=2) the way Stargate3DShape and StargateShapeLayer do.
// Coordinates are the plugin's own: layer = depth, y counts up from the bottom row, x is the
// file's column mirrored (width - 1 - column). Keep this in step with those two classes.

const CELL = /\[(.+?)\]/g;
const SINGLES = new Set(["N", "EP", "EM", "A", "D", "IA", "RA", "RD", "RS"]);

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
  if (height <= 0 || width <= 0) throw new Error("no block grid after GateShape=");
  return { height, width };
}

/** The rows of one layer: lines starting "[" up to the first line that is neither "[" nor "#". */
function readLayerRows(lines, start) {
  const rows = [];
  let i = start;
  while (i < lines.length && (lines[i].startsWith("[") || lines[i].startsWith("#"))) {
    if (lines[i].startsWith("[")) rows.push(lines[i]);
    i++;
  }
  // Past the line that ended the layer, as the plugin does.
  return { rows, next: i + 1 };
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
    else if (m.includes("L")) cell.light = m.includes("#") ? parseInt(m.split("#")[1], 10) : 1;
    else if (m.includes("W")) cell.woosh = m.includes("#") ? parseInt(m.split("#")[1], 10) : 1;
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
  else if (line.includes("LIGHT_TICKS=")) shape.lightTicks = parseInt(value, 10);
  else if (line.includes("WOOSH_TICKS=")) shape.wooshTicks = parseInt(value, 10);
  else if (line.startsWith("REDSTONE_ACTIVATED=")) shape.redstoneActivated = value.toLowerCase() === "true";
}

function distinctSorted(cells, key) {
  return [...new Set(cells.map((c) => c[key]).filter((n) => n !== null))].sort((a, b) => a - b);
}

/** Parses shape file text into its cells, orders and settings. */
export function parseShape(text) {
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
      const layer = parseInt(line.trim().split(/[#=]/)[1], 10);
      const { rows, next } = readLayerRows(lines, i);
      i = next;
      rows.forEach((rowText, row) => {
        let col = 0;
        for (const m of rowText.matchAll(CELL)) {
          const geometry = { layer, row, col, y: shape.height - 1 - row, x: shape.width - 1 - col };
          shape.cells.push(parseCell(m[1].split(":"), geometry));
          col++;
        }
      });
      shape.layers = Math.max(shape.layers, layer);
    } else {
      applySetting(shape, line);
    }
  }
  shape.lightOrders = distinctSorted(shape.cells, "light");
  shape.wooshOrders = distinctSorted(shape.cells, "woosh");
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
