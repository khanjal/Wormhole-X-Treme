package com.wormhole_xtreme.wormhole.command;

import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
// HelpSupport removed

/**
 * The Class Wormhole.
 * 
 * @author alron
 */
public class Wormhole implements CommandExecutor
{

    /**
     * Do activate timeout.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doActivateTimeout(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.TimeoutsCommand().execute(sender, args);
    }

    /**
     * Do cooldown.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doCooldown(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.CooldownCommand().execute(sender, args);
    }

    /**
     * Do custom.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doCustom(final CommandSender sender, final String[] args)
    {

        return new com.wormhole_xtreme.wormhole.command.handlers.CustomCommand().execute(sender, args);

    }

    /**
     * Do iris material.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doIrisMaterial(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.IrisMaterialCommand().execute(sender, args);
    }

    private static boolean doLightMaterial(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.LightMaterialCommand().execute(sender, args);
    }

    /**
     * Do owner.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doOwner(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.OwnerCommand().execute(sender, args);
    }

    

    /**
     * Do Portal Material.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doPortalMaterial(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.PortalMaterialCommand().execute(sender, args);
    }

    /**
     * Do redstone.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doRedstone(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.RedstoneCommand().execute(sender, args);
    }

    /**
     * Do regenerate.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doRegenerate(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand().execute(sender, args);
    }

    /**
     * Do restrict.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doRestrict(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.RestrictCommand().execute(sender, args);
    }

    /**
     * Do shutdown timeout.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doShutdownTimeout(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.TimeoutsCommand().execute(sender, args);
    }

    /**
     * Do simple permissions.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    /**
     * Do woosh depth.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doWooshDepth(final CommandSender sender, final String[] args)
    {
        return new com.wormhole_xtreme.wormhole.command.handlers.WooshDepthCommand().execute(sender, args);
    }
    

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        try
        {
            if (CommandUtilities.playerCheck(sender)
                ? WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG)
                : true)
            {
                final String[] a = CommandUtilities.commandEscaper(args);
                if (a.length > 4)
                {
                    return false;
                }
                if (a.length == 0)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole admin/config command (use /wormhole <subcommand>)");
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid commands: " + SubCommands.nameList());
                    return true;
                }

                final SubCommands.Entry entry = SubCommands.find(a[0]);
                if (entry != null)
                {
                    return entry.run(sender, a);
                }

                sender.sendMessage(ConfigManager.MessageStrings.requestInvalid.toString() + ": " + a[0]);
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid commands: " + SubCommands.nameList());
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            }
            return true;
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Error executing /wormhole command: " + t.getMessage());
            if (CommandUtilities.playerCheck(sender))
            {
                ((Player) sender).sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "An internal error occurred. Check server logs.");
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "An internal error occurred. Check server logs.");
            }
            return true;
        }
    }
}
