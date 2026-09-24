package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The whole plugin enables on a simulated server, from plugin.yml through onEnable.
 *
 * <p>The Mockito tests stub the server one call at a time, so none of them runs onEnable as a
 * server would: a listener that fails to register, or a command plugin.yml names that the plugin
 * never claims, only shows up on a real server. MockBukkit supplies that server.
 */
class PluginEnablesOnMockServerTest
{
    // Once per class: the plugin's statics outlive unmock(), so a second load sees the first's shapes.
    private static ServerMock server;
    private static WormholeXTreme plugin;

    @BeforeAll
    static void startServer()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(WormholeXTreme.class);
    }

    @AfterAll
    static void stopServer()
    {
        MockBukkit.unmock();
    }

    @Test
    void thePluginIsEnabledAfterLoading()
    {
        assertTrue(plugin.isEnabled(), "onEnable threw, or disabled the plugin itself");
        assertSame(plugin, server.getPluginManager().getPlugin("WormholeXTreme"));
    }

    /** An unclaimed command keeps the plugin as its executor, and the plugin answers nothing. */
    @Test
    void everyCommandInPluginYmlHasItsOwnExecutor()
    {
        for (final String name : plugin.getDescription().getCommands().keySet())
        {
            final PluginCommand command = plugin.getCommand(name);
            assertNotSame(plugin, command.getExecutor(), name);
            assertNotSame(plugin, command.getTabCompleter(), name);
        }
    }
}
