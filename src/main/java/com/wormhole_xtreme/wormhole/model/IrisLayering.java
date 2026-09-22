package com.wormhole_xtreme.wormhole.model;

import java.util.function.Predicate;

import org.bukkit.block.BlockFace;

/**
 * Where a shut iris and the horizon behind it go, for one viewer.
 *
 * <p>Pure arithmetic over block coordinates, deliberately separate from anything that draws --
 * the same split {@link IrisSweep} makes. A real gate draws its layers as block changes over
 * air; a preview draws its iris as block displays, which cannot show liquid, and its wormhole
 * as block changes. The decision is the same for both, so it lives here and each draws it its
 * own way.
 *
 * <p>An opening is one block thick, so the iris and the horizon cannot both be in the ring. The
 * one nearer the viewer takes the ring and the other goes a block further off. Keeping the far
 * layer on the far side is not only for looks: a drawn liquid on a viewer's own side of the
 * gate gives their client swim physics the server does not share.
 */
public final class IrisLayering
{
    /** A block position, as plain coordinates. */
    public record At(int x, int y, int z)
    {
        /**
         * This position moved one step along a facing.
         *
         * @param facing
         *            the direction
         * @param steps
         *            how far, which may be negative
         * @return the moved position
         */
        public At moved(final BlockFace facing, final int steps)
        {
            return new At(x + (steps * facing.getModX()), y + (steps * facing.getModY()),
                z + (steps * facing.getModZ()));
        }
    }

    /**
     * What one cell of the opening shows this viewer.
     *
     * @param iris
     *            where the iris goes, never null
     * @param horizon
     *            where the horizon goes, or null when there is no room for two layers and the
     *            iris has the ring to itself
     * @param handBack
     *            the layer position this viewer is not using, which is theirs to be given back
     *            in case they have just come round from the other side; null when the cell is
     *            not one a layer could be drawn in
     */
    public record Placement(At iris, At horizon, At handBack)
    {
    }

    /** Static helpers only. */
    private IrisLayering()
    {
    }

    /**
     * Whether an eye at this position is in front of the opening, the side the facing points to.
     *
     * <p>Measured from the middle of a ring cell. Every shipped opening is one block thick, so
     * any of its cells gives the same answer. Standing in the plane itself counts as the front,
     * the view a gate has always had.
     *
     * @param facing
     *            the gate's facing
     * @param ringCell
     *            any cell of the opening
     * @param eyeX
     *            the viewer's x
     * @param eyeY
     *            the viewer's y
     * @param eyeZ
     *            the viewer's z
     * @return true in front of the opening or in its plane, false behind it
     */
    public static boolean seesFront(final BlockFace facing, final At ringCell,
        final double eyeX, final double eyeY, final double eyeZ)
    {
        if ((facing == null) || (ringCell == null))
        {
            return true;
        }
        final double along = ((eyeX - (ringCell.x() + 0.5)) * facing.getModX())
            + ((eyeY - (ringCell.y() + 0.5)) * facing.getModY())
            + ((eyeZ - (ringCell.z() + 0.5)) * facing.getModZ());
        return along >= 0;
    }

    /**
     * Where the two layers go for one cell of the opening.
     *
     * @param ringCell
     *            the opening cell
     * @param facing
     *            the gate's facing
     * @param front
     *            whether the viewer is in front, from {@link #seesFront}
     * @param free
     *            whether a position is free to be drawn in: only real air, so that what
     *            somebody has built either side of a gate is what they go on seeing
     * @return where this viewer's iris and horizon go
     */
    public static Placement place(final At ringCell, final BlockFace facing, final boolean front,
        final Predicate<At> free)
    {
        final At behind = ringCell.moved(facing, -1);
        final At ahead = ringCell.moved(facing, 1);
        final At far = front ? behind : ahead;
        final At near = front ? ahead : behind;
        if (front)
        {
            // The iris in the ring and the horizon behind it, which is how a gate has always
            // looked from the front.
            return new Placement(ringCell, free.test(far) ? far : null, near);
        }
        if (free.test(far))
        {
            // From behind, the two swap: the horizon takes the ring and the iris goes beyond it.
            return new Placement(far, ringCell, near);
        }
        // No room beyond the ring, so there is no layering to be had. The iris keeps the ring;
        // drawing the horizon there instead would show a shut gate as an open one.
        return new Placement(ringCell, null, near);
    }
}
