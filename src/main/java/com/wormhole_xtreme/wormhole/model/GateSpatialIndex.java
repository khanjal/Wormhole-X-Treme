/*
 * Lightweight spatial index for gate block locations.
 * Buckets locations by chunk to allow fast local-area queries
 */
package com.wormhole_xtreme.wormhole.model;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Location;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.utils.BlockKey;

/**
 * Where gate blocks are, bucketed by chunk, so "what gate is near here" is a short walk
 * rather than a scan of every gate on the server.
 *
 * <p>Keyed by world and then by packed chunk position. It used to be one map keyed by a
 * {@code world + ':' + chunkX + ':' + chunkZ} string, which meant every lookup built a
 * string and every radius query built one per chunk it touched -- on a path reached from
 * {@code BlockPhysicsEvent}, which a busy server raises thousands of times a second for
 * water, falling blocks and redstone anywhere in a loaded world. A packed long costs a box
 * at worst and nothing at all inside the per-chunk loop.
 *
 * <p>The outer key is the {@link World} itself rather than its name: the object is its own
 * identity, costs nothing to hash, and cannot be null, whereas a name has to be read off the
 * world every time and a null one is a {@code NullPointerException} out of the map rather
 * than a miss.
 *
 * <p>Splitting the world out of the key rather than prefixing it also means a query for a
 * world holding no gates stops at the first lookup, which is the common case on a server
 * where gates live in one world and most block activity does not.
 */
public final class GateSpatialIndex
{
    /** Indexed gate block locations, by world and then by packed chunk position. */
    private static final ConcurrentMap<World, ConcurrentMap<Long, Set<Location>>> index =
        new ConcurrentHashMap<>();

    private GateSpatialIndex() {}

    /**
     * The chunk bucket key for a block location.
     *
     * @param loc
     *            the location
     * @return the packed chunk position holding it
     */
    private static long chunkKey(final Location loc)
    {
        return BlockKey.packChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public static void add(final Location loc)
    {
        if (loc == null || loc.getWorld() == null)
        {
            return;
        }
        index.computeIfAbsent(loc.getWorld(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(Long.valueOf(chunkKey(loc)), k -> ConcurrentHashMap.newKeySet())
            .add(loc);
    }

    public static void remove(final Location loc)
    {
        if (loc == null || loc.getWorld() == null)
        {
            return;
        }
        final ConcurrentMap<Long, Set<Location>> buckets = index.get(loc.getWorld());
        if (buckets == null)
        {
            return;
        }
        final Long key = Long.valueOf(chunkKey(loc));
        final Set<Location> inChunk = buckets.get(key);
        if (inChunk != null)
        {
            inChunk.remove(loc);
            if (inChunk.isEmpty())
            {
                buckets.remove(key);
                if (buckets.isEmpty())
                {
                    index.remove(loc.getWorld());
                }
            }
        }
    }

    public static Set<Location> collectLocationsWithinRadius(final Location center, final int radiusXZ, final int radiusY)
    {
        final Set<Location> out = new HashSet<>();
        if ((center == null) || (center.getWorld() == null))
        {
            return out;
        }
        final World world = center.getWorld();
        final ConcurrentMap<Long, Set<Location>> buckets = index.get(world);
        // A world with no gates in it is the common case for most block activity on a
        // server, and this is the whole cost of answering for one.
        if (buckets == null)
        {
            return out;
        }

        final int minChunkX = (center.getBlockX() - radiusXZ) >> 4;
        final int maxChunkX = (center.getBlockX() + radiusXZ) >> 4;
        final int minChunkZ = (center.getBlockZ() - radiusXZ) >> 4;
        final int maxChunkZ = (center.getBlockZ() + radiusXZ) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++)
        {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
            {
                final Set<Location> inChunk = buckets.get(Long.valueOf(BlockKey.packChunk(cx, cz)));
                if (inChunk != null)
                {
                    addWithinRadius(out, inChunk, center, radiusXZ, radiusY);
                }
            }
        }
        return out;
    }

    /**
     * Adds every location in one chunk that is close enough to the centre.
     *
     * <p>The two radii are checked separately because a gate is tall and thin: callers want
     * the neighbours beside them, not the ones forty blocks up the same column.
     *
     * <p>The world is checked again per location even though the buckets are already keyed
     * by world name. The two are redundant with each other on purpose -- either alone keeps
     * another world's gates out of the answer.
     *
     * @param out
     *            the set being built
     * @param inChunk
     *            the locations indexed in one chunk
     * @param center
     *            what to measure from
     * @param radiusXZ
     *            how far to reach horizontally, inclusive
     * @param radiusY
     *            how far to reach vertically, inclusive
     */
    private static void addWithinRadius(final Set<Location> out, final Set<Location> inChunk,
                                        final Location center, final int radiusXZ, final int radiusY)
    {
        final World world = center.getWorld();
        for (final Location l : inChunk)
        {
            if ((l == null) || (l.getWorld() == null) || !l.getWorld().equals(world))
            {
                continue;
            }
            final int dx = Math.abs(center.getBlockX() - l.getBlockX());
            final int dy = Math.abs(center.getBlockY() - l.getBlockY());
            final int dz = Math.abs(center.getBlockZ() - l.getBlockZ());
            if ((dx <= radiusXZ) && (dy <= radiusY) && (dz <= radiusXZ))
            {
                out.add(l);
            }
        }
    }

    public static void clear()
    {
        index.clear();
    }
}
