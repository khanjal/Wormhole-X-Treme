package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The ceiling on how long a wormhole may stay open.
 *
 * <p>Dialling restarts the shutdown timer, and {@code shutdown_timeout} may be set to 0,
 * which means "stay open until something goes through". Either way a gate can end up open
 * indefinitely and unreachable to anyone else. The open time is recorded when the wormhole
 * forms and deliberately not reset by re-dialling, so the ceiling is measured from the
 * start rather than from the most recent trigger.
 */
public class GateMaxOpenTimeTest
{
    @Test
    public void aClosedGateHasNoOpenTime()
    {
        final Stargate gate = new Stargate();

        assertEquals(0L, gate.getGateOpenedAtMillis());
    }

    @Test
    public void openingRecordsTheTime()
    {
        final Stargate gate = new Stargate();
        gate.markGateOpened();

        assertTrue(gate.getGateOpenedAtMillis() > 0L);
    }

    @Test
    public void reDiallingDoesNotRestartTheClock() throws Exception
    {
        // The whole point: if this reset, anything re-triggering a gate on a schedule would
        // hold it open forever.
        final Stargate gate = new Stargate();
        gate.markGateOpened();
        final long first = gate.getGateOpenedAtMillis();

        Thread.sleep(5);
        gate.markGateOpened();

        assertEquals(first, gate.getGateOpenedAtMillis(), "re-dialling must not extend the ceiling");
    }

    @Test
    public void closingStartsAFreshCeiling()
    {
        final Stargate gate = new Stargate();
        gate.markGateOpened();
        gate.clearGateOpenedAt();

        assertEquals(0L, gate.getGateOpenedAtMillis());

        gate.markGateOpened();
        assertTrue(gate.getGateOpenedAtMillis() > 0L, "the next dial gets its own full allowance");
    }

    @Test
    public void aZeroMaximumMeansNoLimit()
    {
        final Stargate gate = new Stargate();
        gate.markGateOpened();

        assertEquals(Long.MAX_VALUE, gate.remainingOpenMillis(0));
    }

    @Test
    public void aClosedGateHasNoLimitToReport()
    {
        final Stargate gate = new Stargate();

        assertEquals(Long.MAX_VALUE, gate.remainingOpenMillis(300));
    }

    @Test
    public void remainingTimeCountsDownFromWhenTheGateOpened()
    {
        final Stargate gate = new Stargate();
        gate.markGateOpened();

        final long remaining = gate.remainingOpenMillis(300);
        assertTrue(remaining > 0L && remaining <= 300_000L,
            "a gate just opened should have almost its whole allowance left, got " + remaining);
    }

    @Test
    public void theAllowanceCountsDownAsTheGateStaysOpen() throws Exception
    {
        final Stargate gate = new Stargate();
        gate.markGateOpened();

        final long atOpen = gate.remainingOpenMillis(300);
        Thread.sleep(20);
        final long later = gate.remainingOpenMillis(300);

        assertTrue(later < atOpen, "the allowance should shrink while the gate is open");
    }

    @Test
    public void anElapsedCeilingLeavesNothingLeft() throws Exception
    {
        // What the dial path checks: once this goes to zero or below, the gate is closed
        // rather than having its shutdown timer extended again.
        final Stargate gate = new Stargate();
        gate.markGateOpened();
        Thread.sleep(1100);

        assertTrue(gate.remainingOpenMillis(1) <= 0L,
            "a one second ceiling should be spent after waiting longer than that");
    }
}
