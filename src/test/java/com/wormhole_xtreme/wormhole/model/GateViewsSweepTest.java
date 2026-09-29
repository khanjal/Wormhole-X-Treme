package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.GateWindow;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorCaptures;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows;

/**
 * What each sweep does with an open gate at each level of {@code gate-view} (#516).
 *
 * <p>The horizon is the one thing here a player can see go wrong: cleared with nothing drawn behind
 * it, a gate is an empty ring onto this world; left standing at {@code open}, the view is hidden;
 * put back in the middle of an iris crossing, the crossing's own picture is painted over. The
 * mirror sweep itself is stood in for, so these say only what {@code GateViews} asks of it.
 */
class GateViewsSweepTest
{
    private World world;
    /** A field, not a local: a Location holds its world weakly, and a far world only it held was collected mid-test. */
    private World far;
    private Player player;
    private Stargate gate;
    private MockedStatic<StargateManager> manager;
    private MockedStatic<MirrorWindows> windows;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        ConfigTestSupport.clear();
        GateViews.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        final Block anyBlock = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(anyBlock);
        player = mock(Player.class);
        when(world.getPlayers()).thenReturn(List.of(player));
        near(true);

        far = mock(World.class);
        when(far.getName()).thenReturn("far");
        final Stargate target = mock(Stargate.class);
        when(target.getGateName()).thenReturn("Chulak");
        when(target.getGatePlayerTeleportLocation()).thenReturn(new Location(far, 100.5, 70.0, 200.5, 0.0f, 0.0f));

        gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn("Abydos");
        when(gate.isGateActive()).thenReturn(true);
        when(gate.isGatePortalOpen()).thenReturn(true);
        when(gate.getGateWorld()).thenReturn(world);
        when(gate.getGateTarget()).thenReturn(target);
        when(gate.getGateFacing()).thenReturn(BlockFace.SOUTH);
        when(gate.getEffectivePortalMaterial()).thenReturn(Material.WATER);
        final List<Location> portal = new ArrayList<>();
        for (int x = 10; x < 13; x++)
        {
            for (int y = 64; y < 67; y++)
            {
                portal.add(new Location(world, x, y, 20));
            }
        }
        when(gate.getGatePortalBlocks()).thenReturn(portal);

        manager = mockStatic(StargateManager.class);
        manager.when(StargateManager::getOpenGates).thenReturn(Set.of(gate));
        manager.when(() -> StargateManager.getStargate("Abydos")).thenReturn(gate);
        windows = mockStatic(MirrorWindows.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        windows.close();
        manager.close();
        GateViews.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** Puts the one player in the gate's world beside the gate, or far off. */
    private void near(final boolean beside)
    {
        when(player.getLocation()).thenReturn(new Location(world, 11.0, 64.0, beside ? 25.0 : 400.0));
    }

    /** What the mirror sweep answers when the gate is offered: whether its view is drawn. */
    private void drawn(final boolean drawn)
    {
        windows.when(() -> MirrorWindows.offerGate(any(GateWindow.class), anyBoolean())).thenReturn(drawn);
    }

    private void offeredTimes(final int times)
    {
        windows.verify(() -> MirrorWindows.offerGate(argThat(window -> "gate:Abydos".equals(window.name())), anyBoolean()), times(times));
    }

    @Test
    void atOpenTheHorizonClearsOnceTheViewIsDrawnAndComesBackWhenItStops()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        drawn(true);

        GateViews.offerAll();

        verify(gate).fillGateInterior(Material.AIR);
        assertEquals(Material.AIR, GateViews.horizonOf(gate, Material.WATER), "cleared for the view");

        drawn(false);
        GateViews.offerAll();

        verify(gate).fillGateInterior(Material.WATER);
        assertEquals(Material.WATER, GateViews.horizonOf(gate, Material.WATER), "the view went, so the horizon is back");
    }

    @Test
    void atOpenTheHorizonStaysUntilTheViewIsReady()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        drawn(false);

        GateViews.offerAll();

        offeredTimes(1);
        verify(gate, never()).fillGateInterior(Material.AIR);
        assertEquals(Material.WATER, GateViews.horizonOf(gate, Material.WATER),
            "no capture yet: an empty ring would show this world through the gate");
    }

    @Test
    void behindDrawsTheViewAndKeepsTheHorizon()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        drawn(true);

        GateViews.offerAll();

        offeredTimes(1);
        verify(gate, never()).fillGateInterior(Material.AIR);
        assertEquals(Material.WATER, GateViews.horizonOf(gate, Material.WATER));
    }

    @Test
    void horizonOffersNoGateAtAll()
    {
        drawn(true);

        GateViews.offerAll();

        offeredTimes(0);
    }

    @Test
    void aGateNobodyIsNearIsNotOffered()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        drawn(true);
        near(false);

        GateViews.offerAll();

        offeredTimes(0);
    }

    @Test
    void onlyTheFirstSweepAfterOpeningSaysSo()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        drawn(true);

        GateViews.offerAll();
        GateViews.offerAll();

        windows.verify(() -> MirrorWindows.offerGate(any(GateWindow.class), eq(true)), times(1));
        windows.verify(() -> MirrorWindows.offerGate(any(GateWindow.class), eq(false)), times(1));
    }

    /**
     * A gate whose iris is crossing keeps its view, and its horizon is left as the crossing paints it.
     *
     * <p>The crossing paints the opening a ring at a time and fills it at the end. Putting the
     * horizon back under it, or clearing it, was painted over by the crossing's next step, and the
     * gate ended with the horizon standing over its view. And the view has to stay: a closing
     * crossing at {@code open} paints the rings it has not reached yet as air, and with the view
     * dropped those showed this world through the ring until the iris was shut. The iris flag is
     * already up while it closes, so that is the case built here.
     */
    @Test
    void aClosingIrisKeepsTheViewBehindItAndLeavesTheHorizonAlone() throws ReflectiveOperationException
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        drawn(true);
        GateViews.offerAll();
        verify(gate).fillGateInterior(Material.AIR);

        final Map<String, Integer> running = PrivateStatics.of(StargateIrisAnimator.class, "running");
        running.put("Abydos", 1);
        when(gate.isGateIrisActive()).thenReturn(true);
        try
        {
            GateViews.offerAll();

            offeredTimes(2);
            verify(gate, never()).fillGateInterior(Material.WATER);
            assertEquals(Material.AIR, GateViews.horizonOf(gate, Material.WATER), "still cleared, until the crossing is over");
        }
        finally
        {
            running.remove("Abydos");
        }
        GateViews.offerAll();

        offeredTimes(2);
    }

    /**
     * Switching to {@code horizon} in the middle of a crossing waits for the crossing too.
     *
     * <p>At {@code horizon} no gate is offered, so no gate was known to be crossing either, and
     * every cleared horizon was put back at once, under the crossing that then painted over it.
     */
    @Test
    void switchingToHorizonMidCrossingWaitsForTheCrossing() throws ReflectiveOperationException
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        drawn(true);
        GateViews.offerAll();
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "horizon");

        final Map<String, Integer> running = PrivateStatics.of(StargateIrisAnimator.class, "running");
        running.put("Abydos", 1);
        try
        {
            GateViews.offerAll();

            verify(gate, never()).fillGateInterior(Material.WATER);
        }
        finally
        {
            running.remove("Abydos");
        }
        GateViews.offerAll();

        verify(gate).fillGateInterior(Material.WATER);
    }

    /**
     * Walking out of range and back is not the gate opening again.
     *
     * <p>Only the gates somebody was near were remembered as open, so each return said the gate had
     * just opened, and a capture over five minutes old was retaken every time somebody did.
     */
    @Test
    void walkingBackIntoRangeIsNotTheGateOpeningAgain()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        drawn(true);

        GateViews.offerAll();
        near(false);
        GateViews.offerAll();
        near(true);
        GateViews.offerAll();

        windows.verify(() -> MirrorWindows.offerGate(any(GateWindow.class), eq(true)), times(1));
        windows.verify(() -> MirrorWindows.offerGate(any(GateWindow.class), eq(false)), times(1));
    }

    /**
     * An opening iris is not cleared under, even with the view ready behind it.
     *
     * <p>The crossing reveals the horizon a ring at a time and fills it in at the end. Clearing the
     * horizon while it ran was painted over by its next step, and the gate ended with the horizon
     * standing over its view; the clearing waits for the crossing to finish.
     */
    @Test
    void anOpeningIrisIsNotClearedUnderUntilItIsOpen() throws ReflectiveOperationException
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        drawn(true);
        final Map<String, Integer> running = PrivateStatics.of(StargateIrisAnimator.class, "running");
        running.put("Abydos", 1);
        try
        {
            GateViews.offerAll();

            offeredTimes(1);
            verify(gate, never()).fillGateInterior(Material.AIR);
        }
        finally
        {
            running.remove("Abydos");
        }
        GateViews.offerAll();

        verify(gate).fillGateInterior(Material.AIR);
    }

    /**
     * A gate somebody dials has its capture started then, before its kawoosh.
     *
     * <p>The first sweep after the kawoosh used to be the first ask, which left a remote gate on
     * its horizon for as long as reading the far side off the disk took.
     */
    @Test
    void aDialledGateHasItsCaptureStartedAtOnce()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");

        GateViews.dialled(gate);

        windows.verify(() -> MirrorWindows.prepareGate(argThat(window -> "gate:Abydos".equals(window.name())
            && "Chulak".equals(window.target()) && (window.depth() == 32))), times(1));
    }

    @Test
    void aDialledGateIsLeftAloneAtHorizonOrWithNobodyNear()
    {
        GateViews.dialled(gate);
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        near(false);
        GateViews.dialled(gate);

        windows.verify(() -> MirrorWindows.prepareGate(any(GateWindow.class)), never());
    }

    /**
     * A gate somebody is standing at has its captures refreshed, and each gate is looked at once a minute at most.
     *
     * <p>Its chunks are mostly loaded anyway, so this is the cheap time to keep the base current;
     * looking every sweep would list its captures on disk every second, and asking every gate who
     * was near it every sweep walked the players once a gate.
     */
    @Test
    void aGateSomebodyIsAtHasItsCapturesRefreshedOnceAMinute()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        when(gate.getGatePlayerTeleportLocation()).thenReturn(new Location(world, 11.5, 64.0, 21.5, 0.0f, 0.0f));
        manager.when(StargateManager::getAllGatesUnsorted).thenReturn(List.of(gate));
        try (MockedStatic<MirrorCaptures> captures = mockStatic(MirrorCaptures.class))
        {
            captures.when(() -> MirrorCaptures.gateFillDepth(any(MirrorPoint.class), anyInt())).thenReturn(160);
            GateViews.refreshWatched(1_000_000L);
            // Within the minute: not looked at again.
            GateViews.refreshWatched(1_030_000L);
            near(false);
            // Nobody there, twice a minute apart: the gate's chunks may not be loaded, so nothing.
            GateViews.refreshWatched(1_070_000L);
            GateViews.refreshWatched(1_140_000L);
            near(true);
            // Within a minute of the last look, somebody there or not.
            GateViews.refreshWatched(1_150_000L);
            GateViews.refreshWatched(1_210_000L);

            // To the full depth, which gateFillDepth decides: somebody there means most of the fill's chunks are loaded.
            captures.verify(() -> MirrorCaptures.refreshGate(eq("Abydos"), any(MirrorPoint.class), eq(160),
                eq(GateViews.REFRESH_SECONDS)), times(2));
        }
    }

    /**
     * Removing a gate forgets its view and deletes what it shows.
     *
     * <p>Named for the gate they show, a removed gate's captures otherwise stayed for good, and a gate
     * built again under the name drew the old place until its first retake.
     */
    @Test
    void removingAGateDeletesWhatItShows()
    {
        try (MockedStatic<MirrorCaptures> captures = mockStatic(MirrorCaptures.class))
        {
            GateViews.removed(gate);

            captures.verify(() -> MirrorCaptures.forgetGate("Abydos"), times(1));
            windows.verify(() -> MirrorWindows.release("gate:Abydos"), times(1));
        }
    }
}
