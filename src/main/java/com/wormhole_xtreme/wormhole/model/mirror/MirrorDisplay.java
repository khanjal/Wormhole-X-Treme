package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Locale;

/**
 * When a mirror shows its look.
 *
 * <p>Separate from {@link MirrorMode} on purpose: this is about when the look is visible, that
 * is about where the look comes from. A proximity mirror can be static or dynamic and an always
 * mirror can be either too, so folding them into one setting would make four values where two
 * questions read more clearly.
 */
public enum MirrorDisplay
{
    /** Nothing is hidden from anybody: the stamped banner is what every player sees. */
    ALWAYS,

    /**
     * Players too far away are sent a blank copy, and get the real banner back on approach.
     *
     * <p>That way round, not the other. The banner in the world stays stamped -- patterns are
     * vanilla data and outlive this plugin -- so the blank is the illusion and the stamped
     * banner is the truth. Disabling the plugin leaves the corridor an operator built, rather
     * than a row of plain cloth.
     *
     * <p>Needs {@code Player.sendBlockUpdate}, which plain 1.20 does not have -- on that one
     * version a proximity mirror behaves as {@link #ALWAYS} rather than never showing anything.
     */
    PROXIMITY;

    /**
     * Reads one of these by name, however it was capitalised.
     *
     * @param value
     *            what somebody typed or what the file said
     * @return the value, or null if it is not one
     */
    public static MirrorDisplay of(final String value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (final IllegalArgumentException notOne)
        {
            return null;
        }
    }

    /** @return the name as a person would type it */
    public String lower()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
