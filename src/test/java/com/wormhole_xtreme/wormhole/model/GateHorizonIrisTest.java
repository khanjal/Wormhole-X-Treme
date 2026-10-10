package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * An iris opening over a horizon cleared for a gate's view opens onto the horizon for everybody (#516).
 *
 * <p>The opening iris paints the opening alike for every client near the gate. While the gate was still
 * held cleared -- its iris shut and opened again inside one sweep, or a whole crossing over between two
 * -- that paint was nothing, behind the gate too, and no sweep put the horizon back for whoever was not
 * drawn the view. Real gate, real iris path; only the server is stood in for.
 */
class GateHorizonIrisTest
{
    private World world;
    private Player front;
    private Player behind;
    private Stargate gate;
    private WormholeXTreme plugin;
    private MockedStatic<MaterialUtils> materials;
    private final BlockData bareOpening = mock(BlockData.class);
    private final BlockData openWater = mock(BlockData.class);
    private final LinkedHashMap<Integer, Runnable> pending = new LinkedHashMap<>();
    private int nextTaskId = 1;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        PluginTestSupport.forgetAllGates();
        GateViews.clear();

        materials = mockStatic(MaterialUtils.class);
        materials.when(() -> MaterialUtils.drawnAcross(any(Material.class), any()))
            .thenAnswer(i -> (i.getArgument(0) == Material.AIR) ? bareOpening
                : ((i.getArgument(0) == Material.WATER) ? openWater : mock(BlockData.class)));

        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenAnswer(invocation ->
        {
            final int id = nextTaskId++;
            pending.put(id, invocation.getArgument(1));
            return id;
        });
        doAnswer(invocation -> pending.remove(invocation.<Integer>getArgument(0))).when(scheduler).cancelTask(anyInt());
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        // The gate faces south, its opening in the plane z = 0.
        front = playerAt(3);
        behind = playerAt(-3);
        when(world.getPlayers()).thenReturn(List.of(front, behind));

        gate = new Stargate();
        gate.setGateName("IrisGate");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateCustom(true);
        gate.setGateCustomPortalMaterial(Material.WATER);
        for (int x = -1; x <= 1; x++)
        {
            for (int y = -1; y <= 1; y++)
            {
                gate.getGatePortalBlocks().add(new Location(world, x, 64 + y, 0));
                for (int z = -1; z <= 1; z++)
                {
                    final Block block = mock(Block.class);
                    when(block.getType()).thenReturn(Material.AIR);
                    when(block.getLocation()).thenReturn(new Location(world, x, 64 + y, z));
                    when(world.getBlockAt(x, 64 + y, z)).thenReturn(block);
                }
            }
        }
        gate.setGateActive(true);
        gate.setGatePortalOpen(true);
        gate.setGateIrisActive(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateIrisAnimator.cancelAll();
        materials.close();
        GateViews.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    private Player playerAt(final int z)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, z));
        return player;
    }

    /** The gate still held cleared from before its iris shut, and the front player sent it so. */
    private void stillCleared() throws ReflectiveOperationException
    {
        final Set<String> cleared = PrivateStatics.of(GateViews.class, "CLEARED");
        cleared.add("IrisGate");
        final Map<UUID, Set<String>> through = PrivateStatics.of(GateViews.class, "SEES_THROUGH");
        through.put(front.getUniqueId(), new HashSet<>(Set.of("IrisGate")));
    }

    /** What the last send to each of the opening's cells was, for one player. */
    private static List<BlockData> lastSentToTheOpening(final Player player)
    {
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(player, atLeastOnce()).sendBlockChange(where.capture(), what.capture());
        final Map<String, BlockData> last = new LinkedHashMap<>();
        for (int i = 0; i < where.getAllValues().size(); i++)
        {
            final Location at = where.getAllValues().get(i);
            if (at.getBlockZ() == 0)
            {
                last.put(at.getBlockX() + "," + at.getBlockY(), what.getAllValues().get(i));
            }
        }
        assertEquals(9, last.size(), "every cell of the opening was sent: " + last);
        return new ArrayList<>(last.values());
    }

    @Test
    void anIrisOpenedAtOnceOverAClearedHorizonOpensOntoTheHorizonBehindTheGate() throws ReflectiveOperationException
    {
        // Disabled: no sweep, the iris opens in one go.
        when(plugin.isEnabled()).thenReturn(false);
        stillCleared();

        gate.toggleIrisActive(false);

        assertTrue(lastSentToTheOpening(behind).stream().allMatch(data -> data == openWater),
            "behind the gate: the horizon, not an empty ring onto this world");
        assertEquals(Material.WATER, GateViews.horizonOf(gate, Material.WATER), "no longer held cleared");
    }

    @Test
    void anIrisCrossingOverBetweenTwoSweepsOpensOntoTheHorizonBehindTheGate() throws ReflectiveOperationException
    {
        when(plugin.isEnabled()).thenReturn(true);
        stillCleared();

        gate.toggleIrisActive(false);
        assertTrue(StargateIrisAnimator.isSweeping(gate), "a crossing, or this proves nothing about one");
        int guard = 0;
        while (!pending.isEmpty() && (guard++ < 50))
        {
            final Integer id = pending.keySet().iterator().next();
            pending.remove(id).run();
        }

        assertTrue(lastSentToTheOpening(behind).stream().allMatch(data -> data == openWater),
            "the crossing ended on the horizon for whoever is behind the gate");
    }

    /**
     * The viewer drawn the view is sent the cleared opening again once the next sweep clears it.
     *
     * <p>Remembered as sent it already, they were sent nothing, and saw the horizon over the view.
     */
    @Test
    void theViewerIsSentTheClearedOpeningAgainOnceItClearsAgain() throws ReflectiveOperationException
    {
        when(plugin.isEnabled()).thenReturn(false);
        stillCleared();
        gate.toggleIrisActive(false);
        clearInvocations(front);

        final Set<String> cleared = PrivateStatics.of(GateViews.class, "CLEARED");
        cleared.add("IrisGate");
        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            manager.when(() -> StargateManager.getStargate("IrisGate")).thenReturn(gate);
            GateViews.drawn(front.getUniqueId(), front, Set.of("gate:IrisGate"));
        }

        verify(front, times(9)).sendBlockChange(argThat(at -> at.getBlockZ() == 0), eq(bareOpening));
    }
}
