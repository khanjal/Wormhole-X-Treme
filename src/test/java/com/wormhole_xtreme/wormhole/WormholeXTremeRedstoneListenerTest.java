package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
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
        // Gate names are reused across these cases and they run milliseconds apart, well
        // inside the repeat-trigger window, so one case would otherwise silence the next.
        WormholeXTremeRedstoneListener.clearTriggerHistory();
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

        // A real scheduler would run the rescheduled shutdown, and the listener swallows
        // Throwable, so a mock is the only way to tell "shutdown pushed back" apart from
        // "shutdown happened" -- which is the whole distinction under test.
        final org.bukkit.scheduler.BukkitScheduler scheduler =
            mock(org.bukkit.scheduler.BukkitScheduler.class);
        setScheduler(scheduler);
        try
        {
            new WormholeXTremeRedstoneListener().onBlockRedstoneChange(new BlockRedstoneEvent(wire, 0, 1));

            // A second cart over a detector rail used to shut the wormhole the first one
            // opened. It must still never do that.
            verify(gate, never()).shutdownStargate(anyBoolean());
            // And it is not re-dialled either. Re-dialling rebuilds the connection and, before
            // max_open_seconds existed, restarted the timer from scratch -- which is what would
            // have let a repeatedly triggered gate stay open and lock everyone else out.
            verify(gate, never()).dialStargate(any(Stargate.class), anyBoolean());
            // What it does instead: pushes the shutdown back. Bounded by max_open_seconds,
            // which is measured from when the wormhole first opened and is not touched here.
            verify(scheduler).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
        }
        finally
        {
            setScheduler(null);
            StargateManager.removeStargate(gate);
        }
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

    /**
     * Fires a redstone change at an offset from a gate's DHD button and returns the gate.
     *
     * <p>No [RD] block is registered, so anything that dials here did so through the DHD
     * itself rather than through the marker cell.
     */
    private Stargate fireRedstoneNextToDhd(final Material sourceType,
                                           final int offX, final int offY, final int offZ,
                                           final boolean sourceIsOwnOutput)
    {
        final World world = mock(World.class);
        final int dx = 300, dy = 64, dz = 400;

        final Stargate gate = spy(new Stargate());
        gate.setGateWorld(world);
        gate.setGateName("dhdTest");

        final Block button = mock(Block.class);
        when(button.getLocation()).thenReturn(new Location(world, dx, dy, dz));
        when(button.getX()).thenReturn(dx);
        when(button.getY()).thenReturn(dy);
        when(button.getZ()).thenReturn(dz);
        when(button.getType()).thenReturn(Material.STONE_BUTTON);

        final Block source = mock(Block.class);
        when(source.getLocation()).thenReturn(new Location(world, dx + offX, dy + offY, dz + offZ));
        when(source.getX()).thenReturn(dx + offX);
        when(source.getY()).thenReturn(dy + offY);
        when(source.getZ()).thenReturn(dz + offZ);
        when(source.getType()).thenReturn(sourceType);

        gate.setGateDialLeverBlock(button);
        if (sourceIsOwnOutput)
        {
            gate.setGateRedstoneGateActivatedBlock(source);
        }
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

    /**
     * A repeater against the DHD dials, the same as dust against it does.
     *
     * <p>The [RD] cell has always accepted any redstone component beside it; the DHD
     * accepted dust and nothing else. So a repeater feeding the button worked one block
     * higher and did nothing at all here, with no way to tell the two cases apart from
     * in game -- the dust that "worked" and the repeater that did not are the same circuit
     * to whoever built it.
     */
    @Test
    public void aRepeaterAgainstTheDhdDialsTheSameWayDustDoes()
    {
        final Stargate gate = fireRedstoneNextToDhd(Material.REPEATER, 1, 0, 0, false);
        verify(gate, atLeastOnce()).dialStargate(any(Stargate.class), eq(false));
    }

    /**
     * A signal underneath the DHD dials.
     *
     * <p>This is the natural place to bring redstone on a gate sunk a block into the
     * ground for a flush entrance, which is how they are commonly built: the marker cell
     * ends up above head height, while the block below the button is at hand level and
     * can be dug out and wired without disturbing the gate.
     */
    @Test
    public void aSignalUnderTheDhdDials()
    {
        final Stargate gate = fireRedstoneNextToDhd(Material.REDSTONE_WIRE, 0, -1, 0, false);
        verify(gate, atLeastOnce()).dialStargate(any(Stargate.class), eq(false));
    }

    /**
     * An ordinary block against the DHD does not dial.
     *
     * <p>Guards the widening above. Redstone events fire for plenty of reasons near a
     * gate, and if any of them counted, a gate would dial itself on unrelated block
     * updates nearby.
     */
    @Test
    public void anOrdinaryBlockAgainstTheDhdDoesNotDial()
    {
        final Stargate gate = fireRedstoneNextToDhd(Material.STONE, 1, 0, 0, false);
        verify(gate, never()).dialStargate(any(Stargate.class), anyBoolean());
    }

    /**
     * The gate's own [RA] output never dials the gate, even sitting right against the DHD.
     *
     * <p>[RA] is a lever the plugin switches on itself the moment the gate opens, and on
     * some shapes it is close enough to the DHD to be adjacent to it. A lever is a redstone
     * source, so once any source beside the DHD counts, the gate's own output becomes an
     * input to its own dial trigger. The shapes keep [RA] clear of [RD] by geometry; this
     * keeps it clear of the DHD by rule, which geometry alone cannot do on a small shape.
     */
    @Test
    public void theGatesOwnActivatedLeverDoesNotDialItEvenWhenItTouchesTheDhd()
    {
        final Stargate gate = fireRedstoneNextToDhd(Material.LEVER, 1, 0, 0, true);
        verify(gate, never()).dialStargate(any(Stargate.class), anyBoolean());
    }

    /**
     * A second dust block in the same run does not dial the gate a second time.
     *
     * <p>Reported from in game: running redstone past the button and up to the marker
     * activated the gate twice in rapid succession. One circuit is not one event -- every dust
     * block along a run fires its own BlockRedstoneEvent as the signal propagates, and a gate
     * answers to any component touching its DHD as well as to its [RD] cell, so a single run
     * legitimately powers several blocks it listens to.
     *
     * <p>The synchronous "already open" guard does not catch this on its own, because the
     * events arrive a tick or so apart and each one is a fresh rising edge.
     */
    @Test
    public void asecondDustBlockInTheSameRunDoesNotDialTwice()
    {
        final World world = mock(World.class);
        final int dx = 500, dy = 64, dz = 600;

        final Stargate gate = spy(new Stargate());
        gate.setGateWorld(world);
        gate.setGateName("debounceTest");

        final Block button = mock(Block.class);
        when(button.getLocation()).thenReturn(new Location(world, dx, dy, dz));
        when(button.getX()).thenReturn(dx);
        when(button.getY()).thenReturn(dy);
        when(button.getZ()).thenReturn(dz);
        when(button.getType()).thenReturn(Material.STONE_BUTTON);

        // Two different dust blocks, both within reach of the DHD, as a real run would be.
        final Block dustLow = mock(Block.class);
        when(dustLow.getLocation()).thenReturn(new Location(world, dx + 1, dy, dz));
        when(dustLow.getX()).thenReturn(dx + 1);
        when(dustLow.getY()).thenReturn(dy);
        when(dustLow.getZ()).thenReturn(dz);
        when(dustLow.getType()).thenReturn(Material.REDSTONE_WIRE);

        final Block dustHigh = mock(Block.class);
        when(dustHigh.getLocation()).thenReturn(new Location(world, dx + 1, dy + 1, dz));
        when(dustHigh.getX()).thenReturn(dx + 1);
        when(dustHigh.getY()).thenReturn(dy + 1);
        when(dustHigh.getZ()).thenReturn(dz);
        when(dustHigh.getType()).thenReturn(Material.REDSTONE_WIRE);

        gate.setGateDialLeverBlock(button);
        gate.setGateRedstonePowered(true);
        gate.setGateSignPowered(true);
        gate.setGateActive(false);

        final Stargate target = new Stargate();
        target.setGateName("targetGate");
        doReturn(target).when(gate).getGateDialSignTarget();
        doReturn(true).when(gate).dialStargate(eq(target), eq(false));

        StargateManager.registerStargate(gate);
        final WormholeXTremeRedstoneListener listener = new WormholeXTremeRedstoneListener();
        listener.onBlockRedstoneChange(new BlockRedstoneEvent(dustLow, 0, 15));
        listener.onBlockRedstoneChange(new BlockRedstoneEvent(dustHigh, 0, 15));
        StargateManager.removeStargate(gate);

        verify(gate, times(1)).dialStargate(any(Stargate.class), eq(false));
    }

    @Test
    public void aFirstTriggerIsNeverARepeat()
    {
        assertFalse(WormholeXTremeRedstoneListener.isRepeatTrigger(null, 1000L, 250L));
    }

    @Test
    public void aTriggerInsideTheWindowIsARepeat()
    {
        assertTrue(WormholeXTremeRedstoneListener.isRepeatTrigger(Long.valueOf(1000L), 1100L, 250L));
    }

    @Test
    public void aTriggerAfterTheWindowIsANewPress()
    {
        assertFalse(WormholeXTremeRedstoneListener.isRepeatTrigger(Long.valueOf(1000L), 1300L, 250L),
            "a deliberate second pulse must still work, or redstone dialling is one-shot");
    }

    /**
     * A clock that jumps backwards does not deafen a gate.
     *
     * <p>Wall-clock time is not monotonic -- an NTP correction can move it back. Treating a
     * negative gap as "recent" would leave the gate ignoring redstone until real time caught
     * up again, which for a large correction could be a very long time.
     */
    @Test
    public void aClockJumpingBackwardsDoesNotSilenceTheGate()
    {
        assertFalse(WormholeXTremeRedstoneListener.isRepeatTrigger(Long.valueOf(5000L), 1000L, 250L));
    }
}
