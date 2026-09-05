package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * How long an open wormhole's shutdown is scheduled for, when two limits disagree.
 *
 * <p>The shutdown timeout says how long a gate stays open after it was last dialled. The
 * maximum open time says how long it may stay open at all, measured from when it first
 * opened. The second has to win, and that is not a preference: it is the only thing stopping
 * a gate that something re-triggers on a schedule -- a minecart crossing a detector rail every
 * few seconds -- from staying open for ever and locking everyone else out of its target.
 *
 * <p>This became worth pinning when a redstone signal on an already-open gate started pushing
 * the shutdown back rather than doing nothing. Doing nothing was safe by construction; this is
 * safe only because of the clamp below.
 */
class ShutdownDelayTest
{
    @Test
    void withNoMaximumTheConfiguredTimeoutIsUsedAsIs()
    {
        assertEquals(600, StargateDialManager.shutdownDelayTicks(600, Long.MAX_VALUE));
    }

    @Test
    void theTimeoutWinsWhenThereIsPlentyOfMaximumLeft()
    {
        // 600 ticks is 30s; 200_000ms of maximum left is far more than that.
        assertEquals(600, StargateDialManager.shutdownDelayTicks(600, 200000L));
    }

    /**
     * The maximum wins when it is the tighter of the two.
     *
     * <p>This is the case that keeps a repeatedly-triggered gate from outliving its cap: each
     * extension can only ever reach as far as the maximum still allows.
     */
    @Test
    void theMaximumWinsWhenItIsTheTighterLimit()
    {
        // 5000ms left is 100 ticks, less than the 600-tick timeout.
        assertEquals(100, StargateDialManager.shutdownDelayTicks(600, 5000L));
    }

    /**
     * A spent maximum closes the gate rather than scheduling anything.
     *
     * <p>Returning a delay here would give a gate that has already outstayed its limit yet
     * more time, which is precisely what the limit exists to prevent.
     */
    @Test
    void aSpentMaximumSaysCloseNow()
    {
        assertEquals(StargateDialManager.CLOSE_NOW, StargateDialManager.shutdownDelayTicks(600, 0L));
        assertEquals(StargateDialManager.CLOSE_NOW, StargateDialManager.shutdownDelayTicks(600, -1L));
    }

    /**
     * A sliver of maximum left still schedules a close, rather than rounding away to none.
     *
     * <p>A delay of 0 means "no timer at all, stay open" elsewhere in this code. Integer
     * division would turn 30ms of remaining maximum into exactly that, so a gate a hair away
     * from its limit would have been granted an indefinite stay by rounding.
     */
    @Test
    void asliverOfMaximumStillSchedulesACloseRatherThanNoTimerAtAll()
    {
        assertEquals(1, StargateDialManager.shutdownDelayTicks(600, 30L),
            "rounding to 0 would mean 'never close', the opposite of what is left");
    }

    /**
     * With no shutdown timeout configured, the maximum alone decides.
     *
     * <p>A timeout of 0 means a gate stays open until something closes it. The maximum is
     * still a real limit, so it has to supply the delay on its own rather than being ignored.
     */
    @Test
    void withNoTimeoutConfiguredTheMaximumSuppliesTheDelay()
    {
        assertEquals(100, StargateDialManager.shutdownDelayTicks(0, 5000L));
    }

    @Test
    void withNeitherLimitSetThereIsNoTimer()
    {
        assertEquals(0, StargateDialManager.shutdownDelayTicks(0, Long.MAX_VALUE));
    }
}
