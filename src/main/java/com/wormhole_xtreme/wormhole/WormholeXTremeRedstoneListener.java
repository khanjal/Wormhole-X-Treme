package com.wormhole_xtreme.wormhole;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Listener for redstone events related to stargates.
 */
class WormholeXTremeRedstoneListener implements Listener
{
    /**
     * How far from an indexed gate block a redstone change may be and still be
     * attributed to that gate. The activation blocks sit one above a structure block,
     * so anything wired into one is within a block or two of the indexed frame.
     */
    private static final int GATE_SEARCH_RADIUS = 2;

    /**
     * Returns true when a redstone change at {@code changed} should count as powering
     * {@code activationBlock} — either it is that block, or it is a redstone component
     * adjacent to it.
     *
     * <p>Adjacency here is the full 3x3x3 neighbourhood, so diagonals count even though
     * redstone does not power diagonally. That matches the existing dial-lever check and
     * errs toward activating a gate the player clearly wired up.
     *
     * @param activationBlock
     *            the gate's RD or RS block, may be null when the shape defines none
     * @param changed
     *            the block whose redstone current changed
     * @return true if this change should trigger the activation block
     */
    private static boolean isPoweringActivationBlock(final Block activationBlock, final Block changed)
    {
        if (activationBlock == null || changed == null)
        {
            return false;
        }
        if (WorldUtils.isSameBlock(activationBlock, changed))
        {
            return true;
        }
        return WorldUtils.isAdjacent(activationBlock, changed)
            && MaterialUtils.isRedstoneSource(changed.getType());
    }

    @EventHandler
    public void onBlockRedstoneChange(final BlockRedstoneEvent event)
    {
        if (event == null)
        {
            return;
        }

        final Block block = event.getBlock();
        if (block == null)
        {
            return;
        }

        Stargate stargate = StargateManager.getGateFromBlock(block);
        if (stargate == null)
        {
            try
            {
                // Fallback: find a nearby indexed gate if the block itself isn't indexed
                stargate = StargateManager.findNearestGateByBlock(block.getLocation(), GATE_SEARCH_RADIUS, GATE_SEARCH_RADIUS);
            }
            catch (final Throwable ignore) {}
            if (stargate == null)
            {
                return;
            }
        }

        // Only act on an actual power state change
        if (event.getOldCurrent() == event.getNewCurrent())
        {
            return;
        }

        // Only handle rising-edge power for activation/shutdown
        if ((event.getOldCurrent() == 0) && (event.getNewCurrent() > 0))
        {
            // If this gate is not configured for redstone activation, ignore
            if (!stargate.isGateRedstonePowered())
            {
                return;
            }

            try
            {
                final Block dial = stargate.getGateDialLeverBlock();
                final Block rdBlock = stargate.getGateRedstoneDialActivationBlock();
                final Block rsBlock = stargate.getGateRedstoneSignActivationBlock();

                // A signal counts when it lands on the activation block itself or on a
                // redstone component touching it. Requiring an exact match meant only dust
                // placed precisely on the RD position ever worked, which is not how anyone
                // builds: dust run up to the block fires the event on the dust, and a
                // detector rail beside the gate fires it on the rail.
                final boolean isRedstoneDial = isPoweringActivationBlock(rdBlock, block);
                final boolean isRedstoneSign = isPoweringActivationBlock(rsBlock, block);
                final boolean isDialSame = (dial != null) && WorldUtils.isSameBlock(dial, block);
                final boolean isDialAdjacent = (dial != null) && WorldUtils.isAdjacent(dial, block);

                // Consider redstone wire adjacency to the dial as an activation trigger.
                final boolean isWireAdjacent = (block.getType() == Material.REDSTONE_WIRE) && isDialAdjacent;

                // Monitor-mode: if the gate defined monitor blocks we treat redstone
                // on those blocks as an activation trigger rather than requiring the
                // plugin to place redstone dust. This allows sign-only gates to keep
                // their lever/button while still supporting player-placed redstone.
                boolean isMonitorTriggered = false;
                try
                {
                    for (final Block m : stargate.getGateRedstoneDialMonitorBlocks())
                    {
                        if (m != null && WorldUtils.isSameBlock(m, block) && block.getType() == Material.REDSTONE_WIRE)
                        {
                            isMonitorTriggered = true;
                            break;
                        }
                    }
                }
                catch (final Throwable ignore) {}

                // RD block (or dial-adjacent wire or monitored wire): activate or shutdown
                if (isRedstoneDial || isDialSame || isWireAdjacent || isMonitorTriggered)
                {
                    if (stargate.isGateActive() || stargate.isGateLightsActive())
                    {
                        // Gate already open — shut it down
                        if (stargate.getGateTarget() != null)
                        {
                            stargate.shutdownStargate(true);
                        }
                        else
                        {
                            final Player activator = StargateManager.removeActivatorForStargate(stargate);
                            stargate.stopActivationTimer();
                            stargate.setGateActive(false);
                            stargate.toggleDialLeverState(false);
                            stargate.lightStargate(false);
                            if (activator != null)
                            {
                                try
                                {
                                    activator.sendMessage(com.wormhole_xtreme.wormhole.config.ConfigManager.MessageStrings.normalHeader.toString() + "Gate deactivated.");
                                }
                                catch (final Throwable ignore) {}
                            }
                        }
                    }
                    else if (stargate.isGateSignPowered())
                    {
                        // Gate is inactive and sign-powered: dial the currently selected sign target
                        final Stargate signTarget = stargate.getGateDialSignTarget();
                        if (signTarget != null)
                        {
                            stargate.dialStargate(signTarget, false);
                        }
                    }
                }

                // RS block: cycle the sign target (forward direction)
                if (isRedstoneSign && !stargate.isGateActive())
                {
                    WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
                        WormholeXTreme.getThisPlugin(),
                        new StargateUpdateRunnable(stargate, ActionToTake.DIAL_SIGN_CLICK),
                        1L);
                }
            }
            catch (final Throwable ignore) {}
        }
    }
}
