package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.GateRedstoneWrite;

/**
 * A gate's own lever writes announce themselves, so the redstone listener can ignore them.
 *
 * <p>This is the half of the double-dial fix that lives in the model. Opening a gate switches
 * the gate's own dial lever, and Bukkit dispatches {@code BlockRedstoneEvent} for that write --
 * and for everything it powers -- synchronously, on this thread, before {@code setBlockData}
 * returns. So the guard has to be open *around* the write, not set afterwards; a version that
 * marked the window after the call would compile, pass any test that only looked at the end
 * state, and put the second dial straight back.
 *
 * <p>Both tests therefore assert on what was true at the moment of the write, not after it.
 */
public class GateLeverWriteIsGuardedTest
{
    @BeforeEach
    public void setUp()
    {
        final WormholeXTreme pluginMock = mock(WormholeXTreme.class);
        try
        {
            final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
            f.setAccessible(true);
            f.set(null, pluginMock);
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
        assertFalse(GateRedstoneWrite.inProgress(), "a previous test left a window open");
    }

    /**
     * Builds a lever block that records whether a redstone-write window was open when the
     * plugin wrote to it -- which is the instant Bukkit would dispatch the event.
     */
    private static Block recordingLever(final AtomicBoolean guardedAtWriteTime)
    {
        final Block lever = mock(Block.class);
        final Powerable data = mock(Powerable.class);
        when(lever.getType()).thenReturn(Material.LEVER);
        when(lever.getBlockData()).thenReturn(data);
        // toggleDialLeverState keeps the lever's chunk loaded while the gate is open, so the
        // block has to be able to answer where it lives before it can be written to at all.
        final org.bukkit.World world = mock(org.bukkit.World.class);
        final org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        when(lever.getWorld()).thenReturn(world);
        when(lever.getChunk()).thenReturn(chunk);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        doAnswer(invocation ->
        {
            guardedAtWriteTime.set(GateRedstoneWrite.inProgress());
            return null;
        }).when(lever).setBlockData(any());
        return lever;
    }

    @Test
    public void switchingTheDialLeverHappensInsideARedstoneWriteWindow()
    {
        final AtomicBoolean guarded = new AtomicBoolean(false);
        final Stargate gate = new Stargate();
        gate.setGateName("leverGuardTest");
        gate.setGateDialLeverBlock(recordingLever(guarded));
        gate.setGateActive(true);

        gate.toggleDialLeverState(false);

        assertTrue(guarded.get(), "the listener must be deaf while the gate switches its own lever");
        assertFalse(GateRedstoneWrite.inProgress(), "and hearing again once the write is done");
    }

    @Test
    public void switchingTheGateActivatedOutputHappensInsideARedstoneWriteWindow()
    {
        final AtomicBoolean guarded = new AtomicBoolean(false);
        final Stargate gate = new Stargate();
        gate.setGateName("outputGuardTest");
        gate.setGateRedstonePowered(true);
        gate.setGateRedstoneGateActivatedBlock(recordingLever(guarded));
        gate.setGateActive(true);

        gate.toggleRedstoneGateActivatedPower();

        assertTrue(guarded.get(), "the [RA] lever powers conductors that can touch the DHD");
        assertFalse(GateRedstoneWrite.inProgress());
    }
}
