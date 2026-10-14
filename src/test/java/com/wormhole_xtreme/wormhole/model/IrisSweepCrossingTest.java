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
import com.wormhole_xtreme.wormhole.PrivateStatics;
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
 * <p>The ring itself is drawn per viewer too (#429). It used to be one block for everybody, the front's
 * picture, so a viewer behind watched the iris replace their wormhole a ring at a time and jump behind it
 * once the sweep had settled. The ring belongs to whoever's near layer it is, and only the far layer moves.
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
    /** What the see-through iris is drawn as, so an iris block can be told from the stand-in. */
    private final BlockData glass = mock(BlockData.class);
    /** What the wormhole is drawn as in the ring, so it can be told from the iris and from the real air. */
    private final BlockData water = mock(BlockData.class);
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
        final BlockData anyOther = mock(BlockData.class);
        materials.when(() -> MaterialUtils.drawnAcross(any(Material.class), any())).thenReturn(anyOther);
        materials.when(() -> MaterialUtils.drawnAcross(eq(Material.YELLOW_STAINED_GLASS), any())).thenReturn(glass);
        materials.when(() -> MaterialUtils.drawnAcross(eq(Material.WATER), any())).thenReturn(water);
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
        swept().clear();
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
     * What one viewer is left looking at in the ring and a block either side of it, folded in as it is sent.
     *
     * <p>The picture, not the packets: the last block sent to each cell is what their client holds.
     * One per viewer, because each look clears what the viewer was sent.
     */
    private static final class FarPlane
    {
        private final Player viewer;
        /** By plane, z = -1, 0 or 1, then by cell. */
        private final Map<Integer, Map<String, BlockData>> last = new HashMap<>();

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
                last.computeIfAbsent(at.getBlockZ(), z -> new HashMap<>())
                    .put(at.getBlockX() + "," + at.getBlockY(), what.getAllValues().get(i));
            }
            clearInvocations(viewer);
        }

        /** The cells, sorted, a block behind the ring whose last block is one of these. */
        List<String> showing(final BlockData... wanted)
        {
            return showingAt(-1, wanted);
        }

        /** The cells, sorted, in one plane whose last block is one of these. */
        List<String> showingAt(final int z, final BlockData... wanted)
        {
            absorb();
            final TreeSet<String> cells = new TreeSet<>();
            last.getOrDefault(z, Map.of()).forEach((cell, data) ->
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

        // The ring is the wormhole's for them already, so the step reaches them a block in front of it instead.
        assertEquals(cellsOf(rings.get(0), rings.get(2)), plane.showingAt(1, truth),
            "drawn the last ring opening, in range again: the iris they held beyond it was handed back");
        assertEquals(List.of(), iced(plane),
            "and every ring is open, so nothing behind any of them: " + rings.get(1));
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

    /** The cells a viewer holds the iris in a block in front of the ring, sorted. */
    private List<String> irisInFront(final FarPlane plane)
    {
        return plane.showingAt(1, glass);
    }

    /**
     * Walking round to the front mid-open takes back the iris a viewer behind held a block in front of the ring.
     *
     * <p>From behind, the iris is drawn a block beyond the ring, on the side they are not. Walk round to
     * the front and that is their side: a solid block their client will not let them walk through, which
     * the server does not have. Only the far horizon used to be moved, so it stayed until the sweep ended.
     */
    @Test
    void walkingRoundToTheFrontMidOpenTakesBackTheIrisHeldInFrontOfTheRing()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        gate.toggleIrisActive(false);
        finishSweep();
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), irisInFront(plane),
            "shut, the viewer behind holds the iris a block in front of every cell");
        gate.toggleIrisActive(false);

        stand(viewer, FRONT);
        step();

        assertEquals(List.of(), irisInFront(plane), "in front now, no iris is left on their side");
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), plane.showingAt(1, truth),
            "every cell a block in front was handed back");
        assertEquals(cellsOf(rings.get(2)), iced(plane), "and the ring still covered has the wormhole behind it");
    }

    /**
     * A viewer who stays behind mid-open sees the iris beyond the ring leave with the ring that uncovers it.
     *
     * <p>The same hand-back with no crossing: the iris a block in front goes ring by ring, not all at the end.
     */
    @Test
    void theIrisBeyondTheRingLeavesWithItsRingForAViewerBehind()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        startSweep(false);

        assertEquals(cellsOf(rings.get(1), rings.get(2)), irisInFront(plane), "the rings still covered keep it");
        assertEquals(cellsOf(rings.get(0)), plane.showingAt(1, truth), "the ring uncovered gives it back");
    }

    /**
     * Walking round to the back mid-open gives the ring still covered back to the wormhole, and the iris goes beyond it.
     *
     * <p>The sweep used to paint the iris in the ring for everybody, so a viewer who walked round kept it there until
     * the sweep settled: the front's picture, from behind (#429). Now the ring is the near layer's, which from behind
     * is the wormhole, and the iris they are still owed is drawn a block beyond it.
     */
    @Test
    void walkingRoundToTheBackMidOpenGivesTheCoveredRingBackToTheWormhole()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        startSweep(false);
        assertEquals(cellsOf(rings.get(1), rings.get(2)), plane.showingAt(0, glass), "the covered rings show the iris");

        stand(viewer, BEHIND);
        step();

        assertEquals(List.of(), plane.showingAt(0, glass), "no iris left in the ring");
        assertEquals(cellsOf(rings.get(2)), irisInFront(plane), "the ring still covered has it a block beyond");
        assertEquals(List.of(), iced(plane), "and no stand-in on their side");
    }

    /**
     * A viewer whose placement has not changed is sent nothing for the cells the step does not reach.
     *
     * <p>Every covered cell is looked at on every step, which is only affordable because nothing is sent
     * for one that stays as it is.
     */
    @Test
    void aViewerWhoStaysWhereTheyAreIsSentNothingForCellsTheStepDoesNotReach()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(false);
        startSweep(false);
        iced(plane);

        step();

        verify(viewer, never()).sendBlockChange(argThat(at -> (at.getBlockZ() != 0)
            && rings.get(2).contains(at.getBlockX() + "," + at.getBlockY())), any(BlockData.class));
        assertEquals(cellsOf(rings.get(0), rings.get(1)), plane.showing(truth),
            "the rings uncovered so far were handed back, and only those");
        assertEquals(List.of(), plane.showingAt(0, truth), "and the ring is the sweep's: nothing hands its cells back");
    }

    /**
     * Walking round to the front mid-close takes back nothing they were never drawn, and draws no iris beyond the ring.
     */
    @Test
    void walkingRoundToTheFrontMidCloseLeavesNoIrisInFrontOfTheRing()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(true);
        gate.toggleIrisActive(false);
        step();
        stand(viewer, FRONT);
        step();

        assertEquals(List.of(), irisInFront(plane), "no iris a block in front of the ring");
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), iced(plane), "the wormhole behind every ring");
    }

    /**
     * An opening called off by shutting takes back the iris a viewer behind held beyond the ring.
     *
     * <p>The closing starts with nothing covered, so an iris a block in front of a cell it has not reached is a
     * block nobody else has. It goes as the closing starts, and comes back beyond each ring as the closing covers it.
     */
    @Test
    void anOpeningCalledOffByShuttingTakesBackTheIrisHeldBeyondTheRing()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> opening = rings(false);
        final List<List<String>> closing = rings(true);
        startSweep(false);
        assertEquals(cellsOf(opening.get(1), opening.get(2)), irisInFront(plane), "the rings still covered keep it");

        gate.toggleIrisActive(false);

        assertEquals(cellsOf(closing.get(0)), irisInFront(plane), "beyond the closing's first ring, and no other");
        assertEquals(cellsOf(closing.get(1), closing.get(2)), plane.showingAt(1, truth),
            "every cell the closing has not reached was handed back");
    }

    /**
     * The stand-in's shimmer waits for a sweep to finish.
     *
     * <p>The shimmer redraws from the picture last drawn whole. An opening called off by shutting leaves
     * that picture from before the opening, so a tick during the closing put the stand-in back behind
     * rings the closing had not reached yet.
     */
    @Test
    void theShimmerSendsNothingWhileASweepRuns()
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        StargateManager.addStargate(gate);
        startSweep(false);
        gate.toggleIrisActive(false);
        assertTrue(StargateIrisAnimator.isSweeping(gate), "the opening was called off and a closing started");
        iced(plane);

        StargateManager.tickIrisHorizon();
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        finishSweep();
        iced(plane);
        StargateManager.tickIrisHorizon();
        assertFalse(iced(plane).isEmpty(), "once it has finished the shimmer runs again");
    }

    /** The private record of what the sweeps have drawn whom. */
    private static Map<UUID, Map<String, Map<Integer, IrisLayering.Placement>>> swept() throws ReflectiveOperationException
    {
        return PrivateStatics.of(StargateBlockSetup.class, "SWEPT");
    }

    /** Whether a viewer has a sweep record for the fixture's gate. */
    private boolean recorded(final Player viewer) throws ReflectiveOperationException
    {
        final Map<String, Map<Integer, IrisLayering.Placement>> byGate = swept().get(viewer.getUniqueId());
        return (byGate != null) && byGate.containsKey(gate.getGateName());
    }

    /** A closing sweep one step in, with the viewer in front holding a record. */
    private Player midClose() throws ReflectiveOperationException
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        gate.toggleIrisActive(false);
        assertTrue(recorded(viewer), "mid-sweep, the viewer has a record");
        return viewer;
    }

    /**
     * A sweep that ends forgets everybody's record, out of range too.
     *
     * <p>Kept until quit, a record outlived the sweep it described and was read by the next one.
     */
    @Test
    void aSweepThatEndsForgetsEverybodysRecordOutOfRangeToo() throws ReflectiveOperationException
    {
        final Player viewer = midClose();
        stand(viewer, 200.5);

        finishSweep();

        assertFalse(recorded(viewer), "gone, though they were out of range at the end");
    }

    /** A gate removed forgets every record kept for it, so one registered again by that name starts clean. */
    @Test
    void aRemovedGateForgetsEveryRecordKeptForIt() throws ReflectiveOperationException
    {
        final Player viewer = midClose();

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            StargateManager.removeStargate(gate, null, false);
        }

        assertFalse(recorded(viewer), "the gate has gone, and its records with it");
    }

    /** A viewer who quits is forgotten. */
    @Test
    void aViewerWhoQuitsIsForgotten() throws ReflectiveOperationException
    {
        final Player viewer = midClose();

        StargateManager.forgetPortalVisuals(viewer.getUniqueId());

        assertFalse(recorded(viewer), "forgotten with everything else drawn for them");
    }

    /** A whole draw replaces the record, which no longer says what they hold. */
    @Test
    void aWholeDrawDropsTheRecord() throws ReflectiveOperationException
    {
        final Player viewer = midClose();

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        assertFalse(recorded(viewer), "the whole draw says what they hold now");
    }

    /**
     * A gate with no name sweeps without recording anything, and without throwing.
     *
     * <p>The records are filed by name, and the maps they live in refuse a null key.
     */
    @Test
    void aGateWithNoNameSweepsWithoutRecordingAnything() throws ReflectiveOperationException
    {
        final Player viewer = viewerAt(FRONT);
        watching(viewer);
        gate.setGateName(null);

        StargateIrisAnimator.sweepClosed(gate, Material.WATER);
        finishSweep();
        StargateIrisAnimator.sweepOpen(gate, Material.WATER, null);
        step();

        assertFalse(swept().containsKey(viewer.getUniqueId()) && !swept().get(viewer.getUniqueId()).isEmpty(),
            "nothing filed for them: " + swept().get(viewer.getUniqueId()));
        verify(viewer, atLeastOnce()).sendBlockChange(argThat(at -> at.getBlockZ() == 0), any(BlockData.class));
    }

    /** Every block sent to one viewer a block in front of the ring, as "x,y", in order. */
    private static List<String> sentInFront(final Player viewer)
    {
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        verify(viewer, atLeast(0)).sendBlockChange(where.capture(), any(BlockData.class));
        return where.getAllValues().stream().filter(at -> at.getBlockZ() == 1)
            .map(at -> at.getBlockX() + "," + at.getBlockY()).toList();
    }

    /**
     * A viewer behind who walks round to the front mid-close is handed back the iris beyond the ring, once per cell.
     *
     * <p>The sweep draws a viewer behind the iris a block beyond each ring it covers (#429), so coming round to the
     * front that block is on their own side and goes: once for each cell they were drawn it in, and for no other.
     */
    @Test
    void aViewerBehindWhoWalksRoundMidCloseIsHandedBackOnlyTheIrisTheyWereDrawn()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(true);
        gate.toggleIrisActive(false);
        step();
        assertEquals(cellsOf(rings.get(0), rings.get(1)), irisInFront(plane), "behind, the iris beyond the rings covered");

        stand(viewer, FRONT);
        step();

        assertEquals(cellsOf(rings.get(0), rings.get(1)), sentInFront(viewer).stream().sorted().toList(),
            "one hand-back a block in front for each cell they held the iris in, and none for the ring just drawn");
        assertEquals(List.of(), irisInFront(plane), "so none is left on their side");
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), plane.showingAt(0, glass),
            "the iris came into the ring of every cell covered");
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), iced(plane),
            "and the wormhole in behind every one of them");
    }

    /**
     * A closing called off by an opening never puts the iris in the ring of a viewer behind.
     *
     * <p>The call-off finishes the closing as its last step would have, each viewer from their own side, and the
     * opening starts from that. Finished as one block for everybody, a viewer behind was shown the iris over their
     * wormhole, and an opening that leaves their ring alone would have left it there until it settled.
     */
    @Test
    void aClosingCalledOffByOpeningNeverPutsTheIrisInTheRingOfAViewerBehind()
    {
        final Player viewer = viewerAt(BEHIND);
        watching(viewer);
        final FarPlane plane = new FarPlane(viewer);
        final List<List<String>> rings = rings(true);
        gate.toggleIrisActive(false);
        step();
        assertEquals(cellsOf(rings.get(0), rings.get(1)), irisInFront(plane), "two rings closed, the iris beyond them");

        gate.toggleIrisActive(false);
        step();
        step();

        assertTrue(StargateIrisAnimator.isSweeping(gate), "the opening has not settled yet, which hands every cell back");
        verify(viewer, never()).sendBlockChange(argThat(at -> at.getBlockZ() == 0), eq(glass));
        verify(viewer, never()).sendBlockChange(argThat(at -> at.getBlockZ() == 0), eq(truth));
        assertEquals(List.of(), irisInFront(plane), "and the iris beyond the ring left with every ring");
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), plane.showingAt(1, truth),
            "every cell, the one the call-off covered too");
    }

    /**
     * A viewer the ring is sent to is a viewer the far layer is drawn for, on a gate wide enough to tell.
     *
     * <p>The ring was sent to whoever was near its own first cell, the far layer to whoever was near the
     * gate's first cell. On a big gate those are blocks apart, and a viewer between the two distances saw
     * every ring of glass arrive with nothing behind it.
     */
    @Test
    void aViewerTheRingReachesIsDrawnTheFarLayerToo()
    {
        final Player viewer = viewerAt(63.9);
        when(viewer.getLocation()).thenReturn(new Location(world, 2, 66, 63.9));
        watching(viewer);
        final Location corner = gate.getGatePortalBlocks().get(gate.getGatePortalBlocks().size() - 1);
        assertTrue(new Location(world, 2, 66, 63.9).distance(gate.getGatePortalBlocks().get(0)) > 64,
            "past the reach from the gate's first cell, or this proves nothing");
        assertTrue(new Location(world, 2, 66, 63.9).distance(corner) <= 64, "and within it from the ring's");

        StargateBlockSetup.sweepRing(gate, List.of(corner), Material.YELLOW_STAINED_GLASS, true);

        verify(viewer).sendBlockChange(argThat(at -> (at.getBlockX() == 2) && (at.getBlockY() == 66)
            && (at.getBlockZ() == -1)), argThat(d -> (d == ice) || (d == packed)));
    }

    /**
     * Closing, a viewer in front and a viewer behind the same gate are drawn the same ring differently (#429).
     *
     * <p>In front the iris comes into the ring. Behind, the ring keeps the wormhole and the iris arrives a block
     * beyond it. The ring used to be one block for everybody, so the viewer behind was shown the iris over their
     * wormhole until the sweep settled and moved it.
     */
    @Test
    void closingTheRingIsTheIrisInFrontAndTheWormholeBehind()
    {
        final Player front = viewerAt(FRONT);
        final Player rear = viewerAt(BEHIND);
        watching(front, rear);
        final FarPlane frontPlane = new FarPlane(front);
        final FarPlane rearPlane = new FarPlane(rear);
        final List<List<String>> rings = rings(true);

        gate.toggleIrisActive(false);

        assertEquals(cellsOf(rings.get(0)), frontPlane.showingAt(0, glass), "in front, the iris comes into the ring");
        assertEquals(cellsOf(rings.get(0)), iced(frontPlane), "with the wormhole a block behind it");
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), rearPlane.showingAt(0, water),
            "behind, every cell of the ring is still the wormhole, the one just covered too");
        assertEquals(cellsOf(rings.get(0)), rearPlane.showingAt(1, glass), "and the iris arrives a block beyond it");
        assertEquals(List.of(), iced(rearPlane), "with nothing on their own side");
    }

    /**
     * Opening, the same two viewers are sent different blocks for the same ring (#429).
     *
     * <p>In front the iris leaves the ring for the wormhole. Behind, the ring is not touched at all, because it has
     * been the wormhole all along, and the iris leaves from a block beyond it.
     */
    @Test
    void openingTheIrisLeavesTheRingInFrontAndFromBeyondItBehind()
    {
        final Player front = viewerAt(FRONT);
        final Player rear = viewerAt(BEHIND);
        watching(front, rear);
        final FarPlane frontPlane = new FarPlane(front);
        final FarPlane rearPlane = new FarPlane(rear);
        final List<List<String>> rings = rings(false);
        gate.toggleIrisActive(false);
        finishSweep();
        assertEquals(cellsOf(rings.get(0), rings.get(1), rings.get(2)), rearPlane.showingAt(1, glass),
            "shut, the viewer behind holds the iris a block beyond every cell");
        frontPlane.showingAt(0, glass);

        gate.toggleIrisActive(false);

        verify(rear, never()).sendBlockChange(argThat(at -> at.getBlockZ() == 0), any(BlockData.class));
        verify(front, atLeastOnce()).sendBlockChange(argThat(at -> at.getBlockZ() == 0), eq(water));
        assertEquals(cellsOf(rings.get(1), rings.get(2)), frontPlane.showingAt(0, glass),
            "in front, the first ring's iris gave way to the wormhole and only it");
        assertEquals(cellsOf(rings.get(0)), rearPlane.showingAt(1, truth), "behind, the iris left from beyond that ring");
        assertEquals(cellsOf(rings.get(1), rings.get(2)), rearPlane.showingAt(1, glass), "and from no other");
    }

    /**
     * A viewer behind is never sent anything but the wormhole in the ring, closing or opening, from the first ring
     * to the settle (#429).
     *
     * <p>The reported fault: toggling the iris replaced the water for a moment, then moved it in front of the water.
     * Opening it had a second cause beside the sweep. Calling off a sweep that was not running sent the iris to
     * every ring before the opening began, so a viewer behind lost the wormhole at once and got it back ring by ring.
     *
     * <p>The closing toggle draws the finished iris for everybody before its sweep hides it again, in the same
     * tick, and before the first ring. That one draw, which a viewer in front gets too, is all that is allowed
     * through: per cell, nothing after the first wormhole may be anything else.
     */
    @Test
    void aViewerBehindIsSentNothingButTheWormholeInTheRing()
    {
        final Player rear = viewerAt(BEHIND);
        watching(rear);

        gate.toggleIrisActive(false);
        finishSweep();
        final Map<String, List<BlockData>> closing = sentToRing(rear);
        assertFalse(closing.isEmpty(), "the ring was drawn for them, or this proves nothing");
        closing.forEach((cell, sent) ->
        {
            final List<BlockData> afterHidden = sent.subList(Math.max(0, sent.indexOf(water)), sent.size());
            assertTrue(!afterHidden.isEmpty() && afterHidden.stream().allMatch(data -> data == water),
                "closing, cell " + cell + " had its wormhole replaced: " + sent);
        });

        gate.toggleIrisActive(false);
        finishSweep();
        sentToRing(rear).forEach((cell, sent) -> assertTrue(sent.stream().allMatch(data -> data == water),
            "opening, cell " + cell + " had its wormhole replaced: " + sent));
    }

    /**
     * Somebody who comes near after an opening began is still shown each ring open.
     *
     * <p>They have nothing on record for the sweep to move, so the per-viewer path has nothing to work from. They
     * are shown the ring as the shared send always showed it, and the settle at the end does the rest.
     */
    @Test
    void aViewerArrivingMidOpenIsShownEachRingOpenAsBefore()
    {
        final Player viewer = viewerAt(200.5);
        watching(viewer);
        final List<List<String>> rings = rings(false);
        startSweep(false);
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        stand(viewer, FRONT);
        step();

        assertEquals(cellsOf(rings.get(1)), new FarPlane(viewer).showingAt(0, water),
            "the ring this step opened, and only it, drawn as the wormhole");
    }

    /** Every block sent to one viewer in the ring, by cell, in order; clears what they were sent. */
    private static Map<String, List<BlockData>> sentToRing(final Player viewer)
    {
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(viewer, atLeast(0)).sendBlockChange(where.capture(), what.capture());
        final Map<String, List<BlockData>> sent = new LinkedHashMap<>();
        for (int i = 0; i < where.getAllValues().size(); i++)
        {
            final Location at = where.getAllValues().get(i);
            if (at.getBlockZ() == 0)
            {
                sent.computeIfAbsent(at.getBlockX() + "," + at.getBlockY(), cell -> new ArrayList<>())
                    .add(what.getAllValues().get(i));
            }
        }
        clearInvocations(viewer);
        return sent;
    }

    /**
     * A gate with no wormhole open still draws its sweep as one block for everybody.
     *
     * <p>There is nothing to layer, so both sides see the same iris in the same ring, and the cheap shared send
     * stays: the per-viewer path is for a gate where no one block is right for everybody.
     */
    @Test
    void anIdleGatesSweepIsTheSameRingForEverybody()
    {
        gate.setGateActive(false);
        final Player front = viewerAt(FRONT);
        final Player rear = viewerAt(BEHIND);
        watching(front, rear);
        final FarPlane frontPlane = new FarPlane(front);
        final FarPlane rearPlane = new FarPlane(rear);
        final List<List<String>> rings = rings(true);

        gate.toggleIrisActive(false);
        step();

        assertEquals(cellsOf(rings.get(0), rings.get(1)), frontPlane.showingAt(0, glass), "in front, two rings of iris");
        assertEquals(cellsOf(rings.get(0), rings.get(1)), rearPlane.showingAt(0, glass), "and behind, the same two");
        assertEquals(List.of(), rearPlane.showingAt(1, glass), "with no iris drawn beyond the ring behind");
        assertEquals(List.of(), iced(frontPlane), "and no wormhole behind it in front");
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
