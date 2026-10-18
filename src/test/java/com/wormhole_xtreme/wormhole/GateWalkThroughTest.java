package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;

/**
 * What walking into an open gate does to the traveller afterwards, through the move handler.
 *
 * <p>The other walk-through tests pin the refusals and the fare. These pin the three effects of a
 * trip that nothing else did: a step inside one block is not a move, the gate arrived at is
 * marked so the traveller cannot walk straight back, and the follow-up tick that settles them
 * splashes them out of the event horizon.
 */
class GateWalkThroughTest
{
    private static final int BX = 10, BY = 64, BZ = 20;

    private World world;
    private Player player;
    private Stargate origin;
    private Stargate destination;
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
        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getX()).thenReturn(Integer.valueOf(BX));
        when(portal.getY()).thenReturn(Integer.valueOf(BY));
        when(portal.getZ()).thenReturn(Integer.valueOf(BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(portal);

        destination = new Stargate();
        destination.setGateName("Abydos");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.NORTH);
        destination.setGatePlayerTeleportLocation(new Location(world, 500, 70, 500));

        origin = new Stargate();
        origin.setGateName("Earth");
        origin.setGateWorld(world);
        origin.setGateFacing(BlockFace.NORTH);
        origin.setGateActive(true);
        origin.setGatePortalOpen(true);
        origin.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        origin.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ - 1.5));
        StargateTestSupport.target(origin, destination);
        StargateManager.addBlockIndex(portal, origin);
        StargateManager.registerStargate(origin);

        player = mock(Player.class);
        when(player.getName()).thenReturn("traveller");
        when(player.isOp()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000cd"));
        when(player.teleport(any(Location.class))).thenReturn(true);

        GateEvents.setDispatcherForTest(e -> { });
        ConfigTestSupport.loadDefaults();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateRestrictions.removePlayerRecentArrival(player);
        GateEvents.setDispatcherForTest(null);
        StargateManager.removeStargate(origin);
        origin.setGateActive(false);
        GateSpatialIndex.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private PlayerMoveEvent walk(final Location from, final Location to)
    {
        final PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        new WormholeXTremePlayerListener().onPlayerMove(event);
        return event;
    }

    private PlayerMoveEvent walkIn()
    {
        return walk(new Location(world, BX + 0.5, BY, BZ - 1.5), new Location(world, BX + 0.5, BY, BZ + 0.5));
    }

    /** Shuffling about inside the portal's own block crosses nothing, so it takes nobody anywhere. */
    @Test
    void aStepWithinOneBlockOfAnOpenGateDoesNotTakeTheTraveller()
    {
        final PlayerMoveEvent within = walk(new Location(world, BX + 0.2, BY, BZ + 0.2),
            new Location(world, BX + 0.8, BY, BZ + 0.8));

        verify(player, never()).teleport(any(Location.class));
        assertFalse(within.isCancelled(), "a step that crosses no block is left alone");

        walkIn();

        verify(player).teleport(any(Location.class));
    }

    /**
     * Without the marker the traveller steps back into the gate they arrived at, and is sent
     * straight home again.
     */
    @Test
    void theGateArrivedAtIsMarkedSoTheTravellerCannotWalkStraightBack()
    {
        assertFalse(StargateRestrictions.isPlayerRecentArrivalFrom(player, destination),
            "nothing marked before the trip");

        walkIn();

        assertTrue(StargateRestrictions.isPlayerRecentArrivalFrom(player, destination),
            "the far gate is the one they must not re-enter at once");
    }

    /** A moment later, once the client has been put where it is going, they surface out of the water. */
    @Test
    void theTickAfterArrivalSplashesTheTraveller()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            walkIn();

            final ArgumentCaptor<Runnable> settle = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskLater(any(Plugin.class), settle.capture(), eq(1L));
            verify(player, never()).getEyeLocation();

            settle.getValue().run();

            verify(player).getEyeLocation();
        }
    }

    /**
     * A turned-away traveller is told once while they keep walking, and told again if they come
     * back after leaving: quitting forgets what they were last told.
     */
    @Test
    void aRefusalIsSaidAgainToSomebodyWhoLeftAndCameBack()
    {
        StargateRestrictions.addPlayerRecentArrival(player, origin);

        walkIn();
        walkIn();
        verify(player, times(1)).sendMessage(anyString());

        final PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        new WormholeXTremePlayerListener().onPlayerQuit(quit);
        StargateRestrictions.addPlayerRecentArrival(player, origin);
        walkIn();

        verify(player, times(2)).sendMessage(anyString());
    }
}
