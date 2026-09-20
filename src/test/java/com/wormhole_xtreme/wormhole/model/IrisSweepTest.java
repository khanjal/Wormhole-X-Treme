package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/**
 * Which cells an iris covers first, and in which order.
 *
 * <p>An iris that closes from the rim inwards is the whole point of the feature: a barrier that
 * arrives all at once reads as a bug rather than a shield. These pin the order, which is pure
 * arithmetic and so can be checked without a world, a scheduler or a gate.
 *
 * <p>A {@code null} world throughout. The cells are only ever read for their block coordinates,
 * which is also why {@link IrisSweep} takes plain {@link Location}s rather than blocks -- see
 * {@code RegenerateCommandTest} for the same trick, and {@code RingSounds.centre()} for the
 * production code that does it.
 */
class IrisSweepTest
{
    private static Location at(final int x, final int y, final int z)
    {
        return new Location(null, x, y, z);
    }

    /**
     * A three-by-three opening is a middle cell and the eight around it.
     */
    private static List<Location> threeByThree()
    {
        final List<Location> cells = new ArrayList<>();
        for (int x = -1; x <= 1; x++)
        {
            for (int y = -1; y <= 1; y++)
            {
                cells.add(at(x, y, 0));
            }
        }
        return cells;
    }

    @Test
    void closingCoversTheRimFirstAndTheMiddleLast()
    {
        final List<List<Location>> rings = IrisSweep.closingRings(threeByThree());

        assertEquals(3, rings.size(), "a corner ring, an edge ring, and the middle");
        assertEquals(1, rings.get(rings.size() - 1).size(), "the middle is one cell, and it is covered last");
        assertEquals(at(0, 0, 0), rings.get(rings.size() - 1).get(0));
    }

    @Test
    void openingIsTheClosingOrderRunBackwards()
    {
        final List<Location> cells = threeByThree();
        final List<List<Location>> closing = IrisSweep.closingRings(cells);
        final List<List<Location>> opening = IrisSweep.openingRings(cells);

        assertEquals(closing.size(), opening.size());
        for (int i = 0; i < closing.size(); i++)
        {
            assertEquals(closing.get(i), opening.get(opening.size() - 1 - i),
                "ring " + i + " from the rim is ring " + i + " from the end of the opening sweep");
        }
    }

    @Test
    void openingUncoversTheMiddleFirst()
    {
        final List<List<Location>> rings = IrisSweep.openingRings(threeByThree());

        assertEquals(at(0, 0, 0), rings.get(0).get(0), "the iris draws back from the middle");
    }

    /**
     * The corners of a square opening are further out than its edges, so they go first.
     *
     * <p>Measured rather than rounded. A sweep that put the corners and the edges in one ring
     * would cover a three-by-three opening in a single step and look like no animation at all.
     */
    @Test
    void theCornersOfASquareAreTheirOwnRing()
    {
        final List<List<Location>> rings = IrisSweep.closingRings(threeByThree());

        final List<Location> first = rings.get(0);
        assertEquals(4, first.size(), "four corners, and they are the furthest out");
        assertTrue(first.contains(at(-1, -1, 0)) && first.contains(at(1, 1, 0)),
            "both diagonals are in it: " + first);
        assertEquals(4, rings.get(1).size(), "then the four edge cells");
    }

    /**
     * Every cell is covered exactly once, whatever shape the opening is.
     *
     * <p>The assertion that matters most and the easiest to lose: a sweep that drops a cell
     * leaves a hole in a closed iris, and one that covers a cell twice is only wasteful. The
     * awkward shape is on purpose -- a gate opening is not always a neat rectangle.
     */
    @Test
    void everyCellIsCoveredExactlyOnce()
    {
        final List<Location> cells = new ArrayList<>(threeByThree());
        cells.add(at(2, 0, 0));
        cells.add(at(-2, 1, 0));
        cells.add(at(0, 3, 0));

        final List<Location> swept = new ArrayList<>();
        for (final List<Location> ring : IrisSweep.closingRings(cells))
        {
            swept.addAll(ring);
        }

        assertEquals(cells.size(), swept.size(), "no cell covered twice, none dropped");
        assertTrue(swept.containsAll(cells), "and every one of them is in there");
    }

    /**
     * The rings come out ordered, furthest from the middle to nearest.
     */
    @Test
    void theClosingRingsComeOutFurthestFirst()
    {
        final List<Location> cells = new ArrayList<>(threeByThree());
        cells.add(at(4, 0, 0));

        final List<List<Location>> rings = IrisSweep.closingRings(cells);

        double previous = Double.MAX_VALUE;
        for (final List<Location> ring : rings)
        {
            final double distance = Math.abs(ring.get(0).getBlockX() - 1.5);
            assertTrue(distance <= previous,
                "ring at " + distance + " came after one at " + previous + ", so the sweep jumps about");
            previous = distance;
        }
    }

    /**
     * The middle is the middle of the opening, not the average of where its cells happen to be.
     *
     * <p>An opening with more cells on one side pulls an average off-centre, and the sweep then
     * arrives at the wrong place. This one is a plus sign with a long left arm: the average sits
     * left of the join, the midpoint of the extremes does not.
     */
    @Test
    void theMiddleIsTheMiddleOfTheOpeningNotTheAverageOfItsCells()
    {
        final List<Location> cells = new ArrayList<>();
        for (int x = -4; x <= 4; x++)
        {
            cells.add(at(x, 0, 0));
        }
        for (int x = -4; x <= -2; x++)
        {
            cells.add(at(x, 1, 0));
            cells.add(at(x, -1, 0));
        }

        final List<List<Location>> rings = IrisSweep.openingRings(cells);

        assertEquals(at(0, 0, 0), rings.get(0).get(0),
            "the join of the arms, not somewhere off in the long one");
    }

    @Test
    void anOpeningWithNoCellsSweepsNothing()
    {
        assertTrue(IrisSweep.closingRings(new ArrayList<>()).isEmpty());
        assertTrue(IrisSweep.openingRings(null).isEmpty(), "and a gate with no iris at all is not a crash");
    }

    /**
     * A single-cell opening is one ring, so the sweep still has something to do.
     */
    @Test
    void aOneBlockOpeningIsOneRing()
    {
        final List<Location> one = List.of(at(7, 64, 7));
        final List<List<Location>> rings = IrisSweep.closingRings(one);

        assertEquals(1, rings.size());
        assertEquals(1, rings.get(0).size());
        assertSame(one.get(0), rings.get(0).get(0), "and it is the cell it was given, not a copy");
    }
}
