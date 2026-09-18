package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;
import com.wormhole_xtreme.wormhole.model.freya.FreyaPreferences;

/**
 * Handler for '/wormhole freya', a hidden toggle for a companion only you can see.
 *
 * <p>For Freya, a black cat who was twenty years old, and who died in December 2025. She
 * followed people from room to room for two decades. This is the smallest possible version of
 * that, and it is here because somebody who worked on this plugin missed her.
 */
public class FreyaCommand implements SubCommand
{
    /** Defaults to true in plugin.yml; negating it is how an operator takes her away. */
    public static final String PERMISSION = "wormhole.freya";

    private static final String ON = "on";
    private static final String OFF = "off";

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "Only a player can do that.");
            return true;
        }

        // No isOp shortcut, unlike other commands: it would stop a negated node applying to ops.
        if (!player.hasPermission(PERMISSION))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }

        final boolean wanted = wanted(args, player.getUniqueId());
        FreyaPreferences.setEnabled(player.getUniqueId(), wanted);

        if (wanted)
        {
            welcome(player);
            return true;
        }
        FreyaCompanion.removeFor(player.getUniqueId());
        player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "She curls up somewhere else.");
        return true;
    }

    /**
     * What the player is asking for: {@code on} and {@code off} say so, and a bare or
     * unrecognised word flips rather than being refused.
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
     */
    private static void welcome(final Player player)
    {
        if (FreyaCompanion.isAway(player.getUniqueId()))
        {
            // Asleep or hunted: the listener brings her once that is over.
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "She will be along soon.");
            return;
        }
        if (FreyaCompanion.spawnFor(player) == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "She did not come. Try again somewhere else.");
            return;
        }
        player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + FreyaCompanion.NAME + " pads over and sits down beside you.");
        player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + ChatColor.ITALIC + FreyaCompanion.YEARS);
    }
}
