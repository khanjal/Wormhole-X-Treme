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
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("a first run writes out every shipped preset and reads them back")
    void restoresTheShippedOnes()
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
    @DisplayName("the shipped list and the files in the jar name the same presets")
    void theShippedListMatchesTheResources() throws IOException, URISyntaxException
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
    @DisplayName("a preset an operator edited is left alone, and a deleted one comes back")
    void doesNotOverwriteWhatIsThere() throws IOException
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
    @DisplayName("one unreadable preset does not cost the others")
    void oneBadFileIsSkipped() throws IOException
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
    @DisplayName("a biome picks the preset that names it")
    void picksByBiome()
    {
        MirrorPresetRegistry.load(folder);

        assertEquals("nether", MirrorPresetRegistry.forBiome("NETHER_WASTES").name());
        assertEquals("ocean", MirrorPresetRegistry.forBiome("deep_cold_ocean").name());
        assertEquals("forest", MirrorPresetRegistry.forBiome("BAMBOO_JUNGLE").name());
    }

    @Test
    @DisplayName("a biome nothing names still gets a preset")
    void fallsBackRatherThanFailing()
    {
        MirrorPresetRegistry.load(folder);

        assertEquals("overworld",
            MirrorPresetRegistry.forBiome("SOMEONES_DATAPACK_BIOME").name(),
            "an unknown biome should get the generic look, not nothing");
        assertEquals("overworld", MirrorPresetRegistry.forBiome(null).name());
        assertEquals("indoors", MirrorPresetRegistry.indoors().name());
    }

    @Test
    @DisplayName("with no preset called overworld, any preset beats none")
    void fallsBackAgainWhenTheFallbackIsMissing() throws IOException
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
    @DisplayName("a folder that cannot be made means nothing offered, rather than a crash")
    void unusableFolder() throws IOException
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
    @DisplayName("presets keep the order they loaded in")
    void keepsLoadOrder()
    {
        MirrorPresetRegistry.load(folder);

        final List<String> names = Arrays.asList(MirrorPresetRegistry.names());
        assertEquals(names.size(), MirrorPresetRegistry.all().size());
        assertEquals(names, MirrorPresetRegistry.all().stream().map(MirrorPreset::name).toList());
    }
}
