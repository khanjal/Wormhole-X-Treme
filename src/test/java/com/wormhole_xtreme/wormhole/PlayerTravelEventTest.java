package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import org.mockito.MockedStatic;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.events.StargatePlayerTravelEvent;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;

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
class PlayerTravelEventTest
{
    private final List<Event> raised = new ArrayList<>();
    private World world;
    private Player player;
    private Stargate origin;
    private Stargate destination;
    private BukkitScheduler scheduler;

    private static final int BX = 10, BY = 64, BZ = 20;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

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
        destination.setGateName("far-end");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.NORTH);
        destination.setGatePlayerTeleportLocation(new Location(world, 500, 70, 500));

        origin = new Stargate();
        origin.setGateName("near-end");
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
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
        // A real server says whether it moved; an unstubbed mock would say it refused.
        when(player.teleport(any(Location.class))).thenReturn(true);

        GateEvents.setDispatcherForTest(raised::add);
    }

    @AfterEach
    void tearDown()
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
    void travellingAnnouncesWhoIsGoingWhere()
    {
        walkIn();

        final StargatePlayerTravelEvent event = theTravelEvent();
        assertNotNull(event, "entering an open gate should announce the trip");
        assertSame(player, event.getPlayer());
        assertSame(origin, event.getStargate(), "the gate being entered");
        assertSame(destination, event.getDestination(), "where it leads");
        assertNotNull(event.getArrival(), "listeners should be told where the player lands");
    }

    /**
     * A far gate with no arrival point announces no trip and moves nobody.
     *
     * <p>The arrival point was never checked, so the trip was announced with no arrival and
     * the player teleported to null, which Bukkit refuses with an exception.
     */
    @Test
    void aFarGateWithNoArrivalPointSendsNobody()
    {
        destination.setGatePlayerTeleportLocation(null);

        walkIn();

        assertNull(theTravelEvent(), "there is nowhere to announce a trip to");
        verify(player, never()).teleport(ArgumentMatchers.<Location>any());
    }

    /**
     * Stepping into a gate that is still dialling walks you through its empty frame.
     *
     * <p>A gate is active from the moment it is dialled, and travel used to ask nothing more, so a
     * player could walk into the opening while the chevrons were still locking and be sent
     * through before the kawoosh.
     */
    @Test
    void walkingIntoAGateStillDiallingSendsNobodyAndHoldsNobodyBack()
    {
        origin.setGatePortalOpen(false);

        final PlayerMoveEvent event = walkIn();

        verify(player, never()).teleport(ArgumentMatchers.<Location>any());
        assertNull(theTravelEvent(), "there is no wormhole yet to announce a trip through");
        assertFalse(event.isCancelled(), "they walk on through the frame rather than being held");
    }

    @Test
    void anUncancelledTripGoesAhead()
    {
        walkIn();

        verify(player).teleport(any(Location.class));
    }

    @Test
    void aFollowingPetGoesThroughTheGateWithItsOwner()
    {
        final Wolf wolf = PetEscortTest.wolfOf(player);
        when(wolf.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ - 3.5));
        PetTestSupport.standsWhereTeleported(player, new Location(world, BX + 0.5, BY, BZ - 1.5));
        when(player.getNearbyEntities(ArgumentMatchers.anyDouble(),
            ArgumentMatchers.anyDouble(), ArgumentMatchers.anyDouble()))
            .thenReturn(List.of(wolf));

        walkIn();
        verify(wolf, never()).teleport(any(Location.class));
        PetTestSupport.runEscorts(scheduler);

        final ArgumentCaptor<Location> landed = ArgumentCaptor.forClass(Location.class);
        verify(wolf).teleport(landed.capture());
        assertEquals(500.0, landed.getValue().getX(), 1.0, "at the far gate, not left at the near one");
    }

    @Test
    void aCancelledTripLeavesThePetsWhereTheyAre()
    {
        final Wolf wolf = PetEscortTest.wolfOf(player);
        when(player.getNearbyEntities(ArgumentMatchers.anyDouble(),
            ArgumentMatchers.anyDouble(), ArgumentMatchers.anyDouble()))
            .thenReturn(List.of(wolf));
        GateEvents.setDispatcherForTest(event ->
        {
            if (event instanceof Cancellable cancellable)
            {
                cancellable.setCancelled(true);
            }
        });

        walkIn();

        verify(wolf, never()).teleport(any(Location.class));
    }

    @Test
    void cancellingStopsThePlayerBeingMoved()
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
    void cancellingSomeoneWalkingInHoldsThemOutside()
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
    void cancellingSomeoneAlreadyInThePortalDoesNotTrapThem()
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
    void aCancelledTripCostsThePlayerNothing()
    {
        // The event used to fire after the use cooldown had been spent and the fare taken,
        // so cancelling left the player poorer, on cooldown, and exactly where they were.
        // That defeats the point of a cancellable event: a combat tag or a jail would have
        // charged people for journeys it then refused them.
        //
        // The cooldown is the observable half here, since it is this plugin's own state
        // rather than an economy provider that is not installed in a test.
        ConfigTestSupport.loadDefaults();
        ConfigManager.setUseCooldownEnabled(true);
        StargateRestrictions.removePlayerUseCooldown(player);
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
                StargateRestrictions.isPlayerUseCooldown(player),
                "a refused trip must not spend the cooldown for a trip that never happened");
        }
        finally
        {
            StargateRestrictions.removePlayerUseCooldown(player);
            ConfigManager.setUseCooldownEnabled(false);
            ConfigTestSupport.clear();
        }
    }

    @Test
    void anAllowedTripStillSpendsTheCooldown()
    {
        // The control: deferring the cooldown past the event must not have lost it.
        ConfigTestSupport.loadDefaults();
        ConfigManager.setUseCooldownEnabled(true);
        StargateRestrictions.removePlayerUseCooldown(player);
        try
        {
            walkIn();

            assertTrue(
                StargateRestrictions.isPlayerUseCooldown(player),
                "a trip that actually happened should still put the player on cooldown");
        }
        finally
        {
            StargateRestrictions.removePlayerUseCooldown(player);
            ConfigManager.setUseCooldownEnabled(false);
            ConfigTestSupport.clear();
        }
    }

    @Test
    void aListenerThatThrowsDoesNotStrandTheTraveller()
    {
        // Another plugin's listener is code this one does not control. A failure there is
        // not a decision to refuse travel, and least of all halfway into a wormhole.
        GateEvents.setDispatcherForTest(e ->
        {
            throw new IllegalStateException("listener blew up");
        });

        assertDoesNotThrow(this::walkIn);
        verify(player).teleport(any(Location.class));
    }

    /**
     * A cancelled trip is not charged for.
     *
     * <p>Affordability is checked before anything moves, so the traveller is turned away for
     * the right reason and in the right order -- but the money must not leave until the trip
     * is certain. A listener can still stop it, and charging for a journey that never
     * happened is the one outcome nobody can argue is correct. The code says so in a comment;
     * nothing was holding it.
     */
    @Test
    void aCancelledTripTakesNoFare()
    {
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class, CALLS_REAL_METHODS);
             MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            config.when(ConfigManager::isEconomyEnabled).thenReturn(true);
            config.when(ConfigManager::getEconomyUseCost).thenReturn(5.0);
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(any(), anyDouble())).thenReturn(true);

            GateEvents.setDispatcherForTest(event ->
            {
                raised.add(event);
                if (event instanceof Cancellable cancellable)
                {
                    cancellable.setCancelled(true);
                }
            });

            walkIn();

            economy.verify(() -> EconomySupport.charge(any(), anyDouble()), never());
        }
    }

    /** A trip that goes ahead is charged for. */
    @Test
    void aCompletedTripTakesTheFare()
    {
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class, CALLS_REAL_METHODS);
             MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            config.when(ConfigManager::isEconomyEnabled).thenReturn(true);
            config.when(ConfigManager::getEconomyUseCost).thenReturn(5.0);
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(any(), anyDouble())).thenReturn(true);

            walkIn();

            economy.verify(() -> EconomySupport.charge(player, 5.0));
        }
    }

    /**
     * A minecart rider is not charged here: the cart has not moved yet, and may not.
     *
     * <p>The vehicle listener moves the cart and charges once it has gone. Charged here, the
     * rider paid as they rolled in, and on Paper 1.20.4 for a cart that then stayed put.
     */
    @Test
    void aMinecartRiderIsLeftToTheCartsListenerToCharge()
    {
        final Minecart cart = mock(Minecart.class);
        when(player.getVehicle()).thenReturn(cart);
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class, CALLS_REAL_METHODS);
             MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            config.when(ConfigManager::isEconomyEnabled).thenReturn(true);
            config.when(ConfigManager::getEconomyUseCost).thenReturn(5.0);
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(any(), anyDouble())).thenReturn(true);

            walkIn();

            economy.verify(() -> EconomySupport.canAfford(any(), anyDouble()));
            economy.verify(() -> EconomySupport.charge(any(), anyDouble()), never());
        }
    }

    /**
     * A trip the server refuses is not charged for, and costs no cooldown.
     *
     * <p>Another plugin cancelling the teleport leaves the traveller where they are. The
     * answer was ignored, so they paid, were put on cooldown and logged as having gone.
     */
    @Test
    void aTripTheServerRefusesTakesNoFareAndNoCooldown()
    {
        when(player.teleport(any(Location.class))).thenReturn(false);
        StargateRestrictions.removePlayerUseCooldown(player);
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class, CALLS_REAL_METHODS);
             MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            config.when(ConfigManager::isEconomyEnabled).thenReturn(true);
            config.when(ConfigManager::getEconomyUseCost).thenReturn(5.0);
            config.when(ConfigManager::isUseCooldownEnabled).thenReturn(true);
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(any(), anyDouble())).thenReturn(true);

            walkIn();

            economy.verify(() -> EconomySupport.charge(any(), anyDouble()), never());
            assertFalse(StargateRestrictions.isPlayerUseCooldown(player),
                "no cooldown for a trip that did not happen");
        }
        finally
        {
            StargateRestrictions.removePlayerUseCooldown(player);
        }
    }

    /**
     * A closed iris at the far end puts the traveller back where they started.
     *
     * <p>An iris is the one thing a gate owner has to keep somebody out, so walking into a
     * gate whose far end is shut must not put them through it.
     */
    @Test
    void aClosedRemoteIrisSendsTheTravellerBack()
    {
        destination.setGateIrisActive(true);

        walkIn();

        verify(player).teleport(origin.getGatePlayerTeleportLocation());
        verify(player, never()).teleport(destination.getGatePlayerTeleportLocation());
    }

    /**
     * A player a vehicle has just carried through does not also travel on their own.
     *
     * <p>The vehicle listener teleports the cart and its rider together, and the rider is
     * ejected at the far end -- which raises a move event inside the arrival gate. Without
     * this suppression the player would be sent again as a solo traveller, arriving without
     * the vehicle they were riding.
     */
    @Test
    void aRiderJustCarriedThroughDoesNotAlsoTravelAlone() throws Exception
    {
        // Set directly rather than through markPlayerRecentlyTeleportedByVehicle, which
        // schedules its own expiry ten ticks out and so needs a live scheduler.
        final Set<UUID> marked =
            PrivateStatics.of(WormholeXTremeVehicleListener.class, "recentlyTeleportedPlayersByVehicle");
        marked.add(player.getUniqueId());
        try
        {
            walkIn();

            verify(player, never()).teleport(any(Location.class));
        }
        finally
        {
            marked.remove(player.getUniqueId());
        }
    }


    /** A traveller who cannot pay is turned away rather than moved and billed. */
    @Test
    void aTravellerWhoCannotPayDoesNotTravel()
    {
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class, CALLS_REAL_METHODS);
             MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            config.when(ConfigManager::isEconomyEnabled).thenReturn(true);
            config.when(ConfigManager::getEconomyUseCost).thenReturn(5.0);
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(any(), anyDouble())).thenReturn(false);

            walkIn();

            economy.verify(() -> EconomySupport.charge(any(), anyDouble()), never());
            verify(player, never()).teleport(destination.getGatePlayerTeleportLocation());
            assertTrue(raised.isEmpty(), "nobody is asked about a trip that is not affordable");
        }
    }
}
