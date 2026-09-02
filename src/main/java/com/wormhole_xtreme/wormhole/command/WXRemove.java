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
        try
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
                        CommandUtilities.gateRemove(s, destroy, true,
                            CommandUtilities.playerCheck(sender) ? (Player) sender : null);
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
        catch (final Throwable t)
        {
            com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, false, "Error executing /wx remove: " + t.getMessage());
            if (CommandUtilities.playerCheck(sender))
            {
                final Player p = (Player) sender;
                p.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "An error occurred while removing the gate. Check server logs.");
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "An error occurred while removing the gate. Check server logs.");
            }
            return true;
        }
    }

}
