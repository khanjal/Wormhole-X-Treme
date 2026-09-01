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
        // The per-group count sub-command is gone. Its write path called
        // doCooldownGroup, so it silently rewrote cooldown timers while reporting a build
        // restriction count, and its read path called doRestrictionGroup, a stub that
        // always returned -1. Build restriction is a plain on/off switch.
        if ((args.length == 2) && com.wormhole_xtreme.wormhole.command.CommandUtilities.isBoolean(args[1]))
        {
            ConfigManager.setBuildRestrictionEnabled(Boolean.valueOf(args[1].toLowerCase()));
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole build count restrictions set to: " + args[1].toLowerCase());
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Usage: /wormhole restrict <true|false> - limits how many gates a player may build.");
        }
        return true;
    }

}
