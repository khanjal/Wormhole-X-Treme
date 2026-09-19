package com.wormhole_xtreme.wormhole.logic;

import java.util.Locale;

/**
 * How the inner ring's light moves while a gate dials ({@code gate-dial-spin}).
 *
 * <p>Every pattern draws within the chevron's own interval, so none changes how fast a gate dials.
 */
public enum DialSpinPattern
{
    /** Half the ring, landing on the chevron about to lock, alternating direction. */
    CHEVRON,

    /** Half the ring to the top chevron, alternating direction, as 1.7's first try did. */
    TOP,

    /** A whole turn clockwise every glyph, landing on the chevron about to lock: round and round. */
    LAP,

    /** Half the ring to the chevron, filling in behind the light rather than a short run. */
    FILL,

    /**
     * As an Atlantis gate dials: from the chevron last locked (the top, for the first glyph) to the
     * next, anticlockwise first and alternating after, a glyph's width at a time.
     */
    PEGASUS,

    /** No ring light; chevrons lock in order alone. */
    NONE;

    /**
     * Reads a pattern by name, whatever its capitals. {@code true} and {@code false} are the
     * setting's values before it had patterns, and mean the default {@link #TOP} and {@link #NONE}; {@code off} is {@link #NONE} too.
     *
     * @param raw
     *            the value as written
     * @return the pattern, or null if the value names none
     */
    public static DialSpinPattern parse(final String raw)
    {
        if (raw == null)
        {
            return null;
        }
        final String name = raw.trim().toUpperCase(Locale.ROOT);
        if ("TRUE".equals(name))
        {
            return TOP;
        }
        if ("FALSE".equals(name) || "OFF".equals(name))
        {
            return NONE;
        }
        for (final DialSpinPattern pattern : values())
        {
            if (pattern.name().equals(name))
            {
                return pattern;
            }
        }
        return null;
    }
}
