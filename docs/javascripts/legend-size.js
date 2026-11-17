// The sizing rules of a viewer's legend, kept free of the page so node can test them.

/**
 * The tallest legend seen so far: the stage is kept at least that tall, so switching to a shape with a
 * shorter legend does not make the page below it jump. A measure that is not a positive number (the
 * legend is hidden behind its button) leaves the reserve as it was.
 */
export function reserveHeight(reserved, measured) {
  if (!Number.isFinite(measured) || measured <= 0) return reserved;
  return Math.max(reserved, Math.ceil(measured));
}

/**
 * The most the open Legend overlay may be, in px: the window less room for the page bar and a margin. It
 * only matters in a window too short for every entry, where the overlay then scrolls rather than run off.
 */
export function overlayMaxHeight(viewportHeight) {
  const room = viewportHeight - OVERLAY_MARGIN;
  return Number.isFinite(room) ? Math.max(MIN_OVERLAY, Math.floor(room)) : MIN_OVERLAY;
}

const OVERLAY_MARGIN = 96;
const MIN_OVERLAY = 160;
