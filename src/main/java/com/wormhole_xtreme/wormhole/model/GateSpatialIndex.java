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

public final class GateSpatialIndex
{
    private static final ConcurrentMap<String, Set<Location>> index = new ConcurrentHashMap<String, Set<Location>>();

    private GateSpatialIndex() {}

    private static String chunkKey(final World w, final int chunkX, final int chunkZ)
    {
        return w.getName() + ':' + chunkX + ':' + chunkZ;
    }

    private static String chunkKey(final Location loc)
    {
        return chunkKey(loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public static void add(final Location loc)
    {
        if (loc == null || loc.getWorld() == null)
        {
            return;
        }
        final String key = chunkKey(loc);
        final Set<Location> set = index.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        set.add(loc);
    }

    public static void remove(final Location loc)
    {
        if (loc == null || loc.getWorld() == null)
        {
            return;
        }
        final String key = chunkKey(loc);
        final Set<Location> set = index.get(key);
        if (set != null)
        {
            set.remove(loc);
            if (set.isEmpty())
            {
                index.remove(key);
            }
        }
    }

    public static Set<Location> collectLocationsWithinRadius(final Location center, final int radiusXZ, final int radiusY)
    {
        final Set<Location> out = new HashSet<Location>();
        if (center == null || center.getWorld() == null)
        {
            return out;
        }

        final World world = center.getWorld();

        final int minChunkX = (center.getBlockX() - radiusXZ) >> 4;
        final int maxChunkX = (center.getBlockX() + radiusXZ) >> 4;
        final int minChunkZ = (center.getBlockZ() - radiusXZ) >> 4;
        final int maxChunkZ = (center.getBlockZ() + radiusXZ) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++)
        {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
            {
                final String key = chunkKey(world, cx, cz);
                final Set<Location> set = index.get(key);
                if (set == null)
                {
                    continue;
                }
                for (final Location l : set)
                {
                    if (l == null || l.getWorld() == null)
                    {
                        continue;
                    }
                    if (!l.getWorld().equals(world))
                    {
                        continue;
                    }
                    final int dx = Math.abs(center.getBlockX() - l.getBlockX());
                    final int dy = Math.abs(center.getBlockY() - l.getBlockY());
                    final int dz = Math.abs(center.getBlockZ() - l.getBlockZ());
                    if (dx <= radiusXZ && dy <= radiusY && dz <= radiusXZ)
                    {
                        out.add(l);
                    }
                }
            }
        }

        return out;
    }

    public static void clear()
    {
        index.clear();
    }
}
