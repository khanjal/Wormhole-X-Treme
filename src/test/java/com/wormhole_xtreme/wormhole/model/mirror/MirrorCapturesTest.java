package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand;
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

    private WormholeXTreme plugin;
    private World far;
    /** A torch on the sand three blocks ahead of the arrival point, or null for none. */
    private BlockData torch;
    /** Every column, as "x,z", whose blocks a capture has read. */
    private final java.util.Set<String> columnsRead = new java.util.HashSet<>();
    private final BlockData air = mock(BlockData.class);
    private final BlockData sand = mock(BlockData.class);
    private final QuantumMirror mirror = new QuantumMirror("museum",
        new MirrorBlock("world", 10, 64, 10), new MirrorPoint("far", 100.5, 70.0, -20.5, 0.0f, 0.0f));

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        // No scheduler, so the capture's file is written on this thread rather than handed to a
        // mock that would drop it -- which a scheduler left behind by another test class did.
        PluginTestSupport.scheduler(null);
        ConfigTestSupport.clear();
        // A small box: 33 across, so three chunks a side.
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 16);
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
                columnsRead.add(((chunkX * 16) + lx) + "," + ((chunkZ * 16) + lz));
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
        MirrorCaptures.siftWith(null);
        PluginTestSupport.scheduler(null);
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /**
     * A capture file whose place is no mirror's room is swept, and one a mirror uses is kept.
     *
     * <p>"Do we clean up any abandoned views (on startup or something)?" Only as each mirror went:
     * a mirror file emptied by hand, or a delete that failed, left its captures behind for good.
     */
    @Test
    void aCaptureNoMirrorUsesIsSweptAndTheRestAreKept() throws Exception
    {
        MirrorManager.add(mirror);
        final File dir = DataLayout.mirrorCaptureDir();
        assertTrue(dir.isDirectory() || dir.mkdirs(), "the captures folder");
        final File used = new File(dir, MirrorCaptures.keyFor(mirror) + ".view");
        final File abandoned = new File(dir, "far_1_2_3.view");
        for (final File file : new File[] { used, abandoned })
        {
            assertTrue(file.createNewFile(), file.getName());
        }

        assertEquals(1, MirrorCaptures.sweepAbandoned(), "one file no mirror's room is");
        assertFalse(abandoned.exists(), "the abandoned capture");
        assertTrue(used.exists(), "museum's room is kept");
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
     * Every column of the box is read, and none of the chunk around it.
     *
     * <p>The box's edges fall inside chunks, not on their boundaries: x 82..118 is from two blocks
     * into chunk 5 to six into chunk 7. A column dropped at an edge is a strip of the far side
     * missing from every view.
     */
    @Test
    void everyColumnOfTheBoxIsReadAndNoneOutsideIt()
    {
        withServer(() ->
        {
            MirrorCaptures.request(mirror);
            MirrorCaptures.step(6);
        });

        final java.util.Set<String> box = new java.util.HashSet<>();
        for (int x = 82; x <= 118; x++)
        {
            for (int z = -22; z <= -3; z++)
            {
                box.add(x + "," + z);
            }
        }
        assertEquals(box, columnsRead);
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

    /**
     * {@code set stamp} leaves the capture alone, and {@code set capture} takes it again.
     *
     * <p>"It should be an understood command." stamp used to retake the capture as a side
     * effect, so a command about the banner changed what people saw through the opening; the
     * only way to take a room again by hand is to ask for that.
     */
    @Test
    void stampLeavesTheCaptureAloneAndCaptureTakesItAgain()
    {
        MirrorManager.add(mirror);
        final Player admin = mock(Player.class);
        when(admin.isOp()).thenReturn(true);
        final World bannerWorld = mock(World.class);
        final Block bannerBlock = mock(Block.class);
        final Banner state = mock(Banner.class);
        when(bannerBlock.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(bannerBlock.getState()).thenReturn(state);
        when(bannerWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(bannerBlock);
        final MirrorCommand command = new MirrorCommand();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);

            command.execute(admin, new String[] { "mirror", "set", "museum", "-stamp", "nether" });
            assertEquals(0, MirrorCaptures.taking(), "stamp is about the banner, not the room");

            command.execute(admin, new String[] { "mirror", "set", "museum", "-capture" });
            assertEquals(1, MirrorCaptures.taking(), "capture is what takes the room again");
        }
        verify(admin, atLeastOnce()).sendMessage(contains("Capturing"));
    }

    @Test
    void aMirrorOntoAWorldThatIsNotLoadedCannotBeCaptured()
    {
        withServer(() -> assertFalse(MirrorCaptures.request(new QuantumMirror("nowhere",
            new MirrorBlock("world", 1, 64, 1), new MirrorPoint("gone", 0, 64, 0, 0, 0)))));
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
        final MirrorCapture fits = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(82, 52, -22, 37, 37, 20), air).build();
        final MirrorCapture narrow = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(83, 52, -22, 36, 37, 20), air).build();
        final MirrorCapture shortAhead = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(82, 52, -22, 37, 37, 19), air).build();
        final MirrorCapture shallow = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(82, 53, -22, 37, 36, 20), air).build();

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

    /**
     * A capture reaches as far as the far world's server sends, whatever the view depth.
     *
     * <p>"Maybe the capture grabs all the way to the server view limit, then we dynamically pull
     * that data depending on the wall?" A capture used to be taken to the view depth and no
     * further, so lowering the depth to make a mirror cheaper cut every capture to match, and
     * raising it again took every one again. The reach is the capture's own: the far world's
     * view distance in blocks, never past 160 and never short of the depth, since a view drawn
     * past its capture would run out of room to draw.
     */
    @Test
    void aCaptureReachesAsFarAsTheFarWorldSendsWhateverTheViewDepth()
    {
        assertEquals(16, MirrorCaptures.reach(null), "not loaded to ask: the view depth, which any capture holds");
        assertEquals(16, MirrorCaptures.reach(far), "a world sending nothing -- a bare mock -- still reaches the depth");
        when(far.getViewDistance()).thenReturn(6);
        assertEquals(96, MirrorCaptures.reach(far), "six chunks is 96 blocks, well past a depth of 16");
        when(far.getViewDistance()).thenReturn(32);
        assertEquals(160, MirrorCaptures.reach(far), "never past ten chunks, however far a server sends");
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 160);
        when(far.getViewDistance()).thenReturn(2);
        assertEquals(160, MirrorCaptures.reach(far), "and never short of the depth, or a view would outrun its capture");
    }

    /**
     * Changing the view depth does not take a capture again; only a reach past what it holds does.
     *
     * <p>The point of a reach of its own: an operator lowering {@code mirror-view-depth} for a
     * smoother mirror should not see every room's world loaded and photographed again for it. A
     * capture taken by the old rule, to the depth alone, is taken again once, since a server
     * sending further can now show more of the room.
     */
    @Test
    void changingTheViewDepthDoesNotOutgrowACaptureTakenToTheReach()
    {
        when(far.getViewDistance()).thenReturn(6);
        // To a depth of 16 alone, as the old rule took it: x 82..118, y 52..88, z -22..-3.
        final MirrorCapture toTheDepth = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(82, 52, -22, 37, 37, 20), air).build();
        // To the reach of 96: x 2..198, y -28..168, z -22..77.
        final MirrorCapture toTheReach = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(2, -28, -22, 197, 197, 100), air).build();

        withServer(() ->
        {
            assertTrue(MirrorCaptures.outgrown(mirror, toTheDepth), "taken to the depth alone: taken again, once");
            assertFalse(MirrorCaptures.outgrown(mirror, toTheReach), "taken to the reach: it holds all a view can draw");
            ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 8);
            assertFalse(MirrorCaptures.outgrown(mirror, toTheReach), "a lower depth draws less of it and asks for nothing");
            ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 120);
            assertTrue(MirrorCaptures.outgrown(mirror, toTheReach), "a depth past the reach is the one thing that grows it");
        });
    }

    /**
     * A sift that throws puts its job down, and the mirror's next request starts a fresh one.
     *
     * <p>The sift runs on the scheduler's pool, which swallows what it throws. Nothing then handed
     * the job back to the main thread, so it sat sifting for good, held its place in the jobs, and
     * that mirror's view never updated again until a restart.
     */
    @Test
    void aSiftThatThrowsLetsTheNextRequestStartAgain() throws Exception
    {
        final Pool pool = new Pool();
        MirrorCaptures.siftWith((builder, from, reach, floor) ->
        {
            throw new IllegalStateException("a bug in the sift");
        });

        withServer(() ->
        {
            assertTrue(MirrorCaptures.request(mirror));
            assertEquals(1, pool.timers.size(), "the job's tick");
            pool.tickUntilIdle();
        });

        assertEquals(0, MirrorCaptures.taking(), "the job is put down");
        assertTrue(pool.timers.isEmpty(), "and its tick cancelled");
        assertTrue(pool.escaped.isEmpty(), "nothing left for the pool to swallow");
        verify(plugin).prettyLog(eq(Level.WARNING), contains("Could not work out what the mirror capture"),
            any(IllegalStateException.class));
        takenAfresh(pool);
    }

    /**
     * An OutOfMemoryError in the sift still reaches the pool, and still puts the job down.
     *
     * <p>A deep capture is tens of megabytes. The error is not caught, but a job left sifting
     * would hold all of that for good.
     */
    @Test
    void aSiftOutOfMemoryStillPutsTheJobDown() throws Exception
    {
        final Pool pool = new Pool();
        MirrorCaptures.siftWith((builder, from, reach, floor) ->
        {
            throw new OutOfMemoryError("a deep capture");
        });

        withServer(() ->
        {
            assertTrue(MirrorCaptures.request(mirror));
            assertThrows(OutOfMemoryError.class, pool::tickUntilIdle, "the error is not swallowed");
            pool.runMain();
        });

        assertEquals(0, MirrorCaptures.taking(), "the job is put down");
        assertTrue(pool.timers.isEmpty(), "and its tick cancelled");
        takenAfresh(pool);
    }

    /**
     * A sift that fails every time is logged once, even after the far world was warned about.
     *
     * <p>The two warnings first shared one set, so a world that had once been unloaded hid every
     * sift failure after it.
     */
    @Test
    void aSiftFailureIsLoggedOnceWhateverWasWarnedBefore() throws Exception
    {
        final Pool pool = new Pool();
        MirrorCaptures.siftWith((builder, from, reach, floor) ->
        {
            throw new IllegalStateException("a bug in the sift");
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            assertFalse(MirrorCaptures.request(mirror), "the far world is not loaded yet");
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);
            for (int attempt = 0; attempt < 2; attempt++)
            {
                assertTrue(MirrorCaptures.request(mirror));
                pool.tickUntilIdle();
            }
        }

        verify(plugin).prettyLog(eq(Level.WARNING), contains("is not loaded"));
        verify(plugin).prettyLog(eq(Level.WARNING), contains("Could not work out what the mirror capture"),
            any(IllegalStateException.class));
        assertEquals(0, MirrorCaptures.taking());
    }

    /** With the sift working again, a request takes the capture from the start. */
    private void takenAfresh(final Pool pool)
    {
        MirrorCaptures.siftWith(null);
        withServer(() ->
        {
            assertTrue(MirrorCaptures.request(mirror));
            assertEquals(1, MirrorCaptures.taking(), "a fresh job");
            assertEquals(1, pool.timers.size(), "with a tick of its own");
            pool.tickUntilIdle();
        });
        assertNotNull(MirrorCaptures.get(mirror), "the capture is taken after all");
        assertEquals(0, MirrorCaptures.taking());
    }

    /**
     * A scheduler whose repeating tasks run when told and stop when cancelled, whose async tasks
     * run at once, swallowing what they throw as a server's pool does, and whose main-thread
     * tasks wait for the next tick.
     */
    private static final class Pool
    {
        final Map<Integer, Runnable> timers = new LinkedHashMap<>();
        final List<Runnable> main = new ArrayList<>();
        final List<RuntimeException> escaped = new ArrayList<>();
        private int nextId;

        Pool() throws Exception
        {
            final BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation ->
                {
                    final int id = nextId++;
                    timers.put(id, invocation.getArgument(1));
                    final BukkitTask task = mock(BukkitTask.class);
                    doAnswer(cancel -> timers.remove(id)).when(task).cancel();
                    return task;
                });
            when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation ->
            {
                try
                {
                    ((Runnable) invocation.getArgument(1)).run();
                }
                catch (final RuntimeException swallowed)
                {
                    escaped.add(swallowed);
                }
                return mock(BukkitTask.class);
            });
            when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation ->
            {
                main.add(invocation.getArgument(1));
                return mock(BukkitTask.class);
            });
            PluginTestSupport.scheduler(scheduler);
        }

        /** Runs every task due this tick. */
        void tick()
        {
            new ArrayList<>(timers.values()).forEach(Runnable::run);
            runMain();
        }

        void runMain()
        {
            final List<Runnable> due = new ArrayList<>(main);
            main.clear();
            due.forEach(Runnable::run);
        }

        /** Ticks until no job is left, or long enough that one must be stuck. */
        void tickUntilIdle()
        {
            for (int i = 0; (i < 100) && !timers.isEmpty(); i++)
            {
                tick();
            }
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
