/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Reading and writing ring pairs, one file per world.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Material;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Loads and saves ring pairs, one file per world.
 *
 * <p>Per world rather than per pair for three reasons pointing the same way. It makes the
 * layout enforce the rule that a pair cannot span worlds, because there is nowhere to write
 * one that does. It cuts startup reads from one per pair to one per world, and file reading
 * is the only part of loading that costs anything — the indexing itself is map insertions.
 * And it makes world lifecycle trivial: an unloaded world's file is skipped, so its rings do
 * not sit in an index nothing can reach, and a deleted world takes its rings with one file.
 *
 * <p>The risk of a shared file is that one bad write loses a world rather than a pair, and
 * that is answered twice over. Writes go to a temp file and are moved into place atomically,
 * the way gate files already are, so a partial write is never visible. And loading tolerates
 * damage one entry at a time: a pair that will not parse is logged and skipped while the
 * rest of the world still loads. Losing one broken ring is recoverable; losing a base's
 * whole transport network to a typo is not.
 */
public final class RingYamlManager
{
    private RingYamlManager() {}

    /**
     * Where ring files live.
     *
     * @return the rings directory
     */
    public static File getRingsDir()
    {
        try
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                return new File(WormholeXTreme.getThisPlugin().getDataFolder(),
                    "WormholeXTremeDB" + File.separator + "rings");
            }
        }
        catch (final RuntimeException e)
        {
            // Fall through to the relative path below.
        }
        return new File("plugins" + File.separator + "WormholeXTreme" + File.separator
            + "WormholeXTremeDB" + File.separator + "rings");
    }

    /**
     * The file a world's pairs are stored in.
     *
     * <p>The name is sanitised because a world name may contain characters a filesystem will
     * not take. It is never parsed back: the {@code World} field inside the file is what
     * says which world these rings belong to, so two worlds whose names sanitise alike
     * cannot be confused for one another on the way in.
     *
     * @param directory
     *            the rings directory
     * @param worldName
     *            the world
     * @return the file for that world
     */
    static File fileForWorld(final File directory, final String worldName)
    {
        return new File(directory, worldName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml");
    }

    /**
     * Loads every world file into the manager and the index.
     *
     * @param reach
     *            how deep each trigger volume runs
     * @return how many pairs were loaded
     */
    public static int loadAll(final int reach)
    {
        return loadAll(getRingsDir(), reach);
    }

    /**
     * Loads every world file from a given directory.
     *
     * <p>The directory is a parameter so this can be pointed at a temporary folder in a test.
     * Storage is the part of the subsystem where a mistake is permanent, so it should not be
     * the part that can only be exercised on a live server.
     *
     * @param directory
     *            the directory to read
     * @param reach
     *            how deep each trigger volume runs
     * @return how many pairs were loaded
     */
    public static int loadAll(final File directory, final int reach)
    {
        if (!directory.exists())
        {
            directory.mkdirs();
            return 0;
        }
        final File[] files = directory.listFiles((d, name) ->
            name.toLowerCase().endsWith(".yml") || name.toLowerCase().endsWith(".yaml"));
        if (files == null)
        {
            return 0;
        }
        int loaded = 0;
        for (final File file : files)
        {
            loaded += loadFile(file, reach);
        }
        return loaded;
    }

    /**
     * Loads one world file.
     *
     * @param file
     *            the file to read
     * @param reach
     *            how deep each trigger volume runs
     * @return how many pairs were loaded from it
     */
    @SuppressWarnings("unchecked")
    private static int loadFile(final File file, final int reach)
    {
        final Yaml yaml = new Yaml();
        final Map<String, Object> root;
        try (FileInputStream in = new FileInputStream(file))
        {
            final Object parsed = yaml.load(in);
            if (!(parsed instanceof Map))
            {
                return 0;
            }
            root = (Map<String, Object>) parsed;
        }
        // A whole file that will not parse is the one case that cannot be salvaged per
        // entry, so it is reported loudly and the other worlds still load.
        catch (final Exception e)
        {
            log(Level.WARNING, "Could not read ring file " + file.getName() + ": " + e.getMessage());
            return 0;
        }

        final String worldName = String.valueOf(root.getOrDefault("World", ""));
        if (worldName.isEmpty())
        {
            log(Level.WARNING, "Ring file " + file.getName() + " names no world; skipping it.");
            return 0;
        }
        final Object pairsNode = root.get("Pairs");
        if (!(pairsNode instanceof Map))
        {
            return 0;
        }

        int loaded = 0;
        for (final Map.Entry<String, Object> entry : ((Map<String, Object>) pairsNode).entrySet())
        {
            try
            {
                final RingPair pair = readPair(entry.getKey(), worldName,
                    (Map<String, Object>) entry.getValue());
                RingManager.addPair(pair, reach);
                loaded++;
            }
            // One damaged pair must not cost a world its other rings.
            catch (final Exception e)
            {
                log(Level.WARNING, "Skipping unreadable ring pair " + entry.getKey() + " in "
                    + worldName + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    /**
     * Builds one pair from its stored map.
     *
     * @param id
     *            the pair id, which is the key it was stored under
     * @param worldName
     *            the world it belongs to
     * @param map
     *            the stored fields
     * @return the pair
     */
    @SuppressWarnings("unchecked")
    private static RingPair readPair(final String id, final String worldName,
        final Map<String, Object> map)
    {
        // A pair written before style moved onto the end carries one value for both. Read
        // it as the fallback for each so those files keep behaving exactly as they did.
        final RingStyle shared = readStyle(map.get("Style"));
        final Ring endA = readRing((Map<String, Object>) map.get("A"), shared);
        final Ring endB = readRing((Map<String, Object>) map.get("B"), shared);
        final RingPair pair = new RingPair(id, worldName, endA, endB);
        pair.setOwner(String.valueOf(map.getOrDefault("Owner", "")));
        pair.setOwnerName(String.valueOf(map.getOrDefault("OwnerName", "")));
        pair.setCreated(((Number) map.getOrDefault("Created", Long.valueOf(0L))).longValue());
        // Absent means private. A file written before access existed, or one somebody hand
        // edited badly, must not quietly open a ring to the whole server.
        pair.setAccess(readAccess(map.get("Access")));
        final Object allowed = map.get("Allowed");
        if (allowed instanceof java.util.List)
        {
            for (final Object entry : (java.util.List<Object>) allowed)
            {
                pair.allow(String.valueOf(entry));
            }
        }
        return pair;
    }

    /**
     * Reads the access mode, defaulting to the closed one.
     *
     * <p>Anything unreadable resolves to {@link RingAccess#PRIVATE}. Failing open would mean
     * a corrupted field silently publishing somebody's private link, which is the one
     * failure here that cannot be undone once people have used it.
     *
     * @param stored
     *            the stored value, possibly null or nonsense
     * @return the access mode
     */
    private static RingAccess readAccess(final Object stored)
    {
        if (stored == null)
        {
            return RingAccess.PRIVATE;
        }
        try
        {
            return RingAccess.valueOf(String.valueOf(stored));
        }
        catch (final IllegalArgumentException e)
        {
            return RingAccess.PRIVATE;
        }
    }

    /**
     * Reads the animation style, defaulting to the commoner one.
     *
     * <p>Unlike access there is nothing at stake in getting this wrong, so an unreadable
     * value simply falls back rather than being treated as damage worth skipping a pair for.
     *
     * @param stored
     *            the stored value, possibly null or nonsense
     * @return the style
     */
    private static RingStyle readStyle(final Object stored)
    {
        if (stored == null)
        {
            return RingStyle.CONCURRENT;
        }
        try
        {
            return RingStyle.valueOf(String.valueOf(stored));
        }
        catch (final IllegalArgumentException e)
        {
            return RingStyle.CONCURRENT;
        }
    }

    /**
     * Builds one end from its stored map.
     *
     * @param map
     *            the stored fields
     * @param fallback
     *            the pair-level style to use when this end names none of its own
     * @return the ring
     */
    private static Ring readRing(final Map<String, Object> map, final RingStyle fallback)
    {
        final int x = ((Number) map.get("X")).intValue();
        final int y = ((Number) map.get("Y")).intValue();
        final int z = ((Number) map.get("Z")).intValue();
        final RingPattern pattern = RingPattern.valueOf(String.valueOf(map.get("Pattern")));
        final RingOrientation orientation = RingOrientation.valueOf(String.valueOf(map.get("Orientation")));
        final Material ring = Material.valueOf(String.valueOf(map.get("Ring")));
        final Material light = Material.valueOf(String.valueOf(map.get("Light")));
        final Ring built = new Ring(x, y, z, pattern, orientation, ring, light);
        built.setStyle(map.containsKey("Style") ? readStyle(map.get("Style")) : fallback);
        built.setName(String.valueOf(map.getOrDefault("Name", "")));
        // A ring written before the flash was its own material used one for both, so falling
        // back to the light keeps those looking exactly as they did.
        final Material flash = Material.matchMaterial(String.valueOf(map.getOrDefault("Flash", "")));
        built.setFlashMaterial(flash == null ? light : flash);
        return built;
    }

    /**
     * Writes every pair in one world.
     *
     * <p>The whole file is rewritten on any change, which is affordable because ring writes
     * are rare and player-initiated — create, remove, relabel, recolour. Nothing writes on
     * the travel path.
     *
     * @param worldName
     *            the world to write
     */
    public static void saveWorld(final String worldName)
    {
        saveWorld(getRingsDir(), worldName);
    }

    /**
     * Writes every pair in one world into a given directory.
     *
     * @param directory
     *            the directory to write into
     * @param worldName
     *            the world to write
     */
    public static void saveWorld(final File directory, final String worldName)
    {
        if (!directory.exists())
        {
            directory.mkdirs();
        }
        final File target = fileForWorld(directory, worldName);

        final Map<String, Object> pairsOut = new LinkedHashMap<String, Object>();
        for (final RingPair pair : RingManager.getPairsInWorld(worldName))
        {
            pairsOut.put(pair.getId(), writePair(pair));
        }

        if (pairsOut.isEmpty())
        {
            // An empty file is a file that has to be read and skipped every startup, and a
            // world with no rings is better represented by there being nothing there.
            if (target.exists() && !target.delete())
            {
                log(Level.WARNING, "Could not delete now-empty ring file " + target.getName());
            }
            return;
        }

        final Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("World", worldName);
        root.put("Pairs", pairsOut);

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        final Yaml yaml = new Yaml(options);

        // Temp file then atomic move, so a partial write is never a readable file. With a
        // world's rings in one place this matters more than it does for a single gate.
        try
        {
            final File temp = new File(target.getAbsolutePath() + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(temp)))
            {
                yaml.dump(root, writer);
            }
            Files.move(temp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (final IOException e)
        {
            log(Level.WARNING, "Failed to write ring file " + target.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Turns one pair into its stored form.
     *
     * @param pair
     *            the pair to write
     * @return the map to dump
     */
    private static Map<String, Object> writePair(final RingPair pair)
    {
        final Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("Owner", pair.getOwner() == null ? "" : pair.getOwner());
        out.put("OwnerName", pair.getOwnerName() == null ? "" : pair.getOwnerName());
        out.put("Created", Long.valueOf(pair.getCreated()));
        out.put("Access", pair.getAccess().name());
        out.put("Allowed", new java.util.ArrayList<String>(pair.getAllowed()));
        out.put("A", writeRing(pair.getEndA()));
        out.put("B", writeRing(pair.getEndB()));
        return out;
    }

    /**
     * Turns one end into its stored form.
     *
     * <p>Only the anchor, pattern and orientation go in. The footprint is a pure function of
     * those, so writing it out would create a second copy that could disagree with the first.
     *
     * @param ring
     *            the end to write
     * @return the map to dump
     */
    private static Map<String, Object> writeRing(final Ring ring)
    {
        final Map<String, Object> out = new HashMap<String, Object>();
        out.put("X", Integer.valueOf(ring.getAnchorX()));
        out.put("Y", Integer.valueOf(ring.getAnchorY()));
        out.put("Z", Integer.valueOf(ring.getAnchorZ()));
        out.put("Pattern", ring.getPattern().name());
        out.put("Orientation", ring.getOrientation().name());
        out.put("Ring", ring.getRingMaterial().name());
        out.put("Light", ring.getLightMaterial().name());
        out.put("Flash", ring.getFlashMaterial().name());
        out.put("Style", ring.getStyle().name());
        out.put("Name", ring.getName());
        return out;
    }

    /**
     * Logs through the plugin when there is one, and stays silent when there is not.
     *
     * @param level
     *            the severity
     * @param message
     *            what to say
     */
    private static void log(final Level level, final String message)
    {
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(level, false, message);
        }
    }
}
