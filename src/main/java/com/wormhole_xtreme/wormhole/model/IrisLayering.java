package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.List;
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
 *
 * <p>The far layer is only drawn while the gate itself stands between it and the eye. Two
 * layers a block apart read as one picture head on and as two slabs from the side, and a
 * two-dimensional gate is a single sheet of blocks with nothing to hide the second one -- which
 * is what "the water is floating beside the gate" was. Seen from far enough round, a gate goes
 * back to the one layer it can tell the truth with, and it goes back all at once: see
 * {@link #hidesFarLayers}.
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

        /** The middle of this block, where a sight line is measured to. */
        private double middle(final int axis)
        {
            return 0.5 + switch (axis)
            {
                case 0 -> x;
                case 1 -> y;
                default -> z;
            };
        }
    }

    /**
     * A viewer's eye, in world coordinates.
     *
     * @param x
     *            the eye's x
     * @param y
     *            the eye's y
     * @param z
     *            the eye's z
     */
    public record Eye(double x, double y, double z)
    {
        /** This eye's coordinate on one axis. */
        private double on(final int axis)
        {
            return switch (axis)
            {
                case 0 -> x;
                case 1 -> y;
                default -> z;
            };
        }
    }

    /**
     * What one cell of the opening shows this viewer.
     *
     * @param iris
     *            where the iris goes, or null when there is no second layer to be had and the
     *            horizon has the ring to itself
     * @param horizon
     *            where the horizon goes, or null when there is no second layer to be had and
     *            the iris has the ring to itself
     */
    public record Placement(At iris, At horizon)
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
     * @param eye
     *            where the viewer is looking from
     * @param free
     *            whether a position is free to be drawn in: only real air, so that what
     *            somebody has built either side of a gate is what they go on seeing
     * @param stacked
     *            whether this gate is layered for this viewer at all, from
     *            {@link #hidesFarLayers}
     * @return where this viewer's iris and horizon go
     */
    public static Placement place(final At ringCell, final BlockFace facing, final Eye eye,
        final Predicate<At> free, final boolean stacked)
    {
        final boolean front = seesFront(facing, ringCell, eye.x(), eye.y(), eye.z());
        final At far = ringCell.moved(facing, front ? -1 : 1);
        final boolean layered = stacked && free.test(far);
        if (front)
        {
            // The iris in the ring and the horizon behind it, which is how a gate has always
            // looked from the front.
            return new Placement(ringCell, layered ? far : null);
        }
        // From behind the horizon keeps the ring either way. What belongs in the plane is drawn
        // first and the other layer follows when there is somewhere to put it -- so walking
        // along the back of a gate no longer swaps the wormhole out for a wall of bare iris the
        // moment the iris cannot go beyond the ring. A viewer who can see no iris is still
        // refused by it: the barrier is the gate's state, not its picture.
        return new Placement(layered ? far : null, ringCell);
    }

    /**
     * The layer positions a viewer is not being drawn in, which are theirs to be given back.
     *
     * <p>Everything a cell could have been drawn in that this viewer's placement did not use --
     * so somebody who has walked round a gate, or watched it stop layering, is never left
     * holding the picture they had a moment ago.
     *
     * @param ringCell
     *            the opening cell
     * @param facing
     *            the gate's facing
     * @param drawn
     *            the positions this viewer is being drawn in, nulls allowed and ignored
     * @return the positions to hand back, which may be empty
     */
    public static List<At> handBacks(final At ringCell, final BlockFace facing, final At... drawn)
    {
        final List<At> back = new ArrayList<>(3);
        for (final At candidate : List.of(ringCell, ringCell.moved(facing, 1), ringCell.moved(facing, -1)))
        {
            if (!isDrawn(candidate, drawn))
            {
                back.add(candidate);
            }
        }
        return back;
    }

    /** Whether one position is among those being drawn in. */
    private static boolean isDrawn(final At candidate, final At... drawn)
    {
        for (final At at : drawn)
        {
            if (candidate.equals(at))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a gate hides the far layer of every one of its cells from this eye.
     *
     * <p>All of them or none of them, on purpose. Asked cell by cell, a gate seen from an angle
     * came apart into a patchwork -- some cells with the two layers and some fallen back to
     * one, which from behind is a square of bare iris in the middle of the wormhole. A gate is
     * one picture, so it is one decision: the layers hold while the gate covers all of them,
     * and the moment any cell would be seen past it the whole gate goes back to a single layer.
     *
     * @param opening
     *            the opening's cells
     * @param facing
     *            the gate's facing
     * @param eye
     *            where the viewer is looking from
     * @param cover
     *            whether a position in the ring plane is part of the gate -- its opening or the
     *            blocks around it, both of which stand between a viewer and the far layer
     * @return true if every cell's far layer is hidden
     */
    public static boolean hidesFarLayers(final List<At> opening, final BlockFace facing,
        final Eye eye, final Predicate<At> cover)
    {
        if ((facing == null) || opening.isEmpty())
        {
            return false;
        }
        final boolean front = seesFront(facing, opening.get(0), eye.x(), eye.y(), eye.z());
        for (final At cell : opening)
        {
            if (!hidden(cell.moved(facing, front ? -1 : 1), cell, facing, eye, cover))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the gate stands between an eye and a layer a block off the ring plane.
     *
     * <p>The test is the sight line from the eye to the middle of that block: where it crosses
     * the ring plane, is it still on the gate? Head on it crosses the layer's own cell and the
     * answer is yes. Step round to the side and the crossing walks off the gate, which is the
     * moment the block would be seen hanging in the air beside it.
     *
     * <p>The blocks around the opening count as cover, not just the opening: they are what a
     * viewer standing off to one side is actually looking through the gate past, and counting
     * only the opening took the layers away from anyone not square in front of a big gate.
     *
     * @param layer
     *            the layer position, a block off the ring plane
     * @param ringCell
     *            the opening cell it belongs to
     * @param facing
     *            the gate's facing
     * @param eye
     *            where the viewer is looking from
     * @param cover
     *            whether a position in the ring plane is part of the gate
     * @return true if the gate hides it
     */
    private static boolean hidden(final At layer, final At ringCell, final BlockFace facing,
        final Eye eye, final Predicate<At> cover)
    {
        final At crossed = crossing(layer, ringCell, facing, eye);
        return (crossed != null) && cover.test(crossed);
    }

    /**
     * The ring-plane cell a sight line from the eye to a layer block passes through.
     *
     * @param layer
     *            the layer position, a block off the ring plane
     * @param ringCell
     *            the opening cell it belongs to, which fixes the plane
     * @param facing
     *            the gate's facing
     * @param eye
     *            where the viewer is looking from
     * @return the cell, or null when the eye is in the layer's own plane and the sight line
     *         never crosses
     */
    private static At crossing(final At layer, final At ringCell, final BlockFace facing,
        final Eye eye)
    {
        final double plane = along(facing, ringCell);
        final double depth = along(facing, layer) - along(facing, eye);
        if (depth == 0)
        {
            return null;
        }
        final double part = (plane - along(facing, eye)) / depth;
        if ((part < 0) || (part > 1))
        {
            return null;
        }
        // The facing's own axis is the ring's by definition -- the crossing is a point in that
        // plane -- so only the two across it are worked out.
        final int[] cell = {ringCell.x(), ringCell.y(), ringCell.z()};
        for (int axis = 0; axis < 3; axis++)
        {
            if (mod(facing, axis) == 0)
            {
                cell[axis] = (int) Math.floor(
                    eye.on(axis) + (part * (layer.middle(axis) - eye.on(axis))));
            }
        }
        return new At(cell[0], cell[1], cell[2]);
    }

    /** The facing's step on one axis. */
    private static int mod(final BlockFace facing, final int axis)
    {
        return switch (axis)
        {
            case 0 -> facing.getModX();
            case 1 -> facing.getModY();
            default -> facing.getModZ();
        };
    }

    /** How far along the facing the middle of a block lies. */
    private static double along(final BlockFace facing, final At at)
    {
        return (at.middle(0) * facing.getModX()) + (at.middle(1) * facing.getModY())
            + (at.middle(2) * facing.getModZ());
    }

    /** How far along the facing an eye lies. */
    private static double along(final BlockFace facing, final Eye eye)
    {
        return (eye.x() * facing.getModX()) + (eye.y() * facing.getModY())
            + (eye.z() * facing.getModZ());
    }
}
