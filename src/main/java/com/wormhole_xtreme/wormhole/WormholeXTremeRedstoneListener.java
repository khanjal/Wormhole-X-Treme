package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.GateRedstoneWrite;
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
     * How long a gate ignores further redstone triggers after acting on one, in milliseconds.
     *
     * <p>One circuit is not one event. Every dust block along a run fires its own
     * {@code BlockRedstoneEvent} as the signal propagates, a tick or so apart, and a gate
     * accepts a trigger from any component touching its DHD as well as from its [RD] cell --
     * so dust laid past the button and up to the marker legitimately powers several blocks
     * this gate answers to. Without a window, that reads as several separate presses.
     *
     * <p>250ms is five ticks: comfortably longer than a signal takes to travel the few blocks
     * around a DHD, and far shorter than anyone can deliberately pulse a gate twice.
     */
    private static final long TRIGGER_WINDOW_MS = 250L;

    /** When each gate last acted on a redstone trigger, keyed by gate name. */
    private static final java.util.Map<String, Long> lastTrigger =
        new java.util.concurrent.ConcurrentHashMap<String, Long>();

    /**
     * Whether a trigger arriving now is a repeat of one already acted on.
     *
     * @param lastMs
     *            when this gate last acted, or null if it never has
     * @param nowMs
     *            the current time
     * @param windowMs
     *            how long a gate stays deaf after acting
     * @return true if this trigger should be ignored
     */
    static boolean isRepeatTrigger(final Long lastMs, final long nowMs, final long windowMs)
    {
        if (lastMs == null)
        {
            return false;
        }
        final long since = nowMs - lastMs.longValue();
        // A clock that moved backwards is not evidence of a repeat, so only a gap inside the
        // window counts. Without this a backwards jump would deafen a gate indefinitely.
        return (since >= 0L) && (since < windowMs);
    }

    /**
     * Forgets every gate's last trigger.
     *
     * <p>Exists for tests, which reuse gate names across cases and run far inside the window,
     * so one case's trigger would otherwise silence the next.
     */
    static void clearTriggerHistory()
    {
        lastTrigger.clear();
    }

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

    /**
     * Whether this event is a rising edge on a real block that a player's circuit caused.
     *
     * <p>Everything here is a reason to walk away before a gate is even looked up: a malformed
     * event, a falling edge -- which is a gate closing or a button releasing, neither of which
     * triggers anything -- and a change the plugin raised itself while switching a gate's own
     * levers.
     *
     * @param event
     *            the redstone event
     * @return true if the rest of this listener should look at it
     */
    private static boolean isActionableRisingEdge(final BlockRedstoneEvent event)
    {
        if ((event == null) || (event.getBlock() == null))
        {
            return false;
        }
        // A gate opening switches its own levers, and Bukkit reports those writes back here
        // as ordinary redstone changes. They are not a player's circuit and must not act as
        // one -- doing so dialled a sign gate a second time in the middle of its first dial.
        if (GateRedstoneWrite.inProgress())
        {
            return false;
        }
        // A rising edge is by definition a change, so this subsumes the old "did anything
        // actually change" check rather than dropping it.
        return (event.getOldCurrent() == 0) && (event.getNewCurrent() > 0);
    }

    /**
     * The gate a redstone change belongs to, or null if it belongs to none.
     *
     * @param block
     *            the block whose current changed
     * @return the gate, or null
     */
    private static Stargate gateFor(final Block block)
    {
        final Stargate indexed = StargateManager.getGateFromBlock(block);
        if (indexed != null)
        {
            return indexed;
        }
        try
        {
            // Fallback: find a nearby indexed gate if the block itself isn't indexed
            return StargateManager.findNearestGateByBlock(block.getLocation(), GATE_SEARCH_RADIUS, GATE_SEARCH_RADIUS);
        }
        catch (final RuntimeException ignore)
        {
            return null;
        }
    }

    /**
     * Whether a redstone change should work this gate's dial.
     *
     * <p>A signal counts when it lands on the activation block itself or on a redstone
     * component touching it. Requiring an exact match meant only dust placed precisely on the
     * RD position ever worked, which is not how anyone builds: dust run up to the block fires
     * the event on the dust, and a detector rail beside the gate fires it on the rail.
     *
     * <p>Any redstone component touching the DHD counts, not only dust. The [RD] cell has
     * always accepted a repeater, torch, block, lever or rail beside it (see
     * {@link #isPoweringActivationBlock}); the DHD itself accepted dust and nothing else, so
     * wiring that worked one block higher silently did nothing here. The DHD is the part a
     * player can actually see and reach -- especially on a gate sunk into the ground, where
     * the marker cell is above head height and the natural place to bring a signal is
     * alongside or underneath the button.
     *
     * <p>The gate's own [RA] output is excluded from both of those. It sits close enough to
     * the DHD on some shapes to be adjacent to it, and it goes high the instant the gate
     * opens, so counting it would let a gate re-trigger itself. It is deliberately not
     * excluded from the [RD] test, which is how it has always been: the shapes keep [RA]
     * clear of [RD] by geometry.
     *
     * @param stargate
     *            the gate the change was attributed to
     * @param block
     *            the block whose current changed
     * @return true if this counts as working the dial
     */
    private static boolean isDialTrigger(final Stargate stargate, final Block block)
    {
        final Block dial = stargate.getGateDialLeverBlock();
        final Block raBlock = stargate.getGateRedstoneGateActivatedBlock();
        final boolean isOwnOutput = (raBlock != null) && WorldUtils.isSameBlock(raBlock, block);

        if (isPoweringActivationBlock(stargate.getGateRedstoneDialActivationBlock(), block))
        {
            return true;
        }
        if ((dial != null) && WorldUtils.isSameBlock(dial, block))
        {
            return true;
        }
        if (!isOwnOutput
            && (dial != null)
            && WorldUtils.isAdjacent(dial, block)
            && MaterialUtils.isRedstoneSource(block.getType()))
        {
            return true;
        }
        return isMonitorTrigger(stargate, block, isOwnOutput);
    }

    /**
     * Whether the change landed on one of this gate's monitored blocks.
     *
     * <p>Monitor-mode: if the gate defined monitor blocks we treat redstone on those blocks as
     * an activation trigger rather than requiring the plugin to place redstone dust. This
     * allows sign-only gates to keep their lever/button while still supporting player-placed
     * redstone.
     *
     * @param stargate
     *            the gate
     * @param block
     *            the block whose current changed
     * @param isOwnOutput
     *            whether that block is the gate's own [RA] lever
     * @return true if a monitored block was powered
     */
    private static boolean isMonitorTrigger(final Stargate stargate, final Block block, final boolean isOwnOutput)
    {
        if (isOwnOutput || !MaterialUtils.isRedstoneSource(block.getType()))
        {
            return false;
        }
        try
        {
            for (final Block m : stargate.getGateRedstoneDialMonitorBlocks())
            {
                if ((m != null) && WorldUtils.isSameBlock(m, block))
                {
                    return true;
                }
            }
        }
        catch (final Throwable ignore) { /* an unreadable block is not a redstone source */ }
        return false;
    }

    /**
     * Works this gate's dial, doing whatever the gate's current state calls for.
     *
     * @param stargate
     *            the gate to act on
     */
    private static void actOnDialTrigger(final Stargate stargate)
    {
        // Asked of the gate alone, not of the gate and its target together. A gate is marked
        // active before its target is assigned, so a signal arriving in that gap used to fall
        // through to the sign branch below and dial a gate that was already dialling.
        if (stargate.isGateActive())
        {
            extendOrLeaveOpen(stargate);
        }
        else if (stargate.isGateLightsActive() && (stargate.getGateTarget() == null))
        {
            deactivateLitGate(stargate);
        }
        else if (stargate.isGateSignPowered())
        {
            dialSignTarget(stargate);
        }
    }

    /**
     * A trigger on a gate that is already open pushes its shutdown back.
     *
     * <p>This used to close the gate, which made repeated triggers useless: a second minecart
     * over a detector rail shut the wormhole the first one had opened. Then it did nothing at
     * all, because re-dialling restarts the shutdown timer and a cart crossing every few
     * seconds would have held the gate open for ever.
     *
     * <p>It now pushes the shutdown back instead, which is what someone running carts through
     * actually wants, and is safe for the reason doing nothing was: max_open_seconds is
     * measured from when the wormhole first opened and nothing here touches it, so extending
     * can buy more time but never unlimited time. Still not a re-dial -- nothing about the
     * connection is rebuilt.
     *
     * @param stargate
     *            the open gate
     */
    private static void extendOrLeaveOpen(final Stargate stargate)
    {
        if (ConfigManager.isRedstoneExtendOpenTime())
        {
            stargate.extendOpenTime();
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Redstone trigger on already-open gate " + stargate.getGateName() + "; leaving it open.");
        }
    }

    /**
     * A trigger on a gate that is lit but was never dialled clears it.
     *
     * <p>The only way to clear a gate somebody activated and walked away from.
     *
     * @param stargate
     *            the lit gate
     */
    private static void deactivateLitGate(final Stargate stargate)
    {
        // Clearing the gate comes before the activator lookup, which is only needed for the
        // message below. removeActivatorForStargate tolerates a null gate and returns null, so
        // leading with it leaves everything after it looking like an unguarded dereference.
        // The two do not interact -- one touches timers, lever and lights, the other only the
        // activated-gate map -- so the order is free to be the one that reads honestly.
        stargate.stopActivationTimer();
        stargate.setGateActive(false);
        stargate.toggleDialLeverState(false);
        stargate.lightStargate(false);

        final Player activator = StargateManager.removeActivatorForStargate(stargate);
        if (activator != null)
        {
            activator.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate deactivated.");
        }
    }

    /**
     * A trigger on a closed sign gate dials whatever its sign is showing.
     *
     * <p>A gate that has not had its sign clicked since the server came up has no destination
     * object yet, only the saved index that names one, so that is resolved first rather than
     * the gate refusing to dial.
     *
     * @param stargate
     *            the closed sign-powered gate
     */
    private static void dialSignTarget(final Stargate stargate)
    {
        if ((stargate.getGateDialSignTarget() == null) && (stargate.getGateDialSignBlock() != null))
        {
            stargate.refreshDialSignTarget();
        }
        final Stargate signTarget = stargate.getGateDialSignTarget();
        if (signTarget != null)
        {
            stargate.dialStargate(signTarget, false);
        }
    }

    @EventHandler
    public void onBlockRedstoneChange(final BlockRedstoneEvent event)
    {
        if (!isActionableRisingEdge(event))
        {
            return;
        }

        final Block block = event.getBlock();
        final Stargate stargate = gateFor(block);
        // If this gate is not configured for redstone activation, ignore
        if ((stargate == null) || !stargate.isGateRedstonePowered())
        {
            return;
        }

        try
        {
            // Both tests are read before either is acted on, as they always have been. Acting
            // on the dial opens a gate, and a gate opening changes blocks; asking afterwards
            // would be asking about a world the first answer had already altered.
            final boolean isRedstoneSign =
                isPoweringActivationBlock(stargate.getGateRedstoneSignActivationBlock(), block);

            if (isDialTrigger(stargate, block))
            {
                // One circuit, one action -- see TRIGGER_WINDOW_MS.
                final String gateKey = stargate.getGateName();
                final long now = System.currentTimeMillis();
                if (isRepeatTrigger(lastTrigger.get(gateKey), now, TRIGGER_WINDOW_MS))
                {
                    // Returns rather than falling through to the sign cycle below: a repeat is
                    // one circuit still settling, and it must not cycle the sign either.
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                        "Ignoring repeat redstone trigger on gate " + gateKey
                            + " within " + TRIGGER_WINDOW_MS + "ms of the last one.");
                    return;
                }
                lastTrigger.put(gateKey, Long.valueOf(now));
                actOnDialTrigger(stargate);
            }

            // RS block: cycle the sign target (forward direction). The active check is asked
            // now rather than above, so a gate the dial trigger just opened is not also cycled.
            if (isRedstoneSign && !stargate.isGateActive())
            {
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
                    WormholeXTreme.getThisPlugin(),
                    new StargateUpdateRunnable(stargate, ActionToTake.DIAL_SIGN_CLICK),
                    1L);
            }
        }
        catch (final Throwable ignore) { /* a missed sign cycle is not worth breaking the event */ }
    }
}
