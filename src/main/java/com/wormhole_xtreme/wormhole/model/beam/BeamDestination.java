package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * One named point a player can beam to — a public destination or a private place.
 *
 * <p>Unlike a gate or a ring, a beam destination is a single point rather than a pair, and it
 * is not tied to any physical structure. Which registry it lives in ({@link BeamManager}'s
 * public map, or a player's own private one) is what makes it public or private; the
 * destination itself carries no access field of its own.
 */
public final class BeamDestination
{
    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public BeamDestination(final String name, final String worldName, final double x, final double y,
        final double z, final float yaw, final float pitch)
    {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static BeamDestination fromLocation(final String name, final Location location)
    {
        return new BeamDestination(name, location.getWorld().getName(), location.getX(), location.getY(),
            location.getZ(), location.getYaw(), location.getPitch());
    }

    public String getName() { return name; }

    public String getWorldName() { return worldName; }

    public double getX() { return x; }

    public double getY() { return y; }

    public double getZ() { return z; }

    public float getYaw() { return yaw; }

    public float getPitch() { return pitch; }

    /**
     * Resolves this destination to a live {@link Location}.
     *
     * @return the location, or null if the world it was recorded in is not currently loaded
     */
    public Location toLocation()
    {
        final World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }
}
