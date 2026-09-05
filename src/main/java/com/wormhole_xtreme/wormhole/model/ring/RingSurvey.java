package com.wormhole_xtreme.wormhole.model.ring;

/**
 * Decides whether a ring is fit to receive anybody.
 *
 * <p>Pure, and reaches the world only through {@link Ground} — the same split
 * {@link RingTemplate} uses, and for the same reason. What counts as a working ring is a set
 * of rules worth arguing about and worth testing; reading a block is neither.
 * {@link BukkitRingWorld} supplies the blocks and keeps none of the rules.
 *
 * <p>Every rule here is judged over the whole interior rather than by hunting for one clear
 * square. Somewhere to stand is not the same as somewhere fit to arrive: a ring with one
 * block dropped in it would still have twenty free columns, and delivering people to
 * whichever corner happened to be empty is not what a transport ring should do.
 *
 * <p><b>Room to stand and room to work are different questions.</b> A traveller is two blocks
 * tall and a finished stack is {@link Ring#STACK_HEIGHT}, so a room can have space for
 * somebody and no space at all for the rings that are supposed to come up around them. Only
 * the first was ever checked, which is why a ring in a two-block crawlspace would count
 * down, fire, and put people through a transport whose rings were mostly inside the ceiling.
 * Both are checked now, and they are told apart in the answer, because "your ceiling is too
 * low" and "somebody has built in your ring" send a player to different places.
 */
public final class RingSurvey
{
    /**
     * How many layers up from the stack base a traveller occupies.
     *
     * <p>Feet and head. Anything in these is something built where a person would arrive,
     * which is a different complaint from a ceiling that is merely too close.
     */
    private static final int TRAVELLER_HEIGHT = 2;

    /**
     * Reads whether the world lets something through at a given block.
     *
     * <p>Three methods and no decisions, deliberately: everything a survey concludes is
     * above, where it can be checked against a map of blocks instead of a server.
     */
    public interface Ground
    {
        /**
         * Whether a block can be passed through.
         *
         * <p>Water and lava are passable, and that is the right answer for both questions
         * asked of this — they are not something built in a ring, and they are not a floor.
         *
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @return true if nothing solid is there
         */
        boolean isPassable(int x, int y, int z);

        /** @return the lowest block layer this world has */
        int minHeight();

        /** @return one past the highest block layer this world has */
        int maxHeight();
    }

    private RingSurvey() {}

    /**
     * Measures a ring against the world, and reports anything wrong with it.
     *
     * <p>Does two things at once, and says so rather than pretending to be a pure question.
     * It finds how far a ceiling ring's floor is and records it on the ring — which is what
     * lets the stack form on the ground rather than under the ceiling — and it reports
     * whether the ring is fit to receive anybody at all. Both need the same walk through the
     * same blocks, and the answer to the second depends on the first.
     *
     * @param ground
     *            how to read the world
     * @param ring
     *            the ring to measure
     * @param maxCeilingDrop
     *            how far below its plane a ceiling ring will look for the floor
     * @return why it cannot take anybody, or null if it can
     */
    public static RingBlockage survey(final Ground ground, final Ring ring,
        final int maxCeilingDrop)
    {
        if (ring.getOrientation() == RingOrientation.CEILING)
        {
            final int found = floorBelow(ground, ring, maxCeilingDrop);
            if (found < 0)
            {
                return RingBlockage.CEILING_TOO_HIGH;
            }
            if (found < Ring.MIN_CEILING_DROP)
            {
                return RingBlockage.CEILING_TOO_LOW;
            }
            // Recorded on the ring so the stack forms on this floor rather than under the
            // ceiling. Everything downstream measures from it.
            ring.setDrop(found);
        }
        final int base = ring.stackBase();
        if ((base - 1) < ground.minHeight())
        {
            return RingBlockage.NO_GROUND;
        }
        if ((base + Ring.STACK_HEIGHT - 1) >= ground.maxHeight())
        {
            return RingBlockage.NO_HEADROOM;
        }
        for (final int[] block : ring.interiorBlocks())
        {
            final RingBlockage wrong = surveyColumn(ground, ring, block[0], block[2], base);
            if (wrong != null)
            {
                return wrong;
            }
        }
        return null;
    }

    /**
     * Everything one interior column has to be.
     *
     * @param ground
     *            how to read the world
     * @param ring
     *            the ring this column belongs to
     * @param x
     *            the column's x
     * @param z
     *            the column's z
     * @param base
     *            the layer the stack is built up from
     * @return what is wrong with this column, or null if nothing is
     */
    private static RingBlockage surveyColumn(final Ground ground, final Ring ring, final int x,
        final int z, final int base)
    {
        for (int up = 0; up < Ring.STACK_HEIGHT; up++)
        {
            final int y = base + up;
            // The ring's own plane is the ring, and a ceiling ring's is the ceiling it was
            // cut into. Demanding that be clear would refuse every ring set flush into a
            // four-block room -- exactly the rooms these are for. It only ever falls inside
            // the stack for a ceiling ring at its shallowest, where the top ring rests
            // against the ceiling with nothing above it to be in the way. A floor ring's
            // plane is its base, which the traveller check below covers instead.
            if ((y == ring.getAnchorY()) && (ring.getOrientation() == RingOrientation.CEILING))
            {
                continue;
            }
            if (ground.isPassable(x, y, z))
            {
                continue;
            }
            // Told apart by what the layer is for. The first two are where a person arrives,
            // so something in them is something built in the ring; above that it is the
            // stack's own room, and something in it means the ceiling is too low rather than
            // that anyone did anything wrong.
            return (up < TRAVELLER_HEIGHT) ? RingBlockage.OBSTRUCTED : RingBlockage.NO_HEADROOM;
        }
        // Ground directly under every column, so nobody arrives over a hole somebody dug.
        // Directly, not somewhere below: a gap with a floor three blocks further down is
        // still a gap to fall through, and a ring you drop out of is not a ring that works.
        // Water and lava are passable and count as no ground too, which is the right answer
        // -- landing in either is not arriving.
        return ground.isPassable(x, base - 1, z) ? RingBlockage.NO_GROUND : null;
    }

    /**
     * How far below a ceiling ring's plane the floor is.
     *
     * <p>Searched down the middle of the ring, out to the configured limit. Beyond that a
     * ceiling ring is over a shaft rather than a room, and rings that fall out of sight are
     * not a transport.
     *
     * @param ground
     *            how to read the world
     * @param ring
     *            the ceiling ring
     * @param limit
     *            how far down to look
     * @return the drop in blocks, or -1 if there is no floor within reach
     */
    private static int floorBelow(final Ground ground, final Ring ring, final int limit)
    {
        for (int down = 1; down <= (limit + 1); down++)
        {
            final int y = ring.getAnchorY() - down;
            if (y < ground.minHeight())
            {
                return -1;
            }
            if (!ground.isPassable(ring.getAnchorX(), y, ring.getAnchorZ()))
            {
                // Feet go on top of it, so the drop is to the layer above the solid block.
                return down - 1;
            }
        }
        return -1;
    }
}
