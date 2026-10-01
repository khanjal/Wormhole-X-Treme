package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * {@code permissions-auto-fallback} is asked at every check, so changing it in-game applies at once.
 *
 * <p>It used to be read once at enable, where finding no provider wrote {@code true} into
 * {@code permissions-support-disable} in memory. Turning the fallback off afterwards changed
 * nothing, and the next save wrote that {@code true} into config.yml, where it outlived the server
 * ever finding a provider.
 */
class PermissionsFallbackTest
{
    @AfterEach
    void tearDown()
    {
        PermissionsSupport.setNoProvider(false);
        ConfigTestSupport.clear();
    }

    /** No provider and the fallback on: simple mode. Turned off in-game: nodes again, at once. */
    @Test
    void theFallbackFollowsTheSettingWhileNoProviderIsThere()
    {
        PermissionsSupport.setNoProvider(true);
        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, true);
        assertTrue(PermissionsSupport.isSimpleMode());

        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, false);
        assertFalse(PermissionsSupport.isSimpleMode(), "nodes again, without a restart");
    }

    /** With a provider there is nothing to fall back from. */
    @Test
    void aServerWithAProviderNeverFallsBack()
    {
        PermissionsSupport.setNoProvider(false);
        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, true);

        assertFalse(PermissionsSupport.isSimpleMode());
    }

    /** Turning provider support off is simple mode whatever was found. */
    @Test
    void supportTurnedOffIsSimpleModeWhateverWasFound()
    {
        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, true);
        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, false);

        assertTrue(PermissionsSupport.isSimpleMode());
    }
}
