package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading a {@code .mirror} file.
 *
 * <p>The leniency is the part worth pinning down. A preset file is hand-edited on a live
 * server, and the parser is deliberately willing to ignore what it does not understand -- so
 * these check both that a good file is read correctly and that a bad line costs only itself.
 */
class MirrorPresetTest
{
    @Test
    @DisplayName("reads name, base, biomes and layers in order")
    void readsAWholeFile()
    {
        final MirrorPreset preset = MirrorPreset.parse("fallback", List.of(
            "# a comment",
            "Name=nether",
            "Base=RED",
            "Biome=NETHER_WASTES,CRIMSON_FOREST",
            "Layer=BLACK TRIANGLES_BOTTOM",
            "Layer=ORANGE GRADIENT_UP"));

        assertEquals("nether", preset.name());
        assertEquals(DyeColor.RED, preset.base());
        assertEquals(List.of(DyeColor.BLACK, DyeColor.ORANGE),
            preset.layers().stream().map(MirrorPreset.Layer::colour).toList());
        assertEquals(List.of("TRIANGLES_BOTTOM", "GRADIENT_UP"),
            preset.layers().stream().map(MirrorPreset.Layer::pattern).toList());
        assertTrue(preset.answersFor("nether_wastes"), "biome match should ignore case");
        assertFalse(preset.answersFor("PLAINS"));
    }

    @Test
    @DisplayName("falls back to the file's own name when the file does not say")
    void usesTheFallbackName()
    {
        assertEquals("cavern", MirrorPreset.parse("cavern", List.of("Base=GRAY")).name());
    }

    @Test
    @DisplayName("no base colour means no preset")
    void refusesWithoutABase()
    {
        assertNull(MirrorPreset.parse("x", List.of("Name=x", "Layer=RED BORDER")),
            "a preset with nothing to dye the banner is not a preset");
        assertNull(MirrorPreset.parse("x", List.of("Base=CHARTREUSE")),
            "a colour Bukkit does not have is no base at all");
    }

    @Test
    @DisplayName("a bad line costs only itself")
    void skipsWhatItCannotRead()
    {
        final MirrorPreset preset = MirrorPreset.parse("x", List.of(
            "Base=GREEN",
            "Wobble=yes",
            "no equals sign here",
            "=leading equals",
            "Layer=NOTACOLOUR BORDER",
            "Layer=RED",
            "Layer=LIME BORDER"));

        assertEquals(1, preset.layers().size(), "only the one good layer should survive");
        assertEquals(DyeColor.LIME, preset.layers().get(0).colour());
        assertEquals(DyeColor.GREEN, preset.base(), "the rest of the file still applies");
    }

    @Test
    @DisplayName("biomes may be spread over several lines, and blanks are dropped")
    void collectsBiomesAcrossLines()
    {
        final MirrorPreset preset = MirrorPreset.parse("x", List.of(
            "Base=BLUE",
            "Biome=OCEAN, ,COLD_OCEAN",
            "Biome=warm_ocean"));

        assertEquals(3, preset.biomes().size());
        assertTrue(preset.answersFor("WARM_OCEAN"), "a lower-case line should still match");
        assertTrue(preset.answersFor("COLD_OCEAN"), "spaces around a name should be trimmed");
    }

    @Test
    @DisplayName("no lines, and a null line, are both survivable")
    void toleratesNothing()
    {
        assertNull(MirrorPreset.parse("x", null), "nothing to read is no preset");

        final MirrorPreset preset =
            MirrorPreset.parse("x", java.util.Arrays.asList("Base=RED", null, "Layer=BLUE CROSS"));
        assertEquals(DyeColor.RED, preset.base());
        assertEquals(1, preset.layers().size(), "the line after the null should still be read");
    }

    @Test
    @DisplayName("what comes out cannot be changed underneath the registry")
    void isImmutable()
    {
        final MirrorPreset preset = MirrorPreset.parse("x", List.of("Base=RED",
            "Biome=PLAINS", "Layer=BLUE BORDER"));
        final List<MirrorPreset.Layer> layers = preset.layers();
        final java.util.Set<String> biomes = preset.biomes();
        final MirrorPreset.Layer extra = new MirrorPreset.Layer(DyeColor.RED, "BORDER");

        assertThrows(UnsupportedOperationException.class, () -> layers.add(extra));
        assertThrows(UnsupportedOperationException.class, () -> biomes.add("SWAMP"));
    }

    @Test
    @DisplayName("a null biome matches nothing")
    void nullBiomeMatchesNothing()
    {
        assertFalse(MirrorPreset.parse("x", List.of("Base=RED", "Biome=PLAINS"))
            .answersFor(null));
    }
}
