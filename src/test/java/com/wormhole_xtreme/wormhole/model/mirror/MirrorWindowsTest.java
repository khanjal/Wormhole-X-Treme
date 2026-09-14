package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
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
     * A right-click shows the mirror chosen at once, not when the sweep next comes round.
     *
     * <p>The sweep runs once a second, so a view waiting for it changed up to a second after the
     * click that chose it -- long enough to click again, and skip past the mirror you wanted.
     */
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
     * A freestanding mirror draws only far-side blocks the eye could see through the opening.
     *
     * <p>The rest stay as the world has them, which is what keeps the far side inside the edges
     * of a mirror with nothing round it to hide them.
     */
    @Test
    void aFreestandingMirrorDrawsOnlyBlocksSeenThroughTheOpening()
    {
        standUp(banner);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 64, 13)), "straight through the middle");
        assertFalse(drawn.containsKey(new Spot(18, 64, 12)),
            "off to the side, where no line of sight through the opening goes");
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
     * Draws a freestanding mirror's view, then steps to where some block it drew lies partly
     * beside the opening -- more than {@code least} of it and no more than {@code most} -- failing,
     * rather than passing, if no such step turns up.
     */
    private Step stepPutting(final double least, final double most)
    {
        wallBehind = false;
        standUp(banner);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final MirrorWindow shape = MirrorWindow.of(new MirrorBlock("world", 10, 64, 10), BlockFace.NORTH, true, arrival);
        final MirrorWindow.Face open = (across, y) -> (across == 10) && ((y == 64) || (y == 65));
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
                        if ((rect != null) && !shape.covered(rect, open, least) && shape.covered(rect, open, most))
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
     * A redraw on the move that runs out of budget keeps the deeper blocks the last one drew.
     *
     * <p>"Still flickering on the stone bricks behind the fence." A redraw while walking has a
     * third of a still one's budget; close to the mirror it ran out, reached less far, and took
     * back what lay further, which the next sweep drew again. Any redraw that does not reach a
     * block -- out of budget, or stopped behind nearer blocks -- keeps it while it is still right
     * to show. The move's reach is checked to have really shrunk, so the test cannot pass by
     * never running out.
     */
    @Test
    void aRedrawOnTheMoveThatRunsOutKeepsTheDeeperBlocksTheLastOneDrew()
    {
        standUp(banner);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final Spot deep = new Spot(10, 65, 22);

        withServer(() ->
        {
            MirrorProximity.tick();
            assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(deep), "drawn standing still");
            MirrorWindows.mostWhileMoving = 200;
            pause();
            MirrorWindows.moved(viewer, new Location(world, 10.5, 64.0, 9.8));
        });

        final List<String> said = MirrorWindows.describe(viewer).stream().map(MirrorWindowsTest::plain).toList();
        final String redraw = String.join("; ", said);
        final int reach = Integer.parseInt(said.stream().filter(line -> line.startsWith("radius: ")).findFirst()
            .orElseThrow(() -> new AssertionError("no radius said: " + redraw)).replaceAll("radius: (\\d+).*", "$1"));
        assertTrue(reach < 11, "the move's own reach fell short of the deep block: " + redraw);
        final List<Collection<BlockState>> sent = changesTo(viewer, 2);
        final Map<Spot, BlockData> update = positions(sent.get(1));
        assertFalse(update.containsKey(deep) && (update.get(deep) == null), deep + " was taken back: " + redraw);
    }

    /**
     * A block landing on the frame round the opening is drawn already, hidden behind the frame.
     *
     * <p>"When I face the frame brick and slide into the mirror view I see it render. It should
     * already be mostly there since I'm right up against the frame." From beside the opening, a
     * block straight behind it lands on the frame brick; it was drawn only once a step brought it
     * into the opening. The frame hides it, so it is drawn beforehand.
     */
    @Test
    void aBlockLandingOnTheFrameRoundTheOpeningIsDrawnAlreadyHiddenBehindTheFrame()
    {
        standUp(banner);
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
     * <p>A redraw keeps what it did not reach, so the bricks behind the fence stop flickering;
     * it must not keep what is now wrong. Stepped well to the side of a freestanding mirror in
     * open air, a block straight behind the opening lands beside it.
     */
    @Test
    void aBlockKeptFromTheLastDrawingIsTakenBackOnceItWouldShowBesideTheOpening()
    {
        wallBehind = false;
        standUp(banner);
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));
        final Spot straight = new Spot(10, 65, 13);

        withServer(() ->
        {
            MirrorProximity.tick();
            assertSame(farOneBlock, positions(changesTo(viewer, 1).get(0)).get(straight), "drawn straight ahead");
            pause();
            MirrorWindows.moved(viewer, new Location(world, 13.5, 64.0, 9.5));
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
     * I can check what it's stored and how it's rendering." A freestanding mirror, so nothing
     * would be drawn off to the side or past the depth otherwise; and the depth is 16, so a
     * block at 30 would not be drawn for anyone else.
     */
    @Test
    void drawnFullForOneViewerAMirrorShowsEverythingItsCaptureHolds()
    {
        standUp(banner);
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
        standUp(banner);
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
     * opening did not change, so it is not in the update. A freestanding mirror, since one in a
     * wall is the same from anywhere.
     */
    @Test
    void steppingSidewaysSendsOnlyWhatChanged()
    {
        standUp(banner);
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
        standUp(banner);
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

    @Test
    void clickingTheOpeningWhileLookingInIsTheMirror()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(MirrorManager.byName("museum"),
            MirrorWindows.clicked(viewer, blockAt(10, 63, 11, true)), "the opening");
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
            x - 40, y - 16, z - 40, 81, 81, 81, air);
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

    /**
     * A view's reach grows with the cube root of the room it had, at most half again per
     * redraw, and never past the configured depth.
     *
     * <p>Two blocks a redraw took a dozen redraws to recover from one close approach. The cost
     * of a view goes with the cube of its radius, so a redraw that used a tenth of its budget can
     * afford a radius 1.7 times as large, capped at 1.5 so one guess too far costs one redraw.
     */
    @Test
    void reachGrowsByTheCubeRootOfTheRoomToSpareAndAtMostHalfAgain()
    {
        final int most = 40_000;
        assertEquals(19, MirrorWindows.grown(19, 32, most / 2, most), "half spent is no room to grow");
        assertEquals(19, MirrorWindows.grown(19, 32, 0, most), "a spent budget never grows");
        assertEquals(21, MirrorWindows.grown(19, 32, (most / 2) + 1, most), "just under half spent grows two, the least step");
        assertEquals(28, MirrorWindows.grown(19, 32, most - 4_000, most), "a tenth spent grows half again, not the cube root's 1.7");
        assertEquals(32, MirrorWindows.grown(30, 32, most - 4_000, most), "and never past the configured depth");
        assertEquals(32, MirrorWindows.grown(40, 32, most, most), "a radius above the configured depth comes down to it");
        assertEquals(28, MirrorWindows.grown(19, 32, 108_000, 120_000), "the same tenth of a bigger budget, standing still");
    }

    /**
     * A floor row seen from close to the opening is a band thinner than a grid part, and the
     * next row is the band above it; the nearer must not hide the farther.
     *
     * <p>Marking a part hidden whole when an outline crossed its middle did exactly that: the
     * floor of the library vanished in patches from a few blocks in, and its shelves, seen
     * edge-on, with it. Behind all the rows together, within them, a block is hidden.
     */
    @Test
    void aThinFloorRowDoesNotHideTheRowBehindIt()
    {
        final MirrorWindows.Occlusion grid = new MirrorWindows.Occlusion(0, 0, 1, 2);
        for (int row = 1; row <= 20; row++)
        {
            final double[] band = { 0.0, 1.0, 0.5 + ((row - 1) * 0.01), 0.5 + (row * 0.01) };
            assertFalse(grid.covers(band, row), "row " + row + " lies above every row before it");
            grid.add(band, row);
        }
        assertTrue(grid.covers(new double[] { 0.2, 0.8, 0.51, 0.69 }, 30),
            "within the twenty rows together, further back than all of them");
        assertFalse(grid.covers(new double[] { 0.2, 0.8, 0.69, 0.71 }, 30),
            "but not where it reaches past them");
    }

    /**
     * A wall of blocks that tile the opening hides everything behind it and closes the horizon.
     */
    @Test
    void aWallOfTiledBlocksHidesWhatIsBehindItAndClosesTheHorizon()
    {
        final MirrorWindows.Occlusion grid = new MirrorWindows.Occlusion(0, 0, 1, 2);
        assertEquals(Integer.MAX_VALUE, grid.horizon(), "open, nothing hides anything");
        grid.add(new double[] { 0.0, 1.0, 0.0, 1.0 }, 4);
        assertEquals(Integer.MAX_VALUE, grid.horizon(), "half a wall is no horizon");
        grid.add(new double[] { 0.0, 1.0, 1.0, 2.0 }, 4);

        assertTrue(grid.covers(new double[] { 0.3, 0.7, 0.2, 1.8 }, 5), "behind the wall");
        assertFalse(grid.covers(new double[] { 0.3, 0.7, 0.2, 1.8 }, 4), "not by its own layer");
        assertEquals(4, grid.horizon(), "and nothing past the wall is worth walking");
    }

    /**
     * Two outlines meeting in one part are joined, and the join hides only what is behind both.
     *
     * <p>Joined with the nearer layer, a block between the two would have been hidden by the
     * farther one, which is in front of nothing.
     */
    @Test
    void outlinesJoinedInOnePartHideOnlyWhatIsBehindBothOfThem()
    {
        final MirrorWindows.Occlusion grid = new MirrorWindows.Occlusion(0, 0, 1, 2);
        grid.add(new double[] { 0.0, 1.0, 0.0, 0.51 }, 3);
        grid.add(new double[] { 0.0, 1.0, 0.51, 1.0 }, 7);
        final double[] onTheSeam = { 0.2, 0.8, 0.505, 0.508 };

        assertFalse(grid.covers(onTheSeam, 5), "between the two, drawn to be safe");
        assertTrue(grid.covers(onTheSeam, 8), "behind both, hidden");
        assertTrue(grid.covers(new double[] { 0.2, 0.8, 0.1, 0.4 }, 5), "and behind the nearer alone, hidden");
    }

    /**
     * Two outlines meeting at a corner in one part are not joined: the rectangle round both
     * would claim the corner neither covers.
     *
     * <p>Found by the replay with its rays a two-hundredth of a block apart: a shelf's outline
     * and the floor's met at a corner in one part of the opening, the join claimed the corner,
     * and twenty-two rays through it met the lake behind the mirror.
     */
    @Test
    void outlinesMeetingAtACornerDoNotClaimTheCornerBetweenThem()
    {
        final MirrorWindows.Occlusion grid = new MirrorWindows.Occlusion(0, 0, 1, 2);
        // Inside the first part, a thirty-second of a block square: a strip up its left side
        // and a strip along its top, leaving the bottom-right corner open.
        grid.add(new double[] { 0.0, 0.02, 0.0, 0.03 }, 3);
        grid.add(new double[] { 0.0, 0.03125, 0.02, 0.03125 }, 3);

        assertFalse(grid.covers(new double[] { 0.022, 0.03, 0.005, 0.015 }, 5), "the open corner");
        assertTrue(grid.covers(new double[] { 0.005, 0.015, 0.005, 0.015 }, 5), "inside the strip that stands");
    }
}
