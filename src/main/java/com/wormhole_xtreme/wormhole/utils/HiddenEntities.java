package com.wormhole_xtreme.wormhole.utils;

import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Entities that exist for one player: hidden from everybody else, and never saved.
 *
 * <p>From 1.20.2 an entity is made, hidden, then added, so nobody else is ever sent it. On 1.20
 * and 1.20.1, which lack {@code createEntity}, it is spawned and hidden straight after in the
 * same tick.
 */
public final class HiddenEntities
{
    /** False once {@code RegionAccessor.createEntity} has been found missing. */
    private static volatile boolean canCreate = true;

    private HiddenEntities() {}

    /**
     * Spawns an entity only one player can see.
     *
     * @param plugin
     *            the plugin showing it
     * @param viewer
     *            who sees it
     * @param at
     *            where, in a loaded chunk
     * @param type
     *            what
     * @param setup
     *            anything else to set before it is added
     * @param <T>
     *            the entity type
     * @return the entity, or null if the world refused it
     */
    public static <T extends Entity> T spawnFor(final Plugin plugin, final Player viewer, final Location at,
        final Class<T> type, final Consumer<? super T> setup)
    {
        final World world = at.getWorld();
        T entity = canCreate ? created(world, at, type, setup) : null;
        if (!canCreate)
        {
            entity = world.spawn(at, type);
            prepare(entity, setup);
        }
        if ((entity == null) || !entity.isValid())
        {
            // A protection plugin cancelled the spawn; there is nothing to show or remove.
            return null;
        }
        viewer.showEntity(plugin, entity);
        return entity;
    }

    private static <T extends Entity> T created(final World world, final Location at, final Class<T> type,
        final Consumer<? super T> setup)
    {
        try
        {
            final T entity = world.createEntity(at, type);
            prepare(entity, setup);
            return world.addEntity(entity);
        }
        catch (final NoSuchMethodError | AbstractMethodError missing)
        {
            canCreate = false;
            return null;
        }
    }

    private static <T extends Entity> void prepare(final T entity, final Consumer<? super T> setup)
    {
        entity.setPersistent(false);
        entity.setVisibleByDefault(false);
        setup.accept(entity);
    }

    /** Forgets what was learnt about the server, for tests. */
    static void reset()
    {
        canCreate = true;
    }
}
