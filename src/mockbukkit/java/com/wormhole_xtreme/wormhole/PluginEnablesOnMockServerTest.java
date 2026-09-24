package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The whole plugin enables on a simulated server, from plugin.yml through onEnable.
 *
 * <p>The Mockito tests stub the server one call at a time, so none of them runs onEnable as a
 * server would: a command plugin.yml names that the plugin never claims only shows up on a real
 * server. MockBukkit supplies that server.
 */
@OnMockServer
class PluginEnablesOnMockServerTest
{
    // Once per class: the plugin's statics outlive unmock(), so a second load sees the first's shapes.
    private static ServerMock server;
    private static WormholeXTreme plugin;

    @BeforeAll
    static void startServer()
    {
        server = MockServerSupport.start();
        plugin = WormholeXTreme.getThisPlugin();
    }

    @AfterAll
    static void stopServer()
    {
        MockServerSupport.stop();
    }

    @Test
    void thePluginIsEnabledAfterLoading()
    {
        assertTrue(plugin.isEnabled(), "the plugin disabled itself during onEnable");
        assertSame(plugin, server.getPluginManager().getPlugin("WormholeXTreme"));
    }

    /**
     * An unclaimed command keeps the plugin as its executor and answers nothing, and with no
     * completer of its own, tab offers player names.
     */
    @Test
    @SuppressWarnings("deprecation") // getDescription(): Paper's replacement has no command map.
    void everyCommandInPluginYmlHasItsOwnExecutorAndCompleter()
    {
        final Set<String> names = plugin.getDescription().getCommands().keySet();
        assertFalse(names.isEmpty(), "plugin.yml declared no commands");
        for (final String name : names)
        {
            final PluginCommand command = plugin.getCommand(name);
            assertNotSame(plugin, command.getExecutor(), name);
            // The completer is null until set, never the plugin, so null is the case to check.
            assertNotNull(command.getTabCompleter(), name);
        }
    }
}
