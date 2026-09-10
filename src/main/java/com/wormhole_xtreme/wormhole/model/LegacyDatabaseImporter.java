package com.wormhole_xtreme.wormhole.model;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataLayout;

/**
 * Brings gates in from a Wormhole X-Treme database.
 *
 * <p>Every build of this plugin descended from the 2011 original kept its gates in a SQLite
 * database of one table, with each gate held as a binary blob. This fork moved to a file per
 * gate; other forks did not. Someone changing over therefore arrives with a server full of
 * gates and a plugin that cannot see any of them -- and, because the two use the same folder,
 * with their old data sitting right next to the new empty one.
 *
 * <p>The blobs need no new code to read. {@link GateSerializer#parseVersionedData} already
 * understands binary versions 3 through 9, which is the whole history of that format, because
 * this fork inherited the same reader. All this class does is get the rows out and hand each
 * blob over.
 *
 * <p>Nothing is written back to the database and nothing is deleted. If an import goes wrong
 * the original is still there, and running it twice does not double the gates -- names that
 * already exist are skipped rather than replaced.
 */
public final class LegacyDatabaseImporter
{
    /** Where every version of this plugin has kept that database. */
    private static final String DB_NAME = "WormholeXTreme.sqlite";

    /** The JDBC driver, if the server has one. */
    private static final String DRIVER = "org.sqlite.JDBC";

    /** What one attempt at importing came to. */
    public static final class Result
    {
        private final int imported;
        private final List<String> skipped = new ArrayList<>();
        private final String problem;
        private int movedExits;

        Result(final int imported, final String problem)
        {
            this.imported = imported;
            this.problem = problem;
        }

        /** @return how many gates came across */
        public int getImported() { return imported; }

        /** @return the gates that did not, and why, one line each */
        public List<String> getSkipped() { return skipped; }

        /** @return what stopped the import entirely, or null if it ran */
        public String getProblem() { return problem; }

        /**
         * @return how many imported gates had an exit point old enough to sit inside the
         *         portal, and were moved clear of it -- the same count
         *         StargateYamlManager.loadStargates reports for the same reason
         */
        public int getMovedExits() { return movedExits; }
    }

    /** Static helpers only. */
    private LegacyDatabaseImporter()
    {
    }

    /**
     * The database this server would import from, if it has one.
     *
     * @return the file, or null if there is nothing to import
     */
    public static File findDatabase()
    {
        final File dir = DataLayout.legacyData();
        final File db = new File(dir, DB_NAME);
        return db.isFile() ? db : null;
    }

    /**
     * Whether this server should be told an import is available.
     *
     * <p>Only when there is a database and no gates of our own. Somebody who has already
     * imported, or who built their gates here, does not need telling about a file they are
     * not using -- and saying it on every startup for ever is how a useful message becomes
     * one nobody reads.
     *
     * @return true if the notice is worth printing
     */
    public static boolean shouldOffer()
    {
        return (findDatabase() != null) && StargateManager.getAllGatesUnsorted().isEmpty();
    }

    /**
     * Whether this server can read a SQLite database at all.
     *
     * <p>Not shipped with this plugin, deliberately: the driver is around thirteen megabytes
     * because it carries native libraries for every platform, and making every server
     * download that for a one-time import most will never run is a poor trade.
     *
     * <p>It costs nothing to leave out, either. Anyone holding gates in one of these
     * databases was necessarily running a server where this driver loaded, because the plugin
     * that wrote it needed the same one. Where the import is wanted, it is already there.
     *
     * @return true if a driver is available
     */
    public static boolean driverAvailable()
    {
        try
        {
            Class.forName(DRIVER);
            return true;
        }
        catch (final ClassNotFoundException | LinkageError absent)
        {
            return false;
        }
    }

    /**
     * Reads every gate out of the database and saves it in this fork's own format.
     *
     * @return what happened
     */
    // S6905 objects to SELECT *. It stays: Owner arrived in schema 5 and WorldName in 4, so
    // naming the columns would fail the whole import against an older database rather than
    // read what is there. Said again at the query itself, which is where it bites.
    @SuppressWarnings("java:S6905")
    public static Result importGates()
    {
        final File db = findDatabase();
        if (db == null)
        {
            return new Result(0, "No " + DB_NAME + " to import from.");
        }
        if (!driverAvailable())
        {
            return new Result(0, "This server has no SQLite driver, so the database cannot be "
                + "opened. The plugin that wrote it needed one too, so if these gates were "
                + "made here it should be present -- check that the old plugin is not still "
                + "installed and shading it away.");
        }

        int imported = 0;
        final List<String> skipped = new ArrayList<>();
        // A one-element holder rather than a second return value: importOne already
        // returns the skip reason (or null for success), and this rides along with it.
        final int[] movedExits = { 0 };
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + db.getAbsolutePath()))
        {
            // Only the columns this fork can use. Owner arrived in schema 5 and WorldName in
            // 4, so both may be absent on an old enough database -- read defensively rather
            // than naming them in the query and failing the whole import over a column.
            // That is what S6905 objects to, and why it does not apply: naming the columns
            // is exactly the failure mode this avoids.
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM Stargates;");
                ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    if (importOneQuietly(rows, movedExits, skipped))
                    {
                        imported++;
                    }
                }
            }
        }
        catch (final Exception e)
        {
            return new Result(imported, "Could not read " + DB_NAME + ": " + e.getMessage());
        }

        final Result done = new Result(imported, null);
        done.getSkipped().addAll(skipped);
        done.movedExits = movedExits[0];
        return done;
    }

    /**
     * Imports one row, and keeps going if that one row cannot be read.
     *
     * <p>Its own method rather than a try inside the query's try-with-resources: one
     * unreadable gate is not a reason to abandon the rest of the database, and the reason it
     * was skipped is worth reporting per gate.
     *
     * @param rows
     *            the result set, positioned on the row to import
     * @param movedExits
     *            a one-element counter, incremented when a gate's exit had to be moved
     * @param skipped
     *            collects a line per gate that was not imported, and why
     * @return true if the gate was imported
     * @throws java.sql.SQLException
     *             if the row cannot be read
     */
    private static boolean importOneQuietly(final ResultSet rows, final int[] movedExits,
        final List<String> skipped)
        throws java.sql.SQLException
    {
        final String name = column(rows, "Name");
        try
        {
            final String outcome = importOne(rows, name, movedExits);
            if (outcome == null)
            {
                return true;
            }
            skipped.add(name + ": " + outcome);
        }
        catch (final RuntimeException oneGate)
        {
            // One unreadable gate is not a reason to abandon the rest.
            skipped.add(name + ": " + oneGate.getMessage());
        }
        return false;
    }

    /**
     * Brings one row across.
     *
     * @param rows
     *            the result set, positioned on the gate
     * @param name
     *            the gate's name
     * @param movedExits
     *            a one-element counter, incremented when this gate's exit point had to be
     *            moved clear of the portal
     * @return null if it was imported, or why it was not
     * @throws java.sql.SQLException
     *             if the row cannot be read
     */
    // Package-private, not private: the four checks that run before Bukkit.getWorld are
    // exercised directly by LegacyImportTest, the same reason BeamCommand.resolveDestination
    // is. Everything past the world lookup needs a live server and stays uncovered.
    static String importOne(final ResultSet rows, final String name,
        final int[] movedExits)
        throws java.sql.SQLException
    {
        final byte[] data = rows.getBytes("GateData");
        final String worldName = column(rows, "WorldName");
        final String refusal = whyNotImportable(name, data, worldName);
        if (refusal != null)
        {
            return refusal;
        }

        final Stargate gate = readGate(rows, name, data, Bukkit.getWorld(worldName));
        if (gate == null)
        {
            return "the stored gate could not be read";
        }
        settleImportedGate(rows, gate, movedExits);
        return null;
    }

    /**
     * Why this row cannot become a gate, if it cannot.
     *
     * <p>Every one of these returns a reason rather than throwing, because one unreadable row
     * must not abandon the rest of somebody's database -- and the reason is what they are
     * told about that gate afterwards.
     *
     * @param name
     *            the gate's name as the row holds it
     * @param data
     *            the stored gate blob
     * @param worldName
     *            the world the row names, which may be absent on an old enough schema
     * @return the reason to skip it, or null if it can be imported
     */
    private static String whyNotImportable(final String name, final byte[] data, final String worldName)
    {
        if ((name == null) || name.isEmpty())
        {
            return "no name";
        }
        if (StargateManager.getStargate(name) != null)
        {
            return "a gate of that name is already here";
        }
        // Empty as well as null: a zero-length blob parses to nothing, and importing it would
        // register a gate with no blocks that looks present and does nothing.
        if ((data == null) || (data.length == 0))
        {
            return "no gate data";
        }
        if ((worldName == null) || worldName.isEmpty())
        {
            return "no world recorded";
        }
        if (Bukkit.getWorld(worldName) == null)
        {
            return "world \"" + worldName + "\" is not loaded";
        }
        return null;
    }

    /**
     * Parses the stored gate, onto its network if the row names one.
     *
     * @param rows
     *            the result set, positioned on the row
     * @param name
     *            the gate's name
     * @param data
     *            the stored gate blob
     * @param world
     *            the world it belongs in
     * @return the gate, or null if the blob would not parse
     */
    private static Stargate readGate(final ResultSet rows, final String name, final byte[] data,
        final World world)
    {
        final String networkName = column(rows, "Network");
        final StargateNetwork network = ((networkName == null) || networkName.isEmpty())
            ? null : StargateManager.addStargateNetwork(networkName);
        return GateSerializer.parseVersionedData(data, world, name, network);
    }

    /**
     * Everything a freshly parsed gate needs before it counts as imported.
     *
     * @param rows
     *            the result set, positioned on the row
     * @param gate
     *            the parsed gate
     * @param movedExits
     *            a one-element counter, incremented when the exit had to be moved
     */
    private static void settleImportedGate(final ResultSet rows, final Stargate gate,
        final int[] movedExits)
    {
        final String owner = column(rows, "Owner");
        if ((owner != null) && !owner.isEmpty())
        {
            // Old databases hold a player name where this fork now holds a uuid. The gate
            // model already carries that distinction for gates written before the uuid
            // migration, so it is set as a name and left to resolve itself.
            gate.setGateOwnerName(owner);
        }
        final String networkName = column(rows, "Network");
        if ((networkName != null) && !networkName.isEmpty())
        {
            StargateManager.addGateToNetwork(gate, networkName);
        }
        // The same fix StargateYamlManager.loadStargates() applies to every gate it reads: an
        // exit point old enough to predate this check can sit inside the portal itself, which
        // is exactly the shape of gate these databases hold. Without this, an imported gate
        // would still land travellers in the water they have to swim out of, even though
        // every gate loaded the normal way is guaranteed clear of it.
        if (gate.normalizeGatePlayerTeleportLocation())
        {
            movedExits[0]++;
        }
        StargateManager.addStargate(gate);
        StargateDBManager.saveStargate(gate);
    }

    /**
     * Reads a column that may not exist on an older schema.
     *
     * @param rows
     *            the result set
     * @param name
     *            the column
     * @return its value, or null if the column is not there
     */
    private static String column(final ResultSet rows, final String name)
    {
        try
        {
            return rows.getString(name);
        }
        catch (final java.sql.SQLException notInThisSchema)
        {
            return null;
        }
    }

    /**
     * Says once, on startup, that there is something to import.
     */
    public static void announceIfWorthwhile()
    {
        if (!shouldOffer())
        {
            return;
        }
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if (plugin == null)
        {
            return;
        }
        plugin.prettyLog(Level.INFO,
            "Found " + DB_NAME + " from an older Wormhole X-Treme and no gates of our own. "
                + "Run /wormhole gate import to bring them across. Nothing is changed until "
                + "you do, and the old database is never written to.");
    }
}
