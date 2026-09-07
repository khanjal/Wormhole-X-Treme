package com.wormhole_xtreme.wormhole.command;

import java.util.Arrays;
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
 * The Class Force.
 * 
 * @author alron
 */
public class Force implements CommandExecutor
{

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender,
            () -> forceClose(sender, CommandUtilities.commandEscaper(args)));
    }

    /**
     * Closes the gate named, or every gate.
     *
     * @param sender
     *            who asked
     * @param a
     *            the arguments, one word naming a gate or {@code -all}
     * @return true unless the word given named nothing, which prints the usage line
     */
    private static boolean forceClose(final CommandSender sender, final String[] a)
    {
        if (a.length != 1)
        {
            return false;
        }
        if (CommandUtilities.playerCheck(sender)
            && !WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }

        if ("-all".equalsIgnoreCase(a[0]))
        {
            closeEveryGate(sender);
        }
        else if (!closeOneGate(sender, a[0]))
        {
            return false;
        }
        logWhoAsked(sender, a);
        return true;
    }

    /**
     * Closes every gate on the server.
     *
     * <p>The one thing this command does that reaches gates the admin did not name.
     *
     * @param sender
     *            who to tell
     */
    private static void closeEveryGate(final CommandSender sender)
    {
        for (final Stargate gate : StargateManager.getAllGates())
        {
            CommandUtilities.closeGate(gate, true);
        }
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + "All gates have been deactivated, darkened, and have had their iris (if any) opened.");
    }

    /**
     * Closes one named gate.
     *
     * @param sender
     *            who to tell
     * @param name
     *            the gate's name as typed
     * @return false if no gate goes by that name
     */
    private static boolean closeOneGate(final CommandSender sender, final String name)
    {
        // One lookup rather than isStargate followed by getStargate: the registry is
        // concurrent, so asking twice can answer differently.
        final Stargate gate = StargateManager.getStargate(name);
        if (gate == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.TARGET_INVALID.toString());
            return false;
        }
        CommandUtilities.closeGate(gate, true);
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + name + " has been closed, darkened, and has had its iris (if any) opened.");
        return true;
    }

    /**
     * Notes who ran this, when it was a player rather than the console.
     *
     * @param sender
     *            who asked
     * @param a
     *            what they typed
     */
    private static void logWhoAsked(final CommandSender sender, final String[] a)
    {
        if (CommandUtilities.playerCheck(sender))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Player: \"" + sender.getName()
                + "\" ran /wormhole force (alias: /wx force): " + Arrays.toString(a));
        }
    }
}
