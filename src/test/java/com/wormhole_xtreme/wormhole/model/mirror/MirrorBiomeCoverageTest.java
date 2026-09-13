package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every biome in the game has a look of its own, and no biome has two.
 *
 * <p>The shipped library used to answer for biomes in groups -- one {@code ocean} for nine
 * oceans, one {@code forest} for ten woods -- so two places that look nothing like each other
 * got the same banner and a mirror told you which family you were looking at rather than where
 * you were going. This is the test that keeps the one-each rule true as biomes are added.
 *
 * <p>The list is written out rather than read from {@code Biome}, and that is not laziness. CI
 * builds this plugin against both 1.20.4 and 1.21.10, and {@code Biome} is an enum on the one
 * and registry-backed on the other -- {@code Biome.values()} compiles here and fails there,
 * which is the same trap {@code PatternType} laid for the stamp. A written list also fails in
 * the direction that matters: when Mojang adds a biome, somebody has to come here and add it,
 * which is exactly the moment to write its preset.
 */
class MirrorBiomeCoverageTest
{
    /** Every biome on 1.21.10, from the API jar, less {@code CUSTOM}, which is a placeholder. */
    private static final Set<String> VANILLA_BIOMES = Set.of(
        "BADLANDS", "BAMBOO_JUNGLE", "BASALT_DELTAS", "BEACH", "BIRCH_FOREST", "CHERRY_GROVE",
        "COLD_OCEAN", "CRIMSON_FOREST", "DARK_FOREST", "DEEP_COLD_OCEAN", "DEEP_DARK",
        "DEEP_FROZEN_OCEAN", "DEEP_LUKEWARM_OCEAN", "DEEP_OCEAN", "DESERT", "DRIPSTONE_CAVES",
        "END_BARRENS", "END_HIGHLANDS", "END_MIDLANDS", "ERODED_BADLANDS", "FLOWER_FOREST",
        "FOREST", "FROZEN_OCEAN", "FROZEN_PEAKS", "FROZEN_RIVER", "GROVE", "ICE_SPIKES",
        "JAGGED_PEAKS", "JUNGLE", "LUKEWARM_OCEAN", "LUSH_CAVES", "MANGROVE_SWAMP", "MEADOW",
        "MUSHROOM_FIELDS", "NETHER_WASTES", "OCEAN", "OLD_GROWTH_BIRCH_FOREST",
        "OLD_GROWTH_PINE_TAIGA", "OLD_GROWTH_SPRUCE_TAIGA", "PALE_GARDEN", "PLAINS", "RIVER",
        "SAVANNA", "SAVANNA_PLATEAU", "SMALL_END_ISLANDS", "SNOWY_BEACH", "SNOWY_PLAINS",
        "SNOWY_SLOPES", "SNOWY_TAIGA", "SOUL_SAND_VALLEY", "SPARSE_JUNGLE", "STONY_PEAKS",
        "STONY_SHORE", "SUNFLOWER_PLAINS", "SWAMP", "TAIGA", "THE_END", "THE_VOID",
        "WARM_OCEAN", "WARPED_FOREST", "WINDSWEPT_FOREST", "WINDSWEPT_GRAVELLY_HILLS",
        "WINDSWEPT_HILLS", "WINDSWEPT_SAVANNA", "WOODED_BADLANDS");

    @TempDir
    File folder;

    /** Every biome resolves to a preset that names it, rather than to the fallback. */
    @Test
    void everyVanillaBiomeHasItsOwnLook()
    {
        MirrorPresetRegistry.load(folder);
        final List<String> missing = new ArrayList<>();

        for (final String biome : VANILLA_BIOMES)
        {
            final MirrorPreset preset = MirrorPresetRegistry.forBiome(biome);
            assertNotNull(preset, biome + " resolved to nothing at all");
            if (!preset.answersFor(biome))
            {
                missing.add(biome);
            }
        }

        assertEquals(List.of(), missing,
            "these biomes fell through to the fallback look instead of having one of their own");
    }

    /**
     * No two presets claim the same biome.
     *
     * <p>{@code forBiome} returns the first preset that answers, and the order it walks is the
     * order the files loaded -- so a biome named twice does not produce a warning or a conflict,
     * it produces whichever look happened to load first. That is invisible until somebody
     * notices the wrong banner, and it is the easiest mistake to make when adding a look by
     * copying the one next to it.
     */
    @Test
    void noBiomeIsClaimedByTwoPresets()
    {
        MirrorPresetRegistry.load(folder);
        final Map<String, List<String>> claims = new HashMap<>();

        for (final MirrorPreset preset : MirrorPresetRegistry.all())
        {
            for (final String biome : preset.biomes())
            {
                claims.computeIfAbsent(biome, any -> new ArrayList<>()).add(preset.name());
            }
        }

        final Map<String, List<String>> duplicated = new HashMap<>();
        claims.forEach((biome, presets) ->
        {
            if (presets.size() > 1)
            {
                duplicated.put(biome, presets);
            }
        });

        assertEquals(Map.of(), duplicated, "each of these biomes is claimed by more than one"
            + " preset, so which look it gets depends on load order");
    }

    /** Nothing claims a biome that is not in the game. */
    @Test
    void noPresetNamesABiomeThatDoesNotExist()
    {
        MirrorPresetRegistry.load(folder);
        final Set<String> unknown = new HashSet<>();

        for (final MirrorPreset preset : MirrorPresetRegistry.all())
        {
            for (final String biome : preset.biomes())
            {
                if (!VANILLA_BIOMES.contains(biome))
                {
                    unknown.add(preset.name() + " -> " + biome);
                }
            }
        }

        assertEquals(Set.of(), unknown, "a misspelt biome name never matches anything and never"
            + " says so; the preset simply never gets picked");
    }

    /**
     * The looks that name no biome are still there, and are still the way to say something
     * about a mirror that is not about where it goes.
     */
    @Test
    void keepsTheStampOnlyLooksAlongsideThePlaces()
    {
        MirrorPresetRegistry.load(folder);

        for (final String look : List.of("plain", "hub", "warning", "private", "arcane",
            "portal", "spawn", "exit", "arrival", "locked", "staff", "market", "shrine",
            "danger", "tomb", "vault", "forge", "library", "port", "compass", "cavern",
            "indoors", "overworld"))
        {
            final MirrorPreset preset = MirrorPresetRegistry.byName(look);
            assertNotNull(preset, look + " should be loaded and stampable by name");
            assertTrue(preset.biomes().isEmpty(),
                look + " names a biome, so it would win an automatic lookup it is not for");
        }
    }
}
