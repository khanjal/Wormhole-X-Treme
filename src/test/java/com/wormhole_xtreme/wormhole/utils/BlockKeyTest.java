package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * The packed block key that the gate and ring indexes are both built on.
 *
 * <p>These keys are what "is this block part of a gate" is answered with, so a collision or a
 * coordinate that does not survive the round trip is not a near-miss -- it is one gate
 * answering for another's blocks, or a block resolving to a gate thousands of blocks away.
 *
 * <p>The packing was lifted out of {@code RingIndex} so the gate indexes could stop keying on
 * {@code Location}. {@code RingIndexTest} pins the ring side; this pins the shared
 * implementation, and in particular the two cases a hand-rolled packing gets wrong.
 */
class BlockKeyTest
{
    /**
     * A block below sea level comes back at the height it went in.
     *
     * <p>Y lives in the low twelve bits, which a plain mask cannot sign-extend: masking a
     * negative y gives a large positive number instead. The world floor is y=-64, so gates in
     * deepslate, in caves and on the nether floor are all below zero -- this is the ordinary
     * case, not an edge one.
     */
    @Test
    void aBlockBelowZeroSurvivesTheRoundTrip()
    {
        final int[][] coords = {
            { 0, 0, 0 },
            { 10, 64, -20 },
            { -1, -64, -1 },
            { -2000000, -60, 2000000 },
            { 2000000, 319, -2000000 },
        };
        for (final int[] c : coords)
        {
            final long packed = BlockKey.pack(c[0], c[1], c[2]);
            assertEquals(c[0], BlockKey.unpackX(packed), "x of " + c[0] + "," + c[1] + "," + c[2]);
            assertEquals(c[1], BlockKey.unpackY(packed), "y of " + c[0] + "," + c[1] + "," + c[2]);
            assertEquals(c[2], BlockKey.unpackZ(packed), "z of " + c[0] + "," + c[1] + "," + c[2]);
        }
    }

    /**
     * The whole build range packs and unpacks, not just the part above zero.
     */
    @Test
    void everyHeightInTheBuildRangeSurvivesTheRoundTrip()
    {
        for (int y = -64; y <= 319; y++)
        {
            assertEquals(y, BlockKey.unpackY(BlockKey.pack(7, y, -7)), "height " + y);
        }
    }

    /**
     * Mirrored coordinates are not the same key.
     *
     * <p>A sign dropped somewhere in the packing shows up exactly here: (-1, -60, -1) and
     * (1, -60, 1) collapsing onto one key would mean a gate at one of them claiming the other.
     */
    @Test
    void mirroredCoordinatesDoNotCollide()
    {
        assertNotEquals(BlockKey.pack(-1, -60, -1), BlockKey.pack(1, -60, 1));
        assertNotEquals(BlockKey.pack(-2000000, 300, -2000000), BlockKey.pack(2000000, 300, 2000000));
    }

    /**
     * Each of the three axes moves the key on its own.
     */
    @Test
    void movingAlongAnyAxisChangesTheKey()
    {
        assertEquals(BlockKey.pack(10, 64, -20), BlockKey.pack(10, 64, -20));
        assertNotEquals(BlockKey.pack(10, 64, -20), BlockKey.pack(11, 64, -20));
        assertNotEquals(BlockKey.pack(10, 64, -20), BlockKey.pack(10, 65, -20));
        assertNotEquals(BlockKey.pack(10, 64, -20), BlockKey.pack(10, 64, -21));
    }

    /**
     * Negative chunk coordinates are distinct keys.
     *
     * <p>Chunk keys bucket the gate spatial index. Two chunks sharing a key would put one
     * chunk's gate blocks in the other's bucket, which a radius query would then either miss
     * or answer with blocks from the wrong side of the origin.
     */
    @Test
    void chunksOnEitherSideOfTheOriginDoNotCollide()
    {
        assertNotEquals(BlockKey.packChunk(-1, -1), BlockKey.packChunk(1, 1));
        assertNotEquals(BlockKey.packChunk(-1, 1), BlockKey.packChunk(1, -1));
        assertNotEquals(BlockKey.packChunk(0, -1), BlockKey.packChunk(-1, 0));
        assertEquals(BlockKey.packChunk(-7, 12), BlockKey.packChunk(-7, 12));
    }

    /**
     * A chunk key and a block key are drawn from different spaces.
     *
     * <p>They are never stored in the same map, and this is what keeps that true by accident
     * as well as by intent: chunk (0,0) and the block at the origin do not share a key.
     */
    @Test
    void aChunkKeyIsNotTheBlockKeyOfTheSamePairOfNumbers()
    {
        assertNotEquals(BlockKey.packChunk(1, 1), BlockKey.pack(1, 0, 1));
    }
}
