package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/**
 * Tests the cached portal-block lookup and bounding box on {@link Stargate}.
 *
 * <p>These back the two hot paths the periodic entity sweep and the player move handler
 * run on, so the containment answers and the box extents both need pinning.
 */
public class StargatePortalCacheTest
{
    private static Stargate gateWithPortalRing(final World world)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("ring");
        gate.setGateWorld(world);
        // A small 2x2 ring at y=64..65, x=10..11, z=20.
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.getGatePortalBlocks().add(new Location(world, 11, 64, 20));
        gate.getGatePortalBlocks().add(new Location(world, 10, 65, 20));
        gate.getGatePortalBlocks().add(new Location(world, 11, 65, 20));
        return gate;
    }

    @Test
    public void portalBlockLookupAnswersContainment()
    {
        final World world = mock(World.class);
        final Stargate gate = gateWithPortalRing(world);

        assertTrue(gate.isGatePortalBlockAt(10, 64, 20));
        assertTrue(gate.isGatePortalBlockAt(11, 65, 20));
        assertFalse(gate.isGatePortalBlockAt(12, 64, 20));
        assertFalse(gate.isGatePortalBlockAt(10, 66, 20));
        assertFalse(gate.isGatePortalBlockAt(10, 64, 21));
    }

    @Test
    public void boundingBoxEnclosesEveryPortalBlock()
    {
        final World world = mock(World.class);
        final Stargate gate = gateWithPortalRing(world);

        final BoundingBox box = gate.getGatePortalBounds();
        assertNotNull(box);
        // Blocks span 10..11, 64..65, 20..20, and the box must cover the full volume of
        // the outermost blocks rather than stopping at their origin corners.
        assertEquals(10.0, box.getMinX());
        assertEquals(12.0, box.getMaxX());
        assertEquals(64.0, box.getMinY());
        assertEquals(66.0, box.getMaxY());
        assertEquals(20.0, box.getMinZ());
        assertEquals(21.0, box.getMaxZ());
    }

    @Test
    public void cachesRebuildWhenPortalBlocksChange()
    {
        final World world = mock(World.class);
        final Stargate gate = gateWithPortalRing(world);

        // Prime both caches.
        assertTrue(gate.isGatePortalBlockAt(10, 64, 20));
        assertEquals(12.0, gate.getGatePortalBounds().getMaxX());

        gate.getGatePortalBlocks().add(new Location(world, 20, 64, 20));

        assertTrue(gate.isGatePortalBlockAt(20, 64, 20), "cache should have picked up the added block");
        assertEquals(21.0, gate.getGatePortalBounds().getMaxX(), "box should have grown");
    }

    @Test
    public void gateWithNoPortalBlocksHasNoBounds()
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);

        assertNull(gate.getGatePortalBounds());
        assertFalse(gate.isGatePortalBlockAt(0, 0, 0));
    }

    @Test
    public void unsortedGateViewSeesRegisteredGates()
    {
        final World world = mock(World.class);
        final Stargate gate = gateWithPortalRing(world);
        StargateManager.registerStargate(gate);
        try
        {
            assertTrue(StargateManager.getAllGatesUnsorted().contains(gate));
        }
        finally
        {
            StargateManager.removeStargate(gate);
        }
    }
}
