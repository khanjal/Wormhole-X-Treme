package com.wormhole_xtreme.wormhole.model.window;

import java.lang.reflect.Method;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.util.Vector;

/**
 * The boats and minecarts in a window's far room, shown as stand-ins beside its mobs (#296, step 1b).
 *
 * <p>Every minecart shows as a plain, empty one, so a TNT, hopper, spawner or furnace cart's copy can
 * never do its job; a chest boat shows as a plain boat of its wood. Nothing inside one is copied.
 */
final class FarVehicles
{
    /** What a boat's wood is read and set by before 1.21.3, looked up by name: deprecated from 1.21.3, where each wood is its own type. */
    private static final Method GET_BOAT_TYPE = boatMethod("getBoatType");

    private static final Method SET_BOAT_TYPE = (GET_BOAT_TYPE == null) ? null
        : boatMethod("setBoatType", GET_BOAT_TYPE.getReturnType());

    /** The entity types whose wood is a {@code Boat.Type}, before 1.21.3. */
    private static final String UNTYPED_BOAT = "BOAT";

    private static final String UNTYPED_CHEST_BOAT = "CHEST_BOAT";

    /** Static methods only. */
    private FarVehicles()
    {
    }

    /**
     * @param entity
     *            an entity in a far room
     * @return true for a boat or a minecart of any kind
     */
    static boolean isVehicle(final Entity entity)
    {
        return (entity instanceof Boat) || (entity instanceof Minecart);
    }

    /**
     * Whether a vehicle in a far room is shown: a boat or minecart, there, not hidden by default and
     * not a stand-in. Who rides it does not matter: a mob rider is shown standing on its own, and the
     * vehicle is shown empty.
     *
     * @param entity
     *            an entity in a far room
     * @return true if it is given a stand-in
     */
    static boolean copied(final Entity entity)
    {
        return isVehicle(entity) && entity.isValid() && entity.isVisibleByDefault() && !StandIns.isStandIn(entity);
    }

    /**
     * What a vehicle's stand-in is spawned as: a plain rideable minecart for every minecart, a boat of
     * the same wood without its chest for a boat.
     *
     * @param original
     *            the far vehicle
     * @return the type, or null where this server has no plain boat of that wood
     */
    // A mock vehicle that stubs no type has none, where a server always gives one.
    @SuppressWarnings("java:S2583")
    static Class<? extends Entity> kindOf(final Entity original)
    {
        if (original instanceof Minecart)
        {
            return RideableMinecart.class;
        }
        final EntityType type = original.getType();
        if (type == null)
        {
            return null;
        }
        final EntityType plain = plainBoat(type.name());
        return (plain == null) ? null : plain.getEntityClass();
    }

    /**
     * The boat without its chest: {@code CHEST_BOAT} is {@code BOAT} up to 1.21.1, and from 1.21.3
     * {@code OAK_CHEST_BOAT} is {@code OAK_BOAT} and {@code BAMBOO_CHEST_RAFT} is {@code BAMBOO_RAFT}.
     *
     * @param name
     *            a boat's entity type name
     * @return the plain boat's type, or null where this server has none by that name
     */
    static EntityType plainBoat(final String name)
    {
        try
        {
            return EntityType.valueOf(name.replace("CHEST_", ""));
        }
        catch (final IllegalArgumentException none)
        {
            return null;
        }
    }

    /**
     * Leaves a vehicle's stand-in where it is put, and gives a boat its wood where the wood is not
     * already its type. With no gravity and a minecart's top speed nothing, it does not roll on a rail
     * or drift off one; anything that moves it all the same, water lifting a boat, is undone by the
     * follow task, which puts it back where its vehicle shows whenever it is found elsewhere.
     *
     * @param copy
     *            the stand-in
     * @param original
     *            the far vehicle
     */
    static void dress(final Entity copy, final Entity original)
    {
        final Vector still = new Vector();
        copy.setVelocity(still);
        if (copy instanceof Minecart cart)
        {
            cart.setMaxSpeed(0.0);
            cart.setFlyingVelocityMod(still);
            cart.setDerailedVelocityMod(still);
        }
        else if (copy instanceof Boat)
        {
            copyWood(copy, original);
        }
    }

    /** Copies a boat's wood by the methods looked up by name, up to 1.21.1; from 1.21.3 the wood is the type, and nothing is copied. */
    private static void copyWood(final Entity copy, final Entity original)
    {
        final String type = String.valueOf(original.getType());
        if ((SET_BOAT_TYPE == null) || !(UNTYPED_BOAT.equals(type) || UNTYPED_CHEST_BOAT.equals(type)))
        {
            return;
        }
        try
        {
            SET_BOAT_TYPE.invoke(copy, GET_BOAT_TYPE.invoke(original));
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError oak)
        {
            // Shown as the default wood: a look, not worth more than the try.
        }
    }

    /** A boat's method by name, or null on a server without it. */
    private static Method boatMethod(final String name, final Class<?>... parameters)
    {
        try
        {
            return Boat.class.getMethod(name, parameters);
        }
        catch (final NoSuchMethodException | RuntimeException | LinkageError absent)
        {
            return null;
        }
    }
}
