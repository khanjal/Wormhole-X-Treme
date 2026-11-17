package com.wormhole_xtreme.wormhole.model.ping;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

/**
 * The locator bar's pieces of the API, found at run time: they arrived in Minecraft 1.21.6, and
 * this jar is built against 1.20.4.
 *
 * <p>The attribute is looked up in {@link Registry#ATTRIBUTE} by its vanilla id rather than named,
 * because {@code Attribute} is an enum up to 1.21.1 and an interface from 1.21.3, and only the
 * registry and its {@code get} are the same shape on both sides of that change.
 */
public final class Waypoints
{
    /** The transmit attribute's vanilla id, the same on Spigot, Paper and Purpur. */
    static final String TRANSMIT_KEY = "waypoint_transmit_range";

    /**
     * How far a marker transmits: the attribute's own maximum, so the receiving player's
     * {@code waypoint_receive_range} alone decides how far off the ping is seen.
     */
    static final double TRANSMIT_RANGE = 6.0E7;

    /** {@code LivingEntity.setWaypointColor(Color)}, on Spigot and Paper from 1.21.6; null before. */
    private static final Method SET_COLOUR = find("setWaypointColor", Color.class);

    /*
     * Seams for a test with no server: the registry cannot initialise without one from 1.20.6, and
     * from 1.21.3 neither can Attribute itself, so a test never touches either.
     */
    private static BooleanSupplier supported = () -> transmitRange() != null;

    private static Function<LivingEntity, AttributeInstance> transmitter = Waypoints::fromEntity;

    /** Static helpers only. */
    private Waypoints()
    {
    }

    /**
     * The attribute that puts a living entity on the locator bar.
     *
     * @return it, or null on a server older than 1.21.6
     */
    public static Attribute transmitRange()
    {
        return fromRegistry(TRANSMIT_KEY);
    }

    /**
     * Whether this server has a locator bar to ping on at all.
     *
     * @return true from Minecraft 1.21.6
     */
    public static boolean isSupported()
    {
        return supported.getAsBoolean();
    }

    /**
     * An entity's transmit range attribute.
     *
     * @param entity
     *            the entity
     * @return its instance, or null where the server or the entity has none
     */
    static AttributeInstance transmitterOf(final LivingEntity entity)
    {
        return transmitter.apply(entity);
    }

    /**
     * Colours an entity's mark on the locator bar, where the server can.
     *
     * @param entity
     *            the marker
     * @param colour
     *            its colour on the bar
     * @return true if it was set
     */
    static boolean colour(final LivingEntity entity, final Color colour)
    {
        if (SET_COLOUR == null)
        {
            return false;
        }
        try
        {
            SET_COLOUR.invoke(entity, colour);
            return true;
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError e)
        {
            return false;
        }
    }

    /**
     * Whether the colour setter was found, for a test on an API that has it.
     *
     * @return true where {@code setWaypointColor} exists
     */
    static boolean canColour()
    {
        return SET_COLOUR != null;
    }

    /**
     * Stands in for the server's locator bar, for a test with no server.
     *
     * @param hasBar
     *            whether the server has one, or null for the registry again
     * @param attributeOf
     *            each entity's transmit range attribute, or null for the registry again
     */
    static void standIn(final BooleanSupplier hasBar, final Function<LivingEntity, AttributeInstance> attributeOf)
    {
        supported = (hasBar == null) ? (() -> transmitRange() != null) : hasBar;
        transmitter = (attributeOf == null) ? Waypoints::fromEntity : attributeOf;
    }

    /** @return the entity's transmit range attribute, or null */
    private static AttributeInstance fromEntity(final LivingEntity entity)
    {
        final Attribute range = transmitRange();
        return (range == null) ? null : entity.getAttribute(range);
    }

    /**
     * An attribute by its vanilla id.
     *
     * @param key
     *            the id, without its namespace
     * @return the attribute, or null where this server has none by that id
     */
    private static Attribute fromRegistry(final String key)
    {
        try
        {
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        }
        // A registry that cannot initialise, or a class missing on an odd fork, means no locator bar.
        catch (final Exception | LinkageError e)
        {
            return null;
        }
    }

    /** @return the public {@link LivingEntity} method by that name, or null where it is absent */
    private static Method find(final String name, final Class<?>... parameters)
    {
        try
        {
            return LivingEntity.class.getMethod(name, parameters);
        }
        catch (final NoSuchMethodException | RuntimeException | LinkageError e)
        {
            return null;
        }
    }
}
