package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.DialSpin;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.GateGrid;

/**
 * A real gate's inner ring turning while it dials (#357): a light travels round its frame to the
 * top chevron before each chevron locks, as the build preview shows it.
 */
class GateRingTurnTest
{
    private World world;
    private BukkitScheduler scheduler;
    private Stargate3DShape shape;
    private GateGrid grid;
    private List<Cell> cells;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        world = mock(World.class);
        when(world.getName()).thenReturn("here");
        shape = new Stargate3DShape(Files.readAllLines(Paths.get("src/main/resources/shapes/gate/Standard.shape"))
            .toArray(new String[0]));
        grid = GateBlueprint.inFrontOf(shape, 0, 64, 0, BlockFace.NORTH);
        cells = GateBlueprint.of(shape, grid);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private Location at(final Cell c)
    {
        return new Location(world, c.x(), c.y(), c.z());
    }

    /** A Standard gate as detection would record it: its frame, its light waves and its DHD button. */
    private Stargate standardGate()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("alpha");
        gate.setGateWorld(world);
        gate.setGateShape(shape);
        gate.setGateFacing(grid.facing());
        gate.getGateLightBlocks().add(null);
        for (int wave = 1; wave <= 8; wave++)
        {
            final List<Location> blocks = new ArrayList<>();
            for (final Cell c : cells)
            {
                if (c.wave() == wave)
                {
                    blocks.add(at(c));
                }
            }
            gate.getGateLightBlocks().add(blocks);
        }
        for (final Cell c : cells)
        {
            if ((c.part() == Part.FRAME) || (c.part() == Part.CHEVRON))
            {
                gate.getGateStructureBlocks().add(at(c));
            }
        }
        final Cell button = cells.stream().filter(c -> c.part() == Part.BUTTON).findFirst().orElseThrow();
        final Block buttonBlock = mock(Block.class);
        when(buttonBlock.getX()).thenReturn(button.x());
        when(buttonBlock.getY()).thenReturn(button.y());
        when(buttonBlock.getZ()).thenReturn(button.z());
        gate.setGateDialLeverBlock(buttonBlock);
        gate.setGateActive(true);
        gate.setGateLightsActive(true);
        return gate;
    }

    private static boolean holds(final List<Location> blocks, final Location l)
    {
        return (blocks != null) && blocks.stream().anyMatch(b -> (b.getBlockX() == l.getBlockX())
            && (b.getBlockY() == l.getBlockY()) && (b.getBlockZ() == l.getBlockZ()));
    }

    /**
     * Before the first chevron locks, the light starts opposite the top and moves a tick at a time,
     * and the chevron locks only once the turn is done.
     */
    @Test
    void theRingTurnsToTheTopBeforeTheFirstChevronLocks()
    {
        final Stargate gate = standardGate();
        final DialSpin spin = StargateAnimator.spinOf(gate);
        assertNotNull(spin, "a Standard gate has a ring to turn");
        final Location start = at(DialSpin.of(cells, grid).path(1).get(0));
        final List<Location> first = gate.getGateLightBlocks().get(1);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            StargateAnimator.lightStargate(gate, true);

            blocks.verify(() -> StargateBlockSetup.drawLights(eq(gate), argThat(l -> holds(l, start))));
            blocks.verify(() -> StargateBlockSetup.drawLights(gate, first), never());
            assertEquals(0, gate.getGateLightingCurrentIteration(), "no chevron locked yet");

            final int ticks = gate.getEffectiveLightTicks();
            for (int tick = 1; tick <= ticks; tick++)
            {
                StargateAnimator.lightStargate(gate, true);
            }

            blocks.verify(() -> StargateBlockSetup.drawLights(gate, first));
            assertEquals(1, gate.getGateLightingCurrentIteration());
        }
        // A tick at a time through the turn, and once more after the chevron locks: the next turn is the wait.
        verify(scheduler, times(gate.getEffectiveLightTicks() + 1)).scheduleSyncDelayedTask(any(Plugin.class),
            any(Runnable.class), eq(1L));
    }

    /** A gate shut part way through a turn takes the ring's light back. */
    @Test
    void shuttingAGateMidTurnTakesTheLightBack()
    {
        final Stargate gate = standardGate();

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            StargateAnimator.lightStargate(gate, true);
            StargateAnimator.lightStargate(gate, false);

            final Location start = at(DialSpin.of(cells, grid).path(1).get(0));
            // Standard's bottom centre is also its eighth chevron, so it is put back as both.
            blocks.verify(() -> StargateBlockSetup.undrawBlocks(eq(gate), argThat(l -> holds(l, start))),
                org.mockito.Mockito.atLeastOnce());
        }
        assertTrue(!gate.isGateLightsActive());
        verify(scheduler, times(1)).scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong());
    }
}
