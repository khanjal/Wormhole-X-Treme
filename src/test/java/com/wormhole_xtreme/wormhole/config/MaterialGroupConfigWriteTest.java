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

/**
 * Tests writing discovered material groups into config.yml.
 *
 * <p>The risk here is structural: this writes into a nested YAML block, and getting it
 * wrong could produce a duplicate top-level key, which SnakeYAML resolves by discarding
 * one copy — taking the admin's own groups with it. Every test re-parses the result.
 */
public class MaterialGroupConfigWriteTest
{
    @TempDir
    File tempDir;

    @BeforeEach
    public void installPluginMock() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
    }

    private static List<MaterialGroup> diamond()
    {
        final List<MaterialGroup> groups = new ArrayList<MaterialGroup>();
        groups.add(new MaterialGroup("Diamond", Material.DIAMOND_BLOCK, Material.WATER,
            Material.GLASS, Material.GOLD_BLOCK, Material.OAK_WALL_SIGN));
        return groups;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(final File cfg) throws Exception
    {
        return (Map<String, Object>) new Yaml().load(
            new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void groupIsInsertedIntoAnExistingSectionWithoutLosingTheOnesAlreadyThere() throws Exception
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
    public void sectionIsCreatedWhenTheConfigHasNoneYet() throws Exception
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
    public void writingProducesExactlyOneTopLevelSectionKey() throws Exception
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
    public void nothingIsWrittenForAnEmptyGroupList() throws Exception
    {
        final File cfg = new File(tempDir, "config.yml");
        Files.write(cfg.toPath(), java.util.Arrays.asList("log-level: INFO"));
        final byte[] before = Files.readAllBytes(cfg.toPath());

        assertFalse(ConfigurationYAML.appendMaterialGroups(cfg, new ArrayList<MaterialGroup>()));

        assertArrayEquals(before, Files.readAllBytes(cfg.toPath()));
    }
}
