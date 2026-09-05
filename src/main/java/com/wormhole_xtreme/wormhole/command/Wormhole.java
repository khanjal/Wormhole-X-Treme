package com.wormhole_xtreme.wormhole.command;

import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
// HelpSupport removed

/**
 * The Class Wormhole.
 * 
 * @author alron
 */
public class Wormhole implements CommandExecutor
{

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        try
        {
            if (CommandUtilities.playerCheck(sender)
                ? WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG)
                : true)
            {
                final String[] a = CommandUtilities.commandEscaper(args);
                if (a.length == 0)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole admin/config command (use /wormhole <subcommand>)");
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid commands: " + SubCommands.nameList());
                    return true;
                }

                final SubCommands.Entry entry = SubCommands.find(a[0]);
                if (entry != null)
                {
                    return entry.run(sender, a);
                }

                sender.sendMessage(ConfigManager.MessageStrings.requestInvalid.toString() + ": " + a[0]);
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid commands: " + SubCommands.nameList());
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            }
            return true;
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Error executing /wormhole command: " + t.getMessage());
            if (CommandUtilities.playerCheck(sender))
            {
                ((Player) sender).sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "An internal error occurred. Check server logs.");
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "An internal error occurred. Check server logs.");
            }
            return true;
        }
    }
}
