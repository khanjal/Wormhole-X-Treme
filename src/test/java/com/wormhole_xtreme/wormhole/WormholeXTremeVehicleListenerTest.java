package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
}
