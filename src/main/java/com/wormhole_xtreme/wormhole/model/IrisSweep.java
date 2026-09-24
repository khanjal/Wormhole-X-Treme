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
    /**
     * How an iris crosses its opening.
     *
     * <p>Each is an order over the same cells and nothing else: the drawing, the pace and the
     * blocks are identical, so a style can never affect whether a gate is shut, only what the
     * crossing looks like.
     */
    public enum Style
    {
        /** Rings, out from the middle. */
        SWEEP,
        /** A wedge turning round the middle and working outwards. */
        SPIRAL,
        /** Rows, in from the top and bottom at once. */
        ROWS,
        /** Columns, in from both sides at once. */
        COLUMNS;

        /**
         * The style of that name, or {@link #SWEEP} for anything unrecognised.
         *
         * <p>Unrecognised rather than refused: this reads a config value a server owner types,
         * and a mistyped style should cost them the style they wanted rather than the iris.
         *
         * @param name
         *            the configured name, in any case
         */
        public static Style of(final String name)
        {
            if (name != null)
            {
                for (final Style style : values())
                {
                    if (style.name().equalsIgnoreCase(name.trim()))
                    {
                        return style;
                    }
                }
            }
            return SWEEP;
        }
    }

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
        return closingOrder(cells, Style.SWEEP);
    }

    /**
     * The opening's cells in the order a closing iris covers them.
     *
     * @param cells
     *            the cells the iris is made of, in any order
     * @param style
     *            how it crosses
     * @return the steps, the first drawn first; empty if there are no cells
     */
    public static List<List<Location>> closingOrder(final List<Location> cells, final Style style)
    {
        return closingOrder(cells, style, 0);
    }

    /**
     * The opening's cells in the order a closing iris covers them, in at most so many steps.
     *
     * @param cells
     *            the cells the iris is made of, in any order
     * @param style
     *            how it crosses
     * @param maxSteps
     *            the most steps to cross in, or 0 for a step per ring however many that is
     * @return the steps, the first drawn first; empty if there are no cells
     */
    public static List<List<Location>> closingOrder(final List<Location> cells, final Style style,
        final int maxSteps)
    {
        final List<List<Location>> steps = openingOrder(cells, style, maxSteps);
        java.util.Collections.reverse(steps);
        return steps;
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
        return openingOrder(cells, Style.SWEEP);
    }

    /**
     * The opening's cells in the order an opening iris uncovers them.
     *
     * @param cells
     *            the cells the iris is made of, in any order
     * @param style
     *            how it crosses
     * @return the steps, the first drawn first; empty if there are no cells
     */
    public static List<List<Location>> openingOrder(final List<Location> cells, final Style style)
    {
        if (style == Style.SPIRAL)
        {
            return spiral(cells);
        }
        return rings(cells, style);
    }

    /**
     * The opening's cells in the order an opening iris uncovers them, in at most so many steps.
     *
     * <p>How many steps an opening has is its geometry: a wide gate has more rings than a small
     * one, so at a fixed pace per step it takes proportionally longer to cross. That is the
     * wrong way round -- a big gate is exactly the one somebody is waiting on the iris of --
     * and it cannot be fixed by the pace, because a step cannot be shorter than a tick.
     *
     * <p>So the steps themselves are merged: adjacent rings are joined into bands until there
     * are few enough of them, which is what keeps the crossing the same length whatever the
     * gate. The cells and their order are untouched -- a band is still covered from the rim in
     * or drawn back from the middle out -- and each band is still drawn in one tick, so this
     * changes how much of the iris arrives at once and nothing else.
     *
     * @param cells
     *            the cells the iris is made of, in any order
     * @param style
     *            how it crosses
     * @param maxSteps
     *            the most steps to cross in, or 0 for a step per ring however many that is
     * @return the steps, the first drawn first; empty if there are no cells
     */
    public static List<List<Location>> openingOrder(final List<Location> cells, final Style style,
        final int maxSteps)
    {
        return atMost(openingOrder(cells, style), maxSteps);
    }

    /**
     * Joins adjacent steps together until there are no more than so many.
     *
     * @param steps
     *            the steps, in the order they are drawn
     * @param maxSteps
     *            the most to leave, or 0 to leave them alone
     * @return the steps, merged if there were too many
     */
    private static List<List<Location>> atMost(final List<List<Location>> steps, final int maxSteps)
    {
        if ((maxSteps <= 0) || (steps.size() <= maxSteps))
        {
            return steps;
        }
        final List<List<Location>> merged = new ArrayList<>(maxSteps);
        for (final List<List<Location>> band : cut(steps, maxSteps))
        {
            final List<Location> cells = new ArrayList<>();
            for (final List<Location> step : band)
            {
                cells.addAll(step);
            }
            merged.add(cells);
        }
        return merged;
    }

    /**
     * Cuts a list into that many pieces, by position rather than by a fixed size.
     *
     * <p>Rounding a piece size up loses a piece whenever it does not divide evenly, which is
     * what once made the spiral cross faster than every other style at the same setting. Cutting
     * by position instead gives exactly the number asked for, the odd remainder spread through
     * them rather than left at one end.
     *
     * @param <T>
     *            what is being cut up
     * @param wanted
     *            how many pieces, at least one
     * @return the pieces, in order; some may be empty if there are fewer items than pieces
     */
    private static <T> List<List<T>> cut(final List<T> items, final int wanted)
    {
        final List<List<T>> pieces = new ArrayList<>(wanted);
        for (int i = 0; i < wanted; i++)
        {
            final int from = (int) (((long) i * items.size()) / wanted);
            final int to = (int) ((((long) i + 1) * items.size()) / wanted);
            pieces.add(new ArrayList<>(items.subList(from, to)));
        }
        return pieces;
    }

    /**
     * The cells grouped by how far they sit from the centre, nearest ring first.
     *
     * @param cells
     *            the cells, in any order
     * @return one list per ring
     */
    private static List<List<Location>> rings(final List<Location> cells, final Style style)
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
            byDistance.computeIfAbsent(keyOf(centre, cell, style), d -> new ArrayList<>()).add(cell);
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
     * What a style groups a cell by: a squared distance along the axes it counts.
     *
     * <p>{@link Style#ROWS} counts only the vertical, so a row is a step; {@link Style#COLUMNS}
     * only the horizontal. Both work in from the outside the way rings do rather than from one
     * end, because an iris that arrives from one side reads as a door and not as an iris.
     *
     * @param centre
     *            the centre as {x, y, z}
     * @param style
     *            which axes to count
     * @return the grouping key
     */
    private static double keyOf(final double[] centre, final Location cell, final Style style)
    {
        final double dx = cell.getBlockX() - centre[0];
        final double dy = cell.getBlockY() - centre[1];
        final double dz = cell.getBlockZ() - centre[2];
        return switch (style)
        {
            case ROWS -> dy * dy;
            case COLUMNS -> (dx * dx) + (dz * dz);
            default -> (dx * dx) + (dy * dy) + (dz * dz);
        };
    }

    /**
     * The cells as a wedge turning round the centre and working outwards.
     *
     * <p>Sorted by angle plus radius, so the wedge does not merely rotate: by the time it comes
     * back round it is a ring further out, which is what makes it read as a spiral rather than
     * a clock hand.
     *
     * <p>Cut into as many steps as {@link Style#SWEEP} would make on the same opening, so every
     * style takes the same number of {@code gate-iris-step-ticks} to cross. A style that took
     * twice as long as another at the same setting would be a second pace control nobody asked
     * for.
     *
     * @return the steps, innermost first
     */
    private static List<List<Location>> spiral(final List<Location> cells)
    {
        if ((cells == null) || cells.isEmpty())
        {
            return new ArrayList<>();
        }
        final double[] centre = centreOf(cells);
        final List<Location> wound = new ArrayList<>(cells);
        wound.sort(Comparator.comparingDouble(cell -> windingOf(centre, cell)));
        // Exactly as many steps as the sweep makes, cut by position rather than by a fixed
        // size: rounding a chunk size up loses a step whenever it does not divide evenly,
        // which made the spiral cross faster than every other style on the same setting.
        return cut(wound, Math.min(wound.size(), Math.max(1, rings(cells, Style.SWEEP).size())));
    }

    /**
     * How far round and out a cell sits, as one number.
     *
     * <p>The radius dominates and the angle breaks ties within it, so the order runs round the
     * middle and outwards at once.
     *
     * @param centre
     *            the centre as {x, y, z}
     * @return the winding position
     */
    private static double windingOf(final double[] centre, final Location cell)
    {
        final double dx = cell.getBlockX() - centre[0];
        final double dy = cell.getBlockY() - centre[1];
        final double dz = cell.getBlockZ() - centre[2];
        // The horizontal offset is whichever of x and z the gate actually spans; a gate stands
        // in a plane, so the other is zero and adding them is the same as picking the right one.
        final double across = dx + dz;
        final double radius = Math.sqrt((across * across) + (dy * dy));
        final double turn = (Math.atan2(dy, across) + Math.PI) / (2 * Math.PI);
        return radius + turn;
    }

}
