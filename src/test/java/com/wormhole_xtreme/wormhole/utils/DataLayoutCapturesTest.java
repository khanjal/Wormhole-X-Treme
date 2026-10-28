package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Captures live under {@code data/captures/}, apart from the saved gates (#655).
 *
 * <p>They used to sit in {@code data/gates/captures/} and {@code data/mirror/captures/}, so the
 * folder an admin backs up or hand-edits for gates held a subfolder of {@code .view} files, and
 * the two kinds of capture were in different places. A server that already has captures must
 * find them in the new place without losing the gates beside them.
 */
class DataLayoutCapturesTest
{
    @TempDir
    File pluginFolder;

    private File data;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(pluginFolder);
        PluginTestSupport.install(plugin);
        data = new File(pluginFolder, "data");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    /** Both kinds of capture are under one folder, grouped by what they are of. */
    @Test
    void bothKindsOfCaptureLiveUnderOneFolder()
    {
        assertEquals(new File(new File(data, "captures"), "gates"), DataLayout.gateCaptureDir());
        assertEquals(new File(new File(data, "captures"), "mirrors"), DataLayout.mirrorCaptureDir());
    }

    /** The gate captures move out of gates/, and the gate files beside them are not touched. */
    @Test
    void gateCapturesMoveOutOfTheGatesFolderAndTheGatesStayPut() throws IOException
    {
        final File earlier = new File(DataLayout.gates(), "captures");
        assertTrue(earlier.mkdirs());
        write(new File(earlier, "alpha.view"), "capture");
        final File gate = new File(DataLayout.gates(), "alpha.yml");
        write(gate, "the gate");

        final File dir = DataLayout.gateCaptureDir();

        assertEquals("capture", read(new File(dir, "alpha.view")), "the capture came along");
        assertFalse(earlier.exists(), "and the old folder is gone");
        assertEquals("the gate", read(gate), "the gate file is where it was, unread by the move");
    }

    /** The folder two builds back, and the one a build back, both end in the new place. */
    @Test
    void everyEarlierMirrorFolderEndsInTheNewOne() throws IOException
    {
        final File oldest = new File(data, "mirror-captures");
        assertTrue(oldest.mkdirs());
        write(new File(oldest, "one.view"), "oldest");
        final File middle = new File(new File(data, "mirror"), "captures");
        assertTrue(middle.mkdirs());
        write(new File(middle, "two.view"), "middle");

        final File dir = DataLayout.mirrorCaptureDir();

        assertEquals("oldest", read(new File(dir, "one.view")));
        assertEquals("middle", read(new File(dir, "two.view")));
        assertFalse(oldest.exists(), "mirror-captures/ is gone");
        assertFalse(new File(data, "mirror").exists(), "and so is the emptied mirror/ folder");
    }

    /** A file already in the new folder is never overwritten, and the old one stays to be seen. */
    @Test
    void aFileAlreadyAtTheDestinationWins() throws IOException
    {
        final File dir = new File(new File(data, "captures"), "gates");
        assertTrue(dir.mkdirs());
        write(new File(dir, "alpha.view"), "new");
        final File earlier = new File(DataLayout.gates(), "captures");
        assertTrue(earlier.mkdirs());
        write(new File(earlier, "alpha.view"), "old");
        write(new File(earlier, "beta.view"), "other");

        DataLayout.gateCaptureDir();

        assertEquals("new", read(new File(dir, "alpha.view")), "not overwritten");
        assertEquals("other", read(new File(dir, "beta.view")), "the rest came across");
        assertEquals("old", read(new File(earlier, "alpha.view")), "and the clash is left, nothing deleted");
    }

    /** A server with no captures is left with nothing created by being asked. */
    @Test
    void askingWithNothingToMoveCreatesNothing()
    {
        assertFalse(DataLayout.gateCaptureDir().exists());
        assertFalse(DataLayout.mirrorCaptureDir().exists());
        assertFalse(DataLayout.gates().exists());
        assertFalse(new File(data, "captures").exists());
    }

    /**
     * A clash left in the old folder is looked at once a run, not on every capture lookup.
     *
     * <p>The accessors are asked for every lookup, often on the main thread, so a folder that can
     * never be emptied (a file in both places) must not be walked and failed on each time.
     */
    @Test
    void aFolderThatCouldNotBeEmptiedIsNotRetried() throws IOException
    {
        final File dir = new File(new File(data, "captures"), "gates");
        write(new File(dir, "alpha.view"), "new");
        final File earlier = new File(DataLayout.gates(), "captures");
        write(new File(earlier, "alpha.view"), "old");
        DataLayout.gateCaptureDir();
        write(new File(earlier, "beta.view"), "later");

        DataLayout.gateCaptureDir();

        assertTrue(new File(earlier, "beta.view").isFile(), "not walked a second time");
        assertFalse(new File(dir, "beta.view").exists());
    }

    /** The emptied mirror/ folder goes, but a plain file of that name is not ours to delete. */
    @Test
    void aFileNamedMirrorIsNotDeleted() throws IOException
    {
        write(new File(data, "mirror"), "an admin's backup");

        DataLayout.mirrorCaptureDir();

        assertEquals("an admin's backup", read(new File(data, "mirror")));
    }

    private static void write(final File file, final String text) throws IOException
    {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text);
    }

    private static String read(final File file) throws IOException
    {
        return Files.readString(file.toPath());
    }
}
