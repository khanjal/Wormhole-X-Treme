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

    /** A gate nobody built is refused. */
    @Test
    void anUnknownGateIsRefused()
    {
        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            assertFalse(force(console, "nowhere"), "returning false prints the usage line");

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
