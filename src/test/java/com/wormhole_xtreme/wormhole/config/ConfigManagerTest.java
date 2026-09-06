package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigManagerTest
{
    @BeforeEach
    void setUp()
    {
        try
        {
            final java.lang.reflect.Field f = ConfigManager.class.getDeclaredField("configurations");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            final java.util.concurrent.ConcurrentHashMap<ConfigManager.ConfigKeys, Setting> map = (java.util.concurrent.ConcurrentHashMap<ConfigManager.ConfigKeys, Setting>) f.get(null);
            map.clear();
        }
        catch (final Exception e)
        {
            // ignore
        }
    }
    @AfterEach
    void tearDown()
    {
        // ConfigManager keeps its settings in a private static map; clear it so tests stay isolated.
        try
        {
            final java.lang.reflect.Field f = ConfigManager.class.getDeclaredField("configurations");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            final java.util.concurrent.ConcurrentHashMap<ConfigManager.ConfigKeys, Setting> map = (java.util.concurrent.ConcurrentHashMap<ConfigManager.ConfigKeys, Setting>) f.get(null);
            map.clear();
        }
        catch (final Exception e)
        {
            // ignore
        }
    }

    /**
     * A setting nobody has configured reads back as the default written into the getter.
     * That fallback is what keeps a half-written config.yml working.
     */
    @Test
    void aMissingSettingFallsBackToItsDefault()
    {
        assertEquals(30, ConfigManager.getTimeoutActivate(), "activation timeout default");
        assertEquals(300, ConfigManager.getMaxOpenSeconds(), "max open seconds default");
    }

    /** A configured setting is read back rather than the default. */
    @Test
    void aConfiguredSettingIsReadBackInsteadOfTheDefault()
    {
        ConfigManager.getConfigurations().put(ConfigManager.ConfigKeys.TIMEOUT_ACTIVATE,
            new Setting(ConfigManager.ConfigKeys.TIMEOUT_ACTIVATE, 45, "test", "WormholeXTreme"));

        assertEquals(45, ConfigManager.getTimeoutActivate());
    }

    /** The one setting with a public setter round-trips through its own getter. */
    @Test
    void permissionsSupportDisableRoundTrips()
    {
        ConfigManager.setPermissionsSupportDisable(true);
        assertEquals(true, ConfigManager.getPermissionsSupportDisable());

        ConfigManager.setPermissionsSupportDisable(false);
        assertEquals(false, ConfigManager.getPermissionsSupportDisable());
    }
}
