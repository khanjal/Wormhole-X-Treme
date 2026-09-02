/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Who may use a ring pair.
 */
package com.wormhole_xtreme.wormhole.model.ring;

/**
 * Who is allowed to travel by a ring pair.
 *
 * <p>Access belongs to the <em>pair</em>, not to either end, and that is not an
 * implementation convenience. Both ends fire together and everything in both interiors is
 * swapped in the same instant, so there is no such thing as authorising half of it. A pair
 * whose ends disagreed would be one you could leave by but not return to, which is not a
 * setting anybody wants and not a thing the swap can express.
 *
 * <p>This is the opposite of how materials work, and for a reason: a material is cosmetic
 * and local, so each end can look like the room it is in. Access is functional and about the
 * link itself.
 */
public enum RingAccess
{
    /** Anyone may use it. */
    PUBLIC,

    /** Only the owner and the people they have named. */
    PRIVATE
}
