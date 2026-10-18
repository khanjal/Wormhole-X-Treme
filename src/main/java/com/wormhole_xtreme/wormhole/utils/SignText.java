package com.wormhole_xtreme.wormhole.utils;

import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.block.sign.SignSide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * A sign's lines, as Adventure's components on Paper and Bukkit's strings elsewhere, in legacy
 * colour codes either way.
 *
 * <p>Paper deprecates {@code setLine}, {@code getLine} and {@code getLines} for {@code line} and
 * {@code lines}, which Spigot does not have, so those are looked up by reflection. A Paper call
 * that fails is made again the Bukkit way, so no sign update is lost to the new path.
 */
public final class SignText
{
    /** Paper's {@code line(int, Component)}, {@code line(int)} and {@code lines()}, or null where the server lacks them. */
    record Lookup(Method set, Method get, Method lines)
    {
    }

    /** Paper's, or whatever a test installed. */
    private static Lookup lookup = PaperLines.find();

    /** Static use only. */
    private SignText()
    {
    }

    /**
     * @param side
     *            the face written
     * @param line
     *            which line, 0 to 3
     * @param legacyText
     *            the text, in legacy colour codes
     */
    public static void set(final SignSide side, final int line, final String legacyText)
    {
        final Lookup found = lookup;
        if (found != null)
        {
            try
            {
                found.set().invoke(side, line, PaperLines.component(legacyText));
                return;
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError refused)
            {
                // Written the Bukkit way instead.
            }
        }
        setLegacy(side, line, legacyText);
    }

    /**
     * @param side
     *            the face read
     * @param line
     *            which line, 0 to 3
     * @return its text, in legacy colour codes
     */
    public static String get(final SignSide side, final int line)
    {
        final Lookup found = lookup;
        if (found != null)
        {
            try
            {
                return PaperLines.legacy(found.get().invoke(side, line));
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError refused)
            {
                // Read the Bukkit way instead.
            }
        }
        return getLegacy(side, line);
    }

    /**
     * @param side
     *            the face read
     * @return every line's text, in legacy colour codes
     */
    public static String[] lines(final SignSide side)
    {
        final Lookup found = lookup;
        if (found != null)
        {
            try
            {
                return PaperLines.legacyLines((List<?>) found.lines().invoke(side));
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError refused)
            {
                // Read the Bukkit way instead.
            }
        }
        return linesLegacy(side);
    }

    /**
     * Tests only.
     *
     * @param stand
     *            the methods to go through, or null for none; {@link #forget()} restores Paper's
     */
    static void lookupWith(final Lookup stand)
    {
        lookup = stand;
    }

    /** Tests only: goes back to the server's own lookups. */
    static void forget()
    {
        lookup = PaperLines.find();
    }

    // Spigot has no other sign text, and Paper deprecates this one.
    @SuppressWarnings("deprecation")
    private static void setLegacy(final SignSide side, final int line, final String legacyText)
    {
        side.setLine(line, legacyText);
    }

    // Spigot has no other sign text, and Paper deprecates this one.
    @SuppressWarnings("deprecation")
    private static String getLegacy(final SignSide side, final int line)
    {
        return side.getLine(line);
    }

    // Spigot has no other sign text, and Paper deprecates this one.
    @SuppressWarnings("deprecation")
    private static String[] linesLegacy(final SignSide side)
    {
        return side.getLines();
    }

    /**
     * The only code naming Adventure, so a server without it fails to link this class inside a
     * try, and never {@link SignText} in its caller.
     */
    private static final class PaperLines
    {
        /** Static use only. */
        private PaperLines()
        {
        }

        /** Holds the serializer apart, so building it waits for the first line, inside a try. */
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

        /** @return Paper's methods, or null on a server without them */
        static Lookup find()
        {
            try
            {
                return new Lookup(SignSide.class.getMethod("line", int.class, Component.class),
                    SignSide.class.getMethod("line", int.class), SignSide.class.getMethod("lines"));
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
            return legacyText == null ? null : Legacy.SECTION.deserialize(legacyText);
        }

        /**
         * @param component
         *            a line as Paper holds it
         * @return its text in legacy colour codes, or null for none
         */
        static String legacy(final Object component)
        {
            return component == null ? null : Legacy.SECTION.serialize((Component) component);
        }

        /**
         * @param components
         *            the lines as Paper holds them
         * @return each one's text in legacy colour codes, or none
         */
        static String[] legacyLines(final List<?> components)
        {
            if (components == null)
            {
                return new String[0];
            }
            final String[] lines = new String[components.size()];
            for (int i = 0; i < lines.length; i++)
            {
                lines[i] = legacy(components.get(i));
            }
            return lines;
        }
    }
}
