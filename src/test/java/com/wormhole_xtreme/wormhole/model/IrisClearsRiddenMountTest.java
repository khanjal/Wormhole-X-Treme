package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.Paper1204Riding;
import com.wormhole_xtreme.wormhole.PetTestSupport;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.window.StandIns;

/**
 * A closing iris moves a ridden horse clear, rider and all, on Paper 1.20.4.
 *
 * <p>The iris shove was a plain teleport, which 1.20.4 refuses for anything with a passenger:
 * the horse and its rider stayed where the iris blocks were about to go (#506).
 */
class IrisClearsRiddenMountTest
{
    private static final int BX = 10;
    private static final int BY = 64;
    private static final int BZ = 20;

    private World world;
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(call -> { call.getArgument(1, Runnable.class).run(); return 1; });
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        gate = new Stargate();
        gate.setGateName("IrisGate");
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        gate.getGatePortalBlocks().add(new Location(world, BX, BY + 1, BZ));
        // No world on the exit: the safe-location search hands it straight back.
        gate.setGatePlayerTeleportLocation(new Location(null, BX + 0.5, BY, BZ + 3.5));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    @Test
    void aRiddenHorseInTheOpeningIsMovedClearWithItsRiderStillOn()
    {
        final Horse horse = mock(Horse.class);
        when(horse.getUniqueId()).thenReturn(UUID.randomUUID());
        when(horse.isValid()).thenReturn(true);
        final Player rider = mock(Player.class);
        when(rider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(rider.isValid()).thenReturn(true);
        // Sitting in the opening too; where they are decides whether the horse will seat them.
        PetTestSupport.standsWhereTeleported(rider, new Location(world, BX + 0.5, BY + 1, BZ + 0.5));
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            horse, new Location(world, BX + 0.5, BY, BZ + 0.5), rider);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(horse, rider));

        StargateBlockSetup.clearIrisPath(gate);

        assertEquals(BZ + 3.5, stack.at().getZ(), 0.001, "the horse must be moved out of the opening");
        assertEquals(BZ + 3.5, rider.getLocation().getZ(), 0.001, "its rider goes with it");
        assertTrue(stack.carries(rider), "and is back in the saddle");
    }

    /**
     * A view's stand-in (#296) standing in the opening is left where it is, while a real zombie
     * beside it is moved clear: it is a picture of a creature elsewhere, and cannot suffocate.
     */
    @Test
    void aStandInInTheOpeningIsLeftWhereARealZombieIsMoved() throws ReflectiveOperationException
    {
        final Zombie real = zombieAt(BY);
        final Zombie standIn = zombieAt(BY + 1);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(real, standIn));
        final Method track = StandIns.class.getDeclaredMethod("track", Entity.class);
        final Method untrack = StandIns.class.getDeclaredMethod("untrack", Entity.class);
        track.setAccessible(true);
        untrack.setAccessible(true);
        track.invoke(null, standIn);
        try
        {
            StargateBlockSetup.clearIrisPath(gate);
        }
        finally
        {
            untrack.invoke(null, standIn);
        }

        assertTrue(teleported(real), "the real zombie is moved out of the iris's way");
        assertFalse(teleported(standIn), "the stand-in is not");
    }

    private Zombie zombieAt(final int y)
    {
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getLocation()).thenReturn(new Location(world, BX + 0.5, y, BZ + 0.5));
        when(zombie.teleport(any(Location.class))).thenReturn(true);
        return zombie;
    }

    private static boolean teleported(final Entity entity)
    {
        return mockingDetails(entity).getInvocations().stream()
            .anyMatch(call -> call.getMethod().getName().equals("teleport"));
    }
}
