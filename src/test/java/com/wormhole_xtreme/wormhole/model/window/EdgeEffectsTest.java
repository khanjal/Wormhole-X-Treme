package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What a small window draws at the edge of its opening: nothing that throws particles (#309).
 *
 * <p>A particle cannot be taken back once the client has it, so a torch that has gone out of
 * sight keeps its flames in the air for up to a second. These guard which blocks are quieted,
 * which are left alone, and what a quieted block becomes.
 */
class EdgeEffectsTest
{
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp()
    {
        bukkit = mockStatic(Bukkit.class);
    }

    @AfterEach
    void tearDown()
    {
        bukkit.close();
    }

    private static WindowShape banner()
    {
        return WindowShape.of(new BlockPlace("world", 0, 64, 0), BlockFace.NORTH,
            new Place("far", 100.5, 70.0, -20.5, 0.0f, 0.0f));
    }

    private static boolean onEdge(final WindowShape shape, final int layer, final int across, final int up)
    {
        final Spot at = shape.behind(layer, across, up);
        return EdgeEffects.onEdge(shape, at.x(), at.y(), at.z());
    }

    private static BlockData of(final Material material)
    {
        final BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        return data;
    }

    private static Lightable lightable(final Material material, final boolean lit)
    {
        final Lightable data = mock(Lightable.class);
        when(data.getMaterial()).thenReturn(material);
        when(data.isLit()).thenReturn(lit);
        return data;
    }

    @Test
    void theRingOneBlockOutsideTheOpeningBehindTheWallIsTheEdge()
    {
        final WindowShape shape = banner();
        assertTrue(onEdge(shape, 1, -1, 0), "beside it on the left");
        assertTrue(onEdge(shape, 3, shape.width(), 1), "beside it on the right, deeper in");
        assertTrue(onEdge(shape, 1, 0, -1), "below it");
        assertTrue(onEdge(shape, 2, 0, shape.height()), "above it");
        assertTrue(onEdge(shape, 1, -1, shape.height()), "at a corner");
    }

    @Test
    void theMiddleOfTheViewAndTheWorldBeyondTheRingAreNotTheEdge()
    {
        final WindowShape shape = banner();
        assertFalse(onEdge(shape, 1, 0, 0), "straight behind the opening");
        assertFalse(onEdge(shape, 5, shape.width() - 1, shape.height() - 1), "straight behind, deep");
        assertFalse(onEdge(shape, 1, -2, 0), "two columns out");
        assertFalse(onEdge(shape, 1, 0, shape.height() + 1), "two rows up");
        assertFalse(onEdge(shape, 1, -3, -3), "a corner past the ring");
        assertFalse(onEdge(shape, 1, -2, -1), "two columns out and a row down");
        assertFalse(onEdge(shape, 1, shape.width() + 1, shape.height()), "two columns out on the right, a row up");
        assertFalse(onEdge(shape, 1, -1, shape.height() + 1), "a column out and two rows up");
        assertFalse(onEdge(shape, 1, shape.width(), -2), "a column out and two rows down");
    }

    @Test
    void nothingInFrontOfOrInTheOpeningIsTheEdge()
    {
        final WindowShape shape = banner();
        assertFalse(onEdge(shape, 0, -1, 0), "in the wall's own layer");
        assertFalse(onEdge(shape, -1, -1, 0), "in front of the wall");
    }

    @Test
    void aTorchBecomesAnUnlitRedstoneTorch()
    {
        final Lightable out = lightable(Material.REDSTONE_TORCH, true);
        bukkit.when(() -> Bukkit.createBlockData(Material.REDSTONE_TORCH)).thenReturn(out);
        assertSame(out, EdgeEffects.quieted(of(Material.TORCH)));
        verify(out).setLit(false);
    }

    @Test
    void aWallTorchKeepsItsWallAndFacing()
    {
        final Directional was = mock(Directional.class, org.mockito.Mockito.withSettings().extraInterfaces(BlockData.class));
        when(((BlockData) was).getMaterial()).thenReturn(Material.SOUL_WALL_TORCH);
        when(was.getFacing()).thenReturn(BlockFace.EAST);
        final BlockData out = mock(BlockData.class, org.mockito.Mockito.withSettings()
            .extraInterfaces(Directional.class, Lightable.class));
        bukkit.when(() -> Bukkit.createBlockData(Material.REDSTONE_WALL_TORCH)).thenReturn(out);
        assertSame(out, EdgeEffects.quieted((BlockData) was));
        verify((Directional) out).setFacing(BlockFace.EAST);
        verify((Lightable) out).setLit(false);
    }

    @Test
    void fireAndSoulFireAreDrawnAsAir()
    {
        final BlockData air = of(Material.AIR);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
        assertSame(air, EdgeEffects.quieted(of(Material.FIRE)));
        assertSame(air, EdgeEffects.quieted(of(Material.SOUL_FIRE)));
    }

    @Test
    void aLitCandleOrCampfireIsDrawnUnlitAndAnUnlitOneIsLeftAlone()
    {
        for (final Material material : new Material[] { Material.CANDLE, Material.RED_CANDLE, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.CANDLE_CAKE, Material.RED_CANDLE_CAKE, Material.REDSTONE_TORCH,
            Material.REDSTONE_WALL_TORCH })
        {
            final Lightable lit = lightable(material, true);
            final Lightable copy = lightable(material, true);
            when(lit.clone()).thenReturn(copy);
            assertSame(copy, EdgeEffects.quieted(lit), material.name());
            verify(copy).setLit(false);

            final Lightable out = lightable(material, false);
            assertSame(out, EdgeEffects.quieted(out), material + " already out");
        }
    }

    @Test
    void blocksThatThrowNothingAreLeftAlone()
    {
        for (final Material material : new Material[] { Material.STONE, Material.LANTERN, Material.CAKE })
        {
            final BlockData data = of(material);
            assertSame(data, EdgeEffects.quieted(data), material.name());
        }
    }

    @Test
    void aWindowWithNoOpeningIsNotSmall()
    {
        final WindowShape shape = banner();
        final Spot edge = shape.behind(1, -1, 0);
        final BlockData fire = of(Material.FIRE);
        assertSame(fire, EdgeEffects.drawn(0, shape, edge.x(), edge.y(), edge.z(), fire));
    }

    @Test
    void onlyASmallWindowsEdgeIsQuieted()
    {
        final WindowShape shape = banner();
        final Spot edge = shape.behind(1, -1, 0);
        final Spot middle = shape.behind(1, 0, 0);
        final BlockData air = of(Material.AIR);
        bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
        final BlockData fire = of(Material.FIRE);

        assertSame(air, EdgeEffects.drawn(Windows.SMALL_OPEN, shape, edge.x(), edge.y(), edge.z(), fire));
        assertSame(fire, EdgeEffects.drawn(Windows.SMALL_OPEN + 1, shape, edge.x(), edge.y(), edge.z(), fire),
            "a wide window's edge is hidden by its wall");
        assertSame(fire, EdgeEffects.drawn(Windows.SMALL_OPEN, shape, middle.x(), middle.y(), middle.z(), fire),
            "the middle of the view keeps its flames");
        assertEquals(Material.FIRE, fire.getMaterial());
    }
}
