package com.wormhole_xtreme.wormhole;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Whether a rider may get off where they are.
 *
 * <p>The rule is one line: not while standing in an open portal. A player who dismounts
 * mid-transit is separated from whatever was carrying them, and the two arrive in different
 * places, or one of them does not arrive at all.
 *
 * <p>The rule lives here rather than in a listener because Spigot moved the event that
 * reports it. {@code EntityDismountEvent} was {@code org.spigotmc.event.entity} up to 1.20.4
 * and {@code org.bukkit.event.entity} from 1.20.4 on — 1.20.4 is the single version carrying
 * both, and is what this plugin compiles against. Each package gets its own small listener,
 * and only the one whose class the running server actually has is registered. They share
 * this.
 *
 * @see GateDismountListener
 * @see LegacyGateDismountListener
 */
final class GateDismount
{
    /** Static helpers only. */
    private GateDismount()
    {
    }

    /**
     * Whether a dismount should be refused.
     *
     * @param who
     *            the entity getting off
     * @return true if they are a player standing in an open gate's portal
     */
    static boolean shouldRefuse(final Entity who)
    {
        if (!(who instanceof Player))
        {
            return false;
        }
        try
        {
            final Location loc = who.getLocation();
            final Block b = loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            final Stargate s = StargateManager.getGateFromBlock(b);
            return (s != null) && s.isGateActive() && StargateManager.isPortalBlock(b);
        }
        catch (final RuntimeException ignore)
        {
            // Deciding this is not worth disturbing a dismount over.
            return false;
        }
    }
}
