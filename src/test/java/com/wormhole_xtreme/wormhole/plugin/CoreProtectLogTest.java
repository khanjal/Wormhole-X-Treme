package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * What reaches CoreProtect (#238): construction and removal, only when asked for, and never at
 * the cost of the change itself.
 */
class CoreProtectLogTest
{
    /** Stands in for CoreProtect's plugin class, which hands out its API by {@code getAPI()}. */
    public abstract static class FakeCoreProtect implements Plugin
    {
        public abstract Object getAPI();
    }

    /** Shaped as CoreProtect's API is, so the plugin's reflection runs for real against it. */
    public static class FakeApi
    {
        final List<String> calls = new ArrayList<>();
        boolean enabled = true;
        int version = 10;

        public boolean isEnabled()
        {
            return enabled;
        }

        public int APIVersion()
        {
            return version;
        }

        public boolean logPlacement(final String user, final Location at, final Material type, final BlockData data)
        {
            calls.add("placed " + user + " " + type);
            return true;
        }

        public boolean logRemoval(final String user, final Location at, final Material type, final BlockData data)
        {
            calls.add("removed " + user + " " + type);
            return true;
        }
    }

    private final FakeApi api = new FakeApi();
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
        CoreProtectLog.setSinkForTest(null);
        final FakeCoreProtect coreProtect = mock(FakeCoreProtect.class);
        when(coreProtect.isEnabled()).thenReturn(true);
        when(coreProtect.getAPI()).thenReturn(api);
        final PluginManager plugins = mock(PluginManager.class);
        when(plugins.getPlugin("CoreProtect")).thenReturn(coreProtect);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        bukkit.close();
        CoreProtectLog.setSinkForTest(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private static Block block(final Material type)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getLocation()).thenReturn(new Location(null, 1, 64, 2));
        when(block.getBlockData()).thenReturn(mock(BlockData.class));
        return block;
    }

    /** Off, as it ships: nothing is sent, and CoreProtect is not even looked for. */
    @Test
    void nothingIsLoggedUntilItIsTurnedOn()
    {
        CoreProtectLog.placed(CoreProtectLog.PLUGIN_USER, block(Material.OBSIDIAN));

        assertEquals(List.of(), api.calls);
        bukkit.verify(Bukkit::getPluginManager, org.mockito.Mockito.never());
    }

    /** On, a placement and a removal reach CoreProtect's API, under the user given. */
    @Test
    void placementsAndRemovalsReachCoreProtect()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.COREPROTECT_ENABLED, true);

        CoreProtectLog.placed(CoreProtectLog.PLUGIN_USER, block(Material.OBSIDIAN));
        CoreProtectLog.removed("Fran", block(Material.SMOOTH_STONE_SLAB));

        assertEquals(List.of("placed #wormhole OBSIDIAN", "removed Fran SMOOTH_STONE_SLAB"), api.calls);
    }

    /** Air is not a block anybody built, so it is not logged. */
    @Test
    void airIsNotLogged()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.COREPROTECT_ENABLED, true);

        CoreProtectLog.removed(CoreProtectLog.PLUGIN_USER, block(Material.AIR));

        assertEquals(List.of(), api.calls);
    }

    /** An API that is off, or older than the one with block data, is left alone. */
    @Test
    void anOldOrDisabledApiIsNotUsed()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.COREPROTECT_ENABLED, true);
        api.version = 8;

        CoreProtectLog.placed(CoreProtectLog.PLUGIN_USER, block(Material.OBSIDIAN));

        assertEquals(List.of(), api.calls);
    }

    /** No CoreProtect on the server: nothing happens, and nothing throws. */
    @Test
    void noCoreProtectMeansNothingHappens()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.COREPROTECT_ENABLED, true);
        final PluginManager empty = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(empty);

        CoreProtectLog.placed(CoreProtectLog.PLUGIN_USER, block(Material.OBSIDIAN));

        assertEquals(List.of(), api.calls);
    }

    /** CoreProtect failing costs the log line, never the gate: nothing escapes to the caller. */
    @Test
    void aFailingCoreProtectDoesNotStopTheChange()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.COREPROTECT_ENABLED, true);
        CoreProtectLog.setSinkForTest((placed, user, at, type, data) ->
        {
            throw new IllegalStateException("CoreProtect fell over");
        });

        CoreProtectLog.placed(CoreProtectLog.PLUGIN_USER, block(Material.OBSIDIAN));

        assertTrue(api.calls.isEmpty());
    }
}
