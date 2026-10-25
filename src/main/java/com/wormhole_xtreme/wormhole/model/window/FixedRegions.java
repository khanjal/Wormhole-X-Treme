package com.wormhole_xtreme.wormhole.model.window;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.World;

/**
 * Which held views each chunk lies behind, so a block changing anywhere is matched to the windows
 * whose view it could change by one lookup (#609).
 *
 * <p>A fixed view reads the live world only to ask whether a real block behind the opening is
 * empty, within its depth of the opening's middle and past the opening's own layer; the tunnel,
 * if it has one, lies inside that too. So a window's region is the box round that half-sphere,
 * as chunks, with a couple of blocks to spare. Block events fire constantly on a busy server, and
 * most land in no region: that answer is one hash lookup.
 */
final class FixedRegions
{
    /** Blocks added to a region's edge on every side. */
    private static final int SPARE = 2;

    /** By world name, then by chunk ({@link #keyOf}), the windows whose region covers it. */
    private static final Map<String, Map<Long, List<String>>> BY_CHUNK = new HashMap<>();

    /** What each indexed window was indexed as: its world, shape and depth. */
    private static final Map<String, Region> INDEXED = new HashMap<>();

    /** A window's region as indexed: where, and the chunk keys it was put under. */
    private record Region(String world, WindowShape shape, int depth, List<Long> chunks)
    {
    }

    /** Static state only. */
    private FixedRegions()
    {
    }

    /**
     * The windows whose region covers a block's chunk.
     *
     * @return their names; empty, and shared, for none
     */
    static List<String> at(final World world, final int x, final int z)
    {
        if (BY_CHUNK.isEmpty())
        {
            return List.of();
        }
        final Map<Long, List<String>> chunks = BY_CHUNK.get(world.getName());
        final List<String> names = (chunks == null) ? null : chunks.get(keyOf(x >> 4, z >> 4));
        return (names == null) ? List.of() : names;
    }

    /**
     * Indexes the windows that pass, as they are now, and forgets every other.
     *
     * @param windows
     *            every window held
     * @param wanted
     *            which of them hold a view, or are building one
     */
    static void track(final Collection<WindowState> windows, final Predicate<WindowState> wanted)
    {
        final Set<String> keep = new HashSet<>();
        for (final WindowState window : windows)
        {
            if (wanted.test(window))
            {
                keep.add(window.name());
                index(window);
            }
        }
        for (final String name : new ArrayList<>(INDEXED.keySet()))
        {
            if (!keep.contains(name))
            {
                forget(name);
            }
        }
    }

    /**
     * Indexes one window, unless it is indexed already as it is.
     */
    static void index(final WindowState window)
    {
        final String world = window.anchor().getWorld().getName();
        final Region was = INDEXED.get(window.name());
        if ((was != null) && was.world().equals(world) && was.shape().equals(window.shape) && (was.depth() == window.depth()))
        {
            return;
        }
        forget(window.name());
        final List<Long> chunks = chunksOf(window.shape, window.depth());
        final Map<Long, List<String>> byChunk = BY_CHUNK.computeIfAbsent(world, w -> new HashMap<>());
        for (final long chunk : chunks)
        {
            byChunk.computeIfAbsent(chunk, c -> new ArrayList<>(1)).add(window.name());
        }
        INDEXED.put(window.name(), new Region(world, window.shape, window.depth(), chunks));
    }

    /** Takes one window out of the index. */
    private static void forget(final String name)
    {
        final Region region = INDEXED.remove(name);
        if (region == null)
        {
            return;
        }
        final Map<Long, List<String>> byChunk = BY_CHUNK.get(region.world());
        for (final long chunk : region.chunks())
        {
            final List<String> names = byChunk.get(chunk);
            names.remove(name);
            if (names.isEmpty())
            {
                byChunk.remove(chunk);
            }
        }
        if (byChunk.isEmpty())
        {
            BY_CHUNK.remove(region.world());
        }
    }

    /**
     * The chunks of the box round everything a view of this depth reads: the layers behind the
     * opening out to the depth, and the depth either side of the opening's middle.
     *
     * @return their keys ({@link #keyOf})
     */
    static List<Long> chunksOf(final WindowShape shape, final int depth)
    {
        final boolean alongX = shape.into().x() != 0;
        final double[] centre = Windows.centreOf(shape);
        final int sign = alongX ? shape.into().x() : shape.into().z();
        final int base = alongX ? shape.base().x() : shape.base().z();
        final int nearAlong = base + sign;
        final int farAlong = base + (sign * (depth + SPARE));
        final double across = centre[alongX ? 2 : 0];
        final int lowAcross = (int) Math.floor(across - depth - SPARE);
        final int highAcross = (int) Math.ceil(across + depth + SPARE);
        final int lowAlong = Math.min(nearAlong, farAlong);
        final int highAlong = Math.max(nearAlong, farAlong);
        final int fromX = (alongX ? lowAlong : lowAcross) >> 4;
        final int toX = (alongX ? highAlong : highAcross) >> 4;
        final int fromZ = (alongX ? lowAcross : lowAlong) >> 4;
        final int toZ = (alongX ? highAcross : highAlong) >> 4;
        final List<Long> chunks = new ArrayList<>();
        for (int cx = fromX; cx <= toX; cx++)
        {
            for (int cz = fromZ; cz <= toZ; cz++)
            {
                chunks.add(keyOf(cx, cz));
            }
        }
        return chunks;
    }

    /**
     * A chunk's key: {@link Windows#chunkKey} scrambled by an odd multiplier, which no two chunks
     * share. Plain, its hash is x xor z, and the chunks behind one window crowd a few hash buckets.
     */
    static long keyOf(final int chunkX, final int chunkZ)
    {
        return Cells.chunkKey(chunkX, chunkZ) * 0x9E3779B97F4A7C15L;
    }

    /** @return true if any window is indexed: false answers every block event with no lookup at all */
    static boolean any()
    {
        return !INDEXED.isEmpty();
    }

    /** @return how many windows are indexed, for a test */
    static int indexed()
    {
        return INDEXED.size();
    }

    /** Forgets every region, for a test or a reload. */
    static void clear()
    {
        BY_CHUNK.clear();
        INDEXED.clear();
    }
}
