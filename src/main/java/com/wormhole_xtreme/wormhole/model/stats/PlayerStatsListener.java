package com.wormhole_xtreme.wormhole.model.stats;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.wormhole_xtreme.wormhole.events.StargateCreatedEvent;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counter;

/**
 * Counts gates built, from the plugin's own event (#237).
 *
 * <p>Trips are not counted from the travel events: those fire before anybody moves, and a trip can
 * still fail after them, or be asked about twice for one cart. Gate, ring and beam trips are counted
 * where the traveller has been moved instead.
 */
public class PlayerStatsListener implements Listener
{
    /**
     * A gate built; one completed by something other than a player counts for nobody.
     *
     * @param event
     *            the gate, already built and registered
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGateBuilt(final StargateCreatedEvent event)
    {
        PlayerStats.countPlayer(event.getBuilder(), Counter.GATES_BUILT);
    }
}
