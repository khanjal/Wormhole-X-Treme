package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Handler for '/wormhole redstone'
 */
public class RedstoneCommand implements SubCommand
{
    private static final String USAGE = "Command: /wormhole redstone [stargate] <boolean>";
    private static final String VALID_OPTIONS = "Valid boolean options are: true and false";


    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Gate management was never actually gated: none of these commands checked a
        // permission at all, so any player able to run /wormhole could reconfigure or
        // reassign any gate on the server. wormhole.config is what an admin already needs
        // for /wormhole config, so it is reused here rather than inventing a second
        // admin-only node that would mean the same thing.
        if ((sender instanceof Player player)
            && !WXPermissions.checkWXPermissions(player, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }

        if ((args.length != 2) && (args.length != 3))
        {
            sendUsage(sender);
            // False, unlike every other refusal here: the caller prints the usage again for
            // a command it could not parse at all, where a named gate that does not exist is
            // a complete command with a wrong answer.
            return false;
        }

        final Stargate stargate = StargateManager.isStargate(args[1])
            ? StargateManager.getStargate(args[1])
            : null;
        if (stargate == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.TARGET_INVALID.toString());
            sendUsage(sender);
            return true;
        }

        if (args.length == 2)
        {
            reportWiring(sender, args[1], stargate);
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + VALID_OPTIONS);
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
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + name + " is redstone powered: " + stargate.isGateRedstonePowered());
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
        if (!com.wormhole_xtreme.wormhole.command.CommandUtilities.isBoolean(value))
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "Invalid boolean option: " + value);
            sendUsage(sender);
            return;
        }
        stargate.setGateRedstonePowered(Boolean.parseBoolean(value.trim()));
        stargate.setupRedstone(stargate.isGateRedstonePowered());
        reportWiring(sender, name, stargate);
    }

    /**
     * The two lines every refusal ends with.
     *
     * @param sender
     *            who asked
     */
    private static void sendUsage(final CommandSender sender)
    {
        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + USAGE);
        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + VALID_OPTIONS);
    }

}
