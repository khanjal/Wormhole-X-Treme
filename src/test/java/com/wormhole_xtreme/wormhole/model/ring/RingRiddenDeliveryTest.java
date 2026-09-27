package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.Paper1204Riding;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A ring carries a ridden horse and its rider together, on a server that will not move a horse
 * with somebody on it.
 *
 * <p>Paper 1.20.4 refuses to teleport an entity with passengers. The ring ignored the answer,
 * so the horse stayed on the pad with its rider while the rider was told "Transport complete"
 * (#506).
 */
class RingRiddenDeliveryTest
{
    private BukkitScheduler scheduler;
    private final World world = mock(World.class);

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(1);
        PluginTestSupport.scheduler(scheduler);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private static Ring farRing()
    {
        final Ring far = mock(Ring.class);
        when(far.getAnchorX()).thenReturn(200);
        when(far.getAnchorZ()).thenReturn(40);
        when(far.stackBase()).thenReturn(64);
        when(far.getName()).thenReturn("far");
        return far;
    }

    private static Player rider()
    {
        final Player rider = mock(Player.class);
        when(rider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(rider.isValid()).thenReturn(true);
        when(rider.teleport(any(Location.class))).thenReturn(true);
        return rider;
    }

    private static Horse horse()
    {
        final Horse horse = mock(Horse.class);
        when(horse.getUniqueId()).thenReturn(UUID.randomUUID());
        when(horse.isValid()).thenReturn(true);
        return horse;
    }

    /** Runs whatever the delivery booked, as though its tick had come. */
    private void runScheduled()
    {
        final ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, org.mockito.Mockito.atLeast(0)).scheduleSyncDelayedTask(any(), tasks.capture(), anyLong());
        for (final Runnable task : tasks.getAllValues())
        {
            task.run();
        }
    }

    @Test
    void aHorseThatWillNotMoveWithItsRiderAboardStillArrivesWithThemSeated()
    {
        final Horse horse = horse();
        final Player rider = rider();
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            horse, new Location(world, 0.5, 64, 0.5), rider);

        new BukkitRingWorld(world, null).deliver(new BukkitRingPassenger(horse), farRing());
        runScheduled();

        assertEquals(200.5, stack.at().getX(), 0.001, "the horse must reach the far ring");
        verify(rider, org.mockito.Mockito.atLeastOnce()).teleport(any(Location.class));
        assertTrue(stack.carries(rider), "the rider must be back in the saddle at the far ring");
        // The arrival line goes out through spigot(); the refused case below relies on that.
        verify(rider, org.mockito.Mockito.atLeastOnce()).spigot();
    }

    /**
     * A horse that will not move keeps its rider on the pad, and nobody is told they arrived.
     *
     * <p>Another plugin cancelling the teleport, say. Sending the rider on without the horse
     * would split them across the two ends.
     */
    @Test
    void aHorseThatWillNotMoveKeepsItsRiderAtThisEnd()
    {
        final Horse horse = horse();
        final Player rider = rider();
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            horse, new Location(world, 0.5, 64, 0.5), rider);
        when(horse.teleport(any(Location.class))).thenReturn(false);

        new BukkitRingWorld(world, null).deliver(new BukkitRingPassenger(horse), farRing());
        runScheduled();

        assertEquals(0.5, stack.at().getX(), 0.001);
        assertTrue(stack.carries(rider), "the rider must still be on the horse");
        verify(rider, never()).teleport(any(Location.class));
        verify(rider, never()).spigot();
    }
}
