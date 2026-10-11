package com.wormhole_xtreme.wormhole.model.window;

import java.util.List;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * The part of a big gate's view a small far gate could show (#583): the near opening at the
 * window, narrowing over {@code taper} layers to the far opening, then that size on.
 *
 * <p>Plain numbers, in the window's own terms: a layer behind the opening, a column counted
 * rightwards from its left edge, and a row up from its bottom. The far opening is where the far
 * gate's own cells show through the window, so a funnel lines up with it whatever the two gates'
 * heights, and never reaches outside the near opening.
 *
 * @param width
 *            the near opening's width
 * @param height
 *            the near opening's height
 * @param farLeft
 *            the far opening's left column, through the window
 * @param farRight
 *            its right column
 * @param farBottom
 *            its bottom row
 * @param farTop
 *            its top row
 * @param taper
 *            how many layers the funnel narrows over
 * @param wall
 *            what is drawn round it: the near gate's portal material
 */
public record Funnel(int width, int height, int farLeft, int farRight, int farBottom, int farTop, int taper,
    Material wall)
{
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
     * @param taper
     *            how many layers to narrow over; 0 or less for none
     * @param wall
     *            the near gate's portal material
     * @return the funnel; or null where the far opening is as big as the near one each way, does not
     *         face the way travellers leave it, or nothing could be drawn round it
     */
    public static Funnel of(final WindowShape shape, final List<Spot> farCells, final int taper, final Material wall)
    {
        if ((taper <= 0) || (farCells == null) || farCells.isEmpty() || (wall == null) || MaterialUtils.isAirMaterial(wall))
        {
            return null;
        }
        final Spot first = shape.hereOf(farCells.get(0).x(), farCells.get(0).y(), farCells.get(0).z());
        final int layer = shape.layerOf(first.x(), first.z());
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MAX_VALUE;
        int top = Integer.MIN_VALUE;
        for (final Spot cell : farCells)
        {
            final Spot here = shape.hereOf(cell.x(), cell.y(), cell.z());
            // A gate lying flat, or turned from its arrival, is not a hole across the view.
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
        // Only ever narrower: a side where the far gate is as big or bigger keeps the near one's.
        left = Math.max(0, left);
        right = Math.min(shape.width() - 1, right);
        bottom = Math.max(0, bottom);
        top = Math.min(shape.height() - 1, top);
        if ((left > right) || (bottom > top)
            || ((left == 0) && (right == (shape.width() - 1)) && (bottom == 0) && (top == (shape.height() - 1))))
        {
            return null;
        }
        return new Funnel(shape.width(), shape.height(), left, right, bottom, top, taper, wall);
    }

    /**
     * Whether a cell behind the window is inside the funnel.
     *
     * <p>A cell is in while its middle is within half a block of the narrowing edge, so both sides
     * of a funnel step in on the same layer.
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
        final double part = Math.min(1.0, Math.max(0, layer) / (double) taper);
        final double left = farLeft * part;
        final double right = (width - 1) + ((farRight - (width - 1)) * part);
        final double bottom = farBottom * part;
        final double top = (height - 1) + ((farTop - (height - 1)) * part);
        return ((across + 0.5) >= left) && ((across - 0.5) <= right) && ((up + 0.5) >= bottom) && ((up - 0.5) <= top);
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
            for (int across = -1; across <= width; across++)
            {
                for (int up = -1; up <= height; up++)
                {
                    if (wall(layer, across, up))
                    {
                        cell.at(layer, across, up);
                    }
                }
            }
        }
    }
}
