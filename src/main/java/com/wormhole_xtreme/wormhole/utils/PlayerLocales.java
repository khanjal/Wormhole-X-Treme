package com.wormhole_xtreme.wormhole.utils;

import java.lang.reflect.Method;
import java.util.Locale;

import org.bukkit.entity.Player;

/**
 * The language a player's client is set to, read each time it is asked, since a player can change it
 * while online.
 *
 * <p>Paper deprecates {@code getLocale()} for {@code locale()}, which Spigot does not have, so that is
 * looked up by reflection; every Spigot, Paper and CraftBukkit API from 1.20 to 26.3 has
 * {@code getLocale()}.
 */
public final class PlayerLocales
{
    /** Paper's {@code locale()}, or null where the server lacks it. */
    record Lookup(Method get)
    {
    }

    /** Paper's, or whatever a test installed. */
    private static Lookup lookup = find();

    /** Static use only. */
    private PlayerLocales()
    {
    }

    /**
     * @param player
     *            whose client
     * @return its locale as the server reports it, such as {@code de_DE}, or null if it reports none
     */
    public static String of(final Player player)
    {
        final Lookup found = lookup;
        if (found != null)
        {
            try
            {
                if (found.get().invoke(player) instanceof Locale locale)
                {
                    return locale.getCountry().isEmpty() ? locale.getLanguage()
                        : locale.getLanguage() + "_" + locale.getCountry();
                }
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError refused)
            {
                // Read the Bukkit way instead.
            }
        }
        return bukkitLocale(player);
    }

    /**
     * Tests only.
     *
     * @param stand
     *            the method to read through, or null for none; {@link #forget()} restores Paper's
     */
    static void lookupWith(final Lookup stand)
    {
        lookup = stand;
    }

    /** Tests only: goes back to the server's own lookup. */
    static void forget()
    {
        lookup = find();
    }

    // Spigot has no other locale getter, and Paper deprecates this one.
    @SuppressWarnings("deprecation")
    private static String bukkitLocale(final Player player)
    {
        try
        {
            return player.getLocale();
        }
        catch (final RuntimeException | LinkageError unread)
        {
            return null;
        }
    }

    /** @return Paper's method, or null on a server without it */
    private static Lookup find()
    {
        try
        {
            return new Lookup(Player.class.getMethod("locale"));
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError notPaper)
        {
            return null;
        }
    }
}
