package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The switch for bStats (#239). It ships on, as bStats' own convention is, unlike the
 * integrations with other plugins beside it; so it has to be in the defaults, where an upgraded
 * config.yml shows an operator it is there to turn off.
 */
class MetricsSettingTest
{
    /** metrics-enabled ships on, is written into config.yml with a description, and the getter reads it. */
    @Test
    void theSwitchShipsOnAndCanBeTurnedOff()
    {
        ConfigTestSupport.loadDefaults();
        try
        {
            final Setting setting = ConfigManager.getConfigurations().get(ConfigManager.ConfigKeys.METRICS_ENABLED);
            assertNotNull(setting, "metrics-enabled should be one of the shipped defaults, so an operator can find it");
            assertNotNull(setting.getDescription());
            assertTrue(ConfigManager.isMetricsEnabled(), "on by default, as bStats' convention is");

            ConfigTestSupport.set(ConfigManager.ConfigKeys.METRICS_ENABLED, false);
            assertFalse(ConfigManager.isMetricsEnabled(), "the getter reads the setting, not a constant");
        }
        finally
        {
            ConfigTestSupport.clear();
        }
    }
}
