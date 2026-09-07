package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GateSpatialIndexTest
{
    @AfterEach
    void tearDown()
    {
        GateSpatialIndex.clear();
    }

    @Test
    void addAndCollectWithinRadiusIncludesNearbyLocation()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("testworld");

        final Location center = new Location(w, 100, 64, 100);
        final Location near = new Location(w, 105, 64, 100);
        final Location far = new Location(w, 200, 64, 200);

        GateSpatialIndex.add(near);
        GateSpatialIndex.add(far);

        final var results = GateSpatialIndex.collectLocationsWithinRadius(center, 10, 5);

        assertTrue(results.contains(near), "Nearby location should be returned by spatial index");
        assertFalse(results.contains(far), "Far location should not be returned by spatial index");
    }

    @Test
    void removeAndClearWorkAsExpected()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("w");

        final Location a = new Location(w, 16, 64, 16);
        GateSpatialIndex.add(a);
        assertTrue(GateSpatialIndex.collectLocationsWithinRadius(a, 1, 1).contains(a));

        GateSpatialIndex.remove(a);
        assertFalse(GateSpatialIndex.collectLocationsWithinRadius(a, 1, 1).contains(a));

        GateSpatialIndex.add(a);
        GateSpatialIndex.clear();
        assertTrue(GateSpatialIndex.collectLocationsWithinRadius(a, 1, 1).isEmpty());
    }

    /**
     * The vertical radius is separate from the horizontal one.
     *
     * <p>Callers pass them separately because a gate is tall and thin: the interesting
     * neighbours are the ones beside you, not the ones forty blocks up in the same column.
     * Collapsing the two into one distance would quietly widen every caller's search.
     */
    @Test
    void theVerticalRadiusIsCheckedSeparately()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("testworld");

        final Location center = new Location(w, 100, 64, 100);
        final Location straightUp = new Location(w, 100, 80, 100);

        GateSpatialIndex.add(straightUp);

        assertFalse(GateSpatialIndex.collectLocationsWithinRadius(center, 16, 5).contains(straightUp),
            "directly overhead is still out of range when the vertical radius is small");
        assertTrue(GateSpatialIndex.collectLocationsWithinRadius(center, 16, 16).contains(straightUp),
            "and in range once the vertical radius allows it");
    }

    /** A location exactly at the radius is inside it. */
    @Test
    void theRadiusIsInclusive()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("testworld");

        final Location center = new Location(w, 100, 64, 100);
        final Location edgeXZ = new Location(w, 105, 64, 100);
        final Location edgeY = new Location(w, 100, 69, 100);
        final Location justPast = new Location(w, 106, 64, 100);

        GateSpatialIndex.add(edgeXZ);
        GateSpatialIndex.add(edgeY);
        GateSpatialIndex.add(justPast);

        final var results = GateSpatialIndex.collectLocationsWithinRadius(center, 5, 5);

        assertTrue(results.contains(edgeXZ), "exactly at the horizontal radius is inside it");
        assertTrue(results.contains(edgeY), "exactly at the vertical radius is inside it");
        assertFalse(results.contains(justPast), "one past it is not");
    }

    /**
     * A gate at the same coordinates in another world is not returned.
     *
     * <p>Two separate things hold this, and either alone is enough: chunk keys are prefixed
     * with the world name, so the other world's gates are in a different bucket entirely,
     * and the collector then checks each location's world again anyway. Mutating either one
     * on its own leaves this passing; mutating both fails it.
     *
     * <p>Worth knowing before anyone deletes one of them as redundant -- they are redundant,
     * and that is the point.
     */
    @Test
    void anotherWorldsGateAtTheSameCoordinatesIsNotReturned()
    {
        final World here = mock(World.class);
        when(here.getName()).thenReturn("world");
        final World nether = mock(World.class);
        when(nether.getName()).thenReturn("world_nether");

        final Location center = new Location(here, 100, 64, 100);
        final Location elsewhere = new Location(nether, 100, 64, 100);

        GateSpatialIndex.add(elsewhere);

        assertFalse(GateSpatialIndex.collectLocationsWithinRadius(center, 16, 16).contains(elsewhere));
    }

    /** Nothing to search around means an empty answer rather than a failure. */
    @Test
    void aCentreWithNoWorldFindsNothing()
    {
        assertTrue(GateSpatialIndex.collectLocationsWithinRadius(null, 10, 10).isEmpty());
        assertTrue(GateSpatialIndex.collectLocationsWithinRadius(
            new Location(null, 0, 64, 0), 10, 10).isEmpty());
    }

    /**
     * A search spanning several chunks finds gates in all of them.
     *
     * <p>The collector walks chunk columns rather than blocks, so a radius wide enough to
     * cross a chunk boundary has to look at more than the one the centre sits in.
     */
    @Test
    void aSearchWiderThanOneChunkCrossesTheBoundary()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("testworld");

        // x=15 and x=16 are in different chunks.
        final Location center = new Location(w, 15, 64, 15);
        final Location nextChunk = new Location(w, 20, 64, 15);

        GateSpatialIndex.add(nextChunk);

        assertTrue(GateSpatialIndex.collectLocationsWithinRadius(center, 8, 8).contains(nextChunk),
            "a neighbour just over a chunk boundary is still a neighbour");
    }
}
