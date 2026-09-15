package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.List;

import org.bukkit.block.BlockFace;

/**
 * The shape of a window mirror: its opening, which blocks behind it a viewer could see through
 * it, and which block at the far side each of those shows.
 *
 * <p>The opening is the banner's own size, one wide and two tall, in the wall just behind it: a
 * wall banner hangs down, so the opening runs down from the banner's row. Only a wall banner makes
 * a window; one on a post has nothing round it to hide its room past its edges.
 *
 * <p>Plain numbers only, so all of it is testable without a server. {@link MirrorWindows} is the
 * part that reads blocks and sends them.
 *
 * <p>Right through the opening is right at the far side, not left, for a window onto another
 * mirror's room. A mirror showing its own room is {@code mirrored}: flipped across the wall, so a
 * step to the right behind it shows a step to the right in front of it.
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
    MirrorWindow.Spot ahead, boolean mirrored, int width)
{
    /**
     * A window one banner wide onto somewhere, not a reflection.
     *
     * @param base
     *            the opening's bottom left block
     * @param into
     *            one step from the opening into the wall
     * @param far
     *            the far block the opening's bottom left shows
     * @param ahead
     *            one step the way a traveller faces on arrival
     */
    public MirrorWindow(final Spot base, final Spot into, final Spot far, final Spot ahead)
    {
        this(base, into, far, ahead, false, 1);
    }

    /** A width is one banner or two. */
    public MirrorWindow
    {
        width = (width >= 2) ? 2 : 1;
    }

    /** How wide the opening is, in blocks: the banner's own column. */
    static final int WIDTH = 1;

    /** How tall the opening is: the banner's cloth. */
    static final int HEIGHT = 2;

    /** The furthest a candidate may be from the opening along its face, however close the eye. */
    static final int WIDEST = 64;

    /** The most of a drawn block's outline that may land beside the opening, in open air. */
    private static final double MOST_BESIDE = 0.05;

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

    /** Says how much of a block in the opening's face keeps a drawn block behind it out of sight. */
    @FunctionalInterface
    public interface Cover
    {
        /**
         * @param across
         *            the block's coordinate along the face, as for {@link Face}
         * @param y
         *            the block's y
         * @return the part of the block, as {@code {acrossMin, acrossMax, yMin, yMax}} in face
         *         coordinates, on which a drawn block cannot be seen anywhere it should not be;
         *         or null for none of it
         */
        double[] clear(int across, int y);
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

    /**
     * The window a wall banner makes, if it can make one.
     *
     * @param banner
     *            the banner block
     * @param facing
     *            which way the banner faces: one of the four cardinals a wall banner hangs
     * @param destination
     *            where the mirror goes
     * @return the window, or null for a banner facing no usable way or a mirror going nowhere
     */
    public static MirrorWindow of(final MirrorBlock banner, final BlockFace facing, final MirrorPoint destination)
    {
        return of(banner, facing, destination, false);
    }

    /**
     * The window a wall banner makes, onto somewhere or as a reflection of its own room.
     *
     * @param banner
     *            the banner block
     * @param facing
     *            which way the banner faces
     * @param destination
     *            where the mirror goes; for a reflection, its own room
     * @param mirrored
     *            true to show the far side flipped across the wall, as a mirror does, rather
     *            than turned to face the viewer
     * @return the window, or null for a banner facing no usable way or a mirror going nowhere
     */
    public static MirrorWindow of(final MirrorBlock banner, final BlockFace facing, final MirrorPoint destination,
        final boolean mirrored)
    {
        return of(banner, facing, destination, mirrored, 1);
    }

    /**
     * The window a wall banner makes, one banner wide or two.
     *
     * @param banner
     *            the banner block; for two, the left one looking at the wall
     * @param facing
     *            which way the banner faces
     * @param destination
     *            where the mirror goes; for a reflection, its own room
     * @param mirrored
     *            true to show the far side flipped across the wall
     * @param width
     *            one banner or two, the second to the right of the first
     * @return the window, or null for a banner facing no usable way or a mirror going nowhere
     */
    public static MirrorWindow of(final MirrorBlock banner, final BlockFace facing, final MirrorPoint destination,
        final boolean mirrored, final int width)
    {
        if ((banner == null) || (destination == null) || (facing == null) || !isCardinal(facing))
        {
            return null;
        }
        final Spot into = new Spot(-facing.getModX(), 0, -facing.getModZ());
        // A wall banner hangs down from where it is hung, so the opening runs down from it.
        final int bottom = banner.y() - (HEIGHT - 1);
        return new MirrorWindow(
            new Spot(banner.x() + into.x(), bottom, banner.z() + into.z()),
            into,
            new Spot(floor(destination.x()), floor(destination.y()), floor(destination.z())),
            aheadOf(destination.yaw()), mirrored, width);
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
        // From the left banner's column, rightwards, looking at the wall.
        for (int across = 0; across < width; across++)
        {
            for (int up = 0; up < HEIGHT; up++)
            {
                opening.at(base.x() + (across * right.x()), base.y() + up,
                    base.z() + (across * right.z()));
            }
        }
    }

    /**
     * The direction at the far side that a direction through this opening becomes.
     *
     * <p>For looking past a block at the far side the way a viewer here looks past the block
     * that shows it: the same turn the block mapping makes, applied to a direction.
     *
     * @param dx
     *            the direction here, x
     * @param dy
     *            the direction here, y
     * @param dz
     *            the direction here, z
     * @return the direction at the far side, as {@code {x, y, z}}
     */
    public double[] farDirection(final double dx, final double dy, final double dz)
    {
        final Spot right = rightOf(into);
        final Spot farRight = farRight();
        final double along = (dx * into.x()) + (dz * into.z());
        final double across = (dx * right.x()) + (dz * right.z());
        return new double[] { (along * ahead.x()) + (across * farRight.x()), dy,
            (along * ahead.z()) + (across * farRight.z()) };
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
    /**
     * How many quarter turns clockwise, seen from above, take the far side's forward to this
     * side's: the turn every far-side block's facing needs to show here the right way round.
     *
     * <p>{@link #farOf} turns positions, not the blocks at them. A glass pane's connections, a
     * stair's facing and a fence's arms are compass directions, so a far side turned round showed
     * panes that did not join and stairs climbing the wrong way.
     *
     * @return 0 to 3
     */
    int quarterTurns()
    {
        // A reflection is flipped across the wall, not turned.
        return mirrored ? 0 : Math.floorMod(compass(into) - compass(ahead), 4);
    }

    /** Which way along the far side a step to the right through the opening goes: flipped in a reflection. */
    private Spot farRight()
    {
        final Spot right = rightOf(ahead);
        return mirrored ? new Spot(-right.x(), 0, -right.z()) : right;
    }

    /** North 0, east 1, south 2, west 3: clockwise from above. */
    private static int compass(final Spot step)
    {
        if (step.z() < 0)
        {
            return 0;
        }
        if (step.x() > 0)
        {
            return 1;
        }
        return (step.z() > 0) ? 2 : 3;
    }

    /**
     * The block behind the opening that shows a far-side block: {@link #farOf} the other way.
     *
     * @param farX
     *            the far-side block, x
     * @param farY
     *            its y
     * @param farZ
     *            its z
     * @return the block here that shows it, which may be in front of the opening or in its layer
     */
    public Spot hereOf(final int farX, final int farY, final int farZ)
    {
        final Spot right = rightOf(into);
        final Spot farRight = farRight();
        final int dx = farX - far.x();
        final int dz = farZ - far.z();
        final int depth = ((dx * ahead.x()) + (dz * ahead.z())) + 1;
        final int across = (dx * farRight.x()) + (dz * farRight.z());
        return new Spot(base.x() + (depth * into.x()) + (across * right.x()),
            base.y() + (farY - far.y()),
            base.z() + (depth * into.z()) + (across * right.z()));
    }

    public Spot farOf(final int x, final int y, final int z)
    {
        final Spot right = rightOf(into);
        final Spot farRight = farRight();
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
        return (across >= 0) && (across < width);
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
            final double cornerX = (double) x + (corner & 1);
            final double cornerY = (double) y + ((corner >> 1) & 1);
            final double cornerZ = (double) z + ((corner >> 2) & 1);
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
     * Whether a block behind the face could project, from this eye, onto a span of the face: a
     * cheap bound before {@link #projected}, for walking a whole room's blocks against one eye.
     *
     * <p>A corner's projection scales by the face's distance over the corner's, so the nearest
     * and farthest depths of the block bound where its corners can land.
     *
     * @param span
     *            {@code {acrossMin, acrossMax, yMin, yMax}} on the face, as {@link #projected} measures
     * @return false only if no part of the block can land on the span
     */
    boolean mightLandOn(final double eyeX, final double eyeY, final double eyeZ, final int x, final int y,
        final int z, final double[] span)
    {
        final boolean alongX = into.x() != 0;
        final double eyeDepth = alongX ? eyeX : eyeZ;
        final double eyeAcross = alongX ? eyeZ : eyeX;
        final double reach = Math.abs(face() - eyeDepth);
        final int near = alongX ? x : z;
        final double nearest = Math.min(Math.abs(near - eyeDepth), Math.abs((near + 1) - eyeDepth));
        if (nearest <= reach)
        {
            return true;
        }
        final double nearScale = reach / nearest;
        final double farScale = reach / (nearest + 1.0);
        final double across = alongX ? z : x;
        final double acrossLow = Math.min(eyeAcross + ((across - eyeAcross) * nearScale), eyeAcross + ((across - eyeAcross) * farScale));
        final double acrossHigh = Math.max(eyeAcross + (((across + 1.0) - eyeAcross) * nearScale),
            eyeAcross + (((across + 1.0) - eyeAcross) * farScale));
        final double upLow = Math.min(eyeY + ((y - eyeY) * nearScale), eyeY + ((y - eyeY) * farScale));
        final double upHigh = Math.max(eyeY + (((y + 1.0) - eyeY) * nearScale), eyeY + (((y + 1.0) - eyeY) * farScale));
        return (acrossHigh >= span[0]) && (acrossLow <= span[1]) && (upHigh >= span[2]) && (upLow <= span[3]);
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
     * Whether nearly all of a projected block falls on blocks of the face that keep it in view.
     *
     * <p>A drawn block is a whole block, not a picture cut to the opening. In a wall that is
     * harmless: the wall hides whatever of it lies outside. In open air nothing does, so a block
     * partly behind the opening shows partly beside it. Half used to be enough, on the grounds
     * that a sliver of real world inside the opening was worse than far scenery beside it; on
     * small freestanding mirrors the far world then showed well past their edges, which was
     * worse. A twentieth is allowed for the rounding of a block that only just touches the edge.
     *
     * @param rect
     *            from {@link #projected}
     * @param face
     *            what each block of the face lets through
     * @return true if all but a twentieth of the projection's area lands on clear blocks of the
     *         face
     */
    boolean covered(final double[] rect, final Face face)
    {
        return covered(rect, face, MOST_BESIDE);
    }

    /**
     * The same, allowing a given share of the outline beside the opening.
     *
     * @param rect
     *            from {@link #projected}
     * @param face
     *            what each block of the face lets through
     * @param mostBeside
     *            the most of the outline's area that may land where nothing hides it
     * @return true if the rest lands on clear blocks of the face
     */
    boolean covered(final double[] rect, final Face face, final double mostBeside)
    {
        return covered(rect,
            (Cover) (across, y) -> face.clear(across, y) ? new double[] { across, across + 1.0, y, y + 1.0 } : null,
            mostBeside);
    }

    /**
     * The same, where a block of the face may keep only part of what lands on it out of sight.
     *
     * @param rect
     *            from {@link #projected}
     * @param cover
     *            the part of each block of the face that hides what lands on it
     * @param mostBeside
     *            the most of the outline's area that may land where nothing hides it
     * @return true if the rest lands on covered parts of the face
     */
    boolean covered(final double[] rect, final Cover cover, final double mostBeside)
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
        final double area = (rect[1] - rect[0]) * (rect[3] - rect[2]);
        double clear = 0.0;
        for (int across = acrossFrom; across <= acrossTo; across++)
        {
            for (int y = yFrom; y <= yTo; y++)
            {
                final double[] part = cover.clear(across, y);
                if (part != null)
                {
                    clear += Math.max(0.0, Math.min(rect[1], part[1]) - Math.max(rect[0], part[0]))
                        * Math.max(0.0, Math.min(rect[3], part[3]) - Math.max(rect[2], part[2]));
                }
            }
        }
        return (area <= 0.0) || (clear >= ((1.0 - mostBeside) * area));
    }

    /**
     * Where a block in front of the face -- between it and the eye -- hides the face from that
     * eye: its outline, thrown onto the face plane.
     *
     * <p>For real blocks on the viewer's side, such as a corridor's walls or a hut's sides and
     * roof, which hide parts of the face beside the opening as surely as the wall itself does.
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
     * @return {@code {acrossMin, acrossMax, yMin, yMax}} on the face; or null if the block is
     *         not wholly between the eye and the face
     */
    double[] shadow(final double eyeX, final double eyeY, final double eyeZ, final int x,
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
            final double cornerX = (double) x + (corner & 1);
            final double cornerY = (double) y + ((corner >> 1) & 1);
            final double cornerZ = (double) z + ((corner >> 2) & 1);
            final double depth = (alongX ? cornerX : cornerZ) - eyeDepth;
            // The same way from the eye as the face, nearer than it, and not on top of the eye.
            if ((depth * reach <= 0.0) || (Math.abs(depth) > Math.abs(reach))
                || (Math.abs(depth) < 0.05))
            {
                return null;
            }
            final double scale = reach / depth;
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
