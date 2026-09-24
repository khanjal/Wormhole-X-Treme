package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.ArgumentCaptor;

/**
 * Drives the pet escort from a transport's test: pets follow a moment after the trip, on a task
 * a mocked scheduler never runs by itself.
 */
public final class PetTestSupport
{
    private PetTestSupport() {}

    /**
     * Runs the pet escorts a transport scheduled, and nothing else it scheduled.
     *
     * @param scheduler
     *            the mocked scheduler the transport used
     */
    public static void runEscorts(final BukkitScheduler scheduler)
    {
        final ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeast(0)).scheduleSyncDelayedTask(any(), tasks.capture(), anyLong());
        for (final Runnable task : tasks.getAllValues())
        {
            if (task.getClass().getName().contains("PetEscort"))
            {
                task.run();
            }
        }
    }

    /**
     * Makes a mocked player stand wherever they were last teleported to.
     *
     * @param start
     *            where they stand before any trip
     */
    public static void standsWhereTeleported(final Player traveller, final Location start)
    {
        final Location[] at = { start };
        when(traveller.isOnline()).thenReturn(true);
        when(traveller.getLocation()).thenAnswer(call -> at[0]);
        when(traveller.teleport(any(Location.class))).thenAnswer(call ->
        {
            at[0] = call.getArgument(0);
            return true;
        });
    }
}
