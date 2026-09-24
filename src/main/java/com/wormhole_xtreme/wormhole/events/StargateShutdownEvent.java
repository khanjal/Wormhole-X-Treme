package com.wormhole_xtreme.wormhole.events;

import org.bukkit.event.HandlerList;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Fired when a gate's wormhole closes.
 *
 * <p>Sent once per gate that was actually open. Closing a gate that was already shut raises
 * nothing: shutdown is called defensively in several places -- before a removal, on plugin
 * disable, on a gate that may or may not be running -- and an event on those paths would
 * announce a wormhole closing that was never open. A dialled pair raises two of these, one at
 * each end, the far end's carrying {@link Reason#FAR_END}.
 *
 * <p>The gate is already closed when this fires, so {@link #getStargate()} reads as inactive
 * and its target has been cleared. It is not cancellable; a listener is being told, not asked.
 *
 * <p>The two handler-list accessors below are necessarily identical, which is why this class
 * carries {@code @SuppressWarnings("java:S4144")}. Bukkit requires both: {@code Event} declares
 * {@code getHandlers()} abstract, and {@code SimplePluginManager} looks the static
 * {@code getHandlerList()} up reflectively.
 *
 * @see StargateActivatedEvent
 */
@SuppressWarnings("java:S4144")
public class StargateShutdownEvent extends StargateEvent
{
    /**
     * Why a wormhole closed.
     *
     * <p>Every constant here is raised by a real path. There is deliberately no {@code IRIS}:
     * raising the iris into an open gate fills the portal and does not close the wormhole, so a
     * constant for it would be one that never arrives -- which reads as a working feature until
     * somebody writes a listener for it.
     */
    public enum Reason
    {
        /**
         * The configured shutdown delay elapsed.
         *
         * <p>Includes the zero-delay case: with {@code timeout-shutdown} set to 0 a gate closes
         * as soon as somebody has travelled through it, which is the same clock run down to
         * nothing rather than a separate kind of closing. Also covers a gate reaching its
         * maximum open time, and the failure to schedule a shutdown task at all, which closes
         * the gate immediately rather than leaving it open forever.
         */
        TIMEOUT,

        /** A player worked the switch, or an admin ran a command that closes the gate. */
        MANUAL,

        /**
         * The gate at the other end closed, or never managed to open.
         *
         * <p>Shutting one end always shuts the other, so an ordinary close raises one event
         * with the reason that closed it and one with this.
         */
        FAR_END,

        /** The gate is being removed, and its wormhole closes on the way out. */
        REMOVAL,

        /** The plugin is being disabled, usually because the server is stopping. */
        PLUGIN_DISABLE
    }

    /** Bukkit dispatches on this list; it must be declared per concrete event class. */
    private static final HandlerList handlers = new HandlerList();

    /** Why the wormhole closed. */
    private final Reason reason;

    /**
     * Creates the event.
     *
     * @param stargate
     *            the gate whose wormhole closed
     * @param reason
     *            why it closed, never null
     */
    public StargateShutdownEvent(final Stargate stargate, final Reason reason)
    {
        super(stargate);
        if (reason == null)
        {
            throw new IllegalArgumentException("reason must not be null");
        }
        this.reason = reason;
    }

    /**
     * Why the wormhole closed.
     *
     * @return the reason, never null
     */
    public Reason getReason()
    {
        return reason;
    }

    /**
     * The handler list for this event type.
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
