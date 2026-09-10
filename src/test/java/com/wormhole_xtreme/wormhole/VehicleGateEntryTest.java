package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.events.StargatePlayerTravelEvent;

/**
 * What a rolling minecart entering a gate is allowed to do.
 *
 * <p>The existing vehicle tests cover the far iris and the shape of the dispatch. Sixteen of
 * twenty mutations to the entry path survived the whole suite anyway, including a closed gate
 * that still teleports, a cart that teleports off any block of the gate frame, a rider who
 * rides through a cooldown for free, and the loop-breaker being removed entirely -- which puts
 * a cart in the arrival portal raising move events with nothing to stop it going back.
 *
 * <p>These are the guards, not the arithmetic: {@link WormholeXTremeVehicleListenerTest}
 * covers the offset and velocity helpers on their own.
 */
class VehicleGateEntryTest
{
    private static final int BX = 10, BY = 64, BZ = 20;

    private BukkitScheduler scheduler;
    private World world;
    private Block portal;
    private Stargate src;
    private Stargate dst;
    private Minecart cart;

    @BeforeEach
    void setUp() throws Exception
    {
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(1);
        PrivateStatics.set(WormholeXTreme.class, "scheduler", scheduler);
        PrivateStatics.set(WormholeXTreme.class, "thisPlugin", mock(WormholeXTreme.class));
        GateSpatialIndex.clear();
        clearRecentMarks();

        world = mock(World.class);
        when(world.getName()).thenReturn("w");
        portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getX()).thenReturn(Integer.valueOf(BX));
        when(portal.getY()).thenReturn(Integer.valueOf(BY));
        when(portal.getZ()).thenReturn(Integer.valueOf(BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(BX, BY, BZ)).thenReturn(portal);

        src = new Stargate();
        src.setGateName("src");
        src.setGateActive(true);
        dst = new Stargate();
        dst.setGateName("dst");
        // East, so the exit runs along +X. A north-facing exit would hide a sign error in
        // the arrival yaw: atan2 gives the same answer either way along that axis.
        dst.setGateFacing(BlockFace.EAST);
        // Given a facing of its own, so the arrival taking one is a visible change rather
        // than agreeing with a default of zero.
        dst.setGatePlayerTeleportLocation(new Location(world, 100.5, 70.0, 200.5, 12.0f, 45.0f));
        PrivateStatics.set(Stargate.class, src, "gateTarget", dst);

        StargateManager.addBlockIndex(portal, src);
        src.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));

        cart = mock(Minecart.class);
        when(cart.getUniqueId()).thenReturn(UUID.randomUUID());
        when(cart.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(cart.getVelocity()).thenReturn(new Vector(1.0, 0.0, 0.0));
        when(cart.getType()).thenReturn(EntityType.MINECART);
        when(cart.isValid()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateManager.removeBlockIndex(portal);
        GateSpatialIndex.clear();
        GateEvents.setDispatcherForTest(null);
        clearRecentMarks();
        PrivateStatics.set(WormholeXTreme.class, "thisPlugin", null);
    }

    /**
     * Empties both recently-teleported sets.
     *
     * <p>They are static and their entries are removed by a scheduled task that never runs
     * under a mock scheduler, so one test would otherwise decide what the next one sees.
     */
    private static void clearRecentMarks() throws Exception
    {
        for (final String name : new String[] { "recentlyTeleported", "recentlyTeleportedPlayersByVehicle" })
        {
            final Set<UUID> marked = PrivateStatics.of(WormholeXTremeVehicleListener.class, name);
            marked.clear();
        }
    }

    /** One move that ends inside the gate's portal block. */
    private void rollIn()
    {
        new WormholeXTremeVehicleListener().onVehicleMove(new VehicleMoveEvent(cart,
            new Location(world, BX + 0.5, BY, BZ - 0.5),
            new Location(world, BX + 0.5, BY, BZ + 0.5)));
    }

    private Player putARiderAboard()
    {
        final Player rider = mock(Player.class);
        when(rider.getName()).thenReturn("rider");
        when(rider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(rider.isValid()).thenReturn(true);
        when(cart.getPassengers()).thenReturn(Collections.<Entity>singletonList(rider));
        return rider;
    }

    /** Where the cart was last put. */
    private Location whereItLanded()
    {
        final ArgumentCaptor<Location> sent = ArgumentCaptor.forClass(Location.class);
        verify(cart, atLeastOnce()).teleport(sent.capture());
        return sent.getValue();
    }

    /** A gate that is not open carries nobody, however far into it they roll. */
    @Test
    void aGateThatIsNotOpenCarriesNoVehicle()
    {
        src.setGateActive(false);

        rollIn();

        verify(cart, never()).teleport(any(Location.class));
    }

    /**
     * Nor does a gate that is open onto nothing.
     *
     * <p>A gate lit and walked away from, or the far end of somebody else's wormhole. There
     * is nowhere to send the cart, and everything past this guard reads the target.
     */
    @Test
    void anOpenGateWithNoTargetCarriesNoVehicle() throws Exception
    {
        PrivateStatics.set(Stargate.class, src, "gateTarget", null);

        rollIn();

        verify(cart, never()).teleport(any(Location.class));
    }

    /**
     * Only the portal carries a cart, not the rest of the gate.
     *
     * <p>The block index answers for every block of the structure, the frame and the sign
     * included. Without the portal check a cart on a rail laid along the gate's own frame
     * would be sent through it.
     */
    @Test
    void aBlockOfTheGateThatIsNotThePortalCarriesNoVehicle()
    {
        src.getGatePortalBlocks().clear();

        rollIn();

        verify(cart, never()).teleport(any(Location.class));
    }

    /**
     * A cart that has just arrived is not sent straight back out.
     *
     * <p>It lands in the far portal and raises a burst of move events from there. The mark is
     * the only thing standing between that and a cart shuttling between two gates forever.
     */
    @Test
    void anArrivingVehicleIsMarkedSoTheArrivalBurstIsIgnored()
    {
        rollIn();
        assertTrue(WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(cart.getUniqueId()),
            "the cart is marked as having just travelled");

        rollIn();

        verify(cart, times(1)).teleport(any(Location.class));
    }

    /**
     * And so is the rider, who would otherwise be teleported out of the cart.
     *
     * <p>{@code veh.teleport()} ejects the passengers on the source side, which the player
     * listener sees as a player moving in a gate. Unmarked, it sends them through on foot and
     * the cart arrives empty.
     */
    @Test
    void aRiderIsMarkedSoThePlayerListenerLeavesThemInTheCart()
    {
        final Player rider = putARiderAboard();

        rollIn();

        assertTrue(WormholeXTremeVehicleListener.isPlayerRecentlyTeleportedByVehicle(rider.getUniqueId()),
            "the rider travels with the cart, not beside it");
    }

    /**
     * A listener that cancels the travel event stops the vehicle too.
     *
     * <p>The vehicle is never announced, only the people in it -- so a plugin protecting a
     * region sees one event per rider and has to be able to stop the trip with it.
     */
    @Test
    void aCancelledTravelEventStopsTheWholeVehicle()
    {
        putARiderAboard();
        GateEvents.setDispatcherForTest(e -> ((StargatePlayerTravelEvent) e).setCancelled(true));

        rollIn();

        verify(cart, never()).teleport(any(Location.class));
    }

    /**
     * Every rider is asked about, not just whoever is sitting at the front.
     *
     * <p>Only the first passenger is asked about permission and cooldown -- a vehicle is
     * worked by whoever is at the front of it. The travel event is not the same question: it
     * is asked of each person being moved, and any one of them can be the reason it stays put.
     */
    @Test
    void aListenerStoppingAnyRiderStopsTheWholeVehicle()
    {
        final Player helmsman = mock(Player.class);
        when(helmsman.getUniqueId()).thenReturn(UUID.randomUUID());
        final Player passenger = mock(Player.class);
        when(passenger.getUniqueId()).thenReturn(UUID.randomUUID());
        when(cart.getPassengers()).thenReturn(List.<Entity>of(helmsman, passenger));
        GateEvents.setDispatcherForTest(e -> {
            final StargatePlayerTravelEvent travel = (StargatePlayerTravelEvent) e;
            if (travel.getPlayer() == passenger)
            {
                travel.setCancelled(true);
            }
        });

        rollIn();

        verify(cart, never()).teleport(any(Location.class));
    }

    /**
     * Riding through a gate costs the same cooldown as walking through it.
     *
     * <p>Both marks are applied after the travel event, not before, so this also pins that
     * they are applied at all: they are the only part of the trip that outlives it.
     */
    @Test
    void aRiderWhoTravelsOwesTheUseCooldownAndIsMarkedAsArriving()
    {
        final Player rider = putARiderAboard();
        try (MockedStatic<ConfigManager> cfg = mockStatic(ConfigManager.class);
             MockedStatic<StargateRestrictions> rules = mockStatic(StargateRestrictions.class))
        {
            cfg.when(ConfigManager::isUseCooldownEnabled).thenReturn(Boolean.TRUE);
            // A mocked config answers 0 here, which would shut the gate behind the cart and
            // is nothing to do with cooldowns.
            cfg.when(ConfigManager::getTimeoutShutdown).thenReturn(Integer.valueOf(30));

            rollIn();

            rules.verify(() -> StargateRestrictions.addPlayerUseCooldown(rider));
            rules.verify(() -> StargateRestrictions.addPlayerRecentArrival(rider, dst));
        }
    }

    /**
     * A rider whose trip was stopped owes nothing.
     *
     * <p>The reason the marks are deferred rather than charged as each rider is admitted: a
     * cooldown spent on a journey that never happened is not one the rider can argue with.
     */
    @Test
    void aRiderStoppedByAListenerIsNotChargedForTheTrip()
    {
        final Player rider = putARiderAboard();
        GateEvents.setDispatcherForTest(e -> ((StargatePlayerTravelEvent) e).setCancelled(true));
        try (MockedStatic<ConfigManager> cfg = mockStatic(ConfigManager.class);
             MockedStatic<StargateRestrictions> rules = mockStatic(StargateRestrictions.class))
        {
            cfg.when(ConfigManager::isUseCooldownEnabled).thenReturn(Boolean.TRUE);
            // A mocked config answers 0 here, which would shut the gate behind the cart and
            // is nothing to do with cooldowns.
            cfg.when(ConfigManager::getTimeoutShutdown).thenReturn(Integer.valueOf(30));

            rollIn();

            rules.verify(() -> StargateRestrictions.addPlayerUseCooldown(rider), never());
            rules.verify(() -> StargateRestrictions.addPlayerRecentArrival(rider, dst), never());
        }
    }

    /** A cart is stopped where it enters, so the gate decides what speed it leaves at. */
    @Test
    void theCartIsStoppedAtTheGateBeforeItIsSentOn()
    {
        rollIn();

        final ArgumentCaptor<Vector> speeds = ArgumentCaptor.forClass(Vector.class);
        verify(cart, atLeastOnce()).setVelocity(speeds.capture());
        final Vector first = speeds.getAllValues().get(0);
        assertEquals(0.0, first.length(), 1.0e-9, "the cart is halted on entry");
    }

    /**
     * An empty cart leaves the far gate moving, away from it and no slower than it arrived.
     *
     * <p>Five times its entry speed, which is what makes it clear of the arrival portal
     * before the next move event. At its entry speed it would sit there.
     */
    @Test
    void anEmptyCartLeavesTheFarGateUnderItsOwnSpeed()
    {
        rollIn();

        final ArgumentCaptor<Vector> speeds = ArgumentCaptor.forClass(Vector.class);
        verify(cart, atLeastOnce()).setVelocity(speeds.capture());
        final List<Vector> all = speeds.getAllValues();
        final Vector exit = all.get(all.size() - 1);
        assertEquals(5.0, exit.getX(), 1.0e-9, "east-facing gate, five times the entry speed");
        assertEquals(0.0, exit.getZ(), 1.0e-9, "and nothing sideways");
    }

    /**
     * The cart is put a block clear of the far portal, not in it.
     *
     * <p>Landing on the arrival block itself is landing in the gate, which raises a move
     * event from inside it.
     */
    @Test
    void theCartLandsClearOfTheFarPortal()
    {
        rollIn();

        final Location landed = whereItLanded();
        assertEquals(101.5, landed.getX(), 1.0e-9, "one block out along the gate's facing");
        assertEquals(71.0, landed.getY(), 1.0e-9, "and one block up");
        assertEquals(200.5, landed.getZ(), 1.0e-9);
        assertEquals(200.5, dst.getGatePlayerTeleportLocation().getZ(), 1.0e-9,
            "the gate's own arrival point is not moved by a cart passing through");
    }

    /**
     * A cart faces the way it is travelling when it arrives.
     *
     * <p>Yaw is measured clockwise from south, so a cart leaving eastward faces -90. Getting
     * the sign wrong points every arrival backwards.
     */
    @Test
    void theCartFacesTheWayItIsTravellingWhenItArrives()
    {
        rollIn();

        assertEquals(-90.0f, whereItLanded().getYaw(), 1.0e-4f, "leaving east");
        assertEquals(0.0f, whereItLanded().getPitch(), 1.0e-4f,
            "and level, whatever the gate's own arrival point was tilted at");
    }

    /** A gate with a minecart arrival point of its own uses it, not the walking one. */
    @Test
    void aMinecartArrivalPointIsPreferredOverTheWalkingOne()
    {
        dst.setGateMinecartTeleportLocation(new Location(world, 50.5, 60.0, 70.5));

        rollIn();

        assertEquals(51.5, whereItLanded().getX(), 1.0e-9, "the rail arrival, stepped clear");
    }

    /**
     * With FINE on, the entry line names the vehicle and what it rolled into.
     *
     * <p>The line is built only when it would be printed. A cart raises this event roughly
     * twenty times a second, and the whole point of the guard is that none of this runs on
     * the other nineteen.
     */
    @Test
    void theEntryLineNamesTheVehicleAndTheBlockWhenFineIsOn() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.isLoggable(Level.FINE)).thenReturn(true);
        PrivateStatics.set(WormholeXTreme.class, "thisPlugin", plugin);

        rollIn();

        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(plugin, atLeastOnce()).prettyLog(eq(Level.FINE), said.capture());
        assertTrue(said.getAllValues().stream().anyMatch(line -> line.startsWith("VehicleMoveEvent:")
            && line.contains("MINECART") && line.contains("AIR")),
            "the entry line says what moved and what it moved into: " + said.getAllValues());
    }

    /**
     * A cart nudged in at a standstill leaves pointing the way the far gate points.
     *
     * <p>With no speed there is no direction to read off the exit velocity, so the gate's own
     * facing answers instead. It gives the same heading the other way round: 270 rather than
     * the -90 a moving cart is given.
     */
    @Test
    void aCartEnteringAtAStandstillIsPointedTheWayTheGatePoints()
    {
        when(cart.getVelocity()).thenReturn(new Vector(0.0, 0.0, 0.0));

        rollIn();

        assertEquals(270.0f, whereItLanded().getYaw(), 1.0e-4f, "east, as the far gate faces");
    }

    /**
     * Something aboard that is not a player is carried, and marked as nothing.
     *
     * <p>The mark exists to stop the player listener teleporting a rider out of their seat.
     * A mob riding along has no such listener, and asking for its player mark would be asking
     * about a player that is not there.
     */
    @Test
    void aPassengerThatIsNotAPlayerIsCarriedButNotMarked()
    {
        final Entity mob = mock(Entity.class);
        final UUID mobId = UUID.randomUUID();
        when(mob.getUniqueId()).thenReturn(mobId);
        when(cart.getPassengers()).thenReturn(Collections.singletonList(mob));

        rollIn();

        assertTrue(WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(cart.getUniqueId()),
            "the cart still travels");
        assertFalse(WormholeXTremeVehicleListener.isPlayerRecentlyTeleportedByVehicle(mobId),
            "but there is no rider to hold back");
    }

    /**
     * A cart bounced off a shut far iris is marked before it is moved.
     *
     * <p>The bounce is a teleport like any other, and lands the cart in the gate it just
     * entered. Unmarked, it reads as a fresh entry and bounces again.
     */
    @Test
    void theIrisBounceIsMarkedSoItDoesNotReadAsAnotherEntry()
    {
        dst.setGateIrisActive(true);
        src.setGateMinecartTeleportLocation(new Location(world, 5.5, 65.0, 6.5));

        rollIn();

        assertTrue(WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(cart.getUniqueId()),
            "a bounced cart has moved, and the move must not read as another trip");
        assertFalse(WormholeXTremeVehicleListener.isPlayerRecentlyTeleportedByVehicle(UUID.randomUUID()),
            "nobody was aboard to mark");
    }
}
