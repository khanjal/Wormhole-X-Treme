package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.errorBody;
import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.infoBody;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;

/**
 * What {@code /wormhole config reload} says, and who may run it (#634).
 *
 * <p>The word has to reach the reload rather than the setting search every other single word at
 * {@code /wormhole config} goes to, which would answer that no setting matches RELOAD.
 */
class ConfigReloadCommandTest
{
    @TempDir
    File dataFolder;

    private ConsoleCommandSender console;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = PluginTestSupport.install();
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        console = mock(ConsoleCommandSender.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private void writeConfig(final String yaml) throws Exception
    {
        Files.writeString(new File(dataFolder, "config.yml").toPath(), yaml, StandardCharsets.UTF_8);
    }

    /** Each change is one line, old value to new. */
    @Test
    void aChangeIsSaidOldToNew() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);
        writeConfig("gate-sounds-enabled: false\n");

        new ConfigCommand().execute(console, new String[] { "config", "reload" });

        verify(console).sendMessage(infoBody("GATE_SOUNDS_ENABLED: true -> false."));
    }

    /** A refused value is an error, in the parser's words, naming the value kept. */
    @Test
    void aRefusedValueIsAnErrorNamingTheValueKept() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);
        writeConfig("gate-sounds-enabled: loud\n");

        new ConfigCommand().execute(console, new String[] { "config", "RELOAD" });

        verify(console).sendMessage(errorBody("GATE_SOUNDS_ENABLED is true or false, not \"loud\". Kept true."));
    }

    /** A file saying what is already set says nothing changed, rather than nothing at all. */
    @Test
    void anUnchangedFileSaysNoSettingChanged() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);
        writeConfig("gate-sounds-enabled: true\n");

        new ConfigCommand().execute(console, new String[] { "config", "reload" });

        verify(console).sendMessage(infoBody("Reloaded config.yml: no setting changed."));
    }

    /** A broken file is an error saying nothing changed. */
    @Test
    void aBrokenFileIsAnErrorSayingNothingChanged() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);
        writeConfig("- not\n- settings\n");

        new ConfigCommand().execute(console, new String[] { "config", "reload" });

        verify(console).sendMessage(errorBody("config.yml is not a list of settings; no setting changed."));
    }

    /** A player without {@code wormhole.config} is refused, and the file is not taken. */
    @Test
    void aPlayerWithoutConfigIsRefused() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);
        writeConfig("gate-sounds-enabled: false\n");
        final Player player = mock(Player.class);
        try (MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class))
        {
            perms.when(() -> WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.CONFIG))
                .thenReturn(false);

            new ConfigCommand().execute(player, new String[] { "config", "reload" });

            verify(player).sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            assertTrue(ConfigManager.isGateSoundsEnabled(), "the file's false was not taken");
        }
    }
}
