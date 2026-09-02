/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Two rings and the link between them: the unit that is stored and fired.
 */
package com.wormhole_xtreme.wormhole.model.ring;

/**
 * A linked pair of transport rings, which is the unit everything else works in.
 *
 * <p>A ring on its own can do nothing, so storing rings individually would only invent
 * problems: partner references that can dangle, a second resolution pass on load to fix
 * them up, and orphans that exist but go nowhere. Storing the pair means none of those
 * states are representable. Deleting a pair removes both ends because there is only one
 * thing to delete.
 *
 * <p>Both ends are always in the same world. That is a design rule rather than a setting —
 * gates remain the long-haul option and rings are local transport — and it pays for itself
 * by removing every half-loaded and dangling-world case at a stroke. {@code World} is
 * therefore a property of the pair, not of each end.
 *
 * <p>Pairs are not named. Nothing addresses a ring at runtime, because point-to-point
 * pairing means there is nothing to address, so a pair carries a generated id for commands
 * and log lines and an optional label that exists only to make a listing readable.
 */
public class RingPair
{
    /** Short generated id. Stable for the life of the pair, and the key it is stored under. */
    private final String id;

    /** Name of the world both ends are in. */
    private final String worldName;

    /** One end. Which end is which carries no meaning; the link is symmetric. */
    private final Ring endA;

    /** The other end. */
    private final Ring endB;

    /** UUID string of whoever built it. */
    private String owner;

    /** Display name of the owner, resolved when it can be. */
    private String ownerName;

    /** Optional display label. Empty means the listing falls back to the id. */
    private String label = "";

    /** When the pair was created, epoch millis. */
    private long created;

    /** Where the pair is in its cycle. */
    private RingPhase phase = RingPhase.IDLE;

    /** Epoch millis before which this pair refuses to fire. */
    private long cooldownUntil = 0L;

    /**
     * Instantiates a new ring pair.
     *
     * @param id
     *            short generated id
     * @param worldName
     *            the world both ends are in
     * @param endA
     *            one end
     * @param endB
     *            the other end
     */
    public RingPair(final String id, final String worldName, final Ring endA, final Ring endB)
    {
        this.id = id;
        this.worldName = worldName;
        this.endA = endA;
        this.endB = endB;
    }

    /** @return the short generated id */
    public String getId()
    {
        return id;
    }

    /** @return name of the world both ends are in */
    public String getWorldName()
    {
        return worldName;
    }

    /** @return one end of the pair */
    public Ring getEndA()
    {
        return endA;
    }

    /** @return the other end of the pair */
    public Ring getEndB()
    {
        return endB;
    }

    /**
     * The far end from the one given.
     *
     * <p>The link is symmetric and neither end is the origin, so travel is always expressed
     * as "the other one from where this started" rather than as a direction.
     *
     * @param end
     *            the end travelled from
     * @return the end travelled to, or null if the ring given is not part of this pair
     */
    public Ring opposite(final Ring end)
    {
        if (end == endA)
        {
            return endB;
        }
        if (end == endB)
        {
            return endA;
        }
        return null;
    }

    /** @return UUID string of whoever built it */
    public String getOwner()
    {
        return owner;
    }

    /**
     * Sets the owner.
     *
     * @param owner
     *            UUID string of the owner
     */
    public void setOwner(final String owner)
    {
        this.owner = owner;
    }

    /** @return display name of the owner */
    public String getOwnerName()
    {
        return ownerName;
    }

    /**
     * Sets the owner's display name.
     *
     * @param ownerName
     *            the display name
     */
    public void setOwnerName(final String ownerName)
    {
        this.ownerName = ownerName;
    }

    /** @return the optional display label, empty when unset */
    public String getLabel()
    {
        return label;
    }

    /**
     * Sets the display label.
     *
     * @param label
     *            the label, or empty to clear it
     */
    public void setLabel(final String label)
    {
        this.label = label == null ? "" : label;
    }

    /** @return creation time, epoch millis */
    public long getCreated()
    {
        return created;
    }

    /**
     * Sets the creation time.
     *
     * @param created
     *            epoch millis
     */
    public void setCreated(final long created)
    {
        this.created = created;
    }

    /** @return where the pair is in its cycle */
    public RingPhase getPhase()
    {
        return phase;
    }

    /**
     * Sets the cycle phase.
     *
     * @param phase
     *            the new phase
     */
    public void setPhase(final RingPhase phase)
    {
        this.phase = phase;
    }

    /** @return epoch millis before which this pair refuses to fire */
    public long getCooldownUntil()
    {
        return cooldownUntil;
    }

    /**
     * Sets when this pair may next fire.
     *
     * @param cooldownUntil
     *            epoch millis
     */
    public void setCooldownUntil(final long cooldownUntil)
    {
        this.cooldownUntil = cooldownUntil;
    }

    /**
     * Whether this pair will accept a trigger right now.
     *
     * <p>The cooldown is shared across both ends because a cycle is one event, not two. That
     * sharing is also what makes a per-player arrival guard unnecessary: someone who has
     * just landed cannot re-fire the ring they landed in, because the cooldown that started
     * when they left the other end is still running.
     *
     * @param now
     *            current time, epoch millis
     * @return true if a trigger would start a cycle
     */
    public boolean canFire(final long now)
    {
        return (phase == RingPhase.IDLE) && (now >= cooldownUntil);
    }

    /**
     * Whether the given player owns this pair.
     *
     * @param uuid
     *            the player's UUID string
     * @return true if they own it
     */
    public boolean isOwnedBy(final String uuid)
    {
        return (owner != null) && owner.equals(uuid);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        final String name = label.isEmpty() ? id : (label + " (" + id + ")");
        return name + " in " + worldName + ": " + endA + " <-> " + endB;
    }
}
