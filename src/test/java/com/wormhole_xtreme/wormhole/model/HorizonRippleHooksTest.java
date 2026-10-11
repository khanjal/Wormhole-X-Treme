package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.events.StargateShutdownEvent;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * The places a real gate calls its ripple off: closing, its iris toggled, and being removed (#579).
 *
 * <p>A real {@link Stargate}, so the order matters as it does on a server. The close used to call the
 * ripple off after marking the gate shut, when the ice in its opening was no longer the ripple's to
 * put back, and the viewer kept it until something redrew the opening.
 */
class HorizonRippleHooksTest
{
    private World world;
    private Player watcher;
    private Stargate gate;
    private MockedStatic<MaterialUtils> materials;
    private final BlockData water = mock(BlockData.class);
    private final Map<Integer, Runnable> pending = new HashMap<>();
    /** The middle of the three by three opening. */
    private Location middle;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.isEnabled()).thenReturn(true);
        PluginTestSupport.install(plugin);
        PluginTestSupport.forgetAllGates();
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(call ->
        {
            final int id = pending.size() + 1;
            pending.put(id, call.getArgument(1));
            return id;
        });
        doAnswer(call -> pending.remove((Integer) call.getArgument(0))).when(scheduler).cancelTask(anyInt());
        PluginTestSupport.scheduler(scheduler);
        ConfigTestSupport.clear();
        ConfigTestSupport.set(ConfigKeys.GATE_RIPPLE, true);
        HorizonRipple.cancelAll();

        materials = mockStatic(MaterialUtils.class);
        materials.when(() -> MaterialUtils.drawnAcross(any(Material.class), any()))
            .thenAnswer(call -> (call.getArgument(0) == Material.WATER) ? water : mock(BlockData.class));
        materials.when(() -> MaterialUtils.isAirMaterial(Material.AIR)).thenReturn(true);

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        final Block empty = mock(Block.class);
        when(empty.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(empty);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(new ArrayList<Entity>());
        watcher = mock(Player.class);
        when(watcher.getUniqueId()).thenReturn(UUID.randomUUID());
        when(watcher.isOnline()).thenReturn(true);
        when(watcher.getWorld()).thenReturn(world);
        when(watcher.getLocation()).thenReturn(new Location(world, 0, 64, 4));
        when(world.getPlayers()).thenReturn(List.of(watcher));

        gate = new Stargate();
        gate.setGateName("RippleGate");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGatePlayerTeleportLocation(new Location(world, 0, 64, 2));
        for (int x = -1; x <= 1; x++)
        {
            for (int y = 63; y <= 65; y++)
            {
                gate.getGatePortalBlocks().add(new Location(world, x, y, 0));
            }
        }
        middle = new Location(world, 0, 64, 0);
        gate.setGateActive(true);
        gate.setGatePortalOpen(true);
        assertTrue(HorizonRipple.start(gate), "a flat water gate of nine cells, somebody near");
        clearInvocations(watcher);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        HorizonRipple.cancelAll();
        StargateIrisAnimator.cancelAll();
        materials.close();
        ConfigTestSupport.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    @Test
    void closingTheGatePutsTheRippleBackToTheHorizonFirst()
    {
        try (MockedStatic<WorldUtils> utils = mockStatic(WorldUtils.class))
        {
            gate.shutdownStargate(false, StargateShutdownEvent.Reason.TIMEOUT);
        }

        verify(watcher).sendBlockChange(eq(middle), eq(water));
        assertFalse(HorizonRipple.isRippling(gate));
    }

    @Test
    void closingTheIrisCallsTheRippleOff()
    {
        StargateLifecycle.setIrisState(gate, true);

        // The iris's own sweep paints the opening; what is the ripple's is that it stopped at once.
        assertFalse(HorizonRipple.isRippling(gate), "called off, not left to find out at its next ring");
    }

    @Test
    void removingTheGateCallsTheRippleOff()
    {
        StargateManager.removeStargate(gate, null, false);

        verify(watcher).sendBlockChange(eq(middle), eq(water));
        assertFalse(HorizonRipple.isRippling(gate));
    }
}
