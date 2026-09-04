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
     * <p>Setting a compass heading does not need a compass and does not fail without one --
     * it is stored against the player either way. Which means the command reports success and
     * then appears to do nothing at all, which is the least helpful outcome available.
     *
     * <p>Asked the way round that stays true: is the player carrying an <em>ordinary</em>
     * compass? Only an ordinary one follows the heading. A lodestone-bound compass points at
     * its lodestone, a recovery compass at where its owner died, and anything Minecraft adds
     * later will have its own idea too. Listing the ones that do not work would need
     * revisiting every time that list grows; asking for the one that does never will.
     *
     * <p>Not an error, and not refused. The heading is set and will be there the moment they
     * hold a plain compass, so this explains rather than stops.
     *
     * @param player
     *            the player who asked
     */
    private static void warnIfNothingWillShowIt(final Player player)
    {
        try
        {
            for (final org.bukkit.inventory.ItemStack item : player.getInventory().getContents())
            {
                if (isOrdinaryCompass(item))
                {
                    return;
                }
            }
            player.sendMessage("You have no ordinary compass, so nothing you are carrying will "
                + "show this. The heading is set and waiting -- a plain compass will point at "
                + "it. Lodestone and recovery compasses follow their own targets instead.");
        }
        catch (final RuntimeException ignored)
        {
            // Advice about an inventory is not worth failing the command over.
        }
    }

    /**
     * Whether an item is a compass that follows the player's heading.
     *
     * <p>A compass with a lodestone recorded on it has been bound to that lodestone and
     * stops following the heading. Every other kind of compass -- recovery today, whatever
     * arrives later -- is simply not {@link org.bukkit.Material#COMPASS}, so it fails the
     * first test without needing to be named.
     *
     * @param item
     *            the item to judge, possibly null
     * @return true if it will point where the heading says
     */
    private static boolean isOrdinaryCompass(final org.bukkit.inventory.ItemStack item)
    {
        if ((item == null) || (item.getType() != org.bukkit.Material.COMPASS))
        {
            return false;
        }
        final org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        return !(meta instanceof org.bukkit.inventory.meta.CompassMeta)
            || !((org.bukkit.inventory.meta.CompassMeta) meta).hasLodestone();
    }

    /**
     * Puts the compass back to pointing at world spawn.
     *
     * <p>Which is what an ordinary compass does when nothing has changed it, so this is
     * "undo" rather than a mode of its own. Worth having because the heading is stored
     * against the player and stays until something else moves it -- there was no way back
     * without dying or finding another plugin that sets it.
     *
     * @param player
     *            the player who asked
     * @return true, the command was handled
     */
    private static boolean resetCompass(final Player player)
    {
        player.setCompassTarget(player.getWorld().getSpawnLocation());
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Compass back to normal -- it points at world spawn again.");
        warnIfNothingWillShowIt(player);
        return true;
    }

    /**
     * Says no, once, in the one place that wording lives.
     *
     * @param player
     *            the player being turned away
     * @return true, the command was handled
     */
    private static boolean refuse(final Player player)
    {
        player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
        return true;
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
                if (!CommandUtilities.playerCheck(sender))
                {
                    return true;
                }
                final Player player = (Player) sender;
                if ((args != null) && (args.length > 0)
                    && ("reset".equalsIgnoreCase(args[0]) || "clear".equalsIgnoreCase(args[0])))
                {
                    return WXPermissions.checkWXPermissions(player, PermissionType.COMPASS)
                        ? resetCompass(player)
                        : refuse(player);
                }
                return doCompass(player);
            }
        });
    }

}
