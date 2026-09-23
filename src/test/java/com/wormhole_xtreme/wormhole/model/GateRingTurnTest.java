package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
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
        PluginTestSupport.forgetAllGates();
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
        // Dialling: only the gate that names a target turns its ring.
        gate.setGateTarget(new Stargate());
        return gate;
    }

    private static boolean holds(final List<Location> blocks, final Location l)
    {
        return (blocks != null) && blocks.stream().anyMatch(b -> (b.getBlockX() == l.getBlockX())
            && (b.getBlockY() == l.getBlockY()) && (b.getBlockZ() == l.getBlockZ()));
    }

    /** A gate its shape no longer fits turns no ring, rather than one laid over blocks it does not own. */
    @Test
    void aGateItsShapeNoLongerFitsTurnsNoRing()
    {
        final Stargate fits = standardGate();
        assertNotNull(StargateAnimator.spinOf(fits), "a gate built as its shape says should turn");

        // The ring's bottom row gone from the gate's record, as a frame built to another shape has it.
        final Stargate moved = standardGate();
        final int bottom = StargateAnimator.spinOf(fits).ring().stream().mapToInt(Cell::y).min().orElseThrow();
        moved.getGateStructureBlocks().removeIf(l -> l.getBlockY() == bottom);
        for (final List<Location> wave : moved.getGateLightBlocks())
        {
            if (wave != null)
            {
                wave.removeIf(l -> l.getBlockY() == bottom);
            }
        }
        assertNull(StargateAnimator.spinOf(moved));
    }

    /**
     * Before the first chevron locks, the default TOP turn starts opposite the top and moves a tick
     * at a time, and the chevron locks only once the turn is done.
     */
    @Test
    void theRingTurnsToTheTopBeforeTheFirstChevronLocks()
    {
        final Stargate gate = standardGate();
        final DialSpin spin = StargateAnimator.spinOf(gate);
        assertNotNull(spin, "a Standard gate has a ring to turn");
        final Location start = at(DialSpin.of(cells, grid).path(DialSpinPattern.TOP, 1).get(0));
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

    /**
     * Every pattern locks the first chevron on the same tick, and NONE locks it at once without a
     * turn. Only TOP's rest after a lock adds time, from the second glyph on.
     */
    @Test
    void everyPatternLocksTheChevronOnTheSameTick()
    {
        try
        {
            for (final com.wormhole_xtreme.wormhole.logic.DialSpinPattern pattern
                : com.wormhole_xtreme.wormhole.logic.DialSpinPattern.values())
            {
                com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                    com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_DIAL_SPIN, pattern.name());
                final Stargate gate = standardGate();
                final int ticks = (pattern == com.wormhole_xtreme.wormhole.logic.DialSpinPattern.NONE)
                    ? 0 : gate.getEffectiveLightTicks();
                try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
                     MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
                {
                    for (int tick = 0; tick < ticks; tick++)
                    {
                        StargateAnimator.lightStargate(gate, true);
                        assertEquals(0, gate.getGateLightingCurrentIteration(), pattern + ": nothing locked at tick " + tick);
                    }
                    StargateAnimator.lightStargate(gate, true);
                    assertEquals(1, gate.getGateLightingCurrentIteration(), pattern + ": locked after " + ticks + " ticks");
                }
            }
        }
        finally
        {
            // The default, as the other tests here assume.
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_DIAL_SPIN, "TOP");
        }
    }

    /**
     * The default TOP turn leaves its light on the top chevron as the first chevron locks, and for
     * the rest after it, before setting off for the second; the second locks that much later.
     */
    @Test
    void theTopTurnRestsOnTheTopThroughTheLock()
    {
        final Stargate gate = standardGate();
        final List<Cell> path = DialSpin.of(cells, grid).path(DialSpinPattern.TOP, 1);
        final Location top = at(path.get(path.size() - 1));
        final int ticks = gate.getEffectiveLightTicks();

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            for (int tick = 0; tick <= ticks; tick++)
            {
                StargateAnimator.lightStargate(gate, true);
            }
            assertEquals(1, gate.getGateLightingCurrentIteration());
            for (int tick = 0; tick < DialSpin.TOP_HOLD_TICKS; tick++)
            {
                StargateAnimator.lightStargate(gate, true);
            }
            blocks.verify(() -> StargateBlockSetup.undrawBlocks(eq(gate), argThat(l -> holds(l, top))), never());

            StargateAnimator.lightStargate(gate, true);
            blocks.verify(() -> StargateBlockSetup.undrawBlocks(eq(gate), argThat(l -> holds(l, top))));
            for (int tick = 1; tick < ticks; tick++)
            {
                StargateAnimator.lightStargate(gate, true);
                assertEquals(1, gate.getGateLightingCurrentIteration(), "still turning at tick " + tick);
            }
            StargateAnimator.lightStargate(gate, true);
            assertEquals(2, gate.getGateLightingCurrentIteration());
        }
    }

    /**
     * UNIVERSE's glyphs stay lit on the ring through each lock, and go when the gate shuts. Checked
     * on the point of origin, carried onto plain frame, as the top is a chevron shutting puts back anyway.
     */
    @Test
    void universeGlyphsStayLitUntilTheGateShuts()
    {
        try
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_DIAL_SPIN, "UNIVERSE");
            final Stargate gate = standardGate();
            final Location origin = at(DialSpin.of(cells, grid).rest(DialSpinPattern.UNIVERSE, 1, 7).stream()
                .filter(c -> (c.part() == Part.FRAME) && (c.wave() == 0)).findFirst().orElseThrow());
            try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
                 MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
            {
                for (int tick = 0; tick <= gate.getEffectiveLightTicks(); tick++)
                {
                    StargateAnimator.lightStargate(gate, true);
                }
                assertEquals(1, gate.getGateLightingCurrentIteration());
                blocks.verify(() -> StargateBlockSetup.drawLights(eq(gate), argThat(l -> holds(l, origin))), atLeastOnce());
                blocks.verify(() -> StargateBlockSetup.undrawBlocks(eq(gate), argThat(l -> holds(l, origin))), never());

                StargateAnimator.lightStargate(gate, false);
                blocks.verify(() -> StargateBlockSetup.undrawBlocks(eq(gate), argThat(l -> holds(l, origin))), atLeastOnce());
            }
        }
        finally
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_DIAL_SPIN, "TOP");
        }
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

            final Location start = at(DialSpin.of(cells, grid).path(DialSpinPattern.TOP, 1).get(0));
            // Standard's bottom centre is also its eighth chevron, so it is put back as both.
            blocks.verify(() -> StargateBlockSetup.undrawBlocks(eq(gate), argThat(l -> holds(l, start))),
                atLeastOnce());
        }
        assertTrue(!gate.isGateLightsActive());
        verify(scheduler, times(1)).scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong());
    }

    /** A Standard gate built at another spot, as detection would record it. */
    private Stargate standardGateAt(final String name, final int ox)
    {
        final GateGrid at = GateBlueprint.inFrontOf(shape, ox, 64, 0, BlockFace.NORTH);
        final List<Cell> its = GateBlueprint.of(shape, at);
        final Stargate gate = spy(new Stargate());
        gate.setGateName(name);
        gate.setGateWorld(world);
        gate.setGateShape(shape);
        gate.setGateFacing(at.facing());
        gate.getGateLightBlocks().add(null);
        for (int wave = 1; wave <= 8; wave++)
        {
            final List<Location> blocks = new ArrayList<>();
            for (final Cell c : its)
            {
                if (c.wave() == wave)
                {
                    blocks.add(at(c));
                }
            }
            gate.getGateLightBlocks().add(blocks);
        }
        for (final Cell c : its)
        {
            if ((c.part() == Part.FRAME) || (c.part() == Part.CHEVRON))
            {
                gate.getGateStructureBlocks().add(at(c));
            }
        }
        final Cell button = its.stream().filter(c -> c.part() == Part.BUTTON).findFirst().orElseThrow();
        final Block buttonBlock = mock(Block.class);
        when(buttonBlock.getX()).thenReturn(button.x());
        when(buttonBlock.getY()).thenReturn(button.y());
        when(buttonBlock.getZ()).thenReturn(button.z());
        gate.setGateDialLeverBlock(buttonBlock);
        gate.setGatePlayerTeleportLocation(new Location(world, ox, 65, 2));
        doNothing().when(gate).toggleDialLeverState(anyBoolean());
        doNothing().when(gate).toggleRedstoneGateActivatedPower();
        StargateManager.registerStargate(gate);
        return gate;
    }

    /**
     * Only the gate dialling turns its ring; the gate being dialled lights its chevrons in order, a
     * chevron's interval apart, as on the show. Found in-game: both turned.
     */
    @Test
    void onlyTheGateDiallingTurnsItsRing()
    {
        final Stargate near = standardGateAt("near", 0);
        final Stargate far = standardGateAt("far", 40);
        final List<Object[]> booked = new ArrayList<>();
        when(scheduler.scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(inv -> {
            booked.add(new Object[] { inv.getArgument(1, Runnable.class), inv.getArgument(2, Long.class) });
            return 7;
        });
        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class);
             MockedStatic<com.wormhole_xtreme.wormhole.utils.WorldUtils> worlds =
                 mockStatic(com.wormhole_xtreme.wormhole.utils.WorldUtils.class, CALLS_REAL_METHODS))
        {
            // Chunk loading is the server's; everything else about the dial runs for real.
            worlds.when(() -> com.wormhole_xtreme.wormhole.utils.WorldUtils.scheduleChunkLoad(any())).thenAnswer(inv -> null);
            worlds.when(() -> com.wormhole_xtreme.wormhole.utils.WorldUtils.forceLoadDestinationChunks(any())).thenAnswer(inv -> null);
            near.setGateLightsActive(true);
            StargateDialManager.dialStargate(near, far, true);
            // The dial's own steps, not the shutdown timer.
            for (int i = 0; i < 20; i++)
            {
                final List<Object[]> now = new ArrayList<>(booked);
                booked.clear();
                now.stream().filter(b -> ((Long) b[1]) <= Stargate.LAST_CHEVRON_PAUSE_TICKS)
                    .forEach(b -> ((Runnable) b[0]).run());
            }

            final GateGrid nearGrid = GateBlueprint.inFrontOf(shape, 0, 64, 0, BlockFace.NORTH);
            final GateGrid farGrid = GateBlueprint.inFrontOf(shape, 40, 64, 0, BlockFace.NORTH);
            final DialSpin nearSpin = DialSpin.of(GateBlueprint.of(shape, nearGrid), nearGrid);
            final DialSpin farSpin = DialSpin.of(GateBlueprint.of(shape, farGrid), farGrid);
            final Location nearStart = at(nearSpin.path(DialSpinPattern.TOP, 1).get(1));
            final Location farStart = at(farSpin.path(DialSpinPattern.TOP, 1).get(1));
            blocks.verify(() -> StargateBlockSetup.drawLights(eq(near), argThat(l -> holds(l, nearStart))),
                atLeastOnce());
            blocks.verify(() -> StargateBlockSetup.drawLights(eq(far), argThat(l -> holds(l, farStart))), never());
            blocks.verify(() -> StargateBlockSetup.drawLights(far, far.getGateLightBlocks().get(1)));
        }
        finally
        {
            StargateManager.removeStargate(near);
            StargateManager.removeStargate(far);
        }
    }
}
