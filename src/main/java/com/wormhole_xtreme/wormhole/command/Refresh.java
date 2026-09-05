package com.wormhole_xtreme.wormhole.command;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * /wormhole refresh — puts the player in refresh mode.
 * Next click on an existing gate's DHD lever/button re-detects the gate
 * geometry from scratch (preserving name, owner, IDC, and network) and
 * re-saves the corrected data without touching any blocks.
 */
// Command handlers return boolean because SubCommand/CommandExecutor say so; "always true" means handled.
@SuppressWarnings("java:S3516")
public class Refresh implements CommandExecutor
{
    private static final ConcurrentHashMap<Player, Boolean> pendingRefresh = new ConcurrentHashMap<>();

    public static void addPendingRefresh(final Player p)
    {
        pendingRefresh.put(p, Boolean.TRUE);
    }

    public static boolean isPendingRefresh(final Player p)
    {
        return pendingRefresh.containsKey(p);
    }

    public static void removePendingRefresh(final Player p)
    {
        pendingRefresh.remove(p);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        final Player player = (Player) sender;
        if (!WXPermissions.checkWXPermissions(player, PermissionType.CONFIG))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        addPendingRefresh(player);
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Refresh mode active. Click the DHD lever/button of the gate to refresh.");
        return true;
    }
}
