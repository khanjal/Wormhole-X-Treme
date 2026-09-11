package com.wormhole_xtreme.wormhole.model.mirror;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A spot in a named world, recorded so it outlives the world being loaded.
 *
 * <p>The same shape {@code BeamPoint} uses, and for the same reason: a Bukkit
 * {@link Location} holds a live {@link World}, which cannot be built for a world the server
 * has not loaded, and these are read off disk during startup before every world exists.
 * Keeping the name and resolving it on demand is what lets a mirror pointing into an unloaded
 * world sit in the registry and simply refuse to travel, rather than failing to load at all.
 *
 * <p>Deliberately a separate type rather than a shared one. Mirrors are a standalone mechanic
 * with their own registry and their own file, the way gates, rings and beam already are to
 * each other, and a shared point type would be the first thread tying two of them together.
 * It is twenty lines; the coupling would cost more than the duplication.
 *
 * <p>Resolution is by world <em>name</em>, not UUID, which matches every other store in this
 * plugin. A world recreated under a different name orphans anything bound to the old one --
 * the mirror stays in the registry and refuses with "that world is not loaded", which is the
 * same answer beam gives.
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
public record MirrorPoint(String worldName, double x, double y, double z, float yaw, float pitch)
{
    /**
     * The spot a live location stands at.
     *
     * @param location
     *            the location to record
     * @return the same spot, holding the world's name rather than the world
     */
    public static MirrorPoint of(final Location location)
    {
        return new MirrorPoint(location.getWorld().getName(), location.getX(), location.getY(),
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
        return (world == null) ? null : new Location(world, x, y, z, yaw, pitch);
    }
}
