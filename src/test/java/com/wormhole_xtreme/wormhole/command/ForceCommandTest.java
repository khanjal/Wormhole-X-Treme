package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * Forcing gates shut.
 *
 * <p>{@code -all} closes every gate on the server, which is the one word here that does
 * something an admin cannot easily undo. Everything else names a single gate.
 *
 * <p>The command had no test.
 */
class ForceCommandTest
{
    private CommandSender console;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        // Not a player, so the admin node is not asked for.
        console = mock(CommandSender.class);
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

    private static Stargate registeredGate(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        StargateManager.registerStargate(gate);
        return gate;
    }

    private boolean force(final CommandSender who, final String... args)
    {
        return new Force().onCommand(who, null, "force", args);
    }

    /** Anything but exactly one word gets the usage line. */
    @Test
    void theWrongNumberOfArgumentsIsAUsageError()
    {
        assertFalse(force(console));
        assertFalse(force(console, "alpha", "extra"));
    }

    /**
     * A gate nobody built is refused, and that is the whole answer: true, so no usage line follows
     * to suggest the line itself was wrong (#325). Found by a Sonnet review.
     */
    @Test
    void anUnknownGateIsRefused()
    {
        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            org.junit.jupiter.api.Assertions.assertTrue(force(console, "nowhere"), "refused and explained, not a usage error");

            util.verify(() -> CommandUtilities.closeGate(any(), anyBoolean()), never());
        }
        verify(console).sendMessage(contains("Invalid gate target"));
    }

    /** A named gate is closed, and only that one. */
    @Test
    void aNamedGateIsClosed()
    {
        final Stargate alpha = registeredGate("alpha");
        final Stargate bravo = registeredGate("bravo");

        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            assertTrue(force(console, "alpha"));

            util.verify(() -> CommandUtilities.closeGate(eq(alpha), eq(true)));
            util.verify(() -> CommandUtilities.closeGate(eq(bravo), anyBoolean()), never());
        }
        verify(console).sendMessage(contains("alpha has been closed"));
    }

    /**
     * A forced-open iris is open by default too, and saved.
     *
     * <p>The file keeps only the default. Opened and left shut by default, a floor gate came
     * back from a restart saying shut over an empty opening, and any gate took the next
     * journey's end as the moment to shut again.
     */
    @Test
    void aForcedOpenIrisStaysOpenAndIsSaved()
    {
        final Stargate alpha = registeredGate("alpha");
        alpha.setGateIrisDeactivationCode("secret");
        alpha.setGateIrisActive(true);
        alpha.setGateIrisDefaultActive(true);

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            assertTrue(force(console, "alpha"));

            assertFalse(alpha.isGateIrisActive(), "force opens the iris");
            assertFalse(alpha.isGateIrisDefaultActive(), "and for good, or the file says shut over an open one");
            db.verify(() -> StargateDBManager.saveStargate(alpha));
        }
    }

    /**
     * {@code -all} closes every gate on the server.
     *
     * <p>The one word here that reaches gates the admin did not name, so it is worth knowing
     * it reaches all of them and not, say, the first.
     */
    @Test
    void allClosesEveryGate()
    {
        final Stargate alpha = registeredGate("alpha");
        final Stargate bravo = registeredGate("bravo");

        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            assertTrue(force(console, "-all"));

            util.verify(() -> CommandUtilities.closeGate(eq(alpha), eq(true)));
            util.verify(() -> CommandUtilities.closeGate(eq(bravo), eq(true)));
            util.verify(() -> CommandUtilities.closeGate(any(), anyBoolean()), times(2));
        }
        verify(console).sendMessage(contains("All gates have been deactivated"));
    }

    /** A player without the admin node closes nothing. */
    @Test
    void aPlayerWithoutTheConfigNodeIsRefused()
    {
        registeredGate("alpha");
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("nobody");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            assertTrue(force(player, "-all"));

            util.verify(() -> CommandUtilities.closeGate(any(), anyBoolean()), never());
        }
        verify(player).sendMessage(contains("ermission"));
    }
}
