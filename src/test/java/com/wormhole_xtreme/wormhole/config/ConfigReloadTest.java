package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * {@code /wormhole config reload} takes what an admin edited into config.yml, and nothing it should not.
 *
 * <p>Without it a hand-edited or deployed file meant a restart, and Bukkit's own reload is unsafe.
 * The cases here are the ways a reload could do harm instead: a broken file wiping settings, a bad
 * value replacing a good one, or a setting the file leaves out going back to its default.
 */
class ConfigReloadTest
{
    @TempDir
    File directory;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private ConfigReload.Report reload(final String yaml) throws Exception
    {
        final File cfg = new File(directory, "config.yml");
        Files.writeString(cfg.toPath(), yaml, StandardCharsets.UTF_8);
        return ConfigReload.reload(cfg);
    }

    private static Object valueOf(final ConfigKeys key)
    {
        return ConfigManager.getConfigurations().get(key).getValue();
    }

    /**
     * A changed value is taken and applied through the same path {@code /wormhole config} uses.
     *
     * <p>The economy is attached once, at enable; a reload that only stored the new value would
     * report it changed while costs stayed off until the next restart.
     */
    @Test
    void aChangedValueIsTakenAndAppliedAtOnce() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            final ConfigReload.Report report = reload("economy-enabled: true\n");

            economy.verify(EconomySupport::enableEconomy);
            assertEquals(Boolean.TRUE, valueOf(ConfigKeys.ECONOMY_ENABLED));
            assertEquals(List.of(new ConfigReload.Change(ConfigKeys.ECONOMY_ENABLED, false, true, null)),
                report.changes());
            assertNull(report.unreadable());
        }
    }

    /** A change that cannot be applied now still counts as taken, and says why it waits. */
    @Test
    void aChangeThatCannotBeAppliedNowSaysWhy() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::enableEconomy).thenThrow(new IllegalStateException("no Vault"));

            final ConfigReload.Report report = reload("economy-enabled: true\n");

            assertEquals(1, report.changes().size(), report.changes().toString());
            assertTrue(String.valueOf(report.changes().get(0).unapplied()).contains("no Vault"),
                "the failure is named: " + report.changes());
        }
    }

    /**
     * A value the command would refuse keeps the old one and is reported with the refusal.
     *
     * <p>An unknown style stored as text would deploy every ring as the default and be reported as
     * if it were a style, the bug {@link ParsedSetting} exists to stop.
     */
    @Test
    void aRefusedValueKeepsTheOldOneAndIsReported() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.RING_DEFAULT_STYLE, "SEQUENTIAL");
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);

        final ConfigReload.Report report = reload("ring-default-style: banana\ngate-sounds-enabled: loud\n");

        assertEquals("SEQUENTIAL", valueOf(ConfigKeys.RING_DEFAULT_STYLE));
        assertEquals(Boolean.TRUE, valueOf(ConfigKeys.GATE_SOUNDS_ENABLED));
        assertEquals(List.of(), report.changes());
        assertEquals(2, report.rejections().size(), report.rejections().toString());
        final ConfigReload.Rejection sounds = report.rejections().stream()
            .filter(r -> r.key() == ConfigKeys.GATE_SOUNDS_ENABLED).findFirst().orElseThrow();
        assertEquals("GATE_SOUNDS_ENABLED is true or false, not \"loud\".", sounds.refusal());
        assertEquals(Boolean.TRUE, sounds.kept());
    }

    /**
     * A file that is not valid YAML changes nothing, not even the settings it spells correctly, and
     * says where it broke.
     */
    @Test
    void aFileThatIsNotValidYamlChangesNothingAndSaysWhy() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);

        final ConfigReload.Report report = reload("economy-enabled: true\nmirror-names: [unclosed\n");

        assertNotNull(report.unreadable(), "a broken file is said to be broken");
        assertTrue(report.unreadable().contains("not valid YAML") && report.unreadable().contains("line"),
            report.unreadable());
        assertEquals(Boolean.FALSE, valueOf(ConfigKeys.ECONOMY_ENABLED), "the line above the break is not taken");
        assertEquals(List.of(), report.changes());
    }

    /** A file that parses but is not a mapping changes nothing and says so. */
    @Test
    void aFileThatIsNotAMappingChangesNothingAndSaysWhy() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);

        final ConfigReload.Report report = reload("- economy-enabled\n- true\n");

        assertEquals("config.yml is not a list of settings", report.unreadable());
        assertEquals(Boolean.FALSE, valueOf(ConfigKeys.ECONOMY_ENABLED));
    }

    /** A missing file is said to be unreadable rather than read as every setting absent. */
    @Test
    void aMissingFileChangesNothingAndSaysWhy()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);

        final ConfigReload.Report report = ConfigReload.reload(new File(directory, "config.yml"));

        assertTrue(String.valueOf(report.unreadable()).contains("could not be read"), report.unreadable());
    }

    /**
     * A file saying what is already in memory reports no change, even where it spells a value
     * differently from how it is stored.
     *
     * <p>The log level is stored upper case; a file holding {@code info} would otherwise report a
     * change on every reload.
     */
    @Test
    void aFileSayingWhatIsAlreadySetReportsNoChange() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.LOG_LEVEL, "INFO");
        ConfigTestSupport.set(ConfigKeys.RING_REACH, 3);
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);

        final ConfigReload.Report report = reload("log-level: info\nring-reach: 3\neconomy-enabled: false\n");

        assertNull(report.unreadable());
        assertEquals(List.of(), report.changes());
        assertEquals(List.of(), report.rejections());
    }

    /**
     * A setting the file leaves out keeps the value it has, rather than going back to its default.
     *
     * <p>Startup defaults a missing key because there is nothing else to use; a reload has the
     * running value, which may have been set in-game.
     */
    @Test
    void aSettingTheFileLeavesOutKeepsItsValue() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, false);
        ConfigTestSupport.set(ConfigKeys.RING_REACH, 3);

        final ConfigReload.Report report = reload("ring-reach: 3\n");

        assertEquals(Boolean.FALSE, valueOf(ConfigKeys.GATE_SOUNDS_ENABLED), "not reset to its default of true");
        assertFalse(report.changes().stream().anyMatch(c -> c.key() == ConfigKeys.GATE_SOUNDS_ENABLED));
    }
}
