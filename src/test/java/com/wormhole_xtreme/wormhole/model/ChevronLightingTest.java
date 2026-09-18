package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * Lighting a gate's chevrons, one wave per tick.
 *
 * <p>{@code lightStargate(gate, true)} advances a counter and draws the wave that counter
 * names, rescheduling itself until it runs out of waves. The counter is incremented
 * <em>before</em> the wave is read, so the shape's wave count and the counter's range have
 * to agree exactly -- and nothing covered that.
 */
class ChevronLightingTest
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        PluginTestSupport.scheduler(mock(BukkitScheduler.class));

        world = mock(World.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();

        PluginTestSupport.scheduler(null);
    }

    /**
     * A real gate whose shape declares {@code waves} light waves, one block each.
     *
     * <p>Real rather than mocked on purpose: the whole question here is what the lighting
     * counter does across ticks, and a mocked getter would keep answering 0 however often
     * the setter was called -- which would make every one of these tests agree with itself
     * and with nothing else.
     */
    private Stargate gateWithLightWaves(final int waves)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("alpha");
        for (int i = 0; i < waves; i++)
        {
            gate.getGateLightBlocks().add(
                new ArrayList<>(Collections.singletonList(new Location(world, i, 64, 0))));
        }
        return gate;
    }

    /**
     * A shape whose light waves are numbered from zero.
     *
     * <p>Light waves are 1-based: {@code addToWave} pads the list up to {@code order + 1},
     * so index 0 is always the padding and {@code L#1} produces a two-element list. That is
     * why the lighting counter is incremented before the wave is read.
     *
     * <p>A shape written with {@code L#0} produces a one-element list instead, and the
     * counter still steps to 1 -- reading one past the end. No shipped shape does this;
     * a hand-written one can, and it took the gate down with an IndexOutOfBoundsException
     * on the first tick of its first dial.
     */
    @Test
    void aShapeNumberedFromZeroDoesNotFallOverOnTheFirstTick()
    {
        final Stargate gate = gateWithLightWaves(1);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            assertDoesNotThrow(() -> StargateAnimator.lightStargate(gate, true));
        }
    }

    /** A shape with several waves lights the one its counter names. */
    @Test
    void theCounterNamesTheWaveThatIsDrawn()
    {
        final Stargate gate = gateWithLightWaves(4);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            StargateAnimator.lightStargate(gate, true);

            blocks.verify(() -> StargateBlockSetup.drawLights(any(Stargate.class), any()));
        }
    }

    /** Darkening puts every wave away, whichever ones were actually lit. */
    @Test
    void darkeningUndrawsEveryWave()
    {
        final Stargate gate = gateWithLightWaves(3);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class))
        {
            StargateAnimator.lightStargate(gate, false);

            blocks.verify(() -> StargateBlockSetup.undrawBlocks(any(Stargate.class), any()),
                times(3));
        }
    }

    /** Darkening also resets the woosh counters, so the next opening starts from the top. */
    @Test
    void darkeningResetsTheAnimationCounters()
    {
        final Stargate gate = gateWithLightWaves(2);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class))
        {
            StargateAnimator.lightStargate(gate, false);
        }

        assertFalse(gate.isGateLightsActive());
        assertEquals(0, gate.getGateAnimationStep3D());
        assertFalse(gate.isGateAnimationRemoving());
    }

    /** Asked to light a gate whose lights are already off mid-sequence, it starts over. */
    @Test
    void aSequenceInterruptedPartWayThroughStartsAgain()
    {
        final Stargate gate = gateWithLightWaves(4);
        gate.setGateLightingCurrentIteration(2);
        gate.setGateLightsActive(false);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            StargateAnimator.lightStargate(gate, true);

            // It darkened rather than carrying on from 2. Checking the counter alone would
            // prove nothing: carrying on reaches the last wave and resets to 0 too, so both
            // paths leave the same number behind. Putting the lights away is what differs.
            blocks.verify(() -> StargateBlockSetup.undrawBlocks(any(Stargate.class), any()),
                atLeastOnce());
        }
        assertEquals(0, gate.getGateLightingCurrentIteration());
        assertFalse(gate.isGateLightsActive());
    }

    private static World world(final String name)
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn(name);
        return w;
    }

    /** A gate with chevrons 1 to 8 (index 0 is the padding a shape leaves), standing in a world. */
    private Stargate eightChevronGate(final String name, final World in)
    {
        final Stargate gate = gateWithLightWaves(9);
        gate.getGateLightBlocks().set(0, null);
        gate.setGateName(name);
        gate.setGateWorld(in);
        return gate;
    }

    /** A dial within one world stops at the seventh chevron; the eighth is for another world (#351). */
    @Test
    void aDialWithinOneWorldLightsSeven()
    {
        final World here = world("here");
        final Stargate gate = eightChevronGate("alpha", here);
        gate.setGateTarget(eightChevronGate("beta", world("here")));

        assertEquals(7, StargateAnimator.lastWave(gate, gate.getGateLightBlocks()));
    }

    /** A dial to another world lights the eighth, after the top one. */
    @Test
    void aDialToAnotherWorldLightsTheEighthLast()
    {
        final Stargate gate = eightChevronGate("alpha", world("here"));
        gate.setGateTarget(eightChevronGate("beta", world("there")));

        assertEquals(8, StargateAnimator.lastWave(gate, gate.getGateLightBlocks()));
    }

    /** The far gate has no target of its own, so it finds the gate dialling it. */
    @Test
    void theGateBeingDialledFromAnotherWorldLightsTheEighthToo()
    {
        final Stargate far = eightChevronGate("far", world("there"));
        final Stargate dialler = eightChevronGate("dialler", world("here"));
        dialler.setGateTarget(far);
        dialler.setGateActive(true);
        StargateManager.registerStargate(dialler);
        try
        {
            assertEquals(8, StargateAnimator.lastWave(far, far.getGateLightBlocks()));
        }
        finally
        {
            StargateManager.removeStargate(dialler);
        }
    }

    /** Pressing the button lights every chevron at once, the eighth too; the dial picks from them. */
    @Test
    void pressingTheButtonLightsEveryChevronAtOnce()
    {
        final Stargate gate = eightChevronGate("alpha", world("here"));

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            gate.lightAllChevrons();

            blocks.verify(() -> StargateBlockSetup.drawLights(eq(gate), any()), times(8));
            sounds.verify(() -> GateSounds.activated(gate));
        }
        assertEquals(true, gate.isGateLightsActive());
        assertEquals(8, StargateAnimator.lastShownWave(gate, gate.getGateLightBlocks()),
            "a player arriving while it waits for /dial sees every chevron lit");
    }

    /** Once open within one world, a player arriving sees the seven the dial used. */
    @Test
    void anOpenGateShowsTheChevronsItsDialUsed()
    {
        final Stargate gate = eightChevronGate("alpha", world("here"));
        gate.setGateTarget(eightChevronGate("beta", world("here")));
        gate.setGateLightsActive(true);
        gate.setGateActive(true);

        assertEquals(7, StargateAnimator.lastShownWave(gate, gate.getGateLightBlocks()));
    }

    /**
     * {@code /dial} on a gate lit by its button darkens it and dials the chevrons in order again,
     * without replaying the activation sound.
     */
    @Test
    void dialingAGateLitByItsButtonRelightsItInOrder()
    {
        final Stargate gate = eightChevronGate("alpha", world("here"));

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            gate.lightAllChevrons();
            gate.relightChevrons();

            blocks.verify(() -> StargateBlockSetup.undrawBlocks(eq(gate), any()), times(8));
            assertEquals(0, gate.getGateLightingCurrentIteration());
            assertEquals(true, gate.isGateLightsActive(), "still lit, so the sequence does not restart itself");

            StargateAnimator.lightStargate(gate, true);

            sounds.verify(() -> GateSounds.activated(gate), times(1));
            sounds.verify(() -> GateSounds.chevron(gate, 1, 7));
        }
    }

    /**
     * The last chevron holds a moment before the wormhole forms, rather than the woosh starting
     * the very next tick: the lock should read as the end of the sequence.
     */
    @Test
    void theLastChevronHoldsBeforeTheWormholeForms() throws Exception
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        final World here = world("here");
        final Stargate gate = eightChevronGate("alpha", here);
        gate.setGateTarget(eightChevronGate("beta", here));
        gate.setGateActive(true);
        gate.setGateLightsActive(true);
        gate.setGateLightingCurrentIteration(6);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            StargateAnimator.lightStargate(gate, true);

            sounds.verify(() -> GateSounds.locked(gate));
        }

        verify(scheduler).scheduleSyncDelayedTask(any(), any(Runnable.class),
            eq(Stargate.LAST_CHEVRON_PAUSE_TICKS));
        assertEquals(0, gate.getGateLightingCurrentIteration(), "the seventh was the last");
    }

    /** A chevron before the last locks with its own sound only. */
    @Test
    void anEarlierChevronDoesNotLockIn()
    {
        final Stargate gate = eightChevronGate("alpha", world("here"));
        gate.setGateLightsActive(true);
        gate.setGateLightingCurrentIteration(2);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            StargateAnimator.lightStargate(gate, true);

            sounds.verify(() -> GateSounds.chevron(gate, 3, 7));
            sounds.verify(() -> GateSounds.locked(gate), never());
        }
    }

    /**
     * A sign dial opens at once: every chevron its link needs lights together with the lock-in
     * sound, the woosh follows on the next tick, and no chevron sound plays from a gate that is
     * already open.
     */
    @Test
    void aSignDialOpensAtOnceWithoutTheChevronSequence() throws Exception
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        final World here = world("here");
        final Stargate gate = eightChevronGate("alpha", here);
        gate.setGateTarget(eightChevronGate("beta", here));
        gate.setGateActive(true);

        try (MockedStatic<StargateBlockSetup> blocks = mockStatic(StargateBlockSetup.class);
             MockedStatic<GateSounds> sounds = mockStatic(GateSounds.class))
        {
            StargateAnimator.openAtOnce(gate);

            blocks.verify(() -> StargateBlockSetup.drawLights(eq(gate), any()), times(7));
            sounds.verify(() -> GateSounds.locked(gate));
            sounds.verify(() -> GateSounds.chevron(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()), never());
        }
        verify(scheduler).scheduleSyncDelayedTask(any(), any(Runnable.class));
        assertEquals(true, gate.isGateLightsActive());
    }
}
