package com.wormhole_xtreme.wormhole.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * The Class Build.
 * 
 * @author alron
 */
public class Build implements CommandExecutor
{

    /**
     * Do build.
     * 
     * @param player
     *            the player
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doBuild(final Player player, final String[] args)
    {
        if (args.length == 1)
        {
            if (WXPermissions.checkWXPermissions(player, PermissionType.CONFIG))
            {

                if (StargateHelper.isStargateShape(args[0]))
                {
                    StargateManager.addPlayerBuilderShape(player, StargateHelper.getStargateShape(args[0]));
                    player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Press Activation button on new DHD to autobuild Stargate in the shape of: " + args[0]);
                }
                else
                {
                    player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid shape: " + args[0]);
                }
            }
            else
            {
                player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            }
            return true;
        }
        return false;
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
                if (CommandUtilities.playerCheck(sender))
                {
                    final String[] arguments = CommandUtilities.commandEscaper(args);
                    if ((arguments.length < 3) && (arguments.length > 0))
                    {
                        final Player player = (Player) sender;
                        return doBuild(player, arguments);
                    }
                    return false;
                }
                return true;
            }
        });
    }

}
