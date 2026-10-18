package com.wormhole_xtreme.wormhole.utils;

import java.lang.reflect.Method;

import org.bukkit.entity.Entity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * The name shown over an entity, as Adventure's component on Paper and Bukkit's string elsewhere.
 *
 * <p>Paper deprecates {@code setCustomName(String)} for {@code customName(Component)}, which Spigot
 * does not have, so that is looked up by reflection. A name is cosmetic: one the server refuses
 * leaves the entity unnamed, never unspawned.
 */
public final class EntityNames
{
    /** Paper's {@code customName(Component)} and {@code customName()}, or null where the server has neither. */
    record Lookup(Method set, Method get)
    {
    }

    /** Paper's, or whatever a test installed. */
    private static Lookup lookup = PaperNames.find();

    /** Static use only. */
    private EntityNames()
    {
    }

    /**
     * @param entity
     *            what to name
     * @param legacyText
     *            the name, in legacy colour codes
     */
    public static void set(final Entity entity, final String legacyText)
    {
        final Lookup found = lookup;
        if (found != null)
        {
            try
            {
                found.set().invoke(entity, PaperNames.component(legacyText));
                return;
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError refused)
            {
                // Named the Bukkit way instead.
            }
        }
        try
        {
            setLegacy(entity, legacyText);
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent -- see the class comment
        }
    }

    /**
     * Gives one entity another's name, colours and all.
     *
     * @param from
     *            whose name
     * @param to
     *            who takes it
     */
    public static void copy(final Entity from, final Entity to)
    {
        final Lookup found = lookup;
        if (found != null)
        {
            try
            {
                found.set().invoke(to, found.get().invoke(from));
                return;
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError refused)
            {
                // Copied the Bukkit way instead.
            }
        }
        try
        {
            copyLegacy(from, to);
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent -- see the class comment
        }
    }

    /**
     * Tests only.
     *
     * @param stand
     *            the methods to name through, or null for none; {@link #forget()} restores Paper's
     */
    static void lookupWith(final Lookup stand)
    {
        lookup = stand;
    }

    /** Tests only: goes back to the server's own lookups. */
    static void forget()
    {
        lookup = PaperNames.find();
    }

    // Spigot has no other custom name, and Paper deprecates this one.
    @SuppressWarnings("deprecation")
    private static void setLegacy(final Entity entity, final String legacyText)
    {
        entity.setCustomName(legacyText);
    }

    // Spigot has no other custom name, and Paper deprecates this one.
    @SuppressWarnings("deprecation")
    private static void copyLegacy(final Entity from, final Entity to)
    {
        to.setCustomName(from.getCustomName());
    }

    /**
     * The only code naming Adventure, so a server without it fails to link this class inside a
     * try, and never {@link EntityNames} in its caller.
     */
    private static final class PaperNames
    {
        /** Static use only. */
        private PaperNames()
        {
        }

        /** @return Paper's methods, or null on a server without them */
        static Lookup find()
        {
            try
            {
                return new Lookup(Entity.class.getMethod("customName", Component.class),
                    Entity.class.getMethod("customName"));
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError notPaper)
            {
                return null;
            }
        }

        /**
         * @param legacyText
         *            text in legacy colour codes
         * @return it as a component; Object, so no caller's signature names Adventure
         */
        static Object component(final String legacyText)
        {
            return legacyText == null ? null : LegacyComponentSerializer.legacySection().deserialize(legacyText);
        }
    }
}
