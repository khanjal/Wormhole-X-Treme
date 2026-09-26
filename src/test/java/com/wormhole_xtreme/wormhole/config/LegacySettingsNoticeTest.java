package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * An upgrade from a Settings.txt-era build is told which of its settings config.yml will not carry.
 *
 * <p>Found migrating a real server's plugin folder from 2016: Settings.txt is never read, so a
 * server that had turned {@code WORMHOLE_USE_IS_TELEPORT} on came up with it off and nothing said
 * so. Most of that file's settings are gone or at their default, so the note names only the ones
 * that would actually change something.
 */
class LegacySettingsNoticeTest
{
    /** The shape the old plugin wrote: two settings changed, two at their default and one long gone. */
    private static final List<String> SETTINGS_TXT = List.of(
        "WormholeXTreme 1.031",
        "WormholeXTreme Config Settings",
        "---------------",
        "Setting: BUILT_IN_PERMISSIONS_ENABLED",
        "Value: false",
        "Description: (the old file described each setting here)",
        "---------------",
        "Setting: WORMHOLE_USE_IS_TELEPORT",
        "Value: true",
        "Description: (the old file described each setting here)",
        "---------------",
        "Setting: TIMEOUT_ACTIVATE",
        "Value: 30",
        "Description: (the old file described each setting here)",
        "---------------",
        "Setting: TIMEOUT_SHUTDOWN",
        "Value: 60",
        "Description: (the old file described each setting here)",
        "---------------",
        "Setting: LOG_LEVEL",
        "Value: INFO",
        // A hand-edited file with a line repeated: it belongs to no setting and must not be read as LOG_LEVEL's.
        "Value: FINE",
        "Description: (the old file described each setting here)");

    @TempDir
    File directory;

    private WormholeXTreme plugin;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** Through the real first start, which is where the note has to fire for anyone to see it. */
    @Test
    void theFirstStartWithoutAConfigYmlAnnouncesTheSettingsTxtBesideIt() throws IOException
    {
        when(plugin.getDataFolder()).thenReturn(directory);
        Files.write(new File(directory, LegacySettingsNotice.LEGACY_FILE).toPath(), SETTINGS_TXT,
            StandardCharsets.ISO_8859_1);

        Configuration.loadConfiguration("WormholeXTreme");

        verify(plugin).prettyLog(eq(Level.INFO), contains("wormhole-use-is-teleport: true"));
    }

    @Test
    void onlySettingsThatStillExistAndDifferFromTheirDefaultAreNamed()
    {
        assertEquals(List.of("wormhole-use-is-teleport: true", "timeout-shutdown: 60"),
            LegacySettingsNotice.carryOver(SETTINGS_TXT, DefaultSettings.config),
            "TIMEOUT_ACTIVATE 30 and LOG_LEVEL INFO are the defaults, BUILT_IN_PERMISSIONS_ENABLED no"
                + " longer exists, and the stray Value: FINE is nobody's, so naming any of them would send"
                + " the operator after nothing");
    }

    @Test
    void aSettingsTxtInThePluginFolderIsAnnouncedWithWhatToCarryOver() throws IOException
    {
        Files.write(new File(directory, LegacySettingsNotice.LEGACY_FILE).toPath(), SETTINGS_TXT,
            StandardCharsets.ISO_8859_1);

        LegacySettingsNotice.announce(directory);

        verify(plugin).prettyLog(eq(Level.INFO),
            contains("Set these again in config.yml to keep them: wormhole-use-is-teleport: true, timeout-shutdown: 60."));
    }

    @Test
    void aFolderWithoutSettingsTxtSaysNothing()
    {
        LegacySettingsNotice.announce(directory);

        verify(plugin, never()).prettyLog(eq(Level.INFO), anyString());
    }
}
