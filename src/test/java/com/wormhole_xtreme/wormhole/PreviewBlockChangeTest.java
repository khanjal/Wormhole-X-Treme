package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;

/** A block placed or broken tells the build previews, unless the listener refused it. */
class PreviewBlockChangeTest
{
    private final World world = mock(World.class);
    private final Player player = mock(Player.class);

    @BeforeEach
    void setUp() throws ReflectiveOperationException
    {
        GateSpatialIndex.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        GateSpatialIndex.clear();
        PluginTestSupport.remove();
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(Material.OBSIDIAN);
        return block;
    }

    private static BlockPlaceEvent placing(final Block block, final Player by)
    {
        final BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.getPlayer()).thenReturn(by);
        return event;
    }

    @Test
    void placingOrBreakingABlockTellsThePreviews()
    {
        final WormholeXTremeBlockListener listener = new WormholeXTremeBlockListener();
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            listener.onBlockPlace(placing(blockAt(1, 64, 2), player));
            listener.onBlockBreak(new BlockBreakEvent(blockAt(3, 65, 4), player));

            previews.verify(() -> GatePreviews.blockChanged(world, 1, 64, 2));
            previews.verify(() -> GatePreviews.blockChanged(world, 3, 65, 4));
        }
    }

    @Test
    void aBreakTheListenerRefusesTellsThemNothing()
    {
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateName("guarded");
        gate.getGateStructureBlocks().add(new Location(world, 10, 64, 20));
        StargateManager.registerStargate(gate);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            final BlockBreakEvent event = new BlockBreakEvent(blockAt(10, 64, 20), player);
            new WormholeXTremeBlockListener().onBlockBreak(event);

            org.junit.jupiter.api.Assertions.assertTrue(event.isCancelled());
            previews.verify(() -> GatePreviews.blockChanged(any(), anyInt(), anyInt(), anyInt()), never());
        }
        finally
        {
            StargateManager.removeStargate(gate);
        }
    }
}
