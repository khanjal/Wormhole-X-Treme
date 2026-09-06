package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * What happens when a player clicks a gate's controls.
 *
 * <p>Covers the buttons, levers and signs a gate is worked by: activating a gate, dialling
 * it, cycling a dial sign's target, opening and closing an iris, and completing a gate that
 * has been built but not yet named.
 *
 * <p>Split out of {@link WormholeXTremePlayerListener}, which had grown to hold both this
 * and everything about a player moving through a gate. The two share nothing but the player,
 * and keeping them apart means a change to how a gate is dialled cannot disturb how someone
 * travels through one.
 *
 * <p>Package-private and static throughout, exactly as it was when it lived in the listener.
 * The listener still owns the {@code @EventHandler} and calls in here.
 */
final class GateInteractionHandler
{
    /** Static helpers only. */
    private GateInteractionHandler()
    {
    }

    /**
     * Button lever hit.
     * 
     * @param player
     *            the p
     * @param clickedBlock
     *            the clicked
     * @param direction
     *            the direction
     * @return true, if successful
     */
    private static boolean buttonLeverHit(final Player player, final Block clickedBlock,
                                          final BlockFace direction)
    {
        if (handlePendingCompletion(player, clickedBlock, direction))
        {
            return true;
        }

        if (handlePendingRefresh(player, clickedBlock, direction))
        {
            return true;
        }

        final Stargate stargate = StargateManager.getGateFromBlock(clickedBlock);
        if (stargate != null)
        {
            handleLeverClick(player, clickedBlock, stargate);
            return true;
        }
        return handleNewGateAttempt(player, clickedBlock, direction);
    }

    /**
     * Works out which of a gate's two levers a click was aimed at, and acts on it.
     *
     * <p>The dial lever and the iris lever sit one block apart on the Standard layout, so
     * an exact match is taken before adjacency, and when the click is next to both the dial
     * wins. Otherwise reaching for the dial on a gate with an iris would sometimes shut the
     * iris instead.
     */
    private static void handleLeverClick(final Player player, final Block clickedBlock,
                                         final Stargate stargate)
    {
        // isSameBlock and isAdjacent both answer false for a null lever, so a gate without
        // one needs no guard of its own here.
        final Block dial = stargate.getGateDialLeverBlock();
        final Block iris = stargate.getGateIrisLeverBlock();
        final boolean dialSame = WorldUtils.isSameBlock(dial, clickedBlock);
        final boolean irisSame = WorldUtils.isSameBlock(iris, clickedBlock);
        final boolean dialAdj = WorldUtils.isAdjacent(dial, clickedBlock);
        final boolean irisAdj = WorldUtils.isAdjacent(iris, clickedBlock);
        if (!dialSame && !irisSame && !dialAdj && !irisAdj)
        {
            return;
        }

        // Gate owners and ops bypass permission checks. A sign-powered gate is worked
        // through its sign, so that is the node its dial lever asks about.
        final boolean isOwner = player.isOp() || stargate.isOwner(player);
        final boolean mayDial = isOwner || WXPermissions.checkWXPermissions(player, stargate,
            stargate.isGateSignPowered() ? PermissionType.SIGN : PermissionType.DIALER);
        final boolean mayIris = isOwner || WXPermissions.checkWXPermissions(player, stargate, PermissionType.DIALER);

        // Exact match first, adjacency second, and the dial ahead of the iris within each.
        // The two levers sit one block apart on the Standard layout, so a click near one is
        // usually near the other; preferring the iris would shut it when somebody reached
        // for the dial.
        if (dialSame && mayDial)
        {
            handleGateActivationSwitch(stargate, player);
        }
        else if (irisSame && !dialSame && mayIris)
        {
            stargate.toggleIrisActive(true);
        }
        else if (dialAdj && mayDial)
        {
            handleGateActivationSwitch(stargate, player);
        }
        else if (irisAdj && !dialAdj && mayIris)
        {
            stargate.toggleIrisActive(true);
        }
        else
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
        }
    }

    /**
     * Treats the click as somebody finishing a gate they have just built.
     *
     * @return true if the click was on a valid gate design
     */
    private static boolean handleNewGateAttempt(final Player player, final Block clickedBlock,
                                                final BlockFace direction)
    {
        final BlockFace facing = resolveClickDirection(clickedBlock, direction);
        if (facing == null)
        {
            return false;
        }

        final Stargate newGate = detectGateDesign(player, clickedBlock, facing);
        if (newGate == null)
        {
            // The player may have clicked beside the DHD rather than on it, so probe the
            // surrounding blocks. See findGateFromNearbyDial for why this is filtered
            // rather than brute-forced.
            if (!findGateFromNearbyDial(clickedBlock, player))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, player.getName() + " has pressed a button or lever but did not find any properly created gates.");
            }
            return false;
        }

        if (!WXPermissions.checkWXPermissions(player, newGate, PermissionType.BUILD))
        {
            if (newGate.isGateSignPowered())
            {
                newGate.resetTeleportSign();
            }
            StargateManager.removeIncompleteStargate(player);
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

        StargateManager.addIncompleteStargate(player, newGate);
        announceValidDesign(player, newGate);
        return true;
    }

    /**
     * The direction the click was aimed along.
     *
     * <p>The caller supplies one for a click it already understands. Otherwise the block's
     * own orientation is used, and a block that has none cannot be the front of a gate.
     *
     * @return the direction, or null if there is none to be had
     */
    private static BlockFace resolveClickDirection(final Block clickedBlock, final BlockFace direction)
    {
        if (direction != null)
        {
            return direction;
        }
        if (clickedBlock.getBlockData() instanceof org.bukkit.block.data.Directional clicked)
        {
            return clicked.getFacing();
        }
        return null;
    }

    /**
     * Looks for a finished gate around the clicked block.
     *
     * <p>A player part-way through {@code /wormhole build} has named a shape, and only that
     * shape is tried. Everyone else gets every shipped shape tried in turn.
     */
    private static Stargate detectGateDesign(final Player player, final Block clickedBlock,
        final BlockFace facing)
    {
        final StargateShape shape = StargateManager.getPlayerBuilderShape(player);
        if (shape != null)
        {
            return StargateHelper.checkStargate(clickedBlock, facing, shape);
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINEST, "Attempting to find any gate shapes!");
        return StargateHelper.checkStargate(clickedBlock, facing);
    }

    /**
     * Tells the player their design is good and what to type next.
     *
     * <p>A sign-powered gate goes through the same {@code /wormhole complete} flow, so the
     * player can still give it a network and an IDC; the only difference is that its sign may
     * already carry the name, in which case the suggested command is filled in.
     */
    private static void announceValidDesign(final Player player, final Stargate newGate)
    {
        final String header = ConfigManager.MessageStrings.normalHeader.toString();
        if (!newGate.isGateSignPowered())
        {
            player.sendMessage(header + "Valid Stargate Design! \u00A73:: \u00A7B<required> \u00A76[optional]");
            player.sendMessage(header + "Type \'\u00A7F/wormhole complete \u00A7B<name> \u00A76[idc=IDC] [net=NET]\u00A77\' to complete.");
            return;
        }
        player.sendMessage(header + "Valid Sign Nav Stargate Design! \u00A73:: \u00A7B<required> \u00A76[optional]");
        final String signName = newGate.getGateName();
        final String name = signName.isEmpty() ? "<name>" : signName;
        player.sendMessage(header + "Type \'\u00A7F/wormhole complete \u00A7B" + name + " \u00A76[idc=IDC] [net=NET]\u00A77\' to complete.");
    }

    /**
     * Answers a click that an interactive {@code /wormhole complete} was waiting for.
     *
     * <p>{@code direction} is refined here from the clicked block's own orientation when
     * the caller did not supply one. That refinement stays local: the only caller passes
     * null, and the refresh below probes all four faces anyway when it has none.
     *
     * @return true if the click was spent on a pending completion
     */
    /** The facings tried when nothing says which way the gate is built. */
    private static final BlockFace[] ALL_FACINGS = new BlockFace[] {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN };

    private static boolean handlePendingCompletion(final Player player, final Block clickedBlock,
                                                   final BlockFace direction)
    {
        final String[] pending = com.wormhole_xtreme.wormhole.command.Complete.getPendingCompletion(player);
        if (pending == null)
        {
            return false;
        }
        try
        {
            com.wormhole_xtreme.wormhole.model.Stargate found = detectAnyFacing(clickedBlock, resolveFacing(clickedBlock, direction));
            if (found == null)
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "No gate detected at clicked block. Try clicking the DHD button/lever again.");
                logDetectionDiagnostics(player, clickedBlock);
                return true;
            }
            completeDetectedGate(player, found, pending);
            com.wormhole_xtreme.wormhole.command.Complete.removePendingCompletion(player);
        }
        catch (final RuntimeException e)
        {
            // This block completes a gate the player asked for, charges them, and messages
            // them. It used to log here and say nothing to the player, who was left clicking
            // a DHD that answered with silence.
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Interactive /wormhole complete failed for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Completing the gate failed. Check server logs.");
            com.wormhole_xtreme.wormhole.command.Complete.removePendingCompletion(player);
        }
        return true;
    }

    /** The facing to try first: the caller's, or the clicked block's own. */
    private static BlockFace resolveFacing(final Block clickedBlock, final BlockFace direction)
    {
        if (direction != null)
        {
            return direction;
        }
        if (clickedBlock.getBlockData() instanceof org.bukkit.block.data.Directional clicked)
        {
            return clicked.getFacing();
        }
        return null;
    }

    /**
     * Looks for a gate at the clicked block, trying every facing if need be.
     *
     * <p>A malformed shape can throw out of detection. That is one facing's problem, so the
     * sweep notes it and carries on rather than giving up on the other five.
     */
    private static com.wormhole_xtreme.wormhole.model.Stargate detectAnyFacing(final Block clickedBlock, final BlockFace first)
    {
        if (first != null)
        {
            final com.wormhole_xtreme.wormhole.model.Stargate found = detectQuietly(clickedBlock, first);
            if (found != null)
            {
                return found;
            }
        }
        for (final BlockFace face : ALL_FACINGS)
        {
            final com.wormhole_xtreme.wormhole.model.Stargate found = detectQuietly(clickedBlock, face);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    /** One detection attempt, where a malformed shape is a miss rather than a failure. */
    private static com.wormhole_xtreme.wormhole.model.Stargate detectQuietly(final Block clickedBlock, final BlockFace face)
    {
        try
        {
            return com.wormhole_xtreme.wormhole.logic.StargateHelper.checkStargate(clickedBlock, face);
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Shape detection failed for face " + face + ": " + e.getMessage());
            return null;
        }
    }

    /** Registers the detected gate and completes it, charging for it if the server does. */
    private static void completeDetectedGate(final Player player, final com.wormhole_xtreme.wormhole.model.Stargate found,
        final String[] pending)
    {
        com.wormhole_xtreme.wormhole.model.StargateManager.addIncompleteStargate(player, found);
        final double buildCost = (ConfigManager.isEconomyEnabled() && com.wormhole_xtreme.wormhole.plugin.EconomySupport.isAvailable())
            ? ConfigManager.getEconomyBuildCost()
            : 0.0;
        if ((buildCost > 0) && !com.wormhole_xtreme.wormhole.plugin.EconomySupport.canAfford(player, buildCost))
        {
            player.sendMessage(ConfigManager.MessageStrings.economyInsufficientFunds.toString());
            return;
        }
        if (!com.wormhole_xtreme.wormhole.model.StargateManager.completeStargate(player, pending[0], pending[1], pending[2]))
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Construction Failed after interactive detection. Check server log.");
            return;
        }
        player.sendMessage(ConfigManager.MessageStrings.constructSuccess.toString());
        if (buildCost > 0)
        {
            com.wormhole_xtreme.wormhole.plugin.EconomySupport.charge(player, buildCost);
            player.sendMessage(ConfigManager.MessageStrings.economyBuildCharged.toString()
                + buildCost + " " + com.wormhole_xtreme.wormhole.plugin.EconomySupport.currencyName(buildCost));
        }
    }

    /** Reports what is around the clicked block when detection found nothing. */
    private static void logDetectionDiagnostics(final Player player, final Block clickedBlock)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || !plugin.isLoggable(Level.FINE))
        {
            return;
        }
        plugin.prettyLog(Level.FINE, "+/wormhole complete diag: running detailed detection diagnostics for player=" + player.getName());
        for (final BlockFace face : ALL_FACINGS)
        {
            try
            {
                final Block holding = clickedBlock.getRelative(WorldUtils.getInverseDirection(face));
                final Block below = holding.getRelative(BlockFace.DOWN);
                plugin.prettyLog(Level.FINE, "+/wormhole complete diag: face=" + face
                    + " holding=" + holding.getLocation() + " holdingType=" + holding.getType()
                    + " below=" + below.getLocation() + " belowType=" + below.getType());
            }
            catch (final RuntimeException ignore)
            {
                // diagnostics only
            }
        }
        plugin.prettyLog(Level.FINE, "+/wormhole complete diag: end diagnostics");
    }

    /**
     * Answers a click that an interactive {@code /wormhole refresh} was waiting for.
     *
     * @return true if the click was spent on a pending refresh
     */
    private static boolean handlePendingRefresh(final Player player, final Block clickedBlock,
                                                final BlockFace direction)
    {
        // --- /wormhole refresh pending check ---
        if (com.wormhole_xtreme.wormhole.command.Refresh.isPendingRefresh(player))
        {
            com.wormhole_xtreme.wormhole.command.Refresh.removePendingRefresh(player);
            final Stargate existing = StargateManager.getGateFromBlock(clickedBlock);
            if (existing == null)
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                    + "No registered gate found at that block. Build or complete the gate first.");
                return true;
            }
            // Preserve metadata from the existing gate before re-detecting geometry.
            final String oldName    = existing.getGateName();
            final String oldOwner   = existing.getGateOwner();
            // Stored, not displayed: copying the fallback would set the owner id as this
            // gate's display name, and the refresh saves immediately afterwards.
            final String oldOwnerNm = existing.getStoredGateOwnerName();
            final String oldIdc     = existing.getGateIrisDeactivationCode();
            final com.wormhole_xtreme.wormhole.model.StargateNetwork oldNet = existing.getGateNetwork();

            // Re-detect the gate geometry fresh from the block.
            BlockFace detectedFacing = direction;
            com.wormhole_xtreme.wormhole.model.Stargate fresh = null;
            if (detectedFacing != null)
            {
                fresh = StargateHelper.checkStargate(clickedBlock, detectedFacing);
            }
            if (fresh == null)
            {
                final org.bukkit.block.BlockFace[] faces = {
                    org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST,  org.bukkit.block.BlockFace.WEST
                };
                for (final org.bukkit.block.BlockFace face : faces)
                {
                    fresh = StargateHelper.checkStargate(clickedBlock, face);
                    if (fresh != null) { detectedFacing = face; break; }
                }
            }
            if (fresh == null)
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                    + "Gate geometry detection failed. Make sure all structure blocks are intact.");
                return true;
            }

            // Remove the stale registration (no block destruction). Not announced: the
            // gate is registered again immediately below, so telling listeners it was
            // removed would have them discard their records on every refresh.
            CommandUtilities.gateRemove(existing, false, false);

            // Restore metadata and register with fresh geometry.
            fresh.setGateName(oldName);
            fresh.setGateOwner(oldOwner);
            fresh.setGateOwnerName(oldOwnerNm);
            fresh.completeGate(oldName, oldIdc != null ? oldIdc : "");
            if (oldNet != null)
            {
                fresh.setGateNetwork(oldNet);
                com.wormhole_xtreme.wormhole.model.StargateManager.addGateToNetwork(fresh, oldNet.getNetworkName());
            }
            com.wormhole_xtreme.wormhole.model.StargateManager.registerStargate(fresh);
            com.wormhole_xtreme.wormhole.model.StargateDBManager.saveStargate(fresh);
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Gate '" + oldName + "' refreshed successfully.");
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
                "Gate '" + oldName + "' refreshed by " + player.getName()
                + " facing=" + (detectedFacing != null ? detectedFacing.toString() : "null")
                + " tpLoc=" + (fresh.getGatePlayerTeleportLocation() != null ? fresh.getGatePlayerTeleportLocation().toString() : "null"));
            return true;
        }
        return false;
    }

    /**
     * Handle gate activation switch.
     * 
     * @param stargate
     *            the stargate
     * @param player
     *            the player
     * @return true, if successful
     */
    static boolean handleGateActivationSwitch(final Stargate stargate, final Player player)
    {
        if (stargate.isGateActive() || stargate.isGateLightsActive())
        {
            return closeOrDeactivate(stargate, player);
        }
        if (stargate.isGateSignPowered())
        {
            return dialFromSign(stargate, player);
        }
        return activateForDialling(stargate, player);
    }

    /**
     * Puts an open or lit gate back to rest.
     *
     * <p>Three cases, in the order they are worth trying: an open wormhole is closed, a gate
     * this player activated is deactivated, and a gate left lit by somebody else is
     * force-cleared. The last exists because an activation mapping can outlive the player it
     * belongs to -- they log out, or the mapping is lost -- and without it the gate would stay
     * lit with no way for anyone to put it out.
     *
     * @param stargate
     *            the gate that is open or lit
     * @param player
     *            the player working the switch
     * @return true if the gate was acted on
     */
    static boolean closeOrDeactivate(final Stargate stargate, final Player player)
    {
        if (stargate.getGateTarget() != null)
        {
            //Shutdown stargate
            stargate.shutdownStargate(true);
            player.sendMessage(ConfigManager.MessageStrings.gateShutdown.toString());
            return true;
        }

        final Stargate s2 = StargateManager.removeActivatedStargate(player);
        if ((s2 != null) && (stargate.getGateId() == s2.getGateId()))
        {
            clearActivation(stargate);
            player.sendMessage(ConfigManager.MessageStrings.gateDeactivated.toString());
            return true;
        }

        if (stargate.isGateLightsActive() && !stargate.isGateActive())
        {
            // The gate is cleared whatever happens from here, so this arm is always a success.
            forceClearStaleActivation(stargate, player);
            return true;
        }

        player.sendMessage(ConfigManager.MessageStrings.gateRemoveActive.toString());
        return false;
    }

    /**
     * Stops a gate's activation timer and puts its lever and lights out.
     *
     * <p>The four calls have to go together and in this order: the timer would otherwise fire
     * against a gate that is already dark, and the lever reads {@code isGateActive} to decide
     * which way to throw, so clearing the flag before touching it is what makes it go down.
     *
     * @param stargate
     *            the gate to clear
     */
    static void clearActivation(final Stargate stargate)
    {
        stargate.stopActivationTimer();
        stargate.setGateActive(false);
        stargate.toggleDialLeverState(false);
        stargate.lightStargate(false);
    }

    /**
     * Clears a gate left lit by an activation that no longer has a player behind it.
     *
     * <p>Both people are told, when there is somebody to tell: whoever pressed the button
     * learns whose activation they cleared, and the original activator learns their pending
     * activation is gone rather than finding the gate mysteriously dark later.
     *
     * @param stargate
     *            the lit gate
     * @param player
     *            the player clearing it
     */
    static void forceClearStaleActivation(final Stargate stargate, final Player player)
    {
        // Stop timers and clear visual state. Deliberately before the activator lookup, which
        // is only needed to write the messages below: removeActivatorForStargate tolerates a
        // null gate and returns null, so calling it first says nothing about whether the gate
        // exists and leaves every dereference after it looking unguarded. Clearing first is
        // the dereference that settles it. The two do not interact -- one touches timers,
        // lever and lights, the other only the activated-gate map -- so the order is free.
        clearActivation(stargate);
        // Attempt to force-clear stale activation mapping so gate can be deactivated.
        final Player activator = StargateManager.removeActivatorForStargate(stargate);

        if (activator == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate deactivated.");
            return;
        }

        try
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate deactivated (was activated by: " + activator.getName() + ").");
            if (activator.isOnline())
            {
                activator.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Your pending gate activation was force-cleared by: " + player.getName());
            }
        }
        catch (final Exception e)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate deactivated.");
        }
    }

    /**
     * Dials a closed sign gate to whatever its sign is showing.
     *
     * @param stargate
     *            the closed sign-powered gate
     * @param player
     *            the player working the switch
     * @return true if the gates connected
     */
    static boolean dialFromSign(final Stargate stargate, final Player player)
    {
        final boolean isOwnerInner = player.isOp() || stargate.isOwner(player);
        if (!isOwnerInner && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.SIGN))
        {
            final String msg = "Permission denied for sign usage: player='" + player.getName() + "' gate='" + stargate.getGateName() + "' owner='" + stargate.getGateOwner() + "'";
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, msg);
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return false;
        }

        // Asked whenever there is no destination object, not just when the sign object is
        // missing: after a load a gate can have both its sign and its saved index and still no
        // destination, which is exactly the case that left the first press after a restart
        // dialling nothing. Resolving is immediate and does not advance the selection, so the
        // gate dials what the sign has been showing all along.
        if ((stargate.getGateDialSignTarget() == null) && (stargate.getGateDialSignBlock() != null))
        {
            stargate.refreshDialSignTarget();
        }

        final Stargate target = stargate.getGateDialSignTarget();
        if (target == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
            return false;
        }

        if (stargate.dialStargate(target, false))
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Stargates connected!");
            return true;
        }
        player.sendMessage(ConfigManager.MessageStrings.gateRemoveActive.toString());
        return false;
    }

    /**
     * Lights a closed dial gate and waits for the player to name a destination.
     *
     * @param stargate
     *            the closed gate
     * @param player
     *            the player activating it
     * @return true, always
     */
    static boolean activateForDialling(final Stargate stargate, final Player player)
    {
        //Activate Stargate
        player.sendMessage(ConfigManager.MessageStrings.gateActivated.toString());
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Chevrons Locked! \u00A73:: \u00A7B<required> \u00A76[optional]");
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Type \'\u00A7F/dial \u00A7B<gatename> \u00A76[idc]\u00A77\'");
        StargateManager.addActivatedStargate(player, stargate);
        stargate.startActivationTimer(player);
        stargate.lightStargate(true);
        return true;
    }

    /**
     * Handle player interact event.
     * 
     * @param event
     *            the event
     * @return true, if successful
     */
    static boolean handlePlayerInteractEvent(final PlayerInteractEvent event)
    {
        final Block clickedBlock = event.getClickedBlock();
        final Player player = event.getPlayer();

        if ((clickedBlock != null) && (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isButton(clickedBlock.getType()) || (clickedBlock.getType() == Material.LEVER)))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "PlayerInteract: " + player.getName() + " clicked potential activator at " + clickedBlock.getLocation().toString() + " type=" + clickedBlock.getType().toString());
            if (buttonLeverHit(player, clickedBlock, null))
            {
                return true;
            }
        }
        else if ((clickedBlock != null) && (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isWallSign(clickedBlock.getType())))
        {
            final Stargate stargate = StargateManager.getGateFromBlock(clickedBlock);
            if (stargate != null)
            {
                if (WXPermissions.checkWXPermissions(player, stargate, PermissionType.SIGN)
                    || player.isOp() || stargate.isOwner(player))
                {
                    // Right-click advances forward; left-click goes backward.
                    final boolean forward = (event.getAction() == Action.RIGHT_CLICK_BLOCK);
                    stargate.tryClickTeleportSign(clickedBlock, player, forward);
                }
                else
                {
                    player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                }
                // Always cancel the interact event for a registered gate sign block
                // so the sign editor never opens regardless of tryClickTeleportSign outcome.
                return true;
            }
        }
        return false;
    }

    /** Faces probed when a candidate dial block does not report its own orientation. */
    private static final BlockFace[] PROBE_FACES = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN };

    /**
     * Checks whether a block could be the dial device of an as-yet-unregistered gate.
     *
     * <p>A DHD is always something a player can click: a button, which the plugin swaps
     * for a lever on first activation, or a lever already.
     *
     * @param block
     *            the candidate
     * @return true if it could be a dial block
     */
    private static boolean isPossibleDialBlock(final Block block)
    {
        if (block == null)
        {
            return false;
        }
        final Material type = block.getType();
        return type == Material.LEVER || MaterialUtils.isButton(type);
    }

    /**
     * Looks for a complete but unregistered gate whose dial block sits next to the block
     * the player actually clicked.
     *
     * <p>This exists because a player can click the frame beside their lever rather than
     * the lever itself. It used to brute-force the problem: 26 surrounding blocks against
     * 6 faces each, so 156 full detection calls per click, every one of them running a
     * geometry check against every registered shape. That fires on any click on a
     * directional block — a lever, a button, stairs, a furnace, a sign — so on a busy
     * server it ran constantly and almost always found nothing.
     *
     * <p>It is now filtered on two cheap facts before any geometry work happens. A
     * candidate must look like a dial block, and the block it is mounted on must be a
     * frame material some shape or palette actually uses. A candidate that reports its own
     * orientation is probed on that face alone rather than all six. In the overwhelmingly
     * common case — a click near no gate at all — this does a couple of dozen block reads
     * and no shape scans whatsoever.
     *
     * @param clickedBlock
     *            the block the player clicked
     * @param player
     *            the clicking player
     * @return true if a candidate gate was found and the player was told about it
     */
    private static boolean findGateFromNearbyDial(final Block clickedBlock, final Player player)
    {
        final org.bukkit.World world = clickedBlock.getWorld();
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dy == 0 && dz == 0)
                    {
                        continue;
                    }
                    final Block candidate = world.getBlockAt(clickedBlock.getX() + dx,
                        clickedBlock.getY() + dy, clickedBlock.getZ() + dz);
                    if (!isPossibleDialBlock(candidate))
                    {
                        continue;
                    }
                    for (final BlockFace face : probeFaces(candidate))
                    {
                        // The dial hangs on a frame block, so if the block behind it is not
                        // a frame material no shape can match here.
                        final Block holder = candidate.getRelative(WorldUtils.getInverseDirection(face));
                        if (holder == null || !StargateHelper.isPossibleGateFrameMaterial(holder.getType()))
                        {
                            continue;
                        }
                        final Stargate nearbyGate = StargateHelper.checkStargate(candidate, face);
                        if (nearbyGate == null)
                        {
                            continue;
                        }
                        // Skip gates that are already fully registered — this prevents the
                        // "gate complete" prompt from firing when a player places a lever
                        // or button near an existing gate's structure blocks.
                        final Block nearbyDial = nearbyGate.getGateDialLeverBlock();
                        if ((nearbyDial != null) && (StargateManager.getGateFromBlock(nearbyDial) != null))
                        {
                            continue;
                        }
                        announceNearbyGate(nearbyGate, candidate, player);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Gets the faces worth probing for a candidate dial block: the one it actually faces
     * when it reports an orientation, otherwise all six.
     *
     * @param candidate
     *            the candidate dial block
     * @return the faces to probe
     */
    private static BlockFace[] probeFaces(final Block candidate)
    {
        final org.bukkit.block.data.BlockData data = candidate.getBlockData();
        if (data instanceof org.bukkit.block.data.Directional directional)
        {
            final BlockFace facing = directional.getFacing();
            if (facing != null)
            {
                return new BlockFace[] { facing };
            }
        }
        return PROBE_FACES;
    }

    /**
     * Registers a detected-but-unnamed gate against the player and tells them how to
     * finish it, or explains why they may not.
     *
     * @param nearbyGate
     *            the detected gate
     * @param candidate
     *            the dial block it was detected from, for the denial log line
     * @param player
     *            the clicking player
     */
    private static void announceNearbyGate(final Stargate nearbyGate, final Block candidate, final Player player)
    {
        if (WXPermissions.checkWXPermissions(player, nearbyGate, PermissionType.BUILD))
        {
            StargateManager.addIncompleteStargate(player, nearbyGate);
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Valid Stargate Design detected via nearby click! Type '/wormhole complete <name>' to complete.");
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
                "Permission denied on nearby/gate-detection: player='" + player.getName()
                + "' nearbyBlock='" + candidate.getLocation().toString() + "'");
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
        }
    }
}
