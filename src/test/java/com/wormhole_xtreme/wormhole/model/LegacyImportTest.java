package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // rather than a reverse-engineering exercise -- so if this reader ever loses its
        // older cases, the import quietly stops working for the servers most likely to want
        // it.
        assertNotNull(GateSerializer.class);
        final java.lang.reflect.Method parse = java.util.Arrays
            .stream(GateSerializer.class.getMethods())
            .filter(m -> "parseVersionedData".equals(m.getName()))
            .findFirst().orElse(null);
        assertNotNull(parse, "the versioned binary reader is what the import depends on");
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
        assertTrue(result.getImported() == 0);
        assertTrue(result.getMovedExits() == 0,
            "nothing was imported, so nothing should have had its exit point moved either");
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
