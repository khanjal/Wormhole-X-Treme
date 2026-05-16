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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.event.StargateMinecartTeleportEvent;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * WormholeXtreme Vehicle Listener.
 * 
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
class WormholeXTremeVehicleListener implements Listener
{

    /** The nospeed. */
    private final static Vector nospeed = new Vector();

    /**
     * Simple minecart safety helper – return one block above the preferred
     * arrival location so players/carts don't spawn inside blocks.
     */
    static Location findSafeMinecartLocation(final Location preferred)
    {
        if (preferred == null || preferred.getWorld() == null)
        {
            return preferred;
        }
        final Location out = preferred.clone();
        out.add(0, 1.0, 0);
        return out;
    }

    /**
     * Return a location offset slightly forward (in the gate facing direction)
     * and up so minecarts arrive clear of the portal. Uses facing.getModX()/getModZ()
     * to compute a small horizontal offset.
     */
    static Location forwardAndUp(final Location base, final BlockFace facing, final double forward, final double up)
    {
        if (base == null || base.getWorld() == null)
        {
            return base;
        }
        if (facing == null)
        {
            final Location out = base.clone();
            out.add(0, up, 0);
            return out;
        }
        final Location out = base.clone();
        out.add(facing.getModX() * forward, up, facing.getModZ() * forward);
        return out;
    }

    /**
     * Handle stargate minecart teleport event.
     * 
     * @param event
     *            the event
     * @return true, if successful
     */
    private static boolean handleStargateMinecartTeleportEvent(final VehicleMoveEvent event)
    {
        final Location l = event.getTo();
        final Block ch = l.getWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        final Stargate st = StargateManager.getGateFromBlock(ch);
        if ((st != null) && st.isGateActive() && (st.getGateTarget() != null) && (ch.getType() == (st.isGateCustom()
            ? st.getGateCustomPortalMaterial()
            : st.getGateShape() != null
                ? st.getGateShape().getShapePortalMaterial()
                : org.bukkit.Material.WATER)))
        {
            String gatenetwork;
            if (st.getGateNetwork() != null)
            {
                gatenetwork = st.getGateNetwork().getNetworkName();
            }
            else
            {
                gatenetwork = "Public";
            }
            Location target = st.getGateTarget().getGateMinecartTeleportLocation() != null
                ? st.getGateTarget().getGateMinecartTeleportLocation()
                : st.getGateTarget().getGatePlayerTeleportLocation();
            final Minecart veh = (Minecart) event.getVehicle();
            final Vector v = veh.getVelocity();
            veh.setVelocity(nospeed);
            final Entity e = veh.getPassenger();
            if ((e != null) && (e instanceof Player))
            {
                final Player p = (Player) e;
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Minecart Player in gate:" + st.getGateName() + " gate Active: " + st.isGateActive() + " Target Gate: " + st.getGateTarget().getGateName() + " Network: " + gatenetwork);
                if (ConfigManager.getWormholeUseIsTeleport() && ((st.isGateSignPowered() && !WXPermissions.checkWXPermissions(p, st, PermissionType.SIGN)) || ( !st.isGateSignPowered() && !WXPermissions.checkWXPermissions(p, st, PermissionType.DIALER))))
                {
                    p.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
                    return false;
                }
                if (st.getGateTarget().isGateIrisActive())
                {
                    p.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Remote Iris is locked!");
                    final Location irisTarget = st.getGateMinecartTeleportLocation() != null
                        ? st.getGateMinecartTeleportLocation()
                        : st.getGatePlayerTeleportLocation();
                    // If player is in a minecart, just move them one block up from the TP location
                                final Location safeIrisTarget = (irisTarget != null)
                                    ? forwardAndUp(irisTarget, st.getGateTarget().getGateFacing(), 1.0, 1.0)
                                    : irisTarget;
                    veh.teleport(safeIrisTarget);
                    if (ConfigManager.getTimeoutShutdown() == 0)
                    {
                        st.shutdownStargate(true);
                    }
                    return false;
                }
                if (ConfigManager.isUseCooldownEnabled())
                {
                    if (StargateRestrictions.isPlayerUseCooldown(p))
                    {
                        p.sendMessage(ConfigManager.MessageStrings.playerUseCooldownRestricted.toString());
                        p.sendMessage(ConfigManager.MessageStrings.playerUseCooldownWaitTime.toString() + StargateRestrictions.checkPlayerUseCooldownRemaining(p));
                        return false;
                    }
                    else
                    {
                        StargateRestrictions.addPlayerUseCooldown(p);
                    }
                }
            }
            else
            {
                if (st.getGateTarget().isGateIrisActive())
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Minecart in gate:" + st.getGateName() + " gate Active: " + st.isGateActive() + " Target Gate: " + st.getGateTarget().getGateName() + " Network: " + gatenetwork);
                    final Location irisTarget = st.getGateMinecartTeleportLocation() != null
                        ? st.getGateMinecartTeleportLocation()
                        : st.getGatePlayerTeleportLocation();
                    // For non-player carts, use a simple one-block-up offset from configured TP
                    final Location safeIrisTarget = (irisTarget != null) ? forwardAndUp(irisTarget, st.getGateTarget().getGateFacing(), 1.0, 1.0) : irisTarget;
                    veh.teleport(safeIrisTarget);
                    if (ConfigManager.getTimeoutShutdown() == 0)
                    {
                        st.shutdownStargate(true);
                    }
                    return false;
                }

            }

            final double speed = v.length();
            final Vector new_speed = new Vector();
            if (st.getGateTarget().getGateFacing() == BlockFace.NORTH)
            {
                new_speed.setX( -1);
            }
            else if (st.getGateTarget().getGateFacing() == BlockFace.SOUTH)
            {
                new_speed.setX(1);
            }
            else if (st.getGateTarget().getGateFacing() == BlockFace.EAST)
            {
                new_speed.setZ( -1);
            }
            else if (st.getGateTarget().getGateFacing() == BlockFace.WEST)
            {
                new_speed.setZ(1);
            }
            // As we all know stargates accelerate matter.
            new_speed.multiply(speed * 5);
                if (st.getGateTarget().isGateIrisActive())
                {
                    target = st.getGateMinecartTeleportLocation() != null
                        ? st.getGateMinecartTeleportLocation()
                        : st.getGatePlayerTeleportLocation();
                    final Location safeTarget = (target != null) ? forwardAndUp(target, st.getGateTarget().getGateFacing(), 1.0, 1.0) : target;
                    veh.teleport(safeTarget);
                    veh.setVelocity(new_speed);
                }
            else
            {
                    if (e != null)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Removing player from cart and doing some teleport hackery");
                        veh.eject();
                        veh.remove();
                        // For player-occupied carts, simply move the TP location one block up to avoid sinking
                        final Location safeTarget = (target != null) ? forwardAndUp(target, st.getGateTarget().getGateFacing(), 1.0, 1.0) : target;
                        final Minecart newveh = safeTarget.getWorld().spawn(safeTarget, Minecart.class);
                        final Event teleportevent = new StargateMinecartTeleportEvent(veh, newveh);
                        WormholeXTreme.getThisPlugin().getServer().getPluginManager().callEvent(teleportevent);
                        e.teleport(safeTarget);
                        final Vector newnew_speed = new_speed;
                        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                newveh.setPassenger(e);
                                newveh.setVelocity(newnew_speed);
                                newveh.setFireTicks(0);
                            }
                        }, 5);
                    }
                else
                {
                    final Location safeTarget = (target != null) ? forwardAndUp(target, st.getGateTarget().getGateFacing(), 1.0, 1.0) : target;
                    veh.teleport(safeTarget);
                    veh.setVelocity(new_speed);
                }
            }

            if (ConfigManager.getTimeoutShutdown() == 0)
            {
                st.shutdownStargate(true);
            }
            return true;
        }

        return false;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.vehicle.VehicleListener#onVehicleMove(org.bukkit.event.vehicle.VehicleMoveEvent)
     */
    @EventHandler
    public void onVehicleMove(final VehicleMoveEvent event)
    {
        if (event.getVehicle() instanceof Minecart)
        {
            handleStargateMinecartTeleportEvent(event);
        }
    }
}
