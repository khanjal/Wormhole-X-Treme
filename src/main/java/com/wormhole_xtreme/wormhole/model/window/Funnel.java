package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * A big gate's view of a small far gate, as a tunnel with the view at its end (#583).
 *
 * <p>The tunnel runs {@code taper} layers behind the opening, the opening itself its first. The
 * next is the near opening's size still (with a least length, a straight run of them), and from the
 * last of those the tunnel narrows a block a layer to the far one's size. It is hollow, walled two blocks thick in
 * the near gate's portal material, and its last layer is the far gate's own plane, where the wall
 * ends in a collar round the far opening. The far room is drawn from the layer after, shifted that
 * much deeper ({@link WindowShape#deeper}), as much of it as the near opening sees through the far
 * one, with no wall round it.
 *
 * <p>With {@code gate-view-funnel-min-length} set (#587), a far gate as big or bigger is seen down a
 * straight tube: every layer the near opening's shape, its last too. Where the longest allowed cuts a
 * tunnel short, the straight run gives way first, so the narrowing keeps its pace as long as it can.
 *
 * <p>Plain numbers, in the window's own terms: a layer behind the opening, a column counted
 * rightwards from its left edge, and a row up from its bottom. The far opening is where the far
 * gate's own cells show through the window, so the tunnel lines up with it whatever the two gates'
 * heights, and never reaches outside the near opening.
 *
 * @param width
 *            the near opening's width
 * @param height
 *            the near opening's height
 * @param far
 *            the far opening, through the window
 * @param taper
 *            how long the tunnel is, in layers counting the opening's own, at most
 *            {@code gate-view-funnel-length}: see {@link #length}
 * @param wall
 *            what is drawn round it: the near gate's portal material
 * @param nearShape
 *            the near opening's cells, as {@link #cellKey}s: the shape each layer of the tunnel is
 * @param farShape
 *            the far opening's cells through the window, as {@link #cellKey}s: the tunnel's last layer
 */
public record Funnel(int width, int height, Opening far, int taper, Material wall, Set<Integer> nearShape,
    Set<Integer> farShape)
{
    /** Copied, so a funnel is equal to another of the same shapes whatever set they came in. */
    public Funnel
    {
        nearShape = Set.copyOf(nearShape);
        farShape = Set.copyOf(farShape);
    }

    /** Wider than any opening a view is drawn through, so a column and a row make one key. */
    private static final int KEY_STRIDE = 1024;

    /**
     * @param across
     *            a column of the near opening
     * @param up
     *            a row of it
     * @return the key a cell is kept under in {@link #nearShape} and {@link #farShape}
     */
    static int cellKey(final int across, final int up)
    {
        return (across * KEY_STRIDE) + up;
    }

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

    /** How many blocks thick the tunnel's wall is. */
    static final int WALL_BLOCKS = 2;

    /** The narrowest and lowest a tunnel ends: a far opening smaller is taken as this, so a Minimal gate shows a room. */
    static final int LEAST_APERTURE = 3;

    /** With a least length, the straight run is at least this many tenths of the near opening's longer side: Standard 3, Large 5. */
    static final int RUN_TENTHS = 6;

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
     * The funnel a rectangular opening narrows to for a far gate, if it narrows at all.
     *
     * @return {@link #of(WindowShape, List, List, int, Material)} for an opening that fills its window
     */
    public static Funnel of(final WindowShape shape, final List<Spot> farCells, final int longest, final Material wall)
    {
        return of(shape, null, farCells, longest, wall);
    }

    /**
     * The funnel a window narrows to for a far gate, if it narrows at all: with no least length, as
     * before #587.
     *
     * @param shape
     *            the near gate's window
     * @param nearCells
     *            the near gate's portal cells, which the tunnel takes its shape from; null for a
     *            rectangle filling the window
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
    public static Funnel of(final WindowShape shape, final List<Spot> nearCells, final List<Spot> farCells,
        final int longest, final Material wall)
    {
        return of(shape, nearCells, farCells, longest, 0, wall);
    }

    /**
     * The funnel a window narrows to for a far gate, with a least length (#587): above 0, a far gate as
     * big or bigger each way is seen down a straight tube the near opening's shape, and every tunnel
     * starts with a run its own size, longer for a bigger near opening.
     *
     * @param least
     *            {@code gate-view-funnel-min-length}; 0 for none, the tunnel as {@code longest} alone makes it
     * @return the funnel, or null as {@link #of(WindowShape, List, List, int, Material)} has it; with a
     *         least length, null for a straight tube only where it would be the opening alone
     */
    public static Funnel of(final WindowShape shape, final List<Spot> nearCells, final List<Spot> farCells,
        final int longest, final int least, final Material wall)
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
        final Opening own = grown(seen);
        // Only ever narrower: a side where the far gate is as big or bigger keeps the near one's.
        final Opening far = new Opening(Math.max(0, own.left()), Math.min(shape.width() - 1, own.right()),
            Math.max(0, own.bottom()), Math.min(shape.height() - 1, own.top()));
        if ((far.left() > far.right()) || (far.bottom() > far.top()))
        {
            return null;
        }
        // A block narrower each way is a block longer, so a Standard gate is further down a Massive one than a
        // Large.
        final int narrows = Math.max(shape.width() - ((far.right() - far.left()) + 1),
            shape.height() - ((far.top() - far.bottom()) + 1));
        final int taper = length(narrows, Math.max(shape.width(), shape.height()), longest, least);
        if ((narrows == 0) && ((least <= 0) || (taper <= 1)))
        {
            return null;
        }
        final Set<Integer> nearShape = cellsIn(shape, nearCells, null);
        if (narrows == 0)
        {
            // As big or bigger each way: a straight tube the near opening's shape, the view no wider than it.
            return new Funnel(shape.width(), shape.height(), far, taper, wall, nearShape, nearShape);
        }
        // Only those inside the near opening: a far gate taller or wider than it is cut to it, as its box is.
        // One grown to the least aperture ends on all of it, not its own few cells.
        final Set<Integer> farShape = cellsIn(shape, own.equals(seen) ? farCells : null, far);
        return new Funnel(shape.width(), shape.height(), far, taper, wall, nearShape, farShape);
    }

    /**
     * How long a tunnel is, in layers counting the opening's own.
     *
     * <p>With no least length: a layer more than the opening narrows, for the layer behind it its own size.
     * With one: a straight run first, as long as the least or {@link #RUN_TENTHS} tenths of the near
     * opening's longer side, whichever is longer, then a layer more for each it narrows; so a pair of Large
     * gates is further down than a pair of Standard ones, and a Large onto a Standard further still.
     *
     * @param narrows
     *            blocks the far opening is narrower, the more of its width and height
     * @param side
     *            the near opening's longer side
     * @param longest
     *            the longest allowed
     * @param least
     *            the least length; 0 or less for none
     * @return layers, at most {@code longest}
     */
    static int length(final int narrows, final int side, final int longest, final int least)
    {
        if (least <= 0)
        {
            return Math.min(longest, narrows + 1);
        }
        // Rounded half up, in whole numbers.
        final int run = Math.max(least, ((RUN_TENTHS * side) + 5) / 10);
        return Math.min(longest, run + narrows);
    }

    /**
     * A far opening grown to the least aperture where it is smaller: sideways about its middle, the
     * extra block of an even growth to the right, and upwards from its bottom row, so the ground at
     * the end of the tunnel stays level with where travellers land.
     *
     * @return the opening, or the same one where it is big enough each way
     */
    static Opening grown(final Opening far)
    {
        final int wide = (far.right() - far.left()) + 1;
        final int tall = (far.top() - far.bottom()) + 1;
        if ((wide >= LEAST_APERTURE) && (tall >= LEAST_APERTURE))
        {
            return far;
        }
        final int extra = Math.max(0, LEAST_APERTURE - wide);
        return new Opening(far.left() - (extra / 2), far.right() + (extra - (extra / 2)), far.bottom(),
            Math.max(far.top(), (far.bottom() + LEAST_APERTURE) - 1));
    }

    /**
     * Some cells as keys of the window's columns and rows.
     *
     * @param cells
     *            cells of either world; null for every cell of the box, or of the window for none
     * @param box
     *            for the far side's cells, which are mapped through the window, the box to keep them
     *            inside; null for the near opening's, which are kept as they are
     * @return their keys
     */
    private static Set<Integer> cellsIn(final WindowShape shape, final List<Spot> cells, final Opening box)
    {
        final Set<Integer> keys = new HashSet<>();
        if (cells == null)
        {
            final Opening all = (box == null) ? new Opening(0, shape.width() - 1, 0, shape.height() - 1) : box;
            for (int across = all.left(); across <= all.right(); across++)
            {
                addColumn(keys, across, all.bottom(), all.top());
            }
            return keys;
        }
        for (final Spot cell : cells)
        {
            final Spot here = (box == null) ? cell : shape.hereOf(cell.x(), cell.y(), cell.z());
            final int across = shape.acrossOf(here.x(), here.z());
            final int up = here.y() - shape.base().y();
            if ((box == null) || ((across >= box.left()) && (across <= box.right()) && (up >= box.bottom()) && (up <= box.top())))
            {
                keys.add(cellKey(across, up));
            }
        }
        return keys;
    }

    /** Adds the rows of one column from one to another, both included. */
    private static void addColumn(final Set<Integer> keys, final int across, final int bottom, final int top)
    {
        for (int up = bottom; up <= top; up++)
        {
            keys.add(cellKey(across, up));
        }
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
     * Whether a cell behind the window is inside the tunnel, or in the room past it.
     *
     * <p>In the tunnel each layer is the near opening's shape, shrunk: a cell is in while its middle
     * is within half a block of the narrowing box, so both sides step in on the same layer, and the
     * near opening has the cell standing the same share of the way across and up it. A round gate's
     * tunnel is round, its floor too. Its last layer, the far gate's plane, is the far opening's own
     * cells. Past it, a cell is in if any line through both the near opening and the far
     * one passes through it: the room widens again from the far opening as far as the near opening
     * can see through it, so what shows is what the small gate's own opening would show, and the
     * halls and ceilings round it stay out.
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
     * The same, with the room's edges moved out by some blocks: for a stand-in already shown, so one on
     * the edge is not taken away and shown again as it steps across it. The tunnel and the far gate's
     * plane are not widened: nothing stands in their walls.
     *
     * @param slack
     *            how far out each of the room's edges is moved
     * @return true if the cell is inside the tunnel or the widened room
     */
    public boolean holds(final int layer, final int across, final int up, final double slack)
    {
        final int plane = taper - 1;
        if (layer >= taper)
        {
            // A tunnel of the opening alone has no far opening in front of the room: it is the plain view.
            return (plane == 0) || seenThrough(layer, across, up, slack, plane);
        }
        if ((plane > 0) && (layer >= plane))
        {
            // The far gate's plane is its opening, slack or none: its frame is the collar, where nothing stands.
            return farShape.contains(cellKey(across, up));
        }
        // In whole numbers, every edge times twice the steps it narrows in, so a tie is a tie on both sides.
        // The straight run behind the opening is its size still: the narrowing starts from its last layer.
        final int run = run();
        final long steps = Math.max(1, plane - run);
        final long part = Math.max(0, layer - run);
        final long left = far.left() * part;
        final long right = ((width - 1) * steps) + ((far.right() - (width - 1L)) * part);
        final long bottom = far.bottom() * part;
        final long top = ((height - 1) * steps) + ((far.top() - (height - 1L)) * part);
        if ((((2L * across) + 1) * steps < (2 * left)) || (((2L * across) - 1) * steps > (2 * right))
            || ((((2L * up) + 1) * steps) < (2 * bottom)) || ((((2L * up) - 1) * steps) > (2 * top)))
        {
            return false;
        }
        // Never narrower than the far opening it ends on, so no far cell is left in a pocket of wall.
        return ((layer > 0) && farShape.contains(cellKey(across, up)))
            || nearShape.contains(cellKey(sample(across * steps, left, right, width), sample(up * steps, bottom, top, height)));
    }

    /**
     * The column or row of the near opening a cell of a narrower layer stands for: its place between
     * the layer's edges, the same share of the way between the near opening's, to the nearest block and
     * a half to the even one. In whole numbers, all of them times the tunnel's length, so a shape the
     * same either side keeps a tunnel the same either side.
     *
     * @param at
     *            the cell's column or row, times the tunnel's length
     * @param low
     *            the layer's first, as a middle of a block, times the tunnel's length
     * @param high
     *            the layer's last, the same
     * @param size
     *            the near opening's width or height
     * @return the near opening's column or row
     */
    private static int sample(final long at, final long low, final long high, final int size)
    {
        final long span = high - low;
        if (span <= 0)
        {
            // An opening one block across, onto one as narrow.
            return 0;
        }
        final long scaled = Math.max(0, Math.min(span, at - low)) * (size - 1);
        final long whole = scaled / span;
        final long twice = 2 * (scaled - (whole * span));
        final boolean up = (twice > span) || ((twice == span) && ((whole % 2) == 1));
        return (int) (up ? (whole + 1) : whole);
    }

    /**
     * The last layer of the straight run, where the narrowing starts: the layers before it, from the
     * opening, are the near opening's shape. Whatever of the tunnel's length the narrowing does not need;
     * 1 with no least length, or where the longest allowed cuts the tunnel short, as before #587.
     *
     * @return the layer, at least 1
     */
    private int run()
    {
        final int narrows = Math.max(width - ((far.right() - far.left()) + 1), height - ((far.top() - far.bottom()) + 1));
        return Math.max(1, taper - narrows);
    }

    /**
     * Whether a cell past the tunnel lies on a line through the near opening and the far one, in block
     * edges. Each is a block deep and walled round, so a line must be inside both at both faces. The
     * widest leaves the far opening's back face at one edge: it spreads a layer at a time as far as
     * the near opening's front face allows, and no more than the far opening is wide, or it would
     * leave the far opening's front face past the other edge. It is widest at the cell's far side.
     */
    private boolean seenThrough(final int layer, final int across, final int up, final double slack, final int plane)
    {
        final double layers = (double) layer - plane;
        final double reach = plane + 1.0;
        final double wide = (far.right() - far.left()) + 1.0;
        final double tall = (far.top() - far.bottom()) + 1.0;
        final double left = (far.left() - 0.5) - (layers * Math.min(((width - 0.5) - (far.left() - 0.5)) / reach, wide)) - slack;
        final double right = (far.right() + 0.5) + (layers * Math.min((far.right() + 1.0) / reach, wide)) + slack;
        final double bottom = (far.bottom() - 0.5) - (layers * Math.min(((height - 0.5) - (far.bottom() - 0.5)) / reach, tall))
            - slack;
        final double top = (far.top() + 0.5) + (layers * Math.min((far.top() + 1.0) / reach, tall)) + slack;
        return ((across + 0.5) > left) && ((across - 0.5) < right) && ((up + 0.5) > bottom) && ((up - 0.5) < top);
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
        // Never in the opening itself, which is the near gate's.
        return (layer >= 1) && ((layer + slack) >= taper) && holds(layer, across, up, slack);
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
     * Whether a cell is the tunnel's wall, two blocks thick: outside the tunnel, within two blocks of
     * it on its own layer, corners included, or inside it one or two layers before. A shrunk shape does
     * not only narrow: a cross's arm can be in on one layer and out on the next. Water ends a
     * block short of its top with nothing over it, so one block left the world showing between the
     * steps; and a step in of several columns at once is walled across its whole face. The wall ends
     * with the tunnel, its last layer a collar round the far opening; the room past it has none.
     *
     * @return true if the wall is drawn there
     */
    public boolean wall(final int layer, final int across, final int up)
    {
        if ((layer < 1) || (layer >= taper) || holds(layer, across, up))
        {
            return false;
        }
        return holds(layer - 1, across, up) || holds(layer - 2, across, up) || besideOnLayer(layer, across, up);
    }

    /** Whether a cell is within two blocks of the tunnel on its own layer, corners included. */
    private boolean besideOnLayer(final int layer, final int across, final int up)
    {
        for (int sideways = -WALL_BLOCKS; sideways <= WALL_BLOCKS; sideways++)
        {
            for (int upwards = -WALL_BLOCKS; upwards <= WALL_BLOCKS; upwards++)
            {
                if (holds(layer, across + sideways, up + upwards))
                {
                    return true;
                }
            }
        }
        return false;
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
        for (int layer = 1; layer < Math.min(depth, taper); layer++)
        {
            wallOfLayer(layer, cell);
        }
    }

    /** Visits the wall's cells on one layer: within two blocks of the near opening, which the tunnel never leaves. */
    private void wallOfLayer(final int layer, final Cell cell)
    {
        for (int across = -WALL_BLOCKS; across < (width + WALL_BLOCKS); across++)
        {
            for (int up = -WALL_BLOCKS; up < (height + WALL_BLOCKS); up++)
            {
                if (wall(layer, across, up))
                {
                    cell.at(layer, across, up);
                }
            }
        }
    }
}
