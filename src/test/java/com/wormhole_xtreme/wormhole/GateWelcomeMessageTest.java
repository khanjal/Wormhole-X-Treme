package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
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
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

/**
 * A player coming out of a gate is told where they are, once, and only if the server asked for it.
 *
 * <p>Settings.txt-era builds said so on every arrival, and the message went with Settings.txt
 * (#485). It is back behind {@code show-gate-welcome-message}, off by default so a server that
 * has grown used to silence is not suddenly chatty after an upgrade. A pet brought along is
 * not a second arrival to announce.
 */
class GateWelcomeMessageTest
{
    private static final int BX = 10, BY = 64, BZ = 20;

    private World world;
    private Player player;
    private Stargate origin;
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

        final Stargate destination = new Stargate();
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
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000ab"));
        when(player.teleport(any(Location.class))).thenReturn(true);

        GateEvents.setDispatcherForTest(e -> { });
        ConfigTestSupport.loadDefaults();
    }

    @AfterEach
    void tearDown()
    {
        GateEvents.setDispatcherForTest(null);
        StargateManager.removeStargate(origin);
        origin.setGateActive(false);
        GateSpatialIndex.clear();
        ConfigTestSupport.clear();
    }

    private void walkIn()
    {
        new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(player,
            new Location(world, BX + 0.5, BY, BZ - 1.5),
            new Location(world, BX + 0.5, BY, BZ + 0.5)));
    }

    /** An upgraded server, or a new one, keeps the quiet it had: the setting ships off. */
    @Test
    void withTheSettingAtItsDefaultAnArrivalSaysNothing()
    {
        walkIn();

        verify(player).teleport(any(Location.class));
        verify(player, never()).sendMessage(anyString());
    }

    /** A config.yml from before the setting existed has no line for it, and that reads as off too. */
    @Test
    void withTheSettingMissingAnArrivalSaysNothing()
    {
        ConfigTestSupport.clear();

        walkIn();

        verify(player).teleport(any(Location.class));
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    void withTheSettingOnAnArrivalNamesTheGateArrivedAtOnce()
    {
        ConfigTestSupport.set(ConfigKeys.SHOW_GATE_WELCOME_MESSAGE, true);

        walkIn();

        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(player, times(1)).sendMessage(said.capture());
        assertEquals("§3:: §7Arrived at Abydos", said.getValue(),
            "the far gate's name, not the gate walked into (Earth)");
    }

    /**
     * The wolf that came along is moved too, but it is the player who arrived.
     *
     * <p>The pet is teleported a second later by the escort, a separate move from the owner's,
     * which is the place a second greeting would come from.
     */
    @Test
    void aPetThatComesAlongDoesNotMakeASecondGreeting()
    {
        ConfigTestSupport.set(ConfigKeys.SHOW_GATE_WELCOME_MESSAGE, true);
        final Wolf wolf = PetEscortTest.wolfOf(player);
        when(wolf.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ - 3.5));
        PetTestSupport.standsWhereTeleported(player, new Location(world, BX + 0.5, BY, BZ - 1.5));
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(wolf));

        walkIn();
        PetTestSupport.runEscorts(scheduler);

        verify(wolf).teleport(any(Location.class));
        verify(player, times(1)).sendMessage(contains("Arrived at Abydos"));
        verify(wolf, never()).sendMessage(anyString());
    }
}
