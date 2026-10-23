package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * Handler for '/wormhole redstone'
 */
public class RedstoneCommand implements SubCommand
{
    private static final String USAGE = "/wormhole redstone <gate> [true|false]";


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

        if ((args.length != 2) && (args.length != 3))
        {
            sendUsage(sender);
            return true;
        }

        final Stargate stargate = StargateManager.isStargate(args[1])
            ? StargateManager.getStargate(args[1])
            : null;
        if (stargate == null)
        {
            sender.sendMessage(CommandUtilities.noGateCalled(sender, args[1]));
            sendUsage(sender);
            return true;
        }

        if (args.length == 2)
        {
            reportWiring(sender, args[1], stargate);
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.redstone.change-hint",
                ChatText.command("true"), ChatText.command("false"))));
            return true;
        }

        setWiring(sender, args[1], stargate, args[2]);
        return true;
    }

    /**
     * Says how the gate is wired, without changing it.
     *
     * @param sender
     *            who asked
     * @param name
     *            the gate's name as they typed it
     * @param stargate
     *            the gate
     */
    private static void reportWiring(final CommandSender sender, final String name, final Stargate stargate)
    {
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.redstone.state", ChatText.name(name),
            ChatText.value(String.valueOf(stargate.isGateRedstonePowered())))));
    }

    /**
     * Wires the gate up, or refuses a word that is not a yes or a no.
     *
     * <p>The check matters more than it looks: {@code Boolean.parseBoolean} answers false for
     * anything it does not recognise, so without it "yes" would quietly unwire a gate and
     * report that it had worked.
     *
     * @param sender
     *            who asked
     * @param name
     *            the gate's name as they typed it
     * @param stargate
     *            the gate
     * @param value
     *            what they typed for the setting
     */
    private static void setWiring(final CommandSender sender, final String name,
        final Stargate stargate, final String value)
    {
        if (!CommandUtilities.isBoolean(value))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.redstone.not-a-switch",
                ChatText.command("true"), ChatText.command("false"), ChatText.command(value))));
            sendUsage(sender);
            return;
        }
        stargate.setGateRedstonePowered(Boolean.parseBoolean(value.trim()));
        stargate.setupRedstone(stargate.isGateRedstonePowered());
        StargateDBManager.saveStargate(stargate);
        reportWiring(sender, name, stargate);
    }

    /**
     * The usage line every refusal ends with.
     *
     * @param sender
     *            who asked
     */
    private static void sendUsage(final CommandSender sender)
    {
        sender.sendMessage(ChatText.info(ChatText.usage(sender, USAGE)));
    }

}
