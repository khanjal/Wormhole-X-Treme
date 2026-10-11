package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        assertTrue(placed.size() > 4, shape + " has a portal");
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
        final List<int[]> nearShape = portalOf(near);
        final WindowShape window = windowFor(nearShape);
        return Funnel.of(window, nearCells(window, nearShape), farCells(portalOf(far)), 16, Material.WATER);
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

        assertEquals(12, funnel.taper(), "seventeen square to five narrows twelve");
        assertEquals(keysOf(portalOf("Massive"), 0), layerOf(funnel, 0), "the near opening's own cells, corners left out");
        assertFalse(funnel.holds(0, 0, 0), "a corner of the box is the frame");
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
     * Every way out of a shaped tunnel is walled two deep: a cell outside it, beside one inside on its
     * layer or behind one inside on the layer before, is wall, and so is the cell past it.
     */
    @Test
    void everyWayOutOfAShapedTunnelIsWalledTwoDeep() throws IOException
    {
        for (final String[] pair : new String[][] { { "Massive", "Standard" }, { "Grand", "Standard" },
            { "Grand", "Large" }, { "Large", "Standard" } })
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
        final int[][] steps = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int across = -1; across <= funnel.width(); across++)
        {
            for (int up = -1; up <= funnel.height(); up++)
            {
                if (funnel.holds(layer, across, up))
                {
                    continue;
                }
                final String where = pair + ", layer " + layer + ", column " + across + ", row " + up;
                if (funnel.holds(layer - 1, across, up))
                {
                    assertTrue(funnel.wall(layer, across, up), "behind the tunnel's last layer: " + where);
                    checked++;
                }
                for (final int[] step : steps)
                {
                    if (funnel.holds(layer, across - step[0], up - step[1]))
                    {
                        assertTrue(funnel.wall(layer, across, up) && funnel.wall(layer, across + step[0], up + step[1]),
                            "two deep beside the tunnel: " + where);
                        checked++;
                    }
                }
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

        assertEquals(13, funnel.taper());
        assertTrue((blocks[0] > 1000) && (blocks[0] < 6000), blocks[0] + " blocks of wall and hollow");
    }
}
