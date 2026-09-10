package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * An upgrading server's shapes end up where the loader now looks.
 *
 * <p>Gate shapes moved from {@code GateShapes/} to {@code shapes/gate/}, so a mirror shape has
 * somewhere to go that is not a folder named for gates. The whole risk of that move is the
 * upgrade: a server whose custom shapes stay behind in the old folder finds them silently gone
 * and every gate built from one undetectable, with nothing in the log to explain it. That is
 * exactly what happened the last time this folder was reorganised, which is why the
 * {@code 3d}/{@code 2d} lift exists at all.
 *
 * <p>The awkward case is that both reorganisations can be outstanding at once. A server old
 * enough to still have {@code GateShapes/3d/} has two hops to make, not one, and it gets no
 * second chance -- whatever is not moved on this startup is not going to be.
 */
class ShapeFolderMigrationTest
{
    @TempDir
    File pluginFolder;

    private File legacy;
    private File target;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        legacy = new File(pluginFolder, "GateShapes");
        target = new File(new File(pluginFolder, "shapes"), "gate");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private void write(final File dir, final String name, final String body) throws Exception
    {
        dir.mkdirs();
        Files.write(new File(dir, name).toPath(), body.getBytes(StandardCharsets.UTF_8));
    }

    private void migrate()
    {
        StargateShapeRegistry.migrateLegacyShapeFolder(legacy, target);
    }

    private static String read(final File file) throws Exception
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** The ordinary upgrade: one hop, from the old flat folder to the new one. */
    @Test
    void aShapeInTheOldFolderMovesToTheNewOne() throws Exception
    {
        write(legacy, "Custom.shape", "Name=Custom");

        migrate();

        assertTrue(new File(target, "Custom.shape").isFile(),
            "the shape should be where the loader now looks");
        assertFalse(new File(legacy, "Custom.shape").isFile(),
            "and moved rather than copied, so there is only one of it to edit");
    }

    /**
     * Both reorganisations at once: {@code GateShapes/3d/} to {@code shapes/gate/} in one run.
     *
     * <p>This is the case that needs the two lifts chained rather than either one alone. A
     * server upgrading from far enough back has a shape two folders away from where it is now
     * read, and only this startup to make the journey.
     */
    @Test
    void aShapeStillInTheOldThreeDeeFolderMakesBothHops() throws Exception
    {
        write(new File(legacy, "3d"), "Ancient.shape", "Name=Ancient");

        migrate();

        assertTrue(new File(target, "Ancient.shape").isFile(),
            "a shape two reorganisations behind still has to arrive, or its gates go dead");
    }

    /** And the 2d folder, which had the same treatment. */
    @Test
    void aShapeStillInTheOldTwoDeeFolderMakesBothHopsToo() throws Exception
    {
        write(new File(legacy, "2d"), "Flat.shape", "Name=Flat");

        migrate();

        assertTrue(new File(target, "Flat.shape").isFile());
    }

    /**
     * A shape already at the destination is not replaced.
     *
     * <p>Same rule the {@code 3d}/{@code 2d} lift established. The file in the new folder is
     * the one that has been loading; overwriting it with an older copy left in the previous
     * folder would quietly undo whatever its owner had changed.
     */
    @Test
    void aShapeAlreadyInTheNewFolderWins() throws Exception
    {
        write(target, "Standard.shape", "Name=Standard current");
        write(legacy, "Standard.shape", "Name=Standard stale");

        migrate();

        assertEquals("Name=Standard current", read(new File(target, "Standard.shape")),
            "the shape in use must not be replaced by the one left in the old folder");
    }

    /** Nothing is deleted, so putting an older jar back still finds the shapes. */
    @Test
    void theShapeThatLostIsLeftWhereItWas() throws Exception
    {
        write(target, "Standard.shape", "Name=Standard current");
        write(legacy, "Standard.shape", "Name=Standard stale");

        migrate();

        assertTrue(new File(legacy, "Standard.shape").isFile(),
            "an operator rolling back to an older jar should still find their old folder intact");
    }

    /** Files that are not shapes are somebody's own; they stay put. */
    @Test
    void anythingThatIsNotAShapeIsLeftAlone() throws Exception
    {
        write(legacy, "Custom.shape", "Name=Custom");
        write(legacy, "notes.txt", "my notes");

        migrate();

        assertFalse(new File(target, "notes.txt").isFile(),
            "only .shape files are the loader's business");
        assertTrue(new File(legacy, "notes.txt").isFile(),
            "and the file should still be where its owner put it");
    }

    /** A new install has no old folder at all. */
    @Test
    void noOldFolderIsNotAProblem()
    {
        assertDoesNotThrow(this::migrate);
        assertFalse(target.exists(),
            "with nothing to migrate there is nothing to create yet; loadShapes makes the folder");
    }

    /**
     * An already-migrated server is not churned on every startup.
     *
     * <p>This runs on every single start, for ever, not just the one that needed it. An
     * operator who migrated months ago is typically left with an empty {@code GateShapes}
     * folder, and that must not mean directories being created or lines being logged each
     * time.
     */
    @Test
    void anEmptyOldFolderDoesNothing()
    {
        legacy.mkdirs();

        migrate();

        assertFalse(target.exists(), "nothing to move means nothing to create");
    }

    /** Running it again after a successful migration changes nothing. */
    @Test
    void runningItTwiceChangesNothingTheSecondTime() throws Exception
    {
        write(legacy, "Custom.shape", "Name=Custom");

        migrate();
        migrate();

        assertTrue(new File(target, "Custom.shape").isFile());
        assertEquals("Name=Custom", read(new File(target, "Custom.shape")));
    }
}
