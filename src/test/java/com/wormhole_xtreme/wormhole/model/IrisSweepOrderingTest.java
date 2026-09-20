package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

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
    private Stargate gate;
    /** Tasks the sweep has booked and not had cancelled, in the order they were booked. */
    private final java.util.LinkedHashMap<Integer, Runnable> pending = new java.util.LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private int nextTaskId = 1;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        PluginTestSupport.forgetAllGates();

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
        org.mockito.Mockito.doAnswer(invocation ->
        {
            pending.remove(invocation.<Integer>getArgument(0));
            return null;
        }).when(scheduler).cancelTask(org.mockito.ArgumentMatchers.anyInt());
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getPlayers()).thenReturn(new ArrayList<>());

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
                org.mockito.Mockito.doAnswer(i ->
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

        org.mockito.Mockito.verify(world, org.mockito.Mockito.atLeastOnce()).playSound(
            any(Location.class), org.mockito.ArgumentMatchers.anyString(),
            any(org.bukkit.SoundCategory.class), org.mockito.ArgumentMatchers.anyFloat(),
            org.mockito.ArgumentMatchers.anyFloat());
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
