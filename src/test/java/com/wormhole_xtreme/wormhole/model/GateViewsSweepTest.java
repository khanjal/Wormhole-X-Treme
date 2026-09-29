package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow;
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
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        player = mock(Player.class);
        when(world.getPlayers()).thenReturn(List.of(player));
        near(true);

        final World far = mock(World.class);
        when(far.getName()).thenReturn("far");
        final Stargate target = mock(Stargate.class);
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
        windows.when(() -> MirrorWindows.offerGate(anyString(), any(Block.class), any(MirrorWindow.class), anyList(),
            any(MirrorPoint.class), anyBoolean())).thenReturn(drawn);
    }

    private void offeredTimes(final int times)
    {
        windows.verify(() -> MirrorWindows.offerGate(eq("gate:Abydos"), any(Block.class), any(MirrorWindow.class),
            anyList(), any(MirrorPoint.class), anyBoolean()), times(times));
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

        windows.verify(() -> MirrorWindows.offerGate(anyString(), any(Block.class), any(MirrorWindow.class), anyList(),
            any(MirrorPoint.class), eq(true)), times(1));
        windows.verify(() -> MirrorWindows.offerGate(anyString(), any(Block.class), any(MirrorWindow.class), anyList(),
            any(MirrorPoint.class), eq(false)), times(1));
    }

    /**
     * A gate whose iris is crossing is left exactly as it is.
     *
     * <p>The crossing paints the opening a ring at a time and fills it at the end. Putting the
     * horizon back under it, or clearing it, was painted over by the crossing's next step, and the
     * gate ended with the horizon standing over its view.
     */
    @Test
    void aGateMidIrisCrossingIsLeftAsItIs() throws ReflectiveOperationException
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        drawn(true);
        GateViews.offerAll();
        verify(gate).fillGateInterior(Material.AIR);

        final Map<String, Integer> running = PrivateStatics.of(StargateIrisAnimator.class, "running");
        running.put("Abydos", 1);
        try
        {
            drawn(false);
            GateViews.offerAll();

            offeredTimes(1);
            verify(gate, never()).fillGateInterior(Material.WATER);
            assertEquals(Material.AIR, GateViews.horizonOf(gate, Material.WATER), "still cleared, until the crossing is over");
        }
        finally
        {
            running.remove("Abydos");
        }
    }
}
