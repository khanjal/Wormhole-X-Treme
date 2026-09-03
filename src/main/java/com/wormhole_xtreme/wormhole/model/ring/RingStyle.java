package com.wormhole_xtreme.wormhole.model.ring;

/**
 * How a ring stack comes out.
 *
 * <p>Both build the same stack and both come home nearest-first. The only thing that differs
 * is when each ring leaves the plane — concurrently, as soon as there is room behind the one
 * in front; sequentially, not until it has stopped — which is why one enum and one changed
 * number covers both rather than two animations existing side by side.
 *
 * <p>Style belongs to the <em>end</em>, like the materials do, because nobody ever watches
 * both at once: a traveller is standing at one of them, and by the time they can see the
 * other they have already arrived. So a base can deploy differently from the outpost it
 * connects to, and neither is any the worse for it.
 *
 * <p>The two still have to finish together, since the swap needs both stacks up. That is
 * arranged by waiting for the slower of them rather than by forcing them to match — a ring
 * that has arrived holds its place regardless, so the wait costs nothing to draw.
 */
public enum RingStyle
{
    /**
     * Several rings climbing at the same time, one behind another.
     *
     * <p>A ring leaves the plane once the one in front of it is a clear block above, so what
     * rises out of the floor is an evenly spaced column rather than a single ring. They
     * arrive in order, top first, each stopping as it reaches its place. Quicker, and the
     * more common look.
     */
    CONCURRENT("fast", "quick", "flowing"),

    /**
     * One ring at a time, each waiting for the one before it to stop.
     *
     * <p>The first out flies all the way to the furthest position and halts; only then does
     * the next emerge. Slower and more deliberate.
     */
    SEQUENTIAL("slow", "stepped", "staged");

    /** Friendlier words a player may type instead of the enum name. */
    private final java.util.List<String> aliases;

    /**
     * Instantiates a style.
     *
     * @param aliases
     *            other words that mean this style
     */
    RingStyle(final String... aliases)
    {
        this.aliases = java.util.Collections.unmodifiableList(java.util.Arrays.asList(aliases));
    }

    /** @return other words that mean this style */
    public java.util.List<String> getAliases()
    {
        return aliases;
    }

    /**
     * Reads a style from whatever the player typed.
     *
     * <p>Accepts the canonical names and a few friendlier words for each. The stored value
     * stays {@code CONCURRENT} or {@code SEQUENTIAL} because those describe what the setting
     * actually does — how many rings are in the air at once — which stays true whatever
     * {@code rings.deploy-ticks} is set to. Naming it by speed would have it claim the same
     * ground as that setting and be contradicted by it.
     *
     * @param text
     *            what the player typed
     * @return the style, or null if it is not one
     */
    public static RingStyle parse(final String text)
    {
        if (text == null)
        {
            return null;
        }
        final String wanted = text.trim().toLowerCase();
        for (final RingStyle style : values())
        {
            if (style.name().toLowerCase().equals(wanted) || style.aliases.contains(wanted))
            {
                return style;
            }
        }
        return null;
    }
}
