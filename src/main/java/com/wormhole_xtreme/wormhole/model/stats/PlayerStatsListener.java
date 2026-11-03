package com.wormhole_xtreme.wormhole.model.stats;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.wormhole_xtreme.wormhole.events.RingTravelEvent;
import com.wormhole_xtreme.wormhole.events.StargateCreatedEvent;
import com.wormhole_xtreme.wormhole.events.StargatePlayerTravelEvent;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counter;

/**
 * Counts gate trips, gates built and ring trips from the plugin's own events (#237).
 *
 * <p>At MONITOR, after every other plugin has had its say, and only for a trip nobody cancelled.
 * The check is in each handler rather than left to {@code ignoreCancelled}, so a test calling the
 * handler sees the same answer Bukkit's dispatch would give. Beams raise no event, and are counted
 * from {@code BeamTravel} instead.
 */
public class PlayerStatsListener implements Listener
{
    /**
     * A trip through a gate.
     *
     * @param event
     *            the trip, before the player is moved
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGateTrip(final StargatePlayerTravelEvent event)
    {
        if (!event.isCancelled())
        {
            PlayerStats.count(event.getPlayer().getUniqueId(), Counter.TRIPS);
        }
    }

    /**
     * A gate built; one completed by something other than a player counts for nobody.
     *
     * @param event
     *            the gate, already built and registered
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGateBuilt(final StargateCreatedEvent event)
    {
        final Player builder = event.getBuilder();
        if (builder != null)
        {
            PlayerStats.count(builder.getUniqueId(), Counter.GATES_BUILT);
        }
    }

    /**
     * A trip by transport rings.
     *
     * @param event
     *            the trip, before anybody is moved
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRingTrip(final RingTravelEvent event)
    {
        if (!event.isCancelled())
        {
            PlayerStats.count(event.getPlayer().getUniqueId(), Counter.RINGS);
        }
    }
}
