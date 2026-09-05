package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.ChatColor;
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
        assertEquals(ChatColor.AQUA, SignStyle.resolveColor("AQUA", ChatColor.WHITE));
    }

    @Test
    void theNameIsReadWhateverCaseAndSpacingItWasTypedIn()
    {
        assertEquals(ChatColor.DARK_GRAY, SignStyle.resolveColor("  dark_gray ", ChatColor.WHITE),
            "an admin typing a setting by hand should not have to match case exactly");
    }

    @Test
    void anUnknownNameFallsBackRatherThanBreakingTheSign()
    {
        assertEquals(ChatColor.GREEN, SignStyle.resolveColor("chartreuse", ChatColor.GREEN));
    }

    @Test
    void anUnsetNameFallsBack()
    {
        assertEquals(ChatColor.GRAY, SignStyle.resolveColor(null, ChatColor.GRAY));
        assertEquals(ChatColor.GRAY, SignStyle.resolveColor("   ", ChatColor.GRAY));
    }

    /**
     * A formatting code that is not a colour is refused.
     *
     * <p>{@code ChatColor} holds both colours and formats, so {@code valueOf} happily returns
     * MAGIC for anyone who types it -- and MAGIC renders as scrambling nonsense characters,
     * which on a gate's dial sign means the destination cannot be read at all. Falling back
     * gives a readable sign instead of an unusable one.
     */
    @Test
    void aFormattingCodeThatIsNotAColourIsRefused()
    {
        assertEquals(ChatColor.AQUA, SignStyle.resolveColor("MAGIC", ChatColor.AQUA),
            "MAGIC is a real ChatColor constant and would render a destination unreadable");
        assertEquals(ChatColor.AQUA, SignStyle.resolveColor("BOLD", ChatColor.AQUA));
    }

    @Test
    void paintingPutsTheColourInFrontOfTheText()
    {
        assertEquals(ChatColor.AQUA + "Helios", SignStyle.paint(ChatColor.AQUA, "Helios"));
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
        assertEquals("", SignStyle.paint(ChatColor.AQUA, ""));
        assertEquals("", SignStyle.paint(ChatColor.AQUA, null));
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
            SignStyle.stripFormatting(ChatColor.AQUA + "-Helios-"),
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
