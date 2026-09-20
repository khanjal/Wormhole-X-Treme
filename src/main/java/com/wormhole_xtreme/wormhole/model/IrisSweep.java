package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Location;

/**
 * The order an iris closes and opens in: rings, measured out from the middle of the opening.
 *
 * <p>Pure arithmetic over block coordinates, deliberately separate from anything that draws.
 * The sweep is the part worth pinning -- which cell belongs to which ring, and which ring goes
 * first -- and none of it needs a world, a scheduler or a gate to decide.
 *
 * <p>Rings are cut by exact distance from the centre rather than by rounding to a whole number
 * of blocks. On an opening with an even width the centre falls between two blocks, so every
 * distance lands on a half; rounding would fold the two innermost rings together on some gates
 * and not others, and a sweep that is symmetric on {@code Standard} but lopsided on
 * {@code Large} is the kind of thing nobody notices until it is built.
 *
 * <p>Squared distances, never square roots. Two cells are in the same ring when their squared
 * distances are equal, and squaring a half is exact in binary, so this compares without an
 * epsilon and without asking which two nearly-equal doubles ought to count as one ring.
 */
public final class IrisSweep
{
    /** Static helpers only. */
    private IrisSweep()
    {
    }

    /**
     * The opening's cells in rings, the outermost first.
     *
     * <p>This is the closing order: an iris comes in from the rim, so the last cell to be
     * covered is the one in the middle.
     *
     * @param cells
     *            the cells the iris is made of, in any order
     * @return the rings, outermost first; empty if there are no cells
     */
    public static List<List<Location>> closingRings(final List<Location> cells)
    {
        final List<List<Location>> rings = rings(cells);
        java.util.Collections.reverse(rings);
        return rings;
    }

    /**
     * The opening's cells in rings, the innermost first.
     *
     * <p>This is the opening order, which is the closing one run backwards: the iris draws
     * back from the middle out to the rim.
     *
     * @param cells
     *            the cells the iris is made of, in any order
     * @return the rings, innermost first; empty if there are no cells
     */
    public static List<List<Location>> openingRings(final List<Location> cells)
    {
        return rings(cells);
    }

    /**
     * The cells grouped by how far they sit from the centre, nearest ring first.
     *
     * @param cells
     *            the cells, in any order
     * @return one list per ring
     */
    private static List<List<Location>> rings(final List<Location> cells)
    {
        final List<List<Location>> rings = new ArrayList<>();
        if ((cells == null) || cells.isEmpty())
        {
            return rings;
        }
        final double[] centre = centreOf(cells);
        // Sorted by squared distance, so the rings come out in order without a second pass.
        final Map<Double, List<Location>> byDistance = new TreeMap<>();
        for (final Location cell : cells)
        {
            byDistance.computeIfAbsent(squaredDistanceFrom(centre, cell), d -> new ArrayList<>()).add(cell);
        }
        for (final List<Location> ring : byDistance.values())
        {
            // Settled order within a ring. Nothing on screen depends on it -- the whole ring
            // is drawn in the same tick -- but a test that says which cells are in a ring
            // should not also have to say which order the map happened to hand them back in.
            ring.sort(Comparator.comparingInt(Location::getBlockY)
                .thenComparingInt(Location::getBlockX)
                .thenComparingInt(Location::getBlockZ));
            rings.add(ring);
        }
        return rings;
    }

    /**
     * The middle of the opening, as a point rather than a block.
     *
     * <p>The midpoint of the extremes rather than the average of every cell: the average is
     * pulled towards whichever side holds more cells, which on a gate whose opening is not a
     * neat rectangle puts the centre off-centre and makes the sweep arrive lopsided.
     *
     * @param cells
     *            the cells
     * @return the centre as {x, y, z}
     */
    private static double[] centreOf(final List<Location> cells)
    {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final Location cell : cells)
        {
            minX = Math.min(minX, cell.getBlockX());
            minY = Math.min(minY, cell.getBlockY());
            minZ = Math.min(minZ, cell.getBlockZ());
            maxX = Math.max(maxX, cell.getBlockX());
            maxY = Math.max(maxY, cell.getBlockY());
            maxZ = Math.max(maxZ, cell.getBlockZ());
        }
        return new double[]
        {
            (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0
        };
    }

    /**
     * How far a cell sits from the centre, squared.
     *
     * @param centre
     *            the centre as {x, y, z}
     * @param cell
     *            the cell
     * @return the squared distance
     */
    private static double squaredDistanceFrom(final double[] centre, final Location cell)
    {
        final double dx = cell.getBlockX() - centre[0];
        final double dy = cell.getBlockY() - centre[1];
        final double dz = cell.getBlockZ() - centre[2];
        return (dx * dx) + (dy * dy) + (dz * dz);
    }
}
