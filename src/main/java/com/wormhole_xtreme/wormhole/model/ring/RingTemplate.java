package com.wormhole_xtreme.wormhole.model.ring;

import org.bukkit.Material;

/**
 * Reads a ring out of the slabs a player laid on the ground.
 *
 * <p>Construction is a template, not a build: the player puts down a circle of slabs, runs
 * the command, and the slabs are consumed. Nothing is left behind, because a ring is
 * invisible until it fires.
 *
 * <p>The template is read for everything it can tell us, rather than only for its shape:
 *
 * <ul>
 * <li><b>Which pattern</b> — whichever of the two the slabs match.
 * <li><b>Where the anchor is</b> — the player can stand anywhere inside the ring, so every
 * interior square is tried as a candidate position for them.
 * <li><b>What the ring is made of</b> — the slab they used becomes the ring's material, so
 * a ring laid in deepslate rises in deepslate with no command needed. This is the same idea
 * as a gate taking its palette from the material its frame is built from.
 * <li><b>Which way it faces</b> — a slab laid on a floor is a bottom slab and a slab hung
 * under a ceiling is a top slab, so the halves say which surface this is without having to
 * guess from what is above or below.
 * </ul>
 *
 * <p>The detection itself is pure, and reaches the world only through {@link BlockProbe}.
 * That keeps every rule here testable without a running server, which matters because these
 * are the rules a player meets first and the ones most likely to need adjusting.
 */
public final class RingTemplate
{
    private RingTemplate() {}

    /** Which half of its block a slab fills. */
    public enum SlabHalf
    {
        /** Fills the lower half. A slab resting on a floor. */
        BOTTOM,

        /** Fills the upper half. A slab hung under a ceiling. */
        TOP
    }

    /**
     * How detection reads the world.
     *
     * <p>The only part of this class that needs a server, kept to two methods so a test can
     * supply a grid of blocks instead.
     */
    public interface BlockProbe
    {
        /**
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @return the material there, {@code AIR} when empty, never null
         */
        Material materialAt(int x, int y, int z);

        /**
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @return which half the slab there fills, or null if that block is not a slab
         */
        SlabHalf halfAt(int x, int y, int z);
    }

    /** Why a template was rejected, in terms a player can act on. */
    public enum Failure
    {
        /** No circle of slabs anywhere near the player. */
        NO_RING_FOUND,

        /** A ring is there, but built from more than one kind of slab. */
        MIXED_MATERIALS,

        /** A ring is there, but some slabs rest on the floor and others hang from a ceiling. */
        MIXED_HALVES,

        /** The circle is filled in. A ring is an outline, not a disc. */
        INTERIOR_NOT_CLEAR
    }

    /**
     * What detection found: either a ring, or the reason there was not one.
     */
    public static final class Result
    {
        /** The detected ring, or null when detection failed. */
        private final Ring ring;

        /** Why detection failed, or null when it succeeded. */
        private final Failure failure;

        /**
         * Instantiates a result.
         *
         * @param ring
         *            the detected ring, or null
         * @param failure
         *            the reason for failure, or null
         */
        private Result(final Ring ring, final Failure failure)
        {
            this.ring = ring;
            this.failure = failure;
        }

        /** @return true if a ring was found */
        public boolean isSuccess()
        {
            return ring != null;
        }

        /** @return the detected ring, or null */
        public Ring getRing()
        {
            return ring;
        }

        /** @return why detection failed, or null */
        public Failure getFailure()
        {
            return failure;
        }
    }

    /**
     * Looks for a ring of slabs around the player.
     *
     * <p>Searches the player's own block layer first and then upward, because a floor ring's
     * slabs sit where the player's feet are while a ceiling ring's hang some way above their
     * head. Within each layer, every interior square of every pattern is tried as the place
     * the player might be standing — a player can stand anywhere inside a ring, so the
     * anchor has to be searched for rather than assumed.
     *
     * <p>The most specific failure wins. Finding a ring built from two kinds of slab is much
     * more useful to say than "no ring found", which is what a strictly first-match search
     * would have reported after silently rejecting it.
     *
     * @param probe
     *            how to read the world
     * @param playerX
     *            block x the player is standing in
     * @param playerY
     *            block y the player is standing in
     * @param playerZ
     *            block z the player is standing in
     * @param searchHeight
     *            how many block layers up to look for a ceiling ring
     * @param lightMaterial
     *            what the countdown lights should be, which the template cannot say
     * @return the ring, or why there was not one
     */
    public static Result detect(final BlockProbe probe, final int playerX, final int playerY,
        final int playerZ, final int searchHeight, final Material lightMaterial)
    {
        Failure best = Failure.NO_RING_FOUND;
        for (int y = playerY; y < (playerY + Math.max(searchHeight, 1)); y++)
        {
            for (final RingPattern pattern : RingPattern.values())
            {
                for (final RingPattern.Offset standing : pattern.getInterior())
                {
                    // If the player is standing on this interior square, the anchor is that
                    // far back the other way.
                    final int anchorX = playerX - standing.getDx();
                    final int anchorZ = playerZ - standing.getDz();
                    final Result result = tryAnchor(probe, pattern, anchorX, y, anchorZ, lightMaterial);
                    if (result.isSuccess())
                    {
                        return result;
                    }
                    best = moreSpecific(best, result.getFailure());
                }
            }
        }
        return new Result(null, best);
    }

    /**
     * Tests one exact position for one exact pattern.
     *
     * @param probe
     *            how to read the world
     * @param pattern
     *            the pattern to test for
     * @param anchorX
     *            candidate anchor x
     * @param anchorY
     *            candidate ring plane
     * @param anchorZ
     *            candidate anchor z
     * @param lightMaterial
     *            what the countdown lights should be
     * @return the ring, or why this position is not one
     */
    private static Result tryAnchor(final BlockProbe probe, final RingPattern pattern,
        final int anchorX, final int anchorY, final int anchorZ, final Material lightMaterial)
    {
        Material material = null;
        SlabHalf half = null;
        for (final RingPattern.Offset offset : pattern.getPerimeter())
        {
            final int x = anchorX + offset.getDx();
            final int z = anchorZ + offset.getDz();
            final SlabHalf found = probe.halfAt(x, anchorY, z);
            if (found == null)
            {
                return new Result(null, Failure.NO_RING_FOUND);
            }
            final Material type = probe.materialAt(x, anchorY, z);
            if (material == null)
            {
                material = type;
                half = found;
                continue;
            }
            if (material != type)
            {
                return new Result(null, Failure.MIXED_MATERIALS);
            }
            if (half != found)
            {
                return new Result(null, Failure.MIXED_HALVES);
            }
        }
        if (material == null)
        {
            return new Result(null, Failure.NO_RING_FOUND);
        }

        // A ring is an outline. Someone who filled the circle in has built something this
        // cannot animate, and saying so beats quietly treating the inner slabs as floor.
        for (final RingPattern.Offset offset : pattern.getInterior())
        {
            if (probe.materialAt(anchorX + offset.getDx(), anchorY, anchorZ + offset.getDz()) == material)
            {
                return new Result(null, Failure.INTERIOR_NOT_CLEAR);
            }
        }

        final RingOrientation orientation = (half == SlabHalf.BOTTOM)
            ? RingOrientation.FLOOR
            : RingOrientation.CEILING;
        return new Result(new Ring(anchorX, anchorY, anchorZ, pattern, orientation, material,
            lightMaterial), null);
    }

    /**
     * Keeps whichever failure tells the player more.
     *
     * <p>"No ring found" is what every wrong position reports, so anything else is a real
     * observation about something the player actually built and should survive to be the
     * message they see.
     *
     * @param current
     *            the best failure so far
     * @param candidate
     *            a new failure
     * @return whichever is more informative
     */
    private static Failure moreSpecific(final Failure current, final Failure candidate)
    {
        if ((candidate == null) || (candidate == Failure.NO_RING_FOUND))
        {
            return current;
        }
        return candidate;
    }
}
