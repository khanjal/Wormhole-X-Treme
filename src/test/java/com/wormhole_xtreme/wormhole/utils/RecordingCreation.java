package com.wormhole_xtreme.wormhole.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.mockito.Mockito;

/**
 * Stands in for {@code createEntity} and {@code addEntity}, which 1.20's API does not have, and
 * notes what each entity looked like when it was added.
 */
public final class RecordingCreation implements HiddenEntities.Creation
{
    /** Every place something was made, in order. */
    public final List<Location> places = new ArrayList<>();

    /** Every entity made, in order. */
    public final List<Entity> created = new ArrayList<>();

    /** For each entity added, whether it was already unsaved and hidden by default. */
    public final List<Boolean> hiddenWhenAdded = new ArrayList<>();

    private final Function<Class<?>, Entity> make;

    /**
     * @param make
     *            what to hand back for a type, usually a mock
     */
    public RecordingCreation(final Function<Class<?>, Entity> make)
    {
        this.make = make;
    }

    @Override
    public <T extends Entity> T create(final World world, final Location at, final Class<T> type)
    {
        final T entity = type.cast(make.apply(type));
        places.add(at);
        created.add(entity);
        return entity;
    }

    @Override
    public <T extends Entity> T add(final World world, final T entity)
    {
        hiddenWhenAdded.add(called(entity, "setPersistent") && called(entity, "setVisibleByDefault"));
        return entity;
    }

    /** Whether a mock had a setter called with false before now. */
    private static boolean called(final Entity entity, final String setter)
    {
        return Mockito.mockingDetails(entity).getInvocations().stream()
            .anyMatch(i -> setter.equals(i.getMethod().getName()) && Boolean.FALSE.equals(i.getArgument(0)));
    }
}
