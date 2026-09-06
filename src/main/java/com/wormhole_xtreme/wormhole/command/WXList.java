package com.wormhole_xtreme.wormhole.command;

import java.util.ArrayList;

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
 * The Class WXList.
 * 
 * @author alron
 */
public class WXList implements CommandExecutor
{

    /** Past this many characters a message is truncated by the client rather than wrapped. */
    private static final int LINE_BUDGET = 75;

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender, new java.util.concurrent.Callable<Boolean>()
        {
            @Override
            public Boolean call() throws Exception
            {
                listGates(sender, args);
                return true;
            }
        });
    }

    /** Prints the gate list, or refuses a player who may not see it. */
    private static void listGates(final CommandSender sender, final String[] args)
    {
        if (CommandUtilities.playerCheck(sender)
            && !WXPermissions.checkWXPermissions((Player) sender, PermissionType.LIST))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return;
        }

        // Optional network filter: /wx list [network]
        final String filterNet = (args.length > 0) ? args[0].trim() : null;
        final ArrayList<Stargate> gates = gatesOn(filterNet);

        final String header = filterNet != null
            ? "Gates on network \u00A7B" + filterNet + "\u00A73 ::"
            : "Available gates \u00A73::";
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + header);
        if (gates.isEmpty())
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "No gates found.");
        }
        sendGateNames(sender, gates);
    }

    /**
     * The gates a filter asks for, or all of them when there is no filter.
     *
     * <p>"Public" means the gates on no network at all rather than one of that name. Without
     * it a gate that was never given a network would not appear on any filtered list.
     */
    private static ArrayList<Stargate> gatesOn(final String filterNet)
    {
        final ArrayList<Stargate> gates = new ArrayList<Stargate>();
        final boolean filterPublic = (filterNet != null) && filterNet.equalsIgnoreCase("Public");
        for (final Stargate g : StargateManager.getAllGates())
        {
            if (filterNet == null)
            {
                gates.add(g);
            }
            else if (filterPublic ? (g.getGateNetwork() == null) : namedNetwork(g, filterNet))
            {
                gates.add(g);
            }
        }
        return gates;
    }

    /** Whether this gate is on the network of that name. */
    private static boolean namedNetwork(final Stargate g, final String filterNet)
    {
        return (g.getGateNetwork() != null)
            && g.getGateNetwork().getNetworkName().equalsIgnoreCase(filterNet);
    }

    /**
     * Sends the names, several to a message, breaking before a message grows too long.
     *
     * <p>A single message past the client's limit is truncated rather than wrapped, so the
     * names off the end would simply not be shown.
     */
    private static void sendGateNames(final CommandSender sender, final ArrayList<Stargate> gates)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gates.size(); i++)
        {
            sb.append("\u00A77").append(gates.get(i).getGateName());
            if (i != (gates.size() - 1))
            {
                sb.append("\u00A78, ");
            }
            if (sb.length() >= LINE_BUDGET)
            {
                sender.sendMessage(sb.toString());
                sb = new StringBuilder();
            }
        }
        if (!sb.isEmpty())
        {
            sender.sendMessage(sb.toString());
        }
    }
}
