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
        assertTrue(walls.contains(new Spot(7, 5, 11)) && walls.contains(new Spot(7, 5, 12)),
            "the ceiling, two rows above the near opening's top, is walled");
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
    void theTunnelNarrowsFromTheOpeningToTheFarOneOnItsLastLayer()
    {
        final Funnel funnel = toStandard();

        assertTrue(funnel.holds(0, 0, 0), "the opening's own corner");
        assertTrue(funnel.holds(0, 10, 10), "and the far one");
        assertFalse(funnel.holds(0, 11, 5), "beside the opening");
        assertFalse(funnel.holds(0, 5, -1), "below it");
        // Layer 4 of a tunnel whose last layer is 7: left 2.3, right 7.7, top 6.
        assertTrue(funnel.holds(4, 2, 6), "the narrowing edges, still in");
        assertTrue(funnel.holds(4, 8, 6));
        assertFalse(funnel.holds(4, 1, 0), "past the left edge");
        assertFalse(funnel.holds(4, 9, 0), "past the right edge");
        assertFalse(funnel.holds(4, 5, 7), "past the ceiling");
        assertTrue(funnel.holds(7, 4, 0), "the far gate's plane is the far opening exactly");
        assertTrue(funnel.holds(7, 6, 3));
        assertFalse(funnel.holds(7, 3, 1));
        assertFalse(funnel.holds(7, 7, 1));
        assertFalse(funnel.holds(7, 5, 4));
        assertFalse(funnel.holds(7, 5, -1));
    }

    /**
     * Past the tunnel the room widens again, as far as the near opening sees through the far one, and
     * no further: the halls and ceiling round the small gate stay out.
     */
    @Test
    void theRoomPastTheTunnelIsWhatTheNearOpeningSeesThroughTheFarOne()
    {
        final Funnel funnel = toStandard();

        assertTrue(funnel.holds(8, 3, 0), "the first layer of the room: columns 3 to 7");
        assertTrue(funnel.holds(8, 7, 0));
        assertFalse(funnel.holds(8, 2, 0), "a hall beside the small gate");
        assertFalse(funnel.holds(8, 8, 0));
        assertTrue(funnel.holds(8, 5, 4), "rows 4 down to -2: what can be seen up and down through it");
        assertTrue(funnel.holds(8, 5, -2));
        assertFalse(funnel.holds(8, 5, 5), "above the small gate's ceiling");
        assertFalse(funnel.holds(8, 5, -3));
        assertTrue(funnel.holds(12, -1, 0), "wider further in, past the near opening's own edges");
        assertTrue(funnel.holds(12, 11, 0));
        assertFalse(funnel.holds(12, -2, 0));
        assertFalse(funnel.holds(12, 12, 0));
        // At layer 15 the widest line reaches exactly -3.5, the edge between columns -4 and -3.
        assertTrue(funnel.holds(15, -3, 0));
        assertFalse(funnel.holds(15, -4, 0), "a line that only grazes a cell's edge does not draw it");
    }

    /**
     * Every cell on a line through the near opening and the far one, past the far gate's plane, is in
     * the room: nothing a viewer could see through the small gate is left out. A line is inside each
     * opening at both its faces, since each is a block deep with its frame or the tunnel's wall round it.
     */
    @Test
    void everyLineThroughBothOpeningsStaysInTheRoom()
    {
        final Funnel funnel = toStandard();
        int lines = 0;
        for (double near = -0.5; near <= 10.5; near += 0.5)
        {
            for (double far = 3.5; far <= 6.5; far += 0.25)
            {
                lines += lineStaysIn(funnel, near, far, 3.5, 6.5, true);
            }
            for (double far = -0.5; far <= 3.5; far += 0.25)
            {
                lines += lineStaysIn(funnel, near, far, -0.5, 3.5, false);
            }
        }
        assertTrue(lines > 1000, lines + " cells along the lines");
    }

    /**
     * Walks the line from a point of the near opening's front face through a point of the far plane's
     * back face, across or up, if it is inside the far opening at its front face too, and checks every
     * cell it crosses in the room.
     *
     * @return how many cells were checked
     */
    private static int lineStaysIn(final Funnel funnel, final double near, final double far, final double low,
        final double high, final boolean across)
    {
        final double slope = (far - near) / 8.0;
        final double atFrontOfPlane = near + (slope * 7.0);
        if ((atFrontOfPlane < low) || (atFrontOfPlane > high))
        {
            return 0;
        }
        int checked = 0;
        for (int layer = 8; layer <= 24; layer++)
        {
            for (final double depth : new double[] { layer - 0.49, layer, layer + 0.49 })
            {
                final double at = near + (slope * (depth + 0.5));
                final double cell = Math.floor(at + 0.5);
                if (Math.abs((at + 0.5) - cell) > 1.0e-9)
                {
                    final int index = (int) cell;
                    assertTrue(across ? funnel.holds(layer, index, 1) : funnel.holds(layer, 5, index),
                        "layer " + layer + ", " + (across ? "column " : "row ") + index + ", from " + near + " through " + far);
                    checked++;
                }
            }
        }
        return checked;
    }

    @Test
    void theWallIsTwoBlocksThickRoundTheTunnelCornersIncludedAndEndsWithIt()
    {
        final Funnel funnel = toStandard();

        assertTrue(funnel.wall(7, 3, 1) && funnel.wall(7, 2, 1), "two thick left of the far opening, its collar");
        assertTrue(funnel.wall(7, 7, 1) && funnel.wall(7, 8, 1), "and right");
        assertTrue(funnel.wall(7, 5, 4) && funnel.wall(7, 5, 5), "and above");
        assertTrue(funnel.wall(7, 5, -1) && funnel.wall(7, 5, -2), "and under: the tunnel's floor");
        assertTrue(funnel.wall(7, 2, -2) && funnel.wall(7, 8, 5), "and its corners, so no step leaves a gap");
        assertFalse(funnel.wall(7, 1, 1), "three out is behind the wall");
        assertFalse(funnel.wall(7, 5, 6));
        assertFalse(funnel.wall(7, 5, -3));
        assertFalse(funnel.wall(7, 5, 1), "inside is hollow");
        assertFalse(funnel.wall(8, 1, 0), "no wall round the room past the tunnel");
        assertFalse(funnel.wall(12, -3, 0));
        assertFalse(funnel.wall(0, -1, 0), "nor in the opening's own layer");
    }

    @Test
    void aStepInOfSeveralColumnsIsWalledAcrossItsWholeFace()
    {
        // A tunnel of three: the opening, then columns 2 to 8, then the far opening's 4 to 6.
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 70, 73), 3, Material.WATER);

        assertTrue(funnel.holds(1, 2, 0) && !funnel.holds(1, 1, 0));
        assertTrue(funnel.wall(2, 2, 0) && funnel.wall(2, 3, 0), "inside the layer before, two columns over");
        assertTrue(funnel.wall(2, 0, 0), "inside two layers before, four columns over: the wall's second block in depth");
        assertFalse(funnel.wall(2, -1, 0), "outside the opening and the layers before");
    }

    @Test
    void theWallIsWalkedOnEveryLayerOfTheTunnelAndNoFurther()
    {
        final Funnel funnel = toStandard();
        final Set<Spot> walls = new HashSet<>();
        funnel.forEachWall(20, (layer, across, up) -> walls.add(new Spot(layer, across, up)));
        final Funnel lower = Funnel.of(BIG, farGate(95, 105, 70, 73), 8, Material.WATER);
        final Set<Spot> wide = new HashSet<>();
        lower.forEachWall(20, (layer, across, up) -> wide.add(new Spot(layer, across, up)));

        assertTrue(walls.contains(new Spot(1, 0, 0)) && walls.contains(new Spot(1, -1, 0)), "beside the first layer");
        assertTrue(walls.contains(new Spot(7, 2, -2)), "a corner of the collar");
        assertTrue(walls.contains(new Spot(1, 5, 11)), "two above the first layer's top row");
        assertEquals(0, walls.stream().filter(cell -> (cell.x() < 1) || (cell.x() > 7)).count(), "only in the tunnel");
        assertEquals(walls.size(), walls.stream().filter(cell -> funnel.wall(cell.x(), cell.y(), cell.z())).count());
        assertTrue(wide.contains(new Spot(3, -2, 0)) && wide.contains(new Spot(3, 12, 0)),
            "two out beside a tunnel the near opening's own width");
        final Set<Spot> shallow = new HashSet<>();
        funnel.forEachWall(3, (layer, across, up) -> shallow.add(new Spot(layer, across, up)));
        assertTrue(shallow.contains(new Spot(2, 0, 0)) && !shallow.contains(new Spot(3, 1, 0)), "and short of the depth");
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
        assertTrue(funnel.hollow(1, 1, 0), "the first layer behind the opening");
        assertFalse(funnel.hollow(1, 0, 0), "a step in from it already");
        assertTrue(funnel.hollow(7, 5, 3), "the far gate's own plane, the tunnel's last layer");
        assertFalse(funnel.hollow(8, 5, 1), "past it is the room");
        assertFalse(funnel.hollow(0, 5, 1), "the opening is the gate's own, never drawn");
        assertFalse(funnel.hollow(3, 0, 10), "outside the narrowing tunnel");
        assertFalse(funnel.shows(7, 5, 1, 0.0), "no far side in the tunnel");
        assertFalse(funnel.shows(1, 5, 1, 0.0));
        assertTrue(funnel.shows(8, 5, 1, 0.0), "the room starts on the layer after the far gate's plane");
        assertTrue(funnel.shows(8, 5, -1, 0.0), "the ground under it");
        assertFalse(funnel.shows(8, 1, 1, 0.0), "the hall beside it");
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

        assertTrue(hollow.contains(new Spot(1, 1, 0)) && hollow.contains(new Spot(1, 9, 9)), "the first layer's corners");
        assertTrue(hollow.contains(new Spot(7, 4, 0)) && hollow.contains(new Spot(7, 6, 3)), "the last layer's");
        assertEquals(0, hollow.stream().filter(cell -> cell.x() >= 8).count(), "none in the room");
        assertEquals(12, hollow.stream().filter(cell -> cell.x() == 7).count(), "three by four on the last layer, the far opening");
        assertTrue(shallow.contains(new Spot(3, 5, 7)), "short of a shallow depth");
        assertFalse(shallow.contains(new Spot(4, 5, 3)), "and none at it");
    }

    @Test
    void aTunnelOfOneLayerIsThePlainView()
    {
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 70, 73), 1, Material.WATER);
        final List<Spot> drawn = new ArrayList<>();
        funnel.forEachHollow(20, (layer, across, up) -> drawn.add(new Spot(layer, across, up)));
        funnel.forEachWall(20, (layer, across, up) -> drawn.add(new Spot(layer, across, up)));

        assertEquals(1, funnel.taper());
        assertTrue(drawn.isEmpty(), "nothing hollow or walled behind the opening");
        assertTrue(funnel.shows(1, 5, 1, 0.0), "the room from the first layer");
        assertTrue(funnel.shows(1, -5, 30, 0.0), "all of it: no far opening stands in front of it");
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
     * rows; the wall runs under them, and through it the room shows down to the arrival's row.
     */
    @Test
    void aFarGateStandingAboveTheArrivalRowNarrowsToItsOwnRows()
    {
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 72, 75), 8, Material.WATER);

        assertNotNull(funnel);
        assertEquals(new Funnel.Opening(4, 6, 2, 5), funnel.far());
        assertTrue(funnel.holds(7, 5, 2), "its bottom row");
        assertFalse(funnel.holds(7, 5, 1), "the block it stands on");
        assertTrue(funnel.wall(7, 5, 1) && funnel.wall(7, 5, 0), "walled two deep underneath");
        assertTrue(funnel.holds(8, 5, 0), "looking down through it, the arrival's row in front of it");
        assertFalse(funnel.holds(8, 5, -1));
    }

    /** A stand-in already shown may stand a block past the room's edge, so one on it does not flicker. */
    @Test
    void slackWidensEveryEdgeByThatMuch()
    {
        final Funnel funnel = toStandard();

        assertFalse(funnel.holds(8, 2, 0), "a column left of the room's first layer");
        assertTrue(funnel.holds(8, 2, 0, 1.0), "held: a block out on the left");
        assertTrue(funnel.holds(8, 8, 0, 1.0), "on the right");
        assertTrue(funnel.holds(8, 5, 5, 1.0), "above");
        assertTrue(funnel.holds(8, 5, -3, 1.0), "below");
        assertFalse(funnel.holds(8, 1, 0, 1.0), "but not two");
        assertFalse(funnel.holds(8, 9, 0, 1.0));
        assertFalse(funnel.holds(8, 5, 6, 1.0));
        assertFalse(funnel.holds(8, 5, -4, 1.0));
        assertTrue(funnel.holds(7, 3, 1, 1.0) && !funnel.holds(7, 2, 1, 1.0), "and in the far gate's plane");
        assertTrue(funnel.holds(7, 5, -1, 1.0) && funnel.holds(7, 5, 4, 1.0) && funnel.holds(7, 7, 1, 1.0));
    }
}
