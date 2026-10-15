package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * One window's fixed view being built a step at a time (#609), so no tick holds the whole walk.
 *
 * <p>Built in one go, the walk behind a mirror's opening took 300 to 800 ms of one tick, once a
 * minute per watched window. A step is one x of the capture, or one column of the volume behind
 * the opening for a capture without seen air; {@link FixedBuilds} runs as many a tick as its budget
 * allows. The blocks are those {@link Windows#fixedTo} gives: the same cells, read the same way,
 * with the depth cut by a quarter and the walk begun again whenever the view will not fit.
 *
 * <p>The only live-world read is whether a real block behind the opening is empty, where the far
 * side is air: such a block is drawn as air. That read is made once per column here rather than
 * once per block through a cache, which is what most of the old walk's time went on.
 */
final class FixedBuild
{
    /** The window as it was when the build was asked for. */
    final WindowState window;
    /** The depth asked for; the view may be cut shallower to fit. */
    final int configured;
    /** When it was asked for: a change in the world after this calls for another. */
    final long startedAt;
    /** What {@link Windows#changes} stood at when it was asked for. */
    final long changesSeen;
    /** The most blocks the view may hold. */
    private final int most;
    private final World here;
    private final WindowShape shape;
    private final Capture capture;
    private final BlockData air = Bukkit.createBlockData(Material.AIR);
    private final double[] centre;
    private final int min;
    private final int max;

    private int depth;
    private double reach;
    private Map<Long, BlockData> view;
    private boolean over;
    private boolean finished;
    private boolean fits = true;
    private final boolean cutToFit;
    /** True once the walk is done and the tunnel and the limit are next, a step each. */
    private boolean walked;
    /** The tunnel's next layer to draw, once the walk is done. */
    private int tunnelLayer;
    /** The most blocks a view's map is made room for at the start: past it, it grows. */
    private static final int MOST_PRESIZE = 1_000_000;
    /** Whether a column read was of a chunk not loaded, whose blocks are then drawn as air. */
    private boolean sawUnloaded;
    /** Blocks handed to this build in its last step, kept or walked. */
    private int cellsLastStep;
    /** The walk over what the capture kept, for a capture with seen air; null otherwise. */
    private Capture.KeptWalk kept;
    /** What the kept walk hands each block to, made once rather than a step at a time. */
    private final Capture.Kept visitor = this::keptAt;
    /** The next layer and column of the volume, for a complete capture. */
    private int layer;
    private int across;
    private int acrossTo;
    private double wide;

    /** The real column a read was last made for, and what was read: loaded, and its highest block. */
    private int columnX = Integer.MIN_VALUE;
    private int columnZ = Integer.MIN_VALUE;
    private boolean columnLoaded;
    private int columnTop;

    /** The far column the kept walk was last in, and the real column it shows through. */
    private int farX = Integer.MIN_VALUE;
    private int farZ = Integer.MIN_VALUE;
    private int hereX;
    private int hereZ;
    private int hereLayer;

    /**
     * @param window
     *            the window as offered now
     * @param depth
     *            how far from the opening's middle the view reaches, at most
     * @param most
     *            the most blocks it may hold
     * @param startedAt
     *            now
     * @param changesSeen
     *            what the count of changes behind windows stands at
     * @param cutToFit
     *            true to cut the depth until the view fits; false to stop at the first walk, too many or not
     */
    FixedBuild(final WindowState window, final int depth, final int most, final long startedAt, final long changesSeen,
        final boolean cutToFit)
    {
        this.window = window;
        this.configured = depth;
        this.startedAt = startedAt;
        this.changesSeen = changesSeen;
        this.most = most;
        this.cutToFit = cutToFit;
        this.here = window.anchor().getWorld();
        this.shape = window.shape;
        this.capture = window.capture;
        this.centre = Windows.centreOf(shape);
        this.min = here.getMinHeight();
        this.max = here.getMaxHeight();
        begin(configured);
    }

    /** @return the depth the view reaches, once finished */
    int depth()
    {
        return depth;
    }

    /** @return the view, once finished: empty if it would not fit at the shallowest depth */
    Map<Long, BlockData> view()
    {
        return view;
    }

    /** @return true once the view is built */
    boolean finished()
    {
        return finished;
    }

    /** @return false if the view would not fit at the last depth tried, which leaves it empty */
    boolean fits()
    {
        return fits;
    }

    /** @return true if a column it read was of a chunk not loaded: worth building again sooner */
    boolean sawUnloaded()
    {
        return sawUnloaded;
    }

    /** @return how many blocks its last step was handed, kept or walked */
    int cellsLastStep()
    {
        return cellsLastStep;
    }

    /**
     * Whether this build is still the one a window wants: from the same capture, shape, depth and funnel.
     *
     * @param now
     *            the window as offered now
     * @return true if what it builds is what that window would draw
     */
    boolean buildsFor(final WindowState now)
    {
        return (now.capture == capture) && now.shape.equals(shape) && (now.depth() == configured)
            && Objects.equals(now.source.funnel(), window.source.funnel())
            && now.anchor().getWorld().equals(here);
    }

    /** Starts the walk again at a depth. */
    private void begin(final int to)
    {
        depth = to;
        reach = (double) to * to;
        view = new HashMap<>(capacityFor(expected()));
        over = false;
        walked = false;
        tunnelLayer = 1;
        sawUnloaded = false;
        forgetColumn();
        if (capture.complete())
        {
            kept = null;
            startLayer(1);
        }
        else
        {
            kept = capture.keptWalk();
        }
    }

    /**
     * Walks one step: up to sixteen columns of the capture, or one column of the volume; or, once the
     * walk is done, the tunnel and the check against the limit.
     *
     * @return true once the view is built
     */
    boolean step()
    {
        cellsLastStep = 0;
        if (finished)
        {
            return true;
        }
        if (walked || over)
        {
            settle();
            return finished;
        }
        // Read afresh each step: a chunk loaded a tick ago may not be now.
        forgetColumn();
        walked = (kept != null) ? kept.step(visitor) : volumeStep();
        return false;
    }

    /**
     * About how many blocks the view will hold: the room it replaces, or else its cap. Grown a doubling
     * at a time from empty, a gate's million-block map was rehashed whole inside one step.
     */
    private int expected()
    {
        final Map<Long, BlockData> before = window.fixed;
        return Math.min(Math.min(most, MOST_PRESIZE), (before == null) ? Integer.MAX_VALUE : (before.size() + (before.size() / 8)));
    }

    /** The capacity a hash map needs to hold this many without growing. */
    private static int capacityFor(final int entries)
    {
        return (int) Math.min(1L << 30, (entries * 4L / 3L) + 1L);
    }

    /** Forgets the column last read, so the next block reads its own. */
    private void forgetColumn()
    {
        columnX = Integer.MIN_VALUE;
        columnZ = Integer.MIN_VALUE;
    }

    /**
     * At the end of a walk, a step each: the tunnel drawn in a layer at a time, then the check against
     * the limit, the depth cut until the view fits. Drawn whole in one step, a big gate's tunnel took
     * tens of milliseconds.
     */
    private void settle()
    {
        final Funnel funnel = window.source.funnel();
        if (!over && (funnel != null) && (tunnelLayer < Math.min(depth, funnel.taper())))
        {
            Windows.tunnelLayer(window, view, tunnelLayer, depth, Windows.now());
            tunnelLayer++;
            return;
        }
        if (!over && (view.size() <= most))
        {
            finished = true;
            return;
        }
        if (cutToFit && (depth > 4))
        {
            begin(Math.max(4, (depth * 3) / 4));
            return;
        }
        view = new HashMap<>();
        fits = false;
        finished = true;
    }

    /** One block the capture kept, as {@link Windows#fixedTo} draws it for a capture with seen air. */
    private void keptAt(final int fx, final int fy, final int fz, final boolean farAir)
    {
        cellsLastStep++;
        if (over)
        {
            return;
        }
        if ((fx != farX) || (fz != farZ))
        {
            final Spot at = shape.hereOf(fx, shape.far().y(), fz);
            farX = fx;
            farZ = fz;
            hereX = at.x();
            hereZ = at.z();
            hereLayer = ((hereX - shape.base().x()) * shape.into().x()) + ((hereZ - shape.base().z()) * shape.into().z());
        }
        final int y = shape.base().y() + (fy - shape.far().y());
        if ((hereLayer < 1) || (y < min) || (y >= max) || !window.showsFar(hereX, y, hereZ) || !within(hereX, y, hereZ))
        {
            return;
        }
        if (!farAir)
        {
            view.put(Windows.key(hereX, y, hereZ), Windows.turned(window, capture.at(fx, fy, fz)));
        }
        else if (!reallyEmpty(hereX, y, hereZ))
        {
            view.put(Windows.key(hereX, y, hereZ), air);
        }
        over = view.size() > most;
    }

    /** Whether a block's middle is within the depth of the opening's middle. */
    private boolean within(final int x, final int y, final int z)
    {
        final double dx = (x + 0.5) - centre[0];
        final double dy = (y + 0.5) - centre[1];
        final double dz = (z + 0.5) - centre[2];
        return ((dx * dx) + (dy * dy) + (dz * dz)) < reach;
    }

    /** Sets the volume walk to the start of a layer, past the last layer when there is none. */
    private void startLayer(final int to)
    {
        layer = to;
        final boolean alongX = shape.into().x() != 0;
        final double centreAcross = centre[alongX ? 2 : 0];
        wide = Math.sqrt(reach - ((double) layer * layer));
        across = (int) Math.floor(centreAcross - wide);
        acrossTo = (int) Math.ceil(centreAcross + wide);
    }

    /**
     * One column of the volume behind the opening, as {@link Windows#fixedTo} walks it for a complete
     * capture: every block of it inside the depth.
     *
     * @return true once the last column is walked
     */
    private boolean volumeStep()
    {
        if (layer >= depth)
        {
            return true;
        }
        final boolean alongX = shape.into().x() != 0;
        final int sign = alongX ? shape.into().x() : shape.into().z();
        final int along = (alongX ? shape.base().x() : shape.base().z()) + (sign * layer);
        final int x = alongX ? along : across;
        final int z = alongX ? across : along;
        final double offAcross = (across + 0.5) - centre[alongX ? 2 : 0];
        final Spot far = shape.farOf(x, shape.base().y(), z);
        final int yTo = Math.min(max - 1, (int) Math.ceil(centre[1] + wide));
        for (int y = Math.max(min, (int) Math.floor(centre[1] - wide)); (y <= yTo) && !over; y++)
        {
            final double offY = (y + 0.5) - centre[1];
            if ((((double) layer * layer) + (offAcross * offAcross) + (offY * offY)) < reach)
            {
                volumeAt(x, y, z, far.x(), far.y() + (y - shape.base().y()), far.z());
            }
        }
        across++;
        if (across > acrossTo)
        {
            startLayer(layer + 1);
        }
        return layer >= depth;
    }

    /** One block of the volume: the far side's block where it is not air, and air where the real block is not empty. */
    private void volumeAt(final int x, final int y, final int z, final int fx, final int fy, final int fz)
    {
        cellsLastStep++;
        if (!window.showsFar(x, y, z) || !capture.contains(fx, fy, fz))
        {
            return;
        }
        if (!capture.isAir(fx, fy, fz))
        {
            view.put(Windows.key(x, y, z), Windows.turned(window, capture.at(fx, fy, fz)));
        }
        else if (!reallyEmpty(x, y, z))
        {
            view.put(Windows.key(x, y, z), air);
        }
        over = view.size() > most;
    }

    /**
     * Whether a real block is empty: above its column's highest block it is. As
     * {@link WindowSight#reallyEmpty}, with the column read once for the run of blocks in it.
     */
    private boolean reallyEmpty(final int x, final int y, final int z)
    {
        if ((x != columnX) || (z != columnZ))
        {
            columnX = x;
            columnZ = z;
            columnLoaded = here.isChunkLoaded(x >> 4, z >> 4);
            sawUnloaded |= !columnLoaded;
            columnTop = columnLoaded ? here.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) : Integer.MAX_VALUE;
        }
        return (y > columnTop) || (columnLoaded && here.getBlockAt(x, y, z).isEmpty());
    }
}
