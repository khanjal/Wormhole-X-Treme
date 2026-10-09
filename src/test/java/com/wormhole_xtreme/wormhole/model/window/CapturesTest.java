package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.utils.DataLayout;

/**
 * Taking a capture: chunk by chunk, from the far world, into a file.
 *
 * <p>A window draws from nothing else, so a capture that missed a chunk would be a view with a
 * hole in it, and one that kept the far banner would show it hanging in mid-air.
 */
class CapturesTest
{
    @TempDir
    File dataFolder;

    private WormholeXTreme plugin;
    private World far;
    /** A torch on the sand three blocks ahead of the arrival point, or null for none. */
    private BlockData torch;
    /** Every column, as "x,z", whose blocks a capture has read. */
    private final Set<String> columnsRead = new HashSet<>();
    private final BlockData air = mock(BlockData.class);
    private final BlockData sand = mock(BlockData.class);
    private final QuantumMirror mirror = new QuantumMirror("museum",
        new BlockPlace("world", 10, 64, 10), new Place("far", 100.5, 70.0, -20.5, 0.0f, 0.0f));

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
        Captures.clear();
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
        when(far.getHighestBlockYAt(anyInt(), anyInt(), ArgumentMatchers.any(HeightMap.class)))
            .thenAnswer(invocation -> ((torch != null) && (((int) invocation.getArgument(0)) == 100)
                && (((int) invocation.getArgument(1)) == -18)) ? 70 : 69);
        Captures.readChunksWith((world, chunkX, chunkZ) ->
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
        Captures.clear();
        Captures.siftWith(null);
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
        final File used = new File(dir, Captures.keyFor(mirror) + ".view");
        final File abandoned = new File(dir, "far_1_2_3.view");
        for (final File file : new File[] { used, abandoned })
        {
            assertTrue(file.createNewFile(), file.getName());
        }

        assertEquals(1, Captures.sweepAbandoned(), "one file no mirror's room is");
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
            assertTrue(Captures.request(mirror));
            assertNull(Captures.get(mirror), "not there until it has been taken");
            assertEquals(1, Captures.taking());
            // The box is x 82..118 by z -22..-3: three chunks by two.
            Captures.step(6);
            assertNull(Captures.get(mirror), "not after the first pass over the six chunks either");
            Captures.step(100);
        });

        final Capture capture = Captures.get(mirror);
        assertNotNull(capture);
        assertEquals(0, Captures.taking());
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
            Captures.request(mirror);
            Captures.step(6);
        });

        final Set<String> box = new HashSet<>();
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
            Captures.request(mirror);
            Captures.step(100);
        });

        assertSame(torch, Captures.get(mirror).at(100, 70, -18), "the torch on the sand, three blocks ahead");
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
        final Place south = new Place("far", 100.5, 70.0, -20.5, 0.0f, 0.0f);
        final Place west = new Place("far", 100.5, 70.0, -20.5, 90.0f, 0.0f);

        assertArrayEquals(new int[] { 82, 52, -22, 118, 88, -3 }, Captures.needed(south, 16, null, null),
            "16 deep plus a margin of 2 ahead, either side, up and down; one layer behind");
        assertArrayEquals(new int[] { 82, 52, -39, 101, 88, -3 }, Captures.needed(west, 16, null, null),
            "facing west, the box runs to lower x");
        assertArrayEquals(new int[] { 82, 60, -22, 118, 75, -3 }, Captures.needed(south, 16, 60, 76),
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
        MirrorManager.add(new QuantumMirror("return", new BlockPlace("far", 100, 69, -21), null));

        withServer(() ->
        {
            Captures.request(mirror);
            Captures.step(100);
        });

        assertTrue(Captures.get(mirror).isAir(100, 69, -21));
        assertFalse(Captures.get(mirror).isAir(101, 69, -21), "only the banner's block");
    }

    /** The capture is written to disk, and read back by a server that has forgotten it. */
    @Test
    void aCaptureSurvivesTheServerForgettingIt()
    {
        withServer(() ->
        {
            Captures.request(mirror);
            Captures.step(100);
        });
        assertTrue(DataLayout.mirrorCaptureDir().isDirectory(), "written under the data folder");
        assertTrue(DataLayout.mirrorCaptureDir().getAbsolutePath().startsWith(dataFolder.getAbsolutePath()),
            "and under this test's folder, not the repository's: " + DataLayout.mirrorCaptureDir());

        Captures.clear();

        final Capture reloaded = Captures.get(mirror);
        assertNotNull(reloaded, "read back from its file");
        assertFalse(reloaded.isAir(100, 69, -21));
    }

    @Test
    void forgettingAMirrorDeletesItsCaptureUnlessAnotherMirrorLooksThere()
    {
        MirrorManager.add(mirror);
        withServer(() ->
        {
            Captures.request(mirror);
            Captures.step(100);
        });
        final QuantumMirror twin = new QuantumMirror("twin", new BlockPlace("world", 20, 64, 10),
            mirror.destination());
        MirrorManager.add(twin);

        Captures.forget(twin);
        assertNotNull(Captures.get(mirror), "the museum still looks there");

        MirrorManager.remove("twin");
        Captures.forget(mirror);
        Captures.clear();
        assertNull(Captures.get(mirror), "gone from disk too");
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
            assertEquals(0, Captures.taking(), "stamp is about the banner, not the room");

            command.execute(admin, new String[] { "mirror", "set", "museum", "-capture" });
            assertEquals(1, Captures.taking(), "capture is what takes the room again");
        }
        verify(admin, atLeastOnce()).sendMessage(contains("Capturing"));
    }

    @Test
    void aMirrorOntoAWorldThatIsNotLoadedCannotBeCaptured()
    {
        withServer(() -> assertFalse(Captures.request(new QuantumMirror("nowhere",
            new BlockPlace("world", 1, 64, 1), new Place("gone", 0, 64, 0, 0, 0)))));
    }


    /**
     * A captures folder left beside the mirror file by an earlier build is moved under mirror/.
     *
     * <p>Captures moved from data/mirror-captures/ to data/mirror/captures/ so that whatever
     * else mirrors keep has a folder to go in. The old folder is moved rather than abandoned,
     * so nobody's captures are left behind to puzzle over.
     */
    @Test
    void anEarlierBuildsCaptureFolderIsMovedUnderMirror() throws IOException
    {
        final File data = new File(dataFolder, "data");
        final File earlier = new File(data, "mirror-captures");
        assertTrue(earlier.mkdirs());
        final File kept = new File(earlier, "world_1_2_3.view");
        Files.writeString(kept.toPath(), "not really a capture");

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
        final Capture fits = new Capture.Builder("far", true, new Capture.Box(82, 52, -22, 37, 37, 20), air).build();
        final Capture narrow = new Capture.Builder("far", true, new Capture.Box(83, 52, -22, 36, 37, 20), air).build();
        final Capture shortAhead = new Capture.Builder("far", true, new Capture.Box(82, 52, -22, 37, 37, 19), air).build();
        final Capture shallow = new Capture.Builder("far", true, new Capture.Box(82, 53, -22, 37, 36, 20), air).build();

        withServer(() ->
        {
            assertFalse(Captures.outgrown(mirror, fits), "the box one taken now would be");
            assertTrue(Captures.outgrown(mirror, narrow), "a block short to one side");
            assertTrue(Captures.outgrown(mirror, shortAhead), "a block short ahead");
            assertTrue(Captures.outgrown(mirror, shallow), "a block short below");
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(null);
            assertTrue(Captures.outgrown(mirror, narrow), "width is judged without the far world");
            assertFalse(Captures.outgrown(mirror, shallow), "height is not: it could not be retaken anyway");
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
        assertEquals(16, Captures.reach(null), "not loaded to ask: the view depth, which any capture holds");
        assertEquals(16, Captures.reach(far), "a world sending nothing -- a bare mock -- still reaches the depth");
        when(far.getViewDistance()).thenReturn(6);
        assertEquals(96, Captures.reach(far), "six chunks is 96 blocks, well past a depth of 16");
        when(far.getViewDistance()).thenReturn(32);
        assertEquals(160, Captures.reach(far), "never past ten chunks, however far a server sends");
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 160);
        when(far.getViewDistance()).thenReturn(2);
        assertEquals(160, Captures.reach(far), "and never short of the depth, or a view would outrun its capture");
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
        final Capture toTheDepth = new Capture.Builder("far", true, new Capture.Box(82, 52, -22, 37, 37, 20), air).build();
        // To the reach of 96: x 2..198, y -28..168, z -22..77.
        final Capture toTheReach = new Capture.Builder("far", true, new Capture.Box(2, -28, -22, 197, 197, 100), air).build();

        withServer(() ->
        {
            assertTrue(Captures.outgrown(mirror, toTheDepth), "taken to the depth alone: taken again, once");
            assertFalse(Captures.outgrown(mirror, toTheReach), "taken to the reach: it holds all a view can draw");
            ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 8);
            assertFalse(Captures.outgrown(mirror, toTheReach), "a lower depth draws less of it and asks for nothing");
            ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 120);
            assertTrue(Captures.outgrown(mirror, toTheReach), "a depth past the reach is the one thing that grows it");
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
        Captures.siftWith((builder, from, reach, floor) ->
        {
            throw new IllegalStateException("a bug in the sift");
        });

        withServer(() ->
        {
            assertTrue(Captures.request(mirror));
            assertEquals(1, pool.timers.size(), "the job's tick");
            pool.tickUntilIdle();
        });

        assertEquals(0, Captures.taking(), "the job is put down");
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
        Captures.siftWith((builder, from, reach, floor) ->
        {
            throw new OutOfMemoryError("a deep capture");
        });

        withServer(() ->
        {
            assertTrue(Captures.request(mirror));
            assertThrows(OutOfMemoryError.class, pool::tickUntilIdle, "the error is not swallowed");
            pool.runMain();
        });

        assertEquals(0, Captures.taking(), "the job is put down");
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
        Captures.siftWith((builder, from, reach, floor) ->
        {
            throw new IllegalStateException("a bug in the sift");
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            assertFalse(Captures.request(mirror), "the far world is not loaded yet");
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);
            for (int attempt = 0; attempt < 2; attempt++)
            {
                assertTrue(Captures.request(mirror));
                pool.tickUntilIdle();
            }
            verify(plugin).prettyLog(eq(Level.WARNING), contains("Could not work out what the mirror capture"),
                any(IllegalStateException.class));

            // An admin's retake is said again, so whoever ran it hears that it failed.
            assertTrue(Captures.retake(mirror));
            pool.tickUntilIdle();
        }

        verify(plugin).prettyLog(eq(Level.WARNING), contains("is not loaded"));
        verify(plugin, times(2)).prettyLog(eq(Level.WARNING), contains("Could not work out what the mirror capture"),
            any(IllegalStateException.class));
        assertEquals(0, Captures.taking());
    }

    /**
     * A job forgotten while its sift runs says nothing when the sift fails, and leaves the job
     * that replaced it alone.
     *
     * <p>Otherwise its warning used up the place's one, and the successor's own failure was never
     * logged.
     */
    @Test
    void aJobForgottenWhileItSiftsSaysNothingWhenItFails() throws Exception
    {
        final Pool pool = new Pool();
        pool.holdAsync = true;
        Captures.siftWith((builder, from, reach, floor) ->
        {
            throw new IllegalStateException("a bug in the sift");
        });

        withServer(() ->
        {
            assertTrue(Captures.request(mirror));
            for (int i = 0; (i < 100) && pool.held.isEmpty(); i++)
            {
                pool.tick();
            }
            assertEquals(1, pool.held.size(), "the first job's sift, still on the pool");
            Captures.forget(mirror);
            assertTrue(Captures.request(mirror));
            pool.runHeld();
            pool.runMain();
            assertEquals(1, Captures.taking(), "the second job is untouched");
            assertEquals(1, pool.timers.size(), "and still ticking");
            verify(plugin, never()).prettyLog(eq(Level.WARNING), contains("Could not work out what the mirror capture"),
                any(IllegalStateException.class));

            pool.holdAsync = false;
            pool.tickUntilIdle();
        });

        verify(plugin).prettyLog(eq(Level.WARNING), contains("Could not work out what the mirror capture"),
            any(IllegalStateException.class));
        assertEquals(0, Captures.taking());
    }

    /** Asks for a gate's capture, to depth 8, landing {@code east} blocks east of the museum's room: a few west keeps the same chunks. */
    private void requestGate(final String gate, final int east)
    {
        assertTrue(Captures.requestGate(Captures.gateKey(gate, Captures.GATE_OPENING, Captures.GATE_OPENING),
            gate, new Place("far", 100.5 + east, 70.0, -20.5, 0.0f, 0.0f), Captures.GATE_OPENING,
            Captures.GATE_OPENING, 8));
    }

    /** Ticks until a number of sifts are held on the pool and a number of gate sifts wait. */
    private static void tickUntilSifting(final Pool pool, final int held, final int waiting)
    {
        for (int i = 0; (i < 200) && ((pool.held.size() < held) || (Captures.gateSiftsWaiting() < waiting)); i++)
        {
            pool.tick();
        }
        assertEquals(held, pool.held.size(), "sifts on the pool");
        assertEquals(waiting, Captures.gateSiftsWaiting(), "gate sifts waiting their turn");
    }

    /**
     * Gate sifts take turns, oldest first, one forgotten while it waited is dropped, and a mirror's
     * does not wait behind them (#516).
     *
     * <p>A gate's sift is a core for seconds, over open sky for a minute and a half. Run as they came,
     * a hub of gates dialled at once was as many cores at once, and on a small server the main thread
     * starved.
     */
    @Test
    void gateSiftsTakeTurnsAndAMirrorsDoesNotWait() throws Exception
    {
        final Pool pool = new Pool();
        pool.holdAsync = true;
        final List<Double> sifted = new ArrayList<>();
        Captures.siftWith((builder, from, reach, floor) ->
        {
            sifted.add((double) from.x());
            return reach;
        });

        withServer(() ->
        {
            requestGate("Abydos", 0);
            requestGate("Chulak", -1);
            requestGate("Dakara", -2);
            requestGate("Edora", -3);
            assertTrue(Captures.request(mirror));
            tickUntilSifting(pool, 2, 3);

            Captures.forgetGate("Chulak");
            pool.runHeld();
            pool.runMain();

            assertEquals(List.of(100.0, 100.0), sifted, "Abydos's and the museum's sifts ran side by side");
            assertEquals(1, pool.held.size(), "then the next gate's, alone");
            assertEquals(1, Captures.gateSiftsWaiting(), "Chulak's dropped, and Edora's still waiting");
            pool.runHeld();
            assertEquals(98.0, sifted.get(2), "Dakara's, the oldest still wanted, not Chulak's or Edora's");
            pool.holdAsync = false;
            pool.runMain();
            pool.tickUntilIdle();
        });

        assertEquals(List.of(100.0, 100.0, 98.0, 97.0), sifted, "Edora's last, and Chulak's never");
        assertEquals(0, Captures.taking());
    }

    /**
     * A gate's name sign is left out of the capture of its front.
     *
     * <p>The sign hangs on a frame block that a view of the gate's front does not keep, so it was
     * drawn hanging in the air before the gate it belongs to.
     */
    @Test
    void aGatesNameSignIsLeftOutOfItsCapture() throws Exception
    {
        final Pool pool = new Pool();
        Captures.siftWith((builder, from, reach, floor) -> reach);
        final Block holder = mock(Block.class);
        final Block signBlock = mock(Block.class);
        when(holder.getRelative(BlockFace.SOUTH)).thenReturn(signBlock);
        when(signBlock.getX()).thenReturn(100);
        when(signBlock.getY()).thenReturn(66);
        when(signBlock.getZ()).thenReturn(-21);
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);
        final Stargate abydos = mock(Stargate.class);
        when(abydos.getGateNameBlockHolder()).thenReturn(holder);
        when(abydos.getGateFacing()).thenReturn(BlockFace.SOUTH);

        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(abydos);
            withServer(() ->
            {
                requestGate("Abydos", 0);
                pool.tickUntilIdle();
            });
        }

        final Capture capture = Captures.get(Captures.gateKey("Abydos", Captures.GATE_OPENING,
            Captures.GATE_OPENING));
        assertNotNull(capture);
        assertNotEquals(sand, capture.at(100, 66, -21), "the sign's own block is not drawn");
        assertEquals(sand, capture.at(100, 66, -22), "though the ground beside it is");
    }

    /**
     * Only a sign is blanked: a gate whose name sign is gone keeps whatever stands in its place.
     *
     * <p>The holder survives in the gate's record when the sign is removed or built over, and the
     * blank was taken on trust.
     */
    @Test
    void aBlockThatIsNoLongerTheNameSignIsKept() throws Exception
    {
        final Pool pool = new Pool();
        Captures.siftWith((builder, from, reach, floor) -> reach);
        final Block holder = mock(Block.class);
        final Block where = mock(Block.class);
        when(holder.getRelative(BlockFace.SOUTH)).thenReturn(where);
        when(where.getX()).thenReturn(100);
        when(where.getY()).thenReturn(66);
        when(where.getZ()).thenReturn(-21);
        when(where.getType()).thenReturn(Material.STONE);
        final Stargate abydos = mock(Stargate.class);
        when(abydos.getGateNameBlockHolder()).thenReturn(holder);
        when(abydos.getGateFacing()).thenReturn(BlockFace.SOUTH);

        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(abydos);
            withServer(() ->
            {
                requestGate("Abydos", 0);
                pool.tickUntilIdle();
            });
        }

        assertEquals(sand, Captures.get(Captures.gateKey("Abydos", Captures.GATE_OPENING,
            Captures.GATE_OPENING)).at(100, 66, -21), "a block that is not a sign is drawn");
    }

    /** A gate with a name holder and no facing is captured without a sign blanked, not thrown out of the sweep. */
    @Test
    void aGateWithNoFacingIsCapturedWithoutBlankingASign() throws Exception
    {
        final Pool pool = new Pool();
        Captures.siftWith((builder, from, reach, floor) -> reach);
        final Stargate abydos = mock(Stargate.class);
        final Block holder = mock(Block.class);
        when(abydos.getGateNameBlockHolder()).thenReturn(holder);

        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(abydos);
            withServer(() ->
            {
                requestGate("Abydos", 0);
                pool.tickUntilIdle();
            });
        }

        assertNotNull(Captures.get(Captures.gateKey("Abydos", Captures.GATE_OPENING,
            Captures.GATE_OPENING)));
    }

    /**
     * A gate forgotten mid-sift hands nothing back and lets the next gate's sift start at once.
     *
     * <p>Its sift stops at its next start point; waiting for that, or for a sift that never notices,
     * would hold every other gate's back.
     */
    @Test
    void aGateForgottenMidSiftLetsTheNextOneGoAtOnce() throws Exception
    {
        final Pool pool = new Pool();
        pool.holdAsync = true;
        Captures.siftWith((builder, from, reach, floor) -> reach);

        withServer(() ->
        {
            requestGate("Abydos", 0);
            requestGate("Chulak", -1);
            tickUntilSifting(pool, 1, 1);

            Captures.forgetGate("Abydos");

            assertEquals(2, pool.held.size(), "Chulak's sift started without waiting for Abydos's to end");
            pool.runHeld();
            assertEquals(1, pool.main.size(), "only Chulak's is handed back to the main thread");
            pool.holdAsync = false;
            pool.tickUntilIdle();
        });

        assertNotNull(Captures.get(Captures.gateKey("Chulak", Captures.GATE_OPENING, Captures.GATE_OPENING)));
        assertEquals(0, Captures.taking());
    }

    /**
     * A sift that ends as the plugin stops does not throw on the pool's thread.
     *
     * <p>The main thread refuses a task from a disabled plugin, and the refusal was thrown from the
     * sift's finally on the pool, logged as SEVERE.
     */
    @Test
    void aSiftEndingAsThePluginStopsThrowsNothing() throws Exception
    {
        final Pool pool = new Pool();
        pool.holdAsync = true;
        Captures.siftWith((builder, from, reach, floor) -> reach);

        withServer(() ->
        {
            requestGate("Abydos", 0);
            tickUntilSifting(pool, 1, 0);
            pool.refuseMain = true;
            pool.runHeld();
        });

        assertTrue(pool.escaped.isEmpty(), "nothing thrown on the pool: " + pool.escaped);
        assertTrue(pool.main.isEmpty(), "and nothing handed back");
    }

    /**
     * Where there is no scheduler, what the step after a sift throws is not swallowed.
     *
     * <p>Only the scheduler's refusal of a task, as the plugin stops, is caught. Caught round the
     * step itself, a real failure there -- or in the next gate's whole sift, which runs inside it on
     * this route -- was logged at FINE as the plugin stopping.
     */
    @Test
    void whatTheStepAfterASiftThrowsIsNotSwallowedWithNoScheduler()
    {
        Captures.siftWith((builder, from, reach, floor) ->
        {
            throw new IllegalStateException("a bug in the sift");
        });
        doThrow(new IllegalArgumentException("the step after it failed too")).when(plugin)
            .prettyLog(eq(Level.WARNING), contains("Could not work out what the mirror capture"), any(IllegalStateException.class));

        withServer(() ->
        {
            requestGate("Abydos", 0);
            assertThrows(IllegalArgumentException.class, () -> Captures.step(100), "reaches whoever stepped the job");
        });
    }

    /** With the sift working again, a request takes the capture from the start. */
    private void takenAfresh(final Pool pool)
    {
        Captures.siftWith(null);
        withServer(() ->
        {
            assertTrue(Captures.request(mirror));
            assertEquals(1, Captures.taking(), "a fresh job");
            assertEquals(1, pool.timers.size(), "with a tick of its own");
            pool.tickUntilIdle();
        });
        assertNotNull(Captures.get(mirror), "the capture is taken after all");
        assertEquals(0, Captures.taking());
    }

    /**
     * A scheduler whose repeating tasks run when told and stop when cancelled, whose async tasks
     * run at once, or are held when asked, and whose main-thread tasks wait for the next tick.
     *
     * <p>An async task's RuntimeException is swallowed, as a server's pool does; an Error is
     * let through to the test, where a server's pool would log it.
     */
    private static final class Pool
    {
        final Map<Integer, Runnable> timers = new LinkedHashMap<>();
        final List<Runnable> main = new ArrayList<>();
        final List<Runnable> held = new ArrayList<>();
        final List<RuntimeException> escaped = new ArrayList<>();
        boolean holdAsync;
        /** True once the plugin has stopped: the main thread refuses its tasks. */
        boolean refuseMain;
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
                if (holdAsync)
                {
                    held.add(invocation.getArgument(1));
                }
                else
                {
                    runAsync(invocation.getArgument(1));
                }
                return mock(BukkitTask.class);
            });
            when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation ->
            {
                if (refuseMain)
                {
                    throw new IllegalStateException("Plugin attempted to register task while disabled");
                }
                main.add(invocation.getArgument(1));
                return mock(BukkitTask.class);
            });
            PluginTestSupport.scheduler(scheduler);
        }

        private void runAsync(final Runnable task)
        {
            try
            {
                task.run();
            }
            catch (final RuntimeException swallowed)
            {
                escaped.add(swallowed);
            }
        }

        /** Finishes the async tasks being held. */
        void runHeld()
        {
            final List<Runnable> due = new ArrayList<>(held);
            held.clear();
            due.forEach(this::runAsync);
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

    /** The gate whose front the gate captures below show, and where travellers through it land. */
    private static final String GATE = "Abydos";

    private String gateKey()
    {
        return Captures.gateKey(GATE, Captures.GATE_OPENING, Captures.GATE_OPENING);
    }

    /** Takes a gate's capture of the beach, through the gate opening, to a depth. */
    private void takeGateCapture(final int depth)
    {
        withServer(() ->
        {
            assertTrue(Captures.requestGate(gateKey(), GATE, mirror.destination(), Captures.GATE_OPENING, Captures.GATE_OPENING, depth));
            Captures.step(100);
        });
    }

    /**
     * A gate's capture is kept with the gates, under the gate's name, and outlives a restart (#516).
     *
     * <p>Kept where a mirror's are, it was keyed by place and deleted at every startup by the sweep
     * for captures no mirror uses, so every gate started from nothing after a restart: half a
     * minute of plain horizon while the far side was read off the disk again.
     */
    @Test
    void aGatesCaptureIsKeptWithTheGatesAndOutlivesARestart()
    {
        takeGateCapture(8);

        final File file = new File(DataLayout.gateCaptureDir(), gateKey().substring("gate:".length()) + ".view");
        assertTrue(file.isFile(), "under the gates, named for the gate and its opening: " + file);
        assertEquals(0, Captures.sweepAbandoned(), "the mirrors' sweep does not see it");
        assertTrue(file.isFile());

        Captures.clear();

        assertNotNull(Captures.get(gateKey()), "read back as the base after a restart");
    }

    @Test
    void aGateKeyIsTheGateAndItsOpening()
    {
        assertTrue(Captures.gateKey("Abydos", 5, 5).startsWith("gate:abydos-"), Captures.gateKey("Abydos", 5, 5));
        assertTrue(Captures.gateKey("Abydos", 5, 5).endsWith("_5x5"));
        assertEquals(Captures.gateKey("Abydos", 5, 5), Captures.gateKey("ABYDOS", 5, 5),
            "gate names are told apart without case");
        assertNotEquals(Captures.gateKey("Abydos", 1, 2), Captures.gateKey("Abydos", 5, 5),
            "a small gate's capture is not a big one's: each holds only what its own opening lets through");
        assertEquals(Captures.keyOf(mirror.destination()), Captures.keyFor(mirror),
            "a mirror's is still its place, the name every capture on disk already has");
    }

    /**
     * A gate's capture reaches its own depth, not a mirror's.
     *
     * <p>Taken to a mirror's reach -- as far as the world sends, 160 blocks -- a capture of somewhere
     * nobody had loaded was some 230 chunks off the disk before a gate could show anything.
     */
    @Test
    void aGatesCaptureReachesItsOwnDepth()
    {
        takeGateCapture(8);
        final Capture capture = Captures.get(gateKey());

        assertTrue(Captures.reaches(capture, mirror.destination(), 8), "as deep as it was asked");
        // Past the two blocks a capture keeps beyond its depth, and short of the 16 a mirror's reaches here.
        assertFalse(Captures.reaches(capture, mirror.destination(), 12), "and no deeper: not a mirror's reach");
    }

    /**
     * A gate's captures are taken again while somebody is at the gate, when old or too shallow, and not otherwise.
     *
     * <p>The capture is the base; this is how it keeps up with what is built there, without loading
     * a chunk nobody is in.
     */
    @Test
    void aGatesCapturesAreRefreshedOnlyWhenOldOrTooShallow()
    {
        takeGateCapture(8);

        withServer(() ->
        {
            assertEquals(0, Captures.refreshGate(GATE, mirror.destination(), 8, 600L), "fresh and deep enough");
            assertEquals(1, Captures.refreshGate(GATE, mirror.destination(), 40, 600L), "too shallow for the depth now");
            Captures.step(1000);
            assertEquals(1, Captures.refreshGate(GATE, mirror.destination(), 40, -1L), "older than the limit");
        });
    }

    /**
     * Two gates whose names are made file-safe alike keep captures of their own.
     *
     * <p>Every character that is not a plain letter or digit became an underscore, so "a b" and
     * "a_b", or any two names in another alphabet, shared one file: dialling either retook it at
     * its own arrival, and the other gate drew the wrong place.
     */
    @Test
    void gatesWhoseNamesSanitiseAlikeKeepCapturesOfTheirOwn()
    {
        assertNotEquals(Captures.gateKey("a b", 5, 5), Captures.gateKey("a_b", 5, 5));
        assertNotEquals(Captures.gateKey("地球", 5, 5), Captures.gateKey("月球", 5, 5));
    }

    /** An empty capture file on disk, named as a gate's own for that opening would be. */
    private static File gateFile(final String gate, final int width, final int height) throws Exception
    {
        final File dir = DataLayout.gateCaptureDir();
        assertTrue(dir.isDirectory() || dir.mkdirs(), "the gate captures folder");
        final File file = new File(dir, Captures.gateKey(gate, width, height).substring("gate:".length()) + ".view");
        assertTrue(file.createNewFile(), file.getName());
        return file;
    }

    /**
     * Removing a gate deletes what it shows, through every opening, and nobody else's.
     *
     * <p>A sweep at startup did this before, and counted a gate gone whenever it had not loaded -- a
     * gate in a world another plugin loads later -- deleting what it showed. At removal there is no
     * guessing. A gate whose name only starts like another's keeps its own.
     */
    @Test
    void removingAGateDeletesWhatItShowsAndNothingElse() throws Exception
    {
        final File own = gateFile("Abydos", 5, 5);
        final File ownSmall = gateFile("Abydos", 1, 2);
        final File other = gateFile("Chulak", 5, 5);
        final File lookalike = gateFile("Abydos_2", 5, 5);

        assertEquals(2, Captures.forgetGate("Abydos"));

        assertFalse(own.exists(), "Abydos's own");
        assertFalse(ownSmall.exists(), "seen through another opening, still Abydos's");
        assertTrue(other.exists(), "another gate's");
        assertTrue(lookalike.exists(), "Abydos_2's, whose name only starts like it");
    }

    /**
     * A gate's capture on disk and not in memory is refreshed by the age of its file, and not read to find out.
     *
     * <p>A watched gate's captures were each read off the disk every minute to learn how old they
     * were, and being asked for kept them from ever being let go.
     */
    @Test
    void aGatesCaptureOnDiskIsRefreshedByItsFileAge()
    {
        takeGateCapture(8);
        Captures.clear();
        final File file = new File(DataLayout.gateCaptureDir(), gateKey().substring("gate:".length()) + ".view");

        withServer(() ->
        {
            assertEquals(0, Captures.refreshGate(GATE, mirror.destination(), 8, 600L), "a new file");
            assertTrue(file.setLastModified(System.currentTimeMillis() - 1_200_000L));
            assertEquals(1, Captures.refreshGate(GATE, mirror.destination(), 8, 600L), "twenty minutes old");
        });
    }

    /**
     * A gate removed while its first capture is still being taken does not have it written afterwards.
     *
     * <p>A capture in progress has neither a file nor a place in memory yet, so removing the gate
     * found nothing to forget, and the capture finished and wrote its file for a gate that was gone:
     * one built again under the name drew the old place.
     */
    @Test
    void aGateRemovedMidCaptureDoesNotHaveItWritten()
    {
        final File file = new File(DataLayout.gateCaptureDir(), gateKey().substring("gate:".length()) + ".view");
        withServer(() ->
        {
            assertTrue(Captures.requestGate(gateKey(), GATE, mirror.destination(), Captures.GATE_OPENING, Captures.GATE_OPENING, 8));
            Captures.step(1);

            Captures.forgetGate(GATE);
            Captures.step(100);
        });

        assertFalse(file.exists(), "no file for a gate that is gone");
        assertNull(Captures.get(gateKey()), "and nothing in memory");
    }

    /**
     * A gate's fill reaches as far as the far world's server sends, and no further.
     *
     * <p>Past the send distance a drawn block lands in a chunk the client does not hold and is
     * never seen, so a capture deeper than that is disk and memory spent on nothing. With the fill
     * off, or set shallower than the first step, the view stays at the first step.
     */
    @Test
    void aGatesFillReachesAsFarAsTheFarWorldSends()
    {
        final Place arrival = mirror.destination();
        when(far.getViewDistance()).thenReturn(6);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);

            assertEquals(96, Captures.gateFillDepth(arrival, 32), "six chunks sent: 96 blocks, not the 160 asked");
            ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 64);
            assertEquals(64, Captures.gateFillDepth(arrival, 32), "asked for less than is sent");
            ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 0);
            assertEquals(32, Captures.gateFillDepth(arrival, 32), "no fill: the first step");
            ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 16);
            assertEquals(32, Captures.gateFillDepth(arrival, 32), "a fill shallower than the first step is none");
            ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 160);
            when(far.getViewDistance()).thenReturn(1);
            assertEquals(32, Captures.gateFillDepth(arrival, 32),
                "a server sending less than the first step still gets the first step, not a shallower fill");
        }
    }

    /**
     * A gate's fill may be cut to fit as far back as its first step, as a mirror's may to its view depth.
     *
     * <p>With the floor at the reach, the cut to keep a capture under its cap never ran for a gate,
     * and a fill onto a jungle or an ocean bed kept millions of blocks the drawing then threw away.
     */
    @Test
    void aGatesFillMayBeCutAsFarBackAsItsFirstStep()
    {
        final int[] asked = new int[2];
        Captures.siftWith((builder, from, reach, floor) ->
        {
            asked[0] = reach;
            asked[1] = floor;
            return reach;
        });
        withServer(() ->
        {
            assertTrue(Captures.requestGate(gateKey(), GATE, mirror.destination(), Captures.GATE_OPENING, Captures.GATE_OPENING, 40));
            Captures.step(1000);
        });

        assertEquals(40, asked[0], "taken as far as the fill");
        assertEquals(32, asked[1], "and cut no shallower than the first step, gate-view-depth");
    }

    /**
     * A gate's capture that failed waits before it is tried again; a mirror's is tried again when next wanted.
     *
     * <p>A gate asks every sweep while it is open, so a fill that failed -- most likely for want of
     * memory -- was started again at once, every second, for as long as anybody stood there.
     */
    @Test
    void aGatesFailedCaptureWaitsBeforeItIsTriedAgain()
    {
        Captures.siftWith((builder, from, reach, floor) ->
        {
            throw new IllegalStateException("a bug in the sift");
        });
        withServer(() ->
        {
            assertTrue(Captures.requestGate(gateKey(), GATE, mirror.destination(), Captures.GATE_OPENING, Captures.GATE_OPENING, 8));
            Captures.step(1000);

            assertFalse(Captures.requestGate(gateKey(), GATE, mirror.destination(), Captures.GATE_OPENING, Captures.GATE_OPENING, 8),
                "not started again at once");
            assertTrue(Captures.request(mirror), "a mirror's, tried again when next wanted, as before");
        });
    }

    /**
     * A gate's fill cut to fit records how far it kept, and a view is drawn no further than that.
     *
     * <p>The box stays at the depth asked, so the capture is not asked for again every sweep; what it
     * truly holds ends at the cut, and past that this world would show.
     */
    @Test
    void aGatesFillCutToFitIsDrawnOnlyAsFarAsItKept()
    {
        // Shallow on purpose: every block this box's chunks are read for is a recorded mock call, and
        // at a hundred deep the suite ran out of heap.
        Captures.siftWith((builder, from, reach, floor) -> 20);
        withServer(() ->
        {
            assertTrue(Captures.requestGate(gateKey(), GATE, mirror.destination(), Captures.GATE_OPENING, Captures.GATE_OPENING, 40));
            Captures.step(4000);
        });
        final Capture capture = Captures.get(gateKey());

        assertEquals(20, capture.keptReach(), "cut from 40 to 20");
        assertEquals(20, Captures.drawableReach(capture, 160), "drawn no further than kept");
        assertEquals(10, Captures.drawableReach(capture, 10), "nor further than asked");
        assertTrue(Captures.reaches(capture, mirror.destination(), 40),
            "and its box still reaches what was asked, so it is not asked for again");
    }

    /**
     * A capture seen through a smaller opening, from before one served them all, is deleted as its gate is refreshed.
     *
     * <p>It is never drawn from again, so it is only disk; the one capture through the largest opening
     * is kept.
     */
    @Test
    void anOldSmallerCaptureGoesAsItsGateIsRefreshed() throws Exception
    {
        takeGateCapture(8);
        // Five by five: what every gate was seen through before a Large gate's opening served them all.
        final File small = gateFile(GATE, 5, 5);
        final File one = new File(DataLayout.gateCaptureDir(), gateKey().substring("gate:".length()) + ".view");

        withServer(() -> Captures.refreshGate(GATE, mirror.destination(), 8, 600L));

        assertFalse(small.exists(), "the smaller one, gone");
        assertTrue(one.exists(), "the one that serves them all, kept");
    }
}
