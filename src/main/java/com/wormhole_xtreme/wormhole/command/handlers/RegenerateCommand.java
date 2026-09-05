package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Handler for '/wormhole regenerate' (regen)
 */
public class RegenerateCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Gate management was never actually gated: none of these commands checked a
        // permission at all, so any player able to run /wormhole could reconfigure or
        // reassign any gate on the server. wormhole.config is what an admin already needs
        // for /wormhole config, so it is reused here rather than inventing a second
        // admin-only node that would mean the same thing.
        if ((sender instanceof Player)
            && !WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

        if (args.length >= 2)
        {
            if ("-all".equalsIgnoreCase(args[1]))
            {
                return regenerateAllExits(sender);
            }
            final Stargate s = StargateManager.getStargate(args[1]);
            if (s != null)
            {
                if ((s.getGateShape() != null) && StargateHelper.isStargateShape(s.getGateShape().getShapeName()))
                {
                    // Shape format (2D/3D) is determined at load time; no runtime upgrade needed.
                }
                // The exit is worked out once when a gate is built and then stored, so a
                // gate that landed travellers at its side kept doing it for ever. This is
                // the command people already reach for when a gate is misbehaving, so it is
                // where the fix belongs.
                if (s.recomputeGatePlayerTeleportLocation())
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                        + "Arrival point recomputed for " + s.getGateName() + ".");
                }
                s.toggleDialLeverState(true);
                if ((s.getGateIrisDeactivationCode() != null) && (s.getGateIrisDeactivationCode().length() > 0))
                {
                    s.setupIrisLever(true);
                }
                if (s.isGateRedstonePowered())
                {
                    s.setupRedstone(true);
                }
                s.setupGateSign(true);
                s.matchDialSignMaterial();
                if (s.isGateSignPowered() && s.getGateDialSignBlock() != null)
                {
                    StargateManager.refreshTeleportSign(s, true);
                }
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Regenerating Gate: " + s.getGateName());
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.constructNameInvalid.toString() + "\"" + args[1] + "\"");
            }
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.gateNotSpecified.toString());
            return false;
        }
        return true;
    }

    /**
     * Recomputes the arrival point of every gate, and only that.
     *
     * <p>Deliberately narrower than a single-gate {@code regenerate}, which also refreshes
     * the dial lever, the iris lever, redstone, and the sign -- all reasonable things to
     * redo on one gate an admin is actively looking at, and none of them reasonable to
     * silently rewrite on every gate on the server at once. This does exactly the one thing
     * that was asked for: find gates whose exit is not where the geometry says it should be,
     * and fix those, unattended.
     *
     * <p>Only gates whose position actually changes are saved back to disk. Recomputing is
     * deterministic, so a gate that was already correct comes back with the same answer --
     * there is nothing to write, and nothing worth telling the admin about.
     *
     * @param sender
     *            who asked
     * @return true, the command was handled
     */
    private static boolean regenerateAllExits(final CommandSender sender)
    {
        int checked = 0;
        int moved = 0;
        int couldNotCompute = 0;
        for (final Stargate gate : StargateManager.getAllGates())
        {
            checked++;
            final Location before = gate.getGatePlayerTeleportLocation();
            if (!gate.recomputeGatePlayerTeleportLocation())
            {
                // No world, no facing, or no portal blocks to derive a position from --
                // an incomplete or badly damaged gate, not something to guess at here.
                couldNotCompute++;
                continue;
            }
            if (exitMoved(before, gate.getGatePlayerTeleportLocation()))
            {
                moved++;
                StargateDBManager.saveStargate(gate);
            }
        }
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Checked " + checked + " gate" + (checked == 1 ? "" : "s") + ". "
            + moved + " arrival point" + (moved == 1 ? "" : "s") + " " + (moved == 1 ? "was" : "were")
            + " out of place and " + (moved == 1 ? "has" : "have") + " been recomputed.");
        if (couldNotCompute > 0)
        {
            sender.sendMessage(couldNotCompute + " gate" + (couldNotCompute == 1 ? "" : "s")
                + " could not be checked -- no world, no facing, or no portal blocks recorded.");
        }
        return true;
    }

    /**
     * Whether a recomputed exit landed somewhere different from where it started.
     *
     * <p>{@code recomputeGatePlayerTeleportLocation()} always overwrites the stored location
     * on success, whether or not the new value differs from the old one -- for a gate that
     * was already correct, recomputing it is a deterministic no-op that happens to rewrite
     * the same numbers. Comparing block coordinates here is what turns "we recomputed N
     * gates" into the actually useful "N gates needed it", and is the difference between a
     * report worth reading and one that says the same big number every time regardless of
     * how many gates were actually broken.
     *
     * @param before
     *            the location before recomputing, or null if there was none
     * @param after
     *            the location after recomputing, or null if there is none
     * @return true if the block position or world changed
     */
    static boolean exitMoved(final Location before, final Location after)
    {
        if ((before == null) || (after == null))
        {
            return before != after;
        }
        if (before.getWorld() != after.getWorld())
        {
            return true;
        }
        return (before.getBlockX() != after.getBlockX())
            || (before.getBlockY() != after.getBlockY())
            || (before.getBlockZ() != after.getBlockZ());
    }

}
