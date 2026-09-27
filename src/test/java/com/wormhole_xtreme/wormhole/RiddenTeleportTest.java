package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.utils.EntityUtils;

/**
 * Moving something that is being ridden, on a server that will not move it with riders aboard.
 *
 * <p>Paper 1.20.4 refuses to teleport an entity that has passengers, so every gate and ring
 * left a ridden boat, cart or horse where it was, rider still in it, while reporting the trip
 * done (#506). The move now takes the riders off first, moves the vehicle, and sends the riders
 * after it; a vehicle that still will not move gets its riders straight back.
 */
class RiddenTeleportTest
{
    private final World world = mock(World.class);
    private final Location start = new Location(world, 0.5, 64, 0.5);

    private static Location arrivalFacing(final World world, final float yaw)
    {
        final Location loc = new Location(world, 100.5, 70, 200.5);
        loc.setYaw(yaw);
        return loc;
    }

    private static List<Entity>[] pairsOf(final Entity root)
    {
        final List<Entity> parents = new ArrayList<>();
        final List<Entity> children = new ArrayList<>();
        EntityUtils.collectPassengerPairs(root, parents, children);
        @SuppressWarnings("unchecked")
        final List<Entity>[] both = new List[] { parents, children };
        return both;
    }

    @Test
    void aBoatThatWillNotMoveWithARiderAboardStillArrivesWithItsRider()
    {
        final Boat boat = mock(Boat.class);
        final Player rider = mock(Player.class);
        when(rider.teleport(any(Location.class))).thenReturn(true);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(boat, start, rider);
        final Location arrival = arrivalFacing(world, 90f);
        final List<Entity>[] pairs = pairsOf(boat);

        assertTrue(RiddenTeleport.move(boat, arrival, pairs[0], pairs[1]),
            "with the rider off, 1.20.4 moves the boat");

        assertEquals(arrival, stack.at(), "the boat itself must reach the far gate, not stay at this one");
        verify(rider).teleport(any(Location.class));
        assertFalse(stack.carries(rider), "seating them again is the caller's, once their client has caught up");
    }

    /**
     * Each rider is sent the arrival's yaw, on their own copy of it.
     *
     * <p>A rider's view is theirs and not the seat's, so without this they arrive facing the
     * way they went in, sideways or backwards on a gate that turns a corner. The copy matters
     * because the same location was just handed to the vehicle.
     */
    @Test
    void aRiderIsSentFacingTheWayTheVehicleTravelsOnTheirOwnCopy()
    {
        final Boat boat = mock(Boat.class);
        final Player rider = mock(Player.class);
        Paper1204Riding.refusesWhileRidden(boat, start, rider);
        final Location arrival = arrivalFacing(world, 45f);
        final List<Entity>[] pairs = pairsOf(boat);

        RiddenTeleport.move(boat, arrival, pairs[0], pairs[1]);

        final ArgumentCaptor<Location> sent = ArgumentCaptor.forClass(Location.class);
        verify(rider).teleport(sent.capture());
        assertEquals(45f, sent.getValue().getYaw(), 0.001f, "the rider faces the travel direction");
        assertNotSame(arrival, sent.getValue(), "the rider gets a copy, not the vehicle's own location");
    }

    /** A player riding a zombie riding a boat: every seat is emptied, or the boat still will not move. */
    @Test
    void aStackSeveralDeepIsTakenApartAllTheWayDown()
    {
        final Boat boat = mock(Boat.class);
        final Zombie zombie = mock(Zombie.class);
        final Player player = mock(Player.class);
        final Paper1204Riding.Stack boatSeats = Paper1204Riding.refusesWhileRidden(boat, start, zombie);
        final Paper1204Riding.Stack zombieSeats = Paper1204Riding.refusesWhileRidden(zombie, start, player);
        final Location arrival = arrivalFacing(world, 0f);
        final List<Entity>[] pairs = pairsOf(boat);

        assertTrue(RiddenTeleport.move(boat, arrival, pairs[0], pairs[1]));

        assertEquals(arrival, boatSeats.at(), "the boat moved");
        assertEquals(arrival.getX(), zombieSeats.at().getX(), 0.001,
            "the zombie, emptied of its own rider, moved too");
        verify(player).teleport(any(Location.class));
    }

    /**
     * A vehicle that will not move keeps its riders, here.
     *
     * <p>Something else refused the trip: another plugin cancelling the teleport, or the
     * vehicle dying mid-tick. Sending the riders on alone would split them from it across two
     * gates, which is worse than not going.
     */
    @Test
    void aVehicleThatStillWillNotMoveHasItsRidersPutBackWhereTheySat()
    {
        final Boat boat = mock(Boat.class);
        final Player rider = mock(Player.class);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(boat, start, rider);
        when(boat.teleport(any(Location.class))).thenReturn(false);
        final List<Entity>[] pairs = pairsOf(boat);

        assertFalse(RiddenTeleport.move(boat, arrivalFacing(world, 0f), pairs[0], pairs[1]),
            "a refused move must be reported as one");

        assertSame(start, stack.at());
        assertTrue(stack.carries(rider), "the rider must be back aboard at this end");
        verify(rider, never()).teleport(any(Location.class));
    }

    @Test
    void aVehicleThatThrowsHasItsRidersPutBackBeforeTheCallerHearsOfIt()
    {
        final Boat boat = mock(Boat.class);
        final Player rider = mock(Player.class);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(boat, start, rider);
        when(boat.teleport(any(Location.class))).thenThrow(new IllegalStateException("gone"));
        final List<Entity>[] pairs = pairsOf(boat);
        final Location arrival = arrivalFacing(world, 0f);

        assertThrows(IllegalStateException.class, () -> RiddenTeleport.move(boat, arrival, pairs[0], pairs[1]));

        assertTrue(stack.carries(rider), "the rider must not be left standing beside it");
    }

    /**
     * The rider comes off at the portal, where the plugin's own dismount rule says no.
     *
     * <p>That rule keeps a player from hopping off mid-transit. Left on, it would cancel this
     * dismount too, and the vehicle would stay unmovable on 1.20.4.
     */
    @Test
    void thePortalDismountRuleStandsAsideWhileTheStackIsTakenApart()
    {
        final Boat boat = mock(Boat.class);
        final Player rider = mock(Player.class);
        final Player bystander = mock(Player.class);
        when(boat.getPassengers()).thenReturn(List.<Entity>of(rider));
        when(boat.teleport(any(Location.class))).thenReturn(true);
        final boolean[] refusedAtUnseat = { true };
        when(boat.removePassenger(any())).thenAnswer(call ->
        {
            refusedAtUnseat[0] = GateDismount.shouldRefuse(rider);
            // Somebody else getting off in the same instant is still asked about.
            GateDismount.shouldRefuse(bystander);
            return Boolean.TRUE;
        });
        // Where the rider stands is what the rule would look up; with it aside, it must not look.
        when(rider.getLocation()).thenThrow(new AssertionError("the dismount rule was consulted"));
        final List<Entity>[] pairs = pairsOf(boat);

        RiddenTeleport.move(boat, arrivalFacing(world, 0f), pairs[0], pairs[1]);

        assertFalse(refusedAtUnseat[0], "the plugin must not refuse its own dismount");
        verify(bystander).getLocation();
    }

    /**
     * A passenger that will not come off stops the move before anything goes.
     *
     * <p>Another plugin can cancel the dismount, and {@code removePassenger} says true whatever
     * happened. Moving on would be refused on 1.20.4, or on 1.21 would carry that one passenger
     * along while the rest were sent separately.
     */
    @Test
    void aPassengerThatWillNotComeOffStopsTheMoveWithEveryoneBackAboard()
    {
        final Boat boat = mock(Boat.class);
        final Zombie zombie = mock(Zombie.class);
        final Player rider = mock(Player.class);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(boat, start, zombie, rider);
        // The zombie's dismount is cancelled: it says yes, and stays.
        // doReturn, not when(): calling the stubbed method to stub it would take the zombie off.
        doReturn(Boolean.TRUE).when(boat).removePassenger(zombie);
        final List<Entity>[] pairs = pairsOf(boat);

        assertFalse(RiddenTeleport.move(boat, arrivalFacing(world, 0f), pairs[0], pairs[1]));

        verify(boat, never()).teleport(any(Location.class));
        verify(rider, never()).teleport(any(Location.class));
        assertTrue(stack.carries(rider), "the rider taken off first is put back");
        assertTrue(stack.carries(zombie));
    }

    @Test
    void aPassengerWhoseUnseatThrowsStopsTheMoveToo()
    {
        final Boat boat = mock(Boat.class);
        final Player rider = mock(Player.class);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(boat, start, rider);
        doThrow(new IllegalStateException("not now")).when(boat).removePassenger(rider);
        final List<Entity>[] pairs = pairsOf(boat);

        assertFalse(RiddenTeleport.move(boat, arrivalFacing(world, 0f), pairs[0], pairs[1]));

        verify(boat, never()).teleport(any(Location.class));
        assertTrue(stack.carries(rider));
    }

    /** The vehicle is sent its own copy too: a server may keep or adjust what it is handed. */
    @Test
    void theVehicleIsSentACopyOfTheTarget()
    {
        final Boat boat = mock(Boat.class);
        when(boat.teleport(any(Location.class))).thenReturn(true);
        final Location arrival = arrivalFacing(world, 0f);

        RiddenTeleport.move(boat, arrival, new ArrayList<>(), new ArrayList<>());

        final ArgumentCaptor<Location> sent = ArgumentCaptor.forClass(Location.class);
        verify(boat).teleport(sent.capture());
        assertNotSame(arrival, sent.getValue());
        assertEquals(arrival, sent.getValue());
    }
}
