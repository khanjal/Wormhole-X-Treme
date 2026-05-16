package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
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
}
