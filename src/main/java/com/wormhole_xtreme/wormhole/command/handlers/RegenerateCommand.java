package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.command.Refresh;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.logic.GateRederivation;
import com.wormhole_xtreme.wormhole.logic.GateRefresh;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;

/**
 * Handler for '/wormhole regenerate' (regen)
 */
public class RegenerateCommand implements SubCommand
{
    /** Places the frame blocks a named gate is missing. */
    private static final String FILL = "-fill";


    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        if ((args.length < 2) || (args[1].startsWith("-") && !"-all".equalsIgnoreCase(args[1])))
        {
            return waitForClick(sender, args);
        }
        if ("-all".equalsIgnoreCase(args[1]))
        {
            return regenerateAll(sender);
        }

        final Stargate s = StargateManager.getStargate(args[1]);
        if (s == null)
        {
            sender.sendMessage(CommandUtilities.noGateCalled(args[1]));
            return true;
        }
        final boolean fill = flagAt(args, 2, FILL) > 0;
        int missing = 0;
        final int shapeAt = flagAt(args, 2, "-shape");
        if (shapeAt > 0)
        {
            missing = adoptForRegen(sender, s, args, shapeAt, fill);
            if (missing == STOP)
            {
                return true;
            }
        }
        else
        {
            takeSignOutOfFrame(sender, s);
            if (fill)
            {
                fillAndReport(sender, s, 0);
            }
        }
        regenerateOneGate(sender, s, missing, flagAt(args, 2, "-water") > 0, true);
        return true;
    }

    /** What {@link #adoptForRegen} returns when the command ends there. */
    private static final int STOP = -1;

    /**
     * Takes the shape named after {@code -shape}, fills its gaps if asked, and says what is left.
     *
     * @return how many frame blocks are still missing, or {@link #STOP} when the shape was not taken
     */
    private static int adoptForRegen(final CommandSender sender, final Stargate s, final String[] args,
        final int shapeAt, final boolean fill)
    {
        if ((shapeAt + 1) >= args.length)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.regen.name-shape",
                ChatText.command("/wormhole gate regen <gate> -shape <shape>"))));
            return STOP;
        }
        final GateRederivation.ShapeFit fit = adoptNamedShape(sender, s, args[shapeAt + 1]);
        if ((fit == null) || !fit.accepted())
        {
            return STOP;
        }
        final int missing = fit.gaps().size();
        if (fill)
        {
            return fillAndReport(sender, s, missing);
        }
        if ((missing > 0) && (missing <= GateRederivation.fillCap(fit.expected())))
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.add-fill", ChatText.command(FILL))));
        }
        return missing;
    }

    /** Where a flag stands among the arguments from a position on, whatever its capitals, or -1. */
    private static int flagAt(final String[] args, final int from, final String flag)
    {
        for (int i = from; i < args.length; i++)
        {
            if (flag.equalsIgnoreCase(args[i]))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * With no gate named, waits for the player's next DHD click and regenerates that gate, as
     * {@code /wormhole refresh} did.
     */
    private static boolean waitForClick(final CommandSender sender, final String[] args)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(ConfigManager.MessageStrings.GATE_NOT_SPECIFIED.toString());
            return false;
        }
        if (flagAt(args, 1, FILL) > 0)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.regen.name-gate-to-fill",
                ChatText.command("/wormhole gate regen <gate> -fill"))));
            return true;
        }
        Refresh.addPendingRefresh(player, flagAt(args, 1, "-water") > 0);
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.click-dhd")));
        return true;
    }

    /**
     * Regenerates the gate whose DHD a player clicked after {@code /wormhole gate regenerate} with
     * no gate name, detecting it from the face they clicked first.
     *
     * @param player
     *            who clicked
     * @param direction
     *            the face clicked, or null
     * @param clearLiquid
     *            whether {@code -water} was asked for
     */
    public static void regenerateClicked(final Player player, final Block clicked,
        final BlockFace direction, final boolean clearLiquid)
    {
        final Stargate existing = StargateManager.getGateFromBlock(clicked);
        if (existing == null)
        {
            player.sendMessage(ChatText.error(Messages.get(player, "gate.regen.no-gate-at-block")));
            return;
        }
        regenerateAt(player, existing, clicked, direction, clearLiquid);
    }

    /**
     * Regenerates a gate from a block of its DHD, detecting it afresh from there first.
     *
     * @param sender
     *            who to tell
     * @param existing
     *            the gate as registered
     * @param button
     *            its DHD button, or the block clicked
     * @param facing
     *            the facing to try first, or null to try all four
     * @param clearLiquid
     *            whether {@code -water} was asked for
     */
    public static void regenerateAt(final CommandSender sender, final Stargate existing,
        final Block button, final BlockFace facing, final boolean clearLiquid)
    {
        shutForRegen(sender, existing);
        takeSignOutOfFrame(sender, existing);
        final Stargate fresh = GateRefresh.refresh(existing, button, facing);
        reportRedetect(sender, existing, fresh);
        regenerateOneGate(sender, (fresh != null) ? fresh : existing, 0, clearLiquid, false);
    }

    /**
     * Shuts an open or dialling gate before regenerating it: the far end holds the gate being
     * replaced, and chevrons drawn from the old order would be left lit.
     */
    private static void shutForRegen(final CommandSender sender, final Stargate s)
    {
        if (!s.isGateActive() && !s.isGateLightsActive())
        {
            return;
        }
        CommandUtilities.closeGate(s, false);
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.shut-down", ChatText.name(s.getGateName()))));
    }

    /**
     * Places the frame blocks a gate is missing, or says why it will not.
     *
     * @return how many blocks are still missing afterwards
     */
    private static int fillAndReport(final CommandSender sender, final Stargate s, final int missing)
    {
        final GateRederivation.Fill fill = GateRederivation.fillFrame(s);
        if (!fill.placed().isEmpty())
        {
            for (final Block placed : fill.placed())
            {
                sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.fill.placed",
                    ChatText.material(placed.getType().name()),
                    ChatText.value(placed.getX() + " " + placed.getY() + " " + placed.getZ()))));
            }
            return 0;
        }
        if (fill.overCap())
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.regen.fill.over-cap",
                ChatText.name(s.getGateName()), fill.gaps().size(),
                ChatText.value(String.valueOf(fill.cap())), ChatText.command(FILL))));
        }
        else
        {
            for (final GateRederivation.Gap gap : fill.blocked().subList(0, Math.min(GAPS_LISTED, fill.blocked().size())))
            {
                sender.sendMessage(ChatText.error(Messages.get(sender, "gate.regen.fill.blocked",
                    ChatText.name(s.getGateName()), ChatText.value(gap.x() + " " + gap.y() + " " + gap.z()),
                    ChatText.material(gap.found().name()))));
            }
        }
        return missing;
    }

    /** Puts back frame blocks a sign was hung in, so the frame can be detected whole. */
    private static void takeSignOutOfFrame(final CommandSender sender, final Stargate s)
    {
        for (final Block restored : GateRederivation.restoreFrameUnderSigns(s))
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.sign-taken-out",
                ChatText.name(s.getGateName()),
                ChatText.value(restored.getX() + " " + restored.getY() + " " + restored.getZ()))));
        }
    }

    /** Says whether the gate's whole geometry was detected afresh, or why it was kept. */
    private static void reportRedetect(final CommandSender sender, final Stargate existing, final Stargate fresh)
    {
        if (fresh != null)
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.redetect.done",
                ChatText.name(fresh.getGateName()), ChatText.name(fresh.getGateShapeName()))));
            return;
        }
        if (existing.isGateActive() || existing.isGateLightsActive())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.redetect.busy",
                ChatText.name(existing.getGateName()))));
            return;
        }
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.redetect.no-match",
            ChatText.name(existing.getGateName()))));
    }

    /** Clears water left standing in the gate if asked, or says it is there. */
    private static void reportLiquid(final CommandSender sender, final Stargate s, final boolean clear)
    {
        if (clear)
        {
            final int cleared = s.clearStrandedLiquid();
            if (cleared > 0)
            {
                final String line;
                if (cleared == 1)
                {
                    line = Messages.get(sender, "gate.regen.liquid.cleared.one", cleared, ChatText.name(s.getGateName()));
                }
                else
                {
                    line = Messages.get(sender, "gate.regen.liquid.cleared.other", cleared, ChatText.name(s.getGateName()));
                }
                sender.sendMessage(ChatText.info(line));
            }
            return;
        }
        final int standing = s.strandedLiquid().size();
        if (standing > 0)
        {
            final String line;
            if (standing == 1)
            {
                line = Messages.get(sender, "gate.regen.liquid.standing.one", standing, ChatText.name(s.getGateName()),
                    ChatText.command("/wormhole gate regen " + s.getGateName() + " -water"));
            }
            else
            {
                line = Messages.get(sender, "gate.regen.liquid.standing.other", standing, ChatText.name(s.getGateName()),
                    ChatText.command("/wormhole gate regen " + s.getGateName() + " -water"));
            }
            sender.sendMessage(ChatText.info(line));
        }
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
     * @return how it fitted, or null when there was nothing to fit
     */
    private static GateRederivation.ShapeFit adoptNamedShape(final CommandSender sender, final Stargate s,
        final String shapeName)
    {
        if (!(namedShape(shapeName) instanceof Stargate3DShape shape))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.regen.shape.unknown", ChatText.name(shapeName))));
            return null;
        }
        if (!StargateShapeRegistry.isEnabled(shape.getShapeName()))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.regen.shape.off", ChatText.name(shape.getShapeName()))));
            return null;
        }
        // After the shape is known to exist, so a mistyped command changes no blocks.
        takeSignOutOfFrame(sender, s);
        final String was = s.getGateShapeName();
        final GateRederivation.ShapeFit fit = GateRederivation.adoptShape(s, shape);
        if (fit.expected() == 0)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.regen.shape.no-anchor",
                ChatText.name(s.getGateName()), ChatText.name(shape.getShapeName()), ChatText.name(was))));
            return null;
        }
        if (fit.accepted())
        {
            StargateDBManager.saveStargate(s);
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.shape.adopted",
                ChatText.name(s.getGateName()), ChatText.name(shape.getShapeName()), ChatText.name(was),
                fit.present(), fit.expected())));
        }
        else
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.regen.shape.stays",
                ChatText.name(s.getGateName()), ChatText.name(was), fit.present(), fit.expected(),
                ChatText.name(shape.getShapeName()),
                ChatText.value(GateRederivation.NAMED_SHAPE_MINIMUM_PERCENT + "%"))));
        }
        if ((fit.layout() != null) && fit.layout().moved())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.shape.laid",
                ChatText.value(fit.layout().describe()))));
        }
        reportGaps(sender, fit.gaps());
        return fit;
    }

    /** A loaded shape by name, whatever its capitals. */
    private static StargateShape namedShape(final String name)
    {
        for (final Map.Entry<String, StargateShape> e
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
    private static void reportGaps(final CommandSender sender, final List<GateRederivation.Gap> gaps)
    {
        if (gaps.isEmpty())
        {
            return;
        }
        final List<String> shown = gaps.stream()
            .limit(GAPS_LISTED)
            .map(gap -> Messages.get(sender, "gate.regen.gap",
                ChatText.value(gap.x() + " " + gap.y() + " " + gap.z()), ChatText.material(gap.found().name())))
            .toList();
        final int more = gaps.size() - shown.size();
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.gaps", listed(sender, shown, more))));
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
     * @param missing
     *            frame blocks a named shape found missing, so markers wait for the frame to be whole
     * @param clearLiquid
     *            whether to clear water left standing in the gate
     * @param redetect
     *            whether to try detecting the whole gate afresh first, as a click already has
     */
    private static void regenerateOneGate(final CommandSender sender, final Stargate given, final int missing,
        final boolean clearLiquid, final boolean redetect)
    {
        Stargate s = given;
        shutForRegen(sender, s);
        if (redetect && (missing == 0))
        {
            final Stargate fresh = GateRefresh.refresh(s, s.getGateDialLeverBlock(), s.getGateFacing());
            reportRedetect(sender, s, fresh);
            if (fresh != null)
            {
                s = fresh;
            }
        }
        reportLiquid(sender, s, clearLiquid);
        // The exit is worked out once when a gate is built and then stored, so a gate that
        // landed travellers at its side kept doing it for ever. This is the command people
        // already reach for when a gate is misbehaving, so it is where the fix belongs.
        if (s.recomputeGatePlayerTeleportLocation())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.arrival-recomputed", ChatText.name(s.getGateName()))));
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
        reportRederivation(sender, s, GateRederivation.rederive(s), missing);
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
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.regenerated", ChatText.name(s.getGateName()))));
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
     * @param missing
     *            frame blocks a named shape found missing
     */
    private static void reportRederivation(final CommandSender sender, final Stargate s,
        final GateRederivation.Outcome outcome, final int missing)
    {
        final String gate = ChatText.name(s.getGateName());
        final String shape = ChatText.name(s.getGateShapeName());
        switch (outcome.result())
        {
            // Deliberately does not claim the file is missing. This branch is also reached by a
            // shape that resolved perfectly well and is simply 2D -- any shape file without
            // Version=2, which StargateShapeFactory still builds as a plain StargateShape and
            // this release still supports. Telling that admin to go and find a file sitting in
            // front of them would send them looking for the wrong problem.
            case NO_SHAPE -> sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.rederive.no-shape", gate, shape)));
            case NO_ANCHOR -> sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.rederive.no-anchor", gate)));
            case NOT_DETECTED -> sender.sendMessage(ChatText.info(notDetected(sender, gate, shape, missing)));
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
        switch (result)
        {
            case REBUILT ->
            {
                StargateDBManager.saveStargate(s);
                sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.light.rebuilt",
                    ChatText.name(s.getGateName()), ChatText.name(s.getGateShapeName()))));
            }
            case BUSY -> sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.light.busy",
                ChatText.name(s.getGateName()))));
            case DOES_NOT_FIT -> sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.light.does-not-fit",
                ChatText.name(s.getGateShapeName()), ChatText.name(s.getGateName()))));
            // No shape and no anchor are already reported by the marker re-derivation.
            default -> { /* nothing changed, nothing to say */ }
        }
    }

    /**
     * Why a gate's markers were left alone: a named shape a few blocks short, or a frame that no
     * longer matches.
     */
    private static String notDetected(final CommandSender sender, final String gate, final String shape,
        final int missing)
    {
        if (missing == 1)
        {
            return Messages.get(sender, "gate.regen.rederive.short.one", gate, ChatText.value(String.valueOf(missing)), shape);
        }
        if (missing > 0)
        {
            return Messages.get(sender, "gate.regen.rederive.short.other", gate, ChatText.value(String.valueOf(missing)),
                shape);
        }
        return Messages.get(sender, "gate.regen.rederive.no-longer-matches", gate, shape);
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
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.rederive.moved",
            ChatText.name(s.getGateShapeName()), ChatText.name(s.getGateName()),
            ChatText.value(String.join(Messages.get(sender, "gate.regen.list-separator"), outcome.changes())))));
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
            final boolean relit = tallyLights(tally, gate.getGateName(), GateRederivation.rebuildLightOrder(gate));
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
        private int relit;
        private final List<String> couldNotCompute = new ArrayList<>();
        private final List<String> busy = new ArrayList<>();
        private final List<String> doesNotFit = new ArrayList<>();
    }

    /** Most gate names a report line lists before the rest are only counted. */
    private static final int NAMES_LISTED = 20;

    /** Counts one gate's light rebuild; true if it changed the gate. */
    private static boolean tallyLights(final AllTally tally, final String name, final GateRederivation.LightResult lights)
    {
        switch (lights)
        {
            case REBUILT ->
            {
                tally.relit++;
                return true;
            }
            case BUSY -> tally.busy.add(name);
            case DOES_NOT_FIT -> tally.doesNotFit.add(name);
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
            tally.couldNotCompute.add(gate.getGateName());
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
        final String movedLine;
        if (tally.moved == 1)
        {
            movedLine = Messages.get(sender, "gate.regen.all.moved.one", ChatText.value(String.valueOf(tally.moved)));
        }
        else
        {
            movedLine = Messages.get(sender, "gate.regen.all.moved.other", ChatText.value(String.valueOf(tally.moved)));
        }
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.all.checked",
            ChatText.value(gates(sender, tally.checked))) + " " + movedLine));
        if (!tally.couldNotCompute.isEmpty())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.regen.all.no-arrival",
                ChatText.bad(gates(sender, tally.couldNotCompute.size())), names(sender, tally.couldNotCompute))));
        }
        if (tally.relit > 0)
        {
            sendRelit(sender, tally.relit);
        }
        if (!tally.busy.isEmpty())
        {
            sendBusy(sender, tally.busy);
        }
        if (!tally.doesNotFit.isEmpty())
        {
            sendDoesNotFit(sender, tally.doesNotFit);
        }
    }

    /** Says how many gates now light their chevrons in their shape's order. */
    private static void sendRelit(final CommandSender sender, final int relit)
    {
        final String line;
        if (relit == 1)
        {
            line = Messages.get(sender, "gate.regen.all.relit.one", relit);
        }
        else
        {
            line = Messages.get(sender, "gate.regen.all.relit.other", relit);
        }
        sender.sendMessage(ChatText.info(line));
    }

    /** Says which gates kept their old light order because they were dialling or open. */
    private static void sendBusy(final CommandSender sender, final List<String> busy)
    {
        final String line;
        if (busy.size() == 1)
        {
            line = Messages.get(sender, "gate.regen.all.busy.one", busy.size(), names(sender, busy));
        }
        else
        {
            line = Messages.get(sender, "gate.regen.all.busy.other", busy.size(), names(sender, busy));
        }
        sender.sendMessage(ChatText.info(line));
    }

    /** Says which gates kept their old light order because the frame no longer fits the shape. */
    private static void sendDoesNotFit(final CommandSender sender, final List<String> doesNotFit)
    {
        final String line;
        if (doesNotFit.size() == 1)
        {
            line = Messages.get(sender, "gate.regen.all.does-not-fit.one", doesNotFit.size(),
                ChatText.command("-shape"), names(sender, doesNotFit));
        }
        else
        {
            line = Messages.get(sender, "gate.regen.all.does-not-fit.other", doesNotFit.size(),
                ChatText.command("-shape"), names(sender, doesNotFit));
        }
        sender.sendMessage(ChatText.info(line));
    }

    /** The gates, as names, the first few listed and the rest counted. */
    private static String names(final CommandSender sender, final List<String> names)
    {
        final List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        final List<String> shown = sorted.stream().limit(NAMES_LISTED).map(ChatText::name).toList();
        return listed(sender, shown, sorted.size() - shown.size());
    }

    /** A list's items joined, and how many more there were if the rest are only counted. */
    private static String listed(final CommandSender sender, final List<String> shown, final int more)
    {
        final String items = String.join(Messages.get(sender, "gate.regen.list-separator"), shown);
        if (more > 0)
        {
            return items + Messages.get(sender, "gate.regen.list-more", ChatText.value(String.valueOf(more)));
        }
        return items + Messages.get(sender, "gate.regen.list-end");
    }

    /** "1 gate", "2 gates". */
    private static String gates(final CommandSender sender, final int n)
    {
        if (n == 1)
        {
            return Messages.get(sender, "gate.regen.gates.one", n);
        }
        return Messages.get(sender, "gate.regen.gates.other", n);
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
