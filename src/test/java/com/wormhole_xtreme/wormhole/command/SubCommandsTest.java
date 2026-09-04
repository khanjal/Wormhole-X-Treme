package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests the /wormhole subcommand registry.
 *
 * <p>The registry exists because dispatch, tab completion and help were three separate
 * hand-maintained lists that had drifted: nine subcommands were offered by tab completion
 * and handled by nothing, while two that worked were never suggested. These tests pin the
 * properties that made that possible.
 */
public class SubCommandsTest
{
    @Test
    public void everySuggestedSubcommandIsAlsoDispatchable()
    {
        // The drift that started this: `list`, `go`, `remove` and six others were offered
        // by tab completion and answered with "Invalid request".
        for (final String name : SubCommands.namesMatching(""))
        {
            assertNotNull(SubCommands.find(name),
                "tab completion offers '" + name + "' but nothing dispatches it");
        }
    }

    @Test
    public void whatIsAdvertisedIsTheFourNamesAndNothingElse()
    {
        // The commands were restructured under two nouns that behave alike, the settings,
        // and the one thing that is neither. Twenty-two names at the top level was how a
        // plugin ends up with a help message nobody reads.
        final List<String> offered = SubCommands.namesMatching("");
        assertEquals(java.util.Arrays.asList("gate", "ring", "compass", "config"),
            offered.stream().sorted(java.util.Comparator.comparing(
                n -> java.util.Arrays.asList("gate", "ring", "compass", "config").indexOf(n)))
                .collect(java.util.stream.Collectors.toList()),
            "the advertised list should be exactly the four nouns");
    }

    @Test
    public void everyOldNameStillDispatches()
    {
        // The point of hiding rather than removing. Anybody with these in a command block, a
        // script, or their fingers keeps working -- they are simply not suggested any more.
        for (final String name : new String[] { "list", "build", "complete", "remove",
            "regenerate", "refresh", "go", "force", "owner", "idc", "redstone", "custom",
            "portalmaterial", "irismaterial", "lightmaterial", "wooshdepth",
            "shutdown_timeout", "activate_timeout", "cooldown", "restrict" })
        {
            assertNotNull(SubCommands.find(name), name + " should still be dispatchable");
            assertFalse(SubCommands.namesMatching("").contains(name),
                name + " has moved, so it should not be suggested any more");
        }
    }

    @Test
    public void aliasesResolveToTheSameEntryAsTheCanonicalName()
    {
        assertSame(SubCommands.find("regenerate"), SubCommands.find("regen"));
        assertSame(SubCommands.find("perms"), SubCommands.find("perm"));
        assertSame(SubCommands.find("shutdown_timeout"), SubCommands.find("timeout"));
    }

    @Test
    public void lookupIsCaseInsensitive()
    {
        assertNotNull(SubCommands.find("LIST"));
        assertNotNull(SubCommands.find("CuStOm"));
    }

    @Test
    public void aliasesAreNotOfferedAsSeparateSuggestions()
    {
        // Suggesting both "regenerate" and "regen" is noise; the canonical name is enough.
        final List<String> offered = SubCommands.namesMatching("");
        assertFalse(offered.contains("regen"), "alias should not be suggested alongside its canonical name");
        assertFalse(offered.contains("perm"));
        assertFalse(offered.contains("timeout"));
    }

    @Test
    public void noTwoSubcommandsShareAName()
    {
        final Set<String> seen = new HashSet<String>();
        final List<String> duplicates = new ArrayList<String>();
        for (final SubCommands.Entry e : SubCommands.all())
        {
            if (!seen.add(e.getName()))
            {
                duplicates.add(e.getName());
            }
            for (final String alias : e.getAliases())
            {
                if (!seen.add(alias))
                {
                    duplicates.add(alias);
                }
            }
        }
        assertTrue(duplicates.isEmpty(), "duplicate subcommand names or aliases: " + duplicates);
    }

    @Test
    public void prefixCompletionNarrowsAsYouType()
    {
        final List<String> forC = SubCommands.namesMatching("c");
        assertTrue(forC.contains("compass"));
        assertTrue(forC.contains("config"));
        assertFalse(forC.contains("gate"));
        // custom and cooldown used to be here. They still work, they are just reached
        // through gate edit and config now rather than offered at the top level.
        assertFalse(forC.contains("custom"));
        assertFalse(forC.contains("cooldown"));

        final List<String> forCon = SubCommands.namesMatching("con");
        assertEquals(1, forCon.size());
        assertEquals("config", forCon.get(0));
    }

    @Test
    public void customOffersItsFlagsAlongsideGateNames()
    {
        // -clean is the migration for gates carrying snapshotted overrides; it has to be
        // discoverable or nobody will know it exists.
        final List<String> completions = SubCommands.find("custom").completeArgs(new String[] { "custom", "-" });
        assertTrue(completions.contains("-all"));
        assertTrue(completions.contains("-clean"));
    }

    @Test
    public void cleanOffersConfirmRatherThanTrueFalse()
    {
        final SubCommands.Entry custom = SubCommands.find("custom");
        assertEquals(List.of("confirm"), custom.completeArgs(new String[] { "custom", "-clean", "" }));
        // The gate form still offers booleans.
        assertTrue(custom.completeArgs(new String[] { "custom", "someGate", "" }).contains("true"));
    }

    @Test
    public void everySubcommandHasUsageTextForHelp()
    {
        for (final SubCommands.Entry e : SubCommands.all())
        {
            assertNotNull(e.getUsage(), e.getName() + " has no usage text");
            assertTrue(e.getUsage().startsWith("/wormhole " + e.getName()),
                e.getName() + " usage should start with its own name: " + e.getUsage());
        }
    }

}
