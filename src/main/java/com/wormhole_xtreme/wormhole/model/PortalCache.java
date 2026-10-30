package com.wormhole_xtreme.wormhole.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import com.wormhole_xtreme.wormhole.utils.BlockKey;

/**
 * A gate's portal blocks as a set of packed positions and a box, rebuilt when the list changes size (#430).
 *
 * <p>Moved out of {@link Stargate} as it was, over the live list the gate hands out; the cache is keyed on
 * that list's size, as before, and nothing here copies or changes the list.
 */
final class PortalCache
{
    private final List<Location> portal;

    /**
     * @param portal
     *            the gate's own portal block list, which the gate lets callers change
     */
    PortalCache(final List<Location> portal)
    {
        this.portal = portal;
    }

    /**
     * Whether the given block is one of the portal's.
     *
     * @return true if it is
     */
    boolean contains(final int x, final int y, final int z)
    {
        refresh();
        return lookup.contains(Long.valueOf(
            BlockKey.pack(x, y, z)));
    }

    /** @return the box enclosing every portal block, or null if there are none */
    BoundingBox bounds()
    {
        refresh();
        return bounds;
    }

    /**
     * Portal block positions as a set, for O(1) containment. Built lazily.
     *
     * <p>Packed positions rather than {@link Location} keys. This set is asked on every
     * player move that lands on a gate block, on every entity the periodic sweep finds near
     * an active gate, and once per open gate whenever any player crosses a block boundary --
     * and a Location key meant allocating a Location to ask the question. The world is not
     * part of the key because the set belongs to one gate, which is in one world; the world
     * was never actually discriminating here, since the old key was always built with this
     * gate's own {@code gateWorld}.
     */
    private Set<Long> lookup = null;

    /** Bounding box enclosing every portal block, for one-shot entity queries. Built lazily. */
    private BoundingBox bounds = null;

    /** Portal block count the caches were built from, used to spot a changed gate. */
    private int size = -1;

    /**
     * Rebuilds the portal block caches if the portal block list has changed size.
     *
     * <p>The list is exposed mutably via {@link Stargate#getGatePortalBlocks()}, so there is no
     * mutation hook to invalidate on. Size is a good enough signal in practice: portal
     * blocks are populated once during detection and cleared wholesale on teardown.
     */
    private void refresh()
    {
        if ((lookup != null) && (size == portal.size()))
        {
            return;
        }
        final Set<Long> built =
            new HashSet<>(Math.max(16, portal.size() * 2));
        final Extents extents = new Extents();
        for (final Location l : portal)
        {
            if (l == null)
            {
                continue;
            }
            built.add(Long.valueOf(BlockKey.pack(
                l.getBlockX(), l.getBlockY(), l.getBlockZ())));
            extents.include(l);
        }
        lookup = built;
        bounds = built.isEmpty() ? null : extents.toBoundingBox();
        size = portal.size();
    }

    /**
     * The corner-to-corner span of a set of blocks, accumulated one block at a time.
     *
     * <p>Six running values tracked by hand is what made the cache rebuild hard to read; the
     * work is the same, but the loop that uses this now says what it is doing rather than
     * spelling out twelve comparisons.
     */
    private static final class Extents
    {
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        /**
         * Widens the span to take in one more block.
         *
         * @param l
         *            the block's location
         */
        void include(final Location l)
        {
            minX = Math.min(minX, l.getBlockX());
            minY = Math.min(minY, l.getBlockY());
            minZ = Math.min(minZ, l.getBlockZ());
            maxX = Math.max(maxX, l.getBlockX());
            maxY = Math.max(maxY, l.getBlockY());
            maxZ = Math.max(maxZ, l.getBlockZ());
        }

        /**
         * The span as a box.
         *
         * <p>The maxima gain a block, because a block's location names its corner and the box
         * has to enclose the whole of it -- a one-block portal would otherwise be a box of
         * zero volume that contains nothing.
         *
         * @return the enclosing box
         */
        BoundingBox toBoundingBox()
        {
            return new BoundingBox(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        }
    }
}
