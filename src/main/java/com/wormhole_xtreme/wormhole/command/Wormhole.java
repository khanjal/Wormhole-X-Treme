package com.wormhole_xtreme.wormhole.command;

import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
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

    /** What {@code /wormhole} lists, a heading and its subcommand each, in this order. */
    private static final String[][] GROUPS = {
        { "Gates", "gate" }, { "Rings", "ring" }, { "Beams", "beam" }, { "Mirrors", "mirror" },
        { "Settings", "config" }, { "Other", "compass" } };

    /**
     * {@code /wormhole} with nothing after it: each subcommand the sender may run, under a heading
     * for its job, with its usage coloured (#325).
     *
     * @param sender
     *            who asked
     * @param mayConfigure
     *            whether they hold {@code wormhole.config}, which opens every subcommand
     */
    static void sendCommandList(final CommandSender sender, final boolean mayConfigure)
    {
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "Wormhole X-Treme commands:");
        for (final String[] group : GROUPS)
        {
            final SubCommands.Entry entry = SubCommands.find(group[1]);
            if ((entry == null) || entry.isHidden() || !(mayConfigure || entry.checksOwnPermissions()))
            {
                continue;
            }
            sender.sendMessage(ChatText.heading(group[0] + ":") + " "
                + ChatText.usage(entry.getUsage()).substring("Usage: ".length()));
        }
    }

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
                if (mayConfigure || entry.admits(sender, a))
                {
                    // Bukkit answers false with plugin.yml's whole usage block, after whatever the
                    // handler already said; the usage of the command they typed is what helps (#325).
                    if (!entry.run(sender, a))
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                            + ChatText.usage(entry.getUsage()));
                    }
                    return true;
                }
                sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
                return true;
            }

            final String valid = SubCommands.nameList(!mayConfigure);
            if (a.length == 0)
            {
                sendCommandList(sender, mayConfigure);
                return true;
            }

            sender.sendMessage(ConfigManager.MessageStrings.REQUEST_INVALID.toString() + ": " + a[0]);
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "Valid commands: " + valid);
            return true;
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Error executing /wormhole command", t);
            // Everyone is told the same thing, console included: the failure is logged
            // server-side, and neither a player nor an operator can act on more than that.
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "An internal error occurred. Check server logs.");
            return true;
        }
    }
}
