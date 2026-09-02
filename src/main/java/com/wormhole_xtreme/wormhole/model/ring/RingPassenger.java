/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Something standing in a ring when it fires.
 */
package com.wormhole_xtreme.wormhole.model.ring;

/**
 * Something standing in a ring at the moment it fires.
 *
 * <p>An interface rather than a Bukkit entity, for one reason: the ordering of the swap is
 * the piece of this subsystem most worth being certain about, and it should not need a
 * running server to check. Everything the cycle needs to know about a traveller is here —
 * whether the rules apply to it, and who it is.
 *
 * <p>The distinction that matters is player versus everything else. Access rules apply only
 * to players; mobs, items and vehicles travel as cargo. That costs nothing to allow, because
 * only a player move can arm a ring in the first place, so a private pair never fires except
 * because somebody permitted made it fire.
 *
 * <p>Riding is the other thing worth knowing. A rider and its mount are two separate things
 * standing in the same ring, and sending them separately is what leaves somebody sitting on
 * the floor where their camel used to be.
 */
public interface RingPassenger
{
    /**
     * Whether the access rules apply to this traveller.
     *
     * @return true for a player, false for cargo
     */
    boolean isPlayer();

    /**
     * @return this thing's own UUID string, whatever it is
     */
    String getUniqueId();

    /**
     * What this is riding, if anything.
     *
     * <p>Needed because a rider and its mount are two separate things standing in the same
     * ring, and moving them separately is what tips somebody off their camel. Whoever is
     * riding something that is also travelling is carried by it instead of being sent on its
     * own.
     *
     * @return the UUID string of the vehicle it is riding, or null if it is not riding one
     */
    String getVehicleId();

    /**
     * @return a name for messages and log lines
     */
    String getName();
}
