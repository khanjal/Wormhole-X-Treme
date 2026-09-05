package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;

import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

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
 *
 * <p>A third, related real bug turned up while confirming the second one's fix: reported as
 * "the event horizon is still showing an additional layer... in the gate," on every
 * completed opening, not just an interrupted one. Retraction's own terminal check read
 * {@code step3D == 1} as the last step, but that check runs after the tick's own undraw of
 * {@code getGateWooshBlocks().get(step3D)} -- so ending at step 1 meant the undraw for step
 * 1 had already happened, and the method settled immediately without ever taking a further
 * tick to undraw step 0, the shallowest wave, right behind the portal. It stayed lit as
 * woosh material for as long as the gate stayed open. The check is against {@code == 0} now.
 */
public class StargateAnimatorTest
{
    /**
     * Points {@link WormholeXTreme#getScheduler()} at a Mockito no-op for the one test that
     * needs the "continue, do not settle yet" branch to actually run to completion instead
     * of settling early -- that branch's only externally-visible action past the counters
     * themselves is scheduling the next tick, so proving it was taken at all means letting
     * that scheduling call happen against something that will not throw for lacking a live
     * server. {@code scheduler} is a private static field on {@link WormholeXTreme}, the
     * same shape {@code BigGateShapeTest} already reaches into for {@code thisPlugin}.
     *
     * @return the field, left accessible, for the test to reset in its own {@code finally}
     */
    private static Field schedulerField() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("scheduler");
        f.setAccessible(true);
        return f;
    }

    @AfterEach
    public void restoreRealScheduler() throws Exception
    {
        // WormholeXTreme.scheduler is a shared static -- every other test in this JVM run
        // that touches it expects the plugin's normal null-until-onEnable default, not
        // whatever mock the one test above it happened to leave behind.
        schedulerField().set(null, null);
    }

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
        //
        // Step 0, not 1: retraction undraws getGateWooshBlocks().get(step3D) *before*
        // deciding whether this was the last step, so the true last step is whichever index
        // that undraw call was just made for -- index 0, the shallowest wave, right behind
        // the portal. Ending on step 1 (this test's original setup, before that was found to
        // be its own bug -- see wave1NeverUndrawnBecauseRetractionEndedOneStepTooEarly below)
        // skipped undrawing index 0 on every single completed opening, not just an
        // interrupted one.
        final Stargate gate = new Stargate();
        // This is the tail of a gate's own opening sequence settling into place, not an
        // externally-triggered close -- the gate has been active since before the woosh
        // started and stays that way until something later shuts it down. animateOpening
        // returns immediately on an inactive gate (guards against a stale scheduled
        // continuation replaying itself after a real close), so this must be set for the
        // method to do anything at all.
        gate.setGateActive(true);
        // One entry, at index 0 -- null so the block-undrawing branch, which needs a live
        // World, is never reached; this test only cares about the counter.
        gate.getGateWooshBlocks().add(null);
        gate.setGateAnimationRemoving(true);
        gate.setGateAnimationStep3D(0);

        StargateAnimator.animateOpening(gate);

        assertFalse(gate.isGateAnimationRemoving(), "closing must still clear the removing flag");
        assertEquals(0, gate.getGateAnimationStep3D(),
            "step3D must return to 0 once closing finishes, or the next opening's kawoosh "
                + "check fails and its first woosh-depth index is off by one");
    }

    @Test
    public void stepOneIsNotTheLastRetractionStepAndMustStillTakeOneMoreTickDownToZero() throws Exception
    {
        // Real bug: retraction's own terminal check read "gate.getGateAnimationStep3D() ==
        // 1" as "this was the last step" -- but that check runs *after* this tick's own
        // undraw of getGateWooshBlocks().get(step3D), so ending at step 1 means the undraw
        // for step 1 already ran, and the method settled immediately afterward without ever
        // taking a further tick for step 0. Every completed opening left wave #1 (index 0,
        // the shallowest layer, directly behind the portal) drawn as woosh material forever,
        // reported as "the event horizon has an extra layer... in the gate" -- not something
        // the already-fixed interrupted-close cleanup could touch, since this happened on
        // every normal, uninterrupted opening too.
        //
        // Reproduces the tick right before the true last step. A correct implementation
        // must not settle here -- it still has index 0 left to undraw -- so it decrements to
        // 0 and schedules one more tick rather than clearing isGateAnimationRemoving early.
        schedulerField().set(null, mock(BukkitScheduler.class));
        final Stargate gate = new Stargate();
        gate.setGateActive(true);
        gate.getGateWooshBlocks().add(null);
        gate.getGateWooshBlocks().add(null);
        gate.setGateAnimationRemoving(true);
        gate.setGateAnimationStep3D(1);

        StargateAnimator.animateOpening(gate);

        assertTrue(gate.isGateAnimationRemoving(),
            "step 1 must not be treated as the last retraction step -- index 0 is still owed an "
                + "undraw, so retraction must still be in progress after this tick, not settled");
        assertEquals(0, gate.getGateAnimationStep3D(),
            "must have stepped down to 0, ready to undraw the shallowest wave on the next tick");
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
