package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * How long an iris takes to cross, as against how fast it moves while it does.
 *
 * <p>{@code gate-iris-step-ticks} is a pace, and a pace answers nothing about duration on its
 * own: how many steps there are to pace is the opening's geometry. {@code Standard} has five
 * rings and {@code Grand} sixty-one, so at the same two ticks a step one closes in half a
 * second and the other in six -- and the gate somebody waits longest on is the big one they
 * built to be impressive. The pace cannot fix it either, because a step cannot be shorter than
 * a tick, so {@code gate-iris-sweep-max-ticks} is the other end of it and the steps are merged
 * to keep to it.
 *
 * <p>These pin the arithmetic between the two settings. {@code IrisSweepTest} pins what the
 * merging then does to the cells.
 */
class IrisSweepPaceSettingTest
{
    @AfterEach
    void forgetThem()
    {
        ConfigTestSupport.clear();
    }

    /**
     * The cap and the pace together say how many steps there may be.
     *
     * <p>Twenty ticks at two ticks a step is ten steps, whatever the gate: a sweep that would
     * have been ten rings is untouched, and one that would have been sixty is drawn in ten
     * bands of six.
     */
    @Test
    void theCapAndThePaceTogetherGiveTheStepCount()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_SWEEP_MAX_TICKS, 20);
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_STEP_TICKS, 2);

        assertEquals(10, ConfigManager.getGateIrisMaxSteps());
    }

    /**
     * A slower pace inside the same limit means fewer, thicker steps rather than a longer sweep.
     *
     * <p>The limit is what the operator asked for; the pace is how it is spent. Raising the pace
     * without this would push the crossing past the limit, which is the bug the limit exists for.
     */
    @Test
    void aSlowerPaceSpendsTheSameLimitInFewerSteps()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_SWEEP_MAX_TICKS, 20);
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_STEP_TICKS, 5);

        assertEquals(4, ConfigManager.getGateIrisMaxSteps());
    }

    /**
     * A limit too short for the pace still leaves two steps, not one.
     *
     * <p>A crossing of one step is the instant iris written the long way round, and a server
     * that set a short limit asked for a fast sweep rather than for no sweep. The same reasoning
     * floors {@code gate-iris-step-ticks} at a tick.
     */
    @Test
    void aLimitTooShortForThePaceStillAnimates()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_SWEEP_MAX_TICKS, 1);
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_STEP_TICKS, 20);

        assertEquals(2, ConfigManager.getGateIrisMaxSteps());
    }

    /**
     * Zero is no limit, which is what every version before this one did.
     *
     * <p>Nothing else may mean it: a cap of zero steps would draw no iris at all, so the value
     * has to be read as "do not merge" rather than passed through as a count.
     */
    @Test
    void zeroIsNoLimitRatherThanNoSteps()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_SWEEP_MAX_TICKS, 0);

        assertEquals(0, ConfigManager.getGateIrisMaxSteps());
    }

    /**
     * A negative is read as no limit, and an absurd one is brought back to ten seconds.
     *
     * <p>Both ends matter for the same reason the pace is clamped: this is a number a server
     * owner types, and a mistyped one should cost them the timing they wanted rather than the
     * iris. A negative reaching the step count would be a cap of minus something, which merges
     * nothing while claiming to.
     */
    @Test
    void anImpossibleLimitIsBroughtBackIntoRange()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_SWEEP_MAX_TICKS, -5);
        assertEquals(0, ConfigManager.getGateIrisSweepMaxTicks(), "a negative is no limit, never a negative one");

        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_SWEEP_MAX_TICKS, 5000);
        assertEquals(200, ConfigManager.getGateIrisSweepMaxTicks(), "and ten seconds is already far past useful");
    }

    /**
     * The shipped default holds a crossing to a second, which every bundled shape now meets.
     *
     * <p>{@code Grand} is the widest opening this plugin ships at eighteen by seventeen, and
     * before the limit it took six seconds to close at the default pace.
     */
    @Test
    void theDefaultHoldsACrossingToAboutASecond()
    {
        ConfigTestSupport.loadDefaults();

        final int ticks = ConfigManager.getGateIrisMaxSteps() * ConfigManager.getGateIrisStepTicks();
        assertTrue(ticks <= 20, "a crossing of " + ticks + " ticks is long enough to stand there waiting for");
        assertTrue(ConfigManager.getGateIrisMaxSteps() > 2,
            "and it still has steps enough to read as a sweep rather than a blink");
    }
}
