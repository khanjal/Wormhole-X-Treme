package com.wormhole_xtreme.wormhole.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Logs through the plugin when there is one, and through {@code java.util.logging} when there
 * is not.
 *
 * <p>Almost everything here logs through {@code WormholeXTreme.getThisPlugin().prettyLog}, and
 * almost everything therefore has to check first that there is a plugin to log through. Three
 * storage managers each grew their own version of that check, and they did not agree:
 * {@code BeamYamlManager} fell back to {@code java.util.logging}, while {@code RingYamlManager}
 * and {@code StargateYamlManager} dropped the message on the floor.
 *
 * <p>That disagreement is the part that bit. The same failure -- a ring file that would not
 * write, say -- was visible in one manager's tests and invisible in another's, so a test could
 * pass by watching a message that was never going to arrive.
 *
 * <p>Falling back is the behaviour worth keeping. A message that vanishes because a static
 * happened to be null is a message that vanishes exactly when something has already gone
 * wrong.
 */
public final class PluginLog
{
    /** Static helpers only. */
    private PluginLog()
    {
    }

    /**
     * Logs a message.
     *
     * @param level
     *            the severity
     * @param message
     *            what to say
     */
    public static void log(final Level level, final String message)
    {
        log(level, message, null);
    }

    /**
     * Logs a message, with the exception behind it.
     *
     * @param level
     *            the severity
     * @param message
     *            what to say
     * @param thrown
     *            the exception, or null if there is none
     */
    public static void log(final Level level, final String message, final Throwable thrown)
    {
        try
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                if (thrown == null)
                {
                    plugin.prettyLog(level, message);
                }
                else
                {
                    plugin.prettyLog(level, message, thrown);
                }
                return;
            }
        }
        catch (final RuntimeException e)
        {
            // No usable plugin: fall through to java.util.logging below.
        }
        Logger.getLogger(PluginLog.class.getName()).log(level, message, thrown);
    }

    /**
     * Whether a message at this level would actually be logged.
     *
     * <p>For guarding a line whose message costs something to build. {@code prettyLog} takes a
     * {@code String}, so the concatenation at the call site happens whether or not the level is
     * enabled -- on a hot path that is real work thrown away.
     *
     * @param level
     *            the severity being considered
     * @return true if it is worth building the message
     */
    public static boolean isLoggable(final Level level)
    {
        try
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                return plugin.isLoggable(level);
            }
        }
        catch (final RuntimeException e)
        {
            // Fall through to the logger below.
        }
        return Logger.getLogger(PluginLog.class.getName()).isLoggable(level);
    }
}
