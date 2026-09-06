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
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;
import com.wormhole_xtreme.wormhole.events.StargateMinecartTeleportEvent;
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
        // Moved to EntityUtils once rings needed it too. Kept here as a delegate so the
        // callers in this package read the same as they always did.
        com.wormhole_xtreme.wormhole.utils.EntityUtils.collectPassengerPairs(root, parents, children);
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
     * Points a rider the way the vehicle is travelling, before they are re-seated.
     *
     * <p>The arrival location already carries a yaw worked out from the exit velocity, but
     * it was only ever applied to the vehicle. A passenger's view direction is theirs, not
     * the seat's: teleporting the cart does not turn the person in it, and neither does
     * re-seating them. So a rider arrived still looking the way they had been looking when
     * they entered, which on a gate turning a corner meant arriving facing sideways or
     * backwards while the cart drove on ahead.
     *
     * <p>Only players have a view to correct. The vehicle teleport has already ejected its
     * passengers when this runs, so there is no mount to disturb -- and it runs there, right
     * after the vehicle moves, rather than in the reattach tick. A player teleport makes the
     * client withhold the packets that follow until it acknowledges the move, and the mount
     * packet is exactly what would be withheld, which is the failure the delay before
     * reattaching already exists to avoid. Both moves now sit on the same side of that wait.
     *
     * @param child
     *            the passenger about to be re-seated
     * @param arrival
     *            the vehicle's arrival location, carrying the travel-direction yaw
     */
    static void faceTravelDirection(final Entity child, final Location arrival)
    {
        if (!(child instanceof Player) || (arrival == null))
        {
            return;
        }
        try
        {
            child.teleport(arrival.clone());
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Could not face rider along travel direction: " + t.getMessage());
        }
    }

    /** What differs between a minecart and a boat when an occupied one goes through a gate. */
    private enum VehicleKind
    {
        /** No re-sync teleport: it would zero the cart's velocity. */
        MINECART("minecart", 8, false),
        /** Paper needs a re-sync so it resends EntityTeleport and SetPassengers once water physics settle. */
        BOAT("boat", 12, true);

        private final String noun;
        private final int maxAttempts;
        private final boolean resync;

        VehicleKind(final String noun, final int maxAttempts, final boolean resync)
        {
            this.noun = noun;
            this.maxAttempts = maxAttempts;
            this.resync = resync;
        }

        static VehicleKind of(final Vehicle veh)
        {
            return veh instanceof Boat ? BOAT : MINECART;
        }
    }

    /**
     * Teleport an occupied vehicle through a gate.
     *
     * <p>Teleports the vehicle, which ejects its passengers server-side, then re-seats them
     * with exponential backoff and applies the exit velocity once the whole stack is aboard.
     *
     * @param veh        the vehicle to teleport
     * @param safeTarget the destination location
     * @param exitSpeed  the velocity to apply after reattachment
     */
    private static void teleportOccupiedVehicle(final Vehicle veh, final Location safeTarget,
        final Vector exitSpeed)
    {
        final VehicleKind kind = VehicleKind.of(veh);
        final List<Entity> parents = new ArrayList<Entity>();
        final List<Entity> children = new ArrayList<Entity>();
        collectPassengerPairs(veh, parents, children);
        try
        {
            veh.teleport(safeTarget);
            // Face the riders now rather than in the reattach tick below. A player teleport
            // makes the client withhold the packets that follow until it has acknowledged
            // the move, and the mount packet is exactly what would be withheld -- the
            // failure mode the delay before reattaching already exists to avoid. The vehicle
            // teleport has already ejected them, so there is no mount to disturb yet.
            for (final Entity rider : children)
            {
                faceTravelDirection(rider, safeTarget);
            }
            scheduleReattach(veh, parents, children, safeTarget, exitSpeed, kind);
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to teleport occupied " + kind.noun + ", falling back to respawn: " + t.getMessage());
            respawnAndReattach(veh, parents, children, safeTarget, exitSpeed);
        }
    }

    /**
     * Re-seats the passengers a tick or two after the vehicle has moved, retrying with
     * backoff until they are all aboard or {@code kind} runs out of attempts.
     */
    private static void scheduleReattach(final Vehicle veh, final List<Entity> parents,
        final List<Entity> children, final Location safeTarget, final Vector exitSpeed,
        final VehicleKind kind)
    {
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
                    if (!veh.isValid())
                    {
                        return;
                    }
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, kind.noun + " reattach attempt "
                        + attempts[0] + " -> vehicle " + veh.getUniqueId() + " (passengers=" + children.size() + ")");
                    final int remaining = attachAll(parents, children, attached);
                    if (remaining == 0)
                    {
                        settle(veh, exitSpeed, kind);
                    }
                    else if (attempts[0] < kind.maxAttempts)
                    {
                        retryLater(veh, safeTarget, attempts[0], taskHolder[0]);
                    }
                    else
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to attach all passengers to "
                            + kind.noun + " " + veh.getUniqueId() + " after " + attempts[0] + " attempts");
                        settle(veh, exitSpeed, kind);
                    }
                }
                catch (final RuntimeException t)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                        "Exception during " + kind.noun + " passenger reattach: " + t.getMessage());
                }
            }
        };
        // Delay 5 ticks so the client finishes its teleport acknowledgment before
        // we send the SetPassengers packet.
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], 5L);
    }

    /**
     * Seats every passenger not already aboard.
     *
     * @return how many are still not seated
     */
    private static int attachAll(final List<Entity> parents, final List<Entity> children,
        final boolean[] attached)
    {
        int remaining = 0;
        for (int i = 0; i < children.size(); i++)
        {
            if (attached[i])
            {
                continue;
            }
            final Entity child = children.get(i);
            try
            {
                if (!child.isValid())
                {
                    continue;
                }
                if (attachOne(parents.get(i), child))
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
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Exception reattaching passenger: " + t.getMessage());
                remaining++;
            }
        }
        return remaining;
    }

    /** Seats one passenger, retrying once with a position sync if the first attempt is refused. */
    private static boolean attachOne(final Entity parent, final Entity child)
    {
        try
        {
            if (parent.addPassenger(child))
            {
                return true;
            }
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "addPassenger failed: " + t.getMessage());
        }
        // An earlier attempt may already have succeeded without reporting it.
        try
        {
            if (parent.getPassengers().contains(child))
            {
                return true;
            }
        }
        catch (final RuntimeException ignore) { /* best effort */ }
        // A passenger too far from its parent is refused, so close the gap and retry.
        try
        {
            child.teleport(parent.getLocation());
            return parent.addPassenger(child);
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "addPassenger after teleport failed: " + t.getMessage());
        }
        return false;
    }

    /** Applies the exit velocity, and for a boat the client re-sync that follows it. */
    private static void settle(final Vehicle veh, final Vector exitSpeed, final VehicleKind kind)
    {
        try
        {
            veh.setVelocity(exitSpeed);
            veh.setFireTicks(0);
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Failed to set " + kind.noun + " state: " + t.getMessage());
        }
        if (!kind.resync)
        {
            return;
        }
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Boat re-sync teleport: " + veh.getUniqueId());
                    }
                }
                catch (final RuntimeException ignore) { /* the re-sync is cosmetic */ }
            }
        }, 3L);
    }

    /** Queues the next attempt, nudging the client with a re-teleport on the 2nd and 5th. */
    private static void retryLater(final Vehicle veh, final Location safeTarget, final int attempt,
        final Runnable task)
    {
        if (attempt == 2 || attempt == 5)
        {
            try
            {
                veh.teleport(safeTarget);
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Re-teleported " + veh.getUniqueId()
                    + " to force client update (attempt " + attempt + ")");
            }
            catch (final RuntimeException tt)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Re-teleport failed: " + tt.getMessage());
            }
        }
        final long backoff = Math.min(1L << Math.max(0, attempt - 1), 20L);
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), task, backoff);
    }

    /**
     * Last resort when the vehicle itself would not move: spawn a replacement at the
     * destination and put everyone back in it.
     *
     * <p>The replacement is spawned as the vehicle's own type rather than a hardcoded one. A
     * birch boat should come back a birch boat, a chest minecart a chest minecart, and
     * {@code EntityType.BOAT} stopped existing in 1.21.3 when boats were split per wood type.
     */
    private static void respawnAndReattach(final Vehicle veh, final List<Entity> parents,
        final List<Entity> children, final Location safeTarget, final Vector exitSpeed)
    {
        try
        {
            final Vehicle newveh = (Vehicle) safeTarget.getWorld().spawnEntity(safeTarget, veh.getType());
            if ((veh instanceof Minecart oldCart) && (newveh instanceof Minecart newCart))
            {
                WormholeXTreme.getThisPlugin().getServer().getPluginManager()
                    .callEvent(new StargateMinecartTeleportEvent(oldCart, newCart));
            }
            markVehicleRecentlyTeleported(newveh.getUniqueId());
            // In order, so a parent is aboard before its own passenger is.
            for (int i = 0; i < children.size(); i++)
            {
                final Entity parent = parents.get(i);
                final Entity child = children.get(i);
                try
                {
                    child.teleport(safeTarget);
                    final Entity seat = parent.equals(veh) ? newveh : parent;
                    try
                    {
                        seat.addPassenger(child);
                    }
                    catch (final RuntimeException tt)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Fallback reattach failed: " + tt.getMessage());
                    }
                }
                catch (final RuntimeException tt)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Fallback passenger teleport failed: " + tt.getMessage());
                }
            }
            newveh.setVelocity(exitSpeed);
        }
        catch (final RuntimeException tt)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Fallback respawn also failed: " + tt.getMessage());
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "VehicleMoveEvent: type=" + vt
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
        final String gatenetwork = (st.getGateNetwork() != null)
                ? st.getGateNetwork().getNetworkName()
                : "Public";
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
            if ((psg instanceof Player rider)
                && !com.wormhole_xtreme.wormhole.events.GateEvents.firePlayerTravel(
                st, rider, st.getGateTarget(), st.getGateTarget().getGatePlayerTeleportLocation()))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
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
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
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

        if (!passengers.isEmpty() && (passengers.get(0) instanceof Player p))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Minecart Player in gate:" + st.getGateName() + " gate Active: " + st.isGateActive() + " Target Gate: " + st.getGateTarget().getGateName() + " Network: " + gatenetwork);
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
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Minecart in gate:" + st.getGateName() + " gate Active: " + st.isGateActive() + " Target Gate: " + st.getGateTarget().getGateName() + " Network: " + gatenetwork);
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
                if (safeTarget != null)
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
            catch (final RuntimeException ignore) { /* arrival facing is cosmetic */ }
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
                if (safeTarget != null)
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
            catch (final RuntimeException ignore) { /* arrival facing is cosmetic */ }
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
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Teleporting occupied vehicle through gate: " + st.getGateName() + " -> " + st.getGateTarget().getGateName() + " (type: " + veh.getType().name() + ")");
                    teleportOccupiedVehicle(veh, safeTarget, new_speed);
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
