package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * {@code /wormhole gate <verb>}'s own job: reading the verb, choosing a handler, and handing
 * each one the argument shape it actually expects -- not what any individual verb then does
 * with them, which is what {@code GateCommand}'s own class comment says every other test class
 * here already covers.
 *
 * <p>This router had no test of its own before this. Two shapes of bug were possible and
 * invisible to every other test in the suite: a verb wired to the wrong handler, and a handler
 * given arguments at the wrong index because the old flat command and the new verb form expect
 * different starting points -- {@code edit} and {@code shapes} take the whole array with the
 * verb still in it, {@code regenerate} and {@code validate} take it rebuilt with the verb at
 * index zero. Getting that backwards for one verb would look identical to every other verb
 * working, right up until somebody typed that one.
 */
class GateCommandTest
{
    private CommandSender sender;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        sender = mock(CommandSender.class);
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        PluginTestSupport.remove();
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private boolean run(final String... args)
    {
        return new GateCommand().execute(sender, args);
    }

    /** With no verb at all, the verbs are listed under their jobs (#325). */
    @Test
    void namingNoVerbListsThemAll()
    {
        assertTrue(run("gate"));

        verify(sender).sendMessage(org.mockito.ArgumentMatchers.<String>argThat((String s) -> com.wormhole_xtreme.wormhole.utils.ChatText.plain(s).contains("/wormhole gate <verb>")));
        verify(sender).sendMessage(org.mockito.ArgumentMatchers.<String>argThat((String s) -> com.wormhole_xtreme.wormhole.utils.ChatText.plain(s).contains("Building: build, preview, complete")));
        verify(sender).sendMessage(org.mockito.ArgumentMatchers.<String>argThat((String s) -> com.wormhole_xtreme.wormhole.utils.ChatText.plain(s).contains("Looking after gates: edit, remove, regen, validate")));
    }

    /** A verb that is not one of the ten says so, and offers the real list. */
    @Test
    void anUnknownVerbNamesTheVerbsAvailable()
    {
        assertTrue(run("gate", "teleport"));

        verify(sender).sendMessage(contains("No such gate command: teleport"));
        verify(sender).sendMessage(contains("Try one of:"));
    }

    /**
     * {@code edit} takes the whole array with {@code gate} and {@code edit} still in it --
     * unlike {@code regenerate} and {@code validate} below, nothing is rebuilt for it. A gate
     * name at the wrong index here would read as a field name instead, which is exactly what
     * this pins: {@code args[2]} landing on the gate, {@code args[3]} on the field.
     */
    @Test
    void editReceivesTheFullArrayWithNothingRebuilt()
    {
        assertTrue(run("gate", "edit", "alpha", "bogusfield"));

        verify(sender).sendMessage(contains("No such field: bogusfield"));
    }

    /** {@code shapes} takes the same unrebuilt shape {@code edit} does. */
    @Test
    void shapesReceivesTheFullArrayWithNothingRebuilt()
    {
        assertTrue(run("gate", "shapes", "bogusaction"));

        verify(sender).sendMessage(contains("No such shapes command: bogusaction"));
    }

    /**
     * {@code regenerate} is rebuilt as {@code [regenerate, <rest...>]} -- the gate name has to
     * land at index one of the rebuilt array, which is where {@link RegenerateCommand} reads
     * it from, not wherever it sat in the original.
     */
    @Test
    void regenerateRebuildsTheArrayWithTheGateNameAtIndexOne()
    {
        assertTrue(run("gate", "regenerate", "nowhere"));

        verify(sender).sendMessage(contains("nowhere"));
    }

    /** The {@code regen} alias reaches the same handler, rebuilt the same way. */
    @Test
    void regenAliasReachesTheSameHandler()
    {
        assertTrue(run("gate", "regen"), "answered here, so Bukkit's usage block does not follow");

        verify(sender).sendMessage(contains("No gate name specified"));
        verify(sender).sendMessage(org.mockito.ArgumentMatchers.<String>argThat((String s) -> com.wormhole_xtreme.wormhole.utils.ChatText.plain(s).contains("Usage: /wormhole gate regen <gate|-all>")));
    }

    /**
     * {@code create} is routed as {@code complete} rather than answered with the verb list.
     *
     * <p>The word the rest of the ecosystem uses for this -- {@code /mv create},
     * {@code /npc create} -- where this plugin's own is {@code complete}, the second half of
     * build-then-complete and nobody's first guess. The router is the only place the two words
     * meet: get this wrong and the guess is met with "No such gate command", which reads as the
     * gate never having been built.
     */
    @Test
    void createIsRoutedToTheCompleteHandler()
    {
        final boolean aliased = run("gate", "create", "Home");

        assertEquals(run("gate", "complete", "Home"), aliased,
            "create is complete under another name, so the router must answer identically");
        verify(sender, never()).sendMessage(contains("No such gate command"));
    }

    /**
     * {@code validate} is rebuilt the same way {@code regenerate} is. This is the shape a bug
     * here would get wrong silently: a gate name shifted one index either way would either
     * validate the wrong thing or throw on an empty array, and both would look like "validate
     * is broken" rather than "the router mis-wired it".
     */
    @Test
    void validateRebuildsTheArrayWithTheGateNameAtIndexOne()
    {
        assertTrue(run("gate", "validate", "nowhere"));

        verify(sender).sendMessage(contains("nowhere"));
    }

    /** With nothing after the verb, validate's own rebuilt array is still a valid one. */
    @Test
    void validateWithNoGateNameReachesTheHandlerRatherThanFailingInTheRouter()
    {
        assertTrue(run("gate", "validate"));

        verify(sender).sendMessage(contains("No gate name specified"));
        verify(sender).sendMessage(org.mockito.ArgumentMatchers.<String>argThat((String s) -> com.wormhole_xtreme.wormhole.utils.ChatText.plain(s).contains("Usage: /wormhole gate validate <gate|-all>")));
    }

    /** {@code remove} and its {@code delete} alias both reach {@link WXRemove}. */
    @Test
    void removeAndDeleteBothReachTheSameHandler()
    {
        assertTrue(run("gate", "remove"));
        assertTrue(run("gate", "delete"));
        verify(sender, times(2)).sendMessage(org.mockito.ArgumentMatchers.<String>argThat((String s) -> com.wormhole_xtreme.wormhole.utils.ChatText.plain(s).contains("Usage: /wormhole gate remove <gate> [-destroy]")));
    }

    /** A named gate that does not exist is reported the same way through either name. */
    @Test
    void removeNamesAnUnknownGate()
    {
        assertTrue(run("gate", "remove", "ghost"));

        verify(sender).sendMessage(contains("Gate does not exist: ghost"));
    }

    /** Import was written with no permission check at all; this is the fix, not a re-check. */
    @Test
    void importRefusesAPlayerWithoutTheConfigNode()
    {
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("nobody");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertTrue(new GateCommand().execute(player, new String[] {"gate", "import"}));

        verify(player).sendMessage(contains("ermission"));
    }
}
