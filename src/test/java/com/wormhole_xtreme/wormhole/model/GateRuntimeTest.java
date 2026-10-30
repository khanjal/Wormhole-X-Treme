package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/**
 * What a gate is doing right now that is not saved: each value reads back as set, and the two collections
 * are the gate's own, not a copy (#430).
 *
 * <p>Written for the move out of {@link Stargate}: each test fails when the one assignment or the one
 * collection it is named for is taken out of {@link GateRuntime}. Distinct values per field, so that two
 * swapped fields cannot read back right.
 */
class GateRuntimeTest
{
    @Test
    void aNewGatesRuntimeIsIdle()
    {
        final GateRuntime runtime = new GateRuntime();

        assertEquals(0, runtime.getGateActivateTaskId(), "no activation scheduled");
        assertEquals(0, runtime.getGateShutdownTaskId(), "no shutdown scheduled");
        assertEquals(0, runtime.getGateAfterShutdownTaskId(), "nothing scheduled after shutdown");
        assertFalse(runtime.isGateAnimationRemoving(), "not taking the animation down");
        assertEquals(0, runtime.getGateLightingCurrentIteration(), "no chevron lit");
        assertEquals(0, runtime.getGateChevronsLocked(), "none locked");
        assertTrue(runtime.getGateAnimatedBlocks().isEmpty(), "no animated blocks");
    }

    @Test
    void eachValueReadsBackAsSetAndTheOthersAreUntouched()
    {
        final GateRuntime runtime = new GateRuntime();

        runtime.setGateActivateTaskId(11);
        runtime.setGateShutdownTaskId(22);
        runtime.setGateAfterShutdownTaskId(33);
        runtime.setGateAnimationRemoving(true);
        runtime.setGateLightingCurrentIteration(44);
        runtime.setGateChevronsLocked(5);

        assertEquals(11, runtime.getGateActivateTaskId(), "activate");
        assertEquals(22, runtime.getGateShutdownTaskId(), "shutdown");
        assertEquals(33, runtime.getGateAfterShutdownTaskId(), "after shutdown");
        assertTrue(runtime.isGateAnimationRemoving(), "removing");
        assertEquals(44, runtime.getGateLightingCurrentIteration(), "lighting");
        assertEquals(5, runtime.getGateChevronsLocked(), "chevrons");

        runtime.setGateAnimationRemoving(false);
        assertFalse(runtime.isGateAnimationRemoving(), "and back");
    }

    @Test
    void theAnimatedBlocksAreTheOnesTheGateKeepsNotCopies()
    {
        final GateRuntime runtime = new GateRuntime();
        final Block block = mock(Block.class);

        runtime.getGateAnimatedBlocks().add(block);

        assertEquals(1, runtime.getGateAnimatedBlocks().size(), "what was added is kept");
        assertSame(block, runtime.getGateAnimatedBlocks().get(0), "the block itself");
        assertNotSame(runtime.getGateAnimatedBlocks(), new GateRuntime().getGateAnimatedBlocks(), "and each runtime has its own");
    }

    @Test
    void aGateHandsOutTheSameRuntimeEveryTimeAndEachGateHasItsOwn()
    {
        final Stargate one = new Stargate();
        final Stargate other = new Stargate();

        assertSame(one.runtime(), one.runtime(), "the same one each time");
        assertNotSame(one.runtime(), other.runtime(), "and not shared with another gate");
    }
}
