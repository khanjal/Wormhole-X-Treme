package com.wormhole_xtreme.wormhole.events;

import org.bukkit.event.HandlerList;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Fired when a gate's wormhole opens.
 *
 * <p>Sent once per gate, as it becomes active. A dialled pair raises two of these, one at each
 * end, because each end opened; a gate that opens without a partner raises one. It is not
 * cancellable: by the time this fires the wormhole is already open, and a listener that wants
 * to prevent a trip should cancel {@link StargatePlayerTravelEvent} instead.
 *
 * <p><strong>The destination is deliberately not carried here.</strong> A gate is marked active
 * before it is linked -- the dialling end's target is set immediately afterwards, and the far
 * end never receives a reciprocal one -- so a destination field on this event would read null
 * at both ends and be worse than no field at all. A listener that needs the far end should read
 * {@link Stargate#getGateTarget()} from the gate once dialling has settled, or watch
 * {@link StargatePlayerTravelEvent}, which carries both ends and where somebody is going.
 *
 * <p>The two handler-list accessors below are necessarily identical, which is why this class
 * carries {@code @SuppressWarnings("java:S4144")}. Bukkit requires both: {@code Event} declares
 * {@code getHandlers()} abstract, and {@code SimplePluginManager} looks the static
 * {@code getHandlerList()} up reflectively. Both return the same field because there is one
 * handler list per event type.
 *
 * @see StargateShutdownEvent
 */
@SuppressWarnings("java:S4144")
public class StargateActivatedEvent extends StargateEvent
{
    /** Bukkit dispatches on this list; it must be declared per concrete event class. */
    private static final HandlerList handlers = new HandlerList();

    /**
     * Creates the event.
     *
     * @param stargate
     *            the gate whose wormhole opened
     */
    public StargateActivatedEvent(final Stargate stargate)
    {
        super(stargate);
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
