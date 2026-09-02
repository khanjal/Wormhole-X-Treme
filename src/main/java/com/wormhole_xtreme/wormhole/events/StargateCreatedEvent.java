package com.wormhole_xtreme.wormhole.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Fired after a gate has been built, named and registered.
 *
 * <p>Sent once the gate exists and can be looked up by name, so a listener may read it,
 * record it, or announce it. It is not cancellable: by the time this fires the gate is
 * already built and saved, and a listener that wants to prevent one should refuse the
 * player the build permission instead.
 *
 * <p>{@link #getBuilder()} is the player who completed it, and may be null for a gate
 * created by something other than a player.
 */
public class StargateCreatedEvent extends StargateEvent
{
    /** Bukkit dispatches on this list; it must be declared per concrete event class. */
    private static final HandlerList handlers = new HandlerList();

    /** The player who completed the gate, or null if it was not a player. */
    private final Player builder;

    /**
     * Creates the event.
     *
     * @param stargate
     *            the gate that was created
     * @param builder
     *            the player who completed it, or null
     */
    public StargateCreatedEvent(final Stargate stargate, final Player builder)
    {
        super(stargate);
        this.builder = builder;
    }

    /**
     * The player who completed the gate.
     *
     * @return the builder, or null if the gate was not completed by a player
     */
    public Player getBuilder()
    {
        return builder;
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
