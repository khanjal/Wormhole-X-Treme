package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.window.StandIns;

/**
 * Rings carry real creatures, not a view's stand-ins (#296).
 *
 * <p>A stand-in standing on a ring pad behind a mirror's wall would otherwise be carried to the far
 * ring, arriving there as a real, inert mob that nobody holds and everybody can see.
 */
class RingLeavesStandInsTest
{
    @Test
    void aStandInOnTheRingIsNotAPassengerThoughARealZombieIs() throws ReflectiveOperationException
    {
        final World world = mock(World.class);
        final Zombie real = zombieAt(world, 1);
        final Zombie standIn = zombieAt(world, 2);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(real, standIn));
        final Method track = StandIns.class.getDeclaredMethod("track", Entity.class);
        final Method untrack = StandIns.class.getDeclaredMethod("untrack", Entity.class);
        track.setAccessible(true);
        untrack.setAccessible(true);
        track.invoke(null, standIn);
        final List<RingPassenger> carried;
        try
        {
            carried = new BukkitRingWorld(world, null).passengersIn(List.of(new int[] { 1, 64, 1 }, new int[] { 2, 64, 2 }));
        }
        finally
        {
            untrack.invoke(null, standIn);
        }

        assertEquals(1, carried.size(), "the real zombie, and not the stand-in beside it");
    }

    private static Zombie zombieAt(final World world, final int at)
    {
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.getLocation()).thenReturn(new Location(world, at + 0.5, 64.0, at + 0.5));
        return zombie;
    }
}
