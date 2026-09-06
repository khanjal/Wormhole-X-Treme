package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Filling an empty shapes folder, and reading what is in it.
 *
 * <p>On a first run there is no folder and no shapes, so the shipped ones are written out of
 * the jar. Nothing covered that end to end: a shape missing from the jar, or a name in the
 * restore list that does not match a resource, would leave a server with fewer gate shapes
 * than it should have and only a warning in the log to say so.
 */
class ShapeLoadingTest
{
    @TempDir
    File tempDir;

    private Map<String, StargateShape> savedShapes;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        // The registry is static and shared, so put back whatever the rest of the suite had.
        savedShapes = new HashMap<String, StargateShape>(StargateShapeRegistry.getStargateShapes());
        StargateShapeRegistry.getStargateShapes().clear();
    }

    @AfterEach
    void tearDown()
    {
        StargateShapeRegistry.getStargateShapes().clear();
        StargateShapeRegistry.getStargateShapes().putAll(savedShapes);
    }

    private File shapesDir()
    {
        return new File(tempDir, "GateShapes");
    }

    /**
     * An empty folder is filled from the jar, and every restored file is then read back.
     *
     * <p>The two halves have to agree: a name in the restore list that does not match a
     * resource in the jar writes nothing, and the shape it names is simply absent afterwards.
     */
    @Test
    void anEmptyFolderIsFilledFromTheJarAndRead()
    {
        StargateShapeRegistry.loadShapes(shapesDir());

        assertTrue(shapesDir().isDirectory(), "the folder is created on a first run");
        assertNotNull(StargateShapeRegistry.getStargateShape("Standard"),
            "Standard is one of the shapes shipped in the jar");
        assertTrue(StargateShapeRegistry.getStargateShapes().size() >= 8,
            "every shipped shape should have been restored and read, got "
                + StargateShapeRegistry.getStargateShapes().keySet());
    }

    /** A shape already on disk is kept as it is rather than overwritten by the shipped one. */
    @Test
    void aShapeAlreadyOnDiskIsNotOverwritten() throws Exception
    {
        shapesDir().mkdirs();
        final File standard = new File(shapesDir(), "Standard.shape");
        Files.write(standard.toPath(),
            // The trailing blank line matters: the layer reader looks at the line after each
            // row, so a file ending on its last row walks off the end.
            "Name=NotTheShippedOne\nVersion=2\nGateShape=\nLayer#1=\n[S][S]\n[S:EP][S]\n\n"
                .getBytes(StandardCharsets.UTF_8));
        final long writtenAt = standard.length();

        StargateShapeRegistry.loadShapes(shapesDir());

        assertTrue(standard.length() == writtenAt, "the operator's own file is left alone");
        assertNotNull(StargateShapeRegistry.getStargateShape("NotTheShippedOne"),
            "and it is the one that gets loaded");
    }

    /**
     * A file that will not parse costs itself and nothing else.
     *
     * <p>The folder is read on startup, so one bad shape taking the rest down would leave a
     * server with no gate shapes at all.
     */
    @Test
    void oneUnreadableShapeDoesNotCostTheRest() throws Exception
    {
        shapesDir().mkdirs();
        Files.write(new File(shapesDir(), "Broken.shape").toPath(),
            "this is not a shape file".getBytes(StandardCharsets.UTF_8));

        StargateShapeRegistry.loadShapes(shapesDir());

        assertNotNull(StargateShapeRegistry.getStargateShape("Standard"),
            "the shipped shapes still load around the broken one");
    }

    /** Files that are not shapes are passed over rather than read. */
    @Test
    void nonShapeFilesArePassedOver() throws Exception
    {
        shapesDir().mkdirs();
        Files.write(new File(shapesDir(), "notes.txt").toPath(),
            "Name=ShouldNotLoad\n".getBytes(StandardCharsets.UTF_8));

        StargateShapeRegistry.loadShapes(shapesDir());

        assertFalse(StargateShapeRegistry.getStargateShapes().containsKey("ShouldNotLoad"),
            "a .txt is not a shape file whatever it says inside");
    }

    /**
     * A shape file that cannot be parsed takes every other shape down with it. This is a bug.
     *
     * <p>The per-file catch handles IOException only, and a malformed grid throws
     * IllegalArgumentException instead, which leaves the loop and the method. One operator's
     * typo in one custom shape and the server has no gate shapes at all -- not even the
     * shipped ones, which are read in the same pass.
     *
     * <p>Pinned as it stands so the fix that follows shows the change.
     */
    @Test
    void aMalformedShapeCurrentlyTakesDownTheWholeLoad() throws Exception
    {
        shapesDir().mkdirs();
        Files.write(new File(shapesDir(), "Malformed.shape").toPath(),
            "Name=Malformed\nVersion=2\nGateShape=\n\n".getBytes(StandardCharsets.UTF_8));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> StargateShapeRegistry.loadShapes(shapesDir()),
            "one bad file should not be able to do this, and currently is");
    }
}
