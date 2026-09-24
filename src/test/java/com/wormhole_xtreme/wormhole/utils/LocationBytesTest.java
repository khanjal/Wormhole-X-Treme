package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/**
 * The 32-byte location that gate saves carry for their arrival points.
 *
 * <p>The writer has put pitch before yaw since the original plugin, and the reader handed
 * those two floats to {@code Location} in yaw-then-pitch order, so every round trip swapped
 * them. The fix is on the reading side because every save already on disk is pitch-first.
 */
class LocationBytesTest
{
    /**
     * Yaw and pitch come back in their own fields, not each other's.
     *
     * <p>Distinct values on purpose: equal ones, or the zeroes most test locations carry,
     * round-trip the same whichever way round the floats are read.
     */
    @Test
    void yawAndPitchSurviveTheRoundTrip()
    {
        final Location in = new Location(null, 10.5, 64.0, -20.25, 135.0f, -30.0f);

        final Location out = DataUtils.locationFromBytes(DataUtils.locationToBytes(in), null);

        assertEquals(10.5, out.getX());
        assertEquals(64.0, out.getY());
        assertEquals(-20.25, out.getZ());
        assertEquals(135.0f, out.getYaw(), "yaw");
        assertEquals(-30.0f, out.getPitch(), "pitch");
    }

    /**
     * A save written before the fix still reads with its yaw and pitch where they belong.
     *
     * <p>Built by hand in the layout the writer has always used, so fixing the round trip by
     * turning the writer round instead of the reader -- which would pass the test above and
     * misread every gate already saved -- fails here.
     */
    @Test
    void aSaveInTheExistingPitchFirstLayoutReadsCorrectly()
    {
        final ByteBuffer b = ByteBuffer.allocate(32);
        b.putDouble(1.0).putDouble(2.0).putDouble(3.0);
        b.putFloat(-30.0f); // pitch
        b.putFloat(135.0f); // yaw

        final Location out = DataUtils.locationFromBytes(b.array(), null);

        assertEquals(135.0f, out.getYaw(), "yaw");
        assertEquals(-30.0f, out.getPitch(), "pitch");
    }
}
