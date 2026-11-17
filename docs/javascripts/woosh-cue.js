// How a woosh cell (:W or :W#n) is told apart in the designer: a stepped tint for its order and
// the cue that is not colour. No page in here; designer.js applies it and the tests run it in node.

export const TINT = { min: 0.1, max: 0.5 };
/** From this cell size up the step number fits in the corner beside the centred letter, two digits included. */
export const BADGE_MIN_ZOOM = 40;

/**
 * Which step of the woosh `order` is, counted from 0 among the shape's distinct orders (the Java plays
 * a gap in the numbers as an empty tick, but a tint has nothing to show for it, so gaps are collapsed here).
 * -1 for no order, an unknown one, or order 0: StargateHelper.recordWooshWaves starts at 1, so 0 never plays.
 */
export const wooshStep = (order, orders) => (order === null || order === undefined || order < 1 ? -1 : orders.indexOf(order));

/** The tint strength of a step: the first is the lightest and the last the darkest; a lone step is the middle. */
export function wooshTint(step, steps) {
  if (!Number.isInteger(step) || !Number.isInteger(steps) || step < 0 || steps < 1 || step >= steps) return TINT.min;
  if (steps === 1) return (TINT.min + TINT.max) / 2;
  return TINT.min + (TINT.max - TINT.min) * (step / (steps - 1));
}

/** The cues for a woosh cell at a cell size: stripes always (they survive any size), the step number when it fits. */
export const wooshCue = (zoom) => ({ stripes: true, number: Number.isFinite(zoom) && zoom >= BADGE_MIN_ZOOM });
