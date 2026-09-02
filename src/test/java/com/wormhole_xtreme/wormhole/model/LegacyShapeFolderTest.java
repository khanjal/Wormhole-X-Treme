package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Shapes left in the old {@code 3d} and {@code 2d} folders are brought up to the top.
 *
 * <p>Shapes were once split between those two folders. They are read from one flat directory
 * now, so anything still in a subfolder is not seen at all — and the way that presents is a
 * server upgrading, finding its custom shapes gone, its gates undetectable, and no error
 * anywhere saying why.
 */
public class LegacyShapeFolderTest
{
    @TempDir
    File gateShapes;

    @BeforeEach
    public void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
    }

    private File writeShape(final String subdirectory, final String name, final String body) throws Exception
    {
        final File dir = (subdirectory == null) ? gateShapes : new File(gateShapes, subdirectory);
        dir.mkdirs();
        final File file = new File(dir, name);
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** Runs the migration, which is private and called on the way into loadShapes. */
    private void migrate() throws Exception
    {
        final java.lang.reflect.Method m = StargateShapeRegistry.class
            .getDeclaredMethod("liftShapesOutOfLegacySubdirectories", File.class);
        m.setAccessible(true);
        m.invoke(null, gateShapes);
    }

    @Test
    public void aShapeInTheOldThreeDeeFolderIsMovedUp() throws Exception
    {
        writeShape("3d", "Custom.shape", "Name=Custom");

        migrate();

        assertTrue(new File(gateShapes, "Custom.shape").isFile(),
            "the shape should now be where the loader actually looks");
        assertFalse(new File(gateShapes, "3d/Custom.shape").isFile(),
            "and should not be left behind in both places");
    }

    @Test
    public void theOldTwoDeeFolderIsHandledToo() throws Exception
    {
        writeShape("2d", "Flat.shape", "Name=Flat");

        migrate();

        assertTrue(new File(gateShapes, "Flat.shape").isFile());
    }

    @Test
    public void aShapeAlreadyAtTheTopWins() throws Exception
    {
        // The top-level copy is the one that has been loading. Overwriting it with an older
        // copy from a subfolder would quietly undo whatever its owner had changed.
        writeShape(null, "Standard.shape", "Name=Standard current");
        writeShape("3d", "Standard.shape", "Name=Standard stale");

        migrate();

        assertEquals("Name=Standard current",
            new String(Files.readAllBytes(new File(gateShapes, "Standard.shape").toPath()),
                StandardCharsets.UTF_8),
            "the shape in use must not be replaced by the one in the old folder");
    }

    @Test
    public void anythingThatIsNotAShapeIsLeftAlone() throws Exception
    {
        writeShape("3d", "notes.txt", "just a note");

        migrate();

        assertFalse(new File(gateShapes, "notes.txt").isFile(),
            "only .shape files are the loader's business");
        assertTrue(new File(gateShapes, "3d/notes.txt").isFile(),
            "and the file should still be where its owner put it");
    }

    @Test
    public void noLegacyFoldersIsNotAProblem() throws Exception
    {
        writeShape(null, "Standard.shape", "Name=Standard");

        assertDoesNotThrow(this::migrate);
        assertTrue(new File(gateShapes, "Standard.shape").isFile());
    }

    @Test
    public void runningItTwiceChangesNothingTheSecondTime() throws Exception
    {
        // It runs on every startup, so it has to be safe to run against an already-migrated
        // install rather than only against the one upgrade that needed it.
        writeShape("3d", "Custom.shape", "Name=Custom");

        migrate();
        migrate();

        assertTrue(new File(gateShapes, "Custom.shape").isFile());
    }
}
