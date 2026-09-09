package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A spot in a named world, recorded so it outlives the world being loaded.
 *
 * <p>Deliberately not a Bukkit {@link Location}, which holds a live {@link World} reference: one
 * cannot be built for a world the server has not loaded yet, and beam destinations are read off
 * disk during startup, before every world exists. Keeping the name and resolving it on demand is
 * what lets a destination in an unloaded world sit in the registry and simply refuse to travel,
 * rather than failing to load at all.
 *
 * @param worldName
 *            the world's name, which may not currently be loaded
 * @param x
 *            the x coordinate
 * @param y
 *            the y coordinate
 * @param z
 *            the z coordinate
 * @param yaw
 *            which way an arriving player faces
 * @param pitch
 *            how far up or down an arriving player looks
 */
public record BeamPoint(String worldName, double x, double y, double z, float yaw, float pitch)
{
    /**
     * The spot a live location stands at.
     *
     * @param location
     *            the location to record
     * @return the same spot, holding the world's name rather than the world
     */
    public static BeamPoint of(final Location location)
    {
        return new BeamPoint(location.getWorld().getName(), location.getX(), location.getY(),
            location.getZ(), location.getYaw(), location.getPitch());
    }

    /**
     * Resolves back to a live location.
     *
     * @return the location, or null if the world it was recorded in is not currently loaded
     */
    public Location toLocation()
    {
        final World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }
}
