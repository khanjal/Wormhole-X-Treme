package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A gate's portal blocks as a set and a box, rebuilt when the live list they come from changes size (#430).
 *
 * <p>Written for the move out of {@link Stargate}, on the conditions the older cache tests left
 * unpinned: each test below fails when the one condition it is named for is taken out of
 * {@link PortalCache}.
 */
class PortalCacheTest
{
    private World world;
    private List<Location> portal;
    private PortalCache cache;

    @BeforeEach
    void setUp()
    {
        world = mock(World.class);
        portal = new ArrayList<>();
        cache = new PortalCache(portal);
    }

    private Location block(final int x, final int y, final int z)
    {
        return new Location(world, x, y, z);
    }

    @Test
    void aCacheIsBuiltFromTheListItWasGivenAndFollowsItWhenItChangesSize()
    {
        portal.add(block(1, 64, 2));

        assertTrue(cache.contains(1, 64, 2), "the block in the list");
        assertFalse(cache.contains(2, 64, 1), "and not one with its coordinates swapped");
        assertFalse(cache.contains(1, 2, 64), "nor with y and z swapped");

        portal.add(block(3, 65, 4));

        assertTrue(cache.contains(3, 65, 4), "grown: the new block is there");
    }

    @Test
    void aListThatShrinksIsReadAgainAndForgetsWhatWentFromIt()
    {
        portal.add(block(1, 64, 2));
        portal.add(block(3, 65, 4));
        assertTrue(cache.contains(3, 65, 4), "built from two blocks");

        portal.remove(1);

        assertFalse(cache.contains(3, 65, 4), "shrunk: the block that went is gone");
        assertTrue(cache.contains(1, 64, 2), "and the one left stays");
        assertEquals(new BoundingBox(1, 64, 2, 2, 65, 3), cache.bounds(), "and the box follows");
    }

    @Test
    void theBoxIsBuiltOnceUntilTheListChangesSize()
    {
        portal.add(block(1, 64, 2));
        portal.add(block(3, 65, 4));

        final BoundingBox first = cache.bounds();

        assertSame(first, cache.bounds(), "asked again: the same box, not one built again");
        assertSame(first, cache.bounds(), "and again");
        assertTrue(cache.contains(1, 64, 2));
        assertSame(first, cache.bounds(), "asking whether a block is in it does not rebuild it");
    }

    @Test
    void theBoxEnclosesEveryBlockWholeFromTheLowestCornerToOneBeyondTheHighest()
    {
        portal.add(block(5, 70, 9));
        portal.add(block(2, 64, 6));
        portal.add(block(-3, 68, 7));

        assertEquals(new BoundingBox(-3, 64, 6, 6, 71, 10), cache.bounds(), "min of each, max of each plus a block");
    }

    @Test
    void aPortalWithNoBlocksHasNoBoxAndAListWithANullInItIsReadPastIt()
    {
        assertNull(cache.bounds(), "no blocks, no box");
        assertFalse(cache.contains(0, 0, 0), "and nothing is in it");

        portal.add(null);
        portal.add(block(1, 64, 2));

        assertTrue(cache.contains(1, 64, 2), "a null entry is skipped");
        assertEquals(new BoundingBox(1, 64, 2, 2, 65, 3), cache.bounds(), "and does not widen the box");
    }
}
