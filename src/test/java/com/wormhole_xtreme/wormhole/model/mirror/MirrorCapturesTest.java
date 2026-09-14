package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.utils.DataLayout;

/**
 * Taking a capture: chunk by chunk, from the far world, into a file.
 *
 * <p>A window draws from nothing else, so a capture that missed a chunk would be a view with a
 * hole in it, and one that kept the far banner would show it hanging in mid-air.
 */
class MirrorCapturesTest
{
    @TempDir
    File dataFolder;

    private World far;
    private final BlockData air = mock(BlockData.class);
    private final BlockData sand = mock(BlockData.class);
    private final QuantumMirror mirror = new QuantumMirror("museum",
        new MirrorBlock("world", 10, 64, 10), new MirrorPoint("far", 100.5, 70.0, -20.5, 0.0f, 0.0f));

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        // No scheduler, so the capture's file is written on this thread rather than handed to a
        // mock that would drop it -- which a scheduler left behind by another test class did.
        PluginTestSupport.scheduler(null);
        ConfigTestSupport.clear();
        // A small box: 33 across, so three chunks a side.
        ConfigTestSupport.set(ConfigKeys.MIRROR_CAPTURE_RADIUS, 16);
        MirrorManager.clear();
        MirrorCaptures.clear();
        when(air.getAsString()).thenReturn("minecraft:air");
        when(air.getMaterial()).thenReturn(Material.AIR);
        when(sand.getAsString()).thenReturn("minecraft:sand");
        when(sand.getMaterial()).thenReturn(Material.SAND);
        far = mock(World.class);
        when(far.getName()).thenReturn("far");
        when(far.getMinHeight()).thenReturn(-64);
        when(far.getMaxHeight()).thenReturn(320);
        when(far.getEnvironment()).thenReturn(World.Environment.NORMAL);
        // Sand up to y 69 everywhere, air above: the arrival point stands on the beach.
        MirrorCaptures.readChunksWith((world, chunkX, chunkZ) ->
        {
            final ChunkSnapshot chunk = mock(ChunkSnapshot.class);
            when(chunk.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(69);
            when(chunk.getBlockData(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                (((int) invocation.getArgument(1)) < 70) ? sand : air);
            return chunk;
        });
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorCaptures.clear();
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    @Test
    void aCaptureIsTakenChunkByChunkAndThenThere()
    {
        withServer(() ->
        {
            assertTrue(MirrorCaptures.request(mirror));
            assertNull(MirrorCaptures.get(mirror), "not there until it has been taken");
            assertEquals(1, MirrorCaptures.taking());
            MirrorCaptures.step(100);
        });

        final MirrorCapture capture = MirrorCaptures.get(mirror);
        assertNotNull(capture);
        assertEquals(0, MirrorCaptures.taking());
        assertSame(sand, capture.at(100, 69, -21), "the beach at the arrival point");
        assertTrue(capture.isAir(100, 70, -21), "and air above it");
        assertSame(sand, capture.at(116, 60, -5), "out at the box's far corner");
        assertEquals(69, capture.top(84, -37));
    }

    /**
     * A mirror's banner at the far side is blanked in the capture.
     *
     * <p>A linked pair arrives in the far banner's own block, so it would otherwise hang in the
     * middle of the view.
     */
    @Test
    void aMirrorBannerAtTheFarSideIsBlankedInTheCapture()
    {
        MirrorManager.add(new QuantumMirror("return", new MirrorBlock("far", 100, 69, -21), null));

        withServer(() ->
        {
            MirrorCaptures.request(mirror);
            MirrorCaptures.step(100);
        });

        assertTrue(MirrorCaptures.get(mirror).isAir(100, 69, -21));
        assertFalse(MirrorCaptures.get(mirror).isAir(101, 69, -21), "only the banner's block");
    }

    /** The capture is written to disk, and read back by a server that has forgotten it. */
    @Test
    void aCaptureSurvivesTheServerForgettingIt()
    {
        withServer(() ->
        {
            MirrorCaptures.request(mirror);
            MirrorCaptures.step(100);
        });
        assertTrue(DataLayout.mirrorCaptureDir().isDirectory(), "written under the data folder");
        assertTrue(DataLayout.mirrorCaptureDir().getAbsolutePath().startsWith(dataFolder.getAbsolutePath()),
            "and under this test's folder, not the repository's: " + DataLayout.mirrorCaptureDir());

        MirrorCaptures.clear();

        final MirrorCapture reloaded = MirrorCaptures.get(mirror);
        assertNotNull(reloaded, "read back from its file");
        assertFalse(reloaded.isAir(100, 69, -21));
    }

    @Test
    void forgettingAMirrorDeletesItsCaptureUnlessAnotherMirrorLooksThere()
    {
        MirrorManager.add(mirror);
        withServer(() ->
        {
            MirrorCaptures.request(mirror);
            MirrorCaptures.step(100);
        });
        final QuantumMirror twin = new QuantumMirror("twin", new MirrorBlock("world", 20, 64, 10),
            mirror.destination());
        MirrorManager.add(twin);

        MirrorCaptures.forget(twin);
        assertNotNull(MirrorCaptures.get(mirror), "the museum still looks there");

        MirrorManager.remove("twin");
        MirrorCaptures.forget(mirror);
        MirrorCaptures.clear();
        assertNull(MirrorCaptures.get(mirror), "gone from disk too");
    }

    @Test
    void aMirrorOntoAWorldThatIsNotLoadedCannotBeCaptured()
    {
        withServer(() -> assertFalse(MirrorCaptures.request(new QuantumMirror("nowhere",
            new MirrorBlock("world", 1, 64, 1), new MirrorPoint("gone", 0, 64, 0, 0, 0)))));
    }

    @Test
    void aDynamicMirrorIsDueAgainAfterTheIntervalAndAStaticOneNever()
    {
        final MirrorCapture old = new MirrorCapture.Builder("far", true, 0, 0, 0, 1, 1, 1, air)
            .build();
        ConfigTestSupport.set(ConfigKeys.MIRROR_DYNAMIC_RESAMPLE_SECONDS, 0);

        assertFalse(MirrorCaptures.due(mirror, old), "static, however old");
        assertTrue(MirrorCaptures.due(mirror.withMode(MirrorMode.DYNAMIC), old));
    }

    /**
     * A capture from before buried blocks were marked is outgrown, whatever its size.
     *
     * <p>It wrote them as air, and a window in a wall draws the far side whole: the inside of the
     * far hill would be carved out of the real ground behind the wall.
     */
    @Test
    void aCaptureThatWroteBuriedBlocksAsAirIsOutgrown() throws java.io.IOException
    {
        final int arrivalY = (int) Math.floor(mirror.destination().y());
        final File file = new File(dataFolder, "old.view");
        new MirrorCapture.Builder("far", true, 0, arrivalY - 64, 0, 33, 129, 33, air).build().save(file);
        MirrorCaptureTest.rewriteVersion(file, 1);
        final MirrorCapture old = MirrorCapture.load(file);

        withServer(() -> assertTrue(MirrorCaptures.outgrown(mirror, old),
            "as wide and deep as one taken now, but from before"));
    }

    /**
     * A captures folder left beside the mirror file by an earlier build is moved under mirror/.
     *
     * <p>Captures moved from data/mirror-captures/ to data/mirror/captures/ so that whatever
     * else mirrors keep has a folder to go in. The old folder is moved rather than abandoned,
     * so nobody's captures are left behind to puzzle over.
     */
    @Test
    void anEarlierBuildsCaptureFolderIsMovedUnderMirror() throws java.io.IOException
    {
        final File data = new File(dataFolder, "data");
        final File earlier = new File(data, "mirror-captures");
        assertTrue(earlier.mkdirs());
        final File kept = new File(earlier, "world_1_2_3.view");
        java.nio.file.Files.writeString(kept.toPath(), "not really a capture");

        final File dir = DataLayout.mirrorCaptureDir();

        assertEquals(new File(new File(data, "mirror"), "captures"), dir);
        assertTrue(new File(dir, "world_1_2_3.view").isFile(), "the file came along");
        assertFalse(earlier.exists(), "and the old folder is gone");
    }

    /**
     * A capture smaller than the configured box is outgrown, and taken again on the next look.
     *
     * <p>The box grew twice during testing, and a file from before kept its old horizon until
     * somebody ran mirror stamp. Depth is judged only with the far world loaded: without it the
     * capture could not be retaken anyway, and asking every sweep would warn every sweep.
     */
    @Test
    void aCaptureSmallerThanTheConfiguredBoxIsOutgrown()
    {
        // The configured radius is 16, so a box is 33 across, and reaches 64 below the arrival.
        final int arrivalY = (int) Math.floor(mirror.destination().y());
        final MirrorCapture fits = new MirrorCapture.Builder("far", true, 0, arrivalY - 64, 0, 33, 129, 33, air)
            .build();
        final MirrorCapture narrow = new MirrorCapture.Builder("far", true, 0, arrivalY - 64, 0, 31, 129, 31, air)
            .build();
        final MirrorCapture shallow = new MirrorCapture.Builder("far", true, 0, arrivalY - 48, 0, 33, 113, 33, air)
            .build();

        withServer(() ->
        {
            assertFalse(MirrorCaptures.outgrown(mirror, fits), "as wide and as deep as one taken now");
            assertTrue(MirrorCaptures.outgrown(mirror, narrow), "narrower than the configured radius");
            assertTrue(MirrorCaptures.outgrown(mirror, shallow), "not as deep below the arrival point");
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(null);
            assertTrue(MirrorCaptures.outgrown(mirror, narrow), "width is judged without the far world");
            assertFalse(MirrorCaptures.outgrown(mirror, shallow), "depth is not: it could not be retaken anyway");
        }
    }

    private void withServer(final Runnable body)
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            body.run();
        }
    }
}
