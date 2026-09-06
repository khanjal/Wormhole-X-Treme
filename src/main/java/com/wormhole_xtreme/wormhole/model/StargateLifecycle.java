package com.wormhole_xtreme.wormhole.model;

import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;

/**
 * Manages the gate's activation/shutdown lifecycle: timers, iris state, and
 * the interaction between the two connected gate ends on shutdown.
 *
 * <p>All methods are static and operate on a {@link Stargate} instance.
 */
class StargateLifecycle
{
    private StargateLifecycle() {}

    // -----------------------------------------------------------------------
    // Shutdown
    // -----------------------------------------------------------------------

    /**
     * Shuts down this gate and its remote target (if any). Cancels the
     * shutdown timer, resets the portal interior, updates the iris and lever,
     * and optionally starts the after-shutdown cooldown timer.
     *
     * @param gate  the gate to shut down
     * @param timer {@code true} to start the after-shutdown cooldown; this
     *              also briefly marks the gate as "recently active" to protect
     *              the exit area from fire/lava
     */
    static void shutdownStargate(final Stargate gate, final boolean timer)
    {
        if (gate.getGateShutdownTaskId() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Wormhole \"" + gate.getGateName() + "\" ShutdownTaskID \"" + gate.getGateShutdownTaskId() + "\" cancelled.");
            WormholeXTreme.getScheduler().cancelTask(gate.getGateShutdownTaskId());
            gate.setGateShutdownTaskId(-1);
        }

        if (gate.getGateTarget() != null)
        {
            gate.getGateTarget().shutdownStargate(true);
        }

        gate.setGateTarget(null);
        // Closed, so the next dial begins a fresh maximum open time.
        gate.clearGateOpenedAt();
        if (timer)
        {
            gate.setGateRecentlyActive(true);
        }
        gate.setGateActive(false);

        // Clear any recent-arrival markers that reference this gate so players
        // can re-enter after shutdown.
        try { StargateRestrictions.removeRecentArrivalsForGate(gate); } catch (final RuntimeException ignore) { /* best effort */ }

        GateSounds.closed(gate);
        GateSounds.stopAmbient(gate);
        gate.lightStargate(false);
        gate.toggleDialLeverState(false);
        gate.toggleRedstoneGateActivatedPower();

        if (gate.isGateIrisDefaultActive())
        {
            setIrisState(gate, gate.isGateIrisDefaultActive());
        }
        else if (!gate.isGateIrisActive())
        {
            gate.fillGateInterior(Material.AIR);
        }

        if (timer)
        {
            startAfterShutdownTimer(gate);
        }

        WorldUtils.scheduleChunkUnload(gate.getGatePlayerTeleportLocation().getBlock());
    }

    // -----------------------------------------------------------------------
    // Timers
    // -----------------------------------------------------------------------

    /**
     * Starts the activation/pick-target timer. If the player does not choose
     * a destination before it expires, {@link #timeoutStargate} is called.
     *
     * @param gate the gate
     * @param p    the player who activated the gate
     */
    static void startActivationTimer(final Stargate gate, final Player p)
    {
        if (gate.getGateActivateTaskId() > 0)
        {
            WormholeXTreme.getScheduler().cancelTask(gate.getGateActivateTaskId());
        }
        final int timeout = ConfigManager.getTimeoutActivate() * 20;
        gate.setGateActivateTaskId(WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
            WormholeXTreme.getThisPlugin(),
            new StargateUpdateRunnable(gate, p, ActionToTake.DEACTIVATE), timeout));
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
            "Wormhole \"" + gate.getGateName() + "\" ActivateTaskID \"" + gate.getGateActivateTaskId() + "\" created.");
    }

    /**
     * Stops the activation timer if it is running.
     *
     * @param gate the gate
     */
    static void stopActivationTimer(final Stargate gate)
    {
        if (gate.getGateActivateTaskId() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Wormhole \"" + gate.getGateName() + "\" ActivateTaskID \"" + gate.getGateActivateTaskId() + "\" cancelled.");
            WormholeXTreme.getScheduler().cancelTask(gate.getGateActivateTaskId());
            gate.setGateActivateTaskId(-1);
        }
    }

    /**
     * Stops the after-shutdown cooldown timer and clears the recently-active flag.
     *
     * @param gate the gate
     */
    static void stopAfterShutdownTimer(final Stargate gate)
    {
        if (gate.getGateAfterShutdownTaskId() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Wormhole \"" + gate.getGateName() + "\" AfterShutdownTaskID \"" + gate.getGateAfterShutdownTaskId() + "\" cancelled.");
            WormholeXTreme.getScheduler().cancelTask(gate.getGateAfterShutdownTaskId());
            gate.setGateAfterShutdownTaskId(-1);
        }
        gate.setGateRecentlyActive(false);
    }

    // -----------------------------------------------------------------------
    // Timeout
    // -----------------------------------------------------------------------

    /**
     * Called when the activation timer expires. Deactivates the gate and
     * notifies the player.
     *
     * @param gate the gate
     * @param p    the player who activated it (may be {@code null})
     */
    static void timeoutStargate(final Stargate gate, final Player p)
    {
        if (gate.getGateActivateTaskId() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Wormhole \"" + gate.getGateName() + "\" ActivateTaskID \"" + gate.getGateActivateTaskId() + "\" timed out.");
            gate.setGateActivateTaskId(-1);
        }
        // By gate identity, not by player: removeActivatedStargate(p) would remove
        // whatever gate is *currently* mapped for p, which is wrong the moment the same
        // player has activated a second gate before this one's timer fired -- that would
        // silently steal the second gate's still-pending activation out of the map, and
        // then act on it (s) instead of the gate that actually timed out. Chevrons on the
        // gate that really timed out stayed lit forever; the unrelated second gate got
        // switched off early and lost its own map entry, so its own timeout later found
        // nothing there and skipped its iris/message cleanup too.
        StargateManager.removeActivatorForStargate(gate);

        if (gate.isGateIrisDefaultActive())
        {
            setIrisState(gate, gate.isGateIrisDefaultActive());
        }
        if (gate.isGateLightsActive())
        {
            gate.lightStargate(false);
            if (p != null)
            {
                p.sendMessage("Gate: " + gate.getGateName() + " timed out and deactivated.");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Iris
    // -----------------------------------------------------------------------

    /**
     * Toggles the iris on/off and optionally persists the new state as the
     * default.
     *
     * @param gate       the gate
     * @param setDefault {@code true} to remember the new state as the default
     */
    static void toggleIrisActive(final Stargate gate, final boolean setDefault)
    {
        gate.setGateIrisActive(!gate.isGateIrisActive());
        setIrisState(gate, gate.isGateIrisActive());
        if (setDefault)
        {
            gate.setGateIrisDefaultActive(gate.isGateIrisActive());
        }
    }

    /**
     * Applies {@code irisActive} to the gate: sets the flag, fills the
     * interior with the appropriate material, and updates the iris lever.
     *
     * @param gate       the gate
     * @param irisActive {@code true} to engage the iris; {@code false} to open it
     */
    static void setIrisState(final Stargate gate, final boolean irisActive)
    {
        // Read before the state is changed, so a call that asks for what is already true is
        // silent rather than announcing an iris that did not move.
        final boolean moved = gate.isGateIrisActive() != irisActive;
        gate.setGateIrisActive(irisActive);
        if (moved)
        {
            if (irisActive)
            {
                GateSounds.irisClosed(gate);
            }
            else
            {
                GateSounds.irisOpened(gate);
            }
        }
        if (gate.isGateIrisActive())
        {
            // The iris is a real barrier, so it is placed as real server-side blocks
            // rather than drawn client-side the way the portal is.
            gate.fillGateIris(gate.getEffectiveIrisMaterial());
        }
        else if (gate.isGateActive())
        {
            // Opening the iris on an active gate returns the interior to AIR with the
            // portal drawn over it, which also clears the iris blocks placed above.
            gate.fillGateInterior(gate.getEffectivePortalMaterial());
        }
        else
        {
            gate.fillGateInterior(Material.AIR);
        }
        if ((gate.getGateIrisLeverBlock() != null)
            && (gate.getGateIrisLeverBlock().getType() == Material.LEVER))
        {
            final org.bukkit.block.data.Powerable lp =
                (org.bukkit.block.data.Powerable) gate.getGateIrisLeverBlock().getBlockData();
            lp.setPowered(gate.isGateIrisActive());
            gate.getGateIrisLeverBlock().setBlockData(lp);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Starts the after-shutdown cooldown timer that clears the
     * recently-active flag after 3 seconds (60 ticks). This prevents fire
     * and lava damage to players who just exited the wormhole.
     *
     * @param gate the gate
     */
    private static void startAfterShutdownTimer(final Stargate gate)
    {
        if (gate.getGateAfterShutdownTaskId() > 0)
        {
            WormholeXTreme.getScheduler().cancelTask(gate.getGateAfterShutdownTaskId());
        }
        final int timeout = 60;
        gate.setGateAfterShutdownTaskId(WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
            WormholeXTreme.getThisPlugin(),
            new StargateUpdateRunnable(gate, ActionToTake.AFTERSHUTDOWN), timeout));
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
            "Wormhole \"" + gate.getGateName() + "\" AfterShutdownTaskID \"" + gate.getGateAfterShutdownTaskId() + "\" created.");
        if (gate.getGateAfterShutdownTaskId() == -1)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE,
                "Failed to schdule wormhole after shutdown, received task id of -1.");
            gate.setGateRecentlyActive(false);
        }
    }
}
