package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Handles the sign-based dialling UI and the gate activation/dialling logic:
 * cycling through network targets on the physical sign, resetting signs, and
 * the two-sided dial handshake between local and remote gates.
 *
 * <p>All methods are static and operate on a {@link Stargate} instance.
 */
class StargateDialManager
{
    private StargateDialManager() {}

    // -----------------------------------------------------------------------
    // Sign UI
    // -----------------------------------------------------------------------

    /**
     * Handles a player clicking the dial sign: cycles the displayed destination
     * forward (right-click) or backward (left-click) and updates the sign text.
     *
     * <p>Sign layout:
     * <pre>
     *   Line 0  -GateName-          (always the gate's own name)
     *   Line 1  PreviousGate        (gate before the current selection)
     *   Line 2  >CurrentGate<       (selected destination)
     *   Line 3  NextGate            (gate after the current selection)
     * </pre>
     *
     * @param gate    the gate whose sign was clicked
     * @param forward {@code true} to advance to the next gate;
     *                {@code false} to go to the previous gate
     */
    static void teleportSignClicked(final Stargate gate, final boolean forward)
    {
        // Fetch the sign block state first.
        final org.bukkit.block.BlockState bState = gate.getGateDialSignBlock().getState();
        if (!(bState instanceof Sign))
        {
            return;
        }
        gate.setGateDialSign((Sign) bState);

        // Build a filtered list of OTHER gates reachable from this gate.
        // Named-network gates see only peers on the same network.
        // Networkless gates form the implicit public pool — they see all other networkless gates.
        final List<Stargate> others = new ArrayList<Stargate>();
        final boolean hasNetwork = gate.getGateNetwork() != null;

        if (hasNetwork)
        {
            synchronized (gate.getGateNetwork().getNetworkGateLock())
            {
                final java.util.List<Stargate> netList = gate.getGateNetwork().getNetworkGateList();
                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, false,
                    "SignDial: gate=" + gate.getGateName() + " network=" + gate.getGateNetwork().getNetworkName()
                    + " networkSize=" + netList.size());
                for (final Stargate s : netList)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, false,
                        "SignDial:   peer=" + s.getGateName() + " signPowered=" + s.isGateSignPowered());
                    if (!s.getGateName().equals(gate.getGateName()))
                    {
                        others.add(s);
                    }
                }
            }
        }
        else
        {
            // No named network — public pool: all other gates that also have no network.
            final java.util.ArrayList<Stargate> allGates = StargateManager.getAllGates();
            WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, false,
                "SignDial: gate=" + gate.getGateName() + " network=none(public) allGatesSize=" + allGates.size());
            for (final Stargate s : allGates)
            {
                if (s.getGateNetwork() == null && !s.getGateName().equals(gate.getGateName()))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, false,
                        "SignDial:   peer=" + s.getGateName() + " signPowered=" + s.isGateSignPowered());
                    others.add(s);
                }
            }
        }

        // Sort peers alphabetically so cycling order is predictable.
        java.util.Collections.sort(others, new java.util.Comparator<Stargate>()
        {
            @Override
            public int compare(final Stargate a, final Stargate b)
            {
                return a.getGateName().compareToIgnoreCase(b.getGateName());
            }
        });

        // Line 0: always this gate's name.
        gate.getGateDialSign().setLine(0, "-" + gate.getGateName() + "-");

        if (others.isEmpty())
        {
            gate.getGateDialSign().setLine(1, "");
            gate.getGateDialSign().setLine(2, "No Other Gates");
            gate.getGateDialSign().setLine(3, "");
            gate.getGateDialSign().update(true, false);
            gate.setGateDialSignTarget(null);
            return;
        }

        // Advance the selection index.
        int idx = gate.getGateDialSignIndex();
        if (idx < 0)
        {
            // First click: start at the beginning regardless of direction.
            idx = 0;
        }
        else if (forward)
        {
            idx = (idx + 1) % others.size();
        }
        else
        {
            idx = (idx - 1 + others.size()) % others.size();
        }
        gate.setGateDialSignIndex(idx);

        final Stargate current = others.get(idx);
        gate.setGateDialSignTarget(current);

        if (others.size() == 1)
        {
            // Only one other gate: no prev/next context needed.
            gate.getGateDialSign().setLine(1, "");
            gate.getGateDialSign().setLine(2, ">" + current.getGateName() + "<");
            gate.getGateDialSign().setLine(3, "");
        }
        else
        {
            final int prevIdx = (idx - 1 + others.size()) % others.size();
            final int nextIdx = (idx + 1) % others.size();
            gate.getGateDialSign().setLine(1, others.get(prevIdx).getGateName());
            gate.getGateDialSign().setLine(2, ">" + current.getGateName() + "<");
            gate.getGateDialSign().setLine(3, others.get(nextIdx).getGateName());
        }

        gate.getGateDialSign().update(true, false);
    }

    /**
     * Resets the gate sign text to its idle (non-dialling) state.
     *
     * @param gate         the gate
     * @param teleportSign {@code true} for the dial sign; currently only {@code true} is used
     */
    static void resetSign(final Stargate gate, final boolean teleportSign)
    {
        if (teleportSign && gate.getGateDialSignBlock() != null)
        {
            final org.bukkit.block.BlockState bState = gate.getGateDialSignBlock().getState();
            if (!(bState instanceof Sign))
            {
                return;
            }
            gate.setGateDialSign((Sign) bState);
            gate.setGateDialSignIndex(-1);
            gate.getGateDialSign().setLine(0, gate.getGateName());
            gate.getGateDialSign().setLine(1, gate.getGateNetwork() != null ? gate.getGateNetwork().getNetworkName() : "Public");
            gate.getGateDialSign().setLine(2, "");
            gate.getGateDialSign().setLine(3, "");
            gate.getGateDialSign().update(true, false);
        }
    }

    /**
     * Clears the teleport sign block and schedules a sign reset via the
     * scheduler (avoids visual glitches from immediate re-set).
     *
     * @param gate the gate
     */
    static void resetTeleportSign(final Stargate gate)
    {
        if (gate.getGateDialSignBlock() != null)
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                new StargateUpdateRunnable(gate, ActionToTake.DIAL_SIGN_RESET), 2);
        }
    }

    /**
     * Handles a click on the dial sign block (no player context).
     *
     * @param gate    the gate
     * @param clicked the block that was clicked
     * @return {@code true} if the click was consumed
     */
    static boolean tryClickTeleportSign(final Stargate gate, final Block clicked)
    {
        return tryClickTeleportSign(gate, clicked, null, true);
    }

    /**
     * Handles a click on the dial sign block.
     *
     * @param gate    the gate
     * @param clicked the block that was clicked
     * @param player  the player who clicked (may be {@code null})
     * @return {@code true} if the click was consumed
     */
    static boolean tryClickTeleportSign(final Stargate gate, final Block clicked, final Player player)
    {
        return tryClickTeleportSign(gate, clicked, player, true);
    }

    /**
     * Handles a click on the dial sign block.
     *
     * @param gate    the gate
     * @param clicked the block that was clicked
     * @param player  the player who clicked (may be {@code null})
     * @param forward {@code true} to advance to the next gate (right-click);
     *                {@code false} to go to the previous gate (left-click)
     * @return {@code true} if the click was consumed
     */
    static boolean tryClickTeleportSign(final Stargate gate, final Block clicked, final Player player, final boolean forward)
    {
        if (gate.getGateDialSignBlock() == null)
        {
            return false;
        }
        // Only consume the click when the player actually clicked the DHD sign,
        // not the gate's ID/name sign (which is a different registered block).
        if (!WorldUtils.isSameBlock(clicked, gate.getGateDialSignBlock()))
        {
            return false;
        }
        if (!com.wormhole_xtreme.wormhole.utils.MaterialUtils.isWallSign(gate.getGateDialSignBlock().getType()))
        {
            return false;
        }
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new StargateUpdateRunnable(gate, player, ActionToTake.DIAL_SIGN_CLICK, forward));
        return true;
    }

    // -----------------------------------------------------------------------
    // Gate dialling
    // -----------------------------------------------------------------------

    /**
     * Activates this gate as the local end of an established wormhole. Cancels
     * any pending shutdown timer, schedules a new one, and kicks off the
     * lighting + woosh animation sequence.
     *
     * @param gate the gate to activate
     */
    static void dialStargate(final Stargate gate)
    {
        WorldUtils.scheduleChunkLoad(gate.getGatePlayerTeleportLocation().getBlock());
        if (gate.getGateShutdownTaskId() > 0)
        {
            WormholeXTreme.getScheduler().cancelTask(gate.getGateShutdownTaskId());
        }
        if (gate.getGateAfterShutdownTaskId() > 0)
        {
            WormholeXTreme.getScheduler().cancelTask(gate.getGateAfterShutdownTaskId());
        }

        final int timeout = ConfigManager.getTimeoutShutdown() * 20;
        if (timeout > 0)
        {
            gate.setGateShutdownTaskId(WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
                WormholeXTreme.getThisPlugin(),
                new StargateUpdateRunnable(gate, ActionToTake.SHUTDOWN), timeout));
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                "Wormhole \"" + gate.getGateName() + "\" ShutdownTaskID \"" + gate.getGateShutdownTaskId() + "\" created.");
            if (gate.getGateShutdownTaskId() == -1)
            {
                gate.shutdownStargate(true);
                WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false,
                    "Failed to schdule wormhole shutdown timeout: " + timeout
                    + " Received task id of -1. Wormhole forced closed NOW.");
            }
        }

        if ((gate.getGateShutdownTaskId() > 0) || (timeout == 0))
        {
            if (!gate.isGateActive())
            {
                gate.setGateActive(true);
                gate.toggleDialLeverState(false);
                gate.toggleRedstoneGateActivatedPower();
                gate.setGateRecentlyActive(false);
            }
            if (!gate.isGateLightsActive())
            {
                gate.lightStargate(true);
            }
            else
            {
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                    new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH));
            }
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "No wormhole. No visual events.");
        }
    }

    /**
     * Dials a specific remote target gate.
     *
     * @param gate   the local gate
     * @param target the gate to connect to
     * @param force  {@code true} to bypass iris/active checks
     * @return {@code true} if both ends activated successfully
     */
    static boolean dialStargate(final Stargate gate, final Stargate target, final boolean force)
    {
        if (target == null)
        {
            return false;
        }
        if (gate.getGateActivateTaskId() > 0)
        {
            WormholeXTreme.getScheduler().cancelTask(gate.getGateActivateTaskId());
        }

        // Prevent dialing a target that currently has an active iris (standard protection)
        if ((target != null) && target.isGateIrisActive() && !force)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                "Dial prevented: target '" + target.getGateName() + "' iris active.");
            return false;
        }

        // Prevent dialing a target that is already active (connected/open) or already targeted
        // by another active gate unless forced.
        if (target != null && !force)
        {
            if (target.isGateActive())
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                    "Dial prevented: target '" + target.getGateName() + "' already active.");
                return false;
            }
            if (target.getGateTarget() != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                    "Dial prevented: target '" + target.getGateName() + "' already has a target.");
                return false;
            }
            // If any other gate currently targets this gate and is active, block the dial.
            try
            {
                for (final Stargate s : StargateManager.getAllGates())
                {
                    if ((s != null) && (s != gate) && (s.getGateTarget() != null) && (s.getGateTarget() == target) && s.isGateActive())
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                            "Dial prevented: target '" + target.getGateName() + "' is already targeted by '" + s.getGateName() + "'.");
                        return false;
                    }
                }
            }
            catch (final Throwable ignore) {}
        }

        if (!target.isGateLightsActive() || force)
        {
            // First, attempt to activate the local gate. Do not assign the target
            // until local activation succeeds to avoid the local activation path
            // clearing the target (which previously caused NPEs and aborted dials).
            dialStargate(gate);

            // If local activation failed, abort and leave state clean.
            if (!gate.isGateActive())
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                    "Dial aborted: local activation failed for gate '" + gate.getGateName() + "'.");
                return false;
            }

            // Assign the remote target now that local activation succeeded.
            gate.setGateTarget(target);

            // Attempt to activate the remote end.
            try
            {
                target.dialStargate();
            }
            catch (final Throwable ignore) {}

            if (gate.isGateActive() && target.isGateActive())
            {
                // Pre-load destination chunks so players/vehicles don't fall through unloaded terrain.
                try
                {
                    final Location destLoc = target.getGatePlayerTeleportLocation();
                    WorldUtils.forceLoadDestinationChunks(destLoc);
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                        "Pre-loaded destination chunks for gate: " + target.getGateName());
                }
                catch (final Throwable ignore) {}
                return true;
            }
            else if (gate.isGateActive() && !target.isGateActive())
            {
                gate.shutdownStargate(true);
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                    "Far wormhole failed to open. Closing local wormhole for safety sake.");
            }
            else if (!gate.isGateActive() && target.isGateActive())
            {
                target.shutdownStargate(true);
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                    "Local wormhole failed to open. Closing far end wormhole for safety sake.");
            }
        }
        return false;
    }
}
