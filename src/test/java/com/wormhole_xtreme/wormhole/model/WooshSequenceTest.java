package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The order a kawoosh plays in, which a real gate and a build preview both follow.
 *
 * <p>Out from the shallowest step, back from the deepest, and settled only after the shallowest is
 * taken back: settling one stage early left that step showing as woosh material for as long as a
 * gate stayed open.
 */
class WooshSequenceTest
{
    @Test
    void threeStepsGoOutInOrderComeBackDeepestFirstAndThenSettle()
    {
        final List<WooshSequence.Step> stages = new ArrayList<>();
        for (int stage = 0; stage <= 6; stage++)
        {
            stages.add(WooshSequence.at(stage, 3));
        }

        assertEquals(List.of(
            new WooshSequence.Step(WooshSequence.Move.OUT, 0),
            new WooshSequence.Step(WooshSequence.Move.OUT, 1),
            new WooshSequence.Step(WooshSequence.Move.OUT, 2),
            new WooshSequence.Step(WooshSequence.Move.BACK, 2),
            new WooshSequence.Step(WooshSequence.Move.BACK, 1),
            new WooshSequence.Step(WooshSequence.Move.BACK, 0),
            new WooshSequence.Step(WooshSequence.Move.SETTLE, 0)), stages);
    }

    @Test
    void aGateWithNoWooshSettlesAtOnce()
    {
        assertEquals(WooshSequence.Move.SETTLE, WooshSequence.at(0, 0).move());
    }

    /** The step and direction a gate keeps map to the same stage, both ways round. */
    @Test
    void aGatesStepAndDirectionAreItsStage()
    {
        for (int stage = 0; stage < 6; stage++)
        {
            final WooshSequence.Step step = WooshSequence.at(stage, 3);
            assertEquals(stage, WooshSequence.stageOf(step.index(), step.move() == WooshSequence.Move.BACK, 3));
        }
    }

    /** A counter left past the end by a shape that lost woosh steps settles rather than climbing forever. */
    @Test
    void aStageBeyondTheEndSettles()
    {
        assertEquals(WooshSequence.Move.SETTLE, WooshSequence.at(WooshSequence.stageOf(5, false, 2), 2).move());
    }

    /** Writes down what the sequence asked of it, and has an iris that can be shut at any stage. */
    private static final class Recorder implements WooshSequence.Canvas
    {
        final java.util.List<String> calls = new java.util.ArrayList<>();
        boolean shut;

        @Override public boolean irisShut() { return shut; }
        @Override public void kawoosh() { calls.add("kawoosh"); }
        @Override public void draw(final int index) { calls.add("out " + index); }
        @Override public void undraw(final int index) { calls.add("back " + index); }
        @Override public void undrawAll() { calls.add("back all"); }
        @Override public void settle() { calls.add("settle"); }
        @Override public void settleBehindIris() { calls.add("behind iris"); }
    }

    /** Plays a woosh of this many steps to the end, shutting or opening the iris before a stage. */
    private static java.util.List<String> playAll(final int steps, final Recorder canvas,
        final int toggleBefore)
    {
        int stage = 0;
        while (stage >= 0)
        {
            if (stage == toggleBefore)
            {
                canvas.shut = !canvas.shut;
            }
            stage = WooshSequence.play(stage, steps, canvas);
        }
        return canvas.calls;
    }

    /** With the iris open throughout, the kawoosh plays once, goes out, comes back, and settles. */
    @Test
    void anOpenIrisPlaysTheWholeWoosh()
    {
        assertEquals(java.util.List.of("kawoosh", "out 0", "out 1", "back 1", "back 0", "settle"),
            playAll(2, new Recorder(), -1));
    }

    /** Shut before the woosh: heard, never seen, and over at once. */
    @Test
    void aShutIrisHidesTheWholeWooshButNotItsSound()
    {
        final Recorder canvas = new Recorder();
        canvas.shut = true;
        assertEquals(java.util.List.of("kawoosh", "back all", "behind iris"), playAll(2, canvas, -1));
    }

    /**
     * Shut partway through: what is out is taken back there and then, and the kawoosh, which has
     * already played, is not played again. The iris is asked at the stage, not when dialling began.
     */
    @Test
    void anIrisShutPartwayStopsTheWooshAndTakesBackWhatIsOut()
    {
        assertEquals(java.util.List.of("kawoosh", "out 0", "back all", "behind iris"), playAll(2, new Recorder(), 1));
    }

    /** Opened before the woosh: the woosh plays as though it had never been shut. */
    @Test
    void anIrisOpenedBeforeTheWooshLetsItThrough()
    {
        final Recorder canvas = new Recorder();
        canvas.shut = true;
        assertEquals(java.util.List.of("kawoosh", "out 0", "out 1", "back 1", "back 0", "settle"),
            playAll(2, canvas, 0));
    }

    /** A gate with no woosh makes no kawoosh either, iris or not. */
    @Test
    void noWooshNoKawoosh()
    {
        assertEquals(java.util.List.of("settle"), playAll(0, new Recorder(), -1));
        final Recorder shut = new Recorder();
        shut.shut = true;
        assertEquals(java.util.List.of("back all", "behind iris"), playAll(0, shut, -1));
    }
}
