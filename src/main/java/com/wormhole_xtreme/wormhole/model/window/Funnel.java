package com.wormhole_xtreme.wormhole.model.window;

import java.util.List;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * A big gate's view of a small far gate, as a tunnel with the view at its end (#583).
 *
 * <p>The tunnel runs {@code taper} layers behind the opening, the opening itself its first,
 * narrowing from the near opening to the far one's size. It is hollow, walled in the near gate's
 * portal material, and its last layer is the far gate's own plane. The far room is drawn from the
 * layer after, shifted that much deeper ({@link WindowShape#deeper}), at the far opening's size and
 * the row of ground beneath it, still walled.
 *
 * <p>Plain numbers, in the window's own terms: a layer behind the opening, a column counted
 * rightwards from its left edge, and a row up from its bottom. The far opening is where the far
 * gate's own cells show through the window, so the tunnel lines up with it whatever the two gates'
 * heights, and never reaches outside the near opening but for that row of ground.
 *
 * @param width
 *            the near opening's width
 * @param height
 *            the near opening's height
 * @param far
 *            the far opening, through the window
 * @param taper
 *            how long the tunnel is, in layers counting the opening's own: as much as the opening
 *            narrows, at most {@code gate-view-funnel}
 * @param wall
 *            what is drawn round it: the near gate's portal material
 */
public record Funnel(int width, int height, Opening far, int taper, Material wall)
{
    /**
     * An opening's columns and rows, inclusive, in the window's terms.
     *
     * @param left
     *            its left column
     * @param right
     *            its right column
     * @param bottom
     *            its bottom row
     * @param top
     *            its top row
     */
    public record Opening(int left, int right, int bottom, int top)
    {
    }

    /** Handed each cell of a funnel's wall. */
    @FunctionalInterface
    public interface Cell
    {
        /**
         * @param layer
         *            layers behind the opening, from 1
         * @param across
         *            the column, from the opening's left edge
         * @param up
         *            the row, from its bottom
         */
        void at(int layer, int across, int up);
    }

    /**
     * The funnel a window narrows to for a far gate, if it narrows at all.
     *
     * @param shape
     *            the near gate's window
     * @param farCells
     *            the far gate's portal cells, in its own world
     * @param longest
     *            the longest tunnel allowed; 0 or less for none
     * @param wall
     *            the near gate's portal material
     * @return the funnel, as long as the opening narrows each way at the most, up to {@code longest};
     *         or null where the far opening is as big as the near one each way, does not
     *         face the way travellers leave it, or nothing could be drawn round it
     */
    public static Funnel of(final WindowShape shape, final List<Spot> farCells, final int longest, final Material wall)
    {
        if ((longest <= 0) || (farCells == null) || farCells.isEmpty() || (wall == null) || MaterialUtils.isAirMaterial(wall))
        {
            return null;
        }
        final Opening seen = seenThrough(shape, farCells);
        if (seen == null)
        {
            return null;
        }
        // Only ever narrower: a side where the far gate is as big or bigger keeps the near one's.
        final Opening far = new Opening(Math.max(0, seen.left()), Math.min(shape.width() - 1, seen.right()),
            Math.max(0, seen.bottom()), Math.min(shape.height() - 1, seen.top()));
        if ((far.left() > far.right()) || (far.bottom() > far.top())
            || far.equals(new Opening(0, shape.width() - 1, 0, shape.height() - 1)))
        {
            return null;
        }
        // A block narrower each way is a block longer, so a Standard gate is further down a Massive one than a Large.
        final int narrows = Math.max(shape.width() - ((far.right() - far.left()) + 1),
            shape.height() - ((far.top() - far.bottom()) + 1));
        return new Funnel(shape.width(), shape.height(), far, Math.min(longest, narrows), wall);
    }

    /**
     * Where a far gate's cells show through a window, as columns and rows of it.
     *
     * @return the box round them; or null where they are not all on one layer, as a gate lying flat,
     *         or turned from its arrival, is not a hole across the view
     */
    private static Opening seenThrough(final WindowShape shape, final List<Spot> farCells)
    {
        final Spot first = shape.hereOf(farCells.get(0).x(), farCells.get(0).y(), farCells.get(0).z());
        final int layer = shape.layerOf(first.x(), first.z());
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MAX_VALUE;
        int top = Integer.MIN_VALUE;
        for (final Spot cell : farCells)
        {
            final Spot here = shape.hereOf(cell.x(), cell.y(), cell.z());
            if (shape.layerOf(here.x(), here.z()) != layer)
            {
                return null;
            }
            final int across = shape.acrossOf(here.x(), here.z());
            final int up = here.y() - shape.base().y();
            left = Math.min(left, across);
            right = Math.max(right, across);
            bottom = Math.min(bottom, up);
            top = Math.max(top, up);
        }
        return new Opening(left, right, bottom, top);
    }

    /**
     * Whether a cell behind the window is inside the tunnel or the room past it.
     *
     * <p>A cell is in while its middle is within half a block of the narrowing edge, so both sides
     * step in on the same layer. Past the tunnel the row beneath the far opening is in too: the
     * ground travellers land on and walk across, level with the tunnel's floor of wall.
     *
     * @param layer
     *            layers behind the opening; 0 is the opening itself
     * @param across
     *            the column
     * @param up
     *            the row
     * @return true if the view is drawn there
     */
    public boolean holds(final int layer, final int across, final int up)
    {
        return holds(layer, across, up, 0.0);
    }

    /**
     * The same, with every edge moved out by some blocks: for a stand-in already shown, so one on the
     * edge is not taken away and shown again as it steps across it.
     *
     * @param slack
     *            how far out each edge is moved
     * @return true if the cell is inside the widened funnel
     */
    public boolean holds(final int layer, final int across, final int up, final double slack)
    {
        final double part = Math.min(1.0, Math.max(0, layer) / (double) taper);
        final double left = (far.left() * part) - slack;
        final double right = (width - 1) + ((far.right() - (width - 1)) * part) + slack;
        final double ground = (layer >= taper) ? 1.0 : 0.0;
        final double bottom = ((far.bottom() * part) - ground) - slack;
        final double top = (height - 1) + ((far.top() - (height - 1)) * part) + slack;
        return ((across + 0.5) >= left) && ((across - 0.5) <= right) && ((up + 0.5) >= bottom) && ((up - 0.5) <= top);
    }

    /**
     * Whether a cell is inside the tunnel, behind the opening: drawn hollow.
     *
     * @return true for air there
     */
    public boolean hollow(final int layer, final int across, final int up)
    {
        return (layer >= 1) && (layer < taper) && holds(layer, across, up);
    }

    /**
     * Whether a cell is in the far room past the tunnel, where the far side is drawn.
     *
     * @param slack
     *            how far out each edge, and the tunnel's end, is moved: for a stand-in already shown
     * @return true if the far side shows there
     */
    public boolean shows(final int layer, final int across, final int up, final double slack)
    {
        return ((layer + slack) >= taper) && holds(layer, across, up, slack);
    }

    /**
     * Visits every hollow cell of the tunnel short of a depth.
     *
     * @param depth
     *            the layer to stop before
     * @param cell
     *            handed each one
     */
    public void forEachHollow(final int depth, final Cell cell)
    {
        for (int layer = 1; layer < Math.min(depth, taper); layer++)
        {
            hollowOfLayer(layer, cell);
        }
    }

    /** Visits the hollow cells of one layer of the tunnel, all inside the near opening. */
    private void hollowOfLayer(final int layer, final Cell cell)
    {
        for (int across = 0; across < width; across++)
        {
            for (int up = 0; up < height; up++)
            {
                if (holds(layer, across, up))
                {
                    cell.at(layer, across, up);
                }
            }
        }
    }

    /**
     * Whether a cell is the funnel's wall: outside it, beside a cell inside it on this layer or the
     * one before. Every line of sight that leaves the funnel enters one of these first.
     *
     * @return true if the wall is drawn there
     */
    public boolean wall(final int layer, final int across, final int up)
    {
        return !holds(layer, across, up) && (holds(layer - 1, across, up) || holds(layer, across - 1, up)
            || holds(layer, across + 1, up) || holds(layer, across, up - 1) || holds(layer, across, up + 1));
    }

    /**
     * Visits every cell of the wall short of a depth.
     *
     * @param depth
     *            the layer to stop before
     * @param cell
     *            handed each one
     */
    public void forEachWall(final int depth, final Cell cell)
    {
        for (int layer = 1; layer < depth; layer++)
        {
            wallOfLayer(layer, cell);
        }
    }

    /** Visits the wall's cells on one layer: within a block of the near opening and the ground under it. */
    private void wallOfLayer(final int layer, final Cell cell)
    {
        for (int across = -1; across <= width; across++)
        {
            for (int up = -2; up <= height; up++)
            {
                if (wall(layer, across, up))
                {
                    cell.at(layer, across, up);
                }
            }
        }
    }
}
