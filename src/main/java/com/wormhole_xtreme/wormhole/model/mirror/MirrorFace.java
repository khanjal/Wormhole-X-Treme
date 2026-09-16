package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * The wall face round a window's opening: how far it is solid, which of it is an edge, and its frame.
 *
 * <p>All of it is the flat plane the banner hangs on, addressed across and up, so a cell of that
 * face is one number ({@link #faceKey}). A window set in wall that is solid for
 * {@link #wallReach()} blocks on every side of its opening can be drawn whole, the same for
 * every viewer; the border, the margin and the frame are what that judgement and the edges of
 * the drawing are made of.
 *
 * <p>Plain geometry over a set of solid cells: no block is read here. Split out of
 * {@link MirrorWindows}.
 */
final class MirrorFace
{
    /**
     * The coordinates along a window's face that its wall is read across: the opening, one or two
     * columns, and a reach and one more on either side of it.
     *
     * @return {@code {from, to}}, both inclusive
     */
    static int[] acrossSpan(final MirrorWindow shape, final int reach)
    {
        final boolean alongX = shape.into().x() != 0;
        // A step right, looking at the wall, is (-into.z, into.x); a pair's second column may lie below its first.
        final int rightStep = alongX ? shape.into().x() : -shape.into().z();
        final int first = alongX ? shape.base().z() : shape.base().x();
        final int low = first + Math.min(0, (shape.width() - 1) * rightStep);
        final int high = first + Math.max(0, (shape.width() - 1) * rightStep);
        return new int[] { low - (reach + 1), high + (reach + 1) };
    }

    /** How far out a window's wall is read: a viewer the proximity distance to one side looks past that much of it. */
    static int wallReach()
    {
        return Math.max(MirrorWindows.SURROUND, ConfigManager.getMirrorProximityDistance());
    }

    /** The block of a window's face at a coordinate along it. */
    static long faceKey(final MirrorWindow shape, final int across, final int y)
    {
        return (shape.into().x() != 0) ? MirrorWindows.key(shape.base().x(), y, across)
            : MirrorWindows.key(across, y, shape.base().z());
    }

    /**
     * How many blocks of solid wall stand on every side of a window's opening.
     *
     * <p>Ring by ring out from the opening, to the first ring with a block that is not solid;
     * a wall solid to the edge of what was read counts as that far. The one-block wall is the
     * case that matters ({@link MirrorWindows#farCellFor}).
     *
     * @param shape
     *            the window
     * @param solid
     *            the solid blocks of its face, as {@link MirrorWindows#refreshSolid} read them
     * @param reach
     *            how far out the face was read
     * @return the border, in blocks, from 0 to {@code reach}
     */
    static int borderOf(final MirrorWindow shape, final Set<Long> solid, final int reach)
    {
        final boolean alongX = shape.into().x() != 0;
        final int rightStep = alongX ? shape.into().x() : -shape.into().z();
        final int first = alongX ? shape.base().z() : shape.base().x();
        final int low = first + Math.min(0, (shape.width() - 1) * rightStep);
        final int high = first + Math.max(0, (shape.width() - 1) * rightStep);
        final int bottom = shape.base().y();
        final int top = (shape.base().y() + MirrorWindow.HEIGHT) - 1;
        for (int ring = 1; ring <= reach; ring++)
        {
            for (int across = low - ring; across <= (high + ring); across++)
            {
                for (int y = bottom - ring; y <= (top + ring); y++)
                {
                    final boolean onRing = (across == (low - ring)) || (across == (high + ring))
                        || (y == (bottom - ring)) || (y == (top + ring));
                    if (onRing && !solid.contains(faceKey(shape, across, y)))
                    {
                        return ring - 1;
                    }
                }
            }
        }
        return reach;
    }

    /** A margin block's open sides, as bits: the next block along the face, the one before, above, below. */
    static final int OPEN_AFTER = 1;
    static final int OPEN_BEFORE = 2;
    static final int OPEN_ABOVE = 4;
    static final int OPEN_BELOW = 8;

    /**
     * The outermost ring of a window's solid face: every solid block, the opening aside, with a
     * neighbour in the face that is not solid, and which of its four sides those are.
     *
     * <p>Only neighbours within what was read count, so a wall solid to the edge of the reading
     * has no margin there -- it is drawn whole in any case.
     */
    static Map<Long, Integer> marginOf(final MirrorWindow shape, final Set<Long> solid, final int[] span,
        final int reach)
    {
        final boolean alongX = shape.into().x() != 0;
        final Set<Long> opening = new HashSet<>();
        shape.forEachOpening((x, y, z) -> opening.add(MirrorWindows.key(x, y, z)));
        final int lowY = shape.base().y() - reach;
        final int highY = shape.base().y() + MirrorWindow.HEIGHT + reach;
        final int[][] steps = { { 1, 0, OPEN_AFTER }, { -1, 0, OPEN_BEFORE }, { 0, 1, OPEN_ABOVE }, { 0, -1, OPEN_BELOW } };
        final Map<Long, Integer> margin = new HashMap<>();
        for (final long face : solid)
        {
            if (opening.contains(face))
            {
                continue;
            }
            final int across = alongX ? MirrorWindows.unpackZ(face) : MirrorWindows.unpackX(face);
            final int y = MirrorWindows.unpackY(face);
            int open = 0;
            for (final int[] step : steps)
            {
                final int a = across + step[0];
                final int b = y + step[1];
                if ((a >= span[0]) && (a <= span[1]) && (b >= lowY) && (b <= highY) && !solid.contains(faceKey(shape, a, b)))
                {
                    open |= step[2];
                }
            }
            if (open != 0)
            {
                margin.put(face, open);
            }
        }
        return margin;
    }

    /**
     * The solid blocks of a window's face touching its opening, corners too.
     *
     * <p>They hide whatever lies just beside the opening, so a block landing on them from the eye
     * can be drawn before the eye moves to where it shows: sliding along a frame into view, it
     * was drawn only once it came into the opening, a step late.
     */
    static List<Spot> frameOf(final MirrorWindow shape, final Set<Long> solid)
    {
        final boolean alongX = shape.into().x() != 0;
        final Set<Long> opening = new HashSet<>();
        final List<int[]> cells = new ArrayList<>();
        shape.forEachOpening((x, y, z) ->
        {
            opening.add(MirrorWindows.key(x, y, z));
            cells.add(new int[] { alongX ? z : x, y });
        });
        final Set<Long> frame = new LinkedHashSet<>();
        for (final int[] cell : cells)
        {
            for (int across = -1; across <= 1; across++)
            {
                for (int up = -1; up <= 1; up++)
                {
                    final long face = faceKey(shape, cell[0] + across, cell[1] + up);
                    if (!opening.contains(face) && solid.contains(face))
                    {
                        frame.add(face);
                    }
                }
            }
        }
        final List<Spot> spots = new ArrayList<>();
        frame.forEach(face -> spots.add(new Spot(MirrorWindows.unpackX(face), MirrorWindows.unpackY(face), MirrorWindows.unpackZ(face))));
        return spots;
    }
    /** Static state only. */
    private MirrorFace()
    {
    }
}
