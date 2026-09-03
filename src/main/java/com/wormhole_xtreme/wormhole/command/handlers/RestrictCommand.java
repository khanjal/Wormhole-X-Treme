package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Handler for '/wormhole restrict'
 */
public class RestrictCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Gate management was never actually gated: none of these commands checked a
        // permission at all, so any player able to run /wormhole could reconfigure or
        // reassign any gate on the server. wormhole.config is what an admin already needs
        // for /wormhole config, so it is reused here rather than inventing a second
        // admin-only node that would mean the same thing.
        if ((sender instanceof Player)
            && !WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

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
