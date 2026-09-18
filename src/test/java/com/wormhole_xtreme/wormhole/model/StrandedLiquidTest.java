package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Water left standing in a gate's opening by versions that built the portal from real blocks.
 *
 * <p>A dial that glitched part-way could strand some; nothing in the opening of a closed gate is
 * the gate's own now that the portal is drawn to clients.
 */
class StrandedLiquidTest
{
    private World world;
    private final Map<String, Block> blocks = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> block(
            inv.getArgument(0, Integer.class), inv.getArgument(1, Integer.class), inv.getArgument(2, Integer.class),
            Material.AIR));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private Block block(final int x, final int y, final int z, final Material type)
    {
        return blocks.computeIfAbsent(x + "," + y + "," + z, k -> {
            final Block b = mock(Block.class);
            when(b.getType()).thenReturn(type);
            return b;
        });
    }

    private Stargate gateWithOpening()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("alpha");
        gate.setGateWorld(world);
        for (int y = 64; y < 67; y++)
        {
            gate.getGatePortalBlocks().add(new Location(world, 0, y, 0));
        }
        return gate;
    }

    /** Water standing in a closed gate's opening is found and cleared; air is left alone. */
    @Test
    void waterInAClosedGatesOpeningIsFoundAndCleared()
    {
        final Block water = block(0, 65, 0, Material.WATER);
        final Block lava = block(0, 66, 0, Material.LAVA);
        final Block air = block(0, 64, 0, Material.AIR);
        final Stargate gate = gateWithOpening();

        assertEquals(2, gate.strandedLiquid().size());
        assertEquals(2, gate.clearStrandedLiquid());

        verify(water).setType(Material.AIR);
        verify(lava).setType(Material.AIR);
        verify(air, never()).setType(Material.AIR);
    }

    /** An open gate's opening is never touched: whatever stands there, it is not left over. */
    @Test
    void anOpenGateIsLeftAlone()
    {
        final Block water = block(0, 65, 0, Material.WATER);
        final Stargate gate = gateWithOpening();
        gate.setGateActive(true);

        assertTrue(gate.strandedLiquid().isEmpty());
        assertEquals(0, gate.clearStrandedLiquid());
        verify(water, never()).setType(Material.AIR);
        gate.setGateActive(false);
    }
}
