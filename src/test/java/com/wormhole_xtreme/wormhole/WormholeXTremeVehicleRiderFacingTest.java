package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A rider arrives looking the way the cart is going, not the way they set off looking.
 *
 * <p>The arrival location has always carried a yaw worked out from the exit velocity, but it
 * was only ever applied to the vehicle. A passenger's view is theirs and not the seat's:
 * teleporting a cart does not turn the person sitting in it, and neither does re-seating
 * them. So a rider came out of a gate still facing whatever direction they entered facing,
 * which on a gate that turns a corner meant arriving looking sideways or backwards while the
 * cart carried on ahead.
 *
 * <p>What makes this worth pinning is that the code looked as though it handled this. The
 * failure path -- used only when {@code addPassenger} does not work first time -- teleports
 * the passenger to the arrival location, yaw and all. So the view aligned correctly exactly
 * when something had gone wrong, and not when everything worked.
 */
class WormholeXTremeVehicleRiderFacingTest
{
    private static Location arrivalFacing(final float yaw)
    {
        final World world = mock(World.class);
        final Location loc = new Location(world, 10.5, 64, 20.5);
        loc.setYaw(yaw);
        loc.setPitch(0f);
        return loc;
    }

    @Test
    void aRiderIsTurnedToFaceTheWayTheVehicleIsTravelling()
    {
        final Player rider = mock(Player.class);
        final Location arrival = arrivalFacing(90f);

        WormholeXTremeVehicleListener.faceTravelDirection(rider, arrival);

        final ArgumentCaptor<Location> sent = ArgumentCaptor.forClass(Location.class);
        verify(rider).teleport(sent.capture());
        assertEquals(90f, sent.getValue().getYaw(), 0.001f,
            "the rider must be turned to the travel direction, or they arrive facing backwards");
    }

    /**
     * The caller's own arrival location is not modified.
     *
     * <p>It is shared: the same object is handed to the vehicle teleport and to the exit
     * velocity, and every passenger of a full cart is faced from it in turn. Turning a rider
     * by mutating it would quietly change where the vehicle itself was sent.
     */
    @Test
    void turningARiderDoesNotDisturbTheSharedArrivalLocation()
    {
        final Player rider = mock(Player.class);
        final Location arrival = arrivalFacing(45f);

        WormholeXTremeVehicleListener.faceTravelDirection(rider, arrival);

        final ArgumentCaptor<Location> sent = ArgumentCaptor.forClass(Location.class);
        verify(rider).teleport(sent.capture());
        assertNotSame(arrival, sent.getValue(),
            "the rider must be sent a copy, not the caller's own arrival location");
        assertEquals(45f, arrival.getYaw(), 0.001f);
    }

    /**
     * Anything that is not a player is left alone.
     *
     * <p>A view direction is a player's. Teleporting a mob or a chest cart passenger here
     * would be a second, pointless move of an entity the vehicle is about to seat anyway.
     */
    @Test
    void aPassengerThatIsNotAPlayerIsNotMoved()
    {
        final Entity animal = mock(Entity.class);

        WormholeXTremeVehicleListener.faceTravelDirection(animal, arrivalFacing(90f));

        verify(animal, never()).teleport(any(Location.class));
    }

    @Test
    void noArrivalLocationMeansNothingToFace()
    {
        final Player rider = mock(Player.class);

        WormholeXTremeVehicleListener.faceTravelDirection(rider, null);

        verify(rider, never()).teleport(any(Location.class));
    }

    @BeforeEach
    void installPlugin() throws Exception
    {
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
    }

    /**
     * A rider the server will not turn is logged and stepped over.
     *
     * <p>Turning the rider is cosmetic and happens after the vehicle has already moved, so
     * refusing it must not leave the arrival half done.
     */
    @Test
    void aRiderWhoCannotBeTurnedDoesNotBreakTheArrival()
    {
        final Player rider = mock(Player.class);
        when(rider.teleport(any(Location.class))).thenThrow(new IllegalStateException("not here"));

        assertDoesNotThrow(() -> WormholeXTremeVehicleListener.faceTravelDirection(rider, arrivalFacing(90f)));
    }

    /**
     * An Error is not swallowed on the way past.
     *
     * <p>These catches were written as {@code Throwable}, which meant a genuinely broken
     * server -- a missing method, an exhausted heap -- was quietly logged as a cosmetic
     * failure to turn someone and the arrival carried on regardless. Only the vehicle APIs
     * are guarded here, and those are stable across the versions this plugin supports, so
     * there is no cross-version Error worth catching.
     */
    @Test
    void anErrorDuringTheTurnIsNotSwallowed()
    {
        final Player rider = mock(Player.class);
        when(rider.teleport(any(Location.class))).thenThrow(new NoSuchMethodError("teleport"));

        assertThrows(NoSuchMethodError.class,
            () -> WormholeXTremeVehicleListener.faceTravelDirection(rider, arrivalFacing(90f)),
            "an Error must reach the server, not be logged as a cosmetic failure");
    }
}
