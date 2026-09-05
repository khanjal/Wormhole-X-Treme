package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.ring.RingStyle;

/**
 * Reading an identifier out of a file or a command does not depend on the server's language.
 *
 * <p>Same mechanism as {@code CommandLookupLocaleTest}, one layer further in: these take text
 * that has already been accepted -- a material name written in a {@code .shape} file, a style
 * word typed at {@code /wormhole ring edit} -- and fold it before handing it to
 * {@code valueOf} or comparing it against an enum constant. On a Turkish server the fold
 * introduces a dotted or dotless i that no enum constant has, and the value is refused.
 *
 * <p>The shape-file case is the quietest failure in the sweep. {@code StargateShape} resolves
 * a {@code SIGN_MATERIAL} line inside a catch block that ignores unknown names, so a rejected
 * name is not logged, not reported, and not visible anywhere: the shape simply falls back to
 * its default sign material as though the line had never been written.
 */
public class IdentifierParsingLocaleTest
{
    /** The locale to put back, since this is JVM-wide state. */
    private Locale before;

    @BeforeEach
    public void speakTurkish()
    {
        before = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @AfterEach
    public void speakWhateverWeDidBefore()
    {
        Locale.setDefault(before);
    }

    @Test
    public void aMaterialNamedInAShapeFileStillResolvesOnATurkishServer()
    {
        // A shape file writes its materials in the game's own spelling. Upper-casing "ice"
        // in Turkish produces a dotted capital I, which is not the I in Material.ICE.
        assertSame(Material.ICE, Stargate3DShape.parseMaterialName("ice"),
            "a shape file naming \"ice\" resolved to nothing on a Turkish server");
        assertSame(Material.IRON_BLOCK, Stargate3DShape.parseMaterialName("iron_block"),
            "a shape file naming \"iron_block\" resolved to nothing on a Turkish server");
        assertSame(Material.WATER, Stargate3DShape.parseMaterialName("stationary_water"),
            "the pre-1.13 alias still has to survive the same fold");
    }

    @Test
    public void aRingStyleTypedInLowerCaseIsStillUnderstood()
    {
        // This one breaks in the direction nobody expects. RingStyle.parse folds *both* the
        // typed text and the enum constant's own name, so "SEQUENTIAL" typed in upper case
        // keeps matching -- both sides mangle identically. Ordinary lower-case "sequential"
        // is what stops working, because only the constant's name changes under the fold.
        assertSame(RingStyle.SEQUENTIAL, RingStyle.parse("sequential"),
            "\"sequential\" was not a ring style on a Turkish server, so /wormhole ring edit "
                + "refused the value a player would most naturally type");
        assertSame(RingStyle.SEQUENTIAL, RingStyle.parse("SEQUENTIAL"),
            "\"SEQUENTIAL\" was not a ring style");
        assertSame(RingStyle.SEQUENTIAL, RingStyle.parse("SLOW"),
            "an alias typed in upper case was not recognised");
        assertSame(RingStyle.CONCURRENT, RingStyle.parse("concurrent"),
            "\"concurrent\" was not a ring style");
    }

    @Test
    public void aDiscoveredGroupKeepsItsAsciiSpelling()
    {
        // suggestGroupName titles a Material's own name for display, and that name then keys
        // the registry. Folded in Turkish, "DIAMOND_BLOCK" came out "Dıamond" -- a name an
        // admin cannot type and config.yml should never contain.
        assertEquals("Diamond", MaterialGroupRegistry.suggestGroupName(Material.DIAMOND_BLOCK),
            "a discovered material group was named with a dotless i on a Turkish server");
        // "BLOCK" is dropped on the way past, so this is one part, not two.
        assertEquals("Iron", MaterialGroupRegistry.suggestGroupName(Material.IRON_BLOCK),
            "a discovered material group was named with a dotless i on a Turkish server");
    }
}
