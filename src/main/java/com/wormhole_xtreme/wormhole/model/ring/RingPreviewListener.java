package com.wormhole_xtreme.wormhole.model.ring;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Takes a player's ring preview back when what it was drawn over goes away (#332).
 *
 * <p>Quitting and changing world are the two ways a player leaves the blocks they were shown.
 * The client forgets its drawing on its own in both, but the preview's pending steps and its
 * timeout would go on holding the player, so both are cancelled here.
 */
public final class RingPreviewListener implements Listener
{
    /**
     * Clears the preview of somebody who has gone.
     *
     * @param event
     *            the quit
     */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event)
    {
        RingPreviews.clear(event.getPlayer());
    }

    /**
     * Clears the preview of somebody who has changed world.
     *
     * @param event
     *            the change
     */
    @EventHandler
    public void onChangedWorld(final PlayerChangedWorldEvent event)
    {
        RingPreviews.clear(event.getPlayer());
    }
}
