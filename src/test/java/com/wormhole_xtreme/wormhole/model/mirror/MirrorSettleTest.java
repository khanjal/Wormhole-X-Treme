package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The window a mirror stays shut for somebody it has just carried.
 *
 * <p>The window itself is tested through {@link MirrorSettle#within}, which takes two plain
 * longs, so both sides of the edge can be pinned without a clock or a sleeping test. What a
 * settling mirror actually does with a click is {@code MirrorInteractionTest}'s subject.
 */
class MirrorSettleTest
{
    @AfterEach
    void tearDown()
    {
        MirrorSettle.clear();
    }

    private Player player()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    /** An arrival this instant is settling. */
    @Test
    void anArrivalIsSettlingTheMomentItHappens()
    {
        assertTrue(MirrorSettle.within(1_000L, 1_000L));
    }

    /**
     * A moment short of the window is still settling, and the window itself is not.
     *
     * <p>The edge matters more than it looks: a held right-click repeats several times a
     * second, so a window that expired early would reopen the door while the button was still
     * down and the bounce would come back for anybody who clicked rather than tapped.
     */
    @Test
    void theEdgeOfTheWindowIsWhereSettlingStops()
    {
        final long arrived = 5_000_000_000L;

        assertTrue(MirrorSettle.within(arrived, (arrived + MirrorSettle.SETTLE_NANOS) - 1),
            "a nanosecond inside the window is still settling");
        assertFalse(MirrorSettle.within(arrived, arrived + MirrorSettle.SETTLE_NANOS),
            "the window is over when it is over, or a mirror never reopens");
    }

    /**
     * The clock may be negative, so only differences mean anything.
     *
     * <p>{@link System#nanoTime} is documented as having an arbitrary origin and is negative on
     * some JVMs. Comparing the two values directly rather than their difference would read a
     * fresh arrival as long expired on exactly those machines, which is a bug nobody would
     * reproduce locally.
     */
    @Test
    void aNegativeClockStillMeasuresTheWindow()
    {
        final long arrived = -9_000_000_000L;

        assertTrue(MirrorSettle.within(arrived, arrived + 1));
        assertFalse(MirrorSettle.within(arrived, arrived + MirrorSettle.SETTLE_NANOS));
    }

    /** Somebody who has not arrived through a mirror is not settling. */
    @Test
    void aPlayerWhoHasNotTravelledIsNotSettling()
    {
        assertFalse(MirrorSettle.settling(player()));
    }

    /** Arriving shuts mirrors for that player, and for that player only. */
    @Test
    void anArrivalSettlesOnlyThePlayerWhoTravelled()
    {
        final Player traveller = player();
        final Player bystander = player();

        MirrorSettle.arrived(traveller);

        assertTrue(MirrorSettle.settling(traveller));
        assertFalse(MirrorSettle.settling(bystander),
            "one player's trip must not shut the mirrors somebody else is standing at");
    }

    /**
     * The explanation is offered once per arrival.
     *
     * <p>Asked twice for the same arrival, the second answer is no -- which is what keeps one
     * held button from writing a column of identical lines into chat.
     */
    @Test
    void theExplanationIsOfferedOncePerArrival()
    {
        final Player traveller = player();
        MirrorSettle.arrived(traveller);

        assertTrue(MirrorSettle.shouldExplain(traveller));
        assertFalse(MirrorSettle.shouldExplain(traveller));

        // A second trip is a second arrival, and earns its own explanation.
        MirrorSettle.arrived(traveller);
        assertTrue(MirrorSettle.shouldExplain(traveller));
    }

    /** Nobody is owed an explanation for a trip they never took. */
    @Test
    void thereIsNothingToExplainWithoutAnArrival()
    {
        assertFalse(MirrorSettle.shouldExplain(player()));
    }
}
