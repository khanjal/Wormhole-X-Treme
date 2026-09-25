package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * Unit tests for the `/wormhole complete` command parsing and robustness.
 */
class CompleteCommandTest
{
    @BeforeEach
    void beforeEach()
    {
        // Ensure plugin reference exists to avoid NPEs during logging
        final WormholeXTreme pluginMock = mock(WormholeXTreme.class);
        try
        {
            PluginTestSupport.install(pluginMock);
        }
        catch (final Throwable ignore) { /* the stub server is only needed by some paths */ }
    }

    @AfterEach
    void afterEach()
    {
        // no-op cleanup; tests remove any pending completion they create
    }

    @Test
    void completeHandlesIdcWithSeparatedValue()
    {
        final Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        when(player.getName()).thenReturn("tester");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        // Ensure no existing gate with this name
        final String gateName = "NickelUnit";
        final Stargate existing = StargateManager.getStargate(gateName);
        if (existing != null)
        {
            StargateManager.removeStargate(existing);
        }

        StargateManager.removeIncompleteStargate(player);

        final String[] args = new String[] { gateName, "idc=", "test" };

        final Complete subject = new Complete();
        final boolean result = subject.onCommand(player, null, "wormhole", args);

        assertTrue(result, "onCommand should return true for complete invocation");

        final String[] pending = Complete.getPendingCompletion(player);
        assertNotNull(pending, "Pending completion should have been registered");
        assertEquals(gateName, pending[0]);
        assertEquals("test", pending[1]);
        assertEquals("", pending[2]);

        Complete.removePendingCompletion(player);
    }

    @Test
    void completeHandlesEmptyIdcToken()
    {
        final Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        when(player.getName()).thenReturn("tester2");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        final String gateName = "NickelUnit2";
        final Stargate existing = StargateManager.getStargate(gateName);
        if (existing != null)
        {
            StargateManager.removeStargate(existing);
        }

        StargateManager.removeIncompleteStargate(player);

        final String[] args = new String[] { gateName, "idc=" };

        final Complete subject = new Complete();
        final boolean result = subject.onCommand(player, null, "wormhole", args);

        assertTrue(result);

        final String[] pending = Complete.getPendingCompletion(player);
        assertNotNull(pending);
        assertEquals(gateName, pending[0]);
        assertEquals("", pending[1]);
        assertEquals("", pending[2]);

        Complete.removePendingCompletion(player);
    }


    /** A player who may build, with no gate of their own part-built. */
    private static Player builder()
    {
        final Player p = mock(Player.class);
        when(p.isOp()).thenReturn(true);
        when(p.getName()).thenReturn("builder");
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        StargateManager.removeIncompleteStargate(p);
        Complete.removePendingCompletion(p);
        return p;
    }

    /**
     * A name of twelve characters or more is refused.
     *
     * <p>The limit is what fits a sign, and it is checked before anything else so a name that
     * cannot be shown never reaches a half-built gate.
     */
    @Test
    void aNameTooLongForASignIsRefused()
    {
        final Player player = builder();

        new Complete().onCommand(player, null, "wormhole", new String[] {"TwelveCharsX"});

        verify(player).sendMessage(contains("TwelveCharsX"));
        assertNull(Complete.getPendingCompletion(player),
            "a refused name must not leave a completion waiting");
    }

    /** A name somebody else already used is refused rather than quietly taking it over. */
    @Test
    void aNameAlreadyInUseIsRefused()
    {
        final Player player = builder();
        final Stargate taken = new Stargate();
        taken.setGateName("Taken");
        StargateManager.registerStargate(taken);
        try
        {
            new Complete().onCommand(player, null, "wormhole", new String[] {"Taken"});

            assertNull(Complete.getPendingCompletion(player),
                "a name already in use must not start a completion");
        }
        finally
        {
            StargateManager.removeStargate(taken);
        }
    }

    /**
     * With nothing part-built, the command waits for a click rather than failing.
     *
     * <p>This is the interactive path: the player names the gate first and then clicks the
     * DHD, which is how a gate built without {@code /wormhole build} gets completed.
     */
    @Test
    void withNothingPartBuiltTheCommandWaitsForAClick()
    {
        final Player player = builder();

        new Complete().onCommand(player, null, "wormhole", new String[] {"Fresh", "net=Private"});

        final String[] pending = Complete.getPendingCompletion(player);
        assertNotNull(pending, "the command should be waiting for the DHD click");
        assertEquals("Fresh", pending[0]);
        assertEquals("Private", pending[2], "the network given on the command line is kept");
        verify(player).sendMessage(contains("click the DHD"));

        Complete.removePendingCompletion(player);
    }

    /**
     * A gate that will not complete names the half-built one it found.
     *
     * <p>This message and the one the interactive click path sends are the only thing that
     * differs between the two completion routes, so it is worth holding still before they
     * are merged.
     */
    @Test
    void aRefusedCompletionNamesTheHalfBuiltGate()
    {
        final Player player = builder();
        final Stargate halfBuilt = new Stargate();
        halfBuilt.setGateName("Partial");
        StargateManager.addIncompleteStargate(player, halfBuilt);
        try (org.mockito.MockedStatic<StargateManager> mgr =
            org.mockito.Mockito.mockStatic(StargateManager.class, org.mockito.Mockito.CALLS_REAL_METHODS))
        {
            mgr.when(() -> StargateManager.completeStargate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

            new Complete().onCommand(player, null, "wormhole", new String[] {"Fresh"});
        }

        verify(player).sendMessage(contains("found incomplete: \"Partial\""));
        StargateManager.removeIncompleteStargate(player);
    }

    /**
     * Completing a part-built gate reads its design again, so a dial sign hung since it was
     * detected is taken up. Placing a preview detects the gate before a sign can be hung.
     */
    @Test
    void completingReadsThePartBuiltGateAgain()
    {
        final Stargate fresh = new Stargate();
        final Stargate completed = completeWhileDetectionAnswers(builder("rereader"),
            detection -> detection.thenReturn(fresh));
        assertSame(fresh, completed, "the stored detection was completed, not a fresh one");
    }

    /**
     * A shape that throws on the second reading leaves the first to be completed, rather than
     * failing the command with a usage message about arguments that were fine.
     */
    @Test
    void aReReadingThatThrowsCompletesTheGateAsFirstDetected()
    {
        final Player player = builder("thrower");
        final Stargate completed = completeWhileDetectionAnswers(player,
            detection -> detection.thenThrow(new IllegalStateException("malformed shape")));
        assertEquals("Held", completed.getGateName(), "the gate first detected was not the one completed");
        verify(player, never()).sendMessage(contains("Invalid arguments"));
    }

    /**
     * Holds a part-built gate called Held, runs {@code complete Named} with the second
     * detection answering as told, and returns the gate that reached completion.
     */
    private static Stargate completeWhileDetectionAnswers(final Player player,
        final java.util.function.Consumer<org.mockito.stubbing.OngoingStubbing<Stargate>> answer)
    {
        final Stargate held = new Stargate();
        held.setGateName("Held");
        StargateManager.addIncompleteStargate(player, held);
        final Stargate[] completed = new Stargate[1];
        try (org.mockito.MockedStatic<com.wormhole_xtreme.wormhole.logic.StargateHelper> helper =
                org.mockito.Mockito.mockStatic(com.wormhole_xtreme.wormhole.logic.StargateHelper.class);
            org.mockito.MockedStatic<StargateManager> mgr =
                org.mockito.Mockito.mockStatic(StargateManager.class, org.mockito.Mockito.CALLS_REAL_METHODS))
        {
            answer.accept(helper.when(() -> com.wormhole_xtreme.wormhole.logic.StargateHelper.checkStargate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())));
            mgr.when(() -> StargateManager.completeStargate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
                    completed[0] = StargateManager.getIncompleteStargate(player);
                    return false;
                });

            new Complete().onCommand(player, null, "wormhole", new String[] {"Named"});
        }
        finally
        {
            StargateManager.removeIncompleteStargate(player);
        }
        assertNotNull(completed[0], "nothing reached completion");
        return completed[0];
    }

    private static Player builder(final String name)
    {
        final Player player = builder();
        when(player.getName()).thenReturn(name);
        return player;
    }

    /**
     * -cancel drops a completion waiting for its DHD click, and makes no gate.
     *
     * <p>The waiting message told players to type {@code complete cancel}, which nothing handled:
     * it started another completion, for a gate called cancel.
     */
    @Test
    void cancelDropsAWaitingCompletionAndMakesNoGate()
    {
        final Player player = builder("canceller");
        new Complete().onCommand(player, null, "wormhole", new String[] { "Waiting" });
        assertNotNull(Complete.getPendingCompletion(player), "waiting for the DHD click");

        assertTrue(new Complete().onCommand(player, null, "wormhole", new String[] { Complete.CANCEL }));

        assertNull(Complete.getPendingCompletion(player), "nothing left waiting, and no gate called -cancel");
        verify(player).sendMessage(contains("Gate completion cancelled."));
        verify(player).sendMessage(contains("'/wormhole gate complete -cancel'"));
    }

    /** -help gives the usage, and makes nothing; help is a name like any other. */
    @Test
    void helpTakesADash()
    {
        final Player player = builder("helper");

        new Complete().onCommand(player, null, "wormhole", new String[] { "-help" });

        assertNull(Complete.getPendingCompletion(player));
        verify(player).sendMessage(contains("Usage: /wormhole complete <name>"));

        new Complete().onCommand(player, null, "wormhole", new String[] { "help" });
        assertEquals("help", Complete.getPendingCompletion(player)[0], "a gate called help, waiting for its DHD");
        Complete.removePendingCompletion(player);
    }

    /** A gate name may not start with a dash, since words that do are options. */
    @Test
    void aGateNameStartingWithADashIsRefused()
    {
        final Player player = builder("dasher");

        new Complete().onCommand(player, null, "wormhole", new String[] { "-gate" });

        assertNull(Complete.getPendingCompletion(player));
        verify(player).sendMessage(contains("cannot start with '-'"));
    }
}
