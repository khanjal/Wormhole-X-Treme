package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Location;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;
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

    /** Vehicles recently teleported — short cooldown to avoid immediate re-trigger. */
    private static final Set<UUID> recentlyTeleported = ConcurrentHashMap.newKeySet();

    /** Players teleported via a vehicle — suppresses a duplicate solo teleport from PlayerListener. */
    private static final Set<UUID> recentlyTeleportedPlayersByVehicle = ConcurrentHashMap.newKeySet();

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
     * Compute an exit velocity that points away from the gate based on its facing.
     * If facing is null or zero-length, fall back to the incoming horizontal direction.
     * The returned vector is scaled by incoming.length() * multiplier.
     */
    static Vector computeExitVelocity(final BlockFace facing, final Vector incoming, final double multiplier)
    {
        final double speed = (incoming == null) ? 0.0 : incoming.length();
        Vector dir = null;
        if (facing != null)
        {
            dir = new Vector(facing.getModX(), 0, facing.getModZ());
            if (dir.length() == 0)
            {
                dir = null;
            }
        }
        if (dir == null)
        {
            if (incoming != null && incoming.length() > 0)
            {
                dir = incoming.clone();
                dir.setY(0);
                if (dir.length() > 0)
                {
                    dir.normalize();
                }
                else
                {
                    dir = new Vector(0, 0, 1);
                }
            }
            else
            {
                dir = new Vector(0, 0, 1);
            }
        }
        else
        {
            dir.normalize();
        }

        dir.multiply(speed * multiplier);
        return dir;
    }

    /**
     * Collect a pre-order list of parent->child passenger pairs for an entity tree.
     * The returned lists are parallel: parents.get(i) is the parent for children.get(i).
     */
    public static void collectPassengerPairs(final Entity root, final List<Entity> parents, final List<Entity> children)
    {
        if (root == null || parents == null || children == null)
        {
            return;
        }
        try
        {
            for (final Entity child : root.getPassengers())
            {
                parents.add(root);
                children.add(child);
                collectPassengerPairs(child, parents, children);
            }
        }
        catch (final Throwable ignore)
        {
            // Concurrent modification or other runtime issue — best-effort only.
        }
    }


    /**
     * Mark a vehicle as recently teleported by the player listener so that an
     * overlapping VehicleMoveEvent in the same tick does not double-process the
     * same gate entry (which would zero out the exit velocity).
     *
     * @param vehicleId the UUID of the vehicle
     */
    static void markVehicleRecentlyTeleported(final UUID vehicleId)
    {
        recentlyTeleported.add(vehicleId);
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
        {
            @Override
            public void run()
            {
                recentlyTeleported.remove(vehicleId);
            }
        }, 20L);
    }


    static void markPlayerRecentlyTeleportedByVehicle(final UUID playerId)
    {
        if (playerId == null) { return; }
        recentlyTeleportedPlayersByVehicle.add(playerId);
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
        {
            @Override
            public void run()
            {
                recentlyTeleportedPlayersByVehicle.remove(playerId);
            }
        }, 10L);
    }


    /**
     * Checks whether this listener owns the entity's movement through gates.
     *
     * <p>Only minecarts and boats raise {@link VehicleMoveEvent}, so only they are handled
     * here. This is deliberately narrower than {@code instanceof Vehicle}: in Bukkit
     * {@code Pig} and {@code AbstractHorse} — horses, camels, donkeys, mules, llamas — are
     * all Vehicles, but they never raise the event. Testing for Vehicle therefore excludes
     * ridable animals from this listener without including them anywhere else, which left
     * a riderless horse unable to walk through a gate at all.
     *
     * @param entity
     *            the entity to test, may be null
     * @return true if VehicleMoveEvent will carry this entity through a gate
     */
    static boolean handlesMovementOf(final Entity entity)
    {
        return entity instanceof Minecart || entity instanceof Boat;
    }

    /**
     * Checks whether an entity was teleported through a gate in the last second.
     *
     * <p>The periodic entity scan consults this so an entity that lands in another
     * active gate is not immediately sent back out of it.
     *
     * @param entityId the entity UUID
     * @return true if it was teleported recently
     */
    static boolean isVehicleRecentlyTeleported(final UUID entityId)
    {
        return entityId != null && recentlyTeleported.contains(entityId);
    }


    static boolean isPlayerRecentlyTeleportedByVehicle(final UUID playerId)
    {
        if (playerId == null) { return false; }
        return recentlyTeleportedPlayersByVehicle.contains(playerId);
    }


    /**
     * Teleport an occupied minecart through a gate.
     * <p>
     * Teleports the vehicle (which ejects the passenger server-side), then schedules
     * reattachment with exponential backoff. Sets exit velocity after successful reattach.
     * Does NOT issue a re-sync teleport — that would zero the cart's velocity.
     *
     * @param veh        the minecart to teleport
     * @param passenger  the entity riding the minecart
     * @param safeTarget the destination location
     * @param exitSpeed  the velocity to apply after reattachment
     */
    private static void teleportOccupiedMinecart(
        final Vehicle veh,
        final List<Entity> passengers,
        final Location safeTarget,
        final Vector exitSpeed)
    {
        final List<Entity> parents = new ArrayList<Entity>();
        final List<Entity> children = new ArrayList<Entity>();
        collectPassengerPairs(veh, parents, children);
        try
        {
            veh.teleport(safeTarget);
            final int[] attempts = new int[] { 0 };
            final int MAX_ATTEMPTS = 8;
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
                        if (!veh.isValid())
                        {
                            return;
                        }
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Minecart reattach attempt " + attempts[0] + " -> vehicle " + veh.getUniqueId() + " (passengers=" + children.size() + ")");
                        int remaining = 0;
                        for (int i = 0; i < children.size(); i++)
                        {
                            if (attached[i]) continue;
                            final Entity parent = parents.get(i);
                            final Entity child = children.get(i);
                            try
                            {
                                if (!child.isValid()) { continue; }
                                boolean added = false;
                                try { added = parent.addPassenger(child); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger failed: " + t.getMessage()); }
                                if (!added)
                                {
                                    try { if (parent.getPassengers().contains(child)) { attached[i] = true; continue; } } catch (final Throwable ignore) {}
                                    try { child.teleport(parent.getLocation()); added = parent.addPassenger(child); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger after teleport failed: " + t.getMessage()); }
                                }
                                if (added)
                                {
                                    attached[i] = true;
                                }
                                else
                                {
                                    remaining++;
                                }
                            }
                            catch (final Throwable t)
                            {
                                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Exception reattaching passenger: " + t.getMessage());
                                remaining++;
                            }
                        }
                        if (remaining == 0)
                        {
                            try { veh.setVelocity(exitSpeed); veh.setFireTicks(0); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Failed to set minecart state: " + t.getMessage()); }
                        }
                        else if (attempts[0] < MAX_ATTEMPTS)
                        {
                            if (attempts[0] == 2 || attempts[0] == 5)
                            {
                                try { veh.teleport(safeTarget); WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Re-teleported minecart " + veh.getUniqueId() + " to force client update (attempt " + attempts[0] + ")"); } catch (final Throwable tt) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Re-teleport failed: " + tt.getMessage()); }
                            }
                            final long backoff = Math.min(1L << Math.max(0, attempts[0] - 1), 20L);
                            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], backoff);
                        }
                        else
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to attach all passengers to minecart " + veh.getUniqueId() + " after " + attempts[0] + " attempts");
                            try { veh.setVelocity(exitSpeed); veh.setFireTicks(0); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Failed to set minecart state: " + t.getMessage()); }
                        }
                    }
                    catch (final Throwable t)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception during minecart passenger reattach: " + t.getMessage());
                    }
                }
            };
            // Delay 5 ticks so the client finishes its teleport acknowledgment before
            // we send the SetPassengers packet.
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], 5L);
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to teleport occupied minecart, falling back to respawn: " + t.getMessage());
            try
            {
                final Vehicle newveh = safeTarget.getWorld().spawn(safeTarget, Minecart.class);
                final Event teleportevent = new StargateMinecartTeleportEvent((Minecart) veh, (Minecart) newveh);
                WormholeXTreme.getThisPlugin().getServer().getPluginManager().callEvent(teleportevent);
                final UUID nid = newveh.getUniqueId();
                markVehicleRecentlyTeleported(nid);
                // Attach pairs in order so parents are attached before children
                for (int i = 0; i < children.size(); i++)
                {
                    final Entity parent = parents.get(i);
                    final Entity child = children.get(i);
                    try
                    {
                        child.teleport(safeTarget);
                        if (parent.equals(veh))
                        {
                            try { newveh.addPassenger(child); } catch (final Throwable tt) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Fallback reattach failed: " + tt.getMessage()); }
                        }
                        else
                        {
                            try { parent.addPassenger(child); } catch (final Throwable tt) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Fallback reattach failed: " + tt.getMessage()); }
                        }
                    }
                    catch (final Throwable tt)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Fallback passenger teleport failed: " + tt.getMessage());
                    }
                }
                newveh.setVelocity(exitSpeed);
            }
            catch (final Throwable tt)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Fallback respawn also failed: " + tt.getMessage());
            }
        }
    }


    /**
     * Teleport an occupied boat through a gate.
     * <p>
     * Teleports the vehicle (which ejects the passenger server-side), then schedules
     * reattachment with exponential backoff. Sets exit velocity after successful reattach,
     * then issues a client re-sync teleport 3 ticks later so Paper resends EntityTeleport
     * and SetPassengers after water-physics settle.
     *
     * @param veh        the boat to teleport
     * @param passenger  the entity riding the boat
     * @param safeTarget the destination location
     * @param exitSpeed  the velocity to apply after reattachment
     */
    private static void teleportOccupiedBoat(
        final Vehicle veh,
        final List<Entity> passengers,
        final Location safeTarget,
        final Vector exitSpeed)
    {
        final List<Entity> parents = new ArrayList<Entity>();
        final List<Entity> children = new ArrayList<Entity>();
        collectPassengerPairs(veh, parents, children);
        try
        {
            veh.teleport(safeTarget);
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
                        if (!veh.isValid())
                        {
                            return;
                        }
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Boat reattach attempt " + attempts[0] + " -> vehicle " + veh.getUniqueId() + " (passengers=" + children.size() + ")");
                        int remaining = 0;
                        for (int i = 0; i < children.size(); i++)
                        {
                            if (attached[i]) continue;
                            final Entity parent = parents.get(i);
                            final Entity child = children.get(i);
                            try
                            {
                                if (!child.isValid()) { continue; }
                                boolean added = false;
                                try { added = parent.addPassenger(child); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger failed: " + t.getMessage()); }
                                if (!added)
                                {
                                    try { if (parent.getPassengers().contains(child)) { attached[i] = true; continue; } } catch (final Throwable ignore) {}
                                    try { child.teleport(parent.getLocation()); added = parent.addPassenger(child); } catch (final Throwable t) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger after teleport failed: " + t.getMessage()); }
                                }
                                if (added)
                                {
                                    attached[i] = true;
                                }
                                else
                                {
                                    remaining++;
                                }
                            }
                            catch (final Throwable t)
                            {
                                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Exception reattaching passenger: " + t.getMessage());
                                remaining++;
                            }
                        }
                        if (remaining == 0)
                        {
                            try
                            {
                                veh.setVelocity(exitSpeed);
                                veh.setFireTicks(0);
                            }
                            catch (final Throwable t)
                            {
                                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Failed to set boat state: " + t.getMessage());
                            }
                            // Boats need a client re-sync teleport so Paper resends
                            // EntityTeleport + SetPassengers after water-physics settle.
                            final Location resyncLoc = veh.getLocation();
                            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    try
                                    {
                                        if (veh.isValid())
                                        {
                                            veh.teleport(resyncLoc);
                                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Boat re-sync teleport: " + veh.getUniqueId());
                                        }
                                    }
                                    catch (final Throwable ignore) {}
                                }
                            }, 3L);
                        }
                        else if (attempts[0] < MAX_ATTEMPTS)
                        {
                            if (attempts[0] == 2 || attempts[0] == 5)
                            {
                                try
                                {
                                    veh.teleport(safeTarget);
                                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Re-teleported boat " + veh.getUniqueId() + " to force client update (attempt " + attempts[0] + ")");
                                }
                                catch (final Throwable tt)
                                {
                                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Re-teleport failed: " + tt.getMessage());
                                }
                            }
                            final long backoff = Math.min(1L << Math.max(0, attempts[0] - 1), 20L);
                            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], backoff);
                        }
                        else
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to attach all passengers to boat " + veh.getUniqueId() + " after " + attempts[0] + " attempts");
                            try
                            {
                                veh.setVelocity(exitSpeed);
                                veh.setFireTicks(0);
                            }
                            catch (final Throwable t)
                            {
                                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Failed to set boat state: " + t.getMessage());
                            }
                        }
                    }
                    catch (final Throwable t)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception during boat passenger reattach: " + t.getMessage());
                    }
                }
            };
            // Delay 5 ticks so the client finishes its teleport acknowledgment before
            // we send the SetPassengers packet.
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], 5L);
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to teleport occupied boat, falling back to respawn: " + t.getMessage());
            try
            {
                final org.bukkit.entity.Entity ent = safeTarget.getWorld().spawnEntity(safeTarget, EntityType.BOAT);
                final Vehicle newveh = (Vehicle) ent;
                final UUID nid = newveh.getUniqueId();
                markVehicleRecentlyTeleported(nid);
                // Attach pairs in order so parents are attached before children
                for (int i = 0; i < children.size(); i++)
                {
                    final Entity parent = parents.get(i);
                    final Entity child = children.get(i);
                    try
                    {
                        child.teleport(safeTarget);
                        if (parent.equals(veh))
                        {
                            try { newveh.addPassenger(child); } catch (final Throwable tt) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Fallback reattach failed: " + tt.getMessage()); }
                        }
                        else
                        {
                            try { parent.addPassenger(child); } catch (final Throwable tt) { WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Fallback reattach failed: " + tt.getMessage()); }
                        }
                    }
                    catch (final Throwable tt)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Fallback passenger teleport failed: " + tt.getMessage());
                    }
                }
                newveh.setVelocity(exitSpeed);
            }
            catch (final Throwable tt)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Fallback respawn also failed: " + tt.getMessage());
            }
        }
    }


    /**
     * Handle stargate minecart teleport event.
     * 
     * @param event
     *            the event
     * @return true, if successful
     */
    private static boolean handleStargateVehicleTeleportEvent(final VehicleMoveEvent event)
    {
        final Location l = event.getTo();
        final Block ch = l.getWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        // Built only when FINE is actually enabled: this used to allocate a Location, two
        // enum-name strings and a concatenation on every event, all of it discarded.
        if (WormholeXTreme.getThisPlugin() != null && WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
        {
            final String vt = (event.getVehicle() != null) ? event.getVehicle().getType().name() : "UNKNOWN";
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "VehicleMoveEvent: type=" + vt
                + " toBlock=" + ch.getLocation() + " blockType=" + ch.getType().name());
        }
        final Stargate st = StargateManager.getGateFromBlock(ch);
        // Ask the gate whether this is one of its portal blocks rather than comparing the
        // block's material to the portal material. An open portal is server-side AIR — the
        // portal material is drawn to clients only, so travellers are not subject to its
        // physics — which means the material comparison never matched and no vehicle ever
        // made it through. The player and entity paths already ask the gate.
        // Not a vehicle entering an open gate that leads somewhere: nothing to do here.
        if ((st == null) || !st.isGateActive() || (st.getGateTarget() == null)
        || !StargateManager.isPortalBlock(ch))
        {
            return false;
        }
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
        final Vehicle veh = (Vehicle) event.getVehicle();
        if (veh == null)
        {
            return false;
        }
        final Vector v = veh.getVelocity();
        veh.setVelocity(nospeed);
        final List<Entity> passengers = new ArrayList<Entity>(veh.getPassengers());
        // Riders whose cooldown and arrival mark are owed once the trip actually happens.
        final List<Player> pendingRestrictions = new ArrayList<Player>();
        if (!admitVehiclePassengers(st, veh, passengers, pendingRestrictions, gatenetwork))
        {
            return false;
        }
        // A player riding through a gate is travelling as much as one on foot, and
        // a listener that saw only walkers would miss every boat and minecart. The
        // vehicle is not announced, only the people in it: cancelling stops the
        // player travelling, and the cart is not a passenger's to veto.
        for (final Entity psg : passengers)
        {
            if ((psg instanceof Player)
            && !com.wormhole_xtreme.wormhole.events.GateEvents.firePlayerTravel(
            st, (Player) psg, st.getGateTarget(), st.getGateTarget().getGatePlayerTeleportLocation()))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                "Vehicle travel cancelled by a listener for player " + ((Player) psg).getName());
                return false;
            }
        }

        // Travel is settled, so what follows from having travelled can be applied.
        for (final Player rider : pendingRestrictions)
        {
            // Per rider, so one failing does not silently deny the rest of the boat
            // their cooldown and arrival mark. RuntimeException rather than
            // Throwable is deliberate and matches the rest of the plugin: an Error
            // is not something to swallow on the way past.
            try
            {
                StargateRestrictions.addPlayerUseCooldown(rider);
                StargateRestrictions.addPlayerRecentArrival(rider, st.getGateTarget());
            }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
            "Failed to apply travel restrictions for " + rider.getName() + ": " + e.getMessage());
        }
        }


        return dispatchVehicleTeleport(st, veh, v, target, passengers);
    }

    /**
     * Decides whether the people aboard may ride through, and defers what they owe.
     *
     * <p>Covers permission, a locked iris at the far end, and the use cooldown. A rider who
     * may travel is added to {@code pendingRestrictions} rather than charged here: a listener
     * further on may still stop the trip, and a cooldown spent on a journey that never
     * happened is not something the rider can argue with.
     *
     * <p>A locked iris bounces the vehicle rather than simply refusing, which is why this
     * needs the vehicle and not just its passengers.
     *
     * @param st
     *            the gate being entered
     * @param veh
     *            the vehicle
     * @param passengers
     *            who is aboard, possibly nobody
     * @param pendingRestrictions
     *            collects riders whose cooldown and arrival mark are owed
     * @param gatenetwork
     *            the gate's network name, for the diagnostic
     * @return true if the trip may go on being considered
     */
    private static boolean admitVehiclePassengers(final Stargate st, final Vehicle veh,
                                                  final List<Entity> passengers,
                                                  final List<Player> pendingRestrictions,
                                                  final String gatenetwork)
    {

        if (!passengers.isEmpty() && (passengers.get(0) instanceof Player))
        {
            final Player p = (Player) passengers.get(0);
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
            if (veh != null)
            {
                final UUID vid = veh.getUniqueId();
                markVehicleRecentlyTeleported(vid);
            }
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
        // Neither is applied here. Both are consequences of having travelled,
        // and a listener further down may still stop this trip - which would
        // leave the rider having spent a cooldown and been marked as arriving
        // somewhere they never went.
        else
        {
            pendingRestrictions.add(p);
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
                if (veh != null)
                {
                    final UUID vid = veh.getUniqueId();
                    markVehicleRecentlyTeleported(vid);
                }
            veh.teleport(safeIrisTarget);
            if (ConfigManager.getTimeoutShutdown() == 0)
            {
                st.shutdownStargate(true);
            }
        return false;
        }

        }

        return true;
    }

    /**
     * Sends a vehicle and whoever is aboard it to the far gate.
     *
     * <p>Reached once travel is settled: the riders may use the gate, the far end is open,
     * and no listener objected. What is left is placing the vehicle, pointing it the way it
     * is travelling, and putting the passengers back aboard in an order the client accepts.
     *
     * @param st
     *            the gate being entered
     * @param veh
     *            the vehicle travelling
     * @param v
     *            its velocity on the way in, which sets the speed on the way out
     * @param target
     *            the far gate's arrival point
     * @param passengers
     *            who is aboard, empty for a vehicle travelling alone
     * @return true if the vehicle was sent
     */
    private static boolean dispatchVehicleTeleport(final Stargate st, final Vehicle veh,
                                                   final Vector v, Location target,
                                                   final List<Entity> passengers)
    {
        final Vector new_speed = computeExitVelocity(st.getGateTarget().getGateFacing(), v, 5.0);
        if (st.getGateTarget().isGateIrisActive())
        {
            target = st.getGateMinecartTeleportLocation() != null
            ? st.getGateMinecartTeleportLocation()
            : st.getGatePlayerTeleportLocation();
            final Location safeTarget = (target != null) ? forwardAndUp(target, st.getGateTarget().getGateFacing(), 1.0, 1.0) : target;
            // set yaw from exit velocity so clients face travel direction
            try
            {
                if (safeTarget != null && new_speed != null)
                {
                    final double dx = new_speed.getX();
                    final double dz = new_speed.getZ();
                    final float yaw = (Math.abs(dx) > 0.0001 || Math.abs(dz) > 0.0001)
                    ? (float) Math.toDegrees(Math.atan2(-dx, dz))
                    : WorldUtils.getDegreesFromBlockFace(st.getGateTarget().getGateFacing());
                    safeTarget.setYaw(yaw);
                    safeTarget.setPitch(0f);
                }
        }
        catch (final Throwable ignore) {}
        if (veh != null)
        {
            final UUID vid = veh.getUniqueId();
            markVehicleRecentlyTeleported(vid);
        }
        veh.teleport(safeTarget);
        veh.setVelocity(new_speed);
        }
        else
        {
            final Location safeTarget = (target != null) ? forwardAndUp(target, st.getGateTarget().getGateFacing(), 1.0, 1.0) : target;
            // set yaw from exit velocity so clients face travel direction
            try
            {
                if (safeTarget != null && new_speed != null)
                {
                    final double dx = new_speed.getX();
                    final double dz = new_speed.getZ();
                    final float yaw = (Math.abs(dx) > 0.0001 || Math.abs(dz) > 0.0001)
                    ? (float) Math.toDegrees(Math.atan2(-dx, dz))
                    : WorldUtils.getDegreesFromBlockFace(st.getGateTarget().getGateFacing());
                    safeTarget.setYaw(yaw);
                    safeTarget.setPitch(0f);
                }
        }
        catch (final Throwable ignore) {}
        if (veh != null)
        {
            final UUID vid = veh.getUniqueId();
            markVehicleRecentlyTeleported(vid);
            if (!passengers.isEmpty())
            {
                // Mark all player passengers so PlayerListener does not solo-teleport them
                // when they are ejected by veh.teleport() on the source side.
                for (final Entity psg : passengers)
                {
                    if (psg instanceof Player)
                    {
                        markPlayerRecentlyTeleportedByVehicle(psg.getUniqueId());
                    }
            }
        // Occupied vehicle: dispatch to type-specific handler.
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Teleporting occupied vehicle through gate: " + st.getGateName() + " -> " + st.getGateTarget().getGateName() + " (type: " + veh.getType().name() + ")");
        if (veh instanceof Boat)
        {
            teleportOccupiedBoat(veh, passengers, safeTarget, new_speed);
        }
        else
        {
            teleportOccupiedMinecart(veh, passengers, safeTarget, new_speed);
        }
        }
        else
        {
            // Unoccupied vehicle: teleport directly and apply exit velocity.
            veh.teleport(safeTarget);
            veh.setVelocity(new_speed);
        }
        }
        }

        if (ConfigManager.getTimeoutShutdown() == 0)
        {
            st.shutdownStargate(true);
        }
        return true;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.vehicle.VehicleListener#onVehicleMove(org.bukkit.event.vehicle.VehicleMoveEvent)
     */
    @EventHandler
    public void onVehicleMove(final VehicleMoveEvent event)
    {
        final Vehicle vehicle = event.getVehicle();
        if (!handlesMovementOf(vehicle))
        {
            return;
        }
        // A moving vehicle raises this event many times per block travelled, and gate
        // detection has nothing to say until the block changes. Checking six ints here
        // skips a block lookup, a map lookup and two Location allocations on the great
        // majority of events — this fires roughly twenty times a second per rolling cart.
        if (!WorldUtils.hasChangedBlock(event.getFrom(), event.getTo()))
        {
            return;
        }
        // A vehicle that just came through a gate lands in the destination portal and
        // immediately raises a burst of move events. Rejecting it here, before the gate
        // lookup, is the cheapest place to break that loop.
        if (recentlyTeleported.contains(vehicle.getUniqueId()))
        {
            return;
        }
        handleStargateVehicleTeleportEvent(event);
    }
}
