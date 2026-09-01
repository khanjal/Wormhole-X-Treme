package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Handler for '/wormhole activate_timeout' and '/wormhole timeout' (shutdown_timeout)
 */
public class TimeoutsCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (args.length == 0)
        {
            return false;
        }

        if (args[0].equalsIgnoreCase("activate_timeout"))
        {
            if (args.length == 2)
            {
                try
                {
                    final int timeout = Integer.parseInt(args[1]);
                    if ((timeout >= 10) && (timeout <= 60))
                    {
                        ConfigManager.setTimeoutActivate(timeout);
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "activate_timeout set to: " + ConfigManager.getTimeoutActivate());
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid activate_timeout: " + args[1]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid timeout is between 10 and 60 seconds.");
                        return false;
                    }
                }
                catch (final NumberFormatException e)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid activate_timeout: " + args[1]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid timeout is between 10 and 60 seconds.");
                    return false;
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Current activate_timeout is: " + ConfigManager.getTimeoutActivate());
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid timeout is between 10 and 60 seconds.");
            }
            return true;
        }

        // Treat 'timeout' or 'shutdown_timeout' as shutdown timeout
        if (args[0].equalsIgnoreCase("timeout") || args[0].equalsIgnoreCase("shutdown_timeout"))
        {
            if (args.length == 2)
            {
                try
                {
                    final int timeout = Integer.parseInt(args[1]);
                    if ((timeout > -1) && (timeout <= 60))
                    {
                        ConfigManager.setTimeoutShutdown(timeout);
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "shutdown_timeout set to: " + ConfigManager.getTimeoutShutdown());
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid shutdown_timeout: " + args[1]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid timeout is between 0 and 60 seconds.");
                        return false;
                    }
                }
                catch (final NumberFormatException e)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid shutdown_timeout: " + args[1]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid timeout is between 0 and 60 seconds.");
                    return false;
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Current shutdown_timeout is: " + ConfigManager.getTimeoutShutdown());
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid timeout is between 0 and 60 seconds.");
            }
            return true;
        }

        return false;
    }

}
