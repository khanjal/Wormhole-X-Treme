// Writes a shape model back out as Version=2 shape file text, in the layout the shipped files use,
// so Stargate3DShape reads it and parseShape in shape-parser.js reads the same cells back.

const REDSTONE = new Set(["RD", "RS", "RA"]);
const ORDER_OF_MATERIALS = ["PORTAL_MATERIAL", "IRIS_MATERIAL", "STARGATE_MATERIAL", "ACTIVE_MATERIAL", "CHEVRON_MATERIAL", "SIGN_MATERIAL"];

/** One line of text: a line break inside a value would start a second line in the file. */
const oneLine = (value) => String(value).replace(/[\r\n]+/g, " ");

/** The text between the brackets of one cell. */
export function cellText(cell) {
  const parts = [];
  if (cell.block === "chevron") parts.push(...(cell.lenientChevron ? ["S", "C"] : ["C"]));
  if (cell.block === "frame") parts.push("S");
  if (cell.portal) parts.push("P");
  parts.push(...cell.markers);
  if (cell.light !== null) parts.push(`L#${cell.light}`);
  if (cell.woosh !== null) parts.push(`W#${cell.woosh}`);
  const bare = parts.length === 1 && REDSTONE.has(parts[0]);
  // Nothing else says the cell is Ignored; the redstone blocks are written bare, as the shipped files do.
  if (!cell.portal && cell.block === "none" && !bare) parts.unshift("I");
  return parts.join(":");
}

/**
 * The shape as file text: a short header, the name, the layers from 1 up, then the settings.
 * @param {object} shape a model as parseShape or the designer makes it, every layer a full grid
 */
export function writeShape(shape) {
  const grid = new Map(shape.cells.map((c) => [`${c.layer},${c.row},${c.col}`, c]));
  const out = [
    "# Made with the gate designer in the Wormhole X-Treme docs.",
    "# Put it in plugins/WormholeXTreme/shapes/gate/, then check it with /wormhole gate shapes validate <name>.",
    `Name=${oneLine(shape.name ?? "")}`,
    "Version=2",
    "",
    "GateShape=",
    "",
  ];
  for (let layer = 1; layer <= shape.layers; layer++) {
    out.push(`Layer#${layer}=`);
    for (let row = 0; row < shape.height; row++) {
      let line = "";
      for (let col = 0; col < shape.width; col++) {
        const cell = grid.get(`${layer},${row},${col}`);
        line += `[${cell ? cellText(cell) : "I"}]`;
      }
      out.push(line);
    }
    // A layer ends at the first line that is not a row; the last one needs one too.
    out.push("");
  }
  if (shape.wooshTicks !== null) out.push(`WOOSH_TICKS=${shape.wooshTicks}`);
  if (shape.lightTicks !== null) out.push(`LIGHT_TICKS=${shape.lightTicks}`);
  for (const key of ORDER_OF_MATERIALS) {
    const value = oneLine(shape.materials[key] ?? "").trim();
    if (value) out.push(`${key}=${value}`);
  }
  for (const line of shape.extra ?? []) out.push(oneLine(line));
  out.push(`REDSTONE_ACTIVATED=${shape.redstoneActivated ? "TRUE" : "FALSE"}`);
  return `${out.join("\n")}\n`;
}
