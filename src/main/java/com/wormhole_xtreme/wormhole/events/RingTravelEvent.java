/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Fired before a player is carried by a pair of transport rings.
 */
package com.wormhole_xtreme.wormhole.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;

/**
 * Fired before a player is carried by a pair of transport rings, and may be cancelled.
 *
 * <p>Raised once every check this plugin makes has already passed — permission, access list,
 * cooldown — and before anything has moved. Cancelling is how another plugin joins that
 * decision: a region protecting its claims, a combat tag refusing to let somebody leave a
 * fight, a jail, or an economy charging per trip.
 *
 * <p><b>The timing is the important part.</b> This fires after both ends have been read and
 * before either has been written. A ring swap moves everybody at both ends in the same
 * instant, so a listener always sees the whole trip as it was before any of it happened —
 * never a half-completed one where the people from one end are already standing in the
 * other.
 *
 * <p>One event per travelling player. Cancelling takes that player out of the trip and
 * leaves everyone else in it: the rings still fire, and they simply stay where they are
 * while the others go. There is no way to cancel a cycle from here, because by this point
 * the rings are up and coming down again regardless.
 *
 * <p>Fires only for players. Mobs, dropped items and vehicles travel as cargo and raise
 * nothing, so cancelling stops a person and not the world around them.
 */
public class RingTravelEvent extends Event implements Cancellable
{
    /** Bukkit dispatches on this list; it must be declared per concrete event class. */
    private static final HandlerList handlers = new HandlerList();

    /** The pair carrying them. */
    private final RingPair pair;

    /** The player about to travel. */
    private final Player player;

    /** The end they are standing in. */
    private final Ring from;

    /** The end they would arrive at. */
    private final Ring to;

    /** Whether a listener has stopped this trip. */
    private boolean cancelled = false;

    /**
     * Creates the event.
     *
     * @param pair
     *            the pair carrying them
     * @param player
     *            the player about to travel
     * @param from
     *            the end they are standing in
     * @param to
     *            the end they would arrive at
     */
    public RingTravelEvent(final RingPair pair, final Player player, final Ring from, final Ring to)
    {
        this.pair = pair;
        this.player = player;
        this.from = from;
        this.to = to;
    }

    /** @return the pair carrying them */
    public RingPair getPair()
    {
        return pair;
    }

    /** @return the player about to travel */
    public Player getPlayer()
    {
        return player;
    }

    /** @return the end they are standing in */
    public Ring getFrom()
    {
        return from;
    }

    /** @return the end they would arrive at */
    public Ring getTo()
    {
        return to;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.Cancellable#isCancelled()
     */
    @Override
    public boolean isCancelled()
    {
        return cancelled;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.Cancellable#setCancelled(boolean)
     */
    @Override
    public void setCancelled(final boolean cancelled)
    {
        this.cancelled = cancelled;
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.Event#getHandlers()
     */
    @Override
    public HandlerList getHandlers()
    {
        return handlers;
    }

    /**
     * Required by Bukkit's event system.
     *
     * @return the handler list
     */
    public static HandlerList getHandlerList()
    {
        return handlers;
    }
}
