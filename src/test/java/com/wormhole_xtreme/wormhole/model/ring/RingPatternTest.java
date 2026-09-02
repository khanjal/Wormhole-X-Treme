package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The two ring footprints are generated, not written out, so the generator is what needs
 * checking.
 *
 * <p>{@link RingPattern} is handed nothing but a list of row widths and works out the rest:
 * where the anchor sits, which cells are the outline the player lays in slabs, and which are
 * the interior that triggers a cycle and travels. That derivation is the piece every other
 * part of the subsystem trusts — the index expands it into block positions, the animator
 * walks the perimeter, and the transit snapshots the interior — so an off-by-one here would
 * surface as rings that fire from the wrong blocks rather than as anything obviously broken.
 *
 * <p>The expected shapes, with {@code #} the perimeter and {@code ·} the interior:
 *
 * <pre>
 *   ODD (5)          EVEN (6)
 *  . # # # .      . # # # # .
 *  # · · · #      # · · · · #
 *  # · · · #      # · · · · #
 *  # · · · #      # · · · · #
 *  . # # # .      # · · · · #
 *                 . # # # # .
 * </pre>
 */
public class RingPatternTest
{
    /** Collects offsets into a set of "dx,dz" strings, so membership reads directly. */
    private static Set<String> cells(final List<RingPattern.Offset> offsets)
    {
        final Set<String> out = new HashSet<String>();
        for (final RingPattern.Offset offset : offsets)
        {
            out.add(offset.getDx() + "," + offset.getDz());
        }
        return out;
    }

    @Test
    public void theOddPatternIsFiveAcrossWithTwelveEdgeBlocksAroundAThreeByThree()
    {
        assertEquals(5, RingPattern.ODD.getDiameter());
        assertEquals(12, RingPattern.ODD.getPerimeter().size());
        assertEquals(9, RingPattern.ODD.getInterior().size());
    }

    @Test
    public void theEvenPatternIsSixAcrossWithSixteenEdgeBlocksAroundAFourByFour()
    {
        assertEquals(6, RingPattern.EVEN.getDiameter());
        assertEquals(16, RingPattern.EVEN.getPerimeter().size());
        assertEquals(16, RingPattern.EVEN.getInterior().size());
    }

    @Test
    public void theOddInteriorIsTheThreeByThreeCentredOnTheAnchor()
    {
        final Set<String> interior = cells(RingPattern.ODD.getInterior());
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                assertTrue(interior.contains(dx + "," + dz), "expected interior at " + dx + "," + dz);
            }
        }
    }

    @Test
    public void theEvenInteriorRunsMinusOneToPlusTwoBecauseTheAnchorIsACornerOfTheMiddleFour()
    {
        // An even ring has no centre block, so it is anchored to the low-x, low-z block of
        // its central 2x2. That is what makes the offsets asymmetric, and getting it wrong
        // shifts the whole ring one block without changing its shape.
        final Set<String> interior = cells(RingPattern.EVEN.getInterior());
        for (int dx = -1; dx <= 2; dx++)
        {
            for (int dz = -1; dz <= 2; dz++)
            {
                assertTrue(interior.contains(dx + "," + dz), "expected interior at " + dx + "," + dz);
            }
        }
    }

    @Test
    public void cornersAreCutSoTheRingReadsAsACircleAndNotASquare()
    {
        // The four corners of the bounding box are the whole reason these are circles. A
        // pattern that kept them would be a square with a hole in it.
        final Set<String> odd = cells(RingPattern.ODD.getPerimeter());
        assertFalse(odd.contains("-2,-2"));
        assertFalse(odd.contains("2,-2"));
        assertFalse(odd.contains("-2,2"));
        assertFalse(odd.contains("2,2"));

        final Set<String> even = cells(RingPattern.EVEN.getPerimeter());
        assertFalse(even.contains("-2,-2"));
        assertFalse(even.contains("3,-2"));
        assertFalse(even.contains("-2,3"));
        assertFalse(even.contains("3,3"));
    }

    @Test
    public void perimeterAndInteriorNeverShareABlock()
    {
        // A block cannot be both a thing that animates and a thing that holds a passenger.
        // The whole design leans on that, including the rule that only the interior arms a
        // cycle.
        for (final RingPattern pattern : RingPattern.values())
        {
            final Set<String> perimeter = cells(pattern.getPerimeter());
            final Set<String> interior = cells(pattern.getInterior());
            for (final String cell : interior)
            {
                assertFalse(perimeter.contains(cell), pattern + " has " + cell + " in both");
            }
        }
    }

    @Test
    public void everyInteriorBlockIsFullyEnclosedByTheDisc()
    {
        // Being interior means having no unfilled orthogonal neighbour, so every neighbour
        // of an interior cell must itself be part of the ring. If one were not, a passenger
        // could stand in the trigger volume with open air beside them where the ring should
        // be, and the animation would have a gap in it.
        for (final RingPattern pattern : RingPattern.values())
        {
            final Set<String> filled = cells(pattern.getPerimeter());
            filled.addAll(cells(pattern.getInterior()));
            for (final RingPattern.Offset offset : pattern.getInterior())
            {
                final int dx = offset.getDx();
                final int dz = offset.getDz();
                assertTrue(filled.contains((dx - 1) + "," + dz), pattern + " gap west of " + offset);
                assertTrue(filled.contains((dx + 1) + "," + dz), pattern + " gap east of " + offset);
                assertTrue(filled.contains(dx + "," + (dz - 1)), pattern + " gap north of " + offset);
                assertTrue(filled.contains(dx + "," + (dz + 1)), pattern + " gap south of " + offset);
            }
        }
    }

    @Test
    public void theOffsetTablesCannotBeMutatedByCallers()
    {
        // These are shared out of a static enum table. A caller that could edit one would
        // reshape every ring on the server at once.
        assertThrows(UnsupportedOperationException.class,
            () -> RingPattern.ODD.getPerimeter().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> RingPattern.EVEN.getInterior().clear());
    }
}
