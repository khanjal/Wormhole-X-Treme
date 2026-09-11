package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.IntPredicate;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Reading the far side of a mirror.
 *
 * <p>The world is a mock that answers every block the sampler asks for, so what these check is
 * the reduction: which colours come back, in what order, and whether the place reads as indoors.
 * The biome is left out -- it comes off a {@code Biome} whose very kind changes between 1.20
 * and 1.21.4, so it is verified against the real jars rather than against a mock that would
 * agree with whatever was written here.
 */
class MirrorViewTest
{
    /** The destination every test looks at. */
    private static final MirrorPoint POINT = new MirrorPoint("far", 100, 64, 200, 0f, 0f);

    @Test
    @DisplayName("a world that is not loaded cannot be looked at")
    void unloadedWorld()
    {
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(null);

            assertNull(MirrorView.look(POINT), "no world is not the same as nothing to see");
        }
        assertNull(MirrorView.look(null));
    }

    @Test
    @DisplayName("open sky over grass reads green, and not as indoors")
    void aMeadow()
    {
        final MirrorView view = look(Material.GRASS_BLOCK, Material.AIR, y -> y < 64);

        assertEquals(DyeColor.GREEN, view.dominant());
        assertFalse(view.enclosed(), "mostly air is not a room");
    }

    @Test
    @DisplayName("a room full of shelves reads brown, and as indoors")
    void aLibrary()
    {
        final MirrorView view = look(Material.BOOKSHELF, Material.STONE, y -> y != 64);

        assertEquals(DyeColor.BROWN, view.dominant(), "the shelves, not the walls");
        assertTrue(view.enclosed(), "solid all round is a room");
        assertTrue(view.colours().contains(DyeColor.GRAY), "the walls should still show");
    }

    @Test
    @DisplayName("the colours come back commonest first, and no more than three")
    void ordersAndTrims()
    {
        // Four colours in descending quantity: two layers of stone, one of water, one of
        // leaves with a single lava block in it. The lava is the fourth and should be dropped.
        final World world = mock(World.class);
        stub(world, (x, y, z) ->
        {
            if (y <= 64)
            {
                return Material.STONE;
            }
            if (y == 66)
            {
                return Material.WATER;
            }
            return ((x == 100) && (z == 200)) ? Material.LAVA : Material.OAK_LEAVES;
        });

        final MirrorView view = lookIn(world);

        assertEquals(List.of(DyeColor.GRAY, DyeColor.BLUE, DyeColor.GREEN), view.colours(),
            "commonest first, and the single lava block trimmed off the end");
    }

    @Test
    @DisplayName("nothing but air has no colours and is not a room")
    void emptySpace()
    {
        final MirrorView view = look(Material.AIR, Material.AIR, y -> true);

        assertTrue(view.colours().isEmpty());
        assertNull(view.dominant());
        assertFalse(view.enclosed());
    }

    @Test
    @DisplayName("solid blocks with no colour still count as a room")
    void enclosedByColourlessBlocks()
    {
        final MirrorView view = look(Material.GLASS, Material.GLASS, y -> true);

        assertTrue(view.enclosed(), "a glasshouse is still enclosed");
        assertTrue(view.colours().isEmpty(), "clear glass shows nothing");
    }

    @Test
    @DisplayName("what comes out cannot be changed underneath the stamp")
    void isImmutable()
    {
        final MirrorView view = new MirrorView("PLAINS", new java.util.ArrayList<>(), false);

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> view.colours().add(DyeColor.RED));
    }

    /** Looks at a world where {@code when} picks the first material and everything else the second. */
    private static MirrorView look(final Material yes, final Material no, final IntPredicate when)
    {
        final World world = mock(World.class);
        stub(world, (x, y, z) -> when.test(y) ? yes : no);
        return lookIn(world);
    }

    /** Runs the sampler against a stubbed world. */
    private static MirrorView lookIn(final World world)
    {
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(world);
            return MirrorView.look(POINT);
        }
    }

    /**
     * Answers every getBlockAt with a block of whatever the function says.
     *
     * <p>The biome is left unstubbed, so it comes back null and the view's biome is empty.
     * That is deliberate: stubbing a {@code Biome} would mean naming the type, and the whole
     * point of how the view reads one is that it never names the type.
     */
    private static void stub(final World world, final MaterialAt materials)
    {
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
        {
            final int x = invocation.getArgument(0);
            final int y = invocation.getArgument(1);
            final int z = invocation.getArgument(2);
            final Block block = mock(Block.class);
            when(block.getType()).thenReturn(materials.at(x, y, z));
            return block;
        });
    }

    /** What block is where. */
    @FunctionalInterface
    private interface MaterialAt
    {
        /**
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @return the material there
         */
        Material at(int x, int y, int z);
    }
}
