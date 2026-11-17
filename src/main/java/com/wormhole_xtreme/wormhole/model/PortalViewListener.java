package com.wormhole_xtreme.wormhole.model;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Learns where nether portals go from the trips players take, and forgets what goes away (#514).
 *
 * <p>Only watches: it never changes a trip. Each handler keeps its own failure to itself.
 */
public final class PortalViewListener implements Listener
{
    /**
     * Notes a player stepping into a nether portal, once nothing has cancelled the trip.
     *
     * @param event
     *            the trip
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(final PlayerPortalEvent event)
    {
        try
        {
            if (event.getCause() == TeleportCause.NETHER_PORTAL)
            {
                PortalViews.leaving(event.getPlayer().getUniqueId(), event.getFrom(), System.currentTimeMillis());
            }
        }
        catch (final Exception | LinkageError e)
        {
            GateViews.failedOnce("Could not note a nether portal trip", e);
        }
    }

    /**
     * Learns where the portal went from where the player landed.
     *
     * @param event
     *            the change of world
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(final PlayerChangedWorldEvent event)
    {
        try
        {
            PortalViews.arrived(event.getPlayer().getUniqueId(), event.getPlayer().getLocation(),
                System.currentTimeMillis());
        }
        catch (final Exception | LinkageError e)
        {
            GateViews.failedOnce("Could not learn where a nether portal went", e);
        }
    }

    /**
     * Forgets a trip under way for a player who left.
     *
     * @param event
     *            the quit
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event)
    {
        try
        {
            PortalViews.forgetViewer(event.getPlayer().getUniqueId());
        }
        catch (final Exception | LinkageError e)
        {
            GateViews.failedOnce("Could not forget a nether portal trip", e);
        }
    }

    /**
     * Forgets the links of a world going away.
     *
     * @param event
     *            the unload
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(final WorldUnloadEvent event)
    {
        try
        {
            PortalViews.worldUnloaded(event.getWorld().getName());
        }
        catch (final Exception | LinkageError e)
        {
            GateViews.failedOnce("Could not drop nether portal links of an unloaded world", e);
        }
    }
}
