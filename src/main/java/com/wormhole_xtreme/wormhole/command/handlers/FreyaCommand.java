package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;
import com.wormhole_xtreme.wormhole.model.freya.FreyaPreferences;

/**
 * Handler for '/wormhole freya'.
 *
 * <p>For Freya, a black cat who was twenty years old, and who died in December 2025. She
 * followed people from room to room for two decades. This is the smallest possible version of
 * that, and it is here because somebody who worked on this plugin missed her.
 *
 * <p>The command is registered hidden: it dispatches, but it is left out of the help text and
 * out of tab completion. Nothing in the plugin advertises it. You have to already know.
 *
 * <p>It is a toggle rather than a spawner, which is the difference between an easter egg and a
 * way to fill a world with cats. Typing it twice turns her off again; {@code on} and
 * {@code off} say so explicitly for anyone who wants to be sure which way they just went. The
 * preference is the only thing that persists -- the cat is spawned fresh on every join and
 * removed on every quit, and never written to a world.
 */
public class FreyaCommand implements SubCommand
{
    /**
     * The node that can take her away.
     *
     * <p>Declared {@code default: true} in plugin.yml, alongside {@code wormhole.ring.use} and
     * {@code wormhole.beam.use}. Bukkit has no such thing as a deny node -- an admin denies by
     * negating an ordinary one -- so a permission to prevent this and a permission to allow it
     * are the same node, and the default decides which it reads as. Everybody has her; an
     * operator who needs to take her away from one player, or from a whole server, can.
     */
    public static final String PERMISSION = "wormhole.freya";

    private static final String ON = "on";
    private static final String OFF = "off";

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (!(sender instanceof Player player))
        {
            // There is nobody for her to follow. Console and command blocks are told plainly
            // rather than silently doing nothing.
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "Only a player can do that.");
            return true;
        }

        // hasPermission alone, with no isOp shortcut. Everything else in this plugin ors in
        // isOp because its nodes default to op; this one defaults to true, so or-ing isOp back
        // in would mean an operator was the one player an admin could not take her away from.
        if (!player.hasPermission(PERMISSION))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }

        final boolean wanted = wanted(args, player.getUniqueId());
        FreyaPreferences.setEnabled(player.getUniqueId(), wanted);

        if (wanted)
        {
            return welcome(player);
        }
        FreyaCompanion.removeFor(player.getUniqueId());
        player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "She curls up somewhere else.");
        return true;
    }

    /**
     * What the player is asking for.
     *
     * <p>A bare {@code /wormhole freya} flips whatever they had, which is what a one-word
     * easter egg should do. Anything else is read as {@code off} only when it says so; an
     * unrecognised word turns her on rather than refusing, because a refusal message is a
     * worse thing to meet than a cat.
     *
     * @param args
     *            the full argument array, the subcommand at index 0
     * @param playerId
     *            who is asking
     * @return true if they want a companion
     */
    private static boolean wanted(final String[] args, final UUID playerId)
    {
        if (args.length < 2)
        {
            return !FreyaPreferences.isEnabled(playerId);
        }
        final String said = args[1].toLowerCase(Locale.ROOT);
        if (OFF.equals(said))
        {
            return false;
        }
        return ON.equals(said) || !FreyaPreferences.isEnabled(playerId);
    }

    /**
     * Spawns her and says so, or says nothing appeared.
     *
     * @param player
     *            her owner
     * @return true, always: the command was understood either way
     */
    private static boolean welcome(final Player player)
    {
        if (FreyaCompanion.spawnFor(player) == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "She did not come. Try again somewhere else.");
            return true;
        }
        player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + FreyaCompanion.NAME + " pads over and sits down beside you.");
        return true;
    }
}
