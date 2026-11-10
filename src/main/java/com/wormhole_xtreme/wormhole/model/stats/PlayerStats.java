package com.wormhole_xtreme.wormhole.model.stats;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.EntityUtils;
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
    /** The section holding every player under their UUID. */
    private static final String PLAYERS = "players";

    /** The section holding each gate's trip count under its name (#638). */
    private static final String GATES = "gates";

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

    /** Trips through each gate, entered or arrived at, by lower-cased gate name as the gate list is keyed. */
    private static final Map<String, Integer> GATE_TRIPS = new ConcurrentHashMap<>();

    /** Whether memory holds a count the file does not. */
    private static volatile boolean changed = false;

    /** Whether the file there now could not be read, and so is set aside before it is written over. */
    private static volatile boolean unreadable = false;

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
     * Counts one thing an entity did, if it is a player and not an NPC plugin's fake one.
     *
     * @param entity
     *            who did it; anything but a player, null included, counts nothing
     * @param counter
     *            what they did
     */
    public static void countPlayer(final Entity entity, final Counter counter)
    {
        if ((entity instanceof Player player) && !EntityUtils.isNpc(player))
        {
            count(player.getUniqueId(), counter);
        }
    }

    /**
     * Counts one trip for a gate, if the entity is a player and not an NPC plugin's fake one.
     *
     * @param entity
     *            who travelled; anything but a player, null included, counts nothing
     * @param gate
     *            the gate they entered or arrived at; null counts nothing
     */
    public static void countGateTrip(final Entity entity, final Stargate gate)
    {
        if ((gate == null) || (gate.getGateName() == null)
            || !(entity instanceof Player player) || EntityUtils.isNpc(player))
        {
            return;
        }
        GATE_TRIPS.merge(gateKey(gate.getGateName()), 1, Integer::sum);
        changed = true;
    }

    /**
     * @param gateName
     *            the gate's name, in any case
     * @return the trips counted through it, zero for a gate with none
     */
    public static int gateTrips(final String gateName)
    {
        return (gateName == null) ? 0 : GATE_TRIPS.getOrDefault(gateKey(gateName), 0);
    }

    /**
     * Drops a gate's count, so a new gate of the same name starts at nothing.
     *
     * @param gateName
     *            the gate's name, in any case
     */
    public static void forgetGate(final String gateName)
    {
        if ((gateName != null) && (GATE_TRIPS.remove(gateKey(gateName)) != null))
        {
            changed = true;
        }
    }

    private static String gateKey(final String gateName)
    {
        return gateName.toLowerCase(Locale.ROOT);
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
     * the beam file is: a count is not worth a server that will not start. It is copied to
     * {@code stats.yml.bad} before the first save writes over it.
     *
     * @return how many players were loaded
     */
    public static int load()
    {
        COUNTS.clear();
        GATE_TRIPS.clear();
        changed = false;
        unreadable = false;
        final File file = DataLayout.statsFile();
        if (!file.exists())
        {
            return 0;
        }
        final Map<String, Object> root = read(file);
        final Object players = root.get(PLAYERS);
        if ((players != null) && !(players instanceof Map))
        {
            PluginLog.log(Level.WARNING, "Player stats file has no map of players; starting from nothing");
            unreadable = true;
        }
        for (final Map.Entry<String, Object> entry : YamlMaps.asMap(players).entrySet())
        {
            final UUID id = readId(entry.getKey());
            if (id == null)
            {
                // The rest still load, but the next save would drop this one for good.
                unreadable = true;
                continue;
            }
            COUNTS.put(id, readCounts(YamlMaps.asMap(entry.getValue())));
        }
        loadGates(root.get(GATES));
        return COUNTS.size();
    }

    /** Reads the gate section; a file with only players has none, which is nothing counted. */
    private static void loadGates(final Object gates)
    {
        if ((gates != null) && !(gates instanceof Map))
        {
            PluginLog.log(Level.WARNING, "Player stats file has no map of gates; their trips start from nothing");
            unreadable = true;
        }
        if (!(gates instanceof Map<?, ?> byName))
        {
            return;
        }
        // Keys are read as anything: a gate called 123 is a number to the parser.
        for (final Map.Entry<?, ?> entry : byName.entrySet())
        {
            final int trips = number(entry.getValue());
            if (trips > 0)
            {
                GATE_TRIPS.merge(gateKey(String.valueOf(entry.getKey())), trips, Integer::sum);
            }
        }
    }

    /** @return the file's top-level map, empty if it will not parse or is not a map */
    private static Map<String, Object> read(final File file)
    {
        try (FileInputStream in = new FileInputStream(file))
        {
            final Object parsed = new Yaml().load(in);
            if ((parsed == null) || (parsed instanceof Map))
            {
                return YamlMaps.asMap(parsed);
            }
            PluginLog.log(Level.WARNING, "Player stats file is not a map of players; starting from nothing");
        }
        catch (final IOException | RuntimeException e)
        {
            PluginLog.log(Level.WARNING, "Failed to read player stats file: " + e.getMessage());
        }
        unreadable = true;
        return Collections.emptyMap();
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
        boolean written = false;
        try
        {
            written = write();
        }
        catch (final RuntimeException e)
        {
            PluginLog.log(Level.WARNING, "Failed to write player stats file", e);
        }
        finally
        {
            // Tried again at the next save rather than given up on, even past an Error.
            if (!written)
            {
                changed = true;
            }
        }
        return written;
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
        root.put(GATES, new LinkedHashMap<String, Object>(GATE_TRIPS));

        final File target = DataLayout.statsFile();
        final File parent = target.getParentFile();
        if ((parent != null) && !parent.exists() && !parent.mkdirs())
        {
            PluginLog.log(Level.WARNING, "Could not create stats data directory " + parent.getAbsolutePath());
            return false;
        }
        if (unreadable && !setAside(target))
        {
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

    /**
     * Copies a file that could not be read to {@code stats.yml.bad}, over any older copy, so the save
     * about to replace it does not lose whatever an admin can still recover from it.
     *
     * @param target
     *            the stats file
     * @return true if it is safe to write over the file
     */
    private static boolean setAside(final File target)
    {
        if (!target.exists())
        {
            unreadable = false;
            return true;
        }
        final File bad = new File(target.getPath() + ".bad");
        try
        {
            Files.copy(target.toPath(), bad.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException e)
        {
            PluginLog.log(Level.WARNING, "Could not copy the unreadable player stats file aside, so it is"
                + " left as it is and the counts are not saved: " + e.getMessage());
            return false;
        }
        unreadable = false;
        PluginLog.log(Level.WARNING, "Kept the unreadable player stats file as " + bad.getName()
            + " before writing a new one.");
        return true;
    }

    /** Forgets every count, without touching the file. For tests. */
    public static void clear()
    {
        COUNTS.clear();
        GATE_TRIPS.clear();
        changed = false;
        unreadable = false;
    }
}
