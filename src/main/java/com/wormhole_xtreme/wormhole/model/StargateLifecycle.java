package com.wormhole_xtreme.wormhole.model;

import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.events.StargateShutdownEvent;
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
    private static final String GATE_PREFIX = "Wormhole \"";
    private static final String CANCELLED_TAIL = "\" cancelled.";
    private static final String ACTIVATE_TASK_ID = "\" ActivateTaskID \"";

    private StargateLifecycle() {}

    // -----------------------------------------------------------------------
    // Shutdown
    // -----------------------------------------------------------------------

    /**
     * Shuts down this gate and its remote target (if any). Cancels the
     * shutdown timer, resets the portal interior, updates the iris and lever,
     * and optionally starts the after-shutdown cooldown timer.
     *
     * <p>Raises {@link StargateShutdownEvent} only if the gate was actually open. This is
     * called defensively -- before a removal, on plugin disable, on a gate that may or may
     * not be running -- and announcing a wormhole closing that was never open would make the
     * event useless to anything counting them.
     *
     * @param gate  the gate to shut down
     * @param timer {@code true} to start the after-shutdown cooldown; this
     *              also briefly marks the gate as "recently active" to protect
     *              the exit area from fire/lava
     * @param reason why it is closing, reported to listeners
     */
    static void shutdownStargate(final Stargate gate, final boolean timer,
                                 final StargateShutdownEvent.Reason reason)
    {
        // Read before anything clears it: every path below runs whether or not the gate was
        // open, and only one of them is a wormhole actually closing.
        final boolean wasActive = gate.isGateActive();
        if (gate.getGateShutdownTaskId() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                GATE_PREFIX + gate.getGateName() + "\" ShutdownTaskID \"" + gate.getGateShutdownTaskId() + CANCELLED_TAIL);
            WormholeXTreme.getScheduler().cancelTask(gate.getGateShutdownTaskId());
            gate.setGateShutdownTaskId(-1);
        }

        if (gate.getGateTarget() != null)
        {
            gate.getGateTarget().shutdownStargate(true,
                StargateShutdownEvent.Reason.FAR_END);
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
        try
        {
            StargateRestrictions.removeRecentArrivalsForGate(gate);
        }
        catch (final RuntimeException ignore) { /* best effort */ }

        GateSounds.closed(gate);
        GateSounds.stopAmbient(gate);
        gate.lightStargate(false);
        gate.toggleDialLeverState(false);
        gate.toggleRedstoneGateActivatedPower();

        // Called off here, not only through setIrisState below, which a gate whose iris is not
        // shut by default never reaches: an opening sweep left running went on painting the
        // wormhole it started with into the idle gate, and its last step filled it in (#434).
        StargateIrisAnimator.cancel(gate);
        if (gate.isGateIrisDefaultActive())
        {
            setIrisState(gate, gate.isGateIrisDefaultActive());
        }
        else if (!gate.isGateIrisActive())
        {
            gate.fillGateInterior(Material.AIR);
        }
        // Here as well as in drawIris: an iris shut against its default reaches neither branch
        // above, and its layers would outlive the wormhole they were drawn around.
        StargateBlockSetup.takeBackLayers(gate);

        if (timer)
        {
            startAfterShutdownTimer(gate);
        }

        WorldUtils.scheduleChunkUnload(gate.getGatePlayerTeleportLocation().getBlock());

        if (wasActive)
        {
            GateEvents.fireShutdown(gate, reason);
        }
    }

    // -----------------------------------------------------------------------
    // Timers
    // -----------------------------------------------------------------------

    /**
     * Starts the activation/pick-target timer. If the player does not choose
     * a destination before it expires, {@link #timeoutStargate} is called.
     *
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
            GATE_PREFIX + gate.getGateName() + ACTIVATE_TASK_ID + gate.getGateActivateTaskId() + "\" created.");
    }

    /**
     * Stops the activation timer if it is running.
     */
    static void stopActivationTimer(final Stargate gate)
    {
        if (gate.getGateActivateTaskId() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                GATE_PREFIX + gate.getGateName() + ACTIVATE_TASK_ID + gate.getGateActivateTaskId() + CANCELLED_TAIL);
            WormholeXTreme.getScheduler().cancelTask(gate.getGateActivateTaskId());
            gate.setGateActivateTaskId(-1);
        }
    }

    /**
     * Stops the after-shutdown cooldown timer and clears the recently-active flag.
     */
    static void stopAfterShutdownTimer(final Stargate gate)
    {
        if (gate.getGateAfterShutdownTaskId() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                GATE_PREFIX + gate.getGateName() + "\" AfterShutdownTaskID \"" + gate.getGateAfterShutdownTaskId() + CANCELLED_TAIL);
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
     * @param p    the player who activated it (may be {@code null})
     */
    static void timeoutStargate(final Stargate gate, final Player p)
    {
        if (gate.getGateActivateTaskId() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                GATE_PREFIX + gate.getGateName() + ACTIVATE_TASK_ID + gate.getGateActivateTaskId() + "\" timed out.");
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
     * Toggles the iris on/off and optionally makes the new state the default.
     *
     * @param setDefault {@code true} to remember the new state as the default; saving the
     *                   gate is left to the caller
     */
    static void toggleIrisActive(final Stargate gate, final boolean setDefault)
    {
        // setIrisState writes the flag itself, and works out whether the iris moved by
        // comparing the old value against the new one. Flipping it here first made those two
        // the same value on every path a player can reach -- the lever, the commands and
        // dialling all come through here -- so the iris was always judged not to have moved:
        // no sound on any of them, however the sound keys were configured. It is the only
        // caller that has ever needed to pass the state it wants rather than the state it has.
        setIrisState(gate, !gate.isGateIrisActive());
        if (setDefault)
        {
            gate.setGateIrisDefaultActive(gate.isGateIrisActive());
        }
    }

    /**
     * Puts a gate read from a save taken mid-dial back to idle, blocks and all.
     *
     * <p>Only a crash leaves such a file, and the world was saved with it: a floor gate's
     * real iris taken out for the journey, the dial and output levers still powered.
     *
     * @param gate
     *            the gate just read, its iris flag already at its default
     */
    static void settleAfterLoad(final Stargate gate)
    {
        gate.setGateActive(false);
        gate.setGateLightsActive(false);
        if (gate.getGateWorld() == null)
        {
            return;
        }
        // The iris first: it is the barrier, and the levers are only its lamp.
        setIrisState(gate, gate.isGateIrisDefaultActive());
        gate.toggleDialLeverState(false);
        gate.toggleRedstoneGateActivatedPower();
    }

    /**
     * Puts the iris blocks where the flag now says they are, and draws what follows.
     *
     * @param gate
     *            the gate, whose iris flag is already set
     * @param moved
     *            whether the iris actually changed, which is what a sweep needs
     */
    private static void drawIris(final Stargate gate, final boolean moved)
    {
        // What the opening looks like with no iris over it: the portal if a wormhole is up,
        // otherwise nothing. Both the sweep and the instant path need it.
        final Material uncovered = gate.isGateActive() ? gate.getEffectivePortalMaterial() : Material.AIR;
        final boolean sweep = moved && StargateIrisAnimator.sweeps(gate);
        if (gate.isGateIrisActive())
        {
            // Drawn on a vertical gate, real blocks on a horizontal one: see
            // StargateBlockSetup.irisIsDrawn for why the floor is the exception.
            gate.fillGateIris(gate.getEffectiveIrisMaterial());
            if (!gate.isGateActive())
            {
                // No wormhole, so nothing to layer: whatever was drawn either side of the ring
                // while there was one comes down with it.
                StargateBlockSetup.takeBackLayers(gate);
            }
            else if (!StargateBlockSetup.irisIsDrawn(gate))
            {
                // A horizontal gate's iris is real blocks filling the opening, so the horizon
                // has nowhere left inside the ring and is shown a block below instead.
                //
                // A drawn iris wants none of this. sendLayered, below, puts both layers where
                // each viewer needs them; sending the horizon to everybody first put it a block
                // behind the ring for all of them, which for anyone standing behind the gate is
                // their own side -- and in real water behind a see-through iris, where the
                // stand-in belongs. Both were then corrected, but not until the sweep had
                // finished, so a gate spent the length of its own animation showing the one
                // picture the layering exists to avoid.
                StargateBlockSetup.sendPortalBackdrop(gate, true);
            }
            // A drawn iris needs nothing here: the sweep brings the far layer in with each
            // ring that covers it, and sendLayered below settles the lot afterwards.
            // A drawn iris over a wormhole is then restacked for each viewer, so anybody behind
            // the gate sees the horizon in the ring and the iris beyond it. After the sweep, not
            // before: the sweep draws the ring cell by cell and would paint over it.
            final Runnable layer = () -> StargateBlockSetup.sendLayered(gate);
            if (sweep)
            {
                // Iris first, sweep second: on a horizontal gate that puts the barrier there
                // before it looks it, and on a vertical one it settles what the sweep spends
                // the next second uncovering.
                StargateIrisAnimator.sweepClosed(gate, uncovered, layer);
            }
            else
            {
                layer.run();
            }
            return;
        }
        if (sweep)
        {
            // Sweep first, iris second, for the same reason the other way round: the barrier
            // outlasts the picture of it rather than the other way about.
            //
            // The layers come down at the end rather than here. The sweep takes the far one
            // back a ring at a time as each ring uncovers, so the wormhole stays behind the
            // iris right up until that piece of iris goes; taking the lot away first left a
            // see-through iris opening onto the landscape, which is the closing sweep's bug
            // read backwards.
            StargateIrisAnimator.sweepOpen(gate, uncovered, () ->
            {
                gate.fillGateInterior(uncovered);
                StargateBlockSetup.takeBackLayers(gate);
            });
            return;
        }
        // Both layers, not only the one behind: a viewer round the back was shown the iris a
        // block in front of the ring, and nothing else would take it back.
        StargateBlockSetup.takeBackLayers(gate);
        // Opening the iris on an active gate returns the interior to the portal, which also
        // clears the iris -- drawn or placed -- that was over it; an inactive one goes to AIR.
        gate.fillGateInterior(uncovered);
    }

    /**
     * Applies {@code irisActive} to the gate: sets the flag, fills the
     * interior with the appropriate material, and updates the iris lever.
     *
     * @param irisActive {@code true} to engage the iris; {@code false} to open it
     */
    static void setIrisState(final Stargate gate, final boolean irisActive)
    {
        // Read before the state is changed, so a call that asks for what is already true is
        // silent rather than announcing an iris that did not move.
        final boolean moved = gate.isGateIrisActive() != irisActive;
        // A sweep still part-way through is stale the moment the iris is redrawn, which it is
        // below whether or not it moved: left running, its next ring paints over the picture the
        // redraw settles, such as the layers a shutdown onto a default-shut iris hands back.
        // Called off before the flag moves, so it finishes as the iris it was heading to --
        // after, a closing sweep on a drawn iris was finished as the air behind it.
        StargateIrisAnimator.cancel(gate);
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
        drawIris(gate, moved);
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
     */
    static void startAfterShutdownTimer(final Stargate gate)
    {
        if (gate.getGateAfterShutdownTaskId() > 0)
        {
            WormholeXTreme.getScheduler().cancelTask(gate.getGateAfterShutdownTaskId());
        }
        // Stopping the server shuts open gates while the plugin is disabled, when the scheduler throws
        // on a new task; nothing is left to wait for, so the flag goes now.
        if (!WormholeXTreme.getThisPlugin().isEnabled())
        {
            gate.setGateAfterShutdownTaskId(-1);
            gate.setGateRecentlyActive(false);
            return;
        }
        final int timeout = 60;
        gate.setGateAfterShutdownTaskId(WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
            WormholeXTreme.getThisPlugin(),
            new StargateUpdateRunnable(gate, ActionToTake.AFTERSHUTDOWN), timeout));
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
            GATE_PREFIX + gate.getGateName() + "\" AfterShutdownTaskID \"" + gate.getGateAfterShutdownTaskId() + "\" created.");
        if (gate.getGateAfterShutdownTaskId() == -1)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE,
                "Failed to schdule wormhole after shutdown, received task id of -1.");
            gate.setGateRecentlyActive(false);
        }
    }
}
