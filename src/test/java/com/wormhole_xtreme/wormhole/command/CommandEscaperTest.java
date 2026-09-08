package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * Rejoining the words of a quoted argument.
 *
 * <p>Minecraft hands a command its arguments already split on spaces, so {@code "my gate"}
 * arrives as two of them. This puts it back together. Three commands rely on it -- build,
 * complete and dial -- and nothing covered it.
 *
 * <p>Two of the cases below are recorded rather than endorsed. They are what the method does
 * today; whether they are what it should do is a separate question, raised on the PR that
 * added these tests.
 */
class CommandEscaperTest
{
    private static void escapes(final String[] in, final String... expected)
    {
        assertArrayEquals(expected, CommandUtilities.commandEscaper(in));
    }

    /** Words with no quotes in them come back untouched. */
    @Test
    void unquotedArgumentsAreLeftAlone()
    {
        escapes(new String[] { "complete", "alpha" }, "complete", "alpha");
    }

    /** A quoted phrase becomes one argument, with the quotes taken off. */
    @Test
    void aQuotedPhraseBecomesOneArgument()
    {
        escapes(new String[] { "complete", "\"my", "gate\"" }, "complete", "my gate");
    }

    /** However many words it spans. */
    @Test
    void aLongerQuotedPhraseAlsoBecomesOneArgument()
    {
        escapes(new String[] { "complete", "\"my", "long", "gate\"", "extra" },
            "complete", "my long gate", "extra");
    }

    /** And whatever sits either side of it survives. */
    @Test
    void wordsAroundTheQuotedPhraseAreKept()
    {
        escapes(new String[] { "complete", "a", "\"b", "c\"", "d" },
            "complete", "a", "b c", "d");
    }

    /**
     * A single fully-quoted word keeps its quotes. Recorded, not endorsed.
     *
     * <p>Both quotes are in one word, so the method reads it as "not a phrase that needs
     * rejoining" and passes it through untouched -- quotes included. A player typing
     * {@code /wormhole complete "mygate"} gets a gate whose name contains the quote marks.
     */
    @Test
    void aSingleFullyQuotedWordKeepsItsQuotes()
    {
        escapes(new String[] { "complete", "\"solo\"" }, "complete", "\"solo\"");
    }

    /**
     * An unterminated quote drops everything after it. Recorded, not endorsed.
     *
     * <p>The words are collected against a closing quote that never comes, so they are never
     * flushed into the result. A player who forgets the closing quote gets a command with the
     * argument silently missing rather than an error -- which reads as the command ignoring
     * them.
     */
    @Test
    void anUnterminatedQuoteSwallowsTheRestOfTheCommand()
    {
        escapes(new String[] { "complete", "\"unterminated", "gate" }, "complete");
    }
}
