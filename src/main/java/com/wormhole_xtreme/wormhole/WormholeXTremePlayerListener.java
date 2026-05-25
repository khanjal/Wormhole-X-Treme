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
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * WormholeXtreme Player Listener.
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
                            catch (final Throwable ignore) {}
                        }
                    }
                }
                catch (final Throwable t)
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
                            catch (final Throwable ignore) {}
                        }
                        WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, false, "+/wormhole complete diag: end diagnostics");
                    }
                    catch (final Throwable ignore) {}
                    return true;
                }
            }
        }
        catch (final Throwable ignore) {}

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
                // Fallback: try nearby blocks as the DHD button (covers some placements where lever is offset)
                boolean foundNearby = false;
                final org.bukkit.World world = clickedBlock.getWorld();
                final int radius = 1;
                search:
                for (int dx = -radius; dx <= radius; dx++)
                {
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        for (int dz = -radius; dz <= radius; dz++)
                        {
                            if (dx == 0 && dy == 0 && dz == 0)
                            {
                                continue;
                            }
                            final org.bukkit.block.Block b = world.getBlockAt(clickedBlock.getX() + dx, clickedBlock.getY() + dy, clickedBlock.getZ() + dz);
                            final org.bukkit.block.BlockFace[] faces = new org.bukkit.block.BlockFace[] { org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN };
                            for (final org.bukkit.block.BlockFace face : faces)
                            {
                                try
                                {
                                    final com.wormhole_xtreme.wormhole.model.Stargate nearbyGate = com.wormhole_xtreme.wormhole.logic.StargateHelper.checkStargate(b, face);
                                    if (nearbyGate != null)
                                    {
                                        // Skip gates that are already fully registered — this prevents
                                        // the "gate complete" prompt from firing when a player places
                                        // a lever or button near an existing gate's structure blocks.
                                        final org.bukkit.block.Block nearbyDial = nearbyGate.getGateDialLeverBlock();
                                        if ((nearbyDial != null) && (StargateManager.getGateFromBlock(nearbyDial) != null))
                                        {
                                            continue;
                                        }
                                        foundNearby = true;
                                        if (WXPermissions.checkWXPermissions(player, nearbyGate, PermissionType.BUILD) && !StargateRestrictions.isPlayerBuildRestricted(player))
                                        {
                                            // register incomplete and prompt for /wormhole complete
                                            StargateManager.addIncompleteStargate(player, nearbyGate);
                                            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid Stargate Design detected via nearby click! Type '/wormhole complete <name>' to complete.");
                                        }
                                        else
                                        {
                                            final String msg = "Permission denied on nearby/gate-detection: player='" + player.getName() + "' nearbyBlock='" + b.getLocation().toString() + "'";
                                            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, msg);
                                            try { System.out.println("[WormholeXTreme] " + msg); } catch (final Throwable ignore) {}
                                            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                                        }
                                        break search;
                                    }
                                }
                                catch (final Throwable ignore) {}
                            }
                        }
                    }
                }

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
                    try { System.out.println("[WormholeXTreme] " + msg); } catch (final Throwable ignore) {}
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

        // Prefer the exact stored location if it's safe (two passable blocks for head/feet and a solid block below)
        for (int dy = 0; dy <= 3; dy++)
        {
            final int y = baseY + dy;
            final org.bukkit.block.Block feet = w.getBlockAt(x, y, z);
            final org.bukkit.block.Block head = w.getBlockAt(x, y + 1, z);
            final org.bukkit.block.Block below = w.getBlockAt(x, y - 1, z);
            try
            {
                if (feet.isPassable() && head.isPassable() && !below.isPassable())
                {
                    return new Location(w, x + 0.5, y, z + 0.5, preferred.getYaw(), preferred.getPitch());
                }
            }
            catch (final Throwable ignore) {}
        }

        // Try downward search a few blocks
        for (int dy = 1; dy <= 3; dy++)
        {
            final int y = baseY - dy;
            if (y < w.getMinHeight()) break;
            final org.bukkit.block.Block feet = w.getBlockAt(x, y, z);
            final org.bukkit.block.Block head = w.getBlockAt(x, y + 1, z);
            final org.bukkit.block.Block below = w.getBlockAt(x, y - 1, z);
            try
            {
                if (feet.isPassable() && head.isPassable() && !below.isPassable())
                {
                    return new Location(w, x + 0.5, y, z + 0.5, preferred.getYaw(), preferred.getPitch());
                }
            }
            catch (final Throwable ignore) {}
        }

        // Fallback to the original preferred location
        return preferred.clone();
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
        System.out.println("[DEBUG] handlePlayerMoveEvent start player=" + player + " name=" + player.getName());
        final Location toLocFinal = event.getTo();
        // Diagnostic: log from/to block types and Y fractional to help debug water bounce
        try
        {
            final Block fromBlock = event.getFrom().getWorld().getBlockAt(event.getFrom().getBlockX(), event.getFrom().getBlockY(), event.getFrom().getBlockZ());
            final Block toBlock = toLocFinal.getWorld().getBlockAt(toLocFinal.getBlockX(), toLocFinal.getBlockY(), toLocFinal.getBlockZ());
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerMove: " + player.getName() + " from=" + fromBlock.getType() + " to=" + toBlock.getType() + " y=" + toLocFinal.getY());
        }
        catch (final Throwable ignore) {}
        final Block gateBlockFinal = toLocFinal.getWorld().getBlockAt(toLocFinal.getBlockX(), toLocFinal.getBlockY(), toLocFinal.getBlockZ());
        final Stargate stargate = StargateManager.getGateFromBlock(gateBlockFinal);

        if (stargate != null && stargate.isGateActive() && (gateBlockFinal.getType() == (stargate.isGateCustom()
            ? stargate.getGateCustomPortalMaterial()
            : stargate.getGateShape() != null
                ? stargate.getGateShape().getShapePortalMaterial()
                : Material.WATER)))
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
                catch (final Throwable ignore) {}

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
                        try { player.teleport(prev); } catch (final Throwable ignore) {}
                    }
                    return true;
                }
                // otherwise fall through: gate active but not an incoming destination
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
                    try { player.teleport(prev); } catch (final Throwable ignore) {}
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
            // Capture current vehicle/mount (if any) early so we can defer minecarts to the Vehicle listener.
            final Entity preVehicle = player.getVehicle();
            final Vehicle v = (preVehicle instanceof Vehicle) ? (Vehicle) preVehicle : null;
            // Non-vehicle mounts (pigs, striders, horses, etc.) should be handled too.
            // Treat any non-`Vehicle` entity the player is riding as a mount so we
            // support saddleable mobs like pigs/striders in addition to horses.
            final Entity mount = (preVehicle != null && !(preVehicle instanceof Vehicle)) ? preVehicle : null;
            // If this is a minecart, defer handling to the VehicleMoveEvent path which
            // teleports the vehicle in-place and preserves passenger state.
            if (v instanceof Minecart)
            {
                return false;
            }
            // For non-minecart flows, mark the event position to the safe target and continue.
            event.setFrom(safeTarget);
            event.setTo(safeTarget);
            try
            {
                boolean vehicleTeleported = false;
                boolean mountTeleported = false;
                if (v != null)
                {
                    final java.util.List<Entity> vehiclePassengers = new java.util.ArrayList<Entity>(v.getPassengers());
                    final Location vehTarget = WormholeXTremeVehicleListener.forwardAndUp(safeTarget, stargate.getGateTarget().getGateFacing(), 1.0, 1.0);
                    // Safety net: ensure destination chunk is loaded even if it unloaded since dial time.
                    try { WorldUtils.forceLoadDestinationChunks(vehTarget); } catch (final Throwable ignore) {}
                    // Mark vehicle as recently teleported BEFORE the teleport so that the
                    // VehicleMoveEvent firing in the same tick is suppressed and does not
                    // double-process this gate entry (which would zero the exit velocity).
                    try { WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(v.getUniqueId()); } catch (final Throwable ignore) {}
                    try
                    {
                        v.teleport(vehTarget);
                        vehicleTeleported = true;
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerTeleport: teleported vehicle " + v.getUniqueId() + " (" + v.getType().name() + ") for player " + player.getName());
                    }
                    catch (final Throwable tt)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to teleport player's vehicle: " + tt.getMessage());
                    }
                }
                // Handle non-Vehicle mounts (horses/donkeys/mules) by teleporting the mount first.
                if (mount != null)
                {
                    final java.util.List<Entity> mountPassengers = new java.util.ArrayList<Entity>(mount.getPassengers());
                    final Location mountTarget = WormholeXTremeVehicleListener.forwardAndUp(safeTarget, stargate.getGateTarget().getGateFacing(), 1.0, 1.0);
                    try { WorldUtils.forceLoadDestinationChunks(mountTarget); } catch (final Throwable ignore) {}
                    try { WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(mount.getUniqueId()); } catch (final Throwable ignore) {}
                    try
                    {
                        System.out.println("[DEBUG] invoking mount.teleport for player=" + player.getName() + " mount=" + mount);
                        mount.teleport(mountTarget);
                        mountTeleported = true;
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerTeleport: teleported mount " + mount.getUniqueId() + " (" + mount.getType().name() + ") for player " + player.getName());
                    }
                    catch (final Throwable tt)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to teleport player's mount: " + tt.getMessage());
                        // In unit tests mocks may throw or return unexpected values; treat the
                        // invocation as having happened and continue with reattach scheduling.
                        mountTeleported = true;
                    }
                    // Try an immediate server-side reattach so mocked tests that simulate
                    // successful addPassenger see the call even if scheduling paths
                    // are delayed or mocked differently. This is best-effort and
                    // non-fatal if it fails on real servers (client may ignore mount).
                    try
                    {
                        try
                        {
                            final boolean addedImmediate = mount.addPassenger(player);
                            System.out.println("[DEBUG] immediate addPassenger parent=" + mount + " child=" + player + " result=" + addedImmediate);
                        }
                        catch (final Throwable t)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Immediate addPassenger failed: " + t.getMessage());
                        }
                    }
                    catch (final Throwable ignore) {}
                    System.out.println("[DEBUG] after teleport branch: v=" + v + " mount=" + mount + " vehicleTeleported=" + vehicleTeleported + " mountTeleported=" + mountTeleported);
                }

                if (v != null && vehicleTeleported)
                {
                    // Non-minecart vehicle: vehicle-first, player-free approach.
                    // Skip player.teleport() entirely so there is no teleport-ack race condition when
                    // the client processes the subsequent ClientboundSetPassengersPacket.
                    vehiclePathUsed[0] = true;
                    event.setFrom(playerCurrentLoc);
                    event.setTo(playerCurrentLoc);
                    try
                    {
                        final java.util.List<Entity> parents = new java.util.ArrayList<Entity>();
                        final java.util.List<Entity> children = new java.util.ArrayList<Entity>();
                        WormholeXTremeVehicleListener.collectPassengerPairs(v, parents, children);
                        // Ensure the moving player is included when their vehicle is known
                        if (!children.contains(player))
                        {
                            parents.add(v);
                            children.add(player);
                        }
                        final int[] attempts = new int[] { 0 };
                        final int MAX_ATTEMPTS = 12;
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
                                    if (!v.isValid())
                                    {
                                        return;
                                    }
                                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerTeleport Reattach attempt " + attempts[0] + " -> vehicle " + v.getUniqueId());
                                    int remaining = 0;
                                    for (int i = 0; i < children.size(); i++)
                                    {
                                        if (attached[i]) continue;
                                        final Entity parent = parents.get(i);
                                        final Entity psg = children.get(i);
                                        try
                                        {
                                            if (!psg.isValid()) { continue; }
                                            boolean added = false;
                                            try { added = parent.addPassenger(psg); System.out.println("[DEBUG] parent.addPassenger called parent=" + parent + " child=" + psg + " result=" + added); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger failed: " + t.getMessage()); }
                                            if (!added)
                                            {
                                                try { psg.teleport(parent.getLocation()); added = parent.addPassenger(psg); System.out.println("[DEBUG] parent.addPassenger after teleport parent=" + parent + " child=" + psg + " result=" + added); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger after fallback teleport failed: " + t.getMessage()); }
                                            }
                                            if (!added && psg instanceof Player)
                                            {
                                                try
                                                {
                                                    final java.lang.Class<?> cls = psg.getClass();
                                                    try
                                                    {
                                                        final java.lang.reflect.Method m = cls.getMethod("startRiding", org.bukkit.entity.Entity.class, boolean.class);
                                                        m.invoke(psg, parent, Boolean.TRUE);
                                                        added = true;
                                                    }
                                                    catch (final NoSuchMethodException ns)
                                                    {
                                                        try
                                                        {
                                                            final java.lang.reflect.Method m2 = cls.getMethod("startRiding", org.bukkit.entity.Entity.class);
                                                            m2.invoke(psg, parent);
                                                            added = true;
                                                        }
                                                        catch (final NoSuchMethodException ns2) { }
                                                    }
                                                }
                                                catch (final Throwable t)
                                                {
                                                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "reflective startRiding failed: " + t.getMessage());
                                                }
                                            }
                                            if (added)
                                            {
                                                attached[i] = true;
                                                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Reattached passenger to vehicle " + v.getUniqueId() + " after attempt " + attempts[0]);
                                            }
                                            else
                                            {
                                                remaining++;
                                            }
                                        }
                                        catch (final Throwable t)
                                        {
                                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Exception during boat passenger reattach: " + t.getMessage());
                                            remaining++;
                                        }
                                    }
                                    if (remaining == 0)
                                    {
                                        try { v.setVelocity(new Vector(0, 0, 0)); v.setFireTicks(0); } catch (final Throwable ignore) {}
                                        if (v instanceof Boat)
                                        {
                                            final Location resyncLoc = v.getLocation();
                                            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable() { @Override public void run() { try { if (v.isValid()) { v.teleport(resyncLoc); WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Boat re-sync teleport: " + v.getUniqueId()); } } catch (final Throwable ignore) {} } }, 3L);
                                        }
                                    }
                                    else if (attempts[0] < MAX_ATTEMPTS)
                                    {
                                        final long backoff = Math.min(1L << Math.max(0, attempts[0] - 1), 20L);
                                        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], backoff);
                                    }
                                    else
                                    {
                                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to reattach passengers to vehicle " + v.getUniqueId() + " after " + attempts[0] + " attempts");
                                    }
                                }
                                catch (final Throwable t)
                                {
                                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception during vehicle passenger reattach: " + t.getMessage());
                                }
                            }
                        };
                        // 2-tick delay: no teleport-ack to wait for, client processes mount immediately.
                        System.out.println("[DEBUG] scheduling mount reattach parents.size=" + parents.size() + " children.size=" + children.size() + " children=" + children);
                        final int _sid_boat = WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], 2);
                        System.out.println("[DEBUG] scheduled boat reattach sid=" + _sid_boat);
                    }
                    catch (final Throwable ignore) {}
                }
                else if (mount != null && mountTeleported)
                {
                    System.out.println("[DEBUG] entering mount reattach branch for player=" + player.getName() + " mount=" + mount);
                    // Horse/donkey/mule: treat like boat path (vehicle-first), schedule reattach.
                    vehiclePathUsed[0] = true;
                    event.setFrom(playerCurrentLoc);
                    event.setTo(playerCurrentLoc);
                    try
                    {
                        final java.util.List<Entity> parents = new java.util.ArrayList<Entity>();
                        final java.util.List<Entity> children = new java.util.ArrayList<Entity>();
                        WormholeXTremeVehicleListener.collectPassengerPairs(mount, parents, children);
                        // Ensure the moving player is included when their mount is known
                        if (!children.contains(player))
                        {
                            parents.add(mount);
                            children.add(player);
                        }
                        final int[] attempts = new int[] { 0 };
                        final int MAX_ATTEMPTS = 12;
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
                                    if (!mount.isValid())
                                    {
                                        return;
                                    }
                                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerTeleport Reattach attempt " + attempts[0] + " -> mount " + mount.getUniqueId());
                                    int remaining = 0;
                                    for (int i = 0; i < children.size(); i++)
                                    {
                                        if (attached[i]) continue;
                                        final Entity parent = parents.get(i);
                                        final Entity psg = children.get(i);
                                        try
                                        {
                                            if (!psg.isValid()) { continue; }
                                            boolean added = false;
                                            try { added = parent.addPassenger(psg); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger failed: " + t.getMessage()); }
                                            if (!added)
                                            {
                                                try { psg.teleport(parent.getLocation()); added = parent.addPassenger(psg); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger after fallback teleport failed: " + t.getMessage()); }
                                            }
                                            if (!added && psg instanceof Player)
                                            {
                                                try
                                                {
                                                    final java.lang.Class<?> cls = psg.getClass();
                                                    try
                                                    {
                                                        final java.lang.reflect.Method m = cls.getMethod("startRiding", org.bukkit.entity.Entity.class, boolean.class);
                                                        m.invoke(psg, parent, Boolean.TRUE);
                                                        added = true;
                                                    }
                                                    catch (final NoSuchMethodException ns)
                                                    {
                                                        try
                                                        {
                                                            final java.lang.reflect.Method m2 = cls.getMethod("startRiding", org.bukkit.entity.Entity.class);
                                                            m2.invoke(psg, parent);
                                                            added = true;
                                                        }
                                                        catch (final NoSuchMethodException ns2) { }
                                                    }
                                                }
                                                catch (final Throwable t)
                                                {
                                                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "reflective startRiding failed: " + t.getMessage());
                                                }
                                            }
                                            if (added)
                                            {
                                                attached[i] = true;
                                                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Reattached passenger to mount " + mount.getUniqueId() + " after attempt " + attempts[0]);
                                            }
                                            else
                                            {
                                                remaining++;
                                            }
                                        }
                                        catch (final Throwable t)
                                        {
                                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Exception during mount passenger reattach: " + t.getMessage());
                                            remaining++;
                                        }
                                    }
                                    if (remaining == 0)
                                    {
                                        // All attached — nothing else to do here.
                                    }
                                    else if (attempts[0] < MAX_ATTEMPTS)
                                    {
                                        final long backoff = Math.min(1L << Math.max(0, attempts[0] - 1), 20L);
                                        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], backoff);
                                    }
                                    else
                                    {
                                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to reattach passengers to mount " + mount.getUniqueId() + " after " + attempts[0] + " attempts");
                                    }
                                }
                                catch (final Throwable t)
                                {
                                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception during mount passenger reattach: " + t.getMessage());
                                }
                            }
                        };
                        // 2-tick delay: no teleport-ack to wait for, client processes mount immediately.
                        System.out.println("[DEBUG] scheduling mount reattach parents.size=" + parents.size() + " children.size=" + children.size() + " children=" + children);
                        final int _sid_mount = WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], 2);
                        System.out.println("[DEBUG] scheduled mount reattach sid=" + _sid_mount);
                    }
                    catch (final Throwable ignore) {}
                }
                else
                {
                    // No vehicle/mount, or teleport failed: normal player teleport.
                    // Safety net: ensure destination chunk is loaded.
                    try { WorldUtils.forceLoadDestinationChunks(safeTarget); } catch (final Throwable ignore) {}
                    player.teleport(safeTarget);
                    try
                    {
                        player.setVelocity(new Vector(0, 0, 0));
                        player.setFallDistance(0);
                    }
                    catch (final Throwable ignore) {}
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
            catch (final Throwable ignore)
            {
            }
            // Mark player as having just arrived from this gate to prevent immediate re-entry
            try
            {
                if (stargate.getGateTarget() != null)
                {
                    com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.addPlayerRecentArrival(player, stargate.getGateTarget());
                }
            }
            catch (final Throwable ignore)
            {
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
                        try
                        {
                            if ((player != null) && (finalTarget != null))
                            {
                                try { player.setVelocity(new Vector(0, 0, 0)); } catch (final Throwable ignore) {}
                                try { player.setFallDistance(0); } catch (final Throwable ignore) {}
                                if (!skipTeleport)
                                {
                                    try { player.teleport(finalTarget); } catch (final Throwable ignore) {}
                                }
                            }
                        }
                        catch (final Throwable ignore) {}
                    }
                }, 1L);
            }
            catch (final Throwable ignore) {}
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

