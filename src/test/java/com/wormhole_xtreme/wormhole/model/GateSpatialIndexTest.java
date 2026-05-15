package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class GateSpatialIndexTest
{
    @AfterEach
    public void tearDown()
    {
        GateSpatialIndex.clear();
    }

    @Test
    public void addAndCollectWithinRadiusIncludesNearbyLocation()
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
    public void removeAndClearWorkAsExpected()
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
}
