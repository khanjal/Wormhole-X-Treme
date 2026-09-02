package com.wormhole_xtreme.wormhole;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;

/**
 * Refuses a dismount inside an open portal, on servers from 1.20.4 onward.
 *
 * <p>Deliberately its own class holding nothing else. Referencing
 * {@code org.bukkit.event.entity.EntityDismountEvent} is what fails to load on a server
 * older than 1.20.4, and keeping it alone means that failure costs only this listener rather
 * than taking every other entity handler down with it.
 *
 * @see LegacyGateDismountListener
 */
public class GateDismountListener implements Listener
{
    /**
     * Holds a rider in place while they are standing in an open portal.
     *
     * @param event
     *            the dismount
     */
    @EventHandler
    public void onEntityDismount(final EntityDismountEvent event)
    {
        if ((event != null) && !event.isCancelled() && GateDismount.shouldRefuse(event.getEntity()))
        {
            event.setCancelled(true);
        }
    }
}
