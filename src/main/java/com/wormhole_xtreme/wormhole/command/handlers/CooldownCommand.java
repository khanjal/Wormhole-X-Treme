package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * Handler for '/wormhole cooldown'
 *
 * <p>Takes a plain number of seconds. It used to take a group name -- one, two or three --
 * but none of those three settings were ever registered in {@code DefaultSettings}, so the
 * write went nowhere and the read fell back to a hardcoded literal: the command reported
 * "cooldown time set to: 300" and the cooldown stayed at 120. Only one of the three groups
 * was read by anything at all, so a single setting is what the feature actually was.
 *
 * <p>The bounds below apply to what is typed here, not to {@code config.yml} -- an admin
 * editing the file directly is trusted with whatever they write, and
 * {@code StargateRestrictions.cooldownTicks} is what keeps an extreme value in that file
 * schedulable.
 */
public class CooldownCommand implements SubCommand
{

    /** Shortest cooldown worth having, in seconds. Below this the timer is not really a wait. */
    private static final int MIN_SECONDS = 15;

    /** Longest cooldown accepted, in seconds. An hour. */
    private static final int MAX_SECONDS = 3600;

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Gate management was never actually gated: none of these commands checked a
        // permission at all, so any player able to run /wormhole could reconfigure or
        // reassign any gate on the server. wormhole.config is what an admin already needs
        // for /wormhole config, so it is reused here rather than inventing a second
        // admin-only node that would mean the same thing.
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
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
            sender.sendMessage(ChatText.error("Cooldown groups are gone; they never took effect. Give the seconds instead."));
            usage(sender);
            return true;
        }

        if (CommandUtilities.isBoolean(args[1]))
        {
            final boolean enabled = Boolean.parseBoolean(args[1]);
            ConfigManager.setUseCooldownEnabled(enabled);
            sender.sendMessage(ChatText.info("Wormhole use cooldowns set to: " + ChatText.value(String.valueOf(enabled)) + "."));
            return true;
        }

        final int seconds;
        try
        {
            seconds = Integer.parseInt(args[1]);
        }
        catch (final NumberFormatException e)
        {
            sender.sendMessage(ChatText.error("Invalid cooldown time: " + ChatText.command(args[1]) + "."));
            usage(sender);
            return true;
        }

        if ((seconds < MIN_SECONDS) || (seconds > MAX_SECONDS))
        {
            sender.sendMessage(ChatText.error("Invalid cooldown time: " + ChatText.command(args[1]) + ". Give "
                + range() + " seconds."));
            return true;
        }

        ConfigManager.setUseCooldownSeconds(seconds);
        sender.sendMessage(ChatText.info("Wormhole cooldown time set to: "
            + ChatText.value(String.valueOf(ConfigManager.getUseCooldownSeconds())) + " seconds."));
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

    /** The seconds accepted, as {@code 15 to 3600}. */
    private static String range()
    {
        return ChatText.value(String.valueOf(MIN_SECONDS)) + " to " + ChatText.value(String.valueOf(MAX_SECONDS));
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
        // Two lines rather than one <true|false|seconds> alternation. Sharing a bracket
        // group with two literals made the third read like a literal too -- as though the
        // word "seconds" were what you typed.
        sender.sendMessage(ChatText.info(ChatText.usage("/wormhole cooldown <seconds>") + " sets the wait between trips, "
            + range() + "."));
        sender.sendMessage(ChatText.info(ChatText.usage("/wormhole cooldown true|false") + " switches cooldowns on or off."));
        sender.sendMessage(ChatText.info("Cooldowns enabled: " + ChatText.value(String.valueOf(ConfigManager.isUseCooldownEnabled()))
            + ", currently " + ChatText.value(String.valueOf(ConfigManager.getUseCooldownSeconds())) + " seconds."));
    }

}
