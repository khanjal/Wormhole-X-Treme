package com.wormhole_xtreme.wormhole.model.ping;

import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;

/** Lets a test in another package stand in for the locator bar and the marker spawn. */
public final class PingTestSupport
{
    private PingTestSupport()
    {
    }

    /**
     * Gives the server a locator bar, or takes it away.
     *
     * @param transmit
     *            the transmit range attribute, or null for a server before 1.21.6
     */
    public static void locatorBar(final Attribute transmit)
    {
        Waypoints.lookupWith(key -> Waypoints.TRANSMIT_KEY.equals(key) ? transmit : null);
    }

    /**
     * Spawns markers with this instead of the world.
     *
     * @param spawn
     *            the spawner
     */
    public static void spawnWith(final Function<Location, ArmorStand> spawn)
    {
        PingMarkers.spawnWith(spawn::apply);
    }

    /** Forgets every ping and cooldown, and puts the real registry lookup and spawner back. */
    public static void reset()
    {
        PingMarkers.reset();
        Waypoints.lookupWith(null);
    }
}
