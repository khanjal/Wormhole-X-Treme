package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * How long an iris takes to cross, as against how fast it moves while it does.
 *
 * <p>The step is a pace, and a pace answers nothing about duration on its own: how many steps
 * there are to pace is the opening's geometry. {@code Standard} has five rings and {@code Grand}
 * sixty-one, so at the same two ticks a step one closes in half a second and the other in six.
 * A step cannot be shorter than a tick, so the sweep limit is the other end of it and the steps
 * are merged to keep to it.
 *
 * <p>These pin the arithmetic between the two. {@code IrisSweepTest} pins what the merging then
 * does to the cells.
 */
class IrisSweepPaceTest
{
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
        assertEquals(10, IrisSweepDriver.maxSteps(20, 2));
    }

    /** A slower pace inside the same limit means fewer, thicker steps rather than a longer sweep. */
    @Test
    void aSlowerPaceSpendsTheSameLimitInFewerSteps()
    {
        assertEquals(4, IrisSweepDriver.maxSteps(20, 5));
    }

    /**
     * A limit too short for the pace still leaves two steps, not one.
     *
     * <p>A crossing of one step is the instant iris written the long way round.
     */
    @Test
    void aLimitTooShortForThePaceStillAnimates()
    {
        assertEquals(2, IrisSweepDriver.maxSteps(1, 20));
    }

    /**
     * Zero is no limit, rather than no steps.
     *
     * <p>A cap of zero steps would draw no iris at all, so the value has to be read as "do not
     * merge" rather than passed through as a count. A test uses it to sweep a ring per step.
     */
    @Test
    void zeroIsNoLimitRatherThanNoSteps()
    {
        assertEquals(0, IrisSweepDriver.maxSteps(0, 2));
        assertEquals(0, IrisSweepDriver.maxSteps(-5, 2), "and a negative is no limit, never a negative one");
    }

    /**
     * The shipped pace holds a crossing to a second, which every bundled shape meets.
     *
     * <p>{@code Grand} is the widest opening this plugin ships at eighteen by seventeen, and
     * before the limit it took six seconds to close at the same pace.
     */
    @Test
    void theShippedPaceHoldsACrossingToAboutASecond()
    {
        IrisSweepDriver.resetPaceForTest();

        final int ticks = IrisSweepDriver.stepCap() * IrisSweepDriver.STEP_TICKS;
        assertTrue(ticks <= 20, "a crossing of " + ticks + " ticks is long enough to stand there waiting for");
        assertTrue(IrisSweepDriver.stepCap() > 2,
            "and it still has steps enough to read as a sweep rather than a blink");
    }
}
