package com.wormhole_xtreme.wormhole.model.ping;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
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

    /** How an attribute is found by id; a seam, since the registry needs a running server from 1.20.6. */
    private static Function<String, Attribute> lookup = Waypoints::fromRegistry;

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
        return lookup.apply(TRANSMIT_KEY);
    }

    /**
     * Whether this server has a locator bar to ping on at all.
     *
     * @return true from Minecraft 1.21.6
     */
    public static boolean isSupported()
    {
        return transmitRange() != null;
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
     * Replaces how attributes are found, for a test with no server.
     *
     * @param stand
     *            the lookup to use, or null for the registry again
     */
    static void lookupWith(final Function<String, Attribute> stand)
    {
        lookup = (stand == null) ? Waypoints::fromRegistry : stand;
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
