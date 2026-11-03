package com.wormhole_xtreme.wormhole.model.stats;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.PluginLog;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;
import com.wormhole_xtreme.wormhole.utils.YamlStore;

/**
 * Each player's lifetime counts: trips through gates, gates built, ring trips and beams (#237).
 *
 * <p>Keyed by UUID, so a count survives a name change, and held in memory: an increment is a map
 * update on the main thread, and the file is written only when something changed, by the periodic
 * save and on disable. A server killed outright loses at most the last few minutes of counting.
 *
 * <p>Read from any thread: PlaceholderAPI answers for scoreboard plugins that ask off the main
 * thread, so each player's counts are an immutable {@link Counts} swapped whole.
 */
public final class PlayerStats
{
    /** The file's one section, every player under their UUID. */
    private static final String PLAYERS = "players";

    /** What is counted, by its key in {@code stats.yml}. */
    public enum Counter
    {
        /** A trip through a gate. */
        TRIPS("trips"),
        /** A gate built and named. */
        GATES_BUILT("gates-built"),
        /** A trip by transport rings. */
        RINGS("rings"),
        /** A beam to a destination or place. */
        BEAMS("beams");

        private final String key;

        Counter(final String key)
        {
            this.key = key;
        }

        /** @return its key in {@code stats.yml} */
        public String key()
        {
            return key;
        }
    }

    /**
     * One player's counts.
     *
     * @param trips
     *            trips through gates
     * @param gatesBuilt
     *            gates built
     * @param rings
     *            ring trips
     * @param beams
     *            beams taken
     */
    public record Counts(int trips, int gatesBuilt, int rings, int beams)
    {
        /** A player with no record. */
        public static final Counts NONE = new Counts(0, 0, 0, 0);

        /**
         * @param counter
         *            which count
         * @return its value
         */
        public int get(final Counter counter)
        {
            return switch (counter)
            {
                case TRIPS -> trips;
                case GATES_BUILT -> gatesBuilt;
                case RINGS -> rings;
                case BEAMS -> beams;
            };
        }

        /**
         * @param counter
         *            which count goes up
         * @return these counts with that one a step higher
         */
        Counts plusOne(final Counter counter)
        {
            return switch (counter)
            {
                case TRIPS -> new Counts(trips + 1, gatesBuilt, rings, beams);
                case GATES_BUILT -> new Counts(trips, gatesBuilt + 1, rings, beams);
                case RINGS -> new Counts(trips, gatesBuilt, rings + 1, beams);
                case BEAMS -> new Counts(trips, gatesBuilt, rings, beams + 1);
            };
        }
    }

    private static final Map<UUID, Counts> COUNTS = new ConcurrentHashMap<>();

    /** Whether memory holds a count the file does not. */
    private static volatile boolean changed = false;

    private PlayerStats()
    {
    }

    /**
     * Counts one thing a player did.
     *
     * @param playerId
     *            who did it; null counts nothing
     * @param counter
     *            what they did
     */
    public static void count(final UUID playerId, final Counter counter)
    {
        if (playerId == null)
        {
            return;
        }
        COUNTS.compute(playerId, (id, had) -> ((had == null) ? Counts.NONE : had).plusOne(counter));
        changed = true;
    }

    /**
     * @param playerId
     *            whose counts
     * @return their counts, all zero for a player with no record or a null id
     */
    public static Counts of(final UUID playerId)
    {
        if (playerId == null)
        {
            return Counts.NONE;
        }
        return COUNTS.getOrDefault(playerId, Counts.NONE);
    }

    /**
     * Reads {@code stats.yml}, replacing whatever was counted in memory.
     *
     * <p>A missing file is a first run. One that will not parse is logged and read as empty, the way
     * the beam file is: a count is not worth a server that will not start.
     *
     * @return how many players were loaded
     */
    public static int load()
    {
        COUNTS.clear();
        changed = false;
        final File file = DataLayout.statsFile();
        if (!file.exists())
        {
            return 0;
        }
        final Map<String, Object> root = read(file);
        for (final Map.Entry<String, Object> entry : YamlMaps.asMap(root.get(PLAYERS)).entrySet())
        {
            final UUID id = readId(entry.getKey());
            if (id != null)
            {
                COUNTS.put(id, readCounts(YamlMaps.asMap(entry.getValue())));
            }
        }
        return COUNTS.size();
    }

    /** @return the file's top-level map, empty if it will not parse */
    private static Map<String, Object> read(final File file)
    {
        try (FileInputStream in = new FileInputStream(file))
        {
            return YamlMaps.asMap(new Yaml().load(in));
        }
        catch (final IOException | RuntimeException e)
        {
            PluginLog.log(Level.WARNING, "Failed to read player stats file: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** @return the id, or null with a warning for one that will not parse */
    private static UUID readId(final String key)
    {
        try
        {
            return UUID.fromString(key);
        }
        catch (final IllegalArgumentException e)
        {
            PluginLog.log(Level.WARNING, "Skipping stats for unreadable player id " + key);
            return null;
        }
    }

    private static Counts readCounts(final Map<String, Object> map)
    {
        return new Counts(number(map.get(Counter.TRIPS.key())), number(map.get(Counter.GATES_BUILT.key())),
            number(map.get(Counter.RINGS.key())), number(map.get(Counter.BEAMS.key())));
    }

    /** @return a count from the file; anything that is not a number reads as none */
    private static int number(final Object value)
    {
        return (value instanceof Number n) ? n.intValue() : 0;
    }

    /**
     * Writes {@code stats.yml}, if anything has been counted since it was last read or written.
     *
     * @return true if the file was written
     */
    public static boolean saveIfChanged()
    {
        if (!changed)
        {
            return false;
        }
        changed = false;
        if (!write())
        {
            // Tried again at the next save rather than given up on.
            changed = true;
            return false;
        }
        return true;
    }

    /** @return whether the file was written */
    private static boolean write()
    {
        final Map<String, Object> players = new LinkedHashMap<>();
        for (final Map.Entry<UUID, Counts> entry : COUNTS.entrySet())
        {
            final Map<String, Object> counts = new LinkedHashMap<>();
            for (final Counter counter : Counter.values())
            {
                counts.put(counter.key(), entry.getValue().get(counter));
            }
            players.put(entry.getKey().toString(), counts);
        }
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put(PLAYERS, players);

        final File target = DataLayout.statsFile();
        final File parent = target.getParentFile();
        if ((parent != null) && !parent.exists() && !parent.mkdirs())
        {
            PluginLog.log(Level.WARNING, "Could not create stats data directory " + parent.getAbsolutePath());
            return false;
        }
        try
        {
            YamlStore.write(target, root);
            return true;
        }
        catch (final IOException e)
        {
            PluginLog.log(Level.WARNING, "Failed to write player stats file: " + e.getMessage());
            return false;
        }
    }

    /** Forgets every count, without touching the file. For tests. */
    public static void clear()
    {
        COUNTS.clear();
        changed = false;
    }
}
