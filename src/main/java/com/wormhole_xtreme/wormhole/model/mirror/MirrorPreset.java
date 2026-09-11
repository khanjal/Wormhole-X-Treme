package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.DyeColor;

/**
 * One shipped look for a mirror's banner, and the biomes it answers for.
 *
 * <p>A banner is six pattern layers over a dyed base, in sixteen colours. That is the whole
 * canvas -- there is no way to render a view onto it -- so a preset is a designed impression
 * of a kind of place rather than a picture of one: reds and rising flame for the Nether,
 * stalactites and stalagmites in grey for a cave, a blue band above green for ordinary
 * country.
 *
 * <p>{@link #biomes()} is what lets a mirror stamp itself. Binding one already knows where it
 * comes out, so the biome there picks the preset, and a corridor of mirrors ends up reading as
 * a row of labelled doors.
 *
 * @param name
 *            what the preset is called, and what {@code mirror stamp} takes
 * @param base
 *            the banner's own colour, before any pattern
 * @param layers
 *            the patterns to lay over it, in order
 * @param biomes
 *            biome names this preset is the answer for, upper-cased; empty if it is only ever
 *            chosen by hand
 * @param sheltered
 *            true if this kind of place is enclosed anyway, so finding it enclosed says
 *            nothing new and must not replace this look with the indoor one
 */
public record MirrorPreset(String name, DyeColor base, List<Layer> layers, Set<String> biomes,
    boolean sheltered)
{
    /**
     * One pattern in one colour.
     *
     * <p>The pattern is kept as a name rather than a {@code PatternType}, and resolved when it
     * is applied. The type is an enum through 1.20.6 and a registry-backed interface from
     * 1.21, and seven names present at this plugin's compile target are gone by 1.20.6 --
     * holding a constant would compile here and fail on a server two versions along.
     *
     * @param colour
     *            the dye colour
     * @param pattern
     *            the pattern's name, as Bukkit spells it
     */
    public record Layer(DyeColor colour, String pattern)
    {
    }

    /** @return the preset with its lists made unmodifiable */
    public MirrorPreset
    {
        layers = List.copyOf(layers);
        biomes = Set.copyOf(biomes);
    }

    /**
     * Whether this preset is the answer for a biome.
     *
     * @param biome
     *            the biome's name, in any case
     * @return true if this preset names it
     */
    public boolean answersFor(final String biome)
    {
        return (biome != null) && biomes.contains(biome.toUpperCase(Locale.ROOT));
    }

    /**
     * Reads a preset from the lines of a {@code .mirror} file.
     *
     * <p>Lenient on purpose. A line it does not understand is ignored rather than failing the
     * file, and a preset with no layers at all is still a preset -- it dyes the banner and
     * leaves it plain. The alternative is an operator's typo costing them every preset on the
     * server, which is the shape of failure this project has already been bitten by in the
     * shape files.
     *
     * @param fallbackName
     *            what to call it if the file does not say, normally the file name
     * @param lines
     *            the file's lines
     * @return the preset, or null if there is no usable base colour
     */
    public static MirrorPreset parse(final String fallbackName, final List<String> lines)
    {
        String name = fallbackName;
        DyeColor base = null;
        final List<Layer> layers = new ArrayList<>();
        final Set<String> biomes = new LinkedHashSet<>();
        boolean sheltered = false;

        for (final String raw : (lines == null) ? Collections.<String>emptyList() : lines)
        {
            final Setting setting = settingIn(raw);
            if (setting == null)
            {
                continue;
            }
            switch (setting.key())
            {
                case "NAME" -> name = setting.value().isEmpty() ? name : setting.value();
                case "BASE" -> base = colour(setting.value());
                case "BIOME" -> addBiomes(biomes, setting.value());
                case "LAYER" -> addLayer(layers, setting.value());
                case "SHELTERED" -> sheltered = Boolean.parseBoolean(setting.value());
                default -> { /* not a key this understands; somebody's own note */ }
            }
        }
        return (base == null) ? null : new MirrorPreset(name, base, layers, biomes, sheltered);
    }

    /**
     * One {@code Key=value} line, split.
     *
     * @param key
     *            what is before the equals sign, trimmed and upper-cased
     * @param value
     *            what is after it, trimmed
     */
    private record Setting(String key, String value)
    {
    }

    /**
     * Splits one line into its key and its value.
     *
     * @param raw
     *            the line as read, which may be null
     * @return the setting, or null for a blank, a comment, or a line with nothing before an
     *         equals sign
     */
    private static Setting settingIn(final String raw)
    {
        final String line = (raw == null) ? "" : raw.trim();
        if (line.isEmpty() || line.startsWith("#"))
        {
            return null;
        }
        final int equals = line.indexOf('=');
        if (equals <= 0)
        {
            return null;
        }
        return new Setting(line.substring(0, equals).trim().toUpperCase(Locale.ROOT),
            line.substring(equals + 1).trim());
    }

    private static void addBiomes(final Set<String> into, final String value)
    {
        for (final String biome : value.split(","))
        {
            final String trimmed = biome.trim().toUpperCase(Locale.ROOT);
            if (!trimmed.isEmpty())
            {
                into.add(trimmed);
            }
        }
    }

    /** A layer line is "COLOUR PATTERN"; anything else is skipped. */
    private static void addLayer(final List<Layer> into, final String value)
    {
        final String[] parts = value.split("\\s+");
        if (parts.length < 2)
        {
            return;
        }
        final DyeColor colour = colour(parts[0]);
        if (colour != null)
        {
            into.add(new Layer(colour, parts[1].trim().toUpperCase(Locale.ROOT)));
        }
    }

    /**
     * A dye colour by name, or null if that is not one.
     *
     * <p>{@code DyeColor} has been the same sixteen values for the whole of this plugin's
     * supported range, so unlike a pattern this one is safe to resolve at load.
     */
    private static DyeColor colour(final String value)
    {
        try
        {
            return DyeColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (final IllegalArgumentException notAColour)
        {
            return null;
        }
    }
}
