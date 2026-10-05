package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * Where an open gate's window looks (#516): which far block each block behind the opening shows.
 *
 * <p>A gate seen from its front shows the ground in front of the gate it dialled, as though the
 * viewer had already stepped through. Get the turn wrong and the far side is shown mirrored, so a
 * traveller walks out to the left of what they aimed at; get the offset wrong and the arrival
 * block is not what shows through the middle of the gate. Plain numbers, no server.
 */
class GateViewsTest
{
    /** Where a traveller through the gates below lands: facing south, the way they walk out. */
    private static final MirrorPoint ARRIVAL = new MirrorPoint("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    /** An opening of the given size, in the plane z = 20 for a gate facing north or south, or x = 20 for east or west. */
    private static List<Spot> opening(final BlockFace facing, final int low, final int width, final int height)
    {
        final List<Spot> cells = new ArrayList<>();
        for (int across = low; across < (low + width); across++)
        {
            for (int y = 64; y < (64 + height); y++)
            {
                cells.add((facing.getModZ() != 0) ? new Spot(across, y, 20) : new Spot(20, y, across));
            }
        }
        return cells;
    }

    /** An opening's window with a frame closing it in on every side, as every ring gate has. */
    private static MirrorWindow shapeOf(final BlockFace facing, final List<Spot> cells)
    {
        return GateViews.shapeOf(facing, cells, ringOf(facing, cells), ARRIVAL);
    }

    /** Every block beside an opening's cells, in its plane, that is not one of them: a frame round it. */
    private static Set<Spot> ringOf(final BlockFace facing, final List<Spot> cells)
    {
        final Set<Spot> ring = new HashSet<>();
        for (final Spot cell : cells)
        {
            for (final int[] side : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
            {
                ring.add((facing.getModZ() != 0) ? new Spot(cell.x() + side[0], cell.y() + side[1], cell.z())
                    : new Spot(cell.x(), cell.y() + side[1], cell.z() + side[0]));
            }
        }
        ring.removeAll(cells);
        return ring;
    }

    @Test
    void theArrivalBlockShowsThroughTheMiddleOfTheOpeningAtItsBottomRow()
    {
        // Faces south, so it is looked into northwards: the first layer behind it is z = 19.
        final MirrorWindow shape = shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, 3));

        assertNotNull(shape);
        assertEquals(new Spot(100, 70, 200), shape.farOf(11, 64, 19),
            "straight through the middle of the bottom row is where a traveller lands");
    }

    @Test
    void aStepToTheViewersRightIsAStepToTheTravellersRight()
    {
        final MirrorWindow shape = shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, 3));

        // The viewer looks north, so their right is east (x 12). The traveller faces south, so
        // theirs is west (x 99). A mirrored window would show x 101 here.
        assertEquals(new Spot(99, 70, 200), shape.farOf(12, 64, 19),
            "a gate is a window, not a reflection: right through it is right on arrival");
        assertEquals(new Spot(100, 72, 200), shape.farOf(11, 66, 19), "and up is up");
    }

    @Test
    void furtherBehindTheGateIsFurtherAheadOfTheArrival()
    {
        final MirrorWindow shape = shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, 3));

        assertEquals(new Spot(100, 70, 204), shape.farOf(11, 64, 15),
            "five blocks behind the gate is four past the arrival block, in the way the traveller faces");
    }

    /**
     * A gate facing east, where the viewer's right runs towards negative z.
     *
     * <p>The across coordinate is signed along the axis, and taking the smallest one as the
     * left edge is only right if the sign is undone when the base is placed. Got wrong, the
     * opening lands on the far side of the plane's origin and not one cell of it is drawn.
     */
    @Test
    void anOpeningFacingEastCoversItsOwnCells()
    {
        final List<Spot> cells = opening(BlockFace.EAST, 5, 3, 3);
        final MirrorWindow shape = shapeOf(BlockFace.EAST, cells);

        assertNotNull(shape);
        for (final Spot cell : cells)
        {
            assertTrue(shape.isOpening(cell.x(), cell.y(), cell.z()), "part of the gate's opening: " + cell);
        }
        assertFalse(shape.isOpening(20, 64, 4), "just past its edge");
        assertFalse(shape.isOpening(20, 64, 8), "just past its other edge");
        // Looked into westwards: the first layer behind is x 19, and the middle column is z 6.
        assertEquals(new Spot(100, 70, 200), shape.farOf(19, 64, 6), "the arrival shows through the middle");
    }

    /** A Standard gate's opening is five by five less its corners, and is drawn whole all the same. */
    @Test
    void aStandardOpeningIsDrawnWholeCornersAndAll()
    {
        final List<Spot> cells = new ArrayList<>(opening(BlockFace.SOUTH, 10, 5, 5));
        cells.removeIf(cell -> ((cell.x() == 10) || (cell.x() == 14)) && ((cell.y() == 64) || (cell.y() == 68)));
        final MirrorWindow shape = shapeOf(BlockFace.SOUTH, cells);

        assertNotNull(shape, "a ring with its corners filled is still an opening");
        assertEquals(5, shape.width(), "its own width, not the capture's");
        assertEquals(new Spot(100, 70, 200), shape.farOf(12, 64, 19),
            "from its bottom row, not lifted a row to clear the missing corners");
    }

    /** An opening of rows of the given widths, each centred on the widest, from y 64 up. */
    private static List<Spot> round(final int... widths)
    {
        final int most = Arrays.stream(widths).max().orElse(0);
        final List<Spot> cells = new ArrayList<>();
        for (int row = 0; row < widths.length; row++)
        {
            final int left = 10 + ((most - widths[row]) / 2);
            for (int x = left; x < (left + widths[row]); x++)
            {
                cells.add(new Spot(x, 64 + row, 20));
            }
        }
        return cells;
    }

    /** A Large gate is the even one, eight across, and is drawn whole, round corners and all. */
    @Test
    void aLargeOpeningIsDrawnWholeFromItsBottomRow()
    {
        final MirrorWindow shape = shapeOf(BlockFace.SOUTH, round(4, 6, 8, 8, 8, 8, 6, 4));

        assertNotNull(shape);
        assertEquals(8, shape.width(), "the whole of its width, not a Standard window carved in it");
        assertEquals(8, shape.height());
        assertTrue(shape.isOpening(10, 64, 20) && shape.isOpening(17, 71, 20), "corner to corner");
    }

    /**
     * A Grand gate is drawn through the whole of its opening, eighteen by seventeen.
     *
     * <p>It used to be drawn through an eight-by-eight window carved at the foot of its middle, the
     * rest keeping its horizon: a small window in a big gate. Its arrival shows through the middle
     * of its bottom row, as through a small gate's.
     */
    @Test
    void aGrandOpeningIsDrawnWhole()
    {
        final List<Spot> cells = round(10, 12, 14, 16, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 16, 14, 12);
        final MirrorWindow shape = shapeOf(BlockFace.SOUTH, cells);

        assertNotNull(shape);
        assertEquals(18, shape.width(), "the whole of its width");
        assertEquals(17, shape.height(), "and its height");
        for (final Spot cell : cells)
        {
            assertTrue(shape.isOpening(cell.x(), cell.y(), cell.z()), "every cell of it: " + cell);
        }
        // Eighteen wide from x 10, so its middle is between x 18 and 19; the arrival is left of the middle.
        assertEquals(new Spot(100, 70, 200), shape.farOf(18, 64, 19), "the arrival through the middle of its bottom row");
    }

    /** A Massive gate's bottom row is five wide, and its window still stands on it. */
    @Test
    void aMassiveOpeningIsDrawnWholeFromItsBottomRow()
    {
        final List<Spot> cells = round(5, 9, 11, 13, 15, 15, 17, 17, 17, 17, 17, 15, 15, 13, 11, 9, 5);
        final MirrorWindow shape = shapeOf(BlockFace.SOUTH, cells);

        assertNotNull(shape);
        assertEquals(17, shape.width());
        assertEquals(17, shape.height());
        assertEquals(new Spot(100, 70, 200), shape.farOf(18, 64, 19),
            "from its five-wide bottom row, not lifted to where a window would fit");
    }

    /**
     * An opening wider or taller than the one every capture is seen through keeps its horizon.
     *
     * <p>The capture's rays were measured through that opening and no bigger: a custom shape with
     * a bigger one would be drawn from rays that never looked where its edges see.
     */
    @Test
    void anOpeningBiggerThanTheCapturesKeepsItsHorizon()
    {
        assertNotNull(shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, GateViews.MOST, GateViews.MOST)),
            "as big as the capture's is drawn");
        assertNull(shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, GateViews.MOST + 1, 3)), "one wider is not");
        assertNull(shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, GateViews.MOST + 1)), "nor one taller");
    }

    /**
     * A gate with no frame round its opening has no window, however it was built.
     *
     * <p>A Minimal gate is two portal cells on one frame block. Nothing but the ring hides a view's
     * edges as one walks round a freestanding gate, so with no ring the far side would hang in the
     * air beside it. The rule reads the gate's blocks, not its shape's name.
     */
    @Test
    void aGateWithNoFrameRoundItsOpeningHasNoWindow()
    {
        final List<Spot> minimal = List.of(new Spot(10, 64, 20), new Spot(10, 65, 20));

        assertNull(GateViews.shapeOf(BlockFace.SOUTH, minimal, Set.of(new Spot(10, 63, 20)), ARRIVAL),
            "a Minimal gate: one frame block, under its opening");
        assertNotNull(shapeOf(BlockFace.SOUTH, minimal), "the same two cells with a ring round them");
        for (final Spot gap : ringOf(BlockFace.SOUTH, minimal))
        {
            final Set<Spot> ring = new HashSet<>(ringOf(BlockFace.SOUTH, minimal));
            ring.remove(gap);
            assertNull(GateViews.shapeOf(BlockFace.SOUTH, minimal, ring, ARRIVAL), "a ring open at " + gap);
        }
    }

    /**
     * The shipped shapes, read by the plugin's own shape reader and laid out as a gate is built:
     * Standard, Large, Grand and Massive are framed, and Minimal is not; each sign-dial shape as its own.
     *
     * <p>The docs say which gates show a view, and why Minimal does not, from these files. A shape
     * edited so its ring no longer closes the opening, or Minimal given a frame, should say so here
     * rather than in a world.
     */
    @Test
    void theShippedRingGatesAreFramedAndMinimalIsNot() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        try
        {
            for (final String name : new String[] { "Standard", "StandardSignDial", "Large", "Grand", "Massive", "Minimal",
                "MinimalSignDial" })
            {
                final Stargate3DShape shape = new Stargate3DShape(
                    Files.readAllLines(Paths.get("src/main/resources/shapes/gate", name + ".shape")).toArray(new String[0]));
                // Looked at from the north, so the gate faces south and its opening lies along x.
                final GateGrid grid = GateBlueprint.inFrontOf(shape, 0, 64, 0, BlockFace.NORTH);
                final List<Spot> cells = GateBlueprint.openingOf(shape, grid).stream()
                    .map(cell -> new Spot(cell.x(), cell.y(), cell.z())).toList();
                final Set<Spot> frame = GateBlueprint.of(shape, grid).stream()
                    .filter(cell -> (cell.part() == GateBlueprint.Part.FRAME) || (cell.part() == GateBlueprint.Part.CHEVRON))
                    .map(cell -> new Spot(cell.x(), cell.y(), cell.z())).collect(Collectors.toSet());
                assertFalse(cells.isEmpty(), name + " has an opening");
                assertEquals(1, cells.stream().map(Spot::z).distinct().count(), name + "'s opening lies along x");

                final boolean framed = GateViews.framed(cells, frame, new Spot(1, 0, 0));

                assertEquals(!name.startsWith("Minimal"), framed, name);
                assertEquals(framed, GateViews.shapeOf(BlockFace.SOUTH, cells, frame, ARRIVAL) != null,
                    name + " shows a view exactly when it is framed: it fits the capture's opening");
            }
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }

    /** Framed is judged in the opening's own plane: a gate facing east has its sides along z. */
    @Test
    void aGateFacingEastIsFramedAlongZ()
    {
        final List<Spot> cells = opening(BlockFace.EAST, 5, 3, 3);
        final Set<Spot> ring = ringOf(BlockFace.EAST, cells);

        assertNotNull(GateViews.shapeOf(BlockFace.EAST, cells, ring, ARRIVAL));
        // The same ring laid along x, as a south-facing gate's would be, leaves its sides open.
        assertNull(GateViews.shapeOf(BlockFace.EAST, cells, ringOf(BlockFace.SOUTH, cells), ARRIVAL),
            "a frame across the wrong axis");
    }

    @Test
    void aGateLyingFlatHasNoWindow()
    {
        assertNull(GateViews.shapeOf(BlockFace.UP, opening(BlockFace.SOUTH, 10, 3, 3),
            ringOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, 3)), ARRIVAL),
            "a horizontal gate is out of scope: its opening is a floor");
        assertNull(GateViews.shapeOf(BlockFace.SOUTH_EAST, opening(BlockFace.SOUTH, 10, 3, 3),
            ringOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, 3)), ARRIVAL),
            "nor a facing that is not a cardinal");
    }

    /**
     * A mirror's window is what it was: two tall, and one banner wide or two.
     *
     * <p>The height became the window's own for gates, and the clamp on the width moved from the
     * record to the constructor mirrors use. A mirror saved three wide by hand must still be
     * drawn two wide, or its view would spill past its wall.
     */
    @Test
    void aMirrorsWindowIsStillTwoTallAndAtMostTwoWide()
    {
        final Spot at = new Spot(0, 64, 0);
        final Spot step = new Spot(0, 0, 1);
        final MirrorWindow mirror = new MirrorWindow(at, step, at, step, false, 3);

        assertEquals(2, mirror.width(), "three banners is still drawn as two");
        assertEquals(2, mirror.height(), "a banner's cloth");
    }

    /**
     * A gate that closes has its horizon back at once, not a sweep later.
     *
     * <p>The sweep was the only thing that forgot a cleared horizon, so a gate closed and dialled
     * again inside that second settled with its opening empty, whether or not its new destination
     * had a capture to show there.
     */
    @Test
    void aGateThatClosesHasItsHorizonBackAtOnce() throws ReflectiveOperationException
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn("Abydos");
        final Set<String> cleared = PrivateStatics.of(GateViews.class, "CLEARED");
        cleared.add("Abydos");
        try
        {
            assertEquals(Material.AIR, GateViews.horizonOf(gate, Material.WATER), "cleared for its view");

            GateViews.closed(gate);

            assertEquals(Material.WATER, GateViews.horizonOf(gate, Material.WATER),
                "closed, so the next dial settles into its horizon");
        }
        finally
        {
            GateViews.clear();
        }
    }
}
