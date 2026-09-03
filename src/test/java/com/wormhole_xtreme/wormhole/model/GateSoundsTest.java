package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * What a gate sounds like as it dials.
 *
 * <p>The chevron climb is the part with arithmetic in it, and the part that has to survive
 * shapes with a different number of chevrons from the seven everybody pictures.
 */
public class GateSoundsTest
{
    @Test
    public void theChevronsClimbThroughTheSequence()
    {
        float previous = -1f;
        for (int i = 1; i <= 7; i++)
        {
            final float pitch = GateSounds.chevronPitch(i, 7);
            assertTrue(pitch > previous, "chevron " + i + " should be higher than the last");
            previous = pitch;
        }
    }

    @Test
    public void aShorterGateClimbsTheSameDistanceInBiggerSteps()
    {
        // Shapes are configurable, so the chevron count is not seven by definition. A gate
        // with three should still start and end on the same notes, or a small gate would
        // sound like a big one that stopped early.
        assertEquals(GateSounds.chevronPitch(1, 7), GateSounds.chevronPitch(1, 3), 0.0001f);
        assertEquals(GateSounds.chevronPitch(7, 7), GateSounds.chevronPitch(3, 3), 0.0001f);
    }

    @Test
    public void aSingleStepShapeDoesNotDivideByZero()
    {
        // A shape can light everything at once. That is one step, and one step has no
        // distance to climb across.
        assertEquals(GateSounds.chevronPitch(1, 1), GateSounds.chevronPitch(1, 0), 0.0001f);
    }

    @Test
    public void everyChevronIsWithinWhatMinecraftWillPlay()
    {
        // Bukkit clamps pitch to 0.5 - 2.0, and a sequence that ran past the top would
        // quietly flatten there -- the climb this exists for, lost silently.
        for (final int total : new int[] { 1, 3, 7, 9, 20 })
        {
            for (int i = 1; i <= total; i++)
            {
                final float pitch = GateSounds.chevronPitch(i, total);
                assertTrue((pitch >= 0.5f) && (pitch <= 2.0f),
                    "chevron " + i + " of " + total + " at " + pitch + " will not play");
            }
        }
    }

    @Test
    public void theAmbientPeriodIsNeverZero()
    {
        // A zero or negative period is not a faster hum, it is a repeating task with no delay
        // in it. Config is text somebody can type anything into, so this is floored rather
        // than trusted.
        assertTrue(ConfigManager.getGateSoundAmbientTicks() >= 1L,
            "the hum would run every tick, or tighter");
    }

    @Test
    public void anIterationPastTheEndStaysAtTheTop()
    {
        // The animator's counter and the light-block list have been off by one from each
        // other before now. Running past the end should sound like the last chevron rather
        // than like something screaming.
        assertEquals(GateSounds.chevronPitch(7, 7), GateSounds.chevronPitch(9, 7), 0.0001f);
    }
}
