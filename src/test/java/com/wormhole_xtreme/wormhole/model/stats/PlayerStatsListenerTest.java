package com.wormhole_xtreme.wormhole.model.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.events.RingTravelEvent;
import com.wormhole_xtreme.wormhole.events.StargateCreatedEvent;
import com.wormhole_xtreme.wormhole.events.StargatePlayerTravelEvent;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counts;

/**
 * Gate trips, gates built and ring trips are counted from the events, for the player in the event,
 * and only for what actually happened (#237).
 *
 * <p>The travel events fire before anybody moves, and another plugin -- a region, a combat tag, a
 * jail -- may cancel them. A trip that was refused and counted anyway is a number a scoreboard shows
 * that nobody earned.
 */
class PlayerStatsListenerTest
{
    private static final UUID TRAVELLER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private final PlayerStatsListener listener = new PlayerStatsListener();

    private Player player;

    @BeforeEach
    void setUp()
    {
        PlayerStats.clear();
        player = mock(Player.class);
        // Unstubbed, a mock Player has no UUID, and every count keys on it.
        when(player.getUniqueId()).thenReturn(TRAVELLER);
    }

    @AfterEach
    void tearDown()
    {
        PlayerStats.clear();
    }

    private StargatePlayerTravelEvent gateTrip()
    {
        return new StargatePlayerTravelEvent(mock(Stargate.class), player, mock(Stargate.class), null);
    }

    @Test
    void aGateTripCountsAsATripForTheTraveller()
    {
        listener.onGateTrip(gateTrip());
        listener.onGateTrip(gateTrip());

        assertEquals(new Counts(2, 0, 0, 0), PlayerStats.of(TRAVELLER));
    }

    @Test
    void aCancelledGateTripCountsNothing()
    {
        final StargatePlayerTravelEvent refused = gateTrip();
        refused.setCancelled(true);

        listener.onGateTrip(refused);

        assertEquals(Counts.NONE, PlayerStats.of(TRAVELLER), "a refused trip was never taken");
    }

    @Test
    void aGateBuiltCountsForItsBuilder()
    {
        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), player));
        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), player));
        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), player));

        assertEquals(new Counts(0, 3, 0, 0), PlayerStats.of(TRAVELLER));
    }

    @Test
    void aGateBuiltByNoPlayerCountsForNobody()
    {
        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), null));

        assertEquals(Counts.NONE, PlayerStats.of(TRAVELLER));
    }

    @Test
    void aRingTripCountsAsARingTripForTheTraveller()
    {
        listener.onRingTrip(new RingTravelEvent(null, player, null, null));
        listener.onRingTrip(new RingTravelEvent(null, player, null, null));

        assertEquals(new Counts(0, 0, 2, 0), PlayerStats.of(TRAVELLER));
    }

    @Test
    void aCancelledRingTripCountsNothing()
    {
        final RingTravelEvent refused = new RingTravelEvent(null, player, null, null);
        refused.setCancelled(true);

        listener.onRingTrip(refused);

        assertEquals(Counts.NONE, PlayerStats.of(TRAVELLER), "a player taken out of the trip stayed where they were");
    }
}
