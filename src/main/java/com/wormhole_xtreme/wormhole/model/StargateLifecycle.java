// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2011 Ben Echols, Dean Bailey. See LICENSE.txt for terms.
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                "Wormhole \"" + gate.getGateName() + "\" ShutdownTaskID \"" + gate.getGateShutdownTaskId() + "\" cancelled.");
            WormholeXTreme.getScheduler().cancelTask(gate.getGateShutdownTaskId());
            gate.setGateShutdownTaskId(-1);
        }

        if (gate.getGateTarget() != null)
        {
            gate.getGateTarget().shutdownStargate(true);
        }

        gate.setGateTarget(null);
        if (timer)
        {
            gate.setGateRecentlyActive(true);
        }
        gate.setGateActive(false);

        // Clear any recent-arrival markers that reference this gate so players
        // can re-enter after shutdown.
        try { StargateRestrictions.removeRecentArrivalsForGate(gate); } catch (final Throwable ignore) {}

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
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                "Wormhole \"" + gate.getGateName() + "\" ActivateTaskID \"" + gate.getGateActivateTaskId() + "\" timed out.");
            gate.setGateActivateTaskId(-1);
        }
        Stargate s = (p != null) ? StargateManager.removeActivatedStargate(p) : gate;
        if (s != null)
        {
            if (gate.isGateIrisDefaultActive())
            {
                setIrisState(gate, gate.isGateIrisDefaultActive());
            }
            if (gate.isGateLightsActive())
            {
                s.lightStargate(false);
            }
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
        gate.setGateIrisActive(irisActive);
        final Material interiorMat;
        if (gate.isGateIrisActive())
        {
            interiorMat = gate.isGateCustom()
                ? gate.getGateCustomIrisMaterial()
                : gate.getGateShape() != null
                    ? gate.getGateShape().getShapeIrisMaterial()
                    : Material.STONE;
        }
        else if (gate.isGateActive())
        {
            interiorMat = gate.isGateCustom()
                ? gate.getGateCustomPortalMaterial()
                : gate.getGateShape() != null
                    ? gate.getGateShape().getShapePortalMaterial()
                    : Material.WATER;
        }
        else
        {
            interiorMat = Material.AIR;
        }
        gate.fillGateInterior(interiorMat);
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
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
            "Wormhole \"" + gate.getGateName() + "\" AfterShutdownTaskID \"" + gate.getGateAfterShutdownTaskId() + "\" created.");
        if (gate.getGateAfterShutdownTaskId() == -1)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false,
                "Failed to schdule wormhole after shutdown, received task id of -1.");
            gate.setGateRecentlyActive(false);
        }
    }
}
