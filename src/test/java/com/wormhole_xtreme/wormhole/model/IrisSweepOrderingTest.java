package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * The real iris blocks are committed at the cautious edge of a sweep, never the other one.
 *
 * <p>This is the property the whole design of the animation rests on, and the one that would
 * be silently lost by a plausible-looking reordering. {@link Stargate#isGateIrisActive()} is
 * the single answer four separate call sites trust -- travel refusal for players and vehicles,
 * the portal redraw, and block protection -- and it flips the instant the iris is toggled. The
 * sweep therefore has to keep the *blocks* at least as solid as the *picture* at every moment:
 *
 * <ul>
 * <li>Closing, the blocks go in before the first ring is drawn, so the barrier exists before
 * it looks like it does.</li>
 * <li>Opening, the blocks come out after the last ring is drawn, so the barrier outlasts the
 * picture of it.</li>
 * </ul>
 *
 * <p>Get either backwards and there is a window, several ticks wide, in which a gate that
 * reads as shut has nothing in it. The scheduler here never runs anything on its own: each
 * step is driven by hand so the ordering can be observed rather than raced.
 */
class IrisSweepOrderingTest
{
    private World world;
    private Player watcher;
    private Stargate gate;
    private WormholeXTreme plugin;
    /**
     * {@code MaterialUtils.drawnAcross} goes through {@code Material.createBlockData}, which
     * needs a live server. Held open for the whole class because every test here has somebody
     * watching, and every draw reaches it.
     */
    private MockedStatic<MaterialUtils> materials;
    /** What {@link Material#AIR} is drawn as, so the uncovering can be recognised. */
    private final BlockData bareOpening = mock(BlockData.class);
    /** What {@link Material#WATER} is drawn as, so an event horizon can be told from empty air. */
    private final BlockData openWater = mock(BlockData.class);
    /** Tasks the sweep has booked and not had cancelled, in the order they were booked. */
    private final java.util.LinkedHashMap<Integer, Runnable> pending = new java.util.LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private int nextTaskId = 1;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        // A mock reports itself disabled unless told otherwise, and a disabled plugin does not
        // sweep -- which is the whole of the guard the last test here covers.
        when(plugin.isEnabled()).thenReturn(true);
        PluginTestSupport.install(plugin);
        PluginTestSupport.forgetAllGates();

        materials = mockStatic(MaterialUtils.class);
        materials.when(() -> MaterialUtils.drawnAcross(any(Material.class), any()))
            .thenAnswer(i ->
            {
                final Material asked = i.getArgument(0);
                if (asked == Material.AIR)
                {
                    return bareOpening;
                }
                return (asked == Material.WATER) ? openWater : mock(BlockData.class);
            });

        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        // "booked" is recorded when the sweep asks for its next step, not when that step is
        // run. The difference is the whole of the closing assertion: a sweep started before
        // the blocks were placed still runs its first step after them, so timing the run
        // rather than the booking would hold whichever way round the two were done.
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(invocation ->
            {
                final int id = nextTaskId++;
                pending.put(id, invocation.getArgument(1));
                events.add("booked");
                return id;
            });
        // Cancelling really does drop the task, so a sweep that was called off and one that
        // was not are told apart by what is left waiting.
        doAnswer(invocation ->
        {
            pending.remove(invocation.<Integer>getArgument(0));
            return null;
        }).when(scheduler).cancelTask(anyInt());
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        // Somebody has to be watching, or every draw stops at the nobody-is-near check and
        // the sweep's whole visible effect goes unobserved -- which is how an opening sweep
        // that drew the iris back over itself passed for a while.
        watcher = mock(Player.class);
        // A real player always has one, and the layering files what it has drawn them under it.
        when(watcher.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(watcher.getLocation()).thenReturn(new Location(world, 0, 64, 3));
        when(world.getPlayers()).thenReturn(List.of(watcher));

        gate = new Stargate();
        gate.setGateName("IrisGate");
        gate.setGateWorld(world);
        // A three-by-three opening, so the sweep has three rings to get wrong.
        for (int x = -1; x <= 1; x++)
        {
            for (int y = -1; y <= 1; y++)
            {
                final Location at = new Location(world, x, 64 + y, 0);
                gate.getGatePortalBlocks().add(at);
                final Block block = mock(Block.class);
                when(block.getLocation()).thenReturn(at);
                when(world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ())).thenReturn(block);
                // Every real placement is recorded, whichever way round it happens.
                doAnswer(i ->
                {
                    events.add("block:" + i.getArgument(0));
                    return null;
                }).when(block).setType(any(Material.class));
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateIrisAnimator.cancelAll();
        if (materials != null)
        {
            materials.close();
        }
        PluginTestSupport.scheduler(null);
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /**
     * Grows the opening to a square of this radius, the way the fixture builds its own.
     *
     * @param radius
     *            how far out from the middle, so a radius of nine is nineteen by nineteen
     */
    private void widenOpeningTo(final int radius)
    {
        for (int x = -radius; x <= radius; x++)
        {
            for (int y = -radius; y <= radius; y++)
            {
                if ((Math.abs(x) <= 1) && (Math.abs(y) <= 1))
                {
                    continue; // the fixture's own three-by-three
                }
                final Location at = new Location(world, x, 64 + y, 0);
                gate.getGatePortalBlocks().add(at);
                final Block block = mock(Block.class);
                when(block.getLocation()).thenReturn(at);
                when(world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ())).thenReturn(block);
                doAnswer(i ->
                {
                    events.add("block:" + i.getArgument(0));
                    return null;
                }).when(block).setType(any(Material.class));
            }
        }
    }

    /**
     * A gate wider than the limit sweeps in the limit's steps, not one per ring.
     *
     * <p>The cap is arithmetic in {@link IrisSweep} and a setting in {@code ConfigManager}, and
     * both are pinned on their own. What nothing pinned is the wire between them: this is the
     * only place a real gate's iris asks for it, and the fixtures everywhere else are small
     * enough to sit inside the cap, so dropping the argument here left every test passing and
     * every big gate back to taking six seconds.
     */
    @Test
    void aGateWiderThanTheLimitSweepsInTheLimitsSteps()
    {
        widenOpeningTo(9);
        final int cap = com.wormhole_xtreme.wormhole.config.ConfigManager.getGateIrisMaxSteps();
        assertTrue(IrisSweep.closingRings(gate.getGatePortalBlocks()).size() > cap,
            "a nineteen-wide opening must have more rings than the cap, or this proves nothing");

        gate.toggleIrisActive(false);
        runSweepToCompletion();

        assertEquals(cap, events.stream().filter("booked"::equals).count(),
            "a step is booked per band drawn, so a gate with more rings than the cap must still "
                + "book only the cap's worth -- one per ring is the crossing the cap exists to stop");
    }

    /** Runs every step the sweep has booked, including ones booked by those steps. */
    private void runSweepToCompletion()
    {
        int guard = 0;
        while (!pending.isEmpty() && (guard++ < 50))
        {
            final Integer id = pending.keySet().iterator().next();
            final Runnable next = pending.remove(id);
            events.add("step");
            next.run();
        }
    }

    /**
     * A closing sweep moves the wormhole behind each ring it covers, while it is still running.
     *
     * <p>The sweep hands every ring it reaches to whoever is following it, and a gate with a
     * see-through iris uses that to move the wormhole behind the ring as the iris arrives --
     * otherwise every pane of glass lands with the landscape behind it and the wormhole appears
     * in one jump at the end.
     *
     * <p>This is the wiring rather than the arithmetic. Emptying the sweep's hand-off left every
     * other test here green, because the fixture's gate is not layered and the follower does
     * nothing for it: the gate has to be dialled, facing and drawn in something that hides
     * water before any of this is reachable at all.
     */
    @Test
    void aClosingSweepMovesTheWormholeBehindEachRingAsItGoes()
    {
        final BlockData ice = mock(BlockData.class);
        final BlockData packed = mock(BlockData.class);
        glassIrisOverAWormhole(mock(BlockData.class));
        materials.when(() -> MaterialUtils.drawnAcross(eq(Material.BLUE_ICE), any())).thenReturn(ice);
        materials.when(() -> MaterialUtils.drawnAcross(eq(Material.PACKED_ICE), any())).thenReturn(packed);

        gate.toggleIrisActive(false);

        assertTrue(StargateIrisAnimator.isSweeping(gate),
            "the sweep is still running -- at the end the layers are stacked anyway");
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        verify(watcher, atLeastOnce()).sendBlockChange(where.capture(),
            argThat(data -> (data == ice) || (data == packed)));
        assertTrue(where.getAllValues().stream().anyMatch(at -> at.getBlockZ() == -1),
            "and the wormhole went a block behind the ring: " + where.getAllValues());
    }

    /**
     * And an opening sweep gives that cell back a ring at a time, as each ring uncovers.
     *
     * <p>The closing one read backwards, and the half that was never reached. {@code setIrisState}
     * writes the iris flag before it draws, so by the time the opening sweep runs the gate no
     * longer counts as layered -- and the hand-off is guarded on exactly that. Every ring of every
     * open returned at the first line, and the wormhole's stand-in sat behind the uncovered rings
     * for the length of the sweep before vanishing all at once at the end.
     *
     * <p>Asserted with the sweep still running, for the same reason as the closing test: at the
     * end {@code takeBackLayers} hands the lot back and it always looked right.
     */
    @Test
    void anOpeningSweepGivesTheCellBehindBackAsEachRingUncovers()
    {
        final BlockData truth = mock(BlockData.class);
        glassIrisOverAWormhole(truth);
        // Shut first, with the layers standing, so there is something to give back.
        gate.toggleIrisActive(false);
        finishSweep();
        clearInvocations(watcher);

        gate.toggleIrisActive(false);

        assertTrue(StargateIrisAnimator.isSweeping(gate),
            "the sweep is still running -- at the end takeBackLayers hands it back anyway");
        verify(watcher, atLeastOnce()).sendBlockChange(
            argThat(at -> at.getBlockZ() == -1), eq(truth));
    }

    /**
     * A gate nobody has dialled draws no wormhole behind its iris, sweep or no sweep.
     *
     * <p>An iris works on an idle gate -- that is the whole point of one -- and there is no
     * wormhole to move behind it. Without this the hand-off would put a sheet of water, or the
     * ice that stands in for one, a block behind an opening with nothing in it, and the gate
     * would read as dialled from the back.
     *
     * <p>Not "nothing is sent there": the truth is, which is a hand-back telling the client what
     * really stands in that cell, and is right whether or not the gate was ever dialled. What
     * must not be sent there is a picture of something else.
     */
    @Test
    void anUndialledGatesIrisMovesNoWormholeBehindIt()
    {
        final BlockData truth = mock(BlockData.class);
        glassIrisOverAWormhole(truth);
        // The same gate, never dialled. Everything else about it is unchanged, so what the
        // sweep does differently is down to this alone.
        gate.setGateActive(false);

        gate.toggleIrisActive(false);

        assertTrue(StargateIrisAnimator.isSweeping(gate),
            "a sweep is actually running -- an undialled gate still sweeps, and without this the"
                + " assertion below would hold for a gate that never drew anything at all");
        verify(watcher, never()).sendBlockChange(argThat(at -> at.getBlockZ() == -1),
            argThat(drawn -> drawn != truth));
    }

    /**
     * A dialled, south-facing gate with a see-through iris, and open air a block behind the ring
     * for the far layer to stand in.
     *
     * @param truth
     *            what those cells really hold, so a hand-back can be told from a draw
     */
    private void glassIrisOverAWormhole(final BlockData truth)
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateActive(true);
        gate.setGateCustom(true);
        gate.setGateCustomIrisMaterial(Material.YELLOW_STAINED_GLASS);
        gate.setGateCustomPortalMaterial(Material.WATER);
        // Both sides: the far layer stands behind, and taking the layers back asks after the
        // cell in front as well, which a viewer round the other side would have had.
        for (int x = -1; x <= 1; x++)
        {
            for (int y = -1; y <= 1; y++)
            {
                for (final int z : new int[] {-1, 1})
                {
                    final Block beside = mock(Block.class);
                    when(beside.getType()).thenReturn(Material.AIR);
                    when(beside.getBlockData()).thenReturn(truth);
                    when(beside.getLocation()).thenReturn(new Location(world, x, 64 + y, z));
                    when(world.getBlockAt(x, 64 + y, z)).thenReturn(beside);
                }
            }
        }
        materials.when(() -> MaterialUtils.isAirMaterial(Material.AIR)).thenReturn(true);
        materials.when(() -> MaterialUtils.cullsWaterBehindIt(Material.YELLOW_STAINED_GLASS))
            .thenReturn(Boolean.TRUE);
        materials.when(() -> MaterialUtils.shownBehindGlassAs(Material.WATER, false))
            .thenReturn(Material.BLUE_ICE);
        materials.when(() -> MaterialUtils.shownBehindGlassAs(Material.WATER, true))
            .thenReturn(Material.PACKED_ICE);
    }

    /** Runs every step the sweep has booked, so the one after it starts from a settled gate. */
    private void finishSweep()
    {
        while (!pending.isEmpty())
        {
            final Runnable step = pending.values().iterator().next();
            pending.remove(pending.keySet().iterator().next());
            step.run();
        }
    }

    @Test
    void closingPlacesTheBlocksBeforeItDrawsTheFirstRing()
    {
        gate.toggleIrisActive(false);

        assertTrue(events.stream().anyMatch(e -> e.startsWith("block:")),
            "the iris blocks are placed, not merely drawn: " + events);
        assertTrue(StargateIrisAnimator.isSweeping(gate),
            "and a sweep is actually running -- without this the ordering below holds vacuously");

        final int firstBlock = indexOfFirst("block:");
        final int firstBooked = events.indexOf("booked");
        assertTrue(firstBooked >= 0, "the sweep booked a step: " + events);
        assertTrue(firstBlock < firstBooked,
            "the barrier must exist before the sweep is even started; got " + events);
    }

    /**
     * An iris that moves makes its noise.
     *
     * <p>It did not, on any path a player can reach. {@code toggleIrisActive} flipped the flag
     * and then handed {@code setIrisState} the value it had just written, so the check for
     * whether the iris had moved compared a value against itself and came back false every
     * time -- and the sound hangs off exactly that check. The lever, the commands and dialling
     * all go through that method, which is every way a player has of working an iris.
     */
    @Test
    void anIrisThatMovesIsHeardToMove()
    {
        gate.setGatePlayerTeleportLocation(new Location(world, 0, 64, 2));

        gate.toggleIrisActive(false);

        verify(world, atLeastOnce()).playSound(
            any(Location.class), anyString(),
            any(SoundCategory.class), anyFloat(),
            anyFloat());
    }

    /**
     * A sweeping ring is drawn in the gate's own plane, like everything else in the opening.
     *
     * <p>The sweep builds its block data down its own path rather than through the layered draw,
     * so it is the one that could quietly keep the game's default. Asserted on the call, because
     * the stub above answers to any facing at all: a sweep that dropped the gate's would hand
     * back the very same block data and every ordering test here would still pass.
     *
     * <p>Pinned on the water, not on "some material with the right facing". A closing sweep hides
     * the whole opening as it was a moment ago before it lets any ring through, and that hide is
     * the only thing in the flow drawn in the portal material -- the iris draw before it is the
     * iris, and the far layer beside it is the ice stand-in. Written the loose way it passed
     * whatever {@code sendCells} did, because the iris draw that runs first already satisfied it.
     */
    @Test
    void aSweepingRingIsDrawnInTheGatesOwnPlane()
    {
        glassIrisOverAWormhole(mock(BlockData.class));

        gate.toggleIrisActive(false);

        materials.verify(() -> MaterialUtils.drawnAcross(Material.WATER, BlockFace.SOUTH),
            atLeastOnce());
        materials.verify(() -> MaterialUtils.drawnAcross(any(Material.class), isNull()),
            never());
    }

    @Test
    void openingDrawsEveryRingBeforeItTakesTheBlocksAway()
    {
        gate.toggleIrisActive(false);
        runSweepToCompletion();
        events.clear();

        gate.toggleIrisActive(false);
        final boolean anyBlockBeforeSweepEnds = events.stream().anyMatch(e -> e.startsWith("block:"));
        assertFalse(anyBlockBeforeSweepEnds,
            "opening must not clear the blocks until the sweep has finished drawing: " + events);

        runSweepToCompletion();
        assertTrue(events.stream().anyMatch(e -> e.startsWith("block:")),
            "and it must actually clear them once it has: " + events);
    }

    /**
     * Toggling again mid-sweep calls the first one off rather than letting the two interleave.
     *
     * <p>Two sweeps running at once would draw each other's rings in whatever order the
     * scheduler happened to fire them, which on a lever somebody is flipping back and forth
     * leaves whichever finishes last showing -- and that is not necessarily the state the gate
     * is actually in.
     */
    @Test
    void togglingAgainPartWayThroughCallsOffTheFirstSweep()
    {
        gate.toggleIrisActive(false);
        assertTrue(StargateIrisAnimator.isSweeping(gate), "a sweep is running");
        assertEquals(1, pending.size(), "waiting on one step");

        gate.toggleIrisActive(false);

        assertEquals(1, pending.size(),
            "the first sweep's step must have been dropped, not left waiting beside the second's: "
                + pending.keySet());
        runSweepToCompletion();
        assertFalse(StargateIrisAnimator.isSweeping(gate), "and the second one finished cleanly");
    }

    /**
     * Setting the iris to the state it is already in still calls off a sweep that is running.
     *
     * <p>The iris is redrawn either way, and a sweep left running paints its next ring over the
     * redrawn picture. The case that shows it is a gate closing onto an iris that defaults shut
     * while its closing sweep is still going: shutdown asks for the iris it already has, hands
     * back the layers drawn either side of the ring, and the stale sweep then repainted the
     * ring over them for everyone, front and back alike.
     */
    @Test
    void settingTheIrisItAlreadyHasCallsOffARunningSweep()
    {
        gate.toggleIrisActive(false);
        assertTrue(StargateIrisAnimator.isSweeping(gate), "a closing sweep is running");
        final boolean shut = gate.isGateIrisActive();

        StargateLifecycle.setIrisState(gate, shut);

        assertFalse(StargateIrisAnimator.isSweeping(gate),
            "a redraw to the same state must not leave the old sweep painting over it");
        assertTrue(pending.isEmpty(), "and its next step is not left booked: " + pending.keySet());
    }

    /**
     * An opening sweep is actually seen to uncover the gate.
     *
     * <p>The iris blocks stay where they are until the sweep ends, so a step that sends what is
     * really in a cell paints the iris straight back over itself and nothing appears to happen.
     * That is precisely what it did: the material to draw was passed in and then never used,
     * and the ordering tests above all still passed, because they time the sweep rather than
     * look at it. SonarCloud noticed the unused parameter; nothing else did.
     */
    @Test
    void openingActuallyUncoversTheOpeningRatherThanRedrawingTheIris()
    {
        gate.toggleIrisActive(false);
        runSweepToCompletion();
        clearInvocations(watcher);

        // Only up to the first ring, deliberately. Letting the sweep finish brings the final
        // fillGateInterior with it, which redraws the whole opening as air for everybody -- so
        // the bare opening turns up in the captured draws whether the sweep drew it or not,
        // and the assertion below would hold against the very bug it is here to catch.
        gate.toggleIrisActive(false);

        final ArgumentCaptor<BlockData> drawn = ArgumentCaptor.forClass(BlockData.class);
        verify(watcher, atLeastOnce()).sendBlockChange(any(Location.class), drawn.capture());
        assertTrue(drawn.getAllValues().stream().anyMatch(d -> d == bareOpening),
            "the first ring has to be drawn as the bare opening over an iris that is still "
                + "standing there, or the open is invisible");
    }

    /**
     * Closing over a live wormhole hides the iris behind the water, not behind nothing.
     *
     * <p>Reported from a server: closing the iris on a dialled gate showed the event horizon
     * being replaced by empty air as the rings came in, rather than by the iris arriving over
     * water still standing.
     *
     * <p>The cells not yet covered are drawn as whatever the opening looked like a moment
     * earlier, and on an open gate that is the horizon. Drawing them as air says the wormhole
     * has gone, a beat before the iris says anything at all.
     */
    @Test
    void closingOverALiveWormholeHidesTheIrisBehindTheWater()
    {
        gate.setGateActive(true);
        clearInvocations(watcher);

        gate.toggleIrisActive(false);

        final ArgumentCaptor<BlockData> drawn = ArgumentCaptor.forClass(BlockData.class);
        verify(watcher, atLeastOnce()).sendBlockChange(any(Location.class), drawn.capture());
        assertTrue(drawn.getAllValues().stream().anyMatch(d -> d == openWater),
            "the opening should be held as water while the iris sweeps over it");
        assertFalse(drawn.getAllValues().stream().anyMatch(d -> d == bareOpening),
            "and never as empty air, which reads as the wormhole having closed");
    }

    /**
     * A closing sweep hides the finished iris before it reveals it a ring at a time.
     *
     * <p>The blocks are placed first, so the server has already told every client what is
     * there. Without drawing the opening back over them the iris is simply present, and the
     * rings that follow reveal something already visible -- the animation runs and shows
     * nothing.
     */
    @Test
    void closingHidesTheFinishedIrisBeforeSweepingItIn()
    {
        clearInvocations(watcher);

        gate.toggleIrisActive(false);

        final ArgumentCaptor<BlockData> drawn = ArgumentCaptor.forClass(BlockData.class);
        verify(watcher, atLeastOnce()).sendBlockChange(any(Location.class), drawn.capture());
        assertTrue(drawn.getAllValues().stream().anyMatch(d -> d == bareOpening),
            "the opening is drawn back over the placed iris before the sweep starts revealing it");
    }

    /**
     * A server on its way down closes irises, and must not try to animate them.
     *
     * <p>Bukkit sets a plugin disabled before it calls {@code onDisable}, and a disabled plugin
     * booking a task is refused with an exception rather than ignored. Shutdown closes the iris
     * of every gate whose iris defaults closed, so a server stopping with one dialled gate of
     * that kind would start a sweep, throw out of {@code shutdownStargate}, and land in the
     * catch wrapping the whole save — taking the remaining gates, the rings, the beams, the
     * mirrors and the database shutdown with it. The blocks still go where they belong; only
     * the picture is skipped, and by then nobody is looking at it.
     */
    @Test
    void aPluginOnItsWayDownPlacesTheIrisWithoutAnimatingIt()
    {
        when(plugin.isEnabled()).thenReturn(false);

        gate.toggleIrisActive(false);

        assertFalse(StargateIrisAnimator.isSweeping(gate), "no sweep is started while disabling");
        assertTrue(pending.isEmpty(), "and nothing is booked on a scheduler that would refuse it");
        assertTrue(events.stream().anyMatch(e -> e.startsWith("block:")),
            "but the iris blocks are still placed, which is the part that matters: " + events);
    }

    /**
     * No plugin at all is not "still running" either.
     *
     * <p>The scheduler and the plugin are separate statics, so one can be there without the
     * other. Reading a missing plugin as running would book a task against null and throw from
     * somewhere less obvious than here.
     */
    @Test
    void aMissingPluginIsNotTreatedAsRunning() throws Exception
    {
        PluginTestSupport.remove();

        gate.toggleIrisActive(false);

        assertFalse(StargateIrisAnimator.isSweeping(gate), "no plugin, no sweep");
        assertTrue(pending.isEmpty(), "and nothing booked against it");
    }

    /** The index of the first event with this prefix, or {@link Integer#MAX_VALUE}. */
    private int indexOfFirst(final String prefix)
    {
        for (int i = 0; i < events.size(); i++)
        {
            if (events.get(i).startsWith(prefix))
            {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * A drawn iris ends its closing sweep drawn everywhere, not with rings left open.
     *
     * <p>Reported on a Standard gate with the spiral sweep: closing the iris left cells in the
     * middle open until the player looked away and back. The sweep revealed each ring as "what
     * is really in the cell", which was the iris while the iris was real blocks -- and is air now
     * that an upright gate's iris is only drawn. The rest looked shut only because a redraw
     * happened to paint over most of it.
     */
    @Test
    void aDrawnIrisSweepsClosedToTheIrisInEveryCell()
    {
        gate.setGateFacing(org.bukkit.block.BlockFace.NORTH);
        assertTrue(StargateBlockSetup.irisIsDrawn(gate), "an upright gate, whose iris is drawn");
        // The layer positions a block either side of the ring, which a shut iris hands back.
        final Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(1))).thenReturn(air);
        when(world.getBlockAt(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(-1))).thenReturn(air);

        // The first ring is drawn by the toggle itself, so nothing is cleared in between: what
        // counts is the last thing each cell was shown.
        gate.toggleIrisActive(false);
        runSweepToCompletion();

        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(watcher, atLeastOnce()).sendBlockChange(where.capture(), what.capture());
        final java.util.Map<List<Integer>, BlockData> last = new java.util.HashMap<>();
        for (int i = 0; i < where.getAllValues().size(); i++)
        {
            final Location at = where.getAllValues().get(i);
            // The ring only: the cells either side of it are the layers, not the sweep's.
            if (at.getBlockZ() == 0)
            {
                last.put(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ()), what.getAllValues().get(i));
            }
        }
        assertEquals(9, last.size(), "every cell of the opening was drawn by the sweep");
        for (final java.util.Map.Entry<List<Integer>, BlockData> cell : last.entrySet())
        {
            assertTrue((cell.getValue() != null) && (cell.getValue() != bareOpening),
                "cell " + cell.getKey() + " was left showing the air the server really has there");
        }
    }

    /**
     * Opening a drawn iris part-way through its closing sweep does not blank it first.
     *
     * <p>The half-finished sweep is called off by sending every cell as it stands. That ran after
     * the iris flag had already flipped to open, so a drawn iris was "finished" as the air behind
     * it and vanished in one frame, and the opening sweep then had nothing to be seen taking away.
     */
    @Test
    void openingADrawnIrisMidSweepFinishesTheClosingAsTheIris()
    {
        gate.setGateFacing(org.bukkit.block.BlockFace.NORTH);
        // Opening draws the horizon behind the opening, one block either side, so those are air.
        final Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(1))).thenReturn(air);
        when(world.getBlockAt(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(-1))).thenReturn(air);
        gate.toggleIrisActive(false);
        final Integer next = pending.keySet().iterator().next();
        pending.remove(next).run();
        clearInvocations(watcher);

        gate.toggleIrisActive(false);

        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(watcher, atLeastOnce()).sendBlockChange(where.capture(), what.capture());
        final java.util.Map<List<Integer>, BlockData> last = new java.util.HashMap<>();
        for (int i = 0; i < where.getAllValues().size(); i++)
        {
            final Location at = where.getAllValues().get(i);
            last.put(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ()), what.getAllValues().get(i));
        }
        assertEquals(9, last.size(), "the call-off draws every cell");
        for (final java.util.Map.Entry<List<Integer>, BlockData> cell : last.entrySet())
        {
            assertNotNull(cell.getValue(),
                "cell " + cell.getKey() + " was blanked to the air behind the drawn iris");
        }
    }

    /**
     * A gate that shuts while its iris is opening calls the sweep off (#434). The sweep went on
     * painting the wormhole it had started with into the idle gate, and its last step filled the
     * opening with it: a wormhole standing in a shut gate for everybody nearby, until a chunk
     * reload. Only a gate whose iris is shut by default had its sweep called off on the way down.
     */
    @Test
    void aGateShuttingMidOpeningSweepPaintsNoWormholeAfterward()
    {
        gate.setGateActive(true);
        gate.setGatePlayerTeleportLocation(new Location(world, 0, 64, 2));
        gate.toggleIrisActive(false);
        finishSweep();
        gate.toggleIrisActive(false);
        assertTrue(StargateIrisAnimator.isSweeping(gate), "the iris is part way open");

        try (MockedStatic<com.wormhole_xtreme.wormhole.utils.WorldUtils> utils =
            mockStatic(com.wormhole_xtreme.wormhole.utils.WorldUtils.class))
        {
            gate.shutdownStargate(false, com.wormhole_xtreme.wormhole.events.StargateShutdownEvent.Reason.TIMEOUT);
        }
        clearInvocations(watcher);
        finishSweep();

        assertFalse(StargateIrisAnimator.isSweeping(gate), "the sweep is called off");
        verify(watcher, never()).sendBlockChange(any(Location.class), eq(openWater));
    }

    /**
     * A gate removed while its iris sweeps calls the sweep off (#434), as a shutdown does. A
     * refresh removes the gate and registers a fresh one in its place, so a sweep left running
     * would draw over the new gate and finish by filling it with what it started with.
     */
    @Test
    void aGateRemovedMidSweepCallsTheSweepOff()
    {
        gate.setGateActive(true);
        gate.toggleIrisActive(false);
        finishSweep();
        gate.toggleIrisActive(false);
        assertTrue(StargateIrisAnimator.isSweeping(gate), "the iris is part way open");

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            StargateManager.removeStargate(gate, null, false);
        }
        clearInvocations(watcher);
        finishSweep();

        assertFalse(StargateIrisAnimator.isSweeping(gate), "the sweep is called off");
        verify(watcher, never()).sendBlockChange(any(Location.class), eq(openWater));
    }

    /**
     * A built iris removed part way through opening comes down anyway (#434). An opening sweep
     * leaves a built iris standing until its last step, and calling the sweep off drops that step:
     * /wormhole remove, which opens an iris-coded gate's iris and then removes the gate, left a
     * horizontal gate's iris blocks in the world for good. Found by a Fable review.
     */
    @Test
    void aBuiltIrisCalledOffMidOpeningIsTakenDown()
    {
        // This fixture's gate has no facing, so its iris is real blocks, as a horizontal gate's is.
        gate.toggleIrisActive(false);
        finishSweep();
        gate.toggleIrisActive(false);
        assertTrue(StargateIrisAnimator.isSweeping(gate), "the iris is part way open, its blocks still standing");
        events.clear();

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            StargateManager.removeStargate(gate, null, false);
        }

        assertEquals(gate.getGatePortalBlocks().size(), events.stream().filter("block:AIR"::equals).count(),
            "every iris block is taken down, now rather than at a last step that will not come: " + events);
    }

    /**
     * Dialling a gate whose iris is part way open calls the sweep off (#434), so its remaining
     * rings do not paint the opening it started from, bare air here, over the wormhole forming.
     * Found by a Fable review.
     */
    @Test
    void diallingMidSweepCallsTheSweepOff()
    {
        gate.setGatePlayerTeleportLocation(new Location(world, 0, 64, 2));
        gate.toggleIrisActive(false);
        finishSweep();
        gate.toggleIrisActive(false);
        assertTrue(StargateIrisAnimator.isSweeping(gate), "the iris is part way open");

        com.wormhole_xtreme.wormhole.events.GateEvents.setDispatcherForTest(event -> { });
        try (MockedStatic<com.wormhole_xtreme.wormhole.utils.WorldUtils> utils =
            mockStatic(com.wormhole_xtreme.wormhole.utils.WorldUtils.class))
        {
            StargateDialManager.dialStargate(gate, true);
        }
        finally
        {
            com.wormhole_xtreme.wormhole.events.GateEvents.setDispatcherForTest(null);
        }

        assertFalse(StargateIrisAnimator.isSweeping(gate), "the sweep is called off as the gate opens");
    }
}
