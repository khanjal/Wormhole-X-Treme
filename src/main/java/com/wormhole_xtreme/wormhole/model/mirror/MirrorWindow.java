package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.List;

import org.bukkit.block.BlockFace;

/**
 * The shape of a window mirror: its opening, which blocks behind it a viewer could see through
 * it, and which block at the far side each of those shows.
 *
 * <p>The opening is the banner's own size, one wide and two tall, in the layer just behind it. A
 * banner hung on a wall hangs down, so the opening is in the wall and runs down from the banner's
 * row. A freestanding banner stands up, so the opening is in the air behind it and runs up.
 *
 * <p>Plain numbers only, so all of it is testable without a server. {@link MirrorWindows} is the
 * part that reads blocks and sends them.
 *
 * <p>Right through the opening is right at the far side, not left: this is a window onto where
 * the mirror goes, not a reflection of it.
 *
 * @param base
 *            the middle of the opening's bottom row
 * @param into
 *            one step through the opening, the way somebody looking in faces
 * @param far
 *            the block a traveller arrives in
 * @param ahead
 *            one step the way a traveller faces on arrival
 */
public record MirrorWindow(MirrorWindow.Spot base, MirrorWindow.Spot into, MirrorWindow.Spot far,
    MirrorWindow.Spot ahead)
{
    /** How wide the opening is, in blocks: the banner's own column. */
    static final int WIDTH = 1;

    /** How tall the opening is: the banner's cloth. */
    static final int HEIGHT = 2;

    /** The furthest a candidate may be from the opening along its face, however close the eye. */
    static final int WIDEST = 64;

    /** An eye nearer the face than this is treated as this far, so the cone stays finite. */
    private static final double NEAREST_EYE = 0.25;

    private static final int HALF = WIDTH / 2;

    /**
     * A block position, or a one-block step along the ground when {@code y} is zero.
     *
     * @param x
     *            x
     * @param y
     *            y
     * @param z
     *            z
     */
    public record Spot(int x, int y, int z)
    {
    }

    /** Says what a block in the opening's face lets a viewer see past it. */
    @FunctionalInterface
    public interface Face
    {
        /**
         * @param across
         *            the block's coordinate along the face: x for a face looking north or south,
         *            z for one looking east or west
         * @param y
         *            the block's y
         * @return true if a drawn block showing there cannot be seen anywhere it should not be:
         *         because it is this window's opening, or because it is solid
         */
        boolean clear(int across, int y);
    }

    /** Handed each block of the opening. */
    @FunctionalInterface
    public interface Opening
    {
        /**
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         */
        void at(int x, int y, int z);
    }

    /** Handed each block a viewer might see through the opening. */
    @FunctionalInterface
    public interface Candidate
    {
        /**
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @return false to stop the walk here
         */
        boolean at(int x, int y, int z);
    }

    /**
     * The window a banner makes, if it can make one.
     *
     * @param banner
     *            the banner block
     * @param facing
     *            which way the banner faces
     * @param standing
     *            true for a freestanding banner, which may face any of sixteen ways and is
     *            snapped to the nearest cardinal; false for one hung on a wall
     * @param destination
     *            where the mirror goes
     * @return the window, or null for a banner facing no usable way or a mirror going nowhere
     */
    public static MirrorWindow of(final MirrorBlock banner, final BlockFace facing,
        final boolean standing, final MirrorPoint destination)
    {
        if ((banner == null) || (destination == null) || (facing == null))
        {
            return null;
        }
        final BlockFace front = standing ? nearestCardinal(facing) : facing;
        if (!isCardinal(front))
        {
            return null;
        }
        final Spot into = new Spot(-front.getModX(), 0, -front.getModZ());
        final int bottom = standing ? banner.y() : (banner.y() - (HEIGHT - 1));
        return new MirrorWindow(
            new Spot(banner.x() + into.x(), bottom, banner.z() + into.z()),
            into,
            new Spot(floor(destination.x()), floor(destination.y()), floor(destination.z())),
            aheadOf(destination.yaw()));
    }

    /**
     * The cardinal nearest one of a freestanding banner's sixteen facings.
     *
     * <p>Exactly between two, it takes the north or south one, so the answer is always the same.
     *
     * @param facing
     *            the banner's facing
     * @return the nearest cardinal, or the facing itself if it has no horizontal direction
     */
    static BlockFace nearestCardinal(final BlockFace facing)
    {
        final int x = facing.getModX();
        final int z = facing.getModZ();
        if (Math.abs(x) > Math.abs(z))
        {
            return (x > 0) ? BlockFace.EAST : BlockFace.WEST;
        }
        if (z != 0)
        {
            return (z > 0) ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        return facing;
    }

    /**
     * The step a yaw faces, snapped to the nearest of the four cardinals.
     *
     * @param yaw
     *            Minecraft yaw: 0 is south, and it turns clockwise through west
     * @return one step that way
     */
    static Spot aheadOf(final float yaw)
    {
        return switch (Math.floorMod(Math.round(yaw / 90.0f), 4))
        {
            case 1 -> new Spot(-1, 0, 0);
            case 2 -> new Spot(0, 0, -1);
            case 3 -> new Spot(1, 0, 0);
            default -> new Spot(0, 0, 1);
        };
    }

    /**
     * Visits every block of the opening.
     *
     * @param opening
     *            handed each one
     */
    public void forEachOpening(final Opening opening)
    {
        final Spot right = rightOf(into);
        for (int across = -HALF; across < (WIDTH - HALF); across++)
        {
            for (int up = 0; up < HEIGHT; up++)
            {
                opening.at(base.x() + (across * right.x()), base.y() + up,
                    base.z() + (across * right.z()));
            }
        }
    }

    /**
     * Visits, a layer at a time from the opening outwards, every block an eye could see through
     * the opening, out to a depth.
     *
     * <p>Only the cone from the eye through the opening, which is what makes a deep view
     * affordable: the blocks walked grow with what can be seen, not with a box drawn around the
     * opening. Generous at the edges -- the exact test is {@link #projected} and what follows it.
     *
     * @param eyeX
     *            the eye, x
     * @param eyeY
     *            the eye, y
     * @param eyeZ
     *            the eye, z
     * @param depth
     *            how many layers behind the opening to walk
     * @param candidate
     *            handed each block, nearest layers first; returns false to stop
     */
    public void forEachCandidate(final double eyeX, final double eyeY, final double eyeZ,
        final int depth, final Candidate candidate)
    {
        final boolean alongX = into.x() != 0;
        final int sign = alongX ? into.x() : into.z();
        final double eyeAlong = alongX ? eyeX : eyeZ;
        final double reach = Math.max(NEAREST_EYE, Math.abs(face() - eyeAlong));
        final int baseAlong = alongX ? base.x() : base.z();
        final int middle = alongX ? base.z() : base.x();
        for (int layer = 1; layer <= depth; layer++)
        {
            final int along = baseAlong + (sign * layer);
            final double one = Math.abs(along - eyeAlong) / reach;
            final double other = Math.abs((along + 1) - eyeAlong) / reach;
            final double near = Math.min(one, other);
            final double farther = Math.max(one, other);
            final double[] span = {
                lowest(alongX ? eyeZ : eyeX, middle - HALF, middle + (WIDTH - HALF), near, farther),
                highest(alongX ? eyeZ : eyeX, middle - HALF, middle + (WIDTH - HALF), near, farther),
                lowest(eyeY, base.y(), base.y() + HEIGHT, near, farther),
                highest(eyeY, base.y(), base.y() + HEIGHT, near, farther) };
            if (!layer(alongX, along, middle, span, candidate))
            {
                return;
            }
        }
    }

    /** One layer of {@link #forEachCandidate}. @return false if the walk was stopped */
    private boolean layer(final boolean alongX, final int along, final int middle,
        final double[] span, final Candidate candidate)
    {
        final int acrossFrom = Math.max(middle - WIDEST, (int) Math.floor(span[0]));
        final int acrossTo = Math.min(middle + WIDEST, (int) Math.ceil(span[1]) - 1);
        final int yFrom = Math.max(base.y() - WIDEST, (int) Math.floor(span[2]));
        final int yTo = Math.min(base.y() + WIDEST, (int) Math.ceil(span[3]) - 1);
        for (int across = acrossFrom; across <= acrossTo; across++)
        {
            for (int y = yFrom; y <= yTo; y++)
            {
                if (!candidate.at(alongX ? along : across, y, alongX ? across : along))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The far-side block a block behind the opening shows.
     *
     * <p>The first layer behind the opening shows the arrival block's own layer, and the
     * opening's bottom row lines up with the arrival block's height -- so what shows through the
     * bottom of the opening is exactly where a traveller lands.
     *
     * @param x
     *            the block behind the opening, x
     * @param y
     *            the block behind the opening, y
     * @param z
     *            the block behind the opening, z
     * @return the block at the far side
     */
    public Spot farOf(final int x, final int y, final int z)
    {
        final Spot right = rightOf(into);
        final Spot farRight = rightOf(ahead);
        final int dx = x - base.x();
        final int dz = z - base.z();
        final int depth = (dx * into.x()) + (dz * into.z());
        final int across = (dx * right.x()) + (dz * right.z());
        return new Spot(far.x() + ((depth - 1) * ahead.x()) + (across * farRight.x()),
            far.y() + (y - base.y()),
            far.z() + ((depth - 1) * ahead.z()) + (across * farRight.z()));
    }

    /**
     * Whether somebody standing here is on the banner's side of the opening.
     *
     * @param x
     *            where they stand, x
     * @param z
     *            where they stand, z
     * @return true if they are past the opening's face on the banner's side
     */
    public boolean inFront(final double x, final double z)
    {
        // From the middle of the opening's layer, so past its face is more than half a block out.
        final double out = (((base.x() + 0.5) - x) * into.x()) + (((base.z() + 0.5) - z) * into.z());
        return out > 0.5;
    }

    /**
     * Whether a block is part of the opening.
     *
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @return true if it is
     */
    public boolean isOpening(final int x, final int y, final int z)
    {
        final int dx = x - base.x();
        final int dz = z - base.z();
        if ((((dx * into.x()) + (dz * into.z())) != 0) || (y < base.y())
            || (y >= (base.y() + HEIGHT)))
        {
            return false;
        }
        final Spot right = rightOf(into);
        final int across = (dx * right.x()) + (dz * right.z());
        return (across >= -HALF) && (across < (WIDTH - HALF));
    }

    /**
     * Where the opening's face on the banner's side is, along the axis it faces.
     *
     * @return the x coordinate of that face for an opening facing east or west, otherwise the z
     */
    int face()
    {
        return (into.x() != 0) ? (base.x() + ((into.x() > 0) ? 0 : 1))
            : (base.z() + ((into.z() > 0) ? 0 : 1));
    }

    /**
     * Whether another window opens in the same face.
     *
     * @param other
     *            the other window
     * @return true if a line of sight through one could instead pass through the other
     */
    boolean sharesFace(final MirrorWindow other)
    {
        return into.equals(other.into) && (face() == other.face());
    }

    /**
     * Where a block behind the opening appears on its face, seen from an eye in front of it.
     *
     * @param eyeX
     *            the eye, x
     * @param eyeY
     *            the eye, y
     * @param eyeZ
     *            the eye, z
     * @param x
     *            the block, x
     * @param y
     *            the block, y
     * @param z
     *            the block, z
     * @return {@code {acrossMin, acrossMax, yMin, yMax}} on the face, measured along it in world
     *         coordinates; or null if the block is not behind the face from that eye
     */
    double[] projected(final double eyeX, final double eyeY, final double eyeZ, final int x,
        final int y, final int z)
    {
        final boolean alongX = into.x() != 0;
        final double eyeDepth = alongX ? eyeX : eyeZ;
        final double eyeAcross = alongX ? eyeZ : eyeX;
        final double reach = face() - eyeDepth;
        final double[] rect =
            { Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE };
        for (int corner = 0; corner < 8; corner++)
        {
            final double cornerX = x + (corner & 1);
            final double cornerY = y + ((corner >> 1) & 1);
            final double cornerZ = z + ((corner >> 2) & 1);
            final double depth = (alongX ? cornerX : cornerZ) - eyeDepth;
            final double scale = (Math.abs(depth) < 1.0e-9) ? -1.0 : (reach / depth);
            // Not further from the eye than the face, on the same side: not behind the opening.
            if ((scale <= 0.0) || (scale > 1.0))
            {
                return null;
            }
            final double across = eyeAcross + (scale * ((alongX ? cornerZ : cornerX) - eyeAcross));
            final double up = eyeY + (scale * (cornerY - eyeY));
            rect[0] = Math.min(rect[0], across);
            rect[1] = Math.max(rect[1], across);
            rect[2] = Math.min(rect[2], up);
            rect[3] = Math.max(rect[3], up);
        }
        return rect;
    }

    /**
     * Whether a projected block falls, even partly, on any of these blocks of the opening.
     *
     * @param rect
     *            from {@link #projected}
     * @param open
     *            the blocks of this window's opening that can be seen through
     * @return true if it does
     */
    boolean overlaps(final double[] rect, final List<Spot> open)
    {
        final boolean alongX = into.x() != 0;
        for (final Spot cell : open)
        {
            final int across = alongX ? cell.z() : cell.x();
            if ((rect[0] < (across + 1)) && (rect[1] > across) && (rect[2] < (cell.y() + 1))
                && (rect[3] > cell.y()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether everything of a projected block falls on blocks of the face that keep it in view.
     *
     * <p>A drawn block is a whole block, not a picture cut to the opening. In a wall that is
     * harmless: the wall hides whatever of it lies outside. In open air nothing does, so a block
     * only partly behind the opening would show in full beside it.
     *
     * @param rect
     *            from {@link #projected}
     * @param face
     *            what each block of the face lets through
     * @return true if every block of the face the projection touches is clear
     */
    boolean covered(final double[] rect, final Face face)
    {
        final int acrossFrom = (int) Math.floor(rect[0]);
        final int acrossTo = (int) Math.ceil(rect[1]) - 1;
        final int yFrom = (int) Math.floor(rect[2]);
        final int yTo = (int) Math.ceil(rect[3]) - 1;
        // A projection this wide is a block right against the face at a glancing angle.
        if (((acrossTo - acrossFrom) > WIDEST) || ((yTo - yFrom) > WIDEST))
        {
            return false;
        }
        for (int across = acrossFrom; across <= acrossTo; across++)
        {
            for (int y = yFrom; y <= yTo; y++)
            {
                if (!face.clear(across, y))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * How far a projected block's middle is from the middle of this opening, along its face.
     *
     * @param rect
     *            from {@link #projected}
     * @return the distance, in blocks
     */
    double offCentre(final double[] rect)
    {
        final double centre = ((into.x() != 0) ? base.z() : base.x()) + 0.5;
        return Math.abs(((rect[0] + rect[1]) / 2.0) - centre);
    }

    /** The lowest point either edge of a span reaches, scaled out from an eye by either factor. */
    private static double lowest(final double eye, final double from, final double to,
        final double near, final double farther)
    {
        return Math.min(Math.min(spread(eye, from, near), spread(eye, from, farther)),
            Math.min(spread(eye, to, near), spread(eye, to, farther)));
    }

    /** The highest point either edge of a span reaches, scaled out from an eye by either factor. */
    private static double highest(final double eye, final double from, final double to,
        final double near, final double farther)
    {
        return Math.max(Math.max(spread(eye, from, near), spread(eye, from, farther)),
            Math.max(spread(eye, to, near), spread(eye, to, farther)));
    }

    /** Where a line from an eye through an edge of the face is, that many times as far away. */
    private static double spread(final double eye, final double edge, final double scale)
    {
        return eye + ((edge - eye) * scale);
    }

    /** @return the step to the right of somebody facing along this one */
    private static Spot rightOf(final Spot step)
    {
        return new Spot(-step.z(), 0, step.x());
    }

    private static boolean isCardinal(final BlockFace facing)
    {
        return (facing == BlockFace.NORTH) || (facing == BlockFace.SOUTH)
            || (facing == BlockFace.EAST) || (facing == BlockFace.WEST);
    }

    private static int floor(final double value)
    {
        return (int) Math.floor(value);
    }
}
