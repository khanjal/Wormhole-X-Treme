package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Loading the presets folder.
 *
 * <p>Every test points the registry at a temporary directory. The no-argument {@code load()}
 * resolves the live plugin folder and writes every shipped preset into it, which is how an earlier
 * test ended up committing {@code data/mirror.yml} into the repository.
 */
class MirrorPresetRegistryTest
{
    /**
     * The pattern names present in {@code PatternType} on every version this plugin supports.
     *
     * <p>The intersection of the 1.20 and 1.21.10 API jars: 41 names on the one, 43 on the
     * other, 34 in common. Written out rather than computed, because the point is to fail when
     * somebody adds a preset naming something outside it, and a set derived from whatever
     * Bukkit is on the test classpath would happily agree with them.
     */
    private static final Set<String> STABLE_PATTERNS = Set.of("BASE", "BORDER", "BRICKS",
        "CREEPER", "CROSS", "CURLY_BORDER", "DIAGONAL_LEFT", "DIAGONAL_RIGHT", "FLOWER",
        "GLOBE", "GRADIENT", "GRADIENT_UP", "HALF_HORIZONTAL", "HALF_VERTICAL", "MOJANG",
        "PIGLIN", "SKULL", "SQUARE_BOTTOM_LEFT", "SQUARE_BOTTOM_RIGHT", "SQUARE_TOP_LEFT",
        "SQUARE_TOP_RIGHT", "STRAIGHT_CROSS", "STRIPE_BOTTOM", "STRIPE_CENTER",
        "STRIPE_DOWNLEFT", "STRIPE_DOWNRIGHT", "STRIPE_LEFT", "STRIPE_MIDDLE", "STRIPE_RIGHT",
        "STRIPE_TOP", "TRIANGLES_BOTTOM", "TRIANGLES_TOP", "TRIANGLE_BOTTOM", "TRIANGLE_TOP");

    @TempDir
    File folder;

    @Test
    void writesOutEveryShippedPresetOnAFirstRunAndReadsThemBack()
    {
        final int loaded = MirrorPresetRegistry.load(folder);

        assertEquals(MirrorPresetRegistry.shippedNames().size(), loaded,
            "every shipped preset should have been written and then read");
        for (final String name : MirrorPresetRegistry.shippedNames())
        {
            assertTrue(new File(folder, name).isFile(), name + " should have been restored");
        }
        assertNotNull(MirrorPresetRegistry.byName("nether"));
        assertNotNull(MirrorPresetRegistry.byName("NETHER"), "lookup should ignore case");
    }

    /**
     * The list in the registry and the files in the jar have to agree, both ways.
     *
     * <p>A name in the list with no file behind it logs a warning on every first run. A file
     * with no name in the list ships in the jar and is never written out, so an operator never
     * sees it -- which is the quieter and worse of the two.
     */
    @Test
    void listsExactlyThePresetFilesThatShipInTheJar() throws IOException, URISyntaxException
    {
        for (final String name : MirrorPresetRegistry.shippedNames())
        {
            try (final InputStream is =
                WormholeXTreme.class.getResourceAsStream("/shapes/mirror/" + name))
            {
                assertNotNull(is, name + " is listed as shipped but is not in the jar");
            }
        }
        final URL directory = WormholeXTreme.class.getResource("/shapes/mirror");
        assertNotNull(directory, "the shipped presets should be on the classpath");
        try (final Stream<Path> files = Files.list(Path.of(directory.toURI())))
        {
            final List<String> onDisk = files.map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".mirror")).sorted().toList();
            assertEquals(MirrorPresetRegistry.shippedNames().stream().sorted().toList(), onDisk,
                "every shipped preset file should be listed, and every listed one shipped");
        }
    }

    @Test
    void leavesAnEditedPresetAloneAndBringsADeletedOneBack() throws IOException
    {
        MirrorPresetRegistry.load(folder);
        final File nether = new File(folder, "nether.mirror");
        Files.writeString(nether.toPath(), "Name=nether\nBase=LIME\n", StandardCharsets.UTF_8);
        assertTrue(new File(folder, "ocean.mirror").delete(), "should be able to delete one");

        MirrorPresetRegistry.load(folder);

        assertEquals(DyeColor.LIME, MirrorPresetRegistry.byName("nether").base(),
            "an edited preset should not be overwritten by the shipped one");
        assertTrue(new File(folder, "ocean.mirror").isFile(), "a deleted one should come back");
    }

    @Test
    void skipsOneUnreadablePresetWithoutCostingTheOthers() throws IOException
    {
        MirrorPresetRegistry.load(folder);
        Files.writeString(new File(folder, "broken.mirror").toPath(),
            "Name=broken\nLayer=RED BORDER\n", StandardCharsets.UTF_8);

        final int loaded = MirrorPresetRegistry.load(folder);

        assertNull(MirrorPresetRegistry.byName("broken"), "no base colour, so no preset");
        assertEquals(MirrorPresetRegistry.shippedNames().size(), loaded,
            "every shipped preset should all still be there");
    }

    @Test
    void picksThePresetThatNamesTheBiome()
    {
        MirrorPresetRegistry.load(folder);

        assertEquals("nether", MirrorPresetRegistry.forBiome("NETHER_WASTES").name());
        assertEquals("ocean", MirrorPresetRegistry.forBiome("deep_cold_ocean").name());
        assertEquals("forest", MirrorPresetRegistry.forBiome("BAMBOO_JUNGLE").name());
        assertEquals("sparse_jungle", MirrorPresetRegistry.forBiome("SPARSE_JUNGLE").name(),
            "thin jungle is its own look, not the woodland one");
        assertEquals("pale_garden", MirrorPresetRegistry.forBiome("PALE_GARDEN").name());
    }

    /**
     * Every pattern a shipped preset names has to exist on every version this plugin supports.
     *
     * <p>{@code PatternType} renamed seven constants between 1.20 and 1.21 -- {@code
     * CIRCLE_MIDDLE} to {@code CIRCLE}, {@code STRIPE_SMALL} to {@code SMALL_STRIPES}, and the
     * four {@code _MIRROR} ones -- and added {@code FLOW} and {@code GUSTER}. A preset naming
     * one of those stamps correctly on the version it was written on and silently loses that
     * layer on the other half of the range, because the name resolves to null and the layer is
     * skipped. Nothing logs loudly enough for an operator to connect it to the banner being
     * wrong, so the place to catch it is here -- which is also what makes a look copied out of
     * a banner gallery dangerous to ship.
     */
    @Test
    void namesOnlyPatternsThatExistOnEverySupportedVersion()
    {
        MirrorPresetRegistry.load(folder);
        int layersChecked = 0;

        for (final MirrorPreset preset : MirrorPresetRegistry.all())
        {
            for (final MirrorPreset.Layer layer : preset.layers())
            {
                assertTrue(STABLE_PATTERNS.contains(layer.pattern()),
                    preset.name() + " names " + layer.pattern() + ", which is not a pattern"
                        + " every supported version has");
                layersChecked++;
            }
        }

        assertEquals(MirrorPresetRegistry.shippedNames().size(),
            MirrorPresetRegistry.all().size(), "every shipped preset should have been examined");
        assertTrue(layersChecked >= MirrorPresetRegistry.all().size(),
            "every preset has at least one layer, so this should have checked at least that"
                + " many -- a loop that checked nothing would pass otherwise");
    }

    /**
     * The looks that name no biome are reachable by name, and only that way.
     *
     * <p>They exist for what an operator wants said about a mirror when it is not where it
     * goes -- the middle of a network, one that only runs one way. {@code arcane.mirror} sorts
     * ahead of every place preset, so one of these answering for a biome would not answer for
     * one biome, it would answer for the first one asked about.
     */
    @Test
    void keepsTheStampOnlyLooksOutOfTheAutomaticChoice()
    {
        MirrorPresetRegistry.load(folder);

        for (final String look : List.of("plain", "hub", "warning", "private", "arcane"))
        {
            final MirrorPreset preset = MirrorPresetRegistry.byName(look);
            assertNotNull(preset, look + " should be loaded and stampable by name");
            assertEquals(Set.of(), preset.biomes(), look + " should name no biome");
        }

        assertEquals("overworld", MirrorPresetRegistry.forBiome("PLAINS").name(),
            "a place still gets its own look, not whichever preset sorts first");
    }

    @Test
    void stillGivesAPresetForABiomeNothingNames()
    {
        MirrorPresetRegistry.load(folder);

        assertEquals("overworld",
            MirrorPresetRegistry.forBiome("SOMEONES_DATAPACK_BIOME").name(),
            "an unknown biome should get the generic look, not nothing");
        assertEquals("overworld", MirrorPresetRegistry.forBiome(null).name());
        assertEquals("indoors", MirrorPresetRegistry.indoors().name());
    }

    @Test
    void fallsBackToAnyPresetWhenNoneIsCalledOverworld() throws IOException
    {
        // The file has to exist or the restore puts the shipped one back; renaming what is
        // inside it is how an operator makes the fallback name genuinely absent.
        Files.writeString(new File(folder, "overworld.mirror").toPath(),
            "Name=renamed\nBase=RED\n", StandardCharsets.UTF_8);

        MirrorPresetRegistry.load(folder);

        assertNull(MirrorPresetRegistry.byName("overworld"), "nothing answers to that now");
        assertNotNull(MirrorPresetRegistry.forBiome("SOMEONES_DATAPACK_BIOME"),
            "an unknown biome should still get some preset rather than null");
        assertNotNull(MirrorPresetRegistry.indoors(),
            "indoors is present, so it should still be found");
    }

    @Test
    void offersNothingRatherThanCrashingWhenTheFolderCannotBeMade() throws IOException
    {
        // A plain file where the directory should be. mkdirs cannot make one, which is the
        // same position the plugin is in when the folder is not writable.
        final File notADirectory = new File(folder, "shapes-mirror");
        Files.writeString(notADirectory.toPath(), "in the way", StandardCharsets.UTF_8);

        assertEquals(0, MirrorPresetRegistry.load(notADirectory));

        assertEquals(0, MirrorPresetRegistry.names().length);
        assertTrue(MirrorPresetRegistry.all().isEmpty());
        assertNull(MirrorPresetRegistry.forBiome("PLAINS"),
            "with nothing loaded there is no preset to fall back to");
        assertNull(MirrorPresetRegistry.indoors());
        assertNull(MirrorPresetRegistry.byName("nether"));
    }

    @Test
    void keepsTheOrderThePresetsLoadedIn()
    {
        MirrorPresetRegistry.load(folder);

        final List<String> names = Arrays.asList(MirrorPresetRegistry.names());
        assertEquals(names.size(), MirrorPresetRegistry.all().size());
        assertEquals(names, MirrorPresetRegistry.all().stream().map(MirrorPreset::name).toList());
    }

    /**
     * The order is by file name, and has to be the same everywhere.
     *
     * <p>{@code listFiles} promises nothing about order -- roughly alphabetical on NTFS, hash
     * order on ext4 -- so before this was sorted the class's own "order is kept" guarantee held
     * only by luck of the filesystem, and two presets claiming one biome could resolve one way
     * on a server and the other way on its backup.
     */
    @Test
    void loadsByFileNameWhateverOrderTheFilesystemHandsThemBack()
    {
        MirrorPresetRegistry.load(folder);

        final List<String> loaded = MirrorPresetRegistry.all().stream()
            .map(MirrorPreset::name).toList();
        final List<String> byFileName = MirrorPresetRegistry.shippedNames().stream()
            .sorted().map(name -> name.substring(0, name.length() - ".mirror".length())).toList();
        assertEquals(byFileName, loaded, "the shipped presets should load in file-name order");
    }

    /**
     * The first preset to claim a biome wins, and which one that is must not move.
     *
     * <p>Two files claiming PLAINS, named so that file order and creation order disagree.
     * Without the sort this passes or fails depending on what the filesystem feels like.
     */
    @Test
    void letsTheEarlierFileNameWinWhenTwoPresetsClaimOneBiome() throws IOException
    {
        Files.writeString(new File(folder, "zzz-claimant.mirror").toPath(),
            "Name=zzz\nBase=RED\nBiome=TEST_BIOME\n", StandardCharsets.UTF_8);
        Files.writeString(new File(folder, "aaa-claimant.mirror").toPath(),
            "Name=aaa\nBase=BLUE\nBiome=TEST_BIOME\n", StandardCharsets.UTF_8);

        MirrorPresetRegistry.load(folder);

        assertEquals("aaa", MirrorPresetRegistry.forBiome("TEST_BIOME").name(),
            "aaa-claimant.mirror sorts first, so it is the one that answers");
    }

    /**
     * A mirror into the Nether wears the Nether's look, not the indoor one.
     *
     * <p>Reported from real play: the banner "doesn't look right". The Nether is solid rock
     * with a ceiling on it, so the sampler reports enclosed for every mirror ever pointed
     * there -- and the rule written for libraries then replaced the Nether's look with the
     * indoor one, every time. Some places are enclosed by their nature and say so.
     */
    @Test
    void keepsTheNethersLookEvenThoughTheNetherIsAlwaysEnclosed()
    {
        MirrorPresetRegistry.load(folder);
        final MirrorView inTheNether = new MirrorView("NETHER_WASTES",
            List.of(DyeColor.RED, DyeColor.BLACK), true);

        assertEquals("nether", MirrorLook.seen(inTheNether).preset().name(),
            "an enclosed Nether reading should still be the Nether");
    }

    @Test
    void keepsACavesLookForTheSameReason()
    {
        MirrorPresetRegistry.load(folder);
        final MirrorView inACave = new MirrorView("DRIPSTONE_CAVES",
            List.of(DyeColor.GRAY), true);

        assertEquals("cavern", MirrorLook.seen(inACave).preset().name());
    }

    /**
     * A room in ordinary country still reads as a room, which is what the rule is for.
     *
     * <p>The library case. Plains is not enclosed by its nature, so finding it enclosed is
     * real information and the indoor look is the right answer.
     */
    @Test
    void stillReadsARoomInOrdinaryCountryAsIndoors()
    {
        MirrorPresetRegistry.load(folder);
        final MirrorView library = new MirrorView("PLAINS",
            List.of(DyeColor.BROWN, DyeColor.GRAY), true);

        assertEquals("indoors", MirrorLook.seen(library).preset().name());
    }

    @Test
    void readsOpenCountryAsItsBiomeWhetherOrNotThatBiomeIsSheltered()
    {
        MirrorPresetRegistry.load(folder);

        assertEquals("nether", MirrorLook.seen(
            new MirrorView("NETHER_WASTES", List.of(DyeColor.RED), false)).preset().name());
        assertEquals("overworld", MirrorLook.seen(
            new MirrorView("PLAINS", List.of(DyeColor.GREEN), false)).preset().name());
    }
}
