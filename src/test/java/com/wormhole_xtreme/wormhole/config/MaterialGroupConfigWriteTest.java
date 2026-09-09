package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;

/**
 * Tests writing discovered material groups into config.yml.
 *
 * <p>The risk here is structural: this writes into a nested YAML block, and getting it
 * wrong could produce a duplicate top-level key, which SnakeYAML resolves by discarding
 * one copy — taking the admin's own groups with it. Every test re-parses the result.
 */
class MaterialGroupConfigWriteTest
{
    @TempDir
    File tempDir;

    @BeforeEach
    void installPluginMock() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
    }

    private static List<MaterialGroup> diamond()
    {
        final List<MaterialGroup> groups = new ArrayList<>();
        groups.add(new MaterialGroup("Diamond", Material.DIAMOND_BLOCK, Material.WATER,
            Material.GLASS, Material.GOLD_BLOCK, Material.OAK_WALL_SIGN));
        return groups;
    }

    private static Map<String, Object> parse(final File cfg) throws Exception
    {
        return YamlMaps.asMap(new Yaml().load(
            new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8)));
    }

    @Test
    void groupIsInsertedIntoAnExistingSectionWithoutLosingTheOnesAlreadyThere() throws Exception
    {
        final File cfg = new File(tempDir, "config.yml");
        Files.write(cfg.toPath(), java.util.Arrays.asList(
            "log-level: INFO",
            "",
            "# Material groups",
            "gate-material-groups:",
            "  Standard:",
            "    structure: OBSIDIAN",
            "    iris: STONE",
            "    light: GLOWSTONE",
            "",
            "# Some later key",
            "log-level: INFO"));

        assertTrue(ConfigurationYAML.appendMaterialGroups(cfg, diamond()));

        final Map<String, Object> parsed = parse(cfg);
        final Map<String, Object> groups = (Map<String, Object>) parsed.get("gate-material-groups");
        assertNotNull(groups, "the section must still parse as a mapping");
        assertTrue(groups.containsKey("Standard"), "existing group must survive");
        assertTrue(groups.containsKey("Diamond"), "new group must be added");
        assertEquals("DIAMOND_BLOCK", ((Map<String, Object>) groups.get("Diamond")).get("structure"));
        assertEquals("GOLD_BLOCK", ((Map<String, Object>) groups.get("Diamond")).get("light"));
        // Keys on either side of the section must be untouched.
        assertEquals("INFO", parsed.get("log-level"));
        assertEquals("INFO", parsed.get("log-level"));
    }

    @Test
    void sectionIsCreatedWhenTheConfigHasNoneYet() throws Exception
    {
        final File cfg = new File(tempDir, "config.yml");
        Files.write(cfg.toPath(), java.util.Arrays.asList("log-level: INFO"));

        assertTrue(ConfigurationYAML.appendMaterialGroups(cfg, diamond()));

        final Map<String, Object> parsed = parse(cfg);
        final Map<String, Object> groups = (Map<String, Object>) parsed.get("gate-material-groups");
        assertNotNull(groups);
        assertTrue(groups.containsKey("Diamond"));
        assertEquals("INFO", parsed.get("log-level"));
    }

    @Test
    void writingProducesExactlyOneTopLevelSectionKey() throws Exception
    {
        final File cfg = new File(tempDir, "config.yml");
        Files.write(cfg.toPath(), java.util.Arrays.asList(
            "gate-material-groups:",
            "  Standard:",
            "    structure: OBSIDIAN"));

        ConfigurationYAML.appendMaterialGroups(cfg, diamond());

        int occurrences = 0;
        for (final String line : Files.readAllLines(cfg.toPath()))
        {
            if (line.startsWith("gate-material-groups:"))
            {
                occurrences++;
            }
        }
        assertEquals(1, occurrences, "a second top-level key would silently discard one copy");
    }

    @Test
    void nothingIsWrittenForAnEmptyGroupList() throws Exception
    {
        final File cfg = new File(tempDir, "config.yml");
        Files.write(cfg.toPath(), java.util.Arrays.asList("log-level: INFO"));
        final byte[] before = Files.readAllBytes(cfg.toPath());

        assertFalse(ConfigurationYAML.appendMaterialGroups(cfg, new ArrayList<>()));

        assertArrayEquals(before, Files.readAllBytes(cfg.toPath()));
    }

    /**
     * A new group is written among the group definitions, not after the next key's comment.
     *
     * <p>Both placements parse, which is why the other tests here do not notice the
     * difference -- but a group written below "# Some later key" reads as belonging to that
     * key instead, and the next person to edit the file by hand has been misled.
     *
     * <p>Two things put it in the right place: the section is taken to end at the first line
     * that is neither blank, nor a comment, nor indented, and then the insertion point steps
     * back over any trailing blanks and comments so it lands with the definitions rather than
     * after the header that follows them.
     */
    @Test
    void aNewGroupIsWrittenAmongTheDefinitionsNotAfterTheNextKeysComment() throws Exception
    {
        final File cfg = new File(tempDir, "config.yml");
        Files.write(cfg.toPath(), java.util.Arrays.asList(
            "gate-material-groups:",
            "  Standard:",
            "    structure: OBSIDIAN",
            "    iris: STONE",
            "",
            "# Some later key",
            "log-level: INFO"));

        assertTrue(ConfigurationYAML.appendMaterialGroups(cfg, diamond()));

        final List<String> lines = Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8);
        final int standardAt = indexOfLineStartingWith(lines, "  Standard:");
        final int diamondAt = indexOfLineStartingWith(lines, "  Diamond:");
        final int commentAt = indexOfLineStartingWith(lines, "# Some later key");

        assertTrue(standardAt >= 0 && diamondAt >= 0 && commentAt >= 0,
            "all three landmarks should still be in the file: " + lines);
        assertTrue(standardAt < diamondAt, "a new group is added after the ones already there");
        assertTrue(diamondAt < commentAt,
            "and before the comment that introduces the next key, not after it");
    }

    /** @return the index of the first line starting with the given text, or -1 */
    private static int indexOfLineStartingWith(final List<String> lines, final String prefix)
    {
        for (int i = 0; i < lines.size(); i++)
        {
            if (lines.get(i).startsWith(prefix))
            {
                return i;
            }
        }
        return -1;
    }
}
