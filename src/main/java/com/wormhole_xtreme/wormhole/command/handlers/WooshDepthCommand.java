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
        if ((sender instanceof Player player)
            && !WXPermissions.checkWXPermissions(player, PermissionType.CONFIG))
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
                                warnIfShapeOwnsTheWaves(sender, stargate);
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
                        warnIfShapeOwnsTheWaves(sender, stargate);
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

    /**
     * Says so when this setting cannot move what the player is presumably trying to move.
     *
     * <p>The woosh animation prefers a shape's own {@code :W#N} waves and only derives waves
     * from this depth when the shape authors none. Every shipped shape authors them, so on
     * an ordinary gate this setting changes no visuals at all -- it still governs how far
     * from the gate the block and entity protection reaches, which is a real effect, just
     * not the one the name suggests. Saying that plainly beats letting someone set a number,
     * watch nothing happen, and conclude the feature is broken.
     *
     * @param sender who to tell
     * @param stargate the gate whose depth was just set or read
     */
    private static void warnIfShapeOwnsTheWaves(final CommandSender sender, final Stargate stargate)
    {
        if ((stargate.getGateWooshBlocks() != null) && !stargate.getGateWooshBlocks().isEmpty())
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Note: this gate's shape defines its own woosh waves, so depth will not change "
                + "how it looks -- it still sets how far protection reaches from the gate.");
        }
    }

}
