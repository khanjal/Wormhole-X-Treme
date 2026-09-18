package com.wormhole_xtreme.wormhole.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * The inner ring turning while a gate dials, drawn as a light travelling round the frame (#357).
 *
 * <p>On a Milky Way gate the ring turns until each glyph sits under the top chevron, clockwise for
 * the first glyph, anticlockwise for the second, and so on. The frame cannot turn, so a short
 * lit segment travels half the ring instead: from the point opposite the top chevron to the top,
 * alternating direction each glyph. When it arrives, that glyph's chevron locks.
 *
 * <p>The ring is the layer holding the top chevron, ordered by angle round its centre, clockwise
 * as seen from the DHD with the top chevron at 0. A horizontal gate works the same way, its far
 * edge being its top.
 */
public final class DialSpin
{
    private final List<Cell> ring;
    private final double[] angles;

    private DialSpin(final List<Cell> ring, final double[] angles)
    {
        this.ring = ring;
        this.angles = angles;
    }

    /**
     * Orders a gate's ring for the spin.
     *
     * @param cells
     *            the gate's cells, as {@link GateBlueprint#of} lays them
     * @param grid
     *            the grid they were laid on
     * @return the spin, or null for a gate with no top chevron to travel to
     */
    public static DialSpin of(final List<Cell> cells, final GateGrid grid)
    {
        final List<Cell> top = cells.stream().filter(c -> c.wave() == Stargate.LOCAL_CHEVRONS).toList();
        if (top.isEmpty())
        {
            return null;
        }
        // The ring is the plane the chevrons lie in: across the facing for a standing gate, level
        // for one lying flat. It is the axis the lit cells spread least along.
        final List<Cell> lit = cells.stream().filter(c -> (c.wave() > 0) && (c.wave() <= Stargate.LOCAL_CHEVRONS)).toList();
        final int normal = narrowestAxis(lit);
        final int level = coordinate(top.get(0), normal);
        final List<Cell> ring = cells.stream()
            .filter(c -> !c.dhd() && (coordinate(c, normal) == level) && ((c.part() == Part.FRAME) || (c.part() == Part.CHEVRON)))
            .toList();
        final double[] centre = centroid(ring);
        final double[] up = unit(minus(centroid(top), centre));
        // GateGrid's right steps its columns, which count from the viewer's right; the viewer's
        // right is the other way.
        final double[] right = { -grid.right().getModX(), 0, -grid.right().getModZ() };
        final List<Cell> ordered = new ArrayList<>(ring);
        ordered.sort(Comparator.comparingDouble(c -> angle(c, centre, up, right)));
        final double[] angles = new double[ordered.size()];
        for (int i = 0; i < angles.length; i++)
        {
            angles[i] = angle(ordered.get(i), centre, up, right);
        }
        return new DialSpin(List.copyOf(ordered), angles);
    }

    /** @return the ring, clockwise from the top */
    public List<Cell> ring()
    {
        return ring;
    }

    /**
     * The clockwise angle of a cell round the ring, the top chevron being 0.
     *
     * @param cell
     *            a ring cell
     * @return its angle, 0 to 2 pi
     */
    public double angleOf(final Cell cell)
    {
        return angles[ring.indexOf(cell)];
    }

    /**
     * The cells a glyph's light passes, from opposite the top to the top: clockwise for odd glyphs,
     * anticlockwise for even ones.
     *
     * @param glyph
     *            which glyph, from 1
     * @return the path, its last cell at the top
     */
    public List<Cell> path(final int glyph)
    {
        final int n = ring.size();
        final int opposite = nearest(Math.PI);
        final int top = nearest(0);
        final int step = ((glyph % 2) == 1) ? 1 : -1;
        final List<Cell> path = new ArrayList<>();
        int i = opposite;
        while (true)
        {
            path.add(ring.get(i));
            if ((i == top) || (path.size() > n))
            {
                return path;
            }
            i = Math.floorMod(i + step, n);
        }
    }

    /**
     * The cells lit at one tick of a glyph's spin: a short run ending at the head.
     *
     * @param glyph
     *            which glyph, from 1
     * @param tick
     *            the tick, from 0
     * @param ticks
     *            how many ticks the spin takes
     * @return the lit cells
     */
    public Set<Cell> comet(final int glyph, final int tick, final int ticks)
    {
        final List<Cell> path = path(glyph);
        final int last = path.size() - 1;
        final int head = (ticks <= 1) ? last : (int) Math.round(((double) tick * last) / (ticks - 1));
        final int length = Math.max(2, ring.size() / 16);
        final Set<Cell> lit = new LinkedHashSet<>();
        for (int i = Math.max(0, head - length + 1); i <= Math.min(head, last); i++)
        {
            lit.add(path.get(i));
        }
        return lit;
    }

    /** The axis (0 x, 1 y, 2 z) the cells spread least along. */
    private static int narrowestAxis(final List<Cell> cells)
    {
        int best = 0;
        int bestSpread = Integer.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++)
        {
            final int a = axis;
            final int spread = cells.stream().mapToInt(c -> coordinate(c, a)).max().orElse(0)
                - cells.stream().mapToInt(c -> coordinate(c, a)).min().orElse(0);
            if (spread < bestSpread)
            {
                bestSpread = spread;
                best = axis;
            }
        }
        return best;
    }

    private static int coordinate(final Cell c, final int axis)
    {
        if (axis == 0)
        {
            return c.x();
        }
        return (axis == 1) ? c.y() : c.z();
    }

    /** The ring index whose angle is nearest the given one, going round. */
    private int nearest(final double target)
    {
        int best = 0;
        double bestGap = Double.MAX_VALUE;
        for (int i = 0; i < angles.length; i++)
        {
            final double raw = Math.abs(angles[i] - target) % (2 * Math.PI);
            final double gap = Math.min(raw, (2 * Math.PI) - raw);
            if (gap < bestGap)
            {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }

    private static double angle(final Cell c, final double[] centre, final double[] up, final double[] right)
    {
        final double[] v = minus(new double[] { c.x(), c.y(), c.z() }, centre);
        final double a = Math.atan2(dot(v, right), dot(v, up));
        return (a < 0) ? (a + (2 * Math.PI)) : a;
    }

    private static double[] centroid(final List<Cell> cells)
    {
        final double[] sum = new double[3];
        for (final Cell c : cells)
        {
            sum[0] += c.x();
            sum[1] += c.y();
            sum[2] += c.z();
        }
        final int n = Math.max(1, cells.size());
        return new double[] { sum[0] / n, sum[1] / n, sum[2] / n };
    }

    private static double[] minus(final double[] a, final double[] b)
    {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    private static double dot(final double[] a, final double[] b)
    {
        return (a[0] * b[0]) + (a[1] * b[1]) + (a[2] * b[2]);
    }

    private static double[] unit(final double[] v)
    {
        final double length = Math.sqrt(dot(v, v));
        return (length == 0) ? new double[] { 0, 1, 0 } : new double[] { v[0] / length, v[1] / length, v[2] / length };
    }
}
