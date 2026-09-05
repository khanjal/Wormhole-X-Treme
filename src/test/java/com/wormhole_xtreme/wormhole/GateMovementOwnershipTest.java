package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Item;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

/**
 * Pins which listener owns an entity's movement through a gate.
 *
 * <p>The split is not {@code instanceof Vehicle}, and getting that wrong has caused two
 * separate bugs already. In Bukkit, {@code Pig} and {@code AbstractHorse} — horses, camels,
 * donkeys, mules, llamas — are Vehicles, but only minecarts and boats ever raise
 * {@link org.bukkit.event.vehicle.VehicleMoveEvent}. Testing for Vehicle therefore hands
 * ridable animals to a listener that never hears about them.
 */
class GateMovementOwnershipTest
{
    private static Entity mockOf(final Class<? extends Entity> type)
    {
        return mock(type);
    }

    @Test
    void onlyMinecartsAndBoatsRaiseVehicleMoveEvents()
    {
        assertTrue(WormholeXTremeVehicleListener.handlesMovementOf(mockOf(Minecart.class)));
        assertTrue(WormholeXTremeVehicleListener.handlesMovementOf(mockOf(Boat.class)));
    }

    @Test
    void ridableAnimalsAreNotTheVehicleListenersJobDespiteBeingVehicles()
    {
        // Each of these is a Bukkit Vehicle and none of them raises VehicleMoveEvent.
        for (final Class<? extends Entity> type : java.util.Arrays.asList(
            Horse.class, Pig.class, Camel.class, Donkey.class, Llama.class, Strider.class))
        {
            final Entity e = mockOf(type);
            assertTrue(e instanceof Vehicle, type.getSimpleName() + " is expected to be a Bukkit Vehicle");
            assertFalse(WormholeXTremeVehicleListener.handlesMovementOf(e),
                type.getSimpleName() + " raises no VehicleMoveEvent, so the vehicle listener must not claim it");
        }
    }

    @Test
    void ordinaryMobsAndItemsAreNobodyElsesJob()
    {
        // These reach a gate only via the periodic sweep, so it must not skip them.
        for (final Class<? extends Entity> type : java.util.Arrays.asList(
            Zombie.class, Skeleton.class, Cow.class, Item.class))
        {
            assertFalse(WormholeXTremeVehicleListener.handlesMovementOf(mockOf(type)),
                type.getSimpleName() + " should be left to the entity sweep");
        }
    }

    @Test
    void thrownItemsAndOrbsAreSwept()
    {
        // Confirmed in game: drop an item into an open gate and it comes out the far side.
        for (final Class<? extends Entity> type : java.util.Arrays.asList(
            org.bukkit.entity.Item.class, org.bukkit.entity.ExperienceOrb.class))
        {
            assertFalse(WormholeXTremeVehicleListener.handlesMovementOf(mockOf(type)),
                type.getSimpleName() + " should be left to the entity sweep");
        }
    }

    @Test
    void hangingEntitiesAreNeverSwept()
    {
        // An item frame or painting is attached to a block. Sending one through a gate
        // rips it off the wall and orphans it at the far end, so a decorated gate frame
        // would strip itself every time the gate opened.
        for (final Class<? extends Entity> type : java.util.Arrays.asList(
            org.bukkit.entity.ItemFrame.class, org.bukkit.entity.Painting.class))
        {
            final Entity e = mockOf(type);
            assertTrue(e instanceof org.bukkit.entity.Hanging, type.getSimpleName() + " should be Hanging");
        }
    }

    @Test
    void nullIsNobodysJob()
    {
        assertFalse(WormholeXTremeVehicleListener.handlesMovementOf(null));
    }
}
