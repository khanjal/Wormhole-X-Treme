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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Projectile;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
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
                sendThrough(entity, arrival, target.getGateFacing());
            }
            catch (final Throwable t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Failed to send entity through gate: " + t.getMessage());
            }
        }
    }

    /**
     * Replaces a projectile with an identical one at the destination, flying outward.
     *
     * <p>Everything that makes the projectile behave and score correctly is carried over:
     * its shooter, so kills are still credited and an ender pearl still teleports the
     * player who threw it, plus the arrow properties that affect damage and pickup.
     *
     * @param projectile
     *            the projectile arriving at the gate
     * @param arrival
     *            where it should reappear
     * @param exitFacing
     *            the direction the destination gate faces
     * @param incoming
     *            the projectile's velocity before it was moved
     * @return true if it was replaced; false to fall back to a plain teleport
     */
    private static boolean respawnProjectile(final Projectile projectile, final Location arrival,
        final BlockFace exitFacing, final Vector incoming)
    {
        try
        {
            final Class<? extends Entity> type = projectile.getType().getEntityClass();
            if (type == null || !Projectile.class.isAssignableFrom(type))
            {
                return false;
            }
            final Vector exit = WormholeXTremeVehicleListener.computeExitVelocity(exitFacing, incoming, 1.0);
            final Entity spawned = arrival.getWorld().spawn(arrival, type, fresh ->
            {
                copyProjectileState(projectile, fresh);
                fresh.setVelocity(exit);
            });
            WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(spawned.getUniqueId());
            projectile.remove();
            return true;
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                "Could not respawn projectile through gate, falling back to teleport: " + e.getMessage());
            return false;
        }
    }

    /**
     * Copies the state that makes a replacement projectile behave like the original.
     *
     * @param from
     *            the projectile arriving at the gate
     * @param to
     *            its replacement at the destination
     */
    private static void copyProjectileState(final Projectile from, final Entity to)
    {
        to.setFireTicks(from.getFireTicks());
        if (to instanceof Projectile)
        {
            // Kill credit, and for an ender pearl, who gets teleported when it lands.
            ((Projectile) to).setShooter(from.getShooter());
        }
        if ((from instanceof org.bukkit.entity.ThrownPotion) && (to instanceof org.bukkit.entity.ThrownPotion))
        {
            // Without this the potion still splashes but has no effect.
            ((org.bukkit.entity.ThrownPotion) to).setItem(((org.bukkit.entity.ThrownPotion) from).getItem());
        }
        if ((from instanceof AbstractArrow) && (to instanceof AbstractArrow))
        {
            final AbstractArrow a = (AbstractArrow) from;
            final AbstractArrow b = (AbstractArrow) to;
            b.setDamage(a.getDamage());
            b.setCritical(a.isCritical());
            b.setKnockbackStrength(a.getKnockbackStrength());
            b.setPierceLevel(a.getPierceLevel());
            b.setPickupStatus(a.getPickupStatus());
            b.setShotFromCrossbow(a.isShotFromCrossbow());
        }
    }

    /**
     * Squared speed above which an entity counts as travelling under its own momentum,
     * rather than sitting in the portal. Chosen well below a walking pace.
     */
    private static final double MOVING_THRESHOLD_SQUARED = 0.01;

    /**
     * Sets an entity's velocity, tolerating an entity that has since been removed.
     *
     * @param entity
     *            the entity
     * @param velocity
     *            the velocity to apply
     */
    private static void applyVelocity(final Entity entity, final Vector velocity)
    {
        try
        {
            entity.setVelocity(velocity);
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                "Could not set exit velocity after gate sweep: " + e.getMessage());
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
        // Players move on their own move events, minecarts and boats on vehicle-move
        // events, and a passenger travels with whatever is carrying it. Everything else is
        // this sweep's job — including a riderless horse or camel, which raises no event of
        // its own and was previously excluded here for being a Bukkit Vehicle.
        if (entity instanceof Player
            || WormholeXTremeVehicleListener.handlesMovementOf(entity)
            || entity.isInsideVehicle())
        {
            return false;
        }
        // Item frames and paintings hang on a block rather than travelling through the
        // world. Sending one through a gate tears it off its wall and leaves it orphaned at
        // the far end, so a decorated gate frame would slowly strip itself every time the
        // gate opened.
        if (entity instanceof Hanging)
        {
            return false;
        }
        // An entity that just arrived here is standing in the destination wormhole; without
        // this it would be bounced straight back on the next sweep.
        return !WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(entity.getUniqueId());
    }

    /**
     * Teleports an entity, points it out of the destination gate, and re-seats anything
     * riding it.
     *
     * <p>Redirecting matters most for things that arrive under their own momentum. An
     * arrow shot north into a gate used to come out of the far end still travelling north,
     * whichever way that gate faced — often straight back into its own frame. Speed is
     * preserved and only the direction changes, so an item that rolled in at walking pace
     * still leaves at walking pace rather than being launched.
     *
     * @param entity
     *            the entity to move
     * @param arrival
     *            the destination
     * @param exitFacing
     *            the direction the destination gate faces
     */
    private static void sendThrough(final Entity entity, final Location arrival, final BlockFace exitFacing)
    {
        WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(entity.getUniqueId());
        final Vector incoming = entity.getVelocity();

        // A projectile cannot simply be moved. Teleporting an arrow leaves it flagged as
        // having landed — AbstractArrow.isInBlock() is readable but not settable — so it
        // arrives at the far gate already "stuck" and drops out of the air no matter what
        // velocity it is given. Replacing it with a fresh one carrying the same properties
        // is the only way through the API.
        if (entity instanceof Projectile && respawnProjectile((Projectile) entity, arrival, exitFacing, incoming))
        {
            return;
        }

        entity.teleport(arrival);

        final Vector exit = WormholeXTremeVehicleListener.computeExitVelocity(exitFacing, incoming, 1.0);
        applyVelocity(entity, exit);

        // Teleporting clears an entity's motion, and a velocity set in the same tick is
        // routinely lost to that — which is why an arrow arrived through the far gate and
        // dropped straight to the ground instead of carrying on. Re-applying on the next
        // tick makes it stick. Only worth doing for something that was actually moving, so
        // a dropped item at rest does not schedule a task for nothing.
        if (incoming.lengthSquared() > MOVING_THRESHOLD_SQUARED)
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
            {
                @Override
                public void run()
                {
                    if (entity.isValid())
                    {
                        applyVelocity(entity, exit);
                    }
                }
            }, 1L);
        }

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
