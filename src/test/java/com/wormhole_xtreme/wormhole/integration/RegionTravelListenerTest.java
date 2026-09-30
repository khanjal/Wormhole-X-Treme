package com.wormhole_xtreme.wormhole.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.events.StargatePlayerTravelEvent;
import com.wormhole_xtreme.wormhole.integration.RegionFlags.Action;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * A trip is refused when a region at either end of the wormhole denies {@code wormhole-use}.
 *
 * <p>Both ends, because a region owner who denies use means nobody arrives in it either: dialling
 * into a denied region is not refused at the dial, so this is the only place that stops it.
 */
class RegionTravelListenerTest
{
    private static final Location HERE = new Location(null, 0, 64, 0);
    private static final Location THERE = new Location(null, 500, 64, 0);

    private final Player player = mock(Player.class);
    private final RegionTravelListener listener = new RegionTravelListener();

    RegionTravelListenerTest()
    {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown()
    {
        RegionFlags.setCheckForTest(null);
    }

    private static Stargate gateAt(final Location arrival)
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGatePlayerTeleportLocation()).thenReturn(arrival);
        return gate;
    }

    private StargatePlayerTravelEvent trip()
    {
        final StargatePlayerTravelEvent event =
            new StargatePlayerTravelEvent(gateAt(HERE), player, gateAt(THERE), THERE);
        listener.onTravel(event);
        return event;
    }

    /** Use denied only where the named spot is. */
    private static void denyUseAt(final Location denied)
    {
        RegionFlags.setCheckForTest((who, where, action) -> (action != Action.USE) || !where.equals(denied));
    }

    /** Leaving from a gate in a denying region is refused, with the region's reason. */
    @Test
    void aDepartureGateInADenyingRegionCancelsTheTrip()
    {
        denyUseAt(HERE);

        assertTrue(trip().isCancelled());
        verify(player).sendMessage(RegionFlags.USE_REFUSED);
    }

    /** Arriving in a denying region is refused too, even though the gate left from allows it. */
    @Test
    void aDestinationInADenyingRegionCancelsTheTrip()
    {
        denyUseAt(THERE);

        assertTrue(trip().isCancelled());
        verify(player).sendMessage(RegionFlags.USE_REFUSED);
    }

    /** The counterpart: both ends allowing leaves the trip alone and says nothing. */
    @Test
    void bothEndsAllowingLeavesTheTripAlone()
    {
        RegionFlags.setCheckForTest((who, where, action) -> true);

        assertFalse(trip().isCancelled());
        verify(player, never()).sendMessage(any(String.class));
    }

    /**
     * Holding forward against a refused gate is told once, not every tick.
     *
     * <p>A cancelled step puts the player back outside, so the next move raises the event again;
     * without the reminder window every one of those was its own chat line.
     */
    @Test
    void walkingIntoARefusedGateRepeatedlyIsToldOnce()
    {
        denyUseAt(HERE);

        assertTrue(trip().isCancelled());
        assertTrue(trip().isCancelled());
        assertTrue(trip().isCancelled());

        verify(player, times(1)).sendMessage(RegionFlags.USE_REFUSED);
    }
}
