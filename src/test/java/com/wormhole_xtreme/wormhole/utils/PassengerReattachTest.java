package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Putting a rider back in the vehicle they were teleported out of.
 *
 * <p>Teleporting a vehicle ejects whoever was in it, so the rider is re-seated a couple of
 * ticks later once the client has caught up. The server can refuse that -- the passenger may
 * not have arrived yet -- so it retries on a backoff rather than dropping them.
 *
 * <p>None of it was covered. The whole thing runs inside a scheduled task, so these capture
 * the task the scheduler was handed and run it directly.
 */
class PassengerReattachTest
{
    private BukkitScheduler scheduler;
    private Minecart cart;
    private Player rider;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(1);
        final Field sf = WormholeXTreme.class.getDeclaredField("scheduler");
        sf.setAccessible(true);
        sf.set(null, scheduler);

        cart = mock(Minecart.class);
        when(cart.isValid()).thenReturn(true);
        when(cart.getUniqueId()).thenReturn(UUID.randomUUID());
        when(cart.getPassengers()).thenReturn(Collections.<Entity>emptyList());

        rider = mock(Player.class);
        when(rider.getName()).thenReturn("rider");
        when(rider.isValid()).thenReturn(true);
    }

    /** Runs the task most recently handed to the scheduler. */
    private Runnable lastScheduled()
    {
        final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeast(1)).scheduleSyncDelayedTask(any(), task.capture(), anyLong());
        final Runnable last = task.getValue();
        assertNotNull(last, "something should have been scheduled");
        return last;
    }

    /**
     * The rider goes back in the cart.
     *
     * <p>The teleport has already ejected them, so they do not show up in the vehicle's own
     * passenger list; the caller names them and they are put back explicitly.
     */
    @Test
    void theRiderIsPutBackInTheVehicle()
    {
        when(cart.addPassenger(rider)).thenReturn(true);

        PassengerReattach.schedule(cart, rider, new Vector(1, 0, 0));
        lastScheduled().run();

        verify(cart).addPassenger(rider);
    }

    /** Once everyone is aboard, the vehicle gets its exit speed and its fire put out. */
    @Test
    void aFullVehicleIsGivenItsExitSpeed()
    {
        when(cart.addPassenger(rider)).thenReturn(true);
        final Vector exit = new Vector(2, 0, 0);

        PassengerReattach.schedule(cart, rider, exit);
        lastScheduled().run();

        verify(cart).setVelocity(exit);
        verify(cart).setFireTicks(0);
    }

    /**
     * A refused seating is tried again rather than dropped.
     *
     * <p>The passenger may simply not have arrived yet, which is why this retries at all.
     */
    @Test
    void aRefusedSeatingIsTriedAgain()
    {
        when(cart.addPassenger(rider)).thenReturn(false);

        PassengerReattach.schedule(cart, rider, new Vector(1, 0, 0));
        final Runnable first = lastScheduled();
        first.run();

        // Once to arm the first attempt, once more to try again.
        verify(scheduler, atLeast(2)).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
        verify(cart, never()).setVelocity(any(Vector.class));
    }

    /**
     * It gives up eventually, and says so.
     *
     * <p>Retrying for ever would leave a task rescheduling itself against a vehicle nobody is
     * going to board, for the life of the server.
     */
    @Test
    void itGivesUpAfterEnoughAttemptsAndSaysSo()
    {
        when(cart.addPassenger(rider)).thenReturn(false);

        PassengerReattach.schedule(cart, rider, new Vector(1, 0, 0));
        final Runnable task = lastScheduled();

        // Eleven refusals are still worth retrying.
        for (int i = 0; i < 11; i++)
        {
            task.run();
        }
        verify(WormholeXTreme.getThisPlugin(), never()).prettyLog(
            any(java.util.logging.Level.class), contains("Failed to reattach passengers"));

        // The twelfth is where it stops.
        task.run();
        verify(WormholeXTreme.getThisPlugin()).prettyLog(
            any(java.util.logging.Level.class), contains("Failed to reattach passengers"));
    }

    /** A vehicle that has gone -- broken, despawned -- stops the whole thing. */
    @Test
    void aVehicleThatIsGoneStopsTheAttempt()
    {
        when(cart.isValid()).thenReturn(false);

        PassengerReattach.schedule(cart, rider, new Vector(1, 0, 0));
        lastScheduled().run();

        verify(cart, never()).addPassenger(any(Entity.class));
    }
}
