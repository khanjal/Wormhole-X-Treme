package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The key a mirror's banner is stored and looked up by.
 *
 * <p>It is a string in the file and a record in memory, and the round trip has to be exact:
 * a key that reads back as different coordinates is a mirror whose banner no longer answers to
 * a click, with nothing in the log to say so.
 */
class BlockPlaceKeyTest
{
    /** The ordinary case, and what the file actually contains. */
    @Test
    void aKeyRoundTripsToTheSameBlock()
    {
        final BlockPlace block = new BlockPlace("world", 12, 64, -30);

        assertEquals("world:12:64:-30", block.toKey());
        assertEquals(block, BlockPlace.fromKey(block.toKey()));
    }

    /**
     * A world name containing a colon survives the round trip.
     *
     * <p>This is why the parser takes the three coordinates from the end rather than the name
     * from the front. Splitting on the first colon would read this world as {@code my} and
     * silently orphan every mirror in it -- and world names are operator-chosen, so a colon in
     * one is somebody's ordinary Tuesday rather than a hypothetical.
     */
    @Test
    void aWorldNameContainingAColonIsNotTruncated()
    {
        final BlockPlace block = new BlockPlace("my:museum:world", 1, 2, 3);

        final BlockPlace back = BlockPlace.fromKey(block.toKey());

        assertEquals("my:museum:world", back.worldName(),
            "splitting on the first colon would leave this as 'my' and lose the mirror");
        assertEquals(block, back);
    }

    /** Negative coordinates are ordinary; the minus sign must not be read as a separator. */
    @Test
    void negativeCoordinatesRoundTrip()
    {
        final BlockPlace block = new BlockPlace("world", -100, -5, -2048);

        assertEquals(block, BlockPlace.fromKey(block.toKey()));
    }

    /** A hand-edited key that is not one is skipped rather than guessed at. */
    @Test
    void aMalformedKeyReadsAsNothing()
    {
        assertNull(BlockPlace.fromKey(null));
        assertNull(BlockPlace.fromKey(""));
        assertNull(BlockPlace.fromKey("world"));
        assertNull(BlockPlace.fromKey("world:12:64"), "three parts is not four");
        assertNull(BlockPlace.fromKey("world:12:sixty-four:-30"), "a coordinate that is not a number");
        assertNull(BlockPlace.fromKey(":1:2:3"), "a key with no world name at all");
    }
}
