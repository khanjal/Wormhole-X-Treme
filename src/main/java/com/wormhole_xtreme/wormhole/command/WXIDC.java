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
 * The Class WXIDC.
 * 
 * @author alron
 */
public class WXIDC implements CommandExecutor
{

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        final String[] a = CommandUtilities.commandEscaper(args);
        if (a.length >= 1)
        {

            if (StargateManager.isStargate(a[0]))
            {
                final Stargate s = StargateManager.getStargate(a[0]);
                if ( !s.isGateSignPowered() && (s.getGateIrisLeverBlock() != null))
                {
                    if (CommandUtilities.playerCheck(sender)
                        ? (WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG) || s.isOwner((Player) sender))
                        : true)
                    {
                        // 2. if args other than name - do a set                
                        if (a.length >= 2)
                        {
                            if (a[1].equals("-clear"))
                            {
                                // Remove from big list of all blocks
                                StargateManager.removeBlockIndex(s.getGateIrisLeverBlock());
                                // Set code to "" and then remove it from stargates block list
                                s.setIrisDeactivationCode("");
                            }
                            else
                            {
                                // Set code
                                s.setIrisDeactivationCode(a[1]);
                                // Make sure that block is in index
                                StargateManager.addBlockIndex(s.getGateIrisLeverBlock(), s);
                            }
                        }

                        // 3. always display current value at end.
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "IDC for gate: " + s.getGateName() + " is:" + s.getGateIrisDeactivationCode());
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Iris not available for sign powered stargates or gates without an iris activation block.");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid Stargate: " + a[0]);

            }
            return true;
        }
        return false;
    }

}
