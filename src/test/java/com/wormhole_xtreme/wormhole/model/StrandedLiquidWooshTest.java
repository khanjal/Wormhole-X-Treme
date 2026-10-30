package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The part of the stranded-liquid scan that {@code StrandedLiquidTest} leaves out: the woosh's
 * cells, a cell named twice, a wave that has nothing, and a gate with no world.
 *
 * <p>Four mutations survived without it: the woosh not scanned at all, a cell counted once per
 * time it was named, a wave of null stopping the scan, and the missing-world guard. The woosh
 * is where older versions stranded most of the water, because it was built from real blocks one
 * wave at a time.
 */
class StrandedLiquidWooshTest
{
    private World world;
    private MockedStatic<StargateAnimator> animator;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
        {
            final Block air = mock(Block.class);
            when(air.getType()).thenReturn(Material.AIR);
            return air;
        });
        animator = mockStatic(StargateAnimator.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        animator.close();
        PluginTestSupport.remove();
    }

    private Block waterAt(final int x)
    {
        final Block b = mock(Block.class);
        when(b.getType()).thenReturn(Material.WATER);
        when(world.getBlockAt(x, 64, 0)).thenReturn(b);
        return b;
    }

    private Location at(final int x)
    {
        return new Location(world, x, 64, 0);
    }

    private Stargate gateWithPortalCell(final int x)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("woosh");
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(at(x));
        return gate;
    }

    private void waves(final List<Location> first, final List<Location> second)
    {
        final List<List<Location>> waves = Arrays.asList(first, second);
        animator.when(() -> StargateAnimator.wooshWaveCount(any(Stargate.class))).thenReturn(waves.size());
        animator.when(() -> StargateAnimator.wooshWave(any(Stargate.class), anyInt()))
            .thenAnswer(inv -> waves.get(inv.getArgument(1, Integer.class)));
    }

    @Test
    void waterStandingInAWooshWaveIsFoundToo()
    {
        final Block inWave = waterAt(7);
        final Stargate gate = gateWithPortalCell(1);
        waves(List.of(at(7)), List.of());

        final List<Block> found = gate.strandedLiquid();

        assertEquals(List.of(inWave), found, "the woosh is where most of it was stranded");
        assertEquals(1, gate.clearStrandedLiquid());
        verify(inWave).setType(Material.AIR);
    }

    @Test
    void aCellNamedByThePortalAndAWaveIsCountedOnce()
    {
        waterAt(3);
        final Stargate gate = gateWithPortalCell(3);
        waves(List.of(at(3)), List.of(at(3)));

        assertEquals(1, gate.strandedLiquid().size());
        assertEquals(1, gate.clearStrandedLiquid(), "cleared once, and reported once");
    }

    @Test
    void aWaveWithNothingInItDoesNotStopTheScan()
    {
        final Block later = waterAt(9);
        final Stargate gate = gateWithPortalCell(1);
        waves(null, List.of(at(9)));

        assertEquals(List.of(later), gate.strandedLiquid());
    }

    @Test
    void aGateWithNoWorldHasNothingToScan()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("worldless");
        gate.getGatePortalBlocks().add(at(1));

        assertTrue(gate.strandedLiquid().isEmpty());
        assertEquals(0, gate.clearStrandedLiquid());
    }

    @Test
    void aGateThatIsNotThereHasNothingToScan()
    {
        assertTrue(StrandedLiquid.strandedLiquid(null).isEmpty());
    }
}
