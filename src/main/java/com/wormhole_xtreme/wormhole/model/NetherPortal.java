package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Axis;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * A lit vanilla nether portal's opening: its portal blocks, one upright rectangle (#514).
 *
 * @param world
 *            the world it is in
 * @param alongX
 *            true if it runs along x (its blocks' axis is X), so it is looked into along z
 * @param lowAcross
 *            its first column: x when it runs along x, z otherwise
 * @param highAcross
 *            its last column
 * @param lowY
 *            its bottom row
 * @param highY
 *            its top row
 * @param plane
 *            the coordinate it does not run along: z when it runs along x, x otherwise
 */
public record NetherPortal(String world, boolean alongX, int lowAcross, int highAcross, int lowY, int highY,
    int plane)
{
    /** The widest and tallest portal vanilla lights. */
    static final int MOST_SIDE = 21;

    /** Asks what one block is, so a test needs no world. */
    @FunctionalInterface
    public interface Cells
    {
        /**
         * @return the axis of a portal block there, X or Z; null for anything else, a block not loaded included
         */
        Axis axisAt(int x, int y, int z);
    }

    /**
     * The portal a block belongs to: a flood fill of the portal blocks sharing its axis, in its plane.
     *
     * @return the portal, or null for no portal block there, one bigger than vanilla lights, or one
     *         that is not a whole rectangle
     */
    public static NetherPortal at(final String world, final int x, final int y, final int z, final Cells cells)
    {
        final Axis axis = cells.axisAt(x, y, z);
        if ((axis != Axis.X) && (axis != Axis.Z))
        {
            return null;
        }
        final boolean alongX = axis == Axis.X;
        final Set<Spot> seen = new HashSet<>();
        final Deque<Spot> next = new ArrayDeque<>();
        final Spot first = new Spot(x, y, z);
        seen.add(first);
        next.add(first);
        int[] box = { Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE };
        while (!next.isEmpty())
        {
            final Spot cell = next.poll();
            final int across = alongX ? cell.x() : cell.z();
            box = new int[] { Math.min(box[0], across), Math.max(box[1], across), Math.min(box[2], cell.y()),
                Math.max(box[3], cell.y()) };
            for (final Spot side : sides(cell, alongX))
            {
                if (!seen.contains(side) && (cells.axisAt(side.x(), side.y(), side.z()) == axis))
                {
                    if (seen.size() >= (MOST_SIDE * MOST_SIDE))
                    {
                        return null;
                    }
                    seen.add(side);
                    next.add(side);
                }
            }
        }
        final NetherPortal portal = new NetherPortal(world, alongX, box[0], box[1], box[2], box[3], alongX ? z : x);
        return ((portal.width() <= MOST_SIDE) && (portal.height() <= MOST_SIDE)
            && (seen.size() == (portal.width() * portal.height()))) ? portal : null;
    }

    /** The four blocks beside one in a portal's plane. */
    private static Spot[] sides(final Spot cell, final boolean alongX)
    {
        final int dx = alongX ? 1 : 0;
        final int dz = alongX ? 0 : 1;
        return new Spot[] { new Spot(cell.x() + dx, cell.y(), cell.z() + dz),
            new Spot(cell.x() - dx, cell.y(), cell.z() - dz), new Spot(cell.x(), cell.y() + 1, cell.z()),
            new Spot(cell.x(), cell.y() - 1, cell.z()) };
    }

    /** @return how many blocks across */
    public int width()
    {
        return (highAcross - lowAcross) + 1;
    }

    /** @return how many blocks tall */
    public int height()
    {
        return (highY - lowY) + 1;
    }

    /** @return a name for it: its world, its lowest corner and its axis */
    public String id()
    {
        return world + ' ' + spotAt(lowAcross, lowY).x() + ',' + lowY + ',' + spotAt(lowAcross, lowY).z()
            + (alongX ? " x" : " z");
    }

    /** The block in one column and row of it. */
    Spot spotAt(final int across, final int y)
    {
        return alongX ? new Spot(across, y, plane) : new Spot(plane, y, across);
    }

    /** @return its portal blocks */
    public List<Spot> cells()
    {
        final List<Spot> cells = new ArrayList<>();
        for (int y = lowY; y <= highY; y++)
        {
            for (int across = lowAcross; across <= highAcross; across++)
            {
                cells.add(spotAt(across, y));
            }
        }
        return cells;
    }

    /** @return the ring round it in its plane, which vanilla's obsidian frame must fill for it to stay lit */
    public Set<Spot> frame()
    {
        final Set<Spot> ring = new HashSet<>();
        for (int across = lowAcross - 1; across <= (highAcross + 1); across++)
        {
            ring.add(spotAt(across, lowY - 1));
            ring.add(spotAt(across, highY + 1));
        }
        for (int y = lowY; y <= highY; y++)
        {
            ring.add(spotAt(lowAcross - 1, y));
            ring.add(spotAt(highAcross + 1, y));
        }
        return ring;
    }

    /** @return its two faces, the ways out of it: south and north for one along x, east and west otherwise */
    public BlockFace[] faces()
    {
        return alongX ? new BlockFace[] { BlockFace.SOUTH, BlockFace.NORTH }
            : new BlockFace[] { BlockFace.EAST, BlockFace.WEST };
    }

    /** @return the block at the middle of its bottom row */
    public Spot bottomMiddle()
    {
        return spotAt(lowAcross + ((width() - 1) / 2), lowY);
    }

    /**
     * Whether every one of its blocks is still a portal block along the same axis.
     *
     * @return false once any has gone, or turned
     */
    public boolean intact(final Cells cells)
    {
        final Axis axis = alongX ? Axis.X : Axis.Z;
        return cells().stream().allMatch(cell -> cells.axisAt(cell.x(), cell.y(), cell.z()) == axis);
    }

    /**
     * How far in front of one face a point stands, measured from the middle of the portal's layer.
     *
     * @return blocks; above half a block is past that face
     */
    public double outFrom(final BlockFace face, final double x, final double z)
    {
        return ((x - (plane + 0.5)) * face.getModX()) + ((z - (plane + 0.5)) * face.getModZ());
    }
}
