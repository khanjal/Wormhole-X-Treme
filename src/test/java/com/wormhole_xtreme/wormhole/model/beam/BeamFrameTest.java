package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Where every beat of a beam sequence actually lands, tick by tick.
 *
 * <p>{@link BeamFrame#at} is the decision {@code BeamAnimation.Sequence} used to make inline,
 * mixed in with the Bukkit calls that acted on it -- pulled out specifically so a phase
 * boundary being off by one is a failing assertion here rather than a visible stutter or a
 * column that starts a tick late, only noticed by actually watching a beam run. These tests
 * use one fixed, arbitrary-but-realistic timing (envelop 12, vanish 6, rise 18, teleport 12,
 * descend 20, fade 8) and pin the exact tick each transition falls on, worked out by hand from
 * the same arithmetic {@link BeamFrame} uses, so a change to that arithmetic has to own up to
 * moving one of these numbers rather than just changing behaviour quietly.
 */
class BeamFrameTest
{
    private static final BeamTiming TIMING = BeamTiming.resolve(12, 6, 18, 12, 20, 8);

    // Absolute ticks, worked out from TIMING above:
    // envelop:  [0, 12)
    // vanish:   6
    // rise:     [12, 30)   (sinceRise = tick - 12, active while sinceRise in [0, 18))
    // teleport: 24         (sinceRise == 12)
    // descend:  [24, 44)   (sinceTeleport = tick - 24, active while sinceTeleport in [0, 20))
    // arrive:   44         (sinceTeleport == 20)
    // fade:     [44, 52)   (sinceDeposit = tick - 44, active while sinceDeposit in [0, 8))
    // finished: tick >= 52

    @Test
    void tickZeroIsStartAndTheFirstEnvelopFrameAtMinimumDensity()
    {
        final BeamFrame frame = BeamFrame.at(0, TIMING);
        assertTrue(frame.isStart());
        assertTrue(frame.isEnvelopActive());
        assertEquals(1, frame.getEnvelopDensity(), "the ramp must start at MIN_DENSITY, not partway up it");
    }

    @Test
    void onlyTickZeroIsStart()
    {
        assertFalse(BeamFrame.at(1, TIMING).isStart());
        assertFalse(BeamFrame.at(51, TIMING).isStart());
    }

    @Test
    void envelopDensityReachesMaximumOnTheLastEnvelopTickNotJustBeforeIt()
    {
        // The ramp's denominator is envelopTicks - 1, deliberately, so the last rendered
        // tick actually hits MAX_DENSITY rather than falling just short of it.
        final BeamFrame lastEnvelopTick = BeamFrame.at(11, TIMING);
        assertTrue(lastEnvelopTick.isEnvelopActive());
        assertEquals(8, lastEnvelopTick.getEnvelopDensity());
    }

    @Test
    void envelopEndsExactlyAtEnvelopTicksWithNoGapOrOverlapBeforeRise()
    {
        final BeamFrame lastEnvelop = BeamFrame.at(11, TIMING);
        final BeamFrame firstRise = BeamFrame.at(12, TIMING);

        assertTrue(lastEnvelop.isEnvelopActive());
        assertFalse(lastEnvelop.isRiseActive(), "envelop and rise must never both be active on the same tick");

        assertFalse(firstRise.isEnvelopActive());
        assertTrue(firstRise.isRiseActive());
        assertEquals(0.0, firstRise.getRiseYOffset(), "rise starts at the ground, not partway up");
    }

    @Test
    void vanishFiresOnceAtTheConfiguredStepRegardlessOfEnvelopLength()
    {
        assertTrue(BeamFrame.at(6, TIMING).isVanish());
        assertFalse(BeamFrame.at(5, TIMING).isVanish());
        assertFalse(BeamFrame.at(7, TIMING).isVanish());
    }

    @Test
    void teleportFiresExactlyOnceAtTheExpectedAbsoluteTick()
    {
        // sinceRise == teleportAtStep (12), and sinceRise = tick - envelopTicks (12), so
        // this lands at tick 24 -- worked out by hand, not derived from the code under test.
        assertTrue(BeamFrame.at(24, TIMING).isTeleport());
        assertFalse(BeamFrame.at(23, TIMING).isTeleport());
        assertFalse(BeamFrame.at(25, TIMING).isTeleport());
    }

    @Test
    void riseIsStillActiveOnTheTeleportTickSoTheOriginColumnKeepsPlayingAfterTheyAreGone()
    {
        final BeamFrame teleportTick = BeamFrame.at(24, TIMING);
        assertTrue(teleportTick.isTeleport());
        assertTrue(teleportTick.isRiseActive(),
            "the origin track has to keep animating after the traveller leaves, independent of them");
    }

    @Test
    void descendStartsAtFullTravelHeightRightWhenTeleportFires()
    {
        final BeamFrame frame = BeamFrame.at(24, TIMING);
        assertTrue(frame.isDescendActive());
        assertEquals(4.0, frame.getDescendYOffset(), 1e-9,
            "the column should arrive from fully overhead, not already partway settled");
    }

    @Test
    void descendEndsExactlyAtArriveWithNoGapOrOverlapBeforeFade()
    {
        final BeamFrame lastDescend = BeamFrame.at(43, TIMING);
        final BeamFrame arrive = BeamFrame.at(44, TIMING);

        assertTrue(lastDescend.isDescendActive());
        assertFalse(lastDescend.isArrive());

        assertFalse(arrive.isDescendActive(), "descend and arrive must not both be active on the same tick");
        assertTrue(arrive.isArrive());
        assertTrue(arrive.isFadeActive(), "fade has to start the same tick arrival is announced");
    }

    @Test
    void fadeStartsAtFullColumnHeightAndMaximumDensity()
    {
        final BeamFrame frame = BeamFrame.at(44, TIMING);
        assertEquals(3.0, frame.getFadeHeight(), 1e-9);
        assertEquals(8, frame.getFadeDensity());
    }

    @Test
    void fadeShrinksTowardPlayerHeightAndMinimumDensityByItsLastTick()
    {
        final BeamFrame lastFadeTick = BeamFrame.at(51, TIMING);
        assertTrue(lastFadeTick.isFadeActive());
        assertTrue(lastFadeTick.getFadeHeight() < 3.0, "height must have visibly shrunk from the full column");
        assertTrue(lastFadeTick.getFadeDensity() < 8, "density must have visibly dimmed from maximum");
    }

    @Test
    void finishesExactlyOnceFadeIsDoneNotBefore()
    {
        assertFalse(BeamFrame.at(51, TIMING).isFinished(), "the last fade tick must still play, not be skipped");
        assertTrue(BeamFrame.at(52, TIMING).isFinished());
    }

    @Test
    void nothingIsActiveOnceFinished()
    {
        final BeamFrame frame = BeamFrame.at(52, TIMING);
        assertFalse(frame.isEnvelopActive());
        assertFalse(frame.isRiseActive());
        assertFalse(frame.isDescendActive());
        assertFalse(frame.isFadeActive());
    }
}
