package com.wormhole_xtreme.wormhole.command;

import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * The Class Dial.
 *
 * @author alron
 */
public class Dial implements CommandExecutor
{

    /**
     * Connects the activated gate to the gate the player named.
     *
     * <p>Every way this can fail sends its own message and puts the activated gate out again,
     * so each refusal is a guard of its own rather than a level of nesting.
     *
     * @param player
     *            the player
     * @param args
     *            the gate name, and optionally the IDC for a closed remote iris
     */
    private static void doDial(final Player player, final String[] args)
    {
        final Stargate start = StargateManager.removeActivatedStargate(player);
        if (start == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.gateNotActive.toString());
            return;
        }
        if ( !WXPermissions.checkWXPermissions(player, start, PermissionType.DIALER))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return;
        }
        final String startnetwork = CommandUtilities.getGateNetwork(start);
        if (start.getGateName().equals(args[0]))
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.targetIsSelf.toString());
            return;
        }
        final Stargate target = StargateManager.getStargate(args[0]);
        if (target == null)
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
            return;
        }
        final String targetnetwork = CommandUtilities.getGateNetwork(target);
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Dial Target - Gate: \"" + target.getGateName() + "\" Network: \"" + targetnetwork + "\"");
        if ( !startnetwork.equals(targetnetwork))
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString() + " Not on same network.");
            return;
        }
        if (start.isGateIrisActive())
        {
            start.toggleIrisActive(false);
        }
        openRemoteIrisIfIdcMatches(player, target, args);
        if (target.isGateIrisActive())
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Remote Iris is active; provide the IDC to unlock.");
            return;
        }

        if (start.dialStargate(target, false))
        {
            player.sendMessage(ConfigManager.MessageStrings.gateConnected.toString());
            return;
        }
        recoverFailedDial(player, start, target);
    }

    /**
     * Opens the target's iris when the player supplied the code that unlocks it.
     *
     * <p>A wrong code is not reported here: the dial is stopped by the iris still being
     * active, which is what the player is told.
     */
    private static void openRemoteIrisIfIdcMatches(final Player player, final Stargate target, final String[] args)
    {
        if ( !target.getGateIrisDeactivationCode().equals("") && target.isGateIrisActive()
            && (args.length >= 2) && target.getGateIrisDeactivationCode().equals(args[1]))
        {
            target.toggleIrisActive(false);
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "IDC accepted. Iris has been deactivated.");
        }
    }

    /**
     * Handles a dial the gate refused, retrying with force when nothing is really using the
     * target.
     *
     * <p>The usual cause is an activator mapping left behind by a gate that never finished
     * closing. Forcing past a target that is genuinely connected would cut someone else off,
     * so that case is reported instead.
     */
    private static void recoverFailedDial(final Player player, final Stargate start, final Stargate target)
    {
        if (isTargetInUse(start, target))
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.targetIsActive.toString());
            return;
        }

        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Dial recovery: removing stale activator for target " + target.getGateName() + " and retrying with force");
        StargateManager.removeActivatorForStargate(target);
        if (start.dialStargate(target, true))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Dial recovery succeeded for target " + target.getGateName());
            player.sendMessage(ConfigManager.MessageStrings.gateConnected.toString());
            return;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Dial recovery failed for target " + target.getGateName());
        CommandUtilities.closeGate(start, false);
        player.sendMessage(ConfigManager.MessageStrings.targetIsActive.toString());
    }

    /**
     * Whether the target is connected to anything, or anything is connected to it.
     *
     * <p>The sweep reads a live collection, so it treats its own failure as "not in use" and
     * lets the recovery go ahead rather than failing the command.
     */
    private static boolean isTargetInUse(final Stargate start, final Stargate target)
    {
        try
        {
            if (target.isGateActive() || (target.getGateTarget() != null))
            {
                return true;
            }
            for (final Stargate s : StargateManager.getAllGates())
            {
                if ((s != null) && (s != start) && (s.getGateTarget() == target) && s.isGateActive())
                {
                    return true;
                }
            }
        }
        catch (final RuntimeException ignore)
        {
            // a failure here must not break the command
        }
        return false;
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
                    if (CommandUtilities.playerCheck(sender))
                    {
                        doDial((Player) sender, arguments);
                    }
                    return true;
                }
                return false;
            }
        });
    }

}
