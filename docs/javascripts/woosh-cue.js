// How a woosh cell (:W or :W#n) is told apart in the designer: a stepped tint for its order and
// the cue that is not colour. No page in here; designer.js applies it and the tests run it in node.

/** A long woosh is grouped into this many bands at most: more steps than that look the same to the eye. */
export const BANDS_MAX = 5;
/** From this cell size up the step number fits in the corner beside the centred letter, two digits included. */
export const BADGE_MIN_ZOOM = 40;

/**
 * Which step of the woosh `order` is, counted from 0 among the shape's distinct orders (gaps in the
 * numbers are collapsed here; the checks refuse a gap, and a start above 1 is a warning of its own).
 * -1 for no order, an unknown one, or order 0: StargateHelper.recordWooshWaves starts at 1, so 0 never plays.
 */
export const wooshStep = (order, orders) => (order === null || order === undefined || order < 1 ? -1 : orders.indexOf(order));

const validStep = (step, steps) => Number.isInteger(step) && Number.isInteger(steps) && steps >= 1 && step >= 0 && step < steps;

/** How many shades the steps make: one a step up to the limit, then the steps share. */
export const wooshBands = (steps) => Math.max(1, Math.min(steps, BANDS_MAX));

/** The band (0 is the lightest) a step falls in; -1 for a bad step. Steps are shared out evenly, the last band the strongest. */
export const wooshBand = (step, steps) => (validStep(step, steps) ? Math.floor((step * wooshBands(steps)) / steps) : -1);

/** The strength of a step's tint from 0 (lightest band) to 1 (strongest); a lone band is the middle, a bad step the lightest. */
export function wooshTint(step, steps) {
  const band = wooshBand(step, steps);
  if (band < 0) return 0;
  const bands = wooshBands(steps);
  return bands === 1 ? 0.5 : band / (bands - 1);
}

/** The cues for a woosh cell at a cell size: stripes always (they survive any size), the step number when it fits. */
export const wooshCue = (zoom) => ({ stripes: true, number: Number.isFinite(zoom) && zoom >= BADGE_MIN_ZOOM });

/**
 * Which cells each viewer mesh draws. A woosh of order 0 is never played (StargateHelper.recordWooshWaves
 * starts at 1), but a [P:W#0] cell is still filled as portal, so it counts as an ordinary portal cell.
 * `wave` and `stay` are sorted by step, so showing a step is setting a count.
 */
export function partitionCells(cells) {
  const played = (c) => c.woosh !== null && c.woosh >= 1;
  const byStep = (list) => list.sort((a, b) => a.woosh - b.woosh);
  return {
    portal: cells.filter((c) => c.portal && !played(c)),
    wave: byStep(cells.filter((c) => played(c) && !c.portal)),
    stay: byStep(cells.filter((c) => played(c) && c.portal)),
  };
}
