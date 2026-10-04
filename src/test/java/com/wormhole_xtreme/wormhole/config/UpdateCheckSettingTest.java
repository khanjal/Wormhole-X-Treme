package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The switch for the startup update check (#461). It ships on, so it has to be in the defaults,
 * where an upgraded config.yml shows an operator it is there to turn off.
 */
class UpdateCheckSettingTest
{
    /** A config with no update-check at all reads as on, as the setting ships. */
    @Test
    void aMissingSwitchReadsAsOn()
    {
        ConfigTestSupport.clear();

        assertTrue(ConfigManager.isUpdateCheckEnabled());
    }

    /** update-check ships on, is written into config.yml with a description, and the getter reads it. */
    @Test
    void theSwitchShipsOnAndCanBeTurnedOff()
    {
        ConfigTestSupport.loadDefaults();
        try
        {
            final Setting setting = ConfigManager.getConfigurations().get(ConfigManager.ConfigKeys.UPDATE_CHECK);
            assertNotNull(setting, "update-check should be one of the shipped defaults, so an operator can find it");
            assertNotNull(setting.getDescription());
            assertTrue(ConfigManager.isUpdateCheckEnabled(), "on by default");

            ConfigTestSupport.set(ConfigManager.ConfigKeys.UPDATE_CHECK, false);
            assertFalse(ConfigManager.isUpdateCheckEnabled(), "the getter reads the setting, not a constant");
        }
        finally
        {
            ConfigTestSupport.clear();
        }
    }
}
