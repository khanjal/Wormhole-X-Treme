package com.wormhole_xtreme.wormhole.plugin.map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.wormhole_xtreme.wormhole.events.StargateActivatedEvent;
import com.wormhole_xtreme.wormhole.events.StargateCreatedEvent;
import com.wormhole_xtreme.wormhole.events.StargateRemovedEvent;
import com.wormhole_xtreme.wormhole.events.StargateShutdownEvent;

/**
 * Brings the map up to date on the next tick after a gate is built, removed or shut, and once
 * a dialled gate's wormhole has formed, rather than waiting for the periodic look.
 *
 * <p>Only asks: the gate is often still changing when its event fires, so the look happens a
 * tick later, once it has settled.
 */
public final class MapRefreshListener implements Listener
{
    /**
     * A gate was built.
     *
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreated(final StargateCreatedEvent event)
    {
        MapMarkers.requestRefresh();
    }

    /**
     * A gate was removed.
     *
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemoved(final StargateRemovedEvent event)
    {
        MapMarkers.requestRefresh();
    }

    /**
     * A gate was dialled. It shows as open once its wormhole forms, which fires no event of its
     * own, so it is watched for that.
     *
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onActivated(final StargateActivatedEvent event)
    {
        MapMarkers.watchForming(event.getStargate());
    }

    /**
     * A gate shut.
     *
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onShutdown(final StargateShutdownEvent event)
    {
        MapMarkers.requestRefresh();
    }
}
