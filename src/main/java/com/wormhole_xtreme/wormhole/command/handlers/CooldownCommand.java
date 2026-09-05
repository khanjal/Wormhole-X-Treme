package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Handler for '/wormhole cooldown'
 *
 * <p>Takes a plain number of seconds. It used to take a group name -- one, two or three --
 * but none of those three settings were ever registered in {@code DefaultSettings}, so the
 * write went nowhere and the read fell back to a hardcoded literal: the command reported
 * "cooldown time set to: 300" and the cooldown stayed at 120. Only one of the three groups
 * was read by anything at all, so a single setting is what the feature actually was.
 */
public class CooldownCommand implements SubCommand
{

    /** Shortest cooldown worth having, in seconds. Below this the timer is not really a wait. */
    private static final int MIN_SECONDS = 15;

    /** Longest cooldown accepted, in seconds. An hour. */
    private static final int MAX_SECONDS = 3600;

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Gate management was never actually gated: none of these commands checked a
        // permission at all, so any player able to run /wormhole could reconfigure or
        // reassign any gate on the server. wormhole.config is what an admin already needs
        // for /wormhole config, so it is reused here rather than inventing a second
        // admin-only node that would mean the same thing.
        if ((sender instanceof Player)
            && !WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

        if (args.length < 2)
        {
            usage(sender);
            return true;
        }

        // Anyone with the old group form in a script or an old wiki page lands here. Say
        // what happened rather than falling through to a bare usage line, since the old
        // form appeared to work for years.
        if (isOldGroupName(args[1]))
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "Cooldown groups are gone -- they never took effect. Use: /wormhole cooldown <seconds>");
            usage(sender);
            return true;
        }

        if (CommandUtilities.isBoolean(args[1]))
        {
            final boolean enabled = Boolean.parseBoolean(args[1]);
            ConfigManager.setUseCooldownEnabled(enabled);
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Wormhole use cooldowns set to: " + enabled);
            return true;
        }

        final int seconds;
        try
        {
            seconds = Integer.parseInt(args[1]);
        }
        catch (final NumberFormatException e)
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "Invalid cooldown time: " + args[1]);
            usage(sender);
            return true;
        }

        if ((seconds < MIN_SECONDS) || (seconds > MAX_SECONDS))
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "Invalid cooldown time: " + args[1]);
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "Valid cooldown times are between " + MIN_SECONDS + " and " + MAX_SECONDS + " seconds.");
            return true;
        }

        ConfigManager.setUseCooldownSeconds(seconds);
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Wormhole cooldown time set to: " + ConfigManager.getUseCooldownSeconds());
        return true;
    }

    /**
     * Whether an argument is one of the three group names the command used to accept.
     *
     * @param arg
     *            the argument to test
     * @return true if it names a group that no longer exists
     */
    private static boolean isOldGroupName(final String arg)
    {
        return arg.equalsIgnoreCase("one") || arg.equalsIgnoreCase("two") || arg.equalsIgnoreCase("three");
    }

    /**
     * Reports the command's shape and what it is currently set to.
     *
     * <p>The current value is read back through the getter rather than printed from a
     * literal, so this line is evidence the setting round-tripped rather than a restatement
     * of what was typed -- which is precisely what the group form got wrong.
     *
     * @param sender
     *            who asked
     */
    private static void usage(final CommandSender sender)
    {
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Command: /wormhole cooldown <true|false|seconds>");
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Valid cooldown times are between " + MIN_SECONDS + " and " + MAX_SECONDS + " seconds.");
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Cooldowns enabled: " + ConfigManager.isUseCooldownEnabled()
            + ", currently " + ConfigManager.getUseCooldownSeconds() + " seconds.");
    }

}
