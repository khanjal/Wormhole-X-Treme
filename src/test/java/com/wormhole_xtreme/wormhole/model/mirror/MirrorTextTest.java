package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

/**
 * The colouring mirror messages are built from.
 *
 * <p>Almost everything here is one rule: a fragment ends in the body colour. That is the bug
 * this class exists to stop, and it is a bad one to find by eye -- a fragment that forgets to
 * change back compiles, sends, and looks perfectly correct until the day somebody puts another
 * word after it, at which point a sentence three screens away turns aqua.
 *
 * <p>The dye table gets its own attention because it is the one place a Minecraft version could
 * break this. An exhaustive enum switch compiled against one version throws on a later one that
 * added a constant, so the table carries a default and this asserts that every dye the compiled
 * enum knows about goes through it without throwing.
 */
class MirrorTextTest
{
    /**
     * A name is coloured and hands the sentence back.
     *
     * <p>Both halves matter. Without the first the colour never arrives; without the second it
     * never leaves.
     */
    @Test
    void aNameIsColouredAndEndsBackInTheBodyColour()
    {
        final String painted = MirrorText.name("museum");

        assertTrue(painted.startsWith(MirrorText.NAME), "the name should start in its colour");
        assertTrue(painted.contains("museum"), "the name itself should survive");
        assertTrue(painted.endsWith(MirrorText.BODY), "and hand the sentence back");
    }

    /** The quotes stay, because the server console has no colour to read instead. */
    @Test
    void aQuotedNameKeepsItsQuotes()
    {
        final String painted = MirrorText.quoted("museum");

        assertTrue(painted.startsWith("'"), "the opening quote is outside the colour");
        assertTrue(painted.endsWith("'"), "and so is the closing one");
        assertTrue(painted.contains(MirrorText.NAME + "museum"), "with the name coloured inside");
    }

    /** A command is meant to be typed, so it is painted as one thing. */
    @Test
    void aCommandIsColouredAndEndsBackInTheBodyColour()
    {
        final String painted = MirrorText.command("/wormhole mirror list");

        assertTrue(painted.startsWith(MirrorText.COMMAND), "a command opens in the command colour");
        assertTrue(painted.endsWith(MirrorText.BODY), "and hands the sentence back");
    }

    /**
     * A command with a name in it keeps the two apart.
     *
     * <p>The verb is fixed and the name is the part a reader has to check against their own
     * mirror, so painting them the same colour would hide the only word that varies.
     */
    @Test
    void aCommandEndingInANameColoursTheNameSeparately()
    {
        final String painted = MirrorText.command("/wormhole mirror link", "museum");

        assertTrue(painted.startsWith(MirrorText.COMMAND + "/wormhole mirror link "),
            "the verb should be in the command colour");
        assertTrue(painted.contains(MirrorText.NAME + "museum"),
            "and the name in the name colour");
        assertTrue(painted.endsWith(MirrorText.BODY), "and the sentence handed back");
    }

    /**
     * A list of names leaves its commas alone.
     *
     * <p>Colouring the separators along with the names turns three choices into one long aqua
     * smear, which is the opposite of what a list of choices is for.
     */
    @Test
    void aListOfNamesLeavesTheSeparatorsInTheBodyColour()
    {
        final String painted = MirrorText.names(new String[] { "nether", "cavern" });

        assertEquals(MirrorText.NAME + "nether" + MirrorText.BODY + ", "
            + MirrorText.NAME + "cavern" + MirrorText.BODY, painted);
    }

    /** One name is still a list, and must not grow a separator. */
    @Test
    void aListOfOneNameHasNoSeparator()
    {
        assertFalse(MirrorText.names(new String[] { "nether" }).contains(","),
            "a single choice should not read as though something follows it");
    }

    /**
     * The approach line carries the plugin's own header.
     *
     * <p>Alone among the fragments here, because this one does not go through the
     * {@code sendMessage} path that prefixes it. On a server running several plugins, a line
     * that appears above the hotbar with nothing to say who said it is a line the player
     * cannot act on -- they do not know what to look at.
     */
    @Test
    void theApproachLineSaysWhichPluginIsTalking()
    {
        assertTrue(MirrorText.approach("museum", "nether").startsWith("§3:: "),
            "the action bar has no header of its own, so this line has to carry one");
    }

    /** It names both halves: which mirror, and the one thing you cannot see from in front. */
    @Test
    void theApproachLineNamesTheMirrorAndWhereItGoes()
    {
        final String line = MirrorText.approach("museum", "nether");

        assertTrue(line.contains(MirrorText.NAME + "museum"), "the mirror, coloured: " + line);
        assertTrue(line.contains(MirrorText.NAME + "nether"), "and the far world: " + line);
    }

    /** The word is what carries the meaning, so it has to be in there whatever the colour. */
    @Test
    void aDyeIsWrittenOutAsWellAsColoured()
    {
        final String painted = MirrorText.dye(DyeColor.RED);

        assertTrue(painted.contains("red"), "the word itself is what a reader actually reads");
        assertTrue(painted.endsWith(MirrorText.BODY), "and the sentence is handed back");
    }

    /** Underscores are a constant name, not something to say out loud. */
    @Test
    void aTwoWordDyeReadsAsTwoWords()
    {
        assertTrue(MirrorText.dye(DyeColor.LIGHT_BLUE).contains("light blue"),
            "LIGHT_BLUE is a constant; 'light blue' is what a person says");
    }

    /**
     * Black is written in dark grey on purpose.
     *
     * <p>The one deliberate lie in the table. True black on a chat background is a word nobody
     * can read, and a colour name you cannot see is worse than a slightly wrong colour.
     */
    @Test
    void blackIsWrittenInSomethingYouCanActuallyRead()
    {
        assertFalse(MirrorText.dye(DyeColor.BLACK).startsWith("§0"),
            "true black would be invisible in chat");
        assertTrue(MirrorText.dye(DyeColor.BLACK).contains("black"), "but it still says black");
    }

    /**
     * Every dye this server knows about survives the table.
     *
     * <p>Written as a loop over the live enum rather than as sixteen cases, so that a Minecraft
     * version which adds a dye is caught here rather than by a player watching a banner message
     * throw. That is not hypothetical in this plugin: the same shape of assumption about an
     * enum has broken it before.
     */
    @Test
    void everyDyeTheServerHasGetsAColourAndAWord()
    {
        for (final DyeColor colour : DyeColor.values())
        {
            final String painted = MirrorText.dye(colour);

            assertTrue(painted.endsWith(MirrorText.BODY),
                colour + " should hand the sentence back");
            assertTrue(painted.contains(colour.name().toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ')), colour + " should still say its own name");
        }
    }
}
