package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataLayout;

/**
 * {@code /wormhole export sqlite} against a real SQLite file: what goes out is what the importer
 * reads back in (#719).
 *
 * <p>The round trip is the point. An export that wrote a database nothing could read, or one that
 * read back as a different gate, would only be found by an admin who had already gone back to the
 * other plugin.
 */
class LegacyDatabaseExporterTest
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
        when(world.getUID()).thenReturn(new UUID(0x1234L, 0x5678L));
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

    private static File legacyDatabase()
    {
        return new File(DataLayout.legacyData(), "WormholeXTreme.sqlite");
    }

    /** One gate in a database where the importer looks, imported as a gate of this server. */
    private Stargate importedGate(final String name) throws Exception
    {
        return importedGate(name, "Standard");
    }

    private Stargate importedGate(final String name, final String shape) throws Exception
    {
        final File dir = DataLayout.legacyData();
        assertTrue(dir.mkdirs() || dir.isDirectory());
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + legacyDatabase().getAbsolutePath());
            Statement ddl = db.createStatement())
        {
            ddl.executeUpdate("CREATE TABLE Stargates (Id INTEGER PRIMARY KEY, Name VARCHAR(32),"
                + " GateData BLOB, Network VARCHAR(32), WorldName VARCHAR(32), Owner VARCHAR(32),"
                + " GateShape VARCHAR(32))");
            try (PreparedStatement row = db.prepareStatement("INSERT INTO Stargates"
                + " (Name, GateData, Network, WorldName, Owner, GateShape) VALUES (?, ?, ?, ?, ?, ?)"))
            {
                row.setString(1, name);
                row.setBytes(2, LegacySaveVersionTest.version3Gate(world));
                row.setString(3, "Milkyway");
                row.setString(4, "gw");
                row.setString(5, "Daniel");
                row.setString(6, shape);
                row.executeUpdate();
            }
        }
        importWithWorldLoaded();
        final Stargate gate = StargateManager.getStargate(name);
        assertNotNull(gate, "the fixture gate should import");
        return gate;
    }

    private LegacyDatabaseImporter.Result importWithWorldLoaded()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("gw")).thenReturn(world);
            return LegacyDatabaseImporter.importGates();
        }
    }

    /**
     * A gate that came in, went out and came in again is the same gate.
     *
     * <p>Compared by the bytes the current format would save it as, which cover every block, both
     * arrival points, the facing, the iris, the redstone and the custom materials, alongside the
     * fields the columns carry.
     */
    @Test
    void importExportImportGivesTheSameGate() throws Exception
    {
        final Stargate first = importedGate("Abydos", "Large");
        first.setGateCustom(true);
        first.setGateCustomStructureMaterial(Material.OBSIDIAN);
        first.setGateCustomWooshTicks(7);
        final byte[] before = GateSerializer.stargateToBinary(first);

        final LegacyDatabaseExporter.Result out = LegacyDatabaseExporter.export();
        assertNull(out.getProblemKey(), "the export should run; it said: " + out.getProblemKey());
        assertEquals(1, out.getExported(), "skipped: " + out.getSkipped());

        // The exported file is what the other server would be given: put it where the importer looks.
        clearGates();
        Files.copy(out.getFile().toPath(), legacyDatabase().toPath(), StandardCopyOption.REPLACE_EXISTING);
        final LegacyDatabaseImporter.Result back = importWithWorldLoaded();
        assertNull(back.getProblem(), "the exported file should import; it said: " + back.getProblem());
        assertEquals(1, back.getImported(), "skipped: " + back.getSkipped());

        final Stargate second = StargateManager.getStargate("Abydos");
        assertNotNull(second);
        assertArrayEquals(before, GateSerializer.stargateToBinary(second),
            "the gate should come back byte for byte as it left");
        assertEquals("Daniel", second.getGateOwnerName());
        assertEquals("Milkyway", second.getGateNetwork().getNetworkName());
        assertEquals("Large", second.getGateShapeName(), "not the Standard a gate defaults to");
        assertEquals(Material.OBSIDIAN, second.getGateCustomStructureMaterial());
        assertEquals(7, second.getGateCustomWooshTicks());
    }

    /**
     * The blob is version 8, the newest the original plugin reads, and the table carries the
     * columns the other fork reads too.
     */
    @Test
    void theRowIsAVersion8BlobWithTheColumnsBothPluginsRead() throws Exception
    {
        importedGate("Abydos");

        final LegacyDatabaseExporter.Result out = LegacyDatabaseExporter.export();

        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + out.getFile().getAbsolutePath());
            Statement query = db.createStatement();
            ResultSet rows = query.executeQuery("SELECT * FROM Stargates"))
        {
            assertTrue(rows.next());
            assertEquals(8, rows.getBytes("GateData")[0], "version 9 is this fork's own; the others stop at 8");
            assertEquals("Abydos", rows.getString("Name"));
            assertEquals("Milkyway", rows.getString("Network"));
            assertEquals("gw", rows.getString("WorldName"));
            assertEquals("NORMAL", rows.getString("WorldEnvironment"));
            assertEquals("Daniel", rows.getString("Owner"));
            assertEquals("Standard", rows.getString("GateShape"));
            assertEquals("4660", rows.getString("World"), "the high half of the world's UUID");
            assertEquals(0, rows.getInt("VisitCount"));
            assertFalse(rows.next());
        }
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + out.getFile().getAbsolutePath());
            Statement query = db.createStatement();
            ResultSet version = query.executeQuery("SELECT MAX(Version) FROM VersionInfo"))
        {
            assertTrue(version.next());
            assertEquals(8, version.getInt(1));
        }
    }

    /**
     * The export lands in a new folder of its own, and the old database and the live gate files are
     * exactly as they were.
     */
    @Test
    void theExportGoesToItsOwnFolderAndTouchesNothingLive() throws Exception
    {
        importedGate("Abydos");
        final File gateFile = new File(DataLayout.gates(), "Abydos.yml");
        assertTrue(gateFile.isFile());
        final byte[] gateBefore = Files.readAllBytes(gateFile.toPath());
        final byte[] databaseBefore = Files.readAllBytes(legacyDatabase().toPath());

        final LegacyDatabaseExporter.Result out = LegacyDatabaseExporter.export();

        final File inside = out.getFile().getParentFile();
        assertEquals(DataLayout.exports(), inside.getParentFile(), "one new folder under exports/");
        assertFalse(out.getFile().getAbsolutePath().contains("WormholeXTremeDB"),
            "that folder is shared with the other forks and is never written to");
        assertArrayEquals(gateBefore, Files.readAllBytes(gateFile.toPath()), "live gate file untouched");
        assertArrayEquals(databaseBefore, Files.readAllBytes(legacyDatabase().toPath()),
            "the database the gates came from is untouched");

        final LegacyDatabaseExporter.Result again = LegacyDatabaseExporter.export();
        assertTrue(again.getFile().getParentFile().isDirectory());
        assertNotEquals(inside, again.getFile().getParentFile(), "a second export must not write over the first");
    }

    /** A gate on a shape the older plugins lack is named and left out; the rest still go. */
    @Test
    void aGateOnAnUnknownShapeIsSkippedAndNamed() throws Exception
    {
        importedGate("Abydos");
        final Stargate custom = GateSerializer.parseVersionedData(
            LegacySaveVersionTest.version3Gate(world), world, "Chulak", null);
        custom.setGateShapeName("Grand");
        StargateManager.addStargate(custom);

        final LegacyDatabaseExporter.Result out = LegacyDatabaseExporter.export();

        assertEquals(1, out.getExported());
        assertEquals(1, out.getSkipped().size(), "skipped: " + out.getSkipped());
        assertEquals("Chulak: its shape \"Grand\" is not one the older plugins know", out.getSkipped().get(0));
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + out.getFile().getAbsolutePath());
            Statement query = db.createStatement();
            ResultSet rows = query.executeQuery("SELECT Name FROM Stargates"))
        {
            assertTrue(rows.next());
            assertEquals("Abydos", rows.getString(1));
            assertFalse(rows.next(), "the skipped gate must not be in the database");
        }
    }

    /** An imported row's shape is kept, even one no shape file answers to, so the export can name it. */
    @Test
    void anImportedGateKeepsAnUnknownShapeNameAndTheExportSkipsIt() throws Exception
    {
        final Stargate gate = importedGate("Chulak", "Atlantis");

        assertEquals("Atlantis", gate.getGateShapeName());
        final LegacyDatabaseExporter.Result out = LegacyDatabaseExporter.export();
        assertEquals(0, out.getExported());
        assertEquals("Chulak: its shape \"Atlantis\" is not one the older plugins know", out.getSkipped().get(0));
    }

    /** The older plugins match a shape by name without regard to case, so another case is written as theirs. */
    @Test
    void aShapeNamedInAnotherCaseIsWrittenUnderTheirName()
    {
        assertEquals("StandardSignDial", LegacyDatabaseExporter.canonicalShape("standardsigndial"));
        assertEquals("Destiny", LegacyDatabaseExporter.canonicalShape("DESTINY"));
        assertNull(LegacyDatabaseExporter.canonicalShape("Grand"));
    }

    /** Settings this fork keeps per gate that the format cannot hold are counted, not lost without a word. */
    @Test
    void settingsTheFormatCannotHoldAreCounted() throws Exception
    {
        final Stargate gate = importedGate("Abydos");
        gate.setGateUseCost(5.0);
        gate.setGateView("clear");
        gate.setGateOwner(UUID.randomUUID().toString());

        final LegacyDatabaseExporter.Result out = LegacyDatabaseExporter.export();

        assertEquals(1, out.getDropped().get(LegacyDatabaseExporter.Dropped.USE_COST));
        assertEquals(1, out.getDropped().get(LegacyDatabaseExporter.Dropped.VIEW));
        assertEquals(1, out.getDropped().get(LegacyDatabaseExporter.Dropped.OWNER_UUID));
        assertNull(out.getDropped().get(LegacyDatabaseExporter.Dropped.DIAL_SPIN),
            "a setting no gate has is not listed");
    }

    /** Without a driver nothing is written, and the message says why. */
    @Test
    void withoutADriverNothingIsWrittenAndTheReasonIsGiven() throws Exception
    {
        importedGate("Abydos");

        final LegacyDatabaseExporter.Result out = LegacyDatabaseExporter.export(false);

        assertEquals("export.no-driver", out.getProblemKey());
        assertNull(out.getFile());
        assertFalse(DataLayout.exports().exists(), "no folder should be made for an export that cannot run");
    }
}
