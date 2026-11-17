package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Axis;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Finding a nether portal's opening from one of its blocks (#514): a flood fill of the portal blocks
 * sharing its axis. Get it wrong and a view is drawn through the wrong rectangle, or through a
 * shape vanilla never lights. Plain numbers, no server.
 */
class NetherPortalTest
{
    /** Portal blocks by place. */
    private final Map<Spot, Axis> blocks = new HashMap<>();

    /** Lights a rectangle of portal blocks, along x in the plane z = 10 or along z in x = 10. */
    private void light(final Axis axis, final int low, final int width, final int height)
    {
        for (int across = low; across < (low + width); across++)
        {
            for (int y = 64; y < (64 + height); y++)
            {
                blocks.put((axis == Axis.X) ? new Spot(across, y, 10) : new Spot(10, y, across), axis);
            }
        }
    }

    private NetherPortal at(final int x, final int y, final int z)
    {
        return NetherPortal.at("overworld", x, y, z, (bx, by, bz) -> blocks.get(new Spot(bx, by, bz)));
    }

    /** The smallest portal along x is found whole from its top corner. */
    @Test
    void aTwoByThreePortalAlongXIsFoundWholeFromAnyOfItsBlocks()
    {
        light(Axis.X, 0, 2, 3);

        final NetherPortal portal = at(1, 66, 10);

        assertNotNull(portal);
        assertTrue(portal.alongX());
        assertEquals(2, portal.width());
        assertEquals(3, portal.height());
        assertEquals(6, portal.cells().size());
        assertEquals(new Spot(0, 64, 10), portal.bottomMiddle());
    }

    /** One along z runs in z, in the plane of its x, and is told apart from one along x. */
    @Test
    void aPortalAlongZIsFoundInItsOwnPlane()
    {
        light(Axis.Z, 5, 4, 5);

        final NetherPortal portal = at(10, 64, 7);

        assertNotNull(portal);
        assertFalse(portal.alongX());
        assertEquals(4, portal.width());
        assertEquals(5, portal.height());
        assertEquals(10, portal.plane());
        assertEquals(new Spot(10, 64, 6), portal.bottomMiddle());
    }

    /** Vanilla's largest, 21 square, is a portal; one a block wider is not one vanilla lights. */
    @Test
    void aPortalWiderThanVanillaLightsIsNotOne()
    {
        light(Axis.X, 0, 21, 21);
        assertNotNull(at(0, 64, 10), "vanilla's largest");

        blocks.clear();
        light(Axis.X, 0, 22, 3);
        assertNull(at(0, 64, 10));
    }

    /** Portal blocks that are not one whole rectangle are not a portal vanilla made. */
    @Test
    void portalBlocksThatAreNotARectangleAreNotAPortal()
    {
        light(Axis.X, 0, 2, 3);
        blocks.remove(new Spot(1, 66, 10));

        assertNull(at(0, 64, 10));
    }

    /** A block that is not a portal block finds nothing, and neither does a portal block on the other axis next to it. */
    @Test
    void theFillStaysOnItsOwnAxis()
    {
        light(Axis.X, 0, 2, 3);
        blocks.put(new Spot(2, 64, 10), Axis.Z);

        assertNull(at(5, 64, 10));
        assertEquals(2, at(0, 64, 10).width(), "the block along z beside it is not part of it");
    }

    /** A portal is intact while every block stays lit, and not once one goes. */
    @Test
    void aPortalIsNoLongerIntactOnceABlockGoes()
    {
        light(Axis.X, 0, 2, 3);
        final NetherPortal portal = at(0, 64, 10);
        assertTrue(portal.intact((x, y, z) -> blocks.get(new Spot(x, y, z))));

        blocks.remove(new Spot(1, 65, 10));

        assertFalse(portal.intact((x, y, z) -> blocks.get(new Spot(x, y, z))));
    }

    /** The frame is the ring round the opening in its plane, corners included, which a gate's framing test accepts. */
    @Test
    void theFrameIsTheRingRoundTheOpening()
    {
        light(Axis.X, 0, 2, 3);
        final NetherPortal portal = at(0, 64, 10);

        assertEquals(14, portal.frame().size());
        assertTrue(portal.frame().contains(new Spot(-1, 63, 10)), "a corner");
        assertTrue(GateViews.framed(portal.cells(), portal.frame(), new Spot(1, 0, 0)));
    }

    /**
     * A sheet of portal blocks far bigger than any portal stops the fill once it passes the biggest,
     * rather than reading every block of it on the main thread.
     */
    @Test
    void aHugeSheetStopsTheFillEarly()
    {
        light(Axis.X, 0, 60, 60);
        final int[] read = { 0 };

        final NetherPortal portal = NetherPortal.at("overworld", 0, 64, 10, (bx, by, bz) ->
        {
            read[0]++;
            return blocks.get(new Spot(bx, by, bz));
        });

        assertNull(portal);
        assertTrue(read[0] < 2000, "read " + read[0] + " blocks of a 3600-block sheet");
    }
}
