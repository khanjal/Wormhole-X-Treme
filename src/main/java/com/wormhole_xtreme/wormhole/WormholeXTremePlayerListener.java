package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

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
 * WormholeXtreme Player Listener.
 *
 * <p><b>On the {@code catch (RuntimeException ignore)} blocks in this class.</b> A gate
 * teleport is a sequence of independent effects on the player and whatever they are
 * riding — position, velocity, fall distance, air, passenger seating, chunk loading. Most
 * are cosmetic settling, and one of them failing is not a reason to abandon the rest and
 * leave the player half-moved. Those calls are individually guarded and deliberately
 * silent.
 *
 * <p>That is the only thing a silent guard may cover. Anything that changes plugin state —
 * a cooldown, an arrival marker, a gate registration — logs when it fails, because
 * swallowing those hides real bugs and, in the cooldown's case, is exploitable. Nothing
 * here catches {@link Throwable}: an {@link Error} is never something this plugin should
 * absorb.
 *
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
class WormholeXTremePlayerListener implements Listener
{
    

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
    private static boolean buttonLeverHit(final Player player, final Block clickedBlock, BlockFace direction)
    {
        // Check if player is awaiting interactive /wormhole complete completion
        try
        {
            final String[] pending = com.wormhole_xtreme.wormhole.command.Complete.getPendingCompletion(player);
            if (pending != null)
            {
                String name = pending[0];
                String idc = pending[1];
                String network = pending[2];

                // Determine facing if not provided
                if (direction == null && clickedBlock.getBlockData() instanceof org.bukkit.block.data.Directional)
                {
                    direction = ((org.bukkit.block.data.Directional) clickedBlock.getBlockData()).getFacing();
                }

                com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, false, "+/wormhole complete interactive: attempting detection for player=" + player.getName() + " at " + clickedBlock.getLocation());

                com.wormhole_xtreme.wormhole.model.Stargate found = null;
                try
                {
                    if (direction != null)
                    {
                        found = com.wormhole_xtreme.wormhole.logic.StargateHelper.checkStargate(clickedBlock, direction);
                    }
                    if (found == null)
                    {
                        // try common facings
                        final org.bukkit.block.BlockFace[] faces = new org.bukkit.block.BlockFace[] { org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN };
                        for (final org.bukkit.block.BlockFace face : faces)
                        {
                            try
                            {
                                found = com.wormhole_xtreme.wormhole.logic.StargateHelper.checkStargate(clickedBlock, face);
                                if (found != null)
                                {
                                    break;
                                }
                            }
                            catch (final RuntimeException e)
                            {
                                // A throw here means a malformed shape, not a normal miss.
                                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                                    "Shape detection failed for face " + face + ": " + e.getMessage());
                            }
                        }
                    }
                }
                catch (final RuntimeException t)
                {
                    com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, false, "/wormhole complete interactive detection error: " + t.getMessage());
                }

                if (found != null)
                {
                    com.wormhole_xtreme.wormhole.model.StargateManager.addIncompleteStargate(player, found);
                    final double buildCost = (ConfigManager.isEconomyEnabled() && com.wormhole_xtreme.wormhole.plugin.EconomySupport.isAvailable()) ? ConfigManager.getEconomyBuildCost() : 0.0;
                    if (buildCost > 0 && !com.wormhole_xtreme.wormhole.plugin.EconomySupport.canAfford(player, buildCost))
                    {
                        player.sendMessage(ConfigManager.MessageStrings.economyInsufficientFunds.toString());
                    }
                    else if (com.wormhole_xtreme.wormhole.model.StargateManager.completeStargate(player, name, idc, network))
                    {
                        player.sendMessage(ConfigManager.MessageStrings.constructSuccess.toString());
                        if (buildCost > 0)
                        {
                            com.wormhole_xtreme.wormhole.plugin.EconomySupport.charge(player, buildCost);
                            player.sendMessage(ConfigManager.MessageStrings.economyBuildCharged.toString()
                                + buildCost + " " + com.wormhole_xtreme.wormhole.plugin.EconomySupport.currencyName(buildCost));
                        }
                    }
                    else
                    {
                        player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Construction Failed after interactive detection. Check server log.");
                    }
                    com.wormhole_xtreme.wormhole.command.Complete.removePendingCompletion(player);
                    return true;
                }
                else
                {
                    player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "No gate detected at clicked block. Try clicking the DHD button/lever again.");
                    // Diagnostic: iterate shapes and facings to report why detection failed
                    try
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, false, "+/wormhole complete diag: running detailed detection diagnostics for player=" + player.getName());
                        final org.bukkit.block.BlockFace[] faces = new org.bukkit.block.BlockFace[] { org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN };
                        for (final org.bukkit.block.BlockFace face : faces)
                        {
                            try
                            {
                                final org.bukkit.block.BlockFace opposite = com.wormhole_xtreme.wormhole.utils.WorldUtils.getInverseDirection(face);
                                final org.bukkit.block.Block holding = clickedBlock.getRelative(opposite);
                                final org.bukkit.block.Block below = holding.getRelative(org.bukkit.block.BlockFace.DOWN);
                                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, false, "+/wormhole complete diag: face=" + face + " holding=" + holding.getLocation().toString() + " holdingType=" + holding.getType().toString() + " below=" + below.getLocation().toString() + " belowType=" + below.getType().toString());
                            }
                            catch (final RuntimeException ignore) {}
                        }
                        WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, false, "+/wormhole complete diag: end diagnostics");
                    }
                    // Diagnostics only: a failure here costs a log line, nothing more.
                    catch (final RuntimeException ignore) {}
                    return true;
                }
            }
        }
        catch (final RuntimeException e)
        {
            // This block completes a gate the player asked for, charges them, and messages
            // them. Failing silently would leave them staring at an unbuilt gate.
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Interactive /wormhole complete failed for " + player.getName() + ": " + e.getMessage());
        }

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
            final String oldOwnerNm = existing.getGateOwnerName();
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

            // Remove the stale registration (no block destruction).
            CommandUtilities.gateRemove(existing, false);

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
            com.wormhole_xtreme.wormhole.model.StargateDBManager.stargateToSQL(fresh);
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Gate '" + oldName + "' refreshed successfully.");
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                "Gate '" + oldName + "' refreshed by " + player.getName()
                + " facing=" + (detectedFacing != null ? detectedFacing.toString() : "null")
                + " tpLoc=" + (fresh.getGatePlayerTeleportLocation() != null ? fresh.getGatePlayerTeleportLocation().toString() : "null"));
            return true;
        }

        final Stargate stargate = StargateManager.getGateFromBlock(clickedBlock);

        if (stargate != null)
        {
            // Disambiguate exact vs adjacent lever clicks to avoid mis-classifying the iris lever
            final boolean dialSame = (stargate.getGateDialLeverBlock() != null) && WorldUtils.isSameBlock(stargate.getGateDialLeverBlock(), clickedBlock);
            final boolean irisSame = (stargate.getGateIrisLeverBlock() != null) && WorldUtils.isSameBlock(stargate.getGateIrisLeverBlock(), clickedBlock);
            final boolean dialAdj = (stargate.getGateDialLeverBlock() != null) && WorldUtils.isAdjacent(stargate.getGateDialLeverBlock(), clickedBlock);
            final boolean irisAdj = (stargate.getGateIrisLeverBlock() != null) && WorldUtils.isAdjacent(stargate.getGateIrisLeverBlock(), clickedBlock);

            // Gate owners and ops always bypass permission checks.
            final boolean isOwner = player.isOp() || stargate.isOwner(player);
            final boolean permSign = isOwner || WXPermissions.checkWXPermissions(player, stargate, PermissionType.SIGN);
            final boolean permDialer = isOwner || WXPermissions.checkWXPermissions(player, stargate, PermissionType.DIALER);

            // Priority: exact same-block match wins. For adjacency, prefer dial when both levers are adjacent
            // (e.g. iris lever right next to the dial lever — this is the common Standard gate layout).
            if (dialSame && ((stargate.isGateSignPowered() && permSign) || (!stargate.isGateSignPowered() && permDialer)))
            {
                handleGateActivationSwitch(stargate, player);
            }
            else if (irisSame && !dialSame && permDialer)
            {
                stargate.toggleIrisActive(true);
            }
            else if (dialAdj && ((stargate.isGateSignPowered() && permSign) || (!stargate.isGateSignPowered() && permDialer)))
            {
                // Both adjacent (overlapping layout) — treat as dial activation.
                handleGateActivationSwitch(stargate, player);
            }
            else if (irisAdj && !dialAdj && permDialer)
            {
                stargate.toggleIrisActive(true);
            }
            else if (dialSame || irisSame || dialAdj || irisAdj)
            {
                player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            }
            return true;
        }
        else
        {
            if (direction == null)
            {
                if (clickedBlock.getBlockData() instanceof org.bukkit.block.data.Directional)
                {
                    direction = ((org.bukkit.block.data.Directional) clickedBlock.getBlockData()).getFacing();
                }

                if (direction == null)
                {
                    return false;
                }
            }
            // Check to see if player has already run the "build" command.
            final StargateShape shape = StargateManager.getPlayerBuilderShape(player);

            Stargate newGate = null;
            if (shape != null)
            {
                newGate = StargateHelper.checkStargate(clickedBlock, direction, shape);
            }
            else
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINEST, false, "Attempting to find any gate shapes!");
                newGate = StargateHelper.checkStargate(clickedBlock, direction);
            }

            if (newGate != null)
            {
                if (WXPermissions.checkWXPermissions(player, newGate, PermissionType.BUILD) && !StargateRestrictions.isPlayerBuildRestricted(player))
                {
                    if (newGate.isGateSignPowered())
                    {
                        // Sign-powered gates follow the same /wormhole complete flow as regular gates,
                        // so the player can specify a network and IDC code.
                        final String signName = newGate.getGateName();
                        StargateManager.addIncompleteStargate(player, newGate);
                        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid Sign Nav Stargate Design! \u00A73:: \u00A7B<required> \u00A76[optional]");
                        if (signName.isEmpty())
                        {
                            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Type \'\u00A7F/wormhole complete \u00A7B<name> \u00A76[idc=IDC] [net=NET]\u00A77\' to complete.");
                        }
                        else
                        {
                            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Type \'\u00A7F/wormhole complete \u00A7B" + signName + " \u00A76[idc=IDC] [net=NET]\u00A77\' to complete.");
                        }
                    }
                    else
                    {
                        // Print to player that it was successful!
                        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid Stargate Design! \u00A73:: \u00A7B<required> \u00A76[optional]");
                        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Type \'\u00A7F/wormhole complete \u00A7B<name> \u00A76[idc=IDC] [net=NET]\u00A77\' to complete.");
                        // Add gate to unnamed gates.
                        StargateManager.addIncompleteStargate(player, newGate);
                    }
                    return true;
                }
                else
                {
                    if (newGate.isGateSignPowered())
                    {
                        newGate.resetTeleportSign();
                    }
                    StargateManager.removeIncompleteStargate(player);
                    if (StargateRestrictions.isPlayerBuildRestricted(player))
                    {
                        player.sendMessage(ConfigManager.MessageStrings.playerBuildCountRestricted.toString());
                    }
                    player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                    return true;
                }
            }
            else
            {
                // Fallback: the player may have clicked beside the DHD rather than on
                // it, so probe the surrounding blocks. See findGateFromNearbyDial for why
                // this is filtered rather than brute-forced.
                final boolean foundNearby = findGateFromNearbyDial(clickedBlock, player);

                if (!foundNearby)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, player.getName() + " has pressed a button or lever but did not find any properly created gates.");
                }
            }
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
    private static boolean handleGateActivationSwitch(final Stargate stargate, final Player player)
    {
        if (stargate.isGateActive() || stargate.isGateLightsActive())
        {
            if (stargate.getGateTarget() != null)
            {
                //Shutdown stargate
                stargate.shutdownStargate(true);
                player.sendMessage(ConfigManager.MessageStrings.gateShutdown.toString());
                return true;
            }
            else
            {
                final Stargate s2 = StargateManager.removeActivatedStargate(player);
                if ((s2 != null) && (stargate.getGateId() == s2.getGateId()))
                {
                    stargate.stopActivationTimer();
                    stargate.setGateActive(false);
                    stargate.toggleDialLeverState(false);
                    stargate.lightStargate(false);
                    player.sendMessage(ConfigManager.MessageStrings.gateDeactivated.toString());
                    return true;
                }
                else
                {
                    if (stargate.isGateLightsActive() && !stargate.isGateActive())
                    {
                        // Attempt to force-clear stale activation mapping so gate can be deactivated.
                        final org.bukkit.entity.Player activator = StargateManager.removeActivatorForStargate(stargate);
                        // Stop timers and clear visual state
                        stargate.stopActivationTimer();
                        stargate.setGateActive(false);
                        stargate.toggleDialLeverState(false);
                        stargate.lightStargate(false);
                        if (activator != null)
                        {
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
                        else
                        {
                            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate deactivated.");
                        }
                        return true;
                    }
                    else
                    {
                        player.sendMessage(ConfigManager.MessageStrings.gateRemoveActive.toString());
                    }
                    return false;
                }
            }
        }
        else
        {
            if (stargate.isGateSignPowered())
            {
                final boolean isOwnerInner = player.isOp() || stargate.isOwner(player);
                if (isOwnerInner || WXPermissions.checkWXPermissions(player, stargate, PermissionType.SIGN))
                {
                    if ((stargate.getGateDialSign() == null) && (stargate.getGateDialSignBlock() != null))
                    {
                        stargate.tryClickTeleportSign(stargate.getGateDialSignBlock());
                    }

                    if (stargate.getGateDialSignTarget() != null)
                    {
                        if (stargate.dialStargate(stargate.getGateDialSignTarget(), false))
                        {
                            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Stargates connected!");
                            return true;
                        }
                        else
                        {
                            player.sendMessage(ConfigManager.MessageStrings.gateRemoveActive.toString());
                            return false;
                        }
                    }
                    else
                    {
                        player.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                        return false;
                    }
                }
                else
                {
                    final String msg = "Permission denied for sign usage: player='" + player.getName() + "' gate='" + stargate.getGateName() + "' owner='" + stargate.getGateOwner() + "'";
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, msg);
                    player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                    return false;
                }
            }
            else
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
        }
    }

    /**
     * Handle player interact event.
     * 
     * @param event
     *            the event
     * @return true, if successful
     */
    private static boolean handlePlayerInteractEvent(final PlayerInteractEvent event)
    {
        final Block clickedBlock = event.getClickedBlock();
        final Player player = event.getPlayer();

        if ((clickedBlock != null) && (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isButton(clickedBlock.getType()) || (clickedBlock.getType() == Material.LEVER)))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerInteract: " + player.getName() + " clicked potential activator at " + clickedBlock.getLocation().toString() + " type=" + clickedBlock.getType().toString());
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

    private static boolean hasChangedBlockCoordinates(final Location fromLoc, final Location toLoc) {
        if (fromLoc.getBlockX() == toLoc.getBlockX()
                && fromLoc.getBlockY() == toLoc.getBlockY()
                && fromLoc.getBlockZ() == toLoc.getBlockZ()) {
            return false;
        }
        return true;
    }

    private static Location findSafePlayerLocation(final Location preferred)
    {
        if (preferred == null || preferred.getWorld() == null)
        {
            return preferred;
        }
        final org.bukkit.World w = preferred.getWorld();
        final int x = preferred.getBlockX();
        final int z = preferred.getBlockZ();
        final int baseY = preferred.getBlockY();

        // Prefer the exact stored location if it is safe, then search upward, then down.
        for (int dy = 0; dy <= 3; dy++)
        {
            if (isStandableAt(w, x, baseY + dy, z))
            {
                return new Location(w, x + 0.5, baseY + dy, z + 0.5, preferred.getYaw(), preferred.getPitch());
            }
        }

        for (int dy = 1; dy <= 3; dy++)
        {
            final int y = baseY - dy;
            if (y < w.getMinHeight())
            {
                break;
            }
            if (isStandableAt(w, x, y, z))
            {
                return new Location(w, x + 0.5, y, z + 0.5, preferred.getYaw(), preferred.getPitch());
            }
        }

        // Fallback to the original preferred location
        return preferred.clone();
    }

    /**
     * Checks whether a player can stand at the given block: head and feet clear, solid
     * ground underneath.
     *
     * <p>The blocks are null-checked rather than wrapped in a catch. A world can return
     * null for an unloaded or out-of-range column, and "no block there" is an ordinary
     * answer meaning not standable — not an error worth swallowing.
     *
     * @param w
     *            the world
     * @param x
     *            block x
     * @param y
     *            block y of the player's feet
     * @param z
     *            block z
     * @return true if a player can stand there
     */
    private static boolean isStandableAt(final org.bukkit.World w, final int x, final int y, final int z)
    {
        final org.bukkit.block.Block feet = w.getBlockAt(x, y, z);
        final org.bukkit.block.Block head = w.getBlockAt(x, y + 1, z);
        final org.bukkit.block.Block below = w.getBlockAt(x, y - 1, z);
        if (feet == null || head == null || below == null)
        {
            return false;
        }
        return feet.isPassable() && head.isPassable() && !below.isPassable();
    }

    /**
     * Returns true if {@code ridden} is a living mount this listener is responsible for.
     *
     * <p>Note that {@code instanceof Vehicle} does not make this distinction: in Bukkit
     * {@code Pig} and {@code AbstractHorse} — so horses, camels, donkeys, mules and
     * llamas, the whole point of the feature — all extend {@link Vehicle}. What actually
     * separates the two cases is which event fires. Minecarts and boats raise
     * {@code VehicleMoveEvent} and are handled by {@link WormholeXTremeVehicleListener};
     * living mounts never raise it, so they can only be caught here off the rider's move.
     * This predicate deliberately mirrors that listener's own filter so the two partition
     * the space with no gap and no double-handling.
     *
     * @param ridden
     *            the entity the player is riding, may be null
     * @return true if this listener should handle it as a mount
     */
    private static boolean isLivingMount(final Entity ridden)
    {
        return ridden != null && !(ridden instanceof Minecart) && !(ridden instanceof Boat);
    }

    /**
     * Finds a portal block of an active gate that {@code mount} is standing in.
     *
     * <p>Scans the blocks the mount's bounding box overlaps rather than a fixed cube
     * around it, so a wide or tall mount is covered exactly and a small one costs
     * only the handful of lookups it actually occupies.
     *
     * <p>Only portal blocks count. Matching any gate block would let a structure
     * block win the search, and the caller requires a portal block — so the mount
     * would be silently ignored even with its legs in the open wormhole.
     *
     * @param mount
     *            the entity being ridden
     * @return a portal block of an active gate, or null if the mount is not in one
     */
    private static Block findActiveGatePortalBlockAtMount(final Entity mount)
    {
        final Location ml = mount.getLocation();
        if (ml == null || ml.getWorld() == null)
        {
            return null;
        }
        final org.bukkit.World world = ml.getWorld();

        // Default to the mount's own block and the one above it, covering the common
        // case when no bounding box is available.
        int minX = ml.getBlockX(), maxX = minX;
        int minY = ml.getBlockY(), maxY = minY + 1;
        int minZ = ml.getBlockZ(), maxZ = minZ;
        final org.bukkit.util.BoundingBox box = mount.getBoundingBox();
        if (box != null)
        {
            minX = (int) Math.floor(box.getMinX());
            maxX = (int) Math.floor(box.getMaxX());
            minY = (int) Math.floor(box.getMinY());
            maxY = (int) Math.floor(box.getMaxY());
            minZ = (int) Math.floor(box.getMinZ());
            maxZ = (int) Math.floor(box.getMaxZ());
        }

        for (int bx = minX; bx <= maxX; bx++)
        {
            for (int by = minY; by <= maxY; by++)
            {
                for (int bz = minZ; bz <= maxZ; bz++)
                {
                    final Block b = world.getBlockAt(bx, by, bz);
                    final Stargate s = StargateManager.getGateFromBlock(b);
                    if (s != null && s.isGateActive() && StargateManager.isPortalBlock(b))
                    {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    /** Maximum re-seat attempts before a passenger is given up on. */
    private static final int MAX_REATTACH_ATTEMPTS = 12;

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
        if (data instanceof org.bukkit.block.data.Directional)
        {
            final BlockFace facing = ((org.bukkit.block.data.Directional) data).getFacing();
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
        if (WXPermissions.checkWXPermissions(player, nearbyGate, PermissionType.BUILD)
            && !StargateRestrictions.isPlayerBuildRestricted(player))
        {
            StargateManager.addIncompleteStargate(player, nearbyGate);
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Valid Stargate Design detected via nearby click! Type '/wormhole complete <name>' to complete.");
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                "Permission denied on nearby/gate-detection: player='" + player.getName()
                + "' nearbyBlock='" + candidate.getLocation().toString() + "'");
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
        }
    }

    /**
     * Teleports a player through a gate on their own, with no mount involved.
     *
     * @param player
     *            the player
     * @param safeTarget
     *            the vetted arrival location
     */
    private static void teleportPlayerAlone(final Player player, final Location safeTarget)
    {
        // Safety net: ensure destination chunk is loaded even if it unloaded since dial time.
        try { WorldUtils.forceLoadDestinationChunks(safeTarget); } catch (final RuntimeException ignore) {}
        player.teleport(safeTarget);
        try
        {
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
        }
        catch (final RuntimeException ignore) {}
    }

    /**
     * Seats {@code passenger} on {@code parent}, retrying once via a position sync.
     *
     * @param parent
     *            the entity to ride
     * @param passenger
     *            the entity to seat
     * @return true if the passenger ends up aboard
     */
    private static boolean attachPassenger(final Entity parent, final Entity passenger)
    {
        try
        {
            if (parent.addPassenger(passenger))
            {
                return true;
            }
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger failed: " + t.getMessage());
        }
        // An earlier attempt may already have succeeded without reporting it.
        try
        {
            if (parent.getPassengers().contains(passenger))
            {
                return true;
            }
        }
        catch (final RuntimeException ignore) {}
        // A passenger too far from its parent is refused, so close the gap and retry.
        try
        {
            passenger.teleport(parent.getLocation());
            return parent.addPassenger(passenger);
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger after position sync failed: " + t.getMessage());
        }
        return false;
    }

    /**
     * Re-seats every passenger of {@code ridden} after it has been teleported through a
     * gate, then applies its exit velocity once the whole stack is aboard.
     *
     * <p>Teleporting an entity detaches its passengers, and the client does not reliably
     * accept the re-seat on the first try, so this retries on an exponential backoff.
     * Boats additionally need a position re-sync or the client keeps drawing them at the
     * departure point.
     *
     * @param ridden
     *            the boat or mount that was just teleported
     * @param player
     *            the moving player, re-seated even when the teleport already detached them
     * @param exitVelocity
     *            velocity to apply once everyone is aboard, may be null
     */
    private static void schedulePassengerReattach(final Entity ridden, final Player player, final Vector exitVelocity)
    {
        final java.util.List<Entity> parents = new java.util.ArrayList<Entity>();
        final java.util.List<Entity> children = new java.util.ArrayList<Entity>();
        WormholeXTremeVehicleListener.collectPassengerPairs(ridden, parents, children);
        // The teleport has usually already detached the moving player, so they will not
        // appear in the collected tree — put them back explicitly.
        if (!children.contains(player))
        {
            parents.add(ridden);
            children.add(player);
        }

        final int[] attempts = new int[] { 0 };
        final boolean[] attached = new boolean[children.size()];
        final Runnable[] taskHolder = new Runnable[1];
        taskHolder[0] = new Runnable()
        {
            @Override
            public void run()
            {
                attempts[0]++;
                try
                {
                    if (!ridden.isValid())
                    {
                        return;
                    }
                    int remaining = 0;
                    for (int i = 0; i < children.size(); i++)
                    {
                        if (attached[i])
                        {
                            continue;
                        }
                        final Entity psg = children.get(i);
                        try
                        {
                            if (!psg.isValid())
                            {
                                continue;
                            }
                            if (attachPassenger(parents.get(i), psg))
                            {
                                attached[i] = true;
                            }
                            else
                            {
                                remaining++;
                            }
                        }
                        catch (final RuntimeException t)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Exception during passenger reattach: " + t.getMessage());
                            remaining++;
                        }
                    }

                    if (remaining == 0)
                    {
                        try
                        {
                            ridden.setVelocity(exitVelocity != null ? exitVelocity : new Vector(0, 0, 0));
                            ridden.setFireTicks(0);
                        }
                        catch (final RuntimeException ignore) {}
                        if (ridden instanceof Boat)
                        {
                            final Location resyncLoc = ridden.getLocation();
                            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    try { if (ridden.isValid()) { ridden.teleport(resyncLoc); } } catch (final RuntimeException ignore) {}
                                }
                            }, 3L);
                        }
                    }
                    else if (attempts[0] < MAX_REATTACH_ATTEMPTS)
                    {
                        final long backoff = Math.min(1L << Math.max(0, attempts[0] - 1), 20L);
                        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], backoff);
                    }
                    else
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to reattach passengers to " + ridden.getUniqueId() + " after " + attempts[0] + " attempts");
                    }
                }
                catch (final RuntimeException t)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception during passenger reattach: " + t.getMessage());
                }
            }
        };
        // 2-tick delay: there is no teleport-ack to wait for, the client re-seats immediately.
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], 2);
    }

    /**
     * Handle player move event.
     *
     * @param event
     *            the event
     * @return true, if successful
     */
    private static boolean handlePlayerMoveEvent(final PlayerMoveEvent event)
    {
        if (!hasChangedBlockCoordinates(event.getFrom(), event.getTo()))
        {
            return false;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "handlePlayerMoveEvent: event player is null, ignoring event.");
            return false;
        }
        final Location toLocFinal = event.getTo();
        // Every player crossing a block boundary reaches here, so the diagnostic is built
        // only when it would actually be printed. It used to call Player.toString() and
        // two extra getBlockAt() lookups on every crossing and throw all of it away.
        if (WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
        {
            try
            {
                final Block fromBlock = event.getFrom().getWorld().getBlockAt(event.getFrom().getBlockX(), event.getFrom().getBlockY(), event.getFrom().getBlockZ());
                final Block toBlock = toLocFinal.getWorld().getBlockAt(toLocFinal.getBlockX(), toLocFinal.getBlockY(), toLocFinal.getBlockZ());
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerMove: " + player.getName()
                    + " from=" + fromBlock.getType() + " to=" + toBlock.getType() + " y=" + toLocFinal.getY());
            }
            // Diagnostics only, and on the move path, so never let it disturb the event.
            catch (final RuntimeException ignore) {}
        }
        Block gateBlockFinal = toLocFinal.getWorld().getBlockAt(toLocFinal.getBlockX(), toLocFinal.getBlockY(), toLocFinal.getBlockZ());
        Stargate stargate = StargateManager.getGateFromBlock(gateBlockFinal);

        // A rider's own block is not a reliable trigger: a camel is tall enough that
        // the rider clears the portal entirely while the camel stands in it. When the
        // player's block is not a gate, look for one under their mount instead so the
        // mount-first teleport still fires.
        if (stargate == null)
        {
            final Entity ridden = player.getVehicle();
            if (isLivingMount(ridden))
            {
                final Block mountBlock = findActiveGatePortalBlockAtMount(ridden);
                if (mountBlock != null)
                {
                    gateBlockFinal = mountBlock;
                    stargate = StargateManager.getGateFromBlock(mountBlock);
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Detected mount-based gate entry for player=" + player.getName() + " via mount=" + ridden + " at block=" + mountBlock.getLocation());
                }
            }
        }

        if (stargate != null && stargate.isGateActive() && StargateManager.isPortalBlock(gateBlockFinal))
        {
            // If this gate has an outgoing target, it's the origin side: handle teleport as before.
            if (stargate.getGateTarget() != null)
            {
                // existing origin handling continues below
            }
            else
            {
                // Gate is active but has no local target: check whether it's the destination of an active incoming connection.
                boolean incomingActive = false;
                try
                {
                    for (final Stargate s : StargateManager.getAllGates())
                    {
                        if ((s != null) && (s.getGateTarget() != null) && (s.getGateTarget() == stargate) && s.isGateActive())
                        {
                            incomingActive = true;
                            break;
                        }
                    }
                }
                catch (final RuntimeException ignore) {}

                if (incomingActive)
                {
                    // Block entry into destination gate while an incoming wormhole is active.
                    player.sendMessage(ConfigManager.MessageStrings.playerRecentArrival.toString());
                    player.setNoDamageTicks(5);
                    final Location prev = stargate.getGatePlayerTeleportLocation();
                    if (prev != null)
                    {
                        event.setFrom(prev);
                        event.setTo(prev);
                        // A failure here means the player was not held out of the gate.
                        try { player.teleport(prev); }
                        catch (final RuntimeException e)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                                "Failed to hold " + player.getName() + " out of gate: " + e.getMessage());
                        }
                    }
                    return true;
                }
                // Gate is active but has neither an outgoing target nor an incoming
                // wormhole — an activated-but-undialed gate. There is nowhere to send
                // the player, so let them walk through the empty ring untouched.
                // Everything below this point dereferences getGateTarget().
                return false;
            }
            // Suppress solo teleport if this player was just ejected by a vehicle that VehicleListener
            // already teleported — the player must exit riding the vehicle, not as a solo traveller.
            if (WormholeXTremeVehicleListener.isPlayerRecentlyTeleportedByVehicle(player.getUniqueId()))
            {
                return false;
            }
            String gatenetwork;
            if (stargate.getGateNetwork() != null)
            {
                gatenetwork = stargate.getGateNetwork().getNetworkName();
            }
            else
            {
                gatenetwork = "Public";
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Player in gate:" + stargate.getGateName() + " gate Active: " + stargate.isGateActive() + " Target Gate: " + stargate.getGateTarget().getGateName() + " Network: " + gatenetwork);

            // Refill the player's air while they stand in the portal so a water-material
            // gate does not drown them. Cosmetic, so a failure is not worth reporting.
            try { player.setRemainingAir(player.getMaximumAir()); } catch (final RuntimeException ignore) {}

            if (ConfigManager.getWormholeUseIsTeleport() && ((stargate.isGateSignPowered() && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.SIGN)) || ( !stargate.isGateSignPowered() && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.DIALER))))
            {
                player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                return false;
            }

            // Prevent immediate re-entry to the gate the player just exited from.
            if (com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.isPlayerRecentArrivalFrom(player, stargate))
            {
                player.sendMessage(ConfigManager.MessageStrings.playerRecentArrival.toString());
                player.setNoDamageTicks(5);
                final Location prev = stargate.getGatePlayerTeleportLocation();
                if (prev != null)
                {
                    event.setFrom(prev);
                    event.setTo(prev);
                    // A failure here means the player was not held out of the gate.
                    try { player.teleport(prev); }
                    catch (final RuntimeException e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                            "Failed to hold " + player.getName() + " out of gate: " + e.getMessage());
                    }
                }
                return true;
            }

            if (ConfigManager.isUseCooldownEnabled())
            {
                if (StargateRestrictions.isPlayerUseCooldown(player))
                {
                    player.sendMessage(ConfigManager.MessageStrings.playerUseCooldownRestricted.toString());
                    player.sendMessage(ConfigManager.MessageStrings.playerUseCooldownWaitTime.toString() + StargateRestrictions.checkPlayerUseCooldownRemaining(player));
                    return false;
                }
                else
                {
                    StargateRestrictions.addPlayerUseCooldown(player);
                }
            }

            if (ConfigManager.isEconomyEnabled() && com.wormhole_xtreme.wormhole.plugin.EconomySupport.isAvailable())
            {
                final double useCost = ConfigManager.getEconomyUseCost();
                if (useCost > 0)
                {
                    if (!com.wormhole_xtreme.wormhole.plugin.EconomySupport.canAfford(player, useCost))
                    {
                        player.sendMessage(ConfigManager.MessageStrings.economyInsufficientFunds.toString());
                        return false;
                    }
                    com.wormhole_xtreme.wormhole.plugin.EconomySupport.charge(player, useCost);
                    player.sendMessage(ConfigManager.MessageStrings.economyCharged.toString()
                        + useCost + " " + com.wormhole_xtreme.wormhole.plugin.EconomySupport.currencyName(useCost));
                }
            }

            if (stargate.getGateTarget().isGateIrisActive())
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Remote Iris is locked!");
                player.setNoDamageTicks(5);
                event.setFrom(stargate.getGatePlayerTeleportLocation());
                event.setTo(stargate.getGatePlayerTeleportLocation());
                player.teleport(stargate.getGatePlayerTeleportLocation());
                return true;
            }

            final Location target = stargate.getGateTarget().getGatePlayerTeleportLocation();

            if (ConfigManager.isSameWorldOnly())
            {
                final org.bukkit.World targetWorld = (target != null) ? target.getWorld() : null;
                if (targetWorld != null && !gateBlockFinal.getWorld().equals(targetWorld))
                {
                    player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Cross-world travel is disabled on this server.");
                    player.setNoDamageTicks(5);
                    return false;
                }
            }

            final Location safeTarget = findSafePlayerLocation(target);
            // Diagnostic logging for teleport issues
            if (WormholeXTreme.getThisPlugin() != null)
            {
                if (target == null)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Teleport target is null for gate: " + stargate.getGateTarget().getGateName());
                }
                else if (target.getWorld() == null)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Teleport target world is null for gate: " + stargate.getGateTarget().getGateName() + " loc: " + target.toString());
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Teleporting " + player.getName() + " to " + stargate.getGateTarget().getGateName() + " @ " + target.toString());
                }
            }
            player.setNoDamageTicks(5);
            // Capture the player's current position before any event/teleport manipulation.
            final Location playerCurrentLoc = event.getFrom().clone();
            // Track whether the vehicle-only path was taken (no explicit player teleport).
            final boolean[] vehiclePathUsed = { false };
            // Whatever the player is riding: boat, horse, camel, pig, strider. Minecarts
            // are the one exception — they raise VehicleMoveEvent, so the vehicle listener
            // owns them and teleports them in place with passenger state preserved.
            final Entity ridden = player.getVehicle();
            if (ridden instanceof Minecart)
            {
                return false;
            }
            // For every other flow, mark the event position to the safe target and continue.
            event.setFrom(safeTarget);
            event.setTo(safeTarget);
            try
            {
                if (ridden != null)
                {
                    final BlockFace exitFacing = stargate.getGateTarget().getGateFacing();
                    final Location riddenTarget = WormholeXTremeVehicleListener.forwardAndUp(safeTarget, exitFacing, 1.0, 1.0);
                    Vector exitVelocity = null;
                    try
                    {
                        exitVelocity = WormholeXTremeVehicleListener.computeExitVelocity(exitFacing, ridden.getVelocity(), 5.0);
                        if (riddenTarget != null && exitVelocity != null)
                        {
                            // Face the direction of travel so the client does not render the
                            // mount spinning to its new yaw after arrival.
                            final double dx = exitVelocity.getX();
                            final double dz = exitVelocity.getZ();
                            riddenTarget.setYaw((Math.abs(dx) > 0.0001 || Math.abs(dz) > 0.0001)
                                ? (float) Math.toDegrees(Math.atan2( -dx, dz))
                                : WorldUtils.getDegreesFromBlockFace(exitFacing));
                            riddenTarget.setPitch(0f);
                        }
                    }
                    catch (final RuntimeException ignore) {}

                    // Safety net: ensure destination chunk is loaded even if it unloaded since dial time.
                    try { WorldUtils.forceLoadDestinationChunks(riddenTarget); } catch (final RuntimeException ignore) {}
                    // Mark before the teleport so a VehicleMoveEvent in the same tick is
                    // suppressed and does not double-process this entry, zeroing the exit velocity.
                    try { WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(ridden.getUniqueId()); } catch (final RuntimeException ignore) {}

                    boolean riddenTeleported = false;
                    try
                    {
                        ridden.teleport(riddenTarget);
                        riddenTeleported = true;
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerTeleport: teleported " + ridden.getType().name() + " " + ridden.getUniqueId() + " for player " + player.getName());
                    }
                    catch (final RuntimeException tt)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to teleport what " + player.getName() + " was riding: " + tt.getMessage());
                    }

                    if (riddenTeleported)
                    {
                        // Ride-first: no player.teleport() at all, so there is no teleport-ack
                        // race when the client processes the follow-up set-passengers packet.
                        vehiclePathUsed[0] = true;
                        event.setFrom(playerCurrentLoc);
                        event.setTo(playerCurrentLoc);
                        schedulePassengerReattach(ridden, player, exitVelocity);
                    }
                    else
                    {
                        // Could not move what they were riding; send the player through alone
                        // rather than stranding them on the source side.
                        teleportPlayerAlone(player, safeTarget);
                    }
                }
                else
                {
                    teleportPlayerAlone(player, safeTarget);
                }
            }
            catch (final Exception e)
            {
                if (WormholeXTreme.getThisPlugin() != null)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception while teleporting " + player.getName() + " to " + (target == null ? "null" : target.toString()) + ": " + e.getMessage());
                }
            }
            try
            {
                com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.addPlayerUseCooldown(player);
            }
            catch (final RuntimeException e)
            {
                // Not fatal to the teleport that already happened, but a silently skipped
                // cooldown lets a player re-enter immediately, so say so.
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                    "Failed to apply use cooldown for " + player.getName() + ": " + e.getMessage());
            }
            // Mark player as having just arrived from this gate to prevent immediate re-entry
            try
            {
                if (stargate.getGateTarget() != null)
                {
                    com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.addPlayerRecentArrival(player, stargate.getGateTarget());
                }
            }
            catch (final RuntimeException e)
            {
                // Without this marker the player can walk straight back into the gate they
                // just arrived from, so a failure is worth a line in the log.
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                    "Failed to mark recent arrival for " + player.getName() + ": " + e.getMessage());
            }

            // Schedule a short delayed task to re-apply zero velocity.
            // Skip the position teleport for the vehicle path — the client is repositioned by addPassenger.
            try {
                final Location finalTarget = target;
                final boolean skipTeleport = vehiclePathUsed[0];
                Bukkit.getScheduler().runTaskLater(WormholeXTreme.getThisPlugin(), new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if ((player == null) || (finalTarget == null))
                        {
                            return;
                        }
                        // Settling the player after arrival. Each call is independently
                        // best-effort: none of them failing is worth aborting the others.
                        try { player.setVelocity(new Vector(0, 0, 0)); } catch (final RuntimeException ignore) {}
                        try { player.setFallDistance(0); } catch (final RuntimeException ignore) {}
                        if (!skipTeleport)
                        {
                            try { player.teleport(finalTarget); } catch (final RuntimeException ignore) {}
                        }
                    }
                }, 1L);
            }
            catch (final RuntimeException ignore) {}
            if (target != stargate.getGatePlayerTeleportLocation())
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, player.getName() + " used wormhole: " + stargate.getGateName() + " to go to: " + stargate.getGateTarget().getGateName());
            }
            if (ConfigManager.getTimeoutShutdown() == 0)
            {
                stargate.shutdownStargate(true);
            }
            return true;
        }
        else if (stargate != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Player entered gate but wasn't active or didn't have a target.");
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent)
     */
    @EventHandler
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent event)
    {
        if ( !event.isCancelled())
        {
            final Stargate stargate = StargateManager.getGateFromBlock(event.getBlockClicked());
            if ((stargate != null) || StargateManager.isBlockInGate(event.getBlockClicked()))
            {
                event.setCancelled(true);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerBucketFill(org.bukkit.event.player.PlayerBucketFillEvent)
     */
    @EventHandler
    public void onPlayerBucketFill(final PlayerBucketFillEvent event)
    {
        if ( !event.isCancelled())
        {
            final Stargate stargate = StargateManager.getGateFromBlock(event.getBlockClicked());
            if ((stargate != null) || StargateManager.isBlockInGate(event.getBlockClicked()))
            {
                event.setCancelled(true);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent)
     */
    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event)
    {
        if (event.getClickedBlock() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught Player: \"" + event.getPlayer().getName() + "\" Action Type: \"" + event.getAction().toString() + "\" Event Block Type: \"" + event.getClickedBlock().getType().toString() + "\" Event World: \"" + event.getClickedBlock().getWorld().toString() + "\" Event Block: " + event.getClickedBlock().toString() + "\"");
            if (handlePlayerInteractEvent(event))
            {
                event.setCancelled(true);
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Cancelled Player: \"" + event.getPlayer().getName() + "\" Action Type: \"" + event.getAction().toString() + "\" Event Block Type: \"" + event.getClickedBlock().getType().toString() + "\" Event World: \"" + event.getClickedBlock().getWorld().toString() + "\" Event Block: " + event.getClickedBlock().toString() + "\"");
            }
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught and ignored Player: \"" + event.getPlayer().getName() + "\" Action Type: \"" + event.getAction().toString() + "\"");
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerMove(org.bukkit.event.player.PlayerMoveEvent)
     */
    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent event)
    {
        if (handlePlayerMoveEvent(event))
        {
            event.setCancelled(true);
        }
    }
}

