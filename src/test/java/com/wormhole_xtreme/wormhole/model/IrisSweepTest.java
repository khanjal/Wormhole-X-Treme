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

    /**
     * A five-by-five opening, big enough for a style to differ from the others on.
     */
    private static List<Location> fiveByFive()
    {
        final List<Location> cells = new ArrayList<>();
        for (int x = -2; x <= 2; x++)
        {
            for (int y = -2; y <= 2; y++)
            {
                cells.add(at(x, y, 0));
            }
        }
        return cells;
    }

    /** Every cell of every step, in order. */
    private static List<Location> flatten(final List<List<Location>> steps)
    {
        final List<Location> all = new ArrayList<>();
        for (final List<Location> step : steps)
        {
            all.addAll(step);
        }
        return all;
    }

    /**
     * Whatever the style, every cell is covered exactly once.
     *
     * <p>The assertion that matters for all of them at once: a style that drops a cell leaves a
     * hole in a closed iris, and one that covers a cell twice only wastes a draw. Checked per
     * style rather than once, because each builds its steps a different way -- the rings group
     * by a key, the spiral cuts a sorted list into chunks -- and the two fail differently.
     */
    @Test
    void everyStyleCoversEveryCellExactlyOnce()
    {
        for (final IrisSweep.Style style : IrisSweep.Style.values())
        {
            final List<Location> cells = fiveByFive();
            final List<Location> swept = flatten(IrisSweep.closingOrder(cells, style));

            assertEquals(cells.size(), swept.size(), style + " covered a different number of cells");
            assertTrue(swept.containsAll(cells), style + " dropped a cell");
        }
    }

    /**
     * Every style actually animates: more than one step on an opening with room for it.
     *
     * <p>How many steps differs by style, and that is the geometry rather than a choice: a
     * five-wide opening has three rows but six rings, so a rows iris crosses in half the steps
     * a sweep does at the same {@code gate-iris-step-ticks}. What none of them may do is cross
     * in one step, which is the instant iris wearing a style's name.
     */
    @Test
    void everyStyleActuallyAnimates()
    {
        for (final IrisSweep.Style style : IrisSweep.Style.values())
        {
            assertTrue(IrisSweep.closingOrder(fiveByFive(), style).size() > 1,
                style + " crossed a five-by-five opening in one step, which is not an animation");
        }
    }

    /**
     * The spiral is cut into as many steps as the sweep makes.
     *
     * <p>Unlike the others it has no natural step: it is one sorted run of cells, so the number
     * of pieces is chosen rather than found. Matching the sweep is that choice, and it is worth
     * pinning because the obvious way to cut it -- a fixed chunk size, rounded up -- silently
     * loses a step whenever it does not divide evenly.
     */
    @Test
    void theSpiralIsCutIntoAsManyStepsAsTheSweep()
    {
        assertEquals(IrisSweep.closingOrder(fiveByFive(), IrisSweep.Style.SWEEP).size(),
            IrisSweep.closingOrder(fiveByFive(), IrisSweep.Style.SPIRAL).size());
    }

    /**
     * Rows come in from the top and the bottom at once, not from one end.
     *
     * <p>An iris arriving from one side reads as a door. The first step of a closing rows iris
     * is therefore both outermost rows, and the last is the middle one.
     */
    @Test
    void rowsCloseFromTheTopAndBottomTowardsTheMiddle()
    {
        final List<List<Location>> steps = IrisSweep.closingOrder(fiveByFive(), IrisSweep.Style.ROWS);

        final List<Location> first = steps.get(0);
        assertEquals(10, first.size(), "both outermost rows of a five-wide opening: " + first);
        assertTrue(first.stream().allMatch(c -> Math.abs(c.getBlockY()) == 2),
            "and nothing from further in: " + first);

        final List<Location> last = steps.get(steps.size() - 1);
        assertTrue(last.stream().allMatch(c -> c.getBlockY() == 0), "the middle row goes last");
    }

    /**
     * Columns do the same from the sides.
     */
    @Test
    void columnsCloseFromBothSidesTowardsTheMiddle()
    {
        final List<List<Location>> steps = IrisSweep.closingOrder(fiveByFive(), IrisSweep.Style.COLUMNS);

        final List<Location> first = steps.get(0);
        assertEquals(10, first.size(), "both outermost columns: " + first);
        assertTrue(first.stream().allMatch(c -> Math.abs(c.getBlockX()) == 2),
            "and nothing from further in: " + first);
    }

    /**
     * The spiral winds outwards rather than only turning.
     *
     * <p>Sorted by radius with the angle breaking ties, so an opening spiral starts in the
     * middle and finishes at the rim. A wedge that only rotated would have the same cells in
     * every step and read as a clock hand.
     */
    @Test
    void theSpiralStartsInTheMiddleAndFinishesAtTheRim()
    {
        final List<List<Location>> steps = IrisSweep.openingOrder(fiveByFive(), IrisSweep.Style.SPIRAL);

        final Location first = steps.get(0).get(0);
        assertEquals(at(0, 0, 0), first, "an opening spiral starts at the middle");

        final List<Location> last = steps.get(steps.size() - 1);
        assertTrue(last.stream().anyMatch(c -> (Math.abs(c.getBlockX()) == 2) || (Math.abs(c.getBlockY()) == 2)),
            "and finishes out at the rim: " + last);
    }

    /**
     * A style name is read loosely, and anything unrecognised is the default.
     *
     * <p>This reads a value a server owner types. A mistyped style should cost them the style,
     * not the iris.
     */
    @Test
    void anUnknownStyleNameFallsBackToTheSweep()
    {
        assertEquals(IrisSweep.Style.SPIRAL, IrisSweep.Style.of("spiral"));
        assertEquals(IrisSweep.Style.ROWS, IrisSweep.Style.of("  RoWs "));
        assertEquals(IrisSweep.Style.SWEEP, IrisSweep.Style.of("corkscrew"));
        assertEquals(IrisSweep.Style.SWEEP, IrisSweep.Style.of(null));
    }

    /**
     * Everything {@code /wormhole config gate-iris-animation } offers is a style the plugin honours.
     *
     * <p>The list was written out by hand and did not include the styles at all, so the setting
     * that has the most values of any in the file was the one that completed to nothing. Offering
     * the wrong words would be worse than offering none: the config command takes any string, so
     * a suggested typo is accepted, silently read as the default, and never reported.
     *
     * <p>Asserted by round-tripping rather than by comparing to a second hand-written list, which
     * would only pin the two lists to each other.
     */
    @Test
    void theConfigCommandOffersEveryStyleAndInstant()
    {
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.loadDefaults();
        final List<String> offered =
            com.wormhole_xtreme.wormhole.config.ConfigManager.valuesFor("gate-iris-animation");

        for (final IrisSweep.Style style : IrisSweep.Style.values())
        {
            final String word = style.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(offered.contains(word), "should offer " + word + ", got " + offered);
            assertEquals(style, IrisSweep.Style.of(word), word + " should read back as itself");
        }
        assertTrue(offered.contains("instant"),
            "and the word that turns the sweep off, which is not a style: " + offered);
        assertEquals(IrisSweep.Style.values().length + 1, offered.size(),
            "and nothing else, or the completion names a value the plugin would ignore");
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
