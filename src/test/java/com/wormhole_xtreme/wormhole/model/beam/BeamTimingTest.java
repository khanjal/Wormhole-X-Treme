package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * A misconfigured beam timing must never freeze a traveller forever.
 *
 * <p>{@link BeamAnimation.Sequence} checks {@code tick == teleportAtStep} while
 * {@code tick < riseTicks} to decide when the real teleport fires. If an admin set
 * {@code BEAM_TELEPORT_AT_STEP} equal to or past {@code BEAM_RISE_TICKS}, that equality would
 * never be reached inside the rise loop -- the traveller would stay frozen and invisible with
 * no way out short of a server restart. The same shape of bug applies to vanishing strictly
 * inside the envelope. {@link BeamTiming#resolve} is where six independently-configurable
 * values get reconciled against each other so that no combination of them can produce this.
 */
public class BeamTimingTest
{
    @Test
    public void reasonableConfigurationPassesThroughUnchanged()
    {
        final BeamTiming timing = BeamTiming.resolve(12, 6, 18, 12, 20, 8);
        assertEquals(12, timing.envelopTicks());
        assertEquals(6, timing.vanishAtStep());
        assertEquals(18, timing.riseTicks());
        assertEquals(12, timing.teleportAtStep());
        assertEquals(20, timing.descendTicks());
        assertEquals(8, timing.fadeTicks());
    }

    @Test
    public void aTeleportStepAtTheRiseLengthWouldNeverFireAndIsPulledInsideIt()
    {
        final BeamTiming timing = BeamTiming.resolve(12, 6, 18, 18, 20, 8);
        assertTrue(timing.teleportAtStep() < timing.riseTicks(),
            "teleportAtStep == riseTicks means tick == teleportAtStep is never true while "
                + "tick < riseTicks still holds -- the teleport would never fire and the "
                + "traveller would be stuck frozen and invisible");
    }

    @Test
    public void aTeleportStepPastTheRiseLengthIsAlsoPulledInsideIt()
    {
        final BeamTiming timing = BeamTiming.resolve(12, 6, 18, 500, 20, 8);
        assertEquals(17, timing.teleportAtStep(), "clamped to riseTicks - 1, the last tick "
            + "the rise loop still runs on");
    }

    @Test
    public void aTeleportStepOfZeroOrBelowIsRaisedToOne()
    {
        assertEquals(1, BeamTiming.resolve(12, 6, 18, 0, 20, 8).teleportAtStep());
        assertEquals(1, BeamTiming.resolve(12, 6, 18, -5, 20, 8).teleportAtStep());
    }

    @Test
    public void aVanishStepAtOrPastTheEnvelopeWouldNeverFireAndIsPulledInsideIt()
    {
        final BeamTiming atLength = BeamTiming.resolve(12, 12, 18, 12, 20, 8);
        assertTrue(atLength.vanishAtStep() < atLength.envelopTicks(),
            "vanishAtStep == envelopTicks means the traveller is never actually made "
                + "invisible before the rise takes over, so bystanders would see them "
                + "vanish mid-air instead of during the envelope");

        final BeamTiming pastLength = BeamTiming.resolve(12, 500, 18, 12, 20, 8);
        assertEquals(11, pastLength.vanishAtStep(), "clamped to envelopTicks - 1");
    }

    @Test
    public void aNegativeVanishStepIsRaisedToZero()
    {
        assertEquals(0, BeamTiming.resolve(12, -5, 18, 12, 20, 8).vanishAtStep());
    }

    @Test
    public void anEnvelopeOrRiseBelowTwoTicksIsFlooredAtTwo()
    {
        // The brightness/travel ramps divide by (ticks - 1); one tick would divide by zero.
        final BeamTiming zero = BeamTiming.resolve(0, 0, 0, 1, 20, 8);
        assertEquals(2, zero.envelopTicks());
        assertEquals(2, zero.riseTicks());

        final BeamTiming one = BeamTiming.resolve(1, 0, 1, 1, 20, 8);
        assertEquals(2, one.envelopTicks());
        assertEquals(2, one.riseTicks());
    }

    @Test
    public void aDescendOrFadeOfZeroOrBelowIsFlooredAtOne()
    {
        final BeamTiming timing = BeamTiming.resolve(12, 6, 18, 12, 0, -3);
        assertEquals(1, timing.descendTicks());
        assertEquals(1, timing.fadeTicks());
    }

    @Test
    public void vanishAlwaysFiresStrictlyBeforeTheEnvelopeEndsAcrossAWideRangeOfInputs()
    {
        for (int envelop = 2; envelop <= 40; envelop += 3)
        {
            for (int vanish = -10; vanish <= 60; vanish += 7)
            {
                final BeamTiming timing = BeamTiming.resolve(envelop, vanish, 18, 12, 20, 8);
                assertTrue(timing.vanishAtStep() < timing.envelopTicks(),
                    "envelop=" + envelop + " vanish=" + vanish + " produced vanishAtStep="
                        + timing.vanishAtStep() + " >= envelopTicks=" + timing.envelopTicks());
            }
        }
    }

    @Test
    public void teleportAlwaysFiresStrictlyBeforeTheRiseEndsAcrossAWideRangeOfInputs()
    {
        for (int rise = 2; rise <= 40; rise += 3)
        {
            for (int teleport = -10; teleport <= 60; teleport += 7)
            {
                final BeamTiming timing = BeamTiming.resolve(12, 6, rise, teleport, 20, 8);
                assertTrue(timing.teleportAtStep() < timing.riseTicks(),
                    "rise=" + rise + " teleport=" + teleport + " produced teleportAtStep="
                        + timing.teleportAtStep() + " >= riseTicks=" + timing.riseTicks());
            }
        }
    }
}
