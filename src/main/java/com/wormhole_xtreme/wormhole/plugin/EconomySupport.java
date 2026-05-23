package com.wormhole_xtreme.wormhole.plugin;

import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Vault Economy integration support.
 * All methods are safe to call even when economy is disabled or Vault is absent —
 * they will no-op and return appropriate defaults.
 */
public final class EconomySupport
{
    private static Object economy = null; // net.milkbowl.vault.economy.Economy

    private EconomySupport() {}

    /**
     * Attempt to attach to Vault's Economy service provider.
     * Called from WormholeXTreme.onEnable after permissions are set up.
     */
    public static void enableEconomy()
    {
        economy = null;
        try
        {
            final Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings("unchecked")
            final RegisteredServiceProvider<Object> rsp =
                (RegisteredServiceProvider<Object>) WormholeXTreme.getThisPlugin()
                    .getServer().getServicesManager().getRegistration(ecoClass);
            if (rsp != null)
            {
                economy = rsp.getProvider();
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                    "Attached to Vault economy provider: " + economy.getClass().getSimpleName());
            }
            else
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                    "Economy enabled in config but no Vault economy provider found. Economy features disabled.");
            }
        }
        catch (final ClassNotFoundException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Vault not found. Economy features disabled.");
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Failed to attach to Vault economy: " + t.getMessage());
        }
    }

    /** Detach from economy provider (e.g. on plugin disable). */
    public static void disableEconomy()
    {
        economy = null;
    }

    /** Returns true if economy is available and connected. */
    public static boolean isAvailable()
    {
        return economy != null;
    }

    /**
     * Returns true if the player has at least {@code amount} in their balance.
     * Always returns true if economy is unavailable (fail-open).
     */
    public static boolean canAfford(final Player player, final double amount)
    {
        if (economy == null || amount <= 0) return true;
        try
        {
            final java.lang.reflect.Method hasMethod =
                economy.getClass().getMethod("has", org.bukkit.OfflinePlayer.class, double.class);
            return (Boolean) hasMethod.invoke(economy, player, amount);
        }
        catch (final Throwable t)
        {
            return true;
        }
    }

    /**
     * Withdraws {@code amount} from the player's balance.
     * Returns true on success, false if the charge failed.
     * No-ops (returns true) if economy is unavailable or amount <= 0.
     */
    public static boolean charge(final Player player, final double amount)
    {
        if (economy == null || amount <= 0) return true;
        try
        {
            final java.lang.reflect.Method withdrawMethod =
                economy.getClass().getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
            final Object result = withdrawMethod.invoke(economy, player, amount);
            // EconomyResponse.transactionSuccess()
            final java.lang.reflect.Method successMethod = result.getClass().getMethod("transactionSuccess");
            return (Boolean) successMethod.invoke(result);
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Economy charge failed for " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * Returns the currency name (singular) from the economy provider, or empty string if unavailable.
     */
    public static String currencyName(final double amount)
    {
        if (economy == null) return "";
        try
        {
            if (amount == 1.0)
            {
                final java.lang.reflect.Method m = economy.getClass().getMethod("currencyNameSingular");
                return (String) m.invoke(economy);
            }
            else
            {
                final java.lang.reflect.Method m = economy.getClass().getMethod("currencyNamePlural");
                return (String) m.invoke(economy);
            }
        }
        catch (final Throwable t)
        {
            return "";
        }
    }
}
