package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.permissions.PermissionsManager;

/**
 * Handler for '/wormhole perms' admin command.
 */
public class PermsCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (com.wormhole_xtreme.wormhole.command.CommandUtilities.playerCheck(sender))
        {
            final Player p = (Player) sender;
            PermissionsManager.handlePermissionRequest(p, args);
        }
        return true;
    }

}
