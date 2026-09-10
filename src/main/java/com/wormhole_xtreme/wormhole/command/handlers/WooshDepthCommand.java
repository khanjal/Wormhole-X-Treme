package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;

/**
 * Handler for '/wormhole wooshdepth'
 */
public class WooshDepthCommand implements SubCommand
{

    /** Said with the usage line every time the command is refused for its shape. */
    private static final String VALID_RANGE = "Valid depth: 0 - 5";

    /** Said whenever the words given do not name a gate and a depth. */
    private static final String USAGE = "Command: /wormhole wooshdepth [stargate] <depth>";

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (refusedForPermissions(sender))
        {
            return true;
        }
        if ((args.length != 2) && (args.length != 3))
        {
            sendUsage(sender);
            return false;
        }

        // One lookup rather than isStargate followed by getStargate: the registry is
        // concurrent, so asking twice can answer differently.
        final Stargate stargate = StargateManager.getStargate(args[1]);
        if (stargate == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.TARGET_INVALID.toString());
            sendUsage(sender);
            return true;
        }
        if (!stargate.isGateCustom())
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "Stargate is not in custom mode. Set it with the '/wormhole custom' command");
            return true;
        }

        if (args.length == 2)
        {
            reportDepth(sender, args[1], stargate);
        }
        else
        {
            setDepth(sender, args[1], args[2], stargate);
        }
        return true;
    }

    /**
     * Whether this sender may not change gate settings.
     *
     * <p>Gate management was never actually gated: none of these commands checked a
     * permission at all, so any player able to run /wormhole could reconfigure or reassign
     * any gate on the server. wormhole.config is what an admin already needs for
     * /wormhole config, so it is reused rather than inventing a second node meaning the same
     * thing. The console is not a player and is not asked.
     *
     * @param sender
     *            who is asking
     * @return true if they were refused and told so
     */
    private static boolean refusedForPermissions(final CommandSender sender)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        return false;
    }

    /**
     * Says how the command is spelled and what it takes.
     *
     * @param sender
     *            who to tell
     */
    private static void sendUsage(final CommandSender sender)
    {
        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + USAGE);
        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + VALID_RANGE);
    }

    /**
     * Reports the depth a gate currently has.
     *
     * @param sender
     *            who to tell
     * @param name
     *            the gate's name, as the player typed it
     * @param stargate
     *            the gate
     */
    private static void reportDepth(final CommandSender sender, final String name, final Stargate stargate)
    {
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + name + " woosh depth is currently: " + stargate.getGateCustomWooshDepth());
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + VALID_RANGE);
        warnIfShapeOwnsTheWaves(sender, stargate);
    }

    /**
     * Sets a gate's depth, if the word given is a number in range.
     *
     * <p>A word that is not a number and a number out of range are the same mistake to the
     * player, so they get the same answer.
     *
     * @param sender
     *            who to tell
     * @param name
     *            the gate's name, as the player typed it
     * @param typed
     *            the depth they typed
     * @param stargate
     *            the gate
     */
    private static void setDepth(final CommandSender sender, final String name,
                                 final String typed, final Stargate stargate)
    {
        final int wooshDepth;
        try
        {
            wooshDepth = Integer.parseInt(typed.trim());
        }
        catch (final NumberFormatException e)
        {
            rejectDepth(sender, typed);
            return;
        }
        if ((wooshDepth < 0) || (wooshDepth > 5))
        {
            rejectDepth(sender, typed);
            return;
        }
        stargate.setGateCustomWooshDepth(wooshDepth);
        // Kept alongside so the animation does not square it per block.
        stargate.setGateCustomWooshDepthSquared(wooshDepth * wooshDepth);
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + name + " woosh depth set to: " + stargate.getGateCustomWooshDepth());
        warnIfShapeOwnsTheWaves(sender, stargate);
    }

    /**
     * Says a depth was no good, quoting back what was typed.
     *
     * @param sender
     *            who to tell
     * @param typed
     *            what they typed
     */
    private static void rejectDepth(final CommandSender sender, final String typed)
    {
        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "Invalid woosh depth: " + typed);
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + VALID_RANGE);
    }

    /**
     * Says so when this setting cannot move what the player is presumably trying to move.
     *
     * <p>The woosh animation prefers a shape's own {@code :W#N} waves and only derives waves
     * from this depth when the shape authors none. Every shipped shape authors them, so on
     * an ordinary gate this setting changes no visuals at all -- it still governs how far
     * from the gate the block and entity protection reaches, which is a real effect, just
     * not the one the name suggests. Saying that plainly beats letting someone set a number,
     * watch nothing happen, and conclude the feature is broken.
     *
     * @param sender who to tell
     * @param stargate the gate whose depth was just set or read
     */
    private static void warnIfShapeOwnsTheWaves(final CommandSender sender, final Stargate stargate)
    {
        if ((stargate.getGateWooshBlocks() != null) && !stargate.getGateWooshBlocks().isEmpty())
        {
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + "Note: this gate's shape defines its own woosh waves, so depth will not change "
                + "how it looks -- it still sets how far protection reaches from the gate.");
        }
    }

}
