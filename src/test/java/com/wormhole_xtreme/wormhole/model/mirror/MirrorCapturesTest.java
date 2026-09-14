package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    /** A torch on the sand three blocks ahead of the arrival point, or null for none. */
    private BlockData torch;
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
        when(sand.isOccluding()).thenReturn(true);
        far = mock(World.class);
        when(far.getName()).thenReturn("far");
        when(far.getMinHeight()).thenReturn(-64);
        when(far.getMaxHeight()).thenReturn(320);
        when(far.getEnvironment()).thenReturn(World.Environment.NORMAL);
        // Sand up to y 69 everywhere, air above: the arrival point stands on the beach. A test may
        // stand a torch on the sand three blocks ahead, at (100, 70, -18), which the world's
        // surface heightmap counts and the snapshot's own -- blocks a player collides with -- does not.
        when(far.getHighestBlockYAt(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any(org.bukkit.HeightMap.class)))
            .thenAnswer(invocation -> ((torch != null) && (((int) invocation.getArgument(0)) == 100)
                && (((int) invocation.getArgument(1)) == -18)) ? 70 : 69);
        MirrorCaptures.readChunksWith((world, chunkX, chunkZ) ->
        {
            final ChunkSnapshot chunk = mock(ChunkSnapshot.class);
            when(chunk.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(69);
            when(chunk.getBlockData(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
            {
                final int lx = invocation.getArgument(0);
                final int y = invocation.getArgument(1);
                final int lz = invocation.getArgument(2);
                if ((torch != null) && (chunkX == 6) && (chunkZ == -2) && (lx == 4) && (y == 70) && (lz == 14))
                {
                    return torch;
                }
                return (y < 70) ? sand : air;
            });
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

    /**
     * A capture is taken in two passes over the chunks -- what kind of block stands where, then
     * the states of the blocks that can be seen -- and is there after the second.
     */
    @Test
    void aCaptureIsTakenChunkByChunkAndThenThere()
    {
        withServer(() ->
        {
            assertTrue(MirrorCaptures.request(mirror));
            assertNull(MirrorCaptures.get(mirror), "not there until it has been taken");
            assertEquals(1, MirrorCaptures.taking());
            // The box is x 82..118 by z -22..-3: three chunks by two.
            MirrorCaptures.step(6);
            assertNull(MirrorCaptures.get(mirror), "not after the first pass over the six chunks either");
            MirrorCaptures.step(100);
        });

        final MirrorCapture capture = MirrorCaptures.get(mirror);
        assertNotNull(capture);
        assertEquals(0, MirrorCaptures.taking());
        assertSame(sand, capture.at(100, 69, -21), "the beach at the arrival point");
        assertTrue(capture.isAir(100, 70, -21), "and air above it");
        // Yaw 0 faces south, so the box runs ahead to z -3: 16 deep and a margin of 2.
        assertSame(sand, capture.at(100, 69, -6), "the beach's surface fifteen blocks straight ahead");
        assertTrue(capture.isBuried(100, 60, -6), "nine blocks under it, which nobody at the opening could see");
        assertTrue(capture.isBuried(116, 69, -3), "the box's far corner, past the depth");
        assertEquals(51, capture.top(82, -22),
            "nothing seen in the column at its near corner, one layer behind the arrival: one below the box");
        assertTrue(capture.isAir(100, 69, -23), "two layers behind the arrival is outside the box");
        assertTrue(capture.isAir(100, 69, -2), "and so is past the depth and margin");
    }

    /**
     * A torch standing above the highest block a player would collide with is captured.
     *
     * <p>"Vines and torches aren't being shown in the mirror on the other world." A column was
     * read only up to the chunk snapshot's highest block, which is the highest one with a
     * collision box; a torch on the sand above it, like a flower, a rail or a vine on an outside
     * wall, was never read at all.
     */
    @Test
    void aTorchStandingAboveTheHighestSolidBlockIsCaptured()
    {
        torch = mock(BlockData.class);
        when(torch.getAsString()).thenReturn("minecraft:torch");
        when(torch.getMaterial()).thenReturn(Material.TORCH);
        when(torch.isOccluding()).thenReturn(false);

        withServer(() ->
        {
            MirrorCaptures.request(mirror);
            MirrorCaptures.step(100);
        });

        assertSame(torch, MirrorCaptures.get(mirror).at(100, 70, -18), "the torch on the sand, three blocks ahead");
    }

    /**
     * A capture's box is the half-sphere of the depth ahead of the arrival point, boxed.
     *
     * <p>Nothing outside it can be seen through a window, since the depth is measured from the
     * opening. A box the capture radius across in every direction was thirty-five times as much
     * at the default depth, most of it behind the arrival point where no window ever looked.
     */
    @Test
    void theBoxACaptureNeedsIsTheHalfSphereAheadOfTheArrivalBoxed()
    {
        final MirrorPoint south = new MirrorPoint("far", 100.5, 70.0, -20.5, 0.0f, 0.0f);
        final MirrorPoint west = new MirrorPoint("far", 100.5, 70.0, -20.5, 90.0f, 0.0f);

        assertArrayEquals(new int[] { 82, 52, -22, 118, 88, -3 }, MirrorCaptures.needed(south, 16, null, null),
            "16 deep plus a margin of 2 ahead, either side, up and down; one layer behind");
        assertArrayEquals(new int[] { 82, 52, -39, 101, 88, -3 }, MirrorCaptures.needed(west, 16, null, null),
            "facing west, the box runs to lower x");
        assertArrayEquals(new int[] { 82, 60, -22, 118, 75, -3 }, MirrorCaptures.needed(south, 16, 60, 76),
            "clamped to the far world's heights when it is loaded to ask");
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
    void aCaptureSmallerThanTheBoxItNeedsIsOutgrown()
    {
        // The configured radius is 16, the depth 32, so the capture is taken 16 deep: a box from
        // x 82..118, y 52..88, z -22..-3 for this mirror, facing south.
        final MirrorCapture fits = new MirrorCapture.Builder("far", true, 82, 52, -22, 37, 37, 20, air).build();
        final MirrorCapture narrow = new MirrorCapture.Builder("far", true, 83, 52, -22, 36, 37, 20, air).build();
        final MirrorCapture shortAhead = new MirrorCapture.Builder("far", true, 82, 52, -22, 37, 37, 19, air).build();
        final MirrorCapture shallow = new MirrorCapture.Builder("far", true, 82, 53, -22, 37, 36, 20, air).build();

        withServer(() ->
        {
            assertFalse(MirrorCaptures.outgrown(mirror, fits), "the box one taken now would be");
            assertTrue(MirrorCaptures.outgrown(mirror, narrow), "a block short to one side");
            assertTrue(MirrorCaptures.outgrown(mirror, shortAhead), "a block short ahead");
            assertTrue(MirrorCaptures.outgrown(mirror, shallow), "a block short below");
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(null);
            assertTrue(MirrorCaptures.outgrown(mirror, narrow), "width is judged without the far world");
            assertFalse(MirrorCaptures.outgrown(mirror, shallow), "height is not: it could not be retaken anyway");
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
