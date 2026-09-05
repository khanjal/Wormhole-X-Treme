package com.wormhole_xtreme.wormhole.utils;

import org.bukkit.ChatColor;

/**
 * Turns a configured colour name into sign text.
 *
 * <p>Colours are named in config.yml rather than written as raw section-sign codes, following
 * the same reasoning {@code Sounds} already uses for sound names: a name is something an admin
 * can read back and check, and a name nobody recognises degrades to a sensible default instead
 * of putting a stray control character on a sign where nothing can be done about it.
 *
 * <p>Everything here is a pure function of its arguments so the decisions can be tested
 * without a server. The one thing worth knowing about the format: a section-sign code colours
 * the rest of the line, so each line is painted on its own and nothing needs resetting.
 */
public final class SignStyle
{
    /** Marks a line as deliberately blank, so callers do not paint an empty string. */
    private static final String EMPTY = "";

    private SignStyle()
    {
    }

    /**
     * Resolves a configured colour name, falling back when it names nothing.
     *
     * <p>Formatting codes that are not colours -- {@code BOLD}, {@code MAGIC} and the rest --
     * are refused rather than accepted, because they do not colour anything and a sign styled
     * with {@code MAGIC} is unreadable by design. An admin who typed one gets the default and
     * a readable sign.
     *
     * @param name
     *            the configured name, case-insensitive, may be null
     * @param fallback
     *            what to use when the name is empty, unknown, or not a colour
     * @return a usable colour, never null
     */
    public static ChatColor resolveColor(final String name, final ChatColor fallback)
    {
        if ((name == null) || name.trim().isEmpty())
        {
            return fallback;
        }
        try
        {
            final ChatColor found = ChatColor.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            return found.isColor() ? found : fallback;
        }
        catch (final IllegalArgumentException notAColorName)
        {
            return fallback;
        }
    }

    /**
     * Paints a line, leaving an empty one empty.
     *
     * <p>An empty line matters: a sign line holding only a colour code looks blank and is not,
     * and the dial sign writes blank lines deliberately to centre its selection.
     *
     * @param color
     *            the colour to paint with
     * @param text
     *            the line text, may be null
     * @return the line ready to write to a sign
     */
    public static String paint(final ChatColor color, final String text)
    {
        if ((text == null) || text.isEmpty())
        {
            return EMPTY;
        }
        return color.toString() + text;
    }

    /**
     * Strips formatting from a line read back off a sign.
     *
     * <p>Detection reads line 0 of the dial sign as the gate's name, and the plugin writes
     * that same line itself. Without this, a gate re-detected after the plugin had styled its
     * sign would take the colour codes into its own name -- invisible characters in a name
     * that has to be typed to dial it.
     *
     * @param line
     *            the raw line read from the sign, may be null
     * @return the line with formatting removed, never null
     */
    public static String stripFormatting(final String line)
    {
        if (line == null)
        {
            return EMPTY;
        }
        final String stripped = ChatColor.stripColor(line);
        return stripped != null ? stripped : EMPTY;
    }
}
