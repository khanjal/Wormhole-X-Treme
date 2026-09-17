package com.wormhole_xtreme.wormhole;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Brings a player's pets along when a gate, ring, beam or mirror moves them.
 *
 * <p>Vanilla pets follow their owner only by walking or a short hop within one world, so every
 * transport left them behind. Each transport gathers before moving its traveller and brings the
 * pets after, since once the owner has gone there is nothing left to measure the pets against.
 */
public final class PetEscort
{
    /** Vanilla teleports a following pet to its owner from 12 blocks, so nearer is following. */
    static final double REACH = 12.0;

    private PetEscort() {}

    /**
     * The pets following a player right now, read before they travel.
     *
     * @param owner
     *            the player about to be moved
     * @return their following pets, empty when there are none or the feature is off
     */
    public static List<Entity> gather(final Player owner)
    {
        if ((owner == null) || !ConfigManager.isPetsFollowOwner())
        {
            return List.of();
        }
        final List<Entity> pets = new ArrayList<>();
        try
        {
            for (final Entity entity : owner.getNearbyEntities(REACH, REACH, REACH))
            {
                if (follows(entity, owner.getUniqueId()))
                {
                    pets.add(entity);
                }
            }
        }
        catch (final RuntimeException e)
        {
            // A pet left behind is not worth failing the owner's own trip over.
            return List.of();
        }
        return pets;
    }

    /**
     * Whether an entity is a pet that follows this owner: tamed to them and not told to stay.
     *
     * <p>{@link Sittable} as well as {@link Tameable} keeps it to wolves, cats and parrots; a
     * tamed horse is tameable but does not follow anyone.
     *
     * @param entity
     *            an entity near the owner
     * @param ownerId
     *            the owner
     * @return true if it should travel with them
     */
    static boolean follows(final Entity entity, final UUID ownerId)
    {
        if (!(entity instanceof Tameable pet) || !(entity instanceof Sittable sittable) || !pet.isTamed())
        {
            return false;
        }
        final AnimalTamer tamer = pet.getOwner();
        return (tamer != null) && ownerId.equals(tamer.getUniqueId()) && !sittable.isSitting()
            && !entity.isInsideVehicle() && !entity.isDead();
    }

    /**
     * Sends gathered pets to where their owner arrived.
     *
     * @param pets
     *            what {@link #gather(Player)} found before the trip
     * @param arrival
     *            where the owner now stands
     * @return how many made it
     */
    public static int bring(final List<Entity> pets, final Location arrival)
    {
        if ((arrival == null) || (arrival.getWorld() == null))
        {
            return 0;
        }
        int brought = 0;
        for (final Entity pet : pets)
        {
            try
            {
                if (!pet.teleport(arrival))
                {
                    continue;
                }
                // Landing next to a gate must not count as walking into it.
                WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(pet.getUniqueId());
                pet.setVelocity(new Vector());
                pet.setFallDistance(0);
                brought++;
            }
            catch (final RuntimeException e)
            {
                // Refused by another plugin, or gone mid-trip; the others still come.
            }
        }
        return brought;
    }
}
