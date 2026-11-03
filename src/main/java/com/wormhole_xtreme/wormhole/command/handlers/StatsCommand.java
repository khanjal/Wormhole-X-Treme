package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * {@code /wormhole stats [player]}: a player's lifetime trips, gates built, ring trips and beams (#237).
 *
 * <p>Your own with {@link #PERMISSION}, anybody's with {@link #OTHERS}. The console has no stats of
 * its own, so it names a player.
 */
public class StatsCommand implements SubCommand
{
    /** Your own stats; true by default. */
    public static final String PERMISSION = "wormhole.stats";

    /** Anybody's stats; op by default. */
    public static final String OTHERS = "wormhole.stats.others";

    // Bukkit reads the boolean as "handled"; false asks the dispatcher for the usage line.
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (args.length > 2)
        {
            return false;
        }
        final Player self = (sender instanceof Player player) ? player : null;
        if (args.length == 2 && ((self == null) || !self.getName().equalsIgnoreCase(args[1])))
        {
            showOther(sender, args[1]);
            return true;
        }
        if (self == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "stats.console-names-player",
                ChatText.command("/wormhole stats <player>"))));
            return true;
        }
        if (!self.hasPermission(PERMISSION))
        {
            self.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }
        show(self, self.getName(), self.getUniqueId());
        return true;
    }

    /**
     * Shows another player's stats, to a sender holding {@link #OTHERS}.
     *
     * @param sender
     *            who asked
     * @param name
     *            the name they typed
     */
    private static void showOther(final CommandSender sender, final String name)
    {
        if (!sender.hasPermission(OTHERS))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        final OfflinePlayer subject = findPlayer(name);
        if (subject == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "stats.unknown-player", ChatText.name(name))));
            return;
        }
        show(sender, (subject.getName() == null) ? name : subject.getName(), subject.getUniqueId());
    }

    /**
     * Sends one player's four counts.
     *
     * @param sender
     *            who reads them
     * @param name
     *            whose they are, as shown
     * @param playerId
     *            whose they are
     */
    private static void show(final CommandSender sender, final String name, final UUID playerId)
    {
        final PlayerStats.Counts counts = PlayerStats.of(playerId);
        sender.sendMessage(ChatText.info(Messages.get(sender, "stats.header", ChatText.name(name))));
        sender.sendMessage(ChatText.info(Messages.get(sender, "stats.trips", count(counts.trips()))));
        sender.sendMessage(ChatText.info(Messages.get(sender, "stats.gates-built", count(counts.gatesBuilt()))));
        sender.sendMessage(ChatText.info(Messages.get(sender, "stats.rings", count(counts.rings()))));
        sender.sendMessage(ChatText.info(Messages.get(sender, "stats.beams", count(counts.beams()))));
    }

    private static String count(final int value)
    {
        return ChatText.value(String.valueOf(value));
    }

    /**
     * Finds a player by name, whether or not they are online, as {@code /wormhole ring} does.
     *
     * @param name
     *            the name typed
     * @return the player, or null if the server has never seen them
     */
    // getOfflinePlayer(String) is deprecated on every supported version with no Spigot replacement;
    // RingCommand.findPlayer says why.
    @SuppressWarnings("deprecation")
    private static OfflinePlayer findPlayer(final String name)
    {
        try
        {
            final Player online = Bukkit.getPlayerExact(name);
            if (online != null)
            {
                return online;
            }
            final OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            // getOfflinePlayer invents a profile for a name nobody has used, so only one who has played is real.
            return offline.hasPlayedBefore() ? offline : null;
        }
        // A server that will not give the roster up is answered as an unknown name, as OwnerCommand does.
        catch (final RuntimeException refused)
        {
            return null;
        }
    }
}
