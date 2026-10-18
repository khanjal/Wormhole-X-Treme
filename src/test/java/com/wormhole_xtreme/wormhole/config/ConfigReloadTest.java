package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * {@code /wormhole config -reload} takes what an admin edited into config.yml, and nothing it should not.
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
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        // A reload reads lang/ too; without a data folder that would be under the working directory.
        when(plugin.getDataFolder()).thenReturn(directory);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        Messages.load();
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

    /**
     * A missing file is said to be missing, in a few words, rather than read as every setting absent.
     *
     * <p>The exception's own text is the server's absolute path, which has no place in chat.
     */
    @Test
    void aMissingFileChangesNothingAndSaysSoShortly()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);

        final ConfigReload.Report report = ConfigReload.reload(new File(directory, "config.yml"));

        assertEquals("There is no config.yml in the plugin folder", report.unreadable());
    }

    /** A file using the enum spelling, or a key's old name, is read as startup reads it. */
    @Test
    void theEnumSpellingAndARenamedKeyAreRead() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);
        ConfigTestSupport.set(ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 16);

        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            reload("ECONOMY_ENABLED: true\nmirror-proximity-radius: 9\n");
        }

        assertEquals(Boolean.TRUE, valueOf(ConfigKeys.ECONOMY_ENABLED));
        assertEquals(9, valueOf(ConfigKeys.MIRROR_PROXIMITY_DISTANCE));
    }

    /** A number left under a switch is read as startup reads it, on above 0, not refused. */
    @Test
    void aNumberUnderASwitchIsReadAsTheSwitch() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);

        final ConfigReload.Report report;
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            report = reload("economy-enabled: 1\n");
        }

        assertEquals(Boolean.TRUE, valueOf(ConfigKeys.ECONOMY_ENABLED));
        assertEquals(List.of(), report.rejections());
    }

    /** A decimal setting takes a decimal. */
    @Test
    void aDecimalSettingTakesADecimal() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_SOUND_VOLUME, 1.5);

        reload("gate-sound-volume: 0.5\n");

        assertEquals(0.5, valueOf(ConfigKeys.GATE_SOUND_VOLUME));
    }

    /** A whole-number setting refuses a decimal as a whole number, quoting what was written. */
    @Test
    void aWholeNumberSettingRefusesADecimalAsAWholeNumber() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.RING_REACH, 4);

        final ConfigReload.Report report = reload("ring-reach: 2.5\n");

        assertEquals(List.of(new ConfigReload.Rejection(ConfigKeys.RING_REACH,
            "RING_REACH is a whole number, not \"2.5\".", 4)), report.rejections());
        assertEquals(4, valueOf(ConfigKeys.RING_REACH));
    }

    /**
     * A bare {@code off} under a text setting is its default, as startup reads it, not refused as
     * {@code "false"}, a word the admin never wrote.
     */
    @Test
    void aBareOffUnderATextSettingIsItsDefault() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_ANIMATION, "spiral");

        final ConfigReload.Report report = reload("gate-view: off\ngate-iris-animation: off\n");

        assertEquals(List.of(), report.rejections());
        assertEquals("horizon", valueOf(ConfigKeys.GATE_VIEW));
        assertEquals("sweep", valueOf(ConfigKeys.GATE_IRIS_ANIMATION));
    }

    /** A bare {@code log-level: off}, which YAML reads as false, is the level OFF, as startup reads it (#647). */
    @Test
    void aBareOffUnderLogLevelIsTheLevelOff() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.LOG_LEVEL, "WARNING");

        final ConfigReload.Report report = reload("log-level: off\n");

        assertEquals(List.of(), report.rejections());
        assertEquals("OFF", valueOf(ConfigKeys.LOG_LEVEL));
    }

    /**
     * A bare switch under any other text setting is refused, naming the switch it was read as, and
     * the old value kept.
     *
     * <p>Reading it as the default would turn {@code ring-default-style: off} into whatever the default
     * style is, and {@code log-level: on} is no level at all.
     */
    @Test
    void aBareSwitchUnderAnyOtherTextSettingIsRefused() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.LOG_LEVEL, "WARNING");
        ConfigTestSupport.set(ConfigKeys.RING_DEFAULT_STYLE, "SEQUENTIAL");

        final ConfigReload.Report report = reload("log-level: on\nring-default-style: off\n");

        assertEquals(List.of(), report.changes());
        assertEquals(List.of(
            new ConfigReload.Rejection(ConfigKeys.LOG_LEVEL,
                "LOG_LEVEL was read as a switch (true); write the value in quotes.", "WARNING"),
            new ConfigReload.Rejection(ConfigKeys.RING_DEFAULT_STYLE,
                "RING_DEFAULT_STYLE was read as a switch (false); write the value in quotes.", "SEQUENTIAL")),
            report.rejections());
        assertEquals("WARNING", valueOf(ConfigKeys.LOG_LEVEL));
        assertEquals("SEQUENTIAL", valueOf(ConfigKeys.RING_DEFAULT_STYLE));
    }

    /** A value in quotes is taken as the text it is, in the command's form. */
    @Test
    void aQuotedValueIsTakenAsText() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.RING_DEFAULT_STYLE, "SEQUENTIAL");

        reload("ring-default-style: \"concurrent\"\n");

        assertEquals("CONCURRENT", valueOf(ConfigKeys.RING_DEFAULT_STYLE));
    }

    /** An empty file, or one of only comments, is said to be empty. */
    @Test
    void anEmptyFileIsSaidToBeEmpty() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);

        assertEquals("config.yml is empty", reload("# nothing but a comment\n").unreadable());
        assertEquals("config.yml is empty", reload("").unreadable());
    }

    /** A bare {@code off} the server started from, stored as {@code "false"}, is no change on reload. */
    @Test
    void aBareOffTheServerStartedFromIsNoChange() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "false");

        final ConfigReload.Report report = reload("gate-view: off\n");

        assertEquals(new ConfigReload.Report(null, List.of(), List.of()), report);
    }

    /**
     * A value startup stored as written, which the command would store differently, is taken in the
     * command's form and reported.
     *
     * <p>{@code slow} is a sequential ring style to the parser and concurrent to the getter, which
     * reads the stored text by enum name.
     */
    @Test
    void aValueStoredAsWrittenIsTakenInTheCommandsForm() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.RING_DEFAULT_STYLE, "slow");

        final ConfigReload.Report report = reload("ring-default-style: slow\n");

        assertEquals("SEQUENTIAL", valueOf(ConfigKeys.RING_DEFAULT_STYLE));
        assertEquals(List.of(new ConfigReload.Change(ConfigKeys.RING_DEFAULT_STYLE, "slow", "SEQUENTIAL", null)),
            report.changes());
    }

    /** A refused value startup already stored is reported, every reload, rather than passed over as unchanged. */
    @Test
    void aRefusedValueAlreadyStoredIsReported() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.RING_DEFAULT_STYLE, "banana");

        final ConfigReload.Report report = reload("ring-default-style: banana\n");

        assertEquals(1, report.rejections().size(), report.rejections().toString());
        assertEquals("banana", report.rejections().get(0).kept());
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

    private void writeGerman(final String none) throws Exception
    {
        final File lang = new File(directory, "lang");
        lang.mkdirs();
        Files.writeString(new File(lang, "de.yml").toPath(), "compass.none: \"" + none + "\"\n", StandardCharsets.UTF_8);
    }

    /** A changed language is read at once, not at the next restart (#26). */
    @Test
    void aChangedLanguageIsReadAtOnce() throws Exception
    {
        writeGerman("Keine Wurmlöcher.");
        ConfigTestSupport.set(ConfigKeys.LANGUAGE, "en");

        final ConfigReload.Report report = reload("language: de\n");

        assertEquals(1, report.changes().size(), String.valueOf(report.changes()));
        assertEquals("Keine Wurmlöcher.", Messages.get("compass.none"));
    }

    /**
     * An edited translation is read by a reload that leaves {@code language} as it was: that is how a
     * translator sees their work without a restart (#26).
     */
    @Test
    void anEditedTranslationIsReadWithTheLanguageUnchanged() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.LANGUAGE, "de");
        writeGerman("Alt.");
        Messages.load();
        assertEquals("Alt.", Messages.get("compass.none"), "the first text should be read");
        writeGerman("Neu.");

        final ConfigReload.Report report = reload("language: de\n");

        assertEquals(List.of(), report.changes(), "language did not change");
        assertEquals("Neu.", Messages.get("compass.none"));
    }

    /**
     * A bare {@code no} or {@code off} is a YAML switch, not the language code: the reload says to quote
     * it and keeps the language (#26).
     */
    @Test
    void aBareNoUnderLanguageIsRefusedWithAdviceToQuoteIt() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.LANGUAGE, "de");

        final ConfigReload.Report report = reload("language: no\n");

        assertEquals(1, report.rejections().size(), String.valueOf(report.rejections()));
        assertTrue(report.rejections().get(0).refusal().contains("in quotes"), report.rejections().get(0).refusal());
        assertEquals("de", valueOf(ConfigKeys.LANGUAGE), "the language is kept");
        assertEquals(List.of(), report.changes());

        assertEquals(List.of(), reload("language: \"no\"\n").rejections(), "quoted, no is the code for Norwegian");
        assertEquals("no", valueOf(ConfigKeys.LANGUAGE));
    }
}
