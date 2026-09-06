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
            final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
            f.setAccessible(true);
            f.set(null, pluginMock);
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
}
