// How a woosh cell (:W or :W#n) is told apart in the designer: a stepped tint for its order and
// the cue that is not colour. No page in here; designer.js applies it and the tests run it in node.

export const TINT = { min: 0.25, max: 0.6 };
/** From this cell size up there is room for the order as a number in the corner. */
export const BADGE_MIN_ZOOM = 28;

/** Where `order` falls among the shape's distinct woosh orders, counted from 0; -1 for none or an unknown one. */
export const wooshStep = (order, orders) => (order === null || order === undefined ? -1 : orders.indexOf(order));

/** The tint strength of a step: the first is the lightest and the last the darkest; a lone step is the middle. */
export function wooshTint(step, steps) {
  if (!Number.isInteger(step) || !Number.isInteger(steps) || step < 0 || steps < 1 || step >= steps) return TINT.min;
  if (steps === 1) return (TINT.min + TINT.max) / 2;
  return TINT.min + (TINT.max - TINT.min) * (step / (steps - 1));
}

/** The cues for a woosh cell at a cell size: stripes always (they survive any size), the order number when it fits. */
export const wooshCue = (zoom) => ({ stripes: true, number: Number.isFinite(zoom) && zoom >= BADGE_MIN_ZOOM });
