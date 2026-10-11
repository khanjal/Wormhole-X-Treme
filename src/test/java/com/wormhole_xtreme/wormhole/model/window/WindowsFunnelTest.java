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
import org.bukkit.HeightMap;
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
 * A big gate's view onto a small gate is drawn at the end of a tunnel walled in its portal material (#583).
 *
 * <p>Drawn whole, a Massive gate's view of a Standard gate ran past the small gate's ceiling and walls
 * into the next hallway. Narrowed to a funnel, the far room's ceiling still hung in the funnel's wide
 * end, the tunnel and the view overlapping. Now the tunnel is hollow and the far room starts past it,
 * shifted as deep as the tunnel is long, at the small gate's size.
 */
class WindowsFunnelTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    /** An eleven-square opening facing south, looked into northwards: its right is +x. */
    private static final WindowShape BIG = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 11, 11);

    /** Twelve layers in, on the far opening's middle column: in the room past the eight-layer tunnel. */
    private static final Spot INSIDE = BIG.behind(12, 5, 1);

    /** The same layer, four columns left: outside the room and its wall, inside the depth. */
    private static final Spot OUTSIDE = BIG.behind(12, 1, 1);

    /** The column just left of the far opening: the wall. */
    private static final Spot WALL = BIG.behind(12, 3, 1);

    /** Halfway down the tunnel, near the top of the big opening: hollow. */
    private static final Spot TUNNEL = BIG.behind(3, 5, 6);

    private World world;
    private Block anchor;
    private BlockData stone;
    private BlockData water;
    private BlockData air;
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
        air = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.WATER)).thenReturn(water);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
        Windows.clear();
        WindowSight.clear();
    }

    @AfterEach
    void tearDown()
    {
        Windows.clear();
        WindowSight.clear();
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

    /** The window a gate draws: shifted as deep as its tunnel, as {@code GateViews} shifts it. */
    private static WindowShape shapeFor(final Funnel funnel)
    {
        return (funnel == null) ? BIG : BIG.deeper(funnel.taper() - 1);
    }

    private GateSource gate(final Funnel funnel)
    {
        final List<Spot> open = new ArrayList<>();
        BIG.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        return new GateSource("gate:Abydos", anchor, shapeFor(funnel), open, ARRIVAL, "Chulak", 16, funnel);
    }

    private static Funnel funnel(final int longest)
    {
        return Funnel.of(BIG, standard(), longest, Material.WATER);
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

    /** A capture that kept stone at some far blocks, walked over what it kept. */
    private Capture keptAtFar(final Spot... far)
    {
        final Capture capture = mock(Capture.class);
        when(capture.at(anyInt(), anyInt(), anyInt())).thenReturn(stone);
        doAnswer(call ->
        {
            final Capture.Kept kept = call.getArgument(0);
            for (final Spot cell : far)
            {
                kept.at(cell.x(), cell.y(), cell.z(), false);
            }
            return null;
        }).when(capture).forEachKept(any());
        return capture;
    }

    /** The far blocks some cells here show through a window. */
    private static Spot[] farOf(final WindowShape shape, final Spot... here)
    {
        final Spot[] far = new Spot[here.length];
        for (int i = 0; i < here.length; i++)
        {
            far[i] = shape.farOf(here[i].x(), here[i].y(), here[i].z());
        }
        return far;
    }

    private static long key(final Spot at)
    {
        return Windows.key(at.x(), at.y(), at.z());
    }

    @Test
    void aViewWalkedThroughTheVolumeIsAHollowTunnelThenTheRoomWalledIn()
    {
        final Map<Long, BlockData> view = Windows.fixedTo(new WindowState(gate(funnel(16)), solidEverywhere()), 16, 0L,
            Integer.MAX_VALUE);

        assertSame(air, view.get(key(TUNNEL)), "the tunnel is hollow, though the far side is stone everywhere");
        assertSame(air, view.get(key(BIG.behind(7, 5, 1))), "to its last layer, the far gate's own plane");
        assertSame(water, view.get(key(BIG.behind(3, 5, -1))), "its floor is the gate's portal material");
        assertSame(stone, view.get(key(INSIDE)), "the far side in the room past it");
        assertSame(water, view.get(key(WALL)), "the gate's portal material round the room");
        assertFalse(view.containsKey(key(OUTSIDE)), "the far side outside it, behind the wall, is never drawn");
        assertSame(stone, view.get(key(BIG.behind(12, 5, -1))), "the far ground under the room, not a wall of water");
        assertSame(water, view.get(key(BIG.behind(12, 5, -2))), "the wall a row beneath it");
    }

    /**
     * Nothing of the far side is drawn anywhere but in the room past the tunnel: not in the tunnel, not
     * in the near opening, and not above the small gate's ceiling or in the halls beside it, at any layer.
     *
     * <p>Seen in a world: the far room's ceiling, pillars and a hall beside it drawn inside the tunnel.
     */
    @Test
    void theFarSideIsDrawnOnlyInTheRoomPastTheTunnelAtEveryLayer()
    {
        final Funnel funnel = funnel(16);
        final WindowShape shape = shapeFor(funnel);
        final Map<Long, BlockData> view = Windows.fixedTo(new WindowState(gate(funnel), solidEverywhere()), 16, 0L,
            Integer.MAX_VALUE);
        int stones = 0;
        int walls = 0;
        int hollows = 0;
        for (final Map.Entry<Long, BlockData> entry : view.entrySet())
        {
            final int x = Windows.unpackX(entry.getKey());
            final int y = Windows.unpackY(entry.getKey());
            final int z = Windows.unpackZ(entry.getKey());
            final int layer = shape.layerOf(x, z);
            final int across = shape.acrossOf(x, z);
            final int up = y - shape.base().y();
            final String where = "layer " + layer + ", column " + across + ", row " + up;
            assertTrue(layer >= 1, "nothing in or in front of the near opening: " + where);
            if (entry.getValue() == stone)
            {
                stones++;
                assertTrue(funnel.shows(layer, across, up, 0.0), "far side outside the room: " + where);
            }
            else if (entry.getValue() == water)
            {
                walls++;
                assertTrue(funnel.wall(layer, across, up), "water off the wall: " + where);
            }
            else
            {
                hollows++;
                assertSame(air, entry.getValue(), where);
                assertTrue(funnel.hollow(layer, across, up), "air outside the tunnel: " + where);
            }
        }
        assertTrue((stones > 0) && (walls > 0) && (hollows > 0), stones + " far, " + walls + " wall, " + hollows + " hollow");
    }

    /** The far side is shifted as deep as the tunnel: the block travellers land on shows on its first layer past it. */
    @Test
    void theFarSideIsShiftedToTheEndOfTheTunnel()
    {
        final Funnel funnel = funnel(16);
        final Spot arrival = new Spot(100, 70, 200);
        final Map<Long, BlockData> view = Windows.fixedTo(new WindowState(gate(funnel), keptAtFar(arrival)), 16, 0L,
            Integer.MAX_VALUE);

        assertEquals(arrival, shapeFor(funnel).farOf(15, 64, 12), "eight layers in, on the middle column and bottom row");
        assertSame(stone, view.get(key(BIG.behind(8, 5, 0))), "drawn there");
        assertNotSame(stone, view.get(key(BIG.behind(1, 5, 0))), "not where the plain view drew it, at the near opening");
    }

    @Test
    void aViewWalkedOverWhatWasKeptKeepsOnlyTheRoomAndWallsItIn()
    {
        final Funnel funnel = funnel(16);
        final WindowShape shape = shapeFor(funnel);
        final Map<Long, BlockData> view = Windows.fixedTo(new WindowState(gate(funnel),
            keptAtFar(farOf(shape, INSIDE, OUTSIDE, TUNNEL))), 16, 0L, Integer.MAX_VALUE);

        assertSame(stone, view.get(key(INSIDE)), "kept inside the room");
        assertSame(water, view.get(key(WALL)), "walled");
        assertFalse(view.containsKey(key(OUTSIDE)), "kept outside it: left out");
        assertSame(air, view.get(key(TUNNEL)), "kept where the tunnel is: hollow");
    }

    @Test
    void withNoFunnelTheWholeViewIsDrawnAndNoWall()
    {
        final Map<Long, BlockData> walked = Windows.fixedTo(new WindowState(gate(null), solidEverywhere()), 16, 0L,
            Integer.MAX_VALUE);
        final Map<Long, BlockData> kept = Windows.fixedTo(new WindowState(gate(null), keptAtFar(farOf(BIG, INSIDE, OUTSIDE))),
            16, 0L, Integer.MAX_VALUE);

        assertSame(stone, walked.get(key(OUTSIDE)), "same-size and small-to-large views are as they were");
        assertSame(stone, walked.get(key(WALL)), "and nothing is walled");
        assertSame(stone, walked.get(key(TUNNEL)), "or hollowed");
        assertSame(stone, kept.get(key(OUTSIDE)));
        assertFalse(walked.containsValue(water));
    }

    @Test
    void theWallStopsAtTheDepthAndTheWorldsFloor()
    {
        final WindowState window = new WindowState(gate(funnel(16)), solidEverywhere());

        final Map<Long, BlockData> view = Windows.tunnelled(window, new HashMap<>(), 16, 0L, Integer.MAX_VALUE);

        assertSame(water, view.get(key(BIG.behind(15, 3, 1))), "the last layer inside the depth");
        assertNull(view.get(key(BIG.behind(16, 3, 1))), "none at the depth");
        assertSame(water, view.get(key(BIG.behind(14, 6, -2))), "under the ground at the far opening's corner");
        assertNull(view.get(key(BIG.behind(15, 6, -2))), "past the depth from the opening's middle, though short of 16 layers");
        assertSame(water, view.get(key(BIG.behind(5, 5, -1))), "the tunnel's floor, above the world's");
        assertSame(water, view.get(key(BIG.behind(5, 1, 0))), "a side, on the bottom row");
        when(world.getMinHeight()).thenReturn(64);
        assertNull(Windows.tunnelled(window, new HashMap<>(), 16, 0L, Integer.MAX_VALUE).get(key(BIG.behind(5, 5, -1))),
            "never below the world's floor");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(64);
        assertNull(Windows.tunnelled(window, new HashMap<>(), 16, 0L, Integer.MAX_VALUE).get(key(BIG.behind(5, 1, 0))),
            "nor at or above its ceiling");
    }

    @Test
    void theHollowStopsAtTheDepthTheWorldsHeightAndWhereTheRealWorldIsEmpty()
    {
        final WindowState window = new WindowState(gate(funnel(16)), solidEverywhere());

        assertSame(air, Windows.tunnelled(window, new HashMap<>(), 16, 0L, Integer.MAX_VALUE).get(key(TUNNEL)));
        assertNull(Windows.tunnelled(window, new HashMap<>(), 3, 0L, Integer.MAX_VALUE).get(key(TUNNEL)),
            "a view three deep holds none of the tunnel's third layer");
        when(world.getMaxHeight()).thenReturn(70);
        assertNull(Windows.tunnelled(window, new HashMap<>(), 16, 0L, Integer.MAX_VALUE).get(key(TUNNEL)),
            "nor above the world's ceiling");
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(60);
        WindowSight.clear();
        assertNull(Windows.tunnelled(window, new HashMap<>(), 16, 0L, Integer.MAX_VALUE).get(key(TUNNEL)),
            "nor where the real world is already empty: nothing to send");
    }

    @Test
    void aTunnelThatTakesTheViewPastItsLimitIsTooMany() throws ReflectiveOperationException
    {
        final Map<Long, BlockData> tooMany = PrivateStatics.of(Windows.class, "TOO_MANY");
        final WindowState window = new WindowState(gate(funnel(16)), solidEverywhere());

        assertSame(tooMany, Windows.tunnelled(window, new HashMap<>(), 16, 0L, 10), "cut shallower, as any view past its limit");
        final Map<Long, BlockData> view = Windows.tunnelled(window, new HashMap<>(), 16, 0L, 100_000);
        assertNotNull(view);
        assertTrue(view.size() > 10, view.size() + " tunnel blocks fit under a limit that big");
        assertSame(tooMany, Windows.tunnelled(window, tooMany, 16, 0L, 100_000), "a view already past its limit stays so");
    }

    /**
     * A gate's held room is let go when its funnel changes, so a new tunnel shows at once rather than
     * after the minute a room is kept.
     */
    @Test
    void aHeldRoomIsLetGoWhenTheFunnelChanges() throws ReflectiveOperationException
    {
        final Capture capture = solidEverywhere();
        Windows.offer(gate(funnel(16)), capture);
        Windows.finish();
        final Map<String, WindowState> active = PrivateStatics.of(Windows.class, "ACTIVE");
        final Map<String, WindowState> offered = PrivateStatics.of(Windows.class, "OFFERED");
        final Map<Long, BlockData> room = new HashMap<>();
        active.get("gate:Abydos").fixed = room;
        active.get("gate:Abydos").fixedUsedAt = Windows.now();

        Windows.offer(gate(funnel(16)), capture);
        assertSame(room, offered.get("gate:Abydos").fixed, "the same funnel: kept");

        final Funnel same = funnel(16);
        final List<Spot> open = new ArrayList<>();
        BIG.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        final Funnel lava = new Funnel(same.width(), same.height(), same.far(), same.taper(), Material.LAVA);
        Windows.offer(new GateSource("gate:Abydos", anchor, shapeFor(lava), open, ARRIVAL, "Chulak", 16, lava), capture);
        assertNull(offered.get("gate:Abydos").fixed, "another funnel on the same shape: built again");
        active.get("gate:Abydos").fixed = room;
        Windows.offer(gate(funnel(4)), capture);
        assertNull(offered.get("gate:Abydos").fixed, "a shorter tunnel: built again");
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
        // The same shape both ways: only the funnel differs.
        final Funnel longer = funnel(16);
        final Funnel other = new Funnel(longer.width(), longer.height(), longer.far(), longer.taper(), Material.LAVA);
        final List<Spot> open = new ArrayList<>();
        BIG.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        Windows.offer(gate(longer), capture);
        Windows.finish();
        final Map<String, WindowState> active = PrivateStatics.of(Windows.class, "ACTIVE");
        final Map<String, WindowState> offered = PrivateStatics.of(Windows.class, "OFFERED");
        final Windows.Whole whole = new Windows.Whole(new HashMap<>(), 16);
        active.get("gate:Abydos").full = whole;
        active.get("gate:Abydos").fullFrom = capture;

        Windows.offer(gate(longer), capture);
        assertSame(whole, offered.get("gate:Abydos").full, "the same funnel: kept");

        Windows.offer(new GateSource("gate:Abydos", anchor, shapeFor(other), open, ARRIVAL, "Chulak", 16, other), capture);
        assertNull(offered.get("gate:Abydos").full, "another funnel on the same shape: drawn again");
    }

    @Test
    void theFarSideShowsOnlyPastTheTunnelAndWithNoFunnelEverywhere()
    {
        final WindowState funnelled = new WindowState(gate(funnel(16)), solidEverywhere());
        final WindowState whole = new WindowState(gate(null), solidEverywhere());

        assertTrue(funnelled.showsFar(INSIDE.x(), INSIDE.y(), INSIDE.z()));
        assertFalse(funnelled.showsFar(OUTSIDE.x(), OUTSIDE.y(), OUTSIDE.z()));
        assertFalse(funnelled.showsFar(WALL.x(), WALL.y(), WALL.z()), "a column out: no slack outside a held stand-in");
        assertFalse(funnelled.showsFar(INSIDE.x(), INSIDE.y() + 4, INSIDE.z()), "above the far gate's ceiling");
        assertFalse(funnelled.showsFar(TUNNEL.x(), TUNNEL.y(), TUNNEL.z()), "in the tunnel");
        assertTrue(whole.showsFar(OUTSIDE.x(), OUTSIDE.y(), OUTSIDE.z()));
        assertTrue(whole.showsFar(TUNNEL.x(), TUNNEL.y(), TUNNEL.z()));
        assertEquals(12, BIG.layerOf(INSIDE.x(), INSIDE.z()), "the shape's own layer and column, the other way");
        assertEquals(5, BIG.acrossOf(INSIDE.x(), INSIDE.z()));
    }

    /** How far a gate's capture reaches is measured from where travellers land, however deep its tunnel moves the far side. */
    @Test
    void howFarACaptureReachesIgnoresTheTunnel()
    {
        final Capture capture = mock(Capture.class);
        when(capture.bounds()).thenReturn(new int[] { 80, 60, 199, 120, 80, 240 });

        assertEquals(40, Windows.reachAhead(new WindowState(gate(funnel(16)), capture)), "from z 200 to 240");
        assertEquals(40, Windows.reachAhead(new WindowState(gate(null), capture)));
    }
}
