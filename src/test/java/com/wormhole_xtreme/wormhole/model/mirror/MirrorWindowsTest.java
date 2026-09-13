package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
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
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * The sweep that draws what is on the other side of window mirrors.
 *
 * <p>What matters: a viewer is shown the far side only through an opening and never past its
 * edges, the view comes back from them when they leave, it is a drawing throughout -- the
 * opening is drawn as something still solid -- windows sharing a wall never draw over each
 * other, and all of it stays cheap enough for a server with people walking past mirrors: redraws
 * are rationed, only differences are sent, and a far chunk is never loaded mid-tick.
 *
 * <p>The world here is a wall along z 11 -- the layer every opening in these tests sits in --
 * with open air in front of it. {@link #wallBehind} takes the wall away.
 */
class MirrorWindowsTest
{
    /** Where saves go, so no test writes a mirror file into the repository. */
    @TempDir
    File dataFolder;

    /** Whether the layer the openings sit in is solid wall, or open air. */
    private boolean wallBehind = true;

    /** Whether every real block of this world, other than the banners, is empty. */
    private boolean localEmpty;

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
        // Shallow unless a test is about depth: every block drawn here is a mock, and a deep cone
        // is thousands of them.
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 16);
        MirrorManager.clear();
        MirrorProximity.clear();

        world = named(mock(World.class), "world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
            blockAt(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2), true));

        banner = bannerAt(10);
        hangOnAWall(banner);

        far = farWorld("far", farOneBlock);
        farTwo = farWorld("far2", farTwoBlock);

        // Nothing set on it: every mirror is a window.
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

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(barrier, drawn.get(new Spot(10, 64, 11)), "behind the banner");
        assertSame(barrier, drawn.get(new Spot(10, 63, 11)), "behind its cloth, a block down");
    }

    /**
     * The blocks in front of the opening are barrier too, so nobody can press right up to it.
     *
     * <p>From a few tenths of a block away the view through a mirror is nearly half a sphere, far
     * more than any redraw's budget, and the real world showed through wherever it ran out. A
     * block back, it fits.
     */
    @Test
    void theBlocksInFrontOfTheOpeningKeepAViewerABlockAway()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(barrier, drawn.get(new Spot(10, 63, 10)), "in front of the lower row");
        if (MirrorPackets.available())
        {
            assertSame(barrier, drawn.get(new Spot(10, 64, 10)), "and in the banner's own block");
        }
    }

    /**
     * Somebody standing in the banner's block is not walled in there.
     *
     * <p>A linked pair puts an arriving traveller exactly there. Drawing barrier where they stand
     * would leave the client and the server arguing about where they are.
     */
    @Test
    void aPlayerStandingInTheBannersBlockIsNotWalledIn()
    {
        final Player arrived = playerAt(10.5, 10.5);
        when(world.getPlayers()).thenReturn(List.of(arrived));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(arrived, 1).get(0));
        assertNotSame(barrier, drawn.get(new Spot(10, 64, 10)), "not where they are standing");
        assertSame(barrier, drawn.get(new Spot(10, 63, 10)), "though the block below it still is");
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
     * The view reaches as far back as the configured depth, which is well past where it used to
     * stop.
     *
     * <p>The first builds drew sixteen blocks deep, and past that the world the viewer was really
     * in showed through the mirror.
     */
    @Test
    void theViewReachesFarBackBehindTheOpening()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 48);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        // Looking down through the opening from just above it, so that far back the line of
        // sight is well below the opening's own rows.
        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 45, 50)), "thirty-nine layers back");
        assertSame(farOneBlock, drawn.get(new Spot(10, 50, 40)), "twenty-nine layers back");
    }

    /**
     * A solid far side hides what is behind it, so what is behind it is never drawn.
     *
     * <p>A deep view is mostly ground: a mirror onto a field looks down into the soil under it.
     * Drawing the inside of the hill was most of the cost of a deep view and none of the picture.
     * The same two blocks are drawn when the far side is not solid, in the test before this one.
     */
    @Test
    void aSolidFarSideHidesWhatIsBehindIt()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 48);
        when(farOneBlock.isOccluding()).thenReturn(true);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 12)), "the first layer, in front");
        assertFalse(drawn.containsKey(new Spot(10, 50, 40)), "behind it, from where the eye is");
        assertFalse(drawn.containsKey(new Spot(10, 45, 50)), "further behind still");
    }

    /**
     * Far-side air over a block that is really empty is not sent: it would change nothing.
     *
     * <p>Sky is most of a deep view, and drawing air over air was most of what got sent. Over a
     * real block it is sent, in the test after this one, because there it opens up the view.
     */
    @Test
    void farSideAirOverAnEmptyBlockIsNotSent()
    {
        far = farWorld("far", air);
        localEmpty = true;
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Collection<BlockState> batch = changesTo(viewer, 1).get(0);
        assertEquals(barriers(), drawnAs(batch, barrier), "the opening still opens");
        assertEquals(0, drawnAs(batch, air), "and nothing at all is drawn as air");
    }

    /**
     * Sky on both sides is not even looked at, let alone sent.
     *
     * <p>Above the highest block in the real column and in the far one, everything is air over
     * air. Walking it a block at a time was nearly all of a redraw from right up against a mirror.
     */
    @Test
    void skyOnBothSidesIsNotEvenLookedAt()
    {
        far = farWorld("far", air);
        when(far.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(0);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(0);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        verify(far, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertEquals(barriers(), drawnAs(changesTo(viewer, 1).get(0), barrier),
            "the opening still opens");
    }

    @Test
    void farSideAirOverARealBlockIsSent()
    {
        far = farWorld("far", air);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertTrue(drawnAs(changesTo(viewer, 1).get(0), air) > 10,
            "air opening up the view through whatever really stands behind the wall");
    }

    /**
     * Something solid in front of part of the opening closes that part.
     *
     * <p>Nobody can see through it, and a neighbouring window may be using the space behind.
     */
    @Test
    void somethingSolidInFrontOfPartOfTheOpeningClosesThatPart()
    {
        doReturn(blockAt(10, 63, 10, false)).when(world).getBlockAt(10, 63, 10);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(barrier, drawn.get(new Spot(10, 64, 11)), "the row behind the banner opens");
        assertFalse(drawn.containsKey(new Spot(10, 63, 11)), "the row behind the block does not");
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
        assertSame(farOneBlock, drawn.get(new Spot(10, 63, 12)), "behind the first opening");
        assertSame(farTwoBlock, drawn.get(new Spot(12, 63, 12)), "behind the second");
    }

    /**
     * In open air a block reaching past the opening is left out, so nothing shows beside it.
     *
     * <p>A mirror on a tower with nothing around it showed its far side well past its edges: a
     * drawn block is a whole block, and only a wall hides the part of one that is not behind the
     * opening. The same block against a wall is drawn, in the test after this one.
     */
    @Test
    void inOpenAirABlockReachingPastTheOpeningIsNotDrawn()
    {
        wallBehind = false;
        standUp(banner);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 65, 20)), "far back, all of it behind");
        assertFalse(drawn.containsKey(new Spot(11, 65, 12)), "close, and half of it beside");
    }

    @Test
    void againstAWallTheSameBlockIsDrawnSinceTheWallHidesTheRest()
    {
        standUp(banner);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(new Spot(11, 65, 12)));
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
     * Stepping sideways redraws, and sends only what changed.
     *
     * <p>Resending all of it on every step would be the whole view several times a second. The
     * opening did not change, so it is not in the update.
     */
    @Test
    void steppingSidewaysSendsOnlyWhatChanged()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            pause();
            MirrorWindows.moved(viewer, new Location(world, 12.0, 64.0, 7.5));
        });

        final Collection<BlockState> update = changesTo(viewer, 2).get(1);
        assertFalse(update.isEmpty(), "the view moved with the viewer");
        assertEquals(0, drawnAs(update, barrier), "and the opening, which did not, was not resent");
    }

    /**
     * A viewer on the move is redrawn at most a few times a second.
     *
     * <p>Every half block was up to eleven redraws a second for somebody sprinting past a row of
     * mirrors, most of them made stale by the next step before anybody could see them.
     */
    @Test
    void aViewerOnTheMoveIsRedrawnAtMostAFewTimesASecond()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorWindows.moved(viewer, new Location(world, 12.0, 64.0, 7.5));
            verify(viewer, times(1)).sendBlockChanges(anyCollection());
            pause();
            MirrorWindows.moved(viewer, new Location(world, 12.5, 64.0, 7.5));
        });

        changesTo(viewer, 2);
    }

    /**
     * Crossing into another chunk sends the whole view again.
     *
     * <p>That is when a client is handed chunks it did not have, and a chunk arriving erases
     * whatever was drawn in it. Sending only differences there would leave holes.
     */
    @Test
    void crossingIntoAnotherChunkSendsTheWholeViewAgain()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            pause();
            MirrorWindows.moved(viewer, new Location(world, 16.2, 64.0, 7.5));
        });

        assertEquals(barriers(), drawnAs(changesTo(viewer, 2).get(1), barrier),
            "the opening and what stands in front of it, which did not change, are in it");
    }

    /**
     * A far chunk that is not loaded is asked for, not read, and loads a couple at a time.
     *
     * <p>Reading a block in an unloaded chunk loads it on the spot, on the main thread -- the
     * stall came the moment somebody walked up to a mirror onto somewhere nobody had been.
     */
    @Test
    void anUnloadedFarChunkIsAskedForRatherThanReadWhereTheViewerStands()
    {
        when(far.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            verify(far, never()).loadChunk(anyInt(), anyInt(), eq(true));
            MirrorProximity.tick();
        });

        verify(far, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(far, times(2)).loadChunk(anyInt(), anyInt(), eq(true));
        assertSame(barrier, positions(changesTo(viewer, 1).get(0)).get(new Spot(10, 64, 11)),
            "the opening still opens while the far side arrives");
    }

    /**
     * A far chunk somebody is looking at is held loaded, and let go when the window is.
     *
     * <p>Unheld, the server unloads it within moments and the next redraw has to load it again.
     */
    @Test
    void aFarChunkBeingLookedAtIsHeldAndLetGoWithTheWindow()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            verify(far, atLeastOnce()).addPluginChunkTicket(anyInt(), anyInt(), any());
            verify(far, never()).removePluginChunkTicket(anyInt(), anyInt(), any());
            MirrorProximity.release(MirrorManager.byName("museum"));
        });

        verify(far, atLeastOnce()).removePluginChunkTicket(anyInt(), anyInt(), any());
    }

    @Test
    void clickingTheOpeningWhileLookingInIsTheMirror()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(MirrorManager.byName("museum"),
            MirrorWindows.clicked(viewer, blockAt(10, 63, 11, true)), "the opening");
        assertSame(MirrorManager.byName("museum"),
            MirrorWindows.clicked(viewer, blockAt(10, 63, 10, true)), "the barrier in front of it");
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
     * A freestanding mirror opens too, behind it and upwards from where it stands.
     *
     * <p>A banner hung on a wall hangs down, and a standing one stands up, so the opening follows
     * the cloth. Snapped to the nearest cardinal, since a standing banner may face sixteen ways.
     */
    @Test
    void aFreestandingBannerOpensUpwardsFromWhereItStands()
    {
        standUp(banner);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(barrier, drawn.get(new Spot(10, 65, 11)), "a block above the banner's own row");
        assertNotSame(barrier, drawn.get(new Spot(10, 63, 11)),
            "not below it, where a wall banner's opening would be");
    }

    /**
     * A mirror onto a world that is not loaded stays a banner.
     *
     * <p>The same banner, pointed somewhere loaded, does open -- which is what shows the refusal
     * was about the world and not about something else in the setup.
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

        assertSame(air, positions(changesTo(viewer, 1).get(0)).get(new Spot(10, 64, 12)));
    }

    /**
     * A mirror written before windows existed opens as one, with nothing in its file changed.
     *
     * <p>That is the whole upgrade path: a window is a fact about the banner, not a setting, so
     * there is no migration to run and no file for a server to rewrite.
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

    /**
     * How many blocks one wall mirror draws as barrier: its two-block opening, and the two in
     * front of it -- less the banner's, where its patterns could not be sent back.
     */
    private static long barriers()
    {
        return MirrorPackets.available() ? 4 : 3;
    }

    /** Waits out the least time between two redraws of one viewer. */
    private static void pause()
    {
        try
        {
            Thread.sleep(MirrorWindows.REDRAW_MILLIS + 30L);
        }
        catch (final InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
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

    /** Where each state in a batch is, and what it was first drawn as (null if never). */
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

    /** A banner at z 10, in front of the layer the openings sit in. */
    private Block bannerAt(final int x)
    {
        final Block made = blockAt(x, 64, 10, true);
        when(made.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(made.getLocation()).thenReturn(new Location(world, x, 64.0, 10.0));
        when(made.getState()).thenAnswer(invocation -> stateAt(Banner.class, x, 64, 10));
        // doReturn, because when() would call the catch-all answer mid-stubbing.
        doReturn(made).when(world).getBlockAt(x, 64, 10);
        return made;
    }

    /** Hangs a banner on the wall to its south, facing north into the room. */
    private static void hangOnAWall(final Block block)
    {
        final Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);
        when(block.getBlockData()).thenReturn(data);
    }

    /** Stands a banner up instead, facing a little west of north. */
    private static void standUp(final Block block)
    {
        final Rotatable post = mock(Rotatable.class);
        when(post.getRotation()).thenReturn(BlockFace.NORTH_NORTH_WEST);
        when(block.getBlockData()).thenReturn(post);
    }

    /** A second wall banner, one block along, onto a different far side. */
    private void secondWindowAt(final int x)
    {
        hangOnAWall(bannerAt(x));
        MirrorManager.add(new QuantumMirror("archive", new MirrorBlock("world", x, 64, 10),
            new MirrorPoint("far2", 300.5, 70.0, -20.5, 0.0f, 0.0f)));
    }

    /** A block of the banner's world, with a fresh state per read as Bukkit gives. */
    private Block blockAt(final int x, final int y, final int z, final boolean passable)
    {
        final Block block = mock(Block.class);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getWorld()).thenReturn(world);
        when(block.isPassable()).thenReturn(passable);
        when(block.isEmpty()).thenReturn(localEmpty);
        final BlockData data = mock(BlockData.class);
        when(data.isOccluding()).thenReturn(wallBehind && (z == 11));
        when(block.getBlockData()).thenReturn(data);
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
        // Something in every column right up to the build limit, unless a test says otherwise:
        // a mock's zero would read as sky above y 0 on both sides, and nothing would be walked.
        when(mocked.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(319);
        return mocked;
    }

    /** A loaded far world made entirely of one block. */
    private static World farWorld(final String name, final BlockData everywhere)
    {
        final World made = named(mock(World.class), name);
        final Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(everywhere);
        when(made.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(made.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
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
            for (final Player player : world.getPlayers())
            {
                bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            }
            body.run();
        }
    }
}
