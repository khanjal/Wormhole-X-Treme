// The order an iris closes and opens in. A port of IrisSweep and IrisSweepDriver: keep it in step
// with them. Cells are the opening's cells as irisCells() lists them; a step is a list of indexes
// into that list, so no cell is copied and none can be named twice.

/** Ticks between one step and the next, and the most a whole crossing may take. */
export const STEP_TICKS = 2;
export const SWEEP_MAX_TICKS = 20;
/** The most steps a crossing is drawn in; a wider opening merges rings into bands. */
export const STEP_CAP = Math.max(2, Math.floor(SWEEP_MAX_TICKS / Math.max(1, STEP_TICKS)));

/** The five values of `gate-iris-animation`; `instant` is no sweep at all. */
export const STYLES = ["sweep", "spiral", "rows", "columns", "instant"];

/** The opening's cells, in the order the plugin records them: layer by layer, in file order. */
export function irisCells(shape) {
  return shape.cells.filter((c) => c.portal);
}

// Cells are at (col, y, layer); only differences matter. The spiral alone needs a side: columns
// count up towards the right of a player at the activation switch, as the 3D viewers draw them.
const centreOf = (cells) => {
  const lo = { col: Infinity, y: Infinity, layer: Infinity };
  const hi = { col: -Infinity, y: -Infinity, layer: -Infinity };
  for (const c of cells) {
    for (const k of Object.keys(lo)) {
      lo[k] = Math.min(lo[k], c[k]);
      hi[k] = Math.max(hi[k], c[k]);
    }
  }
  return { col: (lo.col + hi.col) / 2, y: (lo.y + hi.y) / 2, layer: (lo.layer + hi.layer) / 2 };
};

/** What a style groups a cell by: a squared distance along the axes it counts. */
function keyOf(centre, c, style) {
  const dx = c.col - centre.col;
  const dy = c.y - centre.y;
  const dz = c.layer - centre.layer;
  if (style === "rows") return dy * dy;
  if (style === "columns") return dx * dx + dz * dz;
  return dx * dx + dy * dy + dz * dz;
}

/** Cuts a list into exactly `wanted` pieces by position, as IrisSweep.cut does. */
function cut(items, wanted) {
  const at = (i) => {
    const product = i * items.length;
    return (product - (product % wanted)) / wanted; // integer division, as Java does it
  };
  const pieces = [];
  for (let i = 0; i < wanted; i++) pieces.push(items.slice(at(i), at(i + 1)));
  return pieces;
}

const byPlace = (cells) => (a, b) =>
  cells[a].y - cells[b].y || cells[a].col - cells[b].col || cells[a].layer - cells[b].layer;

/** The cells grouped by how far they sit from the centre, nearest ring first. */
function rings(cells, style) {
  if (cells.length === 0) return [];
  const centre = centreOf(cells);
  const groups = new Map();
  cells.forEach((c, i) => {
    const key = keyOf(centre, c, style);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(i);
  });
  return [...groups.keys()].sort((a, b) => a - b).map((k) => groups.get(k).sort(byPlace(cells)));
}

/** About the most turns the spiral makes, as IrisSweep.ARM_TURNS. */
const ARM_TURNS = 5;

/**
 * The cells along one spiral arm, wound in clockwise from the rim as seen from the activation
 * switch's side, as IrisSweep.closingArm. Upright openings only: there, columns count up towards
 * that viewer's right.
 */
export function closingArm(cells) {
  const centre = centreOf(cells);
  const polar = cells.map((c) => {
    const right = c.col - centre.col;
    const up = c.y - centre.y;
    let turn = Math.atan2(right, up) / (2 * Math.PI); // clockwise from straight up
    if (turn < 0) turn += 1;
    return { radius: Math.hypot(right, up), turn };
  });
  const outermost = Math.max(...polar.map((p) => p.radius));
  const width = Math.max(1, Math.ceil(outermost / ARM_TURNS));
  const along = polar.map((p) => Math.round((outermost - p.radius) / width - p.turn) + p.turn);
  // Down the arm from the rim; across its width, the outer cell first.
  return cells.map((_, i) => i).sort((a, b) => along[a] - along[b] || polar[b].radius - polar[a].radius);
}

/** The closing arm run backwards, in as many steps as the sweep makes. */
function spiral(cells) {
  if (cells.length === 0) return [];
  return cut(closingArm(cells).reverse(), Math.min(cells.length, Math.max(1, rings(cells, "sweep").length)));
}

/** Joins adjacent steps until there are no more than `maxSteps`; 0 leaves them alone. */
function atMost(steps, maxSteps) {
  if (maxSteps <= 0 || steps.length <= maxSteps) return steps;
  return cut(steps, maxSteps).map((band) => band.flat());
}

/**
 * The steps an opening iris uncovers its cells in, the first drawn first: from the middle out.
 * `instant` is the whole opening at once.
 */
export function openingOrder(cells, style, maxSteps = STEP_CAP) {
  if (cells.length === 0) return [];
  if (style === "instant") return [cells.map((_, i) => i)];
  const steps = style === "spiral" ? spiral(cells) : rings(cells, style);
  return atMost(steps, maxSteps);
}

/** The steps a closing iris covers its cells in: the opening order run backwards, rim first. */
export function closingOrder(cells, style, maxSteps = STEP_CAP) {
  return openingOrder(cells, style, maxSteps).reverse();
}

/** How long the page holds the iris shut before it opens again, and open before it ends, in ticks. */
export const HOLD_SHUT_TICKS = 20;
export const HOLD_OPEN_TICKS = 10;

/**
 * Where a closing then opening iris of `steps` steps is at a tick: the first step is drawn at
 * tick 0 and each next STEP_TICKS later. `covered` is how many steps of the closing order are over
 * the opening; closing steps cover from the front of that order and opening ones uncover from the back.
 * `arriving` is true while the step just drawn is a closing one, so its cells are the ones to point out.
 */
export function irisStateAt(steps, tick) {
  const closedAt = (steps - 1) * STEP_TICKS;
  const openFrom = closedAt + HOLD_SHUT_TICKS;
  const total = openFrom + (steps - 1) * STEP_TICKS + HOLD_OPEN_TICKS;
  if (tick <= closedAt) {
    const drawn = Math.floor(tick / STEP_TICKS) + 1;
    return { phase: tick === closedAt ? "closed" : "closing", step: drawn, covered: drawn, arriving: true, total };
  }
  if (tick < openFrom) return { phase: "closed", step: steps, covered: steps, arriving: false, total };
  const drawn = Math.min(steps, Math.floor((tick - openFrom) / STEP_TICKS) + 1);
  const covered = steps - drawn;
  return { phase: covered === 0 ? "open" : "opening", step: drawn, covered, arriving: false, total };
}
