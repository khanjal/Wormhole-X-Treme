package com.wormhole_xtreme.wormhole.model.ping;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

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

    /**
     * Puts up one ping, with mocks standing in for the world, its chunk, the player and the marker.
     * A scheduler must be installed for its expiry.
     *
     * @return the marker
     */
    public static ArmorStand placeOne()
    {
        final World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        final Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
        final ArmorStand stand = mock(ArmorStand.class);
        final AttributeInstance range = mock(AttributeInstance.class);
        when(range.getValue()).thenReturn(Waypoints.TRANSMIT_RANGE);
        locatorBar(true, entity -> (entity == stand) ? range : null);
        spawnWith(at -> stand);
        final Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        if (PingMarkers.place(owner, new Location(world, 0, 64, 0)) != PingMarkers.Outcome.PLACED)
        {
            throw new IllegalStateException("the stand-in ping did not go up");
        }
        return stand;
    }

    /**
     * How many pings are up.
     *
     * @return the count
     */
    public static int live()
    {
        return PingMarkers.live();
    }

    /** Forgets every ping and cooldown, and puts the real locator bar and spawner back. */
    public static void reset()
    {
        PingMarkers.reset();
        Waypoints.standIn(null, null);
    }
}
