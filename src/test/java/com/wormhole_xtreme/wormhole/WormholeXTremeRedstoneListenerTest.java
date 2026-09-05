package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Tests for `WormholeXTremeRedstoneListener` adjacency activation behavior.
 */
public class WormholeXTremeRedstoneListenerTest
{
    @BeforeEach
    public void beforeEach()
    {
        GateSpatialIndex.clear();
        // Ensure static plugin reference is non-null to avoid logging NPEs during indexing
        final WormholeXTreme pluginMock = mock(WormholeXTreme.class);
        try
        {
            final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
            f.setAccessible(true);
            f.set(null, pluginMock);
        }
        catch (final Throwable ignore) {}
    }

    @AfterEach
    public void afterEach()
    {
        GateSpatialIndex.clear();
    }

    @Test
    public void redstoneOnAnAlreadyOpenGateLeavesItOpen()
    {
        final World world = mock(World.class);
        final int dx = 100, dy = 64, dz = 200;

        final Stargate gate = spy(new Stargate());
        gate.setGateWorld(world);
        gate.setGateName("adjTest");

        // Dial block
        final Block dial = mock(Block.class);
        when(dial.getLocation()).thenReturn(new Location(world, dx, dy, dz));
        when(dial.getX()).thenReturn(dx);
        when(dial.getY()).thenReturn(dy);
        when(dial.getZ()).thenReturn(dz);

        // Redstone wire block adjacent to the dial
        final Block wire = mock(Block.class);
        when(wire.getLocation()).thenReturn(new Location(world, dx + 1, dy, dz));
        when(wire.getX()).thenReturn(dx + 1);
        when(wire.getY()).thenReturn(dy);
        when(wire.getZ()).thenReturn(dz);
        when(wire.getType()).thenReturn(Material.REDSTONE_WIRE);

        gate.setGateDialLeverBlock(dial);
        gate.setGateRedstonePowered(true);
        gate.setGateActive(true);
        doReturn(new Stargate()).when(gate).getGateTarget();

        doNothing().when(gate).shutdownStargate(anyBoolean());
        StargateManager.registerStargate(gate);

        new WormholeXTremeRedstoneListener().onBlockRedstoneChange(new BlockRedstoneEvent(wire, 0, 1));

        // A second cart over a detector rail used to shut the wormhole the first one
        // opened. The gate is left to close on its own timer instead.
        verify(gate, never()).shutdownStargate(anyBoolean());
        // And it is not re-dialled either: dialling restarts the shutdown timer, so a
        // repeatedly triggered gate would stay open and lock everyone else out.
        verify(gate, never()).dialStargate(any(Stargate.class), anyBoolean());

        StargateManager.removeStargate(gate);
    }

    @Test
    public void theSignCycleBlockIsIgnoredWhileTheGateIsOpen()
    {
        // The [RA] lever is the gate's own output, switched on the moment the gate opens.
        // A shape that puts it within a block of [RD] and [RS] -- easily done, since a
        // signal counts anywhere within a block of a marker and consecutive layers are
        // neighbours in the world -- lands a signal on both inputs every time the gate
        // opens. This guard is the only reason that is harmless: without it such a gate
        // would advance its own dial sign every single time it opened.
        final World world = mock(World.class);
        final int cx = 400, cy = 64, cz = 500;

        final Stargate gate = spy(new Stargate());
        gate.setGateWorld(world);
        gate.setGateName("raNextToRs");

        final Block cycle = mock(Block.class);
        when(cycle.getLocation()).thenReturn(new Location(world, cx, cy, cz));
        when(cycle.getX()).thenReturn(cx);
        when(cycle.getY()).thenReturn(cy);
        when(cycle.getZ()).thenReturn(cz);

        // The lever one block over and one block up: the [RA] position relative to [RS].
        final Block lever = mock(Block.class);
        when(lever.getLocation()).thenReturn(new Location(world, cx + 1, cy + 1, cz));
        when(lever.getX()).thenReturn(cx + 1);
        when(lever.getY()).thenReturn(cy + 1);
        when(lever.getZ()).thenReturn(cz);
        when(lever.getType()).thenReturn(Material.LEVER);

        gate.setGateRedstoneSignActivationBlock(cycle);
        gate.setGateRedstonePowered(true);
        StargateManager.registerStargate(gate);

        // Cycling the sign is a scheduled task, and the listener swallows Throwable, so
        // asserting on a real scheduler is the only way to tell "guarded" apart from
        // "threw an NPE on the way and nobody noticed".
        final org.bukkit.scheduler.BukkitScheduler scheduler =
            mock(org.bukkit.scheduler.BukkitScheduler.class);
        setScheduler(scheduler);
        try
        {
            gate.setGateActive(true);
            new WormholeXTremeRedstoneListener().onBlockRedstoneChange(new BlockRedstoneEvent(lever, 0, 15));
            verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());

            // The same signal on a closed gate does cycle it, which is what proves the
            // check above is the guard doing the work and not the signal failing to reach.
            gate.setGateActive(false);
            new WormholeXTremeRedstoneListener().onBlockRedstoneChange(new BlockRedstoneEvent(lever, 0, 15));
            verify(scheduler).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
        }
        finally
        {
            setScheduler(null);
            StargateManager.removeStargate(gate);
        }
    }

    /** The scheduler is a private static, and the sign cycle goes through it. */
    private static void setScheduler(final org.bukkit.scheduler.BukkitScheduler scheduler)
    {
        try
        {
            final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("scheduler");
            f.setAccessible(true);
            f.set(null, scheduler);
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void rdBlockRisingEdgeOnInactiveSignGateDialsSignTarget()
    {
        final World world = mock(World.class);
        final int dx = 200, dy = 64, dz = 300;

        final Stargate gate = spy(new Stargate());
        gate.setGateWorld(world);
        gate.setGateName("rdActivateTest");

        // RD wire block at the gate
        final Block rdBlock = mock(Block.class);
        when(rdBlock.getLocation()).thenReturn(new Location(world, dx, dy, dz));
        when(rdBlock.getX()).thenReturn(dx);
        when(rdBlock.getY()).thenReturn(dy);
        when(rdBlock.getZ()).thenReturn(dz);
        when(rdBlock.getType()).thenReturn(Material.REDSTONE_WIRE);

        gate.setGateRedstoneDialActivationBlock(rdBlock);
        gate.setGateRedstonePowered(true);
        gate.setGateSignPowered(true);
        gate.setGateActive(false);

        // Sign target gate
        final Stargate target = new Stargate();
        target.setGateName("targetGate");
        doReturn(target).when(gate).getGateDialSignTarget();

        // Stub dialStargate so it doesn't actually run dial logic
        doReturn(true).when(gate).dialStargate(eq(target), eq(false));

        StargateManager.registerStargate(gate);

        final BlockRedstoneEvent ev = new BlockRedstoneEvent(rdBlock, 0, 1);

        final WormholeXTremeRedstoneListener listener = new WormholeXTremeRedstoneListener();
        listener.onBlockRedstoneChange(ev);

        verify(gate, atLeastOnce()).dialStargate(eq(target), eq(false));

        StargateManager.removeStargate(gate);
    }

    /**
     * Builds a gate whose RD block sits at (200,64,300) and fires a rising-edge redstone
     * event on a block of {@code sourceType} offset from it, returning the gate spy so
     * the caller can assert whether it dialled.
     */
    private Stargate fireRedstoneNextToRdBlock(final Material sourceType, final int offX, final int offY, final int offZ)
    {
        final World world = mock(World.class);
        final int dx = 200, dy = 64, dz = 300;

        final Stargate gate = spy(new Stargate());
        gate.setGateWorld(world);
        gate.setGateName("railTest");

        final Block rdBlock = mock(Block.class);
        when(rdBlock.getLocation()).thenReturn(new Location(world, dx, dy, dz));
        when(rdBlock.getX()).thenReturn(dx);
        when(rdBlock.getY()).thenReturn(dy);
        when(rdBlock.getZ()).thenReturn(dz);
        when(rdBlock.getType()).thenReturn(Material.AIR);

        // The block that actually changes power — never the RD block itself.
        final Block source = mock(Block.class);
        when(source.getLocation()).thenReturn(new Location(world, dx + offX, dy + offY, dz + offZ));
        when(source.getX()).thenReturn(dx + offX);
        when(source.getY()).thenReturn(dy + offY);
        when(source.getZ()).thenReturn(dz + offZ);
        when(source.getType()).thenReturn(sourceType);

        gate.setGateRedstoneDialActivationBlock(rdBlock);
        gate.setGateRedstonePowered(true);
        gate.setGateSignPowered(true);
        gate.setGateActive(false);

        final Stargate target = new Stargate();
        target.setGateName("targetGate");
        doReturn(target).when(gate).getGateDialSignTarget();
        doReturn(true).when(gate).dialStargate(eq(target), eq(false));

        StargateManager.registerStargate(gate);
        new WormholeXTremeRedstoneListener().onBlockRedstoneChange(new BlockRedstoneEvent(source, 0, 15));
        StargateManager.removeStargate(gate);
        return gate;
    }

    @Test
    public void detectorRailBesideRdBlockDialsSignTarget()
    {
        // The minecart case: a cart rolls over a detector rail next to the gate. The
        // event fires on the rail, never on the RD block, so an exact-match check
        // could never see it.
        final Stargate gate = fireRedstoneNextToRdBlock(Material.DETECTOR_RAIL, 1, 0, 0);
        verify(gate, atLeastOnce()).dialStargate(any(Stargate.class), eq(false));
    }

    @Test
    public void redstoneWireRunningIntoRdBlockDialsSignTarget()
    {
        // Dust run up to the activation block powers it, but the current change is
        // reported on the dust.
        final Stargate gate = fireRedstoneNextToRdBlock(Material.REDSTONE_WIRE, 0, 0, 1);
        verify(gate, atLeastOnce()).dialStargate(any(Stargate.class), eq(false));
    }

    @Test
    public void nonRedstoneBlockBesideRdBlockDoesNotDial()
    {
        // Guards the adjacency widening: only redstone components may trigger, or any
        // block update near a gate would dial it.
        final Stargate gate = fireRedstoneNextToRdBlock(Material.STONE, 1, 0, 0);
        verify(gate, never()).dialStargate(any(Stargate.class), anyBoolean());
    }

    @Test
    public void redstoneSourceTwoBlocksFromRdBlockDoesNotDial()
    {
        // Adjacency means adjacency — a rail one block further out is someone else's
        // circuit, not a wire into this gate.
        final Stargate gate = fireRedstoneNextToRdBlock(Material.DETECTOR_RAIL, 2, 0, 0);
        verify(gate, never()).dialStargate(any(Stargate.class), anyBoolean());
    }
}
