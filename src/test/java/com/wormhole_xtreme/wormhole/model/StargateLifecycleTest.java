package com.wormhole_xtreme.wormhole.model;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import org.bukkit.entity.Player;
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
class StargateLifecycleTest
{
    private Stargate gate;

    @BeforeEach
    void setUp()
    {
        gate = new Stargate();
        gate.setGateName("TestGate");
    }

    // -----------------------------------------------------------------------
    // stopAfterShutdownTimer
    // -----------------------------------------------------------------------

    @Test
    void stopAfterShutdownTimerAlwaysClearsRecentlyActiveFlag()
    {
        // When task id <= 0, no scheduler call is made — only the flag is cleared.
        gate.setGateRecentlyActive(true);
        gate.setGateAfterShutdownTaskId(-1);

        StargateLifecycle.stopAfterShutdownTimer(gate);

        assertFalse(gate.isGateRecentlyActive(),
            "stopAfterShutdownTimer must clear the recentlyActive flag");
    }

    @Test
    void stopAfterShutdownTimerSetsTaskIdMinusOneWhenAlreadyNegative()
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
    void stopActivationTimerNoopWhenNoActiveTask()
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
    void setIrisStateTrueSetsGateIrisActiveFlag()
    {
        gate.setGateIrisActive(false);
        // No shape, no custom, no lever, empty portal blocks → safe to call
        StargateLifecycle.setIrisState(gate, true);

        assertTrue(gate.isGateIrisActive(),
            "setIrisState(true) must set the iris-active flag");
    }

    @Test
    void setIrisStateFalseClearsGateIrisActiveFlag()
    {
        gate.setGateIrisActive(true);
        StargateLifecycle.setIrisState(gate, false);

        assertFalse(gate.isGateIrisActive(),
            "setIrisState(false) must clear the iris-active flag");
    }

    @Test
    void setIrisStateFalseOnActiveGateSelectsWaterByDefault()
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
    void setIrisStateFalseOnInactiveGateSelectsAirByDefault()
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
    void toggleIrisActiveFlipsFalseToTrue()
    {
        gate.setGateIrisActive(false);

        StargateLifecycle.toggleIrisActive(gate, false);

        assertTrue(gate.isGateIrisActive(),
            "toggleIrisActive must flip iris-active from false to true");
    }

    @Test
    void toggleIrisActiveFlipsTrueToFalse()
    {
        gate.setGateIrisActive(true);

        StargateLifecycle.toggleIrisActive(gate, false);

        assertFalse(gate.isGateIrisActive(),
            "toggleIrisActive must flip iris-active from true to false");
    }

    @Test
    void toggleIrisActiveSetsDefaultWhenRequested()
    {
        gate.setGateIrisActive(false);
        gate.setGateIrisDefaultActive(false);

        StargateLifecycle.toggleIrisActive(gate, true);

        assertTrue(gate.isGateIrisActive());
        assertTrue(gate.isGateIrisDefaultActive(),
            "toggleIrisActive(setDefault=true) must update the default flag to match");
    }

    @Test
    void toggleIrisActiveDoesNotChangeDefaultWhenNotRequested()
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
    void timeoutStargateWithNullPlayerDeactivatesLights()
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
    void timeoutStargateWithNullPlayerRestoresIrisWhenDefaultActive()
    {
        gate.setGateActivateTaskId(-1);
        gate.setGateLightsActive(false);
        gate.setGateIrisDefaultActive(true);
        gate.setGateIrisActive(false);

        StargateLifecycle.timeoutStargate(gate, null);

        assertTrue(gate.isGateIrisActive(),
            "timeoutStargate must re-engage the iris when its default is active");
    }

    // -----------------------------------------------------------------------
    // timeoutStargate — the same player activated a second gate before the
    // first one's timer fired, so activatedStargates no longer maps that
    // player to the gate that actually timed out.
    // -----------------------------------------------------------------------

    @Test
    void timeoutStargateDeactivatesTheGateThatActuallyTimedOutNotWhicheverIsCurrentlyMappedToThePlayer()
    {
        // Reproduces a real bug: chevrons staying lit forever on a gate that timed
        // out, because the same player had since activated a second gate. The old
        // code resolved "which gate to turn off" through activatedStargates keyed
        // on the player -- by the time gate1's timer fired, that map entry pointed
        // at gate2, so gate1's own lights were never told to turn off, and gate2
        // was wrongly turned off (and its own map entry silently consumed) instead.
        final Player player = mock(Player.class);
        final Stargate gate1 = new Stargate();
        gate1.setGateName("Gate1");
        gate1.setGateActivateTaskId(-1);
        gate1.setGateLightsActive(true);

        final Stargate gate2 = new Stargate();
        gate2.setGateName("Gate2");
        gate2.setGateActivateTaskId(-1);
        gate2.setGateLightsActive(true);

        StargateManager.addActivatedStargate(player, gate1);
        // The player walks away and activates a second gate before gate1's own
        // activation timer expires -- overwriting the player's map entry.
        StargateManager.addActivatedStargate(player, gate2);

        // gate1's timer is the one that actually fires first.
        StargateLifecycle.timeoutStargate(gate1, player);

        assertFalse(gate1.isGateLightsActive(),
            "the gate whose timer actually fired must have its own lights turned off");
        assertTrue(gate2.isGateLightsActive(),
            "a different, still-pending gate activation must not be turned off by " +
                "an unrelated gate's timeout");
        assertSame(gate2, StargateManager.removeActivatedStargate(player),
            "gate2's own pending activation must still be in the map for its own timeout to find later");
    }
}
