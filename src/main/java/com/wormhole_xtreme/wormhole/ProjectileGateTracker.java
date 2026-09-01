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
 * <p>So projectiles are watched individually instead. Each one is followed from launch and
 * checked every tick while it is in the air; the moment it is inside a portal block it goes
 * through. Cost scales with the number of projectiles actually in flight, not with the
 * number of gates, and a server where nobody is shooting anything pays for one pass over an
 * empty map per tick.
 */
class ProjectileGateTracker implements Listener
{
    /**
     * Ticks a projectile is followed before being forgotten. Beyond a few seconds it has
     * hit something, despawned, or is never reaching a gate.
     */
    private static final int TRACK_TICKS = 200;

    /** Projectiles in flight, mapped to the tick at which tracking should stop. */
    private static final Map<Projectile, Integer> tracked = new ConcurrentHashMap<Projectile, Integer>();

    /** Ticks since the tracker started, used only to expire entries. */
    private static int tick = 0;

    /**
     * Starts following a newly launched projectile.
     *
     * @param event
     *            the launch
     */
    @EventHandler
    public void onProjectileLaunch(final ProjectileLaunchEvent event)
    {
        if (event.isCancelled())
        {
            return;
        }
        tracked.put(event.getEntity(), Integer.valueOf(tick + TRACK_TICKS));
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
                if (tracked.isEmpty())
                {
                    return;
                }
                final Iterator<Map.Entry<Projectile, Integer>> it = tracked.entrySet().iterator();
                while (it.hasNext())
                {
                    final Map.Entry<Projectile, Integer> entry = it.next();
                    final Projectile projectile = entry.getKey();
                    try
                    {
                        if (!projectile.isValid() || (tick > entry.getValue().intValue()))
                        {
                            it.remove();
                            continue;
                        }
                        if (sendThroughGateIfInside(projectile))
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
     * Sends a projectile through a gate if it is currently inside one's portal.
     *
     * @param projectile
     *            the projectile to test
     * @return true if it was sent through
     */
    private static boolean sendThroughGateIfInside(final Projectile projectile)
    {
        final Location at = projectile.getLocation();
        final Stargate gate = StargateManager.getGateFromBlock(
            at.getWorld().getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        if (gate == null || !gate.isGateActive() || gate.getGateTarget() == null)
        {
            return false;
        }
        if (!gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()))
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
        tracked.put(projectile, Integer.valueOf(tick + TRACK_TICKS));
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
