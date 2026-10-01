package com.wormhole_xtreme.wormhole;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Catches an item thrown at a gate at the moment it crosses the opening.
 *
 * <p>The sweep only sees an item that is in the opening when it runs, once a second. One a
 * player tosses or a dispenser fires crosses the one-block opening in a few ticks and lands
 * behind the ring, so most of them missed the gate (issue #537).
 *
 * <p>So an item that starts moving near a gate is followed for a few seconds, the way
 * {@link ProjectileGateTracker} follows an arrow: each tick the path it travelled since the last
 * is walked in sub-block steps, and it crosses if any point lies in an open portal. It stops being
 * followed when it lands, is picked up, despawns, or has been followed long enough; an item lying
 * still is left to the sweep, as before. Cost scales with the items in flight near a gate.
 *
 * <p>Ordinary drops count as moving too: a broken block, a killed mob or a harvested crop spawns
 * its items with a little speed, so those within reach of a gate are followed until they settle.
 * Hence the cap on how many are followed at once.
 */
class ItemGateTracker implements Listener
{
    /** Ticks an item is followed. A thrown item has landed well before this. */
    private static final int TRACK_TICKS = 100;

    /** How far apart the path between two ticks is sampled, as for projectiles. */
    private static final double PATH_STEP = 0.5;

    /** Most gates one item is carried through, against two dialled pairs facing each other. */
    static final int MOST_CROSSINGS = ProjectileGateTracker.MOST_CROSSINGS;

    /** Most items followed at once, so an item farm beside a gate cannot grow the map without end. */
    static final int MOST_TRACKED = 256;

    /** How close to an opening an item has to start moving to be followed, in blocks. */
    private static final double REACH = 8.0;

    /**
     * Further than an item can fly in one tick. A longer step was a teleport, by the sweep or
     * anything else, and walking it would cross whatever gates lie between the two places.
     */
    private static final double MOST_TICK_TRAVEL = 8.0;

    /** Squared speed below which a spawned item is lying still, the sweep's to find. */
    private static final double MOVING_SQUARED = 0.01;

    /**
     * Squared speed along the ground below which a landed item has stopped sliding. One that lands
     * short of the opening still slides on into it, so it is followed until it settles.
     */
    private static final double SETTLED_SQUARED = 0.0001;

    /** Ticks between two log lines saying the followed set is full. */
    private static final int FULL_LOG_TICKS = 200;

    /** The tick the followed set was last said to be full. */
    private static int fullLoggedAt = -FULL_LOG_TICKS;

    /** What is known about an item being followed. */
    private static final class Tracked
    {
        private final Item item;
        private final int expiresAtTick;
        private int crossings;
        private Stargate cameOutOf;
        private Location previous;

        Tracked(final Item item, final int expiresAtTick, final Location previous)
        {
            this.item = item;
            this.expiresAtTick = expiresAtTick;
            this.previous = previous;
        }
    }

    /**
     * Items in flight near a gate, by id: a wrapper may hash by something a cross-world teleport
     * changes, and its entry would then never be found again.
     */
    private static final Map<UUID, Tracked> tracked = new ConcurrentHashMap<>();

    /** Ticks since the tracker started, used only to expire entries. */
    private static int tick = 0;

    /** Follows an item a player throws, near a gate. */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(final PlayerDropItemEvent event)
    {
        follow(event.getItemDrop());
    }

    /**
     * Follows an item that comes into the world moving, near a gate: a dispenser's shot, or a
     * throw on a server that raises this rather than the drop event first.
     */
    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(final ItemSpawnEvent event)
    {
        final Item item = event.getEntity();
        if (item.getVelocity().lengthSquared() > MOVING_SQUARED)
        {
            follow(item);
        }
    }

    /** Starts following an item, if it is near a gate it could cross and there is room. */
    private static void follow(final Item item)
    {
        if (item == null)
        {
            return;
        }
        // Near a gate first: an item nowhere near one is not being turned away by a full set.
        final Location at = item.getLocation();
        if (!nearAGate(at))
        {
            return;
        }
        if (tracked.size() >= MOST_TRACKED)
        {
            if ((tick - fullLoggedAt) >= FULL_LOG_TICKS)
            {
                fullLoggedAt = tick;
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                    "Already following " + MOST_TRACKED + " items near gates; more are left to the sweep");
            }
            return;
        }
        tracked.putIfAbsent(item.getUniqueId(), new Tracked(item, tick + TRACK_TICKS, at));
    }

    /**
     * Whether a point is near an opening an item could cross or be stopped by: an open gate, or a
     * drawn iris. Walks only those gates, a handful, never every gate built.
     */
    private static boolean nearAGate(final Location at)
    {
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            if ((gate.getGateTarget() != null) && near(gate, at))
            {
                return true;
            }
        }
        // A drawn iris over an idle gate is air to the server, so an item flies through it too.
        for (final Stargate gate : StargateManager.getIrisGates())
        {
            if (gate.isGateIrisDrawn() && near(gate, at))
            {
                return true;
            }
        }
        return false;
    }

    /** Whether a point is within reach of this gate's opening. */
    private static boolean near(final Stargate gate, final Location at)
    {
        final World world = gate.getGateWorld();
        final BoundingBox box = gate.getGatePortalBounds();
        // The registry last: it normalises the name, where the rest are a few comparisons.
        return (world != null) && (box != null) && world.equals(at.getWorld())
            && (at.getX() >= (box.getMinX() - REACH)) && (at.getX() <= (box.getMaxX() + REACH))
            && (at.getY() >= (box.getMinY() - REACH)) && (at.getY() <= (box.getMaxY() + REACH))
            && (at.getZ() >= (box.getMinZ() - REACH)) && (at.getZ() <= (box.getMaxZ() + REACH))
            && StargateManager.isRegistered(gate);
    }

    /**
     * Creates the per-tick pass that sends followed items through gates.
     *
     * @return a runnable suitable for a repeating scheduler task
     */
    static Runnable createTicker()
    {
        return () ->
        {
            tick++;
            if (tracked.isEmpty())
            {
                return;
            }
            final Iterator<Tracked> it = tracked.values().iterator();
            while (it.hasNext())
            {
                final Tracked state = it.next();
                if (finishedWith(state.item, state))
                {
                    it.remove();
                }
            }
        };
    }

    /**
     * Moves one item on by a tick, and says whether it should stop being followed.
     *
     * <p>An item whose handling throws also stops, rather than throwing again every tick.
     *
     * @return true if it should be dropped from the followed set
     */
    private static boolean finishedWith(final Item item, final Tracked state)
    {
        try
        {
            if (!item.isValid() || (tick > state.expiresAtTick))
            {
                return true;
            }
            final Location from = state.previous;
            final Location to = item.getLocation();
            state.previous = to;
            // The path first: an item lands on the same tick it crossed the opening's floor.
            final Stargate crossed = gateOnPath(from, to, item, state);
            if (crossed != null)
            {
                if (!item.isValid() || (++state.crossings >= MOST_CROSSINGS))
                {
                    return true;
                }
                state.cameOutOf = crossed.getGateTarget();
                state.previous = item.getLocation();
                return false;
            }
            return item.isOnGround() && settled(item.getVelocity());
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Item gate tracking failed", e);
            return true;
        }
    }

    /** Whether a landed item has stopped sliding along the ground. */
    private static boolean settled(final Vector velocity)
    {
        return ((velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ())) < SETTLED_SQUARED;
    }

    /**
     * Sends an item through a gate if the path it just travelled crossed one.
     *
     * @param from
     *            where it was on the previous tick, may be null
     * @param to
     *            where it is now
     * @return the gate it went into, or null if it crossed none
     */
    private static Stargate gateOnPath(final Location from, final Location to, final Item item, final Tracked state)
    {
        if ((to == null) || (to.getWorld() == null))
        {
            return null;
        }
        if ((from == null) || !to.getWorld().equals(from.getWorld()) || (from.distanceSquared(to)
            > (MOST_TICK_TRAVEL * MOST_TICK_TRAVEL)))
        {
            return crossAt(to, item, state);
        }

        final int steps = Math.max(1, (int) Math.ceil(from.distance(to) / PATH_STEP));
        final double dx = (to.getX() - from.getX()) / steps;
        final double dy = (to.getY() - from.getY()) / steps;
        final double dz = (to.getZ() - from.getZ()) / steps;
        // From the start point: where an item spawned, or came out of a gate, was never itself checked.
        for (int i = 0; i <= steps; i++)
        {
            final Location point = new Location(to.getWorld(),
                from.getX() + (dx * i), from.getY() + (dy * i), from.getZ() + (dz * i));
            final Stargate gate = crossAt(point, item, state);
            if (gate != null)
            {
                return gate;
            }
        }
        return null;
    }

    /**
     * Sends an item through, or onto a shut iris, if this point on its path is in a portal.
     *
     * @return the gate it went into, or null if this point is not a way through
     */
    private static Stargate crossAt(final Location point, final Item item, final Tracked state)
    {
        final Stargate gate = StargateManager.getGateFromBlock(
            point.getWorld().getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ()));
        if ((gate == null) || !gate.isGatePortalBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ()))
        {
            return null;
        }
        // Any drawn shut iris stops it: idle, dialling out, or the far end of a wormhole.
        if (gate.isGateIrisActive() && gate.isGateIrisDrawn())
        {
            item.remove();
            return gate;
        }
        if (!gate.isGatePortalOpen() || (gate.getGateTarget() == null) || (gate == state.cameOutOf))
        {
            return null;
        }
        return GateEntityScanner.sendItemThrough(item, gate) ? gate : null;
    }

    /** Forgets every followed item. Used when the plugin shuts down. */
    static void clear()
    {
        tracked.clear();
        fullLoggedAt = tick - FULL_LOG_TICKS;
    }

    /**
     * @return how many items are currently being followed
     */
    static int trackedCount()
    {
        return tracked.size();
    }
}
