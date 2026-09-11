package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Locale;

/**
 * Where a mirror's look comes from, and whether it is allowed to change.
 */
public enum MirrorMode
{
    /** Stamped once and left alone. What an operator chose stays chosen. */
    STATIC,

    /**
     * Re-read from the far side when somebody comes to look at it.
     *
     * <p>Throttled, and only on approach. Re-sampling means loading a distant chunk, which is
     * why this never happens on a timer for its own sake -- a mirror nobody is standing in
     * front of costs nothing.
     */
    DYNAMIC;

    /**
     * Reads one of these by name, however it was capitalised.
     *
     * @param value
     *            what somebody typed or what the file said
     * @return the value, or null if it is not one
     */
    public static MirrorMode of(final String value)
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
