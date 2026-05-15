/*
 *   Wormhole X-Treme Plugin for Bukkit
 *   Copyright (C) 2011  Ben Echols
 *                       Dean Bailey
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

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
                    if (com.wormhole_xtreme.wormhole.model.StargateManager.completeStargate(player, name, idc, network))
                    {
                        player.sendMessage(ConfigManager.MessageStrings.constructSuccess.toString());
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
                        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Stargate Design Valid with Sign Nav.");
                        if (newGate.getGateName().equals(""))
                        {
                            player.sendMessage(ConfigManager.MessageStrings.constructNameInvalid.toString() + "\"\"");
                        }
                        else
                        {
                            final boolean success = StargateManager.completeStargate(player, newGate);
                            if (success)
                            {
                                player.sendMessage(ConfigManager.MessageStrings.constructSuccess.toString());
                                newGate.getGateDialSign().setLine(0, "-" + newGate.getGateName() + "-");
                                newGate.getGateDialSign().update();
                            }
                            else
                            {
                                player.sendMessage("Stargate constrution failed!?");
                            }
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

        if ((clickedBlock != null) && ((clickedBlock.getType() == Material.STONE_BUTTON) || (clickedBlock.getType() == Material.LEVER)))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerInteract: " + player.getName() + " clicked potential activator at " + clickedBlock.getLocation().toString() + " type=" + clickedBlock.getType().toString());
            if (buttonLeverHit(player, clickedBlock, null))
            {
                return true;
            }
        }
        else if ((clickedBlock != null) && (com.wormhole_xtreme.wormhole.utils.LegacyCompat.isWallSign(clickedBlock.getType())))
        {
            final Stargate stargate = StargateManager.getGateFromBlock(clickedBlock);
            if (stargate != null)
            {
                if (WXPermissions.checkWXPermissions(player, stargate, PermissionType.SIGN)
                    || player.isOp() || stargate.isOwner(player))
                {
                    if (stargate.tryClickTeleportSign(clickedBlock, player))
                    {
                        return true;
                    }
                }
                else
                {
                    player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                    return true;
                }
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

        if ((stargate != null) && stargate.isGateActive() && (stargate.getGateTarget() != null) && (gateBlockFinal.getType() == (stargate.isGateCustom()
            ? stargate.getGateCustomPortalMaterial()
            : stargate.getGateShape() != null
                ? stargate.getGateShape().getShapePortalMaterial()
                : Material.WATER)))
        {
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
            event.setFrom(target);
            event.setTo(target);
            try
            {
                player.teleport(target);
                // Zero velocity and fall distance to avoid immediate bounce or knockback
                try
                {
                    player.setVelocity(new Vector(0, 0, 0));
                    player.setFallDistance(0);
                }
                catch (final Throwable ignore) {}
            }
            catch (final Exception e)
            {
                if (WormholeXTreme.getThisPlugin() != null)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception while teleporting " + player.getName() + " to " + (target == null ? "null" : target.toString()) + ": " + e.getMessage());
                }
            }
            try {
                com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.addPlayerUseCooldown(player);
            } catch (final Throwable ignore) {}

            // Schedule a short delayed task to re-apply zero velocity and nudge the player
            // This helps clients that may apply movement due to water flow or packet jitter
            try {
                final Location finalTarget = target;
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
                                try { player.teleport(finalTarget); } catch (final Throwable ignore) {}
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

