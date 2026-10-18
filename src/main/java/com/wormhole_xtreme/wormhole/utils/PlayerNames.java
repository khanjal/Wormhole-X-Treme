package com.wormhole_xtreme.wormhole.utils;

import java.lang.reflect.Method;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * A player's display name, from Adventure's component on Paper and Bukkit's string elsewhere, in
 * legacy colour codes either way.
 *
 * <p>Paper deprecates {@code getDisplayName()} for {@code displayName()}, which Spigot does not
 * have, so that is looked up by reflection.
 */
public final class PlayerNames
{
    /** Paper's {@code displayName()}, or null where the server lacks it. */
    record Lookup(Method get)
    {
    }

    /** Paper's, or whatever a test installed. */
    private static Lookup lookup = PaperNames.find();

    /** Static use only. */
    private PlayerNames()
    {
    }

    /**
     * @param player
     *            whose name
     * @return their display name, in legacy colour codes
     */
    public static String display(final Player player)
    {
        final Lookup found = lookup;
        if (found != null)
        {
            try
            {
                final Object name = found.get().invoke(player);
                if (name != null)
                {
                    return PaperNames.legacy(name);
                }
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError refused)
            {
                // Read the Bukkit way instead.
            }
        }
        return displayLegacy(player);
    }

    /**
     * Tests only.
     *
     * @param stand
     *            the method to read through, or null for none; {@link #forget()} restores Paper's
     */
    static void lookupWith(final Lookup stand)
    {
        lookup = stand;
    }

    /** Tests only: goes back to the server's own lookup. */
    static void forget()
    {
        lookup = PaperNames.find();
    }

    // Spigot has no other display name, and Paper deprecates this one.
    @SuppressWarnings("deprecation")
    private static String displayLegacy(final Player player)
    {
        return player.getDisplayName();
    }

    /**
     * The only code naming Adventure, so a server without it fails to link this class inside a
     * try, and never {@link PlayerNames} in its caller.
     */
    private static final class PaperNames
    {
        /** Static use only. */
        private PaperNames()
        {
        }

        /** Holds the serializer apart, so building it waits for the first name, inside a try. */
        private static final class Legacy
        {
            /** Bukkit's own legacy form, hex colours included, which plain legacySection() loses. */
            static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
                .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat()
                .build();

            /** Static use only. */
            private Legacy()
            {
            }
        }

        /** @return Paper's method, or null on a server without it */
        static Lookup find()
        {
            try
            {
                return new Lookup(Player.class.getMethod("displayName"));
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError notPaper)
            {
                return null;
            }
        }

        /**
         * @param component
         *            a name as Paper holds it
         * @return its text in legacy colour codes
         */
        static String legacy(final Object component)
        {
            return Legacy.SECTION.serialize((Component) component);
        }
    }
}
