package com.wormhole_xtreme.wormhole.event;

import org.bukkit.entity.Minecart;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The Stargate Minecart Teleport Event Class.
 * 
 * @author alron
 */
public class StargateMinecartTeleportEvent extends Event
{

    /** The old minecart. */
    private final Minecart oldMinecart;

    /** The new minecart. */
    private final Minecart newMinecart;
    private static final HandlerList handlers = new HandlerList();

    /**
     * Instantiates a new stargate minecart teleport event.
     * 
     * @param oldMinecart
     *            the old minecart
     * @param newMinecart
     *            the new minecart
     */
    public StargateMinecartTeleportEvent(final Minecart oldMinecart, final Minecart newMinecart)
    {
        this.oldMinecart = oldMinecart;
        this.newMinecart = newMinecart;
    }

    /**
     * Gets the new minecart.
     * 
     * @return the new minecart
     */
    public Minecart getNewMinecart()
    {
        return newMinecart;
    }

    /**
     * Gets the old minecart.
     * 
     * @return the old minecart
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
