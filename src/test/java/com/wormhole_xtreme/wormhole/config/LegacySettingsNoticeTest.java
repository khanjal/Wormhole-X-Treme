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
    /**
     * The shape the old plugin wrote: three settings changed, one at its default, one long gone,
     * and two whose names survive but whose settings do not.
     */
    private static final List<String> SETTINGS_TXT = List.of(
        "WormholeXTreme 1.031",
        "WormholeXTreme Config Settings",
        "---------------",
        "Setting: BUILT_IN_PERMISSIONS_ENABLED",
        "Value: false",
        "Description: (the old file described each setting here)",
        "---------------",
        // In 2012 this meant not attaching to the old Permissions plugin; now it turns LuckPerms off.
        "Setting: PERMISSIONS_SUPPORT_DISABLE",
        "Value: true",
        "Description: (the old file described each setting here)",
        "---------------",
        // Nothing reads it now.
        "Setting: HELP_SUPPORT_DISABLE",
        "Value: true",
        "Description: (the old file described each setting here)",
        "---------------",
        "Setting: WORMHOLE_USE_IS_TELEPORT",
        "Value: TRUE",
        "Description: (the old file described each setting here)",
        "---------------",
        "Setting: TIMEOUT_ACTIVATE",
        // Written as a decimal where today's default is the whole number 30: still the default.
        "Value: 30.0",
        // A hand-edited file with a line repeated: it belongs to no setting and must not be read as TIMEOUT_ACTIVATE's.
        "Value: 99",
        "Description: (the old file described each setting here)",
        "---------------",
        "Setting: TIMEOUT_SHUTDOWN",
        // A decimal pasted as it is would fail as a whole-number setting on first use.
        "Value: 60.0",
        "Description: (the old file described each setting here)",
        "---------------",
        "Setting: LOG_LEVEL",
        // Level.parse takes only capitals.
        "Value: fine",
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
        assertEquals(List.of("wormhole-use-is-teleport: true", "timeout-shutdown: 60", "log-level: FINE"),
            LegacySettingsNotice.carryOver(SETTINGS_TXT, DefaultSettings.config),
            "TIMEOUT_ACTIVATE 30.0 is the default, BUILT_IN_PERMISSIONS_ENABLED no longer exists, the stray"
                + " Value: 99 is nobody's, and the permissions and Help settings are not the same settings now;"
                + " each value is spelled the way config.yml can read it");
    }

    @Test
    void aSettingsTxtInThePluginFolderIsAnnouncedWithWhatToCarryOver() throws IOException
    {
        Files.write(new File(directory, LegacySettingsNotice.LEGACY_FILE).toPath(), SETTINGS_TXT,
            StandardCharsets.ISO_8859_1);

        LegacySettingsNotice.announce(directory);

        verify(plugin).prettyLog(eq(Level.INFO),
            contains("Set these again in config.yml to keep them: wormhole-use-is-teleport: true,"
                + " timeout-shutdown: 60, log-level: FINE."));
    }

    /** A file that size is not a settings file, and reading it all during plugin load would cost for nothing. */
    @Test
    void aSettingsTxtFarTooBigToBeOneIsLeftUnread() throws IOException
    {
        final List<String> padded = new java.util.ArrayList<>(SETTINGS_TXT);
        final String filler = "#".repeat(1023);
        for (long written = 0; written <= LegacySettingsNotice.MAX_BYTES; written += filler.length() + 1)
        {
            padded.add(filler);
        }
        Files.write(new File(directory, LegacySettingsNotice.LEGACY_FILE).toPath(), padded, StandardCharsets.ISO_8859_1);

        LegacySettingsNotice.announce(directory);

        verify(plugin, never()).prettyLog(eq(Level.INFO), anyString());
    }

    /** A server that already has config.yml lost nothing to Settings.txt on this start. */
    @Test
    void aStartWithAConfigYmlAlreadyThereSaysNothing() throws IOException
    {
        when(plugin.getDataFolder()).thenReturn(directory);
        Files.write(new File(directory, LegacySettingsNotice.LEGACY_FILE).toPath(), SETTINGS_TXT,
            StandardCharsets.ISO_8859_1);
        Files.write(new File(directory, "config.yml").toPath(), List.of("timeout-activate: 30"),
            StandardCharsets.UTF_8);

        Configuration.loadConfiguration("WormholeXTreme");

        verify(plugin, never()).prettyLog(eq(Level.INFO), contains(LegacySettingsNotice.LEGACY_FILE));
    }

    @Test
    void aValueThatCannotBeOneForItsSettingIsLeftOut()
    {
        assertEquals(null, LegacySettingsNotice.asConfigValue(30, "45.5"),
            "a fraction cannot go in a whole-number setting, so naming it would only move the failure");
        assertEquals(null, LegacySettingsNotice.asConfigValue(Boolean.FALSE, "yes"),
            "nor can a word other than true or false go in an on-or-off one");
    }

    @Test
    void aFolderWithoutSettingsTxtSaysNothing()
    {
        LegacySettingsNotice.announce(directory);

        verify(plugin, never()).prettyLog(eq(Level.INFO), anyString());
    }
}
