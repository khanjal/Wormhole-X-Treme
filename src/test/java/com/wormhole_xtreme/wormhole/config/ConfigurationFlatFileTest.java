package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * Reading a hand-edited flat config file, where a line may not have the colon the parser
 * assumed. Both cases used to index a split array straight at [1].
 */
class ConfigurationFlatFileTest
{
    @TempDir
    Path dir;

    private WormholeXTreme plugin;

    // Only the unknown-name case reaches the parser's own logging, and that call would
    // otherwise be made on a null plugin.
    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
    }

    private File write(final String... lines) throws IOException
    {
        final Path p = dir.resolve("options.txt");
        Files.write(p, String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }

    @Test
    void aSettingLineWithNothingAfterTheColonIsSkippedRatherThanEndingTheParse() throws IOException
    {
        final File f = write(
            "Setting:",
            "Value: junk",
            "Setting: TIMEOUT_ACTIVATE",
            "Value: 45");

        assertEquals("45", ConfigurationFlatFile.getValueFromSetting(f, ConfigKeys.TIMEOUT_ACTIVATE, "30"),
            "the truncated line should be stepped over and the real setting still found");
        // The outcome alone does not distinguish the length check from the catch below it:
        // without the check, "Setting:" throws on key[1], the catch logs it, and the parse
        // carries on to find 45 anyway. What separates them is that a truncated line is
        // ordinary and is not worth complaining about.
        verify(plugin, never()).prettyLog(any(java.util.logging.Level.class), anyString());
    }

    @Test
    void aValueLineWithNoColonFallsBackToTheDefault() throws IOException
    {
        final File f = write(
            "Setting: TIMEOUT_ACTIVATE",
            "no colon here");

        assertEquals("30", ConfigurationFlatFile.getValueFromSetting(f, ConfigKeys.TIMEOUT_ACTIVATE, "30"),
            "an unreadable value is the default, not a thrown index");
        // As above: the answer is "30" either way, because an index thrown here is caught per
        // line and the parse runs on to its own default. The length check is what keeps it
        // from being reported as a parse error every time somebody wraps a long value.
        verify(plugin, never()).prettyLog(any(java.util.logging.Level.class), anyString());
    }

    @Test
    void anOrdinarySettingStillReadsBack() throws IOException
    {
        final File f = write(
            "Setting: TIMEOUT_ACTIVATE",
            "Value: 45");

        assertEquals("45", ConfigurationFlatFile.getValueFromSetting(f, ConfigKeys.TIMEOUT_ACTIVATE, "30"));
    }

    @Test
    void aSettingThatIsNotInTheFileGivesTheDefault() throws IOException
    {
        final File f = write(
            "Setting: TIMEOUT_ACTIVATE",
            "Value: 45");

        assertEquals("38", ConfigurationFlatFile.getValueFromSetting(f, ConfigKeys.TIMEOUT_SHUTDOWN, "38"));
    }

    /**
     * A setting name the plugin no longer has does not end the parse.
     *
     * <p>{@code ConfigKeys.valueOf} throws for a name that is not in the enum, which is what
     * a config file carrying a setting from an older version looks like. The parser catches
     * it per line and keeps reading; without that, one stale line would hide every setting
     * written after it and the whole file would silently fall back to defaults.
     */
    @Test
    void aSettingNameThePluginNoLongerHasDoesNotHideTheOnesAfterIt() throws IOException
    {
        final File f = write(
            "Setting: SETTING_FROM_AN_OLDER_VERSION",
            "Value: whatever",
            "Setting: TIMEOUT_ACTIVATE",
            "Value: 45");

        assertEquals("45", ConfigurationFlatFile.getValueFromSetting(f, ConfigKeys.TIMEOUT_ACTIVATE, "30"),
            "the unknown name is stepped over and the real setting still found");
    }
}
