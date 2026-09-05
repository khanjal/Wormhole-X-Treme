package com.wormhole_xtreme.wormhole.model.beam;

/**
 * The clamped, internally-consistent timings one beam sequence runs on.
 *
 * <p>Six durations come from config, and two of them have to sit strictly inside a third:
 * vanish must fire before the envelope ends, and the real teleport must fire before the rise
 * ends. Read naively, a server admin setting {@code BEAM_TELEPORT_AT_STEP} equal to or past
 * {@code BEAM_RISE_TICKS} would mean {@code tick == teleportAtStep} is never reached while
 * {@code tick < riseTicks} still holds -- the teleport condition never fires, and the
 * traveller is left frozen and invisible with no way out short of a restart. Resolving all
 * six together, once, in one place is what makes that impossible regardless of what a config
 * file says.
 */
public final class BeamTiming
{
    private final int envelopTicks;
    private final int vanishAtStep;
    private final int riseTicks;
    private final int teleportAtStep;
    private final int descendTicks;
    private final int fadeTicks;

    private BeamTiming(final int envelopTicks, final int vanishAtStep, final int riseTicks,
        final int teleportAtStep, final int descendTicks, final int fadeTicks)
    {
        this.envelopTicks = envelopTicks;
        this.vanishAtStep = vanishAtStep;
        this.riseTicks = riseTicks;
        this.teleportAtStep = teleportAtStep;
        this.descendTicks = descendTicks;
        this.fadeTicks = fadeTicks;
    }

    /**
     * Resolves six raw, possibly-adversarial config values into a set that cannot deadlock a
     * sequence.
     *
     * @param envelopTicks how long the glow gathers before opening into the column; floored
     *            at 2, since the brightness ramp divides by {@code envelopTicks - 1}
     * @param vanishAtStep how far into the envelope the traveller vanishes; clamped to
     *            {@code [0, envelopTicks - 1]}
     * @param riseTicks how long the column rises and departs; floored at 2, since the
     *            teleport step needs at least one tick to sit strictly inside it
     * @param teleportAtStep how far into the rise the real teleport fires; clamped to
     *            {@code [1, riseTicks - 1]} so it always fires before the rise ends
     * @param descendTicks how long the column takes to descend; floored at 1
     * @param fadeTicks how long the column takes to fade out after depositing the traveller;
     *            floored at 1
     * @return the resolved timings
     */
    public static BeamTiming resolve(final int envelopTicks, final int vanishAtStep, final int riseTicks,
        final int teleportAtStep, final int descendTicks, final int fadeTicks)
    {
        final int resolvedEnvelop = Math.max(2, envelopTicks);
        final int resolvedRise = Math.max(2, riseTicks);
        final int resolvedDescend = Math.max(1, descendTicks);
        final int resolvedFade = Math.max(1, fadeTicks);
        final int resolvedTeleport = clamp(teleportAtStep, 1, resolvedRise - 1);
        final int resolvedVanish = clamp(vanishAtStep, 0, resolvedEnvelop - 1);
        return new BeamTiming(resolvedEnvelop, resolvedVanish, resolvedRise, resolvedTeleport,
            resolvedDescend, resolvedFade);
    }

    private static int clamp(final int value, final int min, final int max)
    {
        return Math.min(Math.max(value, min), max);
    }

    public int envelopTicks() { return envelopTicks; }

    public int vanishAtStep() { return vanishAtStep; }

    public int riseTicks() { return riseTicks; }

    public int teleportAtStep() { return teleportAtStep; }

    public int descendTicks() { return descendTicks; }

    public int fadeTicks() { return fadeTicks; }
}
