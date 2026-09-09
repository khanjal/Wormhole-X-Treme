package com.wormhole_xtreme.wormhole.model.beam;

/**
 * What a beam sequence should do on one tick, computed purely from the tick number and its
 * resolved {@link BeamTiming} -- no Bukkit types, no side effects, nothing that needs a
 * running server to compute or to test.
 *
 * <p>{@code BeamAnimation.Sequence} is the "dull" half this exists to make possible: for each
 * tick it asks {@link #at} what should happen and then does exactly that -- spawn a particle
 * column at a given height/offset/density, apply a potion effect, play a sound, fire the real
 * teleport -- with no arithmetic or phase-boundary decisions of its own left to get wrong.
 * Every quantity here is the same value the original, un-split sequence computed inline; this
 * only relocates the computation, not what it computes.
 *
 * <p>Mirrors the split the ring subsystem eventually grew ({@code RingCycle} for the
 * decisions, {@code RingTransit} for touching a live world) -- worth doing here for the same
 * reason: the ordering, the frame arithmetic and the phase boundaries are what is actually
 * easy to get subtly wrong (an off-by-one at a boundary reads as a visible stutter or a column
 * that starts one tick late), and none of that needs a server running to get right.
 *
 * <p>The four phases, and the marks between them, are the components rather than fourteen loose
 * fields. That grouping is not new -- it is the one this comment already described -- so
 * {@link Envelop}, {@link Column}, {@link Fade} and {@link Marks} only say in the type what was
 * previously said in prose. Rise and descent share {@link Column} because they are the same
 * thing travelling opposite ways: a full-density column at some offset.
 *
 * @param envelop
 *            the glow gathering around the traveller before they leave, rooted at their own
 *            live position by the caller since that is a Bukkit fact this class cannot know
 * @param rise
 *            the departure column, at constant maximum density
 * @param descend
 *            the arrival column, likewise
 * @param fade
 *            the glow dispersing after arrival
 * @param marks
 *            the one-shot boundaries between all of the above
 */
public record BeamFrame(Envelop envelop, Column rise, Column descend, Fade fade, Marks marks)
{
    /** Roughly a standing player's own height -- where the envelope gathers and where the
     * fade ends, before/after the taller departure/arrival column. */
    private static final double PLAYER_HEIGHT = 1.8;

    /** How tall the column stands during rise and descent. */
    private static final double COLUMN_HEIGHT = 3.0;

    /** How far the column travels while rising or descending. */
    private static final double TRAVEL_HEIGHT = 4.0;

    /** Particles per burst at the start of the envelope, and the end of the fade. */
    private static final int MIN_DENSITY = 1;

    /** Particles per burst once the glow has built up -- reached by the end of the envelope
     * and held constant through rise and descent; delivery and departure are not a second
     * and third build-up, only the envelope is. Public: rise and descend hold at this same
     * density throughout, a plain constant rather than anything {@link #at} computes per
     * tick, so {@code BeamAnimation} reads it directly rather than keeping its own copy of
     * the same number. */
    public static final int MAX_DENSITY = 8;

    /**
     * The glow gathering before departure, which is the only phase that ramps up.
     *
     * @param active
     *            whether it is drawing this tick
     * @param density
     *            particles per burst, ramping from {@code MIN_DENSITY} to {@code MAX_DENSITY}
     */
    public record Envelop(boolean active, int density) { }

    /**
     * A travelling column, used for both the rise and the descent.
     *
     * <p>One type for the two because they differ only in which way the offset runs; both draw
     * at {@code MAX_DENSITY} throughout, so neither carries a density of its own.
     *
     * @param active
     *            whether it is drawing this tick
     * @param yOffset
     *            how far above the anchor the column currently sits
     */
    public record Column(boolean active, double yOffset) { }

    /**
     * The glow dispersing after arrival, which shrinks and thins at once.
     *
     * @param active
     *            whether it is drawing this tick
     * @param height
     *            the column's current height, falling from {@code COLUMN_HEIGHT} toward a
     *            standing player's own
     * @param density
     *            particles per burst, falling back toward {@code MIN_DENSITY}
     */
    public record Fade(boolean active, double height, int density) { }

    /**
     * The one-shot events that mark the boundaries between phases.
     *
     * <p>Each is true on exactly one tick of a sequence. They are not mutually exclusive with
     * the phases or with each other -- {@code start} fires on the same tick the envelope first
     * draws.
     *
     * @param start
     *            the sequence's first tick
     * @param vanish
     *            hide the traveller
     * @param teleport
     *            the real move, between rise and descent
     * @param arrive
     *            the descent has landed
     * @param finished
     *            nothing left to draw; the sequence should stop
     */
    public record Marks(boolean start, boolean vanish, boolean teleport, boolean arrive,
        boolean finished) { }

    /**
     * Computes everything one tick needs to do.
     *
     * @param tick ticks since the sequence started, at zero
     * @param timing the sequence's resolved durations
     * @return the frame for that tick
     */
    public static BeamFrame at(final int tick, final BeamTiming timing)
    {
        final int envelopTicks = timing.envelopTicks();
        final boolean envelopActive = tick < envelopTicks;
        final int envelopDensity;
        if (envelopActive)
        {
            // Denominator is envelopTicks - 1, not envelopTicks, so the ramp actually
            // reaches MAX_DENSITY on the last rendered tick rather than falling just short
            // of it -- deliberately different from fade's denominator below, which does
            // fall just short of MIN_DENSITY on its last tick, since fade's active window
            // is checked the same exclusive way but was never re-tuned to match.
            final double progress = (double) tick / (double) (envelopTicks - 1);
            envelopDensity = MIN_DENSITY + (int) Math.round((MAX_DENSITY - MIN_DENSITY) * progress);
        }
        else
        {
            envelopDensity = 0;
        }

        final boolean vanish = tick == timing.vanishAtStep();

        final int riseTicks = timing.riseTicks();
        final int sinceRise = tick - envelopTicks;
        final boolean riseActive = (sinceRise >= 0) && (sinceRise < riseTicks);
        final double riseYOffset = riseActive ? TRAVEL_HEIGHT * ((double) sinceRise / (double) riseTicks) : 0.0;

        final boolean teleport = sinceRise == timing.teleportAtStep();

        final int descendTicks = timing.descendTicks();
        final int sinceTeleport = sinceRise - timing.teleportAtStep();
        final boolean descendActive = (sinceTeleport >= 0) && (sinceTeleport < descendTicks);
        final double descendYOffset = descendActive
            ? TRAVEL_HEIGHT * (1.0 - ((double) sinceTeleport / (double) descendTicks))
            : 0.0;

        final boolean arrive = sinceTeleport == descendTicks;

        final int fadeTicks = timing.fadeTicks();
        final int sinceDeposit = sinceTeleport - descendTicks;
        final boolean fadeActive = (sinceDeposit >= 0) && (sinceDeposit < fadeTicks);
        final double fadeHeight;
        final int fadeDensity;
        if (fadeActive)
        {
            final double fadeProgress = (double) sinceDeposit / (double) fadeTicks;
            fadeHeight = COLUMN_HEIGHT - ((COLUMN_HEIGHT - PLAYER_HEIGHT) * fadeProgress);
            fadeDensity = MAX_DENSITY - (int) Math.round((MAX_DENSITY - MIN_DENSITY) * fadeProgress);
        }
        else
        {
            fadeHeight = 0.0;
            fadeDensity = 0;
        }

        final boolean finished = sinceDeposit >= fadeTicks;

        return new BeamFrame(
            new Envelop(envelopActive, envelopDensity),
            new Column(riseActive, riseYOffset),
            new Column(descendActive, descendYOffset),
            new Fade(fadeActive, fadeHeight, fadeDensity),
            new Marks(tick == 0, vanish, teleport, arrive, finished));
    }

    /**
     * Roughly a standing player's own height.
     *
     * @return the height the envelope gathers at and the fade ends at
     */
    public double playerHeight() { return PLAYER_HEIGHT; }

    /**
     * How tall the column stands during rise and descent.
     *
     * @return the column height
     */
    public double columnHeight() { return COLUMN_HEIGHT; }
}
