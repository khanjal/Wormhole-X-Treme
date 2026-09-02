/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Where every travelling ring is on every frame.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out where the travelling rings are on a given frame.
 *
 * <p>Pure geometry and no blocks: given a ring and a frame number it says which slabs should
 * exist and which half of their block each fills. Applying that to the world, remembering
 * what was there first and putting it back is {@link RingCycle}'s job. Keeping the two apart
 * means the part with the arithmetic can be checked exactly, and the part that touches the
 * world has no arithmetic left in it.
 *
 * <p><b>Half-block movement.</b> Blocks only exist at integer positions, so a ring cannot
 * rise half a block by moving. It can by changing which half of its block it fills: a bottom
 * slab occupies the lower half and a top slab the upper half, so alternating them steps half
 * a block at a time. A half-step {@code h} is therefore {@code h / 2} blocks along, filling
 * the far half on even steps and the near half on odd ones. This is the whole reason the
 * ring material is required to be a slab.
 *
 * <p><b>The stack.</b> Four rings end up one block of clear space apart, which is
 * {@link #SPACING} half-steps. {@link RingStyle} decides how they get there: all at once,
 * each a gap behind the last, or strictly one at a time with each waiting for the one before
 * it to stop. The two differ only in when a ring leaves the plane — where they end up, how
 * far each travels and how they come home are the same, which is why one number tells them
 * apart rather than there being two animations.
 *
 * <p><b>Retract is the same frames played backwards</b>, and that is not just an economy.
 * Reversing a sequence that went furthest-first automatically returns them nearest-first:
 * the last ring to emerge is the first to sink away, and the one that flew highest is the
 * last to come home. That is the order these move in, and it falls out of the reversal
 * rather than needing a second sequence that could disagree with the first.
 *
 * <p><b>Direction.</b> A floor ring rises and a ceiling ring descends, and the two also
 * start from opposite halves of their own block — a floor ring's first slab is a bottom slab
 * resting where the template was, a ceiling ring's is a top slab hanging there. Both fall out
 * of {@link RingOrientation#getTravel()} and the orientation itself, so neither direction is
 * a special case.
 */
public final class RingAnimator
{
    /** Half-steps between adjacent rings in the finished stack: one block of clear space. */
    public static final int SPACING = 3;

    /** How many rings travel. */
    public static final int RING_COUNT = 4;

    /** Half-steps the leading ring travels: the top of the finished stack. */
    public static final int TOP_HALF_STEP = (RING_COUNT - 1) * SPACING;

    private RingAnimator() {}

    /**
     * Where a given ring comes to rest.
     *
     * <p>The first one out goes furthest, and each one after it stops a gap lower, so the
     * last one never leaves the plane at all — it is already where it belongs.
     *
     * @param index
     *            which ring, counting from the first one out
     * @return its resting half-step
     */
    static int restingHalfStep(final int index)
    {
        return (RING_COUNT - 1 - index) * SPACING;
    }

    /**
     * The frame a given ring emerges on.
     *
     * <p>Concurrently, a ring leaves one gap behind the one in front and they all arrive
     * together. Sequentially, it waits for the one before it to finish travelling and stop,
     * which makes the whole run as long as the sum of the journeys rather than the longest
     * of them.
     *
     * @param style
     *            how the stack comes out
     * @param index
     *            which ring, counting from the first one out
     * @return the frame it first appears on
     */
    static int emergesOnFrame(final RingStyle style, final int index)
    {
        if (style == RingStyle.CONCURRENT)
        {
            // Everyone is already on their way; each ring simply leaves one gap behind the
            // one in front, so the whole stack arrives together.
            return index * SPACING;
        }
        int frame = 0;
        for (int earlier = 0; earlier < index; earlier++)
        {
            // Its whole journey, plus the frame it spent sitting at the plane before moving.
            frame += restingHalfStep(earlier) + 1;
        }
        return frame;
    }

    /**
     * One slab of one travelling ring on one frame.
     */
    public static final class Placement
    {
        /** Block x. */
        private final int x;

        /** Block y. */
        private final int y;

        /** Block z. */
        private final int z;

        /** True when the slab fills the upper half of its block. */
        private final boolean top;

        /**
         * Instantiates a placement.
         *
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @param top
         *            true when the slab fills the upper half
         */
        Placement(final int x, final int y, final int z, final boolean top)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.top = top;
        }

        /** @return block x */
        public int getX()
        {
            return x;
        }

        /** @return block y */
        public int getY()
        {
            return y;
        }

        /** @return block z */
        public int getZ()
        {
            return z;
        }

        /** @return true when the slab fills the upper half of its block */
        public boolean isTop()
        {
            return top;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString()
        {
            return (top ? "top" : "bottom") + " slab at " + x + "," + y + "," + z;
        }
    }

    /**
     * How many frames a deploy takes.
     *
     * <p>However many it takes for the last ring to reach its place, which depends entirely
     * on the style: concurrently they overlap and the answer is the longest single journey,
     * sequentially they queue and it is the sum of all of them.
     *
     * @param style
     *            how the stack comes out
     * @return the number of deploy frames
     */
    public static int deployFrames(final RingStyle style)
    {
        int frames = 0;
        for (int index = 0; index < RING_COUNT; index++)
        {
            frames = Math.max(frames, emergesOnFrame(style, index) + restingHalfStep(index) + 1);
        }
        return frames;
    }

    /**
     * Every slab that should exist on a given deploy frame.
     *
     * <p>Frame zero is the first ring emerging at the plane; the last frame is the finished
     * stack. Rings that have not emerged yet contribute nothing, and rings that have reached
     * their place in the stack stay there rather than continuing upward.
     *
     * @param ring
     *            the ring deploying
     * @param style
     *            how the stack comes out
     * @param frame
     *            which frame, from zero
     * @return where every travelling slab is on that frame
     */
    public static List<Placement> deployFrame(final Ring ring, final RingStyle style, final int frame)
    {
        final List<Placement> out = new ArrayList<Placement>();
        for (int index = 0; index < RING_COUNT; index++)
        {
            final int travelled = frame - emergesOnFrame(style, index);
            if (travelled < 0)
            {
                // Still below the floor: the ring before it has not finished yet.
                continue;
            }
            // Once it reaches its place it holds there while the rest come out behind it.
            addRing(out, ring, Math.min(travelled, restingHalfStep(index)));
        }
        return out;
    }

    /**
     * Every slab that should exist on a given retract frame.
     *
     * <p>Deploy played backwards, which gives the right order for free: because the rings
     * went out furthest-first, reversing brings them home nearest-first — the last one to
     * emerge sinks away immediately and the one that flew highest is the last to leave.
     * Saying it this way rather than writing a second sequence also means the two can never
     * disagree and strand a slab on the way down.
     *
     * @param ring
     *            the ring retracting
     * @param style
     *            how the stack came out
     * @param frame
     *            which frame, from zero
     * @return where every travelling slab is on that frame
     */
    public static List<Placement> retractFrame(final Ring ring, final RingStyle style, final int frame)
    {
        return deployFrame(ring, style, (deployFrames(style) - 1) - frame);
    }

    /**
     * Adds one complete ring at one half-step.
     *
     * @param out
     *            collects the placements
     * @param ring
     *            the ring being animated
     * @param halfStep
     *            how far along it is, in half blocks
     */
    private static void addRing(final List<Placement> out, final Ring ring, final int halfStep)
    {
        final int y = ring.getAnchorY() + (ring.getOrientation().getTravel() * (halfStep / 2));
        final boolean top = fillsUpperHalf(ring.getOrientation(), halfStep);
        for (final RingPattern.Offset offset : ring.getPattern().getPerimeter())
        {
            out.add(new Placement(ring.getAnchorX() + offset.getDx(), y,
                ring.getAnchorZ() + offset.getDz(), top));
        }
    }

    /**
     * Which half of its block a ring fills at a given half-step.
     *
     * <p>On even half-steps a ring sits in the half of its block furthest along its own
     * direction of travel: the bottom half for a floor ring, which is where a slab laid on a
     * floor rests, and the top half for a ceiling ring, which is where one hung from a
     * ceiling hangs. Odd half-steps are the other half of the same block, which is what puts
     * the ring half a block further on without moving it.
     *
     * @param orientation
     *            which way the ring travels
     * @param halfStep
     *            how far along it is, in half blocks
     * @return true if the slab fills the upper half of its block
     */
    static boolean fillsUpperHalf(final RingOrientation orientation, final int halfStep)
    {
        final boolean even = (halfStep % 2) == 0;
        return even
            ? (orientation == RingOrientation.CEILING)
            : (orientation == RingOrientation.FLOOR);
    }

    /**
     * The blocks the countdown lights occupy.
     *
     * <p>The ring's own perimeter, in its own plane — the pattern lighting up where the ring
     * will come from, before anything has moved. Nothing travels during the countdown, which
     * is what makes it the only phase that can be called off cleanly.
     *
     * @param ring
     *            the ring counting down
     * @return the light block positions, each as {@code {x, y, z}}
     */
    public static List<int[]> lightBlocks(final Ring ring)
    {
        return ring.perimeterBlocks();
    }
}
