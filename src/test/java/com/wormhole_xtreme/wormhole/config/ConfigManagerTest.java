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

    /**
     * A mirror's room is drawn to the server's usual view distance unless told otherwise.
     *
     * <p>"Should we increase the distance?" Ten chunks: past it the client shows nothing, so
     * nothing of this world appears through the mirror. Both the getter's fallback and the
     * default written to config.yml, so a fresh server and a half-written file agree.
     */
    @Test
    void aMirrorsRoomIsDrawnToTenChunksByDefault()
    {
        assertEquals(160, ConfigManager.getMirrorViewDepth(), "the getter's fallback");
        ConfigTestSupport.loadDefaults();
        assertEquals(160, ConfigManager.getMirrorViewDepth(), "and the default setting");
    }

    /** The wall past the depth is the sky's colour by default, and a setting names a block or none. */
    @Test
    void theWallPastTheDepthIsTheSkyByDefault()
    {
        assertEquals("sky", ConfigManager.getMirrorBackdrop(), "the getter's fallback");
        ConfigTestSupport.loadDefaults();
        assertEquals("sky", ConfigManager.getMirrorBackdrop(), "and the default setting");
        ConfigTestSupport.set(ConfigManager.ConfigKeys.MIRROR_BACKDROP, " Black_Concrete ");
        assertEquals("black_concrete", ConfigManager.getMirrorBackdrop(), "trimmed and lowered");
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
