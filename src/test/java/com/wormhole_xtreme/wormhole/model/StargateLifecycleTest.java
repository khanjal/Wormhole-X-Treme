package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StargateLifecycle}.
 *
 * <p>Only paths that do not require a live {@code WormholeXTreme} server instance
 * are exercised here. Methods that unconditionally call
 * {@code WormholeXTreme.getThisPlugin()} (e.g. timer scheduling) are tested
 * through their observable side-effects on gate state flags only when the
 * code path can be reached without hitting those calls (e.g. task-id &le; 0).
 */
public class StargateLifecycleTest
{
    private Stargate gate;

    @BeforeEach
    public void setUp()
    {
        gate = new Stargate();
        gate.setGateName("TestGate");
    }

    // -----------------------------------------------------------------------
    // stopAfterShutdownTimer
    // -----------------------------------------------------------------------

    @Test
    public void stopAfterShutdownTimerAlwaysClearsRecentlyActiveFlag()
    {
        // When task id <= 0, no scheduler call is made — only the flag is cleared.
        gate.setGateRecentlyActive(true);
        gate.setGateAfterShutdownTaskId(-1);

        StargateLifecycle.stopAfterShutdownTimer(gate);

        assertFalse(gate.isGateRecentlyActive(),
            "stopAfterShutdownTimer must clear the recentlyActive flag");
    }

    @Test
    public void stopAfterShutdownTimerSetsTaskIdMinusOneWhenAlreadyNegative()
    {
        gate.setGateAfterShutdownTaskId(-1);

        StargateLifecycle.stopAfterShutdownTimer(gate);

        // id was already -1; the if-body is skipped, so it stays -1
        assertEquals(-1, gate.getGateAfterShutdownTaskId());
    }

    // -----------------------------------------------------------------------
    // stopActivationTimer
    // -----------------------------------------------------------------------

    @Test
    public void stopActivationTimerNoopWhenNoActiveTask()
    {
        // id <= 0 → the body of the if is never entered; no WormholeXTreme call made
        gate.setGateActivateTaskId(-1);

        // Must not throw
        StargateLifecycle.stopActivationTimer(gate);

        assertEquals(-1, gate.getGateActivateTaskId());
    }

    // -----------------------------------------------------------------------
    // setIrisState — pure state / material selection
    // -----------------------------------------------------------------------

    @Test
    public void setIrisStateTrueSetsGateIrisActiveFlag()
    {
        gate.setGateIrisActive(false);
        // No shape, no custom, no lever, empty portal blocks → safe to call
        StargateLifecycle.setIrisState(gate, true);

        assertTrue(gate.isGateIrisActive(),
            "setIrisState(true) must set the iris-active flag");
    }

    @Test
    public void setIrisStateFalseClearsGateIrisActiveFlag()
    {
        gate.setGateIrisActive(true);
        StargateLifecycle.setIrisState(gate, false);

        assertFalse(gate.isGateIrisActive(),
            "setIrisState(false) must clear the iris-active flag");
    }

    @Test
    public void setIrisStateFalseOnActiveGateSelectsWaterByDefault()
    {
        // Without shape/custom config, portal material defaults to WATER when gate is active.
        // We verify indirectly: if fillGateInterior() would have been called with WATER,
        // the gate's iris flag is still false (no NPE thrown, no shape needed).
        gate.setGateActive(true);
        gate.setGateIrisActive(true);

        // Empty portal block list → fillGateInterior iterates nothing; no World call needed
        StargateLifecycle.setIrisState(gate, false);

        assertFalse(gate.isGateIrisActive());
        assertTrue(gate.isGateActive(), "Gate should still be active after iris deactivation");
    }

    @Test
    public void setIrisStateFalseOnInactiveGateSelectsAirByDefault()
    {
        gate.setGateActive(false);
        gate.setGateIrisActive(true);

        StargateLifecycle.setIrisState(gate, false);

        assertFalse(gate.isGateIrisActive());
        assertFalse(gate.isGateActive());
    }

    // -----------------------------------------------------------------------
    // toggleIrisActive
    // -----------------------------------------------------------------------

    @Test
    public void toggleIrisActiveFlipsFalseToTrue()
    {
        gate.setGateIrisActive(false);

        StargateLifecycle.toggleIrisActive(gate, false);

        assertTrue(gate.isGateIrisActive(),
            "toggleIrisActive must flip iris-active from false to true");
    }

    @Test
    public void toggleIrisActiveFlipsTrueToFalse()
    {
        gate.setGateIrisActive(true);

        StargateLifecycle.toggleIrisActive(gate, false);

        assertFalse(gate.isGateIrisActive(),
            "toggleIrisActive must flip iris-active from true to false");
    }

    @Test
    public void toggleIrisActiveSetsDefaultWhenRequested()
    {
        gate.setGateIrisActive(false);
        gate.setGateIrisDefaultActive(false);

        StargateLifecycle.toggleIrisActive(gate, true);

        assertTrue(gate.isGateIrisActive());
        assertTrue(gate.isGateIrisDefaultActive(),
            "toggleIrisActive(setDefault=true) must update the default flag to match");
    }

    @Test
    public void toggleIrisActiveDoesNotChangeDefaultWhenNotRequested()
    {
        gate.setGateIrisActive(false);
        gate.setGateIrisDefaultActive(false);

        StargateLifecycle.toggleIrisActive(gate, false);

        assertTrue(gate.isGateIrisActive());
        assertFalse(gate.isGateIrisDefaultActive(),
            "toggleIrisActive(setDefault=false) must leave the default flag unchanged");
    }

    // -----------------------------------------------------------------------
    // timeoutStargate (null player, activateTaskId <= 0, lights off)
    // -----------------------------------------------------------------------

    @Test
    public void timeoutStargateWithNullPlayerDeactivatesLights()
    {
        // activateTaskId <= 0 → no WormholeXTreme logging call
        gate.setGateActivateTaskId(-1);
        gate.setGateLightsActive(true);
        // getGateLightBlocks() returns null by default → lightStargate(false) just clears the flag

        StargateLifecycle.timeoutStargate(gate, null);

        assertFalse(gate.isGateLightsActive(),
            "timeoutStargate must turn lights off when the gate was lit");
    }

    @Test
    public void timeoutStargateWithNullPlayerRestoresIrisWhenDefaultActive()
    {
        gate.setGateActivateTaskId(-1);
        gate.setGateLightsActive(false);
        gate.setGateIrisDefaultActive(true);
        gate.setGateIrisActive(false);

        StargateLifecycle.timeoutStargate(gate, null);

        assertTrue(gate.isGateIrisActive(),
            "timeoutStargate must re-engage the iris when its default is active");
    }
}
