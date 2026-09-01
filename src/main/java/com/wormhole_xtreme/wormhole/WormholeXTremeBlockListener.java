package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
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
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
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
        // Allow breaking the block directly under the dial button/lever if it is NOT the iris control block.
        try
        {
            if (stargate != null && stargate.getGateDialLeverBlock() != null)
            {
                final Block dial = stargate.getGateDialLeverBlock();
                final Block belowDial = dial.getRelative(BlockFace.DOWN);

                // Determine whether the gate's iris lever is actually present (a placed LEVER),
                // because model-only placeholders are assigned during shape detection.
                Block irisBlock = null;
                boolean irisPresent = false;
                try
                {
                    irisBlock = stargate.getGateIrisLeverBlock();
                    if (irisBlock != null && irisBlock.getType() == Material.LEVER)
                    {
                        irisPresent = true;
                    }
                }
                catch (final Throwable ignore) {}

                if ((belowDial != null) && WorldUtils.isSameBlock(belowDial, block))
                {
                    // If the block under the dial is the iris control block AND an actual
                    // lever is present there, keep protection. Otherwise allow the break.
                    if (!irisPresent || !WorldUtils.isSameBlock(irisBlock, block))
                    {
                        return false; // allow break
                    }
                }

                // Also allow breaking the block that would host the iris lever when
                // an iris lever is not actually present (the "iris placeholder").
                try
                {
                    BlockFace buttonFacing = stargate.getGateFacing(); // fallback
                    final org.bukkit.block.data.BlockData bd = dial.getBlockData();
                    if (bd instanceof Directional)
                    {
                        buttonFacing = ((Directional) bd).getFacing();
                    }
                    final Block backing = dial.getRelative(WorldUtils.getInverseDirection(buttonFacing));
                    final Block dhdBase = backing.getRelative(BlockFace.DOWN);
                    final Block irisCandidate = dhdBase.getRelative(stargate.getGateFacing());
                    if ((irisCandidate != null) && WorldUtils.isSameBlock(irisCandidate, block))
                    {
                        if (!irisPresent || !WorldUtils.isSameBlock(irisBlock, block))
                        {
                            return false; // allow break
                        }
                    }
                }
                catch (final Throwable ignore) {}
            }
        }
        catch (final Throwable ignore) {}

        // Require gate removal via command before manual block destruction.
        if (player != null)
        {
            try
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                    + "This block is part of the registered gate '" + (stargate != null ? stargate.getGateName() : "unknown") + "'.");
                player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                    + "Run '/wormhole remove " + (stargate != null ? stargate.getGateName() : "unknown") + "' to remove the gate first (use -all to also destroy blocks).");
            }
            catch (final Throwable ignore) {}
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Blocked non-player block break on registered gate: " + (stargate != null ? stargate.getGateName() : "unknown") );
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
            if ((closest != null) && (closest.isGateActive() || closest.isGateRecentlyActive()) && ((closest.getEffectivePortalMaterial()) == Material.LAVA))
            {
                final double blockDistanceSquared = StargateManager.distanceSquaredToClosestGateBlock(current, closest);
                if (((blockDistanceSquared <= (closest.getEffectiveWooshDepthSquared())) && ((closest.getEffectiveWooshDepth()) != 0)) || (blockDistanceSquared <= 25))
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
            if ((closest != null) && (closest.isGateActive() || closest.isGateRecentlyActive()) && ((closest.getEffectivePortalMaterial()) == Material.LAVA))
            {
                final double blockDistanceSquared = StargateManager.distanceSquaredToClosestGateBlock(current, closest);
                if (((blockDistanceSquared <= (closest.getEffectiveWooshDepthSquared())) && ((closest.getEffectiveWooshDepth()) != 0)) || (blockDistanceSquared <= 25))
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
            // Protect nearby ice from melting when gates use water as their portal material
            final Material t = block.getType();
            if (MaterialUtils.isIce(t))
            {
                final Location loc = block.getLocation();
                final Stargate closest = StargateManager.findNearestGateByBlock(loc, 10, 5);
                if ((closest != null) && (closest.isGateActive() || closest.isGateRecentlyActive()))
                {
                    final double d2 = StargateManager.distanceSquaredToClosestGateBlock(loc, closest);
                    if (d2 <= 16)
                    {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            if (StargateManager.isBlockInGate(block) && (block.getType() != org.bukkit.Material.REDSTONE_WIRE))
            {
                event.setCancelled(true);
            }
        }
    }
}
