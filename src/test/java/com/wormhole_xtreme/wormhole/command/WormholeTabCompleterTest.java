package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The {@code /wormhole} tab completer's own routing: how many words pick the top-level list
 * versus a subcommand's own completer, and what an unrecognised first word does.
 *
 * <p>Everything a specific subcommand offers is {@link SubCommands}' own job and is covered
 * where that subcommand's completions already are -- {@link GateTabCompletionTest} and its
 * siblings. Nothing here duplicates that; this is only the two or fewer lines of dispatch this
 * class actually contains, which had no test of its own even though a real bug already lived
 * here once: a completer list kept separately from the dispatcher drifted until it offered
 * subcommands that no longer existed and hid ones that did, which is exactly the shape of bug
 * that only shows up by actually calling {@code onTabComplete}, not by reading either list.
 */
class WormholeTabCompleterTest
{
    private static List<String> complete(final String... args)
    {
        return new WormholeTabCompleter().onTabComplete(null, null, "wormhole", args);
    }

    /** With nothing typed yet, every visible subcommand name is offered. */
    @Test
    void noArgumentsOffersEveryVisibleSubcommand()
    {
        final List<String> names = complete();

        assertTrue(names.contains("gate"), "got " + names);
        assertTrue(names.contains("ring"), "got " + names);
        assertTrue(names.contains("beam"), "got " + names);
    }

    /**
     * A flat legacy name -- kept dispatchable, but no longer advertised -- is not offered at
     * the top level once its verb moved under {@code gate}.
     */
    @Test
    void aHiddenLegacyNameIsNotOfferedAtTheTopLevel()
    {
        assertFalse(complete().contains("regenerate"), "regenerate moved under gate");
    }

    /** One partial word still picks the top-level list, filtered to what matches it. */
    @Test
    void onePartialWordFiltersTheTopLevelList()
    {
        final List<String> names = complete("ga");

        assertEquals(List.of("gate"), names);
    }

    /** Two or more words hand off to the named subcommand's own completer. */
    @Test
    void aSecondWordDelegatesToTheNamedSubcommand()
    {
        final List<String> verbs = complete("gate", "");

        assertTrue(verbs.contains("build"), "got " + verbs);
        assertTrue(verbs.contains("validate"), "got " + verbs);
    }

    /** A first word naming no subcommand at all offers nothing rather than guessing. */
    @Test
    void anUnknownFirstWordOffersNothing()
    {
        assertTrue(complete("notacommand", "").isEmpty());
    }
}
