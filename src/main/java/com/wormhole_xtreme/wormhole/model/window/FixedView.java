package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.ACTIVE;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.CHANGED;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.FIRST_CHANGED;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.changes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * A walled window's fixed view: when the one held is still the one to draw, how it is built and put in, the
 * tunnel into it and where a block lies inside it (#430).
 *
 * <p>Moved out of {@link Windows} verbatim, but for {@code fixedView}, {@code install} and
 * {@code TOO_MANY} made package-private.
 */
final class FixedView
{
    /** How old a fixed view may get before it is built again with nothing reported changed: for what no event says (#609). */
    static final long BACKSTOP_MILLIS = 600_000L;

    /** Least time between a view's being put in and its being built again for a change behind its wall. */
    static final long REBUILD_GAP_MILLIS = Windows.RESAMPLE_MILLIS;

    /** The longest that wait grows to while changes keep coming: a flowing river behind the wall (#609). */
    static final long MOST_REBUILD_WAIT_MILLIS = 120_000L;

    /** How many times as long as its build took a view waits, at least, before it is built again for a change. */
    static final long BUILD_REST_FACTOR = 4L;

    /** The most a view built over a chunk that was not loaded, whose blocks there were drawn as air, waits to be built again. */
    static final long PARTIAL_BACKSTOP_MILLIS = 60_000L;

    /**
     * The first such wait, doubled each time the build that follows still finds a chunk not loaded:
     * the first look at a window often comes while the chunks behind it are still loading, and that
     * room is drawn with air through them, past its cap and cut shallower.
     */
    static final long FIRST_PARTIAL_RETRY_MILLIS = 15_000L;

    /** What {@link #fixedTo} gives for a view past its limit, told apart by identity from any real view. */
    static final Map<Long, BlockData> TOO_MANY = Collections.unmodifiableMap(new HashMap<>());

    /** Static helpers only. */
    private FixedView()
    {
    }

    /**
     * Whether a window's fixed view is the one to draw: there, from its current capture, depth and
     * tunnel; younger than the backstop ({@link #BACKSTOP_MILLIS}, or {@link #PARTIAL_BACKSTOP_MILLIS}
     * for one built over a chunk that was not loaded); and either nothing has changed behind its wall
     * since its build began, or it was put in too recently to be built again ({@link #rebuildWait}).
     */
    static boolean fixedIsFresh(final WindowState window, final long now)
    {
        if ((window.fixed == null) || (window.fixedFrom != window.capture) || (window.fixedFor != window.depth())
            || !Objects.equals(window.fixedFunnel, window.source.funnel()))
        {
            return false;
        }
        final long age = now - window.fixedAt;
        if (age >= (window.fixedPartial ? window.fixedPartialWait : BACKSTOP_MILLIS))
        {
            return false;
        }
        return !changedSince(window) || (age < rebuildWait(window));
    }

    /** Whether a block has changed behind a window's wall since its view's build began. */
    private static boolean changedSince(final WindowState window)
    {
        return CHANGED.getOrDefault(window.name(), 0L) > window.fixedChanges;
    }

    /**
     * How long after its view is put in a window waits before building it again for a change: the
     * gap, longer for a view whose build took long, and longer again while changes keep coming.
     *
     * <p>From when the view went in, not when its build began: a build that outlasted the gap and
     * saw a change while it ran started the next one at once, and anything churning behind a
     * watched window -- water flowing, a farm, fire -- kept a build running for good.
     */
    static long rebuildWait(final WindowState window)
    {
        return Math.max(Math.max(REBUILD_GAP_MILLIS, window.fixedWait), BUILD_REST_FACTOR * window.fixedTook);
    }

    /**
     * How deep a change behind a window can matter: the depth its room was built to, which a cut to fit
     * makes shallower than asked; the depth asked while a build is under way, which may reach it.
     *
     * <p>Judged at the depth asked, a mirror at 160 cut to 67 was made stale by a sheep 150 blocks
     * behind its wall. A change past the cut that would let the room fit deeper waits for the backstop.
     *
     * @return blocks from the opening's middle
     */
    static int judgedDepth(final WindowState window)
    {
        if ((window.fixed == null) || FixedBuilds.building(window.name()))
        {
            return window.depth();
        }
        return Math.min(window.depth(), window.fixedDepth);
    }

    /**
     * Whether a mirror's fixed view is held, for a test.
     *
     * @param name
     *            the mirror
     * @return true if its window holds one
     */
    static boolean holdsFixedView(final String name)
    {
        final WindowState window = ACTIVE.get(name);
        return (window != null) && (window.fixed != null);
    }

    /**
     * Asks for a walled window's fixed view to be built: everything behind its wall out to the depth
     * from the middle of its opening, the same whichever eye looks.
     *
     * <p>Trimmed to each eye, a view changed with every step, and a mirror seen from close up
     * reached less far than from a step back: blocks appeared and vanished as a viewer walked.
     * In a wall there is nothing to trim -- the wall hides whatever lies beside the opening -- so
     * the whole far side is drawn once, and built again when something behind the wall changes.
     * The cost is that a viewer sees the far side in place of the real world behind that wall
     * from anywhere else they can see it, a doorway round the side, while they are looking in.
     * A view past {@link Windows#mostFixedFor} blocks is cut shallower until it fits.
     *
     * <p>Built a few milliseconds a tick (#609): in one go it was a tick of most of a second, once a
     * minute per watched window. The old view, if any, is drawn until the new one is whole.
     */
    static void fixedView(final WindowState window)
    {
        final FixedBuild running = FixedBuilds.buildFor(window.name());
        if ((running != null) && running.buildsFor(window))
        {
            return;
        }
        FixedRegions.index(window);
        final FixedBuild build = new FixedBuild(window, window.depth(), Windows.mostFixedFor(window), changes, true);
        if (FixedBuilds.request(build, FixedView::builtLater))
        {
            install(window, build);
        }
    }

    /**
     * Puts a finished build in as a window's fixed view, and sets how long it waits before a change
     * builds it again: doubled, up to {@link #MOST_REBUILD_WAIT_MILLIS}, while changes keep coming --
     * during the build, or within the last wait after the last view went in -- and back to the gap
     * once they stop.
     */
    static void install(final WindowState window, final FixedBuild build)
    {
        final long now = Windows.now();
        final Long firstChange = FIRST_CHANGED.remove(window.name());
        final boolean duringBuild = CHANGED.getOrDefault(window.name(), 0L) > build.changesSeen;
        final boolean soonAfter = (window.fixed != null) && (firstChange != null)
            && ((firstChange - window.fixedAt) < rebuildWait(window));
        window.fixedWait = (duringBuild || soonAfter)
            ? Math.min(MOST_REBUILD_WAIT_MILLIS, Math.max(REBUILD_GAP_MILLIS, window.fixedWait * 2L)) : 0L;
        window.fixed = build.view();
        window.fixedFrom = build.window.capture;
        window.fixedAt = now;
        // Put in is a use: the next sweep's offer carries a room only if it was used within the minute.
        window.fixedUsedAt = now;
        // The build's own stepped time, not the time since it began: several builds share a tick's share.
        window.fixedTook = build.steppedMillis();
        window.fixedFor = build.configured;
        window.fixedDepth = build.depth();
        window.fixedChanges = build.changesSeen;
        window.fixedFunnel = build.window.source.funnel();
        window.fixedPartialWait = partialWaitAfter(window, build.sawUnloaded());
        window.fixedPartial = build.sawUnloaded();
    }

    /**
     * How long a view just built waits to be built again for a chunk its build found not loaded: none
     * for a view that found none, the first retry after one that did not, then twice the last.
     */
    private static long partialWaitAfter(final WindowState window, final boolean partial)
    {
        if (!partial)
        {
            return 0L;
        }
        final long next = window.fixedPartial ? (window.fixedPartialWait * 2L) : FIRST_PARTIAL_RETRY_MILLIS;
        return Math.min(PARTIAL_BACKSTOP_MILLIS, next);
    }

    /**
     * Puts a build finished on a later tick in, if its window is still offered as it was; a window
     * changed meanwhile asks for another on its next look.
     *
     * <p>Nobody is redrawn here: the next sweep, within a second, finds the room changed and redraws
     * whoever looks into it, as it always has. Redrawn here as well, every viewer's redraw landed in
     * the build's own tick, on top of its share.
     */
    static void builtLater(final FixedBuild build)
    {
        final WindowState window = ACTIVE.get(build.window.name());
        if ((window != null) && build.buildsFor(window))
        {
            install(window, build);
        }
    }

    /**
     * A walled window's view to a depth, or {@link #TOO_MANY} if it would hold more than {@code most};
     * narrowed to its funnel, if it has one. All of it at once: for an admin's whole view, and tests.
     */
    static Map<Long, BlockData> fixedTo(final WindowState window, final int depth, final int most)
    {
        final FixedBuild build = new FixedBuild(window, depth, most, changes, false);
        while (!build.step())
        {
            // Sixteen columns of the capture, a column of the volume, or a layer of the tunnel, a step.
        }
        return build.fits() ? build.view() : TOO_MANY;
    }

    /** @return true for the view {@link #fixedTo} gives past its limit */
    static boolean tooMany(final Map<Long, BlockData> view)
    {
        return view == TOO_MANY;
    }

    /**
     * Draws a tunnel into a view, short of the depth (#583): hollow inside, and a wall two blocks
     * thick in its gate's portal material round it, ending in a collar round the far gate's plane.
     *
     * <p>Only the wall, not everything outside: every line of sight that leaves the tunnel meets the
     * wall first, and the far side outside the room past it was never put in the view. Hollow as the
     * far side's seen air is: air only where the real block is not empty.
     *
     * @return the view, or {@link #TOO_MANY} if it now holds more than {@code most}
     */
    static Map<Long, BlockData> tunnelled(final WindowState window, final Map<Long, BlockData> view, final int depth,
        final long now, final int most)
    {
        final Funnel funnel = window.source.funnel();
        if ((funnel == null) || (view == TOO_MANY))
        {
            return view;
        }
        for (int layer = 1; layer < Math.min(depth, funnel.taper()); layer++)
        {
            tunnelLayer(window, view, layer, depth, now);
        }
        return (view.size() > most) ? TOO_MANY : view;
    }

    /**
     * Draws one layer of a tunnel into a view: its wall, and its hollow where the real block is not
     * empty. The wall and the hollow never share a cell, so a layer at a time draws what
     * {@link #tunnelled} draws (#609).
     *
     * @param layer
     *            the layer, from 1
     */
    static void tunnelLayer(final WindowState window, final Map<Long, BlockData> view, final int layer, final int depth,
        final long now)
    {
        final Funnel funnel = window.source.funnel();
        if (funnel == null)
        {
            return;
        }
        final WindowShape shape = window.shape;
        final World here = window.anchor().getWorld();
        final int min = here.getMinHeight();
        final int max = here.getMaxHeight();
        final double[] centre = centreOf(shape);
        final double reach = (double) depth * depth;
        final BlockData wall = Bukkit.createBlockData(funnel.wall());
        final BlockData air = Bukkit.createBlockData(Material.AIR);
        // Never over the gate's own frame, such as a ring a block behind its opening.
        final Set<Spot> own = window.source.structure();
        funnel.wallOn(layer, depth, (at, across, up) ->
        {
            final Spot cell = shape.behind(at, across, up);
            if (drawable(cell, min, max, centre, reach) && !own.contains(cell))
            {
                view.put(Cells.key(cell.x(), cell.y(), cell.z()), wall);
            }
        });
        funnel.hollowOn(layer, depth, (at, across, up) ->
        {
            final Spot cell = shape.behind(at, across, up);
            if (drawable(cell, min, max, centre, reach) && !WindowSight.reallyEmpty(here, cell.x(), cell.y(), cell.z(), now))
            {
                view.put(Cells.key(cell.x(), cell.y(), cell.z()), air);
            }
        });
    }

    /** Whether a block is inside the world's height and within a view's depth of its opening's middle. */
    private static boolean drawable(final Spot at, final int min, final int max, final double[] centre,
        final double reach)
    {
        final double dx = (at.x() + 0.5) - centre[0];
        final double dy = (at.y() + 0.5) - centre[1];
        final double dz = (at.z() + 0.5) - centre[2];
        return (at.y() >= min) && (at.y() < max) && (((dx * dx) + (dy * dy) + (dz * dz)) < reach);
    }

    /**
     * The middle of a window's opening, which its depth is measured from.
     *
     * @return {@code {x, y, z}}
     */
    static double[] centreOf(final WindowShape shape)
    {
        final boolean alongX = shape.into().x() != 0;
        // Halfway along the opening: for two banners, between them, a step right being (-into.z, into.x).
        final int rightStep = alongX ? shape.into().x() : -shape.into().z();
        final double across = (alongX ? shape.base().z() : shape.base().x()) + 0.5
            + ((shape.width() - 1) * 0.5 * rightStep);
        final double y = shape.base().y() + (shape.height() / 2.0);
        final double along = (alongX ? shape.base().x() : shape.base().z()) + 0.5;
        return alongX ? new double[] { along, y, across } : new double[] { across, y, along };
    }

    /**
     * A far-side state as it shows through a window: turned the way the window turns the far side.
     *
     * <p>A pane's connections and a stair's facing are compass directions; drawn as captured, a
     * far side turned round showed panes that did not join. Turned once per state and window.
     */
    static BlockData turned(final WindowState window, final BlockData data)
    {
        if ((data == null) || ((window.rotation == StructureRotation.NONE)
            && (window.flip == Mirror.NONE)))
        {
            return data;
        }
        return window.turned.computeIfAbsent(data, original ->
        {
            final BlockData copy = original.clone();
            if (copy == null)
            {
                return original;
            }
            if (window.rotation != StructureRotation.NONE)
            {
                copy.rotate(window.rotation);
            }
            if (window.flip != Mirror.NONE)
            {
                copy.mirror(window.flip);
            }
            return copy;
        });
    }

    /** Whether a block is inside a walled window's fixed view: behind its wall, within its depth. */
    static boolean insideFixed(final WindowState window, final int x, final int y, final int z,
        final int depth)
    {
        final WindowShape shape = window.shape;
        final int layer = ((x - shape.base().x()) * shape.into().x()) + ((z - shape.base().z()) * shape.into().z());
        if (layer < 1)
        {
            return false;
        }
        final double[] centre = centreOf(shape);
        final double dx = (x + 0.5) - centre[0];
        final double dy = (y + 0.5) - centre[1];
        final double dz = (z + 0.5) - centre[2];
        return ((dx * dx) + (dy * dy) + (dz * dz)) < ((double) depth * depth);
    }
}
