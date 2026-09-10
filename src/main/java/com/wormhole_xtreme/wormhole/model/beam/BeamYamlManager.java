package com.wormhole_xtreme.wormhole.model.beam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.utils.PluginDirectory;
import com.wormhole_xtreme.wormhole.utils.PluginLog;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;
import com.wormhole_xtreme.wormhole.utils.YamlStore;

/**
 * Loads and saves beam destinations, in one file.
 *
 * <p>Unlike rings, beam destinations are not one-per-world: beaming is deliberately cross-world
 * capable (the long-haul option a gate is, not the same-world-only design rings chose), so
 * there is no equivalent reason to shard storage by world. A single file is also the honest
 * size for what this holds — a server's public destinations plus every player's private
 * places, not a per-pair structure that grows with distance.
 */
public final class BeamYamlManager
{
    private BeamYamlManager() {}

    public static File getBeamFile()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, "WormholeXTremeDB", "beam.yml");
    }

    /**
     * Loads every stored destination and place into {@link BeamManager}.
     *
     * @return how many destinations were loaded, public and private combined
     */
    public static int loadAll()
    {
        BeamManager.clear();
        final Map<String, Object> root = readBeamFile();
        return loadPublic(root.get("Public")) + loadPlaces(root.get("Places"));
    }

    /**
     * Reads beam.yml, or null if there is nothing usable to read.
     *
     * <p>A missing file is a first run. A file that will not parse, or parses to something
     * other than a map, is an operator's hand edit gone wrong -- worth a line in the log and
     * not worth taking the server down for. All three come back the same way: nothing to
     * load, which an empty map says without the caller having to check for null.
     *
     * @return the file's top-level map, empty if there is nothing usable to read
     */
    private static Map<String, Object> readBeamFile()
    {
        final File file = getBeamFile();
        if (!file.exists())
        {
            return Collections.emptyMap();
        }
        try (FileInputStream in = new FileInputStream(file))
        {
            return YamlMaps.asMap(new Yaml().load(in));
        }
        catch (final IOException | RuntimeException e)
        {
            log(Level.WARNING, "Failed to read beam file: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Loads the destinations anybody may beam to.
     *
     * @param section
     *            the Public section, whatever the file had there
     * @return how many were loaded
     */
    private static int loadPublic(final Object section)
    {
        int count = 0;
        for (final Map.Entry<String, Object> entry : YamlMaps.asMap(section).entrySet())
        {
            final BeamDestination destination = readDestination(entry.getKey(), entry.getValue());
            if (destination != null)
            {
                BeamManager.setPublicDestination(destination);
                count++;
            }
        }
        return count;
    }

    /**
     * Loads each player's own places, filed under the owner's id.
     *
     * <p>A player whose id will not parse loses their places and nobody else's. The whole
     * file is read at startup, so anything stronger would let one unreadable id leave a
     * server with no beam destinations at all.
     *
     * @param section
     *            the Places section, whatever the file had there
     * @return how many were loaded
     */
    private static int loadPlaces(final Object section)
    {
        int count = 0;
        for (final Map.Entry<String, Object> playerEntry : YamlMaps.asMap(section).entrySet())
        {
            final UUID owner = readOwnerId(playerEntry.getKey());
            if (owner == null)
            {
                continue;
            }
            for (final Map.Entry<String, Object> placeEntry
                : YamlMaps.asMap(playerEntry.getValue()).entrySet())
            {
                final BeamDestination destination = readDestination(placeEntry.getKey(), placeEntry.getValue());
                if (destination != null)
                {
                    BeamManager.setPlace(owner, destination);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * A player id from the file, or null if it will not parse.
     *
     * @param key
     *            the key the file filed these places under
     * @return the id, or null
     */
    private static UUID readOwnerId(final String key)
    {
        try
        {
            return UUID.fromString(key);
        }
        catch (final IllegalArgumentException e)
        {
            log(Level.WARNING, "Skipping places for unreadable player id " + key);
            return null;
        }
    }

    // Package-private rather than private: a pure Map <-> BeamDestination conversion with no
    // file I/O in it at all, so BeamYamlManagerTest exercises it directly rather than
    // round-tripping through a real file.
    static BeamDestination readDestination(final String name, final Object value)
    {
        if (!(value instanceof Map))
        {
            log(Level.WARNING, "Skipping malformed beam entry: " + name);
            return null;
        }
        try
        {
            final Map<String, Object> map = YamlMaps.asMap(value);
            final String world = (String) map.get("World");
            final double x = number(map.get("X"));
            final double y = number(map.get("Y"));
            final double z = number(map.get("Z"));
            final float yaw = (float) number(map.get("Yaw"));
            final float pitch = (float) number(map.get("Pitch"));
            if (world == null)
            {
                log(Level.WARNING, "Skipping beam entry with no world: " + name);
                return null;
            }
            // Absent means null -- inherit the global default -- not zero. A destination
            // written before this field existed, or a place (which never gets one set on
            // it at all), both read back exactly the same way they would have before.
            final Object rawCost = map.get("Cost");
            final Double cost = rawCost instanceof Number number ? number.doubleValue() : null;
            return new BeamDestination(name, new BeamPoint(world, x, y, z, yaw, pitch), cost);
        }
        catch (final RuntimeException e)
        {
            log(Level.WARNING, "Skipping malformed beam entry " + name + ": " + e.getMessage());
            return null;
        }
    }

    private static double number(final Object value)
    {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /** Writes every currently loaded destination and place back to disk, atomically. */
    public static void saveAll()
    {
        final Map<String, Object> publicOut = new LinkedHashMap<>();
        for (final BeamDestination destination : BeamManager.getAllPublicDestinations())
        {
            publicOut.put(destination.name(), writeDestination(destination));
        }

        final Map<String, Object> placesOut = new LinkedHashMap<>();
        for (final Map.Entry<UUID, Map<String, BeamDestination>> playerEntry : BeamManager.getAllPlaces().entrySet())
        {
            final Map<String, Object> playerOut = new LinkedHashMap<>();
            for (final BeamDestination place : playerEntry.getValue().values())
            {
                playerOut.put(place.name(), writeDestination(place));
            }
            if (!playerOut.isEmpty())
            {
                placesOut.put(playerEntry.getKey().toString(), playerOut);
            }
        }

        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("Public", publicOut);
        root.put("Places", placesOut);

        final File target = getBeamFile();
        final File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
        {
            log(Level.WARNING, "Could not create beam data directory " + parent.getAbsolutePath());
            return;
        }

        try
        {
            YamlStore.write(target, root);
        }
        catch (final IOException e)
        {
            log(Level.WARNING, "Failed to write beam file: " + e.getMessage());
        }
    }

    static Map<String, Object> writeDestination(final BeamDestination destination)
    {
        final BeamPoint point = destination.point();
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("World", point.worldName());
        map.put("X", point.x());
        map.put("Y", point.y());
        map.put("Z", point.z());
        map.put("Yaw", point.yaw());
        map.put("Pitch", point.pitch());
        if (destination.cost() != null)
        {
            map.put("Cost", destination.cost());
        }
        return map;
    }

    private static void log(final Level level, final String message)
    {
        PluginLog.log(level, message);
    }
}
