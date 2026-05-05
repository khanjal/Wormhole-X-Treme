package com.nijiko.permissions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

/**
 * Minimal stub for old Permissions API handler.
 */
public class PermissionHandler {
    public boolean has(final Player player, final String node)
    {
        if (player == null)
        {
            return false;
        }
        return player.hasPermission(node) || player.isOp();
    }

    public boolean has(final String playerName, final String node)
    {
        final Player p = Bukkit.getPlayer(playerName);
        return p != null ? has(p, node) : false;
    }

    public boolean has(final CommandSender sender, final String node)
    {
        if (sender == null)
        {
            return false;
        }
        return sender.hasPermission(node) || sender.isOp();
    }
}
