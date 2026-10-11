package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.RippleRings.Cell;

/**
 * Which ring each cell of an opening ripples in (#579).
 *
 * <p>The owner's rules, one per test: an odd opening starts from one cell, an even one from a two
 * by two, an odd by even one from the pair in its middle, and an opening nine or more each way from
 * a patch two cells wider each way. Each opening is drawn as a picture of ring numbers, top row
 * first, so a wrong ring shows as the cell it is in rather than as a count that is off by one.
 */
class RippleRingsTest
{
    /** Ring numbers past nine, as letters, so Grand's eleven rings fit one character a cell. */
    private static final String DIGITS = "0123456789abcdefghij";

    /** Every cell of a box this size the predicate keeps, bottom row first. */
    private static List<Cell> cells(final int width, final int height, final BiPredicate<Integer, Integer> keep)
    {
        final List<Cell> cells = new ArrayList<>();
        for (int up = 0; up < height; up++)
        {
            for (int across = 0; across < width; across++)
            {
                if (keep.test(across, up))
                {
                    cells.add(new Cell(across, up));
                }
            }
        }
        return cells;
    }

    /** The whole of a rectangular opening. */
    private static List<Cell> cells(final int width, final int height)
    {
        return cells(width, height, (across, up) -> true);
    }

    /** An opening's rings as a picture of ring numbers, top row first, a dot where it has no cell. */
    private static List<String> picture(final List<Cell> cells, final int width, final int height)
    {
        final List<List<Integer>> rings = RippleRings.of(cells, width, height);
        final Map<Cell, Integer> ringOf = new HashMap<>();
        for (int ring = 0; ring < rings.size(); ring++)
        {
            for (final int index : rings.get(ring))
            {
                ringOf.put(cells.get(index), ring);
            }
        }
        final List<String> rows = new ArrayList<>();
        for (int up = height - 1; up >= 0; up--)
        {
            final StringBuilder row = new StringBuilder();
            for (int across = 0; across < width; across++)
            {
                final Integer ring = ringOf.get(new Cell(across, up));
                row.append((ring == null) ? '.' : DIGITS.charAt(ring));
            }
            rows.add(row.toString());
        }
        return rows;
    }

    private static List<String> picture(final int width, final int height)
    {
        return picture(cells(width, height), width, height);
    }

    @Test
    void aOneCellOpeningIsOneRing()
    {
        assertEquals(List.of(List.of(0)), RippleRings.of(cells(1, 1), 1, 1));
    }

    @Test
    void aTwoByTwoOpeningIsAllPatch()
    {
        assertEquals(List.of(List.of(0, 1, 2, 3)), RippleRings.of(cells(2, 2), 2, 2),
            "an even opening starts from a two by two, which is the whole of this one");
    }

    @Test
    void anOddOpeningStartsFromTheOneCellInItsMiddle()
    {
        assertEquals(List.of(
            "32223",
            "21112",
            "21012",
            "21112",
            "32223"), picture(5, 5), "Standard's five by five: one cell, then rings a block wide, the corners last");
    }

    @Test
    void anEvenOpeningStartsFromTheTwoByTwoInItsMiddle()
    {
        assertEquals(List.of(
            "1111",
            "1001",
            "1001",
            "1111"), picture(4, 4));
        assertEquals(List.of(
            "322223",
            "211112",
            "210012",
            "210012",
            "211112",
            "322223"), picture(6, 6));
    }

    @Test
    void anOddByEvenOpeningStartsFromThePairInItsMiddle()
    {
        assertEquals(List.of(
            "21112",
            "21012",
            "21012",
            "21112"), picture(5, 4), "odd across, even up: one across and two up");
        assertEquals(List.of(
            "2222",
            "1111",
            "1001",
            "1111",
            "2222"), picture(4, 5), "even across, odd up: two across and one up");
        assertEquals(List.of(
            "0",
            "0"), picture(1, 2), "Minimal's two cells are the pair itself");
    }

    @Test
    void anOpeningJustUnderNineEachWayKeepsTheSmallPatch()
    {
        assertEquals(List.of(
            "44333344",
            "43222234",
            "32111123",
            "32100123",
            "32100123",
            "32111123",
            "43222234",
            "44333344"), picture(8, 8), "Large's eight by eight starts from a two by two");
        assertEquals(List.of(
            "544333445",
            "443222344",
            "432111234",
            "432101234",
            "432101234",
            "432111234",
            "443222344",
            "544333445"), picture(9, 8), "nine across but eight up is not large: the pair in the middle");
    }

    @Test
    void aLargeOddOpeningStartsFromThreeByThree()
    {
        assertEquals(List.of(
            "443333344",
            "432222234",
            "321111123",
            "321000123",
            "321000123",
            "321000123",
            "321111123",
            "432222234",
            "443333344"), picture(9, 9));
    }

    @Test
    void aLargeEvenOpeningStartsFromFourByFour()
    {
        assertEquals(List.of(
            "4433333344",
            "4322222234",
            "3211111123",
            "3210000123",
            "3210000123",
            "3210000123",
            "3210000123",
            "3211111123",
            "4322222234",
            "4433333344"), picture(10, 10));
    }

    @Test
    void aLargeOddByEvenOpeningStartsFromThreeByFour()
    {
        assertEquals(List.of(
            "443333344",
            "432222234",
            "321111123",
            "321000123",
            "321000123",
            "321000123",
            "321000123",
            "321111123",
            "432222234",
            "443333344"), picture(9, 10));
    }

    /** Grand's eighteen by seventeen: a four by three patch and eleven rings, a little over a second at two ticks a step. */
    @Test
    void grandsOpeningRipplesInElevenRings()
    {
        final List<String> grand = picture(18, 17);
        assertEquals("765432100001234567", grand.get(8), "the middle row: four cells of patch, then a ring a block");
        assertEquals("a9988777777778899a", grand.get(0), "the top row, the corners last");
        assertEquals(11, RippleRings.of(cells(18, 17), 18, 17).size());
    }

    @Test
    void aDiamondOpeningRingsWithTheCellsItHas()
    {
        assertEquals(List.of(
            "...3...",
            "..222..",
            ".21112.",
            "3210123",
            ".21112.",
            "..222..",
            "...3..."), picture(cells(7, 7, (across, up) -> (Math.abs(across - 3) + Math.abs(up - 3)) <= 3), 7, 7));
    }

    @Test
    void anOpeningWithNoCellAtItsCentreStartsFromItsFirstRing()
    {
        final List<Cell> hollow = cells(5, 5, (across, up) -> (across != 2) || (up != 2));
        final List<List<Integer>> rings = RippleRings.of(hollow, 5, 5);

        assertEquals(3, rings.size(), "the empty centre is not drawn as a step of its own");
        assertEquals(8, rings.get(0).size(), "it starts with the eight round the missing centre");
    }

    @Test
    void anEmptyOpeningHasNoRings()
    {
        assertTrue(RippleRings.of(List.of(), 0, 0).isEmpty());
    }

    /**
     * Every rectangle up to twelve each way: each cell in exactly one ring, rings in order of distance, and
     * a ring mirrored across either middle line is the same ring.
     */
    @Test
    void everyOpeningUpToTwelveIsCoveredOnceInOrderAndSymmetric()
    {
        for (int width = 1; width <= 12; width++)
        {
            for (int height = 1; height <= 12; height++)
            {
                final List<Cell> cells = cells(width, height);
                final List<List<Integer>> rings = RippleRings.of(cells, width, height);
                final Map<Cell, Integer> ringOf = new HashMap<>();
                for (int ring = 0; ring < rings.size(); ring++)
                {
                    assertFalse(rings.get(ring).isEmpty(), width + "x" + height + " has an empty ring " + ring);
                    for (final int index : rings.get(ring))
                    {
                        assertNull(ringOf.put(cells.get(index), ring), width + "x" + height + " repeats a cell");
                    }
                }
                assertEquals(width * height, ringOf.size(), width + "x" + height + " leaves a cell out");
                for (final Map.Entry<Cell, Integer> entry : ringOf.entrySet())
                {
                    final Cell cell = entry.getKey();
                    assertEquals(entry.getValue(), ringOf.get(new Cell((width - 1) - cell.across(), cell.up())),
                        width + "x" + height + " is lopsided across at " + cell);
                    assertEquals(entry.getValue(), ringOf.get(new Cell(cell.across(), (height - 1) - cell.up())),
                        width + "x" + height + " is lopsided up at " + cell);
                }
            }
        }
    }

    @Test
    void aDistanceIsRoundedToTheNearestRing()
    {
        assertEquals(0, RippleRings.ringAt(0));
        assertEquals(1, RippleRings.ringAt(1));
        assertEquals(1, RippleRings.ringAt(2), "a diagonal neighbour, 1.41, is the first ring");
        assertEquals(2, RippleRings.ringAt(4));
        assertEquals(2, RippleRings.ringAt(6), "2.45 rounds down");
        assertEquals(3, RippleRings.ringAt(7), "2.65 rounds up");
        assertEquals(3, RippleRings.ringAt(12), "3.46 rounds down");
        assertEquals(4, RippleRings.ringAt(13), "3.61 rounds up");
    }

    @Test
    void thePatchIsOneOrTwoAcrossAndTwoMoreOnALargeGate()
    {
        assertEquals(1, RippleRings.patchSide(5, false));
        assertEquals(2, RippleRings.patchSide(4, false));
        assertEquals(3, RippleRings.patchSide(9, true));
        assertEquals(4, RippleRings.patchSide(10, true));
    }
}
