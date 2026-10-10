package com.wormhole_xtreme.wormhole.model.window;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;

/**
 * The stand-ins a view shows for the creatures in its far room (#296): real entities in the
 * viewer's world, which only that viewer is shown.
 */
public final class StandIns
{
    /** Every stand-in alive, by its own id, so anything can tell one from a real creature. */
    private static final Map<UUID, Entity> ALL = new ConcurrentHashMap<>();

    /** Static state only. */
    private StandIns()
    {
    }

    /**
     * Whether an entity is one of the stand-ins a view is showing, by identity.
     *
     * @param entity
     *            any entity
     * @return true for a stand-in
     */
    public static boolean isStandIn(final Entity entity)
    {
        final UUID id = (entity == null) ? null : entity.getUniqueId();
        return (id != null) && (ALL.get(id) == entity);
    }

    /** Notes a stand-in just spawned. */
    static void track(final Entity standIn)
    {
        ALL.put(standIn.getUniqueId(), standIn);
    }

    /** Forgets one that has gone. */
    static void untrack(final Entity standIn)
    {
        ALL.remove(standIn.getUniqueId(), standIn);
    }
}
