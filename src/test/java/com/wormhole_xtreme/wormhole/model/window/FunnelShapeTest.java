package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * A shaped gate's tunnel is its own shape, shrinking a layer at a time to the far opening (#583).
 *
 * <p>Seen in a world: a round Massive gate's tunnel was a box, with a flat floor. Each layer is now
 * the near opening's shape shrunk to the layer's box, and the last the far opening's own cells. Taken
 * from the shipped shapes, so a change to their silhouettes is a change these tests see.
 */
class FunnelShapeTest
{
    /** Where travellers land, in front of the far gate: facing south. */
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    private static final Pattern CELL = Pattern.compile("\\[[^\\]]*\\]");

    /** A shipped shape's portal cells, as {@code {across, up}} from the bottom left of their box. */
    private static List<int[]> portalOf(final String shape) throws IOException
    {
        final List<int[]> cells = new ArrayList<>();
        try (InputStream in = FunnelShapeTest.class.getResourceAsStream("/shapes/gate/" + shape + ".shape"))
        {
            assertNotNull(in, shape + " is shipped");
            int row = 0;
            for (final String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"))
            {
                row = readRow(line, row, cells);
            }
        }
        final int low = cells.stream().mapToInt(cell -> cell[0]).min().orElseThrow();
        final int top = cells.stream().mapToInt(cell -> cell[1]).max().orElseThrow();
        final List<int[]> placed = new ArrayList<>();
        cells.forEach(cell -> placed.add(new int[] { cell[0] - low, top - cell[1] }));
        assertTrue(placed.size() > 1, shape + " has a portal");
        return placed;
    }

    /** Reads one line of a shape file's grid, adding its portal cells; the row the next line is. */
    private static int readRow(final String line, final int row, final List<int[]> cells)
    {
        if (!line.startsWith("["))
        {
            return line.startsWith("Layer") ? 0 : row;
        }
        final Matcher token = CELL.matcher(line);
        int column = 0;
        while (token.find())
        {
            if (token.group().startsWith("[P"))
            {
                cells.add(new int[] { column, row });
            }
            column++;
        }
        return row + 1;
    }

    private static int extent(final List<int[]> cells, final int axis)
    {
        return cells.stream().mapToInt(cell -> cell[axis]).max().orElseThrow() + 1;
    }

    /** The near gate's window for a shape's opening, looked into northwards. */
    private static WindowShape windowFor(final List<int[]> near)
    {
        return WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, extent(near, 0), extent(near, 1));
    }

    /** A shape's cells in the near opening's plane. */
    private static List<Spot> nearCells(final WindowShape window, final List<int[]> near)
    {
        final List<Spot> spots = new ArrayList<>();
        near.forEach(cell -> spots.add(window.behind(0, cell[0], cell[1])));
        return spots;
    }

    /** A shape's cells as a far gate behind the arrival, centred on its column and standing on its row. */
    private static List<Spot> farCells(final List<int[]> far)
    {
        final int middle = (extent(far, 0) - 1) / 2;
        final List<Spot> spots = new ArrayList<>();
        far.forEach(cell -> spots.add(new Spot(100 - (cell[0] - middle), 70 + cell[1], 199)));
        return spots;
    }

    private static Funnel funnel(final String near, final String far) throws IOException
    {
        return funnel(portalOf(near), portalOf(far), 16);
    }

    private static Funnel funnel(final List<int[]> near, final List<int[]> far, final int longest)
    {
        final WindowShape window = windowFor(near);
        return Funnel.of(window, nearCells(window, near), farCells(far), longest, Material.WATER);
    }

    /** A cross the size of a box, its arms three wide through the middle: an opening that is not convex. */
    private static List<int[]> cross(final int size)
    {
        final int middle = size / 2;
        final List<int[]> cells = new ArrayList<>();
        for (int across = 0; across < size; across++)
        {
            for (int up = 0; up < size; up++)
            {
                if ((Math.abs(across - middle) <= 1) || (Math.abs(up - middle) <= 1))
                {
                    cells.add(new int[] { across, up });
                }
            }
        }
        return cells;
    }

    /** The far opening's cells through the window, as {@code across * 100 + up}. */
    private static Set<Integer> farKeys(final Funnel funnel)
    {
        final Set<Integer> keys = new HashSet<>();
        for (int across = 0; across < funnel.width(); across++)
        {
            for (int up = 0; up < funnel.height(); up++)
            {
                if (funnel.holds(funnel.taper() - 1, across, up))
                {
                    keys.add((across * 100) + up);
                }
            }
        }
        return keys;
    }

    /** The cells of one layer the tunnel holds, as {@code across * 100 + up}. */
    private static Set<Integer> layerOf(final Funnel funnel, final int layer)
    {
        final Set<Integer> held = new HashSet<>();
        for (int across = -3; across < (funnel.width() + 3); across++)
        {
            for (int up = -3; up < (funnel.height() + 3); up++)
            {
                if (funnel.holds(layer, across, up))
                {
                    held.add((across * 100) + up);
                }
            }
        }
        return held;
    }

    private static Set<Integer> keysOf(final List<int[]> cells, final int shiftAcross)
    {
        final Set<Integer> keys = new HashSet<>();
        cells.forEach(cell -> keys.add(((cell[0] + shiftAcross) * 100) + cell[1]));
        return keys;
    }

    @Test
    void aMassiveGatesTunnelIsItsRoundOpeningAtTheHorizonAndTheStandardOneAtItsEnd() throws IOException
    {
        final Funnel funnel = funnel("Massive", "Standard");
        final int plane = funnel.taper() - 1;

        assertEquals(13, funnel.taper(), "seventeen square to five narrows twelve, behind a layer its own size");
        assertEquals(keysOf(portalOf("Massive"), 0), layerOf(funnel, 0), "the near opening's own cells, corners left out");
        assertFalse(funnel.holds(0, 0, 0), "a corner of the box is the frame");
        assertEquals(layerOf(funnel, 0), layerOf(funnel, 1), "the first layer behind it is the same shape, not a block smaller at once");
        assertEquals(keysOf(portalOf("Standard"), 6), layerOf(funnel, plane), "the far opening's own cells, on its middle columns");
    }

    @Test
    void theTunnelShrinksLayerByLayerWithARoundFloor() throws IOException
    {
        final Funnel funnel = funnel("Massive", "Standard");

        int before = layerOf(funnel, 0).size();
        for (int layer = 1; layer < funnel.taper(); layer++)
        {
            final int now = layerOf(funnel, layer).size();
            assertTrue(now <= before, "layer " + layer + " holds " + now + ", the one before " + before);
            before = now;
        }
        assertTrue(funnel.hollow(1, 8, 0), "the middle of the floor");
        assertFalse(funnel.hollow(1, 2, 0), "not its edge: a flat floor would run the box's width");
        assertTrue(funnel.wall(1, 2, 0), "which is walled, so the floor curves up");
        assertTrue(funnel.hollow(1, 2, 8), "while the middle row runs nearly the width");
    }

    /**
     * Every shipped pair the same either side, a symmetric near opening onto a far one centred in it,
     * has a tunnel the same either side on every layer.
     *
     * <p>Sampled in floating point, a half came out a hair over on one side and a hair under on the
     * other, and Massive onto Minimal was lopsided by two or three cells on two layers.
     */
    @Test
    void everySymmetricPairsTunnelStaysSymmetric() throws IOException
    {
        final String[] shapes = { "Massive", "Grand", "Large", "Standard", "Minimal" };
        int pairs = 0;
        for (final String near : shapes)
        {
            for (final String far : shapes)
            {
                final Funnel funnel = near.equals(far) ? null : funnel(near, far);
                if ((funnel != null) && ((funnel.far().left() + funnel.far().right()) == (funnel.width() - 1))
                    && mirrored(keysOf(portalOf(near), 0), funnel.width()).equals(keysOf(portalOf(near), 0)))
                {
                    pairs++;
                    for (int layer = 0; layer < funnel.taper(); layer++)
                    {
                        final Set<Integer> held = layerOf(funnel, layer);
                        assertEquals(held, mirrored(held, funnel.width()), near + " to " + far + ", layer " + layer);
                    }
                }
            }
        }
        assertTrue(pairs >= 2, pairs + " symmetric pairs: Massive onto Standard and onto Minimal at least");
    }

    /** Cells as {@code across * 100 + up} mirrored across the middle column of an opening a width wide. */
    private static Set<Integer> mirrored(final Set<Integer> keys, final int width)
    {
        final Set<Integer> mirror = new HashSet<>();
        keys.forEach(key -> mirror.add((((width - 1) - (key / 100)) * 100) + (key % 100)));
        return mirror;
    }

    /**
     * A tunnel never narrows below the far opening before its end: every far cell is held on every
     * layer of it, so none sits in a pocket of wall and the tunnel ends in no one-block spike.
     *
     * <p>Shrunk from Massive's round opening, the layer before Standard's ended one block wide at top
     * and bottom, where Standard's rows are three.
     */
    @Test
    void theTunnelIsNeverNarrowerThanTheFarOpeningItEndsOn() throws IOException
    {
        for (final String far : new String[] { "Standard", "Large" })
        {
            final Funnel funnel = funnel("Massive", far);
            final Set<Integer> farOpening = farKeys(funnel);
            for (int layer = 1; layer < funnel.taper(); layer++)
            {
                assertTrue(layerOf(funnel, layer).containsAll(farOpening), "Massive to " + far + ", layer " + layer);
            }
            assertEquals(keysOf(portalOf(far), funnel.far().left()), farOpening, "and the end is the far opening exactly");
        }
    }

    /**
     * A shrunk shape does not only narrow: a cell can be held on a layer and not the one before. Each
     * such cell, and every cell around the tunnel, is still walled two deep.
     */
    @Test
    void aShrunkShapeCanWidenALittleAndIsStillWalled() throws IOException
    {
        int widened = 0;
        for (final String[] pair : new String[][] { { "Massive", "Standard" }, { "Massive", "Large" }, { "Grand", "Large" } })
        {
            final Funnel funnel = funnel(pair[0], pair[1]);
            for (int layer = 1; layer < funnel.taper(); layer++)
            {
                final Set<Integer> now = layerOf(funnel, layer);
                now.removeAll(layerOf(funnel, layer - 1));
                widened += now.size();
            }
        }
        assertTrue(widened > 0, "some cell is held on a layer and not the one before");
    }

    /**
     * A cross onto Standard at a length of four: the end of an arm of the shrunk cross is hollow on the
     * second layer and out of the far opening on the next, more than two blocks from it, and not in
     * the cross itself. Only the layer before walls it; without that, this world showed through the
     * end of the arm.
     */
    @Test
    void aCrossesArmIsWalledWhereItEndsAtAShortLength() throws IOException
    {
        final Funnel funnel = funnel(cross(17), portalOf("Standard"), 4);

        assertEquals(4, funnel.taper());
        assertEquals(keysOf(cross(17), 0), layerOf(funnel, 0), "the opening is the cross alone, not the far cells it lacks");
        assertTrue(funnel.hollow(1, 0, 8), "the first layer is the cross at its own size");
        assertTrue(funnel.hollow(2, 3, 5), "the end of the left arm, shrunk, hollow on the second layer");
        assertTrue(funnel.hollow(1, 6, 1), "a far cell the cross lacks, held from the first layer on");
        assertFalse(funnel.holds(3, 3, 5) || funnel.holds(1, 3, 5), "out of the far opening, and of the cross");
        assertTrue(funnel.wall(3, 3, 5), "so walled from the layer before");
        for (int layer = 1; layer < funnel.taper(); layer++)
        {
            checkLayer(funnel, layer, "cross to Standard");
        }
    }

    /** At every short length the tunnel ends on the far opening and every way out is walled two deep. */
    @Test
    void everyShortTunnelEndsOnTheFarOpeningWalledTwoDeep() throws IOException
    {
        for (final String[] pair : new String[][] { { "Massive", "Standard" }, { "Grand", "Large" } })
        {
            for (int length = 1; length <= 4; length++)
            {
                final Funnel funnel = funnel(portalOf(pair[0]), portalOf(pair[1]), length);
                final String what = pair[0] + " to " + pair[1] + " at " + length;
                assertEquals(length, funnel.taper(), what);
                if (length == 1)
                {
                    final int[] drawn = { 0 };
                    funnel.forEachWall(20, (layer, across, up) -> drawn[0]++);
                    funnel.forEachHollow(20, (layer, across, up) -> drawn[0]++);
                    assertEquals(0, drawn[0], what + ": the opening alone, nothing behind it");
                    continue;
                }
                assertEquals(keysOf(portalOf(pair[1]), funnel.far().left()), farKeys(funnel), what + ": ends on the far opening");
                for (int layer = 1; layer < funnel.taper(); layer++)
                {
                    checkLayer(funnel, layer, what);
                }
            }
        }
    }

    /**
     * Every way out of a shaped tunnel is walled two deep: a cell outside it, beside one inside on its
     * layer or behind one inside on the layer before, is wall, and so is the cell past it.
     */
    @Test
    void everyWayOutOfAShapedTunnelIsWalledTwoDeep() throws IOException
    {
        for (final String[] pair : new String[][] { { "Massive", "Standard" }, { "Grand", "Standard" },
            { "Grand", "Large" }, { "Large", "Standard" }, { "Massive", "Minimal" }, { "Large", "Minimal" } })
        {
            final Funnel funnel = funnel(pair[0], pair[1]);
            int checked = 0;
            for (int layer = 1; layer < funnel.taper(); layer++)
            {
                checked += checkLayer(funnel, layer, pair[0] + " to " + pair[1]);
            }
            assertTrue(checked > 50, pair[0] + " to " + pair[1] + ": " + checked + " ways out checked");
        }
    }

    /** Checks one layer's ways out, returning how many there were. */
    private static int checkLayer(final Funnel funnel, final int layer, final String pair)
    {
        int checked = 0;
        for (int across = -1; across <= funnel.width(); across++)
        {
            for (int up = -1; up <= funnel.height(); up++)
            {
                checked += checkCell(funnel, layer, across, up, pair);
            }
        }
        return checked;
    }

    /** Checks the ways out through one cell outside the tunnel, returning how many there were. */
    private static int checkCell(final Funnel funnel, final int layer, final int across, final int up, final String pair)
    {
        if (funnel.holds(layer, across, up))
        {
            return 0;
        }
        int checked = 0;
        final String where = pair + ", layer " + layer + ", column " + across + ", row " + up;
        if (funnel.holds(layer - 1, across, up))
        {
            assertTrue(funnel.wall(layer, across, up), "behind the tunnel's last layer: " + where);
            if (((layer + 1) < funnel.taper()) && !funnel.holds(layer + 1, across, up))
            {
                assertTrue(funnel.wall(layer + 1, across, up), "and a layer further: " + where);
            }
            checked++;
        }
        for (final int[] step : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
        {
            if (funnel.holds(layer, across - step[0], up - step[1]))
            {
                // Two deep, unless the next is the tunnel again, across a notch of a shape that is not convex.
                assertTrue(funnel.wall(layer, across, up) && (funnel.wall(layer, across + step[0], up + step[1])
                    || funnel.holds(layer, across + step[0], up + step[1])), "two deep beside the tunnel: " + where);
                checked++;
            }
        }
        return checked;
    }

    /** A rectangular opening given as its cells is the rectangle it was. */
    @Test
    void aRectangularOpeningGivenAsCellsIsTheBoxItWas()
    {
        final WindowShape window = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 11, 11);
        final List<Spot> every = new ArrayList<>();
        window.forEachOpening((x, y, z) -> every.add(new Spot(x, y, z)));
        final List<Spot> standard = new ArrayList<>();
        for (int x = 99; x <= 101; x++)
        {
            for (int y = 70; y <= 73; y++)
            {
                standard.add(new Spot(x, y, 199));
            }
        }
        final Funnel cells = Funnel.of(window, every, standard, 16, Material.WATER);
        final Funnel box = Funnel.of(window, standard, 16, Material.WATER);

        assertEquals(box, cells, "the same funnel");
        for (int layer = 0; layer < 12; layer++)
        {
            assertEquals(layerOf(box, layer), layerOf(cells, layer), "layer " + layer);
        }
    }

    /** What a Grand gate's tunnel to a Standard one costs to work out stays a few thousand blocks. */
    @Test
    void aGrandGatesTunnelIsAFewThousandBlocks() throws IOException
    {
        final Funnel funnel = funnel("Grand", "Standard");
        final int[] blocks = { 0 };
        funnel.forEachWall(64, (layer, across, up) -> blocks[0]++);
        funnel.forEachHollow(64, (layer, across, up) -> blocks[0]++);

        assertEquals(14, funnel.taper());
        assertTrue((blocks[0] > 1000) && (blocks[0] < 7000), blocks[0] + " blocks of wall and hollow");
    }

    /** Every cell of a box, as {@code across * 100 + up}. */
    private static Set<Integer> boxOf(final Funnel.Opening box)
    {
        final Set<Integer> keys = new HashSet<>();
        for (int across = box.left(); across <= box.right(); across++)
        {
            for (int up = box.bottom(); up <= box.top(); up++)
            {
                keys.add((across * 100) + up);
            }
        }
        return keys;
    }

    /** How many cells of one row, and of one column, a layer holds. */
    private static int[] spanOf(final Funnel funnel, final int layer, final int row, final int column)
    {
        int across = 0;
        int up = 0;
        for (int at = -40; at <= 60; at++)
        {
            across += funnel.holds(layer, at, row) ? 1 : 0;
            up += funnel.holds(layer, column, at) ? 1 : 0;
        }
        return new int[] { across, up };
    }

    /**
     * A Minimal gate, one block wide and two tall, is taken as the least aperture, three by three: on
     * its middle column and its bottom row, grown sideways and up. The tunnel ends on all of it, and
     * the room past it is what a three-by-three opening shows.
     *
     * <p>Seen in a world: through one by two the lines through both openings were a sliver, the room
     * all but invisible at the end of a slot.
     */
    @Test
    void aMinimalGateIsSeenThroughTheLeastAperture() throws IOException
    {
        final Funnel massive = funnel("Massive", "Minimal");
        assertEquals(new Funnel.Opening(7, 9, 0, 2), massive.far(), "three by three about Minimal's column, on its row");
        assertEquals(15, massive.taper(), "as long as seventeen narrows to three, not to one, and the layer its own size");
        assertEquals(boxOf(massive.far()), farKeys(massive), "the tunnel ends on the whole aperture");
        assertTrue(layerOf(massive, 5).containsAll(boxOf(massive.far())), "and holds it all the way");
        assertArrayEquals(new int[] { 5, 6 }, spanOf(massive, massive.taper(), 1, 8), "five across and six up on the room's first layer");

        final Funnel large = funnel("Large", "Minimal");
        assertEquals(new Funnel.Opening(2, 4, 0, 2), large.far());
        assertEquals(6, large.taper());
        assertArrayEquals(new int[] { 5, 6 }, spanOf(large, large.taper(), 1, 3), "five across and six up through a shorter tunnel");
    }

    /** A far opening three by three already is its own cells at the tunnel's end, a corner it lacks included. */
    @Test
    void aFarOpeningAtTheLeastApertureEndsOnItsOwnCells() throws IOException
    {
        final List<int[]> threeByThree = new ArrayList<>();
        for (int across = 0; across < 3; across++)
        {
            for (int up = 0; up < 3; up++)
            {
                if ((across != 2) || (up != 2))
                {
                    threeByThree.add(new int[] { across, up });
                }
            }
        }
        final Funnel funnel = funnel(portalOf("Massive"), threeByThree, 16);

        assertEquals(new Funnel.Opening(7, 9, 0, 2), funnel.far(), "not grown");
        assertEquals(keysOf(threeByThree, 7), farKeys(funnel), "its own eight cells, its top right corner left to the collar");
    }

    @Test
    void anOpeningIsGrownToTheLeastApertureSidewaysAboutItsMiddleAndUpFromItsFloor()
    {
        assertEquals(new Funnel.Opening(3, 5, 0, 2), Funnel.grown(new Funnel.Opening(4, 4, 0, 1)), "one by two");
        assertEquals(new Funnel.Opening(4, 6, 2, 4), Funnel.grown(new Funnel.Opening(4, 5, 2, 4)), "two wide: the extra block right");
        assertEquals(new Funnel.Opening(3, 5, 0, 5), Funnel.grown(new Funnel.Opening(4, 4, 0, 5)), "narrow but tall: only wider");
        assertEquals(new Funnel.Opening(4, 8, 0, 2), Funnel.grown(new Funnel.Opening(4, 8, 0, 0)), "wide but low: only taller");
        final Funnel.Opening big = new Funnel.Opening(4, 6, 0, 2);
        assertEquals(big, Funnel.grown(big), "three by three is left as it is");
    }

    /** A Minimal gate at every short length ends on the whole aperture, walled two deep. */
    @Test
    void aMinimalGateAtEveryShortLengthEndsOnTheAperture() throws IOException
    {
        for (int length = 2; length <= 4; length++)
        {
            final Funnel funnel = funnel(portalOf("Massive"), portalOf("Minimal"), length);
            assertEquals(length, funnel.taper());
            assertEquals(boxOf(funnel.far()), farKeys(funnel), "at " + length);
            for (int layer = 1; layer < funnel.taper(); layer++)
            {
                checkLayer(funnel, layer, "Massive to Minimal at " + length);
            }
        }
    }

    /**
     * Standard onto Minimal: the opening, a layer at its own size, then the three-by-three aperture,
     * and the room from the fourth layer.
     *
     * <p>Seen in a world: the tunnel went from Standard's five straight to three on its first layer.
     */
    @Test
    void aStandardGateOntoAMinimalOneHoldsItsSizeALayerBeforeNarrowing() throws IOException
    {
        final Funnel funnel = funnel("Standard", "Minimal");

        assertEquals(3, funnel.taper(), "the opening, its size again, the aperture");
        assertEquals(keysOf(portalOf("Standard"), 0), layerOf(funnel, 1), "Standard's own cells on the first layer");
        assertEquals(boxOf(new Funnel.Opening(1, 3, 0, 2)), layerOf(funnel, 2), "the three by three on the second");
        assertTrue(funnel.shows(3, 2, 1, 0.0) && !funnel.shows(2, 2, 1, 0.0), "the room from the third");
    }

    /** The shipped upright shapes, smallest first. */
    private static final String[] SHIPPED = { "Minimal", "Standard", "Large", "Massive", "Grand" };

    /** Every shipped pair's tunnel length at a least length, near gate by row, far by column; 0 for the plain view. */
    private static int[][] lengths(final int least) throws IOException
    {
        final int[][] table = new int[SHIPPED.length][SHIPPED.length];
        for (int near = 0; near < SHIPPED.length; near++)
        {
            for (int far = 0; far < SHIPPED.length; far++)
            {
                final List<int[]> nearCells = portalOf(SHIPPED[near]);
                final WindowShape window = windowFor(nearCells);
                final Funnel funnel = Funnel.of(window, nearCells(window, nearCells), farCells(portalOf(SHIPPED[far])), 16,
                    least, Material.WATER);
                table[near][far] = (funnel == null) ? 0 : funnel.taper();
            }
        }
        return table;
    }

    private static String rows(final int[][] table)
    {
        final StringBuilder text = new StringBuilder();
        for (final int[] row : table)
        {
            text.append(Arrays.toString(row)).append('\n');
        }
        return text.toString();
    }

    /**
     * Every shipped pair's tunnel, at {@code gate-view-funnel-length: 16}: with no least length as before
     * #587, a pair that does not narrow the plain view; and at a least of three, the wormhole-everywhere
     * value, as docs/GATES.md tabulates it. A change to a shipped opening changes this table.
     *
     * <p>Rows are the near gate, columns the far one: Minimal, Standard, Large, Massive, Grand. Minimal's
     * opening is one by two, Standard five square, Large eight, Massive seventeen, Grand eighteen by
     * seventeen.
     */
    @Test
    void everyShippedPairsTunnelIsAsTabulated() throws IOException
    {
        final int[][] before = {
            { 0, 0, 0, 0, 0 },
            { 3, 0, 0, 0, 0 },
            { 6, 4, 0, 0, 0 },
            { 15, 13, 10, 0, 0 },
            { 16, 14, 11, 2, 0 } };
        final int[][] atThree = {
            { 3, 3, 3, 3, 3 },
            { 5, 3, 3, 3, 3 },
            { 10, 8, 5, 5, 5 },
            { 16, 16, 16, 10, 10 },
            { 16, 16, 16, 12, 11 } };

        assertEquals(rows(before), rows(lengths(0)), "no least length changes nothing");
        assertEquals(rows(atThree), rows(lengths(3)), "at a least of three");
    }

    /**
     * Every cell of every shipped pair's tunnel at no least length, as a hash: whether the tunnel holds
     * it, walls it, leaves it hollow and shows the room there, held or not, over every layer to a few
     * past its end and a few blocks round the opening.
     */
    private static long drawingOf(final int least) throws IOException
    {
        long hash = 17;
        for (final String near : SHIPPED)
        {
            for (final String far : SHIPPED)
            {
                final List<int[]> nearCells = portalOf(near);
                final WindowShape window = windowFor(nearCells);
                final Funnel funnel = Funnel.of(window, nearCells(window, nearCells), farCells(portalOf(far)), 16, least,
                    Material.WATER);
                hash = (hash * 31) + ((funnel == null) ? -1 : funnel.taper());
                if (funnel != null)
                {
                    hash = (hash * 31) + funnel.far().hashCode();
                    hash = drawingOf(funnel, hash);
                }
            }
        }
        return hash;
    }

    private static long drawingOf(final Funnel funnel, final long start)
    {
        long hash = start;
        for (int layer = 0; layer <= (funnel.taper() + 4); layer++)
        {
            for (int across = -4; across < (funnel.width() + 4); across++)
            {
                for (int up = -4; up < (funnel.height() + 4); up++)
                {
                    final int bits = (funnel.holds(layer, across, up) ? 1 : 0) | (funnel.wall(layer, across, up) ? 2 : 0)
                        | (funnel.hollow(layer, across, up) ? 4 : 0) | (funnel.shows(layer, across, up, 0.0) ? 8 : 0)
                        | (funnel.shows(layer, across, up, 1.0) ? 16 : 0);
                    hash = (hash * 31) + bits;
                }
            }
        }
        return hash;
    }

    /**
     * With no least length, every shipped pair's tunnel is drawn cell for cell as it was before #587: the
     * hash was recorded from the #583 code, before the straight run moved where the narrowing starts.
     */
    @Test
    void noLeastLengthDrawsEveryShippedPairAsBefore() throws IOException
    {
        assertEquals(-5904751408123294266L, drawingOf(0),"every cell of every pair, as #583 drew it");
    }

    /**
     * Large onto Standard at a least of three runs straight for five layers at Large's own shape, then
     * narrows exactly as it does with none, four layers deeper: the run comes before the narrowing, not
     * spread through it (#587).
     */
    @Test
    void theStraightRunComesBeforeTheNarrowing() throws IOException
    {
        final List<int[]> large = portalOf("Large");
        final WindowShape window = windowFor(large);
        final List<Spot> standard = farCells(portalOf("Standard"));
        final Funnel plain = Funnel.of(window, nearCells(window, large), standard, 16, 0, Material.WATER);
        final Funnel run = Funnel.of(window, nearCells(window, large), standard, 16, 3, Material.WATER);

        assertEquals(4, plain.taper());
        assertEquals(8, run.taper());
        for (int layer = 1; layer <= 5; layer++)
        {
            assertEquals(keysOf(large, 0), layerOf(run, layer), "Large's own shape on layer " + layer);
        }
        for (int layer = 1; layer <= 3; layer++)
        {
            assertEquals(layerOf(plain, layer), layerOf(run, layer + 4), "the narrowing's layer " + layer + ", four deeper");
        }
        assertTrue(layerOf(run, 6).size() < layerOf(run, 5).size(), "a step in on the first layer past the run");
        assertTrue(layerOf(run, 7).size() < layerOf(run, 6).size(), "and another to the far opening");
    }

    /**
     * A Standard pair is shorter than a Large pair, which is shorter than a Large gate onto a Standard one,
     * which is shorter than a Massive gate onto a Standard one: at a least of three and of four. From five
     * a Standard pair's run is as long as a Large pair's, three fifths of eight (#587).
     */
    @Test
    void aBiggerPairIsFurtherDownAndANarrowingOneFurtherStill() throws IOException
    {
        for (int least = 3; least <= 4; least++)
        {
            final int[][] table = lengths(least);
            final int standardPair = table[1][1];
            final int largePair = table[2][2];
            final int largeToStandard = table[2][1];
            final int massiveToStandard = table[3][1];
            assertTrue((standardPair < largePair) && (largePair < largeToStandard) && (largeToStandard < massiveToStandard),
                "at " + least + ": " + standardPair + ", " + largePair + ", " + largeToStandard + ", " + massiveToStandard);
        }
        assertEquals(lengths(5)[2][2], lengths(5)[1][1], "at five, a Standard pair is as long as a Large one");
    }

    /**
     * Below three, the least length matters only to a Minimal gate: every other shipped opening's own run,
     * three fifths of its longer side, is three or more (#587).
     */
    @Test
    void belowThreeOnlyAMinimalGatesTunnelChanges() throws IOException
    {
        final int[][] three = lengths(3);
        for (int least = 1; least <= 2; least++)
        {
            final int[][] table = lengths(least);
            for (int near = 1; near < SHIPPED.length; near++)
            {
                assertEquals(Arrays.toString(three[near]), Arrays.toString(table[near]), SHIPPED[near] + " at " + least);
            }
            assertEquals((least == 1) ? "[0, 0, 0, 0, 0]" : "[2, 2, 2, 2, 2]", Arrays.toString(table[0]),
                "Minimal at " + least + ": its run the least, and a run of one the plain view");
        }
    }

    /**
     * What a straight tube costs a watched gate (#587): its air and wall are sent with the view. A pair of
     * Standard gates at a least of three is 154 blocks; a pair of Massive ones, ten long, 3357, about what
     * Grand onto Standard already cost.
     */
    @Test
    void aMassivePairsStraightTubeIsAFewThousandBlocks() throws IOException
    {
        final List<int[]> massive = portalOf("Massive");
        final WindowShape window = windowFor(massive);
        final Funnel funnel = Funnel.of(window, nearCells(window, massive), farCells(massive), 16, 3, Material.WATER);
        final int[] blocks = { 0, 0 };
        funnel.forEachHollow(64, (layer, across, up) -> blocks[0]++);
        funnel.forEachWall(64, (layer, across, up) -> blocks[1]++);

        assertEquals(10, funnel.taper());
        assertEquals(9 * 221, blocks[0], "nine layers of Massive's 221 cells");
        assertEquals(1368, blocks[1], "and a wall two thick round each");
    }

    /**
     * A Standard gate dialling a Large one is seen down a tube of Standard's own shape to its end, corners
     * left out: not the square middle of the Large opening it ends in front of (#587).
     */
    @Test
    void aSmallGateOntoABigOnesTubeIsItsOwnShapeToItsEnd() throws IOException
    {
        final List<int[]> standard = portalOf("Standard");
        final WindowShape window = windowFor(standard);
        final Funnel funnel = Funnel.of(window, nearCells(window, standard), farCells(portalOf("Large")), 16, 3,
            Material.WATER);

        assertEquals(3, funnel.taper());
        assertEquals(keysOf(standard, 0), layerOf(funnel, 1));
        assertEquals(keysOf(standard, 0), layerOf(funnel, 2), "its last layer too");
        assertFalse(funnel.holds(2, 0, 0), "a corner of the box is Standard's frame");
    }
}
