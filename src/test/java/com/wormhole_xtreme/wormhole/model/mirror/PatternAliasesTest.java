package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The two spellings of the seven patterns Mojang renamed at 1.21.
 *
 * <p>What this guards is a silent half-failure. A preset naming {@code CIRCLE} on a 1.20 server
 * does not fail to load, and does not fail to stamp -- it loses that one layer, logs a line at
 * FINE, and produces a banner that is wrong in a way nobody connects to a version. The alias is
 * what makes one spelling work everywhere; these tests are what keep the pairs correct.
 *
 * <p>The pairings were taken from vanilla's own identifiers rather than from how alike the names
 * look, which matters most for the diagonals: 1.20's {@code DIAGONAL_LEFT_MIRROR} carries the id
 * {@code lud}, and {@code lud} is what 1.21 spells {@code DIAGONAL_UP_LEFT}. {@code
 * DIAGONAL_LEFT} is a different pattern ({@code ld}) sitting one letter away.
 */
class PatternAliasesTest
{
    /** Each rename, newest spelling first. */
    private static final List<String[]> PAIRS = List.of(
        new String[] { "CIRCLE", "CIRCLE_MIDDLE" },
        new String[] { "RHOMBUS", "RHOMBUS_MIDDLE" },
        new String[] { "SMALL_STRIPES", "STRIPE_SMALL" },
        new String[] { "HALF_HORIZONTAL_BOTTOM", "HALF_HORIZONTAL_MIRROR" },
        new String[] { "HALF_VERTICAL_RIGHT", "HALF_VERTICAL_MIRROR" },
        new String[] { "DIAGONAL_UP_LEFT", "DIAGONAL_LEFT_MIRROR" },
        new String[] { "DIAGONAL_UP_RIGHT", "DIAGONAL_RIGHT_MIRROR" });

    /** Every pair maps both ways, so a preset may use whichever spelling its author knows. */
    @Test
    void everyRenamedPatternMapsBothWays()
    {
        for (final String[] pair : PAIRS)
        {
            assertEquals(pair[1], PatternAliases.other(pair[0]),
                pair[0] + " should map to its 1.20 spelling");
            assertEquals(pair[0], PatternAliases.other(pair[1]),
                pair[1] + " should map to its 1.21 spelling");
        }
    }

    /**
     * The diagonals do not map to the ones they most resemble.
     *
     * <p>The mistake this is here to catch. {@code DIAGONAL_LEFT} and {@code DIAGONAL_RIGHT}
     * exist unchanged on both versions and are not renames of anything; pairing either of them
     * with an up-diagonal would quietly draw the wrong half of the banner on one version and
     * the right half on the other.
     */
    @Test
    void theUnchangedDiagonalsAreNotAliasesOfAnything()
    {
        assertNull(PatternAliases.other("DIAGONAL_LEFT"));
        assertNull(PatternAliases.other("DIAGONAL_RIGHT"));
        assertFalse(PatternAliases.isRenamed("DIAGONAL_LEFT"));
        assertFalse(PatternAliases.isRenamed("DIAGONAL_RIGHT"));
    }

    /** The patterns that were never renamed have no other spelling to try. */
    @Test
    void aPatternThatWasNeverRenamedHasNoAlias()
    {
        assertNull(PatternAliases.other("BORDER"));
        assertNull(PatternAliases.other("CREEPER"));
        assertNull(PatternAliases.other("GRADIENT_UP"));
        assertFalse(PatternAliases.isRenamed("STRIPE_MIDDLE"));
    }

    /**
     * The two genuinely new patterns are not aliased to anything.
     *
     * <p>No table can recover artwork 1.20 does not have. Mapping them to something that looks
     * close would be worse than leaving them unavailable: the banner would stamp, and it would
     * be a different pattern than the file asked for.
     */
    @Test
    void theTrialChamberPatternsHaveNoOlderSpelling()
    {
        assertNull(PatternAliases.other("FLOW"));
        assertNull(PatternAliases.other("GUSTER"));
    }

    /** Case is not the operator's problem, the same way every other name in a preset is not. */
    @Test
    void spellingIsMatchedWithoutRegardToCase()
    {
        assertEquals("CIRCLE_MIDDLE", PatternAliases.other("circle"));
        assertEquals("CIRCLE_MIDDLE", PatternAliases.other("Circle"));
        assertTrue(PatternAliases.isRenamed("rhombus_middle"));
    }

    /** Nothing to look up. */
    @Test
    void aMissingNameHasNoAlias()
    {
        assertNull(PatternAliases.other(null));
        assertNull(PatternAliases.other(""));
        assertNull(PatternAliases.other("NOT_A_PATTERN"));
        assertFalse(PatternAliases.isRenamed(null));
    }
}
