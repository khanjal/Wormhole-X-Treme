package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * A destination is a name and a cost wrapped around a {@link BeamPoint}.
 *
 * <p>The point carries the resolving, so what is left to check here is that the destination
 * actually delegates to it, and that the two ways of making one set the cost the way their
 * callers assume.
 */
class BeamDestinationTest
{
    private static final BeamPoint SPOT = new BeamPoint("world", 1.5, 64.0, -2.5, 90f, 45f);

    /**
     * Resolving a destination goes through its point, including the null.
     *
     * <p>{@code BeamCommand} and {@code BeamTravel} both branch on this being null to mean "that
     * world is not loaded", so the delegation has to carry the null through rather than, say,
     * substituting the default world.
     */
    @Test
    void resolvingGoesThroughThePoint()
    {
        final World world = mock(World.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            final Location resolved = new BeamDestination("spawn", SPOT, null).toLocation();

            assertSame(world, resolved.getWorld());
            assertEquals(1.5, resolved.getX(), 1.0e-9);
            assertEquals(-2.5, resolved.getZ(), 1.0e-9);
        }
    }

    @Test
    void aDestinationInAnUnloadedWorldResolvesToNull()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);

            assertNull(new BeamDestination("spawn", SPOT, null).toLocation());
        }
    }

    /**
     * Setting one from a player's location leaves the cost inheriting.
     *
     * <p>Null rather than zero, and the difference is the whole reason the field is a
     * {@code Double}: a brand new destination charges whatever {@code BEAM_ECONOMY_USE_COST}
     * says at the time, not nothing forever.
     */
    @Test
    void aDestinationMadeFromALocationInheritsTheGlobalCost()
    {
        final World world = mock(World.class);
        when(world.getName()).thenReturn("nether");

        final BeamDestination made =
            BeamDestination.fromLocation("hub", new Location(world, 3.0, 70.0, 4.0, 180f, 10f));

        assertEquals("hub", made.name());
        assertEquals(new BeamPoint("nether", 3.0, 70.0, 4.0, 180f, 10f), made.point());
        assertNull(made.cost(), "a new destination inherits, it is not free");
    }

    /** Repricing changes the price and nothing else. */
    @Test
    void repricingKeepsTheNameAndTheSpot()
    {
        final BeamDestination priced = new BeamDestination("spawn", SPOT, 5.0).withCost(12.0);

        assertEquals(Double.valueOf(12.0), priced.cost());
        assertEquals("spawn", priced.name());
        assertEquals(SPOT, priced.point());

        assertNull(priced.withCost(null).cost(), "and it can be put back to inheriting");
    }
}
