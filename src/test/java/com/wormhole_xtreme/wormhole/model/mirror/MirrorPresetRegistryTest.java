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
import java.util.stream.Stream;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Loading the presets folder.
 *
 * <p>Every test points the registry at a temporary directory. The no-argument {@code load()}
 * resolves the live plugin folder and writes ten files into it, which is how an earlier mirror
 * test ended up committing {@code data/mirror.yml} into the repository.
 */
class MirrorPresetRegistryTest
{
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
            "the shipped ten should all still be there");
    }

    @Test
    void picksThePresetThatNamesTheBiome()
    {
        MirrorPresetRegistry.load(folder);

        assertEquals("nether", MirrorPresetRegistry.forBiome("NETHER_WASTES").name());
        assertEquals("ocean", MirrorPresetRegistry.forBiome("deep_cold_ocean").name());
        assertEquals("forest", MirrorPresetRegistry.forBiome("BAMBOO_JUNGLE").name());
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
        assertEquals(byFileName, loaded, "the shipped ten should load in file-name order");
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
}
