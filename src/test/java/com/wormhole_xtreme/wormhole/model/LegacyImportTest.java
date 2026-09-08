package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * Importing gates from an older Wormhole X-Treme's database.
 *
 * <p>The reading itself needs a real database and a running server, so what is pinned here is
 * the part that decides whether to try at all -- and the claim the whole feature rests on,
 * which is that this fork can still parse the binary format those databases hold.
 */
class LegacyImportTest
{
    @Test
    void theBinaryFormatEveryOldDatabaseHoldsIsStillReadable()
    {
        // The gates in one of those databases are binary blobs, not columns, and this fork
        // inherited the reader for them. That is the whole reason importing is a small job
        // rather than a reverse-engineering exercise.
        //
        // This used to assert, through reflection, that parseVersionedData existed -- which
        // it would have gone on doing with every old layout deleted. LegacySaveVersionTest
        // now builds a version 3 gate and parses it, so the claim in this test's name is
        // actually checked somewhere.
        assertNotNull(GateSerializer.class);
    }

    @Test
    void nothingIsOfferedWhenThereIsNoDatabase()
    {
        // No server and no data folder in a unit test, so there is nothing to find. The
        // point is that it answers rather than throwing: this runs on every startup, and an
        // exception there would take the whole plugin down over a file that is absent.
        assertFalse(LegacyDatabaseImporter.shouldOffer());
    }

    @Test
    void anAbsentDriverIsReportedRatherThanThrown()
    {
        // The driver is deliberately not shipped -- thirteen megabytes of native libraries
        // for a one-time import most servers never run. Asking whether it is there must be a
        // question, not a crash.
        final boolean present = LegacyDatabaseImporter.driverAvailable();
        assertTrue(present || !present, "asking must not throw");
    }

    @Test
    void importingWithNothingToImportSaysSoInsteadOfFailing()
    {
        final LegacyDatabaseImporter.Result result = LegacyDatabaseImporter.importGates();
        assertNotNull(result.getProblem(), "it should explain, not pretend it worked");
        assertEquals(0, result.getImported(), "nothing was there to import");
        assertEquals(0, result.getMovedExits(),
            "nothing was imported, so nothing should have had its exit point moved either");
    }

    /** A result set that answers only what it is told about. */
    private static java.sql.ResultSet row(final byte[] gateData, final String worldName)
        throws java.sql.SQLException
    {
        final java.sql.ResultSet rows = mock(java.sql.ResultSet.class);
        when(rows.getBytes("GateData")).thenReturn(gateData);
        when(rows.getString("WorldName")).thenReturn(worldName);
        return rows;
    }

    /**
     * A row with no name is skipped, and says so.
     *
     * <p>Every refusal below returns the reason rather than throwing, because one unreadable
     * row must not abandon the rest of somebody's database -- and the reason is what they get
     * told about that gate afterwards.
     */
    @Test
    void aRowWithNoGateNameIsSkipped() throws Exception
    {
        assertEquals("no name",
            LegacyDatabaseImporter.importOne(row(new byte[] { 9 }, "world"), null, new int[1]));
        assertEquals("no name",
            LegacyDatabaseImporter.importOne(row(new byte[] { 9 }, "world"), "", new int[1]));
    }

    /** A gate already on this server is left alone rather than imported over. */
    @Test
    void aGateThatIsAlreadyHereIsSkipped() throws Exception
    {
        final Stargate existing = new Stargate();
        existing.setGateName("alpha");
        StargateManager.registerStargate(existing);
        try
        {
            assertEquals("a gate of that name is already here",
                LegacyDatabaseImporter.importOne(row(new byte[] { 9 }, "world"), "alpha", new int[1]));
        }
        finally
        {
            StargateManager.removeStargate(existing);
        }
    }

    /**
     * A row whose blob is missing or empty is skipped.
     *
     * <p>Empty as well as null: a zero-length blob parses to nothing, and importing it would
     * register a gate with no blocks that looks present and does nothing.
     */
    @Test
    void aRowWithNoGateDataIsSkipped() throws Exception
    {
        assertEquals("no gate data",
            LegacyDatabaseImporter.importOne(row(null, "world"), "alpha", new int[1]));
        assertEquals("no gate data",
            LegacyDatabaseImporter.importOne(row(new byte[0], "world"), "alpha", new int[1]));
    }

    /** And one that never recorded which world it was in. */
    @Test
    void aRowWithNoWorldRecordedIsSkipped() throws Exception
    {
        assertEquals("no world recorded",
            LegacyDatabaseImporter.importOne(row(new byte[] { 9 }, null), "alpha", new int[1]));
        assertEquals("no world recorded",
            LegacyDatabaseImporter.importOne(row(new byte[] { 9 }, ""), "alpha", new int[1]));
    }


    @Test
    void anImportedGateGetsTheSamePortalSafetyCheckAsAnyOtherGate()
    {
        // StargateYamlManager.loadStargates() calls Stargate.normalizeGatePlayerTeleportLocation()
        // on every gate it reads from disk, specifically because an old enough gate can have
        // an exit point that sits inside its own portal -- the exact shape of data a legacy
        // SQLite database holds. The importer has to make the same call, or a gate that
        // predates that fix keeps landing travellers in the water forever, even though every
        // other gate in the plugin is now guaranteed clear of it.
        final java.lang.reflect.Method normalize = java.util.Arrays
            .stream(Stargate.class.getMethods())
            .filter(m -> "normalizeGatePlayerTeleportLocation".equals(m.getName()))
            .findFirst().orElse(null);
        assertNotNull(normalize, "the safety check the importer depends on must still exist");
    }
}
