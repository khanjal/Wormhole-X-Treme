package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * When a held view is still the one to draw, how a finished build goes in, and the geometry round a view (#430).
 *
 * <p>Characterised before the fixed view moved out of {@code Windows}, on the conditions the older
 * rebuild, build and funnel tests left unpinned: each test below fails when the one condition it is
 * named for is taken out of {@link FixedView}.
 */
class FixedViewTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    /** An eleven-square opening facing south, looked into northwards: its right is +x. */
    private static final WindowShape BIG = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 11, 11);

    private World world;
    private Block anchor;
    private BlockData water;
    private BlockData stone;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
        water = mock(BlockData.class);
        stone = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.WATER)).thenReturn(water);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(mock(BlockData.class));
        Windows.clear();
        WindowSight.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Windows.clear();
        WindowSight.clear();
        bukkit.close();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private GateSource gate(final String name, final WindowShape shape, final Funnel funnel)
    {
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        return new GateSource(name, anchor, shape, open, ARRIVAL, "Chulak", 16, funnel);
    }

    private WindowState window(final String name, final Capture capture)
    {
        return new WindowState(gate(name, BIG, null), capture);
    }

    private static Capture solidEverywhere(final BlockData stone)
    {
        final Capture capture = mock(Capture.class);
        when(capture.complete()).thenReturn(true);
        when(capture.contains(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(capture.at(anyInt(), anyInt(), anyInt())).thenReturn(stone);
        return capture;
    }

    private static Funnel funnel()
    {
        final List<Spot> far = new ArrayList<>();
        for (int x = 99; x <= 101; x++)
        {
            for (int y = 70; y <= 73; y++)
            {
                far.add(new Spot(x, y, 199));
            }
        }
        return Funnel.of(BIG, far, 16, Material.WATER);
    }

    private static long key(final Spot at)
    {
        return Cells.key(at.x(), at.y(), at.z());
    }

    /** Holds a finished view on a window, built at a time. */
    private static void held(final WindowState window, final long at)
    {
        window.fixed = new HashMap<>();
        window.fixedFrom = window.capture;
        window.fixedFor = window.depth();
        window.fixedDepth = window.depth();
        window.fixedFunnel = window.source.funnel();
        window.fixedAt = at;
    }

    private void withTicker() throws ReflectiveOperationException
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call ->
            mock(BukkitTask.class));
        PluginTestSupport.scheduler(scheduler);
    }

    // --- Whether the held view is the one to draw ---

    @Test
    void aWindowWithNoViewHeldIsNeverFresh()
    {
        final WindowState window = window("gate:Abydos", null);
        window.fixedFor = window.depth();
        window.fixedAt = 0L;

        assertFalse(FixedView.fixedIsFresh(window, 0L), "nothing is held, however young the nothing");
    }

    @Test
    void aViewBuiltForAnotherDepthIsNotFresh()
    {
        final WindowState window = window("gate:Abydos", null);
        held(window, 0L);
        assertTrue(FixedView.fixedIsFresh(window, 0L), "as held, it is fresh");

        window.fixedFor = window.depth() + 1;

        assertFalse(FixedView.fixedIsFresh(window, 0L), "built for a depth the window no longer asks for");
    }

    @Test
    void aViewIsHeldOnlyByAWindowThatIsOfferedWithOne()
    {
        assertFalse(FixedView.holdsFixedView("gate:Abydos"), "no such window");
        final WindowState window = window("gate:Abydos", null);
        WindowStore.ACTIVE.put("gate:Abydos", window);
        assertFalse(FixedView.holdsFixedView("gate:Abydos"), "offered, but nothing built");
        held(window, 0L);
        assertTrue(FixedView.holdsFixedView("gate:Abydos"), "built");
    }

    // --- Building, and how deep a change can matter ---

    @Test
    void aChangeIsJudgedAtTheDepthAskedWhileABuildIsUnderWayAndAtTheDepthBuiltOtherwise() throws ReflectiveOperationException
    {
        withTicker();
        final Capture capture = mock(Capture.class);
        when(capture.keptWalk()).thenAnswer(call -> (Capture.KeptWalk) kept -> false);
        final WindowState window = window("gate:Abydos", capture);
        held(window, 0L);
        window.fixedDepth = 5;
        assertEquals(5, FixedView.judgedDepth(window), "no build under way: the depth the room was built to");

        FixedView.fixedView(window);

        assertTrue(FixedBuilds.building("gate:Abydos"), "a build was asked for");
        assertEquals(window.depth(), FixedView.judgedDepth(window), "under way: the depth asked, which may reach the change");
    }

    @Test
    void askingAgainWhileABuildForTheSameViewRunsStartsNoSecondBuild() throws ReflectiveOperationException
    {
        withTicker();
        final Capture capture = mock(Capture.class);
        when(capture.keptWalk()).thenAnswer(call -> (Capture.KeptWalk) kept -> false);
        final WindowState window = window("gate:Abydos", capture);
        FixedView.fixedView(window);
        final FixedBuild first = FixedBuilds.buildFor("gate:Abydos");
        assertNotNull(first);

        FixedView.fixedView(window);

        assertSame(first, FixedBuilds.buildFor("gate:Abydos"), "the same build is still the one running");
    }

    // --- Putting a finished build in ---

    private FixedBuild buildOf(final WindowState window, final long changesSeen)
    {
        return new FixedBuild(window, window.depth(), 1000, changesSeen, true);
    }

    @Test
    void aBuildFinishedLaterIsPutInOnlyForTheWindowItWasBuiltFor()
    {
        final WindowState built = window("gate:Abydos", solidEverywhere(stone));
        final FixedBuild build = buildOf(built, 0L);
        final WindowState changed = window("gate:Abydos", solidEverywhere(stone));
        WindowStore.ACTIVE.put("gate:Abydos", changed);
        Windows.clock = () -> 7000L;

        FixedView.builtLater(build);

        assertEquals(0L, changed.fixedAt, "another capture is offered now: the old build is not put in");
        WindowStore.ACTIVE.put("gate:Abydos", built);

        FixedView.builtLater(build);

        assertEquals(7000L, built.fixedAt, "the window it was built for takes it");
    }

    @Test
    void aBuildForAWindowNoLongerOfferedIsDropped()
    {
        final WindowState built = window("gate:Abydos", solidEverywhere(stone));
        Windows.clock = () -> 7000L;

        FixedView.builtLater(buildOf(built, 0L));

        assertEquals(0L, built.fixedAt, "nothing offered under its name");
    }

    @Test
    void thePutInViewKeepsTheFunnelItWasBuiltFor()
    {
        final Funnel funnel = funnel();
        final WindowState window = new WindowState(gate("gate:Abydos", BIG, funnel), solidEverywhere(stone));
        WindowStore.ACTIVE.put("gate:Abydos", window);
        assertNull(window.fixedFunnel);

        FixedView.builtLater(buildOf(window, 0L));

        assertSame(funnel, window.fixedFunnel, "so a changed funnel is noticed");
    }

    @Test
    void aChangeExactlyAWaitAfterTheLastViewWentInDoesNotLengthenTheNextWait()
    {
        final WindowState window = window("gate:Abydos", solidEverywhere(stone));
        WindowStore.ACTIVE.put("gate:Abydos", window);
        held(window, 1000L);
        WindowStore.FIRST_CHANGED.put("gate:Abydos", 1000L + FixedView.rebuildWait(window));
        Windows.clock = () -> 9000L;

        FixedView.builtLater(buildOf(window, 0L));

        assertEquals(0L, window.fixedWait, "the wait was over when it came: not churn");
    }

    @Test
    void aChangeJustInsideTheWaitAfterTheLastViewLengthensTheNextWait()
    {
        final WindowState window = window("gate:Abydos", solidEverywhere(stone));
        WindowStore.ACTIVE.put("gate:Abydos", window);
        held(window, 1000L);
        WindowStore.FIRST_CHANGED.put("gate:Abydos", (1000L + FixedView.rebuildWait(window)) - 1L);

        FixedView.builtLater(buildOf(window, 0L));

        assertEquals(FixedView.REBUILD_GAP_MILLIS, window.fixedWait, "churn: the gap, doubling from there");
    }

    @Test
    void aFirstViewIsNeverSoonAfterAnother()
    {
        final WindowState window = window("gate:Abydos", solidEverywhere(stone));
        WindowStore.ACTIVE.put("gate:Abydos", window);
        WindowStore.FIRST_CHANGED.put("gate:Abydos", 0L);

        FixedView.builtLater(buildOf(window, 0L));

        assertEquals(0L, window.fixedWait, "there was no earlier view for the change to come soon after");
    }

    @Test
    void aViewThatFoundNoUnloadedChunkResetsThePartialWait()
    {
        final WindowState window = window("gate:Abydos", solidEverywhere(stone));
        WindowStore.ACTIVE.put("gate:Abydos", window);
        held(window, 0L);
        window.fixedPartial = true;
        window.fixedPartialWait = 99L;

        FixedView.builtLater(buildOf(window, 0L));

        assertEquals(0L, window.fixedPartialWait, "nothing was unloaded this time");
        assertFalse(window.fixedPartial);
    }

    // --- A view past its limit, and the tunnel into one ---

    @Test
    void aViewPastItsLimitIsMarkedAndAViewWithinItIsNot()
    {
        final WindowState window = new WindowState(gate("gate:Abydos", BIG, null), solidEverywhere(stone));

        final Map<Long, BlockData> fits = FixedView.fixedTo(window, 3, Integer.MAX_VALUE);
        final Map<Long, BlockData> past = FixedView.fixedTo(window, 3, 1);

        assertFalse(FixedView.tooMany(fits), "within its limit");
        assertTrue(FixedView.tooMany(past), "past it");
        assertSame(FixedView.TOO_MANY, past);
    }

    @Test
    void aTunnelHasNoLayerPastItsTaper()
    {
        final Funnel funnel = funnel();
        final WindowState window = new WindowState(gate("gate:Abydos", BIG.deeper(funnel.taper() - 1), funnel),
            solidEverywhere(stone));

        final Map<Long, BlockData> view = FixedView.tunnelled(window, new HashMap<>(), 64, 0L, Integer.MAX_VALUE);

        int deepest = 0;
        for (final long cell : view.keySet())
        {
            final int layer = ((Cells.unpackX(cell) - BIG.base().x()) * BIG.into().x())
                + ((Cells.unpackZ(cell) - BIG.base().z()) * BIG.into().z());
            deepest = Math.max(deepest, layer);
        }
        assertEquals(funnel.taper() - 1, deepest, "the collar, one layer short of the taper");
    }

    @Test
    void aTunnelExactlyAtTheLimitFitsAndOneBlockOverDoesNot()
    {
        final Funnel funnel = funnel();
        final WindowState window = new WindowState(gate("gate:Abydos", BIG.deeper(funnel.taper() - 1), funnel),
            solidEverywhere(stone));
        final int size = FixedView.tunnelled(window, new HashMap<>(), 16, 0L, Integer.MAX_VALUE).size();

        assertNotSame(FixedView.TOO_MANY, FixedView.tunnelled(window, new HashMap<>(), 16, 0L, size), "exactly at the limit");
        assertSame(FixedView.TOO_MANY, FixedView.tunnelled(window, new HashMap<>(), 16, 0L, size - 1), "one over it");
    }

    @Test
    void aTunnelCellAtTheWorldsFloorIsDrawn()
    {
        final Funnel funnel = funnel();
        final WindowState window = new WindowState(gate("gate:Abydos", BIG.deeper(funnel.taper() - 1), funnel),
            solidEverywhere(stone));
        final Spot floor = window.shape.behind(5, 5, -1);
        when(world.getMinHeight()).thenReturn(floor.y());

        final Map<Long, BlockData> view = FixedView.tunnelled(window, new HashMap<>(), 16, 0L, Integer.MAX_VALUE);

        assertSame(water, view.get(key(floor)), "the floor is the lowest block a world has, not below it");
    }

    @Test
    void aTunnelCellExactlyAtTheDepthIsLeftOut()
    {
        final Funnel funnel = funnel();
        final WindowState window = new WindowState(gate("gate:Abydos", BIG.deeper(funnel.taper() - 1), funnel),
            solidEverywhere(stone));
        final double[] centre = FixedView.centreOf(window.shape);
        final Map<Long, BlockData> all = FixedView.tunnelled(window, new HashMap<>(), 64, 0L, Integer.MAX_VALUE);
        Long onTheSphere = null;
        int depth = 0;
        for (final long cell : all.keySet())
        {
            final double dx = (Cells.unpackX(cell) + 0.5) - centre[0];
            final double dy = (Cells.unpackY(cell) + 0.5) - centre[1];
            final double dz = (Cells.unpackZ(cell) + 0.5) - centre[2];
            final double d2 = (dx * dx) + (dy * dy) + (dz * dz);
            final int root = (int) Math.round(Math.sqrt(d2));
            if ((root > 0) && (root * root == d2) && ((dx != 0) || (dy != 0)))
            {
                onTheSphere = cell;
                depth = root;
                break;
            }
        }
        assertNotNull(onTheSphere, "the test needs a cell a whole number of blocks from the middle");

        final Map<Long, BlockData> view = FixedView.tunnelled(window, new HashMap<>(), depth, 0L, Integer.MAX_VALUE);

        assertFalse(view.containsKey(onTheSphere), "a view is the blocks nearer than its depth");
    }

    // --- Where the middle of an opening is, and what is inside a view ---

    @Test
    void theMiddleOfAnOpeningIsHalfwayAcrossAndHalfwayUp()
    {
        final double[] facingNorth = FixedView.centreOf(BIG);
        assertEquals(15.5, facingNorth[0], 0.0, "ten and a half across from the base, five to its right");
        assertEquals(69.5, facingNorth[1], 0.0, "half the height up");
        assertEquals(20.5, facingNorth[2], 0.0, "the middle of the block it is in");

        final WindowShape facingEast = WindowShape.through(new Spot(10, 64, 20), new Spot(1, 0, 0), ARRIVAL, 11, 11);
        final double[] east = FixedView.centreOf(facingEast);
        assertEquals(10.5, east[0], 0.0, "the middle of the block along its line");
        assertEquals(69.5, east[1], 0.0);
        assertEquals(25.5, east[2], 0.0, "five to its right, which is +z facing east");
    }

    @Test
    void aBlockIsInsideTheViewOnlyBehindTheWallAndNearerThanTheDepth()
    {
        final WindowState window = window("gate:Abydos", null);
        final int x = 15;
        final int y = 69;

        assertFalse(FixedView.insideFixed(window, x, y, 21, 10), "in front of the wall");
        assertFalse(FixedView.insideFixed(window, x, y, 20, 10), "in the wall's own plane");
        assertTrue(FixedView.insideFixed(window, x, y, 19, 10), "the first layer behind it");
        assertFalse(FixedView.insideFixed(window, x, y, 19, 0), "but not within a depth of nothing");
    }

    // --- A far-side state turned the way the window turns the far side ---

    private BlockData cloneable(final BlockData copy)
    {
        final BlockData data = mock(BlockData.class);
        when(data.clone()).thenReturn(copy);
        return data;
    }

    private WindowState turning(final Spot ahead, final boolean mirrored)
    {
        final WindowShape shape = new WindowShape(new Spot(10, 64, 20), new Spot(0, 0, -1), new Spot(100, 70, 200), ahead,
            mirrored, 1, 2);
        return new WindowState(gate("gate:Abydos", shape, null), null);
    }

    @Test
    void aWindowThatTurnsNothingGivesTheStateBackAsItIs()
    {
        final BlockData copy = mock(BlockData.class);
        final BlockData data = cloneable(copy);
        final WindowState plain = turning(new Spot(0, 0, -1), false);
        assertEquals(StructureRotation.NONE, plain.rotation);
        assertEquals(Mirror.NONE, plain.flip);

        assertSame(data, FixedView.turned(plain, data));
        assertNull(FixedView.turned(plain, null), "and nothing as nothing");
    }

    @Test
    void aWindowThatTurnsTheFarSideGivesATurnedCopyOnceAndOnlyTurned()
    {
        final BlockData copy = mock(BlockData.class);
        final BlockData data = cloneable(copy);
        final WindowState turned = turning(new Spot(1, 0, 0), false);
        assertEquals(StructureRotation.COUNTERCLOCKWISE_90, turned.rotation);
        assertEquals(Mirror.NONE, turned.flip);

        assertSame(copy, FixedView.turned(turned, data), "a copy, not the captured state");
        assertSame(copy, FixedView.turned(turned, data), "the same copy again");
        verify(copy).rotate(StructureRotation.COUNTERCLOCKWISE_90);
        verify(copy, never()).mirror(any(Mirror.class));
        verify(data).clone();
    }

    @Test
    void aReflectionGivesAFlippedCopyAndIsNotTurned()
    {
        final BlockData copy = mock(BlockData.class);
        final BlockData data = cloneable(copy);
        final WindowState flipped = turning(new Spot(0, 0, -1), true);
        assertEquals(StructureRotation.NONE, flipped.rotation);
        assertEquals(Mirror.LEFT_RIGHT, flipped.flip);

        assertSame(copy, FixedView.turned(flipped, data));
        verify(copy).mirror(Mirror.LEFT_RIGHT);
        verify(copy, never()).rotate(any(StructureRotation.class));
    }
}
