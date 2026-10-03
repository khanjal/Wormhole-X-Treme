package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PrivateStatics;
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

    @Test
    void theArrivalBlockShowsThroughTheMiddleOfTheOpeningAtItsBottomRow()
    {
        // Faces south, so it is looked into northwards: the first layer behind it is z = 19.
        final MirrorWindow shape = GateViews.shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, 3), ARRIVAL);

        assertNotNull(shape);
        assertEquals(new Spot(100, 70, 200), shape.farOf(11, 64, 19),
            "straight through the middle of the bottom row is where a traveller lands");
    }

    @Test
    void aStepToTheViewersRightIsAStepToTheTravellersRight()
    {
        final MirrorWindow shape = GateViews.shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, 3), ARRIVAL);

        // The viewer looks north, so their right is east (x 12). The traveller faces south, so
        // theirs is west (x 99). A mirrored window would show x 101 here.
        assertEquals(new Spot(99, 70, 200), shape.farOf(12, 64, 19),
            "a gate is a window, not a reflection: right through it is right on arrival");
        assertEquals(new Spot(100, 72, 200), shape.farOf(11, 66, 19), "and up is up");
    }

    @Test
    void furtherBehindTheGateIsFurtherAheadOfTheArrival()
    {
        final MirrorWindow shape = GateViews.shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 3, 3), ARRIVAL);

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
        final MirrorWindow shape = GateViews.shapeOf(BlockFace.EAST, cells, ARRIVAL);

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
        final MirrorWindow shape = GateViews.shapeOf(BlockFace.SOUTH, cells, ARRIVAL);

        assertNotNull(shape, "a ring with its corners filled is still an opening");
        assertEquals(5, shape.width(), "its own width, not the capture's");
        assertEquals(new Spot(100, 70, 200), shape.farOf(12, 64, 19),
            "from its bottom row, not lifted a row to clear the missing corners");
    }

    /**
     * A gate bigger than the capture's opening is drawn through a window carved at the foot of its
     * middle, rather than not at all: a capture's rays grow with its opening.
     */
    @Test
    void aWiderAndTallerOpeningHasALargeWindowCarvedAtTheFootOfItsMiddle()
    {
        final MirrorWindow shape = GateViews.shapeOf(BlockFace.SOUTH, opening(BlockFace.SOUTH, 10, 12, 10), ARRIVAL);

        assertNotNull(shape);
        assertEquals(GateViews.MOST, shape.width(), "no wider than a Large gate's");
        assertEquals(GateViews.MOST, shape.height(), "nor taller");
        assertTrue(shape.isOpening(12, 64, 20) && shape.isOpening(19, 71, 20), "the middle eight columns, bottom eight rows");
        assertFalse(shape.isOpening(11, 64, 20), "the column left of them keeps its horizon");
        assertFalse(shape.isOpening(20, 64, 20), "and the one right of them");
        assertFalse(shape.isOpening(15, 72, 20), "and the rows above");
        assertEquals(new Spot(100, 70, 200), shape.farOf(15, 64, 19),
            "and the arrival shows through the middle of its bottom row, as through a small gate's");
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
        final MirrorWindow shape = GateViews.shapeOf(BlockFace.SOUTH, round(4, 6, 8, 8, 8, 8, 6, 4), ARRIVAL);

        assertNotNull(shape);
        assertEquals(8, shape.width(), "the whole of its width, not a Standard window carved in it");
        assertEquals(8, shape.height());
        assertTrue(shape.isOpening(10, 64, 20) && shape.isOpening(17, 71, 20), "corner to corner");
    }

    /** A round gate's bottom row can be narrower than the window, which then sits on the first row it fits. */
    @Test
    void aRoundOpeningsWindowSitsOnTheLowestRowsItFitsIn()
    {
        // Massive's lower half: five wide at the foot, then nine, eleven, and so on.
        final List<Spot> cells = round(5, 9, 11, 13, 15, 17, 17, 17, 17, 17);
        final MirrorWindow shape = GateViews.shapeOf(BlockFace.SOUTH, cells, ARRIVAL);

        assertNotNull(shape);
        assertFalse(shape.isOpening(14, 64, 20), "not the bottom row, only five wide");
        assertTrue(shape.isOpening(14, 65, 20) && shape.isOpening(21, 72, 20), "but the eight rows above it");
        assertTrue(cells.containsAll(List.of(new Spot(14, 65, 20), new Spot(21, 65, 20), new Spot(14, 72, 20))),
            "every one of them part of the opening");
    }

    @Test
    void anOpeningWithNoRoomForTheWindowHasNone()
    {
        final List<Spot> cells = new ArrayList<>(opening(BlockFace.SOUTH, 10, 12, 10));
        cells.removeIf(cell -> cell.x() == 15);

        assertNull(GateViews.shapeOf(BlockFace.SOUTH, cells, ARRIVAL),
            "split down its middle, nowhere has eight columns side by side");
    }

    @Test
    void aGateLyingFlatHasNoWindow()
    {
        assertNull(GateViews.shapeOf(BlockFace.UP, opening(BlockFace.SOUTH, 10, 3, 3), ARRIVAL),
            "a horizontal gate is out of scope: its opening is a floor");
        assertNull(GateViews.shapeOf(BlockFace.SOUTH_EAST, opening(BlockFace.SOUTH, 10, 3, 3), ARRIVAL),
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
