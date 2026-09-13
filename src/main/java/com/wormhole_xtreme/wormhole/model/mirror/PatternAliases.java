package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Locale;
import java.util.Map;

/**
 * The seven banner patterns Mojang renamed at 1.21, each spelling mapped to the other.
 *
 * <p>These are not seven patterns a version lacks. They are seven patterns every supported
 * version <em>has</em>, under two different names, and a preset file can only spell them one
 * way. Before this existed the shipped library could use 34 of the 43 patterns -- not because
 * nine were unavailable, but because seven of them had no spelling that worked on both sides of
 * 1.21, and a preset naming either one lost that layer on half the supported range with only a
 * FINE line to say so.
 *
 * <p>Verified by vanilla's own identifiers rather than by how similar the names look, which
 * matters most for the diagonals: 1.20's {@code DIAGONAL_LEFT_MIRROR} carries the identifier
 * {@code lud}, and {@code lud} is what 1.21 calls {@code DIAGONAL_UP_LEFT}. Pairing those two
 * by name alone would have been a guess, and {@code DIAGONAL_LEFT} ({@code ld}) sits right next
 * to it waiting to be picked by mistake.
 *
 * <p>The mapping runs both ways, so a preset may use whichever spelling its author knows and
 * the file keeps working when the server is upgraded under it. Only the two genuinely new
 * patterns -- {@code FLOW} and {@code GUSTER}, added with the trial chambers -- are out of
 * reach on 1.20, and nothing here can help with those.
 */
public final class PatternAliases
{
    /**
     * Both spellings of each renamed pattern, in both directions.
     *
     * <p>Written as pairs rather than one direction plus an inverted copy, because a reader
     * checking this against a wiki page wants to see the two names next to each other.
     */
    private static final Map<String, String> OTHER_SPELLING = Map.ofEntries(
        Map.entry("CIRCLE", "CIRCLE_MIDDLE"),
        Map.entry("CIRCLE_MIDDLE", "CIRCLE"),
        Map.entry("RHOMBUS", "RHOMBUS_MIDDLE"),
        Map.entry("RHOMBUS_MIDDLE", "RHOMBUS"),
        Map.entry("SMALL_STRIPES", "STRIPE_SMALL"),
        Map.entry("STRIPE_SMALL", "SMALL_STRIPES"),
        Map.entry("HALF_HORIZONTAL_BOTTOM", "HALF_HORIZONTAL_MIRROR"),
        Map.entry("HALF_HORIZONTAL_MIRROR", "HALF_HORIZONTAL_BOTTOM"),
        Map.entry("HALF_VERTICAL_RIGHT", "HALF_VERTICAL_MIRROR"),
        Map.entry("HALF_VERTICAL_MIRROR", "HALF_VERTICAL_RIGHT"),
        Map.entry("DIAGONAL_UP_LEFT", "DIAGONAL_LEFT_MIRROR"),
        Map.entry("DIAGONAL_LEFT_MIRROR", "DIAGONAL_UP_LEFT"),
        Map.entry("DIAGONAL_UP_RIGHT", "DIAGONAL_RIGHT_MIRROR"),
        Map.entry("DIAGONAL_RIGHT_MIRROR", "DIAGONAL_UP_RIGHT"));

    /** Static table only. */
    private PatternAliases()
    {
    }

    /**
     * The other name for a pattern, if it has one.
     *
     * @param name
     *            the spelling a preset used, in any case
     * @return the spelling the other side of 1.21 uses, or null if this pattern was never
     *         renamed
     */
    public static String other(final String name)
    {
        return (name == null) ? null : OTHER_SPELLING.get(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Whether a name is one of the fourteen spellings involved.
     *
     * @param name
     *            the name to check
     * @return true if this pattern was renamed at 1.21, under either spelling
     */
    public static boolean isRenamed(final String name)
    {
        return other(name) != null;
    }
}
