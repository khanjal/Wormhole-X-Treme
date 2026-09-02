package com.wormhole_xtreme.wormhole.events;

import org.bukkit.event.Event;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Base class for events about a single gate.
 *
 * <p>Holds the gate the event concerns. It deliberately does <em>not</em> hold the handler
 * list: Bukkit dispatches on the list returned by the concrete class, so a list declared
 * here would be shared by every subclass and a listener registered for one kind of event
 * would be called for all of them. Each concrete event declares its own.
 *
 * @see StargateCreatedEvent
 * @see StargateRemovedEvent
 */
public abstract class StargateEvent extends Event
{
    /** The gate this event concerns. */
    private final Stargate stargate;

    /**
     * Creates an event about a gate.
     *
     * @param stargate
     *            the gate the event concerns, never null
     */
    protected StargateEvent(final Stargate stargate)
    {
        if (stargate == null)
        {
            throw new IllegalArgumentException("stargate must not be null");
        }
        this.stargate = stargate;
    }

    /**
     * The gate this event concerns.
     *
     * @return the gate, never null
     */
    public Stargate getStargate()
    {
        return stargate;
    }

    /**
     * The gate's name, as a convenience for the common case.
     *
     * @return the gate name
     */
    public String getStargateName()
    {
        return stargate.getGateName();
    }
}
