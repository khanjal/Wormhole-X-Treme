package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A player-facing subcommand answers to its own permission node, not to {@code wormhole.config}.
 *
 * <p>{@code /wormhole} used to check {@code wormhole.config} once, up front, before it looked
 * at which subcommand had been typed. Every node underneath it was therefore unreachable for
 * anyone but an operator: a server that granted {@code wormhole.beam.use} to its default group
 * -- which is what that node is for, and it defaults to true -- still saw every player refused
 * at the door, with a message that read as though the beam node itself had not applied. The
 * only workaround was handing out {@code wormhole.config}, which also carries gate editing,
 * regeneration, import and ownership transfer.
 *
 * <p>{@code list} stands in for the whole self-permissioned set here because it is the one
 * that needs no world, no gate and no economy to run to completion. What is being pinned is
 * the dispatcher's behaviour, which is the same for {@code beam} and {@code ring}.
 */
class WormholeCommandPermissionTest
{
    private final Wormhole command = new Wormhole();
    private Player player;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

        player = mock(Player.class);
        when(player.getName()).thenReturn("traveller");
        when(player.isOp()).thenReturn(false);
        // Denied by default, so each test grants exactly the node it is about and nothing
        // arrives through a permission lookup quietly succeeding underneath.
        when(player.hasPermission(anyString())).thenReturn(false);
    }

    @Test
    void aPlayerHoldingOnlyTheFeatureNodeReachesTheSubcommand()
    {
        when(player.hasPermission("wormhole.list")).thenReturn(true);

        command.onCommand(player, null, "wormhole", new String[] { "list" });

        // The listing itself, from WXList -- proof the handler ran rather than the dispatcher
        // refusing on the player's behalf.
        verify(player).sendMessage(contains("Available gates"));
        verify(player, never()).sendMessage(contains("You lack the permissions"));
    }

    @Test
    void aPlayerWithoutTheConfigNodeIsStillRefusedAnAdminSubcommand()
    {
        // The other half of the fix: moving the gate off the front door must not have taken
        // it off the admin subcommands, which have no node of their own to fall back on.
        command.onCommand(player, null, "wormhole", new String[] { "restrict" });

        verify(player).sendMessage(contains("You lack the permissions"));
    }

    @Test
    void anOperatorStillReachesAnAdminSubcommand()
    {
        when(player.isOp()).thenReturn(true);

        command.onCommand(player, null, "wormhole", new String[] { "restrict" });

        verify(player, never()).sendMessage(contains("You lack the permissions"));
    }

    @Test
    void theBareCommandTellsANonAdminWhatTheyCanActuallyRun()
    {
        // Previously a flat refusal, which left a player who had been granted wormhole.beam.use
        // with no way to discover that /wormhole beam was the command it applied to.
        command.onCommand(player, null, "wormhole", new String[0]);

        verify(player).sendMessage(contains("beam"));
        verify(player, never()).sendMessage(contains("You lack the permissions"));
    }

    @Test
    void theConsoleStillReachesAnAdminSubcommand()
    {
        // Console and command blocks have never been asked for wormhole.config -- they hold
        // no permissions to check -- and moving the gate must not have started asking.
        final org.bukkit.command.CommandSender console = mock(org.bukkit.command.CommandSender.class);

        command.onCommand(console, null, "wormhole", new String[] { "restrict" });

        verify(console, never()).sendMessage(contains("You lack the permissions"));
        verify(console, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    void beamListAnswersToTheBeamNodeRatherThanToNothingAtAll()
    {
        // It checked nothing of its own, which went unnoticed while the wormhole.config gate
        // in front of it meant no ordinary player could reach it either way.
        final com.wormhole_xtreme.wormhole.command.handlers.BeamCommand beam =
            new com.wormhole_xtreme.wormhole.command.handlers.BeamCommand();

        beam.execute(player, new String[] { "beam", "list" });

        verify(player).sendMessage(contains("You lack the permissions"));
    }

    @Test
    void theListOfferedToANonAdminLeavesOutTheAdminSubcommands()
    {
        final String offered = SubCommands.nameList(true);

        assertTrue(offered.contains("beam"), "beam carries its own nodes and should be offered");
        assertTrue(offered.contains("ring"), "ring carries its own nodes and should be offered");
        assertFalse(offered.contains("config"),
            "config is the node they lack -- offering it is an invitation to be refused");
        assertFalse(offered.contains("gate"),
            "gate edit/regenerate/import all sit behind wormhole.config");
    }
}
