package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.command.CommandUtilities;

/**
 * Handler for '/wormhole cooldown'
 */
public class CooldownCommand implements SubCommand
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
                    final int timeout = Integer.parseInt(args[2]);
                    if ((timeout >= 15) && (timeout <= 3600))
                    {
                        CommandHandlerUtils.doCooldownGroup(args[1], true, timeout);
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole cooldown time set to: " + args[2]);
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid cooldown time: " + args[2]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid cooldown times are between 15 and 3600 seconds.");
                    }
                }
                catch (final NumberFormatException e)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid cooldown time: " + args[2]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid cooldown times are between 15 and 3600 seconds.");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Current cooldown time is: " + CommandHandlerUtils.doCooldownGroup(args[1], false, 0));
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid cooldown times are between 15 and 3600 seconds.");
            }
        }
        else if ((args.length == 2) && CommandUtilities.isBoolean(args[1]))
        {
            ConfigManager.setUseCooldownEnabled(Boolean.valueOf(args[1].toLowerCase()));
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole use cooldowns set to: " + args[1].toLowerCase());
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Command: /wormhole cooldown [false|true|group] <time>");
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid groups are 'one', 'two', and 'three'.");
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid cooldown times are between 15 and 3600 seconds.");
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole use cooldowns currently enabled: " + ConfigManager.isUseCooldownEnabled());
        }
        return true;
    }

}
