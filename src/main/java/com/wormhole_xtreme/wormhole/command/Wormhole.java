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

    /**
     * Whether the sender may reach the admin and configuration subcommands. Console and
     * command blocks always may, as they always have.
     *
     * @param sender
     *            the command sender
     * @return true if they hold {@code wormhole.config}, or are not a player
     */
    private static boolean hasConfigPermission(final CommandSender sender)
    {
        return !CommandUtilities.playerCheck(sender)
            || WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG);
    }

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        try
        {
            final String[] a = CommandUtilities.commandEscaper(args);
            final SubCommands.Entry entry = a.length == 0 ? null : SubCommands.find(a[0]);
            final boolean mayConfigure = hasConfigPermission(sender);

            // The config gate is applied here, per subcommand, rather than once before
            // dispatch: beaming and rings carry their own nodes, and gating the whole command
            // on wormhole.config made those nodes unreachable for anyone but an operator.
            if (entry != null)
            {
                if (mayConfigure || entry.checksOwnPermissions())
                {
                    return entry.run(sender, a);
                }
                sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                return true;
            }

            final String valid = SubCommands.nameList(!mayConfigure);
            if (a.length == 0)
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole admin/config command (use /wormhole <subcommand>)");
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid commands: " + valid);
                return true;
            }

            sender.sendMessage(ConfigManager.MessageStrings.requestInvalid.toString() + ": " + a[0]);
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid commands: " + valid);
            return true;
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Error executing /wormhole command: " + t.getMessage());
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
