/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   The registry of ring pairs, and the rules about where one may be built.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Holds every ring pair on the server, and decides where a new one may go.
 *
 * <p>Two collections and one set of rules. Pairs are complete links; pendings are the first
 * half of a link whose owner has not built the second half yet, held exactly the way
 * {@code StargateManager} holds a gate between its button press and its {@code complete}.
 *
 * <p>The rules are the interesting part, and they are not about how many rings exist.
 * Density is free: the index is keyed by block, so a hundred rings in a chunk cost the same
 * to look up as one. What is not free is two rings sharing ground, because then a player
 * stands in two trigger volumes at once and two animations write and restore the same
 * blocks. So overlap is refused outright and separation is enforced on top of it.
 */
public final class RingManager
{
    /** Every complete pair, by id. */
    private static final ConcurrentMap<String, RingPair> pairs = new ConcurrentHashMap<String, RingPair>();

    /** First halves waiting for their second, by owner UUID. */
    private static final ConcurrentMap<UUID, PendingRing> pending = new ConcurrentHashMap<UUID, PendingRing>();

    private RingManager() {}

    /**
     * The first end of a pair, waiting for its partner to be built.
     *
     * <p>Carries its world because the second end has to be in the same one, and that is
     * worth refusing at the command with a message rather than discovering later.
     */
    public static final class PendingRing
    {
        /** The end already built. */
        private final Ring ring;

        /** The world it is in. */
        private final String worldName;

        /**
         * Instantiates a pending ring.
         *
         * @param ring
         *            the end already built
         * @param worldName
         *            the world it is in
         */
        public PendingRing(final Ring ring, final String worldName)
        {
            this.ring = ring;
            this.worldName = worldName;
        }

        /** @return the end already built */
        public Ring getRing()
        {
            return ring;
        }

        /** @return the world it is in */
        public String getWorldName()
        {
            return worldName;
        }
    }

    /** Why a ring may not be built where it was asked for. */
    public enum Refusal
    {
        /** The footprint touches another ring's. */
        OVERLAPS_RING,

        /** Far enough not to overlap, but closer than the configured separation. */
        TOO_CLOSE,

        /** The footprint touches a stargate's blocks. */
        OVERLAPS_GATE,

        /** The player already has as many pairs as they are allowed. */
        QUOTA_REACHED,

        /** The second end is in a different world from the first. */
        DIFFERENT_WORLD,

        /** The two ends are further apart than the configured maximum. */
        TOO_FAR
    }

    /**
     * Registers a complete pair and indexes both of its ends.
     *
     * @param pair
     *            the pair to add
     * @param reach
     *            how deep each trigger volume runs
     */
    public static void addPair(final RingPair pair, final int reach)
    {
        if (pair == null)
        {
            return;
        }
        pairs.put(pair.getId(), pair);
        RingIndex.add(pair, reach);
    }

    /**
     * Removes a pair and takes both of its ends out of the index.
     *
     * @param pair
     *            the pair to remove
     * @param reach
     *            the reach it was indexed with
     */
    public static void removePair(final RingPair pair, final int reach)
    {
        if (pair == null)
        {
            return;
        }
        pairs.remove(pair.getId());
        RingIndex.remove(pair, reach);
    }

    /**
     * Gets a pair by id.
     *
     * @param id
     *            the pair id
     * @return the pair, or null
     */
    public static RingPair getPair(final String id)
    {
        return id == null ? null : pairs.get(id);
    }

    /** @return every pair on the server */
    public static Collection<RingPair> getAllPairs()
    {
        return Collections.unmodifiableCollection(pairs.values());
    }

    /**
     * Every pair in one world.
     *
     * @param worldName
     *            the world
     * @return the pairs in it
     */
    public static List<RingPair> getPairsInWorld(final String worldName)
    {
        final List<RingPair> out = new ArrayList<RingPair>();
        for (final RingPair pair : pairs.values())
        {
            if (pair.getWorldName().equals(worldName))
            {
                out.add(pair);
            }
        }
        return out;
    }

    /**
     * How many pairs a player owns.
     *
     * @param uuid
     *            the player's UUID string
     * @return the count
     */
    public static int countPairsOwnedBy(final String uuid)
    {
        int count = 0;
        for (final RingPair pair : pairs.values())
        {
            if (pair.isOwnedBy(uuid))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Drops every pair and pending, and empties the index.
     */
    public static void clear()
    {
        pairs.clear();
        pending.clear();
        RingIndex.clear();
    }

    /**
     * Stashes the first end of a pair against its builder.
     *
     * @param owner
     *            the builder
     * @param ring
     *            the end they just built
     * @param worldName
     *            the world it is in
     */
    public static void setPending(final UUID owner, final Ring ring, final String worldName)
    {
        pending.put(owner, new PendingRing(ring, worldName));
    }

    /**
     * The first end this player is part way through pairing, if any.
     *
     * @param owner
     *            the builder
     * @return their pending end, or null
     */
    public static PendingRing getPending(final UUID owner)
    {
        return owner == null ? null : pending.get(owner);
    }

    /**
     * Forgets a player's pending end.
     *
     * @param owner
     *            the builder
     * @return the end that was forgotten, or null
     */
    public static PendingRing clearPending(final UUID owner)
    {
        return owner == null ? null : pending.remove(owner);
    }

    /** @return every pending end, by owner */
    public static java.util.Map<UUID, PendingRing> getAllPending()
    {
        return Collections.unmodifiableMap(pending);
    }

    /**
     * Whether a ring may be built here.
     *
     * <p>Overlap is checked column by column and ignores height on purpose. Two rings at
     * different heights in the same columns are still two rings whose animations write the
     * same blocks and whose restore maps will undo each other, and a player standing between
     * them is in both trigger volumes at once. Separation is then a comfort margin on top of
     * a rule that is already absolute.
     *
     * @param candidate
     *            the ring somebody wants to build
     * @param worldName
     *            the world it would be in
     * @param minSeparation
     *            required distance between anchors, in blocks
     * @return the reason it may not be built, or null if it may
     */
    public static Refusal checkPlacement(final Ring candidate, final String worldName,
        final int minSeparation)
    {
        final long minimumSquared = (long) minSeparation * minSeparation;
        for (final RingPair pair : getPairsInWorld(worldName))
        {
            final Refusal a = checkAgainst(candidate, pair.getEndA(), minimumSquared);
            if (a != null)
            {
                return a;
            }
            final Refusal b = checkAgainst(candidate, pair.getEndB(), minimumSquared);
            if (b != null)
            {
                return b;
            }
        }
        return null;
    }

    /**
     * Tests a candidate ring against one existing ring.
     *
     * @param candidate
     *            the ring somebody wants to build
     * @param existing
     *            a ring that is already there
     * @param minimumSquared
     *            required squared distance between anchors
     * @return the reason it may not be built, or null if this one does not object
     */
    private static Refusal checkAgainst(final Ring candidate, final Ring existing,
        final long minimumSquared)
    {
        for (final int[] block : candidate.perimeterBlocks())
        {
            if (existing.coversColumn(block[0], block[2]))
            {
                return Refusal.OVERLAPS_RING;
            }
        }
        for (final int[] block : candidate.interiorBlocks())
        {
            if (existing.coversColumn(block[0], block[2]))
            {
                return Refusal.OVERLAPS_RING;
            }
        }
        if (candidate.anchorDistanceSquared(existing) < minimumSquared)
        {
            return Refusal.TOO_CLOSE;
        }
        return null;
    }

    /**
     * A short id that no existing pair is using.
     *
     * <p>Eight hex characters, which is short enough to type into a command and wide enough
     * that a collision needs tens of thousands of pairs before it is worth thinking about.
     * The loop makes it certain rather than merely likely.
     *
     * @return the new id
     */
    public static String newId()
    {
        String id;
        do
        {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        while (pairs.containsKey(id));
        return id;
    }
}
