package com.wormhole_xtreme.wormhole.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamAnimation;
import com.wormhole_xtreme.wormhole.model.beam.BeamTravel;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * The Class Go.
 *
 * <p>A shortcut for two different things, tried in order: a gate, under gate permissions, for
 * whoever holds {@code wormhole.go} -- an admin/debug node, same as it always was; then a beam
 * destination or one of the player's own places, under beaming's own permission, for everyone
 * else. That split is deliberate rather than one blanket check up front: a player with no
 * gate-admin access should still be able to use this as a shortcut for
 * {@code /wormhole beam to}, and a name that exists only as a gate should read as "no gate or
 * beam destination named that" rather than leak whether a gate by that name exists to someone
 * who cannot use it anyway.
 *
 * <p>Both branches run the same {@link BeamAnimation} sequence rather than the gate branch
 * teleporting instantly. Before this, a gate reached via {@code go} was a blink -- an admin
 * skipping the walk to a gate got nothing a beamed player did not, and {@code go} felt like
 * two different commands depending on what it resolved to. It still is not the gate's own
 * event-horizon woosh, and does not touch it: that effect belongs to actually walking through
 * a gate, a different moment this command has never played any part in.
 *
 * @author alron
 */
public class Go implements CommandExecutor
{

    /**
     * Do go.
     *
     * @param player
     *            the player
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doGo(final Player player, final String[] args)
    {
        if (args.length != 1)
        {
            return false;
        }
        final String name = args[0].trim().replace("\n", "").replace("\r", "");

        if (WXPermissions.checkWXPermissions(player, PermissionType.GO))
        {
            final Stargate s = StargateManager.getStargate(name);
            if (s != null)
            {
                BeamAnimation.start(player, s.getGatePlayerTeleportLocation(), s.getGateName());
                return true;
            }
        }

        if (BeamTravel.travelTo(player, name))
        {
            return true;
        }

        player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
            + "No gate or beam destination named: " + args[0]);
        return true;
    }

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender, new java.util.concurrent.Callable<Boolean>()
        {
            @Override
            public Boolean call() throws Exception
            {
                final String[] arguments = CommandUtilities.commandEscaper(args);
                if ((arguments.length < 3) && (arguments.length > 0))
                {
                    return CommandUtilities.playerCheck(sender)
                        ? doGo((Player) sender, arguments)
                        : true;
                }
                return false;
            }
        });
    }

}
