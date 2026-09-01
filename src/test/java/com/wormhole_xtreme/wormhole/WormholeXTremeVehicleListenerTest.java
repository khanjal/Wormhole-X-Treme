package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Tests for safe minecart spawn location selection.
 */
public class WormholeXTremeVehicleListenerTest
{
    @Test
    public void prefersRailBelowOrSameHeight()
    {
        final World world = mock(World.class);
        final int bx = 10, bz = 20;
        final int preferredY = 65;

        final Block air65 = mock(Block.class);
        when(air65.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(bx, preferredY, bz)).thenReturn(air65);

        final Block rail64 = mock(Block.class);
        when(rail64.getType()).thenReturn(Material.RAIL);
        when(rail64.getX()).thenReturn(bx);
        when(rail64.getY()).thenReturn(64);
        when(rail64.getZ()).thenReturn(bz);
        when(world.getBlockAt(bx, 64, bz)).thenReturn(rail64);

        final Location preferred = new Location(world, bx + 0.5, preferredY, bz + 0.5);
        final Location safe = WormholeXTremeVehicleListener.findSafeMinecartLocation(preferred);

        assertEquals(bx + 0.5, safe.getX());
        assertEquals(preferredY + 1.0, safe.getY());
        assertEquals(bz + 0.5, safe.getZ());
    }

    @Test
    public void fallsBackToHighestBlock()
    {
        final World world = mock(World.class);
        final int bx = 5, bz = 7;
        final Location preferred = new Location(world, bx + 0.5, 50.0, bz + 0.5);

        final Block top = mock(Block.class);
        when(top.getY()).thenReturn(70);
        when(world.getHighestBlockAt(bx, bz)).thenReturn(top);

        final Location safe = WormholeXTremeVehicleListener.findSafeMinecartLocation(preferred);
        assertEquals(51.0, safe.getY());
    }

    @Test
    public void forwardFullBlockOffsetsCorrectly()
    {
        final World world = mock(World.class);
        final Location base = new Location(world, 10.5, 65.0, 20.5);

        final Location out = WormholeXTremeVehicleListener.forwardAndUp(base, BlockFace.EAST, 1.0, 1.0);

        assertEquals(10.5 + (BlockFace.EAST.getModX() * 1.0), out.getX());
        assertEquals(65.0 + 1.0, out.getY());
        assertEquals(20.5 + (BlockFace.EAST.getModZ() * 1.0), out.getZ());
    }

    @Test
    public void forwardNullFacingAddsOnlyUp()
    {
        final World world = mock(World.class);
        final Location base = new Location(world, 7.5, 50.0, 8.5);

        final Location out = WormholeXTremeVehicleListener.forwardAndUp(base, null, 1.0, 2.0);

        assertEquals(7.5, out.getX());
        assertEquals(52.0, out.getY());
        assertEquals(8.5, out.getZ());
    }

    @Test
    public void computeExitVelocityPointsAwayFromGate()
    {
        final Vector incoming = new Vector(2.0, 0.0, 0.0); // speed = 2
        final Vector out = WormholeXTremeVehicleListener.computeExitVelocity(BlockFace.NORTH, incoming, 5.0);
        // Facing NORTH => direction z = -1, so output z should be -speed*5
        assertEquals(-10.0, out.getZ(), 1e-6);
        assertEquals(0.0, out.getX(), 1e-6);
    }

    @Test
    public void computeExitVelocityFallsBackToIncomingDirection()
    {
        final Vector incoming = new Vector(0.0, 0.0, 3.0); // speed = 3
        final Vector out = WormholeXTremeVehicleListener.computeExitVelocity(null, incoming, 4.0);
        // no facing, should use incoming Z direction
        assertEquals(0.0, out.getX(), 1e-6);
        assertTrue(out.getZ() > 0);
        assertEquals(3.0 * 4.0, out.length(), 1e-6);
    }
}
