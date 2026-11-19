package com.wormhole_xtreme.wormhole.model.ping;

import org.bukkit.event.EventHandler;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Notes a ping's cancelled spawn, and takes a ping down when its owner leaves or its world unloads. */
public final class PingListener implements Listener
{
    /**
     * Records whether a marker's own spawn finished cancelled, so it is not counted as a ping.
     *
     * @param event
     *            any entity's spawn; an armour stand's is a living entity's, so it arrives here too
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnSettled(final EntitySpawnEvent event)
    {
        if (PingMarkers.isSpawning() && (event.getEntity() instanceof ArmorStand))
        {
            PingMarkers.spawnSettled(event.isCancelled());
        }
    }

    /**
     * A ping is for one player's bar, and theirs has gone.
     *
     * @param event
     *            the quit
     */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event)
    {
        PingMarkers.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Takes the world's pings down before it goes, so none is left counted or holding a chunk.
     *
     * @param event
     *            the unload
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(final WorldUnloadEvent event)
    {
        PingMarkers.removeIn(event.getWorld());
    }
}
