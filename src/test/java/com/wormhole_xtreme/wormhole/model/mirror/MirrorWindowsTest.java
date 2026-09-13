package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * The sweep that draws what is on the other side of window mirrors.
 *
 * <p>What matters: a viewer is shown the far side only through an opening, the view comes back
 * from them when they leave, it is a drawing throughout -- the wall is never opened, so the
 * opening is drawn as something still solid -- and windows sharing a wall never draw over each
 * other. That last one was a real bug: a row of alcoves a block apart took turns overwriting
 * each other's views every few seconds.
 */
class MirrorWindowsTest
{
    /** Where saves go, so no test writes a mirror file into the repository. */
    @TempDir
    File dataFolder;

    private World world;
    private World far;
    private World farTwo;
    private Block banner;
    private final BlockData air = mock(BlockData.class);
    private final BlockData barrier = mock(BlockData.class);
    private final BlockData farOneBlock = mock(BlockData.class);
    private final BlockData farTwoBlock = mock(BlockData.class);

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
                invocation.getArgument(2), true));

        banner = bannerAt(10, BlockFace.NORTH);

        far = farWorld("far", farOneBlock);
        farTwo = farWorld("far2", farTwoBlock);

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
     * A viewer is sent the view, and not again on a sweep where nothing changed.
     *
     * <p>The sweep runs every second for the life of the server. Resent on each one, a view is
     * thousands of block changes a second to somebody who is only standing still.
     */
    @Test
    void aPlayerInFrontIsSentTheViewOnceNotEverySweep()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorProximity.tick();
        });

        assertTrue(drawnAs(changesTo(viewer, 1).get(0), farOneBlock) > 0,
            "the far side is in the view");
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
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertEquals(MirrorWindow.WIDTH * MirrorWindow.HEIGHT,
            drawnAs(changesTo(viewer, 1).get(0), barrier));
    }

    /**
     * Only far-side blocks the eye could see through the opening are drawn.
     *
     * <p>The rest stay as the world has them, which is what leaves room for a neighbouring window
     * and keeps whatever is really behind the wall where nobody could see it anyway.
     */
    @Test
    void onlyBlocksSeenThroughTheOpeningAreDrawn()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 13)), "straight through the middle");
        assertFalse(drawn.containsKey(new Spot(18, 64, 12)),
            "off to the side, behind solid wall from where the viewer stands");
    }

    /**
     * A part of the opening with something solid in front of it does not open.
     *
     * <p>A row of alcoves puts a pillar in front of each opening's side columns. Treating those
     * as open is what let one alcove's view reach into the next.
     */
    @Test
    void aPillarInFrontOfPartOfTheOpeningClosesThatPart()
    {
        pillarAt(9);
        pillarAt(11);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertEquals(MirrorWindow.HEIGHT, drawnAs(changesTo(viewer, 1).get(0), barrier),
            "only the middle column, the one with open air in front of it");
    }

    /**
     * Two windows a block apart share the wall without drawing over each other.
     *
     * <p>Each block behind the wall is drawn once, from the window whose opening the viewer's line
     * of sight passes through. And nothing is resent on a second sweep, because nothing fights.
     */
    @Test
    void twoWindowsABlockApartNeverDrawTheSameBlock()
    {
        secondWindowAt(12);
        pillarAt(9);
        pillarAt(11);
        pillarAt(13);
        final Player viewer = playerAt(11.5, 6.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorProximity.tick();
        });

        final Collection<BlockState> batch = changesTo(viewer, 1).get(0);
        final Map<Spot, BlockData> drawn = positions(batch);
        assertEquals(batch.size(), drawn.size(), "no block is in the view twice");
        assertSame(farOneBlock, drawn.get(new Spot(10, 63, 12)), "behind the first alcove");
        assertSame(farTwoBlock, drawn.get(new Spot(12, 63, 12)), "behind the second");
    }

    @Test
    void walkingAwayTakesTheViewBack()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            stand(viewer, 10.5, -40.0);
            MirrorProximity.tick();
        });

        assertTakenBack(changesTo(viewer, 2));
    }

    /**
     * Turning a window off takes the view back from whoever had it.
     *
     * <p>Otherwise a player standing in front of it keeps a hole in the wall until something
     * resends that chunk.
     */
    @Test
    void releasingAWindowTakesTheViewBack()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorProximity.release(MirrorManager.byName("museum"));
        });

        assertTakenBack(changesTo(viewer, 2));
    }

    /**
     * Stepping sideways redraws at once, and sends only what changed.
     *
     * <p>Waiting for the next sweep left the view a second behind the viewer; resending all of it
     * on every step would be the whole view several times a second.
     */
    @Test
    void steppingSidewaysSendsOnlyWhatChanged()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorWindows.moved(viewer, new Location(world, 12.0, 64.0, 7.5));
        });

        final List<Collection<BlockState>> sent = changesTo(viewer, 2);
        assertTrue(!sent.get(1).isEmpty() && (sent.get(1).size() < sent.get(0).size()),
            "a partial update: " + sent.get(1).size() + " of " + sent.get(0).size());
    }

    @Test
    void clickingTheOpeningWhileLookingInIsTheMirror()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(MirrorManager.byName("museum"),
            MirrorWindows.clicked(viewer, blockAt(10, 63, 11, true)), "the middle of the opening");
        assertNull(MirrorWindows.clicked(viewer, blockAt(10, 63, 12, true)),
            "a block behind the wall is not the opening");
    }

    /** Every right-click on the server comes through here, and almost nobody is looking in. */
    @Test
    void aPlayerLookingIntoNoWindowIsAnsweredWithoutAskingTheBlock()
    {
        final Block wall = mock(Block.class);

        assertNull(MirrorWindows.clicked(playerAt(10.5, 7.5), wall));

        verifyNoInteractions(wall);
    }

    /**
     * A freestanding mirror opens too, in the air behind it and upwards from where it stands.
     *
     * <p>A banner hung on a wall hangs down, and a standing one stands up, so the opening follows
     * the cloth. Snapped to the nearest cardinal, since a standing banner may face sixteen ways.
     */
    @Test
    void aFreestandingBannerOpensUpwardsFromWhereItStands()
    {
        final Rotatable post = mock(Rotatable.class);
        when(post.getRotation()).thenReturn(BlockFace.NORTH_NORTH_WEST);
        when(banner.getBlockData()).thenReturn(post);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(barrier, drawn.get(new Spot(10, 66, 11)), "two above the banner's own row");
        assertFalse(drawn.containsKey(new Spot(10, 62, 11)) && (drawn.get(new Spot(10, 62, 11)) == barrier),
            "not below it, where a wall banner's opening would be");
        assertTrue(drawnAs(changesTo(viewer, 1).get(0), farOneBlock) > 0, "and the far side shows");
    }

    /**
     * A mirror whose banner cannot be a window stays exactly as it was.
     *
     * <p>A far side in a world that is not loaded has nothing to show, so the banner keeps its
     * look rather than opening onto nothing.
     */
    @Test
    void aMirrorOntoAnUnloadedWorldDrawsNothing()
    {
        MirrorManager.clear();
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("nowhere", 0.5, 64.0, 0.5, 0.0f, 0.0f)));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            verify(viewer, never()).sendBlockChanges(anyCollection());
            // The same banner, pointed somewhere loaded, does open: the refusal was the world.
            MirrorManager.add(MirrorManager.byName("museum")
                .withDestination(new MirrorPoint("far", 100.5, 70.0, -20.5, 0.0f, 0.0f)));
            MirrorProximity.tick();
        });

        changesTo(viewer, 1);
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
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(air, positions(changesTo(viewer, 1).get(0)).get(new Spot(10, 63, 12)));
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
        final Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("World", "far");
        destination.put("X", 100.5);
        destination.put("Y", 70.0);
        destination.put("Z", -20.5);
        destination.put("Yaw", 0.0);
        destination.put("Pitch", 0.0);
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("Banner", "world:10:64:10");
        entry.put("Destination", destination);
        entry.put("Display", "proximity");
        MirrorManager.add(MirrorYamlManager.readMirror("museum", entry));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertTrue(drawnAs(changesTo(viewer, 1).get(0), farOneBlock) > 0);
    }

    @Test
    void aPackedBlockKeyComesBackWhole()
    {
        for (final int[] spot : new int[][] { { 0, 0, 0 }, { -30000000, -64, 29999999 },
            { 12, 319, -1 }, { -1, -1, -1 } })
        {
            final long key = MirrorWindows.key(spot[0], spot[1], spot[2]);
            assertEquals(spot[0], MirrorWindows.unpackX(key));
            assertEquals(spot[1], MirrorWindows.unpackY(key));
            assertEquals(spot[2], MirrorWindows.unpackZ(key));
        }
    }

    /** Asserts the second of two sends put back every block the first drew over. */
    private static void assertTakenBack(final List<Collection<BlockState>> sent)
    {
        assertEquals(sent.get(0).size(), sent.get(1).size(), "every block drawn over comes back");
        assertTrue(sent.get(1).stream().noneMatch(state -> setBlockDataCalls(state).findAny()
            .isPresent()), "sent back as the world has them, not as they were drawn");
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

    /** Where each state in a batch is, and what it was drawn as (null if not drawn over). */
    private static Map<Spot, BlockData> positions(final Collection<BlockState> batch)
    {
        final Map<Spot, BlockData> at = new LinkedHashMap<>();
        for (final BlockState state : batch)
        {
            at.put(new Spot(state.getX(), state.getY(), state.getZ()),
                setBlockDataCalls(state).map(call -> (BlockData) call.getArgument(0)).findFirst()
                    .orElse(null));
        }
        return at;
    }

    private static long drawnAs(final Collection<BlockState> batch, final BlockData data)
    {
        return batch.stream()
            .filter(state -> setBlockDataCalls(state).anyMatch(call -> call.getArgument(0) == data))
            .count();
    }

    private static Stream<Invocation> setBlockDataCalls(final BlockState state)
    {
        return mockingDetails(state).getInvocations().stream()
            .filter(call -> "setBlockData".equals(call.getMethod().getName()));
    }

    /** A wall banner at z 10 facing north into the room, on the wall block to its south. */
    private Block bannerAt(final int x, final BlockFace facing)
    {
        final Block made = blockAt(x, 64, 10, true);
        when(made.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(made.getLocation()).thenReturn(new Location(world, x, 64.0, 10.0));
        when(made.getState()).thenAnswer(invocation -> stateAt(Banner.class, x, 64, 10));
        hangOnAWall(made, facing);
        // doReturn, because when() would call the catch-all answer mid-stubbing.
        doReturn(made).when(world).getBlockAt(x, 64, 10);
        return made;
    }

    private static void hangOnAWall(final Block block, final BlockFace facing)
    {
        final Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(facing);
        when(block.getBlockData()).thenReturn(data);
    }

    /** A second window, one pillar along, onto a different far side. */
    private void secondWindowAt(final int x)
    {
        bannerAt(x, BlockFace.NORTH);
        MirrorManager.add(new QuantumMirror("archive", new MirrorBlock("world", x, 64, 10),
            new MirrorPoint("far2", 300.5, 70.0, -20.5, 0.0f, 0.0f)));
    }

    /** A solid column in front of the wall, over the opening's rows. */
    private void pillarAt(final int x)
    {
        for (int y = 62; y <= 63; y++)
        {
            doReturn(blockAt(x, y, 10, false)).when(world).getBlockAt(x, y, 10);
        }
        // The banner row: a pillar beside the banner, not in its place.
        if (x != 10)
        {
            doReturn(blockAt(x, 64, 10, false)).when(world).getBlockAt(x, 64, 10);
        }
    }

    /** An ordinary block of the banner's world, with a fresh state per read as Bukkit gives. */
    private Block blockAt(final int x, final int y, final int z, final boolean passable)
    {
        final Block block = mock(Block.class);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getWorld()).thenReturn(world);
        when(block.isPassable()).thenReturn(passable);
        when(block.getState()).thenAnswer(invocation -> stateAt(BlockState.class, x, y, z));
        return block;
    }

    private static <T extends BlockState> T stateAt(final Class<T> type, final int x, final int y,
        final int z)
    {
        final T state = mock(type);
        when(state.getX()).thenReturn(x);
        when(state.getY()).thenReturn(y);
        when(state.getZ()).thenReturn(z);
        return state;
    }

    private static World named(final World mocked, final String name)
    {
        when(mocked.getName()).thenReturn(name);
        when(mocked.getMinHeight()).thenReturn(-64);
        when(mocked.getMaxHeight()).thenReturn(320);
        return mocked;
    }

    /** A far world made entirely of one block. */
    private static World farWorld(final String name, final BlockData everywhere)
    {
        final World made = named(mock(World.class), name);
        final Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(everywhere);
        when(made.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        return made;
    }

    private Player playerAt(final double x, final double z)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getEyeHeight()).thenReturn(1.62);
        stand(player, x, z);
        return player;
    }

    private void stand(final Player player, final double x, final double z)
    {
        when(player.getLocation()).thenReturn(new Location(world, x, 64.0, z));
        when(player.getEyeLocation()).thenReturn(new Location(world, x, 65.62, z));
    }

    /** Runs something with a server that knows these worlds, these players and two blocks. */
    private void withServer(final Runnable body)
    {
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);
            bukkit.when(() -> Bukkit.getWorld("far2")).thenReturn(farTwo);
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            bukkit.when(() -> Bukkit.createBlockData(Material.BARRIER)).thenReturn(barrier);
            final Set<UUID> ids = new HashSet<>();
            for (final Player player : world.getPlayers())
            {
                ids.add(player.getUniqueId());
                bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            }
            body.run();
        }
    }
}
