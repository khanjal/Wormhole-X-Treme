package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
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
     * {@code MaterialUtils.drawnAs} goes through {@code Material.createBlockData}, which needs
     * a live server. Held open for the whole class because every test here has somebody
     * watching, and every draw reaches it.
     */
    private MockedStatic<MaterialUtils> materials;
    /** What {@link Material#AIR} is drawn as, so the uncovering can be recognised. */
    private final BlockData bareOpening = mock(BlockData.class);
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
        materials.when(() -> MaterialUtils.drawnAs(any(Material.class)))
            .thenAnswer(i -> (i.getArgument(0) == Material.AIR) ? bareOpening : mock(BlockData.class));

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
}
