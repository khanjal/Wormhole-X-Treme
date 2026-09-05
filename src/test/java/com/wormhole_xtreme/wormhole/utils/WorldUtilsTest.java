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
    public void correctingAnAlreadyCorrectedLocationChangesNothingFurther() {
        // The property that lets BeamAnimation.start own this correction for every beam it
        // runs, instead of each caller applying it just before calling in. Every call site
        // used to do it separately and identically, which made it a convention the next
        // caller could forget rather than a guarantee. Folding it inward is only safe if
        // running it twice is the same as running it once -- a caller that still snaps its
        // own location first must not have that undone or nudged again.
        //
        // Two things could break this: the search preferring some *other* standable spot
        // over the exact one it is handed, and the +0.5 block-centring accumulating on each
        // pass. It searches dy=0 first, and getBlockX() of an already-centred x is the same
        // integer it started from, so neither does -- but neither is obvious enough from
        // reading it to leave unpinned now that correctness depends on it.
        final World world = worldWithGroundAt(61);
        final Location stored = new Location(world, 5, 64, 9, 12.0f, 34.0f);

        final Location once = WorldUtils.findSafePlayerLocation(stored);
        final Location twice = WorldUtils.findSafePlayerLocation(once);

        assertEquals(once.getX(), twice.getX(), 1e-9,
            "a second correction must not re-centre an already-centred x");
        assertEquals(once.getY(), twice.getY(), 1e-9,
            "nor move a location that is already resting on standable ground");
        assertEquals(once.getZ(), twice.getZ(), 1e-9,
            "a second correction must not re-centre an already-centred z");
        assertEquals(once.getYaw(), twice.getYaw(), "facing must survive a second pass too");
        assertEquals(once.getPitch(), twice.getPitch());
    }

    @Test
    public void findSafePlayerLocationPassesThroughNullsUnchanged() {
        assertNull(WorldUtils.findSafePlayerLocation(null));

        final Location noWorld = new Location(null, 5, 64, 9);
        assertSame(noWorld, WorldUtils.findSafePlayerLocation(noWorld),
            "with no world to search, the original location is returned as-is");
    }
}
