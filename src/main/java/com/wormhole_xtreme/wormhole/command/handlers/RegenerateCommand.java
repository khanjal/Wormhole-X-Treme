package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.logic.GateRederivation;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;

/**
 * Handler for '/wormhole regenerate' (regen)
 */
public class RegenerateCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        if (args.length < 2)
        {
            sender.sendMessage(ConfigManager.MessageStrings.GATE_NOT_SPECIFIED.toString());
            return false;
        }
        if ("-all".equalsIgnoreCase(args[1]))
        {
            return regenerateAllExits(sender);
        }

        final Stargate s = StargateManager.getStargate(args[1]);
        if (s == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.CONSTRUCT_NAME_INVALID.toString()
                + "\"" + args[1] + "\"");
            return true;
        }
        regenerateOneGate(sender, s);
        return true;
    }


    /**
     * Redoes everything about one gate an admin is looking at.
     *
     * <p>Wider than the {@code -all} sweep on purpose: levers, redstone and the sign are all
     * reasonable to redo on a single gate somebody is actively debugging, and none of them
     * reasonable to rewrite silently across a whole server.
     *
     * @param sender
     *            who to tell
     * @param s
     *            the gate
     */
    private static void regenerateOneGate(final CommandSender sender, final Stargate s)
    {
        // The exit is worked out once when a gate is built and then stored, so a gate that
        // landed travellers at its side kept doing it for ever. This is the command people
        // already reach for when a gate is misbehaving, so it is where the fix belongs.
        if (s.recomputeGatePlayerTeleportLocation())
        {
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + "Arrival point recomputed for " + s.getGateName() + ".");
        }
        // Markers -- redstone, the iris lever, the dial sign -- are worked out once at
        // detection and stored, exactly like the arrival point above, and go stale for
        // exactly the same reason: the shape file they came from can change underneath
        // them. Re-derive before placing anything, so what follows places blocks where
        // today's shape says they go rather than where the shape said when the gate was
        // built.
        //
        // Nothing is taken up first, deliberately. Lifting the wires would mean lifting
        // the [RA] lever with them, and that lever is an output whose powered state is
        // live: setupRedstone puts back a fresh, unpowered one, so an open gate would
        // stop reporting itself open and whatever it powers would shut while the wormhole
        // was still running. setupRedstone already leaves an occupied cell alone, so a
        // marker that has not moved is untouched either way. A marker that has moved
        // leaves its old wire standing, which is cosmetic, visible, and named in the
        // report -- the better trade of the two.
        reportRederivation(sender, s, GateRederivation.rederive(s));

        s.toggleDialLeverState(true);
        if ((s.getGateIrisDeactivationCode() != null) && !s.getGateIrisDeactivationCode().isEmpty())
        {
            s.setupIrisLever(true);
        }
        if (s.isGateRedstonePowered())
        {
            s.setupRedstone(true);
        }
        s.setupGateSign(true);
        s.matchDialSignMaterial();
        if (s.isGateSignPowered() && (s.getGateDialSignBlock() != null))
        {
            StargateManager.refreshTeleportSign(s, true);
        }
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + "Regenerating Gate: " + s.getGateName());
    }

    /**
     * Says what re-deriving the gate's markers came to, and saves the gate if anything moved.
     *
     * <p>Saving matters as much as the message. Marker positions live in the gate file, so a
     * re-derivation nobody wrote down would be undone by the next restart -- and the admin
     * who ran the command would have watched it work and then stop working, which is worse
     * than it never having worked.
     *
     * <p>The three ways it can decline all name something the admin can act on. A gate with
     * no shape has had its shape file renamed or removed since it was built, and the loader
     * has already said so once at startup. A gate with no anchor predates the dial lever
     * being recorded. A gate that no longer detects has had its frame changed -- which is
     * information, not a failure, and nothing was touched either way.
     *
     * @param sender
     *            who to tell
     * @param s
     *            the gate
     * @param outcome
     *            what re-derivation came to
     */
    private static void reportRederivation(final CommandSender sender, final Stargate s,
        final GateRederivation.Outcome outcome)
    {
        switch (outcome.result())
        {
            case NO_SHAPE -> sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + "Cannot re-read the shape of " + s.getGateName() + ": \"" + s.getGateShapeName()
                + "\" is not in the shapes folder. Markers left as they are.");
            case NO_ANCHOR -> sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + s.getGateName() + " records no dial button, so its shape cannot be re-read."
                + " Markers left as they are.");
            case NOT_DETECTED -> sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + s.getGateName() + " no longer matches shape \"" + s.getGateShapeName()
                + "\", so its markers were left alone. Check the frame is still standing.");
            case REDERIVED -> reportRederived(sender, s, outcome);
            default -> { /* every Result is covered above */ }
        }
    }

    /**
     * Reports a successful re-derivation, and saves when it actually changed something.
     *
     * @param sender
     *            who to tell
     * @param s
     *            the gate
     * @param outcome
     *            what moved
     */
    private static void reportRederived(final CommandSender sender, final Stargate s,
        final GateRederivation.Outcome outcome)
    {
        if (!outcome.changedAnything())
        {
            return;
        }
        StargateDBManager.saveStargate(s);
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + "Re-read shape \"" + s.getGateShapeName() + "\" for " + s.getGateName()
            + " and moved: " + String.join(", ", outcome.changes()) + ".");
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
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
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
