/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Watches projectiles in flight so they can cross a gate at the moment they reach it.
 */
package com.wormhole_xtreme.wormhole;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    /** What is known about a projectile being followed. */
    private static final class Tracked
    {
        private final int expiresAtTick;
        private Location previous;

        Tracked(final int expiresAtTick, final Location previous)
        {
            this.expiresAtTick = expiresAtTick;
            this.previous = previous;
        }
    }

    /** Projectiles in flight. */
    private static final Map<Projectile, Tracked> tracked = new ConcurrentHashMap<Projectile, Tracked>();

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
        for (final Stargate gate : StargateManager.getAllGatesUnsorted())
        {
            if (gate.isGateActive() && (gate.getGateTarget() != null))
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
        tracked.put(projectile, new Tracked(tick + TRACK_TICKS, projectile.getLocation()));
    }

    /**
     * Creates the per-tick pass that sends tracked projectiles through gates.
     *
     * @return a runnable suitable for a repeating scheduler task
     */
    static Runnable createTicker()
    {
        return new Runnable()
        {
            @Override
            public void run()
            {
                tick++;
                if ((tick % GATE_CHECK_INTERVAL) == 0)
                {
                    refreshAnyGateOpen();
                }
                if (tracked.isEmpty())
                {
                    return;
                }
                final Iterator<Map.Entry<Projectile, Tracked>> it = tracked.entrySet().iterator();
                while (it.hasNext())
                {
                    final Map.Entry<Projectile, Tracked> entry = it.next();
                    final Projectile projectile = entry.getKey();
                    final Tracked state = entry.getValue();
                    try
                    {
                        if (!projectile.isValid() || (tick > state.expiresAtTick))
                        {
                            it.remove();
                            continue;
                        }
                        final Location from = state.previous;
                        final Location to = projectile.getLocation();
                        state.previous = to;
                        if (sendThroughGateOnPath(from, to, projectile))
                        {
                            // The original is consumed on the way through; the replacement
                            // is tracked in its place so it can cross another gate.
                            it.remove();
                        }
                    }
                    catch (final RuntimeException e)
                    {
                        it.remove();
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                            "Projectile gate tracking failed: " + e.getMessage());
                    }
                }
            }
        };
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
    private static boolean sendThroughGateOnPath(final Location from, final Location to, final Projectile projectile)
    {
        if (to == null || to.getWorld() == null)
        {
            return false;
        }
        if (from == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld()))
        {
            return crossAt(to, projectile);
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
            if (crossAt(point, projectile))
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
    private static boolean crossAt(final Location point, final Projectile projectile)
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
        return GateEntityScanner.sendProjectileThrough(projectile, gate);
    }

    /**
     * Starts tracking a projectile that was created by a gate crossing, so it can cross
     * another one.
     *
     * @param projectile
     *            the replacement projectile
     */
    static void track(final Projectile projectile)
    {
        tracked.put(projectile, new Tracked(tick + TRACK_TICKS, projectile.getLocation()));
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
    }

    /**
     * @return how many projectiles are currently being followed
     */
    static int trackedCount()
    {
        return tracked.size();
    }
}
