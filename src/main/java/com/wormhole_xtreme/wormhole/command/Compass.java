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
     * Says so when the heading has been set but nothing will show it.
     *
     * <p>Setting a compass target does not need a compass and does not fail without one --
     * the heading is stored against the player either way. Which means the command reports
     * success and then appears to do nothing at all, which is the least helpful outcome
     * available.
     *
     * <p>Two ways that happens. Having no compass is the obvious one. The other is holding a
     * compass bound to a lodestone: those point at their lodestone and ignore the target
     * entirely, so the one item the player is looking at is the one that will not move.
     *
     * <p>Neither is an error. The heading is set and will be there when they pick up a plain
     * compass, so this explains rather than refuses.
     *
     * @param player
     *            the player who asked
     */
    private static void warnIfNothingWillShowIt(final Player player)
    {
        try
        {
            if (!player.getInventory().contains(org.bukkit.Material.COMPASS))
            {
                player.sendMessage("You have no compass. The heading is set and waiting -- "
                    + "it will point there as soon as you are holding one.");
                return;
            }
            for (final org.bukkit.inventory.ItemStack held : new org.bukkit.inventory.ItemStack[] {
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand() })
            {
                if ((held != null) && (held.getType() == org.bukkit.Material.COMPASS)
                    && (held.getItemMeta() instanceof org.bukkit.inventory.meta.CompassMeta)
                    && ((org.bukkit.inventory.meta.CompassMeta) held.getItemMeta()).hasLodestone())
                {
                    player.sendMessage("The compass you are holding is bound to a lodestone, "
                        + "so it will keep pointing there. A plain compass will show the gate.");
                    return;
                }
            }
        }
        catch (final RuntimeException ignored)
        {
            // Advice about an inventory is not worth failing the command over.
        }
    }

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
                warnIfNothingWillShowIt(player);
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
