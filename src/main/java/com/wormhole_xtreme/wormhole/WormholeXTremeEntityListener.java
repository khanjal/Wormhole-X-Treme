package com.wormhole_xtreme.wormhole;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * WormholeXtreme Entity Listener.
 * 
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
class WormholeXTremeEntityListener implements Listener
{

    /**
     * Handle entity explode event.
     * 
     * @param explodeBlocks
     *            the explode blocks
     * @return true, if successful
     */
    private static boolean handleEntityExplodeEvent(final List<Block> explodeBlocks)
    {
        final List<Block> eb = explodeBlocks;
        for (int i = 0; i < eb.size(); i++)
        {
            // One lookup per block, not two. isBlockInGate and getGateFromBlock ask the index
            // the same question, and this asked both of every block of every explosion -- and
            // a single charge can list hundreds of blocks.
            final Stargate s = StargateManager.getGateFromBlock(eb.get(i));
            if (s != null)
            {
                if (WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Blocked Creeper Explosion on Stargate: \"" + s.getGateName() + "\"");
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Handle Player damage event.
     * 
     * @param event
     *            the event
     * @return true, if successful
     */
    private static boolean handlePlayerDamageEvent(final EntityDamageEvent event)
    {
        final Player p = (Player) event.getEntity();
        final Location current = p.getLocation();
        // A local lookup against the indexed gate blocks, not a walk of every gate on the
        // server. This runs on every fire, fire-tick and lava damage event for every player,
        // so a burning crowd was paying a full scan of the gate list several times a second
        // each -- and the old scan sorted the list on the way, since findClosestStargate goes
        // through getAllGates.
        //
        // Bounded at the same radius the block-ignite guard beside it uses, which is the same
        // guard on the same question. Ten blocks comfortably covers what the test below can
        // match: a custom woosh depth is capped at 5 by the command that sets it, and the
        // fallback is 16 -- four blocks.
        final Stargate closest = StargateManager.findNearestGateByBlock(current, 10, 5);
        if ((closest != null) && (((closest.getEffectivePortalMaterial()) == Material.LAVA) || ((closest.getGateTarget() != null) && ((closest.getGateTarget().getEffectivePortalMaterial()) == Material.LAVA))))
        {
            final double blockDistanceSquared = StargateManager.distanceSquaredToClosestGateBlock(current, closest);
            if ((closest.isGateActive() || closest.isGateRecentlyActive()) && (((blockDistanceSquared <= (closest.getEffectiveWooshDepthSquared())) && ((closest.getEffectiveWooshDepth()) != 0)) || (blockDistanceSquared <= 16)))
            {
                if (WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Blocked Gate: \"" + closest.getGateName() + "\" Proximity Event: \"" + event.getCause().toString() + "\" On: \"" + p.getName() + "\" Distance Squared: \"" + blockDistanceSquared + "\"");
                }
                p.setFireTicks(0);
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.entity.EntityListener#onEntityDamage(org.bukkit.event.entity.EntityDamageEvent)
     */
    @EventHandler
    public void onEntityDamage(final EntityDamageEvent event)
    {
        if ( !event.isCancelled()
            && (event.getCause().equals(DamageCause.FIRE)
                || event.getCause().equals(DamageCause.FIRE_TICK)
                || event.getCause().equals(DamageCause.LAVA))
            && (event.getEntity() instanceof Player)
            && handlePlayerDamageEvent(event))
        {
            event.setCancelled(true);
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.entity.EntityListener#onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent)
     */
    @EventHandler
    public void onEntityExplode(final EntityExplodeEvent event)
    {
        if ( !event.isCancelled())
        {
            final List<Block> explodeBlocks = event.blockList();
            if (handleEntityExplodeEvent(explodeBlocks))
            {
                event.setCancelled(true);
            }
        }
    }

}
