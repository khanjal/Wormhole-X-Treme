package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * A big gate's view onto a small gate is drawn narrowed to a funnel, walled in its portal material (#583).
 *
 * <p>Drawn whole, a Massive gate's view of a Standard gate ran past the small gate's ceiling and walls
 * into the next hallway. The far side outside the funnel is left out of the held room, and a wall of
 * the gate's own portal material is drawn round it, so it reads as a tunnel of water.
 */
class WindowsFunnelTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    /** An eleven-square opening facing south, looked into northwards: its right is +x. */
    private static final WindowShape BIG = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 11, 11);

    /** Twelve layers in, on the far opening's middle column: inside the funnel. */
    private static final Spot INSIDE = BIG.behind(12, 5, 1);

    /** The same layer, four columns left: outside the funnel and its wall, inside the depth. */
    private static final Spot OUTSIDE = BIG.behind(12, 1, 1);

    /** The column just left of the far opening: the wall. */
    private static final Spot WALL = BIG.behind(12, 3, 1);

    private World world;
    private Block anchor;
    private BlockData stone;
    private BlockData water;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp()
    {
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
        stone = mock(BlockData.class);
        water = mock(BlockData.class);
        final BlockData air = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.WATER)).thenReturn(water);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
        Windows.clear();
    }

    @AfterEach
    void tearDown()
    {
        Windows.clear();
        bukkit.close();
    }

    /** The far gate's portal cells: three wide and four tall, a block behind the arrival. */
    private static List<Spot> standard()
    {
        final List<Spot> cells = new ArrayList<>();
        for (int x = 99; x <= 101; x++)
        {
            for (int y = 70; y <= 73; y++)
            {
                cells.add(new Spot(x, y, 199));
            }
        }
        return cells;
    }

    private GateSource gate(final Funnel funnel)
    {
        final List<Spot> open = new ArrayList<>();
        BIG.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        return new GateSource("gate:Abydos", anchor, BIG, open, ARRIVAL, "Chulak", 16, funnel);
    }

    private static Funnel funnel(final int taper)
    {
        return Funnel.of(BIG, standard(), taper, Material.WATER);
    }

    /** A capture holding stone everywhere, walked through the volume. */
    private Capture solidEverywhere()
    {
        final Capture capture = mock(Capture.class);
        when(capture.complete()).thenReturn(true);
        when(capture.contains(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(capture.at(anyInt(), anyInt(), anyInt())).thenReturn(stone);
        return capture;
    }

    /** A capture that kept stone at the far blocks two cells here show, walked over what it kept. */
    private Capture keptAt(final Spot... here)
    {
        final Capture capture = mock(Capture.class);
        when(capture.at(anyInt(), anyInt(), anyInt())).thenReturn(stone);
        doAnswer(call ->
        {
            final Capture.Kept kept = call.getArgument(0);
            for (final Spot cell : here)
            {
                final Spot far = BIG.farOf(cell.x(), cell.y(), cell.z());
                kept.at(far.x(), far.y(), far.z(), false);
            }
            return null;
        }).when(capture).forEachKept(any());
        return capture;
    }

    private static long key(final Spot at)
    {
        return Windows.key(at.x(), at.y(), at.z());
    }

    @Test
    void aViewWalkedThroughTheVolumeKeepsOnlyTheFunnelAndWallsItIn()
    {
        final Map<Long, BlockData> view = Windows.fixedTo(new WindowState(gate(funnel(8)), solidEverywhere()), 16, 0L,
            Integer.MAX_VALUE);

        assertSame(stone, view.get(key(INSIDE)), "the far side inside the funnel");
        assertSame(water, view.get(key(WALL)), "the gate's portal material round it");
        assertFalse(view.containsKey(key(OUTSIDE)), "the far side outside it, behind the wall, is never drawn");
    }

    @Test
    void aViewWalkedOverWhatWasKeptKeepsOnlyTheFunnelAndWallsItIn()
    {
        final Map<Long, BlockData> view = Windows.fixedTo(new WindowState(gate(funnel(8)), keptAt(INSIDE, OUTSIDE)), 16,
            0L, Integer.MAX_VALUE);

        assertSame(stone, view.get(key(INSIDE)), "kept inside the funnel");
        assertSame(water, view.get(key(WALL)), "walled");
        assertFalse(view.containsKey(key(OUTSIDE)), "kept outside it: left out");
    }

    @Test
    void withNoFunnelTheWholeViewIsDrawnAndNoWall()
    {
        final Map<Long, BlockData> walked = Windows.fixedTo(new WindowState(gate(null), solidEverywhere()), 16, 0L,
            Integer.MAX_VALUE);
        final Map<Long, BlockData> kept = Windows.fixedTo(new WindowState(gate(null), keptAt(INSIDE, OUTSIDE)), 16, 0L,
            Integer.MAX_VALUE);

        assertSame(stone, walked.get(key(OUTSIDE)), "same-size and small-to-large views are as they were");
        assertSame(stone, walked.get(key(WALL)), "and nothing is walled");
        assertSame(stone, kept.get(key(OUTSIDE)));
        assertFalse(walked.containsValue(water));
    }

    @Test
    void theWallStopsAtTheDepthAndTheWorldsFloor()
    {
        final WindowState window = new WindowState(gate(funnel(8)), solidEverywhere());

        final Map<Long, BlockData> view = Windows.walledIn(window, new HashMap<>(), 16, Integer.MAX_VALUE);

        assertSame(water, view.get(key(BIG.behind(15, 3, 1))), "the last layer inside the depth");
        assertNull(view.get(key(BIG.behind(16, 3, 1))), "none at the depth");
        assertSame(water, view.get(key(BIG.behind(14, 6, -1))), "the floor under the far opening's corner");
        assertNull(view.get(key(BIG.behind(15, 6, -1))), "past the depth from the opening's middle, though short of 16 layers");
        assertSame(water, view.get(key(BIG.behind(5, 5, -1))), "the floor below the bottom row, above the world's");
        assertSame(water, view.get(key(BIG.behind(5, 1, 0))), "a side, on the bottom row");
        when(world.getMinHeight()).thenReturn(64);
        assertNull(Windows.walledIn(window, new HashMap<>(), 16, Integer.MAX_VALUE).get(key(BIG.behind(5, 5, -1))),
            "never below the world's floor");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(64);
        assertNull(Windows.walledIn(window, new HashMap<>(), 16, Integer.MAX_VALUE).get(key(BIG.behind(5, 1, 0))),
            "nor at or above its ceiling");
    }

    @Test
    void aWallThatTakesTheViewPastItsLimitIsTooMany() throws ReflectiveOperationException
    {
        final Map<Long, BlockData> tooMany = PrivateStatics.of(Windows.class, "TOO_MANY");
        final WindowState window = new WindowState(gate(funnel(8)), solidEverywhere());

        assertSame(tooMany, Windows.walledIn(window, new HashMap<>(), 16, 10), "cut shallower, as any view past its limit");
        final Map<Long, BlockData> view = Windows.walledIn(window, new HashMap<>(), 16, 100_000);
        assertNotNull(view);
        assertTrue(view.size() > 10, view.size() + " wall blocks fit under a limit that big");
        assertSame(tooMany, Windows.walledIn(window, tooMany, 16, 100_000), "a view already past its limit stays so");
    }

    /**
     * A gate's held room is let go when its funnel changes, so a new taper shows at once rather than
     * after the minute a room is kept.
     */
    @Test
    void aHeldRoomIsLetGoWhenTheFunnelChanges() throws ReflectiveOperationException
    {
        final Capture capture = solidEverywhere();
        Windows.offer(gate(funnel(8)), capture);
        Windows.finish();
        final Map<String, WindowState> active = PrivateStatics.of(Windows.class, "ACTIVE");
        final Map<String, WindowState> offered = PrivateStatics.of(Windows.class, "OFFERED");
        final Map<Long, BlockData> room = new HashMap<>();
        active.get("gate:Abydos").fixed = room;
        active.get("gate:Abydos").fixedUsedAt = Windows.now();

        Windows.offer(gate(funnel(8)), capture);
        assertSame(room, offered.get("gate:Abydos").fixed, "the same funnel: kept");

        Windows.offer(gate(funnel(4)), capture);
        assertNull(offered.get("gate:Abydos").fixed, "a new taper: built again");
        Windows.offer(gate(null), capture);
        assertNull(offered.get("gate:Abydos").fixed, "turned off: built again");
    }

    /** An admin's whole view of a gate is let go with its funnel too, as its held room is. */
    @Test
    void anAdminsWholeViewIsLetGoWhenTheFunnelChanges() throws ReflectiveOperationException
    {
        final Capture capture = solidEverywhere();
        final Map<UUID, String> full = PrivateStatics.of(Windows.class, "FULL");
        full.put(UUID.randomUUID(), "gate:Abydos");
        Windows.offer(gate(funnel(8)), capture);
        Windows.finish();
        final Map<String, WindowState> active = PrivateStatics.of(Windows.class, "ACTIVE");
        final Map<String, WindowState> offered = PrivateStatics.of(Windows.class, "OFFERED");
        final Windows.Whole whole = new Windows.Whole(new HashMap<>(), 16);
        active.get("gate:Abydos").full = whole;
        active.get("gate:Abydos").fullFrom = capture;

        Windows.offer(gate(funnel(8)), capture);
        assertSame(whole, offered.get("gate:Abydos").full, "the same funnel: kept");

        Windows.offer(gate(funnel(4)), capture);
        assertNull(offered.get("gate:Abydos").full, "a new taper: drawn again");
    }

    @Test
    void aWindowWithNoFunnelHoldsEveryBlockAndOneWithOneOnlyItsOwn()
    {
        final WindowState funnelled = new WindowState(gate(funnel(8)), solidEverywhere());
        final WindowState whole = new WindowState(gate(null), solidEverywhere());

        assertTrue(funnelled.inFunnel(INSIDE.x(), INSIDE.y(), INSIDE.z()));
        assertFalse(funnelled.inFunnel(OUTSIDE.x(), OUTSIDE.y(), OUTSIDE.z()));
        assertFalse(funnelled.inFunnel(INSIDE.x(), INSIDE.y() + 4, INSIDE.z()), "above the far gate's ceiling");
        assertTrue(whole.inFunnel(OUTSIDE.x(), OUTSIDE.y(), OUTSIDE.z()));
        assertEquals(12, BIG.layerOf(INSIDE.x(), INSIDE.z()), "the shape's own layer and column, the other way");
        assertEquals(5, BIG.acrossOf(INSIDE.x(), INSIDE.z()));
    }
}
