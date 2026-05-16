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
package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * WormholeXTreme Block Listener.
 * 
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
class WormholeXTremeBlockListener implements Listener
{
    /**
     * Handle block break.
     * 
     * @param player
     *            the player
     * @param stargate
     *            the stargate
     * @param block
     *            the block
     * @return true, if successful
     */
    private static boolean handleBlockBreak(final Player player, final Stargate stargate, final Block block)
    {
        // Require gate removal via command before manual block destruction.
        if (player != null)
        {
            try
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                    + "This block is part of the registered gate '" + stargate.getGateName() + "'.");
                player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                    + "Run '/wormhole remove " + stargate.getGateName() + "' to remove the gate first (use -all to also destroy blocks).");
            }
            catch (final Throwable ignore) {}
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Blocked non-player block break on registered gate: " + stargate.getGateName());
        }
        // Return true to signal that the break should be cancelled.
        return true;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockBreak(org.bukkit.event.block.BlockBreakEvent)
     */
    @EventHandler
    public void onBlockBreak(final BlockBreakEvent event)
    {
        if ( !event.isCancelled())
        {
            final Block block = event.getBlock();
            final Stargate stargate = StargateManager.getGateFromBlock(block);
            final Player player = event.getPlayer();
            if ((stargate != null) && handleBlockBreak(player, stargate, block))
            {
                event.setCancelled(true);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockBurn(org.bukkit.event.block.BlockBurnEvent)
     */
    @EventHandler
    public void onBlockBurn(final BlockBurnEvent event)
    {
        if ( !event.isCancelled())
        {
            final Location current = event.getBlock().getLocation();
            // Localized lookup: scan nearby indexed gate blocks instead of iterating all gates
            final Stargate closest = StargateManager.findNearestGateByBlock(current, 10, 5);
            if ((closest != null) && (closest.isGateActive() || closest.isGateRecentlyActive()) && ((closest.isGateCustom()
                ? closest.getGateCustomPortalMaterial()
                : closest.getGateShape() != null
                    ? closest.getGateShape().getShapePortalMaterial()
                    : Material.WATER) == Material.LAVA))
            {
                final double blockDistanceSquared = StargateManager.distanceSquaredToClosestGateBlock(current, closest);
                if (((blockDistanceSquared <= (closest.isGateCustom()
                    ? closest.getGateCustomWooshDepthSquared()
                    : closest.getGateShape() != null
                        ? closest.getGateShape().getShapeWooshDepthSquared()
                        : 0)) && ((closest.isGateCustom()
                    ? closest.getGateCustomWooshDepth()
                    : closest.getGateShape() != null
                        ? closest.getGateShape().getShapeWooshDepth()
                        : 0) != 0)) || (blockDistanceSquared <= 25))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Blocked Gate: \"" + closest.getGateName() + "\" Proximity Block Burn Distance Squared: \"" + blockDistanceSquared + "\"");
                    event.setCancelled(true);
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockDamage(org.bukkit.event.block.BlockDamageEvent)
     */
    @EventHandler
    public void onBlockDamage(final BlockDamageEvent event)
    {
        if ( !event.isCancelled())
        {
            final Stargate stargate = StargateManager.getGateFromBlock(event.getBlock());
            final Player player = event.getPlayer();
            if ((stargate != null) && (player != null) && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.DAMAGE))
            {
                event.setCancelled(true);
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Player: " + player.getName() + " denied damage on: " + stargate.getGateName());
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockFlow(org.bukkit.event.block.BlockFromToEvent)
     */
    @EventHandler
    public void onBlockFromTo(final BlockFromToEvent event)
    {
        if ( !event.isCancelled())
        {
            if (StargateManager.isBlockInGate(event.getToBlock()) || StargateManager.isBlockInGate(event.getBlock()))
            {
                event.setCancelled(true);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockIgnite(org.bukkit.event.block.BlockIgniteEvent)
     */
    @EventHandler
    public void onBlockIgnite(final BlockIgniteEvent event)
    {
        if ( !event.isCancelled())
        {
            final Location current = event.getBlock().getLocation();
            // Localized lookup: scan nearby indexed gate blocks instead of iterating all gates
            final Stargate closest = StargateManager.findNearestGateByBlock(current, 10, 5);
            if ((closest != null) && (closest.isGateActive() || closest.isGateRecentlyActive()) && ((closest.isGateCustom()
                ? closest.getGateCustomPortalMaterial()
                : closest.getGateShape() != null
                    ? closest.getGateShape().getShapePortalMaterial()
                    : Material.WATER) == Material.LAVA))
            {
                final double blockDistanceSquared = StargateManager.distanceSquaredToClosestGateBlock(current, closest);
                if (((blockDistanceSquared <= (closest.isGateCustom()
                    ? closest.getGateCustomWooshDepthSquared()
                    : closest.getGateShape().getShapeWooshDepthSquared())) && ((closest.isGateCustom()
                    ? closest.getGateCustomWooshDepth()
                    : closest.getGateShape() != null
                        ? closest.getGateShape().getShapeWooshDepth()
                        : 0) != 0)) || (blockDistanceSquared <= 25))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Blocked Gate: \"" + closest.getGateName() + "\" Block Type: \"" + event.getBlock().getType().toString() + "\" Proximity Block Ignite: \"" + event.getCause().toString() + "\" Distance Squared: \"" + blockDistanceSquared + "\"");
                    event.setCancelled(true);
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockPhysics(org.bukkit.event.block.BlockPhysicsEvent)
     */
    @EventHandler
    public void onBlockPhysics(final BlockPhysicsEvent event)
    {
        if ( !event.isCancelled())
        {
            final Block block = event.getBlock();
            if (StargateManager.isBlockInGate(block) && (block.getType() != org.bukkit.Material.REDSTONE_WIRE))
            {
                event.setCancelled(true);
            }
        }
    }
}
