package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * When a player is sent the open portals again: on arriving somewhere, and on crossing into a
 * new chunk.
 *
 * <p>The portal is drawn over the client's copy of the chunk, so a redraw that lands before the
 * chunk is held is overwritten by it. That is why an arrival redraws four times across a couple
 * of seconds instead of once, and why crossing a chunk boundary redraws at all.
 */
class PortalPresenceTest
{
    private static final List<Long> ARRIVAL_DELAYS = List.of(1L, 10L, 30L, 60L);

    private World world;
    private Player player;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(1);
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");
        final Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);

        player = mock(Player.class);
        when(player.getName()).thenReturn("traveller");
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000ee"));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
        GateSpatialIndex.clear();
    }

    private Location at(final double x, final double z)
    {
        return new Location(world, x, 64, z);
    }

    private void move(final Location from, final Location to)
    {
        new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(player, from, to));
    }

    /** The delays of every redraw scheduled so far, in the order they were asked for. */
    private List<Long> scheduledDelays()
    {
        final ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        verify(scheduler, org.mockito.Mockito.atLeast(0)).scheduleSyncDelayedTask(any(), any(Runnable.class),
            delay.capture());
        return delay.getAllValues();
    }

    @Test
    void crossingAChunkBoundaryRedrawsAtTheFourArrivalDelays()
    {
        move(at(15.5, 5.5), at(16.5, 5.5));

        assertEquals(ARRIVAL_DELAYS, scheduledDelays(), "once each at 1, 10, 30 and 60 ticks");
    }

    @Test
    void crossingAChunkBoundaryInZRedrawsToo()
    {
        move(at(5.5, 15.5), at(5.5, 16.5));

        assertEquals(ARRIVAL_DELAYS, scheduledDelays());
    }

    @Test
    void crossingBackOverANegativeBoundaryRedrawsToo()
    {
        move(at(-0.5, 5.5), at(0.5, 5.5));

        assertEquals(ARRIVAL_DELAYS, scheduledDelays(), "-1 and 0 are different chunks");
    }

    @Test
    void walkingWithinOneChunkRedrawsNothing()
    {
        move(at(1.5, 5.5), at(2.5, 5.5));

        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    @Test
    void movingBetweenWorldsAtTheSameCoordinatesRedraws()
    {
        final World other = mock(World.class);
        when(other.getName()).thenReturn("other");
        final Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(other.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);

        move(at(1.5, 5.5), new Location(other, 1.5, 64, 5.5));

        assertEquals(ARRIVAL_DELAYS, scheduledDelays());
    }

    @Test
    void eachRedrawAsksTheManagerToRefreshThePlayer()
    {
        move(at(15.5, 5.5), at(16.5, 5.5));
        final ArgumentCaptor<Runnable> redraw = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(4)).scheduleSyncDelayedTask(any(), redraw.capture(), anyLong());

        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            redraw.getAllValues().forEach(Runnable::run);

            manager.verify(() -> StargateManager.refreshPortalVisuals(player), times(4));
        }
    }

    /** At startup, or in a test, there is no scheduler to ask; the first refusal ends the attempt. */
    @Test
    void aSchedulerThatRefusesIsAskedOnceNotFourTimes()
    {
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenThrow(new IllegalStateException("no scheduler yet"));

        move(at(15.5, 5.5), at(16.5, 5.5));

        verify(scheduler, times(1)).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    @Test
    void joiningRedraws()
    {
        final PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new WormholeXTremePlayerListener().onPlayerJoin(event);

        assertEquals(ARRIVAL_DELAYS, scheduledDelays());
    }

    @Test
    void respawningRedraws()
    {
        final PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new WormholeXTremePlayerListener().onPlayerRespawn(event);

        assertEquals(ARRIVAL_DELAYS, scheduledDelays());
    }

    @Test
    void changingWorldRedraws()
    {
        final PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new WormholeXTremePlayerListener().onPlayerChangedWorld(event);

        assertEquals(ARRIVAL_DELAYS, scheduledDelays());
    }

    @Test
    void teleportingRedraws()
    {
        final PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getTo()).thenReturn(at(500.5, 500.5));

        new WormholeXTremePlayerListener().onPlayerTeleport(event);

        assertEquals(ARRIVAL_DELAYS, scheduledDelays());
    }

    @Test
    void aCancelledTeleportRedrawsNothing()
    {
        final PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.isCancelled()).thenReturn(true);
        when(event.getTo()).thenReturn(at(500.5, 500.5));

        new WormholeXTremePlayerListener().onPlayerTeleport(event);

        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }
}
