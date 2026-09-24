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
import static org.mockito.ArgumentMatchers.anyInt;
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
     * Every pattern locks the first chevron once its turn is done: on the chevron's own interval,
     * but for UNIVERSE, whose turns run to a lap and more at their own pace, and NONE, which locks
     * it at once without a turn. TOP's rest after a lock adds time from the second glyph on.
     */
    @Test
    void everyPatternLocksTheFirstChevronOnceItsTurnIsDone()
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
                    ? 0 : StargateAnimator.spinOf(gate).frames(pattern, 1, gate.getEffectiveLightTicks());
                if ((pattern != com.wormhole_xtreme.wormhole.logic.DialSpinPattern.NONE)
                    && (pattern != com.wormhole_xtreme.wormhole.logic.DialSpinPattern.UNIVERSE))
                {
                    assertEquals(gate.getEffectiveLightTicks(), ticks, pattern + " keeps the chevron's interval");
                }
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
     * UNIVERSE's riders stay lit through a lock, rather than going as TOP's light once did, and a
     * locked chevron does not light in its own place as well: it rides with the ring. Found in-game:
     * lit in both, half the gate stood lit. Checked on the point of origin, which the first turn
     * carries off the top.
     */
    @Test
    void universeRidersStayLitThroughTheLockAndChevronsDoNotLightInPlace()
    {
        try
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_DIAL_SPIN, "UNIVERSE");
            final Stargate gate = standardGate();
            final Location origin = at(DialSpin.of(cells, grid).rest(DialSpinPattern.UNIVERSE, 1, 7).stream()
                .filter(c -> c.wave() != Stargate.LOCAL_CHEVRONS).findFirst().orElseThrow());
            try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
                 MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
            {
                final int frames = StargateAnimator.spinOf(gate).frames(DialSpinPattern.UNIVERSE, 1, gate.getEffectiveLightTicks());
                for (int tick = 0; tick < frames; tick++)
                {
                    StargateAnimator.lightStargate(gate, true);
                }
                // The lock's own tick alone: a rider passing over chevron 1 draws its one block too.
                blocks.clearInvocations();
                StargateAnimator.lightStargate(gate, true);

                assertEquals(1, gate.getGateLightingCurrentIteration());
                blocks.verify(() -> StargateBlockSetup.drawLights(eq(gate), argThat(l -> holds(l, origin))));
                blocks.verify(() -> StargateBlockSetup.undrawBlocks(eq(gate), argThat(l -> holds(l, origin))), never());
                blocks.verify(() -> StargateBlockSetup.drawLights(gate, gate.getGateLightBlocks().get(1)), never());
                sounds.verify(() -> GateSounds.chevron(eq(gate), eq(1), anyInt()));
            }
        }
        finally
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_DIAL_SPIN, "TOP");
        }
    }

    /**
     * Under UNIVERSE only the ring's front layer rides, so the last lock lights every chevron in
     * place: on Grand, whose chevrons are two layers deep, the back layer never lit otherwise, even
     * with the wormhole open. Found by a Sonnet review.
     */
    @Test
    void universeLightsEveryLayerOfEveryChevronAtTheLastLock() throws Exception
    {
        try
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_DIAL_SPIN, "UNIVERSE");
            shape = new Stargate3DShape(Files.readAllLines(Paths.get("src/main/resources/shapes/gate/Grand.shape"))
                .toArray(new String[0]));
            grid = GateBlueprint.inFrontOf(shape, 0, 64, 0, BlockFace.NORTH);
            cells = GateBlueprint.of(shape, grid);
            final Stargate gate = standardGate();
            final DialSpin spin = StargateAnimator.spinOf(gate);
            assertNotNull(spin, "a Grand gate has a ring to turn");
            try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
                 MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
            {
                for (int glyph = 1; glyph <= Stargate.LOCAL_CHEVRONS; glyph++)
                {
                    final int calls = spin.frames(DialSpinPattern.UNIVERSE, glyph, gate.getEffectiveLightTicks()) + 1;
                    for (int call = 0; call < calls; call++)
                    {
                        StargateAnimator.lightStargate(gate, true);
                    }
                    if (glyph < Stargate.LOCAL_CHEVRONS)
                    {
                        assertEquals(glyph, gate.getGateLightingCurrentIteration(), "locked glyph " + glyph);
                    }
                }
                sounds.verify(() -> GateSounds.locked(gate));
                for (int wave = 1; wave <= Stargate.LOCAL_CHEVRONS; wave++)
                {
                    final int w = wave;
                    final List<Location> chevron = gate.getGateLightBlocks().get(wave);
                    assertTrue(chevron.size() > spin.ring().stream().filter(c -> c.wave() == w).count(),
                        "chevron " + wave + " has blocks behind the ring");
                    blocks.verify(() -> StargateBlockSetup.drawLights(gate, chevron));
                }
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
     * The gate being dialled waits as long between chevrons as the dialling gate's turn takes, TOP's
     * rest included, so both wormholes form together. It kept its own interval, and opened three
     * seconds before the gate dialling it. Found by a Fable review.
     */
    @Test
    void theGateBeingDialledKeepsPaceWithTheTurn()
    {
        final Stargate near = standardGateAt("near", 0);
        final Stargate far = standardGateAt("far", 40);
        assertEquals(far.getEffectiveLightTicks(), StargateAnimator.untilNext(far, 2), "undialled, its own interval");

        near.setGateActive(true);
        near.setGateTarget(far);
        far.setGateActive(true);

        final DialSpin spin = StargateAnimator.spinOf(near);
        assertNotNull(spin);
        final long turn = spin.frames(DialSpinPattern.TOP, 2, near.getEffectiveLightTicks()) + 1L;
        assertTrue(turn > far.getEffectiveLightTicks(), "TOP's rest makes the turn the longer");
        assertEquals(turn, StargateAnimator.untilNext(far, 2));
    }

    /**
     * Only the gate dialling turns its ring; the gate being dialled lights its chevrons in order,
     * keeping pace with the turn, as on the show. Found in-game: both turned.
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
