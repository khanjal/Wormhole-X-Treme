// The designer's window-layout rules with no page in them: cell zoom, the split between grid and
// 3D view, and the remembered view choices. designer.js applies them; the tests run them in node.

export const ZOOM = { min: 12, max: 48, step: 4, start: 40, legible: 16 };
const WHEEL_RATE = 0.0015; // a scroll notch (100) is about a sixth bigger or smaller
export const SPLIT = { start: 0.6, step: 0.02 };

const finite = (n) => typeof n === "number" && Number.isFinite(n);

/** A cell size in whole pixels inside the allowed range; anything that is not a number is the start size. */
export function clampZoom(n) {
  if (!finite(n)) return ZOOM.start;
  return Math.min(ZOOM.max, Math.max(ZOOM.min, Math.round(n)));
}

/** One step bigger (direction 1) or smaller (-1), staying inside the range. */
export const stepZoom = (current, direction) => clampZoom(clampZoom(current) + Math.sign(direction) * ZOOM.step);

/** A wheel event's delta in pixels, whatever unit the browser used (0 pixels, 1 lines, 2 pages), and never wild. */
export function wheelPixels(delta, mode = 0) {
  if (!finite(delta)) return 0;
  const px = delta * (mode === 1 ? 16 : mode === 2 ? 100 : 1);
  return Math.max(-400, Math.min(400, px));
}

/** The size after scrolling by `delta` pixels (negative zooms in): smooth, so a trackpad pinch of many tiny events does not jump. */
export const wheelZoom = (current, delta) => clampZoom(clampZoom(current) * Math.exp(-(finite(delta) ? delta : 0) * WHEEL_RATE));

/**
 * The largest cell size at which `columns` by `rows` cells fit a box of `width` by `height` pixels,
 * after the row and column header (`gutterX`, `gutterY`) and a little slack for the outer border.
 * When they do not fit even at the legible size, or the box is unusable, the legible size: the grid
 * then scrolls, which beats cells too small to read.
 */
export function fitZoom({ width, height, columns, rows, gutterX = 0, gutterY = 0, slack = 2 }) {
  const ok = [width, height, columns, rows].every(finite) && columns > 0 && rows > 0;
  if (!ok) return ZOOM.legible;
  const across = (width - gutterX - slack) / columns;
  const down = (height - gutterY - slack) / rows;
  return clampZoom(Math.max(ZOOM.legible, Math.floor(Math.min(across, down))));
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

// Whether the window is expanded is not kept: a page that opens as a full-window overlay is a surprise.
const DEFAULT_VIEW = { zoom: null, split: null, dockOpen: true, toolsOpen: true };

/** The remembered view choices from stored text; missing or damaged parts fall back one by one. */
export function parseViewState(raw) {
  const view = { ...DEFAULT_VIEW };
  let data;
  try { data = JSON.parse(raw); } catch { return view; }
  if (!data || typeof data !== "object" || Array.isArray(data)) return view;
  if (data.dockOpen === false) view.dockOpen = false;
  if (data.toolsOpen === false) view.toolsOpen = false;
  if (finite(data.zoom)) view.zoom = clampZoom(data.zoom);
  if (finite(data.split) && data.split > 0 && data.split < 1) view.split = data.split;
  return view;
}

export const viewStateText = (view) => JSON.stringify({
  zoom: view.zoom, split: view.split, dockOpen: view.dockOpen, toolsOpen: view.toolsOpen,
});
