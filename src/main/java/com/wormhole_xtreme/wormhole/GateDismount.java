package com.wormhole_xtreme.wormhole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

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
    /**
     * The passengers {@link RiddenTeleport} is taking off right now; main thread only. By
     * identity: the event hands back the same cached Bukkit entity the passenger list held.
     */
    private static final Set<Entity> UNSEATING =
        Collections.newSetFromMap(new IdentityHashMap<>());

    /** Static helpers only. */
    private GateDismount()
    {
    }

    /**
     * Runs {@code unseat} with this rule off for {@code riders} alone, so the plugin's own
     * dismount at a portal is not refused by its own listener, and nobody else's is waved through.
     *
     * @param riders
     *            the passengers being taken off
     * @param unseat
     *            the dismounts to allow
     */
    static void allowWhile(final Collection<? extends Entity> riders, final Runnable unseat)
    {
        final List<Entity> added = new ArrayList<>();
        for (final Entity rider : riders)
        {
            if (UNSEATING.add(rider))
            {
                added.add(rider);
            }
        }
        try
        {
            unseat.run();
        }
        finally
        {
            added.forEach(UNSEATING::remove);
        }
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
        if (!(who instanceof Player) || UNSEATING.contains(who))
        {
            return false;
        }
        try
        {
            final Location loc = who.getLocation();
            final Block b = loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            final Stargate s = StargateManager.getGateFromBlock(b);
            return (s != null) && s.isGatePortalOpen() && StargateManager.isPortalBlock(b);
        }
        catch (final RuntimeException ignore)
        {
            // Deciding this is not worth disturbing a dismount over.
            return false;
        }
    }
}
