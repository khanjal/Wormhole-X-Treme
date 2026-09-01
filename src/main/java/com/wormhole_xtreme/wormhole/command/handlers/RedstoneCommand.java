package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Handler for '/wormhole redstone'
 */
public class RedstoneCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if ((args.length == 2) || (args.length == 3))
        {
            if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (args.length == 3)
                {
                    if (com.wormhole_xtreme.wormhole.command.CommandUtilities.isBoolean(args[2]))
                    {
                        stargate.setGateRedstonePowered(Boolean.valueOf(args[2].trim().toLowerCase()));
                        if (stargate.isGateRedstonePowered())
                        {
                            stargate.setupRedstone(true);
                        }
                        else
                        {
                            stargate.setupRedstone(false);
                        }
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " is redstone powered: " + stargate.isGateRedstonePowered());
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid boolean option: " + args[2]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole redstone [stargate] <boolean>");
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " is redstone powered: " + stargate.isGateRedstonePowered());
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid boolean options are: true and false");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole redstone [stargate] <boolean>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole redstone [stargate] <boolean>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
            return false;
        }
    }

}
