// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2011 Ben Echols, Dean Bailey. See LICENSE.txt for terms.
package com.wormhole_xtreme.wormhole.command;

import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * The Class Dial.
 * 
 * @author alron
 */
public class Dial implements CommandExecutor
{

    /**
     * Do dial.
     * 
     * @param player
     *            the player
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doDial(final Player player, final String[] args)
    {
        final Stargate start = StargateManager.removeActivatedStargate(player);
        if (start != null)
        {
            if (WXPermissions.checkWXPermissions(player, start, PermissionType.DIALER))
            {
                final String startnetwork = CommandUtilities.getGateNetwork(start);
                if ( !start.getGateName().equals(args[0]))
                {
                    final Stargate target = StargateManager.getStargate(args[0]);
                    // No target
                    if (target == null)
                    {
                        CommandUtilities.closeGate(start, false);
                        player.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                        return true;
                    }
                    final String targetnetwork = CommandUtilities.getGateNetwork(target);
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Dial Target - Gate: \"" + target.getGateName() + "\" Network: \"" + targetnetwork + "\"");
                    // Not on same network
                    if ( !startnetwork.equals(targetnetwork))
                    {
                        CommandUtilities.closeGate(start, false);
                        player.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString() + " Not on same network.");
                        return true;
                    }
                    if (start.isGateIrisActive())
                    {
                        start.toggleIrisActive(false);
                    }
                    if ( !target.getGateIrisDeactivationCode().equals("") && target.isGateIrisActive())
                    {
                        if ((args.length >= 2) && target.getGateIrisDeactivationCode().equals(args[1]))
                        {
                            if (target.isGateIrisActive())
                            {
                                target.toggleIrisActive(false);
                                player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "IDC accepted. Iris has been deactivated.");
                            }
                        }
                    }

                    // If target still has an active iris (no valid IDC provided), block the dial attempt.
                    if (target.isGateIrisActive())
                    {
                        CommandUtilities.closeGate(start, false);
                        player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Remote Iris is active; provide the IDC to unlock.");
                        return true;
                    }

                    if (start.dialStargate(target, false))
                    {
                        player.sendMessage(ConfigManager.MessageStrings.gateConnected.toString());
                    }
                    else
                    {
                        // Attempt recovery only when the target isn't legitimately in use.
                        boolean targetInUse = false;
                        try
                        {
                            if (target.isGateActive() || (target.getGateTarget() != null))
                            {
                                targetInUse = true;
                            }
                            else
                            {
                                for (final Stargate s : StargateManager.getAllGates())
                                {
                                    if ((s != null) && (s != start) && (s.getGateTarget() != null) && (s.getGateTarget() == target) && s.isGateActive())
                                    {
                                        targetInUse = true;
                                        break;
                                    }
                                }
                            }
                        }
                        catch (final Throwable ignore) {}

                        if (targetInUse)
                        {
                            // Target is actively connected; don't attempt force-recovery.
                            CommandUtilities.closeGate(start, false);
                            player.sendMessage(ConfigManager.MessageStrings.targetIsActive.toString());
                        }
                        else
                        {
                            // Attempt recovery: remove stale activator mapping and retry with force.
                            if (WormholeXTreme.getThisPlugin() != null)
                            {
                                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, false, "Dial recovery: removing stale activator for target " + target.getGateName() + " and retrying with force");
                            }
                            StargateManager.removeActivatorForStargate(target);
                            if (start.dialStargate(target, true))
                            {
                                if (WormholeXTreme.getThisPlugin() != null)
                                {
                                    WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, false, "Dial recovery succeeded for target " + target.getGateName());
                                }
                                player.sendMessage(ConfigManager.MessageStrings.gateConnected.toString());
                            }
                            else
                            {
                                if (WormholeXTreme.getThisPlugin() != null)
                                {
                                    WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, false, "Dial recovery failed for target " + target.getGateName());
                                }
                                CommandUtilities.closeGate(start, false);
                                player.sendMessage(ConfigManager.MessageStrings.targetIsActive.toString());
                            }
                        }
                        }
                    }
                else
                {
                    CommandUtilities.closeGate(start, false);
                    player.sendMessage(ConfigManager.MessageStrings.targetIsSelf.toString());
                }
            }
            else
            {
                player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            }
        }
        else
        {
            player.sendMessage(ConfigManager.MessageStrings.gateNotActive.toString());
        }
        return true;
    }

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        final String[] arguments = CommandUtilities.commandEscaper(args);
        if ((arguments.length < 3) && (arguments.length > 0))
        {
            return CommandUtilities.playerCheck(sender)
                ? doDial((Player) sender, arguments)
                : true;
        }
        return false;
    }

}
