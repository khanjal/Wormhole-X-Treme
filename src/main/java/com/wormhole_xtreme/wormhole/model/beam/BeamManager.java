package com.wormhole_xtreme.wormhole.model.beam;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of beam destinations: an admin-curated public set, and one private set of
 * places per player.
 *
 * <p>Static, like {@link com.wormhole_xtreme.wormhole.model.StargateManager} and
 * {@link com.wormhole_xtreme.wormhole.model.ring.RingManager} — there is exactly one of this
 * per server. Persistence is {@link BeamYamlManager}'s job; this class only holds what is
 * currently loaded.
 */
public final class BeamManager
{
    private static final Map<String, BeamDestination> PUBLIC = new ConcurrentHashMap<String, BeamDestination>();
    private static final Map<UUID, Map<String, BeamDestination>> PLACES =
        new ConcurrentHashMap<UUID, Map<String, BeamDestination>>();

    private BeamManager() {}

    // -----------------------------------------------------------------------
    // Public destinations
    // -----------------------------------------------------------------------

    public static void setPublicDestination(final BeamDestination destination)
    {
        PUBLIC.put(destination.getName().toLowerCase(), destination);
    }

    public static BeamDestination getPublicDestination(final String name)
    {
        return name == null ? null : PUBLIC.get(name.toLowerCase());
    }

    public static boolean removePublicDestination(final String name)
    {
        return name != null && PUBLIC.remove(name.toLowerCase()) != null;
    }

    public static Collection<BeamDestination> getAllPublicDestinations()
    {
        return Collections.unmodifiableCollection(PUBLIC.values());
    }

    // -----------------------------------------------------------------------
    // Private places
    // -----------------------------------------------------------------------

    public static void setPlace(final UUID owner, final BeamDestination place)
    {
        PLACES.computeIfAbsent(owner, k -> new ConcurrentHashMap<String, BeamDestination>())
            .put(place.getName().toLowerCase(), place);
    }

    public static BeamDestination getPlace(final UUID owner, final String name)
    {
        if (owner == null || name == null)
        {
            return null;
        }
        final Map<String, BeamDestination> places = PLACES.get(owner);
        return places == null ? null : places.get(name.toLowerCase());
    }

    public static boolean removePlace(final UUID owner, final String name)
    {
        if (owner == null || name == null)
        {
            return false;
        }
        final Map<String, BeamDestination> places = PLACES.get(owner);
        return places != null && places.remove(name.toLowerCase()) != null;
    }

    public static Collection<BeamDestination> getPlaces(final UUID owner)
    {
        final Map<String, BeamDestination> places = PLACES.get(owner);
        return places == null ? Collections.<BeamDestination>emptyList()
            : Collections.unmodifiableCollection(places.values());
    }

    public static Map<UUID, Map<String, BeamDestination>> getAllPlaces()
    {
        return Collections.unmodifiableMap(PLACES);
    }

    /** Clears everything loaded. Used on reload and in tests. */
    public static void clear()
    {
        PUBLIC.clear();
        PLACES.clear();
    }
}
