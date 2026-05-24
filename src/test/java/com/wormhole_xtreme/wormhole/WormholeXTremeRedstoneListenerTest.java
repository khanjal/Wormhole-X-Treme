package com.wormhole_xtreme.wormhole;

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
    public void redstoneWireAdjacentToDhdTriggersShutdown()
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

        // Prevent actual shutdown side-effects and verify invocation
        doNothing().when(gate).shutdownStargate(anyBoolean());

        StargateManager.registerStargate(gate);

        final BlockRedstoneEvent ev = new BlockRedstoneEvent(wire, 0, 1);

        final WormholeXTremeRedstoneListener listener = new WormholeXTremeRedstoneListener();
        listener.onBlockRedstoneChange(ev);

        verify(gate, atLeastOnce()).shutdownStargate(eq(true));

        StargateManager.removeStargate(gate);
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
}
