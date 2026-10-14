package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.model.beam.BeamTiming;

/**
 * The two speed settings that replaced thirteen ring and beam timings (#589).
 *
 * <p>A server on the defaults must see no change at all, so 1.0 has to give back every old default
 * exactly, not near enough. And a speed is a number somebody types, so it is held to a range in
 * which every sequence still runs whole: a ring's countdown long enough to step clear of, a beam's
 * vanish and teleport inside the phases they belong to.
 */
class AnimationSpeedSettingTest
{
    /** The defaults of the seven ring settings these replace, as 1.10.0 shipped them. */
    private static final List<Integer> RING_DEFAULTS = List.of(100, 2, 20, 3, 20, 40, 20);

    /** And of the six beam settings. */
    private static final List<Integer> BEAM_DEFAULTS = List.of(12, 6, 18, 12, 20, 8);

    @AfterEach
    void forgetThem()
    {
        ConfigTestSupport.clear();
    }

    /** The ring timings in one list: countdown, deploy, settle, flash, linger, outline, hold. */
    private static List<Integer> ringTimings()
    {
        return List.of(ConfigManager.getRingCountdownTicks(), ConfigManager.getRingDeployTicks(),
            ConfigManager.getRingSettleTicks(), ConfigManager.getRingFlashTicks(),
            ConfigManager.getRingLightsLingerTicks(), ConfigManager.getRingOutlineTicks(),
            ConfigManager.getRingHoldTicks());
    }

    /** The beam timings in one list: envelop, vanish, rise, teleport, descend, fade. */
    private static List<Integer> beamTimings()
    {
        return List.of(ConfigManager.getBeamEnvelopTicks(), ConfigManager.getBeamVanishAtStep(),
            ConfigManager.getBeamRiseTicks(), ConfigManager.getBeamTeleportAtStep(),
            ConfigManager.getBeamDescendTicks(), ConfigManager.getBeamFadeTicks());
    }

    /** At 1.0, as shipped, and with the setting missing, every timing is exactly the old default. */
    @Test
    void theShippedSpeedGivesBackEveryOldDefaultExactly()
    {
        assertEquals(RING_DEFAULTS, ringTimings(), "missing");
        assertEquals(BEAM_DEFAULTS, beamTimings(), "missing");

        ConfigTestSupport.loadDefaults();

        assertEquals(1.0, ConfigManager.getRingAnimationSpeed());
        assertEquals(1.0, ConfigManager.getBeamAnimationSpeed());
        assertEquals(RING_DEFAULTS, ringTimings(), "as shipped");
        assertEquals(BEAM_DEFAULTS, beamTimings(), "as shipped");
    }

    /** Twice as fast is half as long, rounded; half as fast is twice as long. */
    @Test
    void aSpeedScalesEveryTiming()
    {
        ConfigTestSupport.set(ConfigKeys.RING_ANIMATION_SPEED, 2.0);
        ConfigTestSupport.set(ConfigKeys.BEAM_ANIMATION_SPEED, 2.0);
        assertEquals(List.of(50, 1, 10, 2, 10, 20, 10), ringTimings());
        assertEquals(List.of(6, 3, 9, 6, 10, 4), beamTimings());

        ConfigTestSupport.set(ConfigKeys.RING_ANIMATION_SPEED, 0.5);
        ConfigTestSupport.set(ConfigKeys.BEAM_ANIMATION_SPEED, 0.5);
        assertEquals(List.of(200, 4, 40, 6, 40, 80, 40), ringTimings());
        assertEquals(List.of(24, 12, 36, 24, 40, 16), beamTimings());
    }

    /** Each setting scales its own animation and not the other's. */
    @Test
    void eachSpeedScalesOnlyItsOwnAnimation()
    {
        ConfigTestSupport.set(ConfigKeys.RING_ANIMATION_SPEED, 2.0);

        assertEquals(BEAM_DEFAULTS, beamTimings());
        assertEquals(1.0, ConfigManager.getBeamAnimationSpeed());
    }

    /** A speed past either end is read as that end, and one that is not a number as 1.0. */
    @Test
    void aSpeedOutOfRangeIsReadAsTheNearestEnd()
    {
        ConfigTestSupport.set(ConfigKeys.RING_ANIMATION_SPEED, 10.0);
        assertEquals(2.0, ConfigManager.getRingAnimationSpeed());
        assertEquals(50, ConfigManager.getRingCountdownTicks(), "as fast as 2.0, and no faster");

        ConfigTestSupport.set(ConfigKeys.RING_ANIMATION_SPEED, 0.1);
        assertEquals(0.5, ConfigManager.getRingAnimationSpeed());

        ConfigTestSupport.set(ConfigKeys.BEAM_ANIMATION_SPEED, -3.0);
        assertEquals(0.5, ConfigManager.getBeamAnimationSpeed(), "never a negative duration");

        ConfigTestSupport.set(ConfigKeys.BEAM_ANIMATION_SPEED, Double.NaN);
        assertEquals(1.0, ConfigManager.getBeamAnimationSpeed(), "a NaN typed in a command is the shipped speed");
    }

    /** At the fastest speed a ring's countdown still leaves time to step clear. */
    @Test
    void theFastestCountdownIsStillLongEnoughToStepClear()
    {
        ConfigTestSupport.set(ConfigKeys.RING_ANIMATION_SPEED, ConfigManager.FASTEST_SPEED);

        assertTrue(ConfigManager.getRingCountdownTicks() >= 30, "a countdown of "
            + ConfigManager.getRingCountdownTicks() + " ticks takes people who were only passing through");
    }

    /**
     * Anywhere in the range a beam's vanish and teleport land inside their phases on their own, so
     * {@link BeamTiming} never has to move them and the sequence keeps its shape.
     */
    @Test
    void everyBeamSpeedInTheRangeKeepsItsStepsInsideTheirPhases()
    {
        for (int hundredths = 50; hundredths <= 200; hundredths += 5)
        {
            final double speed = hundredths / 100.0;
            ConfigTestSupport.set(ConfigKeys.BEAM_ANIMATION_SPEED, speed);
            final List<Integer> raw = beamTimings();
            final BeamTiming timing = BeamTiming.resolve(raw.get(0), raw.get(1), raw.get(2), raw.get(3),
                raw.get(4), raw.get(5));
            assertEquals(raw, List.of(timing.envelopTicks(), timing.vanishAtStep(), timing.riseTicks(),
                timing.teleportAtStep(), timing.descendTicks(), timing.fadeTicks()), "at speed " + speed);
        }
    }

    /** The file says what range a speed is read in, since anything outside it is quietly brought in. */
    @Test
    void theRangeIsNamedInEachDescription()
    {
        int found = 0;
        for (final Setting setting : DefaultSettings.config)
        {
            if ((setting.getName() == ConfigKeys.RING_ANIMATION_SPEED)
                || (setting.getName() == ConfigKeys.BEAM_ANIMATION_SPEED))
            {
                found++;
                assertTrue(setting.getDescription().contains("0.5 to 2.0"), setting.getDescription());
            }
        }
        assertEquals(2, found, "both speeds are settings");
    }
}
