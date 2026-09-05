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
 *
 * <p>{@link #getCost()} exists only in practice for public destinations. A private place
 * setting its own cost would just be its owner choosing what to pay themselves, since a
 * place is only ever reachable by the player who made it -- {@code BeamCommand} never
 * exposes a way to set it on one, so a place's cost stays {@code null} (inherit the global
 * default) for the whole of its life. It lives here rather than on a public-only subtype so
 * {@link BeamManager} and {@link BeamYamlManager} do not need to know which kind of
 * destination they are holding.
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
    private final Double cost;

    public BeamDestination(final String name, final String worldName, final double x, final double y,
        final double z, final float yaw, final float pitch, final Double cost)
    {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.cost = cost;
    }

    /** A destination with no cost override -- {@link #getCost()} will return null, meaning
     * whoever travels to it pays whatever {@code BEAM_ECONOMY_USE_COST} currently says. */
    public static BeamDestination fromLocation(final String name, final Location location)
    {
        return new BeamDestination(name, location.getWorld().getName(), location.getX(), location.getY(),
            location.getZ(), location.getYaw(), location.getPitch(), null);
    }

    /**
     * The same destination with a different cost override.
     *
     * @param newCost the override, or null to go back to inheriting the global default
     * @return a new instance; this one is unchanged
     */
    public BeamDestination withCost(final Double newCost)
    {
        return new BeamDestination(name, worldName, x, y, z, yaw, pitch, newCost);
    }

    public String getName() { return name; }

    public String getWorldName() { return worldName; }

    public double getX() { return x; }

    public double getY() { return y; }

    public double getZ() { return z; }

    public float getYaw() { return yaw; }

    public float getPitch() { return pitch; }

    /**
     * This destination's own cost, if it has one.
     *
     * @return the override, or null to mean "whatever {@code BEAM_ECONOMY_USE_COST} says" --
     *         null and zero are different things: zero is an explicit, permanent "this one is
     *         free" that a later change to the global default cannot override
     */
    public Double getCost() { return cost; }

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
