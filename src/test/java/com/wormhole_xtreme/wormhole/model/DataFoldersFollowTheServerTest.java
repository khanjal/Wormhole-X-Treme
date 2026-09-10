package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;

/**
 * Every store lands in the same plugin folder as every other one.
 *
 * <p>{@code PluginDirectoryTest} covers the resolver. This covers the callers, which is where
 * the bug actually lived: gates, rings, beam and the legacy database asked the server where
 * the plugin folder was, and shapes did not. On a stock install both answers are
 * {@code plugins/WormholeXTreme}, so nothing ever looked wrong. Move the server's plugin
 * folder and the two halves separate, silently, with gates naming shapes that are being read
 * from somewhere else entirely.
 *
 * <p>The assertion is deliberately about agreement rather than about any one path. A future
 * store that builds its own path by hand will pass a test that only checks it ends in the
 * right name, and fail this one.
 */
class DataFoldersFollowTheServerTest
{
    @TempDir
    File dataFolder;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    /** The store that was wrong: shapes used to ignore the server entirely. */
    @Test
    void shapesAreReadFromTheServersPluginFolder()
    {
        assertEquals(new File(new File(dataFolder, "shapes"), "gate"),
            StargateShapeRegistry.shapeDirectory(),
            "shapes read from anywhere but the plugin folder leave every gate built from a "
            + "custom shape undetectable, with nothing in the log to say why");
    }

    /** The folder shapes are migrated out of has to be found the same way. */
    @Test
    void theOldShapeFolderIsLookedForInTheServersPluginFolderToo()
    {
        assertEquals(new File(dataFolder, "GateShapes"),
            StargateShapeRegistry.legacyShapeDirectory(),
            "a migration that looks in the working directory finds nothing to move, and an "
            + "upgrading server loses its custom shapes with nothing in the log to say why");
    }

    /** Gates, which were already right, and are what shapes have to agree with. */
    @Test
    void gatesAreReadFromTheServersPluginFolder()
    {
        assertEquals(new File(new File(dataFolder, "WormholeXTremeDB"), "gates"),
            StargateYamlManager.getGatesDir(),
            "gates and the shapes they name have to come from one tree");
    }

    @Test
    void ringsAreReadFromTheServersPluginFolder()
    {
        assertEquals(new File(new File(dataFolder, "WormholeXTremeDB"), "rings"),
            RingYamlManager.getRingsDir(),
            "rings written to one tree and read from another would look like a wiped world");
    }

    @Test
    void beamDestinationsAreReadFromTheServersPluginFolder()
    {
        assertEquals(new File(new File(dataFolder, "WormholeXTremeDB"), "beam.yml"),
            BeamYamlManager.getBeamFile(),
            "beam destinations are one file, so the wrong tree loses all of them at once");
    }

    /**
     * The importer looks in the server's plugin folder too.
     *
     * <p>It only reports a database it can actually see, so the file has to be there for this
     * to say anything -- an unconditional null would otherwise pass whatever the path was.
     */
    @Test
    void theLegacyDatabaseIsLookedForInTheServersPluginFolder() throws Exception
    {
        final File legacyDir = new File(dataFolder, "WormholeXTremeDB");
        assertTrue(legacyDir.mkdirs(), "could not create the legacy directory for the test");
        final File db = new File(legacyDir, "WormholeXTreme.sqlite");
        assertTrue(db.createNewFile(), "could not create the legacy database for the test");

        final File found = LegacyDatabaseImporter.findDatabase();
        assertNotNull(found,
            "an importer looking in the working directory finds nothing on a server whose "
            + "plugin folder is elsewhere, and silently offers no import at all");
        assertEquals(db, found, "the importer and the gates it imports into must share a folder");
    }
}
