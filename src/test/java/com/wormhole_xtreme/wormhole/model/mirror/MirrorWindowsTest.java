package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private World world;
    private Block banner;
    private final BlockData air = named("minecraft:air");
    private final BlockData barrier = named("minecraft:barrier");
    private final BlockData sky = named("minecraft:light_blue_concrete");
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
     * Past the radius, the far side is painted onto a shell rather than cut off.
     *
     * <p>Every line of sight through the opening crosses the shell, so past it nothing of the
     * real world shows -- which a depth limit could not promise, and a barrier keeping viewers
     * back from the opening only half did.
     */
    @Test
    void pastTheRadiusTheFarSideIsPaintedOntoAShell()
    {
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        // The eye is just above the opening, so every line through it slopes down: these three
        // sit on one such line, thirteen, sixteen and a half, and twenty-three blocks out.
        final Map<Spot, BlockData> drawn = positions(changesTo(viewer, 1).get(0));
        assertSame(farOneBlock, drawn.get(new Spot(10, 62, 20)), "within the radius, the block itself");
        assertSame(farOneBlock, drawn.get(new Spot(10, 61, 23)), "on the shell, what the line meets");
        assertFalse(drawn.containsKey(new Spot(10, 60, 30)), "past the shell, nothing");
    }

    /** A shell block shows the first thing its line of sight meets, however far off. */
    @Test
    void aShellBlockShowsTheFirstThingItsLineOfSightMeets()
    {
        final BlockData ground = named("far:ground");
        MirrorCaptures.install(arrival, groundBelow(arrival, 60, ground));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        // A line sloping down from the eye that crosses the shell in open air and meets the
        // ground a dozen blocks on.
        assertSame(ground, positions(changesTo(viewer, 1).get(0)).get(new Spot(10, 58, 22)));
    }

    /** Where a line of sight leaves the capture without meeting anything, the shell shows sky. */
    @Test
    void whereALineOfSightMeetsNothingTheShellShowsSky()
    {
        MirrorCaptures.install(arrival, groundBelow(arrival, -100, farOneBlock));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(sky, positions(changesTo(viewer, 1).get(0)).get(new Spot(10, 61, 23)));
    }

    /**
     * The shell is painted even where the real world is open air.
     *
     * <p>Far-side air over a really empty block is left out within the radius, since it would
     * change nothing. Applied to the shell, that left the real world's horizon showing through a
     * mirror on a beach: the sand near the arrival point was drawn, and above it the viewer's own
     * glass house and sky. A shell block is solid, and needs painting over open air most of all.
     */
    @Test
    void theShellIsPaintedEvenWhereTheRealWorldIsOpenAir()
    {
        wallBehind = false;
        localEmpty = true;
        MirrorCaptures.install(arrival, groundBelow(arrival, -100, farOneBlock));
        final Player viewer = playerAt(10.5, 7.5);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        final Collection<BlockState> batch = changesTo(viewer, 1).get(0);
        assertEquals(MirrorPackets.available() ? 1 : 0, drawnAs(batch, air),
            "within the radius, air over air is not sent");
        assertTrue(drawnAs(batch, sky) > 10, "but the shell past it is, as sky: " + drawnAs(batch, sky));
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
     * A straddling block whose far side is air is carved all the same.
     *
     * <p>Carving a view through whatever is really there cuts a tunnel, and the tunnel's walls
     * are the blocks just outside the cone, whose faces show inside it. At a hut on a beach that
     * was a wall of sea water in the middle of the library. A notch in the sea beside the hut is
     * the lesser harm.
     */
    @Test
    void aStraddlingBlockWhoseFarSideIsAirIsCarvedAllTheSame()
    {
        wallBehind = false;
        MirrorCaptures.install(arrival, groundBelow(arrival, -100, farOneBlock));
        final Player viewer = playerAt(10.5, 9.2);
        when(world.getPlayers()).thenReturn(List.of(viewer));

        withServer(MirrorProximity::tick);

        assertSame(air, positions(changesTo(viewer, 1).get(0)).get(new Spot(9, 63, 12)));
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
     * A solid far side hides what is behind it, so what is behind it is never drawn.
     *
     * <p>A deep view is mostly ground: a mirror onto a field looks down into the soil under it.
     * Drawing the inside of the hill was most of the cost of a deep view and none of the picture.
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
        final Entity aside = mock(ArmorStand.class);
        when(aside.getUniqueId()).thenReturn(UUID.randomUUID());
        when(aside.getLocation()).thenReturn(new Location(world, 18.5, 63.0, 12.5));
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
        final int x = (int) Math.floor(at.x());
        final int y = (int) Math.floor(at.y());
        final int z = (int) Math.floor(at.z());
        final MirrorCapture.Builder builder = new MirrorCapture.Builder(at.worldName(), true,
            x - 40, y - 16, z - 40, 81, 81, 81, air);
        builder.fillBelow(surface, ground);
        return builder.build();
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
            bukkit.when(() -> Bukkit.createBlockData(Material.LIGHT_BLUE_CONCRETE)).thenReturn(sky);
            bukkit.when(() -> Bukkit.createBlockData(Material.BLACK_CONCRETE)).thenReturn(sky);
            for (final Player player : world.getPlayers())
            {
                bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            }
            body.run();
        }
    }
}
