package com.wormhole_xtreme.wormhole.plugin;

import org.bukkit.Location;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * What each placeholder resolves to, as plain values.
 *
 * <p>Separate from {@link WormholePlaceholders} on purpose. That class extends a
 * PlaceholderAPI type and so cannot be loaded at all unless PlaceholderAPI is present;
 * everything actually worth testing lives here instead, where a test needs neither
 * PlaceholderAPI nor a server.
 *
 * <p>A placeholder is resolved every time a scoreboard, tab list or chat line is rebuilt,
 * which on a busy server is several times a second per player. Nothing here sorts, allocates
 * a copy of the gate list, or logs.
 */
public final class PlaceholderValues
{
    /** Returned for a placeholder this expansion owns but cannot answer right now. */
    static final String UNAVAILABLE = "";

    /** Static helpers only. */
    private PlaceholderValues()
    {
    }

    /**
     * Resolves one placeholder.
     *
     * @param params
     *            what followed {@code %wormhole_}, already lowercased by PlaceholderAPI
     * @param ownerId
     *            the asking player's UUID as a string, or null
     * @param ownerName
     *            the asking player's name, or null
     * @param location
     *            where the asking player is, or null if they are not online
     * @return the value, or null if this expansion does not own that placeholder
     */
    public static String resolve(final String params, final String ownerId,
                                 final String ownerName, final Location location)
    {
        if (params == null)
        {
            return null;
        }
        switch (params.toLowerCase(java.util.Locale.ROOT))
        {
            case "gates_total":
                return Integer.toString(gatesTotal());
            case "gates_open":
                return Integer.toString(gatesOpen());
            case "gates_owned":
                return Integer.toString(gatesOwnedBy(ownerId, ownerName));
            case "nearest_gate":
                return nearestGateName(location);
            default:
                // Null, not empty: PlaceholderAPI leaves a placeholder it is not given a
                // value for exactly as it found it, which is how an operator sees they have
                // typed one this plugin does not have rather than an empty line.
                return null;
        }
    }

    /**
     * How many gates exist.
     *
     * @return the count
     */
    public static int gatesTotal()
    {
        return StargateManager.getAllGatesUnsorted().size();
    }

    /**
     * How many gates have an open wormhole.
     *
     * @return the count
     */
    public static int gatesOpen()
    {
        return StargateManager.getOpenGates().size();
    }

    /**
     * How many gates a player owns.
     *
     * <p>Matches on the UUID <em>or</em> the name. A gate records its owner as a UUID string
     * when it is built and as a plain name on anything built before that changed, so a server
     * carrying gates from both eras would report a player owning none of their older ones if
     * this only compared one of the two.
     *
     * @param ownerId
     *            the player's UUID as a string, or null
     * @param ownerName
     *            the player's name, or null
     * @return how many gates name them as owner
     */
    public static int gatesOwnedBy(final String ownerId, final String ownerName)
    {
        if ((ownerId == null) && (ownerName == null))
        {
            return 0;
        }
        int owned = 0;
        for (final Stargate gate : StargateManager.getAllGatesUnsorted())
        {
            final String owner = gate.getGateOwner();
            if (owner == null)
            {
                continue;
            }
            if (owner.equals(ownerId) || owner.equalsIgnoreCase(ownerName))
            {
                owned++;
            }
        }
        return owned;
    }

    /**
     * The name of the gate nearest a place.
     *
     * @param location
     *            where to measure from, or null if the player is not online
     * @return the gate's name, or {@link #UNAVAILABLE} when there is nowhere to measure from
     *         or no gate to find
     */
    public static String nearestGateName(final Location location)
    {
        if (location == null)
        {
            return UNAVAILABLE;
        }
        final Stargate closest = StargateManager.findClosestStargate(location);
        if ((closest == null) || (closest.getGateName() == null))
        {
            return UNAVAILABLE;
        }
        return closest.getGateName();
    }
}
