package com.wormhole_xtreme.wormhole;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.spigotmc.event.entity.EntityDismountEvent;

/**
 * Refuses a dismount inside an open portal, on servers up to 1.20.4.
 *
 * <p>The same rule as {@link GateDismountListener}, against the package the event used to
 * live in. Spigot moved it to {@code org.bukkit.event.entity} in 1.20.4 and dropped this one
 * in 1.20.6, so no single import covers the supported range and each end needs its own class.
 *
 * <p>1.20.4 is the only version that has both, which is why this plugin compiles against it:
 * it is the one place where both of these classes can be written down at once.
 */
public class LegacyGateDismountListener implements Listener
{
    /**
     * Holds a rider in place while they are standing in an open portal.
     */
    @EventHandler
    // The old package is deprecated, but it is the only one servers before 1.20.4 fire.
    @SuppressWarnings("deprecation")
    public void onEntityDismount(final EntityDismountEvent event)
    {
        if ((event != null) && !event.isCancelled() && GateDismount.shouldRefuse(event.getEntity()))
        {
            event.setCancelled(true);
        }
    }
}
