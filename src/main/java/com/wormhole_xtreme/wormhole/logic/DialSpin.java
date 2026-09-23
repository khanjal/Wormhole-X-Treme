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
 * <p>On a Milky Way gate the ring turns each glyph into place, clockwise for the first glyph,
 * anticlockwise for the second, and so on. The frame cannot turn, so a short lit segment travels
 * half the ring instead, ending on the chevron about to lock and alternating direction each glyph.
 * When it arrives, that chevron locks, so the light is seen to land where the chevron lights.
 *
 * <p>The ring is the front layer of chevrons, nearest the DHD, ordered by angle round its centre,
 * clockwise as seen from the DHD with the top chevron at 0. A horizontal gate works the same way, its far
 * edge being its top.
 */
public final class DialSpin
{
    private final List<Cell> ring;
    private final double[] angles;
    /** Each chevron's angle round the ring by glyph, NaN for one not on it. */
    private final double[] chevrons;

    private DialSpin(final List<Cell> ring, final double[] angles, final double[] chevrons)
    {
        this.ring = ring;
        this.angles = angles;
        this.chevrons = chevrons;
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
        // A gate lit on more than one layer (Grand, Massive) turns on the front one, nearest the
        // DHD, as the show's ring is the face you look at. Layers count up toward the DHD.
        final Cell front = lit.stream().max(Comparator.comparingInt(Cell::layer)).orElse(top.get(0));
        final int level = coordinate(front, normal);
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
        return new DialSpin(List.copyOf(ordered), angles, chevronAngles(ordered, angles));
    }

    /** The mean angle of each chevron's cells on the ring, taken round the circle so the top's two sides agree. */
    private static double[] chevronAngles(final List<Cell> ring, final double[] angles)
    {
        final double[] sin = new double[Stargate.OTHER_WORLD_CHEVRON + 1];
        final double[] cos = new double[sin.length];
        for (int i = 0; i < ring.size(); i++)
        {
            final int wave = ring.get(i).wave();
            if ((wave > 0) && (wave < sin.length))
            {
                sin[wave] += Math.sin(angles[i]);
                cos[wave] += Math.cos(angles[i]);
            }
        }
        final double[] chevrons = new double[sin.length];
        for (int wave = 0; wave < chevrons.length; wave++)
        {
            chevrons[wave] = ((sin[wave] == 0) && (cos[wave] == 0)) ? Double.NaN : Math.atan2(sin[wave], cos[wave]);
        }
        return chevrons;
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
     * The cells a glyph's light passes, half the ring ending on that glyph's chevron: clockwise for
     * odd glyphs, anticlockwise for even ones. A chevron not on the ring is taken as the top.
     *
     * @param glyph
     *            which glyph, from 1
     * @return the path, its last cell on the chevron
     */
    public List<Cell> path(final int glyph)
    {
        final double target = chevronAngle(glyph);
        return route(nearest(target + Math.PI), nearest(target), alternating(glyph));
    }

    /**
     * The cells a glyph's light passes under a pattern, ending on the glyph's chevron, or on the
     * top for {@link DialSpinPattern#TOP}. {@link DialSpinPattern#PEGASUS} starts from the chevron
     * locked before it, the top for the first glyph, so its length varies from glyph to glyph.
     * {@link DialSpinPattern#UNIVERSE} turns the whole ring and has no one path; this is
     * {@link DialSpinPattern#CHEVRON}'s for it.
     *
     * @param pattern
     *            how the light moves
     * @param glyph
     *            which glyph, from 1
     * @return the path, its last cell where the light lands
     */
    public List<Cell> path(final DialSpinPattern pattern, final int glyph)
    {
        final int end = nearest(chevronAngle(glyph));
        return switch (pattern)
        {
            case TOP -> route(nearest(Math.PI), nearest(0.0), alternating(glyph));
            case LAP -> route(Math.floorMod(end + 1, ring.size()), end, 1);
            case PEGASUS -> route(nearest((glyph <= 1) ? 0.0 : chevronAngle(glyph - 1)), end, -alternating(glyph));
            case CHASE -> (glyph <= 1) ? route(Math.floorMod(end - 1, ring.size()), end, -1)
                : route(nearest(chevronAngle(glyph - 1)), end, -alternating(glyph));
            case OVERSHOOT -> overshoot(path(glyph), alternating(glyph));
            default -> path(glyph);
        };
    }

    /** A path run on past its end by a comet's length, then back onto it. */
    private List<Cell> overshoot(final List<Cell> path, final int step)
    {
        final int end = ring.indexOf(path.get(path.size() - 1));
        final int past = Math.min(tail(), ring.size() / 4);
        final List<Cell> out = new ArrayList<>(path);
        for (int i = 1; i <= past; i++)
        {
            out.add(ring.get(Math.floorMod(end + (step * i), ring.size())));
        }
        for (int i = past - 1; i >= 0; i--)
        {
            out.add(ring.get(Math.floorMod(end + (step * i), ring.size())));
        }
        return out;
    }

    /** How long the light's run is. */
    private int tail()
    {
        return Math.max(2, ring.size() / 16);
    }

    /** Ticks the {@link DialSpinPattern#TOP} light rests on the top chevron after each lock, before the ring turns again. */
    public static final int TOP_HOLD_TICKS = 10;

    /** Ticks a glyph's light rests where the one before landed, before it sets off. */
    private static int hold(final DialSpinPattern pattern, final int glyph)
    {
        return ((pattern == DialSpinPattern.TOP) && (glyph > 1)) ? TOP_HOLD_TICKS : 0;
    }

    /**
     * How many ticks a glyph's turn takes under a pattern: the chevron's interval, and any rest
     * before it.
     *
     * @param pattern
     *            how the light moves
     * @param glyph
     *            which glyph, from 1
     * @param interval
     *            the chevron's interval, in ticks
     * @return the frames to play before the chevron locks
     */
    public int frames(final DialSpinPattern pattern, final int glyph, final int interval)
    {
        return hold(pattern, glyph) + travel(pattern, glyph, interval);
    }

    /** The most of the ring a {@link DialSpinPattern#UNIVERSE} turn covers in a tick, as a fraction: past it, it reads as flicker. */
    private static final int UNIVERSE_PACE = 12;

    /**
     * Ticks a glyph's light travels: the chevron's interval, or for UNIVERSE, whose turns run to a
     * lap and more, as long as its pace needs.
     */
    private int travel(final DialSpinPattern pattern, final int glyph, final int interval)
    {
        final int ticks = Math.max(1, interval);
        if (pattern != DialSpinPattern.UNIVERSE)
        {
            return ticks;
        }
        // One more than the steps, as the first tick is where it starts.
        final int paced = (int) Math.ceil((Math.abs(turn(glyph)) * (double) UNIVERSE_PACE) / ring.size()) + 1;
        return Math.max(ticks, paced);
    }

    /**
     * The cells lit at one frame of a glyph's turn: first any rest where the glyph before
     * landed, then the light travelling over the chevron's interval.
     *
     * @param pattern
     *            how the light moves
     * @param glyph
     *            which glyph, from 1
     * @param frame
     *            the frame, from 0, below {@link #frames}
     * @param interval
     *            the chevron's interval, in ticks
     * @return the lit cells
     */
    public Set<Cell> frame(final DialSpinPattern pattern, final int glyph, final int frame, final int interval)
    {
        final int hold = hold(pattern, glyph);
        return (frame < hold) ? topChevron() : lit(pattern, glyph, frame - hold, travel(pattern, glyph, interval));
    }

    /**
     * The cells that stay lit as a glyph's chevron locks, until the next glyph's turn takes them
     * back: the top chevron alone for {@link DialSpinPattern#TOP}, which rests there, and every
     * chevron locked so far, where the ring has carried it, for {@link DialSpinPattern#UNIVERSE}.
     * Once its top chevron locks, those stand in their own places and stay lit while the gate is open.
     *
     * @param pattern
     *            how the light moves
     * @param glyph
     *            the glyph locking, from 1
     * @param last
     *            the dial's last glyph, after which the top chevron needs no light of its own
     * @return the cells, empty for a pattern whose light goes as the chevron locks
     */
    public Set<Cell> rest(final DialSpinPattern pattern, final int glyph, final int last)
    {
        if (pattern == DialSpinPattern.UNIVERSE)
        {
            return universe(glyph, 1, 1, true);
        }
        return ((pattern == DialSpinPattern.TOP) && (glyph < last)) ? topChevron() : Set.of();
    }

    /** The top chevron's own cells on the ring, or the cell nearest the top on a ring without them. */
    private Set<Cell> topChevron()
    {
        final Set<Cell> top = chevron(Stargate.LOCAL_CHEVRONS);
        return top.isEmpty() ? Set.of(ring.get(nearest(0.0))) : top;
    }

    /** One chevron's cells on the ring, empty for one not on it. */
    private Set<Cell> chevron(final int wave)
    {
        final Set<Cell> cells = new LinkedHashSet<>();
        for (final Cell cell : ring)
        {
            if (cell.wave() == wave)
            {
                cells.add(cell);
            }
        }
        return cells;
    }

    /**
     * How many cells, signed, the ring turns for a glyph: from where the last left it to where this
     * one's chevron stands at the top, the way {@link #alternating} says, with a whole turn more
     * when that is under half of one. Each glyph gets about a full spin.
     */
    private int turn(final int glyph)
    {
        final int n = ring.size();
        final int ahead = Math.floorMod(turned(glyph) - turned(glyph - 1), n);
        int cells = (alternating(glyph) > 0) ? ahead : (n - ahead);
        if (cells == 0)
        {
            cells = n;
        }
        else if ((2 * cells) < n)
        {
            cells += n;
        }
        return alternating(glyph) * cells;
    }

    /**
     * How far round the ring stands once a glyph has locked: with that glyph's chevron at the
     * top, so after the top chevron's own glyph every chevron is back in its place. The ring
     * starts there, and a glyph past the top chevron's locks in its chevron's own place.
     */
    private int turned(final int glyph)
    {
        if ((glyph <= 0) || (glyph >= Stargate.LOCAL_CHEVRONS))
        {
            return 0;
        }
        return nearest(0.0) - nearest(chevronAngle(glyph));
    }

    /**
     * Destiny's ring part way through a glyph's turn: each chevron locked so far, and the top
     * chevron as the point of origin from the start, carried round with the ring. Each is its
     * chevron's own cells, so no two ever share one.
     */
    private Set<Cell> universe(final int glyph, final int tick, final int ticks, final boolean landed)
    {
        final int n = ring.size();
        final double progress = (ticks <= 1) ? 1.0 : ((double) tick / (ticks - 1));
        final int now = turned(glyph - 1) + (int) Math.round(turn(glyph) * progress);
        final Set<Cell> riding = new LinkedHashSet<>(topChevron());
        for (int k = 1; k <= (landed ? glyph : (glyph - 1)); k++)
        {
            riding.addAll(chevron(k));
        }
        final Set<Cell> lit = new LinkedHashSet<>();
        for (final Cell cell : riding)
        {
            lit.add(ring.get(Math.floorMod(ring.indexOf(cell) + now, n)));
        }
        return lit;
    }

    /**
     * The cells lit at one tick of a glyph's spin under a pattern. The light reaches the end of
     * its path on the last tick, whatever the pattern, so every pattern takes the same time.
     *
     * @param pattern
     *            how the light moves
     * @param glyph
     *            which glyph, from 1
     * @param tick
     *            the tick, from 0
     * @param ticks
     *            how many ticks the spin takes
     * @return the lit cells
     */
    public Set<Cell> lit(final DialSpinPattern pattern, final int glyph, final int tick, final int ticks)
    {
        if (pattern == DialSpinPattern.UNIVERSE)
        {
            return universe(glyph, tick, ticks, false);
        }
        final List<Cell> path = path(pattern, glyph);
        final int last = path.size() - 1;
        final int head = (ticks <= 1) ? last : (int) Math.round(((double) tick * last) / (ticks - 1));
        if (pattern == DialSpinPattern.PEGASUS)
        {
            return pegasus(path, glyph, head);
        }
        return run(path, head, (pattern == DialSpinPattern.FILL) ? (head + 1) : tail());
    }

    /**
     * A Pegasus step: the run jumps a glyph's width rather than sliding, over the frame between the
     * chevron it sets off from and the one it lands on, and lands as that chevron alone. Neither
     * chevron is lit with the frame beside it.
     */
    private Set<Cell> pegasus(final List<Cell> path, final int glyph, final int head)
    {
        final int last = path.size() - 1;
        final Set<Cell> chevron = chevron(glyph);
        final Set<Cell> landed = chevron.isEmpty() ? Set.of(path.get(last)) : chevron;
        final Set<Cell> from = chevron((glyph <= 1) ? Stargate.LOCAL_CHEVRONS : (glyph - 1));
        // A chevron's cells can sit among frame cells, as Grand's do: up to the first reached.
        final List<Cell> between = new ArrayList<>();
        for (int i = 0; (i < last) && !chevron.contains(path.get(i)); i++)
        {
            if (!from.contains(path.get(i)))
            {
                between.add(path.get(i));
            }
        }
        if ((head >= last) || between.isEmpty())
        {
            return landed;
        }
        final int length = Math.max(2, ring.size() / GLYPHS);
        final int at = (int) (((long) head * between.size()) / last);
        return run(between, Math.min(((at / length) * length) + (length - 1), between.size() - 1), length);
    }

    /** The cells of a path from a run's tail up to its head. */
    private static Set<Cell> run(final List<Cell> path, final int head, final int length)
    {
        final Set<Cell> lit = new LinkedHashSet<>();
        for (int i = Math.max(0, head - length + 1); i <= head; i++)
        {
            lit.add(path.get(i));
        }
        return lit;
    }

    /** How many glyphs a Pegasus step takes the width of: 36 round an Atlantis gate, read as nine. */
    private static final int GLYPHS = 9;

    /** The angle a glyph's light lands on: its chevron's, or the top's for one not on the ring. */
    private double chevronAngle(final int glyph)
    {
        return ((glyph > 0) && (glyph < chevrons.length) && !Double.isNaN(chevrons[glyph])) ? chevrons[glyph] : 0.0;
    }

    /** Clockwise for odd glyphs, anticlockwise for even ones. */
    private static int alternating(final int glyph)
    {
        return ((glyph % 2) == 1) ? 1 : -1;
    }

    /** The ring cells from one index to another, stepping one way round. */
    private List<Cell> route(final int start, final int end, final int step)
    {
        final int n = ring.size();
        final List<Cell> path = new ArrayList<>();
        int i = start;
        while (true)
        {
            path.add(ring.get(i));
            if ((i == end) || (path.size() >= n))
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
        return lit(DialSpinPattern.CHEVRON, glyph, tick, ticks);
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
