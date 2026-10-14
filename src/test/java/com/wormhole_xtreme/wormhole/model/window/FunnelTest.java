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
        return Funnel.of(BIG, farGate(99, 101, 70, 73), 16, Material.WATER);
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
        assertNull(Funnel.of(BIG, farGate(95, 105, 70, 80), 16, Material.WATER), "the same size");
        assertNull(Funnel.of(BIG, farGate(90, 110, 69, 85), 16, Material.WATER), "bigger: small to large is unchanged");
    }

    @Test
    void aFarGateNarrowerOnlyOneWayNarrowsOnlyThatWay()
    {
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 70, 85), 16, Material.WATER);

        assertNotNull(funnel);
        assertEquals(4, funnel.far().left());
        assertEquals(6, funnel.far().right());
        assertEquals(0, funnel.far().bottom());
        assertEquals(10, funnel.far().top(), "taller than the near gate: the near one's height, never past it");
        final Set<Spot> walls = new HashSet<>();
        funnel.forEachWall(20, (layer, across, up) -> walls.add(new Spot(layer, across, up)));
        assertTrue(walls.contains(new Spot(8, 5, 11)) && walls.contains(new Spot(8, 5, 12)),
            "the ceiling, two rows above the near opening's top, is walled");
        final Set<Spot> hollow = new HashSet<>();
        funnel.forEachHollow(20, (layer, across, up) -> hollow.add(new Spot(layer, across, up)));
        assertTrue(hollow.contains(new Spot(3, 5, 10)), "the tunnel's top row is the near opening's, hollow");

        final Funnel lower = Funnel.of(BIG, farGate(95, 105, 70, 73), 16, Material.WATER);
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
        assertNull(Funnel.of(BIG, List.of(), 16, Material.WATER));
        assertNull(Funnel.of(BIG, null, 16, Material.WATER));
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

        assertNull(Funnel.of(BIG, flat, 16, Material.WATER),
            "its cells run along the view, not across it, though they line up with the opening's second row");
    }

    @Test
    void aFarGateWhollyBesideTheViewLeavesItWhole()
    {
        assertNull(Funnel.of(BIG, farGate(80, 82, 70, 73), 16, Material.WATER), "nothing of it lines up with the opening");
        assertNull(Funnel.of(BIG, farGate(99, 101, 90, 93), 16, Material.WATER), "all of it above the opening");
    }

    @Test
    void theTunnelNarrowsFromTheOpeningToTheFarOneOnItsLastLayer()
    {
        final Funnel funnel = toStandard();

        assertTrue(funnel.holds(0, 0, 0), "the opening's own corner");
        assertTrue(funnel.holds(0, 10, 10), "and the far one");
        assertFalse(funnel.holds(0, 11, 5), "beside the opening");
        assertFalse(funnel.holds(0, 5, -1), "below it");
        assertTrue(funnel.holds(1, 0, 0) && funnel.holds(1, 10, 10), "the layer behind it is its size still");
        assertFalse(funnel.holds(1, 11, 5) || funnel.holds(1, 5, -1));
        // Layer 5 of a tunnel narrowing from layer 1 to its last, 8: left 2.3, right 7.7, top 6.
        assertTrue(funnel.holds(5, 2, 6), "the narrowing edges, still in");
        assertTrue(funnel.holds(5, 8, 6));
        assertFalse(funnel.holds(5, 1, 0), "past the left edge");
        assertFalse(funnel.holds(5, 9, 0), "past the right edge");
        assertFalse(funnel.holds(5, 5, 7), "past the ceiling");
        assertTrue(funnel.holds(8, 4, 0), "the far gate's plane is the far opening exactly");
        assertTrue(funnel.holds(8, 6, 3));
        assertFalse(funnel.holds(8, 3, 1));
        assertFalse(funnel.holds(8, 7, 1));
        assertFalse(funnel.holds(8, 5, 4));
        assertFalse(funnel.holds(8, 5, -1));
    }

    /**
     * Past the tunnel the room widens again, as far as the near opening sees through the far one, and
     * no further: the halls and ceiling round the small gate stay out.
     */
    @Test
    void theRoomPastTheTunnelIsWhatTheNearOpeningSeesThroughTheFarOne()
    {
        final Funnel funnel = toStandard();

        assertTrue(funnel.holds(9, 3, 0), "the first layer of the room: columns 3 to 7");
        assertTrue(funnel.holds(9, 7, 0));
        assertFalse(funnel.holds(9, 2, 0), "a hall beside the small gate");
        assertFalse(funnel.holds(9, 8, 0));
        assertTrue(funnel.holds(9, 5, 4), "rows 4 down to -2: what can be seen up and down through it");
        assertTrue(funnel.holds(9, 5, -2));
        assertFalse(funnel.holds(9, 5, 5), "above the small gate's ceiling");
        assertFalse(funnel.holds(9, 5, -3));
        assertTrue(funnel.holds(14, -1, 0), "wider further in, past the near opening's own edges");
        assertTrue(funnel.holds(14, 11, 0));
        assertFalse(funnel.holds(14, -2, 0));
        assertFalse(funnel.holds(14, 12, 0));
        // At layer 17 the widest line reaches exactly -3.5, the edge between columns -4 and -3.
        assertTrue(funnel.holds(17, -3, 0));
        assertFalse(funnel.holds(17, -4, 0), "a line that only grazes a cell's edge does not draw it");
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
        // The far plane is layer 8: its front face 7.5, its back face 8.5, nine from the near front face.
        final double slope = (far - near) / 9.0;
        final double atFrontOfPlane = near + (slope * 8.0);
        if ((atFrontOfPlane < low) || (atFrontOfPlane > high))
        {
            return 0;
        }
        int checked = 0;
        for (int layer = 9; layer <= 25; layer++)
        {
            for (final double depth : new double[] { layer - 0.49, layer, layer + 0.49 })
            {
                checked += cellIsIn(funnel, layer, near + (slope * (depth + 0.5)), across,
                    "from " + near + " through " + far);
            }
        }
        return checked;
    }

    /**
     * Checks the cell a line crosses at a point is in the room, unless the point is on the edge
     * between two cells.
     *
     * @return 1 if a cell was checked, 0 if not
     */
    private static int cellIsIn(final Funnel funnel, final int layer, final double at, final boolean across,
        final String line)
    {
        final double cell = Math.floor(at + 0.5);
        if (Math.abs((at + 0.5) - cell) <= 1.0e-9)
        {
            return 0;
        }
        final int index = (int) cell;
        assertTrue(across ? funnel.holds(layer, index, 1) : funnel.holds(layer, 5, index),
            "layer " + layer + ", " + (across ? "column " : "row ") + index + ", " + line);
        return 1;
    }

    /**
     * A short tunnel for how much it narrows, Massive to Standard at a length of two, shows past the far
     * gate's plane exactly the cells some line through both openings reaches, and none other.
     *
     * <p>The far opening is a block deep: a line leaving its back face at one edge cannot have entered
     * its front face past the other. Spread only as the near opening allowed, the room past a short
     * tunnel was wider than the far opening can show.
     */
    @Test
    void aShortTunnelShowsExactlyWhatSomeLineThroughBothOpeningsReaches()
    {
        final Funnel funnel = Funnel.of(opening(17, 17), farOpening(5, 5), 2, Material.WATER);

        assertEquals(new Funnel.Opening(6, 10, 0, 4), funnel.far());
        assertEquals(2, funnel.taper());
        assertTrue(funnel.holds(2, 1, 2), "five a layer at most, the far opening's width: from 5.5 to 0.5");
        assertFalse(funnel.holds(2, 0, 2), "not the five and a half the near opening alone would allow");
        assertEquals(seenByLines(-0.5, 16.5, 5.5, 10.5), heldAlong(funnel, true, 2), "across");
        assertEquals(seenByLines(-0.5, 16.5, -0.5, 4.5), heldAlong(funnel, false, 8), "up");

        // Two tall, ten above the arrival row, taken as the least aperture's three: looking up through it,
        // only its height bounds the spread.
        final Funnel raised = Funnel.of(opening(17, 17), farGate(98, 102, 80, 81), 2, Material.WATER);
        assertEquals(new Funnel.Opening(6, 10, 10, 12), raised.far());
        assertEquals(seenByLines(-0.5, 16.5, 9.5, 12.5), heldAlong(raised, false, 8), "up through a short, raised far gate");
        assertEquals(seenByLines(-0.5, 16.5, 5.5, 10.5), heldAlong(raised, true, 10), "across it");
    }

    /** The cells of layers 2 to 10, each as layer times a thousand plus index, that the room holds along one axis. */
    private static Set<Integer> heldAlong(final Funnel funnel, final boolean across, final int other)
    {
        final Set<Integer> held = new HashSet<>();
        for (int layer = 2; layer <= 10; layer++)
        {
            for (int index = -80; index <= 100; index++)
            {
                if (across ? funnel.holds(layer, index, other) : funnel.holds(layer, other, index))
                {
                    held.add((layer * 1000) + index);
                }
            }
        }
        return held;
    }

    /**
     * The cells of layers 2 to 10 crossed by lines inside a near opening at both its faces, -0.5 and
     * 0.5, and inside a far one a block deep at both of its, 0.5 and 1.5, sampled every quarter block.
     */
    private static Set<Integer> seenByLines(final double nearLow, final double nearHigh, final double farLow,
        final double farHigh)
    {
        final Set<Integer> seen = new HashSet<>();
        for (double front = nearLow; front <= nearHigh; front += 0.25)
        {
            for (double back = farLow; back <= farHigh; back += 0.25)
            {
                final double slope = (back - front) / 2.0;
                final double between = front + slope;
                if ((between >= Math.max(nearLow, farLow)) && (between <= Math.min(nearHigh, farHigh)))
                {
                    markCrossed(seen, back, slope);
                }
            }
        }
        return seen;
    }

    /** Marks the cells a line crosses in layers 2 to 10, given where it leaves the far opening's back face. */
    private static void markCrossed(final Set<Integer> seen, final double back, final double slope)
    {
        for (int layer = 2; layer <= 10; layer++)
        {
            final double one = back + (slope * (layer - 2.0));
            final double other = back + (slope * (layer - 1.0));
            final double low = Math.min(one, other);
            final double high = Math.max(one, other);
            for (int index = (int) Math.floor(low - 1.0); index <= (int) Math.ceil(high + 1.0); index++)
            {
                if (((index + 0.5) > low) && ((index - 0.5) < high))
                {
                    seen.add((layer * 1000) + index);
                }
            }
        }
    }

    @Test
    void theWallIsTwoBlocksThickRoundTheTunnelCornersIncludedAndEndsWithIt()
    {
        final Funnel funnel = toStandard();

        assertTrue(funnel.wall(8, 3, 1) && funnel.wall(8, 2, 1), "two thick left of the far opening, its collar");
        assertTrue(funnel.wall(8, 7, 1) && funnel.wall(8, 8, 1), "and right");
        assertTrue(funnel.wall(8, 5, 4) && funnel.wall(8, 5, 5), "and above");
        assertTrue(funnel.wall(8, 5, -1) && funnel.wall(8, 5, -2), "and under: the tunnel's floor");
        assertTrue(funnel.wall(8, 2, -2) && funnel.wall(8, 8, 5), "and its corners, so no step leaves a gap");
        assertFalse(funnel.wall(8, 1, 1), "three out is behind the wall");
        assertFalse(funnel.wall(8, 5, 6));
        assertFalse(funnel.wall(8, 5, -3));
        assertFalse(funnel.wall(8, 5, 1), "inside is hollow");
        assertFalse(funnel.wall(9, 1, 0), "no wall round the room past the tunnel");
        assertFalse(funnel.wall(14, -3, 0));
        assertFalse(funnel.wall(0, -1, 0), "nor in the opening's own layer");
    }

    /** A cell whose edge the narrowing box meets exactly is in, on every side, as half a block always rounded in. */
    @Test
    void aCellTheNarrowingEdgeMeetsExactlyIsIn()
    {
        // Columns 3 to 7 and rows 0 to 3 at a length of four: layer 2's box runs from 1.5 to 8.5 and up to 6.5.
        final Funnel funnel = Funnel.of(BIG, farGate(98, 102, 70, 73), 4, Material.WATER);

        assertTrue(funnel.holds(2, 1, 0) && funnel.holds(2, 9, 0), "left and right, met at their outer edge");
        assertTrue(funnel.holds(2, 5, 7), "the top, met at its upper edge");
        assertFalse(funnel.holds(2, 0, 0) || funnel.holds(2, 10, 0) || funnel.holds(2, 5, 8), "and nothing past them");
    }

    @Test
    void aStepInOfSeveralColumnsIsWalledAcrossItsWholeFace()
    {
        // A tunnel of four: the opening, its size again, then columns 2 to 8, then the far opening's 4 to 6.
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 70, 73), 4, Material.WATER);

        assertTrue(funnel.holds(2, 2, 0) && !funnel.holds(2, 1, 0));
        assertTrue(funnel.wall(3, 2, 0) && funnel.wall(3, 3, 0), "inside the layer before, two columns over");
        assertTrue(funnel.wall(3, 0, 0), "inside two layers before, four columns over: the wall's second block in depth");
        assertFalse(funnel.wall(3, -1, 0), "outside the opening and the layers before");
    }

    @Test
    void theWallIsWalkedOnEveryLayerOfTheTunnelAndNoFurther()
    {
        final Funnel funnel = toStandard();
        final Set<Spot> walls = new HashSet<>();
        funnel.forEachWall(20, (layer, across, up) -> walls.add(new Spot(layer, across, up)));
        final Funnel lower = Funnel.of(BIG, farGate(95, 105, 70, 73), 16, Material.WATER);
        final Set<Spot> wide = new HashSet<>();
        lower.forEachWall(20, (layer, across, up) -> wide.add(new Spot(layer, across, up)));

        assertTrue(walls.contains(new Spot(1, -1, 0)) && walls.contains(new Spot(1, -2, 0)), "beside the first layer");
        assertTrue(walls.contains(new Spot(2, 0, 0)), "and where the second steps in from it");
        assertTrue(walls.contains(new Spot(8, 2, -2)), "a corner of the collar");
        assertTrue(walls.contains(new Spot(1, 5, 11)), "two above the first layer's top row");
        assertEquals(0, walls.stream().filter(cell -> (cell.x() < 1) || (cell.x() > 8)).count(), "only in the tunnel");
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
        assertEquals(4, Funnel.of(opening(8, 8), farOpening(5, 5), longest, Material.WATER).taper(), "Large to Standard");
        assertEquals(13, Funnel.of(opening(17, 17), farOpening(5, 5), longest, Material.WATER).taper(), "Massive to Standard");
        assertEquals(14, Funnel.of(opening(18, 17), farOpening(5, 5), longest, Material.WATER).taper(), "Grand to Standard");
        assertEquals(10, Funnel.of(opening(17, 17), farOpening(8, 8), longest, Material.WATER).taper(), "Massive to Large");
        assertEquals(11, Funnel.of(opening(18, 17), farOpening(8, 8), longest, Material.WATER).taper(), "Grand to Large");
        assertEquals(2, Funnel.of(opening(18, 17), farOpening(17, 17), longest, Material.WATER).taper(),
            "Grand to Massive, a block narrower: the opening, then the far plane straight behind it");
        assertEquals(8, Funnel.of(opening(17, 17), farOpening(5, 5), 8, Material.WATER).taper(), "no longer than allowed");
        assertNull(Funnel.of(opening(17, 17), farOpening(18, 17), longest, Material.WATER), "Massive to Grand: wider, no tunnel");
        assertEquals(13, Funnel.of(opening(17, 17), farOpening(17, 5), longest, Material.WATER).taper(),
            "as wide but twelve shorter: the height decides");
    }

    @Test
    void theTunnelIsHollowAndTheFarRoomShowsOnlyPastIt()
    {
        final Funnel funnel = toStandard();

        assertEquals(9, funnel.taper(), "eleven square to three by four narrows eight, behind a layer its own size");
        assertTrue(funnel.hollow(1, 0, 0), "the first layer behind the opening, the opening's size still");
        assertFalse(funnel.hollow(2, 0, 0), "a step in from it on the next");
        assertTrue(funnel.hollow(8, 5, 3), "the far gate's own plane, the tunnel's last layer");
        assertFalse(funnel.hollow(9, 5, 1), "past it is the room");
        assertFalse(funnel.hollow(0, 5, 1), "the opening is the gate's own, never drawn");
        assertFalse(funnel.hollow(4, 0, 10), "outside the narrowing tunnel");
        assertFalse(funnel.shows(8, 5, 1, 0.0), "no far side in the tunnel");
        assertFalse(funnel.shows(1, 5, 1, 0.0));
        assertTrue(funnel.shows(9, 5, 1, 0.0), "the room starts on the layer after the far gate's plane");
        assertTrue(funnel.shows(9, 5, -1, 0.0), "the ground under it");
        assertFalse(funnel.shows(9, 1, 1, 0.0), "the hall beside it");
        assertTrue(funnel.shows(8, 5, 1, 1.0), "a stand-in already shown may stand a layer back in the far gate's plane");
        assertFalse(funnel.shows(7, 5, 1, 1.0), "but no further into the tunnel");
    }

    @Test
    void theHollowIsEveryCellOfTheTunnelShortOfTheDepth()
    {
        final Funnel funnel = toStandard();
        final Set<Spot> hollow = new HashSet<>();
        funnel.forEachHollow(20, (layer, across, up) -> hollow.add(new Spot(layer, across, up)));
        final Set<Spot> shallow = new HashSet<>();
        funnel.forEachHollow(5, (layer, across, up) -> shallow.add(new Spot(layer, across, up)));

        assertTrue(hollow.contains(new Spot(1, 0, 0)) && hollow.contains(new Spot(1, 10, 10)), "the first layer's corners, the opening's");
        assertTrue(hollow.contains(new Spot(2, 1, 0)) && hollow.contains(new Spot(2, 9, 9)), "the second's, a step in");
        assertTrue(hollow.contains(new Spot(8, 4, 0)) && hollow.contains(new Spot(8, 6, 3)), "the last layer's");
        assertEquals(0, hollow.stream().filter(cell -> cell.x() >= 9).count(), "none in the room");
        assertEquals(121, hollow.stream().filter(cell -> cell.x() == 1).count(), "eleven square on the first layer");
        assertEquals(12, hollow.stream().filter(cell -> cell.x() == 8).count(), "three by four on the last layer, the far opening");
        assertTrue(shallow.contains(new Spot(4, 5, 7)), "short of a shallow depth");
        assertFalse(shallow.contains(new Spot(5, 5, 3)), "and none at it");
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
        assertTrue(funnel.shows(1, 5, 1, 1.0), "a stand-in already shown, too");
        assertFalse(funnel.shows(0, 5, 1, 1.0), "but never in the opening itself, even held");
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
                final Funnel funnel = Funnel.of(shape, farGateFacing(arrival), 16, Material.WATER);
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
        final Funnel funnel = Funnel.of(BIG, farGate(99, 101, 72, 75), 16, Material.WATER);

        assertNotNull(funnel);
        assertEquals(new Funnel.Opening(4, 6, 2, 5), funnel.far());
        assertTrue(funnel.holds(8, 5, 2), "its bottom row");
        assertFalse(funnel.holds(8, 5, 1), "the block it stands on");
        assertTrue(funnel.wall(8, 5, 1) && funnel.wall(8, 5, 0), "walled two deep underneath");
        assertTrue(funnel.holds(10, 5, 0), "looking down through it, a layer in, the arrival's row in front of it");
        assertFalse(funnel.holds(10, 5, -1));
    }

    /** A stand-in already shown may stand a block past the room's edge, so one on it does not flicker. */
    @Test
    void slackWidensEveryEdgeByThatMuch()
    {
        final Funnel funnel = toStandard();

        assertFalse(funnel.holds(9, 2, 0), "a column left of the room's first layer");
        assertTrue(funnel.holds(9, 2, 0, 1.0), "held: a block out on the left");
        assertTrue(funnel.holds(9, 8, 0, 1.0), "on the right");
        assertTrue(funnel.holds(9, 5, 5, 1.0), "above");
        assertTrue(funnel.holds(9, 5, -3, 1.0), "below");
        assertFalse(funnel.holds(9, 1, 0, 1.0), "but not two");
        assertFalse(funnel.holds(9, 9, 0, 1.0));
        assertFalse(funnel.holds(9, 5, 6, 1.0));
        assertFalse(funnel.holds(9, 5, -4, 1.0));
        assertTrue(funnel.holds(8, 5, 1, 1.0), "in the far gate's plane, its opening");
        assertFalse(funnel.holds(8, 3, 1, 1.0) || funnel.holds(8, 5, -1, 1.0) || funnel.holds(8, 5, 4, 1.0),
            "but never its frame, the collar, held or not");
    }

    /** The same-size far gate, eleven square, at a least length of three (#587). */
    private static Funnel straightTube()
    {
        return Funnel.of(BIG, null, farGate(95, 105, 70, 80), 16, 3, Material.WATER);
    }

    /**
     * {@code gate-view-funnel-min-length: 0}, as it ships, changes nothing: every pair is the funnel it
     * was before the setting existed, and a pair that does not narrow is still the plain view (#587).
     */
    @Test
    void noLeastLengthLeavesEveryTunnelAsItWas()
    {
        assertEquals(toStandard(), Funnel.of(BIG, null, farGate(99, 101, 70, 73), 16, 0, Material.WATER));
        assertEquals(9, Funnel.of(BIG, null, farGate(99, 101, 70, 73), 16, 0, Material.WATER).taper(),
            "a block a layer narrowed, and one for the layer its own size");
        assertNull(Funnel.of(BIG, null, farGate(95, 105, 70, 80), 16, 0, Material.WATER), "the same size: plain");
        assertNull(Funnel.of(BIG, null, farGate(90, 110, 69, 85), 16, 0, Material.WATER), "small to large: plain");
    }

    /**
     * With a least length, a gate the same size is seen down a straight tube: every layer of it the near
     * opening's whole shape, its last too, walled two blocks thick, and the room past it (#587).
     *
     * <p>Seven long, three fifths of eleven, longer than the least of three.
     */
    @Test
    void aLeastLengthSeesAGateTheSameSizeDownAStraightTube()
    {
        final Funnel funnel = straightTube();
        final List<Spot> hollow = new ArrayList<>();
        funnel.forEachHollow(20, (layer, across, up) -> hollow.add(new Spot(layer, across, up)));

        assertNotNull(funnel, "no longer the plain view");
        assertEquals(7, funnel.taper());
        assertEquals(new Funnel.Opening(0, 10, 0, 10), funnel.far(), "the far opening is the near one's whole size");
        assertEquals(6 * 121, hollow.size(), "six layers behind the opening, each all eleven square");
        assertTrue(funnel.hollow(6, 0, 0) && funnel.hollow(6, 10, 10), "the last layer's corners too: no narrowing");
        assertTrue(funnel.wall(1, -1, 5) && funnel.wall(1, -2, 5), "two thick beside the first layer");
        assertFalse(funnel.wall(1, -3, 5), "and no thicker");
        assertTrue(funnel.wall(6, 12, 12), "round its last layer, corners included");
        assertFalse(funnel.wall(7, -1, 5), "none round the room");
        assertFalse(funnel.shows(6, 5, 0, 0.0), "no far side in the tube");
        assertTrue(funnel.shows(7, 5, 0, 0.0), "the room on the layer after it");
    }

    /**
     * A small gate dialling a big one gets the same tube, at its own opening's size: its view can be no
     * wider than the gate it is seen through (#587).
     */
    @Test
    void aSmallGateOntoABigOneIsTheSameTubeAtItsOwnSize()
    {
        assertEquals(straightTube(), Funnel.of(BIG, null, farGate(90, 110, 69, 85), 16, 3, Material.WATER),
            "a far gate twenty-one by seventeen, cut to the near eleven square");
    }

    /**
     * Past a straight tube, the room is exactly what some line through both its ends reaches: the far
     * side shifted to the tube's end, seen through an opening the near one's size (#587).
     */
    @Test
    void theRoomPastAStraightTubeIsWhatSomeLineThroughBothEndsReaches()
    {
        final Funnel funnel = Funnel.of(opening(17, 17), null, farOpening(17, 17), 2, 3, Material.WATER);

        assertEquals(2, funnel.taper(), "cut to the longest allowed");
        assertEquals(seenByLines(-0.5, 16.5, -0.5, 16.5), heldAlong(funnel, true, 2), "across");
        assertEquals(seenByLines(-0.5, 16.5, -0.5, 16.5), heldAlong(funnel, false, 8), "up");
    }

    /**
     * The tunnel runs straight for the least length, or three fifths of the near opening's longer side
     * if that is longer, then a block longer for each it narrows; at most the longest allowed (#587).
     *
     * <p>So a pair of Large gates is further down than a pair of Standard ones, and a Large onto a
     * Standard further still, since it narrows too.
     */
    @Test
    void theStraightRunIsTheLeastOrThreeFifthsOfTheOpeningThenItNarrows()
    {
        assertEquals(3, Funnel.length(0, 5, 16, 3), "Standard pair: three fifths of five, three");
        assertEquals(4, Funnel.length(0, 5, 16, 4), "a least longer than that wins");
        assertEquals(5, Funnel.length(0, 8, 16, 3), "Large pair: four point eight, to the nearest");
        assertEquals(8, Funnel.length(3, 8, 16, 3), "Large to Standard: five, then three more narrowing");
        assertEquals(10, Funnel.length(0, 17, 16, 3), "Massive pair: ten point two");
        assertEquals(16, Funnel.length(12, 17, 16, 3), "Massive to Standard: twenty-two, cut to the longest");
        assertEquals(2, Funnel.length(0, 5, 2, 3), "never past the longest, the least included");
        assertEquals(4, Funnel.length(3, 8, 16, 0), "no least: a block more than it narrows, as before");
        assertEquals(15, Funnel.of(BIG, null, farGate(99, 101, 70, 73), 16, 3, Material.WATER).taper(),
            "eleven square to three by four: seven straight, then eight narrowing");
    }

    /**
     * A far gate as wide but lower, with a least length: the straight run at the near opening's whole
     * size, then only the ceiling comes down, a block a layer, the width kept all the way (#587).
     */
    @Test
    void asWideButLowerRunsStraightThenOnlyTheCeilingComesDown()
    {
        final Funnel funnel = Funnel.of(BIG, null, farGate(95, 105, 70, 73), 16, 3, Material.WATER);

        assertEquals(new Funnel.Opening(0, 10, 0, 3), funnel.far());
        assertEquals(14, funnel.taper(), "seven straight, three fifths of eleven, then seven lower");
        for (int layer = 1; layer <= 7; layer++)
        {
            assertTrue(funnel.holds(layer, 5, 10), "the top row on the straight run's layer " + layer);
        }
        assertFalse(funnel.holds(8, 5, 10), "the ceiling a block lower on the first layer past it");
        assertTrue(funnel.holds(12, 0, 0) && funnel.holds(12, 10, 0), "the whole width to the end");
        assertTrue(funnel.holds(13, 0, 3) && !funnel.holds(13, 0, 4), "the far plane four high");
    }

    /** A straight tube of the opening alone draws nothing: it is the plain view, as no funnel (#587). */
    @Test
    void aStraightTubeOfOneLayerIsThePlainView()
    {
        assertNull(Funnel.of(BIG, null, farGate(95, 105, 70, 80), 1, 3, Material.WATER), "the longest allowed is one");
        assertNull(Funnel.of(opening(1, 2), null, farOpening(1, 2), 16, 1, Material.WATER),
            "a least of one, onto a run of three fifths of two, one");
        assertEquals(2, Funnel.of(opening(1, 2), null, farOpening(1, 2), 16, 2, Material.WATER).taper(), "a least of two");
    }

    /** A far gate lying flat is still no hole across the view, least length or not (#587). */
    @Test
    void aFarGateLyingFlatHasNoTubeEither()
    {
        final List<Spot> flat = new ArrayList<>();
        for (int z = 190; z <= 199; z++)
        {
            for (int x = 95; x <= 105; x++)
            {
                flat.add(new Spot(x, 71, z));
            }
        }

        assertNull(Funnel.of(BIG, null, flat, 16, 3, Material.WATER));
    }
}
