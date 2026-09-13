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
