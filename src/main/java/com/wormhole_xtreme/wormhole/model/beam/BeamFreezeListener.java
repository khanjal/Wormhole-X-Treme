package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps a beaming player's feet where {@link BeamAnimation} left them, without touching their
 * camera -- position is reverted on any x/y/z change, but the new yaw/pitch is kept, so
 * looking around during the sequence still works.
 */
public final class BeamFreezeListener implements Listener
{
    @EventHandler
    public void onMove(final PlayerMoveEvent event)
    {
        if (!BeamFreeze.isFrozen(event.getPlayer()))
        {
            return;
        }
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (to == null)
        {
            return;
        }
        if ((from.getX() != to.getX()) || (from.getY() != to.getY()) || (from.getZ() != to.getZ()))
        {
            event.setTo(new Location(to.getWorld(), from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event)
    {
        // Neither state must survive the player it was tracking -- including active alone,
        // with frozen not yet set, if they disconnect during the envelope. Otherwise the
        // already-beaming guard would refuse them a beam for good after they reconnect,
        // with nothing left running that could ever clear it.
        BeamFreeze.clear(event.getPlayer());
    }
}
