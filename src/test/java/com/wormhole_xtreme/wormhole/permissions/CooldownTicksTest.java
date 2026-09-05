package com.wormhole_xtreme.wormhole.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * An absurd cooldown in {@code config.yml} no longer becomes no cooldown at all.
 *
 * <p>{@code scheduleSyncDelayedTask} takes an {@code int} of ticks, and the obvious
 * {@code (int) (seconds * 20L)} wraps. Past 107,374,182 seconds the product exceeds
 * {@code Integer.MAX_VALUE} and comes back negative, which Bukkit runs on the next tick --
 * so a cooldown set to something enormous cleared itself immediately, the exact inverse of
 * what was asked for. Nothing about a task firing early looks like an arithmetic problem,
 * which is why this would have been very hard to recognise from a bug report.
 *
 * <p>It was unreachable until {@code use-cooldown-seconds} became a real registered setting.
 * Before that the value was a hardcoded 120 that no command and no config file could change,
 * so the multiplication only ever saw one safe input. Making the setting work is what put an
 * arbitrary number from a text file into it, and that is why this guard arrives in the same
 * change.
 */
class CooldownTicksTest
{
    /** What Bukkit is given a tick count as. */
    private static final long MAX_INT = Integer.MAX_VALUE;

    /**
     * An ordinary cooldown converts exactly, at twenty ticks to the second.
     */
    @Test
    void anOrdinaryCooldownIsTwentyTicksPerSecond()
    {
        assertEquals(2400, StargateRestrictions.cooldownTicks(120L), "120s is 2400 ticks");
        assertEquals(72000, StargateRestrictions.cooldownTicks(3600L), "an hour is 72000 ticks");
    }

    /**
     * The largest value that still fits is converted rather than saturated.
     *
     * <p>Pins the boundary from below, so a future change to the clamp cannot quietly start
     * rounding down ordinary-but-large waits.
     */
    @Test
    void theLargestSchedulableWaitIsStillExact()
    {
        final long seconds = MAX_INT / 20L;
        assertEquals(seconds * 20L, StargateRestrictions.cooldownTicks(seconds),
            "this many seconds still fits in an int of ticks, so it should convert exactly");
    }

    /**
     * One second past that saturates instead of wrapping negative.
     *
     * <p>This is the case the bug was. Against the old {@code (int) (seconds * 20L)} the
     * assertion below sees a large negative number.
     */
    @Test
    void aWaitTooLargeToScheduleSaturatesRatherThanWrapping()
    {
        final long seconds = (MAX_INT / 20L) + 1L;
        assertEquals(Integer.MAX_VALUE, StargateRestrictions.cooldownTicks(seconds),
            "a wait too large to express in ticks should become the longest schedulable wait, "
                + "not a negative one that fires on the next tick");
    }

    /**
     * A wildly large value stays positive, however large it is.
     *
     * <p>The specific number matters less than the sign: a negative delay is what turns a long
     * cooldown into none, so that is what is asserted.
     */
    @Test
    void anAbsurdWaitIsStillAWait()
    {
        for (final long seconds : new long[] { 999999999L, Integer.MAX_VALUE, Long.MAX_VALUE / 2L })
        {
            assertTrue(StargateRestrictions.cooldownTicks(seconds) > 0,
                seconds + " seconds produced a delay that is not positive, which Bukkit runs "
                    + "immediately -- the cooldown would clear itself the moment it was set");
        }
    }

    /**
     * A negative or zero wait means no wait, rather than a nonsense delay.
     *
     * <p>Nothing stops an admin typing {@code use-cooldown-seconds: -5}. Zero is the honest
     * reading of that, and it is what the rest of the cooldown path already degrades to.
     */
    @Test
    void aNegativeOrZeroWaitIsNoWait()
    {
        assertEquals(0, StargateRestrictions.cooldownTicks(0L), "zero seconds is zero ticks");
        assertEquals(0, StargateRestrictions.cooldownTicks(-5L), "a negative wait is no wait");
        assertEquals(0, StargateRestrictions.cooldownTicks(Long.MIN_VALUE),
            "the most negative value is still just no wait, not an overflow");
    }
}
