package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What {@code mirror debug} says about a viewer's windows, line by line (#430).
 *
 * <p>Written for the move out of {@code Windows}, on the conditions the older tests left unpinned:
 * each test below fails when the one condition it is named for is taken out of {@link WindowDebug}.
 * Lines are read without their colours, as {@code label: value}.
 */
class WindowDebugTest
{
    private static final Place ARRIVAL = new Place("far", 10.5, 70.0, 20.5, 0.0f, 0.0f);

    private MockedStatic<ConfigManager> config;
    private MockedStatic<FarChunkHolds> holds;
    private MockedStatic<FixedBuilds> builds;
    private Player viewer;
    private World world;

    /** A window source that is only what the debug text asks of one. */
    private record Source(String name, Block anchor, WindowShape shape, List<Spot> open, Place destination,
        boolean walkThrough) implements WindowSource
    {
        @Override
        public int depth()
        {
            return 16;
        }

        @Override
        public int fullDepth()
        {
            return 24;
        }
    }

    @BeforeEach
    void setUp()
    {
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getViewDepth).thenReturn(160);
        config.when(ConfigManager::isMirrorShowEntities).thenReturn(true);
        config.when(ConfigManager::getMirrorEntityLoadRadius).thenReturn(4);
        config.when(ConfigManager::getMirrorProximityDistance).thenReturn(8);
        holds = mockStatic(FarChunkHolds.class);
        holds.when(() -> FarChunkHolds.heldFor("gate:Abydos")).thenReturn(new int[] { 2, 5 });
        builds = mockStatic(FixedBuilds.class);
        world = mock(World.class);
        final Block ground = mock(Block.class);
        final BlockData stone = mock(BlockData.class);
        when(stone.getAsString()).thenReturn("minecraft:stone");
        when(ground.getBlockData()).thenReturn(stone);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(ground);
        viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        clearState();
    }

    @AfterEach
    void tearDown()
    {
        clearState();
        builds.close();
        holds.close();
        config.close();
    }

    private static void clearState()
    {
        WindowStore.VIEWS.clear();
        WindowStore.ACTIVE.clear();
        WindowStore.BLIND.clear();
        WindowStore.FULL.clear();
    }

    private WindowState window(final String name, final Place destination, final boolean gate, final Capture capture)
    {
        final WindowShape shape = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), destination, 3, 3);
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        final Block anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
        final WindowState window = new WindowState(new Source(name, anchor, shape, open, destination, gate), capture);
        WindowStore.ACTIVE.put(name, window);
        return window;
    }

    private WindowState window(final String name)
    {
        return window(name, ARRIVAL, false, null);
    }

    /** The solid face round a window's opening, so that it is walled. */
    private static void walled(final WindowState window)
    {
        final WindowShape shape = window.shape;
        final int reach = WindowFace.wallReach();
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

    private ViewerDrawing looking(final String... mirrors)
    {
        final ViewerDrawing view = new ViewerDrawing(world);
        view.mirrors = Set.of(mirrors);
        WindowStore.VIEWS.put(viewer.getUniqueId(), view);
        return view;
    }

    private static List<String> plain(final List<String> lines)
    {
        return lines.stream().map(line -> line.replaceAll("§.", "").trim()).toList();
    }

    private List<String> described()
    {
        return plain(WindowDebug.describe(viewer));
    }

    private List<String> summarised()
    {
        return plain(WindowDebug.summary(viewer));
    }

    private static boolean says(final List<String> lines, final String start)
    {
        return lines.stream().anyMatch(line -> line.startsWith(start));
    }

    private static String line(final List<String> lines, final String start)
    {
        return lines.stream().filter(said -> said.startsWith(start)).findFirst().orElse("<no line starting " + start + ">");
    }

    // --- The view in full ---

    @Test
    void aViewerWithViewsOffAndOneDrawnFullIsToldSoEvenWithNothingInView()
    {
        WindowStore.BLIND.add(viewer.getUniqueId());
        WindowStore.FULL.put(viewer.getUniqueId(), "gate:Abydos");

        final List<String> said = described();

        assertEquals("views: off for you, mirror debug -on turns them back on", line(said, "views"));
        assertEquals("drawn full: gate:Abydos, mirror debug on stops that", line(said, "drawn full"));
        assertEquals("looking into: no window", line(said, "looking into"));
        assertFalse(says(said, "drawn:"), "and nothing about what was drawn, with no view");
    }

    @Test
    void aViewerWithViewsOnIsNotToldTheyAreOff()
    {
        assertFalse(says(described(), "views"), "views are on");
        assertFalse(says(described(), "drawn full"), "and nothing is drawn full");
    }

    @Test
    void whatIsPendingAndWhatCouldNotBeDrawnOverAreSaidOnlyWhenThereIsSome()
    {
        final ViewerDrawing view = looking();
        view.drawn.put(1L, mock(BlockData.class));
        view.drawn.put(2L, mock(BlockData.class));

        List<String> said = described();

        assertEquals("drawn: 2 blocks", line(said, "drawn:"));
        assertFalse(says(said, "still to send"), "nothing pending");
        assertFalse(says(said, "not drawn over"), "nothing the world refused");
        assertEquals("last redraw: never", line(said, "last redraw"));

        view.pending.put(3L, mock(BlockData.class));
        view.undrawable = 1;
        view.lastRedraw = new Windows.Redraw(5, 3, 0, 0, new Spot(1, 2, 3), 12L);
        said = described();

        assertEquals("still to send: 1 blocks, " + Windows.streamPerTick + " a tick", line(said, "still to send"));
        assertEquals("not drawn over: 1 block(s) the world would not let be drawn over", line(said, "not drawn over"),
            "a single block is said");
        assertEquals("last redraw: 5 blocks projected, 3 of them drawn, took 12 ms", line(said, "last redraw"));
        assertEquals("drawn from: 1,2,3", line(said, "drawn from"));
    }

    @Test
    void aWindowThatIsGoneIsPassedOverAndTheNamedOneIsSaidWholeWithItsBlocks()
    {
        final WindowState window = window("gate:Abydos");
        looking("gate:Abydos", "gate:Gone");
        WindowStore.FULL.put(viewer.getUniqueId(), "gate:Abydos");

        assertDoesNotThrow(this::described, "no window of that name is held any more");
        assertEquals("gate:Abydos: whole and unlimited for you, 0 blocks", line(described(), "gate:Abydos"),
            "none made yet");

        final Map<Long, BlockData> blocks = new HashMap<>();
        blocks.put(1L, mock(BlockData.class));
        blocks.put(2L, mock(BlockData.class));
        blocks.put(3L, mock(BlockData.class));
        window.full = new Windows.Whole(blocks, 5);

        assertEquals("gate:Abydos: whole and unlimited for you, 3 blocks", line(described(), "gate:Abydos"),
            "the blocks it holds");
    }

    // --- The view in brief ---

    @Test
    void theBriefViewSaysViewsAreOffAndWhenThereIsNoView()
    {
        WindowStore.BLIND.add(viewer.getUniqueId());

        final List<String> said = summarised();

        assertEquals("views: off for you, mirror debug -on turns them back on", line(said, "views"));
        assertEquals("view: you are looking into no window", line(said, "view:"));
    }

    @Test
    void theBriefViewSaysHowEachWindowIsDrawnAndTheLastRedrawIfThereWas()
    {
        final WindowState walled = window("gate:Abydos");
        walled(walled);
        walled.fixed = new HashMap<>();
        walled.fixedDepth = 3;
        walled.fixedFor = 3;
        final ViewerDrawing view = looking("gate:Abydos");

        List<String> said = summarised();

        assertTrue(line(said, "gate:Abydos").contains("another mirror within twice the depth"),
            "walled, but not held whole for this viewer: clipped, and why");
        assertFalse(says(said, "redraw"), "no redraw yet");

        view.fixedNames = Set.of("gate:Abydos");
        view.lastRedraw = new Windows.Redraw(5, 3, 0, 0, new Spot(1, 2, 3), 12L);
        said = summarised();

        assertEquals("gate:Abydos: drawn whole to depth 3, 0 blocks", line(said, "gate:Abydos"), "held whole for this viewer");
        assertEquals("redraw: 5 projected, 3 drawn, 12 ms", line(said, "redraw"));

        WindowStore.FULL.put(viewer.getUniqueId(), "gate:Abydos");
        assertEquals("gate:Abydos: whole and unlimited for you", line(summarised(), "gate:Abydos"), "an admin's full view");
    }

    // --- How a window is drawn ---

    @Test
    void aWindowWithAGapInItsWallSaysWhereTheGapIsAndWhatIsThere()
    {
        final WindowState gappy = window("gate:Abydos");
        looking("gate:Abydos");
        final Spot gap = Windows.gapBeside(gappy);
        final BlockData dirt = mock(BlockData.class);
        when(dirt.getAsString()).thenReturn("minecraft:dirt");
        final Block elsewhere = mock(Block.class);
        when(elsewhere.getBlockData()).thenReturn(dirt);
        final BlockData stoneHere = mock(BlockData.class);
        when(stoneHere.getAsString()).thenReturn("minecraft:stone");
        final Block atGap = mock(Block.class);
        when(atGap.getBlockData()).thenReturn(stoneHere);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(elsewhere);
        when(world.getBlockAt(gap.x(), gap.y(), gap.z())).thenReturn(atGap);

        assertEquals("gate:Abydos: whole to depth 0, clipped to each eye: wall within " + WindowFace.wallReach()
            + " open at " + gap.x() + "," + gap.y() + "," + gap.z() + " (minecraft:stone)",
            line(summarised(), "gate:Abydos"), "the first block of the face that is open, and what it is");
    }

    @Test
    void aHeldRoomIsSaidWithItsBlocksAndWhetherItIsBeingBuiltAgain()
    {
        final WindowState walled = window("gate:Abydos");
        walled(walled);
        walled.fixedDepth = 3;
        walled.fixedFor = 3;
        looking("gate:Abydos").fixedNames = Set.of("gate:Abydos");

        assertEquals("gate:Abydos: drawn whole to depth 3, 0 blocks", line(summarised(), "gate:Abydos"),
            "no room yet: none, not one");

        walled.fixed = new HashMap<>();
        walled.fixed.put(1L, mock(BlockData.class));
        walled.fixed.put(2L, mock(BlockData.class));
        assertEquals("gate:Abydos: drawn whole to depth 3, 2 blocks", line(summarised(), "gate:Abydos"));

        builds.when(() -> FixedBuilds.building("gate:Abydos")).thenReturn(true);
        assertEquals("gate:Abydos: drawn whole to depth 3, 2 blocks, being built again", line(summarised(), "gate:Abydos"));
    }

    @Test
    void aRoomCutToFitIsSaidWithTheCapAndOneNotYetBuiltIsNot()
    {
        final WindowState walled = window("gate:Abydos");
        walled(walled);
        walled.fixedDepth = 3;
        walled.fixedFor = 5;
        looking("gate:Abydos").fixedNames = Set.of("gate:Abydos");

        assertTrue(line(summarised(), "gate:Abydos").contains("to depth 3, 0 blocks"),
            "no room is held to be cut: only the depth");

        walled.fixed = new HashMap<>();

        assertTrue(line(summarised(), "gate:Abydos").contains("cut to depth 3 of 5 to fit " + Windows.mostFixedFor(walled) + " blocks"),
            "a room built to 3 of the 5 asked for");

        walled.fixedDepth = 5;

        assertTrue(line(summarised(), "gate:Abydos").contains("to depth 5"), "built to the depth asked for: not cut");
        assertFalse(line(summarised(), "gate:Abydos").contains("cut to"), "and not said to be");
    }

    @Test
    void aGatesViewSaysHowFarItsCaptureReachesAndWhetherItWasCut()
    {
        final Capture capture = mock(Capture.class);
        when(capture.bounds()).thenReturn(new int[] { 0, 0, 0, 100, 100, 100 });
        when(capture.secondsOld()).thenReturn(7L);
        final WindowState gate = window("gate:Abydos", ARRIVAL, true, capture);
        looking("gate:Abydos");

        when(capture.keptReach()).thenReturn(0);
        assertEquals("gate:Abydos capture: reaches 80 ahead, drawn to 16 (full 24), 7s old",
            line(described(), "gate:Abydos capture"), "uncut: the box's reach");

        when(capture.keptReach()).thenReturn(30);
        assertEquals("gate:Abydos capture: reaches 30 ahead, cut from 80 to fit, drawn to 16 (full 24), 7s old",
            line(described(), "gate:Abydos capture"), "cut: what was kept, and what it was cut from");
        assertTrue(gate.walkThrough());
    }

    @Test
    void aMirrorsViewHasNoCaptureLine()
    {
        final Capture capture = mock(Capture.class);
        when(capture.bounds()).thenReturn(new int[] { 0, 0, 0, 100, 100, 100 });
        window("gate:Abydos", ARRIVAL, false, capture);
        looking("gate:Abydos");

        assertFalse(says(described(), "gate:Abydos capture"), "only a gate's view comes in steps");
    }

    @Test
    void theCreaturesLineIsSaidOnlyWhileCreaturesAreShownAndOneWasTallied()
    {
        final WindowState window = window("gate:Abydos");
        final ViewerDrawing view = looking("gate:Abydos");

        assertFalse(says(described(), "gate:Abydos creatures"), "nothing tallied");

        view.tallies.put("gate:Abydos", new CreatureTally());

        assertEquals("gate:Abydos creatures: " + view.tallies.get("gate:Abydos").line(window)
            + ", chunks held 2 of 5 (mirror-entity-load-radius 4)", line(described(), "gate:Abydos creatures"));

        config.when(ConfigManager::isMirrorShowEntities).thenReturn(false);

        assertFalse(says(described(), "gate:Abydos creatures"), "creatures are not shown");
    }

    @Test
    void aClippedWindowsFarPartIsSaidWithTheCellItIsJudgedFor()
    {
        final WindowState window = window("gate:Abydos");
        window.border = 5;
        final ViewerDrawing view = looking("gate:Abydos");

        assertEquals("gate:Abydos far part: judged once per 4.0 blocks of movement, wall 5 on every side, not yet",
            line(described(), "gate:Abydos far part"));

        view.far.put("gate:Abydos", WindowCompose.Far.judged(new Windows.Whole(new HashMap<>(), 1),
            new Location(world, 0.0, 0.0, 0.0), 4.0, new Location(world, 0.0, 0.0, 0.0), 1000L,
            new HashMap<>(Map.of(1L, mock(BlockData.class)))));

        assertTrue(line(described(), "gate:Abydos far part").endsWith("1 blocks kept"), "what stands");

        view.fixedNames = Set.of("gate:Abydos");

        assertFalse(says(described(), "gate:Abydos far part"), "a window held whole has no far part");
    }

    // --- How far a capture reaches ahead ---

    private WindowState facing(final float yaw, final double x, final double z)
    {
        final Capture capture = mock(Capture.class);
        when(capture.bounds()).thenReturn(new int[] { -20, 0, -30, 50, 100, 70 });
        return window("gate:Facing" + yaw, new Place("far", x, 70.0, z, yaw, 0.0f), true, capture);
    }

    @Test
    void theReachAheadIsMeasuredToTheFarEdgeOfTheBoxFromWhereTravellersLandFloored()
    {
        assertEquals(76, WindowDebug.reachAhead(facing(0.0f, -10.5, -5.5)), "south: to the box's far z from the landing z, floored (-6)");
        assertEquals(9, WindowDebug.reachAhead(facing(90.0f, -10.5, -5.5)), "west: from the box's near x to the landing x, floored (-11)");
        assertEquals(24, WindowDebug.reachAhead(facing(180.0f, -10.5, -5.5)), "north: from the landing z, floored, to the box's near z");
        assertEquals(61, WindowDebug.reachAhead(facing(270.0f, -10.5, -5.5)), "east: to the box's far x from the landing x, floored");
    }
}
