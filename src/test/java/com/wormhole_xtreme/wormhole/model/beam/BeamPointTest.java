package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * A point keeps a world's <em>name</em>, and resolves it only when asked.
 *
 * <p>That indirection is the whole reason this type exists rather than a Bukkit
 * {@link Location}, so these tests are about the two ends of it: recording a live location
 * without holding onto its world, and handing back null rather than a broken location when the
 * world is not there any more.
 */
class BeamPointTest
{
    @Test
    void recordingALocationKeepsTheWorldsNameRatherThanTheWorld()
    {
        final World world = mock(World.class);
        when(world.getName()).thenReturn("nether");

        final BeamPoint point = BeamPoint.of(new Location(world, 1.5, 64.0, -2.5, 90f, 45f));

        assertEquals("nether", point.worldName());
        assertEquals(1.5, point.x(), 1.0e-9);
        assertEquals(64.0, point.y(), 1.0e-9);
        assertEquals(-2.5, point.z(), 1.0e-9);
        assertEquals(90f, point.yaw(), 1.0e-6f);
        assertEquals(45f, point.pitch(), 1.0e-6f);
    }

    @Test
    void resolvingLooksTheWorldUpByNameAndRebuildsTheLocation()
    {
        final World world = mock(World.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("nether")).thenReturn(world);

            final Location resolved = new BeamPoint("nether", 1.5, 64.0, -2.5, 90f, 45f).toLocation();

            assertSame(world, resolved.getWorld(), "resolved against the live server, not a stored reference");
            assertEquals(1.5, resolved.getX(), 1.0e-9);
            assertEquals(64.0, resolved.getY(), 1.0e-9);
            assertEquals(-2.5, resolved.getZ(), 1.0e-9);
            assertEquals(90f, resolved.getYaw(), 1.0e-6f);
            assertEquals(45f, resolved.getPitch(), 1.0e-6f);
        }
    }

    /**
     * A world the server has not loaded resolves to null, not to a location in the wrong world.
     *
     * <p>Both callers branch on exactly this: {@code BeamCommand} tells the player the world is
     * not loaded rather than "no such destination", and {@code BeamTravel} refuses the trip.
     * Returning a location with a null world instead would push the failure into Bukkit, much
     * further from anything that could explain it.
     */
    @Test
    void aWorldThatIsNotLoadedResolvesToNull()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("deleted")).thenReturn(null);

            assertNull(new BeamPoint("deleted", 1.0, 2.0, 3.0, 0f, 0f).toLocation());
        }
    }

    /**
     * Two points recording the same spot are equal.
     *
     * <p>Worth pinning rather than assuming: it is what a destination read back off disk being
     * equal to the one that was written rests on, and it is only true because these are records
     * -- the hand-written classes they replaced compared by identity, so every such pair was
     * unequal.
     */
    @Test
    void twoPointsAtTheSameSpotAreEqualAndOneMovedIsNot()
    {
        final BeamPoint here = new BeamPoint("world", 1.0, 64.0, 2.0, 0f, 0f);

        assertEquals(here, new BeamPoint("world", 1.0, 64.0, 2.0, 0f, 0f));
        assertEquals(here.hashCode(), new BeamPoint("world", 1.0, 64.0, 2.0, 0f, 0f).hashCode());

        assertNotEquals(here, new BeamPoint("world", 1.0, 64.0, 2.5, 0f, 0f), "moved");
        assertNotEquals(here, new BeamPoint("nether", 1.0, 64.0, 2.0, 0f, 0f), "another world");
        assertNotEquals(here, new BeamPoint("world", 1.0, 64.0, 2.0, 180f, 0f), "facing elsewhere");
    }
}
