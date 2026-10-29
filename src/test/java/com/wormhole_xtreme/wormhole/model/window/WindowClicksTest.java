package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What the window drawing does about a click: the view sent again after one the server refused, and the
 * mirror a click through the view reaches (#430).
 *
 * <p>Written for the move out of {@code Windows}, on the conditions the older tests left unpinned:
 * each test below fails when the one condition it is named for is taken out of {@link WindowClicks}.
 */
class WindowClicksTest
{
    private static final Place ARRIVAL = new Place("far", 10.5, 70.0, 20.5, 0.0f, 0.0f);
    private static final WindowShape SHAPE = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 1, 1);

    private World world;
    private World elsewhere;
    private Player player;
    private BukkitScheduler scheduler;

    /** A window source that is only what a click asks of one. */
    private record Source(String name, Block anchor, List<Spot> open, boolean walkThrough, QuantumMirror punchTarget)
        implements WindowSource
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
        world = mock(World.class);
        elsewhere = mock(World.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        WindowStore.VIEWS.clear();
        WindowStore.ACTIVE.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        WindowStore.VIEWS.clear();
        WindowStore.ACTIVE.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private ViewerDrawing viewing(final String... mirrors)
    {
        final ViewerDrawing view = new ViewerDrawing(world);
        view.mirrors = new LinkedHashSet<>(List.of(mirrors));
        WindowStore.VIEWS.put(player.getUniqueId(), view);
        return view;
    }

    private static Block block(final World in, final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(in);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        return block;
    }

    private WindowState window(final String name, final World in, final int x, final int y, final int z,
        final boolean gate, final QuantumMirror target, final Spot... open)
    {
        final WindowState window = new WindowState(new Source(name, block(in, x, y, z), List.of(open), gate, target), null);
        WindowStore.ACTIVE.put(name, window);
        return window;
    }

    private static long key(final Block block)
    {
        return Cells.key(block.getX(), block.getY(), block.getZ());
    }

    private static long sendBlockChanges(final Player who)
    {
        return mockingDetails(who).getInvocations().stream()
            .filter(call -> "sendBlockChange".equals(call.getMethod().getName())).count();
    }

    // --- Sending the view again ---

    @Test
    void aClickMarksTheViewForAFullResendAndSendsItATickLater()
    {
        final ViewerDrawing view = viewing();
        view.fullAt = 5L;
        view.composedAt = 5L;
        view.eye = 5L;

        WindowClicks.resend(player);

        assertEquals(0L, view.fullAt, "the whole view is due");
        assertEquals(0L, view.composedAt, "and composed afresh");
        assertEquals(Long.MIN_VALUE, view.eye, "from an eye nobody has been drawn for");
        final ArgumentCaptor<Long> ticks = ArgumentCaptor.forClass(Long.class);
        verify(scheduler).scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), ticks.capture());
        assertEquals(1L, ticks.getValue(), "a tick later, after the server's own correction");
    }

    @Test
    void aClickFromSomebodyLookingIntoNothingResendsNothing()
    {
        WindowClicks.resend(player);

        verify(scheduler, never()).scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong());
    }

    private Runnable queuedResend()
    {
        final ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleSyncDelayedTask(any(Plugin.class), run.capture(), anyLong());
        return run.getValue();
    }

    @Test
    void theResendSendsWhatWasDrawnWithWhatIsStillOwedAppliedAndNothingTheViewerWasToldToDrop()
    {
        final ViewerDrawing view = viewing();
        final BlockData kept = mock(BlockData.class);
        final BlockData dropped = mock(BlockData.class);
        final BlockData replaced = mock(BlockData.class);
        final BlockData added = mock(BlockData.class);
        final BlockData replacement = mock(BlockData.class);
        view.drawn.put(1L, kept);
        view.drawn.put(2L, dropped);
        view.drawn.put(4L, replaced);
        view.pending.put(2L, null);
        view.pending.put(3L, added);
        view.pending.put(4L, replacement);
        final Location eye = new Location(world, 1.0, 2.0, 3.0);
        when(player.getEyeLocation()).thenReturn(eye);
        WindowClicks.resend(player);
        final Runnable again = queuedResend();
        final List<Map<Long, BlockData>> sent = new ArrayList<>();

        try (MockedStatic<WindowStream> stream = mockStatic(WindowStream.class))
        {
            again.run();

            stream.verify(() -> WindowStream.send(eq(player), eq(view), argThat(meant ->
            {
                sent.add(meant);
                return true;
            }), eq(eye), anyLong(), eq(true), eq(Set.of())));
        }

        final Map<Long, BlockData> expected = new HashMap<>();
        expected.put(1L, kept);
        expected.put(3L, added);
        expected.put(4L, replacement);
        assertEquals(expected, sent.get(0), "what was drawn, less what is owed away, plus what is owed");
    }

    @Test
    void theResendOfAViewerWhoseViewIsGoneSendsNothing()
    {
        viewing();
        WindowClicks.resend(player);
        final Runnable again = queuedResend();
        WindowStore.VIEWS.clear();

        try (MockedStatic<WindowStream> stream = mockStatic(WindowStream.class))
        {
            again.run();

            stream.verify(() -> WindowStream.send(any(), any(), any(), any(), anyLong(), eq(true), any()), never());
        }
    }

    @Test
    void aClickOnABlockSendsTheClickedAndTheNeighbouringBlockAsTheViewDrawsThemAfterTheServersCorrection()
    {
        final ViewerDrawing view = viewing();
        final Block clicked = block(world, 10, 64, 11);
        final Block beside = block(world, 10, 65, 12);
        when(clicked.getRelative(BlockFace.NORTH)).thenReturn(beside);
        final BlockData first = mock(BlockData.class);
        final BlockData second = mock(BlockData.class);
        view.drawn.put(key(clicked), first);
        view.drawn.put(key(beside), second);

        WindowClicks.resend(player, clicked, BlockFace.NORTH);

        verify(scheduler).scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong());
        final ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(any(Plugin.class), run.capture());
        final long before = System.nanoTime();
        run.getValue().run();
        final long tookMillis = (System.nanoTime() - before) / 1_000_000L;

        assertTrue(tookMillis >= 29L, "after the server's own correction has gone out: " + tookMillis + " ms");
        verify(player).sendBlockChange(new Location(world, 10, 64, 11), first);
        verify(player).sendBlockChange(new Location(world, 10, 65, 12), second);
        assertEquals(2L, sendBlockChanges(player), "and nothing else");
    }

    @Test
    void aClickWithNoFaceSendsOnlyTheClickedBlockAndOnlyIfItIsDrawn()
    {
        final ViewerDrawing view = viewing();
        final Block clicked = block(world, 10, 64, 11);
        final Block beside = block(world, 10, 65, 12);
        when(clicked.getRelative(BlockFace.NORTH)).thenReturn(beside);
        final BlockData data = mock(BlockData.class);
        view.drawn.put(key(clicked), data);

        WindowClicks.resend(player, clicked, null);
        final ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(any(Plugin.class), run.capture());
        run.getValue().run();

        verify(player).sendBlockChange(new Location(world, 10, 64, 11), data);
        assertEquals(1L, sendBlockChanges(player), "the block beside is not looked at");
    }

    @Test
    void aBesideBlockThatIsNotDrawnIsNotSent()
    {
        final ViewerDrawing view = viewing();
        final Block clicked = block(world, 10, 64, 11);
        final Block beside = block(world, 10, 65, 12);
        when(clicked.getRelative(BlockFace.NORTH)).thenReturn(beside);
        view.drawn.put(key(clicked), mock(BlockData.class));

        WindowClicks.resend(player, clicked, BlockFace.NORTH);
        final ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(any(Plugin.class), run.capture());
        run.getValue().run();

        assertEquals(1L, sendBlockChanges(player), "only the drawn one");
    }

    @Test
    void aClickOnABlockNothingDrawsQueuesNoCorrectionButStillResendsTheView()
    {
        viewing();

        WindowClicks.resend(player, block(world, 10, 64, 11), BlockFace.NORTH);

        verify(scheduler).scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong());
        verify(scheduler, never()).runTaskAsynchronously(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void aMirrorsBannerChangeMarksOnlyTheViewsLookingIntoItForAFullResend()
    {
        final ViewerDrawing looking = viewing("museum");
        looking.fullAt = 5L;
        final ViewerDrawing other = new ViewerDrawing(world);
        other.mirrors = Set.of("gallery");
        other.fullAt = 5L;
        WindowStore.VIEWS.put(UUID.randomUUID(), other);

        WindowClicks.resendFor("museum");

        assertEquals(0L, looking.fullAt, "the view looking into that mirror");
        assertEquals(5L, other.fullAt, "and not a view of another");
    }

    // --- Which mirror a click reaches ---

    @Test
    void aClickThroughTheOpeningOfAMirrorReachesItAndNobodyReachesOneThroughAGateOrAnotherWorld()
    {
        final QuantumMirror mirrorTarget = mock(QuantumMirror.class);
        final QuantumMirror gateTarget = mock(QuantumMirror.class);
        final QuantumMirror otherWorldTarget = mock(QuantumMirror.class);
        window("mirror", world, 0, 0, 0, false, mirrorTarget, new Spot(10, 64, 20));
        window("gate", world, 0, 0, 0, true, gateTarget, new Spot(30, 64, 20));
        window("faraway", elsewhere, 0, 0, 0, false, otherWorldTarget, new Spot(50, 64, 20));
        viewing("mirror", "gate", "faraway");

        assertSame(mirrorTarget, WindowClicks.clicked(player, block(world, 10, 64, 20)), "the opening of a mirror");
        assertNull(WindowClicks.clicked(player, block(world, 30, 64, 20)), "a gate is walked through, not punched");
        assertNull(WindowClicks.clicked(player, block(world, 11, 64, 20)), "a block that is not in any opening");
        assertNull(WindowClicks.clicked(player, block(world, 50, 64, 20)), "an opening of a window in another world");
    }

    @Test
    void aClickWithNoBlockOrNoPlayerReachesNothing()
    {
        viewing("mirror");

        assertNull(WindowClicks.clicked(player, null), "no block");
        assertNull(WindowClicks.clicked(null, block(world, 1, 2, 3)), "no player");
    }

    @Test
    void aClickOnWhatTheViewDrawsInAnotherWorldReachesNothing()
    {
        final QuantumMirror target = mock(QuantumMirror.class);
        window("mirror", elsewhere, 0, 0, 0, false, target, new Spot(10, 64, 20));
        final ViewerDrawing view = viewing("mirror");
        final Block drawn = block(elsewhere, 1, 2, 3);
        view.drawn.put(key(drawn), mock(BlockData.class));

        assertNull(WindowClicks.clicked(player, drawn), "the view is drawn in another world");
    }

    @Test
    void aClickOnWhatTheViewDrawsReachesTheNearestMirrorOfTheViewsWorld()
    {
        final QuantumMirror nearTarget = mock(QuantumMirror.class);
        final QuantumMirror farTarget = mock(QuantumMirror.class);
        final QuantumMirror gateTarget = mock(QuantumMirror.class);
        final QuantumMirror otherWorldTarget = mock(QuantumMirror.class);
        // Along each axis in turn: the nearer window is the one the block is nearer on that axis alone.
        window("x-near", world, 40, 0, 0, false, nearTarget);
        window("x-far", world, 0, 0, 0, false, farTarget);
        final ViewerDrawing view = viewing("x-far", "x-near");
        final Block alongX = block(world, 30, 0, 0);
        view.drawn.put(key(alongX), mock(BlockData.class));
        assertSame(nearTarget, WindowClicks.clicked(player, alongX), "nearer along x");

        WindowStore.ACTIVE.clear();
        window("y-near", world, 0, 40, 0, false, nearTarget);
        window("y-far", world, 0, 0, 0, false, farTarget);
        view.mirrors = new LinkedHashSet<>(List.of("y-far", "y-near"));
        final Block alongY = block(world, 0, 30, 0);
        view.drawn.put(key(alongY), mock(BlockData.class));
        assertSame(nearTarget, WindowClicks.clicked(player, alongY), "nearer along y");

        WindowStore.ACTIVE.clear();
        window("z-near", world, 0, 0, 40, false, nearTarget);
        window("z-far", world, 0, 0, 0, false, farTarget);
        view.mirrors = new LinkedHashSet<>(List.of("z-far", "z-near"));
        final Block alongZ = block(world, 0, 0, 30);
        view.drawn.put(key(alongZ), mock(BlockData.class));
        assertSame(nearTarget, WindowClicks.clicked(player, alongZ), "nearer along z");

        WindowStore.ACTIVE.clear();
        window("gate-nearest", world, 0, 0, 29, true, gateTarget);
        window("other-world-nearest", elsewhere, 0, 0, 29, false, otherWorldTarget);
        window("kept", world, 0, 0, 0, false, farTarget);
        view.mirrors = new LinkedHashSet<>(List.of("gate-nearest", "other-world-nearest", "kept"));
        assertSame(farTarget, WindowClicks.clicked(player, alongZ), "a gate and a window of another world are not candidates");

        WindowStore.ACTIVE.clear();
        window("first", world, 0, 0, 0, false, nearTarget);
        window("second", world, 0, 0, 60, false, farTarget);
        view.mirrors = new LinkedHashSet<>(List.of("first", "second"));
        assertSame(nearTarget, WindowClicks.clicked(player, alongZ), "equally near: the first");
    }

    // --- Whether a block is drawn over ---

    @Test
    void aBlockIsDrawnOverOnlyInTheViewsWorldAndOnlyIfTheViewDrawsIt()
    {
        final ViewerDrawing view = viewing();
        final Block drawn = block(world, 1, 2, 3);
        final Block elsewhereBlock = block(elsewhere, 1, 2, 3);
        view.drawn.put(key(drawn), mock(BlockData.class));

        assertTrue(WindowClicks.drew(player, drawn), "drawn, in the view's world");
        assertFalse(WindowClicks.drew(player, elsewhereBlock), "the same place in another world");
        assertFalse(WindowClicks.drew(player, block(world, 9, 9, 9)), "a block it does not draw");
        assertFalse(WindowClicks.drew(player, null), "no block");
        assertFalse(WindowClicks.drew(null, drawn), "no player");
    }
}
