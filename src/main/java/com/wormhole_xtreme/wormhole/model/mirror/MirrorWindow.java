package com.wormhole_xtreme.wormhole.model.mirror;

import org.bukkit.block.BlockFace;

/**
 * The shape of a window mirror: the opening in the wall its banner hangs on, the box of blocks
 * behind that opening drawn for whoever looks in, and which block at the far side each one shows.
 *
 * <p>Plain numbers only, so all of it is testable without a server. {@link MirrorWindows} is the
 * part that reads blocks and sends them.
 *
 * <p>Right through the opening is right at the far side, not left: this is a window onto where
 * the mirror goes, not a reflection of it.
 *
 * @param wall
 *            the block the banner hangs on, which is the middle of the opening's top row
 * @param into
 *            one step into the wall, the way somebody looking in faces
 * @param far
 *            the block a traveller arrives in
 * @param ahead
 *            one step the way a traveller faces on arrival
 */
public record MirrorWindow(MirrorWindow.Spot wall, MirrorWindow.Spot into, MirrorWindow.Spot far,
    MirrorWindow.Spot ahead)
{
    /** How wide the opening is, in blocks. Odd, so the banner's wall block is its middle. */
    static final int WIDTH = 3;

    /** How tall the opening is, hanging down from the banner's own row. */
    static final int HEIGHT = 3;

    /** How far behind the wall the far side is drawn. */
    static final int DEPTH = 16;

    /** Drawn past each side of the opening, so looking in at an angle is not cut short. */
    static final int SIDE = 8;

    /** Drawn above the opening's top row. */
    static final int ABOVE = 6;

    /** Drawn below the opening's bottom row, which is where the far side's ground is. */
    static final int BELOW = 3;

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

    /** Handed each block drawn behind the opening, and the far-side block it shows. */
    @FunctionalInterface
    public interface Shown
    {
        /**
         * @param x
         *            block x here
         * @param y
         *            block y here
         * @param z
         *            block z here
         * @param farX
         *            block x at the far side
         * @param farY
         *            block y at the far side
         * @param farZ
         *            block z at the far side
         */
        void at(int x, int y, int z, int farX, int farY, int farZ);
    }

    /**
     * The window a banner makes, if it can make one.
     *
     * @param banner
     *            the banner block
     * @param facing
     *            which way the banner faces
     * @param destination
     *            where the mirror goes
     * @return the window, or null for a freestanding banner, a banner facing no way at all, or
     *         a mirror that goes nowhere
     */
    public static MirrorWindow of(final MirrorBlock banner, final BlockFace facing,
        final MirrorPoint destination)
    {
        if ((banner == null) || (destination == null) || !isCardinal(facing))
        {
            return null;
        }
        final Spot into = new Spot(-facing.getModX(), 0, -facing.getModZ());
        return new MirrorWindow(
            new Spot(banner.x() + into.x(), banner.y(), banner.z() + into.z()),
            into,
            new Spot(floor(destination.x()), floor(destination.y()), floor(destination.z())),
            aheadOf(destination.yaw()));
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
            for (int up = 1 - HEIGHT; up <= 0; up++)
            {
                opening.at(wall.x() + (across * right.x()), wall.y() + up,
                    wall.z() + (across * right.z()));
            }
        }
    }

    /**
     * Visits every block drawn behind the opening, with the far-side block it shows.
     *
     * <p>The first layer behind the wall shows the arrival block's own layer, and the opening's
     * bottom row lines up with the arrival block's height -- so what shows through the middle
     * of the bottom row is exactly where a traveller lands.
     *
     * @param shown
     *            handed each one
     */
    public void forEachShown(final Shown shown)
    {
        final Spot right = rightOf(into);
        final Spot farRight = rightOf(ahead);
        for (int depth = 1; depth <= DEPTH; depth++)
        {
            for (int across = -HALF - SIDE; across < (WIDTH - HALF + SIDE); across++)
            {
                for (int up = 1 - HEIGHT - BELOW; up <= ABOVE; up++)
                {
                    shown.at(
                        wall.x() + (depth * into.x()) + (across * right.x()),
                        wall.y() + up,
                        wall.z() + (depth * into.z()) + (across * right.z()),
                        far.x() + ((depth - 1) * ahead.x()) + (across * farRight.x()),
                        far.y() + up + (HEIGHT - 1),
                        far.z() + ((depth - 1) * ahead.z()) + (across * farRight.z()));
                }
            }
        }
    }

    /**
     * Whether somebody standing here is on the banner's side of the wall.
     *
     * @param x
     *            where they stand, x
     * @param z
     *            where they stand, z
     * @return true if they are past the wall's face on the banner's side
     */
    public boolean inFront(final double x, final double z)
    {
        // From the middle of the wall block, so past its face is more than half a block out.
        final double out = (((wall.x() + 0.5) - x) * into.x()) + (((wall.z() + 0.5) - z) * into.z());
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
        final int dx = x - wall.x();
        final int dz = z - wall.z();
        if ((((dx * into.x()) + (dz * into.z())) != 0) || (y > wall.y())
            || (y <= (wall.y() - HEIGHT)))
        {
            return false;
        }
        final Spot right = rightOf(into);
        final int across = (dx * right.x()) + (dz * right.z());
        return (across >= -HALF) && (across < (WIDTH - HALF));
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
