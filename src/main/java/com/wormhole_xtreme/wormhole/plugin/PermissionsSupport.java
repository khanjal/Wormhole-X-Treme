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
            boolean providerFound = false;
            try {
                final Class<?> permClass = Class.forName("net.milkbowl.vault.permission.Permission");
                final org.bukkit.plugin.RegisteredServiceProvider<?> rsp = WormholeXTreme.getThisPlugin().getServer().getServicesManager().getRegistration(permClass);
                if (rsp != null) {
                    providerFound = true;
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Vault provider detected; permission checks will use Vault/Bukkit provider.");
                }
            } catch (final Throwable ignore) {}

            if (!providerFound)
            {
                if (ConfigManager.getPermissionsAutoFallback())
                {
                    ConfigManager.setPermissionsSupportDisable(true);
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "No Vault/LuckPerms provider detected; enabling simple permission fallback. Players may use gates; advanced actions require OP. Install Vault/LuckPerms to restore node-based permissions or set PERMISSIONS_AUTO_FALLBACK=false.");
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "No Vault/LuckPerms provider detected; permission checks will rely on server built-in permission handling (player.hasPermission()).");
                }
            }
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Permission Plugin support disabled via configuration (config.yml).");
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
