package com.wormhole_xtreme.wormhole;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Catches a projectile at the moment it reaches a gate, rather than finding it afterwards.
 *
 * <p>The periodic sweep works for things that linger in a portal — a wandering mob, a
 * dropped item — because they are still there a tick or a second later. A projectile is
 * not. Portal blocks are air, so an arrow crosses the ring in roughly one tick and carries
 * on until it hits something, often well past the gate. Polling every twenty ticks almost
 * never sees it in the portal, and when it does the arrow has usually already landed and
 * stopped, which is why arrows appeared to trickle out of the destination rather than fly.
 *
 * <p>So projectiles are watched individually, and what is checked is the path they
 * travelled rather than where they happen to be. A drawn bow puts an arrow at roughly three
 * blocks per tick and a portal is one block thick, so sampling its position once a tick
 * steps clean over the gate: in front of it on one tick, well past it on the next, never
 * inside it. That is why an arrow only crossed when something behind the gate stopped it in
 * the portal; with nothing there it flew through and landed beyond, untouched.
 *
 * <p>Each tick the segment from the previous position to the current one is walked in
 * sub-block steps, and the crossing happens if any point along it lies in an open portal.
 * Cost scales with the number of projectiles in flight, not with the number of gates.
 */
class ProjectileGateTracker implements Listener
{
    /**
     * Ticks a projectile is followed before being forgotten. Beyond a few seconds it has
     * hit something, despawned, or is never reaching a gate.
     */
    private static final int TRACK_TICKS = 200;

    /**
     * How far apart the path between two ticks is sampled. A portal is one block thick, so
     * anything below one block cannot step over it; half a block leaves margin for a
     * projectile clipping the ring at an angle.
     */
    private static final double PATH_STEP = 0.5;

    /**
     * Most gates one shot is carried through. Two connected gates facing each other hand an arrow
     * back and forth for as long as it is followed, so the count goes with it to each replacement.
     */
    static final int MOST_CROSSINGS = 4;

    /** Ticks a hit is remembered, long enough for the exit velocity applied a tick later to see it. */
    private static final int HIT_TICKS = 20;

    /** What is known about a projectile being followed. */
    private static final class Tracked
    {
        private final int expiresAtTick;
        private final int crossings;
        private final Stargate cameOutOf;
        private Location previous;

        Tracked(final int expiresAtTick, final Location previous, final int crossings, final Stargate cameOutOf)
        {
            this.expiresAtTick = expiresAtTick;
            this.previous = previous;
            this.crossings = crossings;
            this.cameOutOf = cameOutOf;
        }
    }

    /** Projectiles in flight. */
    private static final Map<Projectile, Tracked> tracked = new ConcurrentHashMap<>();

    /** Projectiles that have hit something, by id, with the tick they did. */
    private static final Map<UUID, Integer> hit = new ConcurrentHashMap<>();

    /** Ticks since the tracker started, used only to expire entries. */
    private static int tick = 0;

    /** How often the any-gate-open flag is refreshed. Gates do not open and close faster. */
    private static final int GATE_CHECK_INTERVAL = 20;

    /**
     * Whether any gate is currently open. When nothing is open a projectile cannot cross
     * anything, so none are followed and the whole mechanism costs one boolean per launch
     * and one empty-map check per tick. Recomputed once a second rather than per launch,
     * because an arrow farm can launch far more often than gates change state.
     */
    private static volatile boolean anyGateOpen = false;

    private static void refreshAnyGateOpen()
    {
        // The open set, not every gate. This ran once a second over the whole gate list to
        // answer a question about the handful of gates that were open, which made a quiet
        // server with thousands of built gates pay thousands of checks a second for an
        // answer that was almost always the same.
        //
        // Still asked of the registry, though: the open set follows the active flag alone, so
        // it can hold a gate the server does not have -- one still being detected, or one
        // built in a test. Filtering the registry is what this did before and a projectile
        // cannot cross a gate nobody can reach.
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            if ((gate.getGateTarget() != null) && StargateManager.isRegistered(gate))
            {
                anyGateOpen = true;
                return;
            }
        }
        anyGateOpen = false;
    }

    /**
     * Starts following a newly launched projectile.
     *
     * @param event
     *            the launch
     */
    @EventHandler
    public void onProjectileLaunch(final ProjectileLaunchEvent event)
    {
        if (event.isCancelled() || !anyGateOpen)
        {
            return;
        }
        final Projectile projectile = event.getEntity();
        tracked.put(projectile, new Tracked(tick + TRACK_TICKS, projectile.getLocation(), 0, null));
    }

    /**
     * Remembers that a projectile hit something, so it is followed no further than this tick's path.
     *
     * <p>Not dropped here: the hit lands mid-tick and the path is walked at the start of the next,
     * so an arrow that crossed a gate and struck the wall behind it would never go through.
     *
     * @param event
     *            the hit
     */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(final ProjectileHitEvent event)
    {
        final Projectile projectile = event.getEntity();
        if (projectile == null)
        {
            return;
        }
        hit.put(projectile.getUniqueId(), Integer.valueOf(tick));
    }

    /**
     * Creates the per-tick pass that sends tracked projectiles through gates.
     *
     * @return a runnable suitable for a repeating scheduler task
     */
    static Runnable createTicker()
    {
        return () ->
        {
            tick++;
            if ((tick % GATE_CHECK_INTERVAL) == 0)
            {
                refreshAnyGateOpen();
            }
            if (!hit.isEmpty())
            {
                hit.values().removeIf(at -> (tick - at.intValue()) >= HIT_TICKS);
            }
            if (tracked.isEmpty())
            {
                return;
            }
            final Iterator<Map.Entry<Projectile, Tracked>> it = tracked.entrySet().iterator();
            while (it.hasNext())
            {
                final Map.Entry<Projectile, Tracked> entry = it.next();
                if (finishedWith(entry.getKey(), entry.getValue()))
                {
                    it.remove();
                }
            }
        };
    }

    /**
     * Moves one projectile on by a tick, and says whether it should stop being followed.
     *
     * <p>Three ways it stops: it is gone, it has been followed longer than anything worth
     * following, or it went through a gate -- in which case the original is consumed and its
     * replacement is already being followed in its place.
     *
     * <p>A projectile whose handling throws also stops. Kept, it would throw again on the
     * next tick and every tick after, turning one bad projectile into a log line per tick for
     * as long as the server runs.
     *
     * @param projectile
     *            the projectile
     * @param state
     *            where it was last tick, and when to give up on it
     * @return true if it should be dropped from the tracked set
     */
    private static boolean finishedWith(final Projectile projectile, final Tracked state)
    {
        try
        {
            if (!projectile.isValid() || (tick > state.expiresAtTick))
            {
                return true;
            }
            final Location from = state.previous;
            final Location to = projectile.getLocation();
            state.previous = to;
            // The path first: a hit just behind a gate lands in the same tick the arrow crossed it.
            return sendThroughGateOnPath(from, to, projectile, state)
                || hit.containsKey(projectile.getUniqueId());
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Projectile gate tracking failed", e);
            return true;
        }
    }

    /**
     * Sends a projectile through a gate if the path it just travelled crossed one.
     *
     * <p>The segment is walked rather than the end point tested, because a projectile
     * covers more ground in one tick than a portal is thick.
     *
     * @param from
     *            where it was on the previous tick, may be null
     * @param to
     *            where it is now
     * @param projectile
     *            the projectile
     * @return true if it was sent through
     */
    private static boolean sendThroughGateOnPath(final Location from, final Location to, final Projectile projectile,
        final Tracked state)
    {
        if (to == null || to.getWorld() == null)
        {
            return false;
        }
        if (from == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld()))
        {
            return crossAt(to, projectile, state);
        }

        final int steps = Math.max(1, (int) Math.ceil(from.distance(to) / PATH_STEP));
        final double dx = (to.getX() - from.getX()) / steps;
        final double dy = (to.getY() - from.getY()) / steps;
        final double dz = (to.getZ() - from.getZ()) / steps;

        // Walked forwards, so a projectile that would reach two gates in one tick takes
        // whichever it actually got to first.
        for (int i = 0; i <= steps; i++)
        {
            final Location point = new Location(to.getWorld(),
                from.getX() + (dx * i), from.getY() + (dy * i), from.getZ() + (dz * i));
            if (crossAt(point, projectile, state))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a projectile through if this point on its path is inside an open portal.
     *
     * @param point
     *            a point on the projectile's path
     * @param projectile
     *            the projectile
     * @return true if it was sent through
     */
    private static boolean crossAt(final Location point, final Projectile projectile, final Tracked state)
    {
        final Stargate gate = StargateManager.getGateFromBlock(
            point.getWorld().getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ()));
        if (gate == null || !gate.isGateActive() || gate.getGateTarget() == null)
        {
            return false;
        }
        if (!gate.isGatePortalBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ()))
        {
            return false;
        }
        if (WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(projectile.getUniqueId()))
        {
            return false;
        }
        // Not straight back into the gate it came out of: that is an arrow bounced off somebody at the exit.
        if ((state != null) && (gate == state.cameOutOf))
        {
            return false;
        }
        return GateEntityScanner.sendProjectileThrough(projectile, gate);
    }

    /**
     * Follows the projectile a gate crossing fired in place of another, so it can cross another
     * gate -- within what is left of the original's lifetime and crossings.
     *
     * @param replacement
     *            the projectile fired at the far gate
     * @param original
     *            the one it replaces
     * @param exit
     *            the gate it was fired from, which it may not go straight back into
     */
    static void track(final Projectile replacement, final Projectile original, final Stargate exit)
    {
        final Tracked was = (original == null) ? null : tracked.get(original);
        final int crossings = ((was == null) ? 0 : was.crossings) + 1;
        if (crossings >= MOST_CROSSINGS)
        {
            return;
        }
        final int expires = (was == null) ? (tick + TRACK_TICKS) : was.expiresAtTick;
        tracked.put(replacement, new Tracked(expires, replacement.getLocation(), crossings, exit));
    }

    /**
     * @param entity
     *            an entity
     * @return true if it is a projectile that has hit something in the last second
     */
    static boolean hasHit(final Entity entity)
    {
        return (entity instanceof Projectile) && hit.containsKey(entity.getUniqueId());
    }

    /**
     * Recomputes the any-gate-open flag immediately. Only for tests, which do not run the
     * ticker often enough to hit the normal refresh.
     */
    static void refreshOpenGateFlagForTest()
    {
        refreshAnyGateOpen();
    }

    /**
     * Forgets every tracked projectile. Used when the plugin shuts down.
     */
    static void clear()
    {
        tracked.clear();
        hit.clear();
    }

    /**
     * @return how many projectiles are currently being followed
     */
    static int trackedCount()
    {
        return tracked.size();
    }
}
