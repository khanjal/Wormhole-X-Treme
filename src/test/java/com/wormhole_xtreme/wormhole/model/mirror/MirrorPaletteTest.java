package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * What colour a block counts as.
 *
 * <p>Most of this is taste and not worth pinning down. What is worth pinning down is the
 * matching itself, because it is done on substrings and substrings collide: {@code AIR} is
 * inside {@code OAK_STAIRS}, {@code LIGHT} inside {@code LIGHTNING_ROD}, {@code STONE} inside
 * half the block names in the game. Each case below is one of those collisions, and getting
 * one wrong would quietly drop a whole family of blocks out of the sample.
 */
class MirrorPaletteTest
{
    @ParameterizedTest(name = "{0} is {1}")
    @DisplayName("the specific entry wins over the general one it is a substring of")
    @CsvSource({
        "SOUL_SAND, BROWN",          // not SAND -> YELLOW
        "GLOWSTONE, YELLOW",         // not STONE -> GRAY
        "REDSTONE_BLOCK, RED",       // not STONE -> GRAY
        "BLACKSTONE, BLACK",         // not STONE -> GRAY
        "END_STONE_BRICKS, YELLOW",  // not STONE, and not BRICK -> RED
        "SANDSTONE_STAIRS, YELLOW",  // not STONE -> GRAY
        "MOSSY_COBBLESTONE, GRAY",   // not MOSS -> GREEN
        "NETHER_BRICKS, RED",        // not BRICK, though it lands on RED either way
        "CRIMSON_PLANKS, RED",       // not PLANKS -> BROWN
        "WARPED_STEM, CYAN",         // not STEM -> BROWN
        "CHERRY_LEAVES, PINK",       // not LEAVES -> GREEN
        "MUSHROOM_STEM, RED",        // not STEM -> BROWN
        "BLUE_ICE, BLUE",            // the dye prefix, before ICE -> LIGHT_BLUE
        "PACKED_ICE, LIGHT_BLUE"
    })
    void resolvesCollisions(final String material, final DyeColor expected)
    {
        assertEquals(expected, MirrorPalette.of(material));
    }

    @ParameterizedTest(name = "{0} is not mistaken for something ignored")
    @DisplayName("AIR and LIGHT are matched exactly, not as substrings")
    @CsvSource({
        "OAK_STAIRS, BROWN",         // contains AIR
        "DEEPSLATE_STAIRS, GRAY",    // contains AIR
        "LIGHTNING_ROD, ORANGE",     // contains LIGHT, and is copper
        "LIGHT_GRAY_CONCRETE, LIGHT_GRAY",
        "LIGHT_BLUE_WOOL, LIGHT_BLUE"
    })
    void doesNotIgnoreBySubstring(final String material, final DyeColor expected)
    {
        assertEquals(expected, MirrorPalette.of(material));
    }

    @Test
    @DisplayName("plain glass shows nothing, stained glass shows its colour")
    void glass()
    {
        assertNull(MirrorPalette.of("GLASS"), "clear glass has no colour of its own");
        assertNull(MirrorPalette.of("GLASS_PANE"));
        assertNull(MirrorPalette.of("TINTED_GLASS"));
        assertEquals(DyeColor.WHITE, MirrorPalette.of("WHITE_STAINED_GLASS"),
            "stained glass is caught by the colour pass before glass is ignored");
    }

    @Test
    @DisplayName("a room full of shelves reads brown")
    void aLibrary()
    {
        assertEquals(DyeColor.BROWN, MirrorPalette.of("BOOKSHELF"));
        assertEquals(DyeColor.BROWN, MirrorPalette.of("CHISELED_BOOKSHELF"));
        assertEquals(DyeColor.BROWN, MirrorPalette.of("LECTERN"));
    }

    @Test
    @DisplayName("air and the blocks nobody can see are not colours")
    void ignored()
    {
        assertNull(MirrorPalette.of("AIR"));
        assertNull(MirrorPalette.of("CAVE_AIR"));
        assertNull(MirrorPalette.of("BARRIER"));
        assertNull(MirrorPalette.of("LIGHT"));
        assertNull(MirrorPalette.of("STRUCTURE_VOID"));
    }

    @Test
    @DisplayName("a block nothing in the table knows about has no colour rather than a wrong one")
    void unknown()
    {
        assertNull(MirrorPalette.of("SOMETHING_FROM_A_FUTURE_VERSION"));
        assertNull(MirrorPalette.of(null));
        assertNull(MirrorPalette.of(""));
    }

    @Test
    @DisplayName("case does not matter")
    void caseInsensitive()
    {
        assertEquals(MirrorPalette.of("BOOKSHELF"), MirrorPalette.of("bookshelf"));
    }

    @Test
    @DisplayName("the families that catch a block added in a newer version still answer")
    void generalEntriesStillCatch()
    {
        assertNotNull(MirrorPalette.of("SOME_NEW_LOG"), "a new wood should land on wood");
        assertNotNull(MirrorPalette.of("SOME_NEW_LEAVES"), "a new tree should land on leaves");
        assertNotNull(MirrorPalette.of("SOME_NEW_STONE"), "a new rock should land on stone");
    }
}
