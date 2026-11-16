package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

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
    /** About the most turns the spiral makes; more and a big gate's arm is a blur. */
    private static final int ARM_TURNS = 5;

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
        /** One arm, winding in clockwise from the rim. */
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
        return closingOrder(cells, style, maxSteps, null);
    }

    /**
     * The same, for a gate facing a known way.
     *
     * @param facing
     *            the way the gate's activation switch faces, which is the side the spiral turns
     *            clockwise for; null for a gate with none
     * @return the steps, the first drawn first; empty if there are no cells
     */
    public static List<List<Location>> closingOrder(final List<Location> cells, final Style style,
        final int maxSteps, final BlockFace facing)
    {
        final List<List<Location>> steps = openingOrder(cells, style, maxSteps, facing);
        Collections.reverse(steps);
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
        return ordered(cells, style, null);
    }

    /** The steps before any merging. */
    private static List<List<Location>> ordered(final List<Location> cells, final Style style,
        final BlockFace facing)
    {
        if (style == Style.SPIRAL)
        {
            return spiral(cells, facing);
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
        return openingOrder(cells, style, maxSteps, null);
    }

    /**
     * The same, for a gate facing a known way.
     *
     * @param facing
     *            the way the gate's activation switch faces, which is the side the spiral turns
     *            clockwise for; null for a gate with none
     * @return the steps, the first drawn first; empty if there are no cells
     */
    public static List<List<Location>> openingOrder(final List<Location> cells, final Style style,
        final int maxSteps, final BlockFace facing)
    {
        return atMost(ordered(cells, style, facing), maxSteps);
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
     * The cells as one spiral arm, cut into steps.
     *
     * <p>Cut into as many steps as {@link Style#SWEEP} would make on the same opening, so every
     * style takes the same number of steps to cross. A style that took
     * twice as long as another at the same pace would be a second pace control nobody asked
     * for.
     *
     * @param facing
     *            the way the activation switch faces, or null to guess from the opening
     * @return the steps, innermost first
     */
    private static List<List<Location>> spiral(final List<Location> cells, final BlockFace facing)
    {
        if ((cells == null) || cells.isEmpty())
        {
            return new ArrayList<>();
        }
        final List<Location> order = closingArm(cells, facing);
        Collections.reverse(order);
        // Exactly as many steps as the sweep makes, cut by position rather than by a fixed
        // size: rounding a chunk size up loses a step whenever it does not divide evenly,
        // which made the spiral cross faster than every other style on the same setting.
        return cut(order, Math.min(order.size(), Math.max(1, rings(cells, Style.SWEEP).size())));
    }

    /**
     * The cells along one spiral arm, wound in clockwise from the rim as the switch's side sees it.
     *
     * <p>An Archimedean spiral: the arm moves {@link #armWidth} blocks in each turn, and a cell
     * sits on whichever turn of it passes nearest at the cell's angle. Sorted along the arm, so
     * the iris fills in behind it rather than sweeping round like a clock hand.
     *
     * @param cells
     *            the cells, at least one
     * @param facing
     *            the way the activation switch faces, or null to guess from the opening
     * @return every cell, in the order a closing iris covers them
     */
    static List<Location> closingArm(final List<Location> cells, final BlockFace facing)
    {
        final double[] centre = centreOf(cells);
        final int[][] view = viewOf(cells, facing);
        final double[][] polar = new double[cells.size()][];
        double outermost = 0;
        for (int i = 0; i < cells.size(); i++)
        {
            final Location cell = cells.get(i);
            final double[] offset =
            {
                cell.getBlockX() - centre[0], cell.getBlockY() - centre[1], cell.getBlockZ() - centre[2]
            };
            final double right = dot(offset, view[0]);
            final double up = dot(offset, view[1]);
            // Turns clockwise from straight up, in [0, 1).
            double turn = Math.atan2(right, up) / (2 * Math.PI);
            if (turn < 0)
            {
                turn += 1;
            }
            polar[i] = new double[] { Math.hypot(right, up), turn };
            outermost = Math.max(outermost, polar[i][0]);
        }
        final double width = armWidth(outermost);
        final List<Wound> wound = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++)
        {
            final double in = (outermost - polar[i][0]) / width;
            wound.add(new Wound(cells.get(i), Math.round(in - polar[i][1]) + polar[i][1], polar[i][0]));
        }
        // Down the arm from the rim; across its width, the outer cell first.
        wound.sort(Comparator.comparingDouble(Wound::along)
            .thenComparing(Comparator.comparingDouble(Wound::radius).reversed()));
        final List<Location> order = new ArrayList<>(wound.size());
        for (final Wound w : wound)
        {
            order.add(w.cell());
        }
        return order;
    }

    /**
     * How wide the arm is, in blocks: one on a small opening, wider on a big one so it still
     * winds in about {@value #ARM_TURNS} turns at most.
     *
     * @param outermost
     *            the radius of the rim
     */
    private static double armWidth(final double outermost)
    {
        return Math.max(1, Math.ceil(outermost / ARM_TURNS));
    }

    /**
     * A cell and where it sits along the arm.
     *
     * @param cell
     *            the cell
     * @param along
     *            turns along the arm from the rim
     * @param radius
     *            its distance from the centre, which orders cells across the arm's width
     */
    private record Wound(Location cell, double along, double radius)
    {
    }

    /**
     * The opening's axes as a player at the activation switch sees them: {right, up}.
     *
     * <p>An upright gate is seen from the side its switch faces, with up as up. A flat one is
     * seen from above, with up as away from the switch. A gate with no facing is guessed at from
     * the opening, which only decides which way round it winds.
     *
     * @param facing
     *            the way the switch faces, or null
     * @return the right and up unit vectors, as {x, y, z}
     */
    private static int[][] viewOf(final List<Location> cells, final BlockFace facing)
    {
        final boolean flat = !spans(cells, 1) && spans(cells, 0) && spans(cells, 2);
        final boolean level = (facing != null) && (facing.getModY() == 0)
            && ((Math.abs(facing.getModX()) + Math.abs(facing.getModZ())) == 1);
        final int[] up;
        final int[] toViewer;
        if (flat)
        {
            toViewer = new int[] { 0, 1, 0 };
            up = level ? new int[] { -facing.getModX(), 0, -facing.getModZ() } : new int[] { 0, 0, -1 };
        }
        else
        {
            up = new int[] { 0, 1, 0 };
            if (level)
            {
                toViewer = new int[] { facing.getModX(), 0, facing.getModZ() };
            }
            else
            {
                toViewer = spans(cells, 0) ? new int[] { 0, 0, 1 } : new int[] { 1, 0, 0 };
            }
        }
        // The viewer looks against toViewer, so their right is up x toViewer.
        final int[] right =
        {
            (up[1] * toViewer[2]) - (up[2] * toViewer[1]),
            (up[2] * toViewer[0]) - (up[0] * toViewer[2]),
            (up[0] * toViewer[1]) - (up[1] * toViewer[0])
        };
        return new int[][] { right, up };
    }

    /**
     * Whether the cells differ along an axis.
     *
     * @param axis
     *            0 for x, 1 for y, 2 for z
     */
    private static boolean spans(final List<Location> cells, final int axis)
    {
        final int first = coordinate(cells.get(0), axis);
        for (final Location cell : cells)
        {
            if (coordinate(cell, axis) != first)
            {
                return true;
            }
        }
        return false;
    }

    private static int coordinate(final Location cell, final int axis)
    {
        return switch (axis)
        {
            case 0 -> cell.getBlockX();
            case 1 -> cell.getBlockY();
            default -> cell.getBlockZ();
        };
    }

    private static double dot(final double[] offset, final int[] axis)
    {
        return (offset[0] * axis[0]) + (offset[1] * axis[1]) + (offset[2] * axis[2]);
    }

}
