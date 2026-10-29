package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.VIEWS;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.sweepRoomBlocks;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.workSecond;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.workSpent;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * How fast a viewer's windows are redrawn: the rest after a slow redraw, the catch-up of a move
 * that came too soon, what has not changed, and this second's and this sweep's shares of work (#430).
 *
 * <p>Moved out of {@link Windows} verbatim, but for the members Windows and its tests call being
 * package-private.
 * The tunable counters stay in {@code Windows}, where the tests set them.
 */
final class WindowPacing
{
    /** Static helpers only. */
    private WindowPacing()
    {
    }

    /**
     * How long a viewer's next redraw on a move waits, after one that took this long.
     *
     * <p>A redraw right against a deep mirror projects the whole half-sphere and took sixty-five
     * milliseconds, ten times a second: two thirds of the main thread for one viewer. It rests
     * three times as long as it took, so a viewer costs at most a quarter of a tick's time, and a
     * quick redraw still comes ten times a second.
     *
     * <p>A small opening comes twice as often ({@link Windows#SMALL_REDRAW_MILLIS}), so what it drew is
     * taken away sooner as the viewer moves. The rest for a slow redraw is unchanged by that: the
     * cost cap is the same multiple of the same measurement, so a small opening that somehow took
     * sixty-five milliseconds still rests nearly two hundred.
     *
     * @param tookMillis
     *            how long the last redraw took
     * @param openCells
     *            the smallest opening being looked through, in blocks, or zero for none known
     * @return the least time before the next, in milliseconds
     */
    static long restAfter(final long tookMillis, final int openCells)
    {
        final long least = ((openCells > 0) && (openCells <= Windows.SMALL_OPEN)) ? Windows.SMALL_REDRAW_MILLIS : Windows.REDRAW_MILLIS;
        return Math.max(least, Windows.restFactor * tookMillis);
    }

    /**
     * The smallest opening a viewer is looking through, in blocks, or zero if they are through none.
     *
     * <p>The smallest decides the pace, because it is the one whose edges the lingering shows
     * past: a viewer at a banner-wide mirror beside a wide one is redrawn for the banner.
     *
     * @param seeing
     *            the windows this viewer is looking through
     * @return the smallest opening's size in blocks, or zero
     */
    static int smallestOpen(final List<WindowState> seeing)
    {
        int smallest = 0;
        for (final WindowState window : seeing)
        {
            final int cells = window.open.size();
            if ((smallest == 0) || (cells < smallest))
            {
                smallest = cells;
            }
        }
        return smallest;
    }

    /** Whether nothing a view depends on has changed since it was last drawn. */
    static boolean unchanged(final ViewerDrawing view, final List<WindowState> seeing, final long eye,
        final String stamp, final long now)
    {
        return (view.eye == eye) && view.stamp.equals(stamp) && ((now - view.composedAt) < Windows.RESAMPLE_MILLIS)
            && (view.generation == Captures.generation()) && view.mirrors.equals(Windows.names(seeing));
    }

    /**
     * How many ticks until a deferred move is drawn: not before the rest is up, and no later
     * than the pace allows, so a small opening's catch-up comes a tick out rather than two.
     *
     * @param restMillis
     *            the viewer's rest after their last redraw
     * @param elapsedMillis
     *            how long ago that redraw was
     * @return the delay in ticks
     */
    static long catchUpTicks(final long restMillis, final long elapsedMillis)
    {
        final long floor = (restMillis <= Windows.SMALL_REDRAW_MILLIS) ? 1L : Windows.REDRAW_TICKS;
        return Math.max(floor, ((restMillis - elapsedMillis) + 49L) / 50L);
    }

    /** Queues one redraw for a viewer who moved too soon after the last, if none is queued. */
    static void catchUpLater(final Player player, final ViewerDrawing view)
    {
        if (view.catchUpQueued)
        {
            return;
        }
        try
        {
            final long ticks = catchUpTicks(view.rest, Windows.now() - view.composedAt);
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                () -> catchUp(player), ticks);
            view.catchUpQueued = true;
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests. The next move or sweep catches up.
        }
    }

    /** Draws a viewer from where they last moved to, if that was never drawn. */
    private static void catchUp(final Player player)
    {
        final ViewerDrawing view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            return;
        }
        view.catchUpQueued = false;
        final Location eye = view.pendingEye;
        view.pendingEye = null;
        if ((eye != null) && player.isOnline())
        {
            Windows.update(player, eye, Windows.now(), false);
        }
    }

    /**
     * Whether this sweep's share of room redraws is spent; if not, counts this viewer's rooms against it.
     *
     * @return true if the viewer is to wait for the next sweep
     */
    static boolean roomShareSpent(final WindowCompose.Wholes wholes)
    {
        if (sweepRoomBlocks >= Windows.roomBlocksPerSweep)
        {
            return true;
        }
        wholes.whole().values().forEach(whole -> sweepRoomBlocks += whole.blocks().size());
        wholes.clipped().values().forEach(whole -> sweepRoomBlocks += whole.blocks().size());
        return false;
    }

    /** Whether this second's share of work is spent, starting a new second's if one has begun. */
    static boolean overBudget(final long now)
    {
        if ((now - workSecond) >= 1000L)
        {
            workSecond = now;
            workSpent = 0;
        }
        return workSpent >= Windows.workPerSecond;
    }
}
