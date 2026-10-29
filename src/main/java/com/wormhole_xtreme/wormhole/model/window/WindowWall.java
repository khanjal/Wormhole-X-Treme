package com.wormhole_xtreme.wormhole.model.window;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.World;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Reading the wall round a window's opening from the world: whether it is solid all round, where the
 * first gap in it is, which blocks in front of the opening close part of it (#430).
 *
 * <p>Moved out of {@link Windows} verbatim, but for the members the rest of the package calls being
 * package-private. {@link WindowFace} is the geometry these read the world into.
 */
final class WindowWall
{
    /** Static helpers only. */
    private WindowWall()
    {
    }

    /**
     * Whether a window's face is solid as far as the proximity distance on every side of its opening.
     *
     * <p>Then the wall hides whatever of the far side lies beside the opening, from anywhere a
     * viewer can be, and the far side can be drawn whole. A gap anywhere in that span shows it.
     */
    static boolean walled(final WindowState window)
    {
        return gapBeside(window) == null;
    }

    /** The first block of the face touching a window's opening that is not solid, or null if none. */
    static Spot gapBeside(final WindowState window)
    {
        // As far as the proximity distance: a viewer that far to one side looks at the space
        // behind the wall across the face that far out, and a block of open air there shows it.
        // One ring of wall was not enough -- a pillar two blocks wide in open air passed.
        final WindowShape shape = window.shape;
        final Set<Long> opening = new HashSet<>();
        shape.forEachOpening((x, y, z) -> opening.add(Cells.key(x, y, z)));
        final int reach = WindowFace.wallReach();
        final int[] span = WindowFace.acrossSpan(shape, reach);
        for (int across = span[0]; across <= span[1]; across++)
        {
            for (int y = shape.base().y() - reach; y <= (shape.base().y() + shape.height() + reach); y++)
            {
                final long face = WindowFace.faceKey(shape, across, y);
                if (!opening.contains(face) && !window.solid.contains(face))
                {
                    return new Spot(Cells.unpackX(face), Cells.unpackY(face), Cells.unpackZ(face));
                }
            }
        }
        return null;
    }

    /** Reads again which blocks around a window's opening are solid, if that reading is old. */
    static void refreshSolid(final WindowState window, final long now)
    {
        if ((now - window.solidAt) < Windows.RESAMPLE_MILLIS)
        {
            return;
        }
        final WindowShape shape = window.shape;
        final World here = window.anchor().getWorld();
        final int reach = WindowFace.wallReach();
        final int[] span = WindowFace.acrossSpan(shape, reach);
        final Set<Long> solid = new HashSet<>();
        for (int across = span[0]; across <= span[1]; across++)
        {
            for (int y = shape.base().y() - reach;
                y <= (shape.base().y() + shape.height() + reach); y++)
            {
                final long face = WindowFace.faceKey(shape, across, y);
                if (here.getBlockAt(Cells.unpackX(face), y, Cells.unpackZ(face)).getBlockData().isOccluding())
                {
                    solid.add(face);
                }
            }
        }
        window.solid = solid;
        window.border = WindowFace.borderOf(shape, solid, reach);
        window.margin = WindowFace.marginOf(shape, solid, span, reach);
        window.frame = WindowFace.frameOf(shape, solid);
        window.solidAt = now;
    }

    /** The blocks of a window's opening with nothing solid in front of them. */
    static List<Spot> openCells(final WindowShape shape, final World here)
    {
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) ->
        {
            // Something in front of part of the opening closes that part: nobody sees through
            // it, and a neighbouring window may be using the space behind.
            if (here.getBlockAt(x - shape.into().x(), y, z - shape.into().z()).isPassable())
            {
                open.add(new Spot(x, y, z));
            }
        });
        return open;
    }
}
