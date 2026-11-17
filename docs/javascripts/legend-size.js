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
 * The most the open Legend overlay may be, in px, given where it starts and the lowest line it may reach
 * (the stage's bottom or the window's, whichever is higher). It only matters where the entries do not
 * fit, and the overlay then scrolls rather than cover what is below.
 */
export function overlayMaxHeight(top, bottom) {
  const room = bottom - top - OVERLAY_MARGIN;
  return Number.isFinite(room) ? Math.max(MIN_OVERLAY, Math.floor(room)) : MIN_OVERLAY;
}

const OVERLAY_MARGIN = 8;
const MIN_OVERLAY = 160;
