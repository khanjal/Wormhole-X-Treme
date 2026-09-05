package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * What {@code /wormhole config <setting> <value>} accepts, and what it now refuses.
 *
 * <p>The bug these guard against was a silent one, and it looked like a success.
 * {@code /wormhole config ring_default_style banana} answered "RING_DEFAULT_STYLE is now
 * banana." and wrote that word to config.yml. Nothing was wrong with the write; the problem
 * was everything afterwards, because {@code getRingDefaultStyle()} parses the stored text and
 * substitutes {@code CONCURRENT} when it cannot. So the setting silently became the default,
 * for as long as nobody thought to look, and the one message that could have said so said the
 * opposite.
 *
 * <p>Every rule lives in {@link ParsedSetting} as a pure function precisely so it can be
 * asserted here: the refusals are the whole point of the change, and a refusal is not
 * something a test can see through {@code ConfigManager.applySetting}, which writes
 * config.yml on the way past.
 */
public class ParsedSettingTest
{
    /** Reads a value the way the config command does, against a text setting. */
    private static ParsedSetting text(final ConfigKeys key, final String typed)
    {
        return ParsedSetting.read(key, "", typed);
    }

    /**
     * Asserts a value was accepted, and stored as the given canonical form.
     *
     * @param key
     *            the setting written
     * @param typed
     *            what was typed
     * @param stored
     *            what should end up in config.yml
     */
    private static void storesAs(final ConfigKeys key, final String typed, final String stored)
    {
        final ParsedSetting parsed = text(key, typed);
        assertTrue(parsed.isAccepted(), key + " refused \"" + typed + "\": " + parsed.getRefusal());
        assertEquals(stored, parsed.getValue(),
            "\"" + typed + "\" has to be stored as " + stored + ", because the getter that "
                + "reads it back parses the stored text and falls back silently when it cannot");
    }

    /**
     * Asserts a value was refused, with a message naming both the options and the mistake.
     *
     * @param key
     *            the setting written
     * @param typed
     *            what was typed
     * @return the refusal, for a caller that wants to check what it says
     */
    private static ParsedSetting refuses(final ConfigKeys key, final String typed)
    {
        final ParsedSetting parsed = text(key, typed);
        assertFalse(parsed.isAccepted(),
            key + " accepted \"" + typed + "\", which the getter cannot read back -- the "
                + "setting would silently be whatever that getter falls back to");
        assertTrue(parsed.getRefusal().contains(typed),
            "a refusal has to quote what was typed, so a typo is visible: "
                + parsed.getRefusal());
        return parsed;
    }

    @Test
    public void aRingStyleThatIsNotAStyleIsRefusedRatherThanStored()
    {
        // The reported case, verbatim. This used to answer "RING_DEFAULT_STYLE is now banana."
        final ParsedSetting parsed = refuses(ConfigKeys.RING_DEFAULT_STYLE, "banana");
        assertTrue(parsed.getRefusal().contains("CONCURRENT")
            && parsed.getRefusal().contains("SEQUENTIAL"),
            "the refusal has to name the valid styles, or the next guess is as blind as the "
                + "first: " + parsed.getRefusal());
    }

    @Test
    public void theFriendlierWordForASlowRingIsStoredAsTheStyleItMeans()
    {
        // The trap in accepting aliases at all. RingStyle.parse takes "slow", but
        // getRingDefaultStyle reads the stored text back with valueOf -- so storing the word
        // as typed would leave SEQUENTIAL silently meaning CONCURRENT, which is the very bug
        // this change exists to stop, reintroduced by the fix for it.
        storesAs(ConfigKeys.RING_DEFAULT_STYLE, "slow", "SEQUENTIAL");
        storesAs(ConfigKeys.RING_DEFAULT_STYLE, "fast", "CONCURRENT");
    }

    @Test
    public void aRingStyleTypedInAnyCaseIsStoredInTheCaseTheGetterReads()
    {
        storesAs(ConfigKeys.RING_DEFAULT_STYLE, "sequential", "SEQUENTIAL");
        storesAs(ConfigKeys.RING_DEFAULT_STYLE, "  Concurrent  ", "CONCURRENT");
    }

    @Test
    public void aRingAccessThatIsNotPublicOrPrivateIsRefused()
    {
        // Worse than the style case if it had gone the other way: the getter falls back to
        // PRIVATE, so a server owner who meant to publish rings and mistyped it would have
        // been told they had.
        refuses(ConfigKeys.RING_DEFAULT_ACCESS, "banana");
        storesAs(ConfigKeys.RING_DEFAULT_ACCESS, "public", "PUBLIC");
        storesAs(ConfigKeys.RING_DEFAULT_ACCESS, "PRIVATE", "PRIVATE");
    }

    @Test
    public void aRingMaterialThatIsNotASlabIsRefused()
    {
        // The same rule /wormhole ring edit ring already applies: the rise is drawn out of
        // slab halves, and a full block costs the animation its half-block movement.
        refuses(ConfigKeys.RING_DEFAULT_MATERIAL, "stone");
        refuses(ConfigKeys.RING_DEFAULT_MATERIAL, "banana");
        storesAs(ConfigKeys.RING_DEFAULT_MATERIAL, "smooth_stone_slab", "SMOOTH_STONE_SLAB");
    }

    @Test
    public void aRingLightThatIsNotAMaterialAtAllIsRefused()
    {
        // Only the "no such material" half is asserted, deliberately. Whether a material is a
        // block is answered by the server's registry from 1.20.6 on, and there is no registry
        // in a unit test -- MaterialUtils.isBlockOrUnknown accepts rather than refuses when it
        // cannot ask, so an item name would pass here while being refused on a live server.
        // CI runs this suite against every version in the supported range, so a test that
        // assumed otherwise would pass on 1.20.4 and fail on the six others.
        refuses(ConfigKeys.RING_DEFAULT_LIGHT, "banana");
        refuses(ConfigKeys.RING_DEFAULT_FLASH, "banana");
        storesAs(ConfigKeys.RING_DEFAULT_LIGHT, "glowstone", "GLOWSTONE");
        storesAs(ConfigKeys.RING_DEFAULT_FLASH, "sea_lantern", "SEA_LANTERN");
    }

    @Test
    public void aLogLevelIsCheckedAndStoredInTheCaseThatCanBeReadBack()
    {
        // The one setting whose getter does not catch its own parse failure: Setting.getLevel
        // calls Level.parse straight, and Level.parse is case-sensitive. So "fine" was written
        // happily and then threw at every log call afterwards -- inside logging, which is a
        // poor place to be the first to notice.
        refuses(ConfigKeys.LOG_LEVEL, "banana");
        storesAs(ConfigKeys.LOG_LEVEL, "fine", "FINE");
        storesAs(ConfigKeys.LOG_LEVEL, "INFO", "INFO");
    }

    @Test
    public void aLogLevelIsFoldedAsAsciiRatherThanInTheServerOwnersLanguage()
    {
        // Caught in review on PR #36, not by me. A Turkish JVM upper-cases "i" to a dotted
        // capital I (U+0130) rather than to "I", so the first version of this -- a plain
        // toUpperCase() -- handed Level.parse a character it has never heard of and refused
        // "fine" on Turkish servers alone. Level names are fixed ASCII; the fold has to be
        // too. The default locale is JVM-wide state, so it is put back before leaving.
        final java.util.Locale before = java.util.Locale.getDefault();
        try
        {
            java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
            storesAs(ConfigKeys.LOG_LEVEL, "fine", "FINE");
            storesAs(ConfigKeys.LOG_LEVEL, "info", "INFO");
            storesAs(ConfigKeys.LOG_LEVEL, "finest", "FINEST");
        }
        finally
        {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    public void aSoundNameIsStillWhateverTheServerOwnerTyped()
    {
        // Deliberately not validated, and this is the guard against somebody "tidying" that
        // up later. The whole point of naming a sound instead of resolving it to a Sound
        // constant is that a resource pack's own sound -- a name this plugin cannot know --
        // still has to pass through.
        storesAs(ConfigKeys.GATE_SOUND_ACTIVATE, "mypack.stargate.kawoosh",
            "mypack.stargate.kawoosh");
        storesAs(ConfigKeys.RING_SOUND_OPEN, "block.beacon.activate", "block.beacon.activate");
    }

    @Test
    public void booleansAndNumbersAreCheckedTheWayTheyAlwaysWere()
    {
        // These worked before this change and have to go on working: the refusals below are
        // the shape every message above was written to match.
        assertTrue(ParsedSetting.read(ConfigKeys.SAME_WORLD_ONLY, Boolean.FALSE, "true")
            .isAccepted());
        assertFalse(ParsedSetting.read(ConfigKeys.SAME_WORLD_ONLY, Boolean.FALSE, "banana")
            .isAccepted());
        assertEquals(Integer.valueOf(40),
            ParsedSetting.read(ConfigKeys.RING_OUTLINE_TICKS, Integer.valueOf(20), " 40 ")
                .getValue());
        assertFalse(ParsedSetting.read(ConfigKeys.RING_OUTLINE_TICKS, Integer.valueOf(20), "soon")
            .isAccepted());
        assertEquals(Double.valueOf(2.0),
            ParsedSetting.read(ConfigKeys.GATE_SOUND_VOLUME, Double.valueOf(1.5), "2.0")
                .getValue());
        assertFalse(ParsedSetting.read(ConfigKeys.GATE_SOUND_VOLUME, Double.valueOf(1.5), "loud")
            .isAccepted());
    }
}
