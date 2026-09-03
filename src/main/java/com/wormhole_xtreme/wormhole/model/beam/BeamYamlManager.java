package com.wormhole_xtreme.wormhole.model.beam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

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
        try
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                return new File(WormholeXTreme.getThisPlugin().getDataFolder(),
                    "WormholeXTremeDB" + File.separator + "beam.yml");
            }
        }
        catch (final RuntimeException e)
        {
            // Fall through to the relative path below.
        }
        return new File("plugins" + File.separator + "WormholeXTreme" + File.separator
            + "WormholeXTremeDB" + File.separator + "beam.yml");
    }

    /**
     * Loads every stored destination and place into {@link BeamManager}.
     *
     * @return how many destinations were loaded, public and private combined
     */
    @SuppressWarnings("unchecked")
    public static int loadAll()
    {
        BeamManager.clear();
        final File file = getBeamFile();
        if (!file.exists())
        {
            return 0;
        }

        final Yaml yaml = new Yaml();
        final Map<String, Object> root;
        try (FileInputStream in = new FileInputStream(file))
        {
            final Object loaded = yaml.load(in);
            if (!(loaded instanceof Map))
            {
                return 0;
            }
            root = (Map<String, Object>) loaded;
        }
        catch (final IOException | RuntimeException e)
        {
            log(Level.WARNING, "Failed to read beam file: " + e.getMessage());
            return 0;
        }

        int count = 0;

        final Object publicSection = root.get("Public");
        if (publicSection instanceof Map)
        {
            for (final Map.Entry<String, Object> entry : ((Map<String, Object>) publicSection).entrySet())
            {
                final BeamDestination destination = readDestination(entry.getKey(), entry.getValue());
                if (destination != null)
                {
                    BeamManager.setPublicDestination(destination);
                    count++;
                }
            }
        }

        final Object placesSection = root.get("Places");
        if (placesSection instanceof Map)
        {
            for (final Map.Entry<String, Object> playerEntry : ((Map<String, Object>) placesSection).entrySet())
            {
                final UUID owner;
                try
                {
                    owner = UUID.fromString(playerEntry.getKey());
                }
                catch (final IllegalArgumentException e)
                {
                    log(Level.WARNING, "Skipping places for unreadable player id " + playerEntry.getKey());
                    continue;
                }
                final Object playerPlaces = playerEntry.getValue();
                if (!(playerPlaces instanceof Map))
                {
                    continue;
                }
                for (final Map.Entry<String, Object> placeEntry : ((Map<String, Object>) playerPlaces).entrySet())
                {
                    final BeamDestination destination = readDestination(placeEntry.getKey(), placeEntry.getValue());
                    if (destination != null)
                    {
                        BeamManager.setPlace(owner, destination);
                        count++;
                    }
                }
            }
        }

        return count;
    }

    @SuppressWarnings("unchecked")
    private static BeamDestination readDestination(final String name, final Object value)
    {
        if (!(value instanceof Map))
        {
            log(Level.WARNING, "Skipping malformed beam entry: " + name);
            return null;
        }
        try
        {
            final Map<String, Object> map = (Map<String, Object>) value;
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
            return new BeamDestination(name, world, x, y, z, yaw, pitch);
        }
        catch (final RuntimeException e)
        {
            log(Level.WARNING, "Skipping malformed beam entry " + name + ": " + e.getMessage());
            return null;
        }
    }

    private static double number(final Object value)
    {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    /** Writes every currently loaded destination and place back to disk, atomically. */
    public static void saveAll()
    {
        final Map<String, Object> publicOut = new LinkedHashMap<>();
        for (final BeamDestination destination : BeamManager.getAllPublicDestinations())
        {
            publicOut.put(destination.getName(), writeDestination(destination));
        }

        final Map<String, Object> placesOut = new LinkedHashMap<>();
        for (final Map.Entry<UUID, Map<String, BeamDestination>> playerEntry : BeamManager.getAllPlaces().entrySet())
        {
            final Map<String, Object> playerOut = new LinkedHashMap<>();
            for (final BeamDestination place : playerEntry.getValue().values())
            {
                playerOut.put(place.getName(), writeDestination(place));
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

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        final Yaml yaml = new Yaml(options);
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
            log(Level.WARNING, "Failed to write beam file: " + e.getMessage());
        }
    }

    private static Map<String, Object> writeDestination(final BeamDestination destination)
    {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("World", destination.getWorldName());
        map.put("X", destination.getX());
        map.put("Y", destination.getY());
        map.put("Z", destination.getZ());
        map.put("Yaw", destination.getYaw());
        map.put("Pitch", destination.getPitch());
        return map;
    }

    private static void log(final Level level, final String message)
    {
        try
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(level, false, message);
                return;
            }
        }
        catch (final RuntimeException e)
        {
            // Fall through to java.util.logging below, e.g. when running under a test.
        }
        java.util.logging.Logger.getLogger(BeamYamlManager.class.getName()).log(level, message);
    }
}
