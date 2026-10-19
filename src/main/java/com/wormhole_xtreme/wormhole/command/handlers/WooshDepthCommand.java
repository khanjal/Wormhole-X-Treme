package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.List;
import java.util.stream.IntStream;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * Handler for '/wormhole wooshdepth'
 */
public class WooshDepthCommand implements SubCommand
{

    /** The shallowest woosh a gate may be set to: none at all. */
    private static final int MIN_DEPTH = 0;

    /** The deepest. Past this the waves reach further than the gate the shape describes. */
    private static final int MAX_DEPTH = 5;

    /** Said whenever the words given do not name a gate and a depth. */
    private static final String USAGE = ChatText.info(ChatText.usage("/wormhole wooshdepth <gate> [depth]"));

    /**
     * The depths this command accepts, for tab completion.
     *
     * <p>Read from the same two bounds the refusal is, so the list offered and the list allowed
     * cannot drift apart -- a completion that suggests a number the command then refuses is
     * worse than no completion.
     *
     * @return every acceptable depth, as words, lowest first
     */
    public static List<String> depths()
    {
        return IntStream.rangeClosed(MIN_DEPTH, MAX_DEPTH)
            .mapToObj(String::valueOf).toList();
    }

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        if ((args.length != 2) && (args.length != 3))
        {
            sendUsage(sender);
            return true;
        }

        // One lookup rather than isStargate followed by getStargate: the registry is
        // concurrent, so asking twice can answer differently.
        final Stargate stargate = StargateManager.getStargate(args[1]);
        if (stargate == null)
        {
            sender.sendMessage(CommandUtilities.noGateCalled(args[1]));
            sendUsage(sender);
            return true;
        }
        if (!stargate.isGateCustom())
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.woosh.not-custom",
                ChatText.name(stargate.getGateName()),
                ChatText.command("/wormhole custom " + stargate.getGateName() + " true"))));
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
     * Says how the command is spelled and what it takes.
     *
     * @param sender
     *            who to tell
     */
    private static void sendUsage(final CommandSender sender)
    {
        sender.sendMessage(USAGE);
        sender.sendMessage(validRange(sender));
    }

    /**
     * Said with the usage line every time the command is refused for its shape.
     *
     * @param sender
     *            who will read it
     * @return the range of depths accepted
     */
    private static String validRange(final CommandSender sender)
    {
        return ChatText.info(Messages.get(sender, "gate.woosh.range", ChatText.value(String.valueOf(MIN_DEPTH)),
            ChatText.value(String.valueOf(MAX_DEPTH))));
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
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.woosh.current", ChatText.name(name),
            ChatText.value(String.valueOf(stargate.getGateCustomWooshDepth())))));
        sender.sendMessage(validRange(sender));
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
        if ((wooshDepth < MIN_DEPTH) || (wooshDepth > MAX_DEPTH))
        {
            rejectDepth(sender, typed);
            return;
        }
        stargate.setGateCustomWooshDepth(wooshDepth);
        // Kept alongside so the animation does not square it per block.
        stargate.setGateCustomWooshDepthSquared(wooshDepth * wooshDepth);
        StargateDBManager.saveStargate(stargate);
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.woosh.set", ChatText.name(name),
            ChatText.value(String.valueOf(stargate.getGateCustomWooshDepth())))));
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
        sender.sendMessage(ChatText.error(Messages.get(sender, "gate.woosh.invalid", ChatText.command(typed))));
        sender.sendMessage(validRange(sender));
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
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.woosh.shape-owns")));
        }
    }

}
