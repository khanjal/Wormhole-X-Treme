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
}
