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
            return removeNamedGate(sender, CommandUtilities.commandEscaper(args));
        }
        catch (final RuntimeException t)
        {
            com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(
                java.util.logging.Level.WARNING, "Error executing /wx remove: " + t.getMessage());
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "An error occurred while removing the gate. Check server logs.");
            return true;
        }
    }

    /**
     * Removes the gate named, if there is one and the sender may.
     *
     * @param sender
     *            who asked
     * @param a
     *            the arguments, gate name first
     * @return true unless the words given were not a gate and a flag, which prints the usage
     *         line
     */
    private static boolean removeNamedGate(final CommandSender sender, final String[] a)
    {
        // "-all" means "and its blocks" as the second word. There is no "remove every gate",
        // so in the name position it is refused rather than looked up as a gate called that.
        if ((a.length < 1) || (a.length > 2) || "-all".equals(a[0]))
        {
            return false;
        }

        final Stargate s = StargateManager.getStargate(a[0]);
        if (s == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "Gate does not exist: " + a[0] + ". Remember proper capitalization.");
            return true;
        }
        if (CommandUtilities.playerCheck(sender)
            && !WXPermissions.checkWXPermissions((Player) sender, s, PermissionType.REMOVE))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }

        final boolean destroy = (a.length == 2) && a[1].equalsIgnoreCase("-all");
        CommandUtilities.gateRemove(s, destroy, true,
            CommandUtilities.playerCheck(sender) ? (Player) sender : null);
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + "Wormhole Removed: " + s.getGateName());
        return true;
    }

}
