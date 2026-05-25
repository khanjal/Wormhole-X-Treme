package com.wormhole_xtreme.wormhole.command;

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
 * The Class Compass.
 * 
 * @author alron
 */
public class Compass implements CommandExecutor
{

    /**
     * Do compass.
     * 
     * @param player
     *            the player
     * @return true, if successful
     */
    private static boolean doCompass(final Player player)
    {
        if (WXPermissions.checkWXPermissions(player, PermissionType.COMPASS))
        {
            final Stargate closest = StargateManager.findClosestStargate(player.getLocation());
            if (closest != null)
            {
                player.setCompassTarget(closest.getGatePlayerTeleportLocation());
                player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Compass set to wormhole: " + closest.getGateName());
            }
            else
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "No wormholes to track!");
            }
        }
        else
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
        }
        return true;
    }

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender, new java.util.concurrent.Callable<Boolean>()
        {
            @Override
            public Boolean call() throws Exception
            {
                return CommandUtilities.playerCheck(sender)
                    ? doCompass((Player) sender)
                    : true;
            }
        });
    }

}
