package com.wormhole_xtreme.wormhole.command;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * /wormhole refresh — puts the player in refresh mode.
 * Next click on an existing gate's DHD lever/button re-detects the gate
 * geometry from scratch (preserving name, owner, IDC, and network) and
 * re-saves the corrected data without touching any blocks.
 */
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

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        final Player player = (Player) sender;
        if (CommandHandlerUtils.lacksConfigPermission(player))
        {
            return true;
        }
        addPendingRefresh(player);
        player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + "Refresh mode active. Click the DHD lever/button of the gate to refresh.");
        return true;
    }
}
