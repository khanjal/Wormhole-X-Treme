package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.HeldScheduler;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Who has a ring preview standing, what keeps it away from a real cycle, and every way it comes
 * down (#332).
 *
 * <p>A preview left standing is a wall only its viewer can see, so each reason for it to end is
 * its own test, and each checks the same thing at the end: that of everything the viewer was
 * sent, the last block at every position is the real one. That is checked against what the
 * <em>client</em> would be showing, built from the block changes in the order they were sent,
 * because a clean-up that sends the real block and then something else over it is a stack left
 * standing that a call count would call clean.
 *
 * <p>The drawing is the real {@link PreviewSurroundings}, over mocks of the world and the
 * players, so "another player sees nothing" is checked on the real send path.
 */
class RingPreviewsTest
{
    private static final String WORLD_NAME = "world";
    private static final int AX = 100, AY = 64, AZ = 100;

    private final HeldScheduler held = new HeldScheduler();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ConfigManager> config;
    private World world;
    private Player viewer;
    private Player bystander;
    private BlockData drawn;
    private BlockData real;
    private RingPair pair;
    private Ring endA;
    private Ring endB;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        held.install();
        RingManager.clear();
        RingPreviews.clearAll();

        drawn = mock(BlockData.class);
        real = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenReturn(drawn);
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingDeployTicks).thenReturn(Integer.valueOf(3));
        config.when(ConfigManager::getRingFlashTicks).thenReturn(Integer.valueOf(2));
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(16));
        config.when(ConfigManager::getRingSoundRing).thenReturn("");
        config.when(ConfigManager::getRingSoundOpen).thenReturn("");
        config.when(ConfigManager::getRingSoundFlash).thenReturn("");

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD_NAME);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final Block block = mock(Block.class);
            when(block.isPassable()).thenReturn(Boolean.valueOf(call.getArgument(1, Integer.class) >= AY));
            when(block.getBlockData()).thenReturn(real);
            return block;
        });

        viewer = playerInTheWorld();
        bystander = playerInTheWorld();
        when(world.getPlayers()).thenReturn(List.of(viewer, bystander));

        endA = ring(AX, AY, AZ);
        endB = ring(200, AY, 200);
        pair = new RingPair("aaaa0001", WORLD_NAME, endA, endB);
        RingManager.addPair(pair, 4);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        RingPreviews.clearAll();
        RingManager.clear();
        config.close();
        bukkit.close();
        held.remove();
        PluginTestSupport.remove();
    }

    private Player playerInTheWorld()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(Boolean.TRUE);
        return player;
    }

    private static Ring ring(final int x, final int y, final int z)
    {
        final Ring made = new Ring(x, y, z, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        made.setStyle(RingStyle.CONCURRENT);
        return made;
    }

    /**
     * Lets the animation run until the viewer's preview reaches a state.
     *
     * <p>Only the animation: the timeout is also waiting, it is the older of the two, and
     * letting it run would clear the preview this is waiting on.
     */
    private void settle(final RingPreview.State wanted)
    {
        for (int guard = 0; (RingPreviews.of(viewer).getState() != wanted) && (guard < 1000); guard++)
        {
            held.runNextOtherThan(RingPreviews.TIMEOUT_TICKS);
        }
        assertEquals(wanted, RingPreviews.of(viewer).getState());
    }

    /** What the viewer's client is showing, from the block changes in the order they were sent. */
    private Map<String, BlockData> onScreen(final Player who)
    {
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(who, atLeastOnce()).sendBlockChange(where.capture(), what.capture());
        final Map<String, BlockData> screen = new HashMap<>();
        for (int i = 0; i < where.getAllValues().size(); i++)
        {
            final Location at = where.getAllValues().get(i);
            screen.put(at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ(), what.getAllValues().get(i));
        }
        return screen;
    }

    private void assertNothingOfTheDrawingRemains(final Player who)
    {
        final Map<String, BlockData> screen = onScreen(who);
        assertFalse(screen.isEmpty(), "something was drawn, or this proves nothing");
        for (final Map.Entry<String, BlockData> entry : screen.entrySet())
        {
            assertSame(real, entry.getValue(), "the last thing sent at " + entry.getKey() + " is the real block");
        }
    }

    /** The pad is lit for the viewer, and the pair carries on as if nothing had happened. */
    @Test
    void lightingThePadShowsItToTheViewerAloneAndLeavesThePairIdle()
    {
        assertEquals(RingPreview.Result.DONE, RingPreviews.show(viewer, pair, endA));

        assertSame(endA, RingPreviews.of(viewer).getRing());
        assertTrue(onScreen(viewer).values().stream().allMatch(data -> data == drawn));
        verify(bystander, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        assertEquals(RingPhase.IDLE, pair.getPhase(), "the pair stays willing to fire for anybody else");
        assertEquals(0L, pair.getCooldownUntil(), "and no cooldown was started");
    }

    /** The stack, the light and the retract all go to the viewer and to nobody else. */
    @Test
    void everyStepIsDrawnForTheViewerAlone()
    {
        RingPreviews.deploy(viewer, pair, endA);
        settle(RingPreview.State.DEPLOYED);
        assertEquals(RingPreview.Result.DONE, RingPreviews.light(viewer));
        settle(RingPreview.State.DEPLOYED);
        assertEquals(RingPreview.Result.DONE, RingPreviews.collapse(viewer));
        settle(RingPreview.State.LIT);

        assertFalse(onScreen(viewer).isEmpty(), "the viewer was sent the whole show");
        verify(bystander, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        assertEquals(RingPhase.IDLE, pair.getPhase());
    }

    /** Deploy lights the pad first when nothing has, so one command is enough. */
    @Test
    void deployFromNothingLightsThePadAndRaisesTheStack()
    {
        assertNull(RingPreviews.of(viewer));

        assertEquals(RingPreview.Result.DONE, RingPreviews.deploy(viewer, pair, endA));

        assertEquals(RingPreview.State.DEPLOYING, RingPreviews.of(viewer).getState());
    }

    /** Light and collapse act on a stack that is up, and say so when there is none. */
    @Test
    void lightAndCollapseWithNoPreviewHaveNoStack()
    {
        assertEquals(RingPreview.Result.NO_STACK, RingPreviews.light(viewer));
        assertEquals(RingPreview.Result.NO_STACK, RingPreviews.collapse(viewer));
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /**
     * A preview is refused on a pair that is running, so the two drawings never fight over the
     * same blocks.
     */
    @Test
    void aPairThatIsMidCycleIsRefusedAndNothingIsDrawn()
    {
        pair.setPhase(RingPhase.COUNTDOWN);

        assertEquals(RingPreview.Result.MID_CYCLE, RingPreviews.show(viewer, pair, endA));
        assertEquals(RingPreview.Result.MID_CYCLE, RingPreviews.deploy(viewer, pair, endA));

        assertNull(RingPreviews.of(viewer));
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        assertEquals(0, held.pending(), "not even a timeout was started");
    }

    /** A preview already up when a cycle starts is not allowed to carry on drawing. */
    @Test
    void aStandingPreviewIsRefusedItsNextStepOnceTheCycleHasStarted()
    {
        RingPreviews.show(viewer, pair, endA);
        pair.setPhase(RingPhase.DEPLOY);

        assertEquals(RingPreview.Result.MID_CYCLE, RingPreviews.light(viewer));
        assertEquals(RingPreview.Result.MID_CYCLE, RingPreviews.collapse(viewer));
    }

    /** Lighting what is already lit says so, and costs nothing but a fresh timeout. */
    @Test
    void lightingTheSamePadTwiceSaysItIsAlreadyShowing()
    {
        RingPreviews.show(viewer, pair, endA);

        assertEquals(RingPreview.Result.ALREADY_SHOWING, RingPreviews.show(viewer, pair, endA));

        assertEquals(1, held.pending(), "one timeout, the old one having been cancelled");
        assertEquals(1, held.cancelled());
    }

    /** One preview per player: starting another end takes the first back. */
    @Test
    void startingAnotherEndTakesTheFirstOneBack()
    {
        RingPreviews.show(viewer, pair, endA);
        final RingPreview first = RingPreviews.of(viewer);

        assertEquals(RingPreview.Result.DONE, RingPreviews.show(viewer, pair, endB));

        assertSame(endB, RingPreviews.of(viewer).getRing());
        assertTrue(first != RingPreviews.of(viewer));
        // End A's pad is back to real, end B's is lit.
        final Map<String, BlockData> screen = onScreen(viewer);
        for (final int[] block : RingAnimator.lightBlocks(endA))
        {
            assertSame(real, screen.get(block[0] + "," + block[1] + "," + block[2]), "end A's pad was taken back");
        }
        for (final int[] block : RingAnimator.lightBlocks(endB))
        {
            assertSame(drawn, screen.get(block[0] + "," + block[1] + "," + block[2]), "end B's pad is lit");
        }
        assertEquals(1, held.pending(), "one timeout, for the one preview");
    }

    /** -clear: everything back, and a second one finds nothing to take. */
    @Test
    void clearingTakesItBackAndThereIsNothingTheSecondTime()
    {
        RingPreviews.deploy(viewer, pair, endA);

        assertTrue(RingPreviews.clear(viewer));

        assertNull(RingPreviews.of(viewer));
        assertNothingOfTheDrawingRemains(viewer);
        assertEquals(0, held.pending(), "neither the animation nor the timeout is left queued");
        assertFalse(RingPreviews.clear(viewer));
    }

    /** Quitting takes the preview back and cancels what it had queued. */
    @Test
    void quittingClearsThePreviewMidRise()
    {
        RingPreviews.deploy(viewer, pair, endA);
        final PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(viewer);

        new RingPreviewListener().onQuit(quit);

        assertNull(RingPreviews.of(viewer));
        assertNothingOfTheDrawingRemains(viewer);
        assertEquals(0, held.pending());
    }

    /** Changing world takes the preview back too. */
    @Test
    void changingWorldClearsThePreview()
    {
        RingPreviews.deploy(viewer, pair, endA);
        final PlayerChangedWorldEvent moved = mock(PlayerChangedWorldEvent.class);
        when(moved.getPlayer()).thenReturn(viewer);

        new RingPreviewListener().onChangedWorld(moved);

        assertNull(RingPreviews.of(viewer));
        assertNothingOfTheDrawingRemains(viewer);
        assertEquals(0, held.pending());
    }

    /** Removing the pair takes everybody's preview of it back, and nobody else's. */
    @Test
    void removingThePairClearsEveryPreviewOfItAndNoOthers()
    {
        final Ring otherEnd = ring(300, AY, 300);
        final RingPair other = new RingPair("bbbb0002", WORLD_NAME, otherEnd, ring(400, AY, 400));
        RingPreviews.show(viewer, pair, endA);
        RingPreviews.show(bystander, other, otherEnd);

        RingManager.removePair(pair, 4);

        assertNull(RingPreviews.of(viewer), "the removed pair's preview is gone");
        assertNothingOfTheDrawingRemains(viewer);
        assertNotNull(RingPreviews.of(bystander), "a different pair's preview is not touched");
    }

    /** Both ends of the pair, for every viewer. */
    @Test
    void clearingForAPairTakesBackEveryViewersEnd()
    {
        RingPreviews.show(viewer, pair, endA);
        RingPreviews.show(bystander, pair, endB);

        RingPreviews.clearFor(pair);

        assertNull(RingPreviews.of(viewer));
        assertNull(RingPreviews.of(bystander));
        assertEquals(0, held.pending());
    }

    /** A preview nobody touches comes down on its own. */
    @Test
    void aPreviewThatIsLeftStandingTimesOut()
    {
        RingPreviews.deploy(viewer, pair, endA);
        settle(RingPreview.State.DEPLOYED);
        assertEquals(List.of(RingPreviews.TIMEOUT_TICKS), held.delays(), "only the timeout is left, and it is the fixed one");

        held.runNext();

        assertNull(RingPreviews.of(viewer));
        assertNothingOfTheDrawingRemains(viewer);
        assertEquals(0, held.pending());
    }

    /** Any preview command starts the timeout again, so one in use is not taken from under its viewer. */
    @Test
    void everyPreviewCommandStartsTheTimeoutAgain()
    {
        RingPreviews.deploy(viewer, pair, endA);
        settle(RingPreview.State.DEPLOYED);
        final int cancelledBefore = held.cancelled();

        RingPreviews.light(viewer);

        assertEquals(cancelledBefore + 1, held.cancelled(), "the old timeout was cancelled");
        assertTrue(held.delays().contains(RingPreviews.TIMEOUT_TICKS), "and a new one started");
        assertEquals(1, held.delays().stream().filter(d -> d == RingPreviews.TIMEOUT_TICKS).count(),
            "exactly one timeout is waiting");
    }

    /** Raising the stack of another end takes the first end's preview back, as lighting it does. */
    @Test
    void deployingAnotherEndReplacesThePreviewOfTheFirst()
    {
        RingPreviews.show(viewer, pair, endA);

        assertEquals(RingPreview.Result.DONE, RingPreviews.deploy(viewer, pair, endB));

        assertSame(endB, RingPreviews.of(viewer).getRing(), "the stack is raised at the end asked for");
        assertEquals(RingPreview.State.DEPLOYING, RingPreviews.of(viewer).getState());
        final int[] padA = RingAnimator.lightBlocks(endA).get(0);
        assertSame(real, onScreen(viewer).get(padA[0] + "," + padA[1] + "," + padA[2]),
            "end A's pad was handed back");
    }

    /** The plugin registers the listener that clears a preview on quit and on a change of world. */
    @Test
    void thePluginRegistersTheListener() throws Exception
    {
        final PluginManager manager = mock(PluginManager.class);
        final Server server = mock(Server.class);
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        // Swapped for this one: the plugin the setup installed knows no server. Removed first so
        // that the teardown's remove() puts back what was there before the setup, not the setup's.
        PluginTestSupport.remove();
        PluginTestSupport.install(plugin);

        WormholeXTreme.registerEvents();

        final ArgumentCaptor<Listener> registered = ArgumentCaptor.forClass(Listener.class);
        verify(manager, atLeastOnce()).registerEvents(registered.capture(), eq(plugin));
        assertTrue(registered.getAllValues().stream().anyMatch(RingPreviewListener.class::isInstance),
            "a RingPreviewListener among " + registered.getAllValues().size() + " registered");
    }

    /**
     * Shutting down takes the previews back.
     *
     * <p>A scan of the source, since {@code onDisable} needs a whole server: the call has to be
     * between the two markers {@code ShutdownCatchesErrorsTest} uses for the shutdown path, and
     * the path has to be a real one or this would pass against an empty string.
     */
    @Test
    void onDisableClearsTheRingPreviews() throws IOException
    {
        final String text = Files.readString(
            Paths.get("src/main/java/com/wormhole_xtreme/wormhole/WormholeXTreme.java"), StandardCharsets.UTF_8);
        final int from = text.indexOf("public void onDisable()");
        final int to = text.indexOf("public void onEnable()");
        assertTrue((from > 0) && (to > from), "onDisable and onEnable are where the scan expects them");
        final String path = text.substring(from, to);
        assertTrue(path.length() > 500, "a real shutdown path, not an empty region");

        final int disable = path.indexOf("clearRingPreviews();");
        assertTrue(disable > 0, "onDisable calls the step that takes the ring previews back");
        assertTrue(disable < path.indexOf("private void saveRings()"), "and before the saves it must not cost");
        assertTrue(path.contains("RingPreviews.clearAll();"), "which clears them all");
    }

    /**
     * A pair named from another world is refused, and nothing is drawn, measured or written.
     *
     * <p>A drawing is sent by position, so from the nether it would land on unrelated terrain,
     * and the deploy's survey would read the wrong world and record a wrong drop on the ring.
     */
    @Test
    void anEndInAnotherWorldIsRefusedAndNothingIsDrawnOrMeasured()
    {
        final World nether = mock(World.class);
        when(nether.getName()).thenReturn("nether");
        when(viewer.getWorld()).thenReturn(nether);
        final Ring hanging = new Ring(AX, AY, AZ, RingPattern.ODD, RingOrientation.CEILING, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final RingPair high = new RingPair("cccc0003", WORLD_NAME, hanging, ring(300, AY, 300));

        assertEquals(RingPreview.Result.WRONG_WORLD, RingPreviews.show(viewer, high, hanging));
        assertEquals(RingPreview.Result.WRONG_WORLD, RingPreviews.deploy(viewer, high, hanging));

        assertNull(RingPreviews.of(viewer));
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        verify(nether, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertEquals(Ring.MIN_CEILING_DROP, hanging.getDrop(), "nothing was written to the stored ring");
        assertEquals(0, held.pending());
    }

    /** An end whose chunk is not loaded is refused without loading it. */
    @Test
    void anEndInAnUnloadedChunkIsRefusedWithoutLoadingIt()
    {
        when(world.isChunkLoaded(AX >> 4, AZ >> 4)).thenReturn(Boolean.FALSE);

        assertEquals(RingPreview.Result.NOT_LOADED, RingPreviews.show(viewer, pair, endA));
        assertEquals(RingPreview.Result.NOT_LOADED, RingPreviews.deploy(viewer, pair, endA));

        assertNull(RingPreviews.of(viewer));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        assertEquals(RingPreview.Result.DONE, RingPreviews.show(viewer, pair, endB), "the far end is loaded");
    }

    /**
     * A step that throws takes the preview back and out of the registry, so the player can start
     * another; it also leaves no timeout behind.
     */
    @Test
    void aStepThatThrowsLeavesNoRegistryEntryAndPreviewWorksAgain()
    {
        RingPreviews.deploy(viewer, pair, endA);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenThrow(new IllegalStateException("boom"));

        held.runNextOtherThan(RingPreviews.TIMEOUT_TICKS);

        assertNull(RingPreviews.of(viewer), "the failed preview is not left registered");
        assertEquals(0, held.pending(), "neither a step nor the timeout is left queued");
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenReturn(drawn);
        assertEquals(RingPreview.Result.DONE, RingPreviews.show(viewer, pair, endA), "and a new preview starts");
        assertNotNull(RingPreviews.of(viewer));
    }

    /** Disabling the plugin takes back every viewer's preview. */
    @Test
    void disablingThePluginClearsEveryPreview()
    {
        RingPreviews.deploy(viewer, pair, endA);
        RingPreviews.show(bystander, pair, endB);

        RingPreviews.clearAll();

        assertNull(RingPreviews.of(viewer));
        assertNull(RingPreviews.of(bystander));
        assertNothingOfTheDrawingRemains(viewer);
        assertNothingOfTheDrawingRemains(bystander);
        assertEquals(0, held.pending());
    }
}
