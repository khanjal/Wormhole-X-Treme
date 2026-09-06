package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Handler for the '/wormhole owner' admin command.
 */
public class OwnerCommand implements SubCommand
{

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
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

        if (args.length < 2)
        {
            sender.sendMessage(ConfigManager.MessageStrings.gateNotSpecified.toString());
            return false;
        }

        final Stargate s = StargateManager.getStargate(args[1]);
        if (s == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.constructNameInvalid.toString() + "\"" + args[1] + "\"");
            return true;
        }

        if (args.length == 2)
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate: " + s.getGateName() + " Owned by: " + s.getGateOwnerName());
            return true;
        }
        if (args.length == 3)
        {
            assignOwner(s, args[2]);
            s.setupGateSign(true);
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate: " + s.getGateName() + " Now owned by: " + s.getGateOwnerName());
        }
        return true;
    }

    /**
     * Records who owns the gate now.
     *
     * <p>Ownership is stored as a UUID wherever one can be found, so it survives the owner
     * renaming themselves. A name the server has never seen has no UUID to store, and is
     * kept as written rather than refused -- an admin naming an owner before that player
     * first joins is a reasonable thing to do.
     */
    private static void assignOwner(final Stargate s, final String newOwnerName)
    {
        final Player online = Bukkit.getPlayerExact(newOwnerName);
        if (online != null)
        {
            s.setGateOwner(online.getUniqueId().toString());
            s.setGateOwnerName(online.getName());
            return;
        }

        final OfflinePlayer known = findKnownPlayer(newOwnerName, offlinePlayers());
        if (known != null)
        {
            s.setGateOwner(known.getUniqueId().toString());
            s.setGateOwnerName(known.getName() != null ? known.getName() : newOwnerName);
            return;
        }

        s.setGateOwner(newOwnerName);
        s.setGateOwnerName(newOwnerName);
    }

    /** The server's roster, or an empty one if it will not give it up. */
    private static OfflinePlayer[] offlinePlayers()
    {
        try
        {
            return Bukkit.getOfflinePlayers();
        }
        catch (final RuntimeException ignore)
        {
            // a failure here must not break the command
            return new OfflinePlayer[0];
        }
    }

    /**
     * The player of that name the server already knows about.
     *
     * <p>Takes the roster rather than asking Bukkit for it, so the matching can be tested
     * without a server: this project's Mockito cannot mock statics.
     *
     * @param name
     *            the name as the admin typed it, matched without regard to case
     * @param roster
     *            the offline players to search
     * @return the player, or null if nobody matches or the match has never played
     */
    static OfflinePlayer findKnownPlayer(final String name, final OfflinePlayer[] roster)
    {
        for (final OfflinePlayer op : roster)
        {
            if (op == null)
            {
                continue;
            }
            final String oname = op.getName();
            if ((oname != null) && oname.equalsIgnoreCase(name))
            {
                return (op.hasPlayedBefore() || op.isOnline()) ? op : null;
            }
        }
        return null;
    }
}
