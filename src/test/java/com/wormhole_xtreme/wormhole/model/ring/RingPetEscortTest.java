package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A ring brings a player's pets that were following from outside it.
 *
 * <p>A pet standing inside the ring was already carried as cargo. One trotting a few blocks
 * behind was left at the near end, the one case a ring handled differently from a gate.
 */
class RingPetEscortTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
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
    void aPetFollowingFromOutsideTheRingArrivesWithItsOwner()
    {
        final World world = mock(World.class);
        final Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getName()).thenReturn("traveller");
        when(owner.getLocation()).thenReturn(new Location(world, 0.5, 64.0, 0.5));
        final Wolf wolf = mock(Wolf.class);
        when(wolf.isTamed()).thenReturn(true);
        when(wolf.getOwner()).thenReturn(owner);
        when(wolf.getUniqueId()).thenReturn(UUID.randomUUID());
        when(wolf.teleport(any(Location.class))).thenReturn(true);
        when(owner.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(wolf));
        final Ring far = mock(Ring.class);
        when(far.getAnchorX()).thenReturn(200);
        when(far.getAnchorZ()).thenReturn(40);
        when(far.stackBase()).thenReturn(64);
        when(far.getName()).thenReturn("far");

        new BukkitRingWorld(world, null).deliver(new BukkitRingPassenger(owner), far);

        final ArgumentCaptor<Location> landed = ArgumentCaptor.forClass(Location.class);
        verify(wolf).teleport(landed.capture());
        assertEquals(200.5, landed.getValue().getX(), 0.001, "at the far ring's centre");
        assertEquals(40.5, landed.getValue().getZ(), 0.001);
    }
}
