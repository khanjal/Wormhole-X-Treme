package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * The sweep that draws a window mirror's far side for whoever stands in front of it.
 *
 * <p>Three things matter: the view goes only to people on the banner's side of the wall, it
 * comes back from them when they leave, and it is a drawing throughout -- the wall is never
 * really opened, so the opening has to be drawn as something still solid.
 */
class MirrorWindowsTest
{
    /** Where saves go, so no test writes a mirror file into the repository. */
    @TempDir
    File dataFolder;

    private World world;
    private World far;
    private Block banner;
    private final BlockData air = mock(BlockData.class);
    private final BlockData barrier = mock(BlockData.class);

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorProximity.clear();

        world = named(mock(World.class), "world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
            blockAt(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2)));

        banner = mock(Block.class);
        when(banner.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(banner.getWorld()).thenReturn(world);
        when(banner.getLocation()).thenReturn(new Location(world, 10.0, 64.0, 10.0));
        when(banner.getState()).thenAnswer(invocation -> mock(Banner.class));
        hangOnAWall();
        // doReturn, because when() would call the answer above mid-stubbing.
        doReturn(banner).when(world).getBlockAt(10, 64, 10);

        far = named(mock(World.class), "far");
        when(far.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
        {
            final Block block = mock(Block.class);
            when(block.getBlockData()).thenReturn(mock(BlockData.class));
            return block;
        });

        // Nothing set on it: any mirror on a wall is a window.
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 100.5, 70.0, -20.5, 0.0f, 0.0f)));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        MirrorProximity.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /**
     * A viewer is sent the whole view, and once rather than on every sweep.
     *
     * <p>The sweep runs every second for the life of the server. Resent on each one, a view is
     * thousands of block changes a second to somebody who is only standing still.
     */
    @Test
    void aPlayerInFrontIsSentTheFarSideOnceNotEverySweep()
    {
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorProximity.tick();
        });

        assertEquals(drawnPerView(), changesTo(viewer, 1).get(0).size(),
            "the banner, the opening, and every block of the box behind it");
    }

    /**
     * A mirror written before windows existed opens as one, with nothing in its file changed.
     *
     * <p>That is the whole upgrade path: a window is a fact about where the banner hangs, not a
     * setting, so there is no migration to run and no file for a server to rewrite.
     */
    @Test
    void aMirrorFromAnOlderFileOpensAsAWindow()
    {
        MirrorManager.clear();
        final java.util.Map<String, Object> destination = new java.util.LinkedHashMap<>();
        destination.put("World", "far");
        destination.put("X", 100.5);
        destination.put("Y", 70.0);
        destination.put("Z", -20.5);
        destination.put("Yaw", 0.0);
        destination.put("Pitch", 0.0);
        final java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("Banner", "world:10:64:10");
        entry.put("Destination", destination);
        entry.put("Display", "proximity");
        MirrorManager.add(MirrorYamlManager.readMirror("museum", entry));
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertEquals(drawnPerView(), changesTo(viewer, 1).get(0).size());
    }

    /**
     * The opening is drawn as barrier: invisible, and as solid as the wall it covers.
     *
     * <p>Drawn as air, the client would let the player walk into blocks the server still has,
     * and the two would argue about where they are standing.
     */
    @Test
    void theOpeningIsDrawnAsBarrierSoItStaysSolid()
    {
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final long barriers = changesTo(viewer, 1).get(0).stream()
            .filter(state -> drawnAs(state, barrier)).count();
        assertEquals(MirrorWindow.WIDTH * MirrorWindow.HEIGHT, barriers);
    }

    @Test
    void behindTheOpeningIsTheFarSidesOwnBlocks()
    {
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final long farBlocks = changesTo(viewer, 1).get(0).stream()
            .filter(state -> !drawnAs(state, air) && !drawnAs(state, barrier)).count();
        assertEquals(drawnPerView() - (MirrorPackets.available() ? 1 : 0)
            - (MirrorWindow.WIDTH * MirrorWindow.HEIGHT), farBlocks);
    }

    /**
     * A mirror's banner at the far side shows as the opening it is there, not as cloth.
     *
     * <p>A linked pair arrives in the far banner's own block, so without this that banner hangs
     * in the middle of the view.
     */
    @Test
    void aMirrorBannerAtTheFarSideIsLeftOutOfTheView()
    {
        MirrorManager.add(new QuantumMirror("return", new MirrorBlock("far", 100, 71, -21), null));
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final long airs = changesTo(viewer, 1).get(0).stream()
            .filter(state -> drawnAs(state, air)).count();
        assertEquals((MirrorPackets.available() ? 1 : 0) + 1, airs,
            "this mirror's banner where the client can be given it back, and the far one");
    }

    /**
     * Somebody behind the wall is not shown it, while somebody in front is.
     *
     * <p>Behind the wall the view is drawn where they are standing. Both players are within
     * range, so only the side of the wall separates them.
     */
    @Test
    void aPlayerBehindTheWallIsSentNothingWhileOneInFrontIs()
    {
        final Player behind = playerAt(10.5, 14.5);
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(behind, viewer));

        withServer(MirrorProximity::tick);

        changesTo(viewer, 1);
        verify(behind, never()).sendBlockChanges(anyCollection());
    }

    @Test
    void walkingAwayTakesTheViewBack()
    {
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            when(viewer.getLocation()).thenReturn(new Location(world, 10.5, 64.0, -40.0));
            MirrorProximity.tick();
        });

        assertTakenBack(changesTo(viewer, 2));
    }

    /**
     * Turning a window off takes the view back from whoever had it.
     *
     * <p>Otherwise the sweep simply stops visiting the mirror, and a player standing in front of
     * it keeps a hole in the wall until something resends that chunk.
     */
    @Test
    void turningTheWindowOffTakesTheViewBack()
    {
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorProximity.release(MirrorManager.byName("museum"));
        });

        assertTakenBack(changesTo(viewer, 2));
    }

    @Test
    void clickingTheOpeningWhileLookingInIsTheMirror()
    {
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(MirrorManager.byName("museum"), MirrorWindows.clicked(viewer, blockAt(10, 63, 11)),
            "the middle of the opening");
        assertNull(MirrorWindows.clicked(viewer, blockAt(10, 63, 12)),
            "a block behind the wall is not the opening");
    }

    /** Every right-click on the server comes through here, and almost nobody is looking in. */
    @Test
    void aPlayerLookingIntoNoWindowIsAnsweredWithoutAskingTheBlock()
    {
        final Block wall = mock(Block.class);

        assertNull(MirrorWindows.clicked(inFront(), wall));

        verifyNoInteractions(wall);
    }

    /**
     * A freestanding banner has no wall to open, even one facing a cardinal.
     *
     * <p>Hung on a wall afterwards, the same mirror draws, which is what shows the refusal was
     * about the banner and not about something else in the setup.
     */
    @Test
    void aFreestandingBannerDrawsNothingUntilItHangsOnAWall()
    {
        final Rotatable post = mock(Rotatable.class);
        when(post.getRotation()).thenReturn(BlockFace.NORTH);
        when(banner.getBlockData()).thenReturn(post);
        final Player viewer = inFront();
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            verify(viewer, never()).sendBlockChanges(anyCollection());
            hangOnAWall();
            MirrorProximity.tick();
        });

        changesTo(viewer, 1);
    }

    /** Asserts the second of two sends put back every block the first drew over. */
    private static void assertTakenBack(final List<Collection<BlockState>> sent)
    {
        assertEquals(sent.get(0).size(), sent.get(1).size(), "every block drawn over comes back");
        assertTrue(sent.get(1).stream().noneMatch(MirrorWindowsTest::drawnOver),
            "sent back as the world has them, not as they were drawn");
    }

    /** How many blocks one view is. */
    private static int drawnPerView()
    {
        final int box = (MirrorWindow.WIDTH + (2 * MirrorWindow.SIDE))
            * (MirrorWindow.HEIGHT + MirrorWindow.ABOVE + MirrorWindow.BELOW)
            * MirrorWindow.DEPTH;
        return (MirrorPackets.available() ? 1 : 0) + (MirrorWindow.WIDTH * MirrorWindow.HEIGHT)
            + box;
    }

    /** The block-change batches one player was sent, asserting how many. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<Collection<BlockState>> changesTo(final Player player, final int howMany)
    {
        final ArgumentCaptor<Collection<BlockState>> sent =
            ArgumentCaptor.forClass((Class) Collection.class);
        verify(player, times(howMany)).sendBlockChanges(sent.capture());
        return sent.getAllValues();
    }

    private static boolean drawnOver(final BlockState state)
    {
        return setBlockDataCalls(state).findAny().isPresent();
    }

    private static boolean drawnAs(final BlockState state, final BlockData data)
    {
        return setBlockDataCalls(state).anyMatch(call -> call.getArgument(0) == data);
    }

    private static java.util.stream.Stream<Invocation> setBlockDataCalls(final BlockState state)
    {
        return mockingDetails(state).getInvocations().stream()
            .filter(call -> "setBlockData".equals(call.getMethod().getName()));
    }

    /** The banner hung on the wall to its south, facing north into the room. */
    private void hangOnAWall()
    {
        final Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(banner.getBlockData()).thenReturn(data);
    }

    /** An ordinary block of the banner's world, with a fresh state per read as Bukkit gives. */
    private Block blockAt(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getWorld()).thenReturn(world);
        when(block.getState()).thenAnswer(invocation -> mock(BlockState.class));
        return block;
    }

    private static World named(final World mocked, final String name)
    {
        when(mocked.getName()).thenReturn(name);
        when(mocked.getMinHeight()).thenReturn(-64);
        when(mocked.getMaxHeight()).thenReturn(320);
        return mocked;
    }

    /** Somebody a couple of blocks out from the banner, in the room. */
    private Player inFront()
    {
        return playerAt(10.5, 7.5);
    }

    private Player playerAt(final double x, final double z)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, x, 64.0, z));
        return player;
    }

    /** Runs something with a server that knows both worlds, these players and two blocks. */
    private void withServer(final Runnable body)
    {
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            bukkit.when(() -> Bukkit.createBlockData(Material.BARRIER)).thenReturn(barrier);
            for (final Player player : world.getPlayers())
            {
                bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            }
            body.run();
        }
    }
}
