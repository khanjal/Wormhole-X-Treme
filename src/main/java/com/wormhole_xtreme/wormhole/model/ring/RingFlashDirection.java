/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Which way the light runs through a ring stack at the moment of transport.
 */
package com.wormhole_xtreme.wormhole.model.ring;

/**
 * Which way the light runs through the stack when a ring pair fires.
 *
 * <p>The light runs through twice, once each side of the transport. It goes one way as the
 * travellers are taken and the other way as they arrive, so the two sweeps read as the
 * departure and the landing rather than as the same effect played twice.
 *
 * <p>Only the departure is configured. The arrival is always its {@link #opposite()},
 * because the whole point is that the second sweep undoes the first: two settings could be
 * set to the same direction, and then the arrival would look like a second departure.
 */
public enum RingFlashDirection
{
    /** Starts at the ring that flew highest and runs down to the floor. As the show does it. */
    TOP_DOWN,

    /** Starts at the floor and runs up. */
    BOTTOM_UP;

    /**
     * The other direction.
     *
     * @return the direction the arrival sweep runs, given this departure
     */
    public RingFlashDirection opposite()
    {
        return (this == TOP_DOWN) ? BOTTOM_UP : TOP_DOWN;
    }
}
