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
 * A big gate's view of the small gate it dials is a tunnel with the view at its end (#583).
 *
 * <p>Seen in a world: a Massive gate dialling a Standard one showed a Massive-sized slice of the far
 * side, past the small gate's ceiling and walls and into the next hallway; clipped to a funnel, the far
 * room's ceiling still hung in the funnel's wide end. The far opening, mapped back through the window,
 * is what the tunnel narrows to, so where it sits is the far gate's own, and the room starts past it.
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
        final Set<Spot> walls = new HashSet<>();
        funnel.forEachWall(20, (layer, across, up) -> walls.add(new Spot(layer, across, up)));
        assertTrue(walls.contains(new Spot(12, 5, 11)), "the ceiling, a row above the near opening's top, is walled");
        final Set<Spot> hollow = new HashSet<>();
        funnel.forEachHollow(20, (layer, across, up) -> hollow.add(new Spot(layer, across, up)));
        assertTrue(hollow.contains(new Spot(3, 5, 10)), "the tunnel's top row is the near opening's, hollow");

        final Funnel lower = Funnel.of(BIG, farGate(95, 105, 70, 73), 8, Material.WATER);
        assertNotNull(lower, "as wide but shorter still narrows");
        assertEquals(0, lower.far().left());
        assertEquals(10, lower.far().right(), "the whole width kept");
        assertEquals(3, lower.far().top(), "only the height narrowed");
    }

    @Test
    void noTaperNoPortalMaterialOrNoFarGateLeavesTheViewWhole()
    {
        assertNull(Funnel.of(BIG, farGate(99, 101, 70, 73), 0, Material.WATER), "no length allowed: turned off");
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
        assertFalse(funnel.holds(0, 5, -1), "the row beneath it is the tunnel's floor");
        for (final int layer : new int[] { 8, 9, 40 })
        {
            assertTrue(funnel.holds(layer, 4, 0), "the far opening's corner, layer " + layer);
            assertTrue(funnel.holds(layer, 6, 3), "and its other, layer " + layer);
            assertFalse(funnel.holds(layer, 3, 0), "a column left of it, layer " + layer);
            assertFalse(funnel.holds(layer, 7, 0), "right of it, layer " + layer);
            assertFalse(funnel.holds(layer, 5, 4), "above its ceiling, layer " + layer);
            assertTrue(funnel.holds(layer, 5, -1), "the ground it stands on, not a wall, layer " + layer);
            assertFalse(funnel.holds(layer, 5, -2), "below the ground, layer " + layer);
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
        assertTrue(funnel.wall(20, 5, -2), "under the ground it stands on");
        assertFalse(funnel.wall(20, 5, -1), "the ground itself is the view: travellers walk across it");
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
        // Both edges step in at layer 2, so this corner has nothing inside beside it on its own layer.
        assertTrue(funnel.holds(1, 10, 9));
        assertFalse(funnel.holds(2, 9, 9) || funnel.holds(2, 10, 8));
        assertTrue(funnel.wall(2, 10, 9), "a corner left on both sides at once, reached only from the layer before");
    }

    /**
     * The wall is the ring of cells just outside the funnel, on every layer to the depth: past the
     * taper, the far opening's sides and ceiling and a row under its ground, and on the first layer,
     * round the near opening behind the frame.
     */
    @Test
    void theWallIsTheRingRoundTheFunnelOnEveryLayer()
    {
        final Funnel funnel = toStandard();
        final Set<Spot> walls = new HashSet<>();
        funnel.forEachWall(20, (layer, across, up) -> walls.add(new Spot(layer, across, up)));
        final Set<Spot> deep = new HashSet<>();
        for (int up = -1; up <= 3; up++)
        {
            deep.add(new Spot(19, 3, up));
            deep.add(new Spot(19, 7, up));
        }
        for (int across = 4; across <= 6; across++)
        {
            deep.add(new Spot(19, across, -2));
            deep.add(new Spot(19, across, 4));
        }
        final Set<Spot> lastLayer = new HashSet<>(walls);
        lastLayer.removeIf(cell -> cell.x() != 19);

        assertEquals(deep, lastLayer, "past the taper: both sides, the ceiling and under the ground, no corners");
        assertTrue(walls.contains(new Spot(1, -1, 0)), "on the first layer, beside the near opening behind the frame");
        assertTrue(walls.contains(new Spot(1, 5, -1)), "and the tunnel's floor, level with the near opening's bottom");
        assertFalse(walls.contains(new Spot(1, 5, -2)), "nothing under the floor of the tunnel");
        assertTrue(walls.contains(new Spot(1, 5, 10)), "and its top row, which the taper has already left");
        assertTrue(walls.contains(new Spot(7, 5, -1)), "the tunnel's floor runs to its last layer");
        assertFalse(walls.contains(new Spot(8, 5, -1)), "where the far ground starts, level with it");
        assertTrue(walls.contains(new Spot(8, 5, -2)), "with the wall under that");
        assertFalse(walls.contains(new Spot(1, -1, 10)), "nor a corner");
        assertFalse(walls.contains(new Spot(20, 3, 0)), "and none at the depth");
    }

    /** A window of a rectangular opening, looked into northwards. */
    private static WindowShape opening(final int wide, final int tall)
    {
        return WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, wide, tall);
    }

    /** A far gate's opening of a size, centred on the arrival's column and standing on its row. */
    private static List<Spot> farOpening(final int wide, final int tall)
    {
        final int low = 100 - ((wide - 1) / 2);
        return farGate(low, (low + wide) - 1, 70, (70 + tall) - 1);
    }

    /**
     * The tunnel is a block longer for each block the far opening is narrower, the more of its width
     * and height: Standard is further down a Massive gate than down a Large one.
     *
     * <p>The shipped openings, by their bounding boxes: Standard five square, Large eight, Massive
     * seventeen, Grand eighteen by seventeen.
     */
    @Test
    void theTunnelIsAsLongAsTheOpeningNarrowsUpToTheLongestAllowed()
    {
        final int longest = 16;
        assertEquals(3, Funnel.of(opening(8, 8), farOpening(5, 5), longest, Material.WATER).taper(), "Large to Standard");
        assertEquals(12, Funnel.of(opening(17, 17), farOpening(5, 5), longest, Material.WATER).taper(), "Massive to Standard");
        assertEquals(13, Funnel.of(opening(18, 17), farOpening(5, 5), longest, Material.WATER).taper(), "Grand to Standard");
        assertEquals(9, Funnel.of(opening(17, 17), farOpening(8, 8), longest, Material.WATER).taper(), "Massive to Large");
        assertEquals(10, Funnel.of(opening(18, 17), farOpening(8, 8), longest, Material.WATER).taper(), "Grand to Large");
        assertEquals(1, Funnel.of(opening(18, 17), farOpening(17, 17), longest, Material.WATER).taper(),
            "Grand to Massive, a block narrower: a tunnel of the opening alone, the view straight behind it");
        assertEquals(8, Funnel.of(opening(17, 17), farOpening(5, 5), 8, Material.WATER).taper(), "no longer than allowed");
        assertNull(Funnel.of(opening(17, 17), farOpening(18, 17), longest, Material.WATER), "Massive to Grand: wider, no tunnel");
        assertEquals(12, Funnel.of(opening(17, 17), farOpening(17, 5), longest, Material.WATER).taper(),
            "as wide but twelve shorter: the height decides");
    }

    @Test
    void theTunnelIsHollowAndTheFarRoomShowsOnlyPastIt()
    {
        final Funnel funnel = toStandard();

        assertEquals(8, funnel.taper(), "eleven square to three by four narrows eight");
        assertTrue(funnel.hollow(1, 0, 0), "the first layer behind the opening, still its whole size");
        assertTrue(funnel.hollow(7, 5, 3), "the far gate's own plane, the tunnel's last layer");
        assertFalse(funnel.hollow(8, 5, 1), "past it is the room");
        assertFalse(funnel.hollow(0, 5, 1), "the opening is the gate's own, never drawn");
        assertFalse(funnel.hollow(3, 0, 10), "outside the narrowing tunnel");
        assertFalse(funnel.shows(7, 5, 1, 0.0), "no far side in the tunnel");
        assertFalse(funnel.shows(1, 5, 1, 0.0));
        assertTrue(funnel.shows(8, 5, 1, 0.0), "the room starts on the layer after the far gate's plane");
        assertTrue(funnel.shows(8, 5, -1, 0.0), "with the ground under it");
        assertFalse(funnel.shows(8, 3, 1, 0.0), "at the far opening's size");
        assertFalse(funnel.shows(30, 5, 4, 0.0), "however deep");
        assertTrue(funnel.shows(7, 5, 1, 1.0), "a stand-in already shown may stand a layer back in the far gate's plane");
        assertFalse(funnel.shows(6, 5, 1, 1.0), "but no further into the tunnel");
    }

    @Test
    void theHollowIsEveryCellOfTheTunnelShortOfTheDepth()
    {
        final Funnel funnel = toStandard();
        final Set<Spot> hollow = new HashSet<>();
        funnel.forEachHollow(20, (layer, across, up) -> hollow.add(new Spot(layer, across, up)));
        final Set<Spot> shallow = new HashSet<>();
        funnel.forEachHollow(4, (layer, across, up) -> shallow.add(new Spot(layer, across, up)));

        assertTrue(hollow.contains(new Spot(1, 0, 0)) && hollow.contains(new Spot(1, 10, 9)), "the first layer's corners");
        assertTrue(hollow.contains(new Spot(7, 4, 0)) && hollow.contains(new Spot(7, 6, 3)), "the last layer's");
        assertEquals(0, hollow.stream().filter(cell -> cell.x() >= 8).count(), "none in the room");
        assertEquals(25, hollow.stream().filter(cell -> cell.x() == 7).count(), "five across by five up on the last layer, a step short of the far size");
        assertTrue(shallow.contains(new Spot(3, 5, 5)), "short of a shallow depth");
        assertFalse(shallow.contains(new Spot(4, 5, 3)), "and none at it");
    }

    @Test
    void aTunnelOfOneLayerIsTheOpeningAloneAndTheRoomStraightBehindIt()
    {
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 70, 73), 1, Material.WATER);
        final List<Spot> hollow = new ArrayList<>();
        funnel.forEachHollow(20, (layer, across, up) -> hollow.add(new Spot(layer, across, up)));

        assertEquals(1, funnel.taper());
        assertTrue(hollow.isEmpty(), "nothing hollow behind the opening");
        assertTrue(funnel.shows(1, 5, 1, 0.0), "the room from the first layer");
        assertFalse(funnel.shows(1, 3, 1, 0.0), "at the far opening's size");
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
     * rows and the block it stands on; the wall runs under that, across the arrival's row.
     */
    @Test
    void aFarGateStandingAboveTheArrivalRowNarrowsToItsOwnRows()
    {
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 72, 75), 8, Material.WATER);

        assertNotNull(funnel);
        assertEquals(new Funnel.Opening(4, 6, 2, 5), funnel.far());
        assertTrue(funnel.holds(20, 5, 2), "its bottom row");
        assertTrue(funnel.holds(20, 5, 1), "the block it stands on");
        assertFalse(funnel.holds(20, 5, 0), "the arrival's row, below that");
        assertTrue(funnel.wall(20, 5, 0), "walled underneath");
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
        assertTrue(funnel.holds(20, 5, -2, 1.0), "below the ground");
        assertFalse(funnel.holds(20, 2, 1, 1.0), "but not two");
        assertFalse(funnel.holds(20, 8, 1, 1.0));
        assertFalse(funnel.holds(20, 5, 5, 1.0));
        assertFalse(funnel.holds(20, 5, -3, 1.0));
    }
}
