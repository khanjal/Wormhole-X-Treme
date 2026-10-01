package com.wormhole_xtreme.wormhole.plugin;

import java.util.logging.Level;

import org.bukkit.plugin.RegisteredServiceProvider;

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
    /** Whether startup looked for a permission provider and found none. */
    private static volatile boolean noProvider = false;

    /** Static helpers only; never instantiated. */
    private PermissionsSupport()
    {
    }

    /**
     * Whether permissions run in simple mode: anyone may use, dial and travel, and the rest is
     * for operators and gate owners.
     *
     * <p>Asked at every check, so {@code permissions-auto-fallback} changed in-game applies at
     * once. Only whether a provider exists is settled at startup.
     *
     * @return true if permission nodes are not consulted
     */
    public static boolean isSimpleMode()
    {
        return ConfigManager.getPermissionsSupportDisable()
            || (noProvider && ConfigManager.getPermissionsAutoFallback());
    }

    /**
     * Records whether a provider was found, for a test standing in for startup.
     *
     * @param missing
     *            true if none was
     */
    static void setNoProvider(final boolean missing)
    {
        noProvider = missing;
    }


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
                final RegisteredServiceProvider<?> rsp = WormholeXTreme.getThisPlugin().getServer().getServicesManager().getRegistration(permClass);
                if (rsp != null) {
                    providerFound = true;
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Vault provider detected; permission checks will use Vault/Bukkit provider.");
                }
            } catch (final Exception | LinkageError ignore) { /* best effort */ }

            // Remembered rather than written into permissions-support-disable, which would then be
            // saved to config.yml and outlive the server finding a provider.
            noProvider = !providerFound;
            if (!providerFound)
            {
                if (ConfigManager.getPermissionsAutoFallback())
                {
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
}
