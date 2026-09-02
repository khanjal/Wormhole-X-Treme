/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   A real entity, seen as a ring passenger.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * A real entity, seen through the small window the cycle needs.
 *
 * <p>Everything the swap has to know about a traveller is whether the access rules apply to
 * it and who it is. Wrapping the entity rather than passing it around keeps
 * {@link RingCycle} free of Bukkit, which is what lets the ordering of the swap be tested
 * without a server.
 */
public class BukkitRingPassenger implements RingPassenger
{
    /** The entity travelling. */
    private final Entity entity;

    /**
     * Instantiates a passenger.
     *
     * @param entity
     *            the entity travelling
     */
    public BukkitRingPassenger(final Entity entity)
    {
        this.entity = entity;
    }

    /** @return the entity travelling */
    public Entity getEntity()
    {
        return entity;
    }

    /* (non-Javadoc)
     * @see RingPassenger#isPlayer()
     */
    @Override
    public boolean isPlayer()
    {
        return entity instanceof Player;
    }

    /* (non-Javadoc)
     * @see RingPassenger#getUniqueId()
     */
    @Override
    public String getUniqueId()
    {
        return entity.getUniqueId().toString();
    }

    /* (non-Javadoc)
     * @see RingPassenger#getVehicleId()
     */
    @Override
    public String getVehicleId()
    {
        final org.bukkit.entity.Entity vehicle = entity.getVehicle();
        return (vehicle == null) ? null : vehicle.getUniqueId().toString();
    }

    /* (non-Javadoc)
     * @see RingPassenger#getName()
     */
    @Override
    public String getName()
    {
        return entity.getName();
    }
}
