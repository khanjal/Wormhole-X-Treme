package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * A view built a step at a time holds exactly the blocks the old walk built in one go (#609).
 *
 * <p>The walk behind a window's opening was a tick of 300 to 800 ms once a minute per watched
 * window. It is now stepped a few milliseconds a tick, and the real world is read once per column
 * rather than once per block through a cache. Nothing a player sees may change with that, so each
 * test here builds the same view both ways -- {@link LegacyFixedView} is the old walk, unchanged --
 * over hilly far sides, real ground with caves and unloaded chunks, windows facing every way, a
 * tunnel, and a limit the view does not fit under, and asks for the same blocks.
 */
class FixedBuildTest
{
    private static final int DEPTH = 24;

    private World world;
    private Block anchor;
    private BlockData air;
    private BlockData water;
    private MockedStatic<Bukkit> bukkit;
    private final BlockData stone = state("minecraft:stone", true);
    private final BlockData dirt = state("minecraft:dirt", true);
    private final BlockData grass = state("minecraft:grass_block", true);
    private final BlockData flower = state("minecraft:poppy", false);

    @BeforeEach
    void setUp()
    {
        air = state("minecraft:air", false);
        water = state("minecraft:water", false);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
        bukkit.when(() -> Bukkit.createBlockData(Material.WATER)).thenReturn(water);
        final Block empty = mock(Block.class, withSettings().stubOnly());
        when(empty.isEmpty()).thenReturn(true);
        final Block solid = mock(Block.class, withSettings().stubOnly());
        world = mock(World.class, withSettings().stubOnly());
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        // Every fifth chunk is not loaded: its far-side air is drawn as air, the real block unread.
        when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(call ->
            Math.floorMod((int) call.getArgument(0) + (int) call.getArgument(1), 5) != 0);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenAnswer(call ->
            realTop(call.getArgument(0), call.getArgument(1)));
        // Under the surface, caves: some real blocks below a column's top are empty.
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final int x = call.getArgument(0);
            final int y = call.getArgument(1);
            final int z = call.getArgument(2);
            return ((y > realTop(x, z)) || (Math.floorMod((x * 7) + (y * 3) + (z * 5), 4) == 0)) ? empty : solid;
        });
        anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
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

    /** A state whose copy, which a turned window turns, is a state of its own, so an unturned block shows. */
    private static BlockData state(final String name, final boolean occluding)
    {
        final BlockData data = mock(BlockData.class, withSettings().stubOnly());
        when(data.getAsString()).thenReturn(name);
        when(data.isOccluding()).thenReturn(occluding);
        final BlockData copy = mock(BlockData.class, withSettings().stubOnly());
        when(copy.getAsString()).thenReturn(name + " turned");
        when(data.clone()).thenReturn(copy);
        return data;
    }

    /** The real ground here: rolling, round the openings' height. */
    private static int realTop(final int x, final int z)
    {
        return 62 + (int) (4 * Math.sin(x / 3.0) * Math.cos(z / 4.0));
    }

    /** A far side of hills round an arrival, seen from it when {@code seen}, as a capture taken on a server is. */
    private Capture hillside(final Place arrival, final boolean seen)
    {
        final int ax = (int) Math.floor(arrival.x());
        final int ay = (int) Math.floor(arrival.y());
        final int az = (int) Math.floor(arrival.z());
        final Capture.Box box = new Capture.Box(ax - 30, ay - 20, az - 30, 61, 50, 61);
        final Capture.Builder builder = new Capture.Builder("far", true, box, air);
        for (int x = box.minX(); x < (box.minX() + box.sizeX()); x++)
        {
            for (int z = box.minZ(); z < (box.minZ() + box.sizeZ()); z++)
            {
                final int ground = (ay - 2) + (int) (6 * Math.sin(x / 5.0) * Math.cos(z / 7.0));
                for (int y = box.minY(); y <= (ground - 3); y++)
                {
                    builder.put(x, y, z, stone);
                }
                builder.put(x, ground - 2, z, dirt);
                builder.put(x, ground - 1, z, dirt);
                builder.put(x, ground, z, grass);
                if ((((x * 31) + z) % 11) == 0)
                {
                    builder.put(x, ground + 1, z, flower);
                }
            }
        }
        if (seen)
        {
            builder.keepOnlySeen(new Capture.Arrival(ax, ay, az, 0, 1, 5, 5), box.sizeX());
            builder.prune();
        }
        return builder.build();
    }

    /** A gate's five-square window at (10, 64, 20) looked into along {@code into}, onto an arrival. */
    private WindowState window(final Spot into, final Place arrival, final Capture capture, final Funnel funnel)
    {
        final WindowShape plain = WindowShape.through(new Spot(10, 64, 20), into, arrival, 5, 5);
        final WindowShape shape = (funnel == null) ? plain : plain.deeper(funnel.taper() - 1);
        final List<Spot> open = new ArrayList<>();
        plain.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        return new WindowState(new GateSource("gate:Abydos", anchor, shape, open, arrival, "Chulak", DEPTH, funnel), capture);
    }

    /** The view built both ways, asserted the same and not trivially so. */
    private void assertBuiltAsBefore(final WindowState window, final String what)
    {
        WindowSight.clear();
        final Map<Long, BlockData> before = LegacyFixedView.fixedTo(window, DEPTH, 0L, Integer.MAX_VALUE);
        final Map<Long, BlockData> now = Windows.fixedTo(window, DEPTH, 0L, Integer.MAX_VALUE);
        assertNotSame(LegacyFixedView.TOO_MANY, before, what);
        assertTrue((before.containsValue(grass) || before.containsValue(grass.clone())) && before.containsValue(air),
            what + ": far ground, turned or not, and carved air, "
            + before.size() + " blocks");
        assertEquals(before, now, what + ": the same blocks as the walk in one go");
    }

    /** Over what a capture kept: its surfaces and its seen air. */
    @Test
    void aViewOverWhatTheCaptureKeptHoldsTheBlocksTheOldWalkDid()
    {
        final Place south = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);
        assertBuiltAsBefore(window(new Spot(0, 0, -1), south, hillside(south, true), null), "looked into northwards");
    }

    /** Through the volume, for a capture with no seen air, in which every missing block is air. */
    @Test
    void aViewThroughTheVolumeHoldsTheBlocksTheOldWalkDid()
    {
        final Place south = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);
        assertBuiltAsBefore(window(new Spot(0, 0, -1), south, hillside(south, false), null), "looked into northwards");
    }

    /** Facing every way, so the far side is turned a quarter, a half, three quarters or not at all. */
    @Test
    void aViewTurnedEveryWayHoldsTheBlocksTheOldWalkDid()
    {
        final Spot[] intos = { new Spot(0, 0, -1), new Spot(1, 0, 0), new Spot(0, 0, 1), new Spot(-1, 0, 0) };
        final float[] yaws = { 0.0f, 90.0f, 180.0f, 270.0f };
        for (final Spot into : intos)
        {
            for (final float yaw : yaws)
            {
                final Place arrival = new Place("far", 100.5, 70.0, 200.5, yaw, 0.0f);
                assertBuiltAsBefore(window(into, arrival, hillside(arrival, true), null), "into " + into + ", far yaw " + yaw);
            }
        }
    }

    /** With a tunnel, over what was kept and through the volume: the far side shifted past it, its wall and its hollow. */
    @Test
    void aViewWithATunnelHoldsTheBlocksTheOldWalkDid()
    {
        final Place south = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);
        final WindowShape big = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), south, 5, 5);
        final List<Spot> farCells = new ArrayList<>();
        for (int x = 100; x <= 101; x++)
        {
            for (int y = 70; y <= 72; y++)
            {
                farCells.add(new Spot(x, y, 199));
            }
        }
        final Funnel funnel = Funnel.of(big, farCells, 16, Material.WATER);
        assertNotNull(funnel, "a five-square gate onto a two by three one has a tunnel");
        for (final boolean seen : new boolean[] { true, false })
        {
            final WindowState window = window(new Spot(0, 0, -1), south, hillside(south, seen), funnel);
            assertBuiltAsBefore(window, "through a tunnel, " + (seen ? "over what was kept" : "through the volume"));
            assertTrue(Windows.fixedTo(window, DEPTH, 0L, Integer.MAX_VALUE).containsValue(water), "walled in water");
        }
    }

    /**
     * Past its limit the view is cut a quarter shallower until it fits, to the same depth and blocks
     * the old cut reached; and asked not to cut, the build says it did not fit.
     */
    @Test
    void aViewPastItsLimitIsCutToTheSameDepthAndBlocks()
    {
        final Place south = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);
        final WindowState window = window(new Spot(0, 0, -1), south, hillside(south, true), null);
        final int full = LegacyFixedView.fixedTo(window, DEPTH, 0L, Integer.MAX_VALUE).size();
        final int most = full / 3;
        final int[] depth = new int[1];
        WindowSight.clear();
        final Map<Long, BlockData> before = LegacyFixedView.cutToFit(window, DEPTH, 0L, most, depth);

        final FixedBuild build = new FixedBuild(window, DEPTH, most, 0L, 0L, true);
        int steps = 0;
        while (!build.step())
        {
            steps++;
        }

        assertTrue((depth[0] < DEPTH) && !before.isEmpty(), "cut to " + depth[0] + ", " + before.size() + " blocks");
        assertEquals(depth[0], build.depth(), "cut to the same depth");
        assertEquals(before, build.view(), "holding the same blocks");
        assertTrue(steps > 1, "a step at a time, not all at once: " + steps);
        assertTrue(Windows.tooMany(Windows.fixedTo(window, DEPTH, 0L, most)), "asked not to cut, it says too many");
    }

    /**
     * No step is handed more than sixteen columns of the capture, or one column of the volume: a step
     * of a whole x was up to a sixth of a million blocks of a gate's capture, all in one tick (#609).
     * The capture here is 61 across and 50 tall, so a step of an x would be up to 3050 blocks.
     */
    @Test
    void noStepIsHandedMoreThanSixteenColumns()
    {
        final Place south = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);
        for (final boolean seen : new boolean[] { true, false })
        {
            final WindowState window = window(new Spot(0, 0, -1), south, hillside(south, seen), null);
            final FixedBuild build = new FixedBuild(window, DEPTH, Integer.MAX_VALUE, 0L, 0L, true);
            int most = 0;
            int steps = 0;
            boolean done = false;
            while (!done)
            {
                done = build.step();
                most = Math.max(most, build.cellsLastStep());
                steps++;
            }
            final int bound = seen ? (16 * 50) : ((2 * DEPTH) + 3);
            assertTrue((most > 0) && (most <= bound), (seen ? "over what was kept" : "through the volume") + ": at most "
                + most + " blocks a step, against " + bound);
            assertTrue(steps > 61, steps + " steps, more than one an x");
        }
    }
}
