package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Colour names on signs behave the way sound names already do: readable, and forgiving.
 *
 * <p>Sign colours are configured by name rather than as raw section-sign codes, so an admin
 * can read the setting back and check it. That only works if a name nobody recognises degrades
 * to something sensible -- a typo must not put a stray control character on a sign, where
 * there is nothing anyone can do about it short of breaking the gate.
 */
class SignStyleTest
{
    @Test
    void aColourNameResolvesToThatColour()
    {
        assertEquals(ChatColour.AQUA, SignStyle.resolveColor("AQUA", ChatColour.WHITE));
    }

    @Test
    void theNameIsReadWhateverCaseAndSpacingItWasTypedIn()
    {
        assertEquals(ChatColour.DARK_GRAY, SignStyle.resolveColor("  dark_gray ", ChatColour.WHITE),
            "an admin typing a setting by hand should not have to match case exactly");
    }

    @Test
    void anUnknownNameFallsBackRatherThanBreakingTheSign()
    {
        assertEquals(ChatColour.GREEN, SignStyle.resolveColor("chartreuse", ChatColour.GREEN));
    }

    @Test
    void anUnsetNameFallsBack()
    {
        assertEquals(ChatColour.GRAY, SignStyle.resolveColor(null, ChatColour.GRAY));
        assertEquals(ChatColour.GRAY, SignStyle.resolveColor("   ", ChatColour.GRAY));
    }

    /**
     * A formatting code that is not a colour is refused.
     *
     * <p>Bukkit's {@code ChatColor} held both colours and formats, so {@code valueOf} happily
     * returned MAGIC for anyone who typed it -- and MAGIC renders as scrambling nonsense
     * characters, which on a gate's dial sign means the destination cannot be read at all.
     * Falling back gives a readable sign instead of an unusable one.
     */
    @Test
    void aFormattingCodeThatIsNotAColourIsRefused()
    {
        assertEquals(ChatColour.AQUA, SignStyle.resolveColor("MAGIC", ChatColour.AQUA),
            "MAGIC is a format, not a colour, and would render a destination unreadable");
        assertEquals(ChatColour.AQUA, SignStyle.resolveColor("BOLD", ChatColour.AQUA));
    }

    @Test
    void paintingPutsTheColourInFrontOfTheText()
    {
        assertEquals(ChatColour.AQUA + "Helios", SignStyle.paint(ChatColour.AQUA, "Helios"));
    }

    /**
     * A blank line stays blank rather than becoming a lone colour code.
     *
     * <p>The dial sign writes empty lines deliberately, to centre the selected destination
     * between its neighbours. A line holding only a colour code looks blank and is not, which
     * matters because detection and the sign's own line handling both read these back.
     */
    @Test
    void ablankLineIsLeftBlankAndNotPaintedIntoAStrayCode()
    {
        assertEquals("", SignStyle.paint(ChatColour.AQUA, ""));
        assertEquals("", SignStyle.paint(ChatColour.AQUA, null));
    }

    /**
     * Formatting is stripped from a line read back off a sign.
     *
     * <p>This is the one that matters beyond looks. Detection reads line 0 of the dial sign as
     * the gate's name, and the plugin writes that same line itself once the gate is running.
     * Without stripping, re-detecting a styled gate would take the colour codes into its name
     * -- invisible characters in a name that has to be typed to dial it.
     */
    @Test
    void aStyledLineReadsBackAsThePlainNameItShows()
    {
        assertEquals("-Helios-",
            SignStyle.stripFormatting(ChatColour.AQUA + "-Helios-"),
            "a re-detected gate must not take colour codes into its own name");
    }

    @Test
    void strippingAnAbsentLineGivesEmptyRatherThanNull()
    {
        assertEquals("", SignStyle.stripFormatting(null));
    }

    @Test
    void anUnstyledLineIsUnchangedByStripping()
    {
        assertEquals("Helios", SignStyle.stripFormatting("Helios"));
    }
}
