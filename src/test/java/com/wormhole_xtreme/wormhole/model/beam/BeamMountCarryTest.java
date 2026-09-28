package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
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
 * A beamed mount with a mob still riding it goes with the traveller on Paper 1.20.4.
 *
 * <p>The beam takes the traveller off before moving the mount, which is why beaming worked on
 * 1.20.4 when gates did not. A chicken sharing the saddle stayed on, though, and 1.20.4 will
 * not teleport anything with a passenger: the horse stayed behind and the traveller was put
 * back on it at the origin (#506).
 */
class BeamMountCarryTest
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

    @Test
    void aMountWithAMobStillAboardIsCarriedAndEveryoneIsReseated()
    {
        final Horse horse = mock(Horse.class);
        when(horse.getUniqueId()).thenReturn(UUID.randomUUID());
        when(horse.isValid()).thenReturn(true);
        final Player traveller = mock(Player.class);
        when(traveller.getName()).thenReturn("traveller");
        when(traveller.isValid()).thenReturn(true);
        final Chicken chicken = mock(Chicken.class);
        when(chicken.getUniqueId()).thenReturn(UUID.randomUUID());
        when(chicken.isValid()).thenReturn(true);
        when(chicken.teleport(any(Location.class))).thenReturn(true);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            horse, new Location(world, 0.5, 64, 0.5), traveller, chicken);
        doAnswer(call -> horse.removePassenger(traveller)).when(traveller).leaveVehicle();
        final Location destination = new Location(world, 300.5, 70, 300.5);

        final BeamMount mount = BeamMount.capture(traveller);
        mount.hold(traveller);
        mount.carry(traveller, destination);
        final ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeast(1)).scheduleSyncDelayedTask(any(), tasks.capture(), anyLong());
        tasks.getValue().run();

        assertEquals(300.5, stack.at().getX(), 0.001, "the horse must be beamed, not left at the origin");
        assertTrue(stack.carries(traveller), "the traveller rides on at the destination");
        assertTrue(stack.carries(chicken), "and so does the chicken");
    }
}
