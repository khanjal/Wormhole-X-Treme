package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigManagerTest
{
    @BeforeEach
    public void setUp()
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
    public void tearDown()
    {
        // Reset storage backend setting to default by clearing the configurations map via reflection.
        // This keeps tests isolated; ConfigManager uses a private static map.
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

}
