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
    /** Whether the last look for a permission provider found none. */
    private static volatile boolean noProvider = false;

    /** Static helpers only; never instantiated. */
    private PermissionsSupport()
    {
    }

    /**
     * Whether permissions run in simple mode: anyone may use, dial and travel, and the rest is
     * for operators and gate owners.
     *
     * <p>Asked at every check, so {@code permissions-support-disable} and
     * {@code permissions-auto-fallback} changed in-game apply at once.
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
     * Looks for a Vault permission provider again, and remembers whether there is one.
     *
     * <p>Asked whatever {@code permissions-support-disable} says, so turning it off in-game finds
     * the fallback ready, and when either permission setting changes, which picks up a provider
     * installed since startup.
     *
     * @return true if one was found
     */
    public static boolean detectProvider()
    {
        boolean providerFound = false;
        try
        {
            final Class<?> permClass = Class.forName("net.milkbowl.vault.permission.Permission");
            final RegisteredServiceProvider<?> rsp =
                WormholeXTreme.getThisPlugin().getServer().getServicesManager().getRegistration(permClass);
            providerFound = (rsp != null);
        }
        catch (final Exception | LinkageError ignore)
        {
            // Best effort: no Vault is no provider.
        }
        noProvider = !providerFound;
        return providerFound;
    }

    /**
     * Looks for a provider at startup and says what permission handling follows from it.
     *
     * Permission checks are handled via Bukkit's standard player.hasPermission() API,
     * which integrates with Vault, LuckPerms, and other permission providers.
     */
    public static void enablePermissions()
    {
        final boolean providerFound = detectProvider();
        if (ConfigManager.getPermissionsSupportDisable())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Permission Plugin support disabled via configuration (config.yml).");
        }
        else if (providerFound)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Vault provider detected; permission checks will use Vault/Bukkit provider.");
        }
        else if (ConfigManager.getPermissionsAutoFallback())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "No Vault/LuckPerms provider detected; enabling simple permission fallback. Players may use gates; advanced actions require OP. Install Vault/LuckPerms to restore node-based permissions or set PERMISSIONS_AUTO_FALLBACK=false.");
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "No Vault/LuckPerms provider detected; permission checks will rely on server built-in permission handling (player.hasPermission()).");
        }
    }
}
