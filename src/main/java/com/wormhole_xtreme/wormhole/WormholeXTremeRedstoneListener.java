package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

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
                final Block raBlock = stargate.getGateRedstoneGateActivatedBlock();

                // A signal counts when it lands on the activation block itself or on a
                // redstone component touching it. Requiring an exact match meant only dust
                // placed precisely on the RD position ever worked, which is not how anyone
                // builds: dust run up to the block fires the event on the dust, and a
                // detector rail beside the gate fires it on the rail.
                final boolean isRedstoneDial = isPoweringActivationBlock(rdBlock, block);
                final boolean isRedstoneSign = isPoweringActivationBlock(rsBlock, block);
                final boolean isDialSame = (dial != null) && WorldUtils.isSameBlock(dial, block);
                final boolean isDialAdjacent = (dial != null) && WorldUtils.isAdjacent(dial, block);

                // Any redstone component touching the DHD counts, not only dust. The [RD]
                // cell has always accepted a repeater, torch, block, lever or rail beside it
                // (see isPoweringActivationBlock); the DHD itself accepted dust and nothing
                // else, so wiring that worked one block higher silently did nothing here.
                // The DHD is the part a player can actually see and reach -- especially on a
                // gate sunk into the ground, where the marker cell is above head height and
                // the natural place to bring a signal is alongside or underneath the button.
                //
                // The gate's own [RA] output is excluded. It sits close enough to the DHD on
                // some shapes to be adjacent to it, and it goes high the instant the gate
                // opens, so counting it would let a gate re-trigger itself.
                final boolean isOwnOutput = (raBlock != null) && WorldUtils.isSameBlock(raBlock, block);
                final boolean isSourceAdjacent = !isOwnOutput
                    && isDialAdjacent
                    && MaterialUtils.isRedstoneSource(block.getType());

                // Monitor-mode: if the gate defined monitor blocks we treat redstone
                // on those blocks as an activation trigger rather than requiring the
                // plugin to place redstone dust. This allows sign-only gates to keep
                // their lever/button while still supporting player-placed redstone.
                boolean isMonitorTriggered = false;
                try
                {
                    for (final Block m : stargate.getGateRedstoneDialMonitorBlocks())
                    {
                        if (m != null && !isOwnOutput && WorldUtils.isSameBlock(m, block)
                            && MaterialUtils.isRedstoneSource(block.getType()))
                        {
                            isMonitorTriggered = true;
                            break;
                        }
                    }
                }
                catch (final Throwable ignore) {}

                // RD block (or dial-adjacent wire or monitored wire): activate or shutdown
                if (isRedstoneDial || isDialSame || isSourceAdjacent || isMonitorTriggered)
                {
                    if (stargate.isGateActive() && (stargate.getGateTarget() != null))
                    {
                        // Already open, so leave it alone. This used to close the gate,
                        // which made repeated triggers useless: a second minecart over a
                        // detector rail shut the wormhole the first one had opened.
                        //
                        // It does not re-dial either. Dialling restarts the shutdown timer,
                        // so a cart crossing every few seconds would hold the gate open and
                        // lock everyone else out. Doing nothing means the gate closes on its
                        // own timer no matter how often it is triggered.
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                            "Redstone trigger on already-open gate " + stargate.getGateName() + "; leaving it open.");
                    }
                    else if (stargate.isGateLightsActive() && (stargate.getGateTarget() == null))
                    {
                        // Lit but never dialled. A second trigger deactivates it, which is
                        // the only way to clear a gate somebody activated and walked away from.
                        final Player activator = StargateManager.removeActivatorForStargate(stargate);
                        stargate.stopActivationTimer();
                        stargate.setGateActive(false);
                        stargate.toggleDialLeverState(false);
                        stargate.lightStargate(false);
                        if (activator != null)
                        {
                            activator.sendMessage(com.wormhole_xtreme.wormhole.config.ConfigManager.MessageStrings.normalHeader.toString() + "Gate deactivated.");
                        }
                    }
                    else if (stargate.isGateSignPowered())
                    {
                        // Closed and sign-powered: dial whatever the dial sign is showing.
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
