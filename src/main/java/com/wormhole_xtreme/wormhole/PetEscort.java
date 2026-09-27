package com.wormhole_xtreme.wormhole;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChunkTickets;
import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Brings a player's pets along when a gate, ring, beam or mirror moves them.
 *
 * <p>Vanilla pets follow their owner only by walking or a short hop within one world, so every
 * transport left them behind. Each transport gathers before moving its traveller, since once the
 * owner has gone there is nothing left to measure the pets against, and they follow a moment later.
 */
public final class PetEscort
{
    /** Vanilla teleports a following pet to its owner from 12 blocks, so nearer is following. */
    static final double REACH = 12.0;

    /** A second: long enough for a rider to be re-seated and a client to load a world it moved to. */
    public static final long FOLLOW_DELAY_TICKS = 20L;

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
        if (owner == null)
        {
            return List.of();
        }
        if (!ConfigManager.isPetsFollowOwner())
        {
            PluginLog.log(Level.FINE, "Pets stay behind: pets-follow-owner is off");
            return List.of();
        }
        final List<Entity> pets = new ArrayList<>();
        final boolean explain = PluginLog.isLoggable(Level.FINE);
        try
        {
            final Location from = owner.getLocation();
            for (final Entity entity : owner.getNearbyEntities(REACH, REACH, REACH))
            {
                if (follows(entity, owner.getUniqueId()) && withinReach(entity.getLocation(), from))
                {
                    pets.add(entity);
                }
                else if (explain)
                {
                    explainLeftBehind(entity, owner);
                }
            }
        }
        catch (final RuntimeException e)
        {
            // A pet left behind is not worth failing the owner's own trip over.
            PluginLog.log(Level.FINE, "Could not look for " + owner.getName() + "'s pets", e);
            return List.of();
        }
        if (explain)
        {
            PluginLog.log(Level.FINE, "Pets travelling with " + owner.getName() + ": " + pets.size());
        }
        return pets;
    }

    /**
     * Says why one of the owner's own pets is not coming, for whoever is testing a transport.
     *
     * @param entity
     *            an entity near the owner that is not travelling
     * @param owner
     *            the traveller
     */
    private static void explainLeftBehind(final Entity entity, final Player owner)
    {
        if (!(entity instanceof Tameable pet) || (pet.getOwner() == null)
            || !owner.getUniqueId().equals(pet.getOwner().getUniqueId()))
        {
            return;
        }
        final String why;
        if (!(entity instanceof Sittable sittable))
        {
            why = "not a following pet";
        }
        else if (sittable.isSitting())
        {
            why = "sitting";
        }
        else if (entity.isInsideVehicle())
        {
            why = "riding something";
        }
        else if (entity.isDead())
        {
            why = "dead";
        }
        else
        {
            why = "further than " + (int) REACH + " blocks away";
        }
        PluginLog.log(Level.FINE, owner.getName() + "'s " + entity.getType() + " stays behind: " + why);
    }

    /**
     * Whether a pet the box turned up stands within {@link #REACH} of its owner.
     *
     * <p>{@link Entity#getNearbyEntities} takes half-widths and hands back a box, and the corner of
     * that box is twenty blocks from the middle. Vanilla's own following is a distance, and so is
     * {@link #apart}, which decides at the far end whether a pet still needs bringing; a pet only
     * the corner reaches would be gathered here and judged out of reach there.
     *
     * @param pet
     *            where the pet stands, or null if the server will not say
     * @param ownerAt
     *            where its owner stands, or null
     * @return true unless both are known and they stand further apart than {@link #REACH}
     */
    private static boolean withinReach(final Location pet, final Location ownerAt)
    {
        if ((pet == null) || (ownerAt == null) || (pet.getWorld() == null) || (ownerAt.getWorld() == null))
        {
            // Nothing to measure with. The box has already said it is near, and leaving a pet
            // behind over a location the server would not give is the worse way to be wrong.
            return true;
        }
        return !apart(pet, ownerAt);
    }

    /**
     * Whether an entity is a pet that follows this owner: tamed to them and not told to stay.
     *
     * <p>{@link Sittable} as well as {@link Tameable} keeps it to wolves, cats and parrots; a
     * tamed horse is tameable but does not follow anyone.
     *
     * @param entity
     *            an entity near the owner
     * @return true if it should travel with them
     */
    static boolean follows(final Entity entity, final UUID ownerId)
    {
        return belongsTo(entity, ownerId) && !((Sittable) entity).isSitting();
    }

    /**
     * Whether an entity is a following kind of pet tamed to this owner, free to come, sitting or not.
     *
     * @param entity
     *            an entity
     * @param ownerId
     *            the owner
     * @return true if it is theirs, alive and riding nothing
     */
    private static boolean belongsTo(final Entity entity, final UUID ownerId)
    {
        if (!(entity instanceof Tameable pet) || !(entity instanceof Sittable) || !pet.isTamed())
        {
            return false;
        }
        final AnimalTamer tamer = pet.getOwner();
        return (tamer != null) && ownerId.equals(tamer.getUniqueId()) && !entity.isInsideVehicle()
            && !entity.isDead();
    }

    /**
     * Sends gathered pets after their owner, a moment after the trip.
     *
     * <p>Not in the same tick. A mounted or vehicle rider is re-seated at the far end ticks later,
     * a refused trip leaves the owner where they were, and a client that has just changed world
     * drops an entity sent before it has loaded, which in play left a pet beside its owner on
     * the server and invisible to them. After the delay the owner is wherever they really are.
     * The pets' chunks are held loaded until then: with the owner gone to another world nothing
     * else keeps them, and an unloaded pet cannot be teleported.
     *
     * @param pets
     *            what {@link #gather(Player)} found before the trip
     * @param owner
     *            the traveller
     */
    public static void follow(final List<Entity> pets, final Player owner)
    {
        if (pets.isEmpty() || (owner == null) || (WormholeXTreme.getScheduler() == null))
        {
            return;
        }
        final List<Chunk> held = hold(pets);
        int task = -1;
        try
        {
            task = WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), () ->
            {
                try
                {
                    bring(pets, owner);
                }
                finally
                {
                    release(held);
                }
            }, FOLLOW_DELAY_TICKS);
        }
        catch (final RuntimeException e)
        {
            // A plugin being disabled cannot book the task; the pets stay, as nothing will bring them.
            PluginLog.log(Level.FINE, "Could not send " + owner.getName() + "'s pets after them", e);
        }
        if (task == -1)
        {
            release(held);
        }
    }

    /**
     * Keeps the chunks the pets stand in loaded until they have been brought.
     *
     * <p>One chunk each is enough. A plugin ticket also keeps the chunks around it loaded, and
     * only the held chunk ticks its entities, so a pet cannot wander out of reach of it.
     *
     * @param pets
     *            the pets about to follow
     * @return the chunks held, one entry per hold taken
     */
    private static List<Chunk> hold(final List<Entity> pets)
    {
        final List<Chunk> held = new ArrayList<>();
        for (final Entity pet : pets)
        {
            try
            {
                final Location at = pet.getLocation();
                if (at.getWorld() == null)
                {
                    continue;
                }
                final Chunk chunk = at.getWorld().getChunkAt(at);
                ChunkTickets.hold(chunk);
                held.add(chunk);
            }
            catch (final RuntimeException e)
            {
                // Without the ticket this pet may still make it, as it did before there was one.
                PluginLog.log(Level.FINE, "Could not hold " + pet.getType() + "'s chunk", e);
            }
        }
        return held;
    }

    /**
     * Lets go of the holds {@link #hold} took.
     *
     * @param held
     *            what it returned
     */
    private static void release(final List<Chunk> held)
    {
        for (final Chunk chunk : held)
        {
            try
            {
                ChunkTickets.release(chunk);
            }
            catch (final RuntimeException e)
            {
                PluginLog.log(Level.FINE, "Could not release a pet's chunk", e);
            }
        }
    }

    /**
     * Sends gathered pets to where their owner now is.
     *
     * @param pets
     *            what {@link #gather(Player)} found before the trip
     * @param owner
     *            the traveller, by now arrived or not
     * @return how many made it
     */
    static int bring(final List<Entity> pets, final Player owner)
    {
        final Location arrival = owner.isOnline() ? owner.getLocation() : null;
        if ((arrival == null) || (arrival.getWorld() == null))
        {
            return 0;
        }
        int brought = 0;
        for (final Entity pet : pets)
        {
            if (bringOne(pet, owner.getUniqueId(), arrival))
            {
                brought++;
            }
        }
        return brought;
    }

    /**
     * Sends one pet to its owner, unless it no longer needs or wants to come.
     *
     * @param ownerId
     *            its owner
     * @param arrival
     *            where the owner now is
     * @return true if it was brought
     */
    private static boolean bringOne(final Entity pet, final UUID ownerId, final Location arrival)
    {
        try
        {
            // Told to sit in the meantime, or still beside an owner whose trip was refused. Vanilla
            // sits a pet whose owner is in another world, so only a sit in the owner's world is an order.
            final Location from = pet.getLocation();
            final boolean ownerElsewhere = (from.getWorld() != null) && !from.getWorld().equals(arrival.getWorld());
            if (!(ownerElsewhere ? belongsTo(pet, ownerId) : follows(pet, ownerId)) || !apart(from, arrival))
            {
                return false;
            }
            if (!pet.teleport(arrival))
            {
                PluginLog.log(Level.FINE, "Could not bring " + pet.getType() + " to " + arrival.getWorld().getName()
                    + ": the teleport was refused (valid " + pet.isValid() + ")");
                return false;
            }
            // Landing next to a gate must not count as walking into it.
            WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(pet.getUniqueId());
            pet.setVelocity(new Vector());
            pet.setFallDistance(0);
            return true;
        }
        catch (final RuntimeException e)
        {
            // Refused by another plugin, or gone mid-trip; the others still come.
            PluginLog.log(Level.FINE, "Could not bring " + pet.getType(), e);
            return false;
        }
    }

    /**
     * Whether a pet is out of following range of its owner.
     *
     * @param pet
     *            where the pet is
     * @param owner
     *            where the owner is
     * @return true if another world or beyond {@link #REACH}
     */
    static boolean apart(final Location pet, final Location owner)
    {
        if ((pet == null) || (pet.getWorld() == null) || !pet.getWorld().equals(owner.getWorld()))
        {
            return true;
        }
        return pet.distanceSquared(owner) > (REACH * REACH);
    }
}
