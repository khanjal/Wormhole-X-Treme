package com.wormhole_xtreme.wormhole.events;

import org.bukkit.entity.Minecart;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The Stargate Minecart Teleport Event Class.
 * 
 * @author alron
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
public class StargateMinecartTeleportEvent extends Event
{

    /** The old minecart. */
    private final Minecart oldMinecart;

    /** The new minecart. */
    private final Minecart newMinecart;
    private static final HandlerList handlers = new HandlerList();

    /**
     * Instantiates a new stargate minecart teleport event, fired when a cart would not move
     * through the gate and a replacement was spawned at the far end instead.
     * 
     * @param oldMinecart
     *            the cart that entered the gate, left where it was
     * @param newMinecart
     *            the cart spawned at the far end in its place
     */
    public StargateMinecartTeleportEvent(final Minecart oldMinecart, final Minecart newMinecart)
    {
        this.oldMinecart = oldMinecart;
        this.newMinecart = newMinecart;
    }

    /**
     * Gets the new minecart.
     * 
     * @return the cart spawned at the far end in the old one's place
     */
    public Minecart getNewMinecart()
    {
        return newMinecart;
    }

    /**
     * Gets the old minecart.
     * 
     * @return the cart that entered the gate, still at the departure gate
     */
    public Minecart getOldMinecart()
    {
        return oldMinecart;
    }
    
        @Override
        public HandlerList getHandlers()
        {
            return handlers;
        }
    
        public static HandlerList getHandlerList()
        {
            return handlers;
        }

}
