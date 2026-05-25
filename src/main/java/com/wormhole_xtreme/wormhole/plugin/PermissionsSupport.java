package com.wormhole_xtreme.wormhole.plugin;

import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * The Class PermissionsSupport.
 * 
 * Handles permission system initialization. Uses Vault/LuckPerms for permission checks via
 * standard Bukkit API (player.hasPermission()). Falls back to built-in permission levels
 * if no permission backend is available.
 * 
 * @author alron
 */
public class PermissionsSupport
{

    /**
     * Setup permissions (informational only).
     * 
     * Permission checks are handled via Bukkit's standard player.hasPermission() API,
     * which integrates with Vault, LuckPerms, and other permission providers.
     */
    public static void enablePermissions()
    {
        if (!ConfigManager.getPermissionsSupportDisable())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Permission checks configured to use Vault/LuckPerms or Bukkit-native permissions.");
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "For best results, install and configure Vault and LuckPerms or another Vault-compatible provider.");
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Permission Plugin support disabled via configuration (config.yml).");
        }
    }

    /**
     * Disable permissions (placeholder for compatibility).
     */
    public static void disablePermissions()
    {
        // No-op: permissions are handled via Bukkit API; no persistent handler to detach
    }
}
