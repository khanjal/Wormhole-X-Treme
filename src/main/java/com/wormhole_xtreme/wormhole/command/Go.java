// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2011 Ben Echols, Dean Bailey. See LICENSE.txt for terms.
package com.wormhole_xtreme.wormhole.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * The Class Go.
 * 
 * @author alron
 */
public class Go implements CommandExecutor
{

    /**
     * Do go.
     * 
     * @param player
     *            the player
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doGo(final Player player, final String[] args)
    {
        if (WXPermissions.checkWXPermissions(player, PermissionType.GO))
        {
            if (args.length == 1)
            {
                final String goGate = args[0].trim().replace("\n", "").replace("\r", "");
                final Stargate s = StargateManager.getStargate(goGate);
                if (s != null)
                {
                    player.teleport(s.getGatePlayerTeleportLocation());
                }
                else
                {
                    player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Gate does not exist: " + args[0]);
                }
            }
            else
            {
                return false;
            }
        }
        else
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
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
                ? doGo((Player) sender, arguments)
                : true;
        }
        return false;
    }

}
