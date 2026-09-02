package com.wormhole_xtreme.wormhole.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Fired while a gate is being removed, before it is torn down.
 *
 * <p>Sent early enough that the gate can still be read: its name, owner, network, blocks and
 * teleport location are all still populated, which is what a listener cleaning up its own
 * records needs. Once removal completes those are gone.
 *
 * <p>Not cancellable. Removal is already under way by the time listeners run, and letting
 * one veto it halfway through would leave the gate half deregistered.
 *
 * <p>{@link #getRemover()} is the player who removed it, and may be null when the gate is
 * removed by something other than a player, such as its structure being broken.
 */
public class StargateRemovedEvent extends StargateEvent
{
    /** Bukkit dispatches on this list; it must be declared per concrete event class. */
    private static final HandlerList handlers = new HandlerList();

    /** The player who removed the gate, or null if it was not a player. */
    private final Player remover;

    /**
     * Creates the event.
     *
     * @param stargate
     *            the gate being removed
     * @param remover
     *            the player who removed it, or null
     */
    public StargateRemovedEvent(final Stargate stargate, final Player remover)
    {
        super(stargate);
        this.remover = remover;
    }

    /**
     * The player who removed the gate.
     *
     * @return the remover, or null if the gate was not removed by a player
     */
    public Player getRemover()
    {
        return remover;
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
