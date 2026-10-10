package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * A viewer who walks round a gate while its see-through iris sweeps is left no stand-in on their own side (#447).
 *
 * <p>Behind a see-through iris the wormhole is drawn as ice a block behind the ring, and the sweep
 * moves it a ring at a time. Each step used to be worked out from where the viewer stood at that
 * moment, so somebody who walked round the gate mid-sweep was taken for a viewer who never had a
 * far layer: the ice they were already holding stayed where it was, now on their own side, a
 * solid block the server does not have, until the sweep ended. An opening sweep did it because the
 * gate no longer counts as layered once the iris flag is cleared; a closing one because a
 * sweeping gate is not restacked as anybody moves. Walking round the other way left the rings
 * already covered with no wormhole behind them.
 *
 * <p>The gate faces south, so its front is the larger z. The ring is at z=0 and the far layer of
 * a viewer in front at z=-1, which is the near side for anybody behind. The opening is five by
 * five, which the rows sweep draws in three rings, so a viewer can cross between two of them
 * with cells covered either side of the crossing. The scheduler runs nothing on its own: each
 * step is run by hand, with the viewers moved in between.
 */
class IrisSweepCrossingTest
{
    /** In front of the gate, square on, near enough that the ring hides every far cell. */
    private static final double FRONT = 4.5;
    /** Behind it, the same distance off. */
    private static final double BEHIND = -3.5;

    private World world;
    private Stargate gate;
    private MockedStatic<MaterialUtils> materials;
    /** What the cells either side of the ring really hold, so a hand-back can be told from a draw. */
    private final BlockData truth = mock(BlockData.class);
    private final BlockData ice = mock(BlockData.class);
    private final BlockData packed = mock(BlockData.class);
    private final LinkedHashMap<Integer, Runnable> pending = new LinkedHashMap<>();
    private int nextTaskId = 1;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.isEnabled()).thenReturn(true);
        PluginTestSupport.install(plugin);
        PluginTestSupport.forgetAllGates();

        materials = mockStatic(MaterialUtils.class);
        materials.when(() -> MaterialUtils.drawnAcross(any(Material.class), any())).thenReturn(mock(BlockData.class));
        materials.when(() -> MaterialUtils.drawnAcross(eq(Material.BLUE_ICE), any())).thenReturn(ice);
        materials.when(() -> MaterialUtils.drawnAcross(eq(Material.PACKED_ICE), any())).thenReturn(packed);
        materials.when(() -> MaterialUtils.isAirMaterial(Material.AIR)).thenReturn(true);
        materials.when(() -> MaterialUtils.cullsWaterBehindIt(Material.YELLOW_STAINED_GLASS)).thenReturn(Boolean.TRUE);
        materials.when(() -> MaterialUtils.shownBehindGlassAs(Material.WATER, false)).thenReturn(Material.BLUE_ICE);
        materials.when(() -> MaterialUtils.shownBehindGlassAs(Material.WATER, true)).thenReturn(Material.PACKED_ICE);

        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenAnswer(invocation ->
        {
            final int id = nextTaskId++;
            pending.put(id, invocation.getArgument(1));
            return id;
        });
        doAnswer(invocation ->
        {
            pending.remove(invocation.<Integer>getArgument(0));
            return null;
        }).when(scheduler).cancelTask(anyInt());
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        gate = new Stargate();
        gate.setGateName("Crossing");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateActive(true);
        gate.setGatePortalOpen(true);
        gate.setGateCustom(true);
        gate.setGateCustomIrisMaterial(Material.YELLOW_STAINED_GLASS);
        gate.setGateCustomPortalMaterial(Material.WATER);
        // Rows, in from the top and bottom: three rings on five rows.
        gate.setGateIrisAnimation("rows");
        for (int x = -2; x <= 2; x++)
        {
            for (int y = -2; y <= 2; y++)
            {
                gate.getGatePortalBlocks().add(new Location(world, x, 64 + y, 0));
                for (final int z : new int[] {-1, 0, 1})
                {
                    airAt(x, 64 + y, z);
                }
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateIrisAnimator.cancelAll();
        materials.close();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /** An air block that hands back {@link #truth}. */
    private void airAt(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(block.getBlockData()).thenReturn(truth);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        when(world.getBlockAt(x, y, z)).thenReturn(block);
    }

    /** A viewer standing at this z, square on to the gate. */
    private Player viewerAt(final double z)
    {
        final Player viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getWorld()).thenReturn(world);
        stand(viewer, z);
        return viewer;
    }

    private void stand(final Player viewer, final double z)
    {
        when(viewer.getLocation()).thenReturn(new Location(world, 0.5, 64, z));
    }

    private void watching(final Player... viewers)
    {
        when(world.getPlayers()).thenReturn(List.of(viewers));
    }

    /** Runs the one step the sweep has booked. */
    private void step()
    {
        assertFalse(pending.isEmpty(), "a step is booked");
        final Integer id = pending.keySet().iterator().next();
        pending.remove(id).run();
    }

    private void finishSweep()
    {
        while (!pending.isEmpty())
        {
            step();
        }
    }

    /** The rings in the order the sweep draws them, each as its cells' "x,y". */
    private List<List<String>> rings(final boolean closing)
    {
        final List<List<String>> out = new ArrayList<>();
        for (final List<Location> ring : IrisSweepDriver.rings(gate.getGatePortalBlocks(),
            gate.getEffectiveIrisAnimation(), closing))
        {
            out.add(ring.stream().map(at -> at.getBlockX() + "," + at.getBlockY()).toList());
        }
        assertEquals(3, out.size(), "three rings, so a crossing can fall between two with cells covered either side");
        return out;
    }

    /** The cells of these rings, sorted. */
    @SafeVarargs
    private static List<String> cellsOf(final List<String>... rings)
    {
        final TreeSet<String> cells = new TreeSet<>();
        for (final List<String> ring : rings)
        {
            cells.addAll(ring);
        }
        return new ArrayList<>(cells);
    }

    /**
     * What one viewer is left looking at in the plane a block behind the ring, folded in as it is sent.
     *
     * <p>The picture, not the packets: the last block sent to each cell is what their client holds.
     */
    private static final class FarPlane
    {
        private final Player viewer;
        private final Map<String, BlockData> last = new HashMap<>();

        FarPlane(final Player viewer)
        {
            this.viewer = viewer;
        }

        /** Folds in what has been sent since the last look, and clears it. */
        private void absorb()
        {
            final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
            final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
            verify(viewer, atLeast(0)).sendBlockChange(where.capture(), what.capture());
            for (int i = 0; i < where.getAllValues().size(); i++)
            {
                final Location at = where.getAllValues().get(i);
                if (at.getBlockZ() == -1)
                {
                    last.put(at.getBlockX() + "," + at.getBlockY(), what.getAllValues().get(i));
                }
            }
            clearInvocations(viewer);
        }

        /** The cells, sorted, whose last block is one of these. */
        List<String> showing(final BlockData... wanted)
        {
            absorb();
            final TreeSet<String> cells = new TreeSet<>();
            last.forEach((cell, data) ->
            {
                for (final BlockData one : wanted)
                {
                    if (data == one)
                    {
                        cells.add(cell);
                    }
                }
            });
            return new ArrayList<>(cells);
        }
    }

    /** The cells holding the stand-in for this viewer, sorted. */
    private List<String> iced(final FarPlane plane)
    {
        return plane.showing(ice, packed);
    }

    /**
     * An opening sweep takes the stand-in off the near side of a viewer who walks round mid-open.
     *
     * <p>The bug as reported. In front with the iris shut, the viewer holds the stand-in behind
     * every cell; one ring uncovers and they walk round. Every cell still covered then had its ice
     * on their side of the gate for the rest of the sweep.
     */
    @Test
    void anOpeningSweepTakesTheStandInOffTheNearSideOfAViewerWhoWalksRound()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        gate.toggleIrisActive(false);
        finishSweep();
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), iced(plane),
            "shut, the viewer in front holds the stand-in behind every cell");

        gate.toggleIrisActive(false);
        assertEquals(cellsOf(rings.get(1), rings.get(2)), iced(plane), "the first ring is uncovered");
        stand(viewer, BEHIND);
        step();

        assertEquals(List.of(), iced(plane), "behind the gate now, they hold no ice on their own side");
        assertTrue(plane.showing(truth).containsAll(rings.get(2)),
            "the ring still covered was handed back, not merely left out: " + plane.showing(truth));
    }

    /**
     * A closing sweep takes it off as well.
     *
     * <p>The closing sweep was thought safe because the gate counts as layered while it runs, but a
     * sweeping gate is not restacked as anybody moves, so the ice already behind the covered rings
     * stayed where it was once they walked round.
     */
    @Test
    void aClosingSweepTakesTheStandInOffTheNearSideOfAViewerWhoWalksRound()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(true);
        gate.toggleIrisActive(false);
        step();
        assertEquals(cellsOf(rings.get(0), rings.get(1)), iced(plane), "two rings covered, each with the ice behind it");

        stand(viewer, BEHIND);
        step();

        assertEquals(List.of(), iced(plane), "behind the gate now, they hold no ice on their own side");
        assertTrue(plane.showing(truth).containsAll(cellsOf(rings.get(0), rings.get(1))),
            "the rings already covered were handed back: " + plane.showing(truth));
    }

    /**
     * Walking round to the front mid-close brings the wormhole in behind every ring already covered.
     *
     * <p>Behind the gate the viewer had no far layer to be given, so the rings covered while they
     * were there went on showing the landscape through the glass after they came round, and only
     * the rings still to come had the wormhole behind them.
     */
    @Test
    void walkingRoundToTheFrontMidCloseBringsTheWormholeBehindEveryRingAlreadyCovered()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(true);
        gate.toggleIrisActive(false);
        step();
        assertEquals(List.of(), iced(plane), "behind, nothing is drawn a block behind the ring");

        stand(viewer, FRONT);
        step();

        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), iced(plane),
            "every ring covered so far, not only the one just drawn");
    }

    /** And mid-open, behind every ring still covered. */
    @Test
    void walkingRoundToTheFrontMidOpenBringsTheWormholeBehindEveryRingStillCovered()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        gate.toggleIrisActive(false);
        finishSweep();
        gate.toggleIrisActive(false);
        assertEquals(List.of(), iced(plane), "behind, nothing is drawn a block behind the ring");

        stand(viewer, FRONT);
        step();

        assertEquals(cellsOf(rings.get(2)), iced(plane), "the one ring still covered, and no other");
    }

    /**
     * A viewer who crosses and crosses back ends every step with the layer on their far side only.
     *
     * <p>Each step is checked, in both directions: in front, the stand-in behind exactly the cells
     * covered so far; behind, none of it on their side.
     */
    @ParameterizedTest(name = "closing: {0}")
    @ValueSource(booleans = {true, false})
    void aViewerWhoCrossesAndCrossesBackHasTheLayerOnlyOnTheirFarSideAfterEveryStep(final boolean closing)
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(closing);
        startSweep(closing);
        assertEquals(covered(rings, closing, 0), iced(plane), "in front after the first step");

        stand(viewer, BEHIND);
        step();
        assertEquals(List.of(), iced(plane), "behind after the second");

        stand(viewer, FRONT);
        step();
        assertEquals(covered(rings, closing, 2), iced(plane), "in front again after the third");
    }

    /**
     * Two viewers who swap sides mid-sweep are each drawn their own far layer.
     *
     * <p>Per viewer, not per gate: one viewer's crossing must not move anything for the other, and
     * each must end the step with their own picture.
     */
    @ParameterizedTest(name = "closing: {0}")
    @ValueSource(booleans = {true, false})
    void twoViewersWhoSwapSidesMidSweepAreEachDrawnTheirOwn(final boolean closing)
    {
        final Player front = viewerAt(FRONT);
        final Player rear = viewerAt(BEHIND);
        watching(front, rear);
        final FarPlane frontPlane = new FarPlane(front);
        final FarPlane rearPlane = new FarPlane(rear);
        final List<List<String>> rings = rings(closing);
        startSweep(closing);
        assertEquals(covered(rings, closing, 0), iced(frontPlane), "the one in front");
        assertEquals(List.of(), iced(rearPlane), "the one behind");

        stand(front, BEHIND);
        stand(rear, FRONT);
        step();

        assertEquals(List.of(), iced(frontPlane), "the first, now behind");
        assertEquals(covered(rings, closing, 1), iced(rearPlane), "the second, now in front");
    }

    /** Starts a sweep through the iris toggle; an opening one from an iris shut and settled first. */
    private void startSweep(final boolean closing)
    {
        if (!closing)
        {
            gate.toggleIrisActive(false);
            finishSweep();
        }
        gate.toggleIrisActive(false);
    }

    /**
     * A viewer who leaves range is sent nothing, and coming back round the other side takes their ice back.
     *
     * <p>Out of range nothing is sent, as before: they keep what they hold until the next redraw.
     * What they hold is still theirs, though, so when they come back on the other side of the gate
     * the ice behind the rings covered before they left is handed back.
     */
    @Test
    void aViewerWhoLeavesRangeIsSentNothingAndComingBackRoundTakesTheirIceBack()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(true);
        gate.toggleIrisActive(false);
        assertEquals(cellsOf(rings.get(0)), iced(plane), "the first ring covered, with the ice behind it");

        stand(viewer, 200.5);
        step();
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        stand(viewer, BEHIND);
        step();
        assertEquals(List.of(), iced(plane), "back behind the gate, no ice on their side");
        assertTrue(plane.showing(truth).containsAll(rings.get(0)),
            "the ring covered before they left was handed back: " + plane.showing(truth));
    }

    /**
     * A viewer back from out of range mid-open is drawn no layer behind cells uncovered while they were away.
     *
     * <p>The other half of keeping what an absent viewer holds: the cells that opened while they were
     * gone are no longer covered, so coming back round to the front must not bring the wormhole in
     * behind them. Behind the gate when it began, they never had a far layer there to keep.
     */
    @Test
    void aViewerBackFromOutOfRangeMidOpenIsDrawnNoLayerBehindCellsThatOpenedWhileTheyWereAway()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        startSweep(false);
        assertEquals(List.of(), iced(plane), "behind, nothing is drawn a block behind the ring");
        stand(viewer, -200.5);
        step();
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        stand(viewer, FRONT);
        step();

        verify(viewer, atLeastOnce()).sendBlockChange(argThat(at -> at.getBlockZ() == 0), any(BlockData.class));
        assertEquals(List.of(), iced(plane),
            "drawn the last ring opening, in range again, and every ring is open, so nothing behind any of them: "
                + rings.get(1));
    }

    /**
     * A viewer restacked between the iris shutting and opening starts the opening from what they were restacked to.
     *
     * <p>Shut while they stood in front, then walked round with the iris settled, then opened: what
     * the opening starts from is the picture their walk drew them, not what the closing sweep had left.
     * Coming back round mid-open then brings the wormhole in behind the cells still covered.
     */
    @Test
    void aViewerRestackedBetweenSweepsStartsTheOpeningFromWhatTheyWereRestackedTo()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        StargateManager.addStargate(gate);
        gate.toggleIrisActive(false);
        finishSweep();
        stand(viewer, BEHIND);
        StargateManager.relayerFor(viewer, new Location(world, 0.5, 64, BEHIND));
        assertEquals(List.of(), iced(plane), "restacked from behind, nothing is drawn a block behind the ring");

        stand(viewer, FRONT);
        gate.toggleIrisActive(false);

        assertEquals(cellsOf(rings.get(1), rings.get(2)), iced(plane),
            "back in front as it opens, the wormhole behind the rings still covered");
    }

    /**
     * A closing sweep called off by opening the iris leaves no ice on the near side of a viewer who has walked round.
     *
     * <p>They walked round between two steps of the closing, which never ran the second. The opening
     * starts from the rings the closing covered, so what the viewer holds from it is still known to
     * be theirs and is handed back at the opening's first step.
     */
    @Test
    void aClosingCalledOffByOpeningLeavesNoIceOnTheNearSideOfAViewerWhoWalksRound()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> closingRings = rings(true);
        gate.toggleIrisActive(false);
        step();
        assertEquals(cellsOf(closingRings.get(0), closingRings.get(1)), iced(plane), "two rings closed");

        stand(viewer, BEHIND);
        gate.toggleIrisActive(false);

        assertTrue(StargateIrisAnimator.isSweeping(gate), "the closing was called off and an opening started");
        assertEquals(List.of(), iced(plane), "behind the gate now, no ice on their side");
        assertTrue(plane.showing(truth).containsAll(cellsOf(closingRings.get(0), closingRings.get(1))),
            "what they held from the closing was handed back: " + plane.showing(truth));
    }

    /**
     * An opening sweep called off by shutting the iris again takes back the ice of a viewer who has walked round.
     *
     * <p>The closing sweep that replaces it starts from an opening with nothing covered, so whatever
     * far layer is left from the opening is handed back as it starts.
     */
    @Test
    void anOpeningCalledOffByShuttingLeavesNoIceOnTheNearSideOfAViewerWhoWalkedRound()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        gate.toggleIrisActive(false);
        finishSweep();
        gate.toggleIrisActive(false);
        assertEquals(cellsOf(rings.get(1), rings.get(2)), iced(plane), "the first ring uncovered");

        stand(viewer, BEHIND);
        gate.toggleIrisActive(false);

        assertEquals(List.of(), iced(plane), "behind the gate, the shutting leaves no ice on their side");
        assertTrue(plane.showing(truth).containsAll(cellsOf(rings.get(1), rings.get(2))),
            "the cells the opening had left covered were handed back: " + plane.showing(truth));
    }

    /**
     * An opening called off by shutting starts the closing from nothing covered, for a viewer who stayed in front too.
     *
     * <p>The closing sweep paints the whole opening bare before its first ring, so the ice the opening
     * had left behind its covered cells is behind bare cells now. It goes as the closing starts and
     * comes back with each ring, rather than hanging behind the wormhole until the rings reach it.
     */
    @Test
    void anOpeningCalledOffByShuttingStartsTheClosingWithNothingCovered()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> opening = rings(false);
        final List<List<String>> closing = rings(true);
        startSweep(false);
        assertEquals(cellsOf(opening.get(1), opening.get(2)), iced(plane), "the first ring uncovered");

        gate.toggleIrisActive(false);

        assertEquals(cellsOf(closing.get(0)), iced(plane), "only the closing's first ring has the wormhole behind it");
    }

    /** The cells covered after this step: closing, the rings so far; opening, the rings still to go. */
    private static List<String> covered(final List<List<String>> rings, final boolean closing, final int step)
    {
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rings.size(); i++)
        {
            if (closing == (i <= step))
            {
                cells.addAll(rings.get(i));
            }
        }
        return cellsOf(cells);
    }
}
