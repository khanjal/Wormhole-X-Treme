package com.wormhole_xtreme.wormhole.utils;

import java.lang.reflect.Method;
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
 * and 1.20.1, which have no {@code createEntity}, it is spawned and hidden straight after in the
 * same tick. The two methods are looked up by reflection, since CI compiles against 1.20 too.
 */
public final class HiddenEntities
{
    /** Making an entity without adding it, and adding it later. */
    public interface Creation
    {
        /**
         * @param world
         *            where
         * @param at
         *            the place
         * @param type
         *            what
         * @param <T>
         *            the entity type
         * @return the entity, not yet in the world, or null if it could not be made
         */
        <T extends Entity> T create(World world, Location at, Class<T> type);

        /**
         * @param world
         *            where
         * @param entity
         *            one {@link #create} made
         * @param <T>
         *            the entity type
         * @return the entity, now in the world unless something refused it
         */
        <T extends Entity> T add(World world, T entity);
    }

    /** {@code RegionAccessor.createEntity}, or null on a server without it. */
    private static final Method CREATE = find("createEntity", Location.class, Class.class);

    /** {@code RegionAccessor.addEntity}, or null on a server without it. */
    private static final Method ADD = find("addEntity", Entity.class);

    /** The server's own, or whatever a test installed; null where the API is absent. */
    private static Creation creation = reflective();

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
    // A spawn that answers nothing is a tested contract here, whatever the annotation says.
    @SuppressWarnings("java:S2589")
    public static <T extends Entity> T spawnFor(final Plugin plugin, final Player viewer, final Location at,
        final Class<T> type, final Consumer<? super T> setup)
    {
        final World world = at.getWorld();
        T entity = (creation == null) ? null : creation.create(world, at, type);
        if (entity != null)
        {
            prepare(entity, setup);
            entity = creation.add(world, entity);
        }
        else
        {
            entity = world.spawn(at, type);
            if (entity != null)
            {
                prepare(entity, setup);
            }
        }
        if ((entity == null) || !entity.isValid())
        {
            // A protection plugin cancelled the spawn; there is nothing to show or remove.
            return null;
        }
        viewer.showEntity(plugin, entity);
        return entity;
    }

    /** @return true if this server can make an entity before adding it */
    public static boolean canCreateBeforeAdding()
    {
        return creation != null;
    }

    /**
     * Uses this instead of the server's own methods, for a test: the API on the compile path may
     * be 1.20's, which has neither.
     *
     * @param stand
     *            what to make and add entities through, or null to go back to the server's own
     */
    public static void creationWith(final Creation stand)
    {
        creation = (stand == null) ? reflective() : stand;
    }

    private static <T extends Entity> void prepare(final T entity, final Consumer<? super T> setup)
    {
        entity.setPersistent(false);
        entity.setVisibleByDefault(false);
        setup.accept(entity);
    }

    /** The server's own methods, or null where this server has none. */
    private static Creation reflective()
    {
        if ((CREATE == null) || (ADD == null))
        {
            return null;
        }
        return new Creation()
        {
            @Override
            public <T extends Entity> T create(final World world, final Location at, final Class<T> type)
            {
                try
                {
                    return type.cast(CREATE.invoke(world, at, type));
                }
                catch (final ReflectiveOperationException | RuntimeException | LinkageError notMade)
                {
                    // Spawned the older way instead.
                    return null;
                }
            }

            @Override
            public <T extends Entity> T add(final World world, final T entity)
            {
                try
                {
                    ADD.invoke(world, entity);
                }
                catch (final ReflectiveOperationException | RuntimeException | LinkageError notAdded)
                {
                    // Not in the world, so isValid is false and nothing is shown.
                }
                return entity;
            }
        };
    }

    private static Method find(final String name, final Class<?>... parameters)
    {
        try
        {
            return World.class.getMethod(name, parameters);
        }
        catch (final NoSuchMethodException | RuntimeException | LinkageError absent)
        {
            return null;
        }
    }
}
