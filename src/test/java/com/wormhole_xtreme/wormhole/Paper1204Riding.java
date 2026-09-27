package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * A mocked vehicle that teleports the way Paper 1.20.4 does: not at all while anything rides it.
 *
 * <p>Paper 1.20.4's {@code CraftEntity.teleport} returns false and moves nothing for an entity
 * with passengers, unless given its own {@code RETAIN_PASSENGERS} flag. 1.21 carries them along
 * instead, which is what every test here assumed, so a boat, cart or horse with a rider stayed
 * at the gate on 1.20.4 with the plugin reporting the trip done (#506).
 */
public final class Paper1204Riding
{
    /** Where the vehicle is now, and who is aboard it. */
    public static final class Stack
    {
        private final List<Entity> aboard = new ArrayList<>();
        private Location at;

        /** @return where the vehicle stands: its start, or the last teleport it accepted */
        public Location at()
        {
            return at;
        }

        /** @return true if {@code rider} is seated on the vehicle right now */
        public boolean carries(final Entity rider)
        {
            return aboard.contains(rider);
        }
    }

    private Paper1204Riding() {}

    /**
     * Wires {@code vehicle}'s seats and teleport, with {@code riders} aboard to start with.
     *
     * @param start
     *            where it stands before any trip
     * @return its live state, for the test to read after the trip
     */
    public static Stack refusesWhileRidden(final Entity vehicle, final Location start, final Entity... riders)
    {
        final Stack stack = new Stack();
        stack.at = start;
        for (final Entity rider : riders)
        {
            stack.aboard.add(rider);
            when(rider.getVehicle()).thenAnswer(call -> stack.aboard.contains(rider) ? vehicle : null);
            when(rider.isInsideVehicle()).thenAnswer(call -> stack.aboard.contains(rider));
        }
        when(vehicle.getLocation()).thenAnswer(call -> stack.at);
        when(vehicle.getPassengers()).thenAnswer(call -> new ArrayList<>(stack.aboard));
        when(vehicle.isEmpty()).thenAnswer(call -> stack.aboard.isEmpty());
        when(vehicle.removePassenger(any())).thenAnswer(call -> stack.aboard.remove(call.getArgument(0)));
        when(vehicle.addPassenger(any())).thenAnswer(call ->
        {
            final Entity rider = call.getArgument(0);
            if (!stack.aboard.contains(rider))
            {
                stack.aboard.add(rider);
            }
            return true;
        });
        when(vehicle.teleport(any(Location.class))).thenAnswer(call ->
        {
            if (!stack.aboard.isEmpty())
            {
                return false;
            }
            stack.at = call.getArgument(0);
            return true;
        });
        return stack;
    }
}
