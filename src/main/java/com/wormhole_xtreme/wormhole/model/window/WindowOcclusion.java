package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What a window's wall hides of a drawn block: where a block lands on the opening, and how much of that the
 * wall covers. Static and state-free on purpose, because the sweeps ask it once per candidate block per redraw.
 */
final class WindowOcclusion
{
    private WindowOcclusion()
    {
    }

    /**
     * Where a block behind the opening appears on it, if it is seen through this window and only
     * through it.
     *
     * <p>Seen through the opening, all of it hidden by solid blocks or the opening itself --
     * never across open air or another window's opening -- and through no opening in the same
     * face whose middle the line of sight passes nearer.
     *
     * @param spread
     *            how far the outline is widened on every side: half the cell, for a far block
     *            judged for every eye in one ({@link Windows#clipWhole}), else nothing
     * @return the block's outline on the opening's face, or {@link WindowShape#UNSEEN} if it is
     *         not seen through it
     */
    static double[] seenThrough(final Location eye, final WindowState window, final int x,
        final int y, final int z, final List<WindowState> seeing, final double spread)
    {
        final double[] rect = window.shape.projected(eye.getX(), eye.getY(), eye.getZ(), x, y, z);
        if (rect == WindowShape.UNSEEN)
        {
            return WindowShape.UNSEEN;
        }
        rect[0] -= spread;
        rect[1] += spread;
        rect[2] -= spread;
        rect[3] += spread;
        // On the opening, or on the frame round it, which hides it until the eye moves.
        if (!window.shape.overlaps(rect, window.open) && !window.shape.overlaps(rect, window.frame))
        {
            return WindowShape.UNSEEN;
        }
        final double mine = window.shape.offCentre(rect);
        for (final WindowState other : seeing)
        {
            if ((other != window) && other.shape.sharesFace(window.shape)
                && other.shape.overlaps(rect, other.open) && (other.shape.offCentre(rect) < mine))
            {
                return WindowShape.UNSEEN;
            }
        }
        return rect;
    }

    /** Whether most of a block's outline on the face lands where nothing outside can see it. */
    static boolean coveredBy(final WindowState window, final double[] rect,
        final Set<Long> allOpen, final Set<Long> shielded, final double mostBeside)
    {
        return window.shape.covered(rect,
            (WindowShape.Cover) (across, up) -> cover(window, across, up, allOpen, shielded), mostBeside);
    }

    /** How much of the wall's outermost ring, from its open edge in, absorbs a step instead of hiding a block. */
    private static final double MARGIN = 0.5;

    /**
     * How much of a block of a window's face keeps a drawn block behind it out of sight elsewhere:
     * all of the opening itself, of a solid block of the wall, or of a block of the face hidden
     * from this eye by something real in front of it -- except along the wall's open edges.
     *
     * <p>A block drawn onto the wall beside the opening is hidden only from the eye it was drawn
     * for, and a step shifts where it lands before the next redraw. The outer half of the wall's
     * outermost ring, on each side it is open, is kept for that shift: with one block of wall a
     * block may spill half a block onto it and no more, and with two the inner ring is all wall.
     * A ring hidden by something real in front hides all of itself, as before.
     */
    static double[] cover(final WindowState window, final int across, final int y,
        final Set<Long> allOpen, final Set<Long> shielded)
    {
        // Boxed once for the four lookups below, not once each.
        final Long face = WindowFace.faceKey(window.shape, across, y);
        final boolean whole = window.openKeys.contains(face) || (shielded.contains(face) && !allOpen.contains(face));
        if (!whole && (!window.solid.contains(face) || allOpen.contains(face)))
        {
            return WindowShape.HIDES_NOTHING;
        }
        final Integer open = whole ? null : window.margin.get(face);
        if (open == null)
        {
            return new double[] { across, across + 1.0, y, y + 1.0 };
        }
        return new double[] { across + (((open & WindowFace.OPEN_BEFORE) == 0) ? 0.0 : MARGIN),
            (across + 1.0) - (((open & WindowFace.OPEN_AFTER) == 0) ? 0.0 : MARGIN),
            y + (((open & WindowFace.OPEN_BELOW) == 0) ? 0.0 : MARGIN), (y + 1.0) - (((open & WindowFace.OPEN_ABOVE) == 0) ? 0.0 : MARGIN) };
    }


    /**
     * The blocks of a window's face hidden from an eye by real solid blocks in front of it.
     *
     * <p>A corridor's walls, a hut's sides and roof: every solid block on the viewer's side
     * within reach of the opening is thrown onto the face plane from the eye, and the face
     * blocks wholly inside its shadow are hidden. That is what makes a hut or a corridor work
     * like a thick wall, instead of leaving holes wherever a drawn block's outline strayed past
     * a three-block-wide wall into the open air beside it.
     */
    static Set<Long> shielded(final WindowState window, final Location eye, final long now)
    {
        final WindowShape shape = window.shape;
        final Spot into = shape.into();
        final boolean alongX = into.x() != 0;
        final int base = alongX ? shape.base().x() : shape.base().z();
        final int step = alongX ? into.x() : into.z();
        final Set<Long> hidden = new HashSet<>();
        for (int front = 1; front <= Windows.SURROUND; front++)
        {
            shadeFrom(hidden, window, eye, base - (front * step), now);
        }
        return hidden;
    }

    /** Hides the face blocks that the solid blocks of one layer in front of the face shadow whole. */
    private static void shadeFrom(final Set<Long> hidden, final WindowState window, final Location eye,
        final int along, final long now)
    {
        final WindowShape shape = window.shape;
        final World here = window.anchor().getWorld();
        final boolean alongX = shape.into().x() != 0;
        final int[] span = WindowFace.acrossSpan(shape, Windows.SURROUND);
        final int lowY = shape.base().y() - Windows.SURROUND;
        final int highY = shape.base().y() + shape.height() + Windows.SURROUND;
        for (int across = span[0]; across <= span[1]; across++)
        {
            for (int y = lowY; y <= highY; y++)
            {
                final int x = alongX ? along : across;
                final int z = alongX ? across : along;
                if (WindowSight.solidHere(here, x, y, z, now))
                {
                    final double[] rect = shape.shadow(eye.getX(), eye.getY(), eye.getZ(), x, y, z);
                    hideUnder(hidden, shape, rect, span, lowY, highY);
                }
            }
        }
    }

    /** Hides the face blocks a shadow covers whole; none for a block that throws none. */
    static void hideUnder(final Set<Long> hidden, final WindowShape shape, final double[] rect,
        final int[] span, final int lowY, final int highY)
    {
        if (rect == WindowShape.UNSEEN)
        {
            return;
        }
        final int[] cells = withinFace(rect, span, lowY, highY);
        for (int a = cells[0]; a <= cells[1]; a++)
        {
            for (int b = cells[2]; b <= cells[3]; b++)
            {
                hidden.add(WindowFace.faceKey(shape, a, b));
            }
        }
    }

    /**
     * The blocks of the face a shadow covers whole, and no further than the face that is read.
     *
     * <p>A block whose near corner is almost level with the eye throws a shadow hundreds of blocks
     * across; the server hung for fifteen seconds adding its cells one by one. Nothing outside the
     * face read for this window is ever asked about, so nothing outside it is worth marking.
     *
     * @return {@code {acrossFrom, acrossTo, yFrom, yTo}}, inclusive, and empty when from is past to
     */
    static int[] withinFace(final double[] rect, final int[] span, final int lowY, final int highY)
    {
        return new int[] { Math.max(span[0], (int) Math.ceil(rect[0])), Math.min(span[1], (int) Math.floor(rect[1] - 1.0)),
            Math.max(lowY, (int) Math.ceil(rect[2])), Math.min(highY, (int) Math.floor(rect[3] - 1.0)) };
    }
}
