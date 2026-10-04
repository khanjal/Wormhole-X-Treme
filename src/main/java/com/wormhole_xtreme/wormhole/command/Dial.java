package com.wormhole_xtreme.wormhole.command;

import java.util.concurrent.Callable;
import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The Class Dial.
 *
 * @author alron
 */
public class Dial implements CommandExecutor
{
    /** What {@code /dial} takes. */
    static final String USAGE = "/dial <gate> [idc]";


    /**
     * Connects the activated gate to the gate the player named.
     *
     * <p>Every way this can fail sends its own message and puts the activated gate out again,
     * so each refusal is a guard of its own rather than a level of nesting.
     *
     * @param args
     *            the gate name, and optionally the IDC for a closed remote iris
     */
    private static void doDial(final Player player, final String[] args)
    {
        final Stargate start = StargateManager.removeActivatedStargate(player);
        if (start == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.GATE_NOT_ACTIVE.toString());
            return;
        }
        if ( !WXPermissions.checkWXPermissions(player, start, PermissionType.DIALER))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        dialFrom(player, start, args);
    }

    /**
     * Dials from a gate to the one named, under the same rules as {@code /dial}: same network, not
     * itself, and past a closed remote iris only with its code. A refusal puts the start gate out.
     *
     * @param player
     *            who is told what happened
     * @param start
     *            the gate dialling
     * @param args
     *            the target's name, and optionally the IDC for a closed remote iris
     */
    public static void dialFrom(final CommandSender player, final Stargate start, final String[] args)
    {
        final String startnetwork = CommandUtilities.getGateNetwork(start);
        if (start.getGateName().equals(args[0]))
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.TARGET_IS_SELF.toString());
            return;
        }
        final Stargate target = StargateManager.getStargate(args[0]);
        if (target == null)
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.TARGET_INVALID.toString());
            return;
        }
        final String targetnetwork = CommandUtilities.getGateNetwork(target);
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Dial Target - Gate: \"" + target.getGateName() + "\" Network: \"" + targetnetwork + "\"");
        if ( !startnetwork.equals(targetnetwork))
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.TARGET_INVALID.toString() + " Not on same network.");
            return;
        }
        if (StargateRestrictions.isCrossWorldRefused(start.getGateWorld(), target.getGateWorld()))
        {
            CommandUtilities.closeGate(start, false);
            player.sendMessage(ConfigManager.MessageStrings.CROSS_WORLD_DISABLED.toString());
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
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "Remote Iris is active; provide the IDC to unlock.");
            return;
        }

        if (start.dialStargate(target, false))
        {
            player.sendMessage(ConfigManager.MessageStrings.GATE_CONNECTED.toString());
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
    private static void openRemoteIrisIfIdcMatches(final CommandSender player, final Stargate target, final String[] args)
    {
        if ( !target.getGateIrisDeactivationCode().equals("") && target.isGateIrisActive()
            && (args.length >= 2) && target.getGateIrisDeactivationCode().equals(args[1]))
        {
            target.toggleIrisActive(false);
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "IDC accepted. Iris has been deactivated.");
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
    private static void recoverFailedDial(final CommandSender player, final Stargate start, final Stargate target)
    {
        if (isTargetInUse(start, target))
        {
            CommandUtilities.closeGate(start, false);
            CommandUtilities.restoreIrisDefault(target);
            player.sendMessage(ConfigManager.MessageStrings.TARGET_IS_ACTIVE.toString());
            return;
        }

        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Dial recovery: removing stale activator for target " + target.getGateName() + " and retrying with force");
        StargateManager.removeActivatorForStargate(target);
        if (start.dialStargate(target, true))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Dial recovery succeeded for target " + target.getGateName());
            player.sendMessage(ConfigManager.MessageStrings.GATE_CONNECTED.toString());
            return;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Dial recovery failed for target " + target.getGateName());
        CommandUtilities.closeGate(start, false);
        CommandUtilities.restoreIrisDefault(target);
        player.sendMessage(ConfigManager.MessageStrings.TARGET_IS_ACTIVE.toString());
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
            // Only an open gate can be dialled into the target, so this asks the open gates
            // rather than copying and sorting every gate on the server to find out.
            return StargateManager.hasIncomingConnection(target, start);
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
        return CommandUtilities.runCommandSafe(sender, new Callable<Boolean>()
        {
            // Always answered here, with a usage line where the arguments do not fit.
            @SuppressWarnings("java:S3516")
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
                // One short line, not plugin.yml's usage block (#325).
                sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                    + ChatText.usage(USAGE));
                return true;
            }
        });
    }

}
