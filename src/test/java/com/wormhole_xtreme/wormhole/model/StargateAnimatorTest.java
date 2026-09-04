package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The woosh animation's own step counter, {@code gateAnimationStep3D}.
 *
 * <p>Real bug, reported as "the lever's activation sound plays, but there's no woosh sound
 * after {@code /dial}": {@code gateAnimationStep3D} defaulted to 1 instead of 0, and the
 * "closing finished" branch in {@link StargateAnimator#animateOpening} never reset it back to
 * 0 either -- it only reset {@code isGateAnimationRemoving}, leaving the step counter at 1. The
 * kawoosh sound only fires when {@code step2D == 0 && step3D == 0}
 * ({@link StargateAnimator#animateOpening}'s very first check), so with the counter stuck at 1
 * that condition was never true -- not on a gate's first-ever opening (the bad default), and
 * not on any opening after its first close either (the missing reset). The same counter also
 * indexes straight into the shape's own woosh-depth blocks
 * ({@code getGateWooshBlocks().get(step3D)}), so every opening was additionally starting one
 * depth layer short of the shape's first one -- a quieter companion bug to the missing sound,
 * not something a player would necessarily notice was wrong on its own.
 */
public class StargateAnimatorTest
{
    @Test
    public void aFreshGatesAnimationStep3DStartsAtZeroNotOne()
    {
        // The kawoosh check and the woosh-depth index both read this as "nothing has happened
        // yet" only at 0 -- the 2D step counter right beside it already defaults to 0, and this
        // one should too.
        final Stargate gate = new Stargate();

        assertEquals(0, gate.getGateAnimationStep3D(),
            "a gate that has never opened should read as step 0, matching gateAnimationStep2D's own default");
    }

    @Test
    public void closingTheWooshResetsStep3DBackToZeroForTheNextOpening()
    {
        // Reproduces the closing side of the bug directly: put the gate in the exact state
        // animateOpening is in on the tick its closing animation finishes, and confirm the
        // step counter is actually zeroed rather than left at 1.
        final Stargate gate = new Stargate();
        // Two entries so index 1 is a legal get() -- both null so the block-undrawing branch,
        // which needs a live World, is never reached; this test only cares about the counter.
        gate.getGateWooshBlocks().add(null);
        gate.getGateWooshBlocks().add(null);
        gate.setGateAnimationRemoving(true);
        gate.setGateAnimationStep3D(1);

        StargateAnimator.animateOpening(gate);

        assertFalse(gate.isGateAnimationRemoving(), "closing must still clear the removing flag");
        assertEquals(0, gate.getGateAnimationStep3D(),
            "step3D must return to 0 once closing finishes, or the next opening's kawoosh "
                + "check fails and its first woosh-depth index is off by one");
    }
}
