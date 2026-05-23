// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2011 Ben Echols, Dean Bailey. See LICENSE.txt for terms.
package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * The listener interface for receiving wormholeXTremeRedstone events.
 * The class that is interested in processing a wormholeXTremeRedstone
 * event implements this interface, and the object created
 * with that class is registered with a component using the
 * component's <code>addWormholeXTremeRedstoneListener<code> method. When
 * the wormholeXTremeRedstone event occurs, that object's appropriate
 * method is invoked.
 * 
 * @see WormholeXTremeRedstoneEvent
 */
class WormholeXTremeRedstoneListener implements Listener
{
    /**
     * Checks if current is new.
     * 
     * @param oldCurrent
     *            the old current
     * @param newCurrent
     *            the new current
     * @return true, if is current new
     */
    private static boolean isCurrentNew(final int oldCurrent, final int newCurrent)
    {
        if (((oldCurrent == 0) && (newCurrent > 0)) || ((oldCurrent > 0) && (newCurrent == 0)))
        {
            return true;
        }
        return false;
    }

    /**
     * Checks if current is on.
     * 
     * @param oldCurrent
     *            the old current
     * @param newCurrent
     *            the new current
     * @return true, if is current on
     */
    private static boolean isCurrentOn(final int oldCurrent, final int newCurrent)
    {
        return (newCurrent > 0) && (oldCurrent == 0)
            ? true
            : false;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockRedstoneChange(org.bukkit.event.block.BlockRedstoneEvent)
     */
    @EventHandler
    public void onBlockRedstoneChange(final BlockRedstoneEvent event)
    {
        final Block block = event.getBlock();
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINEST, false, "Caught redstone event on block: " + block.toString() + " oldCurrent: " + event.getOldCurrent() + " newCurrent: " + event.getNewCurrent());
        if (StargateManager.isBlockInGate(block))
        {
            final Stargate stargate = StargateManager.getGateFromBlock(event.getBlock());
            if (stargate.isGateSignPowered() && stargate.isGateRedstonePowered() && (block.getType() == Material.REDSTONE_WIRE) && isCurrentNew(event.getOldCurrent(), event.getNewCurrent()))
            {
                if ((stargate.getGateRedstoneSignActivationBlock() != null) && block.equals(stargate.getGateRedstoneSignActivationBlock()) && isCurrentOn(event.getOldCurrent(), event.getNewCurrent()))
                {
                    stargate.tryClickTeleportSign(stargate.getGateDialSignBlock());
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught redstone sign event on gate: " + stargate.getGateName() + " block: " + block.toString());
                }
                else if ((stargate.getGateRedstoneDialActivationBlock() != null) && block.equals(stargate.getGateRedstoneDialActivationBlock()) && isCurrentOn(event.getOldCurrent(), event.getNewCurrent()))
                {
                    if (stargate.isGateActive() && (stargate.getGateTarget() != null))
                    {
                        stargate.shutdownStargate(true);
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught redstone shutdown event on gate: " + stargate.getGateName() + " block: " + block.toString());
                    }
                    if ( !stargate.isGateActive() && (stargate.getGateDialSignTarget() != null) && !stargate.isGateRecentlyActive())
                    {
                        stargate.dialStargate(stargate.getGateDialSignTarget(), false);
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught redstone dial event on gate: " + stargate.getGateName() + " block: " + block.toString());
                    }
                }
            }
        }
    }
}
