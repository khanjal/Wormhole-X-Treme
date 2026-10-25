package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The cell-key arithmetic the window sweeps run per cell. Every expected value is a literal, never worked out with
 * the code under test, so a wrong shift or mask cannot agree with itself.
 */
class CellsTest
{
    private static final int[] XZ = { -33554432, -33554431, -4097, -17, -2, -1, 0, 1, 2, 17, 4097, 33554430, 33554431 };

    private static final int[] Y = { -2048, -2047, -17, -2, -1, 0, 1, 2, 17, 2046, 2047 };

    /** A swapped or truncated field would change one of the three numbers on the way back out. */
    @Test
    void keyUnpacksToTheSameXYAndZAcrossTheWholeRange()
    {
        for (final int x : XZ)
        {
            for (final int y : Y)
            {
                for (final int z : XZ)
                {
                    final long key = Cells.key(x, y, z);
                    assertEquals(x, Cells.unpackX(key), "x of " + x + "," + y + "," + z);
                    assertEquals(y, Cells.unpackY(key), "y of " + x + "," + y + "," + z);
                    assertEquals(z, Cells.unpackZ(key), "z of " + x + "," + y + "," + z);
                }
            }
        }
    }

    /** x sits in the top 26 bits, z in the next 26, y in the low 12: a field moved by one bit shows here. */
    @Test
    void eachCoordinateLandsInItsOwnBitsOfTheKey()
    {
        assertEquals(0x4000000000L, Cells.key(1, 0, 0), "x is bit 38");
        assertEquals(0x1000L, Cells.key(0, 0, 1), "z is bit 12");
        assertEquals(0x1L, Cells.key(0, 1, 0), "y is bit 0");
        assertEquals(0x4000001000L, Cells.key(1, 0, 1));
        assertEquals(0x4000001001L, Cells.key(1, 1, 1));
        assertEquals(0L, Cells.key(0, 0, 0));
    }

    /** A negative coordinate must fill only its own field; sign extension into a neighbour would corrupt it. */
    @Test
    void aNegativeCoordinateDoesNotBleedIntoTheFieldsNextToIt()
    {
        assertEquals(0xFFFFFFC000000000L, Cells.key(-1, 0, 0), "x only");
        assertEquals(0x3FFFFFF000L, Cells.key(0, 0, -1), "z only");
        assertEquals(0xFFFL, Cells.key(0, -1, 0), "y only");
        assertEquals(-1L, Cells.key(-1, -1, -1), "all three fill the long");
    }

    /** Only the documented low bits survive, so a huge value cannot reach the next field. */
    @Test
    void bitsAboveTheDocumentedWidthAreDroppedWithoutTouchingTheOtherFields()
    {
        assertEquals(0x4000007005L, Cells.key(0x4000001, 5, 7), "bit 26 of x is dropped");
        assertEquals(0x4000007005L, Cells.key(Integer.MIN_VALUE + 1, 5, 7), "bit 31 of x is dropped");
        assertEquals(0x4000007005L, Cells.key(1, 5, 0x4000007), "bit 26 of z is dropped");
        assertEquals(0x4000007005L, Cells.key(1, 5, Integer.MIN_VALUE + 7), "bit 31 of z is dropped");
        assertEquals(0x4000007005L, Cells.key(1, 0x1005, 7), "bit 12 of y is dropped");
        assertEquals(0x4000007005L, Cells.key(1, 0x7FFFF005, 7), "high bits of y are dropped");
        final long key = Cells.key(0x7FFFFFF, 0x7FFFF, 0x7FFFFFF);
        assertEquals(-1L, key, "every bit of every field is set");
        assertEquals(0, Cells.unpackZ(Cells.key(0x7FFFFFFF, 0, 0)), "a huge x leaves z at 0");
        assertEquals(0, Cells.unpackY(Cells.key(0x7FFFFFFF, 0, 0)), "a huge x leaves y at 0");
        assertEquals(0, Cells.unpackX(Cells.key(0, 0, 0x7FFFFFFF)), "a huge z leaves x at 0");
        assertEquals(0, Cells.unpackY(Cells.key(0, 0, 0x7FFFFFFF)), "a huge z leaves y at 0");
        assertEquals(0, Cells.unpackX(Cells.key(0, 0x7FFFFFFF, 0)), "a huge y leaves x at 0");
        assertEquals(0, Cells.unpackZ(Cells.key(0, 0x7FFFFFFF, 0)), "a huge y leaves z at 0");
    }

    /** Two cells sharing a key would be treated as one: the sweep would skip or double-count a block. */
    @Test
    void noTwoDifferentCellsShareAKey()
    {
        final Set<Long> seen = new HashSet<>();
        int count = 0;
        for (final int x : XZ)
        {
            for (final int y : Y)
            {
                for (final int z : XZ)
                {
                    seen.add(Cells.key(x, y, z));
                    count++;
                }
            }
        }
        assertEquals(13 * 11 * 13, count);
        assertEquals(count, seen.size(), "every cell has its own key");
    }

    /** The high word is x and the low word is z, with the low word's sign kept out of the high one. */
    @Test
    void chunkKeyPacksXAboveZ()
    {
        assertEquals(0x100000002L, Cells.chunkKey(1, 2));
        assertEquals(0x200000001L, Cells.chunkKey(2, 1));
        assertEquals(0L, Cells.chunkKey(0, 0));
        assertEquals(0x7FFFFFFF00000000L, Cells.chunkKey(Integer.MAX_VALUE, 0));
        assertEquals(0x7FFFFFFFL, Cells.chunkKey(0, Integer.MAX_VALUE));
    }

    /** A negative z must not sign-extend over x, and a negative x must still read as its own high word. */
    @Test
    void chunkKeyKeepsANegativeCoordinateInItsOwnWord()
    {
        assertEquals(-1L, Cells.chunkKey(-1, -1));
        assertEquals(0xFFFFFFFFL, Cells.chunkKey(0, -1), "z only");
        assertEquals(0xFFFFFFFF00000000L, Cells.chunkKey(-1, 0), "x only");
        assertEquals(0x80000000L, Cells.chunkKey(0, Integer.MIN_VALUE));
        assertEquals(0x8000000000000000L, Cells.chunkKey(Integer.MIN_VALUE, 0));
    }

    /** Chunk (x, z) and chunk (z, x) are different chunks and must key differently. */
    @Test
    void chunkKeyDistinguishesXFromZ()
    {
        assertNotEquals(Cells.chunkKey(3, 9), Cells.chunkKey(9, 3));
        assertNotEquals(Cells.chunkKey(-3, 9), Cells.chunkKey(9, -3));
        assertNotEquals(Cells.chunkKey(0, 1), Cells.chunkKey(1, 0));
    }
}
