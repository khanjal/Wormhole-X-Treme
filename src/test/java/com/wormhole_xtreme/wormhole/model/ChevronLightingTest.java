package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.lang.reflect.Field;
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
        final Field plugin = WormholeXTreme.class.getDeclaredField("thisPlugin");
        plugin.setAccessible(true);
        plugin.set(null, mock(WormholeXTreme.class));

        final Field scheduler = WormholeXTreme.class.getDeclaredField("scheduler");
        scheduler.setAccessible(true);
        scheduler.set(null, mock(BukkitScheduler.class));

        world = mock(World.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        final Field plugin = WormholeXTreme.class.getDeclaredField("thisPlugin");
        plugin.setAccessible(true);
        plugin.set(null, null);

        final Field scheduler = WormholeXTreme.class.getDeclaredField("scheduler");
        scheduler.setAccessible(true);
        scheduler.set(null, null);
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
                new ArrayList<Location>(Collections.singletonList(new Location(world, i, 64, 0))));
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
}
