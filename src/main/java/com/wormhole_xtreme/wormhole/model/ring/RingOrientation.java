/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Whether a ring is set into a floor or a ceiling.
 */
package com.wormhole_xtreme.wormhole.model.ring;

/**
 * Which surface a transport ring is set into.
 *
 * <p>This is not decoration. Orientation decides two separate things, and getting either
 * backwards produces a ring that looks right and behaves wrongly:
 *
 * <ul>
 * <li>Which way the slabs travel when the ring deploys — up out of a floor, down out of a
 * ceiling.
 * <li>Where the trigger volume sits. A floor ring holds its passengers in the space above
 * it; a ceiling ring holds them in the space below.
 * </ul>
 *
 * <p>It is inferred at construction rather than asked for: slabs resting on a floor make a
 * floor ring, slabs hung under a ceiling make a ceiling one.
 */
public enum RingOrientation
{
    /** Set into a floor. Slabs rise, and passengers stand above the ring plane. */
    FLOOR(1),

    /** Set into a ceiling. Slabs descend, and passengers stand below the ring plane. */
    CEILING(-1);

    /** Which way is "into the room" from the ring plane: +1 for up, -1 for down. */
    private final int travel;

    /**
     * Instantiates a new orientation.
     *
     * @param travel
     *            +1 when the room is above the ring, -1 when it is below
     */
    RingOrientation(final int travel)
    {
        this.travel = travel;
    }

    /**
     * Gets the direction from the ring plane into the space it serves.
     *
     * <p>Both the deploying slabs and the passenger volume run this way, which is why one
     * number covers both: the animation travels into the room, and the room is where the
     * passengers are.
     *
     * @return +1 when the room is above the ring, -1 when it is below
     */
    public int getTravel()
    {
        return travel;
    }
}
