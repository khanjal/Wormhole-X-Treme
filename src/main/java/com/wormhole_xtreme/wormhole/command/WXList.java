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

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        if (CommandUtilities.playerCheck(sender)
            ? WXPermissions.checkWXPermissions((Player) sender, PermissionType.LIST)
            : true)
        {
            // Optional network filter: /wx list [network]
            // "Public" (case-insensitive) matches gates with no assigned network.
            final String filterNet = (args.length > 0) ? args[0].trim() : null;
            final boolean filterPublic = (filterNet != null) && filterNet.equalsIgnoreCase("Public");

            final ArrayList<Stargate> allGates = StargateManager.getAllGates();
            final ArrayList<Stargate> gates = new ArrayList<Stargate>();
            for (final Stargate g : allGates)
            {
                if (filterNet == null)
                {
                    gates.add(g);
                }
                else if (filterPublic && g.getGateNetwork() == null)
                {
                    gates.add(g);
                }
                else if (!filterPublic && g.getGateNetwork() != null && g.getGateNetwork().getNetworkName().equalsIgnoreCase(filterNet))
                {
                    gates.add(g);
                }
            }

            final String header = filterNet != null
                ? "Gates on network \u00A7B" + filterNet + "\u00A73 ::"
                : "Available gates \u00A73::";
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + header);
            if (gates.isEmpty())
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "No gates found.");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < gates.size(); i++)
            {
                sb.append("\u00A77" + gates.get(i).getGateName());
                if (i != gates.size() - 1)
                {
                    sb.append("\u00A78, ");
                }
                if (sb.toString().length() >= 75)
                {
                    sender.sendMessage(sb.toString());
                    sb = new StringBuilder();
                }
            }
            if ( !sb.toString().equals(""))
            {
                sender.sendMessage(sb.toString());
            }
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
        }
        return true;
    }

}
