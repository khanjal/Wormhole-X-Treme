// The designer's window-layout rules with no page in them: cell zoom, the split between grid and
// 3D view, and the remembered view choices. designer.js applies them; the tests run them in node.

export const ZOOM = { min: 12, max: 48, step: 4, start: 40 };
export const SPLIT = { start: 0.6, step: 0.02 };

const finite = (n) => typeof n === "number" && Number.isFinite(n);

/** A cell size in whole pixels inside the allowed range; anything that is not a number is the start size. */
export function clampZoom(n) {
  if (!finite(n)) return ZOOM.start;
  return Math.min(ZOOM.max, Math.max(ZOOM.min, Math.round(n)));
}

/** One step bigger (direction 1) or smaller (-1), staying inside the range. */
export const stepZoom = (current, direction) => clampZoom(clampZoom(current) + Math.sign(direction) * ZOOM.step);

/**
 * The largest cell size at which `columns` by `rows` cells fit a box of `width` by `height` pixels,
 * after the row and column header (`gutterX`, `gutterY`) and a little slack for the outer border.
 * When even the smallest size does not fit, or the box is unusable, the smallest size.
 */
export function fitZoom({ width, height, columns, rows, gutterX = 0, gutterY = 0, slack = 2 }) {
  const ok = [width, height, columns, rows].every(finite) && columns > 0 && rows > 0;
  if (!ok) return ZOOM.min;
  const across = (width - gutterX - slack) / columns;
  const down = (height - gutterY - slack) / rows;
  return clampZoom(Math.floor(Math.min(across, down)));
}

/** The share of the split the grid may take, given the room and each side's smallest size in pixels. */
export function splitBounds(total, minMain, minDock) {
  if (!finite(total) || total <= minMain + minDock) return { lo: 0.5, hi: 0.5 };
  return { lo: minMain / total, hi: 1 - minDock / total };
}

/** `fraction` (the grid's share) held inside the bounds, so neither side can vanish. */
export function clampSplit(fraction, total, minMain, minDock) {
  const { lo, hi } = splitBounds(total, minMain, minDock);
  return Math.min(hi, Math.max(lo, finite(fraction) ? fraction : SPLIT.start));
}

const DEFAULT_VIEW = { expanded: false, zoom: null, split: null, dockOpen: true, toolsOpen: true };

/** The remembered view choices from stored text; missing or damaged parts fall back one by one. */
export function parseViewState(raw) {
  const view = { ...DEFAULT_VIEW };
  let data;
  try { data = JSON.parse(raw); } catch { return view; }
  if (!data || typeof data !== "object" || Array.isArray(data)) return view;
  if (data.expanded === true) view.expanded = true;
  if (data.dockOpen === false) view.dockOpen = false;
  if (data.toolsOpen === false) view.toolsOpen = false;
  if (finite(data.zoom)) view.zoom = clampZoom(data.zoom);
  if (finite(data.split) && data.split > 0 && data.split < 1) view.split = data.split;
  return view;
}

export const viewStateText = (view) => JSON.stringify({
  expanded: view.expanded, zoom: view.zoom, split: view.split, dockOpen: view.dockOpen, toolsOpen: view.toolsOpen,
});
