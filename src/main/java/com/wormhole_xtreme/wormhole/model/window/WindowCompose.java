package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.ACTIVE;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.changes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorPackets;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPlacement;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Composing what one viewer is shown: every window's room, whole or clipped to the eye, and the creatures
 * to hide (#430).
 *
 * <p>Moved out of {@link Windows} verbatim, but for the members Windows and its tests call being
 * package-private.
 */
final class WindowCompose
{
    /** Static helpers only. */
    private WindowCompose()
    {
    }

    /**
     * Every block one viewer should be shown, and what as; and every creature of their own world
     * standing inside the view, to be hidden from them.
     *
     * <p>Openings first, so a block that is part of one opening is never drawn as another's far
     * side. Then, nearest window first: a whole window's room as it is, and a clipped window's
     * room judged against this eye ({@link #clipWhole}). A window whose room is not held yet --
     * the server's share of work spent before it was built -- draws nothing until it is.
     */
    static Map<Long, BlockData> compose(final ViewerDrawing view, final Location eye,
        final List<WindowState> seeing, final Wholes wholes, final long now, final List<Entity> inside,
        final Windows.Budget budget)
    {
        final Map<WindowState, Windows.Whole> fixed = wholes.whole();
        final BlockData air = Bukkit.createBlockData(Material.AIR);
        final BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        final Map<Long, BlockData> wanted = new HashMap<>();
        final Set<Long> allOpen = new HashSet<>();
        seeing.forEach(window -> allOpen.addAll(window.openKeys));
        final Round round = new Round(seeing, allOpen, view.drawn.keySet(), budget);
        for (final WindowState window : seeing)
        {
            // A gate's opening is walked into, and its horizon is the gate's own to draw. A room still
            // being built leaves its banner up rather than a hole into the real wall (#609).
            if (window.walkThrough() || (wholes.of(window) == null))
            {
                continue;
            }
            // Only where the banner's patterns can be sent back afterwards. On plain 1.20 it
            // stays hanging in front of the view.
            if (MirrorPackets.available())
            {
                // Both banners of a pair: the second is to the right of the first, looking at the wall.
                final Block banner = window.anchor();
                final Spot into = window.shape.into();
                for (int across = 0; across < window.shape.width(); across++)
                {
                    wanted.put(Cells.key(banner.getX() - (across * into.z()), banner.getY(), banner.getZ() + (across * into.x())), air);
                }
            }
            window.open.forEach(cell -> wanted.put(Cells.key(cell.x(), cell.y(), cell.z()), barrier));
        }
        for (final WindowState window : Windows.nearestFirst(seeing, eye))
        {
            final Windows.Whole whole = fixed.get(window);
            final Windows.Whole clipped = wholes.clipped().get(window);
            if (whole != null)
            {
                whole.blocks().forEach(wanted::putIfAbsent);
                budget.fixed += whole.blocks().size();
                budget.fixedDepth = Math.max(budget.fixedDepth, whole.depth());
            }
            else if (clipped != null)
            {
                clipWhole(view, eye, window, clipped, wanted, round, now);
            }
        }
        if (!seeing.isEmpty())
        {
            WindowVeil.creaturesInside(seeing.get(0).anchor().getWorld(), eye, Windows.deepest(seeing), seeing, fixed,
                allOpen, inside);
        }
        return wanted;
    }

    /**
     * A clipped room's far part as last judged: which room, from which cell the eye was in and
     * the point it was judged from ({@link #fatEye}), when, and the blocks kept.
     */
    record Far(Windows.Whole whole, int cellX, int cellY, int cellZ, double cell, Location from, long at,
        Map<Long, BlockData> blocks)
    {
        /** A far part judged for the cell an eye is in, from a point, for a room, now. */
        static Far judged(final Windows.Whole whole, final Location eye, final double cell, final Location from,
            final long now, final Map<Long, BlockData> blocks)
        {
            return new Far(whole, cellOf(eye.getX(), cell), cellOf(eye.getY(), cell), cellOf(eye.getZ(), cell), cell,
                from, now, blocks);
        }

        /** Whether this still stands for an eye, or the far part is to be judged again. */
        boolean standsFor(final Windows.Whole room, final Location eye, final long now)
        {
            // The room's blocks, not the record round them, which every redraw makes anew.
            return (whole.blocks() == room.blocks()) && (cellX == cellOf(eye.getX(), cell))
                && (cellY == cellOf(eye.getY(), cell)) && (cellZ == cellOf(eye.getZ(), cell)) && ((now - at) < Windows.FAR_MILLIS);
        }

        /** Which cell of a size a coordinate falls in. */
        private static int cellOf(final double at, final double cell)
        {
            return (int) Math.floor(at / cell);
        }
    }

    /** The most an eye may move before a clipped room's far part is judged again, however wide the wall. */
    private static final double WIDEST_FAR_CELL = 4.0;

    /**
     * How far an eye may move before a clipped room's far part is judged again, behind a wall.
     *
     * <p>Half a block less than the wall is wide, up to four. The far part is judged for every
     * eye in the cell at once ({@link #clipWhole}), so a block drawn for an eye at one edge of
     * it lands, from the other edge, up to the cell's width along the wall, which is where the
     * wall has to hide it; and only the inner half of a wall block with open air past it counts
     * as hiding anything, so that a step out of the cell has somewhere to land before the next
     * redraw. What is left of the wall is the cell: half a block behind a wall a block wide,
     * where "there's a lot of leaking around the border". Wider cells send a wider far part for
     * the wall to hide, so four is as far as it goes.
     *
     * @param border
     *            how many blocks of solid wall stand on every side of the opening
     * @return the cell, in blocks
     */
    static double farCellFor(final int border)
    {
        return Math.min(WIDEST_FAR_CELL, Math.max(0.5, border - 0.5));
    }

    /**
     * The point a clipped room's far part is judged from for every eye in a cell: the middle of
     * the cell, kept no nearer the face than the eye itself, since a cell four blocks wide can
     * reach past the wall.
     */
    static Location fatEye(final Location eye, final double cell, final WindowShape shape)
    {
        final Location from = eye.clone();
        from.setX(middleOf(eye.getX(), cell));
        from.setY(middleOf(eye.getY(), cell));
        from.setZ(middleOf(eye.getZ(), cell));
        final boolean alongX = shape.into().x() != 0;
        final double toEye = shape.face() - (alongX ? eye.getX() : eye.getZ());
        final double toMiddle = shape.face() - (alongX ? from.getX() : from.getZ());
        if (((toMiddle * toEye) <= 0.0) || (Math.abs(toMiddle) < Math.abs(toEye)))
        {
            if (alongX)
            {
                from.setX(eye.getX());
            }
            else
            {
                from.setZ(eye.getZ());
            }
        }
        return from;
    }

    /** The middle of the cell of a size a coordinate falls in. */
    private static double middleOf(final double at, final double cell)
    {
        return (Math.floor(at / cell) + 0.5) * cell;
    }

    /** The windows a viewer sees whose rooms are held whole: drawn as they are, or clipped to the eye. */
    record Wholes(Map<WindowState, Windows.Whole> whole, Map<WindowState, Windows.Whole> clipped)
    {
        Windows.Whole of(final WindowState window)
        {
            final Windows.Whole held = whole.get(window);
            return (held != null) ? held : clipped.get(window);
        }
    }

    /**
     * The wall windows among those seen, their whole rooms brought up to date, sorted by how they
     * are drawn.
     *
     * <p>Whole: walled to the proximity distance, with no other window near enough for the two
     * rooms to overlap. Two walled windows seen together -- alcoves along a corridor -- would each
     * fill the same space behind the wall with a different room, and looking into one would show
     * the other's. Those, and any wall short of that, are clipped to each eye instead: the same
     * whole room, judged block by block against where the eye is ({@link #clipWhole}). Only a
     * freestanding window is left to be walked ({@link Pass}).
     */
    static Wholes wholesOf(final List<WindowState> seeing, final long now,
        final boolean mayWork, final String fullName)
    {
        if (fullName != null)
        {
            return fullOnly(seeing, fullName);
        }
        final Map<WindowState, Windows.Whole> whole = new HashMap<>();
        final Map<WindowState, Windows.Whole> clipped = new HashMap<>();
        final double apart = MirrorPlacement.apartToDrawWhole();
        for (final WindowState window : seeing)
        {
            final boolean alone = Windows.walled(window) && noWindowWithin(window, apart);
            final Map<Long, BlockData> room = heldRoom(window, now, mayWork);
            if (room != null)
            {
                (alone ? whole : clipped).put(window, new Windows.Whole(room, window.fixedDepth));
            }
        }
        return new Wholes(whole, clipped);
    }

    /**
     * A window's room as it is drawn now: its fixed view, with a build asked for when that is not
     * fresh and the server has a moment (#609).
     *
     * @param mayWork
     *            false while this second's share of work is spent: no build is asked for
     * @return the room, the old one while its rebuild runs; null until it has one
     */
    static Map<Long, BlockData> heldRoom(final WindowState window, final long now, final boolean mayWork)
    {
        // Looked through now, room or not: one that goes in before the next sweep is carried by it.
        window.fixedUsedAt = now;
        if (mayWork && !FixedView.fixedIsFresh(window, now))
        {
            FixedView.fixedView(window);
        }
        return window.fixed;
    }

    /** An admin's forced view: that one mirror whole and unlimited, the rest not at all. */
    private static Wholes fullOnly(final List<WindowState> seeing, final String fullName)
    {
        final Map<WindowState, Windows.Whole> whole = new HashMap<>();
        for (final WindowState window : seeing)
        {
            if (window.name().equals(fullName))
            {
                whole.put(window, fullView(window));
            }
        }
        return new Wholes(whole, new HashMap<>());
    }

    /**
     * Whether no other window, seen or not, hangs within a distance of this one: alcoves a block
     * apart along a wall would otherwise each fill the same space behind it with a different far
     * side, for whoever looks into either.
     */
    private static boolean noWindowWithin(final WindowState window, final double apart)
    {
        for (final WindowState other : ACTIVE.values())
        {
            if ((other != window) && other.anchor().getWorld().equals(window.anchor().getWorld())
                && (Windows.fromBanner(other.anchor(), window.anchor().getX(), window.anchor().getY(), window.anchor().getZ())
                    < (apart * apart)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Draws a whole room through a window for one eye: every block of it that is seen through
     * the opening and lands where the wall hides the rest, as {@link Pass} judges a block, and
     * nothing walked, occluded or budgeted.
     *
     * <p>A wall short of the proximity distance used to be walked like a freestanding mirror,
     * reaching what one redraw could afford: shallower right against the mirror and while
     * walking, and never the render distance. The room is held whole as a walled mirror's is, and
     * each redraw keeps the blocks this eye may see. What the capture kept is already only what
     * somebody at the opening could see, so there is nothing to occlude, and a bound on where a
     * block can land ({@link WindowShape#mightLandOn}) spares most of the room a projection.
     *
     * <p>Only the near part every time. The far part, past {@link Windows#nearDistance}, is judged for
     * the whole cell the eye is in ({@link #farCellFor}) rather than for the eye: from the
     * cell's middle ({@link #fatEye}), with each block's landing widened by half the cell on
     * every side, which is as far as it moves for any eye in the cell. A block seen through the
     * opening from anywhere in the cell is drawn, and lands from everywhere in it where the wall
     * hides it. It stands until the eye leaves the cell or {@link Windows#FAR_MILLIS} have passed, and
     * the same blocks are sent per block walked, in a fraction of the batches.
     */
    private static void clipWhole(final ViewerDrawing view, final Location eye, final WindowState window,
        final Windows.Whole whole, final Map<Long, BlockData> wanted, final Round round, final long now)
    {
        final Windows.Budget budget = round.budget();
        final Far last = view.far.get(window.name());
        final boolean farAgain = (last == null) || !last.standsFor(whole, eye, now);
        final Map<Long, BlockData> farKept = farAgain ? new HashMap<>() : last.blocks();
        final double farCell = farCellFor(window.border);
        // Near and far are split from the point the far part is judged from, so a block does not
        // change sides as the eye moves within the cell and go unjudged by both.
        final Location from = farAgain ? fatEye(eye, farCell, window.shape) : last.from();
        final double spread = farCell / 2.0;
        // What stands in front of the opening is thrown onto the face from this eye alone.
        final Sight sight = new Sight(round, WindowOcclusion.shielded(window, eye, now));
        final double[] span = spanOf(window);
        final Eye own = new Eye(eye, span, 0.0, sight);
        final Eye fat = farAgain ? new Eye(from, new double[] { span[0] - spread, span[1] + spread,
            span[2] - spread, span[3] + spread }, spread, sight) : null;
        keep(window, whole, from, own, fat, wanted, farKept);
        if (farAgain)
        {
            view.far.put(window.name(), Far.judged(whole, eye, farCell, from, now, farKept));
        }
        else
        {
            farKept.forEach(wanted::putIfAbsent);
            budget.near += farKept.size();
        }
        budget.fixedDepth = Math.max(budget.fixedDepth, whole.depth());
    }

    /**
     * Keeps every block of a room an eye sees: the near part as the viewer's own eye sees it, the
     * far part as the fat eye does, and the far part left as it stands when there is no fat eye
     * this redraw.
     *
     * @param from
     *            the point near and far are split from
     * @param fat
     *            the eye the far part is judged for, or null to leave the far part alone
     */
    private static void keep(final WindowState window, final Windows.Whole whole, final Location from, final Eye own,
        final Eye fat, final Map<Long, BlockData> wanted, final Map<Long, BlockData> farKept)
    {
        final double near = Windows.nearDistance * Windows.nearDistance;
        for (final Map.Entry<Long, BlockData> entry : whole.blocks().entrySet())
        {
            // The key as it is, boxed once: boxed again for every lookup, it was a redraw's most made object.
            final Long boxed = entry.getKey();
            final long cell = boxed;
            if (wanted.containsKey(boxed))
            {
                continue;
            }
            final double dx = (Cells.unpackX(cell) + 0.5) - from.getX();
            final double dy = (Cells.unpackY(cell) + 0.5) - from.getY();
            final double dz = (Cells.unpackZ(cell) + 0.5) - from.getZ();
            final boolean farOff = ((dx * dx) + (dy * dy) + (dz * dz)) > near;
            // No fat eye leaves the far part as it stands.
            final Eye judge = farOff ? fat : own;
            if ((judge != null) && judge.sees(window, boxed))
            {
                wanted.put(boxed, entry.getValue());
                if (farOff)
                {
                    farKept.put(boxed, entry.getValue());
                }
            }
        }
    }

    /**
     * What one redraw shares across the windows a viewer sees: the windows, their openings, what
     * the viewer was last sent, and the work done.
     */
    private record Round(List<WindowState> seeing, Set<Long> allOpen, Set<Long> before, Windows.Budget budget)
    {
    }

    /**
     * What one redraw judges a clipped room's blocks against, the same for every eye it judges from:
     * the round, and the face blocks real blocks in front of this window hide.
     */
    private record Sight(Round round, Set<Long> shielded)
    {
    }

    /**
     * An eye a clipped room's block is judged from: the viewer's own, or the middle of their cell
     * with every landing widened by half the cell, for the far part ({@link #fatEye}).
     */
    private static final class Eye
    {
        private final Location from;
        private final double[] span;
        private final double spread;
        private final Sight sight;

        Eye(final Location from, final double[] span, final double spread, final Sight sight)
        {
            this.from = from;
            this.span = span;
            this.spread = spread;
            this.sight = sight;
        }

        /**
         * Whether a block is seen through the window from here and lands where the wall hides it,
         * counting the projection and, if it is, the block.
         */
        boolean sees(final WindowState window, final Long cell)
        {
            final int x = Cells.unpackX(cell);
            final int y = Cells.unpackY(cell);
            final int z = Cells.unpackZ(cell);
            if (!window.shape.mightLandOn(from.getX(), from.getY(), from.getZ(), x, y, z, span))
            {
                return false;
            }
            final Round round = sight.round();
            round.budget().projected++;
            final double[] rect = WindowOcclusion.seenThrough(from, window, x, y, z, round.seeing(), spread);
            if ((rect == WindowShape.UNSEEN) || !WindowOcclusion.coveredBy(window, rect, round.allOpen(), sight.shielded(),
                round.before().contains(cell) ? Windows.KEPT_BESIDE : Windows.MOST_BESIDE))
            {
                return false;
            }
            round.budget().near++;
            return true;
        }
    }

    /** The face a block must land on to be seen through a window: its opening and frame, and a block round them. */
    static double[] spanOf(final WindowState window)
    {
        final boolean alongX = window.shape.into().x() != 0;
        final double[] span = { Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE };
        for (final List<Spot> cells : List.of(window.open, window.frame))
        {
            for (final Spot cell : cells)
            {
                final int across = alongX ? cell.z() : cell.x();
                span[0] = Math.min(span[0], across - 1.0);
                span[1] = Math.max(span[1], across + 2.0);
                span[2] = Math.min(span[2], cell.y() - 1.0);
                span[3] = Math.max(span[3], cell.y() + 2.0);
            }
        }
        return span;
    }

    /**
     * A window's whole capture through its opening, without limits, kept until the capture changes.
     *
     * <p>Reaches every corner of the capture's box from the opening's middle, and is capped by
     * nothing: an admin asked to see all of it.
     */
    private static Windows.Whole fullView(final WindowState window)
    {
        if ((window.full != null) && (window.fullFrom == window.capture))
        {
            return window.full;
        }
        final int[] box = window.capture.bounds();
        final Spot far = window.shape.far();
        final int dx = Math.max(Math.abs(box[0] - far.x()), Math.abs(box[3] - far.x())) + 1;
        final int dy = Math.max(Math.abs(box[1] - far.y()), Math.abs(box[4] - far.y())) + 1;
        final int dz = Math.max(Math.abs(box[2] - far.z()), Math.abs(box[5] - far.z())) + 1;
        final int depth = (int) Math.ceil(Math.sqrt(((double) dx * dx) + ((double) dy * dy) + ((double) dz * dz))) + 1;
        final Map<Long, BlockData> blocks = FixedView.fixedTo(window, depth, Integer.MAX_VALUE);
        window.full = new Windows.Whole(FixedView.tooMany(blocks) ? new HashMap<>() : blocks, depth);
        window.fullFrom = window.capture;
        return window.full;
    }
}
