package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Handler for '/wormhole wooshdepth'
 */
public class WooshDepthCommand implements SubCommand
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

        if ((args.length == 3) || (args.length == 2))
        {
            if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (stargate.isGateCustom())
                {
                    if (args.length == 3)
                    {
                        try
                        {
                            final int wooshDepth = Integer.parseInt(args[2].trim());
                            if ((wooshDepth >= 0) && (wooshDepth <= 5))
                            {
                                stargate.setGateCustomWooshDepth(wooshDepth);
                                stargate.setGateCustomWooshDepthSquared(wooshDepth * wooshDepth);
                                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " woosh depth set to: " + stargate.getGateCustomWooshDepth());
                            }
                            else
                            {
                                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid woosh depth: " + args[2]);
                                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid depth: 0 - 5");
                            }
                        }
                        catch (final NumberFormatException e)
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid woosh depth: " + args[2]);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid depth: 0 - 5");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " woosh depth is currently: " + stargate.getGateCustomWooshDepth());
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid depth: 0 - 5");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Stargate is not in custom mode. Set it with the '/wormhole custom' command");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole wooshdepth [stargate] <depth>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid depth: 0 - 5");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole wooshdepth [stargate] <depth>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid depth: 0 - 5");
            return false;
        }
    }

}
