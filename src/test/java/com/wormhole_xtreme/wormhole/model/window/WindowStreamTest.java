package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What a viewer is sent, and in what order (#430).
 *
 * <p>Characterised before the sending moved out of {@code Windows}: each test below fails when the
 * one condition it is named for is taken out of {@link WindowStream}.
 */
class WindowStreamTest
{
    private World world;
    private Player viewer;
    private ViewerDrawing view;
    private BlockData stone;
    private BlockData glass;
    private final List<BlockState> states = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        Windows.clear();
        Windows.streamPerTick = 2500;
        world = mock(World.class);
        when(world.getName()).thenReturn("streaming");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final BlockState state = mock(BlockState.class);
            states.add(state);
            final Block block = mock(Block.class);
            when(block.getState()).thenReturn(state);
            return block;
        });
        viewer = mock(Player.class);
        view = new ViewerDrawing(world);
        stone = mock(BlockData.class);
        glass = mock(BlockData.class);
    }

    @AfterEach
    void tearDown()
    {
        Windows.clear();
    }

    private static Location eyeAt(final double x, final double y, final double z)
    {
        return new Location(null, x, y, z);
    }

    @SuppressWarnings("unchecked")
    private List<BlockState> sent()
    {
        final ArgumentCaptor<Collection<BlockState>> batch = ArgumentCaptor.forClass(Collection.class);
        verify(viewer).sendBlockChanges(batch.capture());
        return new ArrayList<>(batch.getValue());
    }

    @Test
    void theNearestCellsAreSentFirstWhateverOrderTheyAreHeldIn()
    {
        final long far = Cells.key(100, 64, 0);
        final long middle = Cells.key(40, 64, 0);
        final long near = Cells.key(2, 64, 0);
        final Set<Long> held = new LinkedHashSet<>(List.of(far, middle, near));

        assertArrayEquals(new long[] { near, middle, far }, WindowStream.nearestFirst(held, eyeAt(0.5, 64.5, 0.5)),
            "sixteen-block bands from the eye, nearest first, not the order they were held in");
    }

    @Test
    void cellsInOneBandAreGroupedByTheChunkSectionTheyAreIn()
    {
        final long first = Cells.key(1, 64, 1);
        final long otherChunk = Cells.key(-3, 64, -3);
        final long second = Cells.key(2, 64, 2);
        final Set<Long> held = new LinkedHashSet<>(List.of(first, otherChunk, second));

        assertArrayEquals(new long[] { first, second, otherChunk },
            WindowStream.nearestFirst(held, eyeAt(0.5, 64.5, 0.5)),
            "all three are in the first band, so the two in one chunk section come together");
    }

    @Test
    void aBandIsSixteenBlocksWideAndTheNextBandSortsAfterIt()
    {
        final long inside = Cells.key(16, 64, 0);
        final long outside = Cells.key(15, 64, 0);
        final Set<Long> held = new LinkedHashSet<>(List.of(outside, inside));

        assertArrayEquals(new long[] { inside, outside },
            WindowStream.nearestFirst(held, eyeAt(32.0, 64.5, 0.5)),
            "15.5 blocks out is in the first band and 16.5 the second, though the second is in the lower chunk");
    }

    @Test
    void cellsInOneBandAndChunkAreGroupedByVerticalSection()
    {
        final long low = Cells.key(1, 64, 1);
        final long high = Cells.key(1, 80, 1);
        final long beside = Cells.key(2, 64, 2);
        final Set<Long> held = new LinkedHashSet<>(List.of(low, high, beside));

        assertArrayEquals(new long[] { low, beside, high }, WindowStream.nearestFirst(held, eyeAt(0.5, 64.5, 0.5)),
            "the one in the next section up comes after the two in the eye's");
    }

    @Test
    void cellsInOneBandAreGroupedByTheChunkAlongZ()
    {
        final long first = Cells.key(1, 64, 1);
        final long north = Cells.key(1, 64, 20);
        final long second = Cells.key(2, 64, 2);
        final Set<Long> held = new LinkedHashSet<>(List.of(first, north, second));

        assertArrayEquals(new long[] { first, second, north }, WindowStream.nearestFirst(held, eyeAt(0.5, 64.5, 0.5)),
            "the one in the next chunk along z comes after the two in the first");
    }

    @Test
    void aCellJustPastSixteenBlocksSortsAfterOneInTheFirstBandWhateverItsChunk()
    {
        final long past = Cells.key(20, 64, 0);
        final long within = Cells.key(-5, 64, 0);
        final Set<Long> held = new LinkedHashSet<>(List.of(past, within));

        assertArrayEquals(new long[] { within, past }, WindowStream.nearestFirst(held, eyeAt(0.5, 64.5, 0.5)),
            "nineteen blocks out is the second band, though its chunk would sort it first");
    }

    @Test
    void nothingIsFreshWhenTheEyeHasStayedInItsChunk()
    {
        final Set<Long> fresh = WindowStream.freshChunks(viewer, Cells.chunkKey(3, 4), Cells.chunkKey(3, 4));

        assertTrue(fresh.isEmpty(), "no chunk is handed over");
        assertFalse(fresh == WindowStream.ALL_CHUNKS, "and it is not the every-chunk marker");
    }

    @Test
    void everyChunkIsFreshOnAFirstDrawing()
    {
        when(viewer.getWorld()).thenReturn(world);
        when(world.getViewDistance()).thenReturn(8);
        when(viewer.getClientViewDistance()).thenReturn(8);

        final Set<Long> fresh = WindowStream.freshChunks(viewer, Long.MIN_VALUE, Cells.chunkKey(3, 4));

        assertSame(WindowStream.ALL_CHUNKS, fresh, "nothing was drawn before, so there is no old chunk to compare");
    }

    @Test
    void everyChunkIsFreshWhenTheServerDoesNotSayHowFarItSends()
    {
        when(viewer.getWorld()).thenReturn(world);
        when(world.getViewDistance()).thenReturn(0);
        when(viewer.getClientViewDistance()).thenReturn(10);

        assertSame(WindowStream.ALL_CHUNKS, WindowStream.freshChunks(viewer, Cells.chunkKey(0, 0), Cells.chunkKey(1, 0)),
            "a reach of nothing says nothing about which chunks arrived");
    }

    @Test
    void steppingOneChunkEastHandsOverTheStripAtTheFarEdgeAndNoMore()
    {
        when(viewer.getWorld()).thenReturn(world);
        when(world.getViewDistance()).thenReturn(2);
        when(viewer.getClientViewDistance()).thenReturn(10);

        final Set<Long> fresh = WindowStream.freshChunks(viewer, Cells.chunkKey(0, 0), Cells.chunkKey(1, 0));

        final Set<Long> strip = new HashSet<>();
        for (int z = -3; z <= 3; z++)
        {
            strip.add(Cells.chunkKey(4, z));
        }
        assertEquals(strip, fresh, "the shorter of the two distances, 2, and one over for the edge: x = 4, z = -3..3");
    }

    @Test
    void steppingOneChunkAlongZHandsOverTheStripToo()
    {
        when(viewer.getWorld()).thenReturn(world);
        when(world.getViewDistance()).thenReturn(2);
        when(viewer.getClientViewDistance()).thenReturn(10);

        final Set<Long> fresh = WindowStream.freshChunks(viewer, Cells.chunkKey(0, 0), Cells.chunkKey(0, 1));

        final Set<Long> strip = new HashSet<>();
        for (int x = -3; x <= 3; x++)
        {
            strip.add(Cells.chunkKey(x, 4));
        }
        assertEquals(strip, fresh, "z = 4 for x = -3..3, and nothing from the rows already held");
    }

    @Test
    void whatIsSentCountsAgainstTheServersShareOfWork()
    {
        WindowStore.workSpent = 0;
        view.pending.put(Cells.key(1, 64, 1), stone);
        view.pending.put(Cells.key(2, 64, 1), stone);

        WindowStream.stream(viewer, view, 10);

        assertEquals(2, WindowStore.workSpent, "one for each block change sent");
    }

    @Test
    void aTakenBackCellInAChunkThatIsNotLoadedIsLeftAloneAndNotLoaded()
    {
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        final long cell = Cells.key(1, 64, 1);
        view.drawn.put(cell, glass);
        view.pending.put(cell, null);

        WindowStream.stream(viewer, view, 10);

        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(viewer, never()).sendBlockChanges(any(Collection.class));
        assertFalse(view.drawn.containsKey(cell), "it is forgotten all the same");
    }

    @Test
    void aCellsChunkIsFoundFromItsBlockCoordinates()
    {
        assertEquals(Cells.chunkKey(-1, 1), WindowStream.chunkOfCell(Cells.key(-1, 64, 17)),
            "x = -1 is chunk -1 and z = 17 is chunk 1");
        assertEquals(Cells.chunkKey(1, -2), WindowStream.chunkOfCell(Cells.key(17, 64, -17)),
            "x = 17 is chunk 1, not 0, and z = -17 is chunk -2");
    }

    @Test
    void aTicksWorthIsSentAndTheRestKeptForTheNext()
    {
        for (int x = 0; x < 5; x++)
        {
            view.pending.put(Cells.key(x, 64, 0), stone);
        }

        WindowStream.stream(viewer, view, 2);

        assertEquals(2, sent().size(), "two changes went out");
        assertEquals(3, view.pending.size(), "three are still owed");
        assertEquals(2, view.drawn.size(), "and only the two sent count as drawn");
    }

    @Test
    void aBlockIsDrawnAsTheDataItWasGiven()
    {
        view.pending.put(Cells.key(1, 64, 1), glass);

        WindowStream.stream(viewer, view, 10);

        final BlockState state = states.get(0);
        verify(state).setBlockData(glass);
        assertEquals(List.of(state), sent());
        assertEquals(glass, view.drawn.get(Cells.key(1, 64, 1)));
    }

    @Test
    void aNullCellIsSentAsTheBlockTheWorldReallyHasAndForgotten()
    {
        final long cell = Cells.key(1, 64, 1);
        view.drawn.put(cell, glass);
        view.pending.put(cell, null);

        WindowStream.stream(viewer, view, 10);

        final BlockState state = states.get(0);
        verify(state, never()).setBlockData(any());
        assertEquals(List.of(state), sent());
        assertFalse(view.drawn.containsKey(cell), "the viewer is no longer being shown it");
    }

    @Test
    void aCellInAChunkThatIsNotLoadedCannotBeDrawnButIsStillCounted()
    {
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        view.pending.put(Cells.key(1, 64, 1), stone);

        WindowStream.stream(viewer, view, 10);

        verify(viewer, never()).sendBlockChanges(any(Collection.class));
        assertEquals(1, view.undrawable, "one could not be drawn");
        assertEquals(stone, view.drawn.get(Cells.key(1, 64, 1)), "and is remembered so it is not asked for again");
    }

    @Test
    void aBlockEntityThatRefusesTheDataIsLeftAsItReallyIs()
    {
        view.pending.put(Cells.key(1, 64, 1), glass);
        final long cell = Cells.key(1, 64, 1);
        when(world.getBlockAt(1, 64, 1)).thenAnswer(call ->
        {
            final BlockState state = mock(BlockState.class);
            org.mockito.Mockito.doThrow(new IllegalArgumentException("sign")).when(state).setBlockData(glass);
            final Block block = mock(Block.class);
            when(block.getState()).thenReturn(state);
            return block;
        });

        WindowStream.stream(viewer, view, 10);

        verify(viewer, never()).sendBlockChanges(any(Collection.class));
        assertEquals(1, view.undrawable);
        assertEquals(glass, view.drawn.get(cell));
    }

    @Test
    void whatIsAlreadyDrawnIsNotSentAgain()
    {
        final long cell = Cells.key(1, 64, 1);
        view.drawn.put(cell, stone);

        WindowStream.send(viewer, view, Map.of(cell, stone), eyeAt(0.5, 64.5, 0.5), 1000L, false, Set.of());

        verify(viewer, never()).sendBlockChanges(any(Collection.class));
        assertEquals(0L, view.fullAt, "and it was not a full send");
    }

    @Test
    void aChangedBlockIsSentAgain()
    {
        final long cell = Cells.key(1, 64, 1);
        view.drawn.put(cell, stone);

        WindowStream.send(viewer, view, Map.of(cell, glass), eyeAt(0.5, 64.5, 0.5), 1000L, false, Set.of());

        assertEquals(1, sent().size());
        assertEquals(glass, view.drawn.get(cell));
    }

    @Test
    void aBlockNoLongerWantedIsPutBackToWhatTheWorldHas()
    {
        final long cell = Cells.key(1, 64, 1);
        view.drawn.put(cell, stone);

        WindowStream.send(viewer, view, Map.of(), eyeAt(0.5, 64.5, 0.5), 1000L, false, Set.of());

        assertEquals(1, sent().size(), "the real block went out");
        assertFalse(view.drawn.containsKey(cell));
    }

    @Test
    void aFullSendRepeatsWhatIsAlreadyDrawnAndNotesWhen()
    {
        final long cell = Cells.key(1, 64, 1);
        view.drawn.put(cell, stone);

        WindowStream.send(viewer, view, Map.of(cell, stone), eyeAt(0.5, 64.5, 0.5), 1000L, true, Set.of());

        assertEquals(1, sent().size());
        assertEquals(1000L, view.fullAt);
    }

    @Test
    void aFreshChunkIsSentAgainWhateverWasSentBefore()
    {
        final long inFresh = Cells.key(1, 64, 1);
        final long elsewhere = Cells.key(40, 64, 1);
        view.drawn.put(inFresh, stone);
        view.drawn.put(elsewhere, stone);

        WindowStream.send(viewer, view, Map.of(inFresh, stone, elsewhere, stone), eyeAt(0.5, 64.5, 0.5), 1000L, false,
            Set.of(Cells.chunkKey(0, 0)));

        assertEquals(1, sent().size(), "only the one in the chunk the client has just been handed");
    }

    @Test
    void everyChunkFreshSendsEverything()
    {
        final long one = Cells.key(1, 64, 1);
        final long two = Cells.key(40, 64, 1);
        view.drawn.put(one, stone);
        view.drawn.put(two, stone);

        WindowStream.send(viewer, view, Map.of(one, stone, two, stone), eyeAt(0.5, 64.5, 0.5), 1000L, false,
            WindowStream.ALL_CHUNKS);

        assertEquals(2, sent().size());
    }

    @Test
    void whatWasOwedFromAnEarlierRedrawIsOwedNoLonger()
    {
        Windows.streamPerTick = 0;
        view.pending.put(Cells.key(90, 64, 90), stone);
        view.undrawable = 7;
        final long cell = Cells.key(1, 64, 1);

        WindowStream.send(viewer, view, Map.of(cell, glass), eyeAt(0.5, 64.5, 0.5), 1000L, false, Set.of());

        assertEquals(Set.of(cell), view.pending.keySet(), "nothing streamed, so this redraw's debt is all that is left");
        assertEquals(0, view.undrawable);
    }

    @Test
    void whatIsOwedIsQueuedNearestTheEyeFirst()
    {
        Windows.streamPerTick = 0;
        final long far = Cells.key(100, 64, 0);
        final long near = Cells.key(2, 64, 0);
        final Map<Long, BlockData> wanted = new java.util.LinkedHashMap<>();
        wanted.put(far, stone);
        wanted.put(near, stone);

        WindowStream.send(viewer, view, wanted, eyeAt(0.5, 64.5, 0.5), 1000L, false, Set.of());

        assertEquals(List.of(near, far), new ArrayList<>(view.pending.keySet()), "the near one goes out first");
    }

    @Test
    void aSendBeyondATicksWorthLeavesTheRestPending()
    {
        Windows.streamPerTick = 1;
        final Map<Long, BlockData> wanted = Map.of(Cells.key(1, 64, 1), stone, Cells.key(2, 64, 1), stone);

        WindowStream.send(viewer, view, wanted, eyeAt(0.5, 64.5, 0.5), 1000L, false, Set.of());

        assertEquals(1, view.pending.size(), "one went out and one waits for the next tick");
    }
}
