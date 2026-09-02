package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The index is what the player move path asks, so what it answers has to be exactly right.
 *
 * <p>Two distinctions carry real behaviour and are easy to blur. The first is interior
 * versus perimeter: only the interior arms a cycle, because the edge is a threshold people
 * cross rather than a place they stand, and indexing the edge as a trigger would fire rings
 * at anyone walking past. The second is the trigger volume's depth and direction — a floor
 * ring holds its passengers above the ring plane and a ceiling ring below it, and an
 * orientation handled backwards produces a ring that looks correct and never fires.
 */
public class RingIndexTest
{
    private static final String WORLD = "world";
    private static final int REACH = 3;

    /** A pair whose two ends are far enough apart not to overlap. */
    private static RingPair pair(final RingOrientation orientation)
    {
        final Ring a = new Ring(100, 64, 100, RingPattern.ODD, orientation, Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, orientation, Material.STONE_SLAB, Material.GLOWSTONE);
        return new RingPair("testpair", WORLD, a, b);
    }

    @BeforeEach
    public void clearBefore()
    {
        RingIndex.clear();
    }

    @AfterEach
    public void clearAfter()
    {
        RingIndex.clear();
    }

    @Test
    public void theAnchorOfAFloorRingIsInsideItsOwnTriggerVolume()
    {
        final RingPair pair = pair(RingOrientation.FLOOR);
        RingIndex.add(pair, REACH);

        final RingIndex.RingEnd end = RingIndex.volumeAt(WORLD, 100, 64, 100);
        assertNotNull(end);
        assertSame(pair, end.getPair());
        assertSame(pair.getEndA(), end.getRing());
    }

    @Test
    public void bothEndsOfAPairAreIndexedAndResolveToTheSamePair()
    {
        final RingPair pair = pair(RingOrientation.FLOOR);
        RingIndex.add(pair, REACH);

        final RingIndex.RingEnd first = RingIndex.volumeAt(WORLD, 100, 64, 100);
        final RingIndex.RingEnd second = RingIndex.volumeAt(WORLD, 200, 64, 200);
        assertNotNull(first);
        assertNotNull(second);
        assertSame(pair, first.getPair());
        assertSame(pair, second.getPair());
        assertNotSame(first.getRing(), second.getRing());
    }

    @Test
    public void aFloorRingsVolumeRunsUpwardAndACeilingRingsRunsDown()
    {
        final RingPair floor = pair(RingOrientation.FLOOR);
        RingIndex.add(floor, REACH);
        assertNotNull(RingIndex.volumeAt(WORLD, 100, 65, 100), "floor ring should reach up");
        assertNull(RingIndex.volumeAt(WORLD, 100, 63, 100), "floor ring should not reach down");

        RingIndex.clear();

        final RingPair ceiling = pair(RingOrientation.CEILING);
        RingIndex.add(ceiling, REACH);
        assertNotNull(RingIndex.volumeAt(WORLD, 100, 63, 100), "ceiling ring should reach down");
        assertNull(RingIndex.volumeAt(WORLD, 100, 65, 100), "ceiling ring should not reach up");
    }

    @Test
    public void theVolumeStopsAtTheConfiguredReach()
    {
        final RingPair pair = pair(RingOrientation.FLOOR);
        RingIndex.add(pair, REACH);

        assertNotNull(RingIndex.volumeAt(WORLD, 100, 64 + (REACH - 1), 100));
        assertNull(RingIndex.volumeAt(WORLD, 100, 64 + REACH, 100));
    }

    @Test
    public void perimeterBlocksAreNotTriggers()
    {
        // The ring's own row has its outline at dx=-3. Standing there is standing on the
        // ring, which must not start a cycle.
        final RingPair pair = pair(RingOrientation.FLOOR);
        RingIndex.add(pair, REACH);

        assertNull(RingIndex.volumeAt(WORLD, 97, 64, 100), "the edge must not arm a ring");
        assertNotNull(RingIndex.perimeterAt(WORLD, 97, 64, 100), "but it is still perimeter");
    }

    @Test
    public void aBlockOutsideEveryRingResolvesToNothing()
    {
        RingIndex.add(pair(RingOrientation.FLOOR), REACH);
        assertNull(RingIndex.volumeAt(WORLD, 150, 64, 150));
    }

    @Test
    public void anotherWorldsBlocksAreNeverMatched()
    {
        // Coordinates repeat across worlds, so a ring in the overworld must not answer for
        // the same position in the nether.
        RingIndex.add(pair(RingOrientation.FLOOR), REACH);
        assertNull(RingIndex.volumeAt("world_nether", 100, 64, 100));
    }

    @Test
    public void removingAPairTakesBothEndsOutOfTheIndex()
    {
        final RingPair pair = pair(RingOrientation.FLOOR);
        RingIndex.add(pair, REACH);
        RingIndex.remove(pair, REACH);

        assertNull(RingIndex.volumeAt(WORLD, 100, 64, 100));
        assertNull(RingIndex.volumeAt(WORLD, 200, 64, 200));
        assertNull(RingIndex.perimeterAt(WORLD, 97, 64, 100));
    }

    @Test
    public void positionsPackAndDistinguishNeighbouringBlocks()
    {
        // Every lookup on the move path is this one function. Two different blocks colliding
        // to the same key would make one ring answer for another's position.
        assertEquals(RingIndex.pack(10, 64, -20), RingIndex.pack(10, 64, -20));
        assertNotEquals(RingIndex.pack(10, 64, -20), RingIndex.pack(11, 64, -20));
        assertNotEquals(RingIndex.pack(10, 64, -20), RingIndex.pack(10, 65, -20));
        assertNotEquals(RingIndex.pack(10, 64, -20), RingIndex.pack(10, 64, -21));
    }

    @Test
    public void aPackedPositionReadsBackAsTheBlockItCameFrom()
    {
        // The restore path unpacks these to work out which block to put back, so a position
        // that does not survive the round trip puts a block back in the wrong place —
        // silently, and thousands of blocks away.
        final int[][] cases = {
            { 10, 64, -20 }, { 0, 0, 0 }, { -1, -1, -1 },
            { -2000000, 319, 2000000 }, { 2000000, -64, -2000000 },
        };
        for (final int[] c : cases)
        {
            final long packed = RingIndex.pack(c[0], c[1], c[2]);
            assertEquals(c[0], RingIndex.unpackX(packed), "x of " + c[0] + "," + c[1] + "," + c[2]);
            assertEquals(c[1], RingIndex.unpackY(packed), "y of " + c[0] + "," + c[1] + "," + c[2]);
            assertEquals(c[2], RingIndex.unpackZ(packed), "z of " + c[0] + "," + c[1] + "," + c[2]);
        }
    }

    @Test
    public void everyHeightInTheWorldSurvivesTheRoundTrip()
    {
        // The world starts at -64, so rings in deepslate, caves and on the nether floor all
        // sit below zero. Y is the coordinate a naive mask gets wrong, because twelve bits
        // masked off a negative number come back as a large positive one.
        for (int y = -64; y <= 319; y++)
        {
            assertEquals(y, RingIndex.unpackY(RingIndex.pack(7, y, -7)), "height " + y);
        }
    }

    @Test
    public void negativeAndFarOutCoordinatesStillPackDistinctly()
    {
        // Negative coordinates are the case a hand-rolled packing usually gets wrong, and
        // rings will be built in all four quadrants.
        assertNotEquals(RingIndex.pack(-1, -60, -1), RingIndex.pack(1, -60, 1));
        assertNotEquals(RingIndex.pack(-2000000, 300, -2000000), RingIndex.pack(2000000, 300, 2000000));
    }
}
