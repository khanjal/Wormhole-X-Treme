/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Periodic sweep that sends loose entities standing in an open wormhole through it.
 */
package com.wormhole_xtreme.wormhole;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.util.BoundingBox;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Sweeps active gates for loose entities — dropped items, wandering mobs — standing in an
 * open wormhole, and sends them through.
 *
 * <p>Players and vehicles are handled by their own events and are skipped here; this
 * exists only for entities that generate no event when they drift into a portal.
 *
 * <p>The sweep is deliberately shaped around the "many gates, many players" case. Per
 * tick interval it does one entity query per <em>active</em> gate, not one per portal
 * block: a Standard gate has 21 portal blocks, so the naive version issued 21 spatial
 * queries per gate and 1,000+ across a server with 50 open wormholes. Everything that
 * does not depend on the entity — the destination, the arrival location — is computed
 * once per gate rather than once per entity.
 */
public final class GateEntityScanner implements Runnable
{
    private GateEntityScanner() {}

    /**
     * Creates the sweep task.
     *
     * @return a runnable suitable for a repeating scheduler task
     */
    public static Runnable create()
    {
        return new GateEntityScanner();
    }

    @Override
    public void run()
    {
        try
        {
            // Unsorted: this is a filter loop, and sorting every gate by name on each
            // tick interval is pure waste once a server has hundreds of them.
            for (final Stargate gate : StargateManager.getAllGatesUnsorted())
            {
                try
                {
                    sweepGate(gate);
                }
                catch (final Throwable t)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                        "Entity scan failed for gate " + (gate != null ? gate.getGateName() : "null") + ": " + t.getMessage());
                }
            }
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Entity scan aborted: " + t.getMessage());
        }
    }

    /**
     * Sends any loose entity standing in this gate's open wormhole through it.
     *
     * @param gate
     *            the gate to sweep
     */
    private static void sweepGate(final Stargate gate)
    {
        if (gate == null || !gate.isGateActive() || gate.getGateTarget() == null)
        {
            return;
        }
        final World world = gate.getGateWorld();
        final BoundingBox bounds = gate.getGatePortalBounds();
        if (world == null || bounds == null)
        {
            return;
        }

        // One query for the whole gate. The box encloses the ring, so candidates still
        // have to be confirmed against the actual portal blocks below.
        final Collection<Entity> candidates = world.getNearbyEntities(bounds);
        if (candidates.isEmpty())
        {
            return;
        }

        // Per-gate, not per-entity: these do not vary across the entities found.
        final Stargate target = gate.getGateTarget();
        final Location arrival = WormholeXTremeVehicleListener.forwardAndUp(
            target.getGatePlayerTeleportLocation(), target.getGateFacing(), 1.0, 1.0);
        if (arrival == null)
        {
            return;
        }

        for (final Entity entity : candidates)
        {
            try
            {
                if (!shouldSendThrough(entity))
                {
                    continue;
                }
                final Location at = entity.getLocation();
                if (!gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()))
                {
                    continue; // inside the bounding box but not in the wormhole itself
                }
                sendThrough(entity, arrival);
            }
            catch (final Throwable t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Failed to send entity through gate: " + t.getMessage());
            }
        }
    }

    /**
     * Decides whether an entity found in a wormhole is this sweep's responsibility.
     *
     * @param entity
     *            the candidate
     * @return true if it should be sent through
     */
    private static boolean shouldSendThrough(final Entity entity)
    {
        if (entity == null)
        {
            return false;
        }
        // Players move through gates on their own move events, vehicles on vehicle-move
        // events, and a passenger travels with whatever is carrying it.
        if (entity instanceof Player || entity instanceof Vehicle || entity.isInsideVehicle())
        {
            return false;
        }
        // An entity that just arrived here is standing in the destination wormhole; without
        // this it would be bounced straight back on the next sweep.
        return !WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(entity.getUniqueId());
    }

    /**
     * Teleports an entity and re-seats anything riding it.
     *
     * @param entity
     *            the entity to move
     * @param arrival
     *            the destination
     */
    private static void sendThrough(final Entity entity, final Location arrival)
    {
        WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(entity.getUniqueId());
        entity.teleport(arrival);

        final List<Entity> passengers = entity.getPassengers();
        if (passengers.isEmpty())
        {
            return;
        }
        final java.util.List<Entity> parents = new java.util.ArrayList<Entity>();
        final java.util.List<Entity> children = new java.util.ArrayList<Entity>();
        WormholeXTremeVehicleListener.collectPassengerPairs(entity, parents, children);
        for (int i = 0; i < children.size(); i++)
        {
            try
            {
                parents.get(i).addPassenger(children.get(i));
            }
            catch (final Throwable t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Failed to re-seat passenger after gate sweep: " + t.getMessage());
            }
        }
    }
}
