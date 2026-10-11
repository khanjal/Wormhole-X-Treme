package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * The rings a ripple crosses a gate's opening in: out from a patch at its centre, a ring about a
 * block wide per step (#579).
 *
 * <p>Pure arithmetic over the opening's own grid, so which cell belongs to which ring is pinned
 * without a world. The patch is one cell across an odd side and two across an even one, so an odd
 * opening starts from a single cell, an even one from a two by two and an odd by even one from the
 * pair in its middle; an opening {@link #LARGE} or more each way starts two cells wider each way,
 * three by three or four by four, so a big gate does not start from a speck.
 *
 * <p>A cell's ring is its distance from the nearest cell of the patch, rounded to the nearest whole
 * block, which keeps the rings round on a square grid. Compared in whole numbers, never square
 * roots, so no two cells land in different rings by a rounding error. A ring with no cells, as at
 * the centre of an opening with none there, is left out rather than drawn as an empty step.
 */
public final class RippleRings
{
    /** How wide and how tall an opening has to be, both, before its patch is two cells wider each way. */
    static final int LARGE = 9;

    /**
     * One cell of an opening, counted from one bottom corner of the box round it.
     *
     * @param across
     *            how far across, from 0
     * @param up
     *            how far up, from 0
     */
    public record Cell(int across, int up)
    {
    }

    private RippleRings()
    {
    }

    /**
     * The rings an opening ripples in, the centre first.
     *
     * @param cells
     *            the opening's cells, inside a box {@code width} by {@code height}; need not fill it
     * @param width
     *            the box's width
     * @param height
     *            the box's height
     * @return each ring as indexes into {@code cells}, in their order there; every index once
     */
    public static List<List<Integer>> of(final List<Cell> cells, final int width, final int height)
    {
        final boolean large = (width >= LARGE) && (height >= LARGE);
        final int wide = patchSide(width, large);
        final int tall = patchSide(height, large);
        final int left = (width - wide) / 2;
        final int bottom = (height - tall) / 2;
        final Map<Integer, List<Integer>> byRing = new TreeMap<>();
        for (int i = 0; i < cells.size(); i++)
        {
            final Cell cell = cells.get(i);
            final int dx = beyond(cell.across(), left, (left + wide) - 1);
            final int dy = beyond(cell.up(), bottom, (bottom + tall) - 1);
            byRing.computeIfAbsent(ringAt((dx * dx) + (dy * dy)), ring -> new ArrayList<>()).add(i);
        }
        return new ArrayList<>(byRing.values());
    }

    /**
     * The rings lit at each step of a ripple of several waves, each wave the whole ring sequence and
     * each starting {@code gap} rings after the one before, so on a gate of enough rings two or three
     * are lit at once. A gate of too few rings for that, {@code rings <= gap * (waves - 1) + 1}, has
     * its waves back to back instead, so they still read as separate.
     *
     * @param rings
     *            how many rings the opening has
     * @param waves
     *            how many waves, at least one
     * @param gap
     *            how many rings one wave runs ahead of the next
     * @return each step's ring indexes, lowest first; every step lights at least one ring
     */
    public static List<List<Integer>> waves(final int rings, final int waves, final int gap)
    {
        if (rings <= 0)
        {
            return List.of();
        }
        final int count = Math.max(1, waves);
        final int apart = (rings > ((gap * (count - 1)) + 1)) ? gap : rings;
        final int steps = (apart * (count - 1)) + rings;
        return IntStream.range(0, steps)
            .mapToObj(step -> IntStream.range(0, count).map(wave -> step - (wave * apart))
                .filter(ring -> (ring >= 0) && (ring < rings)).sorted().distinct().boxed().toList())
            .toList();
    }

    /** How many cells the patch spans along one side of the opening. */
    static int patchSide(final int side, final boolean large)
    {
        return (((side % 2) == 0) ? 2 : 1) + (large ? 2 : 0);
    }

    /** How far a coordinate lies outside a span, 0 inside it. */
    private static int beyond(final int at, final int low, final int high)
    {
        return Math.max(0, Math.max(low - at, at - high));
    }

    /**
     * The ring a squared distance from the patch falls in: the distance rounded to a whole number.
     *
     * <p>The smallest {@code k} with {@code d <= k + 1/2}, which is {@code 4d² <= (2k+1)²} in whole
     * numbers. A squared distance is a whole number and {@code (k + 1/2)²} never is, so no distance
     * sits on a boundary. Counted up from the distance rounded down, which it is never below.
     */
    static int ringAt(final int squared)
    {
        int ring = (int) Math.sqrt(squared);
        while ((4L * squared) > (((2L * ring) + 1) * ((2L * ring) + 1)))
        {
            ring++;
        }
        return ring;
    }
}
