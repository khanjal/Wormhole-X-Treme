package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Location;

/**
 * One named point a player can beam to — a public destination or a private place.
 *
 * <p>Unlike a gate or a ring, a beam destination is a single point rather than a pair, and it
 * is not tied to any physical structure. Which registry it lives in ({@link BeamManager}'s
 * public map, or a player's own private one) is what makes it public or private; the
 * destination itself carries no access field of its own.
 *
 * <p>{@link #cost()} exists only in practice for public destinations. A private place
 * setting its own cost would just be its owner choosing what to pay themselves, since a
 * place is only ever reachable by the player who made it -- {@code BeamCommand} never
 * exposes a way to set it on one, so a place's cost stays {@code null} (inherit the global
 * default) for the whole of its life. It lives here rather than on a public-only subtype so
 * {@link BeamManager} and {@link BeamYamlManager} do not need to know which kind of
 * destination they are holding.
 *
 * @param name
 *            what the destination is called, and its key in whichever registry holds it
 * @param point
 *            where it is
 * @param cost
 *            this destination's own price, or null to mean "whatever
 *            {@code BEAM_ECONOMY_USE_COST} says" -- null and zero are different things: zero is
 *            an explicit, permanent "this one is free" that a later change to the global
 *            default cannot override
 */
public record BeamDestination(String name, BeamPoint point, Double cost)
{
    /** A destination with no cost override -- {@link #cost()} will be null, meaning whoever
     * travels to it pays whatever {@code BEAM_ECONOMY_USE_COST} currently says.
     *
     * @param name
     *            what to call it
     * @param location
     *            where it is
     * @return the destination */
    public static BeamDestination fromLocation(final String name, final Location location)
    {
        return new BeamDestination(name, BeamPoint.of(location), null);
    }

    /**
     * The same destination with a different cost override.
     *
     * @param newCost the override, or null to go back to inheriting the global default
     * @return a new instance; this one is unchanged
     */
    public BeamDestination withCost(final Double newCost)
    {
        return new BeamDestination(name, point, newCost);
    }

    /**
     * Resolves this destination to a live {@link Location}.
     *
     * @return the location, or null if the world it was recorded in is not currently loaded
     */
    public Location toLocation()
    {
        return point.toLocation();
    }
}
