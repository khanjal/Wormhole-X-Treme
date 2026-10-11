package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.bukkit.Material;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.logic.StargateShapeFactory;

/**
 * Reading config.yml at startup.
 *
 * <p>Two things here are worth holding. Settings are looked up under their kebab-case name
 * and then under the enum name, so a file written by an older version still loads. And a key
 * the file does not mention is defaulted in memory <em>and</em> appended to the file, so the
 * admin can see it exists and change it.
 *
 * <p>Nothing covered either: the method built its own path from {@code plugins/<name>/}, so
 * running it in a test would have written into the working directory.
 */
class ConfigLoadTest
{
    @TempDir
    File directory;

    /** Kept so a test can stub it; installing a second one would outlive the teardown. */
    private WormholeXTreme plugin;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        // A loaded config seeds the example groups, whose Standard has chevrons; this class's other tests expect none.
        MaterialGroupRegistry.load(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private void writeConfig(final String yaml) throws Exception
    {
        Files.write(new File(directory, "config.yml").toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> configLines() throws Exception
    {
        return Files.readAllLines(new File(directory, "config.yml").toPath(), StandardCharsets.UTF_8);
    }

    /** With no file at all, one is written and the defaults are in memory. */
    @Test
    void anEmptyDirectoryGetsADefaultConfig()
    {
        ConfigurationYAML.loadConfiguration(directory);

        assertTrue(new File(directory, "config.yml").isFile(), "a config file is written on a first run");
        assertFalse(ConfigManager.getConfigurations().isEmpty(), "and the defaults are loaded");
    }

    /**
     * A fresh install loads the shipped material groups on its first boot, so the shipped shapes raise no warning.
     *
     * <p>The no-config.yml path wrote the flat settings and never read the file back, so
     * gate-material-groups was neither seeded nor loaded until the second boot. With no group
     * claiming OBSIDIAN, discovery saw the stock shapes disagree on their iris (Horizontal pins
     * GLASS) and told the operator to fix config they had never written.
     */
    @Test
    void aFreshInstallClaimsObsidianBeforeTheShippedShapesAreRead() throws Exception
    {
        // Not the builtin fallback the teardown leaves behind: that claims OBSIDIAN, and would pass this unread.
        MaterialGroupRegistry.load(Map.of("Lapis", Map.of("structure", "LAPIS_BLOCK")));

        Configuration.loadConfiguration(directory);

        assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN),
            "the shipped Standard group is loaded on the first boot, not the second");
        assertTrue(configLines().contains("gate-material-groups:"), "and written to the new config.yml");

        final List<StargateShape> shipped = new ArrayList<>();
        try (Stream<Path> files = Files.list(Paths.get("src/main/resources/shapes/gate")))
        {
            for (final Path file : files.filter(f -> f.toString().endsWith(".shape")).toList())
            {
                shipped.add(StargateShapeFactory.createShapeFromFile(
                    Files.readAllLines(file, StandardCharsets.UTF_8).toArray(new String[0])));
            }
        }
        assertEquals(9, shipped.size(), "every shipped gate shape was read");

        MaterialGroupRegistry.discoverUndeclaredGroups(shipped);

        verify(plugin, never()).prettyLog(eq(Level.WARNING), contains("disagree"));
    }

    /** A value in the file wins over the default. */
    @Test
    void aValueInTheFileIsUsed() throws Exception
    {
        writeConfig("timeout-shutdown: 42\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertEquals(42, ConfigManager.getTimeoutShutdown());
    }

    /**
     * A key written the old way still loads.
     *
     * <p>Settings are looked for under their kebab-case name first and their enum name
     * second, so a config.yml written by a version that used TIMEOUT_SHUTDOWN keeps working
     * rather than silently reverting to the default.
     */
    @Test
    void aKeyUnderItsEnumNameStillLoads() throws Exception
    {
        writeConfig("TIMEOUT_SHUTDOWN: 37\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertEquals(37, ConfigManager.getTimeoutShutdown(),
            "the older spelling of the key is still understood");
    }

    /**
     * A setting written under a name it no longer has still loads, and is written back under the
     * name it has now.
     *
     * <p>{@code mirror-proximity-radius} became {@code mirror-proximity-distance}. A server that
     * had set it would otherwise revert to the default without a word, and keep an orphan line.
     */
    @Test
    void aKeyUnderItsOldNameStillLoadsAndIsRenamedInTheFile() throws Exception
    {
        writeConfig("mirror-proximity-radius: 11\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertEquals(11, ConfigManager.getMirrorProximityDistance(), "the value under the old name");
        final List<String> lines = configLines();
        assertTrue(lines.contains("mirror-proximity-distance: 11"), "written back under the new name: " + lines);
        assertFalse(lines.stream().anyMatch(l -> l.startsWith("mirror-proximity-radius:")),
            "and the old line does not linger: " + lines);
    }

    /**
     * Only a top-level setting is renamed; the same key nested under a section, or in a comment,
     * is left as the admin wrote it.
     */
    @Test
    void onlyATopLevelKeyIsRenamed()
    {
        final List<String> out = ConfigurationYAML.renameSettingLines(List.of(
            "mirror-proximity-radius: 11",
            "some-section:",
            "  mirror-proximity-radius: 4",
            "# mirror-proximity-radius: 9"));

        assertEquals(List.of(
            "mirror-proximity-distance: 11",
            "some-section:",
            "  mirror-proximity-radius: 4",
            "# mirror-proximity-radius: 9"), out);
    }

    /**
     * A key the file does not mention is added to it.
     *
     * <p>Defaulting it in memory alone would leave the admin with no way to discover the
     * setting exists, so the missing keys are appended to the file they were missing from.
     */
    @Test
    void aMissingKeyIsAppendedToTheFile() throws Exception
    {
        writeConfig("timeout-shutdown: 42\n");

        ConfigurationYAML.loadConfiguration(directory);

        final List<String> lines = configLines();
        boolean mentionsActivate = false;
        for (final String line : lines)
        {
            if (line.startsWith("timeout-activate:"))
            {
                mentionsActivate = true;
            }
        }
        assertTrue(mentionsActivate,
            "a key that was defaulted in memory should also be written out: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("timeout-shutdown:")),
            "and the key that was already there survives");
    }

    /**
     * A file that is not a mapping leaves the defaults alone rather than failing, and is not
     * written to.
     *
     * <p>The second half is the half with teeth, and it was added after the first half alone
     * failed to catch a real regression. Reading such a file as an empty mapping produces the
     * same in-memory answer -- everything defaults, because everything is missing -- so the
     * default assertion below passed while the loader had started appending all forty missing
     * keys to the end of a file that is not a mapping. The operator's typo would have come
     * back as a file the loader could no longer parse at all.
     *
     * <p>This is why one {@code instanceof} guard survived the sweep that removed the others.
     * Every other reader only iterates what it was handed, so an empty mapping and a missing
     * one mean the same thing to it; this one acts on what is <em>absent</em>.
     */
    @Test
    void aFileThatIsNotAMappingDoesNotStopStartup() throws Exception
    {
        writeConfig("just a sentence\n");

        ConfigurationYAML.loadConfiguration(directory);

        // Nothing was loaded from it, so the getters fall back to their built-in answers.
        assertEquals(30, ConfigManager.getTimeoutActivate(),
            "an unreadable file leaves the built-in default in place");
        assertEquals(List.of("just a sentence"), configLines(),
            "and the file itself is left exactly as it was, not appended to");
    }

    /**
     * A config.yml whose groups could not be read still leaves a group claiming OBSIDIAN.
     *
     * <p>Otherwise the registry stays empty and the shipped shapes, which disagree on their
     * iris, warn the operator to add a group by hand, on top of the error that actually matters.
     */
    @Test
    void aFileThatIsNotAMappingStillLeavesTheBuiltinGroup() throws Exception
    {
        MaterialGroupRegistry.load(Map.of("Lapis", Map.of("structure", "LAPIS_BLOCK")));
        writeConfig("just a sentence\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN),
            "the builtin Standard group stands in for the groups the file could not give");
    }

    /** The same, when the file cannot be opened at all: here a directory where the file should be. */
    @Test
    void aFileThatCannotBeReadStillLeavesTheBuiltinGroup()
    {
        MaterialGroupRegistry.load(Map.of("Lapis", Map.of("structure", "LAPIS_BLOCK")));
        assertTrue(new File(directory, "config.yml").mkdir(), "set up an unreadable config.yml");

        ConfigurationYAML.loadConfiguration(directory);

        assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN),
            "the builtin Standard group stands in for the groups the file could not give");
    }

    /**
     * A write that fails after the groups were read does not throw them away for the builtin one.
     *
     * <p>Renaming an old key rewrites the file after the groups have loaded, inside the same
     * catch as the read; a read-only file makes that rewrite fail.
     */
    @Test
    void aFailedRewriteKeepsTheGroupsTheFileGave() throws Exception
    {
        writeConfig("mirror-proximity-radius: 11\ngate-material-groups:\n  Lapis:\n    structure: LAPIS_BLOCK\n");
        final File cfg = new File(directory, "config.yml");
        assertTrue(cfg.setWritable(false), "set up a config.yml the rename cannot rewrite");
        assumeFalse(cfg.canWrite(), "running as root, which writes a read-only file anyway");
        try
        {
            ConfigurationYAML.loadConfiguration(directory);
        }
        finally
        {
            cfg.setWritable(true);
        }

        assertEquals("Lapis", MaterialGroupRegistry.getDefaultGroup().getName(),
            "the file's own group, not the builtin Standard that stands in for an unread one");
    }

    /**
     * config.yml is read from the folder the server names, not the working directory.
     *
     * <p>It used to be built as {@code plugins/<name>/} regardless of what the server said,
     * which on a stock install is the same folder and everywhere else is not. The failure was
     * silent in the worst way: a server whose plugin folder had moved read no config at all
     * and ran on defaults, with the admin's real file sitting unopened in another tree and
     * nothing in the log to suggest it existed.
     */
    @Test
    void configIsReadFromTheServersPluginFolder(@TempDir final File dataFolder)
    {
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        assertEquals(new File(dataFolder, "config.yml"),
            ConfigurationYAML.getConfigFile("WormholeXTreme"),
            "reading config from the working directory means an admin's settings are ignored "
            + "with no error, which looks exactly like the settings not working");
    }

    private static List<String> groupNames()
    {
        return MaterialGroupRegistry.getGroups().stream()
            .map(MaterialGroup::getName).toList();
    }

    /**
     * A config.yml with no material groups is given the example ones, once.
     *
     * <p>The groups the guide describes lived only in the example file inside the jar, which
     * nothing copied out. A server's own config.yml had none, so every server had Standard alone
     * and a lapis frame was never an Atlantis gate.
     */
    @Test
    void aConfigWithNoMaterialGroupsIsGivenTheExampleOnesOnce() throws Exception
    {
        writeConfig("timeout-shutdown: 42\n");

        ConfigurationYAML.loadConfiguration(directory);
        ConfigurationYAML.loadConfiguration(directory);

        assertEquals(List.of("Standard", "Atlantis", "Universe", "MilkyWay"), groupNames());
        assertEquals(1, configLines().stream().filter(l -> l.startsWith("gate-material-groups:")).count(),
            "written once, and read back rather than added again");
        assertTrue(configLines().contains("  Atlantis:"));
        assertEquals(42, ConfigManager.getTimeoutShutdown(), "the rest of the file still reads");
    }

    /** A first run gets them too. */
    @Test
    void aFirstRunHasTheExampleMaterialGroups()
    {
        ConfigurationYAML.loadConfiguration(directory);

        assertTrue(groupNames().containsAll(List.of("Standard", "Atlantis", "Universe", "MilkyWay")), "got " + groupNames());
    }

    /** A config.yml that lists its own groups keeps exactly those. */
    @Test
    void aConfigWithItsOwnMaterialGroupsKeepsThem() throws Exception
    {
        writeConfig("gate-material-groups:\n  Stone:\n    structure: STONE_BRICKS\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertEquals(List.of("Stone"), groupNames());
        assertFalse(configLines().contains("  Atlantis:"), "nothing added to a section that is already there");
    }

    /**
     * A config.yml that still holds help-support-disable loads as before, and keeps the line.
     *
     * <p>The setting was retired in 1.9.0: nothing had read it since the Help plugin it turned off
     * stopped existing. Settings are loaded by walking the defaults, not the file, so the line is
     * ignored rather than refused, and an operator's file is not rewritten under them.
     */
    @Test
    void aConfigStillHoldingTheRetiredHelpSettingLoads() throws Exception
    {
        writeConfig("help-support-disable: true\ntimeout-shutdown: 41\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertEquals(41, ConfigManager.getTimeoutShutdown(), "the rest of the file is read");
        assertTrue(configLines().contains("help-support-disable: true"), "and the retired line is left where it was");
    }

    /**
     * A number left under a setting that is now a switch reads as one, and is written back as one (#583).
     *
     * <p>An early build of the gate-view tunnel shipped {@code gate-view-funnel} as a length and
     * appended {@code gate-view-funnel: 8} to the file; the next made it true or false. The 8 was
     * stored as found, so every read of the switch threw ClassCastException, which took down every
     * gate's view each sweep, and {@code /wormhole config gate-view-funnel true} was refused as
     * "a number".
     */
    @Test
    void aNumberUnderASwitchIsReadAsOneAndWrittenBack() throws Exception
    {
        writeConfig("gate-view-funnel: 8\ntimeout-shutdown: 41\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertTrue(ConfigManager.isGateViewFunnel(), "8 was a length, so the tunnel was on");
        assertTrue(ConfigManager.getConfigurations().get(ConfigManager.ConfigKeys.GATE_VIEW_FUNNEL).getValue() instanceof Boolean,
            "stored as a switch, so /wormhole config takes true or false for it");
        assertTrue(configLines().contains("gate-view-funnel: true"), "and the file says what is read: " + configLines());
        assertEquals(41, ConfigManager.getTimeoutShutdown(), "the rest of the file is untouched");
        verify(plugin).prettyLog(eq(Level.WARNING), contains("gate-view-funnel"));
    }

    /** A 0 left there reads as off. */
    @Test
    void aZeroUnderASwitchIsOff() throws Exception
    {
        writeConfig("gate-view-funnel: 0\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertFalse(ConfigManager.isGateViewFunnel());
        assertTrue(configLines().contains("gate-view-funnel: false"), "written back as a switch: " + configLines());
    }

    /**
     * A value of the wrong kind is read as the kind its setting is, where it can be, so no getter
     * meets a type it casts wrongly; one that cannot be read is the default, said once, and left in
     * the file for the admin to put right.
     */
    @Test
    void aValueOfTheWrongKindIsReadAsTheSettingsKindOrItsDefault() throws Exception
    {
        writeConfig("gate-view-depth: 48.0\ntimeout-shutdown: \"41\"\ngate-ripple: \"true\"\ngate-view-full-depth: lots\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertEquals(48, ConfigManager.getGateViewDepth(), "a whole number written with a point");
        assertEquals(41, ConfigManager.getTimeoutShutdown(), "a number in quotes");
        assertTrue(ConfigManager.isGateRipple(), "a switch in quotes");
        assertEquals(160, ConfigManager.getGateViewFullDepth(), "not a number at all: the default");
        assertTrue(configLines().contains("gate-view-full-depth: lots"), "and left as written: " + configLines());
        verify(plugin).prettyLog(eq(Level.WARNING), contains("gate-view-full-depth"));
    }

    /**
     * A switch written back keeps the comment after it, and only the line it was read from changes:
     * the enum spelling of the same key, which was not read, is left as written.
     */
    @Test
    void aSwitchWrittenBackKeepsItsCommentAndOnlyTheLineItWasReadFrom() throws Exception
    {
        writeConfig("gate-view-funnel: 8 \t# left from an early build\nGATE_VIEW_FUNNEL: 0\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertTrue(ConfigManager.isGateViewFunnel(), "the kebab spelling is the one read");
        assertTrue(configLines().contains("gate-view-funnel: true \t# left from an early build"), "comment kept: " + configLines());
        assertTrue(configLines().contains("GATE_VIEW_FUNNEL: 0"), "the spelling not read is left alone: " + configLines());
    }

    /**
     * A text setting given a number or a bare off is read as text, so no getter that casts to text
     * throws: a number for a sign colour, and off for gate-view, which YAML reads as false and is then
     * no level, so the horizon, as off meant. A dial spin written false still means no spin.
     */
    @Test
    void aTextSettingGivenANumberOrABareOffIsReadAsText() throws Exception
    {
        writeConfig("sign-color-gate-name: 1\ngate-view: off\ngate-dial-spin: false\nring-default-style: 5\n");

        ConfigurationYAML.loadConfiguration(directory);

        assertEquals("1", ConfigManager.getSignColorGateName());
        assertEquals("horizon", ConfigManager.getGateView());
        assertEquals("false", ConfigManager.getConfigurations().get(ConfigManager.ConfigKeys.GATE_VIEW).getValue());
        assertEquals(DialSpinPattern.NONE, ConfigManager.getGateDialSpinPattern());
        assertEquals("5", ConfigManager.getConfigurations().get(ConfigManager.ConfigKeys.RING_DEFAULT_STYLE).getValue());

        writeConfig("gate-dial-spin: true\ngate-view: open\n");
        ConfigurationYAML.loadConfiguration(directory);

        assertEquals(DialSpinPattern.TOP, ConfigManager.getGateDialSpinPattern(), "true was the old spinning dial");
        assertEquals("open", ConfigManager.getGateView(), "a level as written is read as it was");
    }

    /**
     * A setting given the default, because the file says nothing or nothing readable, holds its own
     * copy: {@code /wormhole config} sets the value on what is stored, and on the shared default it
     * changed the default for every later load.
     */
    @Test
    void aDefaultedSettingIsACopyNotTheSharedDefault() throws Exception
    {
        writeConfig("gate-view-full-depth: lots\n");

        ConfigurationYAML.loadConfiguration(directory);

        for (final ConfigManager.ConfigKeys key : new ConfigManager.ConfigKeys[] { ConfigManager.ConfigKeys.GATE_VIEW_FULL_DEPTH,
            ConfigManager.ConfigKeys.GATE_RIPPLE })
        {
            final Setting shipped = Arrays.stream(DefaultSettings.config).filter(s -> s.getName() == key).findFirst()
                .orElseThrow();
            final Setting stored = ConfigManager.getConfigurations().get(key);
            assertNotSame(shipped, stored, key + ": unreadable or missing, its own copy");
            assertEquals(shipped.getValue(), stored.getValue(), key + ": holding the default");
            final Object before = shipped.getValue();
            stored.setValue("changed");
            assertEquals(before, shipped.getValue(), key + ": a change to it leaves the default alone");
        }
    }
}
