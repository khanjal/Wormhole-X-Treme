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
    /** The look is in the block, and everyone sees it from wherever they are. */
    ALWAYS,

    /**
     * The block is blank and the look is sent to whoever comes close.
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
