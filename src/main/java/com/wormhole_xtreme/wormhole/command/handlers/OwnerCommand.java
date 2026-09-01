package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Handler for the '/wormhole owner' admin command.
 */
public class OwnerCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (args.length >= 2)
        {
            final Stargate s = StargateManager.getStargate(args[1]);
            if (s != null)
            {
                if (args.length == 3)
                {
                    final String newOwnerName = args[2];
                    Player onlineTarget = Bukkit.getPlayerExact(newOwnerName);
                    if (onlineTarget != null)
                    {
                        s.setGateOwner(onlineTarget.getUniqueId().toString());
                        s.setGateOwnerName(onlineTarget.getName());
                    }
                    else
                    {
                        org.bukkit.OfflinePlayer offline = null;
                        try
                        {
                            for (final org.bukkit.OfflinePlayer op : Bukkit.getOfflinePlayers())
                            {
                                if (op == null) continue;
                                final String oname = op.getName();
                                if (oname != null && oname.equalsIgnoreCase(newOwnerName))
                                {
                                    offline = op;
                                    break;
                                }
                            }
                        }
                        catch (final Throwable ignore) {}

                        if (offline != null && (offline.hasPlayedBefore() || offline.isOnline()))
                        {
                            s.setGateOwner(offline.getUniqueId().toString());
                            s.setGateOwnerName(offline.getName() != null ? offline.getName() : newOwnerName);
                        }
                        else
                        {
                            s.setGateOwner(newOwnerName);
                            s.setGateOwnerName(newOwnerName);
                        }
                    }
                    s.setupGateSign(true);
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate: " + s.getGateName() + " Now owned by: " + s.getGateOwnerName());
                }
                else if (args.length == 2)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate: " + s.getGateName() + " Owned by: " + s.getGateOwnerName());
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.constructNameInvalid.toString() + "\"" + args[1] + "\"");
            }
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.gateNotSpecified.toString());
            return false;
        }
        return true;
    }

}
