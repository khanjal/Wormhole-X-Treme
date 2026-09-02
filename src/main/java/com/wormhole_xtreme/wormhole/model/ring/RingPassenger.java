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
     * @return the player's UUID string, or null for anything that is not a player
     */
    String getUniqueId();

    /**
     * @return a name for messages and log lines
     */
    String getName();
}
