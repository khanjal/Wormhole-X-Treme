package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * The one gate standing in front of every command that administers a gate.
 *
 * <p>Sixteen handlers used to ask this for themselves, in four different spellings. That is
 * not a tidiness complaint: this plugin has already shipped the bug it leads to. Every gate
 * management command -- materials, woosh depth, redstone, owner, regenerate, restrict,
 * cooldown, the timeouts -- once checked nothing at all, so any player who could type
 * {@code /wormhole} could reconfigure or reassign any gate on the server. {@code gate import}
 * was written later and inherited the same gap, because there was nothing to reuse.
 *
 * <p>Seven of those sixteen had a test that would notice if the refusal stopped working.
 * Nine did not: breaking the check deliberately, before this file existed, failed seven
 * tests, so nine of those commands could have started letting anybody through and the build
 * would have stayed green.
 *
 * <p>Now there is one rule rather than sixteen, so one test covers all of them -- and this is
 * the place to say what the rule actually is.
 */
class ConfigPermissionGateTest
{
    // The node check logs through the plugin singleton on its way past, so there has to be
    // one -- without it the refusal path throws before it can refuse anything.
    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static Player playerWho(final boolean op, final boolean hasNode)
    {
        final Player player = mock(Player.class);
        when(player.isOp()).thenReturn(op);
        when(player.hasPermission(anyString())).thenReturn(hasNode);
        return player;
    }

    /** The refusal, which is the whole point of the thing. */
    @Test
    void aPlayerWithoutTheNodeIsRefusedAndToldWhy()
    {
        final Player player = playerWho(false, false);

        assertTrue(CommandHandlerUtils.lacksConfigPermission(player),
            "a player without wormhole.config must not be allowed to administer gates");
        verify(player).sendMessage(contains("permission"));
    }

    /** And the ordinary allow, which must not also send a refusal. */
    @Test
    void aPlayerHoldingTheNodeIsLetThroughSilently()
    {
        final Player player = playerWho(false, true);

        assertFalse(CommandHandlerUtils.lacksConfigPermission(player),
            "a player holding wormhole.config should pass");
        verify(player, never()).sendMessage(anyString());
    }

    /**
     * An operator passes without the node being consulted at all.
     *
     * <p>Deliberate, and documented in {@code WXPermissions}: op is the final word, ahead of
     * even a negated node, so that a server owner cannot lock themselves out of their own
     * gates through a permissions plugin misconfiguration.
     */
    @Test
    void anOperatorPassesWithoutBeingAskedForTheNode()
    {
        final Player op = playerWho(true, false);

        assertFalse(CommandHandlerUtils.lacksConfigPermission(op),
            "an operator may administer gates whatever the permission plugin says");
        verify(op, never()).sendMessage(anyString());
    }

    /**
     * The console and command blocks are let through, and never asked.
     *
     * <p>They hold no permissions to check. Refusing them would mean a server owner could not
     * fix a gate from the console, which is the one place they are certain to be able to
     * reach -- and a command block running plugin commands is already something only an
     * operator could have placed.
     */
    @Test
    void theConsoleIsLetThroughWithoutAPermissionCheck()
    {
        final CommandSender console = mock(CommandSender.class);

        assertFalse(CommandHandlerUtils.lacksConfigPermission(console),
            "a non-player sender has no permissions to check and is not refused");
        verify(console, never()).sendMessage(anyString());
    }
}
