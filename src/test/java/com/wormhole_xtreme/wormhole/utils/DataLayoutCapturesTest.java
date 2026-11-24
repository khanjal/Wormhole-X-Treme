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
        assertEquals(new File(new File(data, "captures"), "mirrors"), DataLayout.mirrorCaptureDir());
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

    /** Where the two older mirror folders hold the same capture, the newer folder's is kept. */
    @Test
    void theNewerMirrorFolderWinsAClashWithTheOlderOne() throws IOException
    {
        final File oldest = new File(data, "mirror-captures");
        write(new File(oldest, "same.view"), "stale");
        final File middle = new File(new File(data, "mirror"), "captures");
        write(new File(middle, "same.view"), "fresh");

        final File dir = DataLayout.mirrorCaptureDir();

        assertEquals("fresh", read(new File(dir, "same.view")), "not the two-builds-old photograph");
    }

    /** A server with no captures is left with nothing created by being asked. */
    @Test
    void askingWithNothingToMoveCreatesNothing()
    {
        assertFalse(DataLayout.mirrorCaptureDir().exists());
        assertFalse(DataLayout.gates().exists());
        assertFalse(new File(data, "captures").exists());
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
