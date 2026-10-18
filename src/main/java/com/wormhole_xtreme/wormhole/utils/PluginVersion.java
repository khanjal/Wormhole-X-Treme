package com.wormhole_xtreme.wormhole.utils;

import java.lang.reflect.Method;

import org.bukkit.plugin.Plugin;

/**
 * A plugin's version, from Paper's plugin meta where there is one and Bukkit's description
 * otherwise.
 *
 * <p>Paper deprecates {@code getDescription()} for {@code getPluginMeta()}, which Spigot does not
 * have, so the meta is looked up by reflection.
 */
public final class PluginVersion
{
    /** {@code Plugin.getPluginMeta()} and its {@code getVersion()}, or null where the server has none. */
    record Lookup(Method meta, Method version)
    {
    }

    /** Paper's, or whatever a test installed. */
    private static Lookup lookup = paper();

    /** Static use only. */
    private PluginVersion()
    {
    }

    /**
     * @param plugin
     *            whose version
     * @return its version, as its plugin.yml gives it
     */
    public static String of(final Plugin plugin)
    {
        final Lookup found = lookup;
        if (found != null)
        {
            try
            {
                final Object meta = found.meta().invoke(plugin);
                if (meta != null && found.version().invoke(meta) instanceof String version)
                {
                    return version;
                }
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError notPaper)
            {
                // Read from the description instead.
            }
        }
        return described(plugin);
    }

    /**
     * Tests only.
     *
     * @param stand
     *            the meta lookups to use, or null for none; {@link #forget()} restores Paper's
     */
    static void lookupWith(final Lookup stand)
    {
        lookup = stand;
    }

    /** Tests only: goes back to the server's own lookups. */
    static void forget()
    {
        lookup = paper();
    }

    /**
     * @param meta
     *            a method on the plugin answering its meta
     * @return the lookup pair, its version read from the meta's own type, or null if it has none
     */
    static Lookup lookupFrom(final Method meta)
    {
        try
        {
            return new Lookup(meta, meta.getReturnType().getMethod("getVersion"));
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError none)
        {
            return null;
        }
    }

    /** @return Paper's lookups, or null on a server without plugin meta */
    private static Lookup paper()
    {
        try
        {
            return lookupFrom(Plugin.class.getMethod("getPluginMeta"));
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError notPaper)
        {
            return null;
        }
    }

    // Spigot has no plugin meta, and Paper deprecates the description.
    @SuppressWarnings("deprecation")
    private static String described(final Plugin plugin)
    {
        return plugin.getDescription().getVersion();
    }
}
