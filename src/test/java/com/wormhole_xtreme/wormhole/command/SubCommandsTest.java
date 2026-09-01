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
    public void theSubcommandsThatWereMissingFromCompletionAreThereNow()
    {
        final List<String> offered = SubCommands.namesMatching("");
        // wooshdepth and restrict dispatched fine but were never suggested.
        assertTrue(offered.contains("wooshdepth"), "wooshdepth should be suggested");
        assertTrue(offered.contains("restrict"), "restrict should be suggested");
        // These nine were suggested but unreachable.
        for (final String name : new String[] { "list", "build", "complete", "remove",
            "refresh", "go", "compass", "force", "idc" })
        {
            assertNotNull(SubCommands.find(name), name + " should be dispatchable");
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
        assertTrue(forC.contains("custom"));
        assertTrue(forC.contains("compass"));
        assertTrue(forC.contains("cooldown"));
        assertFalse(forC.contains("list"));

        final List<String> forCust = SubCommands.namesMatching("cust");
        assertEquals(1, forCust.size());
        assertEquals("custom", forCust.get(0));
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

    @Test
    public void storageCompletionFollowsBothMigrateForms()
    {
        final SubCommands.Entry storage = SubCommands.find("storage");
        assertTrue(storage.completeArgs(new String[] { "storage", "" }).contains("migrate"));
        // migrate <to> and migrate <from> <to> both take a backend at these positions.
        assertTrue(storage.completeArgs(new String[] { "storage", "migrate", "" }).contains("sqlite"));
        assertTrue(storage.completeArgs(new String[] { "storage", "migrate", "sqlite", "" }).contains("file"));
        assertTrue(storage.completeArgs(new String[] { "storage", "migrate", "sqlite", "file", "" }).contains("force"));
    }
}
