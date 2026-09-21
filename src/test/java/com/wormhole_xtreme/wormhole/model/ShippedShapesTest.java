package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A server's copy of a bundled shape is brought up to date only if nobody edited it.
 *
 * <p>Shapes were written out once and never touched again, so a server that upgraded kept the
 * old geometry and light order however many releases went by.
 */
class ShippedShapesTest
{
    @TempDir
    File dir;

    private Map<String, StargateShape> savedShapes;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        savedShapes = new HashMap<>(StargateShapeRegistry.getStargateShapes());
        StargateShapeRegistry.getStargateShapes().clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateShapeRegistry.getStargateShapes().clear();
        StargateShapeRegistry.getStargateShapes().putAll(savedShapes);
        PluginTestSupport.remove();
    }

    private static String oldLarge() throws Exception
    {
        try (InputStream is = ShippedShapesTest.class.getResourceAsStream("/shapes/old/Large-1.6.0.shape"))
        {
            assertNotNull(is, "the 1.6.0 Large fixture should be on the test classpath");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String read(final String name) throws Exception
    {
        return Files.readString(new File(dir, name).toPath(), StandardCharsets.UTF_8);
    }

    /**
     * Every bundled shape as it ships now is on the list, so the version after this one still
     * recognises it as untouched. Changing a shape without regenerating the list fails here.
     */
    @Test
    void everyBundledShapeIsOnTheShippedList()
    {
        final Set<String> shipped = ShippedShapes.shippedVersions();
        for (final String name : ShippedShapes.NAMES)
        {
            final String bundled = ShippedShapes.bundled(name);
            assertNotNull(bundled, name + " should be in the jar");
            assertTrue(shipped.contains(name + " " + ShippedShapes.hash(bundled)),
                name + " as it ships now is missing from shipped-shapes.txt; run scripts/shipped_shapes.py");
        }
    }

    /** 1.6.0's Large, saved with Windows line endings, is replaced on load and kept beside it. */
    @Test
    void anUntouchedOlderShapeIsReplacedAndKept() throws Exception
    {
        final String old = oldLarge().replace("\r\n", "\n").replace("\n", "\r\n");
        dir.mkdirs();
        Files.writeString(new File(dir, "Large.shape").toPath(), old, StandardCharsets.UTF_8);

        StargateShapeRegistry.loadShapes(dir);

        assertEquals(ShippedShapes.bundled("Large.shape"), read("Large.shape"));
        assertEquals(old, read("Large.shape" + ShippedShapes.BACKUP_SUFFIX));
    }

    /** A line of notes makes it somebody's shape, and it is left alone. */
    @Test
    void anEditedShapeIsLeftAsItIs() throws Exception
    {
        final String edited = oldLarge() + "# my notes\n";
        Files.writeString(new File(dir, "Large.shape").toPath(), edited, StandardCharsets.UTF_8);

        ShippedShapes.updateUntouched(dir);

        assertEquals(edited, read("Large.shape"));
        assertFalse(new File(dir, "Large.shape" + ShippedShapes.BACKUP_SUFFIX).exists());
    }

    /** A copy already matching this version is not rewritten, and gets no backup. */
    @Test
    void aCurrentShapeIsNotTouched() throws Exception
    {
        final String current = ShippedShapes.bundled("Standard.shape");
        Files.writeString(new File(dir, "Standard.shape").toPath(), current, StandardCharsets.UTF_8);

        ShippedShapes.updateUntouched(dir);

        assertEquals(current, read("Standard.shape"));
        assertFalse(new File(dir, "Standard.shape" + ShippedShapes.BACKUP_SUFFIX).exists());
    }

    /**
     * A new copy that cannot be written leaves the old one where it was.
     *
     * <p>The old copy used to be moved aside first and the new one written after, so a failed
     * write -- a full disk, a file held open on Windows -- left no shape there at all. A folder
     * standing where the new copy would be written makes that write fail here.
     */
    @Test
    void aShapeThatCannotBeRewrittenIsLeftWhereItWas() throws Exception
    {
        final String old = oldLarge();
        Files.writeString(new File(dir, "Large.shape").toPath(), old, StandardCharsets.UTF_8);
        final File blocked = new File(dir, "Large.shape" + ShippedShapes.INCOMING_SUFFIX);
        assertTrue(blocked.mkdirs());
        assertTrue(new File(blocked, "in-the-way").createNewFile());

        ShippedShapes.updateUntouched(dir);

        assertEquals(old, read("Large.shape"), "the old copy is still in place");
        assertFalse(new File(dir, "Large.shape" + ShippedShapes.BACKUP_SUFFIX).exists(),
            "and was never moved aside");
    }

    /**
     * Somebody's own .old is never overwritten; that shape is left for this start instead.
     *
     * <p>.old is the name the update log line tells admins about, so an edited copy saved under it
     * is a likely thing to find. Only a .old that is itself a shipped version is the plugin's own.
     */
    @Test
    void anAdminsOwnBackupIsNotOverwritten() throws Exception
    {
        final String old = oldLarge();
        final String theirs = old + "# my own version\n";
        Files.writeString(new File(dir, "Large.shape").toPath(), old, StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "Large.shape" + ShippedShapes.BACKUP_SUFFIX).toPath(), theirs,
            StandardCharsets.UTF_8);

        ShippedShapes.updateUntouched(dir);

        assertEquals(theirs, read("Large.shape" + ShippedShapes.BACKUP_SUFFIX), "their backup is untouched");
        assertEquals(old, read("Large.shape"), "and the shape is left for this start");
    }

    /** An edited shape is named in the log, which is how an admin learns why it was not updated. */
    @Test
    void anEditedShapeIsNamedInTheLog() throws Exception
    {
        Files.writeString(new File(dir, "Large.shape").toPath(), oldLarge() + "# my notes\n", StandardCharsets.UTF_8);

        ShippedShapes.updateUntouched(dir);

        verify(WormholeXTreme.getThisPlugin()).prettyLog(eq(java.util.logging.Level.INFO),
            contains("Large.shape has been edited"));
    }

    /** With no shipped list to go on, nothing is replaced: every copy would look edited. */
    @Test
    void withNoShippedListNothingIsReplaced() throws Exception
    {
        final String old = oldLarge();
        Files.writeString(new File(dir, "Large.shape").toPath(), old, StandardCharsets.UTF_8);

        ShippedShapes.updateUntouched(dir, java.util.Set.of());

        assertEquals(old, read("Large.shape"));
        assertFalse(new File(dir, "Large.shape" + ShippedShapes.BACKUP_SUFFIX).exists());
    }

    /** A jar without the list reads as an empty one, not a failure. */
    @Test
    void aMissingShippedListReadsAsEmpty()
    {
        assertTrue(ShippedShapes.shippedVersions(null).isEmpty());
    }

    /** A list that cannot be read is logged, and what was read before the failure is kept. */
    @Test
    void anUnreadableShippedListIsLoggedAndKeepsWhatItRead()
    {
        final java.io.InputStream failing = new java.io.InputStream()
        {
            private final byte[] first = "Large.shape abc\n".getBytes(StandardCharsets.UTF_8);
            private int at;

            @Override
            public int read() throws java.io.IOException
            {
                if (at < first.length)
                {
                    return first[at++];
                }
                throw new java.io.IOException("disk gone");
            }
        };

        final Set<String> read = ShippedShapes.shippedVersions(failing);

        assertTrue(read.contains("Large.shape abc"), "the line before the failure is kept: " + read);
        verify(WormholeXTreme.getThisPlugin()).prettyLog(eq(java.util.logging.Level.WARNING),
            contains("shipped shape list"), org.mockito.ArgumentMatchers.any(java.io.IOException.class));
    }
}
