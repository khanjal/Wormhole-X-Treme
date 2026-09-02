package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.events.StargatePlayerTravelEvent;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Letting another plugin watch, and stop, a player travelling through a gate.
 *
 * <p>The event fires once everything this plugin checks has already passed and before
 * anything has moved, so a listener joins the decision rather than reacting to it.
 *
 * <p>Cancelling is the part worth testing hard. Refusing a move means cancelling it, and a
 * cancelled move returns the player to where the move started — so refusing someone who is
 * already standing in the portal returns them into the portal, where their next move is
 * refused too, and the one after that. They cannot leave and the server eventually drops
 * them. That exact shape of mistake, in the check that holds players out of the exit end of
 * a wormhole, did precisely that.
 */
public class PlayerTravelEventTest
{
    private final List<Event> raised = new ArrayList<Event>();
    private World world;
    private Player player;
    private Stargate origin;
    private Stargate destination;

    private static final int BX = 10, BY = 64, BZ = 20;

    @BeforeEach
    public void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field pf = WormholeXTreme.class.getDeclaredField("thisPlugin");
        pf.setAccessible(true);
        pf.set(null, plugin);

        final org.bukkit.scheduler.BukkitScheduler scheduler =
            mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(1);
        final java.lang.reflect.Field sf = WormholeXTreme.class.getDeclaredField("scheduler");
        sf.setAccessible(true);
        sf.set(null, scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(portal);

        destination = new Stargate();
        destination.setGateName("far-end");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.NORTH);
        destination.setGatePlayerTeleportLocation(new Location(world, 500, 70, 500));

        origin = new Stargate();
        origin.setGateName("near-end");
        origin.setGateWorld(world);
        origin.setGateFacing(BlockFace.NORTH);
        origin.setGateActive(true);
        origin.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        origin.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ - 1.5));
        final java.lang.reflect.Field tf = Stargate.class.getDeclaredField("gateTarget");
        tf.setAccessible(true);
        tf.set(origin, destination);
        StargateManager.addBlockIndex(portal, origin);
        StargateManager.registerStargate(origin);

        player = mock(Player.class);
        when(player.getName()).thenReturn("traveller");
        when(player.isOp()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));

        GateEvents.setDispatcherForTest(raised::add);
    }

    @AfterEach
    public void tearDown()
    {
        GateEvents.setDispatcherForTest(null);
        StargateManager.removeStargate(origin);
        origin.setGateActive(false);
        GateSpatialIndex.clear();
    }

    /** Walking in from the block outside the portal. */
    private PlayerMoveEvent walkIn()
    {
        final PlayerMoveEvent event = new PlayerMoveEvent(player,
            new Location(world, BX + 0.5, BY, BZ - 1.5),
            new Location(world, BX + 0.5, BY, BZ + 0.5));
        new WormholeXTremePlayerListener().onPlayerMove(event);
        return event;
    }

    /**
     * A step that starts on the portal block and crosses into the next one.
     *
     * <p>It has to cross a block boundary. The move handler drops anything that does not,
     * so a shuffle within one block never reaches the travel event at all and would make
     * this whole case prove nothing.
     */
    private PlayerMoveEvent moveWhileInside()
    {
        final PlayerMoveEvent event = new PlayerMoveEvent(player,
            new Location(world, BX + 0.5, BY, BZ + 0.5),
            new Location(world, BX + 1.5, BY, BZ + 0.5));
        new WormholeXTremePlayerListener().onPlayerMove(event);
        return event;
    }

    private StargatePlayerTravelEvent theTravelEvent()
    {
        for (final Event e : raised)
        {
            if (e instanceof StargatePlayerTravelEvent)
            {
                return (StargatePlayerTravelEvent) e;
            }
        }
        return null;
    }

    @Test
    public void travellingAnnouncesWhoIsGoingWhere()
    {
        walkIn();

        final StargatePlayerTravelEvent event = theTravelEvent();
        assertNotNull(event, "entering an open gate should announce the trip");
        assertSame(player, event.getPlayer());
        assertSame(origin, event.getStargate(), "the gate being entered");
        assertSame(destination, event.getDestination(), "where it leads");
        assertNotNull(event.getArrival(), "listeners should be told where the player lands");
    }

    @Test
    public void anUncancelledTripGoesAhead()
    {
        walkIn();

        verify(player).teleport(any(Location.class));
    }

    @Test
    public void cancellingStopsThePlayerBeingMoved()
    {
        GateEvents.setDispatcherForTest(e ->
        {
            raised.add(e);
            if (e instanceof StargatePlayerTravelEvent)
            {
                ((StargatePlayerTravelEvent) e).setCancelled(true);
            }
        });

        walkIn();

        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    public void cancellingSomeoneWalkingInHoldsThemOutside()
    {
        GateEvents.setDispatcherForTest(e ->
        {
            raised.add(e);
            if (e instanceof StargatePlayerTravelEvent)
            {
                ((StargatePlayerTravelEvent) e).setCancelled(true);
            }
        });

        final PlayerMoveEvent event = walkIn();

        assertTrue(event.isCancelled(),
            "the move started outside the portal, so cancelling returns them there");
    }

    @Test
    public void cancellingSomeoneAlreadyInThePortalDoesNotTrapThem()
    {
        // The one that matters. Their move started on the portal block, so cancelling it
        // would put them back on the portal block, and every move after it too. They stop
        // travelling; they do not stop moving.
        GateEvents.setDispatcherForTest(e ->
        {
            raised.add(e);
            if (e instanceof StargatePlayerTravelEvent)
            {
                ((StargatePlayerTravelEvent) e).setCancelled(true);
            }
        });

        for (int attempt = 0; attempt < 5; attempt++)
        {
            assertFalse(moveWhileInside().isCancelled(),
                "attempt " + attempt + ": a cancelled trip must not become a cancelled life");
        }
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    public void aCancelledTripCostsThePlayerNothing()
    {
        // The event used to fire after the use cooldown had been spent and the fare taken,
        // so cancelling left the player poorer, on cooldown, and exactly where they were.
        // That defeats the point of a cancellable event: a combat tag or a jail would have
        // charged people for journeys it then refused them.
        //
        // The cooldown is the observable half here, since it is this plugin's own state
        // rather than an economy provider that is not installed in a test.
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.loadDefaults();
        com.wormhole_xtreme.wormhole.config.ConfigManager.setUseCooldownEnabled(true);
        com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.removePlayerUseCooldown(player);
        GateEvents.setDispatcherForTest(e ->
        {
            raised.add(e);
            if (e instanceof StargatePlayerTravelEvent)
            {
                ((StargatePlayerTravelEvent) e).setCancelled(true);
            }
        });
        try
        {
            walkIn();

            assertFalse(
                com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.isPlayerUseCooldown(player),
                "a refused trip must not spend the cooldown for a trip that never happened");
        }
        finally
        {
            com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.removePlayerUseCooldown(player);
            com.wormhole_xtreme.wormhole.config.ConfigManager.setUseCooldownEnabled(false);
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.clear();
        }
    }

    @Test
    public void anAllowedTripStillSpendsTheCooldown()
    {
        // The control: deferring the cooldown past the event must not have lost it.
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.loadDefaults();
        com.wormhole_xtreme.wormhole.config.ConfigManager.setUseCooldownEnabled(true);
        com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.removePlayerUseCooldown(player);
        try
        {
            walkIn();

            assertTrue(
                com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.isPlayerUseCooldown(player),
                "a trip that actually happened should still put the player on cooldown");
        }
        finally
        {
            com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.removePlayerUseCooldown(player);
            com.wormhole_xtreme.wormhole.config.ConfigManager.setUseCooldownEnabled(false);
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.clear();
        }
    }

    @Test
    public void aListenerThatThrowsDoesNotStrandTheTraveller()
    {
        // Another plugin's listener is code this one does not control. A failure there is
        // not a decision to refuse travel, and least of all halfway into a wormhole.
        GateEvents.setDispatcherForTest(e ->
        {
            throw new IllegalStateException("listener blew up");
        });

        assertDoesNotThrow(() -> walkIn());
        verify(player).teleport(any(Location.class));
    }
}
