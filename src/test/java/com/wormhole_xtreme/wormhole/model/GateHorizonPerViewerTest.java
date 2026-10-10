package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
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
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.window.Windows;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * At {@code gate-view: open} a gate's horizon clears only for whoever is drawn its view (#516).
 *
 * <p>It used to clear for everybody: anybody behind the gate, too far off to be drawn the view, or
 * with no clear line to it saw an empty ring onto this world where the horizon should be. The
 * opening is a drawing either way, the server keeping air in it, so what each player is sent is
 * the whole of it. The window drawing itself is stood in for: {@link GateViews#drawn} is what it
 * tells after each redraw.
 */
class GateHorizonPerViewerTest
{
    private static final String WINDOW = "gate:Abydos";

    /** A Standard-sized opening's nine cells here, three by three. */
    private static final int CELLS = 9;

    private World world;
    /** A field, not a local: a Location holds its world weakly, and a far world only it held was collected mid-test. */
    private World far;
    private Player front;
    private Player behind;
    private Stargate gate;
    private final BlockData water = mock(BlockData.class);
    private final BlockData air = mock(BlockData.class);
    private MockedStatic<StargateManager> manager;
    private MockedStatic<Windows> windows;
    private MockedStatic<GateSource> sources;
    private MockedStatic<MaterialUtils> materials;

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
        // The gate faces south, its opening in the plane z = 20: in front is z > 20.
        front = playerAt(25.0);
        behind = playerAt(15.0);
        when(world.getPlayers()).thenReturn(List.of(front, behind));

        far = mock(World.class);
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
        opening();

        manager = mockStatic(StargateManager.class);
        manager.when(StargateManager::getOpenGates).thenReturn(Set.of(gate));
        manager.when(() -> StargateManager.getStargate("Abydos")).thenReturn(gate);
        windows = mockStatic(Windows.class);
        sources = mockStatic(GateSource.class);
        sources.when(() -> GateSource.offer(any(GateSource.class), anyBoolean())).thenReturn(true);
        materials = mockStatic(MaterialUtils.class);
        materials.when(() -> MaterialUtils.drawnAcross(Material.WATER, BlockFace.SOUTH)).thenReturn(water);
        materials.when(() -> MaterialUtils.drawnAcross(Material.AIR, BlockFace.SOUTH)).thenReturn(air);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        materials.close();
        sources.close();
        windows.close();
        manager.close();
        GateViews.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private Player playerAt(final double z)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 11.0, 64.0, z));
        return player;
    }

    /** Gives the gate a three by three opening in the plane z = 20 and a frame round it. */
    private void opening()
    {
        opening(gate, 10);
    }

    /** Gives a gate a three by three opening from {@code fromX} in the plane z = 20 and a frame round it. */
    private void opening(final Stargate which, final int fromX)
    {
        final List<Location> portal = new ArrayList<>();
        final List<Location> ring = new ArrayList<>();
        for (int x = fromX - 1; x <= (fromX + 3); x++)
        {
            for (int y = 63; y <= 67; y++)
            {
                final boolean inside = (x >= fromX) && (x <= (fromX + 2)) && (y >= 64) && (y <= 66);
                (inside ? portal : ring).add(new Location(world, x, y, 20));
            }
        }
        when(which.getGatePortalBlocks()).thenReturn(portal);
        when(which.getGateStructureBlocks()).thenReturn(ring);
    }

    /** One sweep at {@code open}, which clears the horizon: the view is drawn. */
    private void clearedForTheView()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        GateViews.offerAll();
        assertEquals(Material.AIR, GateViews.horizonOf(gate, Material.WATER), "the horizon cleared for the view");
    }

    /** The drawing's word that this player is drawn the gate's view after a redraw, or is not. */
    private static void drawsTheView(final Player player, final boolean drawn)
    {
        GateViews.drawn(player.getUniqueId(), player, drawn ? Set.of(WINDOW) : Set.of());
    }

    /** The drawing's word that this player is drawn these windows. */
    private static void drawsTheView(final Player player, final Set<String> windows)
    {
        GateViews.drawn(player.getUniqueId(), player, windows);
    }

    /** How many of the opening's cells a player was sent as this. */
    private static void sentTheOpeningAs(final Player player, final BlockData data, final int times, final String why)
    {
        verify(player, times(times * CELLS).description(why)).sendBlockChange(any(Location.class), eq(data));
    }

    @Test
    void aViewerDrawnTheViewSeesThroughTheOpeningAndOneBehindTheGateSeesTheHorizon()
    {
        clearedForTheView();
        drawsTheView(front, true);
        drawsTheView(behind, false);

        sentTheOpeningAs(front, air, 1, "drawn the view: the opening is cleared for them");

        StargateBlockSetup.refreshPortalVisuals(behind);
        StargateBlockSetup.refreshPortalVisuals(front);

        sentTheOpeningAs(behind, water, 1, "behind the gate: the horizon, not an empty ring onto this world");
        verify(behind, never().description("nobody behind the gate is sent the cleared opening"))
            .sendBlockChange(any(Location.class), eq(air));
        sentTheOpeningAs(front, air, 2, "a chunk crossing redraws it cleared for the viewer still drawn the view");
    }

    /**
     * The clearing sweep itself sends nobody anything: each viewer is sent the cleared opening as the
     * drawing says they are drawn the view, and everybody else keeps what they have, the horizon.
     */
    @Test
    void clearingTheHorizonFillsItForNobody()
    {
        clearedForTheView();

        verify(gate, never()).fillGateInterior(Material.AIR);
        StargateBlockSetup.refreshPortalVisuals(front);
        sentTheOpeningAs(front, water, 1, "not yet drawn the view, so the horizon");
    }

    @Test
    void walkingRoundTheGateSendsTheHorizonOneWayAndTheClearedOpeningTheOther()
    {
        clearedForTheView();

        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 1, "in front and drawn the view");
        drawsTheView(front, false);
        sentTheOpeningAs(front, water, 1, "walked behind the gate: the horizon is back for them");
        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 2, "back in front: cleared again");
    }

    @Test
    void aRedrawThatChangesNothingSendsNothing()
    {
        clearedForTheView();
        drawsTheView(front, true);
        clearInvocations(front);

        drawsTheView(front, true);

        verify(front, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test
    void aViewerWhoseDrawingWentWithThemIsForgotten()
    {
        clearedForTheView();
        drawsTheView(front, true);

        // Quit, or into another world: the drawing goes, and nothing is sent to where they were.
        GateViews.drawn(front.getUniqueId(), null, Set.of());
        StargateBlockSetup.refreshPortalVisuals(front);

        sentTheOpeningAs(front, water, 1, "back again and not drawn the view: the horizon");
    }

    @Test
    void aViewerWhoLeavesTheServerIsForgotten()
    {
        clearedForTheView();
        drawsTheView(front, true);

        // What the quit listener calls.
        manager.when(() -> StargateManager.forgetPortalVisuals(any(UUID.class))).thenCallRealMethod();
        StargateManager.forgetPortalVisuals(front.getUniqueId());
        StargateBlockSetup.refreshPortalVisuals(front);

        sentTheOpeningAs(front, water, 1, "rejoined, not drawn the view yet: the horizon");
    }

    @Test
    void aGateThatClosesIsClearedForNobody()
    {
        clearedForTheView();
        drawsTheView(front, true);

        GateViews.closed(gate);

        assertEquals(Material.WATER, GateViews.horizonFor(gate, Material.WATER, front),
            "a redial settles into the horizon, not into an opening cleared for whoever was looking");
        // Dialled again and cleared again: still drawn the view, so the opening is sent cleared again.
        clearedForTheView();
        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 2, "the close forgot them, so the next clearing sends them the opening again");
    }

    /**
     * A view turned off and on again is sent cleared again to whoever is still drawn it.
     *
     * <p>Turned off, the horizon was filled back for everybody; remembered as still sent nothing, a
     * viewer drawn the view again was sent nothing, and saw the horizon standing over the view.
     */
    @Test
    void aViewTurnedOffAndOnAgainIsSentClearedAgainToItsViewer()
    {
        clearedForTheView();
        drawsTheView(front, true);
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "horizon");
        GateViews.offerAll();

        clearedForTheView();
        drawsTheView(front, true);

        sentTheOpeningAs(front, air, 2, "cleared again for the viewer drawn the view");
    }

    /** Somebody too far off to be drawn the horizon is sent nothing for it, whatever the drawing says. */
    @Test
    void somebodyBeyondTheHorizonsReachIsSentNothing()
    {
        clearedForTheView();
        when(front.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 200.0));

        drawsTheView(front, true);

        verify(front, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        // Not remembered as sent: back within reach, the next redraw sends it.
        when(front.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 25.0));
        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 1, "within reach now, and still drawn the view");
    }

    /** Steps somebody sent the cleared opening out of the horizon's reach, no longer drawn the view. */
    private void sentTheClearedOpeningThenOutOfReach()
    {
        clearedForTheView();
        drawsTheView(front, true);
        when(front.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 200.0));
        drawsTheView(front, false);
        sentTheOpeningAs(front, water, 0, "too far off to be sent anything");
        when(front.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 25.0));
    }

    /**
     * Somebody who steps out of the horizon's reach after being sent the cleared opening, and back in
     * without crossing a chunk, is sent the portal material on their next redraw, once.
     *
     * <p>Stepping across the reach inside one chunk runs no chunk-crossing redraw, so dropped while out
     * of reach, nothing would ever send them the horizon over the empty ring they were last sent.
     */
    @Test
    void somebodyWhoStepsBackIntoReachIsSentThePortalMaterialOnTheNextRedraw()
    {
        sentTheClearedOpeningThenOutOfReach();

        drawsTheView(front, false);
        sentTheOpeningAs(front, water, 1, "back in reach: the portal material they were owed");
        drawsTheView(front, false);
        sentTheOpeningAs(front, water, 1, "and owed nothing after that");
    }

    /**
     * What is owed is paid even once no gate is cleared any more.
     *
     * <p>Turning the view off puts the portal material back for whoever is within reach at that moment;
     * somebody out of reach then, still holding the empty ring, is owed it whatever the level is now.
     */
    @Test
    void whatIsOwedIsPaidAfterTheViewIsTurnedOff()
    {
        sentTheClearedOpeningThenOutOfReach();
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "horizon");
        GateViews.offerAll();

        drawsTheView(front, false);

        sentTheOpeningAs(front, water, 1, "back in reach: the portal material they were owed");
    }

    /**
     * Somebody who comes back into reach by a chunk crossing is drawn the portal material by it, not
     * the empty ring.
     *
     * <p>Kept as sent the cleared opening while out of reach, the crossing's redraw sent them the empty
     * ring from behind the gate until the next sweep.
     */
    @Test
    void somebodyWhoCrossesBackIntoReachIsDrawnThePortalMaterialByTheCrossing()
    {
        sentTheClearedOpeningThenOutOfReach();

        StargateBlockSetup.refreshPortalVisuals(front);

        sentTheOpeningAs(front, water, 1, "the crossing draws the portal material");
        sentTheOpeningAs(front, air, 1, "never the empty ring again, only the first time, drawn the view");
    }

    /**
     * A send that fails is tried again on the next redraw, and does not stop the next gate's.
     *
     * <p>Remembered as sent before the send was made, a throw left the viewer recorded as seeing
     * through a gate whose opening never reached them, and the gates after it unsent.
     */
    @Test
    void aSendThatFailsIsTriedAgainAndDoesNotStopTheNextGates()
    {
        final Stargate byblos = byblos();
        manager.when(StargateManager::getOpenGates).thenReturn(Set.of(gate, byblos));
        clearedForTheView();
        doThrow(new IllegalStateException("connection closing")).doNothing()
            .when(front).sendBlockChange(argThat(at -> (at != null) && (at.getBlockX() < 20)), eq(air));

        drawsTheView(front, Set.of(WINDOW, "gate:Byblos"));

        verify(front, times(CELLS).description("the second gate is sent though the first threw"))
            .sendBlockChange(argThat(at -> (at != null) && (at.getBlockX() >= 20)), eq(air));
        drawsTheView(front, Set.of(WINDOW, "gate:Byblos"));
        verify(front, times(1 + CELLS).description("the one that threw is sent whole on the next redraw"))
            .sendBlockChange(argThat(at -> (at != null) && (at.getBlockX() < 20)), eq(air));
    }

    /** A crossing paints the opening itself, so a viewer walking about under it is left to the crossing until it is over. */
    @Test
    void aViewerMovingDuringACrossingIsLeftToTheCrossingUntilItIsOver() throws ReflectiveOperationException
    {
        clearedForTheView();
        final Map<String, Integer> running = PrivateStatics.of(StargateIrisAnimator.class, "running");
        running.put("Abydos", 1);
        try
        {
            drawsTheView(front, true);
        }
        finally
        {
            running.remove("Abydos");
        }

        verify(front, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 1, "the crossing over, the next redraw sends the cleared opening");
    }

    /**
     * A gate still drawn as a window after its iris opened, its clearing ended, is sent the cleared
     * opening once the next sweep clears it again.
     *
     * <p>Counted as cleared while it was not, it was sent its horizon and remembered as sent the
     * cleared opening, so the clearing that followed sent nothing.
     */
    @Test
    void aWindowWhoseClearingEndedIsSentClearedWhenItClearsAgain() throws ReflectiveOperationException
    {
        final Stargate byblos = byblos();
        clearedForTheView();
        drawsTheView(front, Set.of(WINDOW, "gate:Byblos"));

        final Set<String> cleared = PrivateStatics.of(GateViews.class, "CLEARED");
        cleared.add("Byblos");
        drawsTheView(front, Set.of(WINDOW, "gate:Byblos"));

        verify(front, times(CELLS).description("cleared now, so its opening is sent cleared"))
            .sendBlockChange(argThat(at -> (at != null) && (at.getBlockX() >= 20)), eq(air));
        assertEquals(Material.AIR, GateViews.horizonFor(byblos, Material.WATER, front));
    }

    /**
     * A gate whose view is drawn before its horizon has cleared is sent cleared once it does.
     *
     * <p>Beside another gate already cleared, one whose iris was still opening was remembered as sent
     * the cleared opening while it still showed its horizon; when it cleared, nothing was sent, and the
     * viewer saw the horizon standing over its view.
     */
    @Test
    void aViewDrawnBeforeItsHorizonClearsIsSentClearedWhenItDoes() throws ReflectiveOperationException
    {
        final Stargate byblos = byblos();
        manager.when(StargateManager::getOpenGates).thenReturn(Set.of(gate, byblos));
        final Map<String, Integer> running = PrivateStatics.of(StargateIrisAnimator.class, "running");
        running.put("Byblos", 1);
        try
        {
            clearedForTheView();
            drawsTheView(front, Set.of(WINDOW, "gate:Byblos"));
        }
        finally
        {
            running.remove("Byblos");
        }
        assertEquals(Material.WATER, GateViews.horizonOf(byblos, Material.WATER), "its iris still crossing: not cleared");

        GateViews.offerAll();
        drawsTheView(front, Set.of(WINDOW, "gate:Byblos"));

        verify(front, times(CELLS).description("cleared now, so its opening is sent cleared"))
            .sendBlockChange(argThat(at -> at.getBlockX() >= 20), eq(air));
    }

    /** A second gate, Byblos, open beside the first at x = 20, dialled to the same place. */
    private Stargate byblos()
    {
        final Stargate byblos = mock(Stargate.class);
        when(byblos.getGateName()).thenReturn("Byblos");
        when(byblos.isGateActive()).thenReturn(true);
        when(byblos.isGatePortalOpen()).thenReturn(true);
        when(byblos.getGateWorld()).thenReturn(world);
        final Stargate target = gate.getGateTarget();
        when(byblos.getGateTarget()).thenReturn(target);
        when(byblos.getGateFacing()).thenReturn(BlockFace.SOUTH);
        when(byblos.getEffectivePortalMaterial()).thenReturn(Material.WATER);
        opening(byblos, 20);
        manager.when(() -> StargateManager.getStargate("Byblos")).thenReturn(byblos);
        return byblos;
    }

    /** A shut iris draws its own opening, which a viewer's horizon must not be sent over. */
    @Test
    void aShutIrisIsNotDrawnOverForAViewer()
    {
        clearedForTheView();
        when(gate.isGateIrisActive()).thenReturn(true);

        drawsTheView(front, true);

        verify(front, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        when(gate.isGateIrisActive()).thenReturn(false);
        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 1, "the iris open, the next redraw sends the cleared opening");
    }

    @Test
    void turningTheViewOffPutsTheHorizonBackForTheViewerToo()
    {
        clearedForTheView();
        drawsTheView(front, true);

        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "horizon");
        GateViews.offerAll();
        StargateBlockSetup.refreshPortalVisuals(front);

        verify(gate).fillGateInterior(Material.WATER);
        sentTheOpeningAs(front, water, 1, "no view any more, so the horizon, for them as for everybody");
    }

    @Test
    void theViewerIsForgottenAsThePluginStops()
    {
        clearedForTheView();
        drawsTheView(front, true);

        GateViews.clear();

        assertEquals(Material.WATER, GateViews.horizonFor(gate, Material.WATER, front));
        // Started again: nothing is remembered as sent, so the first clearing sends the viewer the opening.
        clearedForTheView();
        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 2, "sent the cleared opening again after the restart");
    }

    /**
     * At {@code behind} the horizon never clears, so a viewer drawn the view is sent nothing for it.
     */
    @Test
    void behindSendsAViewerOfTheViewNothingForTheOpening()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "behind");
        GateViews.offerAll();

        drawsTheView(front, true);

        verify(front, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        assertEquals(Material.WATER, GateViews.horizonFor(gate, Material.WATER, front));
        clearedForTheView();
        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 1, "the same viewer at open is sent the cleared opening");
    }

    @Test
    void horizonSendsAViewerNothingForTheOpening()
    {
        GateViews.offerAll();

        drawsTheView(front, true);

        verify(front, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        assertEquals(Material.WATER, GateViews.horizonFor(gate, Material.WATER, front));
        clearedForTheView();
        drawsTheView(front, true);
        sentTheOpeningAs(front, air, 1, "the same viewer at open is sent the cleared opening");
    }
}
