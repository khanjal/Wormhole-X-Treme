package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
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
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry;
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

    /** Whether the far world has its chunks loaded, which a test turns off to let them go. */
    private boolean chunksLoaded = true;

    /** One chunk, {@code {x, z}}, that is not loaded though the rest are, or null. */
    private int[] chunkLetGo;
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
        ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 16);
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
        chunksLoaded = true;
        chunkLetGo = null;
        when(far.isChunkLoaded(anyInt(), anyInt())).thenAnswer(invocation -> chunksLoaded
            && ((chunkLetGo == null) || (chunkLetGo[0] != (int) invocation.getArgument(0))
                || (chunkLetGo[1] != (int) invocation.getArgument(1))));
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
        Captures.loadFilesWith(null);
        Captures.clock = System::currentTimeMillis;
        PluginTestSupport.scheduler(null);
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /**
     * A following banner that cannot be stamped costs the capture nothing (#613).
     *
     * <p>Re-stamping the banners that follow a room runs as the capture finishes. Thrown out of
     * there, a banner's failure would leave a finished capture unsaved and unannounced.
     */
    @Test
    void aFollowingBannerThatThrowsLeavesTheCaptureTaken()
    {
        MirrorPresetRegistry.load(new File(dataFolder, "presets"));
        MirrorManager.add(mirror);
        MirrorManager.add(new QuantumMirror("hall", new BlockPlace("far", 100, 75, -25), null, null, "museum", 1, true));
        when(far.getBlockAt(anyInt(), anyInt(), anyInt())).thenThrow(new IllegalStateException("no banner here"));

        withServer(() ->
        {
            assertTrue(Captures.request(mirror));
            Captures.step(100);
        });

        assertNotNull(Captures.get(mirror), "the capture is kept");
        verify(plugin).prettyLog(eq(Level.WARNING), contains("Could not stamp the banners following"),
            any(IllegalStateException.class));
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
     * A captures folder left beside the mirror file by an earlier build is moved under captures/.
     *
     * <p>Captures moved from data/mirror-captures/ to data/captures/mirrors/ (#655), apart from
     * the saved gates. The old folder is moved rather than abandoned,
     * so nobody's captures are left behind to puzzle over.
     */
    @Test
    void anEarlierBuildsCaptureFolderIsMovedUnderCaptures() throws IOException
    {
        final File data = new File(dataFolder, "data");
        final File earlier = new File(data, "mirror-captures");
        assertTrue(earlier.mkdirs());
        final File kept = new File(earlier, "world_1_2_3.view");
        Files.writeString(kept.toPath(), "not really a capture");

        final File dir = DataLayout.mirrorCaptureDir();

        assertEquals(new File(new File(data, "captures"), "mirrors"), dir);
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
        ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 160);
        when(far.getViewDistance()).thenReturn(2);
        assertEquals(160, Captures.reach(far), "and never short of the depth, or a view would outrun its capture");
    }

    /**
     * Changing the view depth does not take a capture again; only a reach past what it holds does.
     *
     * <p>The point of a reach of its own: an operator lowering {@code view-depth} for a
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
            ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 8);
            assertFalse(Captures.outgrown(mirror, toTheReach), "a lower depth draws less of it and asks for nothing");
            ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 120);
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

    /** Ticks until a number of sifts are held on the pool. */
    private static void tickUntilSifting(final Pool pool, final int held)
    {
        for (int i = 0; (i < 200) && (pool.held.size() < held); i++)
        {
            pool.tick();
        }
        assertEquals(held, pool.held.size(), "sifts on the pool");
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
            assertTrue(Captures.request(mirror));
            tickUntilSifting(pool, 1);
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
            assertTrue(Captures.request(mirror));
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

    /** Takes the museum's capture and writes it, with no scheduler, then forgets it as a restart would: on disk only. */
    private void mirrorCaptureOnDiskOnly()
    {
        withServer(() ->
        {
            assertTrue(Captures.request(mirror));
            Captures.step(100);
        });
        Captures.clear();
        assertTrue(mirrorFile().isFile(), "written to disk");
    }

    private File mirrorFile()
    {
        return new File(DataLayout.mirrorCaptureDir(), Captures.keyFor(mirror) + ".view");
    }

    /** A pool that holds what it is handed off the main thread, to be counted and run when told. */
    private static Pool holdingPool() throws Exception
    {
        final Pool pool = new Pool();
        pool.holdAsync = true;
        return pool;
    }

    /**
     * A capture on disk is read off the main thread once, and served from memory once it lands (#528).
     *
     * <p>Read on the main thread, a capture at the cap was one hitch the first time a mirror or gate
     * was drawn from it after a restart, or after five minutes nobody looked.
     */
    @Test
    void aCaptureOnDiskIsReadOffTheMainThreadOnceThenServedFromMemory() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        final int[] loads = { 0 };
        Captures.loadFilesWith(file ->
        {
            loads[0]++;
            return Capture.load(file);
        });

        assertNull(Captures.get(mirror), "not ready while it is read");
        assertEquals(1, pool.held.size(), "one read, on the pool");
        assertNull(Captures.get(mirror), "still not ready");
        assertEquals(1, pool.held.size(), "and no second read of the same file");
        assertEquals(0, loads[0], "nothing read on the main thread: the file is opened only once the pool runs the read");
        pool.runHeld();
        assertEquals(1, loads[0], "read once, by the pool");
        assertNull(Captures.get(mirror), "read, but not yet handed to the main thread");
        assertTrue(pool.held.isEmpty(), "still no second read while it waits for the main thread");
        final int before = Captures.generation();
        pool.runMain();

        assertNotEquals(before, Captures.generation(), "every view looks again once it lands");
        assertTrue(mirrorFile().delete(), "the file, gone from disk");
        final Capture capture = Captures.get(mirror);
        assertNotNull(capture, "served from memory, with no file left to read");
        assertFalse(capture.isAir(100, 69, -21), "the beach it captured");
        assertTrue(pool.held.isEmpty(), "and nothing read again");
        assertEquals(1, loads[0], "not on any thread");
    }

    /**
     * A mirror forgotten while its capture is read off the disk does not have it put back.
     *
     * <p>Its read lands a tick or more later; kept then, a mirror removed or re-pointed meanwhile
     * would hold the old room in memory, and draw it to one built again on the spot.
     */
    @Test
    void aMirrorForgottenWhileItsCaptureIsReadDoesNotHaveItPutBack() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        assertNull(Captures.get(mirror));
        pool.runHeld();

        Captures.forget(mirror);
        pool.runMain();

        assertFalse(mirrorFile().exists(), "its file deleted");
        assertNull(Captures.get(mirror), "and nothing in memory for a mirror that is gone");
    }

    /**
     * Captures cleared while one is read -- a reload, or the plugin stopping -- drop what the read finds.
     *
     * <p>Otherwise a reload that let every capture go had one come back from before it.
     */
    @Test
    void aReadUnderWayWhenCapturesAreClearedLandsOnNothing() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        assertNull(Captures.get(mirror));
        pool.runHeld();

        Captures.clear();
        assertNull(Captures.get(mirror), "not in memory after the clear");
        assertEquals(1, pool.held.size(), "so its file is read afresh");
        pool.runMain();

        assertNull(Captures.get(mirror), "the old read landing is dropped, not taken for the new one");
        assertEquals(1, pool.held.size(), "and the new read is still the only one");
    }

    /**
     * A capture taken while the old one is read off the disk is not replaced by the old one landing.
     *
     * <p>An admin's {@code -capture} goes ahead during a read. The read can land after the new
     * capture, and putting it in memory then would draw the room as it was before the retake.
     */
    @Test
    void aCaptureTakenWhileTheOldOneIsReadIsNotReplacedByIt() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        assertNull(Captures.get(mirror));
        final List<Runnable> read = new ArrayList<>(pool.held);
        pool.held.clear();
        pool.holdAsync = false;

        withServer(() ->
        {
            assertTrue(Captures.retake(mirror), "an admin's retake goes ahead during the read");
            pool.tickUntilIdle();
        });
        final Capture fresh = Captures.get(mirror);
        assertNotNull(fresh, "the retake landed first");
        read.forEach(Runnable::run);
        pool.runMain();

        assertSame(fresh, Captures.get(mirror), "the capture just taken, not the file read under it");
    }

    /**
     * An unreadable file is warned about once, as it lands on the main thread, and not read again.
     *
     * <p>As before the read moved off the main thread: a mirror with a broken file costs one failed
     * read, not one a sweep.
     */
    @Test
    void anUnreadableFileReadOffTheMainThreadIsWarnedOfOnceAndNotReadAgain() throws Exception
    {
        assertTrue(DataLayout.mirrorCaptureDir().mkdirs());
        Files.write(mirrorFile().toPath(), new byte[] { 1, 2, 3 });
        final Pool pool = holdingPool();

        assertNull(Captures.get(mirror));
        pool.runHeld();
        pool.runMain();

        verify(plugin).prettyLog(eq(Level.WARNING), contains("Could not read mirror capture"), any(IOException.class));
        assertNull(Captures.get(mirror));
        assertTrue(pool.held.isEmpty(), "remembered as unreadable, not read again");
    }

    /** Where there is no scheduler -- startup, or a test -- a capture is still read at once, and a broken one warned of at once. */
    @Test
    void withNoSchedulerAnUnreadableFileIsWarnedOfAtOnce() throws Exception
    {
        assertTrue(DataLayout.mirrorCaptureDir().mkdirs());
        Files.write(mirrorFile().toPath(), new byte[] { 1, 2, 3 });

        assertNull(Captures.get(mirror));
        assertNull(Captures.get(mirror));

        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), contains("Could not read mirror capture"),
            any(IOException.class));
    }

    /**
     * A capture file of sections cut short is warned of once and remembered as unreadable, as a
     * file of the list before it was (#525).
     *
     * <p>A capture written as sections, then cut off part way through them, as a full disk or a
     * killed server leaves one.
     */
    @Test
    void aSectionsFileCutShortIsWarnedOfOnceAndRememberedAsUnreadable() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final byte[] whole = CaptureSectionsTest.unzipped(mirrorFile());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(mirrorFile().toPath())))
        {
            out.write(whole, 0, whole.length - 9);
        }

        assertNull(Captures.get(mirror), "cut short, so none");
        assertNull(Captures.get(mirror), "and not read again");

        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), contains("Could not read mirror capture"),
            any(EOFException.class));
    }

    /**
     * A read that ends as the plugin stops does not throw on the pool's thread.
     *
     * <p>The main thread refuses a task from a disabled plugin; as for a sift, the refusal is not
     * thrown on the pool, where it would be logged as SEVERE.
     */
    @Test
    void aReadEndingAsThePluginStopsThrowsNothing() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        assertNull(Captures.get(mirror));

        pool.refuseMain = true;
        pool.runHeld();

        assertTrue(pool.escaped.isEmpty(), "nothing thrown on the pool: " + pool.escaped);
        assertTrue(pool.main.isEmpty(), "and nothing handed back");
    }

    /**
     * A mirror whose capture is being read is not captured afresh by the sweep.
     *
     * <p>The sweep takes null from {@code get} as "no capture yet" and asks for one. Asked during
     * a read, that would cast every ray again and write over the very file being read.
     */
    @Test
    void aMirrorWhoseCaptureIsBeingReadIsNotTakenAfresh() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        final Block banner = mock(Block.class);
        final Directional facing = mock(Directional.class);
        when(facing.getFacing()).thenReturn(BlockFace.NORTH);
        when(banner.getBlockData()).thenReturn(facing);

        withServer(() ->
        {
            assertFalse(WindowSweep.offerMirror(mirror, banner), "the banner shows while it is read");
            assertEquals(1, pool.held.size(), "the read the sweep started");
            assertFalse(Captures.request(mirror), "and nothing taken by asking again");
            assertEquals(0, Captures.taking(), "no fresh capture of the room");
        });
    }

    /**
     * A read that fails after a retake has landed does not mark the place as having no capture.
     *
     * <p>A retake's save cannot break the read (it renames a temporary file over, and on Windows
     * the rename is what fails), so a failure here is a file bad all along, met by a retake in
     * flight: rare, and the guard stays because a failed read must never override a newer capture.
     * Counted absent, the place had no capture the moment the retake was let go as idle, and the
     * next look took the room afresh although the retake had just taken it.
     */
    @Test
    void aReadFailingAfterARetakeLandedDoesNotHideTheRetake() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        assertNull(Captures.get(mirror));
        final List<Runnable> read = new ArrayList<>(pool.held);
        pool.held.clear();
        pool.holdAsync = false;
        withServer(() ->
        {
            assertTrue(Captures.retake(mirror));
            pool.tickUntilIdle();
        });
        assertNotNull(Captures.get(mirror), "the retake landed first");
        Captures.loadFilesWith(file ->
        {
            throw new IOException("a file bad all along");
        });

        read.forEach(Runnable::run);
        pool.runMain();
        Captures.loadFilesWith(null);
        final long later = System.currentTimeMillis() + 400_000L;
        Captures.clock = () -> later;
        Captures.unloadIdle();
        pool.holdAsync = true;

        verify(plugin, never()).prettyLog(eq(Level.WARNING), contains("Could not read mirror capture"), any(IOException.class));
        assertNull(Captures.get(mirror), "let go as idle");
        assertEquals(1, pool.held.size(), "and read again from its file, not counted as having none");
    }

    /**
     * A read whose landing never comes is counted lost after a minute, and the file read again.
     *
     * <p>A landing the scheduler dropped left the key loading for good: its view never drew, and
     * nothing could take it afresh either, until a restart.
     */
    @Test
    void aReadThatNeverLandsIsCountedLostAfterAMinute() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        final long[] now = { 1_000_000L };
        Captures.clock = () -> now[0];
        assertNull(Captures.get(mirror));
        pool.held.clear();

        now[0] += Captures.READ_TIMEOUT_MILLIS - 1;
        assertNull(Captures.get(mirror), "still waited for just short of a minute");
        assertTrue(pool.held.isEmpty(), "with no second read");
        withServer(() -> assertFalse(Captures.request(mirror), "and nothing taken over it"));

        now[0]++;
        withServer(() -> assertTrue(Captures.request(mirror), "a lost read no longer holds back a capture"));
        assertNull(Captures.get(mirror));
        assertEquals(1, pool.held.size(), "the file is read again");
        verify(plugin).prettyLog(eq(Level.WARNING), contains("did not come back"));
        pool.runHeld();
        pool.runMain();
        assertNotNull(Captures.get(mirror), "and lands");
    }

    /** A read landing as the plugin is let go warns nobody and throws nothing, and still remembers an unreadable file. */
    @Test
    void anUnreadableFileLandingWithNoPluginThrowsNothing() throws Exception
    {
        assertTrue(DataLayout.mirrorCaptureDir().mkdirs());
        Files.write(mirrorFile().toPath(), new byte[] { 1, 2, 3 });
        final Pool pool = holdingPool();
        assertNull(Captures.get(mirror));
        pool.runHeld();

        PluginTestSupport.remove();
        try
        {
            pool.runMain();
        }
        finally
        {
            PluginTestSupport.install(plugin);
        }

        assertNull(Captures.get(mirror));
        assertTrue(pool.held.isEmpty(), "remembered as unreadable, not read again");
    }

    /**
     * An Error while reading -- running out of memory on a big capture -- reaches the pool, and still lets the key go.
     *
     * <p>Left loading, the place would never be read or taken again until a restart.
     */
    @Test
    void anErrorWhileReadingStillLetsTheKeyGo() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        Captures.loadFilesWith(file ->
        {
            throw new OutOfMemoryError("a capture too big to read");
        });
        assertNull(Captures.get(mirror));

        assertThrows(OutOfMemoryError.class, pool::runHeld, "the error is not swallowed");
        assertEquals(1, pool.main.size(), "handed back all the same");
        pool.runMain();
        Captures.loadFilesWith(null);

        assertNull(Captures.get(mirror));
        assertEquals(1, pool.held.size(), "read again when next wanted, not counted as having none");
        verify(plugin, never()).prettyLog(eq(Level.WARNING), contains("Could not read mirror capture"), any(Throwable.class));
    }

    /**
     * A read that lands before a retake is drawn until the retake replaces it.
     *
     * <p>The other order from {@link #aCaptureTakenWhileTheOldOneIsReadIsNotReplacedByIt}: newer
     * wins either way, so neither a read nor a retake may keep its place by having arrived first.
     */
    @Test
    void aReadLandingBeforeARetakeIsDrawnUntilTheRetakeReplacesIt() throws Exception
    {
        mirrorCaptureOnDiskOnly();
        final Pool pool = holdingPool();
        withServer(() ->
        {
            assertTrue(Captures.retake(mirror));
            assertNull(Captures.get(mirror));
            assertEquals(1, pool.held.size(), "the read, while the retake waits for its first tick");
            pool.runHeld();
            pool.runMain();
            final Capture old = Captures.get(mirror);
            assertNotNull(old, "the file read, drawn while the retake runs");

            pool.holdAsync = false;
            pool.tickUntilIdle();
            final Capture fresh = Captures.get(mirror);
            assertNotNull(fresh);
            assertNotSame(old, fresh, "the retake replaced the capture read first");
        });
    }
}
