package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
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
    private WormholeXTreme plugin;
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
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        Windows.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(-64);
        stone = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        final BlockData air = mock(BlockData.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
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
            when(task.isCancelled()).thenAnswer(asked -> cancelled.get(index));
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
        return capture(shape, new int[1], false);
    }

    /**
     * The same, counting its own steps, its blocks seen air or not: seen air over a real block that is
     * not empty is drawn as air, and is what reads this world.
     */
    private Capture capture(final WindowShape shape, final int[] counted, final boolean air)
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
                kept.at(far.x(), far.y(), far.z(), air);
                stepsTaken++;
                counted[0]++;
                step[0]++;
                return step[0] >= STEPS;
            };
        });
        return capture;
    }

    /** A capture whose walk throws on its first step, as a corrupt one might. */
    private static Capture failing()
    {
        final Capture capture = mock(Capture.class);
        when(capture.keptWalk()).thenAnswer(call -> (Capture.KeptWalk) kept ->
        {
            throw new IllegalStateException("a section that will not read");
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
        return PrivateStatics.of(WindowStore.class, "ACTIVE");
    }

    /** Gives a held window a room built at a time, as a finished build would. */
    private static Map<Long, BlockData> builtAt(final WindowState window, final long at)
    {
        final Map<Long, BlockData> room = new HashMap<>();
        window.fixed = room;
        window.fixedFrom = window.capture;
        window.fixedFor = window.depth();
        window.fixedDepth = window.depth();
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
        final Map<Long, BlockData> old = builtAt(window, now - FixedView.BACKSTOP_MILLIS);

        assertSame(old, WindowCompose.heldRoom(window, now, true), "the old room is drawn as its rebuild starts");
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
                assertSame(old, WindowCompose.heldRoom(window, now, true), "still the old room on tick " + ticks);
            }
        }

        assertTrue(ticks >= (STEPS / 5), "the forty steps spread over " + ticks + " ticks");
        final Map<Long, BlockData> rebuilt = WindowCompose.heldRoom(window, now, true);
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
        final long built = now - FixedView.REBUILD_GAP_MILLIS - 1L;
        builtAt(held.get("gate:Abydos"), built);
        builtAt(held.get("gate:Chulak"), built);
        // Indexed by the next sweep, as each now holds a room.
        Windows.offer(gate("gate:Abydos", SHAPE), held.get("gate:Abydos").capture);
        Windows.offer(gate("gate:Chulak", AWAY), held.get("gate:Chulak").capture);
        final Map<String, WindowState> settled = held();
        final WindowState abydos = settled.get("gate:Abydos");
        final WindowState chulak = settled.get("gate:Chulak");
        assertTrue(FixedView.fixedIsFresh(abydos, now) && FixedView.fixedIsFresh(chulak, now), "both fresh to begin with");

        final Spot inFront = SHAPE.behind(-2, 2, 1);
        Windows.changed(world, inFront.x(), inFront.y(), inFront.z());
        assertTrue(FixedView.fixedIsFresh(abydos, now), "a block in front of the wall is no part of the view");
        final World other = mock(World.class);
        when(other.getName()).thenReturn("world_nether");
        final Spot behind = SHAPE.behind(6, 2, 1);
        Windows.changed(other, behind.x(), behind.y(), behind.z());
        assertTrue(FixedView.fixedIsFresh(abydos, now), "nor the same place in another world");

        Windows.changed(world, behind.x(), behind.y(), behind.z());

        assertFalse(FixedView.fixedIsFresh(abydos, now), "six blocks behind the opening: built again");
        assertTrue(FixedView.fixedIsFresh(chulak, now), "a window a thousand blocks off is left as it is");
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

        assertTrue(FixedView.fixedIsFresh(window, now + FixedView.REBUILD_GAP_MILLIS - 1L), "drawn as it is within the gap");
        assertFalse(FixedView.fixedIsFresh(window, now + FixedView.REBUILD_GAP_MILLIS), "built again once it is past");
    }

    /** With nothing reported changed, a room is built again after the backstop, for what no event says, and not before. */
    @Test
    void theBackstopRebuildsARoomNothingReportedChanged() throws ReflectiveOperationException
    {
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        final long now = Windows.now();
        builtAt(window, now);

        assertTrue(FixedView.fixedIsFresh(window, now + 60_001L), "a minute on: no longer the old refresh");
        assertTrue(FixedView.fixedIsFresh(window, (now + FixedView.BACKSTOP_MILLIS) - 1L), "just short of the backstop");
        assertFalse(FixedView.fixedIsFresh(window, now + FixedView.BACKSTOP_MILLIS), "built again at it");
    }

    /** A window taken down while its room is being built leaves no build and no ticker behind. */
    @Test
    void aWindowReleasedMidBuildLeavesNothingBehind() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 0L;
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        WindowCompose.heldRoom(window, Windows.now(), true);
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
        final Map<Long, BlockData> old = builtAt(window, Windows.now() - FixedView.BACKSTOP_MILLIS);
        WindowCompose.heldRoom(window, Windows.now(), true);
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
        WindowCompose.heldRoom(window, Windows.now(), true);
        assertTrue(FixedBuilds.building("gate:Abydos"), "a build under way");

        // The next sweep offers only another window.
        Windows.offer(gate("gate:Chulak", AWAY), capture(AWAY));
        held();

        assertFalse(FixedBuilds.building("gate:Abydos"), "its build dropped with it");
        assertEquals(0, tick(), "and the ticker with nothing left to build");
    }

    /**
     * A build that throws is dropped and said once, its window keeping its old room, and every other
     * build carries on: left at the head of the queue, it threw every tick and starved the rest.
     */
    @Test
    void aBuildThatThrowsIsDroppedOnceAndTheOthersCarryOn() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 1_000_000_000L;
        Windows.offer(gate("gate:Abydos", SHAPE), failing());
        Windows.offer(gate("gate:Chulak", AWAY), capture(AWAY));
        final Map<String, WindowState> held = held();
        final long now = Windows.now();
        final Map<Long, BlockData> old = builtAt(held.get("gate:Abydos"), now - FixedView.BACKSTOP_MILLIS);
        WindowCompose.heldRoom(held.get("gate:Abydos"), now, true);
        WindowCompose.heldRoom(held.get("gate:Chulak"), now, true);

        tick();
        WindowCompose.heldRoom(held.get("gate:Abydos"), now, true);
        tick();

        assertSame(old, held.get("gate:Abydos").fixed, "the failed window keeps its old room");
        assertEquals(STEPS, held.get("gate:Chulak").fixed.size(), "the other window's room was built and put in");
        assertEquals(0, FixedBuilds.count(), "nothing left in the queue");
        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), anyString(), any(Throwable.class));
    }

    /** A handover that throws for one finished build does not keep the others from theirs. */
    @Test
    void aHandoverThatThrowsLeavesTheOthersHandedOver() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 1_000_000_000L;
        final WindowState first = new WindowState(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState second = new WindowState(gate("gate:Chulak", AWAY), capture(AWAY));
        final List<String> handed = new ArrayList<>();
        FixedBuilds.request(new FixedBuild(first, 16, 1000, 0L, true), build ->
        {
            throw new IllegalStateException("could not put it in");
        });
        FixedBuilds.request(new FixedBuild(second, 16, 1000, 0L, true), build -> handed.add(build.window.name()));

        tick();

        assertEquals(List.of("gate:Chulak"), handed, "the second handed over after the first threw");
        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), anyString(), any(Throwable.class));
    }

    /**
     * Builds take turns across ticks: with a share that allows one step a tick, two builds each move
     * on every other tick, where starting from the first each tick moved only the first.
     */
    @Test
    void buildsTakeTurnsAcrossTicks() throws ReflectiveOperationException
    {
        withTicker();
        final long[] clock = { 0L };
        FixedBuilds.nanos = () -> clock[0]++;
        FixedBuilds.nanosPerTick = 2L;
        final int[] first = new int[1];
        final int[] second = new int[1];
        FixedBuilds.request(new FixedBuild(new WindowState(gate("gate:Abydos", SHAPE), capture(SHAPE, first, false)), 16, 1000,
            0L, true), build -> { });
        FixedBuilds.request(new FixedBuild(new WindowState(gate("gate:Chulak", AWAY), capture(AWAY, second, false)), 16, 1000,
            0L, true), build -> { });

        for (int i = 0; i < 6; i++)
        {
            tick();
        }

        assertEquals(3, first[0], "one step every other tick for the first");
        assertEquals(3, second[0], "and for the second");
    }

    /** Asks for a room's rebuild, notes a change in its view, and runs the build to the end. */
    private void rebuildWithAChangeDuring(final WindowState window, final long[] time, final long building)
    {
        WindowCompose.heldRoom(window, time[0], true);
        final Spot behind = SHAPE.behind(4, 2, 1);
        Windows.changed(world, behind.x(), behind.y(), behind.z());
        time[0] += building;
        tick();
    }

    /**
     * A block changing while a room is built leaves the new room stale once it is in, and the next
     * rebuild waits from when it went in, not from when its build began, for at least four times the
     * build's own stepped time; a build with nothing changed meanwhile stays fresh until the backstop.
     *
     * <p>Each step is timed at a tenth of a second here, so the 41 steps (40 of the walk, one to check
     * the cap) are 4.1 s of building; the seven seconds of wall time between ask and finish, which
     * other windows' builds would share, count for nothing.
     */
    @Test
    void aChangeDuringABuildLeavesTheNewRoomStaleAfterTheWaitFromWhenItWentIn() throws ReflectiveOperationException
    {
        withTicker();
        final long[] nanos = { 0L };
        FixedBuilds.nanos = () -> nanos[0] += 100_000_000L;
        FixedBuilds.nanosPerTick = Long.MAX_VALUE / 4;
        final long[] time = { 1_000_000L };
        Windows.clock = () -> time[0];
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        WindowCompose.heldRoom(window, time[0], true);
        tick();
        final Map<Long, BlockData> first = window.fixed;
        assertTrue(FixedView.fixedIsFresh(window, time[0] + FixedView.BACKSTOP_MILLIS - 1L),
            "nothing changed while it was built: fresh to the backstop");

        time[0] += FixedView.BACKSTOP_MILLIS;
        rebuildWithAChangeDuring(window, time, 7_000L);
        final long putIn = time[0];

        assertNotSame(first, window.fixed, "the new room is in");
        assertEquals(putIn, window.fixedAt, "put in when it finished");
        assertEquals(4_100L, window.fixedTook, "its 41 steps at a tenth of a second, not the seven seconds it waited");
        assertEquals(16_400L, FixedView.rebuildWait(window), "four times what its steps took");
        assertTrue(FixedView.fixedIsFresh(window, putIn + 16_399L), "drawn as it is until then");
        assertFalse(FixedView.fixedIsFresh(window, putIn + 16_400L), "then built again for the change made during its build");
    }

    /** Changes that keep coming double the wait, to two minutes at most; a build with none meanwhile puts it back to the gap. */
    @Test
    void theWaitDoublesWhileChangesKeepComingAndResetsOnceTheyStop() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 1_000_000_000L;
        final long[] time = { 1_000_000L };
        Windows.clock = () -> time[0];
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        WindowCompose.heldRoom(window, time[0], true);
        tick();
        final Spot behind = SHAPE.behind(4, 2, 1);
        Windows.changed(world, behind.x(), behind.y(), behind.z());
        final long[] waits = new long[10];
        for (int i = 0; i < waits.length; i++)
        {
            time[0] += FixedView.rebuildWait(window);
            rebuildWithAChangeDuring(window, time, 0L);
            waits[i] = FixedView.rebuildWait(window);
        }

        assertEquals(FixedView.REBUILD_GAP_MILLIS, waits[0], "the first: the gap");
        assertEquals(2 * FixedView.REBUILD_GAP_MILLIS, waits[1], "then doubled");
        assertEquals(4 * FixedView.REBUILD_GAP_MILLIS, waits[2], "and doubled again");
        assertEquals(FixedView.MOST_REBUILD_WAIT_MILLIS, waits[9], "capped at two minutes");
        assertEquals(120_000L, FixedView.MOST_REBUILD_WAIT_MILLIS, "two minutes, so a real change waits no longer behind churn");

        time[0] += FixedView.rebuildWait(window);
        WindowCompose.heldRoom(window, time[0], true);
        tick();
        assertEquals(FixedView.REBUILD_GAP_MILLIS, FixedView.rebuildWait(window), "built with nothing changing meanwhile: back to the gap");

        // A change a second after each room goes in, never during a build, is changes coming on too.
        for (int i = 0; i < 2; i++)
        {
            time[0] += 1000L;
            Windows.changed(world, behind.x(), behind.y(), behind.z());
            time[0] += FixedView.rebuildWait(window);
            WindowCompose.heldRoom(window, time[0], true);
            tick();
        }
        assertEquals(2 * FixedView.REBUILD_GAP_MILLIS, FixedView.rebuildWait(window), "a change soon after each went in doubles it too");
    }

    /** Two changes, and a look while the rebuild runs, are one rebuild. */
    @Test
    void twoChangesAndALookMidBuildAreOneRebuild() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 0L;
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        final long now = Windows.now();
        builtAt(window, now - FixedView.REBUILD_GAP_MILLIS - 1L);
        FixedRegions.index(window);
        final Spot behind = SHAPE.behind(4, 2, 1);
        Windows.changed(world, behind.x(), behind.y(), behind.z());
        Windows.changed(world, behind.x(), behind.y() + 1, behind.z());

        WindowCompose.heldRoom(window, now, true);
        final FixedBuild running = FixedBuilds.buildFor("gate:Abydos");
        WindowCompose.heldRoom(window, now, true);

        assertEquals(1, FixedBuilds.count(), "one build");
        assertSame(running, FixedBuilds.buildFor("gate:Abydos"), "the same one, not begun again");
    }

    /**
     * Only a block inside the view's depth, two blocks to spare, counts: not one in the same chunk past
     * it. Checked both sides of the world's origin, where a chunk's number rounds the other way.
     */
    @Test
    void onlyABlockWithinTheDepthAndTwoMoreCounts() throws ReflectiveOperationException
    {
        for (final Spot base : new Spot[] { new Spot(10, 64, 20), new Spot(-40, 64, -40) })
        {
            Windows.clear();
            final WindowShape shape = WindowShape.through(base, new Spot(0, 0, -1), ARRIVAL, 5, 5);
            Windows.offer(gate("gate:Abydos", shape), capture(shape));
            final WindowState window = held().get("gate:Abydos");
            final long now = Windows.now();
            builtAt(window, now - FixedView.REBUILD_GAP_MILLIS - 1L);
            FixedRegions.index(window);
            // On the opening's middle column and row: depth 16, and two more.
            final Spot outside = shape.behind(18, 2, 2);
            final Spot inside = shape.behind(17, 2, 2);
            assertEquals(outside.z() >> 4, inside.z() >> 4, "the same chunk, at " + base);

            Windows.changed(world, outside.x(), outside.y(), outside.z());
            assertTrue(FixedView.fixedIsFresh(window, now), "eighteen deep is past it, at " + base);
            Windows.changed(world, inside.x(), inside.y(), inside.z());
            assertFalse(FixedView.fixedIsFresh(window, now), "seventeen deep is inside, at " + base);
        }
    }

    /** A block broken behind the wall, heard by the listener itself, makes that view stale; one in front does not. */
    @Test
    void aBlockBrokenBehindTheWallMakesTheViewStale() throws ReflectiveOperationException
    {
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        final long now = Windows.now();
        builtAt(window, now - FixedView.REBUILD_GAP_MILLIS - 1L);
        FixedRegions.index(window);
        final ViewChangeListener listener = new ViewChangeListener();

        listener.onBreak(breaking(SHAPE.behind(-3, 2, 1)));
        assertTrue(FixedView.fixedIsFresh(window, now), "a block in front of the wall");
        listener.onBreak(breaking(SHAPE.behind(5, 2, 1)));
        assertFalse(FixedView.fixedIsFresh(window, now), "a block behind it");
    }

    private BlockBreakEvent breaking(final Spot at)
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(at.x());
        when(block.getY()).thenReturn(at.y());
        when(block.getZ()).thenReturn(at.z());
        final BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(block);
        return event;
    }

    /**
     * A room built over a chunk that was not loaded drew its air there blind, and is built again
     * fifteen seconds on, then thirty, then every minute while chunks stay unloaded, rather than at
     * the ten-minute backstop; a build that finds them all loaded goes back to the backstop.
     *
     * <p>The load test's first look at a mirror came while the chunks behind it were still loading:
     * that room was air through them, past its cap, cut a quarter shallower, and with no rebuild for
     * ten minutes every viewer's redraw walked it.
     */
    @Test
    void aRoomBuiltOverAnUnloadedChunkIsBuiltAgainSoonerThenLessOften() throws ReflectiveOperationException
    {
        final long[] time = { 1_000_000L };
        Windows.clock = () -> time[0];
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE, new int[1], true));
        final WindowState window = held().get("gate:Abydos");
        final long[] waits = new long[4];
        for (int i = 0; i < waits.length; i++)
        {
            WindowCompose.heldRoom(window, time[0], true);
            assertTrue(window.fixedPartial, "its build read a chunk that was not loaded");
            waits[i] = window.fixedPartialWait;
            assertTrue(FixedView.fixedIsFresh(window, (window.fixedAt + waits[i]) - 1L), "fresh until the wait is up");
            assertFalse(FixedView.fixedIsFresh(window, window.fixedAt + waits[i]), "then built again");
            time[0] = window.fixedAt + waits[i];
        }

        assertEquals(FixedView.FIRST_PARTIAL_RETRY_MILLIS, waits[0], "fifteen seconds first");
        assertEquals(2 * FixedView.FIRST_PARTIAL_RETRY_MILLIS, waits[1], "then thirty");
        assertEquals(FixedView.PARTIAL_BACKSTOP_MILLIS, waits[3], "then a minute at most");

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        WindowCompose.heldRoom(window, time[0], true);
        assertFalse(window.fixedPartial, "every chunk loaded");
        assertTrue(FixedView.fixedIsFresh(window, (window.fixedAt + FixedView.BACKSTOP_MILLIS) - 1L), "back to the backstop");
    }

    /**
     * A change that made a room stale is answered by the build it started: with nothing changed while
     * that build ran, the new room is fresh until the backstop, not built again at the next gap.
     */
    @Test
    void aChangeBeforeABuildIsAnsweredByIt() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 1_000_000_000L;
        final long[] time = { 1_000_000L };
        Windows.clock = () -> time[0];
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        WindowCompose.heldRoom(window, time[0], true);
        tick();
        final Spot behind = SHAPE.behind(4, 2, 1);
        Windows.changed(world, behind.x(), behind.y(), behind.z());
        time[0] += FixedView.REBUILD_GAP_MILLIS;
        WindowCompose.heldRoom(window, time[0], true);
        tick();

        assertTrue(FixedView.fixedIsFresh(window, (window.fixedAt + FixedView.BACKSTOP_MILLIS) - 1L),
            "the change before it began is in it: fresh to the backstop");
    }

    /**
     * A ticker cancelled by something else -- the plugin disabling -- is not taken for a running one:
     * the next build schedules its own.
     */
    @Test
    void aTickerCancelledElsewhereIsReplaced() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 1_000_000_000L;
        FixedBuilds.request(new FixedBuild(new WindowState(gate("gate:Abydos", SHAPE), capture(SHAPE)), 16, 1000, 0L, true),
            build -> { });
        cancelled.set(0, true);
        final List<String> handed = new ArrayList<>();
        FixedBuilds.request(new FixedBuild(new WindowState(gate("gate:Chulak", AWAY), capture(AWAY)), 16, 1000, 0L, true),
            build -> handed.add(build.window.name()));

        tick();

        assertEquals(2, tickers.size(), "a second ticker scheduled");
        assertTrue(handed.contains("gate:Chulak"), "and it ran the build");
    }

    /**
     * A room built between sweeps is carried by the next sweep's offer, and not built again.
     *
     * <p>Put in by the ticker, a first room was never marked used: the next sweep's offer, which keeps
     * a room only if it was used within the minute, threw it away, and the window was built again,
     * forever. A viewer standing still saw the banner for good while the ticker spent its share every
     * tick; only a step that redrew them outside a sweep broke the loop.
     */
    @Test
    void aRoomBuiltBetweenSweepsIsCarriedByTheNext() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 1_000_000_000L;
        final Capture capture = capture(SHAPE);
        Windows.offer(gate("gate:Abydos", SHAPE), capture);
        final WindowState window = held().get("gate:Abydos");
        WindowCompose.heldRoom(window, Windows.now(), true);
        tick();
        final Map<Long, BlockData> room = window.fixed;
        assertEquals(STEPS, room.size(), "built and put in by the ticker");

        Windows.offer(gate("gate:Abydos", SHAPE), capture);
        final WindowState next = held().get("gate:Abydos");

        assertSame(room, next.fixed, "the same room, carried by the next sweep");
        assertTrue(FixedView.fixedIsFresh(next, Windows.now()), "and drawn as it is");
        WindowCompose.heldRoom(next, Windows.now(), true);
        assertEquals(0, FixedBuilds.count(), "no build asked for again");
    }

    /**
     * A room cut shallower to fit is made stale only by a change within the depth it was cut to, and
     * two to spare: a sheep a hundred blocks behind a wall cut to sixty kept building it again.
     */
    @Test
    void aChangePastTheDepthARoomWasCutToLeavesItAlone() throws ReflectiveOperationException
    {
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE));
        final WindowState window = held().get("gate:Abydos");
        final long now = Windows.now();
        builtAt(window, now - FixedView.REBUILD_GAP_MILLIS - 1L);
        window.fixedDepth = 8;
        FixedRegions.index(window);
        final Spot past = SHAPE.behind(12, 2, 2);
        final Spot within = SHAPE.behind(9, 2, 2);

        Windows.changed(world, past.x(), past.y(), past.z());
        assertTrue(FixedView.fixedIsFresh(window, now), "twelve deep, past a cut to eight and two");
        Windows.changed(world, within.x(), within.y(), within.z());
        assertFalse(FixedView.fixedIsFresh(window, now), "nine deep is within it");
    }

    /**
     * A scheduler that refuses -- Bukkit's for a disabled plugin -- builds nothing: taken for no
     * scheduler at all, it built the whole room in the tick that asked, the old spike back.
     */
    @Test
    void aSchedulerThatRefusesBuildsNothing() throws ReflectiveOperationException
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
            .thenThrow(new IllegalPluginAccessException("Plugin attempted to register task while disabled"));
        PluginTestSupport.scheduler(scheduler);
        final int[] steps = new int[1];
        Windows.offer(gate("gate:Abydos", SHAPE), capture(SHAPE, steps, false));
        final WindowState window = held().get("gate:Abydos");

        WindowCompose.heldRoom(window, Windows.now(), true);

        assertEquals(0, steps[0], "not a step walked");
        assertEquals(0, FixedBuilds.count(), "nothing queued");
        assertNull(window.fixed, "no room");
    }

    /**
     * Each step reads its columns afresh: a chunk loaded when one step read it may be gone by the next,
     * and a column remembered across the two would read a block of an unloaded chunk, loading it.
     */
    @Test
    void eachStepReadsItsColumnsAfresh()
    {
        final boolean[] loaded = { true };
        when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(call -> loaded[0]);
        final Block solid = mock(Block.class);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(200);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(solid);
        final Spot here = SHAPE.behind(3, 2, 1);
        final Spot far = SHAPE.farOf(here.x(), here.y(), here.z());
        final Capture capture = mock(Capture.class);
        when(capture.keptWalk()).thenAnswer(call ->
        {
            final int[] step = { 0 };
            return (Capture.KeptWalk) kept ->
            {
                // The same column both steps, seen air a block apart.
                kept.at(far.x(), far.y() + step[0], far.z(), true);
                step[0]++;
                return step[0] >= 2;
            };
        });
        final FixedBuild build = new FixedBuild(new WindowState(gate("gate:Abydos", SHAPE), capture), 16, 1000, 0L, true);

        build.step();
        loaded[0] = false;
        build.step();

        assertTrue(build.sawUnloaded(), "the second step read the chunk as it is now: not loaded");
    }

    /**
     * A room put in counts as used when it goes in, not only when it was asked for: a build that ran
     * longer than the minute an unused room is kept -- many windows building at once -- was otherwise
     * thrown away by the next sweep as soon as it went in.
     */
    @Test
    void aRoomPutInAfterALongBuildIsCarried() throws ReflectiveOperationException
    {
        withTicker();
        FixedBuilds.nanosPerTick = 1_000_000_000L;
        final long[] time = { 1_000_000L };
        Windows.clock = () -> time[0];
        final Capture capture = capture(SHAPE);
        Windows.offer(gate("gate:Abydos", SHAPE), capture);
        final WindowState window = held().get("gate:Abydos");
        WindowCompose.heldRoom(window, time[0], true);
        time[0] += 61_000L;
        tick();
        final Map<Long, BlockData> room = window.fixed;

        Windows.offer(gate("gate:Abydos", SHAPE), capture);

        assertSame(room, held().get("gate:Abydos").fixed, "carried: it went in a moment ago");
    }
}
