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
 * <p>The four phases, and which fields mean something during each:
 * <ul>
 * <li><b>Envelop</b> -- {@link #isEnvelopActive()}, {@link #getEnvelopDensity()}. Rooted at
 * the traveller's own live position by the caller, not tracked here, since that is a Bukkit
 * fact this class has no way to know.</li>
 * <li><b>Rise</b> -- {@link #isRiseActive()}, {@link #getRiseYOffset()}, at constant maximum
 * density.</li>
 * <li><b>Descend</b> -- {@link #isDescendActive()}, {@link #getDescendYOffset()}, also at
 * constant maximum density.</li>
 * <li><b>Fade</b> -- {@link #isFadeActive()}, {@link #getFadeHeight()},
 * {@link #getFadeDensity()}.</li>
 * </ul>
 * Five one-shot events mark the boundaries between them: {@link #isStart()},
 * {@link #isVanish()}, {@link #isTeleport()}, {@link #isArrive()}, {@link #isFinished()}.
 */
public final class BeamFrame
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

    private final boolean start;
    private final boolean envelopActive;
    private final int envelopDensity;
    private final boolean vanish;
    private final boolean riseActive;
    private final double riseYOffset;
    private final boolean teleport;
    private final boolean descendActive;
    private final double descendYOffset;
    private final boolean arrive;
    private final boolean fadeActive;
    private final double fadeHeight;
    private final int fadeDensity;
    private final boolean finished;

    private BeamFrame(final boolean start, final boolean envelopActive, final int envelopDensity,
        final boolean vanish, final boolean riseActive, final double riseYOffset, final boolean teleport,
        final boolean descendActive, final double descendYOffset, final boolean arrive,
        final boolean fadeActive, final double fadeHeight, final int fadeDensity, final boolean finished)
    {
        this.start = start;
        this.envelopActive = envelopActive;
        this.envelopDensity = envelopDensity;
        this.vanish = vanish;
        this.riseActive = riseActive;
        this.riseYOffset = riseYOffset;
        this.teleport = teleport;
        this.descendActive = descendActive;
        this.descendYOffset = descendYOffset;
        this.arrive = arrive;
        this.fadeActive = fadeActive;
        this.fadeHeight = fadeHeight;
        this.fadeDensity = fadeDensity;
        this.finished = finished;
    }

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

        return new BeamFrame(tick == 0, envelopActive, envelopDensity, vanish, riseActive, riseYOffset,
            teleport, descendActive, descendYOffset, arrive, fadeActive, fadeHeight, fadeDensity, finished);
    }

    public double playerHeight() { return PLAYER_HEIGHT; }

    public double columnHeight() { return COLUMN_HEIGHT; }

    public boolean isStart() { return start; }

    public boolean isEnvelopActive() { return envelopActive; }

    public int getEnvelopDensity() { return envelopDensity; }

    public boolean isVanish() { return vanish; }

    public boolean isRiseActive() { return riseActive; }

    public double getRiseYOffset() { return riseYOffset; }

    public boolean isTeleport() { return teleport; }

    public boolean isDescendActive() { return descendActive; }

    public double getDescendYOffset() { return descendYOffset; }

    public boolean isArrive() { return arrive; }

    public boolean isFadeActive() { return fadeActive; }

    public double getFadeHeight() { return fadeHeight; }

    public int getFadeDensity() { return fadeDensity; }

    public boolean isFinished() { return finished; }
}
