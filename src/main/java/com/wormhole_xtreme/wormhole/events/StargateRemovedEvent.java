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
 *
 * <p>The two handler-list accessors below are necessarily identical, which is why this class
 * carries {@code @SuppressWarnings("java:S4144")}. Bukkit requires both: {@code Event}
 * declares {@code getHandlers()} abstract, and {@code SimplePluginManager} looks the static
 * {@code getHandlerList()} up reflectively -- it carries the literal error string
 * {@code getHandlerList must be static}. Both return the same field because there is one
 * handler list per event type. Removing or delegating either breaks event registration at
 * runtime, and no test here would catch it: the tests do not run a plugin manager.
 */
@SuppressWarnings("java:S4144")
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
