package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * A big gate's view narrows to the opening of the small gate it dials (#583).
 *
 * <p>Seen in a world: a Massive gate dialling a Standard one showed a Massive-sized slice of the far
 * side, past the small gate's ceiling and walls and into the next hallway. The far opening, mapped
 * back through the window, is what the view narrows to, so where it sits is the far gate's own.
 */
class FunnelTest
{
    /** Where travellers land, in front of the far gate: facing south. */
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    /** An eleven-square opening facing south, looked into northwards. */
    private static final WindowShape BIG = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 11, 11);

    /** A far gate's portal cells: its opening, a block behind where travellers land, facing them. */
    private static List<Spot> farGate(final int lowX, final int highX, final int lowY, final int highY)
    {
        final List<Spot> cells = new ArrayList<>();
        for (int x = lowX; x <= highX; x++)
        {
            for (int y = lowY; y <= highY; y++)
            {
                cells.add(new Spot(x, y, 199));
            }
        }
        return cells;
    }

    /** A three-wide, four-tall far gate, on the arrival's column and standing on its row. */
    private static Funnel toStandard()
    {
        return Funnel.of(BIG, farGate(99, 101, 70, 73), 8, Material.WATER);
    }

    @Test
    void aSmallFarGateIsMappedThroughTheWindowOntoTheMiddleColumnsAndTheBottomRows()
    {
        final Funnel funnel = toStandard();

        assertNotNull(funnel, "a big gate onto a small one narrows");
        assertEquals(4, funnel.far().left(), "centred on the arrival's column, the opening's middle");
        assertEquals(6, funnel.far().right());
        assertEquals(0, funnel.far().bottom(), "standing on the row travellers land on, not centred up the big opening");
        assertEquals(3, funnel.far().top());
        assertEquals(Material.WATER, funnel.wall());
    }

    @Test
    void aFarGateAsBigOrBiggerEachWayLeavesTheViewWhole()
    {
        assertNull(Funnel.of(BIG, farGate(95, 105, 70, 80), 8, Material.WATER), "the same size");
        assertNull(Funnel.of(BIG, farGate(90, 110, 69, 85), 8, Material.WATER), "bigger: small to large is unchanged");
    }

    @Test
    void aFarGateNarrowerOnlyOneWayNarrowsOnlyThatWay()
    {
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 70, 85), 8, Material.WATER);

        assertNotNull(funnel);
        assertEquals(4, funnel.far().left());
        assertEquals(6, funnel.far().right());
        assertEquals(0, funnel.far().bottom());
        assertEquals(10, funnel.far().top(), "taller than the near gate: the near one's height, never past it");

        final Funnel lower = Funnel.of(BIG, farGate(95, 105, 70, 73), 8, Material.WATER);
        assertNotNull(lower, "as wide but shorter still narrows");
        assertEquals(0, lower.far().left());
        assertEquals(10, lower.far().right(), "the whole width kept");
        assertEquals(3, lower.far().top(), "only the height narrowed");
    }

    @Test
    void noTaperNoPortalMaterialOrNoFarGateLeavesTheViewWhole()
    {
        assertNull(Funnel.of(BIG, farGate(99, 101, 70, 73), 0, Material.WATER), "0 turns it off");
        assertNull(Funnel.of(BIG, farGate(99, 101, 70, 73), 8, Material.AIR), "nothing to draw round it");
        assertNull(Funnel.of(BIG, farGate(99, 101, 70, 73), 8, null));
        assertNull(Funnel.of(BIG, List.of(), 8, Material.WATER));
        assertNull(Funnel.of(BIG, null, 8, Material.WATER));
    }

    @Test
    void aFarGateLyingFlatIsNotAHoleAcrossTheView()
    {
        final List<Spot> flat = new ArrayList<>();
        for (int z = 197; z <= 199; z++)
        {
            for (int x = 99; x <= 101; x++)
            {
                flat.add(new Spot(x, 71, z));
            }
        }

        assertNull(Funnel.of(BIG, flat, 8, Material.WATER),
            "its cells run along the view, not across it, though they line up with the opening's second row");
    }

    @Test
    void aFarGateWhollyBesideTheViewLeavesItWhole()
    {
        assertNull(Funnel.of(BIG, farGate(80, 82, 70, 73), 8, Material.WATER), "nothing of it lines up with the opening");
        assertNull(Funnel.of(BIG, farGate(99, 101, 90, 93), 8, Material.WATER), "all of it above the opening");
    }

    @Test
    void atTheWindowTheFunnelIsTheWholeNearOpeningAndPastTheTaperTheFarOne()
    {
        final Funnel funnel = toStandard();

        assertTrue(funnel.holds(0, 0, 0), "the opening's own corner");
        assertTrue(funnel.holds(0, 10, 10), "and the far one");
        assertFalse(funnel.holds(0, 11, 5), "beside the opening");
        assertFalse(funnel.holds(0, 5, -1), "below it");
        for (final int layer : new int[] { 8, 9, 40 })
        {
            assertTrue(funnel.holds(layer, 4, 0), "the far opening's corner, layer " + layer);
            assertTrue(funnel.holds(layer, 6, 3), "and its other, layer " + layer);
            assertFalse(funnel.holds(layer, 3, 0), "a column left of it, layer " + layer);
            assertFalse(funnel.holds(layer, 7, 0), "right of it, layer " + layer);
            assertFalse(funnel.holds(layer, 5, 4), "above its ceiling, layer " + layer);
            assertFalse(funnel.holds(layer, 5, -1), "below its floor, layer " + layer);
        }
    }

    @Test
    void halfwayDownTheTaperItIsBetweenTheTwo()
    {
        final Funnel funnel = toStandard();

        // At layer 4, half way: left 2, right 8, top 6.5.
        assertTrue(funnel.holds(4, 2, 6), "the narrowing edge, still in");
        assertTrue(funnel.holds(4, 8, 7), "half a block over the edge rounds in");
        assertFalse(funnel.holds(4, 1, 0), "past the left edge");
        assertFalse(funnel.holds(4, 9, 0), "past the right edge");
        assertFalse(funnel.holds(4, 5, 8), "past the ceiling");
    }

    @Test
    void theWallIsTheRingOutsideTheFunnelAndNothingFurther()
    {
        final Funnel funnel = toStandard();

        assertTrue(funnel.wall(20, 3, 1), "left of the far opening");
        assertTrue(funnel.wall(20, 7, 1), "right of it");
        assertTrue(funnel.wall(20, 5, 4), "its ceiling");
        assertTrue(funnel.wall(20, 5, -1), "its floor");
        assertFalse(funnel.wall(20, 5, 1), "inside is the view");
        assertFalse(funnel.wall(20, 2, 1), "two out is behind the wall: never seen, never sent");
        assertFalse(funnel.wall(20, 3, 4), "nor a corner, which no line of sight reaches first");
    }

    @Test
    void whereTheFunnelStepsInTheCellsItLeftAreWall()
    {
        final Funnel funnel = toStandard();

        // Column 10 is in at layer 1 and out at layer 2.
        assertTrue(funnel.holds(1, 10, 0));
        assertFalse(funnel.holds(2, 10, 0));
        assertTrue(funnel.wall(2, 10, 0), "the cell the funnel stepped in from, seen from the layer before");
    }

    /**
     * Every line of sight that leaves the funnel enters a wall cell first: each cell outside it that
     * is beside one inside, on its layer or from the layer before, is wall.
     */
    @Test
    void everyWayOutOfTheFunnelIsWalled()
    {
        final Funnel funnel = toStandard();
        final Set<Spot> walls = new HashSet<>();
        funnel.forEachWall(20, (layer, across, up) -> walls.add(new Spot(layer, across, up)));

        for (int layer = 1; layer < 20; layer++)
        {
            for (int across = -1; across <= 11; across++)
            {
                for (int up = -1; up <= 11; up++)
                {
                    final boolean outside = !funnel.holds(layer, across, up);
                    final boolean beside = funnel.holds(layer - 1, across, up) || funnel.holds(layer, across - 1, up)
                        || funnel.holds(layer, across + 1, up) || funnel.holds(layer, across, up - 1)
                        || funnel.holds(layer, across, up + 1);
                    assertEquals(outside && beside, walls.contains(new Spot(layer, across, up)),
                        "layer " + layer + ", column " + across + ", row " + up);
                }
            }
        }
        assertTrue(walls.contains(new Spot(19, 3, 0)), "the wall runs on past the taper");
        assertFalse(walls.contains(new Spot(20, 3, 0)), "and stops short of the depth");
        assertTrue(walls.contains(new Spot(1, -1, 0)), "and starts on the first layer, behind the frame");
    }

    /** A far gate three wide and four tall, a block behind an arrival facing a yaw, across the way it faces. */
    private static List<Spot> farGateFacing(final Place arrival)
    {
        final Spot ahead = WindowShape.aheadOf(arrival.yaw());
        final Spot across = new Spot(-ahead.z(), 0, ahead.x());
        final int behindX = (int) Math.floor(arrival.x()) - ahead.x();
        final int behindZ = (int) Math.floor(arrival.z()) - ahead.z();
        final List<Spot> cells = new ArrayList<>();
        for (int step = -1; step <= 1; step++)
        {
            for (int y = 70; y <= 73; y++)
            {
                cells.add(new Spot(behindX + (step * across.x()), y, behindZ + (step * across.z())));
            }
        }
        return cells;
    }

    /**
     * Whichever way the near gate is looked into and whichever way the far gate faces, turned a quarter
     * from each other or not, the far opening lands on the same middle columns and bottom rows.
     *
     * <p>A turn or sign wrong for one facing would put the tunnel off to one side, or leave a gate facing
     * that way whole.
     */
    @Test
    void everyFacingAndEveryTurnBetweenTheTwoGatesNarrowsToTheSameColumns()
    {
        final Spot[] intos = { new Spot(0, 0, -1), new Spot(1, 0, 0), new Spot(0, 0, 1), new Spot(-1, 0, 0) };
        for (final Spot into : intos)
        {
            for (final float yaw : new float[] { 0.0f, 90.0f, 180.0f, 270.0f })
            {
                final Place arrival = new Place("far", 100.5, 70.0, 200.5, yaw, 0.0f);
                final WindowShape shape = WindowShape.through(new Spot(10, 64, 20), into, arrival, 11, 11);
                final Funnel funnel = Funnel.of(shape, farGateFacing(arrival), 8, Material.WATER);
                final String pair = "looked into " + into + ", far gate at yaw " + yaw;

                assertNotNull(funnel, pair);
                assertEquals(new Funnel.Opening(4, 6, 0, 3), funnel.far(), pair);
            }
        }
    }

    /**
     * A far gate whose opening starts above where travellers land, a step up into it, narrows to its own
     * rows: the tunnel's floor is a wall, not the arrival's row.
     */
    @Test
    void aFarGateStandingAboveTheArrivalRowNarrowsToItsOwnRows()
    {
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 72, 75), 8, Material.WATER);

        assertNotNull(funnel);
        assertEquals(new Funnel.Opening(4, 6, 2, 5), funnel.far());
        assertTrue(funnel.holds(20, 5, 2), "its bottom row");
        assertFalse(funnel.holds(20, 5, 0), "the arrival's row, below it");
        assertTrue(funnel.wall(20, 5, 1), "walled underneath");
    }

    /** A stand-in already shown may stand a block past the funnel's edge, so one on it does not flicker. */
    @Test
    void slackWidensEveryEdgeByThatMuch()
    {
        final Funnel funnel = toStandard();

        assertFalse(funnel.holds(20, 3, 1), "a column left of the far opening");
        assertTrue(funnel.holds(20, 3, 1, 1.0), "held: a block out on the left");
        assertTrue(funnel.holds(20, 7, 1, 1.0), "on the right");
        assertTrue(funnel.holds(20, 5, 4, 1.0), "above");
        assertTrue(funnel.holds(20, 5, -1, 1.0), "below");
        assertFalse(funnel.holds(20, 2, 1, 1.0), "but not two");
        assertFalse(funnel.holds(20, 8, 1, 1.0));
        assertFalse(funnel.holds(20, 5, 5, 1.0));
        assertFalse(funnel.holds(20, 5, -2, 1.0));
    }
}
