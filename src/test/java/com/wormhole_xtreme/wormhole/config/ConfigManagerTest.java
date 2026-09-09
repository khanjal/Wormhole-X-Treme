package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigManagerTest
{
    // The settings map is a static that outlives a test, so one test's settings would
    // otherwise decide what the next one reads. ConfigTestSupport lives in this package for
    // exactly this; reaching for the field reflectively also swallowed every exception, so a
    // rename would have quietly stopped the isolation rather than failing.
    @BeforeEach
    void setUp()
    {
        ConfigTestSupport.clear();
    }

    @AfterEach
    void tearDown()
    {
        ConfigTestSupport.clear();
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
