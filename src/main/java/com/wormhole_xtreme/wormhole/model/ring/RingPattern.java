/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   The two shapes a transport ring can be.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The two footprints a transport ring can have, and the geometry derived from them.
 *
 * <p>Rings are not gates and deliberately have no {@code .shape} file. There are exactly
 * two of them, they are fixed, and a file format for two constant tables would be a format
 * to parse, validate, document and get wrong for no benefit at all.
 *
 * <p>The odd pattern <em>is</em> the Standard gate's ring — the same profile,
 * {@code 3,5,7,7,7,5,3}, lying flat instead of standing up. The even one is a size down from
 * it, for rooms that cannot spare seven blocks in both directions.
 *
 * <p>What makes them read as circles rather than as squares with clipped corners is that
 * each corner turns through <b>two diagonal steps</b> rather than one. Six is as small as
 * that goes: at five, two steps collapses the shape into a diamond with almost no standing
 * room, and the only usable five-wide ring has a single-step corner that looks like an
 * octagon. So the two sizes here are the small round ring and the gate's own.
 *
 * <p>Each pattern is described by nothing but its row widths. Everything else — which cells
 * are perimeter, which are interior, where the anchor sits — is derived below, so adding a
 * third size later means adding a profile and nothing more.
 *
 * <p>The split between perimeter and interior is what the rest of the subsystem is built
 * on, and the two never overlap:
 *
 * <ul>
 * <li><b>Perimeter</b> is what the player lays in slabs to build the ring, and what
 * animates when it fires. It is a threshold, not a place to stand.
 * <li><b>Interior</b> is the trigger volume and the region that travels. Only the interior
 * arms a cycle, because letting the edge do it would fire rings at people walking past.
 * </ul>
 *
 * <p>Instances are immutable and safe to share across threads.
 */
public enum RingPattern
{
    /** Seven across, a true centre block, 16 perimeter blocks around a 21-block interior. */
    ODD(new int[] { 3, 5, 7, 7, 7, 5, 3 }),

    /** Six across, a 2x2 centre, 12 perimeter blocks around a 12-block interior. */
    EVEN(new int[] { 2, 4, 6, 6, 4, 2 });

    /**
     * One cell of a pattern, as an offset from the ring's anchor block.
     *
     * <p>Immutable, because these are shared out of the static tables below and handing a
     * caller something it could mutate would corrupt every ring on the server at once.
     */
    public static final class Offset
    {
        /** Offset along x from the anchor. */
        private final int dx;

        /** Offset along z from the anchor. */
        private final int dz;

        /**
         * Instantiates a new offset.
         *
         * @param dx
         *            offset along x from the anchor
         * @param dz
         *            offset along z from the anchor
         */
        Offset(final int dx, final int dz)
        {
            this.dx = dx;
            this.dz = dz;
        }

        /** @return offset along x from the anchor */
        public int getDx()
        {
            return dx;
        }

        /** @return offset along z from the anchor */
        public int getDz()
        {
            return dz;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString()
        {
            return "(" + dx + "," + dz + ")";
        }
    }

    /** Row widths of the filled disc, top row first. */
    private final int[] profile;

    /** Cells on the outline: what the player lays in slabs, and what animates. */
    private final List<Offset> perimeter;

    /** Cells enclosed by the outline: the trigger volume and what travels. */
    private final List<Offset> interior;

    /**
     * Instantiates a pattern from its row widths alone.
     *
     * @param profile
     *            row widths of the filled disc, top row first
     */
    RingPattern(final int[] profile)
    {
        this.profile = profile.clone();
        final boolean[][] filled = fill(this.profile);
        final List<Offset> edge = new ArrayList<Offset>();
        final List<Offset> inside = new ArrayList<Offset>();
        classify(filled, edge, inside);
        this.perimeter = Collections.unmodifiableList(edge);
        this.interior = Collections.unmodifiableList(inside);
    }

    /**
     * Paints the filled disc into a square grid.
     *
     * <p>Each row is centred in the grid, which is what makes the profile alone enough to
     * describe the shape. The grid is square and as wide as the widest row, so the odd
     * pattern lands on a 7x7 and the even one on an 8x8.
     *
     * @param profile
     *            row widths, top row first
     * @return grid of the filled cells, indexed {@code [row][column]}
     */
    private static boolean[][] fill(final int[] profile)
    {
        final int size = profile.length;
        final boolean[][] filled = new boolean[size][size];
        for (int row = 0; row < size; row++)
        {
            final int width = profile[row];
            final int start = (size - width) / 2;
            for (int column = start; column < (start + width); column++)
            {
                filled[row][column] = true;
            }
        }
        return filled;
    }

    /**
     * Sorts every filled cell into perimeter or interior.
     *
     * <p>A filled cell is on the perimeter when any of its four orthogonal neighbours is
     * not filled — which is simply what "the outline of a shape" means, and gets the answer
     * right for both patterns without either being written out by hand.
     *
     * <p>Offsets come out relative to the anchor at {@code (size - 1) / 2} on both axes.
     * For the odd pattern that is the true centre and the offsets are symmetric,
     * {@code -3..+3}. The even pattern has no centre block, so the anchor is the low-x,
     * low-z block of the central 2x2 and the offsets run {@code -3..+4}. That asymmetry is
     * deliberate: an even ring has to be anchored to a real block somewhere, and picking a
     * corner of the middle four is the only choice that stays an integer.
     *
     * @param filled
     *            grid of filled cells
     * @param perimeter
     *            collects the outline cells
     * @param interior
     *            collects the enclosed cells
     */
    private static void classify(final boolean[][] filled, final List<Offset> perimeter,
        final List<Offset> interior)
    {
        final int size = filled.length;
        final int anchor = (size - 1) / 2;
        for (int row = 0; row < size; row++)
        {
            for (int column = 0; column < size; column++)
            {
                if (!filled[row][column])
                {
                    continue;
                }
                final Offset offset = new Offset(column - anchor, row - anchor);
                if (isOnEdge(filled, row, column))
                {
                    perimeter.add(offset);
                }
                else
                {
                    interior.add(offset);
                }
            }
        }
    }

    /**
     * Whether a filled cell has an unfilled orthogonal neighbour.
     *
     * <p>Cells outside the grid count as unfilled, which is what makes a row that reaches
     * the grid edge come out as perimeter rather than interior.
     *
     * @param filled
     *            grid of filled cells
     * @param row
     *            row of the cell being tested
     * @param column
     *            column of the cell being tested
     * @return true if the cell is on the outline
     */
    private static boolean isOnEdge(final boolean[][] filled, final int row, final int column)
    {
        return !isFilled(filled, row - 1, column)
            || !isFilled(filled, row + 1, column)
            || !isFilled(filled, row, column - 1)
            || !isFilled(filled, row, column + 1);
    }

    /**
     * Reads the grid, treating anything outside it as unfilled.
     *
     * @param filled
     *            grid of filled cells
     * @param row
     *            row to read, possibly out of range
     * @param column
     *            column to read, possibly out of range
     * @return true if that cell exists and is filled
     */
    private static boolean isFilled(final boolean[][] filled, final int row, final int column)
    {
        if ((row < 0) || (row >= filled.length) || (column < 0) || (column >= filled.length))
        {
            return false;
        }
        return filled[row][column];
    }

    /**
     * Gets the outline cells: what the player lays in slabs, and what animates.
     *
     * @return the perimeter offsets, unmodifiable
     */
    public List<Offset> getPerimeter()
    {
        return perimeter;
    }

    /**
     * Gets the enclosed cells: the trigger volume, and what travels.
     *
     * @return the interior offsets, unmodifiable
     */
    public List<Offset> getInterior()
    {
        return interior;
    }

    /**
     * Gets how many blocks across this pattern is.
     *
     * @return the diameter in blocks
     */
    public int getDiameter()
    {
        return profile.length;
    }
}
