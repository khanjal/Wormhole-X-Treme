package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataLayout;

/**
 * {@code /wormhole gate import} against a real SQLite file, from query to gate file on disk.
 *
 * <p>{@link LegacyImportTest} covers the refusals with a mocked result set, and
 * {@link LegacySaveVersionTest} the blob reader. Neither opened a database, so the JDBC half --
 * finding the file, the driver, {@code SELECT *} over an older schema, reading columns by name
 * -- had never run outside a live server.
 */
class LegacySqliteImportTest
{
    @TempDir
    File pluginFolder;

    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = PluginTestSupport.install();
        when(plugin.getDataFolder()).thenReturn(pluginFolder);

        world = mock(World.class);
        when(world.getName()).thenReturn("gw");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            final int x = call.getArgument(0);
            final int y = call.getArgument(1);
            final int z = call.getArgument(2);
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(x);
            when(b.getY()).thenReturn(y);
            when(b.getZ()).thenReturn(z);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            when(b.getWorld()).thenReturn(world);
            return b;
        });
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        PluginTestSupport.remove();
        PluginTestSupport.forgetAllGates();
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGatesUnsorted()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    /** The database another fork leaves behind, where the importer looks for it. */
    private static Connection database() throws Exception
    {
        final File dir = DataLayout.legacyData();
        assertTrue(dir.mkdirs() || dir.isDirectory());
        return DriverManager.getConnection(
            "jdbc:sqlite:" + new File(dir, "WormholeXTreme.sqlite").getAbsolutePath());
    }

    private static void insert(final Connection db, final String sql, final Object... values)
        throws Exception
    {
        try (PreparedStatement row = db.prepareStatement(sql))
        {
            for (int i = 0; i < values.length; i++)
            {
                row.setObject(i + 1, values[i]);
            }
            row.executeUpdate();
        }
    }

    /** Runs the import with {@code gw} as the only loaded world. */
    private LegacyDatabaseImporter.Result importWithWorldLoaded()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("gw")).thenReturn(world);
            return LegacyDatabaseImporter.importGates();
        }
    }

    /**
     * A gate comes out of the database as a file this fork loads.
     *
     * <p>Read back through {@code loadStargates} rather than the registry, because a gate that
     * imports but was never written would still be in memory and vanish on the next restart.
     */
    @Test
    void aGateInARealDatabaseIsImportedAndSurvivesARestart() throws Exception
    {
        try (Connection db = database(); Statement ddl = db.createStatement())
        {
            // The full schema, with columns this fork does not read, to show SELECT * copes.
            ddl.executeUpdate("CREATE TABLE Stargates (Id INTEGER PRIMARY KEY, Name VARCHAR(32),"
                + " GateData BLOB, Network VARCHAR(32), World VARCHAR(32), WorldName VARCHAR(32),"
                + " WorldEnvironment VARCHAR(32), Owner VARCHAR(32), GateShape VARCHAR(32))");
            insert(db, "INSERT INTO Stargates (Name, GateData, Network, World, WorldName,"
                + " WorldEnvironment, Owner, GateShape) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "Abydos", LegacySaveVersionTest.version3Gate(world), "Milkyway", "gw", "gw",
                "NORMAL", "Daniel", "Standard");
        }

        final LegacyDatabaseImporter.Result result = importWithWorldLoaded();

        assertNull(result.getProblem(), "the import should run; it said: " + result.getProblem());
        assertEquals(1, result.getImported(), "skipped: " + result.getSkipped());
        final File written = new File(DataLayout.gates(), "Abydos.yml");
        assertTrue(written.isFile(),
            "an imported gate that is not written to data/gates is lost on the next restart");

        // Copied aside first: removing a gate from the registry deletes its file too.
        final File restarted = new File(pluginFolder, "restarted");
        assertTrue(restarted.mkdirs());
        Files.copy(written.toPath(), new File(restarted, written.getName()).toPath());
        clearGates();
        final Server server = mock(Server.class);
        when(server.getWorld(anyString())).thenReturn(world);
        StargateYamlManager.loadStargates(server, restarted);

        final Stargate loaded = StargateManager.getStargate("Abydos");
        assertNotNull(loaded, "the written file should load like any gate built here");
        assertEquals("Daniel", loaded.getGateOwnerName(), "the Owner column should come across");
        final Player daniel = mock(Player.class);
        when(daniel.getName()).thenReturn("Daniel");
        when(daniel.getUniqueId()).thenReturn(UUID.randomUUID());
        assertTrue(loaded.isOwner(daniel),
            "the owner must own the gate, not only be shown as its owner, or remove.own fails");
        assertNotNull(loaded.getGateNetwork(), "the Network column should come across");
        assertEquals("Milkyway", loaded.getGateNetwork().getNetworkName());
        assertEquals("letmein", loaded.getGateIrisDeactivationCode(),
            "the blob's own fields should survive the trip into YAML");
    }

    /**
     * An old enough database, without {@code Owner} or {@code Network}, still imports.
     *
     * <p>This is why the importer selects {@code *} instead of naming its columns: naming one
     * that a schema predates would fail the whole query, not just that gate.
     */
    @Test
    void aDatabaseFromBeforeOwnersAndNetworksStillImports() throws Exception
    {
        try (Connection db = database(); Statement ddl = db.createStatement())
        {
            ddl.executeUpdate("CREATE TABLE Stargates (Id INTEGER PRIMARY KEY, Name VARCHAR(32),"
                + " GateData BLOB, WorldName VARCHAR(32))");
            insert(db, "INSERT INTO Stargates (Name, GateData, WorldName) VALUES (?, ?, ?)",
                "Chulak", LegacySaveVersionTest.version3Gate(world), "gw");
        }

        final LegacyDatabaseImporter.Result result = importWithWorldLoaded();

        assertNull(result.getProblem(), "a missing column must not stop the import: "
            + result.getProblem());
        assertEquals(1, result.getImported(), "skipped: " + result.getSkipped());
        assertNotNull(StargateManager.getStargate("Chulak"));
    }

    /**
     * The rows that cannot come across are named, and the rest still do; running it again
     * adds nothing.
     */
    @Test
    void badRowsAreReportedByNameAndASecondRunImportsNothingNew() throws Exception
    {
        try (Connection db = database(); Statement ddl = db.createStatement())
        {
            ddl.executeUpdate("CREATE TABLE Stargates (Id INTEGER PRIMARY KEY, Name VARCHAR(32),"
                + " GateData BLOB, WorldName VARCHAR(32), Owner VARCHAR(32))");
            final String sql = "INSERT INTO Stargates (Name, GateData, WorldName) VALUES (?, ?, ?)";
            insert(db, sql, "Abydos", LegacySaveVersionTest.version3Gate(world), "gw");
            insert(db, sql, "Tollana", LegacySaveVersionTest.version3Gate(world), "unloaded");
            insert(db, sql, "Empty", new byte[0], "gw");
            // Cut short, as a copy interrupted mid-write leaves it: the reader runs off the end.
            insert(db, sql, "Cut", java.util.Arrays.copyOf(LegacySaveVersionTest.version3Gate(world), 40), "gw");
        }

        final LegacyDatabaseImporter.Result first = importWithWorldLoaded();
        assertEquals(1, first.getImported(), "skipped: " + first.getSkipped());
        assertEquals(3, first.getSkipped().size(), "skipped: " + first.getSkipped());
        assertTrue(first.getSkipped().contains("Tollana: world \"unloaded\" is not loaded"),
            "skipped: " + first.getSkipped());
        assertTrue(first.getSkipped().contains("Empty: no gate data"), "skipped: " + first.getSkipped());
        // It used to say "Cut: null", which tells the operator nothing about what to look at.
        assertTrue(first.getSkipped().contains("Cut: its gate data could not be read (BufferUnderflowException)"),
            "skipped: " + first.getSkipped());

        final LegacyDatabaseImporter.Result second = importWithWorldLoaded();
        assertEquals(0, second.getImported(), "a second run must not duplicate gates");
        assertTrue(second.getSkipped().contains("Abydos: a gate of that name is already here"),
            "skipped: " + second.getSkipped());
    }
}
