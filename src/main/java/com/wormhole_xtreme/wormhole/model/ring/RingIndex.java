package com.wormhole_xtreme.wormhole.model.ring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Answers "is this block inside a ring, and which one" in one map lookup.
 *
 * <p>This sits on the player move path, which every player crossing every block boundary
 * runs through, so the answer has to cost a hash and nothing else. Every trigger volume in
 * the world is expanded once at load and stored block by block; nothing is scanned, tested
 * for distance, or recomputed at lookup time.
 *
 * <p>Unlike {@link com.wormhole_xtreme.wormhole.model.GateSpatialIndex} this does not bucket
 * by chunk, because it never needs a radius query. Gates ask "what is near here", which is a
 * search; rings ask "what is exactly here", which is a key. Ring creation does need an area
 * query for its overlap and separation checks, but that runs once per player command and can
 * afford to walk the world's pairs.
 *
 * <p>Coordinates are packed into a long the way Minecraft packs block positions, so a lookup
 * allocates one boxed key rather than building a string.
 */
public final class RingIndex
{
    /** Trigger volume blocks, by world name and then by packed block position. */
    private static final ConcurrentMap<String, ConcurrentMap<Long, RingEnd>> volumes =
        new ConcurrentHashMap<String, ConcurrentMap<Long, RingEnd>>();

    /** Perimeter blocks, by world name and then by packed block position. */
    private static final ConcurrentMap<String, ConcurrentMap<Long, RingEnd>> perimeters =
        new ConcurrentHashMap<String, ConcurrentMap<Long, RingEnd>>();

    private RingIndex() {}

    /**
     * One end of one pair: what a block lookup resolves to.
     *
     * <p>Both halves are needed. The ring says where the block is and which way it faces;
     * the pair holds the cooldown, the phase and the far end, and is the thing a cycle
     * actually runs on.
     */
    public static final class RingEnd
    {
        /** The pair this end belongs to. */
        private final RingPair pair;

        /** The specific end that was hit. */
        private final Ring ring;

        /**
         * Instantiates a new ring end.
         *
         * @param pair
         *            the pair this end belongs to
         * @param ring
         *            the specific end
         */
        RingEnd(final RingPair pair, final Ring ring)
        {
            this.pair = pair;
            this.ring = ring;
        }

        /** @return the pair this end belongs to */
        public RingPair getPair()
        {
            return pair;
        }

        /** @return the specific end that was hit */
        public Ring getRing()
        {
            return ring;
        }
    }

    /**
     * Packs a block position into a single long, as Minecraft does.
     *
     * <p>26 bits of x, 26 of z, 12 of y. That covers the full world border and the whole
     * build range, and means a lookup key is one primitive rather than a concatenated
     * string built on a path that runs for every player on every block boundary.
     *
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @return the packed position
     */
    static long pack(final int x, final int y, final int z)
    {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    /**
     * Reads the x back out of a packed position.
     *
     * <p>Shifting a signed long right brings the sign with it, which is what makes a
     * negative coordinate survive the round trip.
     *
     * @param packed
     *            a position from {@link #pack}
     * @return block x
     */
    static int unpackX(final long packed)
    {
        return (int) (packed >> 38);
    }

    /**
     * Reads the y back out of a packed position.
     *
     * <p>Y sits in the low twelve bits, which is not enough for a plain mask to preserve its
     * sign — masking a negative y gives a large positive number instead. Shifting it up to
     * the top of the long and arithmetically back down sign-extends it properly.
     *
     * <p>This matters more than it looks. The world runs from y=-64, so ordinary rings in
     * deepslate, caves and the nether floor all sit below zero, and getting this wrong would
     * restore their blocks thousands of blocks away — leaving slabs standing in the floor
     * forever and writing stray air into the sky.
     *
     * @param packed
     *            a position from {@link #pack}
     * @return block y
     */
    static int unpackY(final long packed)
    {
        return (int) ((packed << 52) >> 52);
    }

    /**
     * Reads the z back out of a packed position.
     *
     * @param packed
     *            a position from {@link #pack}
     * @return block z
     */
    static int unpackZ(final long packed)
    {
        return (int) ((packed << 26) >> 38);
    }

    /**
     * Adds both ends of a pair to the index.
     *
     * @param pair
     *            the pair to index
     * @param reach
     *            how many block layers deep each trigger volume runs
     */
    public static void add(final RingPair pair, final int reach)
    {
        if (pair == null)
        {
            return;
        }
        addEnd(pair, pair.getEndA(), reach);
        addEnd(pair, pair.getEndB(), reach);
    }

    /**
     * Adds one end of a pair to the index.
     *
     * @param pair
     *            the pair the end belongs to
     * @param ring
     *            the end to index
     * @param reach
     *            how many block layers deep the trigger volume runs
     */
    private static void addEnd(final RingPair pair, final Ring ring, final int reach)
    {
        final String world = pair.getWorldName();
        final RingEnd end = new RingEnd(pair, ring);

        final ConcurrentMap<Long, RingEnd> volume = volumes.computeIfAbsent(world,
            k -> new ConcurrentHashMap<Long, RingEnd>());
        for (final int[] block : ring.triggerVolumeBlocks(volumeDepth(ring, reach)))
        {
            volume.put(Long.valueOf(pack(block[0], block[1], block[2])), end);
        }

        final ConcurrentMap<Long, RingEnd> edge = perimeters.computeIfAbsent(world,
            k -> new ConcurrentHashMap<Long, RingEnd>());
        for (final int[] block : ring.perimeterBlocks())
        {
            edge.put(Long.valueOf(pack(block[0], block[1], block[2])), end);
        }
    }

    /**
     * Removes both ends of a pair from the index.
     *
     * @param pair
     *            the pair to drop
     * @param reach
     *            the reach the pair was indexed with
     */
    public static void remove(final RingPair pair, final int reach)
    {
        if (pair == null)
        {
            return;
        }
        removeEnd(pair, pair.getEndA(), reach);
        removeEnd(pair, pair.getEndB(), reach);
    }

    /**
     * Removes one end of a pair from the index.
     *
     * @param pair
     *            the pair the end belongs to
     * @param ring
     *            the end to drop
     * @param reach
     *            the reach the end was indexed with
     */
    private static void removeEnd(final RingPair pair, final Ring ring, final int reach)
    {
        final String world = pair.getWorldName();
        final ConcurrentMap<Long, RingEnd> volume = volumes.get(world);
        if (volume != null)
        {
            for (final int[] block : ring.triggerVolumeBlocks(volumeDepth(ring, reach)))
            {
                volume.remove(Long.valueOf(pack(block[0], block[1], block[2])));
            }
            if (volume.isEmpty())
            {
                volumes.remove(world);
            }
        }
        final ConcurrentMap<Long, RingEnd> edge = perimeters.get(world);
        if (edge != null)
        {
            for (final int[] block : ring.perimeterBlocks())
            {
                edge.remove(Long.valueOf(pack(block[0], block[1], block[2])));
            }
            if (edge.isEmpty())
            {
                perimeters.remove(world);
            }
        }
    }

    /**
     * How deep to index one ring's trigger volume.
     *
     * <p>A floor ring holds its passengers just above itself. A ceiling ring's stand on the
     * floor, which can be most of a room below the plane, so it has to be indexed all the
     * way down to the furthest floor it could ever reach — otherwise somebody standing in
     * exactly the right place would never set it off.
     *
     * <p>Read from config here rather than passed in, because the index is built once at
     * load and the depth is a property of the ring rather than of the caller.
     *
     * @param ring
     *            the ring being indexed
     * @param reach
     *            how deep a floor ring's volume runs
     * @return the number of block layers to index
     */
    private static int volumeDepth(final Ring ring, final int reach)
    {
        int maxDrop = Ring.MIN_CEILING_DROP;
        try
        {
            maxDrop = com.wormhole_xtreme.wormhole.config.ConfigManager.getRingMaxCeilingDrop();
        }
        // No config loaded, which happens in tests. The minimum still indexes a working ring.
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
        return ring.volumeDepth(reach, maxDrop);
    }

    /**
     * The ring whose trigger volume contains this block, if any.
     *
     * <p>This is the move path. Only the interior is indexed here, because only the interior
     * arms a cycle — standing on a ring's edge is crossing a threshold, and letting that
     * fire would take people who were walking past.
     *
     * @param worldName
     *            the world the block is in
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @return the ring end, or null if the block is not in one
     */
    public static RingEnd volumeAt(final String worldName, final int x, final int y, final int z)
    {
        final Map<Long, RingEnd> volume = volumes.get(worldName);
        if (volume == null)
        {
            return null;
        }
        return volume.get(Long.valueOf(pack(x, y, z)));
    }

    /**
     * The ring whose perimeter occupies this block, if any.
     *
     * <p>Used to protect the ring from being built over and to find passengers standing on
     * the edge when a cycle commits, not to trigger anything.
     *
     * @param worldName
     *            the world the block is in
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @return the ring end, or null if the block is not perimeter
     */
    public static RingEnd perimeterAt(final String worldName, final int x, final int y, final int z)
    {
        final Map<Long, RingEnd> edge = perimeters.get(worldName);
        if (edge == null)
        {
            return null;
        }
        return edge.get(Long.valueOf(pack(x, y, z)));
    }

    /**
     * Drops every world's rings from the index.
     */
    public static void clear()
    {
        volumes.clear();
        perimeters.clear();
    }
}
