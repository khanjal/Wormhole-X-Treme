package com.wormhole_xtreme.wormhole.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Writes this server's gates out in the format {@link LegacyDatabaseImporter} reads.
 *
 * <p>The way back out for an admin who tries this fork and returns to the 2011 original, or runs
 * Dakto's build on a second server: both keep gates in one SQLite table, each gate a binary blob.
 * The blob is written at save version 8, the newest both understand (this fork's version 9 only
 * changes how custom materials are named). Version 8 stores materials by ordinal, so those
 * only match on the same Minecraft version.
 *
 * <p>It writes a fresh database into a folder of its own under {@code exports/} and nothing
 * else: never into {@code WormholeXTremeDB}, which other forks share, and never to a live gate file.
 * Whatever the format cannot hold is reported rather than dropped silently.
 */
public final class LegacyDatabaseExporter
{
    /** The file the database is written to. */
    public static final String DB_NAME = "WormholeXTreme.sqlite";

    /** What those forks call the schema this table layout belongs to. */
    private static final int SCHEMA_VERSION = 8;

    /**
     * The shapes the older plugins can name. A gate on any other shape is skipped rather than
     * written under a name those plugins would not find. Matched case-insensitively, as they do.
     */
    private static final List<String> KNOWN_SHAPES = List.of("Standard", "Large", "Small", "Super",
        "Minimal", "Horizontal", "StandardSignDial", "LargeSignDial", "SmallSignDial",
        "SuperSignDial", "MinimalSignDial", "HorizontalSignDial", "Pegasus", "Destiny");

    /** Per-gate settings of this fork that the format has no place for. */
    public enum Dropped
    {
        USE_COST, DIAL_SPIN, IRIS_ANIMATION, VIEW, MATERIAL_GROUP, FRAME_MATERIAL, DIAL_SIGN_TARGET,
        OWNER_UUID, OWNER_UNKNOWN
    }

    /** Why an export could not run at all. */
    public enum Problem
    {
        NO_DRIVER, NO_FOLDER, WRITE_FAILED
    }

    /** What one export came to. */
    public static final class Result
    {
        private final File file;
        private final Problem problem;
        private final Object[] problemArgs;
        private int exported;
        private final List<String> skipped = new ArrayList<>();
        private final Map<Dropped, Integer> dropped = new EnumMap<>(Dropped.class);

        Result(final File file, final Problem problem, final Object... problemArgs)
        {
            this.file = file;
            this.problem = problem;
            this.problemArgs = problemArgs;
        }

        /** @return the database written, or null if the export did not run */
        public File getFile() { return file; }

        /** @return what stopped the export entirely, or null if it ran */
        public Problem getProblem() { return problem; }

        /** @return the arguments that problem's message takes */
        public Object[] getProblemArgs() { return problemArgs.clone(); }

        /** @return how many gates were written */
        public int getExported() { return exported; }

        /** @return the gates left out and why, one line each */
        public List<String> getSkipped() { return skipped; }

        /** @return for each kind of setting lost, how many exported gates had one */
        public Map<Dropped, Integer> getDropped() { return dropped; }
    }

    /** Static helpers only. */
    private LegacyDatabaseExporter()
    {
    }

    /**
     * Writes every gate to a new database.
     *
     * @return what happened
     */
    public static Result export()
    {
        return export(LegacyDatabaseImporter.driverAvailable());
    }

    // Package-private so the no-driver refusal can be tested on a server that has one.
    static Result export(final boolean driverAvailable)
    {
        if (!driverAvailable)
        {
            return new Result(null, Problem.NO_DRIVER);
        }
        final File folder = newFolder();
        if (folder == null)
        {
            return new Result(null, Problem.NO_FOLDER, DataLayout.exports().toString());
        }
        final File file = new File(folder, DB_NAME);
        final Result result = new Result(file, null);

        final List<Stargate> gates = new ArrayList<>(StargateManager.getAllGatesUnsorted());
        gates.removeIf(Objects::isNull);
        gates.sort(Comparator.comparing(Stargate::getGateName, String.CASE_INSENSITIVE_ORDER));
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath()))
        {
            createSchema(db);
            db.setAutoCommit(false);
            writeGates(db, gates, result);
            db.commit();
        }
        catch (final SQLException | RuntimeException e)
        {
            // Not a half-made export left for somebody to mistake for a whole one.
            PluginLog.log(Level.WARNING, "Could not write " + file, e);
            discard(folder);
            return new Result(null, Problem.WRITE_FAILED, file.toString(), e.getMessage());
        }
        return result;
    }

    /** Removes the folder this export made, and what it put in it. */
    private static void discard(final File folder)
    {
        final File[] files = folder.listFiles();
        if (files != null)
        {
            for (final File made : files)
            {
                try
                {
                    Files.deleteIfExists(made.toPath());
                }
                catch (final IOException stuck)
                {
                    PluginLog.log(Level.WARNING, "Could not remove " + made, stuck);
                }
            }
        }
        try
        {
            Files.deleteIfExists(folder.toPath());
        }
        catch (final IOException stuck)
        {
            PluginLog.log(Level.WARNING, "Could not remove " + folder, stuck);
        }
    }

    /** A folder named for the moment, and never one that is already there. */
    private static File newFolder()
    {
        final String stamp = LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File folder = new File(DataLayout.exports(), stamp);
        for (int attempt = 2; folder.exists(); attempt++)
        {
            folder = new File(DataLayout.exports(), stamp + "-" + attempt);
        }
        return folder.mkdirs() ? folder : null;
    }

    /** The original's tables, with the extra columns the other fork reads. */
    private static void createSchema(final Connection db) throws SQLException
    {
        try (Statement ddl = db.createStatement())
        {
            ddl.executeUpdate("CREATE TABLE VersionInfo(Version INTEGER)");
            ddl.executeUpdate("CREATE TABLE StargateIndividualPermissions(PlayerName VARCHAR(255),"
                + " Permission VARCHAR(255), CONSTRAINT UNIQUE_PNAME UNIQUE (PlayerName))");
            ddl.executeUpdate("CREATE TABLE StargateGroupPermissions(GroupName VARCHAR(255),"
                + " Permission VARCHAR(255), CONSTRAINT UNIQUE_GNAME UNIQUE (GroupName))");
            ddl.executeUpdate("CREATE TABLE Configurations(Key VARCHAR(255), Value VARCHAR(255))");
            ddl.executeUpdate("CREATE TABLE Stargates (Id INTEGER PRIMARY KEY, Name VARCHAR(128) UNIQUE,"
                + " GateData BINARY, Network VARCHAR(255), World VARCHAR(512) DEFAULT '',"
                + " WorldName VARCHAR(255) DEFAULT '', WorldEnvironment VARCHAR(255) DEFAULT '',"
                + " Owner VARCHAR(255), GateShape VARCHAR(255) DEFAULT '',"
                + " Message VARCHAR(1024) DEFAULT '', VisitCount INTEGER DEFAULT 0)");
            // One row per schema step, as a database that grew up through them holds.
        }
        try (PreparedStatement versions = db.prepareStatement("INSERT INTO VersionInfo (Version) VALUES (?)"))
        {
            for (int version = 1; version <= SCHEMA_VERSION; version++)
            {
                versions.setInt(1, version);
                versions.addBatch();
            }
            versions.executeBatch();
        }
    }

    /** Hands out the ids: a gate's own where it has a usable one, else the lowest free, in the order asked. */
    private static final class Ids
    {
        private final Set<Long> taken = new HashSet<>();
        private final Set<Long> assigned = new HashSet<>();
        private long next = 1;

        Ids(final List<Stargate> gates)
        {
            gates.forEach(gate -> taken.add(gate.getGateId()));
        }

        long assign(final long own)
        {
            if ((own > 0) && assigned.add(own))
            {
                return own;
            }
            while (taken.contains(next) || assigned.contains(next))
            {
                next++;
            }
            assigned.add(next);
            return next;
        }
    }

    private static void writeGates(final Connection db, final List<Stargate> gates, final Result result)
        throws SQLException
    {
        final Ids ids = new Ids(gates);
        try (PreparedStatement insert = db.prepareStatement("INSERT INTO Stargates (Id, Name, GateData,"
            + " Network, World, WorldName, WorldEnvironment, Owner, GateShape, VisitCount)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"))
        {
            for (final Stargate gate : gates)
            {
                if (addRow(insert, gate, ids, result))
                {
                    result.exported++;
                    countDropped(gate, ownerName(gate), result.dropped);
                }
            }
            insert.executeBatch();
        }
    }

    /**
     * Queues one gate's row, or says why it cannot be.
     *
     * @return true if the gate was queued
     */
    private static boolean addRow(final PreparedStatement insert, final Stargate gate, final Ids ids,
        final Result result) throws SQLException
    {
        final String shape = canonicalShape(gate.getGateShapeName());
        final String refusal = whyNotExportable(gate, shape);
        if (refusal != null)
        {
            result.skipped.add(gate.getGateName() + ": " + refusal);
            return false;
        }
        final byte[] data = GateSerializer.stargateToLegacyBinary(gate);
        if (data == null)
        {
            result.skipped.add(gate.getGateName() + ": its gate data could not be encoded");
            return false;
        }
        bind(insert, ids.assign(gate.getGateId()), gate, data, shape);
        insert.addBatch();
        return true;
    }

    private static void bind(final PreparedStatement insert, final long id, final Stargate gate,
        final byte[] data, final String shape) throws SQLException
    {
        final World world = gate.getGateWorld();
        insert.setLong(1, id);
        insert.setString(2, gate.getGateName());
        insert.setBytes(3, data);
        insert.setString(4, (gate.getGateNetwork() == null) ? "" : gate.getGateNetwork().getNetworkName());
        // The high half of the world's UUID, which is how the other fork records it.
        insert.setString(5, String.valueOf(world.getUID().getMostSignificantBits()));
        insert.setString(6, world.getName());
        insert.setString(7, String.valueOf(world.getEnvironment()));
        insert.setString(8, ownerName(gate));
        insert.setString(9, shape);
    }

    /**
     * The name the older format keeps for the owner: the stored one, else a legacy name-owner as it
     * is, else the name of the account an id belongs to. Null when none can be found.
     */
    static String ownerName(final Stargate gate)
    {
        final String stored = gate.getStoredGateOwnerName();
        if (stored != null)
        {
            return stored;
        }
        final String owner = gate.getGateOwner();
        if ((owner == null) || !isUuid(owner))
        {
            return owner;
        }
        try
        {
            return Bukkit.getOfflinePlayer(UUID.fromString(owner)).getName();
        }
        catch (final RuntimeException refused)
        {
            // A server that will not look an account up leaves the owner unnamed, and counted.
            return null;
        }
    }

    /** Why this gate cannot be written, or null if it can. */
    private static String whyNotExportable(final Stargate gate, final String shape)
    {
        if (shape == null)
        {
            return "its shape \"" + gate.getGateShapeName() + "\" is not one the older plugins know";
        }
        if (gate.getGateWorld() == null)
        {
            return "its world is not loaded";
        }
        if (gate.getGateName().isEmpty())
        {
            return "no name";
        }
        return null;
    }

    /** @return the shape under the name the older plugins use, or null if they have none of that name */
    static String canonicalShape(final String name)
    {
        for (final String known : KNOWN_SHAPES)
        {
            if (known.equalsIgnoreCase(name))
            {
                return known;
            }
        }
        return null;
    }

    private static void countDropped(final Stargate gate, final String name, final Map<Dropped, Integer> dropped)
    {
        count(dropped, Dropped.USE_COST, gate.getGateUseCost() != null);
        count(dropped, Dropped.DIAL_SPIN, gate.getGateDialSpin() != null);
        count(dropped, Dropped.IRIS_ANIMATION, gate.getGateIrisAnimation() != null);
        count(dropped, Dropped.VIEW, gate.getGateView() != null);
        count(dropped, Dropped.MATERIAL_GROUP, gate.isGateMaterialGroupChosen());
        count(dropped, Dropped.FRAME_MATERIAL, gate.getGateFrameMaterialHint() != null);
        count(dropped, Dropped.DIAL_SIGN_TARGET, gate.getGateDialSignTargetName() != null);
        count(dropped, Dropped.OWNER_UUID, isUuid(gate.getGateOwner()));
        count(dropped, Dropped.OWNER_UNKNOWN, isUuid(gate.getGateOwner()) && (name == null));
    }

    private static void count(final Map<Dropped, Integer> dropped, final Dropped kind, final boolean has)
    {
        if (has)
        {
            dropped.merge(kind, 1, Integer::sum);
        }
    }

    private static boolean isUuid(final String owner)
    {
        if (owner == null)
        {
            return false;
        }
        try
        {
            UUID.fromString(owner);
            return true;
        }
        catch (final IllegalArgumentException name)
        {
            return false;
        }
    }
}
