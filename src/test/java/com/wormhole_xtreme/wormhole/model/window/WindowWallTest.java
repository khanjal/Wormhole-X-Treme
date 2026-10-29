package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Reading the wall round a window from the world: where the first gap in it is, and when the reading
 * is taken again (#430).
 *
 * <p>Written for the move out of {@code Windows}, on the conditions the older tests left unpinned:
 * each test below fails when the one condition it is named for is taken out of {@link WindowWall}.
 */
class WindowWallTest
{
    private static final Place ARRIVAL = new Place("far", 10.5, 70.0, 20.5, 0.0f, 0.0f);
    private static final WindowShape SHAPE = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 2, 2);

    private MockedStatic<ConfigManager> config;
    private World world;

    /** A window source that is only what the wall reading asks of one. */
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
    void setUp()
    {
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getMirrorProximityDistance).thenReturn(8);
        world = mock(World.class);
    }

    @AfterEach
    void tearDown()
    {
        config.close();
    }

    private WindowState window()
    {
        final List<Spot> open = new ArrayList<>();
        SHAPE.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        final Block anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
        return new WindowState(new Source("gate:Abydos", anchor, open), null);
    }

    /** Every block of the face the wall is read across, as a face key. */
    private static Set<Long> wholeFace(final WindowState window)
    {
        final int reach = WindowFace.wallReach();
        final int[] across = WindowFace.acrossSpan(SHAPE, reach);
        final Set<Long> face = new HashSet<>();
        for (int a = across[0]; a <= across[1]; a++)
        {
            for (int y = SHAPE.base().y() - reach; y <= (SHAPE.base().y() + SHAPE.height() + reach); y++)
            {
                face.add(WindowFace.faceKey(SHAPE, a, y));
            }
        }
        face.removeAll(window.openKeys);
        return face;
    }

    // --- Where the wall has a gap ---

    @Test
    void aWallSolidAllRoundHasNoGap()
    {
        final WindowState window = window();
        window.solid = wholeFace(window);

        assertTrue(WindowWall.walled(window), "solid all round");
        assertNull(WindowWall.gapBeside(window), "and so no gap");
    }

    @Test
    void aGapAtAnyEdgeOfTheReadFaceIsFoundWhereItIs()
    {
        final int reach = WindowFace.wallReach();
        final int[] across = WindowFace.acrossSpan(SHAPE, reach);
        final int bottom = SHAPE.base().y() - reach;
        final int top = SHAPE.base().y() + SHAPE.height() + reach;
        final int[][] edges = { { across[0], bottom }, { across[1], bottom }, { across[0], top }, { across[1], top },
            { across[0] + 1, bottom + 1 } };

        for (final int[] edge : edges)
        {
            final WindowState window = window();
            window.solid = wholeFace(window);
            final long face = WindowFace.faceKey(SHAPE, edge[0], edge[1]);
            window.solid.remove(face);

            final Spot gap = WindowWall.gapBeside(window);

            assertNotNull(gap, "a gap at across " + edge[0] + ", height " + edge[1]);
            assertEquals(edge[1], gap.y(), "at the height it is at");
            assertEquals(Cells.unpackX(face), gap.x(), "and the x");
            assertEquals(Cells.unpackZ(face), gap.z(), "and the z");
            assertFalse(WindowWall.walled(window), "so the wall is not solid");
        }
    }

    @Test
    void theOpeningItselfIsNotAGapAndANonSolidBlockBesideItIs()
    {
        final WindowState window = window();
        window.solid = wholeFace(window);

        assertNull(WindowWall.gapBeside(window), "the opening is not in the solid set, and is no gap");

        final long beside = window.solid.iterator().next();
        window.solid.remove(beside);

        assertNotNull(WindowWall.gapBeside(window), "a block beside it that is not solid is");
    }

    // --- When the solid blocks are read again ---

    private void everythingSolid()
    {
        final BlockData occluding = mock(BlockData.class);
        when(occluding.isOccluding()).thenReturn(true);
        final Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(occluding);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
    }

    @Test
    void theSolidBlocksAreReadAgainOnceAResampleHasPassedAndNotBefore()
    {
        everythingSolid();
        final WindowState window = window();
        final Set<Long> old = Set.of(1L);
        window.solid = old;
        window.solidAt = 1000L;

        WindowWall.refreshSolid(window, 1000L + Windows.RESAMPLE_MILLIS - 1L);

        assertSame(old, window.solid, "within the resample: left as read");
        assertEquals(1000L, window.solidAt, "and its time kept");

        WindowWall.refreshSolid(window, 1000L + Windows.RESAMPLE_MILLIS);

        assertEquals(wholeFace(window).size(), window.solid.size() - window.openKeys.size(),
            "a resample later: the face is read again, the opening's blocks too, being solid here");
        assertEquals(1000L + Windows.RESAMPLE_MILLIS, window.solidAt, "and the time of the reading noted");
    }

    @Test
    void aWallReadAgainIsNotSolidWhereTheWorldIsNot()
    {
        final BlockData open = mock(BlockData.class);
        final Block air = mock(Block.class);
        when(air.getBlockData()).thenReturn(open);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        final WindowState window = window();

        WindowWall.refreshSolid(window, Windows.RESAMPLE_MILLIS);

        assertTrue(window.solid.isEmpty(), "nothing occludes: nothing is solid");
        assertEquals(Windows.RESAMPLE_MILLIS, window.solidAt, "and it was read, at that time");
    }
}
