package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The switch that decides whether the PlaceholderAPI expansion is registered at all.
 *
 * <p>It has to default to off. The expansion registers against another plugin, and a server
 * that upgrades this one has not asked for that: an integration switching itself on during a
 * routine upgrade is a surprise, and on a server without PlaceholderAPI it would be a warning
 * in the log for something nobody wanted.
 *
 * <p>The defaults are loaded first on purpose. Nothing fills the settings map until a config
 * is read, and every getter answers "not configured" until then -- so without this the first
 * test below would pass because the setting does not exist, which is exactly what it is
 * supposed to rule out. It did, on the first run of this file.
 */
class PlaceholderSettingTest
{
    @BeforeEach
    void loadTheShippedDefaults()
    {
        ConfigTestSupport.loadDefaults();
    }

    @Test
    void placeholdersAreOffUntilSomebodyAsksForThem()
    {
        assertFalse(ConfigManager.isPlaceholdersEnabled(),
            "a routine upgrade must not switch on an integration with another plugin");
    }

    @Test
    void theSettingIsRegisteredSoItReachesAnUpgradedConfigFile()
    {
        // A key the defaults do not carry never reaches an existing config.yml, so an operator
        // reading the file to find out what they can turn on would not find this at all: the
        // setting would exist only for somebody who already knew its name.
        final Setting setting =
            ConfigManager.getConfigurations().get(ConfigManager.ConfigKeys.PLACEHOLDERS_ENABLED);

        assertNotNull(setting, "placeholders-enabled should be one of the shipped defaults");
        assertFalse(setting.getBooleanValue(), "and should ship off");
        assertNotNull(setting.getDescription(),
            "with a line saying what it does, like every other setting in the file");
    }

    @Test
    void turningItOnIsWhatTheGetterReads()
    {
        // The control for the first test: a default of false has to be a real default rather
        // than a getter that always says no, which would read exactly the same way.
        try
        {
            ConfigTestSupport.set(ConfigManager.ConfigKeys.PLACEHOLDERS_ENABLED, true);

            assertTrue(ConfigManager.isPlaceholdersEnabled(),
                "the getter should read the setting, not a constant");
        }
        finally
        {
            ConfigTestSupport.set(ConfigManager.ConfigKeys.PLACEHOLDERS_ENABLED, false);
        }
    }
}
