package com.wormhole_xtreme.wormhole.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.wormhole_xtreme.wormhole.events.StargatePlayerTravelEvent;

/**
 * Stops a trip through a gate when a region at either end denies {@code wormhole-use}.
 *
 * <p>Walking in and riding in both raise the travel event, so this one listener covers both.
 */
final class RegionTravelListener implements Listener
{
    /** How long before a refused player is told again; holding forward refuses every tick. */
    static final long REMINDER_MILLIS = 2000L;

    /** When each recently refused player was last told. */
    private final Map<UUID, Long> lastTold = new HashMap<>();

    /**
     * Cancels the trip if a region at the gate entered, or the gate it leads to, denies use.
     *
     * @param event
     *            the trip about to happen
     */
    @EventHandler(ignoreCancelled = true)
    public void onTravel(final StargatePlayerTravelEvent event)
    {
        final Player player = event.getPlayer();
        if (RegionFlags.mayUse(player, event.getStargate()) && RegionFlags.mayUse(player, event.getDestination()))
        {
            return;
        }
        event.setCancelled(true);
        remind(player, System.currentTimeMillis());
    }

    /** Tells the player why, unless they were told a moment ago. */
    private void remind(final Player player, final long now)
    {
        lastTold.values().removeIf(at -> (now - at) > REMINDER_MILLIS);
        final UUID id = player.getUniqueId();
        if (lastTold.putIfAbsent(id, now) == null)
        {
            player.sendMessage(RegionFlags.USE_REFUSED);
        }
    }
}
