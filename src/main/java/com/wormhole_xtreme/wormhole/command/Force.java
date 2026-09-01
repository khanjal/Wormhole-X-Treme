package com.wormhole_xtreme.wormhole.command;

import java.util.Arrays;
import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * The Class Force.
 * 
 * @author alron
 */
public class Force implements CommandExecutor
{

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
                final String[] a = CommandUtilities.commandEscaper(args);
                if (a.length == 1)
                {
                    if (CommandUtilities.playerCheck(sender)
                        ? WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG)
                        : true)
                    {
                        if (a[0].equalsIgnoreCase("-all"))
                        {
                            for (final Stargate gate : StargateManager.getAllGates())
                            {
                                CommandUtilities.closeGate(gate, true);
                            }
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "All gates have been deactivated, darkened, and have had their iris (if any) opened.");
                        }
                        else if (StargateManager.isStargate(a[0]))
                        {
                            CommandUtilities.closeGate(StargateManager.getStargate(a[0]), true);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + a[0] + " has been closed, darkened, and has had its iris (if any) opened.");
                        }
                        else
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                            return false;
                        }

                        if (CommandUtilities.playerCheck(sender))
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Player: \"" + ((Player) sender).getName() + "\" ran /wormhole force (alias: /wx force): " + Arrays.toString(a));
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                    }
                    return true;
                }
                else
                {
                    return false;
                }
            }
        });
    }
}
