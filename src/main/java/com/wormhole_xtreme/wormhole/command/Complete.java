package com.wormhole_xtreme.wormhole.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * The Class Complete.
 * 
 * @author alron
 */
public class Complete implements CommandExecutor, TabCompleter
{
    // Pending completions: player -> {name, idc, network}
    private static final java.util.concurrent.ConcurrentHashMap<org.bukkit.entity.Player, String[]> pendingCompletions = new java.util.concurrent.ConcurrentHashMap<>();

    public static void addPendingCompletion(final org.bukkit.entity.Player p, final String name, final String idc, final String network)
    {
        pendingCompletions.put(p, new String[] { name, idc, network });
    }

    public static String[] getPendingCompletion(final org.bukkit.entity.Player p)
    {
        return pendingCompletions.get(p);
    }

    public static void removePendingCompletion(final org.bukkit.entity.Player p)
    {
        pendingCompletions.remove(p);
    }

    /**
     * Do complete.
     * 
     * @param player
     *            the player
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doComplete(final Player player, final String[] args)
    {
        final String name = args[0].trim().replace("\n", "").replace("\r", "");
        if (name.length() >= 12)
        {
            player.sendMessage(ConfigManager.MessageStrings.constructNameTooLong.toString() + "\"" + name + "\"");
            return true;
        }

        final String[] options = parseOptions(args);
        final String idc = options[0];
        final String network = options[1];

        if (!WXPermissions.checkWXPermissions(player, network, PermissionType.BUILD))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        if (StargateManager.getStargate(name) != null)
        {
            player.sendMessage(ConfigManager.MessageStrings.constructNameTaken.toString() + "\"" + name + "\"");
            return true;
        }

        final String incompleteName = StargateManager.getIncompleteStargateName(player);
        if (incompleteName == null)
        {
            awaitDhdClick(player, name, idc, network);
            return true;
        }
        finishBuiltGate(player, name, idc, network, incompleteName);
        return true;
    }

    /**
     * Reads the optional {@code idc=} and {@code net=} arguments.
     *
     * <p>Tolerates a space after the equals, which is the common way to mistype these:
     * {@code idc= 1234} arrives as two arguments and means what {@code idc=1234} means.
     * Anything that is not a key=value pair is passed over rather than refused.
     *
     * @return the IDC and the network, in that order, each empty if not given
     */
    private static String[] parseOptions(final String[] args)
    {
        String idc = "";
        String network = "";
        for (int i = 1; i < args.length; i++)
        {
            final String token = args[i];
            if ((token == null) || token.isEmpty())
            {
                continue;
            }
            final int eqPos = token.indexOf('=');
            if (eqPos < 0)
            {
                continue;
            }
            final String key = token.substring(0, eqPos).trim();
            String value = token.substring(eqPos + 1).trim();
            if (value.isEmpty() && ((i + 1) < args.length) && !args[i + 1].contains("="))
            {
                value = args[i + 1].trim();
                i++;
            }
            if ("idc".equalsIgnoreCase(key))
            {
                idc = value;
            }
            else if ("net".equalsIgnoreCase(key))
            {
                network = value;
            }
        }
        return new String[] {idc, network};
    }

    /**
     * Waits for the player to click the DHD, having nothing part-built to finish.
     *
     * <p>This is how a gate built without {@code /wormhole build} gets completed: the name
     * comes first and the click says which gate it belongs to.
     */
    private static void awaitDhdClick(final Player player, final String name, final String idc,
        final String network)
    {
        addPendingCompletion(player, name, idc, network);
        final String header = ConfigManager.MessageStrings.normalHeader.toString();
        player.sendMessage(header + "Please click the DHD lever/button to complete the gate.");
        player.sendMessage(header + "Optional parameters: idc=<code> net=<network> (example: /wormhole complete " + name + " idc=1234 net=Private)");
        player.sendMessage(header + "Type '/wormhole complete cancel' to cancel (alias: '/wx complete cancel').");
    }

    /** Finishes the gate this player already has part-built, charging for it if the server does. */
    private static void finishBuiltGate(final Player player, final String name, final String idc,
        final String network, final String incompleteName)
    {
        final double buildCost = (ConfigManager.isEconomyEnabled() && com.wormhole_xtreme.wormhole.plugin.EconomySupport.isAvailable())
            ? ConfigManager.getEconomyBuildCost()
            : 0.0;
        if ((buildCost > 0) && !com.wormhole_xtreme.wormhole.plugin.EconomySupport.canAfford(player, buildCost))
        {
            player.sendMessage(ConfigManager.MessageStrings.economyInsufficientFunds.toString());
            return;
        }
        if (!StargateManager.completeStargate(player, name, idc, network))
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Construction Failed!? (found incomplete: \"" + incompleteName + "\") Check server logs for details.");
            com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, "/wormhole complete failed for player " + player.getName() + " - incomplete gate exists: " + incompleteName);
            return;
        }
        player.sendMessage(ConfigManager.MessageStrings.constructSuccess.toString());
        if (buildCost > 0)
        {
            com.wormhole_xtreme.wormhole.plugin.EconomySupport.charge(player, buildCost);
            player.sendMessage(ConfigManager.MessageStrings.economyBuildCharged.toString()
                + buildCost + " " + com.wormhole_xtreme.wormhole.plugin.EconomySupport.currencyName(buildCost));
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        final String[] arguments = CommandUtilities.commandEscaper(args);
        if ((arguments.length <= 3) && (arguments.length > 0))
        {
            if (arguments[0].equalsIgnoreCase("help"))
            {
                if (CommandUtilities.playerCheck(sender))
                {
                    final Player player = (Player) sender;
                    player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Usage: /wormhole complete <name> [idc=<code>] [net=<network>]");
                    player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Example: /wormhole complete MyGate idc=1234 net=Private (alias: '/wx complete').");
                }
                return true;
            }
            if (CommandUtilities.playerCheck(sender))
            {
                try
                {
                    return doComplete((Player) sender, arguments);
                }
                catch (final Exception e)
                {
                    com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, "Error executing /wormhole complete: " + e.getMessage());
                    final Player player = (Player) sender;
                    player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid arguments. Usage: /wormhole complete <name> [idc=<code>] [net=<network>]");
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public java.util.List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args)
    {
        // Disable Bukkit's default player-name autocompletion for /wxcomplete.
        // Return an empty list so the client receives no suggestions.
        return java.util.Collections.emptyList();
    }

}
