package com.wormhole_xtreme.wormhole.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.model.GateQuota;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * The Class WXList.
 *
 * <p>{@code /wormhole list [network|mine|owner <player>|top]}. The three keywords take precedence
 * over a network of the same name (#638).
 *
 * @author alron
 */
public class WXList implements CommandExecutor
{

    /** Lists the sender's own gates, to a player without {@code wormhole.list} too. */
    public static final String MINE = "wormhole.list.mine";

    /** Lists another player's gates, to a player without {@code wormhole.list} too. */
    public static final String OWNER = "wormhole.list.owner";

    /** How many gates {@code list top} shows. */
    private static final int TOP_LIMIT = 10;

    /** Past this many visible characters a message is truncated by the client rather than wrapped. */
    private static final int LINE_BUDGET = 75;

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender, new Callable<Boolean>()
        {
            @Override
            public Boolean call() throws Exception
            {
                return listGates(sender, args);
            }
        });
    }

    /**
     * Prints the gate list, or refuses a player who may not see it.
     *
     * @return false to have the usage line shown, for {@code owner} with no name
     */
    private static boolean listGates(final CommandSender sender, final String[] args)
    {
        final String first = (args.length > 0) ? args[0].trim() : null;
        if ("mine".equalsIgnoreCase(first))
        {
            listMine(sender);
            return true;
        }
        if ("owner".equalsIgnoreCase(first))
        {
            return listOwner(sender, args);
        }
        if (!mayList(sender))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "common.permission-no")));
            return true;
        }
        if ("top".equalsIgnoreCase(first))
        {
            listTop(sender);
            return true;
        }

        // Optional network filter: /wx list [network]
        final String filterNet = first;
        final ArrayList<Stargate> gates = gatesOn(filterNet);

        final String header;
        if (filterNet != null)
        {
            header = Messages.get(sender, "gate.list.network", ChatText.name(filterNet), ChatText.heading(" ::"));
        }
        else
        {
            header = Messages.get(sender, "gate.list.all", ChatText.heading("::"));
        }
        sender.sendMessage(ChatText.info(header));
        if (gates.isEmpty())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.list.none")));
        }
        sendGateNames(sender, gates);
        sendStanding(sender);
        return true;
    }

    /** Whether the sender may see the whole list: the console, or a player holding the list check. */
    private static boolean mayList(final CommandSender sender)
    {
        return !CommandUtilities.playerCheck(sender)
            || WXPermissions.checkWXPermissions((Player) sender, PermissionType.LIST);
    }

    /** The gate limit line a limited player is shown after a list; nothing for anyone else. */
    private static void sendStanding(final CommandSender sender)
    {
        if (sender instanceof Player player)
        {
            final String standing = GateQuota.standing(player);
            if (standing != null)
            {
                sender.sendMessage(ChatText.info(standing));
            }
        }
    }

    /** {@code list mine}: the gates the sender owns, on the list check or {@link #MINE}. */
    private static void listMine(final CommandSender sender)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.list.player-only")));
            return;
        }
        if (!mayList(sender) && !player.hasPermission(MINE))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "common.permission-no")));
            return;
        }
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.list.mine", ChatText.heading("::"))));
        sendOwned(sender, player.getUniqueId(), player.getName());
        sendStanding(sender);
    }

    /**
     * {@code list owner <player>}: another player's gates, online or not, to the console or a
     * player on the list check or {@link #OWNER}.
     *
     * @return false when no player was named
     */
    private static boolean listOwner(final CommandSender sender, final String[] args)
    {
        if (!mayList(sender) && !sender.hasPermission(OWNER))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "common.permission-no")));
            return true;
        }
        if ((args.length < 2) || args[1].isBlank())
        {
            return false;
        }
        final String typed = args[1].trim();
        final OfflinePlayer owner = CommandUtilities.findPlayer(typed);
        if (owner == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "stats.unknown-player", ChatText.name(typed))));
            return true;
        }
        final String name = (owner.getName() == null) ? typed : owner.getName();
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.list.owner", ChatText.name(name),
            ChatText.heading(" ::"))));
        sendOwned(sender, owner.getUniqueId(), name);
        return true;
    }

    /** Sends the gates owned by that player, or the none-found line. */
    private static void sendOwned(final CommandSender sender, final UUID id, final String name)
    {
        final List<Stargate> gates = StargateManager.getAllGates().stream()
            .filter(g -> g.isOwnedBy(id, name))
            .collect(Collectors.toCollection(ArrayList::new));
        if (gates.isEmpty())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.list.none")));
        }
        sendGateNames(sender, gates);
    }

    /** {@code list top}: the gates with the most trips, each with its count. */
    private static void listTop(final CommandSender sender)
    {
        final List<Stargate> busiest = StargateManager.getAllGates().stream()
            .filter(g -> PlayerStats.gateTrips(g.getGateName()) > 0)
            .sorted(Comparator.<Stargate>comparingInt(g -> PlayerStats.gateTrips(g.getGateName())).reversed()
                .thenComparing(Stargate::getGateName, String.CASE_INSENSITIVE_ORDER))
            .limit(TOP_LIMIT)
            .toList();
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.list.top", ChatText.heading("::"))));
        if (busiest.isEmpty())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.list.top-none")));
            return;
        }
        for (int i = 0; i < busiest.size(); i++)
        {
            final Stargate g = busiest.get(i);
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.list.top-entry", String.valueOf(i + 1),
                ChatText.name(g.getGateName()), ChatText.value(String.valueOf(PlayerStats.gateTrips(g.getGateName()))))));
        }
    }

    /**
     * The gates a filter asks for, or all of them when there is no filter.
     *
     * <p>"Public" means the gates on no network at all rather than one of that name. Without
     * it a gate that was never given a network would not appear on any filtered list.
     */
    private static ArrayList<Stargate> gatesOn(final String filterNet)
    {
        final boolean filterPublic = (filterNet != null) && filterNet.equalsIgnoreCase("Public");
        // No filter takes everything. "Public" means the gates on no network at all,
        // which is a different question from being on a network of that name.
        return StargateManager.getAllGates().stream()
            .filter(g -> (filterNet == null)
                || (filterPublic ? (g.getGateNetwork() == null) : namedNetwork(g, filterNet)))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Whether this gate is on the network of that name. */
    private static boolean namedNetwork(final Stargate g, final String filterNet)
    {
        return (g.getGateNetwork() != null)
            && g.getGateNetwork().getNetworkName().equalsIgnoreCase(filterNet);
    }

    /**
     * Sends the names, several to a message, breaking before a message grows too long.
     *
     * <p>A single message past the client's limit is truncated rather than wrapped, so the
     * names off the end would simply not be shown.
     */
    private static void sendGateNames(final CommandSender sender, final List<Stargate> gates)
    {
        final String separator = Messages.get(sender, "gate.list.separator");
        StringBuilder sb = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < gates.size(); i++)
        {
            final String gateName = gates.get(i).getGateName();
            sb.append(ChatText.name(gateName));
            visible += gateName.length();
            if (i != (gates.size() - 1))
            {
                sb.append(separator);
                visible += separator.length();
            }
            if (visible >= LINE_BUDGET)
            {
                sender.sendMessage(ChatText.info(sb.toString()));
                sb = new StringBuilder();
                visible = 0;
            }
        }
        if (!sb.isEmpty())
        {
            sender.sendMessage(ChatText.info(sb.toString()));
        }
    }
}
