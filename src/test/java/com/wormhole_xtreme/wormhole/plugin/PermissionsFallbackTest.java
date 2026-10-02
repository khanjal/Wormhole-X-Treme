package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.UUID;
import org.bukkit.entity.Player;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * {@code permissions-auto-fallback} is asked at every check, so changing it in-game applies at once.
 *
 * <p>It used to be read once at enable, where finding no provider set
 * {@code permissions-support-disable} to {@code true} in memory, so turning the fallback off
 * afterwards changed nothing until a restart. (That value was never saved: config.yml's writer
 * leaves permissions-support-disable as the admin wrote it.)
 */
class PermissionsFallbackTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        // A node check logs what it asked, through the plugin.
        PluginTestSupport.install();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PermissionsSupport.setNoProvider(false);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
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

    /**
     * The whole check follows it: a player holding no node may use the compass under the fallback,
     * and not once the fallback is turned off in-game.
     */
    @Test
    void aPermissionCheckFollowsTheFallbackAtOnce()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(false);
        PermissionsSupport.setNoProvider(true);
        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, true);
        assertTrue(WXPermissions.checkWXPermissions(player, PermissionType.COMPASS), "simple mode lets anyone");

        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, false);

        assertFalse(WXPermissions.checkWXPermissions(player, PermissionType.COMPASS), "nodes, which they lack");
    }
}
