package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A gate's three scheduler tasks are cancelled, and forgotten, when a later one replaces or ends them (#430).
 *
 * <p>Each task id is the only handle on a task already booked; a timer started over the top of a pending
 * one, or stopped without cancelling it, leaves the old one to fire against a gate that has moved on.
 * These are the branches of {@link StargateLifecycle} that act on an id already held, which the older
 * lifecycle tests did not reach.
 */
class StargateTimerCancelTest
{
    private BukkitScheduler scheduler;
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.isEnabled()).thenReturn(true);
        PluginTestSupport.install(plugin);
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(9);
        PluginTestSupport.scheduler(scheduler);
        gate = new Stargate();
        gate.setGateName("TestGate");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
        PluginTestSupport.forgetAllGates();
    }

    @Test
    void startingTheActivationTimerOverAPendingOneCancelsItAndKeepsTheNewTask()
    {
        gate.runtime().setGateActivateTaskId(5);

        StargateLifecycle.startActivationTimer(gate, null);

        verify(scheduler).cancelTask(5);
        assertEquals(9, gate.runtime().getGateActivateTaskId(), "the new timer's task is the one kept");
    }

    @Test
    void startingTheActivationTimerWithNonePendingCancelsNothing()
    {
        StargateLifecycle.startActivationTimer(gate, null);

        verify(scheduler, never()).cancelTask(anyInt());
        assertEquals(9, gate.runtime().getGateActivateTaskId());
    }

    @Test
    void stoppingTheActivationTimerCancelsItAndForgetsItsTask()
    {
        gate.runtime().setGateActivateTaskId(5);

        StargateLifecycle.stopActivationTimer(gate);

        verify(scheduler).cancelTask(5);
        assertEquals(-1, gate.runtime().getGateActivateTaskId(), "no task is held any more");
    }

    @Test
    void stoppingAnActivationTimerThatIsNotRunningCancelsNothing()
    {
        StargateLifecycle.stopActivationTimer(gate);

        verify(scheduler, never()).cancelTask(anyInt());
        assertEquals(0, gate.runtime().getGateActivateTaskId(), "and leaves the id as it was");
    }

    @Test
    void stoppingTheAfterShutdownTimerCancelsItForgetsItAndClearsTheRecentlyActiveFlag()
    {
        gate.runtime().setGateAfterShutdownTaskId(4);
        gate.setGateRecentlyActive(true);

        StargateLifecycle.stopAfterShutdownTimer(gate);

        verify(scheduler).cancelTask(4);
        assertEquals(-1, gate.runtime().getGateAfterShutdownTaskId(), "no task is held any more");
        assertFalse(gate.isGateRecentlyActive(), "the flag goes with it");
    }

    @Test
    void startingTheAfterShutdownTimerOverAPendingOneCancelsIt()
    {
        gate.runtime().setGateAfterShutdownTaskId(3);

        StargateLifecycle.startAfterShutdownTimer(gate);

        verify(scheduler).cancelTask(3);
        assertEquals(9, gate.runtime().getGateAfterShutdownTaskId(), "the new follow-up is the one kept");
    }

    @Test
    void anActivationThatTimesOutForgetsItsTask()
    {
        gate.runtime().setGateActivateTaskId(5);

        StargateLifecycle.timeoutStargate(gate, null);

        assertEquals(-1, gate.runtime().getGateActivateTaskId(), "it has fired, so there is nothing left to cancel");
    }

    @Test
    void aTimeoutWithNoActivationPendingLeavesTheIdAlone()
    {
        StargateLifecycle.timeoutStargate(gate, null);

        assertEquals(0, gate.runtime().getGateActivateTaskId());
    }
}
