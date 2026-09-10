package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * The one write path gates, rings and beam destinations all now share.
 *
 * <p>It was three copies of the same code before -- the same {@code DumperOptions}, the same
 * temp file, the same atomic move. Collapsing them means these properties are worth pinning
 * once rather than trusting three times, because a regression here now loses gates *and* rings
 * *and* beam destinations together.
 */
class YamlStoreTest
{
    private static Map<String, Object> sample()
    {
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("World", "overworld");
        root.put("X", 1.5);
        return root;
    }

    /** What is written comes back as what was put in. */
    @Test
    void whatGoesInComesBackOut(@TempDir final File dir) throws Exception
    {
        final File target = new File(dir, "rings.yml");

        YamlStore.write(target, sample());

        final Map<String, Object> back = new Yaml().load(
            Files.newBufferedReader(target.toPath(), StandardCharsets.UTF_8));
        assertEquals("overworld", back.get("World"));
        assertEquals(1.5, back.get("X"));
    }

    /**
     * A name with an accent survives the round trip.
     *
     * <p>This is the `Caf?` bug, which is why the charset is named explicitly rather than left
     * to the platform default. A gate called `Café` was written in one charset and read back in
     * another, and came back with its name mangled -- on a server where the gate's name is its
     * identity, that is the gate lost.
     */
    @Test
    void aNameWithAnAccentSurvivesTheRoundTrip(@TempDir final File dir) throws Exception
    {
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("Name", "Café");
        final File target = new File(dir, "gate.yml");

        YamlStore.write(target, root);

        final Map<String, Object> back = new Yaml().load(
            Files.newBufferedReader(target.toPath(), StandardCharsets.UTF_8));
        assertEquals("Café", back.get("Name"),
            "written in the platform charset and read back as UTF-8, this comes back as Caf?");
    }

    /**
     * The temp file is not left lying beside the target.
     *
     * <p>The write goes to {@code <target>.tmp} and is moved on. A leftover would sit in the
     * gates directory being listed as a gate file on the next load.
     */
    @Test
    void noTempFileIsLeftBehind(@TempDir final File dir) throws Exception
    {
        final File target = new File(dir, "beam.yml");

        YamlStore.write(target, sample());

        assertFalse(new File(dir, "beam.yml.tmp").exists(), "the temp file was moved, not copied");
        assertEquals(1, dir.listFiles().length, "only the target should be in the directory");
    }

    /** Writing again replaces the file rather than failing or appending. */
    @Test
    void writingAgainReplacesTheFile(@TempDir final File dir) throws Exception
    {
        final File target = new File(dir, "rings.yml");
        YamlStore.write(target, sample());

        final Map<String, Object> second = new LinkedHashMap<>();
        second.put("World", "nether");
        YamlStore.write(target, second);

        final Map<String, Object> back = new Yaml().load(
            Files.newBufferedReader(target.toPath(), StandardCharsets.UTF_8));
        assertEquals("nether", back.get("World"));
        assertFalse(back.containsKey("X"), "the previous contents should be gone, not merged");
    }

    /**
     * Block style, not inline braces.
     *
     * <p>These files are meant to be opened and hand-edited by server owners.
     * {@code {World: overworld, X: 1.5}} on one line is valid YAML and hostile to that.
     */
    @Test
    void theFileIsBlockStyleSoItCanBeHandEdited(@TempDir final File dir) throws Exception
    {
        final File target = new File(dir, "rings.yml");

        YamlStore.write(target, sample());

        final String text = Files.readString(target.toPath(), StandardCharsets.UTF_8);
        assertTrue(text.contains("World: overworld"), "expected a key per line, got: " + text);
        assertFalse(text.contains("{"), "inline flow style is not hand-editable: " + text);
    }
}
