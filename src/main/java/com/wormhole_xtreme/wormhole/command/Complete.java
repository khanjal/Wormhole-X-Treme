/**
 *   Wormhole X-Treme Plugin for Bukkit
 *   Copyright (C) 2011  Ben Echols
 *                       Dean Bailey
 *   Copyright (C) 2026  Justin Harding
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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

        if (name.length() < 12)
        {
            String idc = "";
            String network = "Public";

            for (int i = 1; i < args.length; i++)
            {
                final String[] key_value_string = args[i].split("=");
                if (key_value_string[0].equals("idc"))
                {
                    idc = key_value_string[1];
                }
                else if (key_value_string[0].equals("net"))
                {
                    network = key_value_string[1];
                }
            }
            if (WXPermissions.checkWXPermissions(player, network, PermissionType.BUILD))
            {
                if ( !StargateRestrictions.isPlayerBuildRestricted(player))
                {
                    if (StargateManager.getStargate(name) == null)
                    {
                        // If player already has an incomplete stargate registered, attempt immediate completion.
                        final String incompleteName = com.wormhole_xtreme.wormhole.model.StargateManager.getIncompleteStargateName(player);
                        if (incompleteName != null)
                        {
                            final double buildCost = (ConfigManager.isEconomyEnabled() && com.wormhole_xtreme.wormhole.plugin.EconomySupport.isAvailable()) ? ConfigManager.getEconomyBuildCost() : 0.0;
                            if (buildCost > 0 && !com.wormhole_xtreme.wormhole.plugin.EconomySupport.canAfford(player, buildCost))
                            {
                                player.sendMessage(ConfigManager.MessageStrings.economyInsufficientFunds.toString());
                            }
                            else if (StargateManager.completeStargate(player, name, idc, network))
                            {
                                player.sendMessage(ConfigManager.MessageStrings.constructSuccess.toString());
                                if (buildCost > 0)
                                {
                                    com.wormhole_xtreme.wormhole.plugin.EconomySupport.charge(player, buildCost);
                                    player.sendMessage(ConfigManager.MessageStrings.economyBuildCharged.toString()
                                        + buildCost + " " + com.wormhole_xtreme.wormhole.plugin.EconomySupport.currencyName(buildCost));
                                }
                            }
                            else
                            {
                                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Construction Failed!? (found incomplete: \"" + incompleteName + "\") Check server logs for details.");
                                com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, false, "/wormhole complete failed for player " + player.getName() + " — incomplete gate exists: " + incompleteName);
                            }
                        }
                        else
                        {
                            // Enter interactive completion mode: wait for the player to click the DHD lever/button.
                            addPendingCompletion(player, name, idc, network);
                            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Please click the DHD lever/button to complete the gate.");
                            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Optional parameters: idc=<code> net=<network> (example: /wormhole complete " + name + " idc=1234 net=Private)");
                            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Type '/wormhole complete cancel' to cancel (alias: '/wx complete cancel').");
                        }
                    }
                    else
                    {
                        player.sendMessage(ConfigManager.MessageStrings.constructNameTaken.toString() + "\"" + name + "\"");
                    }
                }
                else
                {
                    player.sendMessage(ConfigManager.MessageStrings.playerBuildCountRestricted.toString());
                }
            }
            else
            {
                player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            }
        }
        else
        {
            player.sendMessage(ConfigManager.MessageStrings.constructNameTooLong.toString() + "\"" + name + "\"");
        }
        return true;
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
            return CommandUtilities.playerCheck(sender)
                ? doComplete((Player) sender, arguments)
                : true;
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
