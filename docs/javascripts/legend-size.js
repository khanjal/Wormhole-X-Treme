// The sizing rules of a viewer's legend, kept free of the page so node can test them.

/**
 * The tallest legend seen so far: the stage is kept at least that tall, so switching to a shape with a
 * shorter legend does not make the page below it jump. A measure that is not a number leaves the reserve
 * as it was (zero or less can never raise it).
 */
export function reserveHeight(reserved, measured) {
  if (!Number.isFinite(measured)) return reserved;
  return Math.max(reserved, Math.ceil(measured));
}

/**
 * The legend's own height for the reserve: the list as measured, less the explanations a reader has
 * opened (heights in px), so opening one does not make the stage taller for good.
 */
export function collapsedHeight(measured, openNotes) {
  const open = openNotes.filter(Number.isFinite).reduce((sum, h) => sum + Math.max(0, h), 0);
  return Math.max(0, measured - open);
}

/**
 * The name a screen reader gets for an entry's "?" button: the entry's words, without the glyph or
 * swatch character that leads the visible text.
 */
export function detailsName(label) {
  return `Details: ${String(label).replace(/^[^\p{L}\p{N}]+/u, "").trim()}`;
}

/**
 * Where the open Legend overlay starts, in window px, so every entry shows and it never covers its
 * own button: below the button if the list fits above the window's bottom, else above the button if it
 * fits below `minTop` (the page header's bottom edge), else against the window's bottom. Only a window
 * shorter than the list leaves `maxHeight` (px, else null), and the overlay then scrolls.
 */
export function overlayPlacement({ toggleTop, toggleBottom, natural, viewport, minTop }) {
  if (![toggleTop, toggleBottom, natural, viewport].every(Number.isFinite)) {
    return { top: Number.isFinite(toggleBottom) ? toggleBottom + GAP : 0, maxHeight: null };
  }
  const floor = Math.max(Number.isFinite(minTop) ? minTop : 0, MARGIN);
  const limit = viewport - MARGIN;
  const below = toggleBottom + GAP;
  if (below + natural <= limit) return { top: below, maxHeight: null };
  const above = toggleTop - GAP - natural;
  if (above >= floor) return { top: above, maxHeight: null };
  if (natural <= limit - floor) return { top: limit - natural, maxHeight: null };
  return { top: floor, maxHeight: Math.max(MIN_OVERLAY, Math.floor(limit - floor)) };
}

const GAP = 4;
const MARGIN = 8;
const MIN_OVERLAY = 160;
