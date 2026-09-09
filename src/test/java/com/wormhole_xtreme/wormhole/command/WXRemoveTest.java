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
 * Removing a gate.
 *
 * <p>Two things here are worth holding. {@code -all} as the second word destroys the gate's
 * blocks as well as unregistering it, which is the difference between tidying up a record and
 * taking somebody's build apart. And {@code -all} in the <em>first</em> position is refused
 * outright rather than read as a gate name -- there is no "remove every gate" and quietly
 * inventing one would be the worst possible reading.
 *
 * <p>The command had no test.
 */
class WXRemoveTest
{
    private CommandSender console;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        // Not a player, so the permission check is skipped.
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

    private boolean remove(final CommandSender who, final String... args)
    {
        return new WXRemove().onCommand(who, null, "remove", args);
    }

    /** No words, or too many, gets the usage line by returning false. */
    @Test
    void theWrongNumberOfArgumentsIsAUsageError()
    {
        assertFalse(remove(console));
        assertFalse(remove(console, "alpha", "-all", "extra"));
    }

    /**
     * {@code -all} where a gate name goes is refused, not read as a name.
     *
     * <p>There is no "remove every gate", and inventing one from a word that means
     * "and its blocks" elsewhere in the same command would be the worst possible reading.
     */
    @Test
    void allInTheNamePositionIsRefused()
    {
        registeredGate("-all");

        assertFalse(remove(console, "-all"), "returning false prints the usage line");
    }

    /** A gate nobody built is named back, with a hint about capitalisation. */
    @Test
    void anUnknownGateIsNamedBack()
    {
        assertTrue(remove(console, "nowhere"));

        verify(console).sendMessage(contains("Gate does not exist: nowhere"));
    }

    /** A named gate is removed, and its blocks are left alone. */
    @Test
    void aNamedGateIsRemovedWithoutDestroyingIt()
    {
        final Stargate gate = registeredGate("alpha");

        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            assertTrue(remove(console, "alpha"));

            util.verify(() -> CommandUtilities.gateRemove(eq(gate), eq(false), eq(true), any()));
        }
        verify(console).sendMessage(contains("Wormhole Removed: alpha"));
    }

    /** {@code -all} as the second word takes the blocks down too. */
    @Test
    void allAsTheSecondWordDestroysTheBlocks()
    {
        final Stargate gate = registeredGate("alpha");

        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            assertTrue(remove(console, "alpha", "-all"));

            util.verify(() -> CommandUtilities.gateRemove(eq(gate), eq(true), eq(true), any()));
        }
    }

    /** A second word that is not {@code -all} does not destroy anything. */
    @Test
    void anyOtherSecondWordLeavesTheBlocksAlone()
    {
        final Stargate gate = registeredGate("alpha");

        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            assertTrue(remove(console, "alpha", "please"));

            util.verify(() -> CommandUtilities.gateRemove(eq(gate), eq(false), eq(true), any()));
        }
    }

    /** A player without the remove permission is refused and the gate stays. */
    @Test
    void aPlayerWithoutPermissionIsRefused()
    {
        registeredGate("alpha");
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("nobody");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        try (MockedStatic<CommandUtilities> util = mockStatic(CommandUtilities.class, CALLS_REAL_METHODS))
        {
            assertTrue(remove(player, "alpha"));

            util.verify(() -> CommandUtilities.gateRemove(any(), anyBoolean(), anyBoolean(), any()),
                never());
        }
        verify(player).sendMessage(contains("ermission"));
    }
}
