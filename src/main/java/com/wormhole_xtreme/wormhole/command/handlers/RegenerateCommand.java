package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.logic.GateRederivation;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;

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
            return regenerateAll(sender);
        }

        final Stargate s = StargateManager.getStargate(args[1]);
        if (s == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.CONSTRUCT_NAME_INVALID.toString()
                + "\"" + args[1] + "\"");
            return true;
        }
        if ((args.length >= 3) && "-shape".equalsIgnoreCase(args[2]))
        {
            if (args.length < 4)
            {
                sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                    + "Name the shape: /wormhole gate regenerate <gate> -shape <shape>");
                return true;
            }
            if (!adoptNamedShape(sender, s, args[3]))
            {
                return true;
            }
        }
        regenerateOneGate(sender, s);
        return true;
    }

    /** Most missing blocks listed before the rest are only counted. */
    private static final int GAPS_LISTED = 10;

    /**
     * Records the shape an admin named for a gate, if enough of it is standing, and says what is
     * missing either way.
     *
     * @param sender
     *            who to tell
     * @param s
     *            the gate
     * @param shapeName
     *            the shape named
     * @return true if the gate took the shape, so regenerating it can carry on
     */
    private static boolean adoptNamedShape(final CommandSender sender, final Stargate s, final String shapeName)
    {
        final String header = ConfigManager.MessageStrings.NORMAL_HEADER.toString();
        if (!(namedShape(shapeName) instanceof Stargate3DShape shape))
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No gate shape called \""
                + shapeName + "\" is loaded.");
            return false;
        }
        final String was = s.getGateShapeName();
        final GateRederivation.ShapeFit fit = GateRederivation.adoptShape(s, shape);
        if (fit.expected() == 0)
        {
            sender.sendMessage(header + s.getGateName() + " records no dial button, facing or world to lay \"" + shape.getShapeName()
                + "\" from. Left as \"" + was + "\".");
            return false;
        }
        final String counted = fit.present() + " of " + fit.expected() + " frame blocks of \"" + shape.getShapeName()
            + "\" are in place";
        if (fit.accepted())
        {
            StargateDBManager.saveStargate(s);
            sender.sendMessage(header + s.getGateName() + " is now recorded as \"" + shape.getShapeName() + "\" (was \""
                + was + "\"): " + counted + ".");
        }
        else
        {
            sender.sendMessage(header + "Only " + counted + " at " + s.getGateName() + ", under the "
                + GateRederivation.NAMED_SHAPE_MINIMUM_PERCENT + "% needed, so it stays \"" + was + "\".");
        }
        if ((fit.layout() != null) && fit.layout().moved())
        {
            sender.sendMessage("Laid where its own recorded frame is, not where its DHD puts it: "
                + fit.layout().describe() + ".");
        }
        reportGaps(sender, fit.gaps());
        return fit.accepted();
    }

    /** A loaded shape by name, whatever its capitals. */
    private static com.wormhole_xtreme.wormhole.model.StargateShape namedShape(final String name)
    {
        for (final java.util.Map.Entry<String, com.wormhole_xtreme.wormhole.model.StargateShape> e
            : StargateShapeRegistry.getStargateShapes().entrySet())
        {
            if (e.getKey().equalsIgnoreCase(name))
            {
                return e.getValue();
            }
        }
        return null;
    }

    /** Lists the first few missing or wrong blocks, and counts the rest. */
    private static void reportGaps(final CommandSender sender, final java.util.List<String> gaps)
    {
        if (gaps.isEmpty())
        {
            return;
        }
        final java.util.List<String> shown = gaps.subList(0, Math.min(GAPS_LISTED, gaps.size()));
        sender.sendMessage("Missing or wrong: " + String.join("; ", shown)
            + ((gaps.size() > shown.size()) ? "; and " + (gaps.size() - shown.size()) + " more." : "."));
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
        reportLightOrder(sender, s, GateRederivation.rebuildLightOrder(s));

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
            // Deliberately does not claim the file is missing. This branch is also reached by a
            // shape that resolved perfectly well and is simply 2D -- any shape file without
            // Version=2, which StargateShapeFactory still builds as a plain StargateShape and
            // this release still supports. Telling that admin to go and find a file sitting in
            // front of them would send them looking for the wrong problem.
            case NO_SHAPE -> sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + "Cannot re-read the shape of " + s.getGateName() + ": \"" + s.getGateShapeName()
                + "\" is not a 3D shape to re-derive from -- either it is missing from the shapes"
                + " folder, or it is an older 2D shape file with no Version=2 line."
                + " Markers left as they are.");
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
     * Says what rebuilding one gate's light order came to, and saves the gate if it changed.
     *
     * @param sender
     *            who to tell
     * @param s
     *            the gate
     * @param result
     *            what the rebuild came to
     */
    private static void reportLightOrder(final CommandSender sender, final Stargate s,
        final GateRederivation.LightResult result)
    {
        final String header = ConfigManager.MessageStrings.NORMAL_HEADER.toString();
        switch (result)
        {
            case REBUILT ->
            {
                StargateDBManager.saveStargate(s);
                sender.sendMessage(header + s.getGateName() + " now lights its chevrons in the order shape \""
                    + s.getGateShapeName() + "\" gives.");
            }
            case BUSY -> sender.sendMessage(header + s.getGateName()
                + " is dialling or open, so its light order was left alone. Try again once it is shut.");
            case DOES_NOT_FIT -> sender.sendMessage(header + "Shape \"" + s.getGateShapeName()
                + "\" lights blocks that are not part of " + s.getGateName()
                + "'s frame, so its light order was left alone.");
            // No shape and no anchor are already reported by the marker re-derivation.
            default -> { /* nothing changed, nothing to say */ }
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
     * Recomputes the arrival point and light order of every gate, and only those.
     *
     * <p>Deliberately narrower than a single-gate {@code regenerate}, which also refreshes
     * the dial lever, the iris lever, redstone, and the sign -- all reasonable things to
     * redo on one gate an admin is actively looking at, and none of them reasonable to
     * silently rewrite on every gate on the server at once. This does exactly the one thing
     * that was asked for: find gates whose exit is not where the geometry says it should be,
     * and fix those, unattended. The light order joins it because it reads no blocks and leaves a
     * gate alone unless its frame still fits the shape.
     *
     * <p>Only gates whose position actually changes are saved back to disk. Recomputing is
     * deterministic, so a gate that was already correct comes back with the same answer --
     * there is nothing to write, and nothing worth telling the admin about.
     *
     * @param sender
     *            who asked
     * @return true, the command was handled
     */
    private static boolean regenerateAll(final CommandSender sender)
    {
        final AllTally tally = new AllTally();
        for (final Stargate gate : StargateManager.getAllGates())
        {
            tally.checked++;
            final boolean relit = tallyLights(tally, GateRederivation.rebuildLightOrder(gate));
            if (tallyExit(tally, gate) || relit)
            {
                StargateDBManager.saveStargate(gate);
            }
        }
        reportAll(sender, tally);
        return true;
    }

    /** What {@code -all} found, counted as it goes. */
    private static final class AllTally
    {
        private int checked;
        private int moved;
        private int couldNotCompute;
        private int relit;
        private int lightsLeft;
    }

    /** Counts one gate's light rebuild; true if it changed the gate. */
    private static boolean tallyLights(final AllTally tally, final GateRederivation.LightResult lights)
    {
        switch (lights)
        {
            case REBUILT ->
            {
                tally.relit++;
                return true;
            }
            case BUSY, DOES_NOT_FIT -> tally.lightsLeft++;
            default -> { /* nothing to change, or reported by name only */ }
        }
        return false;
    }

    /** Recomputes one gate's exit and counts it; true if it moved. */
    private static boolean tallyExit(final AllTally tally, final Stargate gate)
    {
        final Location before = gate.getGatePlayerTeleportLocation();
        if (!gate.recomputeGatePlayerTeleportLocation())
        {
            // No world, no facing, or no portal blocks to derive a position from --
            // an incomplete or badly damaged gate, not something to guess at here.
            tally.couldNotCompute++;
            return false;
        }
        if (exitMoved(before, gate.getGatePlayerTeleportLocation()))
        {
            tally.moved++;
            return true;
        }
        return false;
    }

    /** Tells the admin what {@code -all} came to. */
    private static void reportAll(final CommandSender sender, final AllTally tally)
    {
        final boolean one = tally.moved == 1;
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + "Checked " + gates(tally.checked) + ". "
            + tally.moved + " arrival point" + plural(tally.moved) + " " + (one ? "was" : "were")
            + " out of place and " + (one ? "has" : "have") + " been recomputed.");
        if (tally.couldNotCompute > 0)
        {
            sender.sendMessage(gates(tally.couldNotCompute)
                + " could not be checked -- no world, no facing, or no portal blocks recorded.");
        }
        if (tally.relit > 0)
        {
            sender.sendMessage(gates(tally.relit) + ((tally.relit == 1)
                ? " now lights its chevrons in its shape's order."
                : " now light their chevrons in their shapes' order."));
        }
        if (tally.lightsLeft > 0)
        {
            sender.sendMessage(gates(tally.lightsLeft) + " kept " + ((tally.lightsLeft == 1) ? "its" : "their")
                + " old light order: dialling or open, or the frame no longer fits the shape."
                + " Regenerate one by name to see which.");
        }
    }

    /** "1 gate", "2 gates". */
    private static String gates(final int n)
    {
        return n + " gate" + plural(n);
    }

    private static String plural(final int n)
    {
        return (n == 1) ? "" : "s";
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
