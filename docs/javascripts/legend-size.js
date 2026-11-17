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
 * Where the open Legend overlay goes so every entry shows: `top` is where it starts in the window and
 * `natural` its height with room to spare. The overlay may run past the stage, but not the window, so a
 * list that would is lifted by `shift` px (never above the window's top), and only a window shorter than
 * the list itself leaves `maxHeight` (px, else null) and the overlay scrolls.
 */
export function overlayPlacement(top, natural, viewportHeight) {
  const limit = viewportHeight - OVERLAY_MARGIN;
  if (![top, natural, limit].every(Number.isFinite)) return { shift: 0, maxHeight: null };
  const shift = Math.min(Math.max(0, top + natural - limit), Math.max(0, top - OVERLAY_MARGIN));
  const room = Math.floor(limit - (top - shift));
  return { shift, maxHeight: room >= natural ? null : Math.max(MIN_OVERLAY, room) };
}

const OVERLAY_MARGIN = 8;
const MIN_OVERLAY = 160;
