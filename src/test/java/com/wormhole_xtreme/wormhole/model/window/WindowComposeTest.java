package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPackets;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPlacement;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What a viewer is shown of the windows they see: banners, openings, whole and clipped rooms, the
 * far part's cache, and the admin's full view (#430).
 *
 * <p>Written for the move out of {@code Windows}, on the conditions the older tests left
 * unpinned: each test below fails when the one condition it is named for is taken out of
 * {@link WindowCompose}. The geometry is worked out in the comments where it is not obvious.
 */
class WindowComposeTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    /** A seven-square opening facing south, looked into northwards: the face is at z 21, its right is +x. */
    private static final WindowShape WIDE = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 7, 7);

    /** The eye the clipped-room tests look from, in front of the middle of {@link #WIDE}. */
    private static final double[] EYE = { 13.5, 67.5, 24.5 };

    private World world;
    private BlockData air;
    private BlockData barrier;
    private BlockData stone;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<MirrorPackets> packets;

    /** A window source that is only what the drawing asks of one. */
    private record Source(String name, Block anchor, WindowShape shape, List<Spot> open, boolean walkThrough)
        implements WindowSource
    {
        @Override
        public Place destination()
        {
            return ARRIVAL;
        }

        @Override
        public int depth()
        {
            return 16;
        }
    }

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        air = mock(BlockData.class);
        barrier = mock(BlockData.class);
        stone = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
        bukkit.when(() -> Bukkit.createBlockData(Material.BARRIER)).thenReturn(barrier);
        packets = mockStatic(MirrorPackets.class);
        packets.when(MirrorPackets::available).thenReturn(true);
        Windows.clear();
        WindowSight.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Windows.clear();
        WindowSight.clear();
        packets.close();
        bukkit.close();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private Block anchorAt(final World in, final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(in);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        return block;
    }

    private WindowState window(final String name, final int ax, final int ay, final int az, final WindowShape shape)
    {
        return window(name, ax, ay, az, shape, null);
    }

    private WindowState window(final String name, final int ax, final int ay, final int az, final WindowShape shape,
        final Capture capture)
    {
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        return new WindowState(new Source(name, anchorAt(world, ax, ay, az), shape, open, false), capture);
    }

    private Location eye(final double x, final double y, final double z)
    {
        return new Location(world, x, y, z);
    }

    private static long key(final int x, final int y, final int z)
    {
        return Cells.key(x, y, z);
    }

    private static Windows.Whole room(final int depth, final long... keys)
    {
        final Map<Long, BlockData> blocks = new HashMap<>();
        for (final long at : keys)
        {
            blocks.put(at, mock(BlockData.class));
        }
        return new Windows.Whole(blocks, depth);
    }

    private static WindowCompose.Wholes whole(final WindowState window, final Windows.Whole room)
    {
        return new WindowCompose.Wholes(Map.of(window, room), Map.of());
    }

    private static WindowCompose.Wholes clipped(final WindowState window, final Windows.Whole room)
    {
        return new WindowCompose.Wholes(Map.of(), Map.of(window, room));
    }

    private Map<Long, BlockData> compose(final ViewerDrawing view, final Location at, final WindowState window,
        final WindowCompose.Wholes wholes, final long now, final Windows.Budget budget)
    {
        return WindowCompose.compose(view, at, List.of(window), wholes, now, new ArrayList<>(), budget);
    }

    /** Every block of the face round a window's opening, out to {@code reach}, as a solid wall. */
    private static void wallRound(final WindowState window, final int reach)
    {
        final WindowShape shape = window.shape;
        final int[] across = WindowFace.acrossSpan(shape, reach);
        final Set<Long> solid = new HashSet<>();
        for (int a = across[0]; a <= across[1]; a++)
        {
            for (int y = shape.base().y() - reach; y <= (shape.base().y() + shape.height() + reach); y++)
            {
                final long at = WindowFace.faceKey(shape, a, y);
                if (!window.openKeys.contains(at))
                {
                    solid.add(at);
                }
            }
        }
        window.solid = solid;
    }

    /** A window of {@link #WIDE} with a wall five blocks wide, so its far part is judged once per four. */
    private WindowState clippedWindow()
    {
        final WindowState window = window("gate:Abydos", 500, 70, 500, WIDE);
        window.border = 5;
        return window;
    }

    // --- Banners, openings and what a room leaves them ---

    @Test
    void bothBannersOfAPairFacingSouthAreAiredAlongTheWall()
    {
        final WindowShape pair = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 2, 1);
        final WindowState window = window("gate:Abydos", 30, 70, 40, pair);

        final Map<Long, BlockData> wanted = compose(new ViewerDrawing(world), eye(EYE[0], EYE[1], EYE[2]), window,
            whole(window, room(3)), 1000L, new Windows.Budget());

        assertSame(air, wanted.get(key(30, 70, 40)), "the first banner");
        assertSame(air, wanted.get(key(31, 70, 40)), "the second is a block to the right, looking at the wall");
        assertFalse(wanted.containsKey(key(29, 70, 40)), "and not a block to the left");
        assertSame(barrier, wanted.get(key(10, 64, 20)), "the opening is barred");
        assertSame(barrier, wanted.get(key(11, 64, 20)), "all of it");
    }

    @Test
    void bothBannersOfAPairFacingEastAreAiredAlongTheWall()
    {
        final WindowShape pair = WindowShape.through(new Spot(10, 64, 20), new Spot(1, 0, 0), ARRIVAL, 2, 1);
        final WindowState window = window("gate:Abydos", 30, 70, 40, pair);

        final Map<Long, BlockData> wanted = compose(new ViewerDrawing(world), eye(EYE[0], EYE[1], EYE[2]), window,
            whole(window, room(3)), 1000L, new Windows.Budget());

        assertSame(air, wanted.get(key(30, 70, 40)), "the first banner");
        assertSame(air, wanted.get(key(30, 70, 41)), "the second is a block to the right, looking at the wall");
        assertFalse(wanted.containsKey(key(30, 70, 39)), "and not a block to the left");
    }

    @Test
    void aRoomNotYetHeldLeavesTheBannerAndTheWallAlone()
    {
        final WindowState window = window("gate:Abydos", 30, 70, 40, WIDE);

        final Map<Long, BlockData> wanted = compose(new ViewerDrawing(world), eye(EYE[0], EYE[1], EYE[2]), window,
            new WindowCompose.Wholes(Map.of(), Map.of()), 1000L, new Windows.Budget());

        assertTrue(wanted.isEmpty(), "no hole into the real wall until the room is built: " + wanted.size());
    }

    @Test
    void aWholeRoomYieldsToTheOpeningItIsDrawnThroughAndIsCounted()
    {
        final WindowState window = window("gate:Abydos", 30, 70, 40, WIDE);
        final long inOpening = key(10, 64, 20);
        final long behind = key(10, 64, 5);
        final Windows.Whole room = room(5, inOpening, behind);
        final Windows.Budget budget = new Windows.Budget();

        final Map<Long, BlockData> wanted = compose(new ViewerDrawing(world), eye(EYE[0], EYE[1], EYE[2]), window,
            whole(window, room), 1000L, budget);

        assertSame(barrier, wanted.get(inOpening), "the opening stays barred, whatever the room holds there");
        assertSame(room.blocks().get(behind), wanted.get(behind), "the rest of the room is drawn");
        assertEquals(2, budget.fixed, "every block of the room counts");
        assertEquals(5, budget.fixedDepth, "the room's depth is the deepest so far, not the shallowest");
        assertEquals(0, budget.near, "a whole room judges nothing against the eye");
    }

    @Test
    void aWindowWithBothAWholeAndAClippedRoomDrawsTheWholeAlone()
    {
        final WindowState window = window("gate:Abydos", 30, 70, 40, WIDE);
        final Windows.Whole drawn = room(5, key(10, 64, 5));
        final Windows.Whole judged = room(5, key(14, 66, 19));
        final Windows.Budget budget = new Windows.Budget();

        final Map<Long, BlockData> wanted = compose(new ViewerDrawing(world), eye(EYE[0], EYE[1], EYE[2]), window,
            new WindowCompose.Wholes(Map.of(window, drawn), Map.of(window, judged)), 1000L, budget);

        assertFalse(wanted.containsKey(key(14, 66, 19)), "the clipped room is not judged as well");
        assertEquals(0, budget.projected, "nothing was projected");
        assertEquals(0, budget.near, "nor kept");
    }

    // --- A clipped room judged against an eye ---

    @Test
    void aClippedRoomSplitsNearFromFarAtThePointItsFarPartIsJudgedFrom()
    {
        // Border 5: a cell of four. The eye's cell middle is (14, 66, 26), kept no nearer the face than the eye.
        // The near distance is the root of 42.75, which is how far the block at z 19 is from it
        // (0.5 across, 0.5 up, 6.5 along), so that block is exactly on the line and is near.
        final double near = Math.sqrt(42.75);
        assertEquals(42.75, near * near, "the fixture's own precondition: the root squares back");
        Windows.nearDistance = near;
        final WindowState window = clippedWindow();
        final long onTheLine = key(14, 66, 19);
        final long justFar = key(14, 66, 18);
        final long wellFar = key(14, 66, 10);
        final Windows.Whole room = room(9, onTheLine, justFar, wellFar);
        final ViewerDrawing view = new ViewerDrawing(world);
        final Windows.Budget budget = new Windows.Budget();

        final Map<Long, BlockData> wanted = compose(view, eye(EYE[0], EYE[1], EYE[2]), window, clipped(window, room), 1000L,
            budget);

        for (final long at : room.blocks().keySet())
        {
            assertSame(room.blocks().get(at), wanted.get(at), "every block seen through the opening is drawn");
        }
        final WindowCompose.Far far = view.far.get("gate:Abydos");
        assertEquals(Set.of(justFar, wellFar), far.blocks().keySet(),
            "only what is past the near distance from the cell's middle is kept as the far part");
        assertEquals(14.0, far.from().getX(), 1e-9, "judged from the cell's middle, not from the eye");
        assertEquals(66.0, far.from().getY(), 1e-9, "all three ways");
        assertEquals(26.0, far.from().getZ(), 1e-9, "and");
        assertEquals(3, budget.near, "the three blocks seen are counted");
        assertEquals(3, budget.projected, "and each was projected once");
        assertEquals(9, budget.fixedDepth, "a clipped room's depth is noted too");
    }

    @Test
    void aFarPartThatStandsIsDrawnAndCountedAgainWithoutProjectingIt()
    {
        Windows.nearDistance = Math.sqrt(42.75);
        final WindowState window = clippedWindow();
        final Windows.Whole room = room(9, key(14, 66, 19), key(14, 66, 18), key(14, 66, 10));
        final ViewerDrawing view = new ViewerDrawing(world);
        final WindowCompose.Wholes wholes = clipped(window, room);
        compose(view, eye(EYE[0], EYE[1], EYE[2]), window, wholes, 1000L, new Windows.Budget());
        final Windows.Budget again = new Windows.Budget();

        final Map<Long, BlockData> wanted = compose(view, eye(EYE[0] + 0.1, EYE[1], EYE[2]), window, wholes, 1100L, again);

        assertSame(room.blocks().get(key(14, 66, 10)), wanted.get(key(14, 66, 10)), "the far part is drawn from what stood");
        assertEquals(3, again.near, "the near block judged and two far ones kept all count");
        assertEquals(1, again.projected, "only the near one was projected");
    }

    @Test
    void aBlockAlreadyWantedIsNotJudgedOrOverwritten()
    {
        final WindowState window = clippedWindow();
        final long inOpening = key(13, 67, 20);
        final Windows.Whole room = room(9, inOpening);
        final Windows.Budget budget = new Windows.Budget();

        final Map<Long, BlockData> wanted = compose(new ViewerDrawing(world), eye(EYE[0], EYE[1], EYE[2]), window,
            clipped(window, room), 1000L, budget);

        assertSame(barrier, wanted.get(inOpening), "the opening's bar is not drawn over by a block of the room");
        assertEquals(0, budget.projected, "and the block is not projected");
    }

    @Test
    void aBlockThatCannotLandOnTheOpeningIsNotProjected()
    {
        Windows.nearDistance = 1000.0;
        final WindowState window = clippedWindow();
        final Windows.Whole room = room(9, key(14, 66, 19), key(100, 66, 19));
        final Windows.Budget budget = new Windows.Budget();

        compose(new ViewerDrawing(world), eye(EYE[0], EYE[1], EYE[2]), window, clipped(window, room), 1000L, budget);

        assertEquals(1, budget.projected, "the block a hundred across is ruled out before it is projected");
        assertEquals(1, budget.near, "the other is kept");
    }

    @Test
    void aBlockPartlyBesideTheOpeningIsKeptOnlyIfItWasAlreadyDrawn()
    {
        // From (13.9, 67.5, 24.5) the block at x 17 lands across 15.87 to 17.09, and the opening ends at 17:
        // 7% of it is beside, which is more than a new block may have (5%) and less than a drawn one (15%).
        Windows.nearDistance = 1000.0;
        final WindowState window = clippedWindow();
        final long edge = key(17, 67, 19);
        final Windows.Whole room = room(9, edge);
        final ViewerDrawing fresh = new ViewerDrawing(world);

        final Map<Long, BlockData> notDrawnYet = compose(fresh, eye(13.9, EYE[1], EYE[2]), window, clipped(window, room), 1000L,
            new Windows.Budget());
        final ViewerDrawing drawnBefore = new ViewerDrawing(world);
        drawnBefore.drawn.put(edge, stone);
        final Map<Long, BlockData> kept = compose(drawnBefore, eye(13.9, EYE[1], EYE[2]), window, clipped(window, room),
            1000L, new Windows.Budget());

        assertFalse(notDrawnYet.containsKey(edge), "a block not drawn yet needs nearly all of it behind the opening");
        assertTrue(kept.containsKey(edge), "one the viewer already has is allowed to spill a little more");
    }

    @Test
    void theFarPartIsJudgedForTheWholeCellSoABlockJustPastTheOpeningsEdgeIsDrawn()
    {
        // From the cell's middle (14, 66, 26) the block at (37, 66, 0) lands across 18.42 to 18.8: past the
        // opening (which ends at 17) but within half the cell, 2, of the wall that hides it; widened by
        // 2 it is covered by the opening and the wall to x 21. Widened by a whole cell, 4, it reaches 22.8.
        final WindowState window = clippedWindow();
        final Set<Long> solid = new HashSet<>();
        for (int across = 4; across <= 20; across++)
        {
            for (int y = 56; y <= 76; y++)
            {
                final long at = WindowFace.faceKey(WIDE, across, y);
                if (!window.openKeys.contains(at))
                {
                    solid.add(at);
                }
            }
        }
        window.solid = solid;
        final long far = key(37, 66, 0);
        // And the block at (14, 100, 0) lands up 72.54 to 73, past the opening's span (to 72) the same way.
        final long high = key(14, 100, 0);
        final Windows.Whole room = room(40, far, high);

        final Map<Long, BlockData> wanted = compose(new ViewerDrawing(world), eye(EYE[0], EYE[1], EYE[2]), window,
            clipped(window, room), 1000L, new Windows.Budget());

        assertTrue(wanted.containsKey(far), "a block that lands in the wall for some eye of the cell is drawn");
        assertTrue(wanted.containsKey(high), "so is one that lands above it");
    }

    // --- The far part's cache ---

    @Test
    void aFarPartStandsForTheSameRoomCellAndTimeAndNoLonger()
    {
        final Windows.Whole room = room(1);
        final Location judged = eye(1.0, 1.0, 1.0);
        final WindowCompose.Far far = WindowCompose.Far.judged(room, judged, 4.0, judged, 1000L, Map.of());

        assertTrue(far.standsFor(room, judged, 1999L), "within the second, in the cell, for the room");
        assertTrue(far.standsFor(new Windows.Whole(room.blocks(), 1), judged, 1999L),
            "a redraw makes the record anew; the room's blocks are what count");
        assertFalse(far.standsFor(room, judged, 2000L), "a second to the millisecond is past it");
        assertFalse(far.standsFor(room(1), judged, 1999L), "another room's blocks are not this one's");
        assertFalse(far.standsFor(room, eye(5.0, 1.0, 1.0), 1999L), "the next cell along x");
        assertFalse(far.standsFor(room, eye(1.0, 5.0, 1.0), 1999L), "the next cell along y");
        assertFalse(far.standsFor(room, eye(1.0, 1.0, 5.0), 1999L), "the next cell along z");
    }

    @Test
    void aCellBelowZeroIsNotTheCellAboveIt()
    {
        final Windows.Whole room = room(1);
        final Location below = eye(-0.5, 1.0, 1.0);
        final WindowCompose.Far far = WindowCompose.Far.judged(room, below, 4.0, below, 1000L, Map.of());

        assertFalse(far.standsFor(room, eye(0.5, 1.0, 1.0), 1001L), "half a block either side of zero is two cells");
    }

    @Test
    void theFarCellIsHalfABlockLessThanTheWallUpToFourAndNeverLessThanHalf()
    {
        assertEquals(3.5, WindowCompose.farCellFor(4), "four blocks of wall");
        assertEquals(4.0, WindowCompose.farCellFor(5), "five: the widest");
        assertEquals(4.0, WindowCompose.farCellFor(9), "nine: still the widest, not the wall less half a block");
        assertEquals(0.5, WindowCompose.farCellFor(1), "one block: half a block");
        assertEquals(0.5, WindowCompose.farCellFor(0), "none: no less than half a block");
        assertEquals(0.5, WindowCompose.farCellFor(-3), "and never negative");
    }

    // --- The point the far part is judged from ---

    @Test
    void theFatEyeIsTheMiddleOfTheCellWhenThatIsNoNearerTheFace()
    {
        // Cell 2: the eye (3.3, 7.2, 24.9) is in the cell whose middle is (3, 7, 25); the face is at z 21.
        final Location from = WindowCompose.fatEye(eye(3.3, 7.2, 24.9), 2.0, WIDE);

        assertEquals(3.0, from.getX(), 1e-9, "x is the cell's middle");
        assertEquals(7.0, from.getY(), 1e-9, "so is y, which is not left at the eye's");
        assertEquals(25.0, from.getZ(), 1e-9, "and so is the depth, a little further off than the eye");
    }

    @Test
    void theFatEyeKeepsTheEyesDepthWhenTheCellsMiddleIsNearerTheFace()
    {
        final Location from = WindowCompose.fatEye(eye(3.3, 7.2, 25.9), 2.0, WIDE);

        assertEquals(25.9, from.getZ(), 1e-9, "no nearer the face than the eye: the eye's own depth");
        assertEquals(3.0, from.getX(), 1e-9, "the other two ways still the middle");
        assertEquals(7.0, from.getY(), 1e-9, "both");
    }

    @Test
    void theFatEyeKeepsTheEyesDepthWhenTheEyeIsOnTheFace()
    {
        final Location from = WindowCompose.fatEye(eye(3.3, 7.2, 21.0), 4.0, WIDE);

        assertEquals(21.0, from.getZ(), 1e-9, "on the face, not the middle of a cell that lies past it");
    }

    @Test
    void theFatEyeKeepsTheEyesDepthAlongXForAFaceThatLooksAlongX()
    {
        final WindowShape east = WindowShape.through(new Spot(10, 64, 20), new Spot(1, 0, 0), ARRIVAL, 3, 3);

        final Location from = WindowCompose.fatEye(eye(6.1, 7.2, 3.3), 2.0, east);

        assertEquals(6.1, from.getX(), 1e-9, "x is the depth here, and the middle (7) is nearer the face (10)");
        assertEquals(3.0, from.getZ(), 1e-9, "z is across the face, so it is the middle");
    }

    // --- What face a block must land on ---

    @Test
    void theSpanIsTheOpeningAndFramePlusABlockAroundThemAcrossAndUp()
    {
        final List<Spot> open = List.of(new Spot(3, 5, 7));
        final WindowState south = new WindowState(new Source("a", anchorAt(world, 0, 0, 0), WIDE, open, false), null);
        south.frame = List.of(new Spot(10, 6, 20));
        final WindowShape east = WindowShape.through(new Spot(10, 64, 20), new Spot(1, 0, 0), ARRIVAL, 3, 3);
        final WindowState along = new WindowState(new Source("b", anchorAt(world, 0, 0, 0), east, open, false), null);
        along.frame = List.of(new Spot(10, 6, 20));

        assertEquals(List.of(2.0, 12.0, 4.0, 8.0), toList(WindowCompose.spanOf(south)),
            "facing south it runs along x: from a block before the lowest to two past the highest");
        assertEquals(List.of(6.0, 22.0, 4.0, 8.0), toList(WindowCompose.spanOf(along)), "facing east it runs along z");
    }

    private static List<Double> toList(final double[] values)
    {
        final List<Double> list = new ArrayList<>();
        for (final double value : values)
        {
            list.add(value);
        }
        return list;
    }

    // --- Which windows are drawn whole ---

    private WindowState walledAt(final String name, final World in, final int ax, final int ay, final int az)
    {
        return walledAt(name, in, ax, ay, az, null);
    }

    private WindowState walledAt(final String name, final World in, final int ax, final int ay, final int az,
        final Capture capture)
    {
        final List<Spot> open = new ArrayList<>();
        WIDE.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        final WindowState window = new WindowState(new Source(name, anchorAt(in, ax, ay, az), WIDE, open, false), capture);
        wallRound(window, WindowFace.wallReach());
        window.fixed = new HashMap<>();
        window.fixedDepth = 7;
        WindowStore.ACTIVE.put(name, window);
        return window;
    }

    private WindowCompose.Wholes wholes(final WindowState... seeing)
    {
        return WindowCompose.wholesOf(List.of(seeing), 1000L, false, null);
    }

    @Test
    void aWalledWindowWithNothingNearIsDrawnWholeAndOneWithAGapBesideItIsClipped()
    {
        final WindowState walled = walledAt("gate:Abydos", world, 30, 70, 40);
        final WindowState gappy = walledAt("gate:Chulak", world, 3000, 70, 40);
        gappy.solid = Set.of();

        final WindowCompose.Wholes wholes = wholes(walled, gappy);

        assertSame(walled.fixed, wholes.whole().get(walled).blocks(), "walled and alone: whole, from its held room");
        assertEquals(7, wholes.whole().get(walled).depth(), "to the depth it was built");
        assertFalse(wholes.clipped().containsKey(walled), "and not clipped as well");
        assertSame(gappy.fixed, wholes.clipped().get(gappy).blocks(), "not walled: clipped to the eye");
        assertFalse(wholes.whole().containsKey(gappy), "and not whole");
    }

    @Test
    void aWalledWindowWithAnotherNearIsClippedAndAnotherInAnotherWorldIsNotNear()
    {
        final World elsewhere = mock(World.class);
        when(elsewhere.getName()).thenReturn("elsewhere");
        final WindowState walled = walledAt("gate:Abydos", world, 30, 70, 40);
        walledAt("gate:Chulak", elsewhere, 30, 70, 40);

        assertTrue(wholes(walled).whole().containsKey(walled), "a window in another world at the same place is not near");

        walledAt("gate:Dakara", world, 32, 70, 40);

        assertTrue(wholes(walled).clipped().containsKey(walled), "one two blocks away is");
        assertFalse(wholes(walled).whole().containsKey(walled), "and the window is not drawn whole");
    }

    @Test
    void anotherWindowExactlyTheDistanceApartIsNotNearButOneBlockNearerIs()
    {
        final int apart = (int) MirrorPlacement.apartToDrawWhole();
        final WindowState walled = walledAt("gate:Abydos", world, 30, 70, 40);
        final WindowState other = walledAt("gate:Chulak", world, 30 + apart, 70, 40);

        assertTrue(wholes(walled).whole().containsKey(walled), "exactly " + apart + " away: left to be drawn whole");

        WindowStore.ACTIVE.remove("gate:Chulak");
        walledAt("gate:Chulak", world, (30 + apart) - 1, 70, 40);

        assertTrue(wholes(walled).clipped().containsKey(walled), "a block nearer: clipped");
        assertNotSame(other, WindowStore.ACTIVE.get("gate:Chulak"), "the fixture moved the other window");
    }

    @Test
    void aWindowWithNoRoomHeldIsInNeitherSet()
    {
        final WindowState walled = walledAt("gate:Abydos", world, 30, 70, 40);
        walled.fixed = null;

        final WindowCompose.Wholes wholes = wholes(walled);

        assertNull(wholes.of(walled), "nothing to draw until it is built");
        assertTrue(wholes.whole().isEmpty(), "not whole");
        assertTrue(wholes.clipped().isEmpty(), "not clipped");
    }

    @Test
    void anAdminsViewDrawsTheNamedWindowAloneAndTheRestNotAtAll()
    {
        final WindowState named = walledAt("gate:Abydos", world, 30, 70, 40, fullCapture());
        final WindowState other = walledAt("gate:Chulak", world, 3000, 70, 40, fullCapture());

        final WindowCompose.Wholes wholes = WindowCompose.wholesOf(List.of(named, other), 1000L, false, "gate:Abydos");

        assertTrue(wholes.whole().containsKey(named), "the one the admin asked for");
        assertFalse(wholes.whole().containsKey(other), "and no other");
        assertTrue(wholes.clipped().isEmpty(), "none of them clipped");
    }

    // --- Holding a room, and the admin's whole view ---

    @Test
    void aStaleRoomIsNotRebuiltWhileThisSecondsShareOfWorkIsSpent() throws ReflectiveOperationException
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call ->
            mock(BukkitTask.class));
        PluginTestSupport.scheduler(scheduler);
        final WindowState window = window("gate:Abydos", 30, 70, 40, WIDE, fullCapture());

        WindowCompose.heldRoom(window, 1000L, false);

        assertFalse(FixedBuilds.building("gate:Abydos"), "no build was asked for with no share of work left");
        assertEquals(1000L, window.fixedUsedAt, "though the window was looked through");
    }

    private static Capture fullCapture()
    {
        final Capture capture = mock(Capture.class);
        when(capture.bounds()).thenReturn(new int[] { 90, 64, 190, 110, 75, 210 });
        when(capture.complete()).thenReturn(true);
        when(capture.keptWalk()).thenAnswer(call -> (Capture.KeptWalk) kept -> false);
        return capture;
    }

    @Test
    void anAdminsViewReachesEveryCornerOfTheCapturesBoxFromTheOpeningsFarMiddle()
    {
        // Far middle (103, 70, 200); the box runs 90..110, 64..75, 190..210: 14, 7 and 11 to its far corners.
        final WindowState window = walledAt("gate:Abydos", world, 30, 70, 40, fullCapture());

        final Windows.Whole full = WindowCompose.wholesOf(List.of(window), 1000L, false, "gate:Abydos").whole().get(window);

        assertEquals(21, full.depth(), "ceil(sqrt(14 squared + 7 squared + 11 squared)) + 1, from the farther corner each way");
    }

    @Test
    void anAdminsViewIsKeptWhileTheCaptureIsTheSameAndMadeAgainWhenItIsNot()
    {
        final Capture capture = fullCapture();
        final WindowState window = walledAt("gate:Abydos", world, 30, 70, 40, capture);
        final Windows.Whole kept = new Windows.Whole(new HashMap<>(), 3);
        window.full = kept;
        window.fullFrom = capture;

        final Windows.Whole same = WindowCompose.wholesOf(List.of(window), 1000L, false, "gate:Abydos").whole().get(window);

        assertSame(kept, same, "made from this capture already: not made again");

        window.fullFrom = fullCapture();
        final Windows.Whole later = WindowCompose.wholesOf(List.of(window), 1000L, false, "gate:Abydos").whole().get(window);

        assertNotSame(kept, later, "made from another capture: made again");
        assertEquals(21, later.depth(), "from this one");
        assertSame(capture, window.fullFrom, "and noted as made from it");
    }
}
