package com.wormhole_xtreme.wormhole.model.ping;

import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;

/** Lets a test in another package stand in for the locator bar and the marker spawn. */
public final class PingTestSupport
{
    private PingTestSupport()
    {
    }

    /**
     * Gives the server a locator bar, or takes it away.
     *
     * @param hasBar
     *            whether it has one, as from 1.21.6
     * @param attributeOf
     *            each entity's transmit range attribute
     */
    public static void locatorBar(final boolean hasBar, final Function<LivingEntity, AttributeInstance> attributeOf)
    {
        Waypoints.standIn(() -> hasBar, attributeOf);
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

    /** Forgets every ping and cooldown, and puts the real locator bar and spawner back. */
    public static void reset()
    {
        PingMarkers.reset();
        Waypoints.standIn(null, null);
    }
}
