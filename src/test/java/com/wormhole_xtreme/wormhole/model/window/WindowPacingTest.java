package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * How fast a viewer's windows are redrawn: the rest after a slow redraw, the catch-up of a move that
 * came too soon, what has not changed, and the shares of work (#430).
 *
 * <p>Written for the move out of {@code Windows}, on the conditions the older tests left unpinned:
 * each test below fails when the one condition it is named for is taken out of {@link WindowPacing}.
 */
class WindowPacingTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, -20.5, 0.0f, 0.0f);
    private static final WindowShape SHAPE = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 3, 3);

    private long restFactor;
    private int workPerSecond;
    private long roomBlocksPerSweep;
    private LongSupplier clock;
    private BukkitScheduler scheduler;

    /** A window source that is only what the drawing asks of one. */
    private record Source(String name, Block anchor, List<Spot> open) implements WindowSource
    {
        @Override
        public WindowShape shape()
        {
            return SHAPE;
        }

        @Override
        public Place destination()
        {
            return ARRIVAL;
        }

        @Override
        public boolean walkThrough()
        {
            return false;
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
        scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        restFactor = Windows.restFactor;
        workPerSecond = Windows.workPerSecond;
        roomBlocksPerSweep = Windows.roomBlocksPerSweep;
        clock = Windows.clock;
        WindowStore.VIEWS.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Windows.restFactor = restFactor;
        Windows.workPerSecond = workPerSecond;
        Windows.roomBlocksPerSweep = roomBlocksPerSweep;
        Windows.clock = clock;
        WindowStore.sweepRoomBlocks = 0L;
        WindowStore.workSecond = 0L;
        WindowStore.workSpent = 0;
        WindowStore.VIEWS.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private static WindowState window(final String name, final int cells)
    {
        final List<Spot> open = new ArrayList<>();
        for (int at = 0; at < cells; at++)
        {
            open.add(new Spot(at, 64, 20));
        }
        final World world = mock(World.class);
        final Block anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
        return new WindowState(new Source(name, anchor, open), null);
    }

    // --- The rest after a redraw ---

    @Test
    void aRedrawRestsThreeTimesAsLongAsItTookAndNeverLessThanThePaceForItsOpening()
    {
        Windows.restFactor = 3L;
        final int wide = Windows.SMALL_OPEN + 1;

        assertEquals(Windows.REDRAW_MILLIS, WindowPacing.restAfter(0L, wide), "a wide opening: the usual pace");
        assertEquals(Windows.REDRAW_MILLIS, WindowPacing.restAfter(0L, 0), "no opening known: the usual pace");
        assertEquals(Windows.SMALL_REDRAW_MILLIS, WindowPacing.restAfter(0L, 1), "a small one: twice as often");
        assertEquals(Windows.SMALL_REDRAW_MILLIS, WindowPacing.restAfter(0L, Windows.SMALL_OPEN),
            "the largest small opening is still small");
        assertEquals(195L, WindowPacing.restAfter(65L, wide), "a slow redraw rests three times as long");
    }

    @Test
    void theSmallestOpeningDecidesThePace()
    {
        assertEquals(2, WindowPacing.smallestOpen(List.of(window("a", 4), window("b", 2), window("c", 6))),
            "the smallest of three, wherever it comes");
        assertEquals(3, WindowPacing.smallestOpen(List.of(window("a", 3))), "one window: its own size");
        assertEquals(0, WindowPacing.smallestOpen(List.of()), "no window, no opening");
    }

    // --- Whether anything changed ---

    private static ViewerDrawing drawn(final String... mirrors)
    {
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        view.eye = 5L;
        view.stamp = "stamp";
        view.composedAt = 1000L;
        view.generation = Captures.generation();
        view.mirrors = Set.of(mirrors);
        return view;
    }

    @Test
    void aViewIsUnchangedOnlyWhileEveryThingItDependsOnIsTheSame()
    {
        final List<WindowState> seeing = List.of(window("a", 1));
        final ViewerDrawing view = drawn("a");

        assertTrue(WindowPacing.unchanged(view, seeing, 5L, "stamp", 1100L), "as it was drawn");
        assertFalse(WindowPacing.unchanged(view, seeing, 6L, "stamp", 1100L), "the eye moved");
        assertFalse(WindowPacing.unchanged(view, seeing, 5L, "other", 1100L), "a window's room changed");
        assertTrue(WindowPacing.unchanged(view, seeing, 5L, "stamp", (1000L + Windows.RESAMPLE_MILLIS) - 1L),
            "the last moment it is still fresh");
        assertFalse(WindowPacing.unchanged(view, seeing, 5L, "stamp", 1000L + Windows.RESAMPLE_MILLIS),
            "a resample later it is read again");
        assertFalse(WindowPacing.unchanged(view, seeing, 5L, "stamp", 1000L + (10 * Windows.RESAMPLE_MILLIS)),
            "long after");

        view.generation = Captures.generation() - 1;
        assertFalse(WindowPacing.unchanged(view, seeing, 5L, "stamp", 1100L), "a capture was taken since");

        assertFalse(WindowPacing.unchanged(drawn("b"), seeing, 5L, "stamp", 1100L), "other windows were seen");
    }

    // --- The catch-up of a move that came too soon ---

    @Test
    void aCatchUpWaitsForTheRestToRunOutAndNoLessThanThePace()
    {
        assertEquals(1L, WindowPacing.catchUpTicks(Windows.SMALL_REDRAW_MILLIS, 0L), "a small opening's rest: a tick");
        assertEquals(2L, WindowPacing.catchUpTicks(Windows.SMALL_REDRAW_MILLIS + 1L, 0L), "just past it: the usual two");
        assertEquals(2L, WindowPacing.catchUpTicks(Windows.REDRAW_MILLIS, 0L), "the usual pace: two ticks");
        assertEquals(4L, WindowPacing.catchUpTicks(195L, 10L), "what is left of a long rest, rounded up");
        assertEquals(3L, WindowPacing.catchUpTicks(101L, 0L), "a millisecond over two ticks is three");
        assertEquals(2L, WindowPacing.catchUpTicks(100L, 0L), "exactly two ticks is two");
        assertEquals(2L, WindowPacing.catchUpTicks(195L, 150L), "a rest nearly spent still waits the usual two");
    }

    private Player viewer(final ViewerDrawing view)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        WindowStore.VIEWS.put(player.getUniqueId(), view);
        return player;
    }

    @Test
    void aMoveTooSoonQueuesOneCatchUpForWhatIsLeftOfTheRest()
    {
        Windows.clock = () -> 10_000L;
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        view.rest = 195L;
        view.composedAt = 10_000L - 150L;
        final Player player = viewer(view);

        WindowPacing.catchUpLater(player, view);
        WindowPacing.catchUpLater(player, view);

        final ArgumentCaptor<Long> ticks = ArgumentCaptor.forClass(Long.class);
        verify(scheduler, times(1)).scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), ticks.capture());
        assertEquals(2L, ticks.getValue(), "45 milliseconds of the rest are left: the usual two ticks, not the whole rest's four");
        assertTrue(view.catchUpQueued, "and it is noted as queued, so the second move queued none");
    }

    @Test
    void aCatchUpThatCannotBeQueuedIsNotNotedAsQueued()
    {
        Windows.clock = () -> 10_000L;
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        when(scheduler.scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong()))
            .thenThrow(new IllegalStateException("no scheduler yet"));

        assertDoesNotThrow(() -> WindowPacing.catchUpLater(viewer(view), view), "startup and tests have none");
        assertFalse(view.catchUpQueued, "so the next move may try again");
    }

    private Runnable queuedFor(final Player player, final ViewerDrawing view)
    {
        Windows.clock = () -> 10_000L;
        WindowPacing.catchUpLater(player, view);
        final ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleSyncDelayedTask(any(Plugin.class), run.capture(), anyLong());
        return run.getValue();
    }

    @Test
    void theCatchUpClearsWhatWasWaitingAndDrawsNothingForAViewerWhoLeft()
    {
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        final Player player = viewer(view);
        final Runnable catchUp = queuedFor(player, view);
        view.pendingEye = new Location(mock(World.class), 1.0, 2.0, 3.0);
        when(player.isOnline()).thenReturn(false);
        assertTrue(view.catchUpQueued, "queued");

        catchUp.run();

        assertFalse(view.catchUpQueued, "no longer queued");
        assertNull(view.pendingEye, "the move that was waiting is taken");
    }

    @Test
    void theCatchUpOfAViewerWhoIsOnlineWithNoMoveWaitingDrawsNothing()
    {
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        final Player player = viewer(view);
        final Runnable catchUp = queuedFor(player, view);
        when(player.isOnline()).thenReturn(true);

        assertDoesNotThrow(catchUp::run, "no eye to draw from");
        assertFalse(view.catchUpQueued, "and it is queued no more");
    }

    @Test
    void theCatchUpOfAViewerWhoseViewIsGoneDoesNothing()
    {
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        final Player player = viewer(view);
        final Runnable catchUp = queuedFor(player, view);
        WindowStore.VIEWS.clear();

        assertDoesNotThrow(catchUp::run, "there is nothing to catch up");
    }

    // --- The shares of work ---

    private static WindowCompose.Wholes rooms(final int whole, final int clipped)
    {
        final WindowState window = window("a", 1);
        final WindowState other = window("b", 1);
        final Map<Long, BlockData> held = new HashMap<>();
        for (int at = 0; at < whole; at++)
        {
            held.put((long) at, mock(BlockData.class));
        }
        final Map<Long, BlockData> clip = new HashMap<>();
        for (int at = 0; at < clipped; at++)
        {
            clip.put((long) at, mock(BlockData.class));
        }
        return new WindowCompose.Wholes(Map.of(window, new Windows.Whole(held, 1)),
            Map.of(other, new Windows.Whole(clip, 1)));
    }

    @Test
    void theSweepsShareOfRoomsIsSpentWhenItIsReachedAndOtherwiseCountsTheRoomsAgainstIt()
    {
        Windows.roomBlocksPerSweep = 10L;
        WindowStore.sweepRoomBlocks = 10L;

        assertTrue(WindowPacing.roomShareSpent(rooms(2, 3)), "reached: wait for the next sweep");
        assertEquals(10L, WindowStore.sweepRoomBlocks, "and nothing more is counted");

        WindowStore.sweepRoomBlocks = 9L;

        assertFalse(WindowPacing.roomShareSpent(rooms(2, 3)), "one block short: this viewer's rooms are drawn");
        assertEquals(14L, WindowStore.sweepRoomBlocks, "the whole room's two and the clipped room's three are counted");
    }

    @Test
    void aSecondsShareOfWorkIsSpentWhenItIsReachedAndANewSecondStartsAfterAThousandMilliseconds()
    {
        Windows.workPerSecond = 5;
        WindowStore.workSecond = 1000L;
        WindowStore.workSpent = 5;

        assertTrue(WindowPacing.overBudget(1500L), "reached within the second");
        assertEquals(5, WindowStore.workSpent, "kept");

        WindowStore.workSpent = 4;
        assertFalse(WindowPacing.overBudget(1500L), "one short");

        WindowStore.workSpent = 5;
        assertFalse(WindowPacing.overBudget(2000L), "exactly a second later a new one has begun");
        assertEquals(2000L, WindowStore.workSecond, "noted as begun then");
        assertEquals(0, WindowStore.workSpent, "with none of its work spent");
    }
}
