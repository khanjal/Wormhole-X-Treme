package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The blocks a gate's woosh has drawn, counted as it draws them and as it takes them back (#430).
 *
 * <p>The count is what a gate that closes mid-woosh uses to undraw whatever is left showing, so a
 * block drawn and never counted is one nothing takes down, and a block taken back and still counted
 * is one it tries to take down twice.
 */
class StargateWooshBlocksTest
{
    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
        PluginTestSupport.forgetAllGates();
    }

    @Test
    void aWooshStepCountsTheBlocksItDrawsAndForgetsThemWhenItTakesThemBack() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        PluginTestSupport.scheduler(mock(BukkitScheduler.class));
        final World world = mock(World.class);
        final Block block = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateActive(true);
        final List<Location> wave = new ArrayList<>();
        wave.add(new Location(world, 1, 64, 1));
        gate.getGateWooshBlocks().add(wave);

        try (MockedStatic<StargateBlockSetup> setup = mockStatic(StargateBlockSetup.class);
            MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            StargateAnimator.animateOpening(gate);

            assertEquals(1, gate.runtime().getGateAnimatedBlocks().size(), "the wave drawn is counted");
            assertSame(block, gate.runtime().getGateAnimatedBlocks().get(0), "as the block that is really there");

            StargateAnimator.animateOpening(gate);

            assertTrue(gate.runtime().getGateAnimatedBlocks().isEmpty(), "taken back, it is no longer counted");
        }
    }
}
