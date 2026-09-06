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
class WormholeXTremeBlockListenerTest
{
    @BeforeEach
    void beforeEach()
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
        catch (final Throwable ignore) { /* the stub server is only needed by some paths */ }
    }

    @AfterEach
    void afterEach()
    {
        GateSpatialIndex.clear();
    }

    @Test
    void blockThatIsPartOfGateIsCancelled()
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
    void blockAdjacentToGateIsNotCancelled()
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
    void allowBreakingIrisPlaceholderUnderDial()
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

    /**
     * An iris lever that is actually there stays protected.
     *
     * <p>The placeholder cases above are allowed because no lever was ever placed. Once one
     * is, breaking it would leave the gate holding a lever block that is not there, and the
     * player could take the iris off a gate they cannot otherwise touch.
     */
    @Test
    void breakingARealIrisLeverIsStillRefused()
    {
        final World world = mock(World.class);
        final int dx = 10, dy = 66, dz = 20;

        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateName("gateWithIris");

        final Block dial = mock(Block.class);
        when(dial.getLocation()).thenReturn(new Location(world, dx, dy, dz));
        when(dial.getX()).thenReturn(dx);
        when(dial.getY()).thenReturn(dy);
        when(dial.getZ()).thenReturn(dz);

        // The block under the dial is the iris position, and this time it holds a lever.
        final Block belowDial = mock(Block.class);
        when(belowDial.getLocation()).thenReturn(new Location(world, dx, dy - 1, dz));
        when(belowDial.getX()).thenReturn(dx);
        when(belowDial.getY()).thenReturn(dy - 1);
        when(belowDial.getZ()).thenReturn(dz);
        when(belowDial.getType()).thenReturn(org.bukkit.Material.LEVER);
        when(dial.getRelative(BlockFace.DOWN)).thenReturn(belowDial);

        gate.setGateDialLeverBlock(dial);
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateIrisLeverBlock(belowDial);

        StargateManager.registerStargate(gate);
        StargateManager.addBlockIndex(belowDial, gate);
        try
        {
            final Player player = mock(Player.class);
            final BlockBreakEvent ev = new BlockBreakEvent(belowDial, player);

            new WormholeXTremeBlockListener().onBlockBreak(ev);

            assertTrue(ev.isCancelled(),
                "a lever that is really there is part of the gate and stays protected");
        }
        finally
        {
            StargateManager.removeStargate(gate);
        }
    }
}
