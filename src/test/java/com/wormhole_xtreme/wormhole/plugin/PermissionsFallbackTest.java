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
 * With no permission provider the plugin falls back to simple mode, asked at every check, so a
 * provider found again applies at once.
 *
 * <p>It used to be read once at enable, where finding no provider set
 * {@code permissions-support-disable} to {@code true} in memory, so nothing found afterwards
 * changed anything until a restart. The fallback was a setting, permissions-auto-fallback, until
 * 1.11.0; it is always on now.
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

    /** No provider: simple mode. A provider found again: nodes, at once. */
    @Test
    void theFallbackFollowsWhetherAProviderIsThere()
    {
        PermissionsSupport.setNoProvider(true);
        assertTrue(PermissionsSupport.isSimpleMode());

        PermissionsSupport.setNoProvider(false);
        assertFalse(PermissionsSupport.isSimpleMode(), "nodes again, without a restart");
    }

    /** Turning provider support off is simple mode whatever was found. */
    @Test
    void supportTurnedOffIsSimpleModeWhateverWasFound()
    {
        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, true);
        PermissionsSupport.setNoProvider(false);

        assertTrue(PermissionsSupport.isSimpleMode());
    }

    /**
     * The whole check follows it: a player holding no node may use the compass under the fallback,
     * and not once a provider is there.
     */
    @Test
    void aPermissionCheckFollowsTheFallbackAtOnce()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(false);
        PermissionsSupport.setNoProvider(true);
        assertTrue(WXPermissions.checkWXPermissions(player, PermissionType.COMPASS), "simple mode lets anyone");

        PermissionsSupport.setNoProvider(false);

        assertFalse(WXPermissions.checkWXPermissions(player, PermissionType.COMPASS), "nodes, which they lack");
    }
}
