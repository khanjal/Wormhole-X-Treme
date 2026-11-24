package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.entity.minecart.SpawnerMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

/**
 * The boats and minecarts in a window's far room are shown beside its mobs (#296, step 1b), and
 * nothing else that is not a creature is.
 *
 * <p>Every minecart's stand-in is a plain one: a TNT cart's copy that could be lit, a hopper cart's
 * that could pull items out of the viewer's world, or a spawner cart's that could spawn, would turn a
 * cosmetic copy into something that acts.
 */
class FarVehiclesTest
{
    /** Every kind of minecart, the rideable one included. */
    private static final List<Class<? extends Entity>> CARTS = List.of(RideableMinecart.class, StorageMinecart.class,
        HopperMinecart.class, PoweredMinecart.class, ExplosiveMinecart.class, SpawnerMinecart.class, CommandMinecart.class);

    /** Boats, chest boats and every kind of minecart are copied, and the scenery the plan leaves out is not. */
    @Test
    void boatsAndEveryMinecartAreCopiedButNotFramesPaintingsItemsStandsOrDisplays()
    {
        final World far = mock(World.class);
        for (final Class<? extends Entity> cart : CARTS)
        {
            assertTrue(FarCreatures.copied(entity(cart, far, 0, 0, 0)), cart.getSimpleName() + " is shown");
        }
        assertTrue(FarCreatures.copied(entity(Boat.class, far, 0, 0, 0)), "a boat is shown");
        assertTrue(FarCreatures.copied(entity(ChestBoat.class, far, 0, 0, 0)), "a chest boat is shown");
        for (final Class<? extends Entity> scenery : List.of(ItemFrame.class, Painting.class, Item.class, ArmorStand.class,
            Display.class))
        {
            assertFalse(FarCreatures.copied(entity(scenery, far, 0, 0, 0)), scenery.getSimpleName() + " is never shown");
        }
    }

    /** A vehicle gone, hidden by another plugin, or one of the stand-ins themselves, is not copied. */
    @Test
    void aVehicleGoneHiddenOrAStandInIsNotCopied()
    {
        final World far = mock(World.class);
        final Boat gone = entity(Boat.class, far, 0, 0, 0);
        when(gone.isValid()).thenReturn(false);
        assertFalse(FarCreatures.copied(gone), "a boat broken since the room was read");
        final Boat hidden = entity(Boat.class, far, 0, 0, 0);
        when(hidden.isVisibleByDefault()).thenReturn(false);
        assertFalse(FarCreatures.copied(hidden), "a boat another plugin hides by default");
        final RideableMinecart standIn = entity(RideableMinecart.class, far, 0, 0, 0);
        StandIns.track(standIn);
        try
        {
            assertFalse(FarCreatures.copied(standIn), "a cart stand-in is never copied again, one more each sweep");
        }
        finally
        {
            StandIns.untrack(standIn);
        }
        assertTrue(FarCreatures.copied(standIn), "once it is no stand-in, the same cart is copied");
    }

    /** A mob riding a far boat is still shown, standing where it sits; the boat is shown as well, empty. */
    @Test
    void aMobRidingAFarVehicleIsShownAndSoIsTheVehicle()
    {
        final World far = mock(World.class);
        final Zombie rider = entity(Zombie.class, far, 0, 0, 0);
        when(rider.isInsideVehicle()).thenReturn(true);
        final Boat boat = entity(Boat.class, far, 0, 0, 0);
        when(boat.getPassengers()).thenReturn(List.of(rider));

        assertTrue(FarCreatures.copied(rider), "the rider stands in on its own");
        assertTrue(FarCreatures.copied(boat), "the boat it rides is shown too");
    }

    /**
     * Vehicles are read only from chunks already loaded with their entities, like mobs: the far room
     * is never loaded to find a cart in it.
     */
    @Test
    void vehiclesAreReadOnlyFromChunksAlreadyLoadedWithTheirEntities()
    {
        final World far = mock(World.class);
        final Chunk ready = mock(Chunk.class);
        when(ready.isEntitiesLoaded()).thenReturn(true);
        final Chunk arriving = mock(Chunk.class);
        when(far.isChunkLoaded(6, -2)).thenReturn(true);
        when(far.isChunkLoaded(5, -2)).thenReturn(true);
        when(far.getChunkAt(6, -2)).thenReturn(ready);
        when(far.getChunkAt(5, -2)).thenReturn(arriving);
        final Boat boat = entity(Boat.class, far, 100.5, 70.0, -20.5);
        final HopperMinecart early = entity(HopperMinecart.class, far, 90.5, 70.0, -20.5);
        when(far.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.of(boat, early));

        final List<Entity> found = FarCreatures.inRoom(far, new int[] { 90, 66, -40, 104, 75, -18 });

        assertEquals(List.of(boat), found, "the boat in the loaded chunk, and not the cart in the one still loading");
        verify(far, never()).loadChunk(anyInt(), anyInt());
        verify(far, never()).loadChunk(anyInt(), anyInt(), any(Boolean.class));
        for (final Invocation call : mockingDetails(far).getInvocations())
        {
            if (call.getMethod().getName().equals("getChunkAt"))
            {
                assertEquals(-2, (int) call.getArgument(1), "getChunkAt on a chunk not loaded would load it");
            }
        }
    }

    /** Every minecart, TNT, hopper and spawner carts included, is spawned as a plain rideable one. */
    @Test
    void everyMinecartIsSpawnedAsAPlainRideableOne()
    {
        for (final Class<? extends Entity> cart : CARTS)
        {
            assertEquals(RideableMinecart.class, StandIns.kindOf(mock(cart)), cart.getSimpleName() + " shows as a plain cart");
        }
    }

    /**
     * Every chest boat this server has is spawned as the plain boat of its wood: {@code CHEST_BOAT}
     * as {@code BOAT} up to 1.21.1, {@code OAK_CHEST_BOAT} as {@code OAK_BOAT} and the bamboo chest
     * raft as the raft from 1.21.3. Run against every API in CI, so each leg checks its own names.
     */
    @Test
    void aChestBoatIsSpawnedAsThePlainBoatOfItsWood()
    {
        int chestBoats = 0;
        for (final EntityType type : EntityType.values())
        {
            final String name = type.name();
            if (name.contains("CHEST_BOAT") || name.contains("CHEST_RAFT"))
            {
                chestBoats++;
                final ChestBoat original = mock(ChestBoat.class);
                when(original.getType()).thenReturn(type);
                final Class<? extends Entity> kind = StandIns.kindOf(original);
                assertNotNull(kind, name + " has a plain boat to show");
                assertEquals(EntityType.valueOf(name.replace("CHEST_", "")).getEntityClass(), kind, name + " without its chest");
                assertFalse(ChestBoat.class.isAssignableFrom(kind), name + " shows no chest, and has no inventory to copy");
            }
            else if (name.endsWith("_BOAT") || name.endsWith("_RAFT") || "BOAT".equals(name))
            {
                final Boat original = mock(Boat.class);
                when(original.getType()).thenReturn(type);
                assertEquals(type.getEntityClass(), StandIns.kindOf(original), name + " is its own wood");
            }
        }
        assertNotEquals(0, chestBoats, "every API in the supported range has a chest boat");
    }

    /** A boat type with no plain boat by that name has no stand-in, rather than a wrong one. */
    @Test
    void aBoatWithNoPlainBoatByItsNameIsNotShown()
    {
        assertNull(FarVehicles.plainBoat("NO_SUCH_CHEST_BOAT"), "nothing by that name on any server");
        assertNull(StandIns.kindOf(mock(Boat.class)), "a boat with no type has none to spawn");
    }

    private static <T extends Entity> T entity(final Class<T> type, final World world, final double x, final double y,
        final double z)
    {
        final T entity = mock(type);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        when(entity.isVisibleByDefault()).thenReturn(true);
        when(entity.getLocation()).thenReturn(new Location(world, x, y, z));
        return entity;
    }

    /** Players are never vehicles here, whatever they ride. */
    @Test
    void aPlayerIsNeverTakenForAVehicle()
    {
        assertFalse(FarVehicles.isVehicle(mock(Player.class)), "a player goes by the players' rules");
    }
}
