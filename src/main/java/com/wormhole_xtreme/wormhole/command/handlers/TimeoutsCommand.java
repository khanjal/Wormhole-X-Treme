package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;

/**
 * Handler for '/wormhole activate_timeout' and '/wormhole timeout' (shutdown_timeout)
 */
public class TimeoutsCommand implements SubCommand
{
    /** Past a minute a gate is holding a wormhole open long enough to be somebody else's problem. */
    private static final int MAX_SECONDS = 60;

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        if (args.length == 0)
        {
            return false;
        }

        if (args[0].equalsIgnoreCase("activate_timeout"))
        {
            // A gate lit for under ten seconds is not really dialable, so that is the floor.
            handleTimeout(sender, args, "activate_timeout", 10,
                ConfigManager::getTimeoutActivate, ConfigManager::setTimeoutActivate);
            return true;
        }
        if (args[0].equalsIgnoreCase("timeout") || args[0].equalsIgnoreCase("shutdown_timeout"))
        {
            // 0 is legal here and means the wormhole never closes on its own.
            handleTimeout(sender, args, "shutdown_timeout", 0,
                ConfigManager::getTimeoutShutdown, ConfigManager::setTimeoutShutdown);
            return true;
        }
        return false;
    }


    /**
     * Reads or writes one timeout setting.
     *
     * <p>Both timeouts behave identically apart from their floor, so they share this. The
     * ceiling is 60 for both: past a minute a gate is holding a wormhole open long enough to
     * be somebody else's problem.
     *
     * @param sender
     *            who to tell
     * @param args
     *            the full argument array, the setting name at index 0
     * @param label
     *            what to call the setting when talking to the player
     * @param floor
     *            the lowest value this setting accepts
     * @param read
     *            reads the current value
     * @param write
     *            stores a new value
     */
    private static void handleTimeout(final CommandSender sender, final String[] args,
                                         final String label, final int floor,
                                         final IntSupplier read, final IntConsumer write)
    {
        if (args.length != 2)
        {
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + "Current " + label + " is: " + read.getAsInt());
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + range(floor));
            return;
        }

        final int timeout;
        try
        {
            timeout = Integer.parseInt(args[1]);
        }
        catch (final NumberFormatException e)
        {
            reject(sender, label, args[1], floor);
            return;
        }
        if ((timeout < floor) || (timeout > MAX_SECONDS))
        {
            reject(sender, label, args[1], floor);
            return;
        }

        write.accept(timeout);
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + label + " set to: " + read.getAsInt());
    }

    /**
     * Says a timeout was no good, quoting back what was typed.
     *
     * @param sender
     *            who to tell
     * @param label
     *            the setting's name
     * @param typed
     *            what they typed
     * @param floor
     *            the lowest value this setting accepts
     */
    private static void reject(final CommandSender sender, final String label,
                                  final String typed, final int floor)
    {
        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
            + "Invalid " + label + ": " + typed);
        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + range(floor));
    }

    /**
     * How a setting's accepted range reads to a player.
     *
     * @param floor
     *            the lowest value it accepts
     * @return the sentence to print
     */
    private static String range(final int floor)
    {
        return "Valid timeout is between " + floor + " and " + MAX_SECONDS + " seconds.";
    }

}
