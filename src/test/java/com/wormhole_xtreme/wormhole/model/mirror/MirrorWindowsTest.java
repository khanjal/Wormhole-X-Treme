package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
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
 * other, the view has no far edge where the real world shows, and all of it stays cheap enough
 * for a server with people walking past mirrors: redraws are rationed and only differences are
 * sent.
 *
 * <p>The far side is a capture installed directly: the arrival point (100, 70, -21) in a world
 * of one block. The world here is a wall along z 11 -- the layer every opening in these tests
 * sits in -- with open air in front of it. {@link #wallBehind} takes the wall away.
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

    /** A block of the wall that is not there, or null for a whole wall. */
    private Spot gap;

    /** How far out from the opening the wall reaches, as a panel in the open, or null for a whole wall. */
    private Integer panel;

    private World world;
    private Block banner;
    private final BlockData air = named("minecraft:air");
    private final BlockData barrier = named("minecraft:barrier");
    private final BlockData farOneBlock = named("far:one");
    private final BlockData farTwoBlock = named("far:two");
    private final MirrorPoint arrival = new MirrorPoint("far", 100.5, 70.0, -20.5, 0.0f, 0.0f);
    private final MirrorPoint arrivalTwo = new MirrorPoint("far2", 300.5, 70.0, -20.5, 0.0f, 0.0f);

    /** How far {@link #pause()} has moved the redraw clock past the real one. */
    private static long paused;

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
        paused = 0L;
        MirrorWindows.clock = () -> System.currentTimeMillis() + paused;
        // A redraw over these mocks takes hundreds of milliseconds; resting three times that would
        // put every step that follows a pause() off until a catch-up that never comes.
        MirrorWindows.restFactor = 0L;
        // One batch a redraw, as before streaming, unless a test is about the stream: most tests
        // read a view off its first batch, and there is no scheduler here to send the rest.
        MirrorWindows.streamPerTick = Integer.MAX_VALUE;

        world = named(mock(World.class), "world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
            blockAt(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2), true));
        // Sky over sky above a column's top is skipped, and a bare mock's top is y 0: the
        // real side here reaches the top of the world, so nothing a test builds is skipped.
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(org.bukkit.HeightMap.class)))
            .thenReturn(Integer.MAX_VALUE);

        banner = bannerAt(10);
        hangOnAWall(banner);

        MirrorCaptures.install(arrival, solidCapture(arrival, farOneBlock));
        MirrorCaptures.install(arrivalTwo, solidCapture(arrivalTwo, farTwoBlock));

        // Nothing set on it: every mirror is a window.
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10), arrival));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        MirrorProximity.clear();
        MirrorFog.sendDistanceWith(null);
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
     * Two names on one banner draw one far side: the one a click would travel through.
     *
     * <p>{@code link} used to bind a second name to a banner that was already a mirror. Both
     * were windows on the same opening, each drawing its own capture, and a viewer saw the two
     * worlds mixed in one view.
     */
    @Test
    void twoNamesOnOneBannerDrawOnlyTheOneItIsIndexedUnder()
    {
        MirrorManager.add(new QuantumMirror("archive", new MirrorBlock("world", 10, 64, 10), arrivalTwo));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        final List<String> said = new ArrayList<>();
        withServer(() ->
        {
            MirrorProximity.tick();
            said.addAll(MirrorWindows.describe(viewer));
        });

        // Asked of the windows, not of the blocks drawn: two windows on one opening take turns per
        // block, and a single sweep can happen to draw all of one.
        assertTrue(said.stream().map(MirrorWindowsTest::plain).anyMatch("server: 1 window(s), 1 viewer(s)"::equals),
            "one banner is one window, not one per name: " + said);
        assertTrue(said.stream().map(MirrorWindowsTest::plain).anyMatch("looking into: archive"::equals),
            "the window is archive's, the mirror a click would take: " + said);
        assertTrue(drawnAs(changesTo(viewer, 1).get(0), farTwoBlock) > 0, "and archive's far side is what shows");
    }

    /**
     * A mirror whose banner was just written is sent whole again at the next sweep.
     *
     * <p>"I did mirror stamp and the banner always shows, but behind it I can see the mirrored
     * environment -- it took a while to go away." Stamping sends every client the real banner, over
     * the view that draws it away; the view thought it had sent air there already, and waited half a
     * minute for its next whole resend.
     */
    @Test
    void aStampedBannerIsDrawnAwayAgainAtTheNextSweep()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorWindows.resendFor("museum");
            MirrorProximity.tick();
        });

        final List<Collection<BlockState>> sent = changesTo(viewer, 2);
        assertEquals(sent.get(0).size(), sent.get(1).size(), "the whole view again, banner and all");
    }

    /**
     * The clicked block and the one on its face are sent again straight after the server's own
     * correction, off the main thread, so the real banner does not show for a tick.
     *
     * <p>"We still have a very quick flicker of a banner when right clicking and switching mirrors."
     * The client, seeing the banner drawn as air, clicks the barrier in the opening behind it; the
     * server corrects that block and the one on its near face, the banner's, in the same tick, and
     * the whole view sent a tick later left the banner showing for that tick. On plain 1.20 the
     * banner is never drawn as air, since its patterns could not be sent back, so only the
     * barrier is sent again there: the one job in the matrix that failed this test before it said so.
     */
    @Test
    void aClickSendsTheTwoBlocksTheServerCorrectsAgainWithinTheTick() throws Exception
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            withServer(() ->
            {
                MirrorProximity.tick();
                final Block opening = world.getBlockAt(10, 64, 11);
                final Block bannerBlock = world.getBlockAt(10, 64, 10);
                when(opening.getRelative(BlockFace.NORTH)).thenReturn(bannerBlock);
                MirrorWindows.resend(viewer, opening, BlockFace.NORTH);
                final org.mockito.ArgumentCaptor<Runnable> soon = org.mockito.ArgumentCaptor.forClass(Runnable.class);
                verify(scheduler).runTaskAsynchronously(org.mockito.ArgumentMatchers.any(org.bukkit.plugin.Plugin.class),
                    soon.capture());
                verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));
                soon.getValue().run();
            });
            verify(viewer).sendBlockChange(new Location(world, 10, 64, 11), barrier);
            if (MirrorPackets.available())
            {
                verify(viewer).sendBlockChange(new Location(world, 10, 64, 10), air);
            }
            else
            {
                // Plain 1.20 cannot send a banner's patterns back, so the view never draws the
                // banner as air there, and there is nothing of it to send again.
                verify(viewer, never()).sendBlockChange(new Location(world, 10, 64, 10), air);
            }
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }
    }

    /**
     * A click on a mirror sends the whole view again a tick later.
     *
     * <p>"When right clicking, the real banner and block shows." The server answers a refused click
     * by sending the clicked block and the one beside it as they really are, once the click is
     * handled; the view was only marked to be sent again, so the real banner and wall stayed until
     * the player moved or the sweep came round.
     */
    @Test
    void aClickOnAMirrorSendsTheWholeViewAgainATickLater() throws Exception
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            withServer(() ->
            {
                MirrorProximity.tick();
                MirrorWindows.resend(viewer);
                final org.mockito.ArgumentCaptor<Runnable> later = org.mockito.ArgumentCaptor.forClass(Runnable.class);
                verify(scheduler, org.mockito.Mockito.atLeastOnce()).scheduleSyncDelayedTask(
                    org.mockito.ArgumentMatchers.any(org.bukkit.plugin.Plugin.class), later.capture(),
                    org.mockito.ArgumentMatchers.eq(1L));
                verify(viewer, times(1)).sendBlockChanges(anyCollection());
                later.getAllValues().forEach(Runnable::run);
            });
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }

        // Everything the view held, not a list of blocks: on plain 1.20 the banner is never drawn
        // away, so naming it failed there while every later version passed.
        final List<Collection<BlockState>> sent = changesTo(viewer, 2);
        final Map<Spot, BlockData> first = positions(sent.get(0));
        final Map<Spot, BlockData> again = positions(sent.get(1));
        assertTrue(again.keySet().containsAll(first.keySet()), "the whole view, sent again: " + again.keySet());
        assertTrue(again.containsKey(new Spot(10, 64, 11)), "the wall the banner hangs on, drawn as the opening again");
    }

    @Test
    void aRightClickShowsTheChosenMirrorWithoutWaitingForTheSweep()
    {
        secondWindowAt(20);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        try
        {
            withServer(() ->
            {
                MirrorProximity.tick();
                final QuantumMirror museum = MirrorManager.byName("museum");
                MirrorNetwork.scroll(museum, false);
                MirrorWindows.redraw(museum, banner);
            });

            final List<Collection<BlockState>> sent = changesTo(viewer, 2);
            assertTrue(drawnAs(sent.get(0), farOneBlock) > 0, "museum's own far side, from the sweep");
            assertTrue(drawnAs(sent.get(1), farTwoBlock) > 0, "then archive's, straight after the click");
        }
        finally
        {
            MirrorNetwork.clear();
        }
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
     * A mirror set in a wall draws its whole far side once, the same wherever the viewer stands.
     *
     * <p>Trimmed to each eye, the view changed with every step and reached less far from close
     * up, and blocks appeared and vanished as a viewer walked. The wall hides whatever lies beside
     * the opening, so there is nothing to trim: moving about in front of it sends nothing more.
     */
    @Test
    void aMirrorInAWallDrawsItsWholeFarSideOnceWhereverTheViewerStands()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            pause();
            MirrorWindows.moved(viewer, new Location(world, 13.0, 64.0, 9.5));
            pause();
            MirrorWindows.moved(viewer, new Location(world, 10.5, 64.0, 10.4));
            MirrorProximity.tick();
        });

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(18, 64, 12)),
            "off to the side, where no line of sight from the first eye goes");
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 26)), "fifteen blocks in, however close the eye comes");
    }

    /**
     * In front of a mirror in a wall, a move that leaves the eye where it was does not look for
     * windows again.
     *
     * <p>A player turning their head moves without going anywhere, many times a second. The view
     * of a walled mirror is the same from every eye, so a move is judged unchanged without being
     * redrawn, and the eye it was judged at is kept so the next move to the same place is turned
     * away before any of that: looking for the windows a player sees casts a line to every opening.
     */
    @Test
    void inFrontOfAWalledMirrorTurningOnTheSpotDoesNotLookForWindowsAgain()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final long[] looks = new long[3];

        withServer(() ->
        {
            MirrorProximity.tick();
            pause();
            MirrorWindows.moved(viewer, new Location(world, 13.0, 64.0, 9.5));
            looks[0] = looksForWindows(viewer);
            pause();
            MirrorWindows.moved(viewer, new Location(world, 13.0, 64.0, 9.5));
            looks[1] = looksForWindows(viewer);
            pause();
            MirrorWindows.moved(viewer, new Location(world, 10.5, 64.0, 10.4));
            looks[2] = looksForWindows(viewer);
        });

        assertEquals(looks[0], looks[1], "the same place again: not looked for");
        assertTrue(looks[2] > looks[1], "somewhere new is: " + looks[1] + " then " + looks[2]);
    }

    /** How many times the windows a viewer sees have been looked for, which starts from where they stand. */
    private static long looksForWindows(final Player viewer)
    {
        return mockingDetails(viewer).getInvocations().stream()
            .filter(call -> "getLocation".equals(call.getMethod().getName())).count();
    }

    /**
     * A far side turned round is drawn with its blocks turned too, each state turned once.
     *
     * <p>"The glass panes aren't connecting." A pane's connections are compass directions; the
     * far side's positions were turned to face the viewer, and the panes at them were not.
     */
    @Test
    void aFarSideTurnedRoundIsDrawnWithItsBlocksTurnedToo()
    {
        final MirrorPoint northward = new MirrorPoint("far", 100.5, 70.0, -20.5, 180.0f, 0.0f);
        final BlockData pane = named("far:pane");
        final BlockData turnedPane = named("far:pane-turned");
        when(pane.clone()).thenReturn(turnedPane);
        MirrorCaptures.install(northward, groundBelow(northward, 1000, pane));
        MirrorManager.clear();
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10), northward));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(turnedPane, positions(changesTo(viewer, 1).get(0)).get(new Spot(10, 64, 13)),
            "drawn as the turned copy");
        verify(turnedPane).rotate(StructureRotation.CLOCKWISE_180);
        verify(pane, times(1)).clone();
    }

    /**
     * A block at the edge of a trimmed view, once drawn, stays drawn while all but a little of it
     * is behind the opening.
     *
     * <p>"The bricks behind the fence flicker when approaching or backing away from the portal."
     * Drawn when all but a twentieth of it was behind the opening and left alone otherwise, a
     * block at the edge flipped with every step. This finds a block the first eye drew that the
     * second sees between a twentieth and {@link #KEPT_BESIDE} beside the opening, and checks it
     * was not taken back.
     */
    @Test
    void aBlockAtTheEdgeOnceDrawnStaysDrawnWhileAlmostAllOfItIsBehindTheOpening()
    {
        final Step step = stepPutting(0.05, KEPT_BESIDE);

        assertFalse(step.update().containsKey(step.block()) && (step.update().get(step.block()) == null),
            step.block() + " was taken back after a step to " + step.x() + "," + step.z());
    }

    /**
     * A block at the edge of a trimmed view is taken back once more of it than that is beside the
     * opening.
     *
     * <p>"Sometimes I can see the overflow on the sides." Kept while half of it was behind the
     * opening, a block at the edge showed the far side up to half a block past it. This finds a
     * block the first eye drew that the second sees between a fifth and a half beside the
     * opening, and checks it was taken back.
     */
    @Test
    void aBlockAtTheEdgeIsTakenBackOnceAFifthOfItIsBesideTheOpening()
    {
        final Step step = stepPutting(0.2, 0.5);

        assertTrue(step.update().containsKey(step.block()) && (step.update().get(step.block()) == null),
            step.block() + " should have been taken back after a step to " + step.x() + "," + step.z()
                + ": that much of it shows the far side past the edge");
    }

    /** How much of a drawn block the view keeps beside the opening, as {@code MirrorWindows} has it. */
    private static final double KEPT_BESIDE = 0.15;

    /** A block a first view drew, the step after it, and what that step sent. */
    private record Step(Spot block, double x, double z, Map<Spot, BlockData> update) {}

    /**
     * Draws a mirror's view on a wall of nothing but its opening, then steps to where some block
     * it drew lies partly beside the opening -- more than {@code least} of it and no more than
     * {@code most} -- failing, rather than passing, if no such step turns up.
     */
    private Step stepPutting(final double least, final double most)
    {
        wallBehind = false;
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final MirrorWindow shape = MirrorWindow.of(new MirrorBlock("world", 10, 64, 10), BlockFace.NORTH, arrival);
        final MirrorWindow.Face open = (across, y) -> (across == 10) && ((y == 63) || (y == 64));
        final Spot[] edge = { null };
        final double[] second = new double[3];

        withServer(() ->
        {
            MirrorProximity.tick();
            final Map<Spot, BlockData> first = positions(changesTo(viewer, 1).get(0));
            // To one side of the opening's column, nearer or both, in tenths of a block. From inside
            // the column a block behind the opening projects inside it however near the eye comes.
            search:
            for (double x = 8.5; x <= 12.55; x += 0.1)
            {
                for (double z = 7.5; z <= 10.35; z += 0.1)
                {
                    for (final Map.Entry<Spot, BlockData> entry : first.entrySet())
                    {
                        final Spot spot = entry.getKey();
                        if (entry.getValue() != farOneBlock)
                        {
                            continue;
                        }
                        final double[] rect = shape.projected(x, 65.62, z, spot.x(), spot.y(), spot.z());
                        if ((rect != MirrorWindow.UNSEEN) && !shape.covered(rect, open, least) && shape.covered(rect, open, most))
                        {
                            edge[0] = spot;
                            second[0] = x;
                            second[2] = z;
                            break search;
                        }
                    }
                }
            }
            assertNotNull(edge[0], "a step that puts a drawn block partly beside the opening");
            pause();
            MirrorWindows.moved(viewer, new Location(world, second[0], 64.0, second[2]));
        });

        return new Step(edge[0], second[0], second[2], positions(changesTo(viewer, 2).get(1)));
    }

    /**
     * A block landing on the frame round the opening is drawn already, hidden behind the frame.
     *
     * <p>"When I face the frame brick and slide into the mirror view I see it render. It should
     * already be mostly there since I'm right up against the frame." From beside the opening, a
     * block straight behind it lands on the frame brick; it was drawn only once a step brought it
     * into the opening. The frame hides it, so it is drawn beforehand. A gap in the wall, so the
     * room is clipped to the eye rather than drawn whole.
     */
    @Test
    void aBlockLandingOnTheFrameRoundTheOpeningIsDrawnAlreadyHiddenBehindTheFrame()
    {
        gap = new Spot(16, 64, 11);
        final Player viewer = playerAt(11.5, 10.7);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(new Spot(10, 65, 15)),
            "straight behind the opening, which from beside it lands on the frame brick");
    }

    /**
     * A block kept from the last drawing is still taken back once it would show beside the
     * opening from where the viewer now stands.
     *
     * <p>A block already drawn is held while a little of it is beside the opening; it must not be
     * held once most of it is. Stepped well to the side of a mirror on a wall of nothing but its
     * opening, a block straight behind the opening lands beside it.
     */
    @Test
    void aBlockKeptFromTheLastDrawingIsTakenBackOnceItWouldShowBesideTheOpening()
    {
        wallBehind = false;
        // A block lower: a wall banner's opening hangs down, and an eye at 65.6 looks over it.
        final Player viewer = playerAt(10.5, 7.5);
        standLow(viewer, 10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final Spot straight = new Spot(10, 64, 13);

        withServer(() ->
        {
            MirrorProximity.tick();
            assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(straight), "drawn straight ahead");
            pause();
            MirrorWindows.moved(viewer, new Location(world, 13.5, 63.0, 9.5));
        });

        final Map<Spot, BlockData> update = positions(changesTo(viewer, 2).get(1));
        assertTrue(update.containsKey(straight) && (update.get(straight) == null),
            "taken back: from here it lands beside the opening, in the open air");
    }

    /**
     * With views off, a viewer is drawn nothing, and what they had is taken back.
     *
     * <p>For an admin who wants the world as it is: "an admin command to remove the view".
     */
    @Test
    void withViewsOffAViewerIsDrawnNothingAndWhatTheyHadIsTakenBack()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorWindows.blind(viewer, true);
            MirrorProximity.tick();
            MirrorProximity.tick();
        });

        assertTakenBack(changesTo(viewer, 2));
    }

    /**
     * Drawn full for one viewer, a mirror shows everything its capture holds, whatever the rules.
     *
     * <p>"An admin command that forces the mirror world chunk to fully render without limits so
     * I can check what it's stored and how it's rendering." A mirror clipped to the eye by a gap
     * in its wall, so nothing would be drawn off to the side or past the depth otherwise; and the
     * depth is 16, so a block at 30 would not be drawn for anyone else.
     */
    @Test
    void drawnFullForOneViewerAMirrorShowsEverythingItsCaptureHolds()
    {
        gap = new Spot(16, 64, 11);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorWindows.full(viewer, "museum");
            MirrorProximity.tick();
        });

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(18, 64, 12)), "off to the side, past the edge");
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 41)), "thirty blocks in, past the depth");
        assertFalse(drawn.containsKey(new Spot(10, 64, 60)), "but nothing the capture does not hold");
    }

    /**
     * A mirror drawn full for a viewer stays drawn wherever they stand, out of range and behind it.
     *
     * <p>"Keep it on even if I'm not looking at a mirror, so I can look around better at what it's
     * stored." Forty blocks off, on the far side of the wall, the view is still theirs.
     */
    @Test
    void aMirrorDrawnFullStaysDrawnWhereverTheViewerStands()
    {
        final Player viewer = playerAt(10.5, 40.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorWindows.full(viewer, "museum");
            MirrorProximity.tick();
        });

        assertTrue(drawnAs(changesTo(viewer, 1).get(0), farOneBlock) > 0, "the far side, from behind and far off");
    }

    /**
     * A fixed view nobody has looked through for a minute is let go.
     *
     * <p>Every mirror in a loaded chunk is a window each sweep, looked at or not, and a fixed
     * view is up to a quarter of a million blocks. Carried for the life of the chunk, a server of
     * a few hundred mirrors each looked at once would hold all of them.
     */
    @Test
    void aFixedViewNobodyHasLookedThroughForAMinuteIsLetGo()
    {
        final long[] time = { 5_000_000L };
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorWindows.clock = () -> time[0];
            MirrorProximity.tick();
            assertTrue(MirrorWindows.holdsFixedView("museum"), "held while looked through");
            stand(viewer, 10.5, -40.0);
            time[0] += 30_000L;
            MirrorProximity.tick();
            assertTrue(MirrorWindows.holdsFixedView("museum"), "and for a while after");
            time[0] += 31_000L;
            MirrorProximity.tick();
        });

        assertFalse(MirrorWindows.holdsFixedView("museum"), "let go a minute after the last look");
    }

    /**
     * Past the server's share of work for a second, a viewer keeps what they see until the next.
     *
     * <p>One redraw's budget bounds one viewer, not a server: a hundred people walking past
     * mirrors at once was a hundred budgets every quarter second.
     */
    @Test
    void pastTheServersShareOfWorkForASecondTheNextViewerWaits()
    {
        final Player first = playerAt(10.5, 7.5);
        final Player second = playerAt(10.5, 6.5);
        when(world.getPlayers()).thenReturn(List.of(first, second));

        withServer(() ->
        {
            MirrorWindows.workPerSecond = 1;
            MirrorProximity.tick();
        });

        final long sent = mockingDetails(first).getInvocations().stream()
            .filter(call -> "sendBlockChanges".equals(call.getMethod().getName())).count()
            + mockingDetails(second).getInvocations().stream()
                .filter(call -> "sendBlockChanges".equals(call.getMethod().getName())).count();
        assertEquals(1, sent, "the first viewer drawn, the second kept waiting");
    }

    /**
     * A viewer who moves while the server's share of work is spent is drawn from there once there
     * is room, without moving again.
     *
     * <p>The move is kept and a catch-up booked. Without it, a viewer who stepped in front of a
     * mirror during a busy second and then stood still kept the view from where they had been
     * until the next sweep, or the next time they moved. A gap in the wall, so the view depends on
     * the eye.
     */
    @Test
    void aMoveWhileTheShareIsSpentIsDrawnOnceThereIsRoom() throws Exception
    {
        gap = new Spot(16, 64, 11);
        final long[] clock = { 1_000_000L };
        MirrorWindows.clock = () -> clock[0];
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            final String[] from = new String[3];
            withServer(() ->
            {
                MirrorProximity.tick();
                from[0] = drawnFrom(viewer);
                MirrorWindows.workPerSecond = 1;
                clock[0] += 200L;
                MirrorWindows.moved(viewer, new Location(world, 13.5, 64.0, 7.5));
                from[1] = drawnFrom(viewer);
                MirrorWindows.workPerSecond = Integer.MAX_VALUE;
                runBooked(scheduler);
                from[2] = drawnFrom(viewer);
            });

            assertEquals("10,65,7", from[0]);
            assertEquals(from[0], from[1], "no work to spare: kept as drawn");
            assertEquals("13,65,7", from[2], "then drawn from where they moved to");
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }
    }

    /** Where a viewer's last redraw was drawn from, off the debug line. */
    private static String drawnFrom(final Player viewer)
    {
        return MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain)
            .filter(line -> line.startsWith("drawn from: ")).findFirst().orElseThrow()
            .substring("drawn from: ".length());
    }

    /**
     * A mirror on a wall with a gap in the wall within the proximity distance is trimmed, like a
     * freestanding one.
     *
     * <p>Through the gap, the far side drawn whole would show beside the mirror. One ring of
     * solid wall round the opening was the first rule, and a mirror in a stone arch two blocks
     * wide on a beach passed it and drew the library across the sand.
     */
    @Test
    void aWallMirrorWithAGapWithinTheProximityRadiusIsTrimmedLikeAFreestandingOne()
    {
        gap = new Spot(16, 64, 11);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 13)), "straight through the middle");
        assertFalse(drawn.containsKey(new Spot(18, 64, 12)), "off to the side, not drawn");
    }

    /**
     * A gap in the wall twelve blocks out trims a mirror while the proximity distance is sixteen.
     *
     * <p>"Sometimes I can see the overflow on the sides." The wall was read eight blocks out, the
     * radius when that was chosen, and stayed eight when the radius doubled: a viewer twelve blocks
     * to one side looked round the end of eight blocks of wall into the far side drawn behind it.
     */
    @Test
    void aGapInTheWallWithinASixteenBlockRadiusButPastEightTrimsTheMirror()
    {
        gap = new Spot(22, 64, 11);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 13)), "straight through the middle");
        assertFalse(drawn.containsKey(new Spot(18, 64, 12)),
            "off to the side: a viewer within the radius can see the far side through that gap");
    }

    /** The same gap is past the wall a mirror needs while the radius is eight, and it is drawn whole. */
    @Test
    void theSameGapPastAnEightBlockRadiusLeavesTheMirrorDrawnWhole()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 8);
        gap = new Spot(22, 64, 11);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(new Spot(18, 64, 12)),
            "off to the side, drawn: nobody near enough to see the gap");
    }

    /**
     * A clipped room's far part stands between small steps, and behind a wall two blocks wide it
     * stands for a whole-block move and follows a move of a block and a half.
     *
     * <p>"It was real laggy." Through a one-block opening a tenth-of-a-block step swings the far
     * end of a view a dozen blocks sideways, so thousands of far blocks changed ten times a second.
     * With the far part starting eight blocks from the eye, a step inside the same cell projects
     * only what is near, and a step into the next cell projects the far part too. The cell is
     * half a block less than the wall is wide: the gap here is three blocks from the opening, so
     * the wall round it is two wide and the cell a block and a half, and a whole-block move that
     * once judged the far part again no longer does.
     */
    @Test
    void behindATwoBlockWallTheFarPartStandsForAWholeBlockMoveAndFollowsABlockAndAHalf()
    {
        MirrorWindows.nearDistance = 8.0;
        gap = new Spot(13, 64, 11);
        // A clock of our own: a redraw over these mocks takes longer than the second the far
        // part stands, so real time would judge it again on every step.
        final long[] clock = { 1_000_000L };
        MirrorWindows.clock = () -> clock[0];
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final int[] projected = new int[3];

        withServer(() ->
        {
            MirrorProximity.tick();
            clock[0] += 200L;
            MirrorWindows.moved(viewer, new Location(world, 10.8, 64.0, 7.5));
            projected[0] = projectedByTheLastRedraw(viewer);
            clock[0] += 200L;
            MirrorWindows.moved(viewer, new Location(world, 11.5, 64.0, 7.5));
            projected[1] = projectedByTheLastRedraw(viewer);
            clock[0] += 200L;
            MirrorWindows.moved(viewer, new Location(world, 12.0, 64.0, 7.5));
            projected[2] = projectedByTheLastRedraw(viewer);
        });

        assertEquals(1.5, MirrorWindows.farCellFor(2), "two blocks of wall: a cell of a block and a half");
        assertTrue(projected[0] > 0, "a step inside the cell projects the near layers");
        assertTrue(projected[1] < (2 * projected[0]),
            "a whole-block move inside the cell projects the near layers alone: " + projected[0] + " then "
                + projected[1]);
        assertTrue(projected[2] > (2 * projected[0]),
            "a step into the next cell projects the far layers too: " + projected[0] + " then " + projected[2]);
    }

    /**
     * A far part that stands is still drawn: the step that reuses it takes none of it back.
     *
     * <p>What stands is the far blocks kept when it was last judged, and a step inside the cell
     * draws them again without projecting them. Kept wrong -- the near blocks in their place --
     * the far part would vanish from the view on every step that does not judge it, and come back
     * on the next that does.
     */
    @Test
    void aFarPartThatStandsIsStillDrawnOnTheStepThatReusesIt()
    {
        MirrorWindows.nearDistance = 8.0;
        gap = new Spot(13, 64, 11);
        final long[] clock = { 1_000_000L };
        MirrorWindows.clock = () -> clock[0];
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final int[] projected = new int[2];
        final int[] drawn = new int[2];

        withServer(() ->
        {
            // The first drawing judges the far part; a step inside the cell reuses it.
            MirrorProximity.tick();
            projected[0] = projectedByTheLastRedraw(viewer);
            drawn[0] = drawnCount(viewer);
            clock[0] += 200L;
            MirrorWindows.moved(viewer, new Location(world, 10.8, 64.0, 7.5));
            projected[1] = projectedByTheLastRedraw(viewer);
            drawn[1] = drawnCount(viewer);
        });

        assertTrue(projected[0] > (2 * projected[1]),
            "the step reuses the far part rather than judging it again: " + projected[0] + " then " + projected[1]);
        assertTrue(drawn[0] > 1000, "the first drawing reaches the far part: " + drawn[0]);
        assertTrue(drawn[1] > ((drawn[0] * 9) / 10),
            "and the step still draws it: " + drawn[0] + " then " + drawn[1]);
    }

    /**
     * Behind a wide wall the far part stands across several blocks of movement, and is judged for
     * every eye in the cell at once.
     *
     * <p>Through a one-by-two opening a step at the opening swings the far end of the view by the
     * room's depth, so the far part must be sent again for each cell the eye walks through; what a
     * wall's width buys is how far a cell is. Judged from the eye alone, a cell that wide would
     * leave holes and leaks for every eye but the one it was judged for, so the far part is judged
     * for the whole cell: a block seen through the opening from anywhere in it is drawn, and lands
     * where the wall hides it from everywhere in it. The gap here is six blocks out, so the wall is
     * five wide and the cell four: three whole-block moves project the near layers alone, and the
     * fourth, into the next cell, projects the far part. The same blocks are sent per block walked,
     * in a quarter of the batches, and the client re-meshes each far chunk section a quarter as often.
     */
    @Test
    void behindAWideWallTheFarPartStandsAcrossFourBlocksOfMovement()
    {
        MirrorWindows.nearDistance = 8.0;
        gap = new Spot(16, 64, 11);
        final long[] clock = { 1_000_000L };
        MirrorWindows.clock = () -> clock[0];
        // Cells are laid on the world's grid: from 8.5, the cell of four runs to 12.
        final Player viewer = playerAt(8.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final int[] projected = new int[4];
        final List<String> said = new ArrayList<>();

        withServer(() ->
        {
            MirrorProximity.tick();
            for (int step = 0; step < 4; step++)
            {
                clock[0] += 200L;
                MirrorWindows.moved(viewer, new Location(world, 9.5 + step, 64.0, 7.5));
                projected[step] = projectedByTheLastRedraw(viewer);
            }
            said.addAll(MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList());
        });

        assertEquals(4.0, MirrorWindows.farCellFor(5), "five blocks of wall: a cell of four");
        assertEquals(4.0, MirrorWindows.farCellFor(16), "a wall solid as far as it is read: still four");
        assertTrue(projected[0] > 0, "a step inside the cell projects the near layers");
        for (int step = 1; step < 3; step++)
        {
            assertTrue(projected[step] < (2 * projected[0]), "whole-block move " + (step + 1)
                + " inside the cell projects the near layers alone: " + projected[0] + " then " + projected[step]);
        }
        assertTrue(projected[3] > (2 * projected[0]),
            "the move into the next cell projects the far layers too: " + projected[0] + " then " + projected[3]);
        assertTrue(said.stream().anyMatch(line -> line.startsWith(
            "museum far part: judged once per 4.0 blocks of movement, wall 5 on every side, ")
            && line.endsWith(" blocks kept")), "debug says how far the far part stands: " + said);
    }

    /**
     * Behind a wall only a block wide, the far part follows a half-block move.
     *
     * <p>"This problem is mostly because of the 1 block border mirrors." Only the inner half of a
     * wall block with open air past it counts as hiding anything, so a one-block wall absorbs
     * half a block of movement before a stale far block's landing slips past its edge; the far
     * part standing for a whole-block move let half a block of stale room show. The gap here is
     * two blocks from the opening, so the wall round it is one block wide. A step within the same
     * half block projects the near part alone; a step into the other half of the same block, not
     * a new block, projects the far part too.
     */
    @Test
    void behindAOneBlockWallTheFarPartFollowsAHalfBlockMove()
    {
        MirrorWindows.nearDistance = 8.0;
        gap = new Spot(12, 64, 11);
        final long[] clock = { 1_000_000L };
        MirrorWindows.clock = () -> clock[0];
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final int[] projected = new int[2];

        withServer(() ->
        {
            MirrorProximity.tick();
            clock[0] += 200L;
            MirrorWindows.moved(viewer, new Location(world, 10.8, 64.0, 7.5));
            projected[0] = projectedByTheLastRedraw(viewer);
            clock[0] += 200L;
            MirrorWindows.moved(viewer, new Location(world, 10.4, 64.0, 7.5));
            projected[1] = projectedByTheLastRedraw(viewer);
        });

        assertEquals(0.5, MirrorWindows.farCellFor(1), "a one-block wall: half a block");
        assertEquals(0.5, MirrorWindows.farCellFor(0), "no wall at all: no less than half a block");
        assertTrue(projected[0] > 0, "a step within the half block projects the near layers");
        assertTrue(projected[1] > (2 * projected[0]),
            "a step into the other half of the same block projects the far layers too: " + projected[0] + " then "
                + projected[1]);
    }

    /**
     * A redraw that took long earns a rest three times as long before the next; a quick one none.
     *
     * <p>Right against a deep mirror a redraw projected the whole half-sphere in sixty-five
     * milliseconds, ten times a second: two thirds of the main thread for one viewer.
     */
    @Test
    void aSlowRedrawEarnsARestThreeTimesAsLong()
    {
        MirrorWindows.restFactor = 3L;
        assertEquals(MirrorWindows.REDRAW_MILLIS, MirrorWindows.restAfter(0L), "a quick redraw keeps the pace");
        assertEquals(MirrorWindows.REDRAW_MILLIS, MirrorWindows.restAfter(30L), "so does one under a third of it");
        assertEquals(195L, MirrorWindows.restAfter(65L), "sixty-five milliseconds rests nearly two hundred");
    }

    /** How many blocks the viewer's last redraw projected, off the debug line. */
    private static int projectedByTheLastRedraw(final Player viewer)
    {
        final String line = MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain)
            .filter(said -> said.startsWith("last redraw: ")).findFirst().orElseThrow();
        return Integer.parseInt(line.replaceAll("last redraw: (\\d+) blocks projected.*", "$1"));
    }

    /**
     * A room bigger than a tick's worth is streamed in a tick at a time, nearest the eye first.
     *
     * <p>"It's rendering lag when you look at or move in/out of view: it remains and then takes a
     * moment to generate." A room at the render distance is some eighty thousand blocks, and sent
     * as one batch the client re-meshed every chunk section it touched before it drew another
     * frame. Rooms over 20,000 blocks were clipped to each eye instead, which cost every step;
     * now a batch is a tick's worth, the rest follows a tick apart, and the cap is gone. The near
     * end of the room is what the viewer is looking at, so it goes first: here the first batch is
     * all within sixteen blocks of the eye and the last is all beyond.
     */
    @Test
    void aRoomBiggerThanATickIsStreamedInATickAtATimeNearestFirst() throws Exception
    {
        MirrorWindows.streamPerTick = 500;
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            final int[] ticks = new int[1];
            final List<String> said = new ArrayList<>();
            withServer(() ->
            {
                MirrorProximity.tick();
                said.addAll(MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList());
                ticks[0] = runBooked(scheduler);
            });

            assertTrue(said.stream().anyMatch(line -> line.startsWith("still to send: ")),
                "debug says what is owed after the first batch: " + said);
            final List<Collection<BlockState>> sent = changesTo(viewer, ticks[0] + 1);
            assertTrue(sent.size() > 3, "a room of thousands goes in many batches of 500: " + sent.size());
            for (final Collection<BlockState> batch : sent)
            {
                assertTrue(batch.size() <= 500, "no batch over a tick's worth: " + batch.size());
            }
            assertTrue(farthestFromTheEye(sent.get(0)) < 16.0,
                "the first batch is the near end of the room: " + farthestFromTheEye(sent.get(0)));
            assertTrue(nearestToTheEye(sent.get(sent.size() - 1)) >= 16.0,
                "the last batch is the far end: " + nearestToTheEye(sent.get(sent.size() - 1)));
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }
    }

    /**
     * A room is taken back a tick at a time as the viewer leaves, and the view ends when it is done.
     *
     * <p>Taking eighty thousand blocks back in one batch was the same freeze as sending them, on
     * the way out. The real blocks go back at the same pace, and the view is kept until the last
     * of them has gone rather than dropped with blocks still owed.
     */
    @Test
    void aRoomIsTakenBackATickAtATimeAsTheViewerLeaves() throws Exception
    {
        MirrorWindows.streamPerTick = 500;
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            final int[] ticks = new int[2];
            final List<String> midway = new ArrayList<>();
            final List<String> after = new ArrayList<>();
            withServer(() ->
            {
                MirrorProximity.tick();
                ticks[0] = runBooked(scheduler);
                pause();
                MirrorWindows.moved(viewer, new Location(world, 10.5, 64.0, 60.0));
                midway.addAll(MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList());
                ticks[1] = runBooked(scheduler) - ticks[0];
                after.addAll(MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList());
            });

            assertTrue(midway.stream().anyMatch(line -> line.startsWith("still to send: ")),
                "the view stands while the room is owed back: " + midway);
            assertTrue(ticks[1] > 3, "a room of thousands goes back in many batches of 500: " + ticks[1]);
            final List<Collection<BlockState>> sent = changesTo(viewer, ticks[0] + ticks[1] + 2);
            for (final Collection<BlockState> batch : sent.subList(ticks[0] + 1, sent.size()))
            {
                assertTrue(batch.size() <= 500, "no batch over a tick's worth on the way out: " + batch.size());
                assertTrue(positions(batch).values().stream().allMatch(java.util.Objects::isNull),
                    "the way out is the real world, block for block");
            }
            assertTrue(after.contains("looking into: no window"), "and the view ends with the last batch: " + after);
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }
    }

    /**
     * Crossing into a new chunk sends again only what was drawn in the chunks the client is newly handed.
     *
     * <p>A chunk arrives as the world has it, over whatever was drawn there, so a crossing used to
     * send the whole view again. With whole rooms of eighty thousand blocks that was the whole room
     * every sixteen blocks of walking along the wall. The client is handed only the chunks that
     * come within its reach -- the shorter of the server's distance and its own, a chunk over for
     * the edge -- so only what was drawn in those goes again. Here the reach is one chunk, the
     * viewer walks one chunk to the right, and the column of chunks that comes into reach holds
     * the room's blocks at x 16 and beyond; those are sent again, and nothing nearer is.
     */
    @Test
    void aChunkCrossingSendsAgainOnlyTheChunksNewlyInReach()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 40);
        when(world.getViewDistance()).thenReturn(1);
        final Player viewer = playerAt(-21.5, 7.5);
        when(viewer.getClientViewDistance()).thenReturn(1);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            pause();
            MirrorWindows.moved(viewer, new Location(world, -5.5, 64.0, 7.5));
        });

        final List<Collection<BlockState>> sent = changesTo(viewer, 2);
        final Map<Spot, BlockData> first = positions(sent.get(0));
        final Map<Spot, BlockData> again = positions(sent.get(1));
        assertTrue(first.keySet().stream().anyMatch(spot -> spot.x() < 16), "the whole room went at first");
        assertTrue(again.keySet().stream().anyMatch(spot -> spot.x() >= 16),
            "what was drawn in the chunks newly in reach goes again");
        assertTrue(again.keySet().stream().allMatch(spot -> spot.x() >= 16),
            "and nothing in the chunks the client already had: " + again.keySet().stream()
                .filter(spot -> spot.x() < 16).limit(3).toList());
    }

    /** Runs every task booked on a scheduler, those booked while running too, and says how many ran. */
    private static int runBooked(final org.bukkit.scheduler.BukkitScheduler scheduler)
    {
        int ran = 0;
        while (true)
        {
            final ArgumentCaptor<Runnable> booked = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, atLeast(0)).scheduleSyncDelayedTask(any(org.bukkit.plugin.Plugin.class),
                booked.capture(), anyLong());
            final List<Runnable> all = booked.getAllValues();
            if (ran >= all.size())
            {
                return ran;
            }
            all.get(ran++).run();
        }
    }

    /** How far the farthest block of a batch is from the test viewer's eye at (10.5, 65.62, 7.5). */
    private static double farthestFromTheEye(final Collection<BlockState> batch)
    {
        return batch.stream().mapToDouble(MirrorWindowsTest::fromTheEye).max().orElseThrow();
    }

    /** The same for the nearest. */
    private static double nearestToTheEye(final Collection<BlockState> batch)
    {
        return batch.stream().mapToDouble(MirrorWindowsTest::fromTheEye).min().orElseThrow();
    }

    private static double fromTheEye(final BlockState state)
    {
        final double dx = (state.getX() + 0.5) - 10.5;
        final double dy = (state.getY() + 0.5) - 65.62;
        final double dz = (state.getZ() + 0.5) - 7.5;
        return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    /**
     * A room cut shallower than the depth to fit under the cap says so, in red.
     *
     * <p>"If it says a smaller depth, the 250,000-block cap on a held room cut it." It reported
     * the smaller depth as though that were the setting; an admin raising the depth and seeing no
     * change had nothing to tell them why.
     */
    @Test
    void aRoomCutToFitUnderTheCapSaysSo()
    {
        MirrorWindows.mostFixed = 100;
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final List<String> said = MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList();
        assertTrue(said.stream().anyMatch(line -> line.startsWith("museum: drawn whole cut to depth ")
            && line.contains(" of 16 to fit 100 blocks")), "the cut and the cap named: " + said);
    }

    /**
     * A room from a capture taken on a server is cut to fit under the cap too.
     *
     * <p>A capture keeps only what could be seen, and its room is built by walking what it kept
     * rather than the volume; the test above builds from a capture that holds everything. Uncut,
     * a room past the cap is held whole however many blocks it is.
     */
    @Test
    void aRoomFromAPrunedCaptureIsCutToFitUnderTheCapToo()
    {
        MirrorCaptures.install(arrival, prunedGroundBelow(arrival, 70, farOneBlock));
        MirrorWindows.mostFixed = 100;
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final List<String> said = MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList();
        assertTrue(said.stream().anyMatch(line -> line.startsWith("museum: drawn whole cut to depth ")
            && line.contains(" of 16 to fit 100 blocks")), "the cut and the cap named: " + said);
    }

    /**
     * A wall mirror short of the proximity distance is held whole and clipped to each eye, so it
     * reaches the full depth on the move, where walking its cone ran out.
     *
     * <p>"I want to capture further for the mirror. Right now it seems short and is showing the
     * real world after the mirror one." A wall with a gap was walked like a freestanding mirror,
     * and a redraw on the move reached what its budget allowed: from right against the mirror,
     * well short of the depth. The clipped room reaches the configured depth from there, keeps
     * what it drew, and says so.
     */
    @Test
    void aWallShortOfTheProximityDistanceIsHeldWholeAndClippedToEachEye()
    {
        gap = new Spot(16, 64, 11);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            pause();
            MirrorWindows.moved(viewer, new Location(world, 10.5, 64.0, 9.8));
        });

        final List<String> said = MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList();
        assertTrue(said.stream().anyMatch(line -> line.startsWith("museum: whole to depth 16, clipped to each eye")),
            "how it is drawn: " + said);
        assertFalse(positions(changesTo(viewer, 2).get(1)).containsKey(new Spot(10, 65, 22)),
            "the deep block drawn at first is not taken back");
    }

    /**
     * A wall mirror with another mirror within twice the depth is trimmed, whether or not the
     * viewer can see the other one.
     *
     * <p>Alcoves a block apart along the library wall: drawn whole, each would fill the same
     * space behind the wall with its own far side, and looking into one would show the other's.
     * The viewer here stands too far from the second to see it, and the first is still trimmed.
     */
    @Test
    void aWallMirrorWithAnotherMirrorNearbyIsTrimmedEvenWhenTheOtherIsOutOfSight()
    {
        secondWindowAt(20);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 13)), "straight through the middle");
        assertFalse(drawn.containsKey(new Spot(18, 64, 12)), "off to the side, not drawn");
        assertFalse(drawn.containsKey(new Spot(20, 64, 13)), "and nothing of the second, out of sight");
    }

    /**
     * Past the depth from the opening nothing is drawn.
     *
     * <p>The view was closed with a shell a block thick -- painted with the distance, then fog,
     * then sky -- and none of them looked right. The view stops at the depth now, measured from
     * the middle of the opening rather than the eye, and past it a line of sight meets the real
     * world.
     */
    @Test
    void pastTheDepthFromTheOpeningNothingIsDrawn()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        // The opening's middle is (10.5, 64, 11.5): fifteen blocks straight in, and seventeen.
        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 26)), "within the depth, the block itself");
        assertFalse(drawn.containsKey(new Spot(10, 64, 28)), "past it, nothing at all");
    }

    /**
     * A mirror whose far side has not been captured yet stays a banner, and asks for a capture.
     *
     * <p>The same mirror opens once the capture is there, which is what shows the refusal was
     * about the capture and not about something else in the setup.
     */
    @Test
    void aMirrorWithNoCaptureYetStaysABannerAndAsksForOne()
    {
        MirrorCaptures.clear();
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            verify(viewer, never()).sendBlockChanges(anyCollection());
            assertEquals(1, MirrorCaptures.taking(), "asked for");
            MirrorCaptures.install(arrival, solidCapture(arrival, farOneBlock));
            MirrorProximity.tick();
        });

        changesTo(viewer, 1);
    }

    /**
     * A new capture arriving redraws every viewer of it, without anybody moving.
     *
     * <p>That is how a dynamic mirror's view changes, and how the first capture appears for a
     * viewer already standing in front of the mirror.
     */
    @Test
    void aNewCaptureRedrawsWhoeverIsLooking()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorCaptures.install(arrival, solidCapture(arrival, farTwoBlock));
            MirrorProximity.tick();
        });

        assertTrue(drawnAs(changesTo(viewer, 2).get(1), farTwoBlock) > 0);
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
     * A mirror with something solid between the viewer and its opening is not seen at all.
     *
     * <p>Somebody in a corridor was "in front of" every alcove mirror on the same wall, and a
     * redraw spent its whole budget on four mirrors the corridor walls hid from them, cutting
     * short the one they were looking at. Take the wall away and the same mirror is seen.
     */
    @Test
    void aMirrorBehindSomethingSolidIsNotSeen()
    {
        solidAt(10, 64, 9);
        solidAt(10, 63, 9);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            verify(viewer, never()).sendBlockChanges(anyCollection());
            doReturn(blockAt(10, 64, 9, true)).when(world).getBlockAt(10, 64, 9);
            doReturn(blockAt(10, 63, 9, true)).when(world).getBlockAt(10, 63, 9);
            MirrorProximity.clear();
            MirrorCaptures.install(arrival, solidCapture(arrival, farOneBlock));
            MirrorProximity.tick();
        });

        changesTo(viewer, 1);
    }

    /**
     * Two windows a block apart share the wall without drawing over each other.
     *
     * <p>Each block behind the wall is drawn once, from the window whose opening the viewer's line
     * of sight passes through. And nothing is resent on a second sweep, because nothing fights.
     * Seen together, walled windows are trimmed like this rather than drawn whole: whole, their
     * two far sides fill the same space behind the wall, and one would show in the other.
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
     * opening. The same block against a wall is drawn, in the test after this one. A wall of
     * nothing but the opening stands in for the open air.
     */
    @Test
    void inOpenAirABlockReachingPastTheOpeningIsNotDrawn()
    {
        wallBehind = false;
        final Player viewer = playerAt(10.5, 7.5);
        standLow(viewer, 10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 20)), "far back, all of it behind");
        assertFalse(drawn.containsKey(new Spot(11, 64, 12)), "close, and half of it beside");
    }

    /**
     * In open air, a block straddling the edge of a narrow wall is drawn when a real wall in
     * front hides the face beside it.
     *
     * <p>A mirror on a small hut on a beach showed the sea through the view: blocks behind the
     * hut straddled the edge of its three-block front wall, were rejected as showing beside it,
     * and the real sea in them stayed. With the hut's side walls counted, the face beside the
     * front wall is hidden from the eye inside, and the same blocks are drawn.
     */
    @Test
    void aRealWallInFrontHidesTheFaceBesideANarrowWall()
    {
        wallBehind = false;
        for (int y = 62; y <= 66; y++)
        {
            for (int z = 8; z <= 10; z++)
            {
                solidAt(9, y, z);
                solidAt(11, y, z);
            }
        }
        final Player viewer = playerAt(10.5, 9.2);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        // Just behind the opening and a block to the side: two thirds of its outline lands on
        // the face beside the opening, which the side wall at x 9 hides from this eye.
        assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(new Spot(9, 63, 12)));
    }

    /** The same block, with nothing real in front to hide the face beside the opening. */
    @Test
    void withoutARealWallInFrontTheSameStraddlingBlockIsLeftOut()
    {
        wallBehind = false;
        final Player viewer = playerAt(10.5, 9.2);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertFalse(positions(changesTo(viewer, 1).get(0)).containsKey(new Spot(9, 63, 12)),
            "most of it would show beside the opening, in the open air");
    }

    /**
     * In open air, a straddling block whose far side is air is left alone, not carved.
     *
     * <p>Carving it opened a notch of the far side beside the opening: on small freestanding
     * mirrors the other world showed in this one. A block straight behind the opening, all of
     * it covered, is still carved.
     */
    @Test
    void inOpenAirAStraddlingBlockWhoseFarSideIsAirIsLeftAlone()
    {
        wallBehind = false;
        MirrorCaptures.install(arrival, groundBelow(arrival, -100, farOneBlock));
        final Player viewer = playerAt(10.5, 9.2);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(air, drawn.get(new Spot(10, 63, 12)), "straight behind the opening, carved");
        assertFalse(drawn.containsKey(new Spot(9, 63, 12)), "beside it, left as it really is");
    }

    @Test
    void againstAWallTheSameBlockIsDrawnSinceTheWallHidesTheRest()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(new Spot(11, 65, 12)));
    }

    /**
     * The outer half of the wall's outermost ring is a margin nothing is drawn onto, so what shows
     * beside the opening scales with the wall.
     *
     * <p>"For a border of 1 we need to trim better; there's a lot of leaking around the border.
     * Should it change based on border?" A block drawn onto the wall beside the opening is hidden
     * only from the eye it was drawn for; a step shifts where it lands, and with one block of wall
     * the shift carried it past the wall's edge before the next redraw. A whole ring was too much:
     * on a three-wide panel a block straight through the opening spills a third of a block onto
     * the top ring and was dropped too. Half a block absorbs a step. The block at 11 65 12 lands
     * mostly on the outer half of the ring at x 11 -- the edge of a three-wide panel -- and is left
     * out; inside a five-wide one it is drawn; and the block straight through is drawn on both.
     */
    @Test
    void theOuterHalfOfTheWallsEdgeIsAMarginNothingIsDrawnOnto()
    {
        panel = 1;
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> narrow = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, narrow.get(new Spot(10, 64, 13)),
            "straight through the opening, spilling a third of a block onto the ring's inner half");
        assertFalse(narrow.containsKey(new Spot(11, 65, 12)), "landing on the panel's edge: a step would carry it off");
    }

    /**
     * A shadow thrown by a block almost level with the eye is clamped to the face that is read.
     *
     * <p>From the server log: fifteen seconds in {@code shielded}, adding the cells of one shadow
     * hundreds of blocks across, one by one, while a redraw caught a viewer up. A block whose near
     * corner is a twentieth of a block from the eye's depth projects three hundred times its size.
     * Nothing outside the read face is ever asked about, so the clamp loses nothing.
     */
    @Test
    void aShadowThrownFromAlmostLevelWithTheEyeIsClampedToTheFaceRead()
    {
        final int[] span = { 0, 18 };

        assertArrayEquals(new int[] { 0, 18, 56, 82 },
            MirrorWindows.withinFace(new double[] { -300.0, 300.0, -200.0, 250.0 }, span, 56, 82),
            "a shadow six hundred blocks across marks the read face and no more");
        assertArrayEquals(new int[] { 10, 10, 64, 64 },
            MirrorWindows.withinFace(new double[] { 9.9, 11.05, 63.9, 65.1 }, span, 56, 82),
            "an ordinary shadow marks only the blocks wholly inside it, as before");
    }

    /** The same block on a five-wide panel lands a block inside its edge, and is drawn. */
    @Test
    void insideAWiderPanelsEdgeTheSameBlockIsDrawn()
    {
        panel = 2;
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(new Spot(11, 65, 12)));
    }

    /**
     * The inside of the far ground is left as the real world has it, not drawn and not carved.
     *
     * <p>A deep view is mostly ground: a mirror onto a field looks down into the soil under it.
     * Drawn whole, a wall's view would send the inside of the hill, most of its cost and none of
     * the picture; read as air, it would carve that hill out of the real ground behind the wall.
     */
    @Test
    void theInsideOfTheFarGroundIsLeftAsTheRealWorldHasIt()
    {
        when(farOneBlock.isOccluding()).thenReturn(true);
        MirrorCaptures.install(arrival, prunedGroundBelow(arrival, 70, farOneBlock));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        // A block here shows the far side seven blocks higher: y 62 is the far surface, y 69.
        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 62, 14)), "the far surface");
        assertSame(farOneBlock, drawn.get(new Spot(10, 61, 14)), "and the layer under it");
        assertFalse(drawn.containsKey(new Spot(10, 58, 14)), "four deep, the real world stays");
    }

    /**
     * Far-side air over a block that is really empty is not sent: it would change nothing.
     *
     * <p>Over a real block it is sent, in the test after this one, because there it opens up the
     * view.
     */
    @Test
    void farSideAirOverAnEmptyBlockIsNotSent()
    {
        MirrorCaptures.install(arrival, groundBelow(arrival, -100, farOneBlock));
        localEmpty = true;
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Collection<BlockState> batch = changesTo(viewer, 1).get(0);
        assertEquals(2, drawnAs(batch, barrier), "the opening still opens");
        assertEquals(MirrorPackets.available() ? 1 : 0, drawnAs(batch, air),
            "and only the banner is drawn as air");
    }

    @Test
    void farSideAirOverARealBlockIsSent()
    {
        MirrorCaptures.install(arrival, groundBelow(arrival, -100, farOneBlock));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertTrue(drawnAs(changesTo(viewer, 1).get(0), air) > 10,
            "air opening up the view through whatever really stands behind the wall");
    }

    /**
     * A creature of the viewer's own world standing inside the view is hidden from them, and
     * shown again when they walk away.
     *
     * <p>A drawn block hides what is behind it, but a creature is not a block: an armour stand on
     * the real side kept standing in the middle of the far side.
     */
    @Test
    void aCreatureStandingInsideTheViewIsHiddenAndShownAgainAfterwards()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final Entity stand = mock(ArmorStand.class);
        when(stand.getUniqueId()).thenReturn(UUID.randomUUID());
        when(stand.getLocation()).thenReturn(new Location(world, 10.5, 63.0, 14.5));
        // In front of the wall, on the viewer's own side, where nothing is drawn.
        final Entity aside = mock(ArmorStand.class);
        when(aside.getUniqueId()).thenReturn(UUID.randomUUID());
        when(aside.getLocation()).thenReturn(new Location(world, 18.5, 63.0, 9.5));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(List.of(stand, aside));

        withServer(() ->
        {
            MirrorProximity.tick();
            verify(viewer).hideEntity(any(), org.mockito.ArgumentMatchers.eq(stand));
            verify(viewer, never()).hideEntity(any(), org.mockito.ArgumentMatchers.eq(aside));
            stand(viewer, 10.5, -40.0);
            MirrorProximity.tick();
        });

        verify(viewer).showEntity(any(), org.mockito.ArgumentMatchers.eq(stand));
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
     * opening did not change, so it is not in the update. A gap in the wall, since a mirror
     * drawn whole is the same from anywhere.
     */
    @Test
    void steppingSidewaysSendsOnlyWhatChanged()
    {
        gap = new Spot(16, 64, 11);
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
        gap = new Spot(16, 64, 11);
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

        assertEquals(2, drawnAs(changesTo(viewer, 2).get(1), barrier),
            "the opening, which did not change, is in it");
    }


    /**
     * The whole view goes again every half minute, which is what heals a chunk reload.
     *
     * <p>Nothing tells this plugin that a client reloaded a chunk or was teleported somewhere and
     * back, and a chunk arriving as the world really is erases whatever was drawn in it.
     * Differences alone would leave those holes until the viewer happened to cross a chunk, so the
     * whole view is sent again on a timer whatever else happens. The opening is the tell: it is
     * drawn as barrier and never changes, so it rides along only when everything does.
     */
    @Test
    void theWholeViewGoesAgainAfterTheResendInterval()
    {
        final long[] clock = { 1_000_000L };
        MirrorWindows.clock = () -> clock[0];
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            // A step in the same chunk, a moment later. The room is held whole behind this wall,
            // so it is drawn the same for every eye and a step changes nothing to send.
            clock[0] += 200L;
            MirrorWindows.moved(viewer, new Location(world, 10.8, 64.0, 7.5));
            verify(viewer, times(1)).sendBlockChanges(anyCollection());
            // The same sort of step, half a minute on: everything goes again anyway.
            clock[0] += MirrorWindows.RESEND_MILLIS + 1L;
            MirrorWindows.moved(viewer, new Location(world, 11.1, 64.0, 7.5));
        });

        assertEquals(2, drawnAs(changesTo(viewer, 2).get(1), barrier),
            "the opening, which never changes, rides along only when the whole view does");
    }


    /**
     * A viewer drawn a room has their own fog pulled in to it, and gets it back on leaving.
     *
     * <p>Wiring, not arithmetic: {@code MirrorFogTest} pins what number is asked for and why.
     * What this holds is that the asking happens where a view begins and the putting-back where
     * one ends -- and a view ends in four places, two of them inside the stream that takes a room
     * back. Restoring only where a redraw finds nothing left to draw looked right and left a
     * viewer who walked away narrowed for the rest of their session.
     *
     * <p>Paper only and off by default, so both halves are easy to wire to nothing by accident.
     */
    @Test
    void aViewerDrawnARoomHasTheirFogPulledInAndPutBack()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, true);
        final List<Integer> fog = new ArrayList<>();
        MirrorFog.sendDistanceWith(new MirrorFog.SendDistance()
        {
            @Override
            public int get(final Player player)
            {
                return 10;
            }

            @Override
            public void set(final Player player, final int chunks)
            {
                fog.add(chunks);
            }
        });
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            assertEquals(List.of(2), fog, "a room 16 deep is one chunk, and one over for the edge");
            // The feature is invisible by design, so debug has to be able to say it happened.
            final List<String> debug = MirrorWindows.describe(viewer).stream()
                .map(MirrorWindowsTest::plain).toList();
            assertTrue(debug.stream().anyMatch(line -> line.equals("fog: pulled in to 2 chunk(s), from 10")),
                "debug says what the fog did: " + debug);
            pause();
            // Well out of range: the room goes back, and the view ends with it.
            MirrorWindows.moved(viewer, new Location(world, 10.5, 64.0, 60.0));
        });

        assertEquals(List.of(2, 10), fog, "and the ten they were being sent before comes back");
    }


    /**
     * Stopping puts a viewer's fog back even if they have since changed worlds.
     *
     * <p>From the review. The blocks a mirror drew in a world somebody has left are already gone
     * with the chunks, so the send is rightly skipped for them -- but a narrowed send distance is
     * the player's own and follows them out of the world, so it has to go back whatever world they
     * are standing in. Gated behind the same world check as the blocks, it did not.
     */
    @Test
    void stoppingPutsTheFogBackEvenForAViewerWhoChangedWorlds()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, true);
        final List<Integer> fog = new ArrayList<>();
        MirrorFog.sendDistanceWith(new MirrorFog.SendDistance()
        {
            @Override
            public int get(final Player player)
            {
                return 10;
            }

            @Override
            public void set(final Player player, final int chunks)
            {
                fog.add(chunks);
            }
        });
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            assertEquals(List.of(2), fog, "narrowed while being drawn a room");
            // Through a portal: the drawing's world is stale, the player's fog is not.
            final World elsewhere = mock(World.class);
            when(viewer.getWorld()).thenReturn(elsewhere);
            MirrorWindows.restoreAll();
        });

        assertEquals(List.of(2, 10), fog, "the ten they had comes back wherever they are");
    }

    /**
     * {@code mirror debug} says which of the fog's four states a viewer is in.
     *
     * <p>The feature is invisible by design -- a room ending in fog looks like a room that ends --
     * so the line is the only way to tell "pulled in" from "off", from "this server has not got
     * the method", from "already sent no further than the room reaches". The pulled-in case is
     * {@code aViewerDrawnARoomHasTheirFogPulledInAndPutBack}, no method is
     * {@code debugSaysWhenTheServerHasNoFog}, and these are the other two.
     */
    @Test
    void debugSaysWhichStateTheFogIsIn()
    {
        final Player asker = playerAt(200.5, 200.5);

        assertEquals("fog: off (mirror-fog-at-depth)", fogLine(asker), "off by default");

        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, true);
        // A client already being sent no further than a 16-deep room reaches.
        MirrorFog.sendDistanceWith(new MirrorFog.SendDistance()
        {
            @Override
            public int get(final Player player)
            {
                return 2;
            }

            @Override
            public void set(final Player player, final int chunks)
            {
                throw new AssertionError("nothing to gain, so nothing should be set");
            }
        });
        assertTrue(fogLine(asker).contains("nothing pulled in"),
            "on, and no narrower than they already are: " + fogLine(asker));
    }

    /** On a server without the method, debug says so. Not Paper, where the method is there. */
    @Test
    @DisabledIfSystemProperty(named = "server.api", matches = "paper")
    void debugSaysWhenTheServerHasNoFog()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, true);

        final String line = fogLine(playerAt(200.5, 200.5));

        assertTrue(line.contains("no Player.setSendViewDistance"),
            "on, with no Paper under it: " + line);
    }

    /** The one debug line about the fog, without its colours. */
    private static String fogLine(final Player player)
    {
        return MirrorWindows.describe(player).stream().map(MirrorWindowsTest::plain)
            .filter(line -> line.startsWith("fog: ")).findFirst().orElse("no fog line");
    }


    /**
     * Walking into another world drops the view and gives the fog back.
     *
     * <p>The chunks of the new world have already replaced everything drawn in the old one, so
     * there is nothing to take back -- but the send distance is the player's own and came with
     * them, so it has to go back here too. One of the four places a view ends.
     */
    @Test
    void walkingIntoAnotherWorldGivesTheFogBack()
    {
        final List<Integer> fog = watchTheFog();
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            assertEquals(List.of(2), fog, "narrowed while being drawn a room");
            // A redraw on a move is throttled, so wait the gap out before stepping.
            pause();
            final World elsewhere = mock(World.class);
            when(viewer.getWorld()).thenReturn(elsewhere);
            MirrorWindows.moved(viewer, new Location(world, 12.0, 64.0, 7.5));
        });

        assertEquals(List.of(2, 10), fog, "and given back on the way into the new world");
        assertFalse(MirrorFog.narrowed(viewer.getUniqueId()), "nothing left remembered");
    }

    /**
     * A viewer who logs out mid-view is forgotten by the next sweep.
     *
     * <p>Nothing can be sent to somebody who is not there, and their send distance died with the
     * connection. What matters is that the sweep stops remembering them: an entry kept for a
     * player who has gone would make a mirror skip them if they came back on the same id.
     */
    @Test
    void aViewerWhoLogsOutMidViewIsForgottenByTheSweep()
    {
        final List<Integer> fog = watchTheFog();
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);
        assertEquals(List.of(2), fog, "narrowed while they were here");

        // Gone: no players in the world, so the sweep finds no player behind the view either.
        when(world.getPlayers()).thenReturn(List.of());
        withServer(MirrorProximity::tick);

        assertEquals(List.of(2), fog, "nothing sent to somebody who is not there");
        assertFalse(MirrorFog.narrowed(viewer.getUniqueId()),
            "and they are not remembered as narrowed, so coming back narrows them again");
    }

    /** A fog that reports ten chunks and records every number it is asked to set. */
    private static List<Integer> watchTheFog()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, true);
        final List<Integer> asked = new ArrayList<>();
        MirrorFog.sendDistanceWith(new MirrorFog.SendDistance()
        {
            @Override
            public int get(final Player player)
            {
                return 10;
            }

            @Override
            public void set(final Player player, final int chunks)
            {
                asked.add(chunks);
            }
        });
        return asked;
    }


    /**
     * A click part-way through taking a room back still takes all of it back.
     *
     * <p>"Scrolling through mirror worlds, sometimes part of the previous one remains for a couple
     * seconds." A click books the whole view to be sent again a tick later, and a room big enough
     * to stream is still going out then. That resend took what had been sent so far as the truth:
     * it threw away the rest of the stream, and drew the part of the room not yet taken back all
     * over again, where it stayed until the next sweep. Walking away is the plainest way to have a
     * room half taken back; scrolling to another mirror does the same to the room it leaves.
     */
    @Test
    void aClickPartWayThroughTakingARoomBackStillTakesAllOfItBack() throws Exception
    {
        MirrorWindows.streamPerTick = 500;
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            final List<String> midway = new ArrayList<>();
            final List<String> after = new ArrayList<>();
            withServer(() ->
            {
                MirrorProximity.tick();
                runBooked(scheduler);
                // What has run is done: from here only the take-back and the click are booked.
                clearInvocations(scheduler);
                pause();
                MirrorWindows.moved(viewer, new Location(world, 10.5, 64.0, 60.0));
                MirrorWindows.resend(viewer);
                midway.addAll(MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList());
                runBooked(scheduler);
                after.addAll(MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList());
            });

            assertTrue(midway.stream().anyMatch(line -> line.startsWith("still to send: ")),
                "the click has to land while the room is still going back, or this proves nothing: " + midway);
            assertTrue(after.contains("looking into: no window"),
                "and every block of it goes back, rather than being drawn again: " + after);
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }
    }

    /**
     * A click part-way through drawing a room still draws all of it.
     *
     * <p>The other half of the same resend. Blocks still owed to a room coming into view had not
     * been sent, so they were not in what the resend sent again, and throwing the stream away lost
     * them: the far part of the room a scroll had just opened onto stayed missing until the sweep.
     */
    @Test
    void aClickPartWayThroughDrawingARoomStillDrawsAllOfIt() throws Exception
    {
        MirrorWindows.streamPerTick = 500;
        final Player viewer = playerAt(10.5, 7.5);
        when(viewer.isOnline()).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            final int[] drawn = new int[2];
            withServer(() ->
            {
                MirrorProximity.tick();
                MirrorWindows.resend(viewer);
                drawn[0] = drawnCount(viewer);
                runBooked(scheduler);
                drawn[1] = drawnCount(viewer);
            });

            assertTrue((drawn[0] > 0) && (drawn[0] <= 500),
                "the click has to land part-way through, with a tick sent: " + drawn[0]);
            assertTrue(drawn[1] > (3 * drawn[0]),
                "and the rest of the room still arrives, rather than stopping at what was out when the click"
                    + " landed: " + drawn[0] + " then " + drawn[1]);
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }
    }

    /** How many blocks a viewer has been sent, read from {@code mirror debug}. */
    private static int drawnCount(final Player viewer)
    {
        for (final String line : MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList())
        {
            if (line.startsWith("drawn: "))
            {
                return Integer.parseInt(line.substring("drawn: ".length()).split(" ")[0]);
            }
        }
        return 0;
    }


    /** Turning the fog on while somebody stands at a mirror reaches them at the next sweep. */
    @Test
    void turningTheFogOnReachesAViewerAlreadyAtTheMirror()
    {
        final List<Integer> fog = watchTheFog();
        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, false);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(() ->
        {
            MirrorProximity.tick();
            assertEquals(List.of(), fog, "off when they walked up, so nothing yet");
            ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, true);
            MirrorProximity.tick();
        });

        assertEquals(List.of(2), fog, "on at the next sweep, without leaving the mirror");
    }

    @Test
    void clickingTheOpeningWhileLookingInIsTheMirror()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(MirrorManager.byName("museum"),
            MirrorWindows.clicked(viewer, blockAt(10, 63, 11, true)), "the opening");
        assertNull(MirrorWindows.clicked(viewer, blockAt(40, 63, 12, true)),
            "a block nowhere near the view is nothing to do with the mirror");
    }

    /**
     * A block drawn into the view is the mirror's to click, not the world's.
     *
     * <p>From testing: right-clicking into a mirror with a torch placed it "momentarily". The
     * client clicked the room it was shown, the server placed on the real air behind the wall, and
     * the next redraw drew the room back over it.
     */
    @Test
    void aBlockDrawnIntoTheViewIsTheMirrorToClick()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        Block drawn = null;
        for (int z = 12; (z < 40) && (drawn == null); z++)
        {
            final Block candidate = blockAt(10, 63, z, true);
            if (MirrorWindows.drew(viewer, candidate))
            {
                drawn = candidate;
            }
        }
        assertNotNull(drawn, "the room behind the opening is drawn somewhere along its middle");
        assertSame(MirrorManager.byName("museum"), MirrorWindows.clicked(viewer, drawn),
            "a click on it is a click on the mirror");
        assertFalse(MirrorWindows.drew(viewer, blockAt(40, 63, 12, true)), "and nothing else is");
        assertFalse(MirrorWindows.drew(playerAt(10.5, 7.5), drawn), "and only for whoever is shown it");
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
     * A banner on a post is no window, however it is pointed.
     *
     * <p>It used to open in the air behind where it stood, and its room showed past its edges
     * whatever trimmed it; {@code create} refuses one now, and one from an older file draws nothing.
     */
    @Test
    void aFreestandingBannerIsNoWindow()
    {
        standUp(banner);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        verify(viewer, never()).sendBlockChanges(anyCollection());
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

    /** A {@code mirror debug} line without its colours or indent, as {@code label: value}. */
    private static String plain(final String line)
    {
        return line.replaceAll("§.", "").trim();
    }

    /** Moves the redraw clock past the least time between two redraws of one viewer. */
    private static void pause()
    {
        paused += MirrorWindows.REDRAW_MILLIS + 30L;
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

    /** A capture of one block everywhere, 40 around the arrival point and 16 below to 64 above. */
    private MirrorCapture solidCapture(final MirrorPoint at, final BlockData everywhere)
    {
        return groundBelow(at, 1000, everywhere);
    }

    /** A capture of one block below a height and air above it. */
    private MirrorCapture groundBelow(final MirrorPoint at, final int surface, final BlockData ground)
    {
        return groundBuilder(at, surface, ground).build();
    }

    /** The same, with what is buried two deep marked as buried, as a capture taken on a server is. */
    private MirrorCapture prunedGroundBelow(final MirrorPoint at, final int surface, final BlockData ground)
    {
        final MirrorCapture.Builder builder = groundBuilder(at, surface, ground);
        builder.prune();
        return builder.build();
    }

    private MirrorCapture.Builder groundBuilder(final MirrorPoint at, final int surface, final BlockData ground)
    {
        final int x = (int) Math.floor(at.x());
        final int y = (int) Math.floor(at.y());
        final int z = (int) Math.floor(at.z());
        final MirrorCapture.Builder builder = new MirrorCapture.Builder(at.worldName(), true,
            new MirrorCapture.Box(x - 40, y - 16, z - 40, 81, 81, 81), air);
        builder.fillBelow(surface, ground);
        return builder;
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
        MirrorManager.add(new QuantumMirror("archive", new MirrorBlock("world", x, 64, 10), arrivalTwo));
    }

    /** Puts a solid, occluding block of the banner's world here. */
    private void solidAt(final int x, final int y, final int z)
    {
        final Block block = blockAt(x, y, z, false);
        final BlockData data = mock(BlockData.class);
        when(data.isOccluding()).thenReturn(true);
        when(block.getBlockData()).thenReturn(data);
        doReturn(block).when(world).getBlockAt(x, y, z);
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
        when(data.isOccluding()).thenReturn(wallBehind && (z == 11) && !new Spot(x, y, z).equals(gap)
            && ((panel == null) || ((Math.abs(x - 10) <= panel) && (y >= (63 - panel)) && (y <= (64 + panel)))));
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

    private static BlockData named(final String name)
    {
        final BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn(name);
        return data;
    }

    private static World named(final World mocked, final String name)
    {
        when(mocked.getName()).thenReturn(name);
        when(mocked.getMinHeight()).thenReturn(-64);
        when(mocked.getMaxHeight()).thenReturn(320);
        return mocked;
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

    /** Stands a player a block lower, with their eye inside the opening's two rows rather than over them. */
    private void standLow(final Player player, final double x, final double z)
    {
        when(player.getLocation()).thenReturn(new Location(world, x, 63.0, z));
        when(player.getEyeLocation()).thenReturn(new Location(world, x, 64.62, z));
    }

    /** Runs something with a server that knows this world, these players and the drawn blocks. */
    private void withServer(final Runnable body)
    {
        final World far = named(mock(World.class), "far");
        when(far.getEnvironment()).thenReturn(World.Environment.NORMAL);
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
