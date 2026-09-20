package com.wormhole_xtreme.wormhole.plugin;

import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * PlaceholderAPI integration support.
 *
 * <p>Safe to call whether or not PlaceholderAPI is installed: it looks for it once at enable,
 * says once what it found, and otherwise does nothing. A server without PlaceholderAPI still
 * gets its gates.
 *
 * <p>Unlike {@link EconomySupport}, which reaches Vault entirely by name, this one has a
 * compile-time dependency, because an expansion has to <em>extend</em> PlaceholderAPI's own
 * type rather than merely call it. That makes the ordering here load-bearing:
 * {@link WormholePlaceholders} is named only after PlaceholderAPI has been found, so on a
 * server without it the class is never loaded and its missing supertype never matters.
 */
public final class PlaceholderSupport
{
    /** Whether the expansion is registered, so a second enable does not register it twice. */
    private static volatile boolean registered = false;

    /** Static helpers only. */
    private PlaceholderSupport()
    {
    }

    /**
     * Attempts to register the expansion.
     *
     * <p>Called from {@code WormholeXTreme.onEnable} when the config asks for it.
     */
    public static void enablePlaceholders()
    {
        if (registered)
        {
            return;
        }
        try
        {
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
        }
        catch (final ClassNotFoundException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Placeholders enabled in config but PlaceholderAPI was not found."
                    + " Placeholders disabled.");
            return;
        }
        try
        {
            // Only reached with PlaceholderAPI on the classpath, which is what makes naming
            // the expansion class here safe. Hoisting this above the check would load it on
            // every server and fail on the ones without.
            registered = new WormholePlaceholders().register();
            if (registered)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
                    "Registered PlaceholderAPI expansion: %wormhole_...%");
            }
            else
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "PlaceholderAPI refused the wormhole expansion. Placeholders disabled.");
            }
        }
        catch (final Exception | LinkageError t)
        {
            // LinkageError as well as Exception: a PlaceholderAPI old or new enough to have
            // moved the expansion type fails here rather than at Class.forName, and a
            // placeholder that cannot register is not a reason to fail the whole plugin.
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to register the PlaceholderAPI expansion", t);
        }
    }

    /**
     * Whether the expansion is registered.
     *
     * @return true if placeholders are being answered
     */
    public static boolean isRegistered()
    {
        return registered;
    }

    /**
     * Forgets the registration, so a reload can register again.
     *
     * <p>Called on disable. PlaceholderAPI drops its own expansions when this plugin unloads;
     * this only clears the flag that stops a second registration.
     */
    public static void reset()
    {
        registered = false;
    }
}
