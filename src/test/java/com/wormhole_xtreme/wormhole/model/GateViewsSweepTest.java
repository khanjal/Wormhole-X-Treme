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
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.Windows;

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
    private MockedStatic<Windows> windows;

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
        opening(10, 3, 64, 3);

        manager = mockStatic(StargateManager.class);
        manager.when(StargateManager::getOpenGates).thenReturn(Set.of(gate));
        manager.when(() -> StargateManager.getStargate("Abydos")).thenReturn(gate);
        windows = mockStatic(Windows.class);
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

    /**
     * Gives the gate a rectangular opening in the plane z = 20 and a frame round it.
     *
     * @return the opening's cells
     */
    private List<Location> opening(final int fromX, final int wide, final int fromY, final int tall)
    {
        final List<Location> portal = new ArrayList<>();
        for (int y = (fromY + tall) - 1; y >= fromY; y--)
        {
            for (int x = fromX; x < (fromX + wide); x++)
            {
                portal.add(new Location(world, x, y, 20));
            }
        }
        final List<Location> ring = new ArrayList<>();
        for (int x = fromX - 1; x <= (fromX + wide); x++)
        {
            ring.add(new Location(world, x, fromY - 1, 20));
            ring.add(new Location(world, x, fromY + tall, 20));
        }
        for (int y = fromY; y < (fromY + tall); y++)
        {
            ring.add(new Location(world, fromX - 1, y, 20));
            ring.add(new Location(world, fromX + wide, y, 20));
        }
        when(gate.getGatePortalBlocks()).thenReturn(portal);
        when(gate.getGateStructureBlocks()).thenReturn(ring);
        return portal;
    }

    /** Puts the one player in the gate's world beside the gate, or far off. */
    private void near(final boolean beside)
    {
        when(player.getLocation()).thenReturn(new Location(world, 11.0, 64.0, beside ? 25.0 : 400.0));
    }

    /** What the mirror sweep answers when the gate is offered: whether its view is drawn. */
    private void drawn(final boolean drawn)
    {
        windows.when(() -> Windows.offerGate(any(GateWindow.class), anyBoolean())).thenReturn(drawn);
    }

    private void offeredTimes(final int times)
    {
        windows.verify(() -> Windows.offerGate(argThat(window -> "gate:Abydos".equals(window.name())), anyBoolean()), times(times));
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

    /**
     * A Grand gate's opening, eighteen by seventeen, is drawn whole and cleared whole at {@code open}.
     *
     * <p>It used to be drawn through an eight-by-eight window carved at the foot of its middle, the
     * rest of the opening keeping its horizon, because a capture through a bigger hole spread its
     * rays too thin to trust. The rays are bounded and staggered now, and the whole opening is the window.
     */
    @Test
    void atOpenAGrandGateClearsItsWholeOpening()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        opening(10, 18, 64, 17);
        drawn(true);

        GateViews.offerAll();

        windows.verify(() -> Windows.offerGate(argThat(window -> (window.open().size() == (18 * 17))
            && (window.shape().width() == 18) && (window.shape().height() == 17)), anyBoolean()));
        verify(gate).fillGateInterior(Material.AIR);
        assertEquals(Material.AIR, GateViews.horizonOf(gate, Material.WATER), "the whole opening, not a window in it");
    }

    /**
     * A tall gate's view is measured from the middle of its opening, not its first cell.
     *
     * <p>The drawing draws a window for whoever is within the mirror proximity distance of the block
     * it is anchored to. Anchored to the first portal cell, a top-row one, a Massive gate's view was
     * never drawn for somebody six blocks in front of it at the foot: the facility saw it, with the
     * window offered and nobody looking into it. A carved window had hidden this, being anchored to
     * its own first cell, eight up.
     */
    @Test
    void aTallGatesViewIsAnchoredToTheMiddleOfItsOpening()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        opening(10, 17, 64, 17);
        drawn(false);

        GateViews.offerAll();

        offeredTimes(1);
        verify(world).getBlockAt(18, 72, 20);
        verify(world, never()).getBlockAt(10, 80, 20);
    }

    /**
     * A Minimal gate keeps its horizon: two portal cells on one frame block, open to the air beside
     * and above.
     *
     * <p>Nothing but the ring hides a view's edges as one walks round a freestanding gate, so with no
     * ring the far side would hang in the air beside it.
     */
    @Test
    void aGateWithNoFrameRoundItsOpeningKeepsItsHorizon()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        when(gate.getGatePortalBlocks()).thenReturn(List.of(new Location(world, 11, 65, 20), new Location(world, 11, 64, 20)));
        when(gate.getGateStructureBlocks()).thenReturn(List.of(new Location(world, 11, 63, 20)));
        drawn(true);

        GateViews.offerAll();
        GateViews.dialled(gate);

        offeredTimes(0);
        windows.verify(() -> Windows.prepareGate(any(GateWindow.class)), never());
        verify(gate, never()).fillGateInterior(Material.AIR);
    }

    /**
     * A gate is watched only by somebody the drawing would draw its view for: near the middle of its
     * opening, not merely near some part of it.
     *
     * <p>Measured to the nearest cell, somebody fourteen blocks in front of a Massive gate's foot and
     * off to one side was eight from the opening and twenty from its middle, where the drawing
     * measures from: the capture, seconds of work in the far world, was started for a view nobody
     * was drawn.
     */
    @Test
    void somebodyNearATallGatesFootButFarFromItsMiddleDoesNotWatchIt()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        opening(10, 17, 64, 17);
        // Middle (18, 72, 20): 16.9 off, just past the 16, and only counting all three ways at once.
        // The opening's nearest cell (10, 64, 20): about 11.
        when(player.getLocation()).thenReturn(new Location(world, 8.0, 64.0, 31.0));
        drawn(false);

        GateViews.offerAll();
        GateViews.dialled(gate);

        offeredTimes(0);
        windows.verify(() -> Windows.prepareGate(any(GateWindow.class)), never());
    }

    /** Somebody six blocks in front of a tall gate's foot is ten from its middle, and watches it. */
    @Test
    void somebodyAtATallGatesFootWatchesIt()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        opening(10, 17, 64, 17);
        when(player.getLocation()).thenReturn(new Location(world, 18.0, 64.0, 26.0));
        drawn(false);

        GateViews.offerAll();

        offeredTimes(1);
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

        windows.verify(() -> Windows.offerGate(any(GateWindow.class), eq(true)), times(1));
        windows.verify(() -> Windows.offerGate(any(GateWindow.class), eq(false)), times(1));
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

        windows.verify(() -> Windows.offerGate(any(GateWindow.class), eq(true)), times(1));
        windows.verify(() -> Windows.offerGate(any(GateWindow.class), eq(false)), times(1));
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

        windows.verify(() -> Windows.prepareGate(argThat(window -> "gate:Abydos".equals(window.name())
            && "Chulak".equals(window.target()) && (window.depth() == 32))), times(1));
    }

    @Test
    void aDialledGateIsLeftAloneAtHorizonOrWithNobodyNear()
    {
        GateViews.dialled(gate);
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        near(false);
        GateViews.dialled(gate);

        windows.verify(() -> Windows.prepareGate(any(GateWindow.class)), never());
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
        try (MockedStatic<Captures> captures = mockStatic(Captures.class))
        {
            captures.when(() -> Captures.gateFillDepth(any(Place.class), anyInt())).thenReturn(160);
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
            captures.verify(() -> Captures.refreshGate(eq("Abydos"), any(Place.class), eq(160),
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
        try (MockedStatic<Captures> captures = mockStatic(Captures.class))
        {
            GateViews.removed(gate);

            captures.verify(() -> Captures.forgetGate("Abydos"), times(1));
            windows.verify(() -> Windows.release("gate:Abydos"), times(1));
        }
    }
}
