package com.wormhole_xtreme.wormhole.utils;

import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.bukkit.entity.Entity;
import org.mockito.invocation.Invocation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Reads and stubs a mock entity's name through whichever method this API build names it with.
 *
 * <p>On Paper's API {@link EntityNames} calls {@code customName(Component)}, never
 * {@code setCustomName}, so a test verifying only Spigot's setter would fail on the Paper legs.
 */
public final class ShownNames
{
    /** Static use only. */
    private ShownNames()
    {
    }

    /**
     * @param entity
     *            the mock named
     * @return the last name it was given, in legacy colour codes, or null if it was given none
     */
    public static String of(final Entity entity)
    {
        String name = null;
        for (final Invocation call : mockingDetails(entity).getInvocations())
        {
            final String method = call.getMethod().getName();
            if ("setCustomName".equals(method))
            {
                name = call.getArgument(0);
            }
            else if ("customName".equals(method) && call.getArguments().length == 1)
            {
                final Component given = call.getArgument(0);
                name = given == null ? null : LegacyComponentSerializer.legacySection().serialize(given);
            }
        }
        return name;
    }

    /**
     * Gives a mock the name {@link EntityNames#copy} reads from it on this API build.
     *
     * @param entity
     *            the mock
     * @param legacyText
     *            its name, in legacy colour codes
     * @throws ReflectiveOperationException
     *             never, on an API build that has the getter it found
     */
    public static void give(final Entity entity, final String legacyText) throws ReflectiveOperationException
    {
        final Method paper = paperGetter();
        if (paper == null)
        {
            when(entity.getCustomName()).thenReturn(legacyText);
        }
        else
        {
            when(paper.invoke(entity)).thenReturn(LegacyComponentSerializer.legacySection().deserialize(legacyText));
        }
    }

    /** @return Paper's {@code Entity.customName()}, or null on Spigot's API */
    private static Method paperGetter()
    {
        try
        {
            return Entity.class.getMethod("customName");
        }
        catch (final NoSuchMethodException spigot)
        {
            return null;
        }
    }
}
