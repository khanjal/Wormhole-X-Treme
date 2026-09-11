package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
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

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * The sweep that lets a mirror go dark until somebody walks up to it.
 *
 * <p>Two things are worth pinning down here and they pull in opposite directions. It has to
 * send the right thing at the right moment, and it has to send almost nothing the rest of the
 * time -- this runs on a timer for the life of the server, so a packet per player per mirror
 * per tick would be the whole cost of the feature.
 *
 * <p>The blank is the illusion, never the banner. The world's block stays stamped throughout,
 * because banner patterns outlive this plugin and a server that removes it should keep the
 * corridor its operator built. Several of these assert on exactly that.
 */
class MirrorProximityTest
{
    /** Where saves go, so no test writes a mirror file into the repository. */
    @TempDir
    File dataFolder;

    private World world;
    private Block banner;
    private Location bannerAt;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorProximity.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        banner = mock(Block.class);
        bannerAt = new Location(world, 10.0, 64.0, 10.0);
        when(banner.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        // A fresh state per call, because that is what Bukkit does -- getState hands back a
        // copy. Returning one shared mock made the blank and the real banner the same object,
        // so a test could not tell which of the two had been sent.
        when(banner.getState()).thenAnswer(invocation -> mock(Banner.class));
        when(banner.getLocation()).thenReturn(bannerAt);
        when(banner.getWorld()).thenReturn(world);
        when(world.getBlockAt(10, 64, 10)).thenReturn(banner);
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
     * A mirror says what it is when somebody walks into range.
     *
     * <p>The complaint this answers: a stamped banner looks like scenery, and nothing about it
     * says it is a door until somebody happens to right click it -- which players do to signs
     * and not to wall hangings.
     *
     * <p>Run on the most ordinary mirror there is, with no proximity and no dynamic mode,
     * because that is the one the sweep had no other reason to visit and therefore the one an
     * implementation could easily leave out.
     */
    @Test
    void anOrdinaryMirrorNamesItselfWhenSomebodyWalksUp()
    {
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        ordinaryBoundMirror();

        withServer(() ->
        {
            MirrorProximity.tick();
            when(walker.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 10.0));
            MirrorProximity.tick();
        });

        final List<String> said = actionBarOf(walker);
        assertEquals(1, said.size(), "walking up to a mirror should say one thing: " + said);
        assertTrue(said.get(0).contains("museum"), "it should name the mirror: " + said);
        assertTrue(said.get(0).contains("far"), "and say where it goes: " + said);
    }

    /**
     * Standing in front of one is silent.
     *
     * <p>The same rule the packets follow, and for the same reason: this runs on a timer for
     * the life of the server, so a line per sweep would make a player who stopped to look at a
     * mirror unable to read anything else.
     */
    @Test
    void saysNothingMoreToSomebodyWhoIsAlreadyStandingThere()
    {
        final Player standing = playerAt(11.0);
        when(world.getPlayers()).thenReturn(List.of(standing));
        ordinaryBoundMirror();

        tickTwice();

        assertEquals(1, actionBarOf(standing).size(),
            "the second sweep found nobody new, so it should have said nothing");
    }

    /**
     * A mirror that goes nowhere keeps quiet.
     *
     * <p>Announcing one would be the plugin nagging about half-built work in front of everyone
     * who walked past. The click already says what to do about it, to the one person who asked.
     */
    @Test
    void aMirrorThatGoesNowhereSaysNothingToAnybody()
    {
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10), null));

        withServer(() ->
        {
            MirrorProximity.tick();
            when(walker.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 10.0));
            MirrorProximity.tick();
        });

        assertTrue(actionBarOf(walker).isEmpty(),
            "an unpointed mirror has nothing to announce");
    }

    /**
     * Turned off, it is silent and costs nothing.
     *
     * <p>Both halves matter, and the second is why this asserts on the block rather than only
     * on the message. The approach message is the reason the sweep visits an ordinary mirror
     * at all, so switching it off has to put back the older and cheaper behaviour of not
     * looking at one -- not merely build the line and throw it away.
     */
    @Test
    void turningTheApproachMessageOffStopsTheSweepVisitingOrdinaryMirrors()
    {
        ConfigTestSupport.set(
            com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.MIRROR_APPROACH_MESSAGE,
            false);
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        ordinaryBoundMirror();

        withServer(() ->
        {
            MirrorProximity.tick();
            when(walker.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 10.0));
            MirrorProximity.tick();
        });

        assertTrue(actionBarOf(walker).isEmpty(), "switched off means switched off");
        verify(world, never()).getBlockAt(10, 64, 10);
    }

    @Test
    void sendsTheBlankOnceToAPlayerStandingWellAway()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        proximityMirror();

        tickTwice();

        // Once, not once per sweep: a corridor of mirrors with somebody standing still in it
        // must not be a stream of packets. And blanked, which sentTo cannot tell on its own.
        assertWasBlanked(sentTo(far, 1).get(0));
    }

    @Test
    void sendsTheRealBlockWhenThatPlayerWalksUp()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        proximityMirror();

        withServer(() ->
        {
            MirrorProximity.tick();
            when(walker.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 10.0));
            MirrorProximity.tick();
        });

        final List<TileState> sent = sentTo(walker, 2);
        assertWasBlanked(sent.get(0));
        assertWasNotBlanked(sent.get(1));
    }

    @Test
    void sendsNothingToAPlayerWhoHasNotMoved()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        final Player standing = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(standing));
        proximityMirror();

        withServer(() ->
        {
            for (int sweep = 0; sweep < 5; sweep++)
            {
                MirrorProximity.tick();
            }
        });

        assertEquals(1, blockUpdatesTo(standing).size(),
            "standing still should cost one packet in total, not one per sweep");
    }

    @Test
    void leavesAnOrdinaryMirrorAlone()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10), null)));

        tickTwice();

        assertTrue(blockUpdatesTo(far).isEmpty(), "nothing should be sent");
    }

    @Test
    void leavesAMirrorThatHasNeverBeenStampedAlone()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10), null)
            .withDisplay(MirrorDisplay.PROXIMITY));

        tickTwice();

        assertTrue(blockUpdatesTo(far).isEmpty(), "nothing should be sent");
    }

    /**
     * A mirror in an unloaded chunk is not what keeps that chunk resident.
     *
     * <p>The check has to come before {@code getBlockAt}, which would load it. A corridor in a
     * corner of the map nobody has visited would otherwise be pinned in memory by the sweep
     * that exists to make it cheap.
     */
    @Test
    void doesNotTouchABlockInAnUnloadedChunk()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        proximityMirror();

        tickTwice();

        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertTrue(blockUpdatesTo(far).isEmpty(), "nothing should be sent");
    }

    @Test
    void doesNothingForAMirrorWhoseWorldIsNotLoaded()
    {
        proximityMirror();

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            MirrorProximity.tick();
        }

        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void putsEveryIllusionBackWhenThePluginStops()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        proximityMirror();

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorProximity.restoreAll();
        });

        // The blank on the sweep, then the block's own state put back on shutdown -- otherwise
        // a disabled plugin looks exactly like a plugin that ate somebody's banners.
        final List<TileState> sent = sentTo(far, 2);
        assertWasBlanked(sent.get(0));
        assertWasNotBlanked(sent.get(1));
    }

    /**
     * A dynamic mirror re-reads the far side when somebody walks up to it, and not otherwise.
     *
     * <p>The throttle is the point. Sampling loads a distant chunk, so a player pacing in and
     * out of range must not be able to ask for that every second -- and a mirror nobody walks
     * up to must never be sampled at all, however dynamic it is.
     */
    @Test
    void readsTheFarSideAgainWhenSomebodyArrivesAtADynamicMirror()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)))
            .withDisplay(MirrorDisplay.PROXIMITY).withMode(MirrorMode.DYNAMIC));

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);

            MirrorProximity.tick();
            verify(destination, never()).getBlockAt(anyInt(), anyInt(), anyInt());

            walkUpTo(walker);
            MirrorProximity.tick();
        }

        // The far side was read, and what it found replaced the named look it started with.
        verify(destination, atLeastOnce()).getBlockAt(anyInt(), anyInt(), anyInt());
        final MirrorLook look = MirrorManager.byName("museum").look();
        assertNotNull(look.view(), "a dynamic mirror should remember what it saw");
        assertNull(look.presetName(), "and stop wearing the name it was given");
    }

    @Test
    void doesNotReadTheFarSideAgainWithinTheThrottle()
    {
        assumeTrue(MirrorProximity.canHide(),
            "this server has no per-player block update, so nothing here can happen");
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)))
            .withDisplay(MirrorDisplay.PROXIMITY).withMode(MirrorMode.DYNAMIC));

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);

            walkUpTo(walker);
            MirrorProximity.tick();
            final int afterFirst = sampleCount(destination);

            // Out of range and back in, which is a fresh arrival by every measure except the
            // clock -- and the clock is the one that decides.
            when(walker.getLocation()).thenReturn(new Location(world, 200.0, 64.0, 10.0));
            MirrorProximity.tick();
            walkUpTo(walker);
            MirrorProximity.tick();

            assertEquals(afterFirst, sampleCount(destination),
                "the second arrival is inside the throttle, so nothing should be re-read");
        }
    }

    /**
     * Where the API exists, the sweep says so -- and that is what the command line promises.
     *
     * <p>Telling an operator their mirror will go dark is only honest where this is true, so
     * {@code mirror display} asks before it says it.
     */
    @Test
    void saysItCanHideAMirrorOnAServerFrom1201Onwards()
    {
        assumeTrue(hasSendBlockUpdate(), "this jar is older than 1.20.1");

        assertTrue(MirrorProximity.canHide());
    }

    /**
     * Where it does not exist, the sweep does nothing at all and says nothing can.
     *
     * <p>Plain 1.20, the oldest supported server. The documented behaviour there is that a
     * proximity mirror simply stays visible -- the banner is stamped, so it looks like an
     * ordinary mirror rather than like a broken one. This is the row of the matrix that
     * actually runs this test.
     */
    @Test
    void staysQuietOnAServerThatCannotHideABlock()
    {
        assumeFalse(hasSendBlockUpdate(), "this jar has the method, so it is not the 1.20 case");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        proximityMirror();

        tickTwice();

        assertFalse(MirrorProximity.canHide());
        assertTrue(blockUpdatesTo(far).isEmpty(), "nothing should be sent where nothing can be");
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    /** Whether this server's Player has the per-player block update at all. */
    private static boolean hasSendBlockUpdate()
    {
        for (final java.lang.reflect.Method method : Player.class.getMethods())
        {
            if ("sendBlockUpdate".equals(method.getName()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Turning proximity off hands the banner back to whoever was being shown the blank.
     *
     * <p>Without it the sweep simply stops visiting this mirror, and anybody holding the blank
     * keeps it -- so the setting meant to make a banner appear on approach would, switched off,
     * make it vanish for exactly the people standing furthest away.
     */
    @Test
    void handsTheBannerBackWhenAMirrorStopsBeingAProximityOne()
    {
        assumeTrue(MirrorProximity.canHide(), "nothing is ever hidden without the API");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        proximityMirror();

        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorProximity.release(MirrorManager.byName("museum"));
        });

        final List<TileState> sent = sentTo(far, 2);
        assertWasBlanked(sent.get(0));
        assertWasNotBlanked(sent.get(1));
    }

    /**
     * And the command is what has to call it, which is a separate thing to get right.
     *
     * <p>{@code release} doing the right thing is worth nothing if {@code mirror display} does
     * not reach for it -- an earlier version cleared the sweep's memory instead, which is the
     * same bug wearing a tidier name. Driven through the command for that reason.
     */
    @Test
    void handsTheBannerBackWhenTheCommandTurnsProximityOff()
    {
        assumeTrue(MirrorProximity.canHide(), "nothing is ever hidden without the API");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        proximityMirror();
        final Player admin = mock(Player.class);
        when(admin.isOp()).thenReturn(true);

        withServer(() ->
        {
            MirrorProximity.tick();
            new com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand().execute(admin,
                new String[] { "mirror", "display", "museum", "always" });
        });

        final List<TileState> sent = sentTo(far, 2);
        assertWasBlanked(sent.get(0));
        assertWasNotBlanked(sent.get(1));
        assertEquals(MirrorDisplay.ALWAYS, MirrorManager.byName("museum").display());
    }

    /**
     * Removing a proximity mirror hands the banner back too.
     *
     * <p>The same gap as turning the setting off, in the other command that stops the sweep
     * ever visiting a mirror again. It matters more here, because {@code remove} says out loud
     * that the banner is an ordinary banner again -- which would be untrue for exactly the
     * players standing furthest away, who would be left unable to see it at all.
     */
    @Test
    void handsTheBannerBackWhenTheCommandRemovesTheMirror()
    {
        assumeTrue(MirrorProximity.canHide(), "nothing is ever hidden without the API");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        proximityMirror();
        final Player admin = mock(Player.class);
        when(admin.isOp()).thenReturn(true);

        withServer(() ->
        {
            MirrorProximity.tick();
            new com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand().execute(admin,
                new String[] { "mirror", "remove", "museum" });
        });

        final List<TileState> sent = sentTo(far, 2);
        assertWasBlanked(sent.get(0));
        assertWasNotBlanked(sent.get(1));
        assertNull(MirrorManager.byName("museum"));
    }

    /**
     * A player who walked into another world is not sent a block update for this one.
     *
     * <p>A block update names a coordinate, not a world. The tracked set can be several sweeps
     * old, so somebody in it may have gone through a gate since -- and sending then would paint
     * a banner onto whatever happens to stand at those coordinates where they are now.
     */
    @Test
    void doesNotSendAcrossWorldsToSomebodyWhoHasMovedOn()
    {
        assumeTrue(MirrorProximity.canHide(), "nothing is ever sent without the API");
        final Player traveller = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(traveller));
        proximityMirror();

        withServer(() ->
        {
            MirrorProximity.tick();
            // Through a gate, between the sweep that hid it and the restore.
            final World elsewhere = mock(World.class);
            when(traveller.getWorld()).thenReturn(elsewhere);
            MirrorProximity.restoreAll();
        });

        // The blank, and nothing after it: the restore had nowhere safe to send.
        assertEquals(1, blockUpdatesTo(traveller).size(),
            "a cross-world block update would land on an unrelated block");
    }

    /**
     * A destination world that was down does not use up the throttle.
     *
     * <p>The timestamp marks a reading, not an attempt. Recording the attempt would leave a
     * dynamic mirror stale for another whole interval after its world came back -- and an
     * attempt that finds no world costs nothing, because the sampler gives up immediately.
     */
    @Test
    void doesNotSpendTheThrottleOnADestinationWorldThatIsDown()
    {
        assumeTrue(MirrorProximity.canHide(), "the sweep does nothing without the API");
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)))
            .withDisplay(MirrorDisplay.PROXIMITY).withMode(MirrorMode.DYNAMIC));

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);
            // Arrives while the far world is down, so there is nothing to read.
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(null);
            walkUpTo(walker);
            MirrorProximity.tick();
            assertNull(MirrorManager.byName("museum").look().view(),
                "nothing was read, so it still wears the name it was given");

            // The world comes back, and the next arrival should read it rather than wait out
            // an interval it never actually used.
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            when(walker.getLocation()).thenReturn(new Location(world, 200.0, 64.0, 10.0));
            MirrorProximity.tick();
            walkUpTo(walker);
            MirrorProximity.tick();

            assertNotNull(MirrorManager.byName("museum").look().view(),
                "the far side should be read as soon as its world is back");
        }
    }

    /**
     * A mirror that never hides is still kept current, and on a server that cannot hide.
     *
     * <p>The two settings are documented as independent, and for a while they were not: the
     * sweep visited only proximity mirrors and gave up entirely without per-player updates, so
     * {@code always} plus {@code dynamic} never re-read anything and {@code dynamic} did
     * nothing at all on 1.20. Re-reading writes to the banner everybody can see, so it needs
     * no packet and belongs to neither of those conditions.
     *
     * <p>No assumption on this one: the point is that it holds on every version.
     */
    @Test
    void keepsAnAlwaysVisibleMirrorCurrentWithoutHidingAnything()
    {
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)))
            .withMode(MirrorMode.DYNAMIC));

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);

            MirrorProximity.tick();
            walkUpTo(walker);
            MirrorProximity.tick();
        }

        assertNotNull(MirrorManager.byName("museum").look().view(),
            "an always-visible dynamic mirror should still read the far side on approach");
        assertTrue(blockUpdatesTo(walker).isEmpty(),
            "and should hide nothing from anybody while doing it");
    }

    /**
     * A dynamic mirror nobody has stamped can go and find its own first look.
     *
     * <p>{@code mode dynamic} says the mirror re-reads the far side when somebody walks up. If
     * that only held for a mirror already stamped by hand, the message would be describing a
     * different command's work.
     */
    @Test
    void givesAnUnstampedDynamicMirrorItsFirstLook()
    {
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)).withMode(MirrorMode.DYNAMIC));

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);

            walkUpTo(walker);
            MirrorProximity.tick();
        }

        assertNotNull(MirrorManager.byName("museum").look(),
            "walking up to it should be enough to give it a look");
    }

    /**
     * Changing the display setting does not hand a dynamic mirror a fresh re-sample clock.
     *
     * <p>{@code mirror display} releases a mirror that is still there, so if releasing forgot
     * the clock, touching that setting would make the mirror eligible to re-read the far side
     * however recently it had read -- which is not what "at most every so many seconds" says,
     * and would be an accidental way to force a sample by running an unrelated command.
     */
    @Test
    void keepsTheResampleClockWhenOnlyTheDisplaySettingChanges()
    {
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)))
            .withDisplay(MirrorDisplay.PROXIMITY).withMode(MirrorMode.DYNAMIC));

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);

            walkUpTo(walker);
            MirrorProximity.tick();
            final int afterFirst = sampleCount(destination);

            // An unrelated setting changes, and the mirror is released as part of it.
            MirrorProximity.release(MirrorManager.byName("museum"));

            // Leave and come back: a fresh arrival, but inside the interval.
            when(walker.getLocation()).thenReturn(new Location(world, 200.0, 64.0, 10.0));
            MirrorProximity.tick();
            walkUpTo(walker);
            MirrorProximity.tick();

            assertEquals(afterFirst, sampleCount(destination),
                "releasing must not reset the clock -- only removing the mirror does");
        }
    }

    /** A removed mirror leaves no clock behind for whatever is named after it. */
    @Test
    void forgettingAMirrorDropsItsResampleClockToo()
    {
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)))
            .withDisplay(MirrorDisplay.PROXIMITY).withMode(MirrorMode.DYNAMIC));

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);

            walkUpTo(walker);
            MirrorProximity.tick();
            final int afterFirst = sampleCount(destination);

            MirrorProximity.forget(MirrorManager.byName("museum"));

            when(walker.getLocation()).thenReturn(new Location(world, 200.0, 64.0, 10.0));
            MirrorProximity.tick();
            walkUpTo(walker);
            MirrorProximity.tick();

            assertTrue(sampleCount(destination) > afterFirst,
                "a name reused after a removal should not inherit the old mirror's throttle");
        }
    }

    /**
     * And {@code mirror remove} is what has to reach for it, which is its own thing to get right.
     *
     * <p>Driven through the command, because {@code forget} doing the right thing is worth
     * nothing if {@code remove} calls {@code release} instead -- a mutation that survived until
     * this test existed. The observable consequence is a name reused after a removal
     * inheriting the old mirror's throttle and refusing to read its own far side.
     */
    @Test
    void aNameReusedAfterRemovalDoesNotInheritTheOldThrottle()
    {
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        final Player admin = mock(Player.class);
        when(admin.isOp()).thenReturn(true);
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)))
            .withDisplay(MirrorDisplay.PROXIMITY).withMode(MirrorMode.DYNAMIC));

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);

            walkUpTo(walker);
            MirrorProximity.tick();
            final int afterFirst = sampleCount(destination);

            new com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand().execute(admin,
                new String[] { "mirror", "remove", "museum" });

            // Somebody hangs a new banner and gives it the same name.
            MirrorManager.add(stamped(new QuantumMirror("museum",
                new MirrorBlock("world", 10, 64, 10),
                new MirrorPoint("far", 0, 64, 0, 0f, 0f)))
                .withDisplay(MirrorDisplay.PROXIMITY).withMode(MirrorMode.DYNAMIC));

            when(walker.getLocation()).thenReturn(new Location(world, 200.0, 64.0, 10.0));
            MirrorProximity.tick();
            walkUpTo(walker);
            MirrorProximity.tick();

            assertTrue(sampleCount(destination) > afterFirst,
                "the new mirror should read its own far side, not wait out the old one's clock");
        }
    }

    /**
     * Setting a mirror to the display it already has changes nothing at all.
     *
     * <p>Releasing forgets who is currently near, so doing it on the way in would make the
     * next sweep read everybody as a fresh arrival -- a reveal for people who never moved, and
     * a dynamic mirror asked to re-read a far side nobody walked up to. Running a command
     * should not be a way to fake an approach.
     */
    @Test
    void settingTheDisplayItAlreadyHasIsANoOp()
    {
        assumeTrue(MirrorProximity.canHide(), "nothing is tracked without the API");
        final Player far = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(far));
        proximityMirror();
        final Player admin = mock(Player.class);
        when(admin.isOp()).thenReturn(true);

        withServer(() ->
        {
            MirrorProximity.tick();
            new com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand().execute(admin,
                new String[] { "mirror", "display", "museum", "proximity" });
            MirrorProximity.tick();
        });

        // The blank, once. Re-setting the same value must not undo it and send it again.
        assertEquals(1, blockUpdatesTo(far).size(),
            "re-setting the display a mirror already has should send nothing");
    }

    @Test
    void offersATickerToSchedule()
    {
        assertNotNull(MirrorProximity.createTicker());
    }

    /**
     * The states one player was sent, in order.
     *
     * @param player
     *            who was sent them
     * @param howMany
     *            how many are expected, asserted as part of capturing them
     * @return the captured states
     */
    private List<TileState> sentTo(final Player player, final int howMany)
    {
        final List<TileState> sent = blockUpdatesTo(player);
        assertEquals(howMany, sent.size(), "wrong number of block updates sent");
        return sent;
    }

    /**
     * The states a player was sent, read off what was actually invoked.
     *
     * <p>Rather than {@code verify(player).sendBlockUpdate(...)}, which names the method at
     * compile time -- and plain 1.20 has no such method, so naming it would stop this whole
     * class compiling on the oldest row of the matrix. What is being tested is identical on
     * every version; only whether the API exists differs, and {@link MirrorPackets} is what
     * answers that.
     *
     * @param player
     *            whose packets to read
     * @return the states sent, in the order they were sent
     */
    private static List<TileState> blockUpdatesTo(final Player player)
    {
        final List<TileState> sent = new ArrayList<>();
        for (final Invocation invocation : mockingDetails(player).getInvocations())
        {
            if ("sendBlockUpdate".equals(invocation.getMethod().getName()))
            {
                sent.add(invocation.getArgument(1));
            }
        }
        return sent;
    }

    /**
     * Asserts a state had its patterns taken off, which is what the blank is.
     *
     * <p>By what was done to it rather than by what it holds, because a mock Banner holds
     * nothing. {@code blankState} is the only thing in the sweep that calls setPatterns.
     */
    private static void assertWasBlanked(final TileState sent)
    {
        verify((Banner) sent).setPatterns(argThat(List::isEmpty));
    }

    /** Asserts a state was sent exactly as the block had it, patterns untouched. */
    private static void assertWasNotBlanked(final TileState sent)
    {
        verify((Banner) sent, never()).setPatterns(anyList());
    }

    /**
     * A plain mirror that goes somewhere: no proximity, no dynamic, nothing stamped.
     *
     * <p>Deliberately the most ordinary mirror there is. Before the approach message the sweep
     * never visited one of these at all, so it is the case where a mistake would be invisible
     * to every other test in this class.
     */
    private void ordinaryBoundMirror()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)));
    }

    /**
     * Everything said to one player's action bar.
     *
     * <p>Read back off the Spigot handle rather than off the player, because that is where
     * this actually goes -- and a bare mock answers null to {@code spigot()}, which the
     * sending code swallows. A test that forgot this would pass whether or not anything was
     * ever sent.
     *
     * @param player
     *            whose hotbar to read
     * @return what landed there, in order
     */
    private static List<String> actionBarOf(final Player player)
    {
        final ArgumentCaptor<BaseComponent> said = ArgumentCaptor.forClass(BaseComponent.class);
        verify(player.spigot(), atLeast(0))
            .sendMessage(eq(ChatMessageType.ACTION_BAR), said.capture());
        final List<String> lines = new ArrayList<>();
        said.getAllValues().forEach(component -> lines.add(component.toPlainText()));
        return lines;
    }

    /** Two sweeps with nothing moving in between, which is the no-change case. */
    private void tickTwice()
    {
        withServer(() ->
        {
            MirrorProximity.tick();
            MirrorProximity.tick();
        });
    }

    /**
     * Runs something with a server that knows this world and these players.
     *
     * <p>Every path into the sweep goes through {@code Bukkit.getWorld}, so anything calling
     * {@code tick} outside this throws a NullPointerException about a null server rather than
     * testing what it says it tests.
     *
     * @param body
     *            what to run
     */
    private void withServer(final Runnable body)
    {
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            for (final Player player : world.getPlayers())
            {
                bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            }
            body.run();
        }
    }

    /** A proximity mirror on the banner block, already stamped with a named look. */
    private void proximityMirror()
    {
        MirrorManager.add(stamped(new QuantumMirror("museum",
            new MirrorBlock("world", 10, 64, 10), null))
            .withDisplay(MirrorDisplay.PROXIMITY));
    }

    /** The same mirror, wearing a look it was given by name. */
    private static QuantumMirror stamped(final QuantumMirror mirror)
    {
        return mirror.withLook(MirrorLook.named("nether"));
    }

    /** A world on the far side, solid stone all through, with an ordinary floor and ceiling. */
    private static World destinationWorld()
    {
        final World far = mock(World.class);
        when(far.getName()).thenReturn("far");
        when(far.getMinHeight()).thenReturn(-64);
        when(far.getMaxHeight()).thenReturn(320);
        when(far.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
        {
            final Block block = mock(Block.class);
            when(block.getType()).thenReturn(Material.DEEPSLATE);
            return block;
        });
        return far;
    }

    /** How many blocks of a destination world have been read, which is the sampling cost. */
    private static int sampleCount(final World destination)
    {
        return mockingDetails(destination).getInvocations().size();
    }

    /** Moves a player to within arm's reach of the banner. */
    private void walkUpTo(final Player player)
    {
        when(player.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 10.0));
    }

    /** A player standing that far away along x, in the banner's world. */
    private Player playerAt(final double x)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("someone");
        when(player.getLocation()).thenReturn(new Location(world, x, 64.0, 10.0));
        when(player.getWorld()).thenReturn(world);
        // The action bar goes through this handle, and an unstubbed mock answers null -- which
        // the sending code catches and swallows, exactly as it would for a client that will
        // not take one. Stubbed here rather than per test so no test can accidentally assert
        // silence that came from the mock rather than from the sweep.
        when(player.spigot()).thenReturn(mock(Player.Spigot.class));
        return player;
    }
}
