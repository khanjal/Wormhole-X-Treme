package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Handler for '/wormhole restrict'
 */
public class RestrictCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if ((args.length >= 2) && CommandHandlerUtils.isValidGroupName(args[1]))
        {
            if (args.length == 3)
            {
                try
                {
                    final int gateCount = Integer.parseInt(args[2]);
                    if ((gateCount >= 1) && (gateCount <= 200))
                    {
                        CommandHandlerUtils.doCooldownGroup(args[1], true, gateCount);
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole build restriction count: " + args[2]);
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Build restriction count: " + args[2]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid restriction values are between 1 and 200.");
                    }
                }
                catch (final NumberFormatException e)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid restriction count: " + args[2]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid restriction values are between 1 and 200.");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Current restriction count is: " + CommandHandlerUtils.doRestrictionGroup(args[1], false, 0));
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid restriction values are between 1 and 200.");
            }
        }
        else if ((args.length == 2) && com.wormhole_xtreme.wormhole.command.CommandUtilities.isBoolean(args[1]))
        {
            ConfigManager.setBuildRestrictionEnabled(Boolean.valueOf(args[1].toLowerCase()));
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole build count restrictions set to: " + args[1].toLowerCase());
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Build restriction feature has been removed. Use Vault/LuckPerms for permissions.");
        }
        return true;
    }

}
