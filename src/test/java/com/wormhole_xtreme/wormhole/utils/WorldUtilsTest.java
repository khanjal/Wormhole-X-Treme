package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

public class WorldUtilsTest {

    /**
     * A world whose only solid ground is the single block layer at {@code solidTop} -- every
     * block at or below that y is solid, everything above is passable. That makes exactly one
     * y-value per column standable: {@code solidTop + 1}, feet resting right on top of it.
     */
    private static World worldWithGroundAt(final int solidTop) {
        final World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            final int y = invocation.getArgument(1);
            final Block block = mock(Block.class);
            when(block.isPassable()).thenReturn(y > solidTop);
            return block;
        });
        return world;
    }

    @Test
    public void testIsSameBlock() {
        Block b1 = mock(Block.class);
        Block b2 = mock(Block.class);

        when(b1.getX()).thenReturn(1);
        when(b1.getY()).thenReturn(2);
        when(b1.getZ()).thenReturn(3);

        when(b2.getX()).thenReturn(1);
        when(b2.getY()).thenReturn(2);
        when(b2.getZ()).thenReturn(3);

        assertTrue(WorldUtils.isSameBlock(b1, b2));

        when(b2.getZ()).thenReturn(4);
        assertFalse(WorldUtils.isSameBlock(b1, b2));
    }

    @Test
    public void testIsAdjacent() {
        Block b1 = mock(Block.class);
        Block b2 = mock(Block.class);

        when(b1.getX()).thenReturn(10);
        when(b1.getY()).thenReturn(20);
        when(b1.getZ()).thenReturn(30);

        // same block
        when(b2.getX()).thenReturn(10);
        when(b2.getY()).thenReturn(20);
        when(b2.getZ()).thenReturn(30);
        assertTrue(WorldUtils.isAdjacent(b1, b2));

        // within 1 block
        when(b2.getX()).thenReturn(9);
        when(b2.getY()).thenReturn(20);
        when(b2.getZ()).thenReturn(29);
        assertTrue(WorldUtils.isAdjacent(b1, b2));

        // outside radius
        when(b2.getX()).thenReturn(12);
        when(b2.getY()).thenReturn(20);
        when(b2.getZ()).thenReturn(30);
        assertFalse(WorldUtils.isAdjacent(b1, b2));
    }

    @Test
    public void testIsIce() {
        assertTrue(MaterialUtils.isIce(Material.ICE));
        assertTrue(MaterialUtils.isIce(Material.PACKED_ICE));
        assertTrue(MaterialUtils.isIce(Material.BLUE_ICE));
        assertTrue(MaterialUtils.isIce(Material.FROSTED_ICE));
        assertFalse(MaterialUtils.isIce(Material.AIR));
    }

    @Test
    public void findSafePlayerLocationDropsDownWhenTheGroundHasBeenDugOutSinceItWasStored() {
        // Stored while standable at y=64 (ground top was 63); the ground has since been dug
        // out down to a top of 61, so 64 is now open air with nothing underfoot for two more
        // blocks down.
        final World world = worldWithGroundAt(61);
        final Location stored = new Location(world, 5, 64, 9, 12.0f, 34.0f);

        final Location safe = WorldUtils.findSafePlayerLocation(stored);

        assertEquals(62, safe.getBlockY(), "should have dropped to rest on the new, lower ground");
        assertEquals(5.5, safe.getX(), 1e-9);
        assertEquals(9.5, safe.getZ(), 1e-9);
        assertEquals(12.0f, safe.getYaw(), "facing should be preserved, only position corrected");
        assertEquals(34.0f, safe.getPitch());
    }

    @Test
    public void findSafePlayerLocationRisesUpWhenTheGroundHasBeenBuiltUpSinceItWasStored() {
        // Stored while standable at y=64 (ground top was 63); the ground has since been built
        // up to a top of 64, so the stored point is now buried inside solid block.
        final World world = worldWithGroundAt(64);
        final Location stored = new Location(world, 5, 64, 9);

        final Location safe = WorldUtils.findSafePlayerLocation(stored);

        assertEquals(65, safe.getBlockY(), "should have risen to stand on top of the new ground");
    }

    @Test
    public void findSafePlayerLocationFallsBackToTheStoredPointWhenNothingNearbyIsStandable() {
        // Solid everywhere the search reaches -- nothing to stand on at all, e.g. the point
        // has been entombed well beyond the +-3 search window.
        final World world = worldWithGroundAt(999);
        final Location stored = new Location(world, 5, 64, 9);

        final Location safe = WorldUtils.findSafePlayerLocation(stored);

        assertEquals(stored.getBlockX(), safe.getBlockX());
        assertEquals(stored.getBlockY(), safe.getBlockY());
        assertEquals(stored.getBlockZ(), safe.getBlockZ());
    }

    @Test
    public void findSafePlayerLocationPassesThroughNullsUnchanged() {
        assertNull(WorldUtils.findSafePlayerLocation(null));

        final Location noWorld = new Location(null, 5, 64, 9);
        assertSame(noWorld, WorldUtils.findSafePlayerLocation(noWorld),
            "with no world to search, the original location is returned as-is");
    }
}
