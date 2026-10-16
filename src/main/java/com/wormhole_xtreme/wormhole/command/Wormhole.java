package com.wormhole_xtreme.wormhole.command;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.handlers.GateCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

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
        sender.sendMessage(ChatText.info("Wormhole X-Treme commands:"));
        for (final String[] group : GROUPS)
        {
            final SubCommands.Entry entry = SubCommands.find(group[1]);
            if ((entry == null) || entry.isHidden())
            {
                continue;
            }
            final String usage;
            if (mayConfigure || entry.checksOwnPermissions())
            {
                usage = entry.getUsage();
            }
            else if (entry.admits(sender, new String[] { group[1], "build" }))
            {
                // gate opens build to whoever may preview, without wormhole.config.
                usage = GateCommand.usageOf("build");
            }
            else
            {
                usage = null;
            }
            if (usage != null)
            {
                sender.sendMessage(ChatText.info(ChatText.heading(group[0] + ":") + " " + ChatText.usageBody(usage)));
            }
        }
    }

    /**
     * Whether the sender may reach the admin and configuration subcommands. Console and
     * command blocks always may, as they always have.
     *
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
    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
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
                        sender.sendMessage(ChatText.info(ChatText.usage(entry.getUsage())));
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

            sender.sendMessage(ChatText.error("No command called " + ChatText.command(a[0]) + ". Try one of these:"));
            sender.sendMessage(ChatText.info(Arrays.stream(valid.split(", ")).map(ChatText::command)
                .collect(Collectors.joining(", "))));
            return true;
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Error executing /wormhole command", t);
            // Everyone is told the same thing, console included: the failure is logged
            // server-side, and neither a player nor an operator can act on more than that.
            sender.sendMessage(CommandUtilities.INTERNAL_ERROR);
            return true;
        }
    }
}
