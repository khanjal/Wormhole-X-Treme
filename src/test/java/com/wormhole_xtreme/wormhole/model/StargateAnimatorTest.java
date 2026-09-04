package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.block.Block;
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
 *
 * <p>Also covers a second, related real bug, reported as "gates are leaving water one block
 * from the gate" (visible only to the traveller who just went through, and only sometimes --
 * matching an interruption that has to land inside a specific timing window rather than
 * something that happens on every trip): the woosh is only ever undrawn by its own
 * step-by-step retraction inside {@link StargateAnimator#animateOpening}, and each step
 * reschedules its own continuation with a raw {@code scheduleSyncDelayedTask} call that
 * nothing tracks a task id for. A gate that closes -- its own lever, a partner gate shutting
 * down, an idle timeout -- while the woosh is still mid-flight leaves whatever it had drawn
 * so far (the woosh material, one block out from the portal on a 2D gate's very first step)
 * showing to anyone nearby, and the already-scheduled continuation still fires afterward
 * regardless. {@link StargateAnimator#lightStargate}'s closing branch now undraws whatever is
 * left in {@code getGateAnimatedBlocks()} and resets both step counters unconditionally, the
 * same way it already unconditionally undraws every chevron light block regardless of which
 * ones were actually lit; {@link StargateAnimator#animateOpening} now also returns
 * immediately on an inactive gate, so a continuation that fires after this reset reads the
 * gate as closed rather than as "nothing has happened yet, start a fresh opening."
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
        // This is the tail of a gate's own opening sequence settling into place, not an
        // externally-triggered close -- the gate has been active since before the woosh
        // started and stays that way until something later shuts it down. animateOpening
        // returns immediately on an inactive gate (guards against a stale scheduled
        // continuation replaying itself after a real close), so this must be set for the
        // method to do anything at all.
        gate.setGateActive(true);
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

    @Test
    public void animateOpeningDoesNothingOnceTheGateHasClosed()
    {
        // Reproduces a stale scheduled continuation firing after the gate already closed.
        // Its own re-schedule keeps no task id to cancel, so it still runs even though
        // closing (see closingUndrawsWhateverTheWooshLeftShowingAndResetsBothCounters below)
        // already reset the counters to zero and marked the gate inactive. Without checking
        // isGateActive() first, step2D == 0 reads as "nothing has happened yet" and the 2D
        // path starts a brand new opening -- kawoosh included -- on a gate that closed.
        final Stargate gate = new Stargate();
        gate.setGateActive(false);
        gate.setGateCustom(true);
        gate.setGateCustomWooshDepth(3);

        StargateAnimator.animateOpening(gate);

        assertEquals(0, gate.getGateAnimationStep2D(),
            "an inactive gate must not start a fresh opening, even with a nonzero woosh depth configured");
    }

    @Test
    public void closingUndrawsWhateverTheWooshLeftShowingAndResetsBothCounters()
    {
        // Reproduces landing mid-woosh: some blocks already drawn and remembered (what a
        // real opening in progress leaves in getGateAnimatedBlocks()), both step counters
        // non-zero, partway through the 3D path's own retraction. Nothing about this state
        // is "the tail of a normal opening settling into place" -- an external close has
        // interrupted it, and closingTheWooshResetsStep3DBackToZeroForTheNextOpening above
        // already covers the normal-completion case.
        final Stargate gate = new Stargate();
        gate.getGateAnimatedBlocks().add(mock(Block.class));
        gate.setGateAnimationStep2D(2);
        gate.setGateAnimationStep3D(1);
        gate.setGateAnimationRemoving(true);

        StargateAnimator.lightStargate(gate, false);

        assertTrue(gate.getGateAnimatedBlocks().isEmpty(),
            "closing must undraw and forget whatever the woosh left showing, not just whatever "
                + "its own retraction already knew to look for");
        assertEquals(0, gate.getGateAnimationStep2D());
        assertEquals(0, gate.getGateAnimationStep3D());
        assertFalse(gate.isGateAnimationRemoving());
    }
}
