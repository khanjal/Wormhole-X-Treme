/*
 *   Wormhole X-Treme Plugin for Bukkit
 *   Copyright (C) 2011  Ben Echols
 *                       Dean Bailey
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wormhole_xtreme.wormhole.model;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
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
     * Handles a player clicking the dial sign: advances the displayed target
     * by one and updates the sign text.
     *
     * @param gate the gate whose sign was clicked
     */
    static void teleportSignClicked(final Stargate gate)
    {
        synchronized (gate.getGateNetwork().getNetworkGateLock())
        {
            gate.getGateDialSignBlock().setType(gate.getGateShape().getShapeSignMaterial());
            final Directional tsWs = (Directional) gate.getGateDialSignBlock().getBlockData();
            tsWs.setFacing(gate.getGateFacing());
            gate.getGateDialSignBlock().setBlockData(tsWs);
            gate.setGateDialSign((Sign) gate.getGateDialSignBlock().getState());
            gate.getGateDialSign().setLine(0, "-" + gate.getGateName() + "-");

            if (gate.getGateDialSignIndex() == -1)
            {
                gate.setGateDialSignIndex(gate.getGateDialSignIndex() + 1);
            }

            if ((gate.getGateNetwork().getNetworkSignGateList().size() == 0)
                || (gate.getGateNetwork().getNetworkSignGateList().size() == 1))
            {
                gate.getGateDialSign().setLine(1, "");
                gate.getGateDialSign().setLine(2, "No Other Gates");
                gate.getGateDialSign().setLine(3, "");
                gate.getGateDialSign().update();
                gate.setGateDialSignTarget(null);
                return;
            }

            if (gate.getGateDialSignIndex() >= gate.getGateNetwork().getNetworkSignGateList().size())
            {
                gate.setGateDialSignIndex(0);
            }

            if (gate.getGateNetwork().getNetworkSignGateList().get(gate.getGateDialSignIndex())
                    .getGateName().equals(gate.getGateName()))
            {
                gate.setGateDialSignIndex(gate.getGateDialSignIndex() + 1);
                if (gate.getGateDialSignIndex() == gate.getGateNetwork().getNetworkSignGateList().size())
                {
                    gate.setGateDialSignIndex(0);
                }
            }

            final int networkSize = gate.getGateNetwork().getNetworkSignGateList().size();

            if (networkSize == 2)
            {
                gate.getGateSignOrder().clear();
                gate.getGateSignOrder().put(Integer.valueOf(2),
                    gate.getGateNetwork().getNetworkSignGateList().get(gate.getGateDialSignIndex()));
                gate.getGateDialSign().setLine(1, "");
                gate.getGateDialSign().setLine(2, ">" + gate.getGateSignOrder().get(Integer.valueOf(2)).getGateName() + "<");
                gate.getGateDialSign().setLine(3, "");
                gate.setGateDialSignTarget(gate.getGateNetwork().getNetworkSignGateList().get(gate.getGateDialSignIndex()));
            }
            else if (networkSize == 3)
            {
                gate.getGateSignOrder().clear();
                int orderIndex = 1;
                while (gate.getGateSignOrder().size() < 2)
                {
                    if (gate.getGateDialSignIndex() >= gate.getGateNetwork().getNetworkSignGateList().size())
                    {
                        gate.setGateDialSignIndex(0);
                    }
                    if (gate.getGateNetwork().getNetworkSignGateList().get(gate.getGateDialSignIndex())
                            .getGateName().equals(gate.getGateName()))
                    {
                        gate.setGateDialSignIndex(gate.getGateDialSignIndex() + 1);
                        if (gate.getGateDialSignIndex() == gate.getGateNetwork().getNetworkSignGateList().size())
                        {
                            gate.setGateDialSignIndex(0);
                        }
                    }
                    gate.getGateSignOrder().put(Integer.valueOf(orderIndex),
                        gate.getGateNetwork().getNetworkSignGateList().get(gate.getGateDialSignIndex()));
                    orderIndex++;
                    if (orderIndex == 4) { orderIndex = 1; }
                    gate.setGateDialSignIndex(gate.getGateDialSignIndex() + 1);
                }
                gate.getGateDialSign().setLine(1, gate.getGateSignOrder().get(Integer.valueOf(1)).getGateName());
                gate.getGateDialSign().setLine(2, ">" + gate.getGateSignOrder().get(Integer.valueOf(2)).getGateName() + "<");
                gate.getGateDialSign().setLine(3, "");
                gate.setGateDialSignTarget(gate.getGateSignOrder().get(Integer.valueOf(2)));
                gate.setGateDialSignIndex(gate.getGateNetwork().getNetworkSignGateList()
                    .indexOf(gate.getGateSignOrder().get(Integer.valueOf(2))));
            }
            else
            {
                gate.getGateSignOrder().clear();
                int orderIndex = 1;
                while (gate.getGateSignOrder().size() < 3)
                {
                    if (gate.getGateDialSignIndex() == gate.getGateNetwork().getNetworkSignGateList().size())
                    {
                        gate.setGateDialSignIndex(0);
                    }
                    if (gate.getGateNetwork().getNetworkSignGateList().get(gate.getGateDialSignIndex())
                            .getGateName().equals(gate.getGateName()))
                    {
                        gate.setGateDialSignIndex(gate.getGateDialSignIndex() + 1);
                        if (gate.getGateDialSignIndex() == gate.getGateNetwork().getNetworkSignGateList().size())
                        {
                            gate.setGateDialSignIndex(0);
                        }
                    }
                    gate.getGateSignOrder().put(Integer.valueOf(orderIndex),
                        gate.getGateNetwork().getNetworkSignGateList().get(gate.getGateDialSignIndex()));
                    orderIndex++;
                    gate.setGateDialSignIndex(gate.getGateDialSignIndex() + 1);
                }
                gate.getGateDialSign().setLine(1, gate.getGateSignOrder().get(Integer.valueOf(3)).getGateName());
                gate.getGateDialSign().setLine(2, ">" + gate.getGateSignOrder().get(Integer.valueOf(2)).getGateName() + "<");
                gate.getGateDialSign().setLine(3, gate.getGateSignOrder().get(Integer.valueOf(1)).getGateName());
                gate.setGateDialSignTarget(gate.getGateSignOrder().get(Integer.valueOf(2)));
                gate.setGateDialSignIndex(gate.getGateNetwork().getNetworkSignGateList()
                    .indexOf(gate.getGateSignOrder().get(Integer.valueOf(2))));
            }
            gate.getGateDialSign().update(true);
        }
    }

    /**
     * Resets the gate sign text to its idle (non-dialling) state.
     *
     * @param gate         the gate
     * @param teleportSign {@code true} for the dial sign; currently only {@code true} is used
     */
    static void resetSign(final Stargate gate, final boolean teleportSign)
    {
        if (teleportSign)
        {
            gate.getGateDialSignBlock().setType(gate.getGateShape().getShapeSignMaterial());
            final Directional dialWs = (Directional) gate.getGateDialSignBlock().getBlockData();
            dialWs.setFacing(gate.getGateFacing());
            gate.getGateDialSignBlock().setBlockData(dialWs);
            gate.setGateDialSign((Sign) gate.getGateDialSignBlock().getState());
            gate.getGateDialSign().setLine(0, gate.getGateName());
            gate.getGateDialSign().setLine(1, gate.getGateNetwork() != null ? gate.getGateNetwork().getNetworkName() : "");
            gate.getGateDialSign().setLine(2, "");
            gate.getGateDialSign().setLine(3, "");
            gate.getGateDialSign().update(true);
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
        if ((gate.getGateDialSignBlock() != null) && (gate.getGateDialSign() != null))
        {
            gate.getGateDialSignBlock().setType(Material.AIR);
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
        return tryClickTeleportSign(gate, clicked, null);
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
        if ((gate.getGateDialSign() == null) && (gate.getGateDialSignBlock() != null))
        {
            if (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isWallSign(gate.getGateDialSignBlock().getType()))
            {
                gate.setGateDialSignIndex(-1);
                gate.getGateDialSignBlock().setType(Material.AIR);
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                    new StargateUpdateRunnable(gate, player, ActionToTake.DIAL_SIGN_CLICK));
            }
        }
        else if (WorldUtils.isSameBlock(clicked, gate.getGateDialSignBlock()))
        {
            gate.getGateDialSignBlock().setType(Material.AIR);
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                new StargateUpdateRunnable(gate, player, ActionToTake.DIAL_SIGN_CLICK));
            return true;
        }
        return false;
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
            gate.setGateTarget(target);
            dialStargate(gate);
            gate.getGateTarget().dialStargate();

            if (gate.isGateActive() && gate.getGateTarget().isGateActive())
            {
                // Pre-load destination chunks so players/vehicles don't fall through unloaded terrain.
                // Any brief load-time lag occurs here at dial time, not when the player walks through.
                try
                {
                    final Location destLoc = gate.getGateTarget().getGatePlayerTeleportLocation();
                    WorldUtils.forceLoadDestinationChunks(destLoc);
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                        "Pre-loaded destination chunks for gate: " + gate.getGateTarget().getGateName());
                }
                catch (final Throwable ignore) {}
                return true;
            }
            else if (gate.isGateActive() && !gate.getGateTarget().isGateActive())
            {
                gate.shutdownStargate(true);
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                    "Far wormhole failed to open. Closing local wormhole for safety sake.");
            }
            else if (!gate.isGateActive() && gate.getGateTarget().isGateActive())
            {
                target.shutdownStargate(true);
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                    "Local wormhole failed to open. Closing far end wormhole for safety sake.");
            }
        }
        return false;
    }
}
