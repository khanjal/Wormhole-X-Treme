package com.wormhole_xtreme.wormhole.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Fired before a player travels through a wormhole, and may be cancelled to stop them.
 *
 * <p>Raised once every check this plugin makes has already passed — permission, iris code,
 * cooldown, one-way, same-world — and before anything is moved. Cancelling is how another
 * plugin joins that decision: a region protecting its claims, a combat tag refusing to let
 * someone leave a fight, a jail, or an economy charging per trip rather than per gate built.
 *
 * <p>The gate from {@link #getStargate()} is the one being entered. {@link #getDestination()}
 * is where it leads and {@link #getArrival()} is the exact spot the player would land, after
 * the safe-location search has run.
 *
 * <p>Fires for a player on foot and for one riding anything — a horse, a minecart, a boat.
 * It does not fire for whatever is carrying them, nor for anything travelling on its own,
 * so cancelling stops the player and not the world around them.
 *
 * <p>A cancelled traveller is held rather than moved. If they were walking in they are kept
 * out; if they were already standing in the portal they are left free to walk away, because
 * refusing every move of someone already inside would trap them there.
 */
public class StargatePlayerTravelEvent extends StargateEvent implements Cancellable
{
    /** Bukkit dispatches on this list; it must be declared per concrete event class. */
    private static final HandlerList handlers = new HandlerList();

    /** The player about to travel. */
    private final Player player;

    /** The gate at the far end. */
    private final Stargate destination;

    /** Where the player would arrive. */
    private final Location arrival;

    /** Whether a listener has stopped this trip. */
    private boolean cancelled = false;

    /**
     * Creates the event.
     *
     * @param stargate
     *            the gate being entered
     * @param player
     *            the player about to travel
     * @param destination
     *            the gate at the far end
     * @param arrival
     *            where the player would land
     */
    public StargatePlayerTravelEvent(final Stargate stargate, final Player player,
                                     final Stargate destination, final Location arrival)
    {
        super(stargate);
        if (player == null)
        {
            throw new IllegalArgumentException("player must not be null");
        }
        this.player = player;
        this.destination = destination;
        this.arrival = arrival;
    }

    /**
     * The player about to travel.
     *
     * @return the player, never null
     */
    public Player getPlayer()
    {
        return player;
    }

    /**
     * The gate at the far end of the wormhole.
     *
     * @return the destination gate
     */
    public Stargate getDestination()
    {
        return destination;
    }

    /**
     * Where the player would arrive, after the safe-location search.
     *
     * @return the arrival location
     */
    public Location getArrival()
    {
        return arrival;
    }

    @Override
    public boolean isCancelled()
    {
        return cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel)
    {
        cancelled = cancel;
    }

    /**
     * The handler list for this event type.
     *
     * @return the handlers
     */
    public static HandlerList getHandlerList()
    {
        return handlers;
    }

    @Override
    public HandlerList getHandlers()
    {
        return handlers;
    }
}
