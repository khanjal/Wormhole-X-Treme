package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Unit tests for `WormholeXTremeBlockListener` protection logic.
 */
public class WormholeXTremeBlockListenerTest
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
    public void blockThatIsPartOfGateIsCancelled()
    {
        final World world = mock(World.class);
        final int x = 10, y = 64, z = 20;

        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateName("gateA");
        gate.getGateStructureBlocks().add(new Location(world, x, y, z));
        StargateManager.registerStargate(gate);

        final Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        when(block.getType()).thenReturn(org.bukkit.Material.AIR);

        final Player player = mock(Player.class);
        final BlockBreakEvent ev = new BlockBreakEvent(block, player);

        final WormholeXTremeBlockListener listener = new WormholeXTremeBlockListener();
        listener.onBlockBreak(ev);

        assertTrue(ev.isCancelled(), "Breaking a block that is part of a registered gate should be cancelled");

        StargateManager.removeStargate(gate);
    }

    @Test
    public void blockAdjacentToGateIsNotCancelled()
    {
        final World world = mock(World.class);
        final int x = 10, y = 64, z = 20;

        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateName("gateB");
        gate.getGateStructureBlocks().add(new Location(world, x, y, z));
        StargateManager.registerStargate(gate);

        final Block adj = mock(Block.class);
        when(adj.getLocation()).thenReturn(new Location(world, x + 1, y, z));
        when(adj.getType()).thenReturn(org.bukkit.Material.AIR);

        final Player player = mock(Player.class);
        final BlockBreakEvent ev = new BlockBreakEvent(adj, player);

        final WormholeXTremeBlockListener listener = new WormholeXTremeBlockListener();
        listener.onBlockBreak(ev);

        assertFalse(ev.isCancelled(), "Breaking a block adjacent to a gate (but not part of it) should NOT be cancelled");

        StargateManager.removeStargate(gate);
    }

    @Test
    public void allowBreakingIrisPlaceholderUnderDial()
    {
        final World world = mock(World.class);
        final int dx = 10, dy = 66, dz = 20;

        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateName("gateIris");

        // Dial block (wall-mounted button)
        final Block dial = mock(Block.class);
        when(dial.getLocation()).thenReturn(new Location(world, dx, dy, dz));
        when(dial.getX()).thenReturn(dx);
        when(dial.getY()).thenReturn(dy);
        when(dial.getZ()).thenReturn(dz);

        // Backing, dhd base, and iris-candidate blocks
        final Block backing = mock(Block.class);
        final Block dhdBase = mock(Block.class);
        final Block irisCandidate = mock(Block.class);

        when(backing.getLocation()).thenReturn(new Location(world, dx - 1, dy, dz));
        when(backing.getX()).thenReturn(dx - 1);
        when(backing.getY()).thenReturn(dy);
        when(backing.getZ()).thenReturn(dz);

        when(dhdBase.getLocation()).thenReturn(new Location(world, dx - 1, dy - 1, dz));
        when(dhdBase.getX()).thenReturn(dx - 1);
        when(dhdBase.getY()).thenReturn(dy - 1);
        when(dhdBase.getZ()).thenReturn(dz);

        when(irisCandidate.getLocation()).thenReturn(new Location(world, dx - 1, dy - 1, dz + 1));
        when(irisCandidate.getX()).thenReturn(dx - 1);
        when(irisCandidate.getY()).thenReturn(dy - 1);
        when(irisCandidate.getZ()).thenReturn(dz + 1);
        when(irisCandidate.getType()).thenReturn(org.bukkit.Material.DIRT);

        // Stub relative navigation off the dial
        when(dial.getRelative(WorldUtils.getInverseDirection(BlockFace.NORTH))).thenReturn(backing);
        when(backing.getRelative(BlockFace.DOWN)).thenReturn(dhdBase);
        when(dhdBase.getRelative(BlockFace.NORTH)).thenReturn(irisCandidate);

        gate.setGateDialLeverBlock(dial);
        gate.setGateFacing(BlockFace.NORTH);

        // Register gate and explicitly index the iris-candidate location so the listener
        // treats it as part of the gate (mirrors the problematic protected dirt case).
        StargateManager.registerStargate(gate);
        StargateManager.addBlockIndex(irisCandidate, gate);

        final Player player = mock(Player.class);
        final BlockBreakEvent ev = new BlockBreakEvent(irisCandidate, player);

        final WormholeXTremeBlockListener listener = new WormholeXTremeBlockListener();
        listener.onBlockBreak(ev);

        assertFalse(ev.isCancelled(), "Breaking the iris-placeholder block under the DHD should be allowed when no iris lever exists");

        StargateManager.removeStargate(gate);
    }
}
