package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Every look a mirror's banner can be stamped with, read from {@code shapes/mirror}.
 *
 * <p>The same arrangement gate shapes use, for the same reason: the shipped ones are written
 * out on first run so an operator has something to read and copy, and anything they add beside
 * them is picked up without touching the jar. A shipped file they delete comes back; a shipped
 * file they edit does not get overwritten, because the restore only writes what is missing.
 *
 * <p>Order is by file name, and that is load-bearing rather than tidy. {@link #forBiome(String)}
 * walks the presets in the order they loaded, so two presets claiming the same biome resolve by
 * that order -- which has to be the same order on every server, not whatever order the
 * filesystem happened to hand back.
 */
public final class MirrorPresetRegistry
{
    /** What a preset file is called. */
    private static final String SUFFIX = ".mirror";

    /**
     * The looks that ship in the jar, restored whenever one is missing.
     *
     * <p>The last five name no biome, so nothing ever picks them automatically -- they exist
     * for {@code mirror stamp <name> <look>}, where what the operator wants said about a mirror
     * is not where it goes.
     */
    private static final String[] DEFAULTS = {
        "plains.mirror", "sunflower_plains.mirror", "meadow.mirror", "swamp.mirror",
        "mangrove_swamp.mirror", "river.mirror", "frozen_river.mirror", "beach.mirror",
        "snowy_beach.mirror", "stony_shore.mirror", "mushroom_fields.mirror", "forest.mirror",
        "birch_forest.mirror", "old_growth_birch_forest.mirror", "dark_forest.mirror",
        "flower_forest.mirror", "taiga.mirror", "snowy_taiga.mirror",
        "old_growth_pine_taiga.mirror", "old_growth_spruce_taiga.mirror", "jungle.mirror",
        "bamboo_jungle.mirror", "sparse_jungle.mirror", "cherry_grove.mirror",
        "pale_garden.mirror", "windswept_forest.mirror", "desert.mirror", "badlands.mirror",
        "eroded_badlands.mirror", "wooded_badlands.mirror", "savanna.mirror",
        "savanna_plateau.mirror", "windswept_savanna.mirror", "snowy_plains.mirror",
        "ice_spikes.mirror", "snowy_slopes.mirror", "frozen_peaks.mirror",
        "jagged_peaks.mirror", "stony_peaks.mirror", "grove.mirror", "windswept_hills.mirror",
        "windswept_gravelly_hills.mirror", "ocean.mirror", "deep_ocean.mirror",
        "cold_ocean.mirror", "deep_cold_ocean.mirror", "lukewarm_ocean.mirror",
        "deep_lukewarm_ocean.mirror", "warm_ocean.mirror", "frozen_ocean.mirror",
        "deep_frozen_ocean.mirror", "dripstone_caves.mirror", "lush_caves.mirror",
        "deep_dark.mirror", "nether.mirror", "crimson_forest.mirror", "warped_forest.mirror",
        "soul_sand_valley.mirror", "basalt_deltas.mirror", "end.mirror",
        "end_highlands.mirror", "end_midlands.mirror", "small_end_islands.mirror",
        "end_barrens.mirror", "the_void.mirror", "overworld.mirror", "indoors.mirror",
        "cavern.mirror", "plain.mirror", "hub.mirror", "warning.mirror", "private.mirror",
        "arcane.mirror", "portal.mirror", "spawn.mirror", "exit.mirror", "arrival.mirror",
        "locked.mirror", "staff.mirror", "market.mirror", "shrine.mirror", "danger.mirror",
        "tomb.mirror", "vault.mirror", "forge.mirror", "library.mirror", "port.mirror",
        "compass.mirror" };

    /** The one used when nothing else matches, and when the far side is enclosed. */
    private static final String FALLBACK = "overworld";

    /** The one used when the far side is indoors and the biome is beside the point. */
    private static final String INDOORS = "indoors";

    /** Loaded presets, by lower-cased name, in load order. */
    private static final Map<String, MirrorPreset> PRESETS = new LinkedHashMap<>();

    /** Static state only. */
    private MirrorPresetRegistry()
    {
    }

    /**
     * Reads every preset, restoring the shipped ones first.
     *
     * @return how many are registered afterwards
     */
    public static int load()
    {
        return load(DataLayout.mirrorShapes());
    }

    /**
     * Reads every preset in a given directory.
     *
     * <p>Takes the directory so a test can point it somewhere harmless. The no-argument
     * version resolves the live plugin folder and writes every shipped preset into it, which
     * is not something a test that only wants to parse one preset should do.
     *
     * @param directory
     *            the folder to read, created if it is not there
     * @return how many presets are registered afterwards
     */
    public static int load(final File directory)
    {
        PRESETS.clear();
        if (!ensureDirectory(directory))
        {
            return 0;
        }
        restoreMissingDefaults(directory);
        final File[] files = directory.listFiles(
            (dir, name) -> !name.startsWith(".") && name.endsWith(SUFFIX));
        if (files == null)
        {
            PluginLog.log(Level.SEVERE, "Could not read " + directory.getPath()
                + "; no mirror presets will be loaded.");
            return 0;
        }
        // Sorted, because listFiles makes no promise about order -- it is roughly alphabetical
        // on NTFS and hash order on ext4. Without this the "presets keep their load order"
        // guarantee below holds only by luck of the filesystem, and two presets claiming the
        // same biome could resolve one way on a server and the other way on its backup.
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (final File file : files)
        {
            register(file);
        }
        return PRESETS.size();
    }

    /**
     * Reads one file and registers what it declares.
     *
     * <p>Per file, and catching more than IOException, the same as the shape loader: the whole
     * folder is read at startup, and one operator's malformed preset must not cost them every
     * other one.
     */
    private static void register(final File file)
    {
        try
        {
            final String fallbackName =
                file.getName().substring(0, file.getName().length() - SUFFIX.length());
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            final MirrorPreset preset = MirrorPreset.parse(fallbackName, lines);
            if (preset == null)
            {
                PluginLog.log(Level.WARNING, "Mirror preset " + file.getName()
                    + " has no usable Base colour, so it was skipped.");
                return;
            }
            PRESETS.put(preset.name().toLowerCase(Locale.ROOT), preset);
        }
        catch (final IOException | RuntimeException e)
        {
            PluginLog.log(Level.WARNING, "Could not read mirror preset " + file.getName(), e);
        }
    }

    /**
     * A preset by name, however it was capitalised.
     *
     * @param name
     *            what to look for
     * @return the preset, or null if there is none by that name
     */
    public static MirrorPreset byName(final String name)
    {
        return (name == null) ? null : PRESETS.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return every preset, in the order they loaded */
    public static Collection<MirrorPreset> all()
    {
        return List.copyOf(PRESETS.values());
    }

    /** @return every preset's name, for tab completion */
    public static String[] names()
    {
        return PRESETS.values().stream().map(MirrorPreset::name).toArray(String[]::new);
    }

    /**
     * The preset that answers for a biome.
     *
     * <p>Falls back rather than failing. A server with a biome no shipped preset names -- a
     * data pack's own, or one added in a version newer than these files -- still gets a
     * stamped banner, just a generic one.
     *
     * @param biome
     *            the biome's name
     * @return the matching preset, the fallback, or null if nothing is loaded at all
     */
    public static MirrorPreset forBiome(final String biome)
    {
        for (final MirrorPreset preset : PRESETS.values())
        {
            if (preset.answersFor(biome))
            {
                return preset;
            }
        }
        return fallback();
    }

    /**
     * What to frame an indoor view with.
     *
     * @return the indoors preset, or the fallback if somebody deleted and replaced it
     */
    public static MirrorPreset indoors()
    {
        final MirrorPreset preset = byName(INDOORS);
        return (preset == null) ? fallback() : preset;
    }

    /** @return the generic preset, or any preset at all, or null if none loaded */
    private static MirrorPreset fallback()
    {
        final MirrorPreset preset = byName(FALLBACK);
        if (preset != null)
        {
            return preset;
        }
        return PRESETS.isEmpty() ? null : PRESETS.values().iterator().next();
    }

    /** Makes sure the presets folder is there; mkdirs, because neither level need exist. */
    private static boolean ensureDirectory(final File directory)
    {
        if (directory.isDirectory())
        {
            return true;
        }
        boolean created = false;
        try
        {
            created = directory.mkdirs();
        }
        catch (final SecurityException e)
        {
            PluginLog.log(Level.SEVERE, "Not allowed to create " + directory.getPath(), e);
        }
        if (!created && !directory.isDirectory())
        {
            PluginLog.log(Level.SEVERE, "Could not create " + directory.getPath()
                + "; no mirror presets will be loaded.");
            return false;
        }
        return true;
    }

    /** Writes out any shipped preset the folder does not already have. */
    private static void restoreMissingDefaults(final File directory)
    {
        for (final String name : DEFAULTS)
        {
            final File file = new File(directory, name);
            if (!file.exists())
            {
                restoreDefault(name, file);
            }
        }
    }

    /** Copies one shipped preset out of the jar. */
    private static void restoreDefault(final String name, final File file)
    {
        try (final InputStream is =
            WormholeXTreme.class.getResourceAsStream("/shapes/mirror/" + name))
        {
            if (is == null)
            {
                PluginLog.log(Level.WARNING,
                    "Default mirror preset not found in JAR: " + name);
                return;
            }
            copy(is, file);
            PluginLog.log(Level.INFO, "Restored default mirror preset: " + name);
        }
        catch (final IOException e)
        {
            PluginLog.log(Level.SEVERE, "Unable to create default mirror preset " + name, e);
        }
    }

    /** Line by line, so the file ends up with this platform-independent newline throughout. */
    private static void copy(final InputStream is, final File file) throws IOException
    {
        try (final BufferedReader br =
                new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             final BufferedWriter bw =
                new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                bw.write(line);
                bw.write("\n");
            }
        }
    }

    /** @return the shipped preset file names, for a test to check the jar against */
    static List<String> shippedNames()
    {
        return new ArrayList<>(List.of(DEFAULTS));
    }
}
