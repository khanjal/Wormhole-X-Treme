package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The version in the banner, the log tag, the update check and the placeholder comes from Paper's
 * plugin meta where there is one, and from Bukkit's description otherwise.
 *
 * <p>Spigot's API has no {@code getPluginMeta()}, so these tests install a stand-in pair of
 * methods, found by reflection exactly as Paper's are.
 */
class PluginVersionTest
{
    /** Paper's meta, as far as the version goes. */
    public interface Meta
    {
        String getVersion();
    }

    /** Paper's {@code getPluginMeta()}, under a name no API build has. */
    public interface Metadated
    {
        Meta meta();
    }

    private Plugin plugin;

    private Meta meta;

    @BeforeEach
    void setUp() throws NoSuchMethodException
    {
        plugin = mock(Plugin.class, withSettings().extraInterfaces(Metadated.class));
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("WormholeXTreme", "1.11.0", "Main"));
        meta = mock(Meta.class);
        when(meta.getVersion()).thenReturn("1.12.0");
        PluginVersion.lookupWith(PluginVersion.lookupFrom(Metadated.class.getMethod("meta")));
    }

    @AfterEach
    void tearDown()
    {
        PluginVersion.forget();
    }

    /** Paper deprecates the description, so a Paper server is asked for its meta's version. */
    @Test
    void paperGivesTheVersionFromItsPluginMeta()
    {
        when(((Metadated) plugin).meta()).thenReturn(meta);

        assertEquals("1.12.0", PluginVersion.of(plugin));
    }

    @Test
    void aServerWithoutPluginMetaGivesTheDescriptionsVersion()
    {
        PluginVersion.lookupWith(null);

        assertEquals("1.11.0", PluginVersion.of(plugin));
    }

    /** A real JavaPlugin answers a null meta until the server has set it up. */
    @Test
    void aPluginWithNoMetaYetGivesTheDescriptionsVersion()
    {
        assertEquals("1.11.0", PluginVersion.of(plugin));
    }

    @Test
    void aMetaThatThrowsGivesTheDescriptionsVersion()
    {
        when(((Metadated) plugin).meta()).thenThrow(new IllegalStateException("not yet"));

        assertEquals("1.11.0", PluginVersion.of(plugin));
    }

    /** A meta type without {@code getVersion()} is no lookup at all, not one that fails on every call. */
    @Test
    void aMetaWithoutAVersionIsNoLookup() throws NoSuchMethodException
    {
        assertNull(PluginVersion.lookupFrom(Object.class.getMethod("toString")));
    }
}
