package com.wormhole_xtreme.wormhole.command;

import java.util.Collection;
import java.util.Collections;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.ping.PingMarkers;
import com.wormhole_xtreme.wormhole.model.ping.Waypoints;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * {@code /wormhole ping <gate>}: shows a named gate on the player's locator bar for a few
 * seconds (#16). Experimental and off unless {@code ping-enabled} is true; needs 1.21.6 or later.
 *
 * <p>A gate the player may not see, by the list rules tab completion uses or by its network, is
 * answered exactly as one that does not exist, so a ping cannot be used to find a private gate.
 */
public class Ping implements CommandExecutor
{
    /**
     * Whether a player may ping this gate: it is among the gates they may be offered by name,
     * and they may ping on its network.
     *
     * @param player
     *            who is asking
     * @param gate
     *            the gate
     * @return true if they may
     */
    static boolean maySee(final Player player, final Stargate gate)
    {
        return CommandUtilities.gatesToComplete(player).contains(gate)
            && WXPermissions.checkWXPermissions(player, gate, PermissionType.PING);
    }

    /**
     * The gates a sender may be offered after {@code ping}: none while ping is off, on a server
     * with no locator bar, or to anyone but a player; else those {@link #maySee} allows.
     *
     * @param sender
     *            who is completing
     * @return the gates, unsorted
     */
    static Collection<Stargate> gatesToComplete(final CommandSender sender)
    {
        if (!(sender instanceof Player player) || !ConfigManager.isPingEnabled() || !Waypoints.isSupported())
        {
            return Collections.emptyList();
        }
        return CommandUtilities.gatesToComplete(player).stream()
            .filter(gate -> WXPermissions.checkWXPermissions(player, gate, PermissionType.PING)).toList();
    }

    /**
     * Pings one gate.
     *
     * @param player
     *            who asked
     * @param args
     *            exactly one, the gate's name; any other count answers false
     * @return false for the usage, true otherwise
     */
    private static boolean doPing(final Player player, final String[] args)
    {
        if (args.length != 1)
        {
            return false;
        }
        if (!ConfigManager.isPingEnabled())
        {
            player.sendMessage(ChatText.error(Messages.get(player, "ping.disabled")));
            return true;
        }
        if (!Waypoints.isSupported())
        {
            player.sendMessage(ChatText.error(Messages.get(player, "ping.unsupported", ChatText.command("/wormhole compass"))));
            return true;
        }
        if (!WXPermissions.checkWXPermissions(player, PermissionType.PING))
        {
            player.sendMessage(ChatText.noPermission(player));
            return true;
        }
        final Stargate gate = StargateManager.getStargate(args[0]);
        if ((gate == null) || !maySee(player, gate))
        {
            player.sendMessage(ChatText.error(Messages.get(player, "ping.none", ChatText.name(args[0]))));
            return true;
        }
        final Location at = gate.getGatePlayerTeleportLocation();
        if ((at == null) || (at.getWorld() == null) || !at.getWorld().equals(player.getWorld()))
        {
            player.sendMessage(ChatText.info(Messages.get(player, "ping.other-world", ChatText.name(gate.getGateName()))));
            return true;
        }
        final long wait = PingMarkers.cooldownLeft(player.getUniqueId());
        if (wait > 0)
        {
            final String message;
            if (wait == 1)
            {
                message = Messages.get(player, "ping.cooldown.one");
            }
            else
            {
                message = Messages.get(player, "ping.cooldown.other", String.valueOf(wait));
            }
            player.sendMessage(ChatText.error(message));
            return true;
        }
        if (PingMarkers.place(player, at) == PingMarkers.Outcome.PLACED)
        {
            player.sendMessage(ChatText.info(Messages.get(player, "ping.sent", ChatText.name(gate.getGateName()))));
        }
        else
        {
            player.sendMessage(ChatText.error(Messages.get(player, "ping.failed", ChatText.name(gate.getGateName()))));
        }
        return true;
    }

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender, () ->
            !CommandUtilities.playerCheck(sender) || doPing((Player) sender, CommandUtilities.commandEscaper(args)));
    }
}
