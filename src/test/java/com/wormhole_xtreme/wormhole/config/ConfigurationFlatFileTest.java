package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * Reading a hand-edited flat config file, where a line may not have the colon the parser
 * assumed. Both cases used to index a split array straight at [1].
 */
class ConfigurationFlatFileTest
{
    @TempDir
    Path dir;

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
    }

    @Test
    void aValueLineWithNoColonFallsBackToTheDefault() throws IOException
    {
        final File f = write(
            "Setting: TIMEOUT_ACTIVATE",
            "no colon here");

        assertEquals("30", ConfigurationFlatFile.getValueFromSetting(f, ConfigKeys.TIMEOUT_ACTIVATE, "30"),
            "an unreadable value is the default, not a thrown index");
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
}
