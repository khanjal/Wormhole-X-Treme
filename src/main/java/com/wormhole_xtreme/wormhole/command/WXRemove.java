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
 * The Class WXRemove.
 * 
 * @author alron
 */
public class WXRemove implements CommandExecutor
{

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        final String[] a = CommandUtilities.commandEscaper(args);
        if ((a.length >= 1) && (a.length <= 2))
        {
            if (a[0].equals("-all"))
            {
                return false;
            }
            final Stargate s = StargateManager.getStargate(a[0]);

            if (s != null)
            {
                if (CommandUtilities.playerCheck(sender)
                    ? WXPermissions.checkWXPermissions((Player) sender, s, PermissionType.REMOVE)
                    : true)
                {
                    boolean destroy = false;
                    if ((a.length == 2) && a[1].equalsIgnoreCase("-all"))
                    {
                        destroy = true;
                    }
                    CommandUtilities.gateRemove(s, destroy);
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole Removed: " + s.getGateName());
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                }

            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Gate does not exist: " + a[0] + ". Remember proper capitalization.");
            }
        }
        else
        {
            return false;
        }
        return true;
    }

}
