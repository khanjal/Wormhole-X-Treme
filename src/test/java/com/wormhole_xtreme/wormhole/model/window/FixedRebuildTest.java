package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
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
import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * A window's fixed view is built a few steps a tick, drawn old until the new one is whole, and
 * built again when the world behind its wall changes rather than once a minute (#609).
 *
 * <p>Built in one go every minute, the walk behind a mirror was a 700 to 1400 ms tick per watched
 * window, and thirty viewers left a server behind most of the time. What a viewer sees in the
 * meantime is the room they already had, and what changes a room is a block behind its wall, not
 * the players and creatures in front of it, which are drawn separately.
 */
class FixedRebuildTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    /** A Standard gate's five by five at (10, 64, 20), looked into northwards. */
    private static final WindowShape SHAPE = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 5, 5);

    /** The same far off along x, past any region of the first. */
    private static final WindowShape AWAY = WindowShape.through(new Spot(1000, 64, 20), new Spot(0, 0, -1), ARRIVAL, 5, 5);

    /** Steps a walk over the stand-in capture takes: one far block each. */
    private static final int STEPS = 40;

    private World world;
    private BlockData stone;
    private MockedStatic<Bukkit> bukkit;
    /** The ticker the builds asked for, run only while it is not cancelled. */
    private final List<Runnable> tickers = new ArrayList<>();
    private final List<Boolean> cancelled = new ArrayList<>();
    /** Steps every walk has taken. */
    private int stepsTaken;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        Windows.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(-64);
        stone = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(mock(BlockData.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Windows.clear();
        bukkit.close();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    /** A scheduler that holds the build ticker and honours its cancel. */
    private void withTicker() throws ReflectiveOperationException
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call ->
        {
            final int index = tickers.size();
            tickers.add(call.getArgument(1));
            cancelled.add(false);
            final BukkitTask task = mock(BukkitTask.class);
            doAnswer(cancel -> cancelled.set(index, true)).when(task).cancel();
            return task;
        });
        PluginTestSupport.scheduler(scheduler);
    }

    /** Runs one tick of every ticker still scheduled; @return how many ran */
    private int tick()
    {
        int ran = 0;
        for (int i = 0; i < tickers.size(); i++)
        {
            if (!cancelled.get(i))
            {
                tickers.get(i).run();
                ran++;
            }
        }
        return ran;
    }

    /** A capture whose walk takes {@link #STEPS} steps, each keeping one far block that shows behind the opening. */
    private Capture capture(final WindowShape shape)
    {
        final Capture capture = mock(Capture.class);
        when(capture.at(anyInt(), anyInt(), anyInt())).thenReturn(stone);
        when(capture.keptWalk()).thenAnswer(call ->
        {
            final int[] step = { 0 };
            return (Capture.KeptWalk) kept ->
            {
                final Spot here = shape.behind(1 + (step[0] % 10), step[0] / 10, 1);
                final Spot far = shape.farOf(here.x(), here.y(), here.z());
                kept.at(far.x(), far.y(), far.z(), false);
                stepsTaken++;
                step[0]++;
                return step[0] >= STEPS;
            };
        });
        return capture;
    }

    private GateSource gate(final String name, final WindowShape shape)
    {
        final Block anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        return new GateSource(name, anchor, shape, open, ARRIVAL, "Chulak", 16);
    }

    /** Offers windows and settles the sweep; @return the windows as held */
    private static Map<String, WindowState> held() throws ReflectiveOperationException
    {
        Windows.finish();
        return PrivateStatics.of(Windows.class, "ACTIVE");
    }

    /** Gives a held window a room built at a time, as a finished build would. */
    private static Map<Long, BlockData> builtAt(final WindowState window, final long at)
    {
        final Map<Long, BlockData> room = new HashMap<>();
        window.fixed = room;
        window.fixedFrom = window.capture;
        window.fixedFor = window.depth();
        window.fixedAt = at;
        window.fixedUsedAt = at;
        return room;
    }

    /**
     * No tick walks more of a build than its share of time, and the room drawn meanwhile is the old
     * one, swapped for the new only once it is whole.
     *
     * <p>Time is counted in calls to the clock here, a share of five: each tick takes a handful of
     * steps of the forty, never the whole walk.
     */
    @Test
    void aRebuildTakesItsShareATickAndTheOldRoomIsDrawnUntilTheNewIsWhole() throws ReflectiveOperationException
    {
        withTicker();
        final long[] clock = { 0L };
        FixedBuilds.nanos = () -> clock[0]++;
        FixedBuilds.nanosPerTick = 5L;
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        final long now = Windows.now();
        final Map<Long, BlockData> old = builtAt(window, now - Windows.BACKSTOP_MILLIS);

        assertSame(old, Windows.heldRoom(window, now, true), "the old room is drawn as its rebuild starts");
        assertTrue(FixedBuilds.building("gate:Abydos"), "and a rebuild is under way");
        int ticks = 0;
        while (FixedBuilds.building("gate:Abydos"))
        {
            final int before = stepsTaken;
            assertEquals(1, tick(), "one ticker while there is a build");
            ticks++;
            assertTrue((stepsTaken - before) <= 5, "tick " + ticks + " took " + (stepsTaken - before) + " steps of a share of five");
            if (FixedBuilds.building("gate:Abydos"))
            {
                assertSame(old, Windows.heldRoom(window, now, true), "still the old room on tick " + ticks);
            }
        }

        assertTrue(ticks >= (STEPS / 5), "the forty steps spread over " + ticks + " ticks");
        final Map<Long, BlockData> rebuilt = Windows.heldRoom(window, now, true);
        assertNotSame(old, rebuilt, "the new room swapped in once whole");
        assertEquals(STEPS, rebuilt.size(), "holding every block the walk kept");
        assertEquals(0, tick(), "and the ticker stopped once nothing is left to build");
    }

    /**
     * A block changing behind one window's wall has that window built again, and no other: not one
     * far along the same world, nor the same window for a block in front of its wall.
     */
    @Test
    void aChangedBlockBehindTheWallRebuildsThatWindowAndNoOther() throws ReflectiveOperationException
    {
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        Windows.offer(gate("gate:Chulak", AWAY), capture(AWAY));
        final Map<String, WindowState> held = held();
        final long now = Windows.now();
        final long built = now - Windows.REBUILD_GAP_MILLIS - 1L;
        builtAt(held.get("gate:Abydos"), built);
        builtAt(held.get("gate:Chulak"), built);
        // Indexed by the next sweep, as each now holds a room.
        Windows.offer(gate("gate:Abydos", SHAPE), held.get("gate:Abydos").capture);
        Windows.offer(gate("gate:Chulak", AWAY), held.get("gate:Chulak").capture);
        final Map<String, WindowState> settled = held();
        final WindowState abydos = settled.get("gate:Abydos");
        final WindowState chulak = settled.get("gate:Chulak");
        assertTrue(Windows.fixedIsFresh(abydos, now) && Windows.fixedIsFresh(chulak, now), "both fresh to begin with");

        final Spot inFront = SHAPE.behind(-2, 2, 1);
        Windows.changed(world, inFront.x(), inFront.y(), inFront.z());
        assertTrue(Windows.fixedIsFresh(abydos, now), "a block in front of the wall is no part of the view");
        final World other = mock(World.class);
        when(other.getName()).thenReturn("world_nether");
        final Spot behind = SHAPE.behind(6, 2, 1);
        Windows.changed(other, behind.x(), behind.y(), behind.z());
        assertTrue(Windows.fixedIsFresh(abydos, now), "nor the same place in another world");

        Windows.changed(world, behind.x(), behind.y(), behind.z());

        assertFalse(Windows.fixedIsFresh(abydos, now), "six blocks behind the opening: built again");
        assertTrue(Windows.fixedIsFresh(chulak, now), "a window a thousand blocks off is left as it is");
    }

    /** A change within a few seconds of the last build waits for the gap, so a flowing river does not rebuild every tick. */
    @Test
    void aChangeSoonAfterABuildWaitsForTheGap() throws ReflectiveOperationException
    {
        final Capture capture = capture(SHAPE);
        Windows.offer(gate("gate:Abydos", SHAPE), capture);
        final long now = Windows.now();
        builtAt(held().get("gate:Abydos"), now);
        // Indexed by the next sweep, as it now holds a room.
        Windows.offer(gate("gate:Abydos", SHAPE), capture);
        final WindowState window = held().get("gate:Abydos");
        final Spot behind = SHAPE.behind(3, 2, 1);

        Windows.changed(world, behind.x(), behind.y(), behind.z());

        assertTrue(Windows.fixedIsFresh(window, now + Windows.REBUILD_GAP_MILLIS - 1L), "drawn as it is within the gap");
        assertFalse(Windows.fixedIsFresh(window, now + Windows.REBUILD_GAP_MILLIS), "built again once it is past");
    }

    /** With nothing reported changed, a room is built again after the backstop, for what no event says, and not before. */
    @Test
    void theBackstopRebuildsARoomNothingReportedChanged() throws ReflectiveOperationException
    {
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        final long now = Windows.now();
        builtAt(window, now);

        assertTrue(Windows.fixedIsFresh(window, now + 60_001L), "a minute on: no longer the old refresh");
        assertTrue(Windows.fixedIsFresh(window, (now + Windows.BACKSTOP_MILLIS) - 1L), "just short of the backstop");
        assertFalse(Windows.fixedIsFresh(window, now + Windows.BACKSTOP_MILLIS), "built again at it");
    }

    /** A window taken down while its room is being built leaves no build and no ticker behind. */
    @Test
    void aWindowReleasedMidBuildLeavesNothingBehind() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 0L;
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        Windows.heldRoom(window, Windows.now(), true);
        assertTrue(FixedBuilds.building("gate:Abydos"), "a build under way");
        FixedBuilds.nanosPerTick = 1_000_000_000L;
        final long before = stepsTaken;

        Windows.release("gate:Abydos");

        assertEquals(0, FixedBuilds.count(), "its build dropped");
        assertEquals(0, tick(), "and the ticker cancelled");
        assertEquals(before, stepsTaken, "nothing more walked");
    }

    /** A build that finishes for a window offered differently meanwhile -- a fresh capture -- is not put in. */
    @Test
    void aBuildForAWindowThatChangedMeanwhileIsNotPutIn() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 0L;
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        final Map<Long, BlockData> old = builtAt(window, Windows.now() - Windows.BACKSTOP_MILLIS);
        Windows.heldRoom(window, Windows.now(), true);
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState now = held().get("gate:Abydos");
        FixedBuilds.nanosPerTick = 1_000_000_000L;

        tick();

        assertEquals(0, FixedBuilds.count(), "the build ran out");
        assertSame(old, now.fixed, "and the window, now on another capture, still holds its old room");
    }

    /**
     * The region a window is indexed under reaches its view's depth behind the opening, and its
     * depth to either side, and not in front. Placed so that the sixteenth block deep and the
     * sixteenth to the side each lie a chunk past the eighth: a region half as big would miss them.
     */
    @Test
    void aWindowsRegionCoversItsViewAndNotTheSpaceInFront()
    {
        final WindowShape shape = WindowShape.through(new Spot(0, 64, 40), new Spot(0, 0, -1), ARRIVAL, 5, 5);
        final WindowState window = new WindowState(gate("gate:Abydos", shape), capture(shape));

        FixedRegions.index(window);

        final Spot deepest = shape.behind(16, 2, 1);
        final Spot wide = shape.behind(1, 2 + 16, 1);
        assertEquals(1, deepest.z() >> 4, "sixteen deep is in the chunk past eight deep's");
        assertEquals(1, wide.x() >> 4, "sixteen to the side is in the chunk past eight's");
        assertTrue(FixedRegions.at(world, deepest.x(), deepest.z()).contains("gate:Abydos"), "sixteen deep");
        assertTrue(FixedRegions.at(world, wide.x(), wide.z()).contains("gate:Abydos"), "sixteen to the side");
        final Spot front = shape.behind(-20, 2, 1);
        assertTrue(FixedRegions.at(world, front.x(), front.z()).isEmpty(), "twenty in front: no chunk of it");
        FixedRegions.track(List.of(window), held -> false);
        assertTrue(FixedRegions.at(world, deepest.x(), deepest.z()).isEmpty(), "forgotten once it holds no room");
        assertEquals(0, FixedRegions.indexed());
    }

    /** A window the sweep no longer offers -- its chunk unloaded, its gate shut -- has its build dropped. */
    @Test
    void aWindowNoLongerOfferedHasItsBuildDropped() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 0L;
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        Windows.heldRoom(window, Windows.now(), true);
        assertTrue(FixedBuilds.building("gate:Abydos"), "a build under way");

        // The next sweep offers only another window.
        Windows.offer(gate("gate:Chulak", AWAY), capture(AWAY));
        held();

        assertFalse(FixedBuilds.building("gate:Abydos"), "its build dropped with it");
        assertEquals(0, tick(), "and the ticker with nothing left to build");
    }
}
