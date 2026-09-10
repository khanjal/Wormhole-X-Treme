package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

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
     * config.yml is read from the folder the server names, not the working directory.
     *
     * <p>It used to be built as {@code plugins/<name>/} regardless of what the server said,
     * which on a stock install is the same folder and everywhere else is not. The failure was
     * silent in the worst way: a server whose plugin folder had moved read no config at all
     * and ran on defaults, with the admin's real file sitting unopened in another tree and
     * nothing in the log to suggest it existed.
     */
    @Test
    void configIsReadFromTheServersPluginFolder(@TempDir final File dataFolder) throws Exception
    {
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        assertEquals(new File(dataFolder, "config.yml"),
            ConfigurationYAML.getConfigFile("WormholeXTreme"),
            "reading config from the working directory means an admin's settings are ignored "
            + "with no error, which looks exactly like the settings not working");
    }
}
