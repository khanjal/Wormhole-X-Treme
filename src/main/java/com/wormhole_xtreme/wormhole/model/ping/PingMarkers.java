package com.wormhole_xtreme.wormhole.model.ping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChunkTickets;
import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Every locator bar ping showing, and the one place they are put up and taken down (#16).
 *
 * <p>A ping is an invisible marker armour stand at the gate, given a waypoint transmit range so
 * the locator bar shows it, and removed a few seconds later. Each player has at most one: a new
 * ping replaces their last. Each is also removed when its owner leaves, its world unloads, ping
 * is turned off or the plugin stops, and its chunk is held loaded only while it stands.
 * Main thread only.
 */
public final class PingMarkers
{
    /** Scoreboard tag on every marker, so one the registry lost can still be told apart. */
    public static final String TAG = "wormhole_ping";

    /** The marker's colour on the locator bar, where the server can set one: the aqua gate names are in. */
    static final Color COLOUR = Color.AQUA;

    /** What became of a ping. */
    public enum Outcome
    {
        /** The marker stands and transmits. */
        PLACED,
        /** This server has no locator bar. */
        UNSUPPORTED,
        /** The marker could not be spawned, or would not take the transmit range; nothing is left standing. */
        FAILED
    }

    /** Spawns the marker; a seam, since a mock world spawns nothing. */
    @FunctionalInterface
    interface Spawner
    {
        /**
         * @param at
         *            where
         * @return the new armour stand
         */
        ArmorStand spawn(Location at);
    }

    /** One standing ping. */
    private record Marker(ArmorStand stand, Chunk chunk, int task) {}

    /** Standing pings by owner id. */
    private static final Map<UUID, Marker> LIVE = new LinkedHashMap<>();

    /** When each player last pinged, in clock milliseconds; pruned as pings are placed. */
    private static final Map<UUID, Long> LAST_PING = new HashMap<>();

    private static Spawner spawner = at -> at.getWorld().spawn(at, ArmorStand.class);

    private static LongSupplier clock = System::currentTimeMillis;

    /** Static helpers only. */
    private PingMarkers()
    {
    }

    /**
     * How long a player still has to wait before pinging again.
     *
     * @param owner
     *            the player's id
     * @return whole seconds, rounded up; 0 when they may ping now
     */
    public static long cooldownLeft(final UUID owner)
    {
        final Long last = LAST_PING.get(owner);
        if (last == null)
        {
            return 0;
        }
        final long left = (ConfigManager.getPingCooldownSeconds() * 1000L) - (clock.getAsLong() - last.longValue());
        return (left <= 0) ? 0 : ((left + 999) / 1000);
    }

    /**
     * Puts a ping up for a player, replacing any they already have, and starts their cooldown.
     *
     * @param owner
     *            who pinged
     * @param at
     *            where the marker stands; its chunk is loaded if it is not
     * @return what became of it
     */
    public static Outcome place(final Player owner, final Location at)
    {
        final Attribute transmit = Waypoints.transmitRange();
        if (transmit == null)
        {
            return Outcome.UNSUPPORTED;
        }
        final UUID id = owner.getUniqueId();
        remove(id);
        final Chunk chunk = at.getChunk();
        ChunkTickets.hold(chunk);
        final ArmorStand stand;
        try
        {
            stand = spawner.spawn(at.clone());
            settle(stand, owner);
        }
        catch (final RuntimeException e)
        {
            PluginLog.log(Level.WARNING, "Could not place a locator bar ping", e);
            ChunkTickets.release(chunk);
            return Outcome.FAILED;
        }
        if (!transmits(stand, transmit))
        {
            PluginLog.log(Level.WARNING, "A locator bar ping would not take its waypoint transmit range; removed it");
            discard(new Marker(stand, chunk, -1));
            return Outcome.FAILED;
        }
        Waypoints.colour(stand, COLOUR);
        final int task = expireLater(id, stand);
        if (task < 0)
        {
            // Nothing would ever take it down.
            discard(new Marker(stand, chunk, -1));
            return Outcome.FAILED;
        }
        LIVE.put(id, new Marker(stand, chunk, task));
        final long now = clock.getAsLong();
        final long cooldown = ConfigManager.getPingCooldownSeconds() * 1000L;
        LAST_PING.values().removeIf(last -> (now - last.longValue()) >= cooldown);
        LAST_PING.put(id, Long.valueOf(now));
        return Outcome.PLACED;
    }

    /**
     * Makes a fresh armour stand a marker nobody can see, hit, hear or push, and that is never saved.
     *
     * @param stand
     *            the new armour stand
     * @param owner
     *            the player it is for, the only one it is shown to
     */
    private static void settle(final ArmorStand stand, final Player owner)
    {
        stand.addScoreboardTag(TAG);
        stand.setMarker(true);
        stand.setInvisible(true);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setPersistent(false);
        // Hidden from everyone else, in case the server hides a hidden entity's waypoint too.
        stand.setVisibleByDefault(false);
        final Plugin plugin = WormholeXTreme.getThisPlugin();
        if (plugin != null)
        {
            owner.showEntity(plugin, stand);
        }
    }

    /**
     * Gives the marker its transmit range and reads it back.
     *
     * @param stand
     *            the marker
     * @param transmit
     *            the transmit range attribute
     * @return true if the entity has the attribute and now transmits
     */
    private static boolean transmits(final ArmorStand stand, final Attribute transmit)
    {
        try
        {
            final AttributeInstance instance = stand.getAttribute(transmit);
            if (instance == null)
            {
                return false;
            }
            instance.setBaseValue(Waypoints.TRANSMIT_RANGE);
            return instance.getValue() > 0;
        }
        catch (final RuntimeException e)
        {
            return false;
        }
    }

    /** @return the task that takes this marker down when its time is up, or -1 if none could be scheduled */
    private static int expireLater(final UUID owner, final ArmorStand stand)
    {
        final BukkitScheduler scheduler = WormholeXTreme.getScheduler();
        if (scheduler == null)
        {
            return -1;
        }
        return scheduler.scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            () -> expire(owner, stand), ConfigManager.getPingSeconds() * 20L);
    }

    /**
     * Takes a marker down when its time is up, whether or not its owner is still here.
     *
     * @param owner
     *            whose it was
     * @param stand
     *            the marker this task was for; a newer ping of theirs is left alone
     */
    private static void expire(final UUID owner, final ArmorStand stand)
    {
        final Marker marker = LIVE.get(owner);
        if ((marker != null) && (marker.stand() == stand))
        {
            LIVE.remove(owner);
            discard(marker);
        }
    }

    /**
     * Takes a player's ping down early.
     *
     * @param owner
     *            the player's id
     * @return true if they had one
     */
    public static boolean remove(final UUID owner)
    {
        final Marker marker = LIVE.remove(owner);
        if (marker == null)
        {
            return false;
        }
        cancel(marker);
        discard(marker);
        return true;
    }

    /**
     * Takes down every ping in a world, which is about to unload.
     *
     * @param world
     *            the world
     * @return how many were taken down
     */
    public static int removeIn(final World world)
    {
        final List<UUID> owners = new ArrayList<>();
        for (final Map.Entry<UUID, Marker> entry : LIVE.entrySet())
        {
            if (world.equals(entry.getValue().chunk().getWorld()))
            {
                owners.add(entry.getKey());
            }
        }
        owners.forEach(PingMarkers::remove);
        return owners.size();
    }

    /**
     * Takes every ping down, for shutdown or ping being turned off.
     *
     * @return how many were taken down
     */
    public static int removeAll()
    {
        final List<UUID> owners = new ArrayList<>(LIVE.keySet());
        owners.forEach(PingMarkers::remove);
        return owners.size();
    }

    /**
     * Removes marker stands this registry does not hold, in the worlds given: left by a reload
     * that failed to take them down.
     *
     * @param worlds
     *            the worlds to look in
     * @return how many were removed
     */
    public static int sweepOrphans(final Iterable<World> worlds)
    {
        int removed = 0;
        for (final World world : worlds)
        {
            for (final ArmorStand stand : world.getEntitiesByClass(ArmorStand.class))
            {
                if (stand.getScoreboardTags().contains(TAG) && !isLive(stand))
                {
                    stand.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** @return true if a standing ping is this stand */
    private static boolean isLive(final ArmorStand stand)
    {
        return LIVE.values().stream().anyMatch(marker -> marker.stand() == stand);
    }

    /**
     * How many pings are standing.
     *
     * @return the count
     */
    public static int live()
    {
        return LIVE.size();
    }

    /** Cancels a marker's expiry, which has not run yet. */
    private static void cancel(final Marker marker)
    {
        final BukkitScheduler scheduler = WormholeXTreme.getScheduler();
        if ((marker.task() >= 0) && (scheduler != null))
        {
            scheduler.cancelTask(marker.task());
        }
    }

    /** Removes a marker's entity and lets its chunk go; neither failing stops the other. */
    private static void discard(final Marker marker)
    {
        try
        {
            marker.stand().remove();
        }
        catch (final RuntimeException e)
        {
            PluginLog.log(Level.FINE, "Could not remove a locator bar ping", e);
        }
        try
        {
            ChunkTickets.release(marker.chunk());
        }
        catch (final RuntimeException e)
        {
            PluginLog.log(Level.FINE, "Could not let go of a locator bar ping's chunk", e);
        }
    }

    /**
     * Forgets every ping and cooldown without touching an entity, and restores the real spawner and clock.
     */
    static void reset()
    {
        LIVE.clear();
        LAST_PING.clear();
        spawner = at -> at.getWorld().spawn(at, ArmorStand.class);
        clock = System::currentTimeMillis;
    }

    /**
     * Replaces how markers are spawned, for a test.
     *
     * @param stand
     *            the spawner to use
     */
    static void spawnWith(final Spawner stand)
    {
        spawner = stand;
    }

    /**
     * Replaces the clock cooldowns are measured by, for a test.
     *
     * @param stand
     *            milliseconds
     */
    static void clockWith(final LongSupplier stand)
    {
        clock = stand;
    }
}
